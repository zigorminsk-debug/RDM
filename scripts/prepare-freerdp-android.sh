#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
freerdp_root="${repo_root}/vendor/FreeRDP"

if [[ ! -d "${freerdp_root}/client/Android" ]]; then
  echo "FreeRDP submodule is missing. Run: git submodule update --init --recursive" >&2
  exit 1
fi

python3 - "${freerdp_root}" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
patches_by_file = {
    "client/Android/Studio/freeRDPCore/build.gradle": (
        ("abiFilters rootProject.ext.abiFilters", "abiFilters(*rootProject.ext.abiFilters)"),
        ("include rootProject.ext.splitArchs", "include(*rootProject.ext.splitArchs)"),
        ("arguments rootProject.ext.cmakeArguments", "arguments(*rootProject.ext.cmakeArguments)"),
        ("androidx.core:core:1.19.0", "androidx.core:core:1.17.0"),
    ),
    "client/Android/cmake/ExternalOpenSSL.cmake": (
        ("make -j SHLIB_EXT=.so build_libs", "make -j2 SHLIB_EXT=.so build_libs"),
        ("make -j SHLIB_EXT=.so install_sw", "make -j2 SHLIB_EXT=.so install_sw"),
    ),
    "client/Android/cmake/ExternalFFmpeg.cmake": (
        ("make -j\n", "make -j2\n"),
        ("make -j install", "make -j2 install"),
    ),
}

changes = []
for relative_path, patches in patches_by_file.items():
    path = root / relative_path
    if not path.is_file():
        raise SystemExit(f"Expected FreeRDP Android build file is missing: {path}")
    source = path.read_text()
    changed = []
    for old, new in patches:
        if old in source:
            if source.count(old) != 1:
                raise SystemExit(f"Expected one '{old}' declaration in {path}")
            source = source.replace(old, new, 1)
            changed.append(old.split()[0])
        elif new not in source:
            raise SystemExit(f"Unrecognized FreeRDP build configuration: '{old}' in {path}")
    if changed:
        path.write_text(source)
        changes.append(f"{relative_path}: {', '.join(changed)}")

if changes:
    print("Applied FreeRDP Android build compatibility fixes:")
    for change in changes:
        print(f"  {change}")
else:
    print("FreeRDP Android build configuration is already compatible.")
PY
