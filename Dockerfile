FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY target/cloud-0.0.1-SNAPSHOT.jar .

EXPOSE 8080


ENTRYPOINT ["java", "-jar", "cloud-0.0.1-SNAPSHOT.jar"]