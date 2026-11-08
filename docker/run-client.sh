#!/bin/bash
# Vanilla client without the mod, so the server has a real player to test the player APIs against.
set -euo pipefail

hmc=(java -jar /deployments/quarkus-run.jar)

# HeadlessMC plays as "Offline" unless MC_PLAYER names another offline account. This writes that
# account into HeadlessMC's account store and makes it the last one used. The server runs in
# offline mode, so the UUID does not matter.
if [ -n "${MC_PLAYER:-}" ]; then
    auth="$HOME/.local/share/headlessmc/.auth"
    mkdir -p "$auth/offline" "$auth/last"
    cat > "$auth/offline/.accounts.json" <<EOF
{"accounts": {"$MC_PLAYER": {"provider": "offline", "name": "$MC_PLAYER", "uuid": "$(cat /proc/sys/kernel/random/uuid)", "token": "", "type": "msa", "xuid": ""}}, "version": 0}
EOF
    cat > "$auth/last/.accounts.json" <<EOF
{"accounts": {"latest": [{"provider": "offline", "name": "$MC_PLAYER"}]}, "version": 0}
EOF
fi

args=(launch "$MC_VERSION" --offline -lwjgl)
if [ -n "${MC_SERVER:-}" ]; then
    args+=(--server "$MC_SERVER")
fi
exec "${hmc[@]}" "${args[@]}"
