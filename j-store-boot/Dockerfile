FROM amazoncorretto:21.0.8-al2023-headless
VOLUME /tmp
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
