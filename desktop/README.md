# SGE Diário Desktop para Windows

O aplicativo Windows abre o SGE Web oficial em uma janela separada e apresenta, offline, os painéis **Meu diário** e **Coordenação** de um backup JSON exportado pelo Android. A busca, o filtro de estado e o PDF usam apenas os dados importados. `—` indica consulta não verificada; `*` indica cobertura parcial.

## Como usar

1. No Android, exporte o backup em **Ajustes → Backup** e transfira o JSON para o PC.
2. Abra `SGE-Diario-Desktop-2.5.5.exe` e escolha **Importar backup JSON**.
3. Use **Abrir SGE Web** para consultar ou corrigir registros no site oficial. A sessão fica no armazenamento local do aplicativo Windows; a senha e os cookies do Android não vêm no backup.
4. Filtre os resultados e use **Salvar relatório PDF** para gerar o recorte visível.

Este primeiro executável é um **painel complementar**: não sincroniza automaticamente com o SGE, não inclui os widgets Android nem transfere a sessão entre PC e celular. A importação não altera o arquivo original nem envia dados a servidores do aplicativo. Confirme os registros no SGE antes de agir.

## Desenvolvimento

Requer Node.js 22. `npm install`, `npm test` e, em Windows, `npm run build:win` geram o executável portátil em `desktop/dist`. O GitHub Actions faz esse build em um runner Windows e publica o `.exe` como artefato do workflow. O executável é portátil e, nesta etapa, não recebe assinatura de código Windows.
