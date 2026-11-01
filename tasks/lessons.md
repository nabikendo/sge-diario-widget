# Lições

- Uma alteração de interface só é entregue para teste quando existe um APK compilado do commit correspondente; identificar o número da prévia no app e no artefato evita confundir o release anterior.
- Uma troca de paleta não cumpre uma solicitação de reformulação de navegação e hierarquia. Cartões de resumo devem conduzir a detalhes organizados e legíveis.
- Nunca atribuir pendências a um professor a partir de nomes presumidos. Usar vínculos explícitos, distinguir docência compartilhada e preservar o grupo não identificado.

- Em `org.jsoup.select.Elements`, converter para `toList()` antes de `filter { ... }`: o método Java `filter(NodeFilter)` pode ocultar a extensão de coleção do Kotlin.

- `Intent.setClipData` não retorna o Intent: definir `clipData` em `apply` antes de continuar a configuração de flags.
- Em lista/detalhe no tablet, a identidade do conteúdo e o estado de rolagem da lista não devem depender do cartão selecionado.

- Ao sair do SGE, cancelar consultas e invalidar a geração da sessão antes de remover cookies; blocos finally não canceláveis não podem restaurar cookies de uma sessão anterior.
