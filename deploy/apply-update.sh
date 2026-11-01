#!/usr/bin/env bash
# Explicit operator action: stop, replace only this plugin, start and verify it.
set -euo pipefail
umask 077

ROOT=$(cd -- "${MINECRAFT_ROOT:-$HOME/teploteplo/minecraft}" && pwd -P)
DEPLOY="$ROOT/.deploy"
CONTAINER="${MINECRAFT_CONTAINER:-minecraft}"
mkdir -p "$DEPLOY"
exec 9>"$DEPLOY/lock"
echo "Waiting for any current build to finish..."
flock 9

if ! candidate=$(readlink -f "$DEPLOY/pending") || [[ ! -s "$candidate" || "$candidate" != "$DEPLOY/releases/"*.jar ]]; then
    echo "No prepared update. Check $ROOT/update.log or run $ROOT/update.sh first." >&2
    exit 1
fi
revision=${candidate##*/}
revision=${revision%.jar}
if [[ ! "$revision" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Invalid prepared release name: $candidate" >&2
    exit 1
fi
target="releases/$revision.jar"
running=$(docker inspect --format '{{.State.Running}}' "$CONTAINER")
if [[ -L "$DEPLOY/applied" && "$(readlink "$DEPLOY/applied")" == "$target" && "$running" == true ]] \
        && cmp -s "$candidate" "$ROOT/data/plugins/potato-minigames.jar"; then
    echo "$revision is already running; no restart needed."
    exit 0
fi

cd "$ROOT"
mkdir -p plugins data/plugins
backup=$(mktemp -d "$DEPLOY/backup.XXXXXX")
mkdir -p "$backup/plugins" "$backup/data/plugins"
shopt -s nullglob
previous=()
for directory in plugins data/plugins; do
    for jar in "$directory/potato-minigames.jar" "$directory"/potato-minigames-*-mc26.3.jar; do
        [[ -f "$jar" ]] && previous+=("$jar")
    done
done
if (( ${#previous[@]} > 0 )); then
    cp -p --parents -- "${previous[@]}" "$backup"
fi

rollback() {
    local result=$?
    trap - EXIT HUP INT TERM
    echo "Apply failed; restoring the previous plugin from $backup." >&2
    if docker stop --timeout 60 "$CONTAINER"; then
        if rm -f plugins/potato-minigames.jar data/plugins/potato-minigames.jar \
                && cp -a "$backup/plugins/." plugins/ \
                && cp -a "$backup/data/plugins/." data/plugins/; then
            docker start "$CONTAINER" || echo "Could not start the old server; backup: $backup" >&2
        else
            echo "Could not restore plugin files; backup: $backup" >&2
        fi
    else
        echo "Could not stop the server for rollback; backup: $backup" >&2
    fi
    exit "$result"
}
trap rollback EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

printf 'Applying %s; plugin backup: %s\n' "$revision" "$backup"
docker stop --timeout 60 "$CONTAINER"
if (( ${#previous[@]} > 0 )); then
    rm -f -- "${previous[@]}"
fi
install -m 644 "$candidate" plugins/potato-minigames.jar
docker start "$CONTAINER"

deadline=$((SECONDS + 180))
ready=false
status="RCON has not responded yet"
while (( SECONDS < deadline )); do
    if [[ "$(docker inspect --format '{{.State.Running}}' "$CONTAINER")" != true ]]; then
        break
    fi
    if status=$(docker exec "$CONTAINER" rcon-cli pm status 2>&1) && [[ "$status" == *PotatoMinigames* ]]; then
        ready=true
        break
    fi
    sleep 2
done
if [[ "$ready" != true ]] || ! cmp -s "$candidate" data/plugins/potato-minigames.jar; then
    printf 'The new plugin did not become ready: %s\n' "$status" >&2
    docker logs --tail 40 "$CONTAINER" >&2 || true
    exit 1
fi

ln -sfn "$target" "$DEPLOY/applied.new"
mv -Tf "$DEPLOY/applied.new" "$DEPLOY/applied"
trap - EXIT HUP INT TERM
printf 'Applied %s. Minecraft is ready; worlds and plugin data were not replaced.\n' "$revision"
