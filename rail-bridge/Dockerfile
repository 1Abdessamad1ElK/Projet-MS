# rail-bridge : Java 8 (JacORB). Contexte de build = RACINE du depot (compose le configure),
# car il faut l'IDL fourni : rail-operator-corba/src/main/idl/rail.idl
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app/rail-bridge
COPY rail-operator-corba/src/main/idl /app/rail-operator-corba/src/main/idl
COPY rail-bridge/pom.xml .
RUN mvn -q -B -e dependency:go-offline || true
COPY rail-bridge/src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY --from=build /app/rail-bridge/target/app.jar app.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
