package com.aihandwriting.service;

import com.aihandwriting.config.LangfuseChatModelListener;
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

import java.time.Duration;
import java.util.Collections;

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

    /**
     * Extract structured JSON from raw OCR text by calling OpenAI chat completions.
     * Returns a JSON string. If OpenAI key is missing or API fails, returns a safe
     * fallback JSON.
     */
    public String extractStructuredData(String rawText) {
        try {
            System.err.println(
                    "DEBUG: AIService called with text length: " + (rawText != null ? rawText.length() : "null"));
            System.err.println("DEBUG: OpenAI Key present: " + (openAiKey != null && !openAiKey.isBlank()));

            if (openAiKey == null || openAiKey.isBlank()) {
                System.err.println("DEBUG: OpenAI Key missing, returning fallback.");
                return buildAgentFallback(rawText, "OpenAI API key not configured.");
            }

            LangfuseChatModelListener listener = new LangfuseChatModelListener(
                    langfusePublicKey,
                    langfuseSecretKey,
                    langfuseHost);

            ChatLanguageModel model = OpenAiChatModel.builder()
                    .apiKey(openAiKey)
                    .modelName("gpt-4o-mini")
                    .temperature(0.0)
                    .timeout(Duration.ofSeconds(30))
                    .listeners(Collections.singletonList(listener))
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

            System.err.println("DEBUG: Calling model.generate...");
            String assistantText = model.generate(
                    SystemMessage.from(systemText),
                    UserMessage.from(userText)).content().text();
            System.err.println("DEBUG: Model response received.");

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
            e.printStackTrace();
            System.err.println("❌ AIService Exception: " + e.getMessage());
            try {
                return buildAgentFallback(rawText, "Exception: " + e.getMessage());
            } catch (Exception ignore) {
                return "{\"document_type\":\"unknown\",\"pages\":[{\"page\":1,\"fields\":[],\"tables\":[]}]}";
            }
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
