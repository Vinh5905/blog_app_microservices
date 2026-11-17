# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-jammy@sha256:60fcdd4a85c94a23ef86d0d55867c6a8dbc4f7c3efc8286e259956c3ea4ef08c AS dependencies
ADD https://maven-central.storage-download.googleapis.com/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz /tmp/maven.tar.gz
RUN echo 'a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b  /tmp/maven.tar.gz' | sha512sum -c - \
    && tar -xzf /tmp/maven.tar.gz -C /opt
WORKDIR /build
COPY .github/maven-ci-settings.xml settings.xml
COPY migrations/pom.xml pom.xml
RUN /opt/apache-maven-3.9.9/bin/mvn -B -ntp --strict-checksums --settings settings.xml \
    org.apache.maven.plugins:maven-dependency-plugin:3.9.0:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=/flyway/lib

FROM eclipse-temurin:17-jre-jammy@sha256:aa6381384f8db99762fa7d0d3c2b5de58874e5f12c96df4c13a1455376ae0ed1 AS flyway-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade \
        apt libapt-pkg6.0 libbrotli1 libssl3 openssl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /flyway
COPY --from=dependencies /flyway/lib/ /flyway/lib/
ADD --checksum=sha256:d77962877d010777cff997015da90ee689f0f4bb76848340e1488f2b83332af5 --chmod=0444 \
    https://maven-central.storage-download.googleapis.com/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar \
    /flyway/lib/mysql-connector-j-8.4.0.jar
ENV FLYWAY_LOCATIONS=filesystem:/flyway/sql \
    FLYWAY_CLEAN_DISABLED=true

USER 10001:10001
ENTRYPOINT ["java", "-cp", "/flyway/lib/*", "org.flywaydb.commandline.Main"]
CMD ["migrate"]

FROM flyway-base AS userauthservice
COPY userauthservice/src/main/resources/db/migration/user/ /flyway/sql/

FROM flyway-base AS postservice
COPY postservice/src/main/resources/db/migration/post/ /flyway/sql/

FROM flyway-base AS commentservice
COPY commentservice/src/main/resources/db/migration/comment/ /flyway/sql/
