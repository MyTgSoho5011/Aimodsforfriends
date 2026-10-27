# Aimodsforfriends

Fabric-моды для Minecraft **1.21.11** (Java 21). Проект — многомодульный Gradle-билд,
каждый мод собирается в свой jar.

| Модуль | Что это | Артефакт |
|---|---|---|
| `codeforge` | In-game Java-консоль: пишешь код, он компилируется ECJ прямо в игре и выполняется | `codeforge/build/libs/codeforge-3.0.0.jar` (внутри уже лежат `soho-api` и `ecj`) |
| `soho-api` | Маленькая общая библиотека для «сохо»-модов (`SohoRegistry`) | `soho-api/build/libs/soho-api-1.0.0.jar` |
| `codeforge-plugin-api` | API для плагинов CodeForge (обычная Java-библиотека, без Minecraft) | `codeforge-plugin-api/build/libs/codeforge-plugin-api-1.0.0.jar` |

## Как собрать

Нужен **JDK 21** (Gradle сам скачает нужную тулчейн-версию, если её нет) и интернет
(артефакты Minecraft/Fabric/Loom тянутся с `maven.fabricmc.net` и Mojang).

```bash
# всё сразу
./gradlew build

# только CodeForge (быстрее, если остальное не нужно)
./gradlew :codeforge:build

# разложить готовые jar-ники в build/dist
./gradlew build collectJars
```

Раньше в репозитории не было `gradle/wrapper/*`, поэтому `./gradlew` не запускался —
теперь wrapper добавлен (Gradle 9.8.2). Если предпочитаешь системный Gradle:

```bash
gradle build
```

Готовые моды кладутся в `.minecraft/mods`:

* `codeforge-3.0.0.jar` — **один файл**, `soho-api` и компилятор ECJ вложены внутрь
  (`META-INF/jars`), отдельно их ставить не обязательно;
* нужен установленный **Fabric Loader** и **Fabric API**.

## CodeForge: что внутри

Открыть консоль — клавиша `]` (или кнопка в Mod Menu, если он установлен).

Вкладки: `Editor` (код + Compile & Run / Save as... / Copy / Paste / Export .java),
`Tutorial`, `My Scripts` (с версиями скриптов), `History`, `Mods`, `Textures`
(пиксель-арт редактор 32×32, сохраняет PNG в `config/codeforge/textures/`), `Plugins`.

Как это работает: тело метода → ECJ (Eclipse Compiler for Java, **чистая Java-библиотека**,
никакого внешнего `javac`/JDK не нужно) → класс грузится своим `ClassLoader` →
выполняется в отдельном потоке с таймаутом 5 с. Перед запуском код прогоняется через
`DangerousCallScanner` (System.exit, запуск процессов, удаление файлов, рефлексия и т.п.);
если что-то нашлось — показывается диалог подтверждения.

Файлы в `config/codeforge/`: `scripts.json` (сохранённые скрипты + история версий),
`history.json` (журнал запусков), `keybinds.json` (привязка скриптов к F7–F10/F12),
`plugins/` (плагины `*.jar` через `ServiceLoader`), `textures/` (PNG из редактора).

## Что пришлось восстановить

`codeforge-workspace.zip` — это 7-Zip-архив (LZMA), обычный `unzip`/проводник Windows
его не открывает; распакован через Python (`py7zr`). В архиве лежали только 6 java-файлов,
остальные каталоги были пустыми, но код ссылался на ~14 отсутствующих классов. Они
написаны заново в совместимом стиле (те же пакеты/имена/сигнатуры, что ожидает
`CodeForgeClient` и `CodeForgeScreen`):

* `scripts/` — `ScriptLibrary` (папки, скрипты, версии), `RunHistory`, `ScriptContext`;
* `plugin/` — `PluginRegistry`, `PluginLoader` (плагины из `config/codeforge/plugins`);
* `keybinds/` — `KeyBindingManager` (5 слотов быстрого запуска, F7–F10/F12);
* `safety/` — `DangerousCallScanner`;
* `mods/` — `ModListHelper`;
* `gui/` — `HighlightedCodeArea`, `ConfirmDialogScreen`, `TextInputPromptScreen`,
  `ScriptDetailScreen`, `TextureEditorScreen`.

Исходники разделены на два source-каталога: `codeforge/src/main/java` (GUI и точка входа)
и `codeforge/src/core/java` (всё остальное).

## Ограничения / что стоит знать

1. **Jar-ники не собраны в этой сессии.** Песочница, где правился проект, имеет доступ
   только к GitHub/PyPI/npm — `maven.fabricmc.net` и `maven.mojang.com` недоступны, поэтому
   Gradle не может скачать Minecraft/Loom. Проверено: синтаксис всех 26 `.java`-файлов
   (парсер Java), конфигурация модулей, отсутствие «висячих» ссылок между классами.
   Первый реальный `./gradlew build` нужно сделать у себя (или в CI — см. workflow ниже).
2. **Компиляция скриптов в рантайме рассчитана на dev-окружение** (`runClient`/IDE), где
   Minecraft использует yarn-имена: в релизной игре классы называются по intermediary,
   и скрипты с `MinecraftClient.player` там не скомпилируются. Это ограничение подхода
   «скомпилировать на лету внутри мода», а не конкретной реализации.
3. Редактор — однострочный `EditBox` (переносы строк в тексте сохраняются и компилируются,
   но подсветки/нумерации строк нет). Полноценная отрисовка кода ломается от версии к версии.
4. `Mod Menu` подключён как `compileOnly`: без него мод работает, просто нет кнопки настроек.
5. Версия Loom в корне (`1.14-SNAPSHOT`) взята из исходного `build.gradle` архива. Если
   Gradle ругается на `modImplementation`, замени его на `implementation` — в новых Loom
   API это синоним, а сам `fabric-loom` мог переехать на id `net.fabricmc.fabric-loom`.

## CI

`.github/workflows/build.yml` собирает все моды на push/PR и складывает jar-ники в артефакты
GitHub Actions — удобно, если локально нет JDK/Gradle.
