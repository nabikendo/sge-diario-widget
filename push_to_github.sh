#!/usr/bin/env bash
set -e
git init
git branch -M main
git add -A
git commit -m "fix: estrutura completa SGE Diario Widget e GitHub Actions" || true
git remote remove origin 2>/dev/null || true
git remote add origin https://github.com/nabikendo/sge-diario-widget.git
git push -u origin main --force
echo "Enviado com sucesso para https://github.com/nabikendo/sge-diario-widget"
