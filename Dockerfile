FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/career-scan-0.0.1-SNAPSHOT.jar careerscan.v1.0.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "careerscan.v1.0.jar"]