"""Prepare or explicitly launch an isolated localhost tank client playtest server."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import uuid
import zipfile


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", action="append", default=[], help="Exact client name to whitelist and op")
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--port", type=int, help="Reuse an explicitly selected private loopback port")
    parser.add_argument("--run", action="store_true", help="Launch after preparing; use only when final JAR is ready")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    cached = root / ".tools/server"
    runtime = root / ".tools/tanks-playtest"
    runtime.mkdir(parents=True, exist_ok=True)
    for folder in ("libraries", "cache", "versions"):
        shutil.copytree(cached / folder, runtime / folder, dirs_exist_ok=True)
    shutil.copy2(cached / "asp-server.jar", runtime / "asp-server.jar")
    (runtime / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    with socket.socket() as probe:
        if args.port is not None and not 1024 <= args.port <= 65535:
            raise SystemExit("Playtest port must be between 1024 and 65535")
        probe.bind(("127.0.0.1", args.port or 0))
        port = probe.getsockname()[1]
    (runtime / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=" + str(port) + "\nonline-mode=false\n"
        "white-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\n"
        "enable-status=false\nmanagement-server-enabled=false\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nallow-nether=false\n"
        "generator-settings={\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:stone\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\"}\n"
        "gamemode=creative\nforce-gamemode=false\nview-distance=5\nsimulation-distance=3\n"
        "spawn-protection=0\npause-when-empty-seconds=-1\nmax-players=2\naccepts-transfers=false\n",
        encoding="utf-8",
    )
    (runtime / "bukkit.yml").write_text("settings:\n  allow-end: false\n", encoding="utf-8")
    whitelist = []
    for name in args.name:
        if not name or len(name) > 16 or any(not (c.isascii() and (c.isalnum() or c == "_")) for c in name):
            raise SystemExit("Invalid Minecraft player name")
        offline = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest(), version=3)
        whitelist.append({"uuid": str(offline), "name": name})
    (runtime / "whitelist.json").write_text(json.dumps(whitelist, indent=2) + "\n", encoding="utf-8")
    (runtime / "ops.json").write_text(json.dumps([{**entry, "level": 4, "bypassesPlayerLimit": False}
                                                  for entry in whitelist], indent=2) + "\n", encoding="utf-8")
    plugin_config = runtime / "plugins/PotatoMinigames/config.yml"
    plugin_config.parent.mkdir(parents=True, exist_ok=True)
    plugin_config.write_text("asp:\n  enable: false\nlobby:\n  enabled: false\n", encoding="utf-8")
    if args.jar:
        artifact = args.jar.resolve()
        with zipfile.ZipFile(artifact) as archive:
            if "me/petr1furious/potatominigames/games/tanks/TankVisual.class" not in archive.namelist():
                raise SystemExit("Main plugin JAR lacks tank implementation")
        shutil.copy2(artifact, runtime / "plugins/PotatoMinigames.jar")
        digest = hashlib.sha256((runtime / "plugins/PotatoMinigames.jar").read_bytes()).hexdigest()
        (runtime / "tested-main-sha256.txt").write_text(digest + "\n", encoding="utf-8")
        print("Main JAR SHA-256: " + digest, flush=True)
    print("Private playtest address: 127.0.0.1:" + str(port), flush=True)
    print("Whitelist: " + (", ".join(args.name) or "empty; add the exact client name before connecting"), flush=True)
    print("Solo command: /tanks test; stop: /tanks stop; console shutdown: stop", flush=True)
    if not args.run:
        print("Prepared only; no server started", flush=True)
        return 0
    if not args.jar:
        raise SystemExit("--run requires the explicitly selected final --jar")
    if any(path.name != "PotatoMinigames.jar" for path in (runtime / "plugins").glob("*.jar")):
        raise SystemExit("Playtest directory contains an unexpected extra plugin JAR")
    java = next((root / ".tools/jdk25").glob("*/bin/java.exe"))
    with (runtime / "console.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen([str(java), "-Xms256M", "-Xmx1536M", "-jar", "asp-server.jar", "--nogui"],
                                   cwd=runtime, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding="utf-8", errors="replace")
        assert process.stdout is not None
        for line in process.stdout:
            log.write(line)
            log.flush()
            print(line, end="", flush=True)
        return process.wait()


if __name__ == "__main__":
    sys.exit(main())
