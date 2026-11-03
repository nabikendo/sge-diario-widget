# SGE Diário Widget

### O diário da escola no radar — sem caça ao tesouro e sem adivinhar zeros.

O SGE Diário Widget acompanha pendências do **SGE 3.0 da Seduc-TO** por turma e componente. Mostra o que foi conferido, o que ainda precisa de atenção e, principalmente, quando o app **não tem dados suficientes para afirmar alguma coisa**.

> O app é somente leitura: não altera registros no SGE. Para corrigir uma pendência, é só abrir o SGE e resolver por lá.

[![Versão estável 2.2.1](https://img.shields.io/badge/est%C3%A1vel-2.2.1-168B75)](https://github.com/nabikendo/sge-diario-widget/releases/tag/v2.2.1)
[![Canal de testes](https://img.shields.io/badge/testes-latest-7466D8)](https://github.com/nabikendo/sge-diario-widget/releases/tag/latest)
[![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-2D7A46)](LICENSE)

## Baixe e comece

- **Uso diário:** [baixar o APK estável 2.2.1](https://github.com/nabikendo/sge-diario-widget/releases/download/v2.2.1/SGE-Diario-2.2.1.apk) ou [ver a release](https://github.com/nabikendo/sge-diario-widget/releases/tag/v2.2.1).
- **Quer experimentar antes?** [abrir o canal de testes `latest`](https://github.com/nabikendo/sge-diario-widget/releases/tag/latest) e baixar `SGE-Diario-teste.apk` nos Assets.

Para instalar a versão estável, permita a instalação de apps baixados dessa fonte quando o Android perguntar. Depois, abra o app, siga a introdução e entre no SGE pela aba **SGE Web**. Volte a **Meu diário** e toque em atualizar. Pronto: o painel começa a montar o mapa das pendências.

O APK de teste tem nome e pacote separados (`SGE Diário (teste)`), então pode ficar instalado ao lado do app estável. Ele é uma prévia: pode mudar com frequência; para o dia a dia, prefira a versão estável.

## O que aparece no painel?

O app organiza os resultados em cinco módulos:

| Módulo | O que é contado |
|---|---|
| Conteúdos | Dias sem registro no mapa de conteúdos, até hoje. |
| Frequência | Dias com frequência ainda não concluída, até hoje. |
| Plano de aula | Documentos ausentes, não enviados ou reprovados. |
| Plano de curso | Documentos ausentes, não enviados ou reprovados. |
| PEI | Documentos ausentes, não enviados ou reprovados. |

“**Aguardando avaliação**” aparece à parte e não entra na conta de pendências. O app também mostra cobertura e atualidade dos dados, para a contagem não parecer mais certeira do que realmente é.

### Legenda anti-susto

- `—` significa **não verificado**. O app ainda não conseguiu confirmar esse módulo.
- `*` significa **cobertura parcial**. Pode haver informações que o SGE não devolveu.
- `0*` não é “tudo certo”: é zero encontrado numa consulta incompleta.
- Uma turma só fica **em dia** quando os cinco módulos foram conferidos completamente e todos têm zero pendências.

Se um período do PEI ou dos planos vier num formato diferente, o app não inventa datas para preencher a lacuna. Mantém a cobertura parcial e registra o nome do período no diagnóstico — melhor um “preciso conferir” honesto do que um zero de ficção.

## Um passeio rápido pelos recursos

- **Meu diário e Coordenação:** cartões pesquisáveis, filtros e agrupamentos por turma, componente ou vínculo docente.
- **Comparação:** veja mudanças entre consultas comparáveis. O app só compara módulos completos e os mesmos vínculos; mudança de turma ou cobertura incompleta não vira tendência imaginária.
- **Filtros salvos:** guarde recortes que você usa com frequência e aplique-os novamente sem remontar o quebra-cabeça.
- **Mapa da escola e mapa de dias:** encontre padrões por módulo, turma ou professor e abra os detalhes a partir do próprio mapa.
- **Vínculos docentes:** vínculos ambíguos não são atribuídos automaticamente. Você pode revisar e ajustar vínculos localmente, com a origem indicada.
- **Fila de coordenação:** acompanhe o progresso, pause ou retome a consulta e tente novamente apenas as turmas que falharam.
- **Histórico semanal:** confira consultas recentes e compare dados completos no próprio aparelho.
- **Relatórios:** compartilhe resumo em texto ou PDF; na coordenação há exportações em lote e arquivos de apoio para impressão.
- **Seis widgets:** resumo do diário, minhas turmas, por componente, coordenação, por professor e cartão em foco. Cada widget pode ter seu recorte e agrupamento.
- **Aparência renovada na 2.2.1:** tema verde-menta, tipografia Plus Jakarta Sans, filtros e cartões mais claros, detalhes com mapa de dias e ações rápidas.

## Como o app conversa com o SGE

1. Você entra no SGE pela aba **SGE Web**. O app não pede nem guarda sua senha.
2. A consulta reutiliza a sessão do WebView para ler o horário e buscar os resultados disponíveis.
3. Os resultados são guardados localmente no aparelho e aparecem nos painéis e widgets.
4. Você pode atualizar manualmente. A sincronização pessoal também pode rodar automaticamente conforme o intervalo escolhido em **Ajustes → Sincronização econômica**.

O Android controla quando trabalhos em segundo plano realmente executam; bateria, rede e regras do aparelho podem atrasar uma consulta automática. A atualização manual continua sendo o atalho mais direto.

## Privacidade e proteção

- O app é **somente leitura** no SGE. Não grava nem corrige registros por você.
- Senhas não são lidas nem armazenadas. A sessão permanece no armazenamento de cookies do WebView do Android.
- Painéis e históricos ficam no aparelho. O backup é acionado por você e exportado pelo seletor de documentos do Android.
- O backup JSON **não é criptografado pelo app**. Guarde-o num local confiável; ele não inclui senha, cookies nem sessão do SGE.
- **Proteger coordenação** continua opcional. Usa biometria forte e, em aparelhos compatíveis com Android 11 ou superior, também a credencial do dispositivo. O bloqueio pode abranger coordenação, SGE Web, histórico, diagnósticos e widgets.
- Os widgets são widgets Android comuns. A tela inicial e o fabricante controlam onde aparecem e quais interações funcionam; o app não atravessa a tela de bloqueio.
- Revise o diagnóstico antes de compartilhá-lo. Ele ajuda a investigar falhas, mas pode conter informações operacionais da escola.

## Ajuda para investigar um “ué?”

Em **Ajustes → Diagnóstico**, o diagnóstico único reúne perfil observado, estado dos painéis, vínculos, sincronização e falhas. Ele não inclui cookies nem senhas. Em **SGE Web → Copiar página**, você pode copiar a estrutura da página aberta para ajudar a entender mudanças no site.

Quando o SGE muda sua estrutura, uma etapa de leitura pode falhar. O app tenta apontar onde isso aconteceu, em vez de transformar a falha num belo — e enganoso — zero.

## Para quem desenvolve

O projeto usa Kotlin, Jetpack Compose, Material 3, Jetpack Glance, WorkManager e Jsoup. Requer Android 10 (API 29) ou superior e JDK 17.

Para rodar os testes unitários e compilar a variante de teste, com Gradle e Android SDK instalados:

```sh
gradle testDebugUnitTest assembleDebug
```

O GitHub Actions executa os testes e compila o APK de teste. A mudança em `release/versao.txt` aciona o workflow que testa e publica o APK estável assinado. A chave de assinatura e suas credenciais ficam fora do repositório — não as coloque em commits, issues ou prints.

## Ideias para depois — sugestões, não promessas

Algumas ideias que podem entrar numa conversa futura: favoritos e ordenação por antiguidade, alertas locais opcionais por módulo, um widget da aula atual/próxima e uma camada monocromática para o ícone. Nada disso tem data ou compromisso de lançamento.

## Licença

MIT. Consulte [LICENSE](LICENSE).
