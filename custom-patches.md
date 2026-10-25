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
    Started from (% of the duration)    5        progress_label_min_percent
[x] Show the played label [*]                    show_played_label
    Played from (% of the duration)    95        played_label_percent
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

