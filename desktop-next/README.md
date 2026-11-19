# SGE Diário Desktop (autônomo) — em construção

Novo app desktop que entra no SGE e lê os dados sozinho, sem precisar do backup do celular.
Fica ao lado de `desktop/` (o app atual, offline), que continua sendo o publicado até este alcançar paridade.

## Estrutura

```
desktop-next/
├─ electron/                 ← processo principal (Node). A interface nunca acessa isto diretamente.
│  ├─ main.js                ← janelas (vitrine visível, login), IPC, agendamento da sincronização
│  ├─ preload.js             ← ponte mínima e congelada: window.sgeDesktop
│  ├─ motor.js               ← orquestra a leitura e monta o "painel" limpo (+ cache em userData/painel.json)
│  └─ sge/                   ← tudo que fala com o SGE
│     ├─ constantes.js       ← URL, partição persistente, rota de login
│     ├─ janelaOculta.js     ← BrowserWindow { show:false }: confere sessão e lê o que o SGE monta via JS
│     ├─ cliente.js          ← session.fetch() com os cookies da partição (porta de SgeReadClient.request)
│     ├─ leitor.js           ← turmas, grade, bimestre, conteúdos (mesmas rotas AJAX do Android)
│     └─ parsing.js          ← HTML → dados, com cheerio (porta de SgeParsing). Puro e testável.
├─ src/                      ← vitrine: React + Tailwind (sem Node, sem acesso ao SGE)
│  ├─ App.jsx
│  ├─ components/            ← Layout (barra lateral/superior), CardsTopo, CartaoTurma, BarraSegmentada
│  ├─ lib/sge.js             ← usa window.sgeDesktop; fora do Electron cai nos dados de exemplo
│  └─ styles.css             ← gradiente de fundo, utilitários `vidro` e `vidro-forte`, animações
├─ tests/parsing.test.js     ← node --test, com HTML de exemplo no formato do SGE
├─ index.html · vite.config.js · package.json
```

## Comandos

```
npm install
npm run dev        # Vite + Electron com recarga
npm test           # testes do parsing
npm run build:win  # .exe portátil em release-dist/
```

## Regras que valem para os dois apps

- **Só leitura.** Nenhuma rota de gravação. O único POST é a busca de frequência.
- **Senha nunca passa pelo app.** O login é feito na página do SGE (janela visível). O app guarda
  só a sessão, na partição `persist:sge-oficial`.
- **Mesmas rotas e mesmos critérios do Android**, para os números baterem nos dois.
