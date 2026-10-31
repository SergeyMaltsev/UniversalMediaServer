# Custom patches (branch `custom`)

**English** | [Русский](custom-patches.ru.md)

Branch `custom` = upstream `main` + our own changes. Every change in the code is marked with tags:

```
// <custom:patch-name> Description
our code
// </custom:patch-name>

// <custom-remove:patch-name>
// commented-out base code
// </custom-remove:patch-name>
```

In `.properties` files the same tags are used with `#`, in JSX - `{/* ... */}`. New files are wrapped entirely in one `custom`
block after the license. Tag check: `pwsh -File build\check-custom-tags.ps1`.
Update from upstream: merge `upstream/main` into `custom`. The branch is kept in the fork
(`origin`, push is allowed only there); upstream UMS is the remote `upstream`, fetch only.
UI strings of the patches are needed in all UMS languages: English (`messages.properties`) and Russian are written by hand,
the other 38 are translated and placed at the end of each `messages_<language>.properties` in blocks under the tags of their patch. A new patch string goes into English and Russian first, then translation into the other languages.
Update this file and the short `README-Custom.md` with every change.
Tests (JUnit 5, `src/test/java`, under the tags of their patch): `FullyPlayedLabelTest` (`playback-labels`),
`MacAddressesTest`, `MediaTableFilesStatusClearPlaybackTest` (`common`), `FolderManagerApiServletTest` (`folders-root`),
`PlaybackPositionTrackerTest` (`position-tracker`), `ControlFolderSettingsTest` (`reload-media`), `FolderResetActionTest`
(`folder-reset`); `ruid-no-mmap` is checked by the standard `ResourceIdentifierTest`. Run: `mvn test -P testing-windows`
(with `setup\build-tools.ps1`). A new patch gets new tests.

Summary:

| Patch | What it does | Files |
|---|---|---|
| `folders-root` | "Folders" folder (own name) in the root instead of the shared folders; "Folder management" web UI page | `UmsConfiguration`, `MediaStore`, `FoldersContainer`, `FolderManagerApiServlet` (new), `FileWatcher`, `WebGuiServerJetty`, `FullyPlayed`, `FolderManager/*.tsx`, `folder-service.tsx` (new), `ManageNavbar.tsx`, `UmsAppShell.tsx`, `messages*.properties` |
| `playback-labels` | Name = file name without suffixes, `[*]` / `[h:mm:ss]` label at the end, own thresholds | `UmsConfiguration`, `FullyPlayed`, `DidlHelper`, `StoreResourceHelper`, `StoreItem`, `messages*.properties` |
| `common` | General improvements not tied to plugins: draggable left panel edge; device cards: rename, MAC, play icon, 3 per row; red server restart button in the header | `NavbarSplitter.tsx`, `RestartServerButton.tsx`, `RendererRenameModal.tsx`, `RendererNames`, `MacAddresses` (new), `UmsAppShell.tsx`, `Renderers.tsx`, `home-service.ts`, `Renderer`, `RendererItem`, `RenderersApiServlet`, `UmsConfiguration`, `messages*.properties` |
| `custom-settings` | "Custom Settings" tab in Server Settings for the settings of our patches | `CustomSettings.tsx` (new), `ServerSettings.tsx`, `messages*.properties` |
| `logo` | Own logo instead of the UMS logo: at the start of the web UI header, web UI icon and browser tab, server icon on TV (DLNA), desktop window and tray, `UMS.exe` and installer, `README.md` | `UmsAppShell.tsx`; images `react-client/public/*`, `images/icon-*`, `icon-square-*`, `logo.*`, `external-resources/icon.ico`; `README.md` |
| `release` | GitHub releases of the fork: number `<upstream version>-custom.<N>` (for example `15.9.0-custom.1`), Windows x64 installer and portable zip; the number is shown in the web UI as the version | `project.properties`, `AboutApiServlet`; `build/release.ps1` (new), `build/build.ps1 -CustomVersion`, `release-custom.bat` in the root of the working folder |
| `readme-custom` | `README-Custom.md` - what the fork changes, for users (grouped by kind of change, English and Russian), link in `README.md` ("Custom fork"); the "Custom edition" tab on the "About" page shows this file (Markdown -> HTML via showdown, section chosen by the UI language: Russian or English) | `README-Custom.md` (new), `README.md`, `About.tsx`, `CustomEdition.tsx` (new), `messages*.properties`; dependency `showdown` and `@types/showdown` in `react-client/package.json` and `yarn.lock` (JSON has no tags) |
| `build-scripts` | Build scripts in `build/`, output files in `.gitignore`; the branch commit as `git.commit.id` (with uncommitted changes - `<commit>-dirty-<time>`, otherwise the browser keeps old translations); only the x86_64 installer (x86 is disabled in `pom.xml`, saves ~4 min) | `build/*`, `.gitignore`, `pom.xml` |

All settings of our patches: Server Settings -> **Custom Settings** -> Root folders:

```
[x] Show the "Folders" folder                    show_folders_container
    Name of the "Folders" folder (empty = default)   folders_container_name
[ ] Allow disk operations on the "Folder management" page   allow_disk_operations
[x] Use the Windows Recycle Bin when deleting                use_recycle_bin
    Label position   [At the start: [*] name.ext v]  playback_label_position
[x] Show the progress label [h:mm:ss]            show_progress_label
    Started from (seconds from the beginning)   60   progress_label_min_seconds
[x] Show the played label [*]                    show_played_label
    Played from (seconds before the end)       120   played_label_seconds_to_end
    Clear the label after playing from the start for (seconds)  2   label_reset_seconds
[x] Show the "Reset watched" item at the end of folders              show_folder_reset_item
```

The "Control" folder section:

```
[x] Show the "Control" folder in the root        show_control_folder
[x] Show "Reload media library"                  show_reload_media_item
[x] Show "Restart server"                        show_restart_server_item
[x] Show "Hibernate computer"                    show_hibernate_item
[x] Show "Shut down computer"                    show_shutdown_item
```

Server UUID section (patch `server-uuid`):

```
<current UUID>  [New server UUID]           -> uuid (new random) + restart of the media server
```

---

## folders-root

### What it does

- Root of the server when `show_folders_container = true`:
  ```
  Media Library      (if show_media_library_folder = true)
  <Folders>          -> shared folders from Shared Content (F:\Video\...)
  ```
- "Folders" (`FoldersContainer`) collects all shared folders (`FolderContent`) instead of the root, including with
  `flatten_folders`; virtual folders and iTunes/iPhoto stay in the root. When the root is rebuilt, the found folders
  are moved without being recreated, and their ids are kept.
- Name: `folders_container_name` or, if empty, "Folders" (localized by the UI language). The internal (system) name is always
  `Folders`, so renaming does not change file ids and client addresses.
- The keys are in `NEED_RENDERERS_MEDIA_STORE_RELOAD_FLAGS`: changing them rebuilds the root for the devices.
- Turning "Folders" on once gives files new object ids (key "parent + name"): clients that store marks by address lose them once.

### Folder management (web UI)

The "Folder management" menu item (address `/folders`) after "Shared Content", for the right to view
settings; changes require the right to change settings. Three panels:

```
+----------------------+----------------------------------------------------------+---------------------------+
| Series     [m]12 [e]5| [ ] Name                         Dur.    Codec    Size   | Mark played               |
|  Detective [m]9  [e]3| [x] [*] Balthazar.S01E06.mkv     59:32   H264     1.2 GB | Mark all played           |
|   Balthazar [m]9 [e]3| [ ] [30:43] Balthazar.S01E05...  58:10   H264     1.1 GB | Mark unplayed             |
| TV         [m]40 [e]0|                                       Selected 1 of 9    | Mark all unplayed         |
| -------------------- |                                                          | ------------------------- |
| [x] Show counts      |                                                          | Delete played from disk   |
| [x] Show played      |                                                          | Delete selected from disk |
+----------------------+----------------------------------------------------------+---------------------------+
```

- On the left - a tree of subfolders of the active Shared Content folders (hidden, `.` folders and `ignored_folder_names` are not
  shown). To the right of the folder name - columns (all with subfolders, `nodeProps` of the tree): played (in the color
  of the selected folder; 0 - empty), number of audio/video files, their size. Above the tree there is a header of the columns - icons only
  (eye, film, disk) with tooltips. Under the tree there are checkboxes "Show played", "Show counts", "Show size". After actions the tree is refreshed.
- Between the tree and the list there is a splitter (`PanelSplitter`): tree width 200..700 px; a second splitter - between
  the list and the button panel, panel width 120..400 px. Button labels wrap by words, longer than 4 lines
  are cut with "..."; the full text is in a tooltip. On a narrow
  screen (< 62em) the panels go one under another.
- Remembered in the browser (`localStorage`): checkboxes, tree and button panel widths, the last opened folder. When the page opens
  this folder is selected and the branches leading to it are expanded; if it no longer exists - the nearest existing folder above.
- Under the page title, across the full width above the panels - a filter by file name (all words, case-insensitive;
  the cross clears it). It is kept when moving to another folder and is not remembered in the browser. The list, the status line
  and the actions see only the matching files: "Mark all played", "Delete played from disk" and the selection do not touch
  files hidden by the filter. If all are hidden - "No files match the filter".
  The tree is filtered too (300 ms after typing, `GET tree?filter=...`, the same rule on the server): only folders
  with matching files (in the folder itself or below) remain, their counters (files, played, size)
  count only the matching files; all branches are expanded. After the filter is cleared the previous expansion comes back.
  While the filter is active, there is no "Delete folder from disk" button: the tree counters show only the matching files,
  but the whole folder is deleted.
- In the center - the audio and video files of the selected folder (without subfolders): name with a label, duration, codec (video; for audio files - audio), video size (`1920x1080`), file size; duration, codec and video size come from the database,
  for files not yet scanned they are empty. The "Name" header is text, the column takes the rest of the width; the headers of the
  other columns are icons (clock, film, frame, disk) with tooltips, as above the tree; the width of these columns depends on
  the content. Sorting - click on a column header, a repeated click changes the direction (names are sorted "naturally": 2 before 10; video size - by the number of pixels). Under the list there is a status line
  (`FolderStatusBar`): selected, played, total - the number of files and their total size, with icons. On the right -
  actions, a panel 250 px wide.
- Names - with a label by the same settings as for devices (`FullyPlayed.addPlaybackLabel`: position, thresholds,
  label switches); played files (`[*]`) are dimmed. The checkbox in the header selects all / clears the selection.
- "Read media info" (first in the actions panel): reads duration, codecs and video size of the open folder's files into
  the media library in the background, the same parsing as when a client browses them (`POST /v1/api/folders/read
  {path}`, progress `GET .../read` -> `{running, done, total}`; one job at a time). The button shows "Reading media
  info: n of N", then the list and the tree reload.
- "Mark unplayed" clears both "played" and the stop point (the `[h:mm:ss]` label goes too), without
  increasing the play counter (`MediaStatusStore.clearPlayback`, see `common`). Files in shared folders without
  "monitored" are not marked (UMS does not store status there) - a warning with their number is shown.
- Deletion - a warning window with the list of files; files are deleted from the disk permanently (`Files.delete`), not to the
  Recycle Bin, and are removed from the media library at once in the same way as the standard folder watching
  (`MediaInfoStore.removeMediaEntry`: the file and its status in the database and caches; `fileRemoved` for the already created device
  media libraries; SystemUpdateID). A file that is no longer on the disk is only removed from the media library. Message: how many
  files were deleted and how much space was freed.
- Virtual folders of Shared Content (type "Virtual folders": one name, several folders) - a tree node with its own
  name and a "folder" icon (value `virtual:Name`, nested - `virtual:Name/Inner`). Checkbox under the tree
  "Merge virtual folders" (`localStorage`, enabled by default):
  ```
  enabled                                   disabled
  Series              <- F:\ and E:\ together   Series
    Balthazar  (F:)                           F:\Video\Series
    Detective  (E:)                             Balthazar
                                              E:\Video\Series
                                                Detective
  ```
  Enabled - as for devices (the standard `VirtualFolder`): files and subfolders of all its folders together, by name;
  same-named subfolders of different disks are two nodes. Disabled - each folder is a separate node inside, labeled with its full
  path. Selecting a virtual folder node shows the top-level files of all its folders; the list header shows the name.
  Folders of virtual folders count as shared for path checks (marks, deletion). The node of a shared folder that is a disk root is
  labeled with its path (`E:\`); before it was empty.
- At the bottom of the action panel, under file deletion, there is a red "Delete folder from disk" button
  : it deletes the open folder with all its content (subfolders, any files, not only audio/video) after confirmation
  (path, warning, number and size of the audio/video files from the tree). Available only for a real folder below a
  shared folder: not for a shared folder (the root of the tree, a folder directly in a virtual folder node) and not for a virtual folder;
  the server also refuses (with the reason in the response, `reason`) if another shared folder lies inside (comparison by real
  paths `toRealPath`; both disabled shared folders and the folders of virtual folders are taken into account), or if there is a link on the path
  from the shared folder to the target (each link is checked without following it; the same for file deletion). The nodes of the shared
  folders themselves are marked by the tree with `nodeProps.shared`. A symlink and a junction inside
  are deleted as links, the content of their target is not touched (the sign is attributes without following the link: `isSymbolicLink`
  or `isOther`; the canonical path of a junction is not resolved). Deleted audio/video files leave the media library as for file deletion, the folder
  node leaves the device trees (`MediaStore.fileRemoved`). Files that could not be deleted remain (with their
  folders), their names are in the error message. After deletion the parent folder is opened.
- API: `FolderManagerApiServlet`, `/v1/api/folders/` - `GET tree[?merge=false][&filter=words]`, `POST files {path}`,
  `POST mark {paths, played}` -> `{count, skipped}`, `POST delete {paths}` -> `{deleted, freed, failed}`, `POST delete-folder {path}` -> `{deleted, freed, failed, removed}`. Each
  path is checked by its canonical path (`..`, junction, symlink do not lead out of a shared folder; a shared folder can be
  a disk root): only inside an active shared folder, only audio/video. After that the original path is used - the status
  is stored by it, and the devices use it too. The tree does not loop through a junction to a folder above (a folder that is already above
  on this path - by its real path, the junction is resolved - is shown without content); one folder in two
  places (shared and inside a virtual folder) is shown in full in both. Status is for the user of the account (without authentication - 0, as for
  devices).

### Settings in the UI

Custom Settings -> Root folders:
- "Show the "Folders" folder" with a tooltip;
- right after it "Name of the "Folders" folder (empty = default)", active only when the checkbox is on;
- "Allow disk operations on the "Folder management" page", off by default: without it the page has no delete buttons (for files and folders), and the server answers
  403 to `delete` / `delete-folder`;
- "Use the Windows Recycle Bin when deleting", on by default, active only when disk operations are allowed: files and the folder go to the Recycle Bin (own `SHFileOperationW` call with the UTF-16 path: `FileUtils.moveToTrash` from jna-platform garbles non-ASCII paths;
  a folder is moved as a whole in one operation, links inside stay links); the confirmation and result texts mention the Recycle Bin.
  Before a folder is moved, UMS stops watching it and its subfolders (`FileWatcher.unwatch`): open watch handles prevent Windows from moving the folder (error `0x7C`); if the move fails, watching is restored
  (`FileWatcher.rewatch`) and nothing is deleted.
  Windows silently deletes permanently what it cannot put into the Recycle Bin, so before the move the server refuses:
  a non-local non-removable drive or UNC (`NotFixedDrive`), the Recycle Bin of the drive is turned off (`RecycleBinOff`, `NukeOnDelete`),
  the size is larger than the Recycle Bin size (`TooLargeForRecycleBin`, `MaxCapacity`; both from
  `HKCU\...\Explorer\BitBucket\Volume\{GUID}`, without the key - 5% of the disk). The request carries the mode the page warned about (`recycle`); if the setting has changed since then - response 409, the page reloads the settings.

### Settings in conf (`UMS.conf`)

| Key | Default | Value |
|---|---|---|
| `show_folders_container` | `false` | `true` - shared folders inside "Folders" |
| `folders_container_name` | `""` | own folder name; empty - "Folders" (localized by the UI language; in `VALID_EMPTY_KEYS`, can be cleared from the UI) |
| `allow_disk_operations` | `false` | `true` - disk delete buttons on the "Folder management" page |
| `use_recycle_bin` | `true` | `true` - delete to the Windows Recycle Bin, `false` - permanently |

---

## playback-labels

### What it does

- Name for clients (DLNA: `DidlHelper`, `StoreResourceHelper`; web player: `StoreItem.getLocalizedResumeName`)
  = display name without the suffix (`{Audio: ...}`, `{English subtitles}`); the extension - by the standard settings.
  The suffix is removed only from ordinary files (`FullyPlayed.getTitleSuffix`): in `#--TRANSCODE--#` and in virtual
  entries (DVD chapters) it stays, there it tells the variants apart.
- A label from the server database data (`FILES_STATUS`) is added at the start of the name (by default) or at the end (`playback_label_position`),
  `FullyPlayed.addPlaybackLabel`:
  ```
  <name.ext>              no stop point, or it is less than "Started from" seconds from the beginning
  [mm:ss] <name.ext>      stop point from "Started from" seconds on, more than "Played from" seconds before the end
  [h:mm:ss] <name.ext>    the same, the point is more than an hour
  [*] <name.ext>          marked in the database as played (standard or manually), or stopped less than
                          "Played from" seconds before the end
  ```
  At the end (`end`): `<name.ext> [*]`. At the start the label is visible also for long names that the client cuts.
  Each label can be switched off (`show_progress_label`, `show_played_label`). The thresholds are our own, not `resume_back`. A file shorter than both thresholds together (default 180 seconds) gets no label from its position, only `[*]` when it is marked played in the database.
- The stop point is always written to the database (standard, `MediaMonitor`); the thresholds affect only the display.
- Does not depend on `resume` ("Enable video resuming"): it controls only the virtual "Resume: ..." entries and the
  files of `data\resume\`. `resume = false` is recommended - no extra entries for one file.
- Status is stored only for files in shared folders with the "monitored" checkbox (the standard UMS rule).
- Folders have no label: UMS does not store the watch status for folders; the folder block in `StoreResourceHelper` is kept
  in case upstream adds it.
- The label is set regardless of whether the device shows thumbnails (standard `[Watched]` only without thumbnails).
  The standard `FullyPlayed.addFullyPlayedNamePrefix` is not touched (it is no longer called).
- The file address (`<res>`) does not change: it contains the file name, not the display title.
- Titles are built at each play, so the label keys do not rebuild the media libraries: changing them only
  increases SystemUpdateID (`NEED_RENDERERS_TITLES_REFRESH_FLAGS`, handled in `PMS`), and clients reread
  the folders and get the new titles.

### Settings in the UI

Custom Settings -> Root folders:
- "Label position": "At the start: [*] name.ext" / "At the end: name.ext [*]";
- "Show the progress label [h:mm:ss]" and "Started from (seconds from the beginning)";
- "Show the played label [*]" and "Played from (seconds before the end)".

### Settings in conf (`UMS.conf`)

| Key | Default | Value |
|---|---|---|
| `playback_label_position` | `start` | `start` - label before the name, `end` - after it |
| `show_progress_label` | `true` | the `[h:mm:ss]` label |
| `progress_label_min_seconds` | `60` | from how many seconds from the beginning the stop point is shown |
| `show_played_label` | `true` | the `[*]` label |
| `played_label_seconds_to_end` | `120` | the file counts as played for the label when stopped less than this many seconds before the end |
| `resume` | `false` recommended | without the virtual "Resume: ..." entries |

---

## common

General web UI improvements not tied to plugins.

### What it does

- Draggable edge of the left panel (settings and player navigation), `NavbarSplitter`: a thin strip on the inner
  edge of the panel, the cursor "resize". Width 100..500 px (as for the "Navbar width" sliders on the
  Customize page). Stored in the same standard browser keys `mantine-navbar-width-sm/md/lg` - separately for the small,
  medium and large screen (the width of the current one changes); the Customize sliders show the same value.
- On a narrow screen (less than `sm`, the panel slides out by a button) there is no edge. For right-to-left writing the edge is on the left.
- Red icon button for restarting the server at the right edge of the header, after the user menu
  (`RestartServerButton`, tooltip "Restart server"): the same `Server.Restart` action and the same confirmation window as the button on the
  "Tools" page (restart of the media services, not of the application). Visible with the "restart server" right, not in player mode.
- Copy icon buttons after the IP and MAC in the card (`CopyValueButton`, the standard `CopyButton`); after copying,
  a check mark is shown for a second. Copying to the clipboard requires a secure context: it works on `http://localhost`; when the UI is opened by IP on the network, the browser may not allow it.
- The menu item "Manage accounts" has a shorter Russian translation (`ManageAccounts` in
  `messages_ru.properties`; the English does not change).
- Renaming a device (Home -> card -> "..." menu -> "Rename"): the own name
  is shown instead of the name the device reports (UPnP `friendlyName`, for example two "movian" devices). `RendererNames` stores names by UUID in `UMS.conf` (`renderers_name`, like the standard `renderers_user`: JSON with `"`
  written as `|`, so `"` and `|` are removed from the name). `Renderer.getRendererName()` returns the own name first -
  it is visible everywhere (cards, logs, player). A renamed device shows `*` after the name in the card; the tooltip on hover shows the device name. An empty name or the "Device name" button returns the device name.
- MAC address in the card under the IP (`MacAddresses`): from the system ARP table (`arp -a <ip>`, on Linux/macOS with `-n`),
  cached by IP. The lookup does not delay the card update: `arp` runs in the background (one per IP, no longer than 5 s, a failed
  attempt is repeated at most once a minute), and the found MAC updates the card itself. Empty for this computer and
  for switched-off devices.
- The bottom status line is always visible: the earlier messages on the left, on the right "Universal Media Server <version> custom
  edition - <build date and time>". The build time is local, `build\build.ps1` passes `-Dbuild.time`, in
  `project.properties` it is `project.build.time`, the "About" API returns `buildTime` (empty for a build without build.ps1).
- Status helpers for our patches (`MediaStatusStore`): `isStatusStored(path)` - whether UMS stores the status of the file
  (a folder with "monitored"); `clearPlayback(path, user)` - clears "played" and resets the stop point without
  increasing the play counter, in one request (`MediaTableFilesStatus.clearPlayback`); `false` if there is nothing to clear
  (no label or a folder without "monitored").
- `Renderer.hasMediaStore()` - whether the device has a media library, without creating it.
- Device cards (Home): an icon before the name - a green "play" during playback, a grey "pause" on
  pause (`state.playback` 1 / 2); 3 cards per row on a wide screen (2 on a medium one, 1 on a narrow one); the device picture is 30% smaller (112 px) with margins at the top and bottom; IP and MAC in the same font size.

### Settings in the UI

None (the panel width - by dragging or with the Customize -> Navbar width sliders; the name - in the device card menu).

### Settings in conf (`UMS.conf`)

| Key | Default | Value |
|---|---|---|
| `renderers_name` | `{}` | own device names by UUID (set from the UI) |

---

## custom-settings

### What it does

The "Custom Settings" tab in Server Settings, always visible (it does not depend on "Show advanced
settings"). All settings of our patches that have a UI are here; the standard tabs are not changed. The component
`CustomSettings.tsx`; the tab and the panel are added to `ServerSettings.tsx`.

Sections: Root folders - the settings of `folders-root`, `playback-labels`, `position-tracker` and
`folder-reset`, `reload-media`.

### Settings in the UI

The tab itself is the UI; new settings of the patches are added here, the strings - in English and Russian.
A key can be saved from the UI only if it is in `UmsConfiguration.getWebSettingsWithDefaults()`.

### Settings in conf (`UMS.conf`)

No keys of its own.

---

## logo

### What it does

Own logo (source - `E:\Soft\UniversalMediaServer\resource\Logo.png`, 427x395, transparent background) instead of the
UMS logo. All pictures are made from it (Pillow, LANCZOS resize, the logo centered in a square):

| Where it shows | Files |
|---|---|
| Web UI header | `UmsAppShell.tsx`: `/icon-256.png` 36x36 at the start of the left group (after the menu button on a narrow screen), tag `custom:logo` |
| "About" page (`/about`) | `About.tsx`: `/icon-256.png` 96x96 above the program name, tag `custom:logo` |
| Web UI, browser tab, iOS | `react-client/public/favicon.ico` (16, 32, 48), `icon-32.png`, `icon-256.png`, `apple-touch-icon.png` (180) |
| Server icon on TV (DLNA) | `src/main/resources/images/icon-square-{48,120,128,256}.png` and `-{48,120,128}.jpg` - on a dark blue background `#0E162C` (opaque) |
| Window and tray of the desktop UMS | `images/icon-{16..64,128}.png`, `icon.svg` (PNG inside SVG, size 32x32 - checked by the standard `SvgMultiResolutionImageTest`) |
| "About", `README.md` | `images/logo.png` (256), `logo.ico`, `logo.svg` (PNG inside SVG); the link in `README.md` - to `logo.png` of this branch (tags `<!-- <custom:logo> -->`) |
| `UMS.exe`, installer, uninstaller entries | `src/main/external-resources/icon.ico` (16..256) |

Not changed: `logo.icns` (macOS), `icon-bw*` / `icon-darkmode*` (monochrome tray icons of macOS / Linux),
`splash/splash.png` (the splash screen of the desktop GUI, with text).

The pictures are binary files, they have no tags. When merging upstream, a conflict in them is resolved in favor of ours
(`git checkout --ours`); if upstream added a new icon size - make it from `Logo.png` in the same way.

### Settings in the UI

None.

### Settings in conf (`UMS.conf`)

None.

---

## release

### What it does

Publishing of Windows x64 builds on the Releases page of the fork. Start - `release-custom.bat` in the root of the working folder
(a wrapper over `source-custom\build\release.ps1`):

1. Checks: branch `custom`, no local changes, the branch is pushed (`HEAD` = `origin/custom`), tags in order,
   `gh` is authorized.
2. Number: the version from `pom.xml` without `-SNAPSHOT` + the next number by the fork's tags: `v15.9.0-custom.1`, `.2`, ...;
   a new upstream version starts again from `.1`.
3. Build `build.ps1 -Target all -CustomVersion <number>`: the number goes to `project.custom.version`
   (`-Dcustom.version`), the "About" API returns it as the version - it is shown on the "About" page and in the status line; the installer is called `UMS-Windows-<number>-x86_64.exe`.
4. Files in `build\release\<number>\`: the installer and `UMS-Windows-<number>-x86_64-portable.zip` (the program folder +
   an empty `portable\`: the settings sit next to the program), `release-notes.md` (what edition it is + the commits since the last
   release).
5. A question (without `-Yes`), then an annotated tag, push of the tag to `origin`, `gh release create` with the two files.

Keys: `-DryRun` - only build and prepare the files, `-Yes` - without the question, `-Offline` - for `build.ps1`.

### Settings in the UI

None.

### Settings in conf (`UMS.conf`)

None.

---

