package com.aihandwriting;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HandwritingBackendApplication {
    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(HandwritingBackendApplication.class, args);
    }

    private static void loadEnv() {
        String apiKey = null;
        String source = null;

        // 1. Try loading from parent directory
        try {
            Dotenv parentEnv = Dotenv.configure()
                    .directory("../")
                    .ignoreIfMissing()
                    .load();
            String parentKey = parentEnv.get("OPENAI_API_KEY");
            if (isValidKey(parentKey)) {
                apiKey = parentKey;
                source = "parent directory (../.env)";
                // Load other keys from parent
                parentEnv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
            }
        } catch (Exception ignored) {
        }

        // 2. Try loading from current directory
        try {
            Dotenv currentEnv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            String currentKey = currentEnv.get("OPENAI_API_KEY");
            
            // Use current key if it's valid, overriding parent
            if (isValidKey(currentKey)) {
                apiKey = currentKey;
                source = "current directory (./.env)";
                // Load other keys from current
                currentEnv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
            } else if (currentKey != null) {
                System.out.println("WARNING: Ignoring potentially invalid API key in ./.env (ends in h90A)");
                // Load other keys BUT NOT the bad API key
                currentEnv.entries().forEach(entry -> {
                    if (!"OPENAI_API_KEY".equals(entry.getKey())) {
                        System.setProperty(entry.getKey(), entry.getValue());
                    }
                });
            }
        } catch (Exception ignored) {
        }

        if (apiKey != null) {
            System.setProperty("OPENAI_API_KEY", apiKey);
            System.out.println("Loaded OPENAI_API_KEY from " + source);
        } else {
            System.out.println("WARNING: No valid OPENAI_API_KEY found in .env files.");
        }
    }

    private static boolean isValidKey(String key) {
        return key != null && !key.isBlank() && !key.trim().endsWith("h90A");
    }
}
