FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/handwriting-backend-0.0.1-SNAPSHOT.jar app.jar

RUN apt-get update && apt-get install -y \
	netcat-openbsd \
	tesseract-ocr \
	tesseract-ocr-eng \
	libtesseract-dev \
	libleptonica-dev \
	pkg-config \
	wget \
	ca-certificates \
	&& rm -rf /var/lib/apt/lists/*

# Ensure the English traineddata is present where Tess4J expects it
RUN mkdir -p /usr/share/tessdata \
	&& wget -q -O /usr/share/tessdata/eng.traineddata \
	   https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata || true

EXPOSE 5000

ENV OPENAI_API_KEY=""
ENV LANGFUSE_SECRET_KEY=""
ENV LANGFUSE_PUBLIC_KEY=""
ENV TESSDATA_PREFIX="/usr/share/tessdata"

ENTRYPOINT ["java", "-jar", "app.jar"]
# Ensure the Spring Boot server binds to all interfaces inside the container
CMD ["--server.port=5000","--server.address=0.0.0.0"]
