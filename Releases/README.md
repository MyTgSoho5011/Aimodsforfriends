# Releases

Готовые jar-ники, собранные CI (см. `.github/workflows/build.yml`).
Кладите файлы в `.minecraft/mods`.

| Папка | Файл | Что это |
|---|---|---|
| `codeforge/3.0.1/` | `codeforge-3.0.1.jar` | Консоль CodeForge. Внутри уже `soho-api` и ECJ (jar-in-jar) — ставить один файл |
| `soho-api/1.0.0/` | `soho-api-1.0.0.jar` | Soho API отдельным файлом (необязательно, если стоит codeforge) |
| `codeforge-plugin-api/1.0.0/` | `codeforge-plugin-api-1.0.0.jar` | API для плагинов CodeForge (в игру не ставится) |

Собрано под Minecraft **1.21.11**, Fabric Loader 0.18.4+, Java 21.
