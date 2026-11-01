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
- [x] Tela lista/detalhe simultânea em largura útil de tablet, preservando navegação no celular.
- [x] Histórico local de sete dias, sem converter desconhecidos em zero e comparando apenas escopos/coberturas compatíveis.
- [x] Exportar agrupamentos, inclusive por professor, em PDF paginado e texto pelo compartilhamento nativo (WhatsApp).
- [x] Bloqueio opcional da coordenação por biometria/credencial, com novo bloqueio em segundo plano e widgets protegidos.
- [x] Consulta noturna opcional às 03h, condicionada à sessão escolar, rede e bateria; sem alterar o SGE.
- [x] Selo do perfil realmente observado no SGE, separado da preferência de uso.
- [x] Preservar atributos Set-Cookie e retirar aceitação desnecessária de cookies de terceiros.
- [x] Testes JVM, interface no emulador, PDF renderizado e APK da prévia.

O erro de datas dos períodos permanece sem correção nesta etapa; a cobertura parcial continua explícita em painéis, histórico e relatórios. O targetSdk permanece 34; a migração de SDK fica fora desta evolução.

### Conferência desta evolução
- Prévia 80: 39 testes JVM aprovados, 10 testes de interface no tablet Android 14 aprovados e nove no celular Android 10 aprovados (o teste exclusivo de tela larga é ignorado no celular).
- Capturas reais com dados ilustrativos revisadas: lista/detalhe, histórico e bloqueios.
- PDF A4 de oito páginas validado pelo PdfRenderer e FileProvider, renderizado e revisado visualmente.
- Datas de períodos permanecem adiadas por solicitação expressa; o app não transforma cobertura parcial em zero confirmado.

### Publicação principal
- PR #3 integrado à main com as melhorias solicitadas.
- Versão estável 1.7 publicada pelo workflow estável 5, com assinatura validada e testes aprovados: https://github.com/nabikendo/sge-diario-widget/releases/tag/v1.7
- Commit do APK estável: c39fdde92783c6610cdc1d4cb53b2b39f10ef9a1.

## Versão 1.8 — ferramentas da escola e atualização no app
- [ ] Atualizador estável/teste separado; aviso Atualizar/Mais tarde; download no app; SHA-256, pacote, versão e assinatura verificados; instalação confirmada pelo Android.
- [ ] Mapa da escola por turma/professor, com cinco módulos, estados desconhecidos/parciais e acesso aos detalhes.
- [ ] Filtros por escola/série/turno/componente/professor/módulo/estado; organização local explícita quando o SGE não fornece metadados.
- [ ] Exportação em lote: PDF individual por professor, PDF reunido para impressão, recibos de recebimento e ciência com assinatura e ZIP.
- [ ] Backup/restauração por seletor do Android; validação antes de substituir dados; sem senhas, cookies, sessão, instalador ou desativação do bloqueio local.
- [ ] Economia: rede não tarifada, carregamento, bateria, intervalo automático e horário da verificação escolar; consulta manual continua disponível.
- [ ] Diagnóstico único dos painéis e logs separados, sem depender da aba de origem.
- [ ] Avaliação documentada do login Google; manter sessão SGE enquanto não houver integração de contas/nuvem que o justifique.
- [ ] Testes de dados e segurança do atualizador/backup, testes de UI no celular/tablet e PDFs de lote/recibos renderizados.
- [ ] APK assinado 1.8 e publicação após validação.

A correção dos períodos continua adiada por solicitação do usuário; não se inferem datas por código e a cobertura parcial permanece explícita.
