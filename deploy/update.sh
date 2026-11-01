#!/usr/bin/env bash
# Cron stages the latest main; only apply-update.sh may touch the live plugin.
set -euo pipefail
umask 077

ROOT="${MINECRAFT_ROOT:-$HOME/teploteplo/minecraft}"
DEPLOY="$ROOT/.deploy"
REPO="$DEPLOY/repo"
CACHE="$ROOT/potato-minigames/.gradle-home"
export GIT_TERMINAL_PROMPT=0
export GIT_SSH_COMMAND='ssh -i ~/.ssh/caos_ssh -o IdentitiesOnly=yes'

mkdir -p "$DEPLOY/releases" "$CACHE"
exec 9>"$DEPLOY/lock"
flock -n 9 || exit 0

if [[ ! -d "$REPO/.git" ]]; then
    git clone -q --single-branch --branch main https://github.com/tiom4eg/mc_minigames.git "$REPO"
fi
if [[ "$(git -C "$REPO" branch --show-current)" != main || -n "$(git -C "$REPO" status --porcelain)" ]]; then
    echo "Managed checkout is not clean main; leaving it and the staged plugin untouched." >&2
    exit 1
fi
git -C "$REPO" fetch -q origin main
git -C "$REPO" merge --ff-only -q origin/main
revision=$(git -C "$REPO" rev-parse HEAD)
if [[ -s "$DEPLOY/pending" && "$(readlink "$DEPLOY/pending")" == "releases/$revision.jar" ]]; then
    exit 0
fi

printf '%s Building main %s\n' "$(date --iso-8601=seconds)" "$revision"
docker run --rm --pull=never --network host --cpus 2 --memory 2g \
    -u "$(id -u):$(id -g)" -e HOME=/tmp -e GRADLE_USER_HOME=/gradle-cache \
    -v "$REPO:/work" -v "$CACHE:/gradle-cache" -w /work eclipse-temurin:25-jdk \
    sh ./gradlew clean build --no-daemon --max-workers=2 --console=plain \
    -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=14881

shopt -s nullglob
jars=("$REPO"/build/libs/*-mc26.3.jar)
if (( ${#jars[@]} != 1 )) || [[ ! -s "${jars[0]}" ]]; then
    echo "Expected exactly one non-empty mc26.3 plugin JAR; keeping the previous staged release." >&2
    exit 1
fi
install -m 644 "${jars[0]}" "$DEPLOY/releases/$revision.jar.new"
mv -f "$DEPLOY/releases/$revision.jar.new" "$DEPLOY/releases/$revision.jar"

# Replace installed scripts atomically; the running shell keeps its original file open.
for script in update.sh apply-update.sh; do
    install -m 755 "$REPO/deploy/$script" "$ROOT/.$script.new"
    mv -f "$ROOT/.$script.new" "$ROOT/$script"
done
ln -sfn "releases/$revision.jar" "$DEPLOY/pending.new"
mv -Tf "$DEPLOY/pending.new" "$DEPLOY/pending"
printf '%s Ready %s; run %s/apply-update.sh to apply. Minecraft was not restarted.\n' \
    "$(date --iso-8601=seconds)" "$revision" "$ROOT"
