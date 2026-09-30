FROM node:22-alpine AS web-build
WORKDIR /workspace/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk-alpine AS backend-build
WORKDIR /workspace
COPY backend/ ./backend/
RUN rm -rf ./backend/src/main/resources/static/*
COPY --from=web-build /workspace/web/dist/ ./backend/src/main/resources/static/
WORKDIR /workspace/backend
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S sportmap && adduser -S sportmap -G sportmap
COPY --from=backend-build --chown=sportmap:sportmap /workspace/backend/build/libs/*.jar /app/sportmap.jar
USER sportmap
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/sportmap.jar"]
