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

