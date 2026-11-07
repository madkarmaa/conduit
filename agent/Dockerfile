# The image holds Node.js, Bun and the runner. The runner installs the agent CLI into the home volume
# on first use, so the image stays small and the CLI can update without a new image.
FROM oven/bun:1.4.4-slim AS bun

FROM node:24-bookworm-slim

# The slim image has no CA certificates, which CLIs that use the system's store need for HTTPS.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --create-home --home-dir /home/agent agent

# Bun runs the runner's TypeScript as it is. The agent CLIs and npm run on Node.js.
COPY --from=bun /usr/local/bin/bun /usr/local/bin/bun

WORKDIR /opt/conduit
COPY package.json bun.lock ./
# The SDKs drive the CLIs installed in the home volume, so their own bundled CLIs stay out.
RUN bun install --frozen-lockfile --production --omit optional && rm -rf /root/.bun
COPY src ./src
COPY conduit-login /usr/local/bin/conduit-login

ENV HOME=/home/agent \
    NPM_CONFIG_PREFIX=/home/agent/.npm-global \
    NPM_CONFIG_UPDATE_NOTIFIER=false \
    NPM_CONFIG_FUND=false \
    PATH=/home/agent/.npm-global/bin:$PATH

# The runner starts rarely, so Bun's transpiler cache would only collect files in the home volume.
ENV BUN_RUNTIME_TRANSPILER_CACHE_PATH=0

USER agent
WORKDIR /home/agent
VOLUME /home/agent
EXPOSE 8080

CMD ["bun", "/opt/conduit/src/runner.ts"]
