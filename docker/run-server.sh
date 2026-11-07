#!/bin/bash
set -euo pipefail

hmc=(java -jar /deployments/quarkus-run.jar)

name="conduit-$MC_VERSION"
dir="$HOME/.local/share/headlessmc/server/$name"

if [ ! -d "$dir" ]; then
    "${hmc[@]}" server add -n "$name" fabric "$MC_VERSION"
    "${hmc[@]}" mod add mod fabric-api "$name"
    "${hmc[@]}" mod add mod fabric-language-kotlin "$name"
fi

rm -f "$dir"/mods/conduit-*.jar
find /mod -maxdepth 1 -name "conduit-*+$MC_VERSION.jar" \
    -exec cp {} "$dir/mods/" \;

# The test token reaches the mod through a variable, like a real deployment's secrets.
config="$dir/config/conduit.json"
if [ ! -f "$config" ]; then
    mkdir -p "$dir/config"
    echo '{"mcp": {"tokens": [{"name": "test", "token": "${CONDUIT_TEST_TOKEN}"}]}}' > "$config"
fi

properties="$dir/server.properties"
touch "$properties"
for setting in online-mode=false enforce-secure-profile=false white-list=false \
    enable-rcon=true rcon.port=25575 "rcon.password=$RCON_PASSWORD"; do
    key=${setting%%=*}
    sed -i "/^$key=/d" "$properties"
    echo "$setting" >> "$properties"
done

exec "${hmc[@]}" server launch --eula-accept "$name" --game nogui
