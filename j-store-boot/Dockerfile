FROM amazoncorretto:25-al2023-headless@sha256:f388f9e66b3ba56109683d63539ee7b65797ad35ae191753ea98435c9d5f18f5
VOLUME /tmp
COPY build/libs/app.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.io.tmpdir=/tmp", "-jar", "/app.jar"]
