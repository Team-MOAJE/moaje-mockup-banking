FROM gradle:8.10.2-jdk21-alpine AS builder

WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src

RUN gradle --no-daemon bootJar -x test --max-workers=1

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/build/libs/banking-mockup.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
