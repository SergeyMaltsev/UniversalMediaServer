<!-- <custom:readme-custom> Whole file: what the custom fork changes, for users (English and Russian) -->
# Universal Media Server - Custom edition

[English](#english) | [Русский](#русский)

## English

**The idea:** your TV shows the folders exactly as they are on the server's disks, you always see what you have
already watched, and you manage the folders from the browser - without renaming or moving files by hand.

### 1. Folders as they are on the disk

- All shared folders sit together in one **"Folders"** folder on the TV (you can name it yourself).
- Several disk folders can be joined under one name: e.g. **"Series"** from two disks shows as one folder.
- The "Scan media library" button reads these folders too.

### 2. Watched marks

- The marks are right in the titles: `[*] Movie.mkv` - watched, `[1:02:15] Movie.mkv` - stopped at 1:02:15.
- The mark can go at the start or at the end of the title; you choose from what percent a file counts as
  started and as watched, and can switch each mark off.
- When the TV reports where you stopped, that exact time is saved. Starting a file from the beginning clears its mark.
- **"Reset watched"** - the last item of every folder on the TV: clears the marks of all files in it.

### 3. Folder management in the browser

- The **"Folder management"** page: the folder tree with file counts, sizes and watched files; the file list with
  marks, duration, codec and resolution; search by name.
- Mark files watched / unwatched; delete watched or selected files and whole folders - to the Recycle Bin or for
  good, with safety checks (shared folders and links are never deleted).

### 4. Server control from the TV

- The **"Control"** folder at the end of the list: reload the media library, restart the server, hibernate or shut
  down the computer - right from the TV remote. Each item can be switched off.

### 5. Devices

- Rename a TV, see its IP and MAC address (with a copy button) and whether it is playing or paused.

### 6. Interface and settings

- **"Custom Settings"** tab in Server Settings: every option of the fork in one place.
- Side menu width by dragging its edge, a red "Restart server" button in the header, the version and build time at
  the bottom, the fork's own logo, the "Custom edition" tab on the About page.
- All new texts are translated into every language of UMS.

### 7. Fixes

- Changing a setting applies to all folders at once, without a server restart.
- A folder bookmarked on the TV opens right after the server restarts.
- New files are no longer kept locked by the server: they can be moved or deleted at once.

### For developers

Branch `custom` = upstream `main` + patches; every change in the code is marked `<custom:name>` ...
`</custom:name>`. Per patch details, settings and `UMS.conf` keys: [custom-patches.md](custom-patches.md).

| Group | Patches |
|---|---|
| 1. Folders | `folders-root`, `virtual-folders` |
| 2. Watched marks | `playback-labels`, `position-tracker`, `folder-reset` |
| 3. Folder management | `folders-root` |
| 4. Server control | `reload-media` |
| 5. Devices | `common` |
| 6. Interface and settings | `custom-settings`, `common`, `server-uuid`, `logo`, `readme-custom` |
| 7. Fixes | `settings-reload`, `deep-browse`, `ruid-no-mmap` |
| Build and releases | `build-scripts` (`build/build.bat`, Windows x64 only), `release` (`build/release.ps1`: GitHub release `<upstream version>-custom.<N>`, installer + portable zip) |

Tests: JUnit 5 in `src/test/java` (`mvn test -P testing-windows`) - `FullyPlayedLabelTest`, `MacAddressesTest`,
`MediaTableFilesStatusClearPlaybackTest`, `FolderManagerApiServletTest`, `PlaybackPositionTrackerTest`,
`ControlFolderSettingsTest`, `FolderResetActionTest`. Update from upstream: merge `upstream/main` into `custom`.

## Русский

**Идея:** телевизор показывает папки ровно так, как они лежат на дисках сервера, всегда видно, что уже
просмотрено, а управлять папками можно из браузера - без ручного переименования и перекладывания файлов.

### 1. Папки как на диске

- Все общие папки собраны на телевизоре в одной папке **"Папки"** (имя можно задать свое).
- Несколько папок с дисков можно объединить под одним именем: например, **"Сериалы"** с двух дисков видны
  одной папкой.
- Кнопка "Сканировать медиатеку" читает и эти папки.

### 2. Отметки просмотра

- Отметки прямо в названиях: `[*] Фильм.mkv` - просмотрен, `[1:02:15] Фильм.mkv` - остановлен на 1:02:15.
- Отметку можно ставить в начале или в конце названия; с какого процента файл считается начатым и просмотренным -
  задается в настройках, каждую отметку можно выключить.
- Когда телевизор сообщает, где вы остановились, сохраняется именно это время. Запуск файла с начала снимает
  отметку.
- **"Сбросить просмотренное"** - последний пункт каждой папки на телевизоре: снимает отметки со всех ее файлов.

### 3. Управление папками в браузере

- Страница **"Управление папками"**: дерево папок с количеством файлов, размером и просмотренными; список файлов
  с отметками, длительностью, кодеком и разрешением; поиск по названию.
- Пометить файлы просмотренными / непросмотренными; удалить просмотренные или выбранные файлы и целые папки - в
  Корзину или навсегда, с проверками безопасности (общие папки и ссылки никогда не удаляются).

### 4. Управление сервером с телевизора

- Папка **"Управление"** в конце списка: обновить медиатеку, перезапустить сервер, гибернация или выключение
  компьютера - прямо с пульта. Каждый пункт можно выключить.

### 5. Устройства

- Переименовать телевизор, увидеть его IP и MAC-адрес (с кнопкой копирования) и что он сейчас играет или стоит
  на паузе.

### 6. Интерфейс и настройки

- Вкладка **"Свои настройки"** в настройках сервера: все параметры форка в одном месте.
- Ширина бокового меню - перетаскиванием края, красная кнопка "Перезапустить сервер" в заголовке, версия и время
  сборки внизу, собственный логотип форка, вкладка "Custom edition" на странице "О программе".
- Все новые тексты переведены на все языки UMS.

### 7. Исправления

- Изменение настройки сразу действует во всех папках, без перезапуска сервера.
- Папка из закладки на телевизоре открывается сразу после перезапуска сервера.
- Новые файлы больше не остаются занятыми сервером: их сразу можно переместить или удалить.

### Для разработчиков

Ветка `custom` = upstream `main` + патчи; каждое изменение в коде помечено `<custom:имя>` ... `</custom:имя>`.
Подробности, настройки и ключи `UMS.conf` по каждому патчу: [custom-patches.ru.md](custom-patches.ru.md).

| Группа | Патчи |
|---|---|
| 1. Папки | `folders-root`, `virtual-folders` |
| 2. Отметки просмотра | `playback-labels`, `position-tracker`, `folder-reset` |
| 3. Управление папками | `folders-root` |
| 4. Управление сервером | `reload-media` |
| 5. Устройства | `common` |
| 6. Интерфейс и настройки | `custom-settings`, `common`, `server-uuid`, `logo`, `readme-custom` |
| 7. Исправления | `settings-reload`, `deep-browse`, `ruid-no-mmap` |
| Сборка и релизы | `build-scripts` (`build/build.bat`, только Windows x64), `release` (`build/release.ps1`: релиз на GitHub `<версия upstream>-custom.<N>`, установщик + portable zip) |

Тесты: JUnit 5 в `src/test/java` (`mvn test -P testing-windows`) - `FullyPlayedLabelTest`, `MacAddressesTest`,
`MediaTableFilesStatusClearPlaybackTest`, `FolderManagerApiServletTest`, `PlaybackPositionTrackerTest`,
`ControlFolderSettingsTest`, `FolderResetActionTest`. Обновление из upstream: слить `upstream/main` в `custom`.
<!-- </custom:readme-custom> -->
