# The image holds Node.js and the runner. The runner installs the agent CLI into the home volume
# on first use, so the image stays small and the CLI can update without a new image.
FROM node:24-bookworm-slim

# The slim image has no CA certificates, which CLIs that use the system's store need for HTTPS.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --create-home --home-dir /home/agent agent

ENV HOME=/home/agent \
    NPM_CONFIG_PREFIX=/home/agent/.npm-global \
    NPM_CONFIG_UPDATE_NOTIFIER=false \
    NPM_CONFIG_FUND=false \
    PATH=/home/agent/.npm-global/bin:$PATH

COPY runner.mjs presets.mjs /opt/conduit/
COPY conduit-login /usr/local/bin/conduit-login

USER agent
WORKDIR /home/agent
VOLUME /home/agent
EXPOSE 8080

CMD ["node", "/opt/conduit/runner.mjs"]
