Spring Boot backend (Java) - Handwritten Form Reader

How to run:
1. Install Java 17+ and Maven.
2. Place tessdata (Tesseract trained data) on your machine and update OCRService.setDatapath if necessary.
3. cd backend-java
4. mvn spring-boot:run

Notes:
- AIService currently contains a placeholder. Replace with real LangChain4j/OpenAI calls.
- For LangFuse, make HTTP calls to their ingest API to record traces (or adapt when a Java SDK is available).
