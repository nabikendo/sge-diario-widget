#!/usr/bin/env bash
# Coleta as capturas mesmo quando um teste falha. O runner recebe um único comando.
set +e
gradle connectedDebugAndroidTest "$@" --no-daemon --stacktrace
sge_ui_status=$?
adb pull /sdcard/Android/data/br.to.seduc.sgediariowidget.teste/files/ui-previews ./ui-previews || true
exit "$sge_ui_status"
