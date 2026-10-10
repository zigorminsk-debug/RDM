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
old = "abiFilters rootProject.ext.abiFilters"
new = "abiFilters(*rootProject.ext.abiFilters)"

if old in source:
    if source.count(old) != 1:
        raise SystemExit(f"Expected one FreeRDP abiFilters declaration in {path}")
    path.write_text(source.replace(old, new, 1))
    print("Applied Android Gradle compatibility fix to FreeRDP's ABI filters.")
elif new in source:
    print("FreeRDP Android Gradle ABI filters are already compatible.")
else:
    raise SystemExit(f"Unrecognized FreeRDP ABI-filter configuration in {path}")
PY
