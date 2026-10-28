# Multi-stage build: compile with Java 25, then run with a smaller Java 25 image.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:25-jre-noble
ENV PORT=8080
ENV FFMPEG=ffmpeg
WORKDIR /app

# Install the Linux FFmpeg binary used by HlsProcessingService.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar /app/music-service.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/music-service.jar"]
