# Minecraft gRPC

A Fabric mod that runs a gRPC server inside Minecraft, so external programs can control the world,
entities, players and the game client, run any command and stream live events.

## Requirements

- The Minecraft and Fabric Loader versions set in [`gradle.properties`](./gradle.properties), and
  the Java version that Minecraft version requires
- [Fabric API](https://modrinth.com/mod/fabric-api) and
  [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## Building

```sh
./gradlew build
```

The mod jar is written to `build/libs/minecraft_grpc-<mod version>+<minecraft version>.jar`.

## Configuration

On first start the mod writes `config/minecraft-grpc.json`:

| Setting       | Default                            | Description                                |
| ------------- | ---------------------------------- | ------------------------------------------ |
| `enabled`     | `true`                             | Start the gRPC server                      |
| `host`        | `127.0.0.1`                        | Bind address                               |
| `port`        | `25585` (server), `25586` (client) | Listen port                                |
| `requireAuth` | `true`                             | Require a bearer token                     |
| `token`       | random, generated on first start   | Token expected in `authorization` metadata |

Each setting can be overridden with an environment variable: `MINECRAFT_GRPC_ENABLED`,
`MINECRAFT_GRPC_HOST`, `MINECRAFT_GRPC_PORT`, `MINECRAFT_GRPC_REQUIRE_AUTH` and
`MINECRAFT_GRPC_TOKEN`.

Every call except the standard health check needs `authorization: Bearer <token>`.

## API

The service definitions live in
[`src/main/proto/minecraft/grpc/v1`](src/main/proto/minecraft/grpc/v1). Server reflection is
enabled, so tools like `grpcurl` can discover them.

| Service         | Scope                                                                  |
| --------------- | ---------------------------------------------------------------------- |
| `ServerService` | Server info, performance, any command with captured output, save, stop |
| `WorldService`  | Blocks and regions, block entities, biomes, heightmaps, chunks, time,  |
|                 | weather, game rules, spawn, explosions                                 |
| `EntityService` | Query (filters or selectors), spawn, remove, teleport, NBT, effects,   |
|                 | attributes, damage, riding                                             |
| `PlayerService` | Inventories, items, game mode, hunger, experience, messages, titles,   |
|                 | sounds, kick, operator status                                          |
| `EventService`  | Live stream of chat, joins, block, combat, command and tick events     |
| `ClientService` | Client only: state, looking, key input, chat, screens, slot and widget |
|                 | clicks, screenshots, connecting to servers                             |

On a dedicated server all services except `ClientService` are available. On a client, the server
services work while a singleplayer world is open and return `UNAVAILABLE` otherwise.

Entities can only be spawned or teleported into loaded chunks; use `WorldService.SetChunkForced`
or keep a player nearby.

```sh
grpcurl -plaintext -H "authorization: Bearer $TOKEN" localhost:25585 \
    minecraft.grpc.v1.ServerService/GetInfo
grpcurl -plaintext -H "authorization: Bearer $TOKEN" -d '{"command": "time set day"}' \
    localhost:25585 minecraft.grpc.v1.ServerService/ExecuteCommand
```

## Test environment

[`compose.yaml`](./compose.yaml) runs a Fabric server and a headless client with the mod from
`build/libs`, using the [HeadlessMC](https://github.com/headlesshq/headlessmc) Docker image. The
client always uses an offline account, so the test server runs with `online-mode=false`.

`MC_VERSION` is required and must match the Minecraft version the mod was built for:

```sh
./gradlew build
MC_VERSION=<minecraft version> docker compose up -d server   # server, gRPC on localhost:25585
MC_VERSION=<minecraft version> docker compose up -d          # server and client, client on :25586
```

Both use the token `dev-token` unless `MINECRAFT_GRPC_TOKEN` is set. Game data is kept in Docker
volumes between runs. The client uses the preconfigured
[`docker/options.txt`](./docker/options.txt), so it skips the first-start onboarding and joins the
server directly.

The headless client does not render, so `ClientService.Screenshot` fails there.

## License

CC0-1.0, see [LICENSE](./LICENSE).
