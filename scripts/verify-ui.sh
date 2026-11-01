#!/usr/bin/env bash
# Coleta as capturas mesmo quando um teste falha. O runner recebe um único comando.
set +e
gradle connectedDebugAndroidTest "$@" --no-daemon --stacktrace
sge_ui_status=$?
adb pull /sdcard/Pictures/SGEPreview ./ui-previews || true
adb pull /sdcard/Download/SGEPreview ./pdf-previews || true
exit "$sge_ui_status"
