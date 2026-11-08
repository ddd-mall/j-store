FROM registry.access.redhat.com/ubi10/openjdk-25-runtime:1.24-13@sha256:68525bc239f93a62070625354e3b863be0963f61f1338794011665d5b8a946f5
VOLUME /tmp
COPY build/libs/app.jar /app.jar
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.io.tmpdir=/tmp", "-jar", "/app.jar"]
