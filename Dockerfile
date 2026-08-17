# syntax=docker/dockerfile:1

# Build the Spring Boot executable JAR with the Maven wrapper supplied by the repo.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --create-home spring
COPY --from=build /workspace/target/*.jar /app/app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
