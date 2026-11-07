# The image holds Bun and the runner, and no Node.js. Bun runs the runner's TypeScript as it is,
# and installs the agent CLI into the home volume on first use, so the CLI can update without a new
# image. The image's node command is Bun as well, which runs the CLIs' Node.js launchers.
FROM oven/bun:1.4.4-slim

RUN useradd --uid 10001 --create-home --home-dir /home/agent agent

WORKDIR /opt/conduit
COPY package.json bun.lock ./
# The SDKs drive the CLIs installed in the home volume, so their own bundled CLIs stay out.
RUN bun install --frozen-lockfile --production --omit optional && rm -rf /root/.bun
COPY src ./src
COPY conduit-login /usr/local/bin/conduit-login

# The base image installs global packages into /usr/local, which the read-only root filesystem
# keeps the runner from writing.
ENV HOME=/home/agent \
    BUN_INSTALL=/home/agent/.bun \
    BUN_INSTALL_BIN=/home/agent/.bun/bin \
    PATH=/home/agent/.bun/bin:$PATH

USER agent
WORKDIR /home/agent
VOLUME /home/agent
EXPOSE 8080

CMD ["bun", "/opt/conduit/src/runner.ts"]
