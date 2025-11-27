package com.aihandwriting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class AIService {

    @Value("${openai.api.key:}")
    private String openAiKey;

    @Value("${langfuse.public.key:}")
    private String langfusePublicKey;

    @Value("${langfuse.secret.key:}")
    private String langfuseSecretKey;

    @Value("${langfuse.host:https://cloud.langfuse.com}")
    private String langfuseHost;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Extract structured JSON from raw OCR text by calling OpenAI chat completions.
     * Returns a JSON string. If OpenAI key is missing or API fails, returns a safe
     * fallback JSON.
     */
    public String extractStructuredData(String rawText) {
        try {
            if (openAiKey == null || openAiKey.isBlank()) {
                return buildAgentFallback(rawText, "OpenAI API key not configured.");
            }

            ChatLanguageModel model = OpenAiChatModel.builder()
                    .apiKey(openAiKey)
                    .modelName("gpt-4o-mini")
                    .temperature(0.0)
                    .timeout(Duration.ofSeconds(30))
                    .build();

            String systemText = """
                    You are an expert at extracting structured data from handwritten forms and OCR text.
                    Your task is to carefully identify and extract ALL available information from the provided text.

                    CRITICAL: Return ONLY a valid JSON object with these exact fields:
                    {
                        "name": "extracted full name or null",
                        "phone": "extracted phone number or null",
                        "email": "extracted email address or null",
                        "address": "extracted full address or null",
                        "postcode": "extracted postcode/zip or null",
                        "dob": "extracted date of birth or null",
                        "ocrText": "the raw OCR input text"
                    }

                    EXTRACTION RULES:
                    - Name: Full name in Title Case (e.g., "Paula Butler", "John Smith")
                    - Phone: Standardized format with country code if visible (e.g., "+44 20 7123 4567", "020 7123 4567")
                    - Email: Lowercase email address (e.g., "paulab400@mail.com")
                    - Address: Complete street address, city, and region on one line
                    - Postcode: Only the postal/zip code (e.g., "87654", "AP 87654")
                    - DOB: Date format YYYY-MM-DD if possible, or any clear date format
                    - ocrText: Include the complete raw input as provided

                    IMPORTANT:
                    - If ANY value is unclear or missing, use null (not empty string)
                    - Do NOT include markdown, code fences, or explanations
                    - Return ONLY the JSON object, nothing else
                    """;

            String userText = "OCR input:\n" + rawText + "\n\nReturn only the JSON object in the schema described.";

            String assistantText = null;
            long startTime = System.currentTimeMillis();
            try {
                assistantText = model.generate(
                        SystemMessage.from(systemText),
                        UserMessage.from(userText)).content().text();
            } finally {
                long endTime = System.currentTimeMillis();
                logToLangfuse(rawText, systemText, userText, assistantText, startTime, endTime);
            }

            String jsonResult = tryExtractJsonFromAssistant(assistantText);
            if (jsonResult == null) {
                return buildAgentFallback(rawText, "OpenAI returned non-JSON output.");
            }
            JsonNode parsed = mapper.readTree(jsonResult);

            ObjectNode out = mapper.createObjectNode();
            out.put("document_type", "handwritten_form");

            ObjectNode page = mapper.createObjectNode();
            page.put("page", 1);

            ArrayNode fields = mapper.createArrayNode();

            String[] keys = new String[] { "name", "phone", "email", "address", "postcode", "dob", "ocrText" };
            for (String k : keys) {
                JsonNode v = parsed.path(k);
                boolean missing = v.isMissingNode() || v.isNull() || (v.isTextual() && v.asText().isBlank());

                if ("ocrText".equals(k)) {
                    if (!v.isMissingNode()) {
                        ObjectNode f = mapper.createObjectNode();
                        f.put("name", k);
                        if (v.isNull()) {
                            f.putNull("value");
                        } else {
                            f.put("value", v.asText());
                        }
                        f.put("confidence", 0.0);
                        fields.add(f);
                    }
                    continue;
                }

                if (!missing) {
                    ObjectNode f = mapper.createObjectNode();
                    f.put("name", k);
                    if (v.isNull()) {
                        f.putNull("value");
                    } else {
                        f.put("value", v.asText());
                    }
                    f.put("confidence", 0.0);
                    fields.add(f);
                }
            }

            page.set("fields", fields);
            page.set("tables", mapper.createArrayNode());

            ArrayNode pages = mapper.createArrayNode();
            pages.add(page);

            out.set("pages", pages);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);

        } catch (Exception e) {
            try {
                return buildAgentFallback(rawText, "Exception: " + e.getMessage());
            } catch (Exception ignore) {
                return "{\"document_type\":\"unknown\",\"pages\":[{\"page\":1,\"fields\":[],\"tables\":[]}]}";
            }
        }
    }

    private void logToLangfuse(String rawInput, String systemPrompt, String userPrompt, String output, long startTime,
            long endTime) {
        if (langfusePublicKey == null || langfusePublicKey.isBlank() ||
                langfuseSecretKey == null || langfuseSecretKey.isBlank()) {
            return;
        }

        try {
            String traceId = UUID.randomUUID().toString();
            String generationId = UUID.randomUUID().toString();
            String now = java.time.Instant.now().toString();

            // Prepare batch ingestion payload
            ObjectNode root = mapper.createObjectNode();
            ArrayNode batch = root.putArray("batch");

            // 1. Trace Create
            ObjectNode traceEvent = mapper.createObjectNode();
            traceEvent.put("id", UUID.randomUUID().toString());
            traceEvent.put("type", "trace-create");
            ObjectNode traceBody = traceEvent.putObject("body");
            traceBody.put("id", traceId);
            traceBody.put("name", "extract-structured-data");
            traceBody.put("timestamp", now);
            batch.add(traceEvent);

            // 2. Generation Create
            ObjectNode genEvent = mapper.createObjectNode();
            genEvent.put("id", UUID.randomUUID().toString());
            genEvent.put("type", "generation-create");
            ObjectNode genBody = genEvent.putObject("body");
            genBody.put("traceId", traceId);
            genBody.put("id", generationId);
            genBody.put("name", "gpt-4o-mini-extraction");
            genBody.put("startTime", java.time.Instant.ofEpochMilli(startTime).toString());
            genBody.put("endTime", java.time.Instant.ofEpochMilli(endTime).toString());
            genBody.put("model", "gpt-4o-mini");

            // Input/Output
            ArrayNode inputMsgs = mapper.createArrayNode();
            inputMsgs.add(mapper.createObjectNode().put("role", "system").put("content", systemPrompt));
            inputMsgs.add(mapper.createObjectNode().put("role", "user").put("content", userPrompt));
            genBody.set("input", inputMsgs);

            genBody.put("output", output);

            batch.add(genEvent);

            String jsonPayload = mapper.writeValueAsString(root);
            String auth = Base64.getEncoder()
                    .encodeToString((langfusePublicKey + ":" + langfuseSecretKey).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(langfuseHost + "/api/public/ingestion"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            // Fire and forget (async) or sync but catch error
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(resp -> {
                        if (resp.statusCode() >= 400) {
                            System.err.println("Langfuse ingestion failed: " + resp.statusCode());
                        }
                    });

        } catch (Exception e) {
            System.err.println("Failed to log to Langfuse: " + e.getMessage());
        }
    }

    private String buildAgentFallback(String rawText, String note) throws Exception {
        ObjectNode out = mapper.createObjectNode();
        out.put("document_type", "unknown");

        ObjectNode page = mapper.createObjectNode();
        page.put("page", 1);

        ArrayNode fields = mapper.createArrayNode();

        if (rawText != null && !rawText.isBlank()) {
            ObjectNode ocr = mapper.createObjectNode();
            ocr.put("name", "ocrText");
            ocr.put("value", rawText);
            ocr.put("confidence", 0.0);
            fields.add(ocr);
        }

        if (note != null && !note.isBlank()) {
            ObjectNode noteField = mapper.createObjectNode();
            noteField.put("name", "note");
            noteField.put("value", note);
            noteField.put("confidence", 0.0);
            fields.add(noteField);
        }

        page.set("fields", fields);
        page.set("tables", mapper.createArrayNode());

        ArrayNode pages = mapper.createArrayNode();
        pages.add(page);
        out.set("pages", pages);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    private static String tryExtractJsonFromAssistant(String assistantText) {
        if (assistantText == null)
            return null;
        assistantText = assistantText.trim();

        if (assistantText.startsWith("```") && assistantText.contains("```")) {
            int first = assistantText.indexOf("```");
            int second = assistantText.indexOf("```", first + 3);
            if (second > first) {
                assistantText = assistantText.substring(first + 3, second).trim();
            }
        }
        try {
            final ObjectMapper m = new ObjectMapper();
            m.readTree(assistantText);
            return assistantText;
        } catch (Exception ignored) {
        }

        return extractFirstJsonObject(assistantText);
    }

    private static String extractFirstJsonObject(String text) {
        if (text == null)
            return null;
        int len = text.length();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString)
                continue;
            if (c == '{') {
                if (start == -1)
                    start = i;
                depth++;
            } else if (c == '}') {
                if (depth > 0)
                    depth--;
                if (depth == 0 && start != -1) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}
