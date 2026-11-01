# Reformulação do painel e dos widgets

## Critérios de aceite
- [x] Cartões por turma/componente/professor abrem uma página de detalhes com os cinco módulos.
- [x] Resumos preservam dados desconhecidos/parciais e não tratam avaliação pendente como ação do professor.
- [x] Coordenação agrupa professores por vínculo explícito do horário; vínculos compartilhados não duplicam contagens.
- [x] Introdução reapresentável, legendas completas e opções de cor sem nome de marca.
- [x] Seis widgets configuráveis por instância, com filtros e atalhos aos detalhes.
- [x] Compilação de prévia e testes dos novos agrupamentos e da leitura de vínculos.

## Execução
- [x] Inspecionar modelos, consultas confirmadas, perfil escola, UI e processo de compilação.
- [x] Implementar agregação e vínculos docentes com migração compatível dos dados locais.
- [x] Implementar cartões, detalhes, busca/filtros, ajuda e introdução.
- [x] Implementar galeria e configuração dos widgets.
- [x] Executar testes e build pelo CI; revisar o artefato e atualizar o PR.

## Limites verificados
- A consulta de pendências permanece por turma e componente. O vínculo docente agrupa esses resultados e não identifica o autor individual de cada registro.
- A tela de bloqueio depende do host do aparelho. Não se declara showWhenLocked nem se cria uma tela de bloqueio paralela.

## Validação concluída
- Prévia 72: 29 testes JVM e cinco testes no emulador Android 10 passaram, incluindo capturas dos widgets compactos.
- Prévia 73: 29 testes JVM, cinco testes de interface e compilação aprovados após o ajuste de fundos arredondados compatíveis com Android 10. Capturas finais revisadas.
- Integração com vínculos docentes no SGE real ainda depende de uma sessão do usuário; o ajuste local está disponível.
- Alterações mantidas no PR #2; a branch principal e o release oficial não foram substituídos.
