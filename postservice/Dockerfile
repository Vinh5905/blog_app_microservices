FROM eclipse-temurin:17-jdk-jammy@sha256:60fcdd4a85c94a23ef86d0d55867c6a8dbc4f7c3efc8286e259956c3ea4ef08c AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp clean package && cp target/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy@sha256:97137382c6f0c30427d9b7c44ad8b2d55ac823b0768c171d81a643ed219023c5
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
