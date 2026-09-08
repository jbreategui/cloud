FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

COPY .mvn/ .mvn/

COPY pom.xml mvnw ./

RUN chmod +x mvnw

COPY src/ ./src/

RUN ./mvnw clean package -DskipTests


FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/cloud-0.0.1-SNAPSHOT.jar cloud.jar

EXPOSE 8080


ENTRYPOINT ["java", "-jar", "cloud.jar"]
