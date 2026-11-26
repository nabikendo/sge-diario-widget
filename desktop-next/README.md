# SGE Diário Desktop

App para Windows e Linux que entra no SGE 3.0 (Seduc-TO) e mostra as pendências do diário, sem precisar do backup do celular.
Funciona com os dois perfis do SGE e troca de tela sozinho conforme o perfil ativo:

- **Servidor (professor):** Home, Semana, Turmas, Histórico e Relatórios. Conteúdo Aplicado, Chamada, Plano de aula, Plano de curso e PEI por turma; escolha do 1º ao 4º bimestre (consulta); botão que abre o SGE direto na turma e no dia pendente.
- **Unidade escolar (coordenação, só consulta):** Visão geral, Professores, Turmas, Componentes, Histórico, Direção e Relatórios, por bimestre ou ano letivo; comparação com a semana passada; mensagens de cobrança prontas para o WhatsApp; PDF e planilha.

Também tem: notificação do sistema para pendência nova, resumo no fim da tarde ("você deu 4 aulas hoje e falta registrar o conteúdo de 2"), ícone na bandeja com o número de pendências, tema claro/escuro, busca global (Ctrl+K), calendário escolar (feriados e dias sem aula), tutorial de boas-vindas, atualização automática e backup no Google Drive.

## Instalar

Baixe `SGE-Diario-Desktop-Setup-<versão>.exe` na [última release](https://github.com/nabikendo/sge-diario-widget/releases/latest) e instale. Depois disso o app se atualiza sozinho (Configurações → Atualizações → Verificar agora). Como o instalador ainda não é assinado, o Windows pode mostrar o aviso azul do SmartScreen: clique em **Mais informações** e depois em **Executar assim mesmo**. O passo a passo com imagens está no [README principal](../README.md#passo-a-passo-para-instalar-para-quem-nunca-instalou-um-programa-fora-da-loja).

### Linux

Baixe `SGE-Diario-Desktop-<versão>-amd64.deb` na mesma release e instale com dois cliques (Ubuntu, Zorin, Mint, Debian e derivados) ou pelo terminal:

```
sudo apt install ./SGE-Diario-Desktop-<versão>-amd64.deb
```

O app aparece no menu como **SGE Diário Desktop**. A atualização automática baixa o `.deb` novo e pede a senha de administrador para instalar. "Abrir junto com o Linux" cria um atalho em `~/.config/autostart`. O ícone com o número de pendências precisa de suporte a AppIndicator na barra (já vem no Zorin e no Ubuntu). O backup no Google Drive guarda o login no chaveiro do sistema (GNOME Keyring ou KWallet); sem chaveiro, o backup não conecta.

## Primeiro uso

1. Abra o app e clique em **Entrar no SGE**. Digite o login na página do SGE.
2. O app reconhece o perfil (Servidor ou Unidade escolar) e faz a primeira leitura.
3. Coordenação: no SGE, abra **Escola → Conteúdo Aplicado** uma vez, para o app aprender a lista de turmas, e clique em **Ler a escola**. A Chamada vem ligada por padrão; os planos, em Configurações.

O SGE usa o **ano letivo ativo** na barra superior (ex.: "ANO LETIVO: 2026"). O app não escolhe o ano: segue o que o SGE tem ativo. O botão "Ano letivo" do app significa o ano inteiro desse ano.

## Dados e privacidade

- **Só leitura.** Nenhuma rota de gravação no SGE; o app nunca clica em Salvar.
- **A senha nunca passa pelo app.** O login é feito na página do SGE; o app guarda só a sessão, na partição `persist:sge-oficial`.
- Tudo fica no computador (`%APPDATA%` no Windows, `~/.config` no Linux). Em Configurações → Conta e dados dá para abrir a pasta ou apagar tudo.
- **Backup no Google Drive (opcional):** Configurações → Backup no Google Drive → *Entrar com o Google*. O app guarda painéis e históricos numa pasta privada do próprio app no seu Drive (escopo `drive.appdata`), nunca a sessão do SGE nem a senha. Há envio automático (no máximo a cada 5 minutos), **Enviar agora** e **Restaurar** (une com os dados do computador). O login do Google fica criptografado pelo Windows.
- **Calendário:** feriados nacionais e de Tocantins, Carnaval, Sexta-feira Santa e Corpus Christi não entram nas pendências. Recessos e sábados letivos são informados em Configurações → Calendário escolar.

## Estrutura

```
desktop-next/
├─ electron/                 ← processo principal (Node). A interface nunca acessa isto diretamente.
│  ├─ main.js                ← janelas, bandeja, notificações, IPC, agendamento, fim do dia
│  ├─ preload.js             ← ponte mínima: window.sgeDesktop
│  ├─ motor.js               ← orquestra a leitura e monta os painéis (professor e escola) com cache
│  ├─ armazenamento.js       ← config, painéis, históricos, agenda (JSON em userData)
│  ├─ avisos.js · iconeContador.js · calendario.js   ← regras de avisos, ícone da bandeja, dias sem aula
│  ├─ escolaVisao.mjs        ← visão da coordenação por período (usada também pela interface)
│  ├─ relatorio.js           ← PDF e CSV (professor e coordenação)
│  ├─ atualizador.js         ← atualização automática (electron-updater, releases do GitHub)
│  ├─ nuvem.js · nuvemNucleo.js   ← backup no Google Drive (OAuth + PKCE; núcleo puro e testável)
│  └─ sge/                   ← tudo que fala com o SGE (cliente, leitor, parsing, janelas)
├─ src/                      ← interface: React 19 + Tailwind 4 (sem Node, sem acesso ao SGE)
│  ├─ App.jsx · paginas/ (Escola, Semana, Boasvindas, Paginas) · components/ · lib/
├─ tests/                    ← node --test
└─ index.html · vite.config.js · package.json
```

## Comandos

```
npm install
npm run dev        # Vite + Electron com recarga
npm test           # testes (parsing, avisos, calendário, nuvem, coordenação)
npm run build:win    # instalador .exe em release-dist/
npm run build:linux  # pacote .deb em release-dist/ (rodar no Linux)
```

Fora do Electron (`npm run build:ui` + `vite preview`), a interface usa dados de exemplo; `?escola` mostra a coordenação e `?intro` o tutorial.

## Publicar uma versão

O fluxo `release-estavel.yml` roda ao enviar para `main` com `release/versao.txt` alterado. Se só `desktop-next/` mudou desde a última tag, ele publica só o desktop e **não** gera APK nem envia à Play Store. Escreva as notas em `release/NOTAS.md`.

## Segredos do repositório (Settings → Secrets → Actions)

| Segredo | Para quê |
|---|---|
| `GOOGLE_CLIENT_SECRET` | Chave do cliente OAuth do Google (app para computador) usada no backup do Drive. Entra só no build, em `electron/google.segredo.json` (ignorado pelo git). Sem ela, o backup aparece como indisponível. |
| `WIN_CSC_LINK`, `WIN_CSC_KEY_PASSWORD` | Assinatura digital do instalador (opcional, abaixo). |

O ID do cliente OAuth não é secreto e está em `electron/nuvemNucleo.js`. No Google Cloud, o projeto precisa da **Google Drive API** ativada e, enquanto o app estiver em modo "Testando", dos e-mails permitidos em Público-alvo → Usuários de teste. Para outras pessoas usarem, mude o app para "Em produção".

## Assinatura digital do instalador (tira o aviso azul do Windows)

O build já está pronto para assinar. Falta o certificado, que é pago e emitido em nome da pessoa ou da empresa:

1. Compre um certificado de assinatura de código (OV ou EV) numa autoridade certificadora, ou use o serviço Azure Trusted Signing.
2. Para certificado em arquivo `.pfx`: converta para base64 e cadastre nos segredos `WIN_CSC_LINK` (o base64) e `WIN_CSC_KEY_PASSWORD` (a senha).
3. O próximo build assina o instalador sozinho. Sem os segredos, ele sai sem assinatura.

Com certificado OV, o aviso do SmartScreen some aos poucos, conforme o instalador ganha reputação. Com EV, some desde o primeiro download.
