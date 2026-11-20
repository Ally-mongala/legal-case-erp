# ============================================================================
# LegalCase ERP - backend container
# Multi-stage build: Maven compiles the fat jar, then a slim JRE runs it.
# Build:   docker build -t legal-case-erp-backend -f backend/Dockerfile .
# Run:     docker compose up   (see docker-compose.yml - PostgreSQL on its own
#          container, meeting the "components hosted on different machines"
#          requirement)
# ============================================================================

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# copy the Maven project (shared + backend needed to build the backend jar)
COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY backend/pom.xml backend/pom.xml
RUN mvn -q -pl shared -am dependency:go-offline || true

COPY shared shared
COPY backend backend
RUN mvn -q -pl shared install -DskipTests \
 && mvn -q -pl backend package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# OCR engine for scanned PDFs (optional at runtime; the app reports clearly when it is missing)
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/backend/target/legal-backend.jar app.jar

# data (uploads + database files for H2) - override with a volume for persistence
RUN mkdir -p /app/data
VOLUME ["/app/data"]

ENV LCE_DATA_DIR=/app/data \
    LCE_DEMO_AUTH=false
# LCE_PORT is deliberately NOT set here: the app falls back to the PORT variable
# that Render / Cloud Run / Railway inject, and defaults to 8080 otherwise.
# docker-compose.yml sets LCE_PORT=8080 explicitly for the local stack.

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
