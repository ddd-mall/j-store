# syntax=docker/dockerfile:1
ARG JAVA_IMAGE=eclipse-temurin:25-jdk-noble@sha256:04a6f35ad3131b2747ae2c37940c5baf50536af5f28eda514847c31db2fed78b

FROM ${JAVA_IMAGE}
RUN --mount=type=bind,from=gradle-seed,target=/seed,ro \
    --mount=type=cache,id=jstore-gate-gradle-9.4.0,target=/root/.gradle \
    cp -a /seed/. /root/.gradle/ \
    && chown -R 65532:65532 /root/.gradle
