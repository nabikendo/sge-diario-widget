#!/usr/bin/env bash
# Coleta as capturas mesmo quando um teste falha. O runner recebe um único comando.
set +e
if [[ "${SGE_UI_SMOKE:-false}" == true ]]; then
    classe=br.to.seduc.sgediariowidget.SgeUiSmokeTest
    casos="$classe#handoffRespeitaProtecaoDaCoordenacao,$classe#widgetsCompactosRenderizamDadosConhecidos,$classe#widgetDaCoordenacaoOcultaDadosQuandoProtegido,br.to.seduc.sgediariowidget.SgeWidgetAtalhosTest"
    set -- "$@" "-Pandroid.testInstrumentationRunnerArguments.class=$casos"
fi
gradle connectedDebugAndroidTest "$@" --no-daemon --stacktrace
sge_ui_status=$?
adb pull /sdcard/Pictures/SGEPreview ./ui-previews || true
adb pull /sdcard/Download/SGEPreview ./pdf-previews || true
exit "$sge_ui_status"
