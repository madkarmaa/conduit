# Minecraft gRPC

A server-side Fabric mod that runs a gRPC server inside Minecraft, so external programs can control
the world, entities and players, run any command and stream live events. It runs on dedicated
servers and in singleplayer worlds; players do not need the mod to join.

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

Each setting can be overridden with an environment variable: `MINECRAFT_GRPC_ENABLED`,
`MINECRAFT_GRPC_HOST`, `MINECRAFT_GRPC_PORT`, `MINECRAFT_GRPC_REQUIRE_AUTH`,
`MINECRAFT_GRPC_TOKEN`, `MINECRAFT_GRPC_AUDIT_LOG`, `MINECRAFT_GRPC_BACKUP_DIRECTORY`,
`MINECRAFT_GRPC_MAX_STRUCTURE_VOLUME` and `MINECRAFT_GRPC_MAX_STRUCTURE_UPLOAD_BYTES`.

The audit log records every call that is not read-only, and every call rejected for a missing or
wrong token, through the `minecraft_grpc/audit` logger, so it can be routed to its own file.

Every call except the standard health check needs `authorization: Bearer <token>`.

## API

The service definitions live in
[`src/main/proto/minecraft/grpc/v1`](src/main/proto/minecraft/grpc/v1). Server reflection is
enabled, so tools like `grpcurl` can discover them.

| Service            | Scope                                                                  |
| ------------------ | ---------------------------------------------------------------------- |
| `ServerService`    | Server info, performance, any command with captured output, save,      |
|                    | stop, datapacks, backups                                               |
| `WorldService`     | Blocks and regions, block entities, biomes, heightmaps, chunks, time,  |
|                    | weather, game rules, spawn, explosions, world border                   |
| `EntityService`    | Query (filters or selectors), spawn, remove, teleport, NBT, effects,   |
|                    | attributes, damage, riding                                             |
| `PlayerService`    | Inventories, items, game mode, hunger, experience, messages, titles,   |
|                    | sounds, kick, operator status, statistics, saved data of offline       |
|                    | players                                                                |
| `EventService`     | Live stream of chat, joins, movement, block, combat, command, tick,    |
|                    | entity spawn and removal, chunk, advancement, inventory, item pickup,  |
|                    | drop, craft, smelt and trade events                                    |
| `RegistryService`  | Registries and their entries, tags, recipes, loot tables               |
| `BatchService`     | Many world, entity, player and command operations in one call and one  |
|                    | server tick                                                            |
| `StructureService` | Export, import, save and place regions with their block entities and   |
|                    | entities, in the vanilla structure file format                         |

In singleplayer the gRPC server runs while a world is open.

Entities can only be spawned or teleported into loaded chunks; use `WorldService.SetChunkForced`
or keep a player nearby.

Events in a subscription are numbered. A client that reads too slowly loses the oldest buffered
events, and an `events_dropped` event reports which ones were lost, so the client knows to re-read
any state it tracks.

```sh
grpcurl -plaintext -H "authorization: Bearer $TOKEN" localhost:25585 \
    minecraft.grpc.v1.ServerService/GetInfo
grpcurl -plaintext -H "authorization: Bearer $TOKEN" -d '{"command": "time set day"}' \
    localhost:25585 minecraft.grpc.v1.ServerService/ExecuteCommand
```

## Test environment

[`compose.yaml`](./compose.yaml) runs a Fabric server with the mod from `build/libs` and a vanilla
headless client as a real player, using the [HeadlessMC](https://github.com/headlesshq/headlessmc)
Docker image. The client always uses an offline account, so the test server runs with
`online-mode=false`.

`MC_VERSION` is required and must match the Minecraft version the mod was built for:

```sh
./gradlew build
MC_VERSION=<minecraft version> docker compose up -d server   # server, gRPC on localhost:25585
MC_VERSION=<minecraft version> docker compose up -d          # server and a client joining it
```

The gRPC endpoint uses the token `dev-token` unless `MINECRAFT_GRPC_TOKEN` is set. Game data is
kept in Docker volumes between runs. The client uses the preconfigured
[`docker/options.txt`](./docker/options.txt), so it skips the first-start onboarding and joins the
server directly.

## License

CC0-1.0, see [LICENSE](./LICENSE).
