FROM maven:3-eclipse-temurin-26 AS build

WORKDIR /workspace
COPY backend-spring/pom.xml backend-spring/pom.xml
RUN mvn -q -f backend-spring/pom.xml -DskipTests dependency:go-offline

COPY backend-spring backend-spring
RUN mvn -q -f backend-spring/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/backend-spring/target/*.jar /app/app.jar

ENV SHIPCAD_STORAGE_ROOT=/app/data
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
