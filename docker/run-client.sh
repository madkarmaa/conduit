#!/bin/bash
# Vanilla client without the mod, so the server has a real player to test the player APIs against.
set -euo pipefail

hmc=(java -jar /deployments/quarkus-run.jar)

args=(launch "$MC_VERSION" --offline -lwjgl)
if [ -n "${MC_SERVER:-}" ]; then
    args+=(--server "$MC_SERVER")
fi
exec "${hmc[@]}" "${args[@]}"
