# Auditoria Curricular — especificação e implementação segura

## Objetivo
Comparar a matriz curricular anexada com os conteúdos **efetivamente registrados** e as habilidades **efetivamente vinculadas/sincronizadas** no SGE, por ano letivo, escola, série, turma, componente, área e bimestre. Sem acesso a registros suficientes, exibir **Não verificado**, nunca "em dia".

## Estado atual
A versão 2.5.13 apenas contabiliza ausências e situações dos cinco módulos existentes. O parser de calendário (`SgeParsing.conteudos`) lê o estado de cada dia no mapa, mas não extrai o texto completo de conteúdo aplicado nem uma lista confiável de habilidades vinculadas por aula. Não existe endpoint validado de leitura de vínculos de habilidades por bimestre. **Não afirmar que a verificação automática já está implementada.**

## Modelo de dados
- `MatrizCurricular`: id, origem, versão, ano letivo, data da importação, hash SHA-256, etapa, rede e escola (quando específico).
- `MatrizItem`: identificador estável, área de conhecimento, componente, série, bimestre, código de habilidade, enunciado da habilidade, objeto de conhecimento, conteúdo previsto, obrigatoriedade e aliases aprovados.
- `RegistroSge`: escolaId, turmaId, gradeEscolarId, docenteId quando confirmado, ano, bimestre, data, id do registro, texto do conteúdo aplicado, códigos de habilidades e **origem de cada campo**.
- `ResultadoAuditoria`: item da matriz, evidências, método de correspondência, estado, motivo, data da coleta e cobertura.

## Importação
1. Documentos PDF/XLSX/CSV pelo Storage Access Framework; nunca gravar dados pessoais ou documentos curriculares no Git.
2. XLSX/CSV: importar tabela com colunas explícitas, apresentar prévia e permitir corrigir mapeamentos; PDF: extrair texto/tabelas, exigir confirmação de colunas e linhas antes de usar. PDFs digitalizados requerem procedimento de extração separado.
3. Conferir se a matriz corresponde ao ano letivo, série, disciplina e bimestre; não inferir série a partir de prefixo do código da turma.
4. Permitir múltiplas versões e substituição com histórico; não misturar matrizes de anos/rede/escola diferentes.

## Leitura do SGE
1. Descobrir e documentar, com capturas sanitizadas e sessão autorizada, os recursos GET/POST **somente de leitura** que apresentam conteúdo aplicado e vínculos de habilidade.
2. O campo "texto registrado" não equivale ao vínculo com o código de habilidade no SGE. Registrar ambos como evidências distintas.
3. Se não existir evidência do vínculo, exibir "Habilidade não verificada", mesmo quando o texto menciona a habilidade.
4. Carimbar cada captura com turma, componente, bimestre, horário, cobertura completa/parcial e versão do parser.

## Motor de conformidade
- `CONFORME`: código da habilidade vinculado ao componente/série/bimestre correto e conteúdo com correspondência confirmada à matriz, com cobertura completa.
- `CONTEUDO_DIVERGENTE`: texto efetivamente lançado, mas diferente da previsão da matriz, com evidência explícita; respeitar aliases e equivalências aprovadas.
- `HABILIDADE_NAO_VINCULADA`: resposta completa do SGE comprova inexistência de vínculo exigido.
- `PENDENTE`: período concluído e leitura completa comprova ausência de registro.
- `EM_ANALISE`: similaridade textual ou IA sugere correspondência, mas não comprova; revisão humana.
- `NAO_VERIFICADO`: sem dados, sessão expirada, HTML alterado, bimestre desconhecido, matrícula/componente ambíguo ou cobertura parcial.
- `AINDA_NAO_EXIGIVEL`: bimestre futuro ou requisito não exigível conforme a matriz.

## UI
Nova aba "Matriz curricular" no painel da coordenação; importar arquivo, prévia, validação, seleção de matriz por ano e série; visão por Área > Componente > Professor > Turma > Bimestre. Cartão com quatro indicadores separados: conteúdo registrado, conteúdo compatível, habilidade vinculada e habilidade prevista. Não agregue "desconhecido" com "zero".
Tabela de divergências com evidências e ação de exportar PDF/CSV, sem gravação automática no SGE.

## Verificações
- Dados exemplificativos em testes de unidade: mesmo objeto com textos equivalentes, série errada, habilidade de outro bimestre, código ausente, conteúdo lançado mas não vinculado, calendário futuro, duplicatas, turmas com dois docentes, falha parcial de rede e troca de matriz.
- Testes de integração com HTML sanitizado do SGE.
- Testes de UI em tablet horizontal e celular; foco na coordenação e acessibilidade.
- CI: teste unitário, lint, assembleDebug e instrumentação quando disponível.
- Backup: incluir matriz, versão, hashes e regras de validação no schema seguinte; restaurar somente após validação transacional, sem credenciais ou cookies.

## Não fazer
Não usar IA para declarar ausência de vínculo sem consulta estruturada ao SGE; não estimar datas de bimestres; não presumir que cada habilidade consta em todas as aulas; não alterar o SGE em uma auditoria somente leitura.


## Decisão funcional: módulo independente e reunião presencial

A Auditoria Curricular aparece como **botão próprio**, fora dos cartões de pendências do diário (conteúdo, frequência, plano de aula, plano de curso e PEI). Não altera os indicadores existentes nem interfere no agendamento de sincronização desses módulos.

### Fluxo real
1. Professores e coordenação selecionam habilidades em reunião presencial e anotam em papel.
2. O coordenador registra as escolhas no módulo, por ano, escola, área, componente, série, turma, bimestre e professor; pode anexar foto/PDF da ata ou folha, com confirmação manual dos códigos.
3. O professor faz os lançamentos posteriormente no SGE.
4. O módulo compara três conjuntos independentes: **matriz oficial**, **seleção da reunião** e **vínculos efetivamente consultados no SGE**, além dos textos dos conteúdos aplicados.
5. O relatório mostra separadamente: planejado e não lançado, lançado fora do planejamento, objeto compatível, divergência de conteúdo e não verificado por indisponibilidade de evidência.

### Regras obrigatórias
- Não presumir que toda habilidade da matriz oficial deva ser vinculada em todos os bimestres: a seleção presencial determina o escopo auditado.
- A folha manuscrita não comprova lançamento no SGE; OCR/transcrição, quando houver, exige conferência humana.
- Uma habilidade selecionada pode atender mais de um objeto de conhecimento e vice-versa; preservar vínculos muitos-para-muitos.
- Se o SGE não expuser consulta confiável de habilidades vinculadas, marcar **NÃO VERIFICADO**, nunca **NÃO LANÇADO**.
- Exibir separadamente ausência comprovada e pendência de conferência.
- Documentos fornecidos: Ciências da Natureza Ensino Médio (51 páginas), Matemática Ensino Médio (107 páginas), Matemática Anos Finais (239 páginas). A matriz dos Anos Finais explicita bimestres; não atribuir bimestre automaticamente aos itens do Ensino Médio quando o PDF não o indica.
- Backup local inclui escolhas da reunião, evidências, histórico de alterações e matrizes, com importação/exportação validada.

### Navegação proposta
Botão inicial **Auditoria Curricular** → **Planejamento da reunião**, **Verificar lançamentos**, **Matrizes**, **Relatórios**. Priorizar layout em duas colunas para tablet horizontal, com filtros persistentes e visualização de evidências por habilidade.
