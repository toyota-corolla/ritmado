# RITMO — Life Dashboard

Aplicativo Android nativo em Kotlin. Interface retrofuturista inspirada em Frutiger Aero: aqua, vidro, gradientes e painéis arredondados.

## O que existe no app
- **Edição de registros:** botão de edição em cada receita, gasto, treino e avanço da evolução social; permite corrigir os dados sem apagar e cadastrar novamente.
- **Financeiro:** cadastro e edição de receitas e gastos (valor, descrição e data); saldo total acumulado e saldo diário calculados como receitas menos despesas. Os históricos ficam separados.
- **Lista de tarefas:** criação, conclusão e exclusão de tarefas; até três tarefas pendentes aparecem no widget e podem ser concluídas sem abrir o app.
- **Físico:** registro e edição de treinos (flexões, barras, observação e data), acumulados e gráfico dos últimos registros.
- **Projeto social / evolução:** diário de avanços com título, descrição e data; os registros podem ser editados.
- **Dashboard:** saldo total acumulado (todas as receitas menos todos os gastos), saldo do dia, resumo físico, social e de tarefas.
- **Widget financeiro:** tamanho padrão 2×1, com saldo total acumulado, saldo líquido do dia e mini gráfico dos últimos sete dias; fundo transparente.
- **Widget de tarefas:** tamanho padrão 2×2, com até três tarefas pendentes e botões para concluí-las; fundo transparente.
- Persistência local usando SharedPreferences/JSON. Não exige conta, internet ou servidor.

## Requisitos técnicos
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- compileSdk/targetSdk 35; minSdk 26 (Android 8.0)
- applicationId: `com.ritmo.planner`

## Compilar no GitHub (sem instalar Android Studio)
1. Entre em https://github.com e faça login ou crie uma conta.
2. Clique no `+` no canto superior direito e escolha **New repository**.
3. Em **Repository name**, digite `ritmo-life`. Marque **Public** (ou Private, se preferir) e clique **Create repository**.
4. Na página do repositório vazio, clique em **uploading an existing file**.
5. No computador, extraia o ZIP deste projeto. Abra a pasta `ritmo_life` que contém `README.md`, `gradlew`, `settings.gradle.kts` e `app`.
6. Selecione TODO o conteúdo de dentro dessa pasta (não envie a pasta externa como uma pasta dentro do repositório) e arraste para a área **Drag files here** do GitHub.
7. Espere todos os arquivos terminarem de carregar. Confira se aparecem `app`, `.github`, `gradle`, `gradlew`, `gradlew.bat`, `build.gradle.kts` e `settings.gradle.kts`.
8. Em **Commit changes**, escreva `Primeira versão do Ritmo` e clique no botão verde **Commit changes**.
9. No topo do repositório, clique em **Actions**. Se aparecer a tela de confirmação, clique em **I understand my workflows, go ahead and enable them**.
10. Abra o workflow **Build Android APK** na coluna da esquerda.
11. Clique em **Run workflow**, depois no botão verde **Run workflow** que aparece.
12. Aguarde o círculo ficar verde. Isso pode levar alguns minutos na primeira compilação, pois o Gradle e o Android SDK precisam ser baixados.
13. Clique na execução concluída (linha com o círculo verde).
14. Desça até **Artifacts**. Clique em `ritmo-debug-apk` para baixar o APK.
15. Extraia esse ZIP. Dentro estará `app-debug.apk`. Envie o APK para seu Android e instale. Se o Android perguntar, autorize a instalação pelo navegador/gerenciador de arquivos.
16. (Recomendado) Baixe também `ritmo-gradle-wrapper-oficial`. Extraia e substitua no seu repositório os arquivos `gradlew`, `gradlew.bat` e a pasta `gradle/wrapper`. Faça um novo commit. Assim, o repositório passa a guardar o Gradle Wrapper oficial gerado pelo próprio Gradle.

## Adicionar os widgets à tela inicial do Android
1. Instale o APK atualizado.
2. Toque e segure uma área vazia da tela inicial do celular.
3. Abra **Widgets**.
4. Procure **Ritmo**.
5. Arraste **Saldo — Ritmo** (2×1) ou **Tarefas — Ritmo** (2×2) para a tela inicial.
6. No widget de tarefas, toque no círculo ao lado de uma tarefa para marcá-la como concluída.

O tamanho final pode variar conforme a grade e o launcher do aparelho. O Android usa `targetCellWidth`/`targetCellHeight` a partir do Android 12 e `minWidth`/`minHeight` como compatibilidade com versões anteriores.

## Como atualizar depois
1. Abra o repositório no GitHub.
2. Clique em **Add file** → **Upload files**.
3. Envie os arquivos alterados, mantendo a mesma estrutura de pastas.
4. Clique em **Commit changes**.
5. Vá em **Actions** e abra a nova execução. Ao terminar, baixe o novo artifact.

## Compilar localmente (opcional)
Com JDK 17 e Android SDK 35 instalados, na pasta raiz execute:

```bash
./gradlew assembleDebug
```

O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Observações
- O saldo acumulado é calculado pela soma de todas as receitas menos a soma de todos os gastos cadastrados. Os registros antigos de receitas são preservados na atualização.
- O app armazena os registros no próprio aparelho. Desinstalar o app ou limpar seus dados pode apagar os registros; faça backup antes de trocar de aparelho.
- O APK gerado pelo workflow é um APK **debug**, adequado para instalar e testar. Para publicar na Play Store será necessário configurar assinatura de release e política de privacidade conforme o uso real.
- Para não depender de baixar manualmente o Gradle no computador, o ZIP inclui um bootstrap mínimo que executa Gradle 8.9. Na primeira execução do workflow, o próprio Gradle gera e atualiza os quatro arquivos oficiais do Wrapper; o workflow disponibiliza esses arquivos no artifact `ritmo-gradle-wrapper-oficial`. Baixe esse artifact e substitua os arquivos correspondentes no repositório para manter o Wrapper oficial versionado, como recomenda a documentação do Gradle.
