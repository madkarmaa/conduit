import { readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
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

type ConduitProto = BatchProto &
    EntityProto &
    EventsProto &
    PlayerProto &
    RegistryProto &
    ServerProto &
    StructureProto &
    WorldProto;

// Resolves the same way from src and dist, so the client always loads the mod's own protos.
const PROTO_DIRECTORY = fileURLToPath(new URL("../../src/main/proto", import.meta.url));
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

export type ConduitClients = ReturnType<typeof connect>;

export function connect({ address = "localhost:25585", token }: ConnectionOptions = {}) {
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
export function close(clients: ConduitClients) {
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
