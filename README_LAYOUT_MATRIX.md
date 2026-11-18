# LIFE.EXE — layout Matrix / Cyber Core

A tela inicial foi reorganizada para seguir a referência visual enviada:

- Cabeçalho LIFE.EXE com status ONLINE.
- Dois cards financeiros lado a lado: saldo total e saldo de hoje, com mini gráficos.
- Dois cards lado a lado: desempenho físico e evolução financeira.
- Painel Projeto Liberdade em largura total, com os avanços realmente registrados.
- To-do list em largura total, com caixas de seleção funcionais.
- Navegação inferior com Início, Estatísticas, botão central de novo registro, Conquistas e Configurações.
- Paleta preta e verde neon, tipografia monoespaçada e barras de sistema escuras.

Os valores e avanços exibidos continuam vindo dos registros do usuário. Não foi inserida uma porcentagem fictícia para o Projeto Liberdade nem prazos fictícios nas tarefas.

## Compilação

Na raiz do projeto:

```bash
./gradlew --no-daemon assembleDebug
```

O APK de debug será gerado em `app/build/outputs/apk/debug/app-debug.apk`.
