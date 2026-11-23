FROM eclipse-temurin:25-jre
ARG MODULE
COPY ${MODULE}/target/app.jar /app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app.jar"]
