# LIFE.EXE — Discipline Protocol

Aplicativo Android nativo em Kotlin, atualizado sobre o projeto-base RITMO. A estrutura original, o `applicationId` (`com.ritmo.planner`), as preferências locais existentes e os providers dos widgets foram preservados. A interface foi convertida para o visual **Cyber Core / Matrix**: preto profundo, verde neon, painéis HUD, tipografia monoespaçada e navegação inferior.

## Módulos

- **Dashboard:** resumo financeiro com gráfico de patrimônio no mês, recordes físicos, missões do dia, hábitos, foco e objetivos.
- **Financeiro:** cadastro, edição e exclusão de receitas e despesas; seleção de data para lançar histórico anterior; saldo total, saldo do dia, evolução financeira e meta mensal configurável.
- **Físico:** registro, edição e exclusão de flexões e barras; recordes pessoais por exercício com a data do recorde; comparação visual do último treino com o anterior e gráfico de evolução.
- **Mission Control:** missões por categoria, prioridade, dificuldade e prazo selecionado pelo calendário; conclusão, início, adiamento com motivo e registro de abandono. Limite de três missões prioritárias ativas por dia.
- **Habit System:** frequência diária, dias úteis ou semanal; meta normal e mínima; lembretes locais; registro retroativo com calendário; calendário de 28 dias; sequência atual, melhor sequência e consistência.
- **Calendário de registros:** seletor de calendário em receitas, despesas, prazos de missões, treinos, diário de evolução e registros retroativos de hábitos.
- **Focus Mode:** sessões de 5, 25, 45 ou 60 minutos, com pausar, retomar, concluir e cancelar. O tempo é baseado em timestamps persistidos e é recuperado ao abrir o app novamente.
- **Anti-Procrastination Protocol:** motivos de evitação, resistência de 1 a 10, primeira ação recomendada e histórico de enfrentamento.
- **Projeto Liberdade:** objetivos, descrição, edição, etapas, conclusão e progresso percentual; diário de avanços mantido.
- **XP e conquistas:** pontuação por ações, histórico, níveis e conquistas por metas reais registradas.
- **Analytics:** produtividade, hábitos, foco, finanças, físico, procrastinação, taxa de enfrentamento, recomendações e snapshots de relatórios semanais.
- **Notificações:** lembretes locais para hábitos e avisos automáticos quando vencem prazos de missões, com a mensagem “Você esqueceu de fazer a …”. Um aviso por item/prazo; alarmes restaurados após reiniciar o celular.
- **Configurações:** metas pessoais, ativação/desativação de notificações locais, exportação e restauração de backup JSON.
- **Widgets:** saldo acumulado em largura total, com resultado do dia e mini tendência de sete dias; widget Mission Control ligado às missões reais, com linhas que se expandem conforme a altura disponível e até seis missões. Ambos usam fundo escuro translúcido. Saldo e missões atualizam ao salvar seus respectivos dados e ao abrir o app.

## Compatibilidade e persistência

- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- compileSdk / targetSdk 35
- minSdk 26 (Android 8.0)
- `applicationId`: `com.ritmo.planner` (preservado para manter a identidade do app-base)
- `versionCode`: 13 / `versionName`: 7.6
- Sem conta, servidor ou dependência de conexão para usar as funções locais.


**Atualização da instalação existente:** o package ID foi mantido. Para instalar por cima da versão atual sem desinstalar, o APK novo também precisa estar assinado com a mesma chave do APK instalado. APKs debug assinados por máquinas/ambientes diferentes podem usar chaves diferentes; nesse caso, o Android pode recusar a atualização. Antes de reinstalar ou trocar de aparelho, exporte um backup em Configurações.

## Compilar pelo GitHub Actions

1. Crie um repositório GitHub vazio.
3. Faça commit na branch `main`.
4. Abra a aba **Actions** e selecione **LIFE.EXE Android CI**.
5. A execução também começa automaticamente a cada push na branch `main`; para iniciar manualmente, use **Run workflow**.
6. Quando terminar, abra a execução e baixe o artefato **`life-exe-debug-apk`**.
7. Extraia o artefato e instale `app-debug.apk` no Android.

O workflow instala Java 17 e Android SDK 35, prepara o Gradle Wrapper e executa. A versão do app foi incrementada para 7.6, mantendo o mesmo `applicationId`:

```bash
./gradlew --no-daemon assembleDebug
```

## Compilar localmente (opcional)

Com JDK 17 e Android SDK 35 instalados, na raiz do projeto execute:

```bash
./gradlew --no-daemon assembleDebug
```

O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Widgets

1. Instale o APK.
2. Toque e segure uma área vazia da tela inicial do Android.
3. Abra **Widgets** e procure **LIFE.EXE**.
4. Adicione **Saldo — LIFE.EXE** (2×1) ou **Missões — LIFE.EXE** (2×2). Se já tinha os widgets antigos, remova-os da tela inicial e adicione-os novamente para aplicar as novas dimensões mínimas.

## Observações de validação

O projeto mantém o Gradle Wrapper e o workflow configurados. Neste ambiente de edição não há Android SDK instalado e a conexão a `services.gradle.org` está indisponível; por isso, a compilação real do APK deve ser confirmada no runner do GitHub Actions. XMLs, arquivos de configuração e estrutura do workflow podem ser verificados localmente antes do push, mas isso não substitui a compilação Android no CI.


## Atualização sem conflito de assinatura

O workflow aceita uma chave persistente por meio dos GitHub Actions Secrets:
`LIFE_EXE_KEYSTORE_BASE64`, `LIFE_EXE_KEYSTORE_PASSWORD`, `LIFE_EXE_KEY_ALIAS` e `LIFE_EXE_KEY_PASSWORD`.
A chave configurada precisa ser exatamente a mesma usada para assinar o APK atualmente instalado.
Sem esses secrets, o workflow usa a chave debug efêmera do runner e o Android pode recusar a atualização.
Não desinstale o aplicativo para contornar o erro antes de exportar e guardar um backup.

## LIFE.EXE 7.6

- A lista independente To-do foi removida da interface. O conteúdo antigo da chave `todos` permanece guardado nas preferências para não apagar dados históricos.
- O widget de saldo agora usa toda a largura para exibir o valor acumulado, sinaliza resultados negativos/positivos, mostra o líquido de hoje e mantém um mini gráfico de saldo acumulado dos últimos sete dias. Atualiza em toda gravação de receita ou gasto.
- O widget Mission Control lê as missões salvas, reconhece status com e sem acentos, aceita campos de título/prazo legados e calcula quantas linhas exibir a partir da altura máxima redimensionada. As linhas ocupam o espaço vertical disponível.
- O gráfico financeiro da tela inicial ganhou eixo de valores, grade temporal, área sombreada, linha de destaque e marcação da cotação final, em verde quando sobe e vermelho quando cai. A série é o saldo acumulado dia a dia do mês atual, calculado com as receitas e despesas registradas.
