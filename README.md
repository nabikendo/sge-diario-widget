# SGE Diário

Acompanhe as pendências do diário escolar no SGE 3.0 — do seu diário e da escola inteira.

[![Versão 2.5.13](https://img.shields.io/badge/versão-2.5.13-168B75)](https://github.com/nabikendo/sge-diario-widget/releases/tag/v2.5.13) [![Licença MIT](https://img.shields.io/badge/licença-MIT-2D7A46)](LICENSE)

---

## Baixar

| Plataforma | Arquivo | Observação |
|---|---|---|
| Android | `SGE-Diario-2.5.13.apk` | Versão estável, instala por cima da anterior. |
| Windows | `SGE-Diario-Desktop-2.5.13.exe` | Mostra um backup local; não sincroniza com o SGE. |
| Canal de testes | [release `latest`](https://github.com/nabikendo/sge-diario-widget/releases/tag/latest) | Pré-versões automáticas. |

Todas as versões estão em [Releases](https://github.com/nabikendo/sge-diario-widget/releases).

---

## Como começar

1. Instale o APK e abra o app.
2. Na aba **SGE Web**, entre com seu perfil no SGE 3.0.
3. Volte a **Meu diário** ou **Coordenação** e toque em atualizar.

O app lê o que o SGE mostra na sua sessão e monta o painel no aparelho. Não grava nada no SGE.

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
- **Widgets:** seis opções para a tela inicial.
- **Fila de coordenação:** acompanhe, pause, retome e reprocesse as turmas que falharam.
- **Histórico:** compare duas janelas de sete dias.
- **Relatórios:** texto para WhatsApp ou PDF para a coordenação, com o bimestre no cabeçalho.
- **Tablets:** painel lado a lado em telas largas.

---

## Privacidade

O app é somente leitura. Não guarda senha nem cookie. Os dados ficam no aparelho, e o backup é manual, pelo seletor de arquivos do Android.

---

## Limitações conhecidas

- Os resultados valem para o último sync. Depois de trocar o bimestre, o painel atualiza sozinho, mas leva alguns minutos.
- Não é oficial nem tem vínculo com a Seduc-TO ou a Ponto iD.

---

## Licença

MIT. Consulte [LICENSE](LICENSE).
