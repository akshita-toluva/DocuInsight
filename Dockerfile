# ---------- Stage 0: model files ----------
# Render's build downloads them here. Locally, --build-context models=<folder> REPLACES this stage (no download).
FROM scratch AS models
ADD https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx /model.onnx
ADD https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json /tokenizer.json

# ---------- Stage 1: build the jar with Maven ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline          # cached layer: only re-runs when pom.xml changes
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Model files come from a named build context (no download), see the build command
COPY --from=models model.onnx tokenizer.json /models/all-MiniLM-L6-v2/
COPY --from=build /build/target/*.jar app.jar

# Run as a normal user, not root; it needs a home dir (DJL caches native libs there) and a writable uploads dir
RUN useradd -m appuser && mkdir -p /app/uploads && chown -R appuser /app /models
USER appuser

ENV EMBEDDING_MODEL_DIR=/models/all-MiniLM-L6-v2 \
    JAVA_TOOL_OPTIONS="-Xmx150m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m -XX:MaxMetaspaceSize=160m"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]