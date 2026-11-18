# LIFE.EXE — Discipline Protocol

Aplicativo Android nativo em Kotlin, atualizado sobre o projeto-base RITMO. A estrutura original, o `applicationId` (`com.ritmo.planner`), as preferências locais existentes e os providers dos widgets foram preservados. A interface foi convertida para o visual **Cyber Core / Matrix**: preto profundo, verde neon, painéis HUD, tipografia monoespaçada e navegação inferior.

## Módulos

- **Dashboard:** resumo financeiro, treino, missões do dia, hábitos, foco, objetivos e tarefas.
- **Financeiro:** cadastro, edição e exclusão de receitas e despesas; saldo total, saldo do dia, evolução de sete dias e meta mensal configurável.
- **Físico:** registro, edição e exclusão de flexões e barras, totais acumulados e gráfico de evolução.
- **To-do:** tarefas com categoria, prioridade, prazo e horário; conclusão, edição, exclusão e identificação de atrasadas. O widget de tarefas original continua disponível.
- **Mission Control:** missões por categoria, prioridade, dificuldade, prazo e horário; conclusão, início, adiamento com motivo e registro de abandono. Limite de três missões prioritárias ativas por dia.
- **Habit System:** frequência diária, dias úteis ou semanal; meta normal e mínima; lembretes locais; calendário de 28 dias; sequência atual, melhor sequência e consistência.
- **Focus Mode:** sessões de 5, 25, 45 ou 60 minutos, com pausar, retomar, concluir e cancelar. O tempo é baseado em timestamps persistidos e é recuperado ao abrir o app novamente.
- **Anti-Procrastination Protocol:** motivos de evitação, resistência de 1 a 10, primeira ação recomendada e histórico de enfrentamento.
- **Projeto Liberdade:** objetivos, descrição, edição, etapas, conclusão e progresso percentual; diário de avanços mantido.
- **XP e conquistas:** pontuação por ações, histórico, níveis e conquistas por metas reais registradas.
- **Analytics:** produtividade, hábitos, foco, finanças, físico, procrastinação, taxa de enfrentamento, recomendações e snapshots de relatórios semanais.
- **Configurações:** metas pessoais, notificações locais, exportação e restauração de backup JSON.
- **Widgets:** saldo e tarefas na tela inicial, mantidos a partir do projeto original e reestilizados com a identidade LIFE.EXE.

## Compatibilidade e persistência

- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- compileSdk / targetSdk 35
- minSdk 26 (Android 8.0)
- `applicationId`: `com.ritmo.planner` (preservado para manter a identidade do app-base)
- `versionCode`: 7 / `versionName`: 7.0
- Sem conta, servidor ou dependência de conexão para usar as funções locais.

Os dados originais em `SharedPreferences` continuam usando as chaves `income`, `expenses`, `todos`, `workouts` e `social`. Os novos módulos usam novas chaves no mesmo arquivo de preferências `ritmo_data`; não há migração destrutiva das chaves antigas. O backup JSON inclui as preferências do app, inclusive os dados novos.

**Atualização da instalação existente:** o package ID foi mantido. Para instalar por cima da versão atual sem desinstalar, o APK novo também precisa estar assinado com a mesma chave do APK instalado. APKs debug assinados por máquinas/ambientes diferentes podem usar chaves diferentes; nesse caso, o Android pode recusar a atualização. Antes de reinstalar ou trocar de aparelho, exporte um backup em Configurações.

## Compilar pelo GitHub Actions

1. Crie um repositório GitHub vazio.
2. Extraia este ZIP e envie **todo o conteúdo da pasta** (a pasta `app`, `.github`, `gradle`, `gradlew`, `settings.gradle.kts` etc.) para a raiz do repositório.
3. Faça commit na branch `main`.
4. Abra a aba **Actions** e selecione **LIFE.EXE Android CI**.
5. A execução também começa automaticamente a cada push na branch `main`; para iniciar manualmente, use **Run workflow**.
6. Quando terminar, abra a execução e baixe o artefato **`life-exe-debug-apk`**.
7. Extraia o artefato e instale `app-debug.apk` no Android.

O workflow instala Java 17 e Android SDK 35, prepara o Gradle Wrapper e executa. A versão do app foi incrementada para 7.0, mantendo o mesmo `applicationId`:

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
4. Adicione **Saldo — LIFE.EXE** (2×1) ou **Tarefas — LIFE.EXE** (2×2).

## Observações de validação

O projeto mantém o Gradle Wrapper e o workflow configurados. Neste ambiente de edição não há Android SDK instalado e a conexão a `services.gradle.org` está indisponível; por isso, a compilação real do APK deve ser confirmada no runner do GitHub Actions. XMLs, arquivos de configuração e estrutura do workflow podem ser verificados localmente antes do push, mas isso não substitui a compilação Android no CI.
