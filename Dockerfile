# syntax=docker/dockerfile:1
# Image unique de Learnink : le frontend React est compilé puis servi par le backend Spring Boot.
# Utilisé par Clever Cloud (Dockerfile à la racine), docker-compose.yml et Vercel (vercel.json).

# --- 1. Frontend ---------------------------------------------------------------------------------------------
FROM node:24-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# --- 2. Backend (tests compris) -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /build/backend
# Téléchargements Maven : chaque requête est retentée, et l'étape entière est relancée jusqu'à 5 fois en cas de
# coupure réseau avec Maven Central (« Remote host terminated the handshake »)
ENV MAVEN_ARGS="-B -q -Daether.connector.http.retryHandler.count=5"
COPY backend/pom.xml ./
RUN for essai in 1 2 3 4 5; do \
      mvn dependency:go-offline && exit 0; \
      echo "Téléchargement des dépendances échoué (essai $essai/5), nouvel essai dans 15 s"; sleep 15; \
    done; exit 1
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn package && cp target/learnink-backend-*.jar /build/app.jar

# --- 3. Exécution ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-noble
RUN useradd --system --uid 10001 --no-create-home learnink
WORKDIR /app
COPY --from=backend /build/app.jar app.jar
USER learnink
# Port d'écoute attendu par Clever Cloud (et à définir dans les variables du projet sur Vercel)
ENV PORT=8080
EXPOSE 8080
# La JVM s'adapte à la mémoire allouée au conteneur (1 Gio sur une instance XS Clever Cloud)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
