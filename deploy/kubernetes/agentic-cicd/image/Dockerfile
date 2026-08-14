ARG ELIXIR_IMAGE=hexpm/elixir:1.19.5-erlang-28.3-debian-bookworm-20260202-slim@sha256:09279250196a9ad971ebe4673ec2df47bc760c0409a055df8ea283954ac6a099
ARG NODE_IMAGE=node:22-bookworm-slim@sha256:d649c27dae7ba0137b3cef5dd75baa422c08dc3d9e3fc0c23dfb172dc3cc6436

FROM ${NODE_IMAGE} AS codex
ARG CODEX_VERSION=0.146.0
RUN npm install --global "@openai/codex@${CODEX_VERSION}" \
    && codex --version | grep -Fx "codex-cli ${CODEX_VERSION}"

FROM ${ELIXIR_IMAGE} AS builder
RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates build-essential git \
    && rm -rf /var/lib/apt/lists/*
COPY --from=symphony-source /elixir /build/symphony/elixir
COPY deploy/kubernetes/agentic-cicd/patches/symphony-phase-bridge.patch /tmp/symphony-phase-bridge.patch
COPY deploy/kubernetes/agentic-cicd/patches/symphony-phase-routing.patch /tmp/symphony-phase-routing.patch
WORKDIR /build/symphony
RUN git apply --recount --check /tmp/symphony-phase-bridge.patch \
    && git apply --recount /tmp/symphony-phase-bridge.patch \
    && git apply --recount --check /tmp/symphony-phase-routing.patch \
    && git apply --recount /tmp/symphony-phase-routing.patch
WORKDIR /build/symphony/elixir
ENV MIX_ENV=prod
RUN mix local.hex --force \
    && mix local.rebar --force \
    && mix deps.get --only prod \
    && mix escript.build \
    && test -x bin/symphony

FROM ${ELIXIR_IMAGE}
ARG CODEX_VERSION=0.146.0
ARG SYMPHONY_COMMIT=8001b52e3062495a16e520e4ceaf8f9de868c4d0
ARG JSTORE_CONTROLLER_REVISION=unknown
RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates git openssh-client bash curl python3 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 symphony \
    && useradd --uid 10001 --gid 10001 --no-create-home --home-dir /var/lib/symphony symphony \
    && mkdir -p /var/lib/symphony /etc/symphony \
    && chown -R 10001:10001 /var/lib/symphony /etc/symphony
COPY --from=codex /usr/local/bin/node /usr/local/bin/node
COPY --from=codex /usr/local/lib/node_modules/@openai/codex /usr/local/lib/node_modules/@openai/codex
RUN ln -s /usr/local/lib/node_modules/@openai/codex/bin/codex.js /usr/local/bin/codex \
    && codex --version | grep -Fx "codex-cli ${CODEX_VERSION}"
COPY --from=builder /build/symphony/elixir/bin/symphony /usr/local/bin/symphony
COPY scripts/agentic_cicd /opt/jstore-agentic-controller/agentic_cicd
COPY scripts/agentic-cicd-controller.py /opt/jstore-agentic-controller/controller.py
COPY config/agentic-cicd/state-contract.json /opt/jstore-agentic-controller/state-contract.json
RUN printf 'SYMPHONY_REVISION=%s\nJSTORE_CONTROLLER_REVISION=%s\n' \
      "$SYMPHONY_COMMIT" "$JSTORE_CONTROLLER_REVISION" \
      > /opt/jstore-agentic-controller/runtime-revisions
RUN symphony --help >/dev/null 2>&1 || test "$?" -eq 1
LABEL org.opencontainers.image.source="https://github.com/openai/symphony" \
      org.opencontainers.image.revision="${SYMPHONY_COMMIT}" \
      io.jstore.controller.revision="${JSTORE_CONTROLLER_REVISION}" \
      io.jstore.codex.version="${CODEX_VERSION}"
USER 10001:10001
WORKDIR /var/lib/symphony
ENTRYPOINT ["/usr/local/bin/symphony"]
