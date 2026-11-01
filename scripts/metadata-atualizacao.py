#!/usr/bin/env python3
"""Gera o contrato público a partir do APK real; não usa tokens nem chaves."""
import argparse
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path


def gerar(apk: Path, badging: str):
    pacote = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    sdk = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    if not pacote or not sdk:
        raise ValueError('Não foi possível identificar o APK')
    return dict(schema=1, pacote=pacote[1], codigo=int(pacote[2]), versao=pacote[3], minSdk=int(sdk[1]), arquivo=apk.name, tamanho=apk.stat().st_size, sha256=hashlib.sha256(apk.read_bytes()).hexdigest())


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('saida', type=Path)
    args = parser.parse_args()
    candidatos = glob.glob(os.path.join(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')), 'build-tools', '*', 'aapt'))
    aapt = shutil.which('aapt') or sorted(candidatos, key=lambda x: [int(n) for n in re.findall(r'\d+', x)])[-1]
    badging = subprocess.check_output([aapt, 'dump', 'badging', str(args.apk)], text=True)
    args.saida.write_text(json.dumps(gerar(args.apk, badging), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
