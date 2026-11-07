FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q verify

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --gid 10001 kkd && useradd --uid 10001 --gid kkd --no-create-home kkd
WORKDIR /app
COPY --from=build --chown=kkd:kkd /build/target/kkd-backend-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
