#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project_file="${repo_root}/vendor/FreeRDP/client/Android/Studio/freeRDPCore/build.gradle"

if [[ ! -f "${project_file}" ]]; then
  echo "FreeRDP submodule is missing. Run: git submodule update --init --recursive" >&2
  exit 1
fi

python3 - "${project_file}" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
source = path.read_text()
patches = (
    ("abiFilters rootProject.ext.abiFilters", "abiFilters(*rootProject.ext.abiFilters)"),
    ("include rootProject.ext.splitArchs", "include(*rootProject.ext.splitArchs)"),
    ("arguments rootProject.ext.cmakeArguments", "arguments(*rootProject.ext.cmakeArguments)"),
    ("androidx.core:core:1.19.0", "androidx.core:core:1.17.0"),
)
changed = []

for old, new in patches:
    if old in source:
        if source.count(old) != 1:
            raise SystemExit(f"Expected one '{old}' declaration in {path}")
        source = source.replace(old, new, 1)
        changed.append(old.split()[0])
    elif new not in source:
        raise SystemExit(f"Unrecognized FreeRDP Gradle configuration: '{old}' in {path}")

if changed:
    path.write_text(source)
    print("Applied FreeRDP Android Gradle compatibility fixes: " + ", ".join(changed))
else:
    print("FreeRDP Android Gradle configuration is already compatible.")
PY
