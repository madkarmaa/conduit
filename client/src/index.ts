import { existsSync, readdirSync } from "node:fs";
import { join } from "node:path";
import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import type { ProtoGrpcType as BatchProto } from "./generated/batch";
import type { ProtoGrpcType as EntityProto } from "./generated/entity";
import type { ProtoGrpcType as EventsProto } from "./generated/events";
import type { ProtoGrpcType as PlayerProto } from "./generated/player";
import type { ProtoGrpcType as RegistryProto } from "./generated/registry";
import type { ProtoGrpcType as ServerProto } from "./generated/server";
import type { ProtoGrpcType as StructureProto } from "./generated/structure";
import type { ProtoGrpcType as WorldProto } from "./generated/world";
import type { BatchServiceClient } from "./generated/conduit/v1/BatchService";
import type { EntityServiceClient } from "./generated/conduit/v1/EntityService";
import type { EventServiceClient } from "./generated/conduit/v1/EventService";
import type { PlayerServiceClient } from "./generated/conduit/v1/PlayerService";
import type { RegistryServiceClient } from "./generated/conduit/v1/RegistryService";
import type { ServerServiceClient } from "./generated/conduit/v1/ServerService";
import type { StructureServiceClient } from "./generated/conduit/v1/StructureService";
import type { WorldServiceClient } from "./generated/conduit/v1/WorldService";

export { call, fromServiceError, type CallError } from "./call";

type ConduitProto = BatchProto &
    EntityProto &
    EventsProto &
    PlayerProto &
    RegistryProto &
    ServerProto &
    StructureProto &
    WorldProto;

// The built client ships the protos next to itself. Run from source, it loads the mod's own protos.
const BUNDLED_PROTO_DIRECTORY = join(import.meta.dirname, "proto");
const PROTO_DIRECTORY = existsSync(BUNDLED_PROTO_DIRECTORY)
    ? BUNDLED_PROTO_DIRECTORY
    : join(import.meta.dirname, "../../src/main/proto");
const PACKAGE_DIRECTORY = "conduit/v1";

// These must match the flags of the prebuild script, or the generated types will not match the
// loaded messages.
const LOADER_OPTIONS = {
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
    includeDirs: [PROTO_DIRECTORY],
} satisfies protoLoader.Options;

export interface ConnectionOptions {
    address?: string;
    token?: string;
}

export interface ConduitClients {
    batch: BatchServiceClient;
    entity: EntityServiceClient;
    events: EventServiceClient;
    player: PlayerServiceClient;
    registry: RegistryServiceClient;
    server: ServerServiceClient;
    structure: StructureServiceClient;
    world: WorldServiceClient;
}

export function connect({
    address = "localhost:25585",
    token,
}: ConnectionOptions = {}): ConduitClients {
    const protoFiles = readdirSync(`${PROTO_DIRECTORY}/${PACKAGE_DIRECTORY}`)
        .filter((file) => file.endsWith(".proto"))
        .map((file) => `${PACKAGE_DIRECTORY}/${file}`);
    const packageDefinition = protoLoader.loadSync(protoFiles, LOADER_OPTIONS);
    const proto = grpc.loadPackageDefinition(packageDefinition) as unknown as ConduitProto;
    const services = proto.conduit.v1;

    const credentials = grpc.credentials.createInsecure();
    const channel = new grpc.Channel(address, credentials, {});
    const options = {
        channelOverride: channel,
        interceptors: token ? [authorization(token)] : [],
    };

    return {
        batch: new services.BatchService(address, credentials, options),
        entity: new services.EntityService(address, credentials, options),
        events: new services.EventService(address, credentials, options),
        player: new services.PlayerService(address, credentials, options),
        registry: new services.RegistryService(address, credentials, options),
        server: new services.ServerService(address, credentials, options),
        structure: new services.StructureService(address, credentials, options),
        world: new services.WorldService(address, credentials, options),
    };
}

// All clients share one channel, so closing any of them closes the connection.
export function close(clients: ConduitClients): void {
    clients.server.close();
}

// grpc-js refuses call credentials on a plaintext channel, and the mod only serves plaintext.
function authorization(token: string): grpc.Interceptor {
    return (options, nextCall) =>
        new grpc.InterceptingCall(nextCall(options), {
            start(metadata, listener, next) {
                metadata.set("authorization", `Bearer ${token}`);
                next(metadata, listener);
            },
        });
}
