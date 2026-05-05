FROM eclipse-temurin:17-jdk-alpine as builder

WORKDIR /app

# Copy Maven wrapper and pom files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY po-service/pom.xml po-service/
COPY po-rules/pom.xml po-rules/
COPY po-plugin/pom.xml po-plugin/
COPY po-legacy-bridge/pom.xml po-legacy-bridge/
COPY po-api/pom.xml po-api/
COPY po-test/pom.xml po-test/

# Download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY po-service/src po-service/src
COPY po-rules/src po-rules/src
COPY po-plugin/src po-plugin/src
COPY po-legacy-bridge/src po-legacy-bridge/src
COPY po-api/src po-api/src
COPY config config

# Build application
RUN ./mvnw clean package -DskipTests -pl po-api -am

# Runtime image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/po-api/target/*.jar app.jar

# Copy config files
COPY --from=builder /app/config config

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
