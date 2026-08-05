FROM amazoncorretto:25-al2023-headless
VOLUME /tmp
COPY build/libs/*.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app.jar"]
