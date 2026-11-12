# SGE Diário

Acompanhe as pendências do diário escolar no SGE 3.0 — do seu diário e da escola inteira.

[![Versão 2.6.0](https://img.shields.io/badge/versão-2.6.0-168B75)](https://github.com/nabikendo/sge-diario-widget/releases/tag/v2.6.0) [![Licença MIT](https://img.shields.io/badge/licença-MIT-2D7A46)](LICENSE)

---

## Baixar

| Plataforma | Arquivo | Observação |
|---|---|---|
| Android | `SGE-Diario-2.6.3.apk` | Android 8.0 ou superior. |
| Windows | `SGE-Diario-Desktop-2.6.3.exe` | Portátil para Windows x64. Abre backups do Android. |
| Canal de testes | [release `latest`](https://github.com/nabikendo/sge-diario-widget/releases/tag/latest) | Pré-versões automáticas. |

Todas as versões estão em [Releases](https://github.com/nabikendo/sge-diario-widget/releases).

---

## Como começar no Android

1. Instale o APK e abra o app.
2. Na aba **SGE Web**, entre com seu perfil no SGE 3.0.
3. Volte a **Meu diário** ou **Coordenação** e toque em atualizar.

O app lê o que o SGE mostra na sua sessão e monta o painel no aparelho. Não grava nada no SGE.

## Como começar no Windows

1. No Android, exporte um backup em **Ajustes → Backup**.
2. No Windows, abra o executável e selecione **Importar backup JSON**.
3. Escolha **Meu diário** ou **Coordenação**, filtre os resultados e salve o PDF do recorte exibido.

O painel Windows usa os dados da última consulta salva no backup. O botão **Abrir SGE Web** abre o site oficial em uma sessão independente. Veja o [guia do Windows](desktop/README.md) para detalhes.

---

## Bimestre

Na tela inicial, escolha **Atual**, **1º**, **2º**, **3º**, **4º** ou **Todos**.

- **Atual** é o padrão: o bimestre em que estamos hoje, pelas datas do próprio SGE.
- **Todos** mostra o ano inteiro.
- Trocar o bimestre atualiza o painel sozinho. O cabeçalho mostra qual bimestre está aplicado.
- Conteúdos e Frequência consideram só o bimestre escolhido. Os demais módulos seguem o mesmo recorte do último sync.

---

## Categorias de turma

O app identifica a categoria pelo nome da turma:

| Nome | Categoria | Entra na verificação |
|---|---|---|
| `13.01/EM.MAT-ANL` (1 ou 2 dígitos) | Escolarização | Sim, por padrão |
| `115.09…` (3 dígitos) | Diversificada | Só se você ativar em Ajustes |
| `1111.01…` (4 dígitos) | AEE | Só se você ativar em Ajustes |

Para incluir diversificadas ou AEE, abra **Ajustes** e ative a categoria. O app busca e sincroniza essas turmas na hora.

---

## O que você vê

Cinco módulos por turma: **Conteúdos · Frequência · Plano de aula · Plano de curso · PEI**.

| Símbolo | Significado |
|---|---|
| `—` | Não verificado nesta consulta. |
| `*` | Cobertura parcial: pode haver dados faltando. |
| `0*` | Zero em consulta incompleta: não confirma que está em dia. |

Uma turma só aparece **em dia** quando os cinco módulos foram conferidos por completo e todos têm zero pendências.

---

## Recursos

- **Painel pessoal e coordenação:** cartões por turma, componente ou professor, com busca e filtros.
- **Coordenação por professor:** no grupo de um professor, sincronizar verifica só as disciplinas dele.
- **Hero Card:** total de pendências, barra de turmas em dia e botão de sincronizar.
- **Widgets:** seis opções para a tela inicial, com horário da última consulta, avisos de cobertura e atalhos por módulo.
- **Fila de coordenação:** veja o progresso, pause, retome depois de renovar a sessão e reprocesse as turmas que falharam.
- **Histórico:** compare consultas e veja pendências novas, resolvidas ou sem verificação.
- **Conferência semanal:** em Coordenação, selecione uma semana e um professor, revise o que o SGE retornou e confira a prévia do PDF ou do texto.
- **Relatórios:** texto para WhatsApp ou PDF para a coordenação, com o bimestre no cabeçalho.
- **Tablets:** painel lado a lado em telas largas.

---

## Android 17

Com o **Continuar em outro dispositivo** do sistema, um aparelho compatível pode abrir o mesmo painel ou semana em outro aparelho com o SGE Diário instalado. O app transfere apenas o recorte de navegação. Cada aparelho usa seus próprios dados locais, sessão do SGE e proteção da coordenação.

As consultas manuais exibem progresso no app. Se as notificações estiverem autorizadas e o Android aceitar a promoção, o progresso pode aparecer como atualização ao vivo.

## Privacidade

O painel consulta dados em modo somente leitura. O backup, em arquivo ou na pasta oculta do Google Drive, é feito por ação sua e pode conter informações escolares. Guarde o arquivo e os relatórios em local de acesso restrito. A janela oficial do SGE Web no Windows mantém sua própria sessão local.

---

## Limitações conhecidas

- Os resultados valem para o último sync. Depois de trocar o bimestre, o painel atualiza sozinho, mas leva alguns minutos.
- A conferência semanal usa as datas que o SGE retornou e os vínculos docentes locais. Confira o horário oficial e a adequação do conteúdo à matriz, ao plano e ao PEI nas fontes originais.
- Não é oficial nem tem vínculo com a Seduc-TO ou a Ponto iD.

---

## Como funciona a versão

Usamos três números no formato **MAIOR.MENOR.CORREÇÃO**. Em **2.5.15**, o **2** identifica a geração do app, o **5** a série de funcionalidades e o **15** a revisão dessa série.

| Próxima versão | Quando usar |
|---|---|
| `2.5.16` | Corrigir falhas mantendo a compatibilidade. |
| `2.6.0` | Adicionar um recurso compatível, como novos filtros no painel. |
| `3.0.0` | Fazer uma mudança que quebra compatibilidade, como trocar o formato de backup sem oferecer migração. |

O fluxo estável usa [release/versao.txt](release/versao.txt) ou a versão informada no disparo manual para compilar o APK e o EXE. O Android também usa um código interno de compilação para ordenar instalações. [Referência do versionamento semântico](https://semver.org/lang/pt-BR/).

## Licença

MIT. Consulte [LICENSE](LICENSE).
