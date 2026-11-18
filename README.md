# LIFE.EXE — Android

Sistema pessoal de execução e disciplina com identidade Cyber Core / Matrix. Aplicativo Android nativo em Java, com armazenamento local SQLite e sem dependências de bibliotecas de terceiros.

## Compilar pelo GitHub Actions

1. Crie um repositório no GitHub e envie o conteúdo desta pasta para a branch `main` (ou `master`).
2. Abra **Actions** no repositório. O workflow **Android CI** executa automaticamente em push, pull request e também pode ser iniciado manualmente.
3. Quando a execução terminar com sucesso, abra a execução e baixe o artefato **LIFE-EXE-debug-apk**. O arquivo gerado é `app-debug.apk`.

O workflow instala JDK 17, Android SDK Platform 35 e Build Tools 35.0.0, executa `./gradlew --no-daemon assembleDebug` e publica o APK como artefato por 14 dias.

## Compilar localmente

Requisitos: JDK 17, conexão com a internet na primeira execução e Android SDK Platform 35 / Build Tools 35.0.0.

```bash
./gradlew --no-daemon assembleDebug
```

O APK fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`gradlew` e `gradlew.bat` são scripts de bootstrap que baixam e usam a distribuição Gradle 8.9 indicada em `gradle/wrapper/gradle-wrapper.properties`. Assim, não é necessário ter Gradle instalado globalmente.

## Funcionalidades incluídas

- **Dashboard:** saldo total e diário, receitas/despesas, flexões, barras, objetivos, missões prioritárias, tarefas e resumo de disciplina.
- **Mission Control / To-do:** cadastro, edição, exclusão, conclusão com XP, prioridade, dificuldade, prazo e adiamento com justificativa.
- **Habit System:** hábitos, meta normal e mínima, frequência, registros diários e contagem de dias registrados sem apagar o histórico após uma falha.
- **Focus Mode:** sessões de 5, 25, 45 ou 60 minutos, tarefa vinculada, pausa, retomada, cancelamento e registro de sessões concluídas.
- **Anti-Procrastination:** registro da tarefa evitada, motivo, resistência de 1 a 10 e primeira ação concreta; início do protocolo concede XP.
- **Projeto Liberdade:** objetivos com alvo numérico, progresso e registro de avanços.
- **Finanças e corpo:** lançamentos de receita/despesa e registros de flexões/barras, com edição e exclusão.
- **XP e conquistas:** ledger de XP, nível calculado, histórico e medalhas baseadas em marcos registrados.
- **Analytics e relatório semanal:** indicadores agregados, taxa de enfrentamento operacional e relatório compartilhável com dados dos últimos sete dias.
- **Backup:** exportação e restauração de JSON via seletor de arquivos Android; a restauração pede confirmação antes de substituir os dados locais.

## Persistência e privacidade

Os dados operacionais ficam no banco SQLite privado do aplicativo. O app não exige conta, login, conexão com servidor ou permissões de rede. Use **Configurações → Exportar backup JSON** para manter uma cópia externa dos registros.

## Estrutura

```text
.
├── .github/workflows/android.yml
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/lifeexe/app/MainActivity.java
│       ├── java/com/lifeexe/app/data/DatabaseHelper.java
│       └── res/values/styles.xml
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── build.gradle
└── settings.gradle
```

## Observações da versão 2.0 inicial

- A aplicação usa SQLite local. Sincronização entre dispositivos e conta online não fazem parte desta entrega.
- Lembretes/notificações agendadas, calendário visual de hábitos e gráficos históricos com séries temporais ainda não estão implementados; os indicadores disponíveis são calculados a partir dos registros reais.
- O cronômetro de foco mantém a sessão em memória enquanto a atividade está aberta; uma sessão é gravada no histórico ao ser concluída. Fechar o processo durante uma sessão não restaura o cronômetro automaticamente.
- A validação de compilação deve ser confirmada no GitHub Actions. O ambiente em que este projeto foi preparado não contém Android SDK e não tem acesso à rede para baixar o Gradle, portanto nenhum APK foi gerado localmente nesta sessão.
