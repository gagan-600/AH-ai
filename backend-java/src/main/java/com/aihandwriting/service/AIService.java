package com.aihandwriting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class AIService {

    // IMPORTANT: supply this via application.properties or environment variable.
    // Do NOT hard-code keys in source.
   
    @Value("${openai.api.key:}")
    private String openAiKey;


    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Extract structured JSON from raw OCR text by calling OpenAI chat completions.
     * Returns a JSON string. If OpenAI key is missing or API fails, returns a safe fallback JSON.
     */
    public String extractStructuredData(String rawText) {
        try {
            if (openAiKey == null || openAiKey.isBlank()) {
                return buildAgentFallback(rawText, "OpenAI API key not configured.");
            }

                        // Improved prompt for better field extraction
                        String system = """
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

                        String user = "OCR input:\n" + rawText + "\n\nReturn only the JSON object in the schema described.";

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", new Object[] {
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    },
                    "max_tokens", 1024,
                    "temperature", 0.0
            );

            String requestJson = mapper.writeValueAsString(requestBody);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + openAiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            String body = resp.body();

            if (status / 100 != 2) {
                if (status == 401) {
                    return buildAgentFallback(rawText, "OpenAI returned 401 Unauthorized - check API key.");
                }
                return buildAgentFallback(rawText, "OpenAI API error: HTTP " + status);
            }

            JsonNode root = mapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return buildAgentFallback(rawText, "OpenAI returned no choices.");
            }

            String assistantText = choices.get(0).path("message").path("content").asText();
            String jsonResult = tryExtractJsonFromAssistant(assistantText);
            if (jsonResult == null) {
                return buildAgentFallback(rawText, "OpenAI returned non-JSON output.");
            }
            JsonNode parsed = mapper.readTree(jsonResult);

            // Build the requested schema:
            // {
            //   "document_type": string,
            //   "pages": [ { "page": number, "fields": [ {name,value,confidence} ], "tables": [] } ]
            // }
            ObjectNode out = mapper.createObjectNode();
            out.put("document_type", "handwritten_form");

            ObjectNode page = mapper.createObjectNode();
            page.put("page", 1);

            ArrayNode fields = mapper.createArrayNode();

            // Map fields from the simplified response - only include fields that are present
            String[] keys = new String[]{"name", "phone", "email", "address", "postcode", "dob", "ocrText"};
            for (String k : keys) {
                JsonNode v = parsed.path(k);
                boolean missing = v.isMissingNode() || v.isNull() || (v.isTextual() && v.asText().isBlank());

                // Always include ocrText when present
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

                // For other keys, only add if a non-empty value exists
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

    // New fallback for agent schema — returns the same structured schema with empty/default values
    private String buildAgentFallback(String rawText, String note) throws Exception {
        ObjectNode out = mapper.createObjectNode();
        out.put("document_type", "unknown");

        ObjectNode page = mapper.createObjectNode();
        page.put("page", 1);

        ArrayNode fields = mapper.createArrayNode();

        // Only include OCR text and an explanatory note in the fallback so consumers can still
        // see the raw input and the error reason. Do not add empty fields for absent structured values.
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
        if (assistantText == null) return null;
        assistantText = assistantText.trim();

        // remove code fences if present
        if (assistantText.startsWith("```") && assistantText.contains("```")) {
            int first = assistantText.indexOf("```");
            int second = assistantText.indexOf("```", first + 3);
            if (second > first) {
                assistantText = assistantText.substring(first + 3, second).trim();
            }
        }
        // try direct parse
        try {
            final ObjectMapper m = new ObjectMapper();
            m.readTree(assistantText);
            return assistantText;
        } catch (Exception ignored) {}

        // fallback: extract first balanced JSON object
        String candidate = extractFirstJsonObject(assistantText);
        return candidate;
    }

    private static String extractFirstJsonObject(String text) {
        if (text == null) return null;
        int len = text.length();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) continue;
            if (c == '{') {
                if (start == -1) start = i;
                depth++;
            } else if (c == '}') {
                if (depth > 0) depth--;
                if (depth == 0 && start != -1) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    // Removed old simple fallback; use buildAgentFallback for consistent schema.
}
