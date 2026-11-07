<div align="center">
    <img src="docs/conduit.gif" alt="Conduit" />
    <h1>Conduit</h1>
</div>

An AI bot for Minecraft servers. Players ask it with `/conduit <request>`, and an AI agent answers
in the chat, using tools that read and change the game. Operators can let it build, fill, teleport,
edit inventories and run commands, while everyone else can ask questions about the world and their
own player.

Conduit is a server-side Fabric mod. Players do not need it to join. The agents run in two Docker
containers next to the server, one for operators and one for everyone else, and use the agent CLI
you pick: [Claude Code](https://www.anthropic.com/claude-code),
[Codex](https://github.com/openai/codex) or [OpenCode](https://opencode.ai). The mod also serves
its tools over [MCP](https://modelcontextprotocol.io), so other MCP hosts can control the game too.

Each [GitHub release](https://github.com/madkarmaa/conduit/releases) has the mod jar, and the agent
image is `ghcr.io/madkarmaa/conduit-agent`.

## Requirements

- The Minecraft and Fabric Loader versions set in [`gradle.properties`](./gradle.properties), and
  the Java version that Minecraft version requires
- [Fabric API](https://modrinth.com/mod/fabric-api) and
  [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- Docker with Compose, for the agents
- An account or API key for the agent CLI

## Setup

1. Put the mod jar in the server's `mods` folder.

2. Copy [`agent/compose.yaml`](./agent/compose.yaml) to the server, for example as
   `conduit-agents.yaml`, and create a `.env` file next to it with two random secrets. Each needs
   at least 16 letters, digits or `-._~+/` characters, such as the output of `openssl rand -hex 24`:

    ```sh
    CONDUIT_FULL_SECRET=<random string>
    CONDUIT_CHAT_SECRET=<another random string>
    ```

3. Start the agents. They restart on their own, and Conduit never starts or stops them.

    ```sh
    docker compose -f conduit-agents.yaml up -d
    ```

4. Log each container in to its CLI once. The CLI keeps the login in the container's volume, so it
   stays when Compose recreates the container.

    ```sh
    docker compose -f conduit-agents.yaml exec -it conduit-full conduit-login claude
    docker compose -f conduit-agents.yaml exec -it conduit-chat conduit-login opencode
    ```

    `conduit-login` installs the CLI first. Claude Code starts its interactive login, where `/login`
    signs in. Codex uses a device code, and OpenCode asks for a provider and its key.

5. Give the Minecraft server the same two secrets as environment variables, and start it. On the
   first start, the mod writes `config/conduit.json`. Point it at the agents as the
   [networking](#networking) section describes, then run `/conduit reload`.

The agent containers install their CLI on first use and update it once a day while idle. Until the
first install finishes, the bot answers requests with "try again in a few minutes".

## Networking

The mod reaches the runner in each agent container, and the CLI in the container reaches the mod's
MCP server. Pick the setup that matches the server.

### Minecraft in Docker Compose

If the server runs in Docker Compose, such as with
[itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server), start the agents in the
same project as the server, so they share its network.

```sh
docker compose -f compose.yaml -f conduit-agents.yaml up -d
```

The defaults fit a server service named `minecraft`. For another name, change both `mcpUrl`
values and add the name to `mcp.allowedHosts`.

### Minecraft on the host

The runners listen on `127.0.0.1:8081` and `127.0.0.1:8082`, and the containers reach the host as
`host.docker.internal`. Point the config at them:

```json
{
    "agents": {
        "full": {
            "runner": { "url": "http://127.0.0.1:8081" },
            "mcpUrl": "http://host.docker.internal:25585/mcp"
        },
        "chat": {
            "runner": { "url": "http://127.0.0.1:8082" },
            "mcpUrl": "http://host.docker.internal:25585/mcp"
        }
    }
}
```

### Resource limits

Each agent container may use 1 CPU. `conduit-full` gets 768 MiB of memory and `conduit-chat` 1 GiB.
Set `CONDUIT_FULL_MEMORY`, `CONDUIT_CHAT_MEMORY`, `CONDUIT_FULL_CPUS` or `CONDUIT_CHAT_CPUS` in
`.env` to change these limits.

## Using the bot

`/conduit <request>` asks the bot, such as `/conduit build a small oak cabin next to me`. The bot
posts the question and the answer in the public chat, like a player talking. While a request waits
or runs, the asker's action bar shows how many requests are ahead and then "Thinking…".

Operators and the players in `access.allow` get **full mode**, where the agent can change the game.
Everyone else gets **chat mode**, where the agent can only read the world and the asking player's
own data, such as their inventory, position and statistics, but never another player's data or an
IP address. Each mode has a line of its own, so operators never wait behind chat mode. The bot
answers one request at a time per mode, and each player can have one request in line.

The agent remembers earlier requests. In chat mode every player has a conversation of their own.
In full mode, `requests.memory` picks one conversation per player, one `shared` by everyone, or
`none`.

### Approvals

Before the agent runs an action that is hard to undo, such as `run_command`, `edit_blocks` `fill`
or `edit_player` `kick`, the asker and every online operator get the action with its arguments and
an `[Approve]` and a `[Deny]` button. A click from the asker or an operator decides. When nobody
answers within `approvals.timeoutSeconds`, the action does not run. Either way the agent learns what
happened, and the time spent waiting does not count against the request's timeout.

`approvals.add` and `approvals.remove` change which actions need approval. Each entry is a tool,
such as `edit_player`, or one of its actions, such as `edit_player.game_mode`.

### Operator commands

| Command                                   | Description                                                   |
| ----------------------------------------- | ------------------------------------------------------------- |
| `/conduit status`                         | Each mode's agent, its CLI version and its line               |
| `/conduit cancel [full\|chat\|<player>]`  | Stops a mode's running request, or a player's request         |
| `/conduit reload`                         | Reloads the config, and keeps the old one if the new is bad   |
| `/conduit forget [<player>\|shared\|all]` | Starts fresh conversations, so the agent forgets earlier ones |

Without an argument, `cancel` and `forget` act on the operator's own request and conversations.

An operator's request runs a command when it is one of these words alone, or `cancel` or `forget`
followed by one more word. Longer requests that start with these words, such as
`/conduit status of the farm`, go to the bot. Requests from other players always go to the bot.

## Configuration

The mod reads `config/conduit.json` at startup and on `/conduit reload`. Fields the file leaves out
keep their defaults. Any string can use `${NAME}` or `${NAME:-default}` to read an environment
variable. The variables of a disabled agent may stay unset.

When the config is invalid at startup, the mod logs the missing variables and invalid fields, and
keeps the bot and the MCP server off until `/conduit reload` loads a valid config. On a reload, the
command lists the problems and keeps the old config.

| Field                      | Default                                                       | Description                                                           |
| -------------------------- | ------------------------------------------------------------- | --------------------------------------------------------------------- |
| `mcp.host`                 | `0.0.0.0`                                                     | Address the MCP server listens on                                     |
| `mcp.port`                 | `25585`                                                       | Port of the MCP server                                                |
| `mcp.allowedHosts`         | `minecraft`, `host.docker.internal`, `localhost`, `127.0.0.1` | Host names clients may use to reach the MCP server                    |
| `mcp.tokens`               | none                                                          | Tokens for other [MCP hosts](#other-mcp-hosts)                        |
| `agents.<mode>.enabled`    | `true`                                                        | Turns the mode on                                                     |
| `agents.<mode>.cli`        | `claude` for full, `opencode` for chat                        | `claude`, `codex` or `opencode`                                       |
| `agents.<mode>.model`      | `sonnet` for full, `opencode-go/deepseek-v4.1-flash` for chat | The model, as the CLI names it                                        |
| `agents.<mode>.effort`     | `medium` for full, `high` for chat                            | The reasoning effort, as the CLI names it                             |
| `agents.<mode>.args`       | none                                                          | Extra arguments for the CLI                                           |
| `agents.<mode>.web`        | `true` for full, `false` for chat                             | Lets the CLI search and fetch the web                                 |
| `agents.<mode>.runner`     | `http://conduit-<mode>:8080`, `${CONDUIT_<MODE>_SECRET}`      | `url` and `secret` of the mode's agent container                      |
| `agents.<mode>.mcpUrl`     | `http://minecraft:25585/mcp`                                  | Where the agent container reaches the MCP server                      |
| `access.operators`         | `true`                                                        | Operators get full mode                                               |
| `access.allow`             | none                                                          | Names or UUIDs of other players who get full mode                     |
| `tools.readOnly`           | `false`                                                       | Full mode only gets the actions that read                             |
| `tools.chat.allow`         | the chat tools below                                          | The only tools chat mode gets                                         |
| `tools.chat.deny`          | none                                                          | Tools chat mode never gets. Without `allow`, it gets every other tool |
| `approvals.timeoutSeconds` | `30`                                                          | How long an approval waits for a click                                |
| `approvals.add`            | none                                                          | Tools or actions that also need approval                              |
| `approvals.remove`         | none                                                          | Tools or actions that never need approval                             |
| `requests.timeoutSeconds`  | `300`                                                         | How long a request may run, not counting approvals                    |
| `requests.memory`          | `player`                                                      | Full mode conversations: `player`, `shared` or `none`                 |
| `requests.instructions`    | none                                                          | Extra instructions for the agent, such as the server's rules          |
| `replies.maxLength`        | `1000`                                                        | Longest answer the bot posts                                          |
| `game.backupDirectory`     | `backups`                                                     | Where backups go, relative to the server folder                       |
| `game.maxStructureVolume`  | `8000000`                                                     | Largest structure the tools save or place, in blocks                  |

Chat mode gets `server_info`, `world_info`, `game_rules`, `get_block`, `get_region`, `terrain`,
`registry`, `recipes`, `structures` and `get_player` by default, and never the tools that list
other players or entities.

## Other MCP hosts

Claude Code, Cursor and other MCP hosts can use the tools too. The MCP server speaks streamable
HTTP at `http://<server>:25585/mcp` and needs a token from `mcp.tokens`:

```json
{
    "mcp": {
        "tokens": [
            {
                "name": "desktop",
                "token": "${CONDUIT_DESKTOP_TOKEN}",
                "readOnly": false,
                "allow": [],
                "deny": []
            }
        ]
    }
}
```

`readOnly` offers only the actions that read, `allow` limits the token to those tools, and `deny`
leaves tools out. Hosts outside the game never wait for approvals. For Claude Code:

```sh
claude mcp add --transport http conduit http://localhost:25585/mcp \
    --header "Authorization: Bearer <token>"
```

The host name in the URL must be in `mcp.allowedHosts`.

The server has 30 tools. Tools with several operations take an `action` argument.

| Area      | Read                                  | Write                                                               |
| --------- | ------------------------------------- | ------------------------------------------------------------------- |
| Server    | `server_info`                         | `run_command`, `server_admin`                                       |
| Blocks    | `get_block`, `get_region`             | `edit_blocks`                                                       |
| World     | `terrain`, `world_info`, `game_rules` | `world_settings`, `world_border`, `game_rules`, `explode`           |
| Entities  | `list_entities`, `get_entity`         | `spawn_entity`, `remove_entity`, `move_entity`, `edit_entity`       |
| Players   | `list_players`, `get_player`          | `inventory`, `give_item`, `offline_player`, `edit_player`, `notify` |
| Game data | `registry`, `recipes`                 |                                                                     |
| Other     | `structures`, `wait_for_events`       | `structures`, `batch`                                               |

The actions that are hard to undo are `run_command`, `server_admin` `stop` and `set_datapack`,
`edit_blocks` `fill`, `explode`, `remove_entity`, `inventory` `clear`, `offline_player`,
`edit_player` `operator` and `kick`, `structures` `place`, `game_rules` `set` and `world_border`
`set`. Their tools carry the MCP `destructiveHint`. Each tool's `_meta` also maps every action to
`read`, `write` or `destructive` under `top.madkarma.conduit/actions`, so a host can ask for
confirmation before the destructive ones. `batch` runs calls to other tools within one server tick.

## Security

- Anyone in full mode controls the agent and every tool it has, including `run_command`. Keep
  `access.allow` short, or turn on `tools.readOnly`.
- The MCP server listens on every interface by default, so the agent containers can reach it.
  Every request needs a token, but keep port 25585 closed to the internet. Each agent request gets
  a token of its own that only works while the request runs.
- The runner secrets and the MCP tokens never show up in the log or in `/conduit status`.
- The agent containers have a read-only root filesystem, no Linux capabilities, and limits on
  memory, CPU and processes. They can reach the internet, because the CLI needs its model's API.
  `agents.<mode>.web` decides whether the agent may also search and fetch the web. The runner turns
  off the CLI's own tools, such as shell commands and file edits.
- A modified client can send any click. Every approval button therefore carries a random value that
  only the players who got the prompt know, and the mod only counts clicks from the asker and
  operators.

## Building

Building needs JDK 25 and [Bun](https://bun.sh) on the `PATH`. The build uses Bun to format the
Markdown, JSON, YAML and JavaScript files.

```sh
./gradlew build
```

The build writes the mod jar to `build/libs/conduit-<mod version>+<minecraft version>.jar`. To
build the agent image, run `docker build -t conduit-agent agent/` and set
`CONDUIT_AGENT_IMAGE=conduit-agent` for the compose file.

The runner in `agent/` is TypeScript that Bun runs without a build step. Bun does not check types,
so run `bun install` and `bun run check` in `agent/` after changing it.

`bun install` sets up two Git hooks. The pre-commit hook runs `./gradlew spotlessApply` and adds
the formatted files to the commit. The commit-msg hook checks that the commit message follows
[Conventional Commits](https://www.conventionalcommits.org).

## Test environment

[`compose.yaml`](./compose.yaml) runs a Fabric server with the mod from `build/libs` and a vanilla
headless client as a real player, using the [HeadlessMC](https://github.com/headlesshq/headlessmc)
Docker image. `MC_VERSION` is required and must match the Minecraft version in `gradle.properties`:

```sh
./gradlew build
MC_VERSION=<minecraft version> docker compose up -d server   # server only
MC_VERSION=<minecraft version> docker compose up -d          # server and a client joining it
```

The MCP server is at `http://localhost:25585/mcp` with the token `dev-token`, unless
`CONDUIT_TEST_TOKEN` sets another. RCON on `localhost:25575` runs console commands, such as
`execute as Offline run conduit hello`, with the password `dev-rcon`, unless `RCON_PASSWORD` sets
another. To test the bot, add the agent containers with their secrets:

```sh
CONDUIT_FULL_SECRET=<secret> CONDUIT_CHAT_SECRET=<secret> MC_VERSION=<minecraft version> \
    docker compose -f compose.yaml -f agent/compose.yaml up -d
```

Docker volumes keep the game data and the agents' logins between runs. The client starts with
[`docker/options.txt`](./docker/options.txt), so it skips the onboarding screens of a first start
and joins the server right away.

## License

CC0-1.0, see [LICENSE](./LICENSE).
