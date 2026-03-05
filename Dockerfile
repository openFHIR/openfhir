FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY . .
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/engine/target/open-fhir-engine-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
