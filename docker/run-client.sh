#!/bin/bash
# Vanilla client without the mod, so player-related server APIs can be tested against a real player.
set -euo pipefail

hmc=(java -jar /deployments/quarkus-run.jar)

args=(launch "$MC_VERSION" --offline -lwjgl)
if [ -n "${MC_SERVER:-}" ]; then
    args+=(--server "$MC_SERVER")
fi
exec "${hmc[@]}" "${args[@]}"
