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
4. A sincronização automática do diário pessoal usa um intervalo de 2, 6, 12 ou 24 horas, escolhido em **Ajustes → Sincronização econômica**. A consulta manual está no botão **Atualizar diário** do cabeçalho e nos widgets que o exibem. O Android determina quando as tarefas periódicas realmente executam.

A lista de turmas (`#TurmaContextoId`) é montada por JavaScript no SGE, então o app a lê do WebView logado e a guarda para o worker.

## Instalação

1. Abra a aba **Releases** deste repositório.
2. Baixe o APK da versão estável em **Assets** e instale (permita instalar apps desta fonte).
3. Abra o app, siga a introdução e entre no SGE na aba **SGE Web**. Volte para **Meu diário** e toque no ícone de atualização do cabeçalho.
4. Em **Ajustes → Galeria de widgets**, escolha um formato e configure seu recorte. Também é possível adicioná-lo pelo seletor de widgets do aparelho.

A prévia do PR #2 é distribuída como `SGE-Diario-previa-N.apk` nos artefatos do workflow. Ela se chama **SGE Diário (teste)** e tem pacote `.teste`, separado da versão estável.

A versão estável é assinada com a chave privada do app principal, mantida fora do repositório. O canal `.teste` usa `app/debug.keystore`, uma chave pública de debug. Os dois canais são separados e não substituem um ao outro. Nunca adicione o keystore estável ou suas senhas ao repositório.

## Privacidade e segurança

- Somente leitura: nenhuma rota de gravação do SGE é chamada.
- Senha nunca é lida nem armazenada; a sessão fica no cookie store do Android.
- Backup do Android desativado (`allowBackup=false`).
- Os dados do painel ficam no aparelho. Exportações e backups são feitos somente por ação do usuário, para o local ou destinatário escolhido no seletor do Android.
- **Nunca** publique tokens, senhas ou cookies em issues, commits ou conversas.

## Diagnóstico

- **Diagnóstico único** (Ajustes): reúne perfil observado, os dois painéis, itens parciais/não verificados, vínculos, regras de sincronização e logs separados por origem. Não inclui cookies ou senhas. Revise o texto antes de compartilhá-lo.
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
- [x] Tela de detalhes por módulo, com cobertura, avaliação e o texto das datas informado pelo SGE.
- [x] Seis widgets com tamanho adaptável, tema claro/escuro e configuração por instância.
- [x] Coordenação por turma, componente e vínculo docente (SGE ou ajuste local explícito).
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

## Novo painel

O PR #2 incorpora a reformulação aprovada à `main`, publicada na versão estável 1.5. A alteração de `release/versao.txt` na `main` inicia a compilação assinada; o workflow Release estável também permite disparo manual.
O APK de debug tem pacote `.teste` e nome **SGE Diário (teste)**, independente da versão estável.
Cada compilação mostra `1.8-preview.N` nos Ajustes e entrega `SGE-Diario-previa-N.apk` no artefato do Actions.

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

## Recursos da versão 1.7

- Larguras a partir de 840 dp exibem lista e detalhe simultâneos. O cartão selecionado fica destacado; a lista mantém a rolagem.
- Ajustes → Histórico semanal mostra consultas concluídas neste aparelho. Mantém oito dias localmente e exibe sete (o dia adicional permite a comparação do primeiro dia da semana); não recupera consultas anteriores à atualização. A comparação exige os mesmos vínculos turma/componente e cobertura completa no módulo.
- Compartilhar nos detalhes oferece texto (seletor nativo, incluindo WhatsApp) e PDF A4 paginado. O agrupamento por professor usa os vínculos já existentes. O PDF preserva símbolos de cobertura, separa dias/documentos e não lista alunos.
- Proteger coordenação é opcional. Usa biometria forte (Android 10+) ou credencial do aparelho (Android 11+), exige autenticação para ativar/desativar e bloqueia novamente em segundo plano. Protege também SGE Web, histórico escolar, diagnósticos e configuração de widgets. Widgets da escola ficam sem nomes e contagens enquanto a proteção está ativa. É um bloqueio local de acesso; não altera o login nem as permissões no SGE.
- Verificação noturna é opcional: agenda a partir das 03h com rede e bateria suficiente. O Android pode adiar. Só enfileira turmas conhecidas com sessão Unidade escolar, não troca o perfil e não substitui uma fila manual em andamento. O estado da última tentativa aparece nos Ajustes.
- O cabeçalho indica o perfil observado nas flags do SGE. A preferência Ambos/Professor/Coordenação continua organizando a navegação e não muda o perfil remoto.
- Cookies renovados preservam os atributos dos cabeçalhos Set-Cookie; a sincronização devolve cookies também ao concluir uma turma da coordenação. Cookies de terceiros ficam desativados.

A correção da leitura de datas dos períodos continua adiada a pedido do usuário. Valores parciais não confirmam ausência de pendências. Estas melhorias integram a versão estável 1.7; as opções de proteção e consulta noturna começam desativadas.


## Recursos da versão 1.8

- **Atualizações do app**: aviso opcional de nova versão, “Baixar atualização” e “Mais tarde” (24 horas). Ajustes permite verificar manualmente e escolher download só em rede sem franquia. O app consulta Releases públicos, baixa pelo DownloadManager e valida SHA-256, tamanho, pacote, versão, Android mínimo e certificado de assinatura antes de entregar o APK ao instalador. A instalação exige confirmação do Android; não é silenciosa. A primeira instalação da 1.8 é manual; as próximas versões compatíveis poderão ser baixadas pelo app.
- **Mapa da escola**: matriz dos cinco módulos por turma ou professor, com rolagem horizontal e abertura dos detalhes pela célula. Registros sem vínculo docente ficam identificados. É um mapa de pendências, não uma planta física da escola.
- **Filtros avançados**: escola, série/etapa, turno, componente, professor, módulo e situação; o recorte é compartilhado entre cartões, mapa e lote da coordenação. A situação usa o módulo escolhido ou todos, se nenhum for selecionado. Série e turno são organizados localmente, sem inferência a partir do código da turma e sem alteração no SGE.
- **Relatórios em lote**: seleção de professores identificados, um PDF por professor, PDF reunido para impressão e folhas de recebimento e ciência, reunidos em ZIP. As folhas têm data e assinatura manuscritas. O cabeçalho de escola e a pessoa que entrega são opcionais. Registros sem vínculo ficam fora do lote nominal, com aviso. Cada docente de uma turma compartilhada recebe a cópia correspondente; os PDFs individuais não devem ser somados como total escolar e não atribuem autoria de registros.
- **Backup e restauração**: arquivo JSON pelo seletor de documentos do Android (incluindo provedores como Drive quando disponíveis). Inclui painéis, histórico, vínculos, organização, filtros e preferências. Valida todo o arquivo antes da confirmação e gravação atômica. Exclui sessão, cookies, senha, diagnóstico bruto, credencial biométrica e estado do instalador. A proteção do aparelho atual é preservada. O backup não é criptografado pelo app: guarde-o em local de sua confiança; os widgets precisam ser adicionados no novo aparelho.
- **Sincronização econômica**: rede sem franquia, carregamento, bateria suficiente, intervalo de 2/6/12/24h e horário escolar configurável. As regras valem para automáticos e para cada turma da fila noturna. Consultas manuais continuam usando a rede disponível. WorkManager pode adiar horários.
- **Diagnóstico único**: reúne as duas origens, sem depender da aba em que a cópia foi iniciada. Consultas antigas invalidadas por sair, limpar ou restaurar não podem sobrescrever os dados restaurados nem recriar a fila da escola.

### Decisão sobre Google

Nesta versão não há uma conta Google adicional dentro do app: ela não autentica o usuário no SGE e não substitui a sessão do WebView. O seletor do Android já permite salvar backups em provedores instalados, sem o app pedir acesso à conta Google. Um login Google próprio passa a fazer sentido se houver uma futura conta de nuvem, sincronização entre aparelhos ou integração institucional aprovada; exigiria configuração OAuth e tratamento próprio dos dados.

### Publicação e validação

O workflow estável publica `atualizacao.json` junto ao APK assinado, obtido dos metadados do próprio APK. O canal de testes publica `atualizacao-teste.json`, independente da versão estável. A assinatura oficial não muda.
Os testes incluem filtros combinados, vínculos compartilhados, backup sem credenciais, rejeição de arquivos inválidos, integridade/assinatura/canal do atualizador, PDF/ZIP/recibos e navegação protegida no telefone e tablet.
A identificação das datas de alguns períodos continua adiada. Os resultados permanecem parciais ou não verificados até esse ajuste.
