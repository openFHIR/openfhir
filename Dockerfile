# base images pinned by digest; Dependabot (.github/dependabot.yml) opens PRs that bump them
FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS builder
WORKDIR /build
COPY . .
RUN mvn -B -q clean package -DskipTests=true

FROM eclipse-temurin:21-jre@sha256:d77e090ae1f6970c047b443c6f0a6c1358b0426d44432bb5a7753408cd43de0d
WORKDIR /app
COPY --from=builder /build/engine/target/open-fhir-engine-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
