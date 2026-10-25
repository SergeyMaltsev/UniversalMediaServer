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
| `common` | Общие улучшения, не связанные с плагинами: перетаскиваемый край левой панели; карточки устройств: переименование, MAC, значок воспроизведения, 3 в ряд; красная кнопка перезапуска сервера в шапке | `NavbarSplitter.tsx`, `RestartServerButton.tsx`, `RendererRenameModal.tsx`, `RendererNames`, `MacAddresses` (новые), `UmsAppShell.tsx`, `Renderers.tsx`, `home-service.ts`, `Renderer`, `RendererItem`, `RenderersApiServlet`, `UmsConfiguration`, `messages*.properties` |
| `logo` | Свой логотип вместо логотипа UMS: в начале заголовка веб-интерфейса, иконки веб-интерфейса и вкладки браузера, значок сервера на ТВ (DLNA), окно и трей, `UMS.exe` и установщик, `README.md` | `UmsAppShell.tsx`; картинки `react-client/public/*`, `images/icon-*`, `icon-square-*`, `logo.*`, `external-resources/icon.ico`; `README.md` |
| `release` | Релизы на GitHub форка: номер `<версия upstream>-custom.<N>` (например `15.9.0-custom.1`), установщик Windows x64 и portable zip; номер виден в веб-интерфейсе как версия | `project.properties`, `AboutApiServlet`; `build/release.ps1` (новый), `build/build.ps1 -CustomVersion`, `release-custom.bat` в корне рабочей папки |
| `readme-custom` | `README-Custom.md` - что меняет форк, для пользователей (по группам изменений, английский и русский), ссылка в `README.md` ("Custom fork"); вкладка "Custom edition" на странице "О программе" показывает этот файл (Markdown -> HTML через showdown, раздел по языку интерфейса: русский или английский) | `README-Custom.md` (новый), `README.md`, `About.tsx`, `CustomEdition.tsx` (новый), `messages*.properties`; зависимость `showdown` и `@types/showdown` в `react-client/package.json` и `yarn.lock` (в JSON тегов нет) |
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

## common

Общие улучшения веб-интерфейса, не связанные с плагинами.

### Суть работы

- Перетаскиваемый край левой панели (навигация настроек и плеера), `NavbarSplitter`: тонкая полоса на внутреннем
  крае панели, курсор "изменить ширину". Ширина 100..500 px (как у ползунков "Navbar width" на странице
  Customize). Хранится в тех же штатных ключах браузера `mantine-navbar-width-sm/md/lg` - отдельно для малого,
  среднего и большого экрана (меняется ширина текущего); ползунки Customize показывают то же значение.
- На узком экране (меньше `sm`, панель выезжает по кнопке) края нет. Для письма справа налево край слева.
- Красная кнопка-иконка перезапуска сервера у правого края шапки, после меню пользователя
  (`RestartServerButton`, подсказка "Restart server" / "Перезапуск сервера"): то же действие `Server.Restart` и то же окно подтверждения, что у кнопки на странице
  "Инструменты" (перезапуск медиасервисов, не приложения). Видна при праве "перезапуск сервера", не в режиме плеера.
- Кнопки-иконки копирования после IP и MAC в карточке (`CopyValueButton`, штатный `CopyButton`), после копирования
  на секунду галочка. Копирование в буфер требует безопасного контекста: работает на `http://localhost`, при
  открытии интерфейса по IP в сети браузер может его не разрешить.
- Пункт меню "Управление учётными записями" по-русски называется короче - "Учётные записи" (`ManageAccounts` в
  `messages_ru.properties`; английский не меняется).
- Переименование устройства (Home -> карточка -> меню "..." -> "Rename" / "Переименовать"): свое имя
  показывается вместо имени, которое сообщает устройство (UPnP `friendlyName`, например два "movian").
  `RendererNames` хранит имена по UUID в `UMS.conf` (`renderers_name`, как штатный `renderers_user`: JSON с `"`
  записанным как `|`, поэтому `"` и `|` из имени убираются). `Renderer.getRendererName()` отдает свое имя первым -
  оно видно везде (карточки, логи, плеер). У переименованного в карточке после имени `*`, подсказка при
  наведении - имя устройства. Пустое имя или кнопка "Device name" возвращают имя устройства.
- MAC-адрес в карточке под IP (`MacAddresses`): из системной таблицы ARP (`arp -a <ip>`, на Linux/macOS с `-n`),
  кэш по IP. Поиск не задерживает обновление карточки: `arp` идет в фоне (один на IP, не дольше 5 с, неудачная
  попытка повторяется не чаще раза в минуту), найденный MAC сам обновляет карточку. Для этого компьютера и
  выключенных устройств пусто.
- Нижняя строка состояния видна всегда: слева прежние сообщения, справа "Universal Media Server <версия> custom
  edition - <дата и время сборки>". Время сборки - местное, `build\build.ps1` передает `-Dbuild.time`, в
  `project.properties` это `project.build.time`, API "About" отдает `buildTime` (пусто при сборке без build.ps1).
- Помощники статуса для своих патчей (`MediaStatusStore`): `isStatusStored(path)` - хранит ли UMS статус файла
  (папка с "monitored"); `clearPlayback(path, user)` - снять "просмотрен" и обнулить точку остановки без
  увеличения счетчика воспроизведений, одним запросом (`MediaTableFilesStatus.clearPlayback`); `false`, если снимать нечего
  (нет метки или папка без "monitored").
- `Renderer.hasMediaStore()` - есть ли у устройства медиатека, не создавая ее.
- Карточки устройств (Home): значок перед названием - зеленый "плей" при воспроизведении, серая "пауза" на
  паузе (`state.playback` 1 / 2); 3 карточки в ряд на широком экране (2 на среднем, 1 на узком); картинка
  устройства на 30% меньше (112 px) с отступами сверху и снизу; IP и MAC одним размером шрифта.

### Настройки в UI

Нет (ширина панели - перетаскиванием или ползунками Customize -> Navbar width; имя - в меню карточки устройства).

### Настройки в conf (`UMS.conf`)

| Ключ | По умолчанию | Значение |
|---|---|---|
| `renderers_name` | `{}` | свои имена устройств по UUID (задаются из интерфейса) |

---

## logo

### Суть работы

Свой логотип (исходник - `E:\Soft\UniversalMediaServer\resource\Logo.png`, 427x395, прозрачный фон) вместо логотипа
UMS. Все картинки сделаны из него (Pillow, ресайз LANCZOS, лого по центру квадрата):

| Где виден | Файлы |
|---|---|
| Заголовок веб-интерфейса | `UmsAppShell.tsx`: `/icon-256.png` 36x36 в начале левой группы (после кнопки меню на узком экране), тег `custom:logo` |
| Страница "О программе" (`/about`) | `About.tsx`: `/icon-256.png` 96x96 над названием программы, тег `custom:logo` |
| Веб-интерфейс, вкладка браузера, iOS | `react-client/public/favicon.ico` (16, 32, 48), `icon-32.png`, `icon-256.png`, `apple-touch-icon.png` (180) |
| Значок сервера на ТВ (DLNA) | `src/main/resources/images/icon-square-{48,120,128,256}.png` и `-{48,120,128}.jpg` - на темно-синем фоне `#0E162C` (непрозрачные) |
| Окно и трей настольного UMS | `images/icon-{16..64,128}.png`, `icon.svg` (PNG внутри SVG, размер 32x32 - его проверяет штатный `SvgMultiResolutionImageTest`) |
| "О программе", `README.md` | `images/logo.png` (256), `logo.ico`, `logo.svg` (PNG внутри SVG); ссылка в `README.md` - на `logo.png` этой ветки (теги `<!-- <custom:logo> -->`) |
| `UMS.exe`, установщик, удаление программ | `src/main/external-resources/icon.ico` (16..256) |

Не менялись: `logo.icns` (macOS), `icon-bw*` / `icon-darkmode*` (монохромные значки трея macOS / Linux),
`splash/splash.png` (заставка настольного GUI, с текстом).

Картинки - двоичные файлы, тегов в них нет. При слиянии с upstream конфликт в них решать в пользу своих
(`git checkout --ours`); если upstream добавил новый размер значка - сделать его из `Logo.png` так же.

### Настройки в UI

Нет.

### Настройки в conf (`UMS.conf`)

Нет.

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

