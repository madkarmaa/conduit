<div align="center">
    <img src="docs/conduit.gif" alt="Conduit" />
    <h1>Conduit</h1>
</div>

A server-side Fabric mod that runs a gRPC server inside Minecraft, so external programs can control
the world, entities and players, run any command and stream live events. It runs on dedicated
servers and in singleplayer worlds. Players do not need the mod to join.

Each [GitHub release](https://github.com/madkarmaa/conduit/releases) has the mod jar. Releases also
publish the [TypeScript client](#typescript-client), the [MCP server](#mcp-server) and the
[chat bot](#chat-bot) to `https://npm.madkarma.top`, so npm needs that registry for the `@conduit`
scope:

```sh
npm config set @conduit:registry https://npm.madkarma.top
```

## Requirements

- The Minecraft and Fabric Loader versions set in [`gradle.properties`](./gradle.properties), and
  the Java version that Minecraft version requires
- [Fabric API](https://modrinth.com/mod/fabric-api) and
  [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## Building

Building needs JDK 25 and [Bun](https://bun.sh) on the `PATH`.

```sh
./gradlew build
```

The build writes the mod jar to `build/libs/conduit-<mod version>+<minecraft version>.jar`. It also
installs the Bun workspace and builds the client, the MCP server and the chat bot. To build only
those, build the client first, since the other two use it:

```sh
bun install
bun run --cwd client build
bun run --cwd mcp build
bun run --cwd bot build
```

`bun install` also sets up a pre-commit hook that runs `./gradlew spotlessApply` and adds the
formatted files to the commit.

## Configuration

On first start the mod writes `config/conduit.json`:

| Setting                   | Default                        | Description                         |
| ------------------------- | ------------------------------ | ----------------------------------- |
| `enabled`                 | `true`                         | Start the gRPC server               |
| `host`                    | `127.0.0.1`                    | Bind address                        |
| `port`                    | `25585`                        | Listen port                         |
| `requireAuth`             | `true`                         | Require a bearer token              |
| `token`                   | random, created on first start | Token expected in `authorization`   |
|                           |                                | metadata                            |
| `auditLog`                | `true`                         | Log calls that change the game      |
| `backupDirectory`         | `backups`                      | Folder for backups, relative to the |
|                           |                                | server folder (the game folder in   |
|                           |                                | singleplayer)                       |
| `maxStructureVolume`      | `8000000`                      | Largest structure, in blocks, that  |
|                           |                                | can be exported, saved or placed    |
| `maxStructureUploadBytes` | `67108864` (64 MiB)            | Largest structure file that can be  |
|                           |                                | imported                            |

An environment variable can override each setting: `CONDUIT_ENABLED`, `CONDUIT_HOST`,
`CONDUIT_PORT`, `CONDUIT_REQUIRE_AUTH`, `CONDUIT_TOKEN`, `CONDUIT_AUDIT_LOG`,
`CONDUIT_BACKUP_DIRECTORY`, `CONDUIT_MAX_STRUCTURE_VOLUME` and `CONDUIT_MAX_STRUCTURE_UPLOAD_BYTES`.

The audit log records every call that is not read-only, and every call rejected for a missing or
wrong token. It writes through the `conduit/audit` logger, so a logging configuration can route it
to its own file.

While `requireAuth` is on, every call except the standard gRPC health check needs
`authorization: Bearer <token>`.

## API

The service definitions are in [`src/main/proto/conduit/v1`](src/main/proto/conduit/v1). The server
has reflection enabled, so tools like `grpcurl` can discover the services.

| Service            | Scope                                                                 |
| ------------------ | --------------------------------------------------------------------- |
| `ServerService`    | Server info, performance, any command with captured output,           |
|                    | broadcasts, save, stop, datapacks, backups                            |
| `WorldService`     | Dimensions, blocks and regions, block entities, biomes, heightmaps,   |
|                    | chunks, time, weather, game rules, spawn, explosions, world border    |
| `EntityService`    | Query (filters or selectors), spawn, remove, teleport, velocity,      |
|                    | rotation, NBT, custom names, tags, damage, health, effects,           |
|                    | attributes, riding                                                    |
| `PlayerService`    | Inventories, items, game mode, hunger, experience, messages, titles,  |
|                    | sounds, kick, operator status, statistics, saved data of offline      |
|                    | players                                                               |
| `EventService`     | Live stream of chat, player join, leave, respawn, movement and        |
|                    | dimension change, block break, place and use, damage, death and       |
|                    | attack, command, server tick, start and stop, entity spawn and        |
|                    | removal, chunk load and unload, advancement, inventory change, item   |
|                    | pickup, drop, craft and smelt, trade, and custom click events         |
| `RegistryService`  | Registries and their entries, tags, recipes, loot tables              |
| `BatchService`     | Many world, entity, player and command operations in one call and one |
|                    | server tick                                                           |
| `StructureService` | Export, import, save and place regions with their block entities and  |
|                    | entities, in the vanilla structure file format                        |

In singleplayer the gRPC server runs while a world is open.

Entities can only spawn or be teleported into loaded chunks. Force-load the chunk with
`WorldService.SetChunkForced` or keep a player nearby.

The mod numbers the events in a subscription. A client that reads too slowly loses the oldest
buffered events, and an `events_dropped` event reports which ones were lost, so the client knows to
re-read any state it tracks.

```sh
grpcurl -plaintext -H "authorization: Bearer $TOKEN" localhost:25585 \
    conduit.v1.ServerService/GetInfo
grpcurl -plaintext -H "authorization: Bearer $TOKEN" -d '{"command": "time set day"}' \
    localhost:25585 conduit.v1.ServerService/ExecuteCommand
```

## TypeScript client

[`client/`](./client) is a typed Node.js client built with the official
[`@grpc/grpc-js`](https://www.npmjs.com/package/@grpc/grpc-js) and
[`@grpc/proto-loader`](https://www.npmjs.com/package/@grpc/proto-loader) packages. Its types come
from the mod's protos, and `./gradlew build` regenerates them. It needs Node.js 24 or later.

```sh
npm install @conduit/client
```

```ts
import { close, connect } from "@conduit/client";

const clients = connect({ host: "localhost", port: 25585, token: process.env.TOKEN });
clients.server.executeCommand({ command: "time set day" }, (error, response) => {
    console.log(error ?? response);
    close(clients);
});
```

## MCP server

[`mcp/`](./mcp) is an [MCP](https://modelcontextprotocol.io) server over stdio that lets AI agents
control the game through the mod. It needs Node.js 24 or later.

| Variable                 | Default     | Description                                          |
| ------------------------ | ----------- | ---------------------------------------------------- |
| `CONDUIT_HOST`           | `localhost` | Host of the mod's gRPC server                        |
| `CONDUIT_PORT`           | `25585`     | Port of the mod's gRPC server                        |
| `CONDUIT_TOKEN`          |             | Bearer token of the mod                              |
| `CONDUIT_MCP_READ_ONLY`  | `false`     | Only offer tools that read state                     |
| `CONDUIT_MCP_TOOLS`      |             | Only offer these tools, separated by commas          |
| `CONDUIT_MCP_DENY_TOOLS` |             | Never offer these tools, separated by commas         |
| `CONDUIT_MCP_PLAYER`     |             | Names and UUIDs of the only player the tools may see |

With `CONDUIT_MCP_PLAYER`, tools only accept that player in their player arguments. The server
leaves out the tools that list other players or entities, and removes IP addresses from results.

The server has 30 tools. Tools with several operations take an `action` argument.

| Area      | Read                                  | Write                                       |
| --------- | ------------------------------------- | ------------------------------------------- |
| Server    | `server_info`                         | `run_command`, `server_admin`               |
| Blocks    | `get_block`, `get_region`             | `edit_blocks`                               |
| World     | `terrain`, `world_info`, `game_rules` | `world_settings`, `world_border`,           |
|           |                                       | `game_rules`, `explode`                     |
| Entities  | `list_entities`, `get_entity`         | `spawn_entity`, `remove_entity`,            |
|           |                                       | `move_entity`, `edit_entity`                |
| Players   | `list_players`, `get_player`          | `inventory`, `give_item`, `offline_player`, |
|           |                                       | `edit_player`, `notify`                     |
| Game data | `registry`, `recipes`                 |                                             |
| Other     | `structures`, `wait_for_events`       | `structures`, `batch`                       |

Some write actions are hard to undo: `run_command`, `server_admin` `stop` and `set_datapack`,
`edit_blocks` `fill`, `explode`, `remove_entity`, `inventory` `clear`, `offline_player`,
`edit_player` `operator` and `kick`, `structures` `place`, `game_rules` `set` and `world_border`
`set`. Their tools have the MCP `destructiveHint`, and each tool's `_meta` maps every action to
`read`, `write` or `destructive` under `top.madkarma.conduit/actions`, so a host can ask for
confirmation. A `batch` is only destructive when it holds one of the operations listed under
`top.madkarma.conduit/destructiveOperations`.

To add the server to an MCP host:

```json
{
    "mcpServers": {
        "conduit": {
            "command": "npx",
            "args": ["-y", "@conduit/mcp"],
            "env": { "CONDUIT_TOKEN": "<token>" }
        }
    }
}
```

## Chat bot

[`bot/`](./bot) lets players talk to an AI agent in the game chat. A message that mentions
`@conduit`, such as `@conduit build a small house next to me`, goes to an agent CLI that controls
the game through the [MCP server](#mcp-server). The bot posts the agent's answer in the chat, with
colors and styles if the agent uses Minecraft's `§` codes. It needs Node.js 24 or later and
[Docker](https://www.docker.com).

```sh
CONDUIT_TOKEN=<token> npx @conduit/bot [config file]
```

The bot answers one request at a time, like one player in the chat. Requests wait in line in the
order they arrive, and the action bar tells each player how many are ahead of theirs. A player can
have one request in line at a time. Full mode has a line of its own, so operators never wait behind
chat mode.

The config file defaults to `conduit-bot.json` in the working directory. `CONDUIT_HOST`,
`CONDUIT_PORT` and `CONDUIT_TOKEN` work as they do for the MCP server.

```json
{
    "agents": {
        "full": { "agent": "claude", "model": "sonnet", "effort": "medium" },
        "chat": {
            "agent": "opencode",
            "model": "opencode-go/deepseek-v4.1-flash",
            "effort": "high"
        }
    },
    "access": { "allow": ["Steve"] }
}
```

Any string in the config can read a variable with `${NAME}`, or with `${NAME:-default}` when it has
a default. The bot does not start while a variable without a default is not set. It also loads the
`.env` file next to the config file, if there is one. Variables that are already set keep their
value.

| Key                        | Default        | Description                                        |
| -------------------------- | -------------- | -------------------------------------------------- |
| `keyword`                  | `conduit`      | Word that players mention with `@`                 |
| `memory`                   | `player`       | `player` keeps one conversation per player,        |
|                            |                | `shared` one for all players, and `none` starts a  |
|                            |                | new one for every request                          |
| `instructions`             |                | Extra instructions for the agent                   |
| `agents.full`              |                | Agent for full mode, see [Agents](#agents)         |
| `agents.chat`              |                | Agent for chat mode, which is off without it       |
| `access.operators`         | `true`         | Server operators get full mode                     |
| `access.allow`             | `[]`           | Players who get full mode, by name or UUID         |
| `access.chat`              | `true`         | Answer everyone else in [chat mode](#modes)        |
| `tools.readOnly`           | `false`        | Only give full mode tools that read state          |
| `tools.chat.allow`         |                | Only these tools in chat mode, instead of the      |
|                            |                | default list                                       |
| `tools.chat.deny`          | `[]`           | Never these tools in chat mode. On its own, it     |
|                            |                | allows every other read tool                       |
| `approvals.timeoutSeconds` | `30`           | Time to answer an approval before it counts as     |
|                            |                | denied                                             |
| `approvals.add`            | `[]`           | Tools or actions that also need approval           |
| `approvals.remove`         | `[]`           | Tools or actions that never need approval          |
| `sandbox.offline`          | `false`        | Cut the agent's container off from every network   |
| `sandbox.timeoutSeconds`   | `300`          | Time limit for one request                         |
| `sandbox.files`            | `[]`           | More login files to copy into the agent's home,    |
|                            |                | relative to `~`                                    |
| `sandbox.env`              | `{}`           | Variables for the agent by name, such as API keys  |
| `replies.maxLength`        | `1000`         | The bot cuts longer answers                        |
| `stateDirectory`           | `.conduit-bot` | Where the bot keeps conversations, relative to the |
|                            |                | config file                                        |

Each agent has an `agent`, a `model` and an `effort`, which are required, and optional `args`.

### Modes

Operators and the players in `access.allow` get full mode, where the agent controls the game.
Anyone in full mode controls the agent and every tool it has, so keep the list short or turn on
`tools.readOnly`. With `shared` memory, full mode answers go to everyone.

Chat mode lets every other player ask questions. `agents.chat` turns it on and picks its agent,
which can be another CLI or a cheaper model than full mode's. In chat mode the agent can only read
the game. The player tools only show the asking player's own data, such as their inventory,
position and statistics, and never another player's data or an IP address. Each player has a chat
mode conversation of their own, separate from full mode, and only that player sees its answers.

The default chat mode tools are `server_info`, `world_info`, `game_rules`, `get_block`,
`get_region`, `terrain`, `registry`, `recipes`, `structures` and `get_player`. Chat mode never gets
the tools that list other players or entities.

### Approvals

Before the agent uses a destructive action, the ones the [MCP server](#mcp-server) lists as hard to
undo, the bot asks for approval in the chat. The asking player and every online operator get the
action with its arguments and an `[Approve]` and a `[Deny]` button. A click from the asking player
or an operator decides. When nobody answers within `approvals.timeoutSeconds`, the action does not
run. Either way, the agent learns what happened and can tell the player. The time spent waiting
does not count against `sandbox.timeoutSeconds`, but the requests in line wait for it too.

`approvals.add` and `approvals.remove` change which actions need approval. Each entry is a tool,
such as `edit_player`, or one of its actions, such as `edit_player.game_mode`. For `batch`, the
actions are its operations, such as `batch.setBlock`.

The buttons use Minecraft's custom click events, which reach the bot through the mod's event stream.
Every button has a random value that only the players who got it know, since a modified client can
send any click.

### Isolation

Every request runs the agent in a new Docker container. The container sees only the agent's own
home, with the conversation, and a copy of its login files. It cannot see the host's files, other
processes or the Docker socket, and its root filesystem is read-only. The Conduit MCP server runs in
the bot, outside the container, and the agent reaches it through the container's input and output.
The gRPC token never enters the container, and the agent cannot turn off `tools.readOnly` or the
chat mode limits from inside it.

The container can reach the internet, which the agent needs for its model's API, unless
`sandbox.offline` is on. It cannot reach services that only listen on the host's loopback interface,
such as the mod's gRPC server by default.

The bot copies the login files into the agent's home before each run and deletes them after it. If
the agent's CLI refreshed a token during the run, the bot copies it back, unless the host file
changed in the meantime.

### Agents

An agent's `agent` is `claude`, `codex` or `opencode`. To use another agent CLI, open an issue. The
bot builds an image with the latest version of each CLI it uses the first time it runs, and again
after each bot update. It copies the CLI's logins from the host into the container, so log in on
the host first. It also turns off the CLI's own tools, so the agent only uses the Conduit tools. The
bot passes `model`, `effort` and `args` to the CLI as given.

#### Claude Code

[Claude Code](https://claude.com/claude-code) logs in with `claude` on the host. Where it keeps the
login in the system keychain instead of `~/.claude/.credentials.json`, as on macOS, pass a token
from `claude setup-token` or an API key, such as
`"sandbox": { "env": { "CLAUDE_CODE_OAUTH_TOKEN": "${CLAUDE_CODE_OAUTH_TOKEN}" } }`.

```json
{
    "agents": {
        "full": { "agent": "claude", "model": "sonnet", "effort": "high" },
        "chat": { "agent": "claude", "model": "haiku", "effort": "low" }
    }
}
```

#### Codex

[Codex CLI](https://github.com/openai/codex) logs in with `codex login` on the host.

```json
{
    "agents": {
        "full": { "agent": "codex", "model": "gpt-6.1-sol", "effort": "high" },
        "chat": { "agent": "codex", "model": "gpt-6-luna", "effort": "low" }
    }
}
```

#### OpenCode

[OpenCode](https://opencode.ai) logs in with `opencode auth login` on the host. It keeps those
logins in the same database as every session, so the bot exports them with `opencode auth export`
before each run and imports them in the container. Without a login, free models such as
`opencode/beam-free` work too.

The bot also copies OpenCode's config file, so custom providers work, and passes the variables that
the config reads through `{env:NAME}`. The model is `provider/model`, as `opencode models` lists
them, and the effort is one of the model's variants.

```json
{
    "agents": {
        "full": { "agent": "opencode", "model": "opencode-go/glm-5.3", "effort": "high" },
        "chat": { "agent": "opencode", "model": "opencode-go/glm-5.3-flash", "effort": "low" }
    }
}
```

## Test environment

[`compose.yaml`](./compose.yaml) runs a Fabric server with the mod from `build/libs` and a vanilla
headless client as a real player, using the [HeadlessMC](https://github.com/headlesshq/headlessmc)
Docker image.

`MC_VERSION` is required and must match the Minecraft version the mod was built for:

```sh
./gradlew build
MC_VERSION=<minecraft version> docker compose up -d server   # server, gRPC on localhost:25585
MC_VERSION=<minecraft version> docker compose up -d          # server and a client joining it
```

The gRPC endpoint uses the token `dev-token` unless `CONDUIT_TOKEN` is set. Docker volumes keep
the game data between runs. The client uses the preconfigured
[`docker/options.txt`](./docker/options.txt), so it skips the first-start onboarding and joins the
server directly.

## License

CC0-1.0, see [LICENSE](./LICENSE).
