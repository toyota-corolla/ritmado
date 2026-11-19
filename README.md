# Projeto Android — Painel pessoal

Aplicativo Android nativo em Java, com armazenamento local SQLite e interface escura em verde neon.

## Recursos

- Finanças: entradas, saídas, descrição, data, saldo total, saldo do dia e gráfico dos últimos sete dias.
- Treinos: registros de flexões e barras; último resultado, recorde e comparação com o treino anterior.
- Missões: criação de missões, subquests, marcação de etapas concluídas e barra de progresso.
- Estatísticas e configurações para consultar os dados e apagá-los.

Os dados ficam no aparelho. O projeto não exige login, servidor ou permissões especiais.

## Compilar pelo GitHub Actions

1. Crie um repositório novo no GitHub.
2. Extraia este ZIP no computador/celular.
3. Envie todos os arquivos e pastas para a raiz do repositório. A pasta `.github/workflows` precisa ser enviada também.
4. Abra a aba **Actions** no GitHub.
5. Se aparecer, clique em **I understand my workflows, go ahead and enable them**.
6. Faça um commit na branch `main` ou `master`, ou abra o workflow **Build APK** e clique em **Run workflow**.
7. Aguarde o job ficar verde.
8. Abra a execução concluída, desça até **Artifacts** e baixe `aplicativo-debug`.
9. Extraia o ZIP baixado. O arquivo `app-debug.apk` é o instalador.

O workflow configura Java 17, instala Android API 35 e Gradle 8.9 automaticamente. Em seguida, gera o Gradle Wrapper oficial dentro do próprio runner e compila usando `./gradlew`. A combinação AGP 8.7.3 + Gradle 8.9 + JDK 17 segue a matriz oficial de compatibilidade do Android Gradle Plugin.

## Compilar localmente

É necessário ter JDK 17, Android SDK (API 35) e acesso à internet na primeira execução. Na raiz do projeto, execute `./gradlew assembleDebug` (Linux/macOS) ou `gradlew.bat assembleDebug` (Windows). O lançador local baixa Gradle 8.9 se ele ainda não estiver instalado.

O APK será gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Observações

- O aplicativo ainda não tem nome comercial; o rótulo do launcher foi deixado vazio.
- O gráfico financeiro usa os valores registrados nos últimos sete dias, não cotações reais de bolsa.
- O ZIP não carrega o JAR binário oficial do Gradle Wrapper. Para não deixar a compilação do GitHub dependente desse arquivo ausente, o workflow gera o Wrapper oficial durante a execução e usa esse Wrapper para compilar. O `gradlew` incluído é apenas um bootstrap local.
- O APK gerado pelo workflow é de depuração (`debug`), adequado para instalar e testar. Para publicar na Play Store, será necessário configurar assinatura de versão (`release`).
