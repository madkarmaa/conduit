#!/bin/bash
set -euo pipefail

hmc=(java -jar /deployments/quarkus-run.jar)

mods="$HOME/.minecraft/fabric-$MC_VERSION/mods"

if [ ! -d "$mods" ]; then
    "${hmc[@]}" mod add mod fabric-api fabric "$MC_VERSION"
    "${hmc[@]}" mod add mod fabric-language-kotlin fabric "$MC_VERSION"
fi

rm -f "$mods"/minecraft_grpc-*.jar
find /mod -maxdepth 1 -name "minecraft_grpc-*+$MC_VERSION.jar" \
    -exec cp {} "$mods/" \;

args=(launch fabric "$MC_VERSION" --offline -lwjgl)
if [ -n "${MC_SERVER:-}" ]; then
    args+=(--server "$MC_SERVER")
fi
exec "${hmc[@]}" "${args[@]}"
