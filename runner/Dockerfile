FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S runner && adduser -S runner -G runner && mkdir -p /data && chown runner:runner /data
USER runner
WORKDIR /app
COPY --from=build /workspace/target/joyhub-deployment-runner-0.1.0.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
