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

# Secrets reach the mod through variables, like in a real deployment. The agents are on when their
# secrets are set, which the agent compose file needs too.
agents=false
if [ -n "${CONDUIT_FULL_SECRET:-}" ] && [ -n "${CONDUIT_CHAT_SECRET:-}" ]; then
    agents=true
fi

# The agents use API keys unless a test asks for the containers' subscription logins.
subscriptions="${CONDUIT_DANGEROUSLY_USE_SUBSCRIPTION_CREDENTIALS:-false}"
case "$subscriptions" in
    true | false) ;;
    *)
        echo "CONDUIT_DANGEROUSLY_USE_SUBSCRIPTION_CREDENTIALS must be true or false" >&2
        exit 2
        ;;
esac

mkdir -p "$dir/config"
cat > "$dir/config/conduit.json" <<EOF
{
    "mcp": {
        "allowedHosts": ["server", "localhost", "127.0.0.1"],
        "tokens": [{ "name": "test", "token": "\${CONDUIT_TEST_TOKEN}" }]
    },
    "agents": {
        "full": {
            "enabled": $agents,
            "mcpUrl": "http://server:25585/mcp",
            "dangerouslyUseSubscriptionCredentials": $subscriptions
        },
        "chat": {
            "enabled": $agents,
            "mcpUrl": "http://server:25585/mcp",
            "dangerouslyUseSubscriptionCredentials": $subscriptions
        }
    }
}
EOF

properties="$dir/server.properties"
touch "$properties"
for setting in online-mode=false enforce-secure-profile=false white-list=false \
    enable-rcon=true rcon.port=25575 "rcon.password=$RCON_PASSWORD"; do
    key=${setting%%=*}
    sed -i "/^$key=/d" "$properties"
    echo "$setting" >> "$properties"
done

exec "${hmc[@]}" server launch --eula-accept "$name" --game nogui
