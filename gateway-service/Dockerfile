# Build stage
FROM gradle:8.6-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
