# Consultas confirmadas no SGE

O horário usa GET `/HorarioDeAulaDoProfessor/QuadroHorarioProfessor` com
`dataReferencia`, `tipo`, `colaboradorId`, `escolaId`, `turno`, `somenteConflitos`.
A resposta JSON traz `html`. As linhas têm horário, componente e turma.
Os IDs são obtidos de `#TurmaContextoId option[data-turmadescricao]` na página inicial.
Nunca fixar IDs pessoais no aplicativo.

Planejamentos usam GET `/PlanejamentoTurma/PartialPlanejamentos`, retornando JSON
com `html`. Os parâmetros estão implementados em `SgePortalClient`.
Os tipos são descobertos pelo texto das opções, pois IDs podem variar por escola.
Os componentes são descobertos em `/PlanejamentoTurma/CarregueGradesEscolares`.

Não enviado e Reprovado são ações do professor. Aguardando Avaliação deve ser
exibido à parte. Registros ausentes exigem comparar os períodos esperados e,
para PEI, estudantes elegíveis. Uma tabela vazia não comprova ausência de pendências.

O calendário mensal inclui datas futuras. A subtração dos totais anuais é inadequada
para pendências vencidas. É necessário validar os marcadores por data e componente.

Estado: consultas integradas ao painel, Worker e widget. O mapa de conteúdos
usa as datas e os componentes dos dias vermelhos. A frequência consulta
`/NotasFaltas/ListarFrequencia` com o filtro oficial de não concluídas.
Ambos contam dias, não faltas de estudantes ou quantidade de aulas.

A cobertura de planos usa `/PlanejamentoTurma/CarreguePeriodos`; PEIs usam também
`/PlanejamentoTurma/ObtenhaAlunosElegiveis` e os vínculos dos registros existentes.
Períodos futuros e meses sem aulas são excluídos. Quando o SGE exige escolha
de periodicidade ou retorna um formato não reconhecido, a cobertura é marcada
como parcial, nunca como zero ausências confirmado.

Testes de parsers adicionados ao GitHub Actions. A validação Android e a instalação
no aparelho precisam ser confirmadas antes de declarar a versão final.
