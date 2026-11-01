# SGE Diário Widget

Aplicativo Android que mostra, na tela inicial e no app, as **pendências do diário de classe** do SGE 3.0 (Seduc-TO) **por turma e disciplina**. É somente leitura: o app não grava nada no SGE. A correção das pendências continua sendo feita pelo professor, direto no SGE.

## O que o app mostra

Para cada turma e disciplina do horário do professor:

| Módulo | O que é contado |
|---|---|
| Conteúdos | dias pendentes no mapa de registros (até hoje) |
| Frequência | dias com frequência não concluída (até hoje) |
| Plano de aula | documentos ausentes + não enviados/reprovados |
| Plano de curso | documentos ausentes + não enviados/reprovados |
| PEI | documentos ausentes + não enviados/reprovados |

Registros "aguardando avaliação" aparecem à parte e **não** contam como pendência.
Um valor com `*` tem cobertura parcial; `—` significa não verificado. O app nunca converte "não sei" em zero.

## Como funciona

1. Você entra no SGE pela aba **SGE Web** (um WebView). O app não pede nem guarda usuário e senha.
2. O `SgeSyncWorker` (WorkManager) reutiliza a sessão do WebView, lê o horário do professor e consulta cada turma.
3. O resultado é salvo localmente (`SgePainelStore`) e exibido no app e no widget (Jetpack Glance).
4. A sincronização roda a cada 2 horas, ao abrir o app, no botão **Sincronizar agora** e no botão ↻ do widget.

A lista de turmas (`#TurmaContextoId`) é montada por JavaScript no SGE, então o app a lê do WebView logado e a guarda para o worker.

## Instalação

1. Abra a aba **Releases** deste repositório.
2. Baixe `SGE-Diario-Widget.apk` em **Assets** e instale (permita instalar apps desta fonte).
3. Abra o app, entre no SGE na aba **SGE Web**, volte para **Turmas** e toque em **Sincronizar agora**.
4. Adicione o widget "SGE 3.0 • Pendências" à tela inicial.

O APK é assinado sempre com a mesma chave (`app/debug.keystore`), então as atualizações funcionam por cima. É uma chave de **debug**, pública de propósito: serve para atualizar, não para distribuição na Play Store.

## Privacidade e segurança

- Somente leitura: nenhuma rota de gravação do SGE é chamada.
- Senha nunca é lida nem armazenada; a sessão fica no cookie store do Android.
- Backup do Android desativado (`allowBackup=false`).
- Os dados do painel ficam só no aparelho.
- **Nunca** publique tokens, senhas ou cookies em issues, commits ou conversas.

## Diagnóstico

- **Copiar diagnóstico** (aba Turmas): copia a versão, a mensagem e as últimas chamadas ao SGE, só com a *estrutura* das respostas (ids, colunas, campos), sem nomes de alunos nem valores.
- **Copiar página** (aba SGE Web): copia a estrutura da tela aberta. Serve para mapear telas novas, como a visão de coordenação.

Quando o SGE mudar o HTML, o app mostra exatamente em que etapa falhou, em vez de exibir zero.

## Desenvolvimento

- Kotlin, Jetpack Compose (Material 3), Jetpack Glance, WorkManager, Jsoup.
- `minSdk 29`, `targetSdk 34`, JDK 17.
- Código em `app/src/main/java`; manifesto em `AndroidManifest.xml` (raiz); testes em `app/src/test`.
- O GitHub Actions roda os testes, compila o APK e o publica em **Releases** (tag `latest`) a cada push na `main`.

```
gradle testDebugUnitTest assembleDebug
```

## Roadmap e sugestões

**Próximos passos já planejados**
- [ ] Tela de detalhe: exibir as **datas** pendentes (ex.: conteúdo de 30/10) em vez de só a contagem.
- [ ] Widget refeito: cores dinâmicas do sistema, tamanhos responsivos (2x2 total, 4x2 por módulo, 4x4 por turma).
- [ ] Painel de **coordenação**: pendências por turma e disciplina (e por professor, se o SGE expuser).
- [ ] Ícone com Material You (cores dinâmicas e camada monocromática).

**Ideias**
- Notificação diária (ex.: 17h) com o resumo das pendências.
- Aviso quando o total de pendências aumentar desde a última sincronização.
- Histórico semanal para ver se as pendências estão caindo.
- Filtros por escola, turno e módulo; ocultar turmas sem pendência.
- Exportar o resumo (PDF ou texto) para reuniões pedagógicas.
- Detectar o dia da aula sem registro no horário do professor.
- Modo de teste offline com HTML de exemplo para os parsers (os testes em `SgeParsingTest.kt` já cobrem parte).
- Assinatura com chave própria e canal de versão estável, separado do canal de testes.

## Licença

MIT. Veja o arquivo [LICENSE](LICENSE).

## Prévia do novo painel

O branch `feat/ios-inspired-neutral-ui` contém a reformulação em avaliação no PR #2.
O APK de debug tem pacote `.teste` e nome **SGE Diário (teste)**, independente da versão estável.
Cada compilação mostra `1.3-preview.N` nos Ajustes e entrega `SGE-Diario-previa-N.apk` no artefato do Actions.

- **Cartões**: busca e filtros por turma ou componente; cada cartão abre uma página com os cinco módulos, os detalhes originais da consulta e o resumo compartilhável.
- **Coordenação**: visões por turma, componente e vínculo docente. A ação Atualizar vínculos docentes consulta a lista `#ProfessorId` e o quadro de horário já usado pelo cliente. Uma tarefa consulta cada professor, evitando concentrar a escola inteira em um único Worker.
- **Vínculos**: os resultados continuam sendo da turma/componente. Uma turma com vários docentes fica em Docência compartilhada; o mesmo registro não é somado uma vez por professor. Uma correspondência ambígua fica sem atribuição. É possível ajustar os vínculos apenas no aparelho, com indicação de vínculo local.
- **Introdução e ajuda**: a primeira abertura apresenta o funcionamento; os Ajustes permitem rever a introdução e consultar as legendas. As paletas se chamam Neutras e Do sistema.
- **Seis widgets**: Resumo do diário, Minhas turmas, Por componente, Coordenação, Por professor e Cartão em foco. Cada instância tem origem, agrupamento e recorte próprios, filtro de atenção e opção de ocultar nomes. O cartão em foco tem controles anterior/próximo; as listas usam rolagem vertical.

### Contagens e cobertura

Conteúdos e frequência contam **dias**, não quantidade de aulas nem alunos faltosos. Os outros módulos contam documentos.
Um resultado desconhecido permanece `—`; um total com qualquer parte não conferida permanece `*`, inclusive `0*`.
Em dia exige todos os cinco módulos com cobertura completa e contagem zero. Aguardando avaliação fica separado.
Uma turma com pendências e cobertura parcial pode aparecer tanto no contador de pendências quanto no de conferência.

### Tela bloqueada

Os widgets são widgets Android comuns. O host do fabricante determina se podem ser colocados na tela bloqueada e quais interações funcionam ali.
O app não cria uma tela sobre o bloqueio, não exige sobreposição e não usa `showWhenLocked` para abrir dados sem autenticação.
Resumo do diário e Cartão em foco são as opções compactas; Ocultar nomes permite apresentar contagens sem expor os rótulos dos cartões.

### Validação da prévia

Os testes JVM cobrem agrupamentos, contagens parciais/desconhecidas, docência compartilhada, prioridade dos vínculos locais e identificação conservadora das turmas do horário.
O job `ui-previews` executa testes de navegação em emulador Android e gera capturas com **dados ilustrativos**, sem sessão do SGE.
A leitura de vínculos no SGE real depende do perfil Unidade escolar e precisa ser validada com uma sessão do usuário. Falhas preservam vínculos anteriores ou deixam a atribuição desconhecida.

### Próximas melhorias sugeridas

1. Histórico local de consultas com comparação de cobertura para distinguir melhoria real de uma leitura incompleta.
2. Favoritos e ordenação por registros mais antigos, quando as datas forem representadas como dados estruturados.
3. Alertas locais opcionais, por período e módulo, sem repetir notificações sobre os mesmos dados.
4. Widget de aula atual/próxima turma, depois de persistir o horário e cruzá-lo com o calendário escolar.
5. Exportação de resumo em PDF e backup local dos filtros, favoritos e vínculos informados no aparelho.
