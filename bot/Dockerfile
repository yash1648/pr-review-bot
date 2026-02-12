#STAGE 1 : Build the application
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY .. .
RUN ./bot/mvnw clean package -DskipTests

#STAGE 2 : Run the application
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/bot/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
