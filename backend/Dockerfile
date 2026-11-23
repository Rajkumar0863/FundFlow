FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system fundflow && \
    useradd --system --gid fundflow fundflow

COPY --from=build /app/target/*.jar app.jar

USER fundflow

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]