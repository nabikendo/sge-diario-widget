# Reformulação do painel e dos widgets

## Critérios de aceite
- [ ] Cartões por turma/componente/professor abrem uma página de detalhes com os cinco módulos.
- [ ] Resumos preservam dados desconhecidos/parciais e não tratam avaliação pendente como ação do professor.
- [ ] Coordenação agrupa professores por vínculo explícito do horário; vínculos compartilhados não duplicam contagens.
- [ ] Introdução reapresentável, legendas completas e opções de cor sem nome de marca.
- [ ] Seis widgets configuráveis por instância, com filtros e atalhos aos detalhes.
- [ ] Compilação de prévia e testes dos novos agrupamentos e da leitura de vínculos.

## Execução
- [x] Inspecionar modelos, consultas confirmadas, perfil escola, UI e processo de compilação.
- [ ] Implementar agregação e vínculos docentes com migração compatível dos dados locais.
- [ ] Implementar cartões, detalhes, busca/filtros, ajuda e introdução.
- [ ] Implementar galeria e configuração dos widgets.
- [ ] Executar testes e build pelo CI; revisar o artefato e atualizar o PR.

## Limites verificados
- A consulta de pendências permanece por turma e componente. O vínculo docente agrupa esses resultados e não identifica o autor individual de cada registro.
- A tela de bloqueio depende do host do aparelho. Não se declara showWhenLocked nem se cria uma tela de bloqueio paralela.
