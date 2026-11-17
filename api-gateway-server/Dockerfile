FROM eclipse-temurin:17-jdk-jammy@sha256:60fcdd4a85c94a23ef86d0d55867c6a8dbc4f7c3efc8286e259956c3ea4ef08c AS build
WORKDIR /workspace
ENV MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp --settings .mvn/settings.xml dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp --settings .mvn/settings.xml clean package && cp target/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy@sha256:aa6381384f8db99762fa7d0d3c2b5de58874e5f12c96df4c13a1455376ae0ed1
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade \
        apt libapt-pkg6.0 libbrotli1 libssl3 openssl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
