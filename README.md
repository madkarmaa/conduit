<div align="center">
    <img src="docs/conduit.gif" alt="Conduit" />
    <h1>Conduit</h1>
</div>

A server-side Fabric mod that runs a gRPC server inside Minecraft, so external programs can control
the world, entities and players, run any command and stream live events. It runs on dedicated
servers and in singleplayer worlds. Players do not need the mod to join.

Each [GitHub release](https://github.com/madkarmaa/conduit/releases) has the mod jar.

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
installs the Bun workspace and builds the [TypeScript client](#typescript-client), the
[MCP server](#mcp-server) and the [chat bot](#chat-bot).

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

Every call except the standard health check needs `authorization: Bearer <token>`.

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
| `EventService`     | Live stream of chat, joins, movement, block, combat, command, tick,   |
|                    | entity spawn and removal, chunk, advancement, inventory, item pickup, |
|                    | drop, craft, smelt and trade events                                   |
| `RegistryService`  | Registries and their entries, tags, recipes, loot tables              |
| `BatchService`     | Many world, entity, player and command operations in one call and one |
|                    | server tick                                                           |
| `StructureService` | Export, import, save and place regions with their block entities and  |
|                    | entities, in the vanilla structure file format                        |

In singleplayer the gRPC server runs while a world is open.

Entities can only spawn or be teleported into loaded chunks. Force-load the chunk with
`WorldService.SetChunkForced` or keep a player nearby.

Events in a subscription are numbered. A client that reads too slowly loses the oldest buffered
events, and an `events_dropped` event reports which ones were lost, so the client knows to re-read
any state it tracks.

```sh
grpcurl -plaintext -H "authorization: Bearer $TOKEN" localhost:25585 \
    conduit.v1.ServerService/GetInfo
grpcurl -plaintext -H "authorization: Bearer $TOKEN" -d '{"command": "time set day"}' \
    localhost:25585 conduit.v1.ServerService/ExecuteCommand
```

## TypeScript client

[`client`](./client) is a typed Node.js client built with the official
[`@grpc/grpc-js`](https://www.npmjs.com/package/@grpc/grpc-js) and
[`@grpc/proto-loader`](https://www.npmjs.com/package/@grpc/proto-loader) packages. Its types are
generated from the mod's protos, and `./gradlew build` regenerates them. Each release publishes it
as `@conduit/client` to the same registry as the [MCP server](#mcp-server):

```sh
npm install @conduit/client
```

To build only the client from this checkout:

```sh
bun install
cd client
bun run build
```

```ts
import { close, connect } from "@conduit/client";

const clients = connect({ address: "localhost:25585", token: process.env.TOKEN });
clients.server.executeCommand({ command: "time set day" }, (error, response) => {
    console.log(error ?? response);
    close(clients);
});
```

## MCP server

[`mcp`](./mcp) is an [MCP](https://modelcontextprotocol.io) server over stdio that lets AI agents
control the game through the mod. It needs Node.js 20 or later. Each release publishes it as
`@conduit/mcp` to `https://npm.madkarma.top`, so npm needs that registry for the `@conduit` scope:

```sh
npm config set @conduit:registry https://npm.madkarma.top
```

| Variable                 | Default           | Description                                          |
| ------------------------ | ----------------- | ---------------------------------------------------- |
| `CONDUIT_ADDRESS`        | `localhost:25585` | gRPC address of the mod                              |
| `CONDUIT_TOKEN`          |                   | Bearer token of the mod                              |
| `CONDUIT_MCP_READ_ONLY`  | `false`           | Only offer tools that read state                     |
| `CONDUIT_MCP_TOOLS`      |                   | Only offer these tools, separated by commas          |
| `CONDUIT_MCP_DENY_TOOLS` |                   | Never offer these tools, separated by commas         |
| `CONDUIT_MCP_PLAYER`     |                   | Names and UUIDs of the only player the tools may see |

With `CONDUIT_MCP_PLAYER`, tools only accept that player in their player arguments, tools that list
other players or entities are not offered, and results leave out IP addresses.

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

`./gradlew build` also builds the MCP server from this checkout. To build only the MCP server:

```sh
bun install
cd mcp
bun run build
```

A local build runs with `node mcp/dist/index.mjs` and loads its dependencies from the repository's
`node_modules`.

## Chat bot

[`bot`](./bot) lets players talk to an AI agent in the game chat. A message that mentions
`@conduit`, such as `@conduit build a small house next to me`, goes to an agent CLI that controls the
game through the [MCP server](#mcp-server). The bot posts the agent's answer in the chat. It needs
Node.js 20 or later and [Docker](https://www.docker.com). Each release publishes it as
`@conduit/bot` to the same registry as the MCP server.

```sh
CONDUIT_TOKEN=<token> npx @conduit/bot [config file]
```

The config file defaults to `conduit-bot.json` in the working directory. `CONDUIT_ADDRESS` and
`CONDUIT_TOKEN` work as they do for the MCP server.

```json
{
    "agent": { "preset": "claude", "model": "sonnet" },
    "allow": ["Steve"],
    "chat": { "enabled": true, "model": "haiku" }
}
```

| Key              | Default        | Description                                                      |
| ---------------- | -------------- | ---------------------------------------------------------------- |
| `agent`          |                | Agent CLI, model and effort, see [Agents](#agents)               |
| `keyword`        | `conduit`      | Word that players mention with `@`                               |
| `memory`         | `player`       | `player` keeps one conversation per player, `shared` one for all |
|                  |                | players, and `none` starts a new one for every request           |
| `operators`      | `true`         | Server operators get full mode                                   |
| `allow`          | `[]`           | Players who get full mode, by name or UUID                       |
| `readOnly`       | `false`        | Only give full mode tools that read state                        |
| `chat`           |                | Chat mode for everyone else, see below                           |
| `offline`        | `false`        | Cut the agent's container off from every network                 |
| `files`          | `[]`           | More login files to copy into the agent's home, relative to `~`  |
| `env`            | `[]`           | Names of variables to pass to the agent, such as API keys        |
| `instructions`   |                | Extra instructions for the agent                                 |
| `timeoutSeconds` | `300`          | Time limit for one request                                       |
| `maxReplyLength` | `1000`         | Longer answers are cut off                                       |
| `stateDirectory` | `.conduit-bot` | Where conversations are kept, relative to the config file        |

### Modes

Operators and the players in `allow` get full mode, where the agent controls the game. Anyone in
full mode controls the agent and every tool it has, so keep the list short or turn on `readOnly`.
With `shared` memory, full mode answers go to everyone.

Chat mode lets every other player ask questions. It is off unless `chat.enabled` is `true`. The
agent can only read the game, and the player tools only show the asking player's own data: their
inventory, position and statistics, but nobody else's and no IP addresses. Chat mode conversations
are never shared, with other players or with full mode, and their answers only go to the asking
player.

| Key            | Default | Description                                                  |
| -------------- | ------- | ------------------------------------------------------------ |
| `chat.enabled` | `false` | Answer players who do not get full mode                      |
| `chat.allow`   |         | Only these tools. Defaults to a list of safe read tools      |
| `chat.deny`    | `[]`    | Never these tools. On its own, every other read tool applies |
| `chat.model`   |         | Model for chat mode, instead of the agent's                  |
| `chat.effort`  |         | Effort for chat mode, instead of the agent's                 |

The default tools are `server_info`, `world_info`, `game_rules`, `get_block`, `get_region`,
`terrain`, `registry`, `recipes`, `structures` and `get_player`. Tools that list other players or
entities are never available in chat mode.

### Isolation

Every request runs the agent in a new Docker container. The container sees only the agent's own
home, with the conversation, and a copy of its login files. It cannot see the host's files, other
processes or the Docker socket, and its root filesystem is read-only. The Conduit MCP server runs in
the bot, outside the container, and the agent reaches it through the container's input and output.
The gRPC token never enters the container, and `readOnly` holds even against an agent that tries to
work around it.

The container can reach the internet, which the agent needs for its model's API, unless `offline`
is on. Services that only listen on the host's loopback interface, such as the mod's gRPC server
by default, stay out of reach.

The login files are copied into the agent's home before each run and deleted after it. A token that
the agent's CLI refreshed during the run is copied back, unless the host file changed in the
meantime.

### Agents

`agent` is one of the presets `claude` ([Claude Code](https://claude.com/claude-code)), `codex`
([Codex CLI](https://github.com/openai/codex)) and `opencode` ([OpenCode](https://opencode.ai)).
The bot builds an image with the latest version of the CLI the first time it runs, and again after
each bot update. Log in to the CLI on the host first, or pass an API key through `env`. Each preset
copies the CLI's usual login file and turns off the CLI's own tools, so the agent only uses the
Conduit tools. An object sets the model, the effort and extra arguments, which go to the CLI as
given:

```json
{ "agent": { "preset": "codex", "model": "gpt-6.1-sol", "effort": "high", "args": [] } }
```

OpenCode takes the model as `provider/model` and the effort as one of the model's variants, so it
needs a model to set the effort.

Any other agent CLI works through a command that prints the answer on stdout, in an image that has
the CLI and Node.js. `resume` continues the latest conversation in the working directory, which is
separate for every conversation. Without it, every request starts a new conversation.

```json
{
    "agent": {
        "command": ["my-agent", "--mcp-config", "{mcp_config}", "{prompt}"],
        "resume": ["my-agent", "--continue", "--mcp-config", "{mcp_config}", "{prompt}"],
        "image": "my-agent-image",
        "model": "my-model"
    }
}
```

| Placeholder     | Value                                                              |
| --------------- | ------------------------------------------------------------------ |
| `{prompt}`      | The player's request, with context about the game and the player   |
| `{mcp_config}`  | JSON in the `mcpServers` format that starts the Conduit MCP server |
| `{mcp_command}` | Executable that starts the Conduit MCP server                      |
| `{mcp_script}`  | Script to pass to `{mcp_command}`                                  |

`./gradlew build` also builds the bot from this checkout.

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
