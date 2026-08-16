# syntax=docker/dockerfile:1
ARG JAVA_IMAGE=eclipse-temurin:25-jdk-noble@sha256:04a6f35ad3131b2747ae2c37940c5baf50536af5f28eda514847c31db2fed78b
ARG KUBECTL_VERSION=v1.28.15
ARG KUBECTL_SHA256=1f7651ad0b50ef4561aa82e77f3ad06599b5e6b0b2a5fb6c4f474d95a77e41c5

FROM ${JAVA_IMAGE} AS kubectl-downloader
ARG KUBECTL_VERSION
ARG KUBECTL_SHA256
RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && curl --fail --location --silent --show-error \
      "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
      --output /kubectl \
    && printf '%s  %s\n' "${KUBECTL_SHA256}" /kubectl | sha256sum --check --strict \
    && chmod 0555 /kubectl

FROM ${JAVA_IMAGE} AS cache-builder
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
      bash ca-certificates coreutils findutils git python3 python3-pip \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 65532 gate \
    && useradd --uid 65532 --gid 65532 --no-create-home --home-dir /tmp/gate-home gate \
    && mkdir -p /opt/jstore-gate /tmp/gate-home \
    && chown 65532:65532 /opt/jstore-gate /tmp/gate-home
COPY --chown=65532:65532 . /build/j-store
WORKDIR /build/j-store
RUN --mount=type=cache,id=jstore-gate-pip,target=/root/.cache/pip \
    --mount=type=cache,id=jstore-gate-gradle-9.4.0,target=/var/cache/jstore-gradle \
    python3 -m pip install --break-system-packages \
      --requirement requirements-quality.txt --target /opt/jstore-gate/python \
    && chown -R 65532:65532 /opt/jstore-gate/python /var/cache/jstore-gradle \
    && find . -type f -print | LC_ALL=C sort > /tmp/repository-files \
    && JSTORE_REPOSITORY_FILES_FILE=/tmp/repository-files \
      bash deploy/kubernetes/agentic-cicd/image/write-spotless-targets.sh \
      /tmp/spotless-targets \
    && chown 65532:65532 /tmp/spotless-targets \
    && su --shell /bin/bash gate --command \
      'HOME=/tmp/gate-home \
       ORG_GRADLE_PROJECT_spotlessFilesFile=/tmp/spotless-targets \
       GRADLE_USER_HOME=/var/cache/jstore-gradle \
       ./gradlew spotlessCheck verifyDependencyResolution licensee test \
         verifyLicenseArtifacts --no-daemon --console=plain' \
    && mkdir -p /opt/jstore-gate/gradle-home \
    && cp -a /var/cache/jstore-gradle/wrapper /opt/jstore-gate/gradle-home/ \
    && mkdir -p /opt/jstore-gate/gradle-home/caches \
    && cp -a /var/cache/jstore-gradle/caches/modules-2 \
      /opt/jstore-gate/gradle-home/caches/ \
    && mkdir -p /opt/jstore-gate/gradle-home/init.d \
    && printf '%s\n' 'gradle.startParameter.offline = true' \
      > /opt/jstore-gate/gradle-home/init.d/offline.gradle \
    && find /opt/jstore-gate/gradle-home -type f \
      \( -name '*.lock' -o -name 'gc.properties' \) -delete

FROM ${JAVA_IMAGE}
ARG JSTORE_CONTROLLER_REVISION=unknown
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
      bash ca-certificates coreutils findutils git python3 ripgrep \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 65532 gate \
    && useradd --uid 65532 --gid 65532 --no-create-home --home-dir /tmp/gate-home gate \
    && mkdir -p /opt/jstore-gate
COPY --from=cache-builder /opt/jstore-gate/python /opt/jstore-gate/python
COPY --from=cache-builder /opt/jstore-gate/gradle-home /opt/jstore-gate/gradle-home
COPY --from=kubectl-downloader /kubectl /usr/local/bin/kubectl
COPY deploy/kubernetes/agentic-cicd/image/run-quality-gate.sh /opt/jstore-gate/run-quality-gate
COPY deploy/kubernetes/agentic-cicd/image/write-spotless-targets.sh /opt/jstore-gate/write-spotless-targets
COPY scripts/agentic_cicd/gate_fetch.py /opt/jstore-gate/fetch-candidate.py
COPY scripts/agentic_cicd/network_probe.py /opt/jstore-gate/network-probe.py
COPY scripts/quality-gate.sh /opt/jstore-gate/trusted/quality-gate.sh
COPY scripts/check-agent-governance.sh /opt/jstore-gate/trusted/check-agent-governance.sh
COPY scripts/check-agentic-cicd.py /opt/jstore-gate/trusted/check-agentic-cicd.py
COPY scripts/check-file-ownership.py /opt/jstore-gate/trusted/check-file-ownership.py
COPY scripts/repository_files.py /opt/jstore-gate/trusted/repository_files.py
COPY scripts/agentic_cicd/__init__.py scripts/agentic_cicd/capabilities.py /opt/jstore-gate/trusted/agentic_cicd/
RUN chmod 0555 /opt/jstore-gate/run-quality-gate \
    && chmod 0555 /opt/jstore-gate/write-spotless-targets \
    && chmod 0444 /opt/jstore-gate/fetch-candidate.py \
    && chmod 0444 /opt/jstore-gate/network-probe.py \
    && chmod 0555 /opt/jstore-gate/trusted/*.sh \
    && chmod 0444 /opt/jstore-gate/trusted/*.py \
    && chmod 0444 /opt/jstore-gate/trusted/agentic_cicd/*.py \
    && chmod -R a-w /opt/jstore-gate
ENV PYTHONPATH=/opt/jstore-gate/python \
    HOME=/tmp/gate-home
LABEL org.opencontainers.image.source="https://github.com/pansf/j-store" \
      org.opencontainers.image.revision="${JSTORE_CONTROLLER_REVISION}" \
      io.jstore.image.role="isolated-gate-runner"
USER 65532:65532
WORKDIR /workspace/source
ENTRYPOINT ["/opt/jstore-gate/run-quality-gate"]
