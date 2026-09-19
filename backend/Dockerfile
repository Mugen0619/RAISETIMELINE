# syntax=docker/dockerfile:1

# --- build stage ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test -x checkstyleMain -x checkstyleTest

# --- runtime stage ---
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin appuser
COPY --from=build /workspace/build/libs/*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
