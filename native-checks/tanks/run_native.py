"""Prepare a private cached ASP runtime and run the source-only tank native fixture."""
from __future__ import annotations

import argparse
import hashlib
from datetime import datetime
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import zipfile


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--load", action="store_true", help="Run dedicated12-tank lifecycle/load fixture")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    source = root / "native-checks/tanks"
    cached = root / ".tools/server"
    fixture_name = "TanksLoadSmoke" if args.load else "TanksNativeSmoke"
    runtime = root / (".tools/tanks-load/mc26.3" if args.load else ".tools/tanks-native/mc26.3")
    runtime.mkdir(parents=True, exist_ok=True)
    # Preserve evidence before a repeat run replaces the current result and console files.
    previous = runtime / ("plugins/" + fixture_name + "/result.txt")
    if previous.exists():
        evidence = runtime / "evidence" / datetime.now().strftime("%Y%m%d-%H%M%S")
        evidence.mkdir(parents=True, exist_ok=True)
        for item in (previous, runtime / "console.log", runtime / "tested-main-sha256.txt"):
            if item.exists():
                shutil.copy2(item, evidence / item.name)
    for folder in ("libraries", "cache", "versions"):
        shutil.copytree(cached / folder, runtime / folder, dirs_exist_ok=True)
    shutil.copy2(cached / "asp-server.jar", runtime / "asp-server.jar")
    (runtime / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    (runtime / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=" + str(port) + "\nonline-mode=false\n"
        "white-list=true\nenforce-whitelist=true\nenable-rcon=false\nenable-query=false\n"
        "enable-status=false\nmanagement-server-enabled=false\n"
        "level-type=minecraft:flat\ngenerate-structures=false\n"
        "generator-settings={\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1}],\"biome\":\"minecraft:plains\"}\n"
        "view-distance=3\nsimulation-distance=3\nspawn-protection=0\n"
        "pause-when-empty-seconds=-1\nmax-players=1\naccepts-transfers=false\n",
        encoding="utf-8",
    )
    (runtime / "whitelist.json").write_text("[]\n", encoding="utf-8")
    plugin_config = runtime / "plugins/PotatoMinigames/config.yml"
    plugin_config.parent.mkdir(parents=True, exist_ok=True)
    plugin_config.write_text("asp:\n  enable: false\nlobby:\n  enabled: false\n", encoding="utf-8")
    jdk = next((root / ".tools/jdk25").glob("*/bin"))
    jars = list((runtime / "libraries").rglob("*.jar"))
    jars += list((root / ".gradle-home/caches/modules-2/files-2.1/org.jetbrains/annotations").rglob("*.jar"))
    classes = runtime / "fixture-classes"
    classes.mkdir(exist_ok=True)
    compile_args = runtime / "compile.args"
    quote = lambda value: '"' + str(value).replace("\\", "/") + '"'
    compile_args.write_text("-encoding UTF-8\n-cp\n" + quote(";".join(str(p) for p in jars))
                            + "\n-d\n" + quote(classes) + "\n" + quote(source / (fixture_name + ".java")) + "\n", encoding="utf-8")
    subprocess.run([str(jdk / "javac.exe"), "@" + str(compile_args)], cwd=root, check=True)
    fixture = runtime / ("plugins/" + fixture_name + ".jar")
    with zipfile.ZipFile(fixture, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.write(source / ("plugin-load.yml" if args.load else "plugin.yml"), "plugin.yml")
        for compiled in classes.rglob("*.class"):
            archive.write(compiled, compiled.relative_to(classes).as_posix())
    print(f"Prepared isolated ASP26.3 runtime on 127.0.0.1:{port}", flush=True)
    print(f"Fixture compiled and packaged: {fixture}", flush=True)
    if args.prepare_only:
        return 0
    main_jar = (args.jar or root / "build/libs/potato-minigames-0.2.0-mc26.3.jar").resolve()
    with zipfile.ZipFile(main_jar) as archive:
        if "me/petr1furious/potatominigames/games/tanks/TankVisual.class" not in archive.namelist():
            raise SystemExit("Main plugin JAR does not yet contain the tank implementation")
    shutil.copy2(main_jar, runtime / "plugins/PotatoMinigames.jar")
    digest = hashlib.sha256((runtime / "plugins/PotatoMinigames.jar").read_bytes()).hexdigest()
    (runtime / "tested-main-sha256.txt").write_text(digest + "\n", encoding="utf-8")
    print(f"Main plugin SHA-256: {digest}", flush=True)
    result = runtime / ("plugins/" + fixture_name + "/result.txt")
    if result.exists():
        result.unlink()
    with (runtime / "console.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen([str(jdk / "java.exe"), "-Xms256M", "-Xmx1536M",
                                    "-jar", "asp-server.jar", "--nogui"], cwd=runtime,
                                   stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        try:
            code = process.wait(timeout=300)
        except subprocess.TimeoutExpired:
            if process.stdin:
                process.stdin.write("stop\n")
                process.stdin.flush()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.terminate()
            raise SystemExit("Native fixture timed out; inspect " + str(runtime / "console.log"))
    print(f"Server exit: {code}; console: {runtime / 'console.log'}", flush=True)
    print(result.read_text(encoding="utf-8") if result.exists() else "No fixture result was written", flush=True)
    expected = "TANKS_LOAD_SMOKE_PASS" if args.load else "TANKS_NATIVE_SMOKE_PASS"
    return 0 if result.exists() and result.read_text(encoding="utf-8").startswith(expected) else 1


if __name__ == "__main__":
    sys.exit(main())
