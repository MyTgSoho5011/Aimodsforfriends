# Aimodsforfriends

Fabric-моды для Minecraft **1.21.11** (Java 21). Проект — многомодульный Gradle-билд,
каждый мод собирается в свой jar. **Сборка проверена CI: `.github/workflows/build.yml` зелёный.**

| Модуль | Что это | Артефакт |
|---|---|---|
| `codeforge` | In-game Java-консоль: пишешь код, он компилируется ECJ прямо в игре и выполняется | `codeforge/build/libs/codeforge-3.0.0.jar` (внутри `META-INF/jars` уже лежат `soho-api` и `ecj`) |
| `soho-api` | Маленькая общая библиотека для «сохо»-модов (`SohoRegistry`) | `soho-api/build/libs/soho-api-1.0.0.jar` |
| `codeforge-plugin-api` | API для плагинов CodeForge (обычная Java-библиотека, без Minecraft) | `codeforge-plugin-api/build/libs/codeforge-plugin-api-1.0.0.jar` |

## Как получить готовый jar

**Вариант 1 — забрать из CI (ничего ставить не нужно):**
Actions → `Build mods` → последний зелёный запуск → блок **Artifacts** → `mods`.
Там лежат все три jar-ника. Прямая ссылка на запуски:
<https://github.com/MyTgSoho5011/Aimodsforfriends/actions/workflows/build.yml>

**Вариант 2 — собрать локально:**

```bash
./gradlew build            # или: gradle build
./gradlew build collectJars   # то же + сложить всё в build/dist
```

Нужен JDK 21 и интернет (Minecraft, Yarn, Fabric API, ECJ тянутся при первой сборке;
дальше работает кеш Gradle).

Ставится в `.minecraft/mods` **один файл** `codeforge-3.0.0.jar` — `soho-api` и компилятор
ECJ вложены внутрь. Нужны Fabric Loader и Fabric API.

## CodeForge: что внутри

Открыть консоль — клавиша `]`.

Вкладки: `Editor` (код + Compile & Run / Save as... / Copy / Paste / Export .java),
`Tutorial`, `My Scripts` (с версиями скриптов), `History`, `Mods`, `Textures`
(пиксель-арт редактор 32×32, сохраняет PNG в `config/codeforge/textures/`), `Plugins`.
Горячие клавиши в редакторе: `Ctrl+Enter` — запуск, `Ctrl+S` — сохранить.
Быстрый запуск сохранённых скриптов — `F7`…`F10`, `F12` (переназначаются в настройках управления).

Как это работает: тело метода → ECJ (Eclipse Compiler for Java, **чистая Java-библиотека**,
никакого внешнего `javac`/JDK не нужно) → класс грузится своим `ClassLoader` →
выполняется в отдельном потоке с таймаутом 5 с. Перед запуском код прогоняется через
`DangerousCallScanner` (System.exit, запуск процессов, удаление файлов, рефлексия и т.п.);
если что-то нашлось — показывается диалог подтверждения.

Файлы в `config/codeforge/`: `scripts.json` (сохранённые скрипты + история версий),
`history.json` (журнал запусков), `keybinds.json` (привязка скриптов к клавишам),
`plugins/` (плагины `*.jar` через `ServiceLoader`), `textures/` (PNG из редактора).

## Что было восстановлено из архива

`codeforge-workspace.zip` — 7-Zip-архив (LZMA), обычный `unzip` его не открывает; распакован
через Python (`py7zr`). Внутри лежали только 6 java-файлов, остальные каталоги были пустыми,
но код ссылался на ~14 отсутствующих классов. Они написаны заново в совместимом стиле
(те же пакеты/имена/сигнатуры, что ожидают `CodeForgeClient` и `CodeForgeScreen`):

* `scripts/` — `ScriptLibrary` (папки, скрипты, версии), `RunHistory`, `ScriptContext`, `ScriptExporter`;
* `plugin/` — `PluginRegistry`, `PluginLoader` (плагины из `config/codeforge/plugins`);
* `keybinds/` — `KeyBindingManager` (5 слотов быстрого запуска);
* `safety/` — `DangerousCallScanner`;
* `mods/` — `ModListHelper`;
* `gui/` — `ConfirmDialogScreen`, `TextInputPromptScreen`, `ScriptDetailScreen`, `TextureEditorScreen`.

Исходники разделены на два source-каталога: `codeforge/src/main/java` (GUI и точка входа)
и `codeforge/src/core/java` (всё остальное).

## Что пришлось поправить под 1.21.11 (реальные ошибки компиляции)

1. **Loom 1.14 разделил id плагинов.** `fabric-loom` — legacy; для обфусцированных версий
   (1.21.11 и старше) нужен `net.fabricmc.fabric-loom-remap`. Версия объявлена один раз в
   `settings.gradle` (`pluginManagement.plugins`).
2. **Access widener'ы Fabric API больше не совместимы с Yarn-сборкой:** часть модулей
   объявляет `classTweaker v1 official`, а Loom с Yarn ждёт `intermediary`. Наш код не
   использует расширяемые члены → в `codeforge/build.gradle` стоит
   `enableTransitiveAccessWideners = false`.
3. **`soho-api` стал обычной `java-library`** (он не трогает классы Minecraft). Иначе Loom
   на этапе конфигурации пытался прочитать dev-jar подпроекта, которого ещё нет на чистом клоне.
4. **API 1.21.11:** `KeyBinding` принимает `KeyBinding.Category.MISC` вместо строки;
   `EditBox` стал моделью текста, виджет — `EditBoxWidget` (создаётся билдером, наследовать
   нельзя — конструктор закрыт); ввод теперь приходит объектами `KeyInput`/`Click`
   (`keyPressed(KeyInput)`, `mouseClicked(Click, boolean)`), а не тремя `int`/`double`.
5. **Mod Menu отключён:** его jar для 1.21.11 собран под Java 25 (class file 69), а игра и
   наш билд — Java 21, javac такой класс прочитать не может. Мод работает без него,
   пропадает только кнопка настроек в Mod Menu. Вернём, когда появится сборка под 21.

## Ограничения

* **Компиляция скриптов в рантайме рассчитана на dev-окружение** (`runClient`/IDE), где
  Minecraft использует yarn-имена: в релизной игре классы называются по intermediary,
  и скрипты с `MinecraftClient.player` там не скомпилируются. Это ограничение подхода
  «скомпилировать на лету внутри мода», а не конкретной реализации.
* Редактор — многострочный `EditBoxWidget`: переносы строк и ввод работают, подсветки
  синтаксиса нет (только отметка «compile error near line N»).
* Тесты/`runClient` в CI не запускаются — только сборка.

## CI

`.github/workflows/build.yml`: JDK 21 + Gradle 9.8.2, собирает все модули, проверяет
раскладку jar-ника (классы, `fabric.mod.json`, вложенные `soho-api`/`ecj`), складывает
jar-ники в артефакт `mods`. На падении печатает выжимку ошибок и `javap`-дамп API
Minecraft аннотациями чек-рана.
