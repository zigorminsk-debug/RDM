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
    "client/Android/cmake/ExternalOpenH264.cmake": (
        ("ARCH=${H264_ARCH} -j libraries", "ARCH=${H264_ARCH} -j2 libraries"),
        ("SHAREDLIB_DIR=${DEPS_INSTALL_DIR}/${CMAKE_INSTALL_LIBDIR} -j install",
         "SHAREDLIB_DIR=${DEPS_INSTALL_DIR}/${CMAKE_INSTALL_LIBDIR} -j2 install"),
    ),
    "client/Android/Studio/freeRDPCore/src/main/java/com/freerdp/freerdpcore/presentation/SessionActivity.java": (
        ("\t\ttouchPointerView.setTouchPointerListener(inputManager);\n\t\tsessionView.setScaleGestureDetector(",
         "\t\ttouchPointerView.setTouchPointerListener(inputManager);\n"
         "\t\tif (getIntent().getBooleanExtra(\"com.rdm.android.MOUSE_MODE\", false))\n"
         "\t\t\tinputManager.toggleTouchPointer();\n"
         "\t\tsessionView.setScaleGestureDetector("),
    ),
    "client/Android/Studio/freeRDPCore/src/main/res/values/strings.xml": (
        ('<string name="yes">Yes</string>', '<string name="yes">Да</string>'),
        ('<string name="no">No</string>', '<string name="no">Нет</string>'),
        ('<string name="cancel">Cancel</string>', '<string name="cancel">Отмена</string>'),
        ('<string name="save">Save</string>', '<string name="save">Сохранить</string>'),
        ('<string name="cont">Continue</string>', '<string name="cont">Продолжить</string>'),
        ('<string name="login">Log in</string>', '<string name="login">Войти</string>'),
        ('<string name="menu_sys_keyboard">Keyboard</string>',
         '<string name="menu_sys_keyboard">Клавиатура</string>'),
        ('<string name="menu_ext_keyboard">Function Keys</string>',
         '<string name="menu_ext_keyboard">Функциональные клавиши</string>'),
        ('<string name="menu_touch_pointer">Touch Pointer</string>',
         '<string name="menu_touch_pointer">Виртуальный указатель</string>'),
        ('<string name="kbd_win_key">Windows key</string>',
         '<string name="kbd_win_key">Клавиша Windows</string>'),
        ('<string name="tp_hscroll">Horizontal scroll</string>',
         '<string name="tp_hscroll">Горизонтальная прокрутка</string>'),
        ('<string name="tp_middle_click">Middle click</string>',
         '<string name="tp_middle_click">Нажатие средней кнопки</string>'),
        ('<string name="session_double_back_to_exit">Press back again to disconnect</string>',
         '<string name="session_double_back_to_exit">Нажмите «Назад» ещё раз, чтобы отключиться</string>'),
        ('<string name="error_connection_failure">Could not establish a connection to the server!</string>',
         '<string name="error_connection_failure">Не удалось подключиться к серверу.</string>'),
        ('<string name="info_capabilities_changed">Screen settings changed to ones supported by the server instead.</string>',
         '<string name="info_capabilities_changed">Параметры экрана изменены на поддерживаемые сервером.</string>'),
        ('<string name="dlg_title_verify_certificate">Verify Certificate</string>',
         '<string name="dlg_title_verify_certificate">Проверка сертификата</string>'),
        ('<string name="dlg_msg_verify_certificate">The identity of the remote computer cannot be verified. Do you want to connect anyway?</string>',
         '<string name="dlg_msg_verify_certificate">Не удалось подтвердить подлинность удалённого компьютера. Всё равно подключиться?</string>'),
        ('<string name="dlg_title_credentials">Please enter your credentials</string>',
         '<string name="dlg_title_credentials">Введите учётные данные</string>'),
        ('<string name="dlg_msg_connecting">Connecting &#8230;</string>',
         '<string name="dlg_msg_connecting">Подключение…</string>'),
        ('<string name="dlg_title_exit">Exit App?</string>',
         '<string name="dlg_title_exit">Завершить работу?</string>'),
        ('<string name="dlg_msg_exit">Are you sure you want to exit the app?</string>',
         '<string name="dlg_msg_exit">Вы действительно хотите завершить работу?</string>'),
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
