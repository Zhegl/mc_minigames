#!/usr/bin/env bash
# Real Git/filesystem/locks; a local Docker stand-in isolates process lifecycle failures.
set -euo pipefail
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf -- "$WORK"' EXIT
export MINECRAFT_ROOT="$WORK/minecraft"
export MINECRAFT_CONTAINER=deployment-check
export DEPLOY_CHECK_WORK="$WORK"
export PATH="$WORK/bin:$PATH"
mkdir -p "$WORK/bin" "$WORK/origin/deploy" "$MINECRAFT_ROOT/.deploy" \
    "$MINECRAFT_ROOT/plugins" "$MINECRAFT_ROOT/data/plugins"
: > "$WORK/calls"
cat > "$WORK/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$1" >> "$DEPLOY_CHECK_WORK/calls"
case "$1" in
    run)
        [[ ! -f "$DEPLOY_CHECK_WORK/fail-build" ]] || exit 17
        repo="$MINECRAFT_ROOT/.deploy/repo"
        mkdir -p "$repo/build/libs"
        git -C "$repo" rev-parse HEAD > "$repo/build/libs/potato-minigames-0.2.0-mc26.3.jar"
        ;;
    inspect)
        if [[ -f "$DEPLOY_CHECK_WORK/stopped" ]]; then echo false; else echo true; fi
        ;;
    stop) touch "$DEPLOY_CHECK_WORK/stopped" ;;
    start)
        if [[ -f "$DEPLOY_CHECK_WORK/fail-start" ]]; then
            rm "$DEPLOY_CHECK_WORK/fail-start"
            exit 19
        fi
        cp "$MINECRAFT_ROOT/plugins/"*.jar "$MINECRAFT_ROOT/data/plugins/"
        rm -f "$DEPLOY_CHECK_WORK/stopped"
        ;;
    exec) echo 'PotatoMinigames 0.2.0 — no game running.' ;;
    logs) echo 'Isolated deployment test log' ;;
    *) echo "Unexpected Docker action: $*" >&2; exit 1 ;;
esac
DOCKER
chmod +x "$WORK/bin/docker"
cp "$HERE/update.sh" "$HERE/apply-update.sh" "$WORK/origin/deploy/"
printf 'build/\n' > "$WORK/origin/.gitignore"
printf first > "$WORK/origin/payload"
git -C "$WORK/origin" init -q -b main
git -C "$WORK/origin" config user.name DeploymentCheck
git -C "$WORK/origin" config user.email deployment-check@example.invalid
git -C "$WORK/origin" add .
git -C "$WORK/origin" commit -qm first
git clone -q "$WORK/origin" "$MINECRAFT_ROOT/.deploy/repo"
printf old > "$MINECRAFT_ROOT/plugins/potato-minigames-0.2.0-mc26.3.jar"
cp "$MINECRAFT_ROOT/plugins/"*.jar "$MINECRAFT_ROOT/data/plugins/"
printf 'worlds untouched' > "$MINECRAFT_ROOT/data/sentinel"

fail() { echo "Deployment check failed: $*" >&2; exit 1; }
expected_failure() {
    if "$@"; then fail "command unexpectedly succeeded: $*"; fi
}

# A missing candidate must not stop a server.
expected_failure bash "$HERE/apply-update.sh"
[[ ! -s "$WORK/calls" ]] || fail 'missing candidate touched Docker'

bash "$HERE/update.sh"
first=$(git -C "$WORK/origin" rev-parse HEAD)
[[ "$(readlink "$MINECRAFT_ROOT/.deploy/pending")" == "releases/$first.jar" ]] || fail 'first stage missing'
[[ "$(cat "$MINECRAFT_ROOT/plugins/potato-minigames-0.2.0-mc26.3.jar")" == old ]] || fail 'update changed live JAR'
[[ "$(cat "$WORK/calls")" == run ]] || fail 'update used a server lifecycle action'
calls=$(cat "$WORK/calls")
bash "$HERE/update.sh"
[[ "$(cat "$WORK/calls")" == "$calls" ]] || fail 'unchanged commit was rebuilt'

printf second > "$WORK/origin/payload"
git -C "$WORK/origin" commit -qam second
second=$(git -C "$WORK/origin" rev-parse HEAD)
exec 8>"$MINECRAFT_ROOT/.deploy/lock"
flock 8
bash "$HERE/update.sh"
[[ "$(cat "$WORK/calls")" == "$calls" ]] || fail 'concurrent update bypassed lock'
flock -u 8
exec 8>&-
touch "$WORK/fail-build"
expected_failure bash "$HERE/update.sh"
[[ "$(readlink "$MINECRAFT_ROOT/.deploy/pending")" == "releases/$first.jar" ]] || fail 'failed build replaced stage'
[[ "$(cat "$MINECRAFT_ROOT/plugins/potato-minigames-0.2.0-mc26.3.jar")" == old ]] || fail 'failed build touched live JAR'
rm "$WORK/fail-build"
bash "$HERE/update.sh"
[[ "$(readlink "$MINECRAFT_ROOT/.deploy/pending")" == "releases/$second.jar" ]] || fail 'retry did not stage new commit'
! grep -Eq '^(stop|start|restart)$' "$WORK/calls" || fail 'automatic update restarted Minecraft'

printf dirty >> "$MINECRAFT_ROOT/.deploy/repo/payload"
calls=$(cat "$WORK/calls")
expected_failure bash "$HERE/update.sh"
[[ "$(cat "$WORK/calls")" == "$calls" ]] || fail 'dirty checkout was built'
git -C "$MINECRAFT_ROOT/.deploy/repo" restore payload

bash "$HERE/apply-update.sh"
[[ "$(cat "$MINECRAFT_ROOT/data/plugins/potato-minigames.jar")" == "$second" ]] || fail 'candidate not applied'
[[ "$(readlink "$MINECRAFT_ROOT/.deploy/applied")" == "releases/$second.jar" ]] || fail 'applied revision missing'
[[ ! -e "$MINECRAFT_ROOT/plugins/potato-minigames-0.2.0-mc26.3.jar" ]] || fail 'legacy source JAR remains'
[[ ! -e "$MINECRAFT_ROOT/data/plugins/potato-minigames-0.2.0-mc26.3.jar" ]] || fail 'legacy runtime JAR remains'
[[ "$(cat "$MINECRAFT_ROOT/data/sentinel")" == 'worlds untouched' ]] || fail 'runtime data changed'
calls=$(grep -E '^(stop|start|restart)$' "$WORK/calls")
bash "$HERE/apply-update.sh"
[[ "$(grep -E '^(stop|start|restart)$' "$WORK/calls")" == "$calls" ]] || fail 'same applied release restarted again'

printf third > "$WORK/origin/payload"
git -C "$WORK/origin" commit -qam third
third=$(git -C "$WORK/origin" rev-parse HEAD)
bash "$HERE/update.sh"
touch "$WORK/fail-start"
expected_failure bash "$HERE/apply-update.sh"
[[ "$(cat "$MINECRAFT_ROOT/data/plugins/potato-minigames.jar")" == "$second" ]] || fail 'rollback lost old plugin'
[[ "$(readlink "$MINECRAFT_ROOT/.deploy/applied")" == "releases/$second.jar" ]] || fail 'failed apply advanced revision'
[[ ! -e "$WORK/stopped" ]] || fail 'rollback left server stopped'
bash "$HERE/apply-update.sh"
[[ "$(cat "$MINECRAFT_ROOT/data/plugins/potato-minigames.jar")" == "$third" ]] || fail 'apply could not retry'
[[ "$(cat "$MINECRAFT_ROOT/data/sentinel")" == 'worlds untouched' ]] || fail 'retry changed runtime data'

mv "$MINECRAFT_ROOT/.deploy/pending" "$MINECRAFT_ROOT/.deploy/pending.saved"
printf outside > "$WORK/outside.jar"
ln -s "$WORK/outside.jar" "$MINECRAFT_ROOT/.deploy/pending"
calls=$(cat "$WORK/calls")
expected_failure bash "$HERE/apply-update.sh"
[[ "$(cat "$WORK/calls")" == "$calls" ]] || fail 'out-of-directory candidate reached Docker'
echo 'Deployment checks passed: no automatic restart, staging, locking, dirty checkout, retries and rollback.'
