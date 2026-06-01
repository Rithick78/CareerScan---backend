# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/career-scan-0.0.1-SNAPSHOT.jar careerscan.v1.0.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "careerscan.v1.0.jar"]