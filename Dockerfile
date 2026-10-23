# Build:  docker build -t newerp .
# Run:    docker run -p 8080:8080 -e NEWERP_DB_URL=... -e NEWERP_DB_USER=... -e NEWERP_DB_PASSWORD=... newerp
# Two stages: the JDK image builds the jar, only the jar goes into the smaller JRE image.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
# Tests need Docker themselves (Testcontainers), so they run in CI, not here.
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home newerp
WORKDIR /app
COPY --from=build /src/newerp-api/target/newerp.jar newerp.jar
RUN mkdir logs && chown newerp logs
USER newerp
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "newerp.jar"]
