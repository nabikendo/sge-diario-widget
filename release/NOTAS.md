SGE Diário 2.5.6

### Build mais seguro
- Versão obrigatória: o build falha com mensagem clara se -Pversao não for passado nem release/versao.txt existir.
- Keystore obrigatório: assembleRelease sem KEYSTORE_FILE aborta antes de gravar o APK.
- R8 ligado: APK de release com minificação e remoção de código e recursos não usados.

### Dependências de teste
- Truth e MockK disponíveis para testes unitários.

Instala por cima da versão anterior.
