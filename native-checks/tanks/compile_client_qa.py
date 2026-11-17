"""Compile/package the manual private-playtest helper; never starts or changes a server."""
from pathlib import Path
import hashlib
import os
import subprocess
import zipfile


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    source = root / "native-checks/tanks"
    classes = root / "build/tanks-client-qa"
    artifact = root / "build/libs/TanksClientQa.jar"
    classes.mkdir(parents=True, exist_ok=True)
    artifact.parent.mkdir(parents=True, exist_ok=True)
    for previous in classes.glob("TanksClientQa*.class"):
        previous.unlink()
    jdk = next((root / ".tools/jdk25").glob("*/bin"))
    jars = [path for path in (root / ".gradle-home/caches/modules-2/files-2.1").rglob("*.jar")
            if not path.name.startswith("paper-api-") or path.name.startswith("paper-api-26.3.")]
    quote = lambda value: '"' + str(value).replace("\\", "/") + '"'
    arguments = classes / "compile.args"
    arguments.write_text("-encoding UTF-8\n-cp\n" + quote(os.pathsep.join(map(str, jars)))
                         + "\n-d\n" + quote(classes) + "\n" + quote(source / "TanksClientQa.java")
                         + "\n", encoding="utf-8")
    compiler = jdk / ("javac.exe" if os.name == "nt" else "javac")
    subprocess.run([str(compiler), "@" + str(arguments)], cwd=root, check=True)
    with zipfile.ZipFile(artifact, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.write(source / "plugin-qa.yml", "plugin.yml")
        for compiled in sorted(classes.glob("TanksClientQa*.class")):
            archive.write(compiled, compiled.name)
    print(artifact)
    print("SHA256 " + hashlib.sha256(artifact.read_bytes()).hexdigest().upper())


if __name__ == "__main__":
    main()
