# Свои патчи (ветка `custom`)

[English](custom-patches.md) | **Русский**

Ветка `custom` = upstream `main` + свои изменения. Каждое изменение в коде помечено тегами:

```
// <custom:имя-патча> Описание
свой код
// </custom:имя-патча>

// <custom-remove:имя-патча>
// закомментированный базовый код
// </custom-remove:имя-патча>
```

В `.properties` - те же теги через `#`, в JSX - `{/* ... */}`. Новые файлы целиком обернуты одним блоком `custom`
после лицензии. Проверка тегов: `pwsh -File build\check-custom-tags.ps1`.
Обновление из upstream: слить `upstream/main` в `custom`. Ветка хранится в форке
(`origin`, push разрешен только туда); upstream UMS - remote `upstream`, только fetch.
Строки интерфейса патчей - на всех языках UMS: английский (`messages.properties`) и русский пишутся вручную, остальные 38 переведены и стоят в конце каждого `messages_<язык>.properties` блоками в тегах своего патча. Новая строка патча - в английский и русский, затем перевод на остальные языки.
При каждом изменении обновлять и этот файл, и краткий `README-Custom.md`.
Тесты (JUnit 5, `src/test/java`, в тегах своего патча): `FullyPlayedLabelTest` (`playback-labels`),
`MacAddressesTest`, `MediaTableFilesStatusClearPlaybackTest` (`common`), `FolderManagerApiServletTest` (`folders-root`),
`PlaybackPositionTrackerTest` (`position-tracker`), `ControlFolderSettingsTest` (`reload-media`), `FolderResetActionTest`
(`folder-reset`); `ruid-no-mmap` проверяет штатный `ResourceIdentifierTest`. Запуск: `mvn test -P testing-windows`
(с `setup\build-tools.ps1`). Новый патч - новые тесты к нему.

Сводка:

| Патч | Что делает | Файлы |
|---|---|---|
| `release` | Релизы на GitHub форка: номер `<версия upstream>-custom.<N>` (например `15.9.0-custom.1`), установщик Windows x64 и portable zip; номер виден в веб-интерфейсе как версия | `project.properties`, `AboutApiServlet`; `build/release.ps1` (новый), `build/build.ps1 -CustomVersion`, `release-custom.bat` в корне рабочей папки |
| `build-scripts` | Скрипты сборки в `build/`, выходные файлы в `.gitignore`; коммит ветки как `git.commit.id` (с незакоммиченными изменениями - `<коммит>-dirty-<время>`, иначе браузер держит старые переводы); только установщик x86_64 (x86 выключен в `pom.xml`, экономит ~4 мин) | `build/*`, `.gitignore`, `pom.xml` |

Все настройки своих патчей: Server Settings -> **Custom Settings** -> Root folders
(рус.: "Свои настройки" -> "Папки в корне"):

```
[x] Show the "Folders" folder                    show_folders_container
    Name of the "Folders" folder (empty = default)   folders_container_name
[ ] Allow disk operations on the "Folder management" page   allow_disk_operations
[x] Use the Windows Recycle Bin when deleting                use_recycle_bin
    Label position   [At the start: [*] name.ext v]  playback_label_position
[x] Show the progress label [h:mm:ss]            show_progress_label
    Started from (% of the duration)    5        progress_label_min_percent
[x] Show the played label [*]                    show_played_label
    Played from (% of the duration)    95        played_label_percent
    Clear the label after playing from the start for (seconds)  2   label_reset_seconds
[x] Show the "Reset watched" item at the end of folders              show_folder_reset_item
```

Раздел The "Control" folder (рус.: "Папка «Управление»"):

```
[x] Show the "Control" folder in the root        show_control_folder
[x] Show "Reload media library"                  show_reload_media_item
[x] Show "Restart server"                        show_restart_server_item
[x] Show "Hibernate computer"                    show_hibernate_item
[x] Show "Shut down computer"                    show_shutdown_item
```

Раздел Server UUID (рус.: "UUID сервера"), патч `server-uuid`:

```
<текущий UUID>  [New server UUID]           -> uuid (новый случайный) + перезапуск медиасервера
```

---

## release

### Суть работы

Публикация сборок Windows x64 на странице Releases форка. Запуск - `release-custom.bat` в корне рабочей папки
(обертка над `source-custom\build\release.ps1`):

1. Проверки: ветка `custom`, нет локальных изменений, ветка отправлена (`HEAD` = `origin/custom`), теги в порядке,
   `gh` авторизован.
2. Номер: версия из `pom.xml` без `-SNAPSHOT` + следующий номер по тегам форка: `v15.9.0-custom.1`, `.2`, ...;
   новая версия upstream снова начинается с `.1`.
3. Сборка `build.ps1 -Target all -CustomVersion <номер>`: номер уходит в `project.custom.version`
   (`-Dcustom.version`), API "About" отдает его как версию - он виден на странице "О программе" и в строке
   состояния; установщик называется `UMS-Windows-<номер>-x86_64.exe`.
4. Файлы в `build\release\<номер>\`: установщик и `UMS-Windows-<номер>-x86_64-portable.zip` (папка программы +
   пустая `portable\`: настройки рядом с программой), `release-notes.md` (что за редакция + коммиты с прошлого
   релиза).
5. Вопрос (без `-Yes`), затем аннотированный тег, push тега в `origin`, `gh release create` с двумя файлами.

Ключи: `-DryRun` - только собрать и подготовить файлы, `-Yes` - без вопроса, `-Offline` - для `build.ps1`.

### Настройки в UI

Нет.

### Настройки в conf (`UMS.conf`)

Нет.

---

