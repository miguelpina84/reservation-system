# Imagen multi-stage: compila con Maven y ejecuta sobre la JRE 21 de Liberica.
# La imagen de runtime ya esta disponible en el registro local; la de build se descarga
# de Docker Hub (si la red no lo permite, usa Dockerfile.offline con el jar ya compilado).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM bellsoft/liberica-openjdk-alpine:21.0.1-12-cds-x86_64 AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/reservation-system-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
