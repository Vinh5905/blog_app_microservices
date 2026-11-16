# syntax=docker/dockerfile:1
FROM flyway/flyway:11.7.2@sha256:8ace7d9825bb3ad1d6e14ee27b3a830b638ac841ba424b99b2d92aa65a99d484 AS flyway-base

ADD --checksum=sha256:d77962877d010777cff997015da90ee689f0f4bb76848340e1488f2b83332af5 --chmod=0444 \
    https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar \
    /flyway/drivers/mysql-connector-j-8.4.0.jar

ENV FLYWAY_LOCATIONS=filesystem:/flyway/sql \
    FLYWAY_CLEAN_DISABLED=true

USER 10001:10001
CMD ["migrate"]

FROM flyway-base AS userauthservice
COPY userauthservice/src/main/resources/db/migration/user/ /flyway/sql/

FROM flyway-base AS postservice
COPY postservice/src/main/resources/db/migration/post/ /flyway/sql/

FROM flyway-base AS commentservice
COPY commentservice/src/main/resources/db/migration/comment/ /flyway/sql/