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
- PR #2 incorporado à `main` com a aprovação do proprietário em 01/11/2026 (merge `5cbe64dc406b85215c8fb74f6c63eb8758cbabb8`).
- Versão estável 1.5 publicada: https://github.com/nabikendo/sge-diario-widget/releases/tag/v1.5
- O workflow estável 3 aprova testes JVM, valida assinatura e compila o APK de produção (pacote original, versionCode 3). Commit do APK: `e7aa7994c7d9b0da660e014ef642f184fa999233`.
- Alterações futuras de `release/versao.txt` na `main` disparam a publicação estável; o disparo manual continua disponível.

## Evolução após v1.6 — períodos adiados pelo usuário
- [ ] Tela lista/detalhe simultânea em largura útil de tablet, preservando navegação no celular.
- [ ] Histórico local de sete dias, sem converter desconhecidos em zero e comparando apenas escopos/coberturas compatíveis.
- [ ] Exportar agrupamentos, inclusive por professor, em PDF paginado e texto pelo compartilhamento nativo (WhatsApp).
- [ ] Bloqueio opcional da coordenação por biometria/credencial, com novo bloqueio em segundo plano e widgets protegidos.
- [ ] Consulta noturna opcional às 03h, condicionada à sessão escolar, rede e bateria; sem alterar o SGE.
- [ ] Selo do perfil realmente observado no SGE, separado da preferência de uso.
- [ ] Preservar atributos Set-Cookie e retirar aceitação desnecessária de cookies de terceiros.
- [ ] Testes JVM, interface no emulador, PDF renderizado e APK da prévia.

O erro de datas dos períodos permanece sem correção nesta etapa; a cobertura parcial continua explícita em painéis, histórico e relatórios. O targetSdk permanece 34; a migração de SDK fica fora desta evolução.
