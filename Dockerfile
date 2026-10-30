# base images pinned by digest; Dependabot (.github/dependabot.yml) opens PRs that bump them
FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS builder
WORKDIR /build
COPY . .
RUN mvn -B -q clean package -DskipTests=true

FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
WORKDIR /app
COPY --from=builder /build/engine/target/open-fhir-engine-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
