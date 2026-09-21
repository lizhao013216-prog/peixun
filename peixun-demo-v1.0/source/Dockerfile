FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
COPY backend/src ./src
RUN mvn -B -ntp package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app/backend
COPY --from=backend /build/backend/target/peixun-demo-1.0.0.jar ./app.jar
COPY --from=frontend /build/frontend/dist /app/frontend/dist
RUN mkdir -p /app/backend/.data && chown -R 10001:10001 /app
USER 10001
ENV BIND_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
