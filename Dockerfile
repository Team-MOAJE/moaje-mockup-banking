FROM gradle:8.10.2-jdk21-alpine AS builder

WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src

RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/build/libs/banking-mockup.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
