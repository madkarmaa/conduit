#!/bin/bash
set -euo pipefail

hmc=(java -jar /deployments/quarkus-run.jar)

name="minecraft-grpc-$MC_VERSION"
dir="$HOME/.local/share/headlessmc/server/$name"

if [ ! -d "$dir" ]; then
    "${hmc[@]}" server add -n "$name" fabric "$MC_VERSION"
    "${hmc[@]}" mod add mod fabric-api "$name"
    "${hmc[@]}" mod add mod fabric-language-kotlin "$name"
fi

rm -f "$dir"/mods/minecraft_grpc-*.jar
find /mod -maxdepth 1 -name "minecraft_grpc-*+$MC_VERSION.jar" \
    -exec cp {} "$dir/mods/" \;

properties="$dir/server.properties"
touch "$properties"
for setting in online-mode=false enforce-secure-profile=false white-list=false; do
    key=${setting%%=*}
    sed -i "/^$key=/d" "$properties"
    echo "$setting" >> "$properties"
done

exec "${hmc[@]}" server launch --eula-accept "$name" --game nogui
