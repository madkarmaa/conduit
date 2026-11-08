#!/bin/sh
# Logs the agent CLI in once, inside its container. The login stays in the home volume.
#
#   docker compose exec -it conduit-full conduit-login claude
set -eu

cli="${1:-}"
case "$cli" in
    antigravity | claude | codex | opencode) ;;
    *)
        echo "Usage: conduit-login antigravity|claude|codex|opencode" >&2
        exit 2
        ;;
esac

bun /opt/conduit/src/runner.ts install "$cli"

case "$cli" in
    # The container has no keyring, so the login goes to a file in the home volume.
    antigravity) exec antigravity acp --login --cli_dir_name conduit ;;
    claude)
        echo "Type /login and follow the steps, then /exit."
        exec claude
        ;;
    codex) exec codex login --device-auth ;;
    opencode) exec opencode auth login ;;
esac
