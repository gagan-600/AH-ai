package com.aihandwriting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public class LangfuseChatModelListener implements ChatModelListener {

    private final String publicKey;
    private final String secretKey;
    private final String host;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public LangfuseChatModelListener(String publicKey, String secretKey, String host) {
        this.publicKey = publicKey;
        this.secretKey = secretKey;
        this.host = host;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        try {
            ChatModelRequest request = context.request();
            ChatModelResponse response = context.response();

            sendGeneration(request, response, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        try {
            ChatModelRequest request = context.request();
            Throwable error = context.error();
            sendGeneration(request, null, error);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendGeneration(ChatModelRequest request, ChatModelResponse response, Throwable error) {
        System.out.println("🚀 LangfuseChatModelListener: Sending generation...");
        try {
            String traceId = UUID.randomUUID().toString();
            String observationId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            // Create trace event
            ObjectNode traceBody = mapper.createObjectNode();
            traceBody.put("id", traceId);
            traceBody.put("name", "chat-completion-trace");
            traceBody.put("timestamp", now.toString());
            if (request != null && request.messages() != null) {
                traceBody.put("input", request.messages().toString());
            }
            if (response != null && response.aiMessage() != null) {
                traceBody.put("output", response.aiMessage().text());
            }

            ObjectNode traceEvent = mapper.createObjectNode();
            traceEvent.put("id", UUID.randomUUID().toString());
            traceEvent.put("type", "trace-create");
            traceEvent.put("timestamp", now.toString());
            traceEvent.set("body", traceBody);

            // Create observation event
            ObjectNode observationBody = mapper.createObjectNode();
            observationBody.put("id", observationId);
            observationBody.put("traceId", traceId); // Link to trace
            observationBody.put("type", "GENERATION");
            observationBody.put("name", "chat-completion");
            observationBody.put("startTime", now.toString());
            observationBody.put("endTime", now.toString());

            // Input
            if (request != null && request.messages() != null) {
                observationBody.put("input", request.messages().toString());
                if (request.model() != null) {
                    observationBody.put("model", request.model());
                }
            }

            // Output
            if (response != null) {
                if (response.aiMessage() != null) {
                    observationBody.put("output", response.aiMessage().text());
                }
                // Usage
                if (response.tokenUsage() != null) {
                    ObjectNode usage = observationBody.putObject("usage");
                    usage.put("input", response.tokenUsage().inputTokenCount());
                    usage.put("output", response.tokenUsage().outputTokenCount());
                    usage.put("total", response.tokenUsage().totalTokenCount());
                }
            } else if (error != null) {
                observationBody.put("output", "Error: " + error.getMessage());
                observationBody.put("level", "ERROR");
            }

            ObjectNode observationEvent = mapper.createObjectNode();
            observationEvent.put("id", UUID.randomUUID().toString());
            observationEvent.put("type", "observation-create");
            observationEvent.put("timestamp", now.toString());
            observationEvent.set("body", observationBody);

            // Create batch with both events
            ObjectNode payload = mapper.createObjectNode();
            ArrayNode batch = payload.putArray("batch");
            batch.add(traceEvent);
            batch.add(observationEvent);

            String json = mapper.writeValueAsString(payload);

            String auth = basicAuth(publicKey, secretKey);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(host + "/api/public/ingestion"))
                    .header("Authorization", auth)
                    .header("Content-Type", "application/json")
                    .version(HttpClient.Version.HTTP_1_1)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            System.err.println("DEBUG: Sending HTTP request to: " + host + "/api/public/ingestion");
            System.err.println("DEBUG: Payload JSON: " + json);

            try {
                HttpResponse<String> res = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                System.err.println("DEBUG: Response status: " + res.statusCode());
                System.err.println("DEBUG: Response body: " + res.body());

                if (res.statusCode() == 200 || res.statusCode() == 201 || res.statusCode() == 207) {
                    System.out.println("✅ Langfuse ingestion success: " + res.statusCode());
                } else {
                    System.err.println("❌ Langfuse ingestion failed: " + res.statusCode() + " " + res.body());
                }
            } catch (java.io.IOException ioEx) {
                System.err.println("❌ Langfuse IOException: " + ioEx.getMessage());
                System.err.println("   Host: " + host);
            } catch (InterruptedException intEx) {
                System.err.println("❌ Langfuse InterruptedException: " + intEx.getMessage());
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String basicAuth(String user, String pass) {
        String auth = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }
}
