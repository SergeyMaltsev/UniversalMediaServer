/*
 * This file is part of Universal Media Server, based on PS3 Media Server.
 *
 * This program is a free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; version 2 of the License only.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
// <custom:folders-root> Whole file: "Folder management" page - folder tree, files, actions
import { Box, Checkbox, CloseButton, Divider, getTreeExpandedState, Group, LoadingOverlay, Paper, ScrollArea, Stack, Text, TextInput, Tooltip, TreeNodeData, useTree } from '@mantine/core'
import { useDebouncedValue, useLocalStorage, useMediaQuery } from '@mantine/hooks'
import { IconFilter } from '@tabler/icons-react'
import axios from 'axios'
import { useEffect, useMemo, useRef, useState } from 'react'

import { I18nInterface } from '../../services/i18n-service'
import { SessionInterface, UmsPermission } from '../../services/session-service'
import {
  deleteFiles,
  deleteFolder,
  DeleteResult,
  findDeletableFolder,
  findExistingFolder,
  findFolderPath,
  FolderCounts,
  FolderFile,
  FolderOptions,
  formatSize,
  getFolderFiles,
  getFolderOptions,
  getFolderTitle,
  getFolderTree,
  markFiles,
  matchesFilter,
} from '../../services/folder-service'
import { showError, showInfo, showWarning } from '../../utils/notifications'
import FolderActions from './FolderActions'
import FolderFiles from './FolderFiles'
import FolderStatusBar from './FolderStatusBar'
import FolderTree, { FolderTreeHeader } from './FolderTree'
import PanelSplitter from './PanelSplitter'

// Below the title and the name filter
const panelHeight = 'calc(100vh - 212px)'
const actionsMinWidth = 120
const actionsMaxWidth = 400
const treeMinWidth = 200
const treeMaxWidth = 700

export default function FolderManager({ i18n, session }: { i18n: I18nInterface, session: SessionInterface }) {
  const [tree, setTree] = useState<TreeNodeData[]>([])
  const [folder, setFolder] = useState<string>()
  const [files, setFiles] = useState<FolderFile[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  // Custom Settings: the disk buttons are hidden until allowDiskOperations is known to be on
  const [options, setOptions] = useState<FolderOptions>()
  // Kept when another folder is opened: finds the same name in every folder
  const [filter, setFilter] = useState('')
  // The tree is asked again with the filter once typing pauses: only folders with matching files, their counts
  const [treeFilter] = useDebouncedValue(filter.trim(), 300)
  // The expanded folders before a filter expanded them all, back when the filter is cleared
  const expandedBeforeFilter = useRef<Record<string, boolean> | undefined>(undefined)
  // Answers of older tree requests (typing goes on) are dropped
  const treeRequest = useRef(0)
  const [showCount, setShowCount] = useLocalStorage({ key: 'folder-manager-show-count', defaultValue: true })
  const [showPlayed, setShowPlayed] = useLocalStorage({ key: 'folder-manager-show-played', defaultValue: true })
  const [showSize, setShowSize] = useLocalStorage({ key: 'folder-manager-show-size', defaultValue: true })
  // Read at once: the first tree load already uses it
  const [mergeVirtual, setMergeVirtual] = useLocalStorage({ key: 'folder-manager-merge-virtual', defaultValue: true, getInitialValueInEffect: false })
  const shownColumns = { played: showPlayed, files: showCount, size: showSize }
  // Read at once (not in an effect): the first tree load restores this folder
  const [lastFolder, setLastFolder] = useLocalStorage({ key: 'folder-manager-folder', defaultValue: '', getInitialValueInEffect: false })
  const [treeWidth, setTreeWidth] = useLocalStorage({ key: 'folder-manager-tree-width', defaultValue: 340 })
  const [actionsWidth, setActionsWidth] = useLocalStorage({ key: 'folder-manager-actions-width', defaultValue: 250 })
  // Side by side with the splitter from the "md" breakpoint on, stacked below it
  const wide = useMediaQuery('(min-width: 62em)')
  const restored = useRef(false)
  const treeController = useTree()
  const canModify = session.havePermission(UmsPermission.settings_modify)
  const canView = canModify || session.havePermission(UmsPermission.settings_view)

  // The list, the status bar and the actions only see the files that match the filter, so
  // "Mark all" / "Delete played" / a selection never reach files hidden by it
  const shownFiles = useMemo(() => files.filter(file => matchesFilter(file.name, filter)), [files, filter])
  const shownSelected = useMemo(() => selected.filter(path => shownFiles.some(file => file.path === path)), [selected, shownFiles])

  const showLoadError = () => showError({ title: i18n.get('Error'), message: i18n.get('FolderDataNotReceived') })
  const showActionError = () => showError({ title: i18n.get('Error'), message: i18n.get('FolderActionFailed') })

  /** @returns the new tree, undefined when the request failed or a newer one was sent */
  const loadTree = async (): Promise<TreeNodeData[] | undefined> => {
    const request = ++treeRequest.current
    try {
      const data = await getFolderTree(mergeVirtual, treeFilter)
      if (request !== treeRequest.current) {
        return undefined
      }
      setTree(data)
      if (treeFilter) {
        // All matching folders open; the user's own expanded state is kept for later
        expandedBeforeFilter.current ??= treeController.expandedState
        treeController.setExpandedState(getTreeExpandedState(data, '*'))
      }
      else if (expandedBeforeFilter.current) {
        treeController.setExpandedState(expandedBeforeFilter.current)
        expandedBeforeFilter.current = undefined
      }
      if (!restored.current) {
        restored.current = true
        restoreFolder(data)
      }
      return data
    }
    catch {
      showLoadError()
      return undefined
    }
  }

  const loadOptions = () => getFolderOptions().then(setOptions).catch(() => setOptions(undefined))

  /** Opens the last opened folder, or its nearest parent that still exists */
  const restoreFolder = (data: TreeNodeData[]) => {
    const path = lastFolder ? findExistingFolder(data, lastFolder) : undefined
    if (!path) {
      return
    }
    findFolderPath(data, path)?.slice(0, -1).forEach(value => treeController.expand(value))
    selectFolder(path)
  }

  useEffect(() => {
    session.stopPlayerSse()
    session.setDocumentI18nTitle('FolderManagement')
    session.setNavbarManage(FolderManager.name)
  }, [])

  useEffect(() => {
    if (canView) {
      loadTree()
    }
  }, [canView, mergeVirtual, treeFilter])

  useEffect(() => {
    if (canView) {
      loadOptions()
    }
  }, [canView])

  /** "Files deleted from disk: n, space freed" or "Moved to the Recycle Bin: n files (size)" */
  const deletedMessage = (result: DeleteResult) => i18n.getFormat([
    result.recycled ? 'i18n@FilesMovedToRecycleBin' : 'i18n@FilesDeletedFromDisk',
    result.deleted.toString(),
    formatSize(result.freed),
  ])

  // Answers of older file requests (another folder clicked meanwhile) are dropped
  const filesRequest = useRef(0)

  const loadFiles = async (path: string, keepSelection: boolean) => {
    const request = ++filesRequest.current
    setBusy(true)
    try {
      const folderFiles = await getFolderFiles(path)
      if (request === filesRequest.current) {
        setFiles(folderFiles)
        setSelected(current => keepSelection ? current.filter(value => folderFiles.some(file => file.path === value)) : [])
      }
    }
    catch {
      if (request === filesRequest.current) {
        setFiles([])
        showLoadError()
      }
    }
    if (request === filesRequest.current) {
      setBusy(false)
    }
  }

  const openFolder = (path: string) => {
    setFolder(path)
    setLastFolder(path)
    loadFiles(path, false)
  }

  const selectFolder = (path: string) => {
    if (path !== folder) {
      openFolder(path)
    }
  }

  const mark = async (paths: string[], played: boolean) => {
    if (!folder) {
      return
    }
    try {
      const result = await markFiles(paths, played)
      if (result.skipped > 0) {
        showWarning({ title: i18n.get('FolderNotMonitored'), message: i18n.getFormat(['i18n@FilesNotMarked', result.skipped.toString()]) })
      }
    }
    catch {
      showActionError()
    }
    await loadFiles(folder, true)
    loadTree()
  }

  /** The failed names, with the reason the server refused them (link on the path, no Recycle Bin, ...) */
  const showNotDeleted = (result: DeleteResult, fallback: string) => showError({
    title: i18n.get('FilesNotDeleted'),
    message: (result.reason ? i18n.get('DeleteRefused' + result.reason) + ': ' : '') + (result.failed.join(', ') || fallback),
  })

  /** 409: the Recycle Bin setting changed since the page warned; reload it, the user tries again */
  const showDeleteError = (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 409) {
      loadOptions()
      showError({ title: i18n.get('Error'), message: i18n.get('RecycleBinSettingChanged') })
    }
    else {
      showActionError()
    }
  }

  const remove = async (paths: string[]) => {
    if (!folder || !options) {
      return
    }
    try {
      const result = await deleteFiles(paths, options.useRecycleBin)
      showInfo({
        title: i18n.get('DeleteFromDisk'),
        message: deletedMessage(result),
      })
      if (result.failed.length > 0) {
        showNotDeleted(result, '')
      }
    }
    catch (error) {
      showDeleteError(error)
    }
    await loadFiles(folder, false)
    loadTree()
  }

  // Only a real folder below a shared folder can be deleted; the counts come from the tree. Not while
  // filtering: the filtered tree counts only the matching files, the delete takes all of them
  const deletableNode = folder && !filter.trim() && !treeFilter ? findDeletableFolder(tree, folder) : undefined

  /** Deletes the open folder with its content, then opens its parent */
  const removeFolder = async (path: string) => {
    if (!options) {
      return
    }
    setBusy(true)
    try {
      const result = await deleteFolder(path, options.useRecycleBin)
      if (result.removed) {
        showInfo({
          title: i18n.get('DeleteFolderFromDisk'),
          message: deletedMessage(result),
        })
      }
      if (result.failed.length > 0 || !result.removed) {
        showNotDeleted(result, path)
      }
    }
    catch (error) {
      showDeleteError(error)
    }
    setBusy(false)
    const data = await loadTree()
    const parent = data && findExistingFolder(data, path)
    if (parent) {
      openFolder(parent)
    }
  }

  if (!canView) {
    return (
      <Box style={{ maxWidth: 1024 }} mx="auto">
        <Text c="red">{i18n.get('YouDontHaveAccessArea')}</Text>
      </Box>
    )
  }

  const treePanel = (
    <Paper withBorder p="xs" h="100%">
      <FolderTreeHeader i18n={i18n} shown={shownColumns} />
      <ScrollArea h={`calc(${panelHeight} - 145px)`}>
        <FolderTree
          i18n={i18n}
          tree={treeController}
          data={tree}
          selected={folder}
          shown={shownColumns}
          onSelect={selectFolder}
        />
      </ScrollArea>
      <Divider my="xs" />
      <Stack gap={6} px="xs" pb={4}>
        <Checkbox size="xs" label={i18n.get('ShowFolderPlayedCount')} checked={showPlayed} onChange={event => setShowPlayed(event.currentTarget.checked)} />
        <Checkbox size="xs" label={i18n.get('ShowFolderFilesCount')} checked={showCount} onChange={event => setShowCount(event.currentTarget.checked)} />
        <Checkbox size="xs" label={i18n.get('ShowFolderSize')} checked={showSize} onChange={event => setShowSize(event.currentTarget.checked)} />
        <Tooltip label={i18n.get('MergeVirtualFoldersHint')} multiline w={280} withArrow position="top-start">
          <Checkbox
            size="xs"
            label={i18n.get('MergeVirtualFolders')}
            checked={mergeVirtual}
            onChange={event => setMergeVirtual(event.currentTarget.checked)}
          />
        </Tooltip>
      </Stack>
    </Paper>
  )

  const filesPanel = (
    <Paper withBorder p="xs" pos="relative">
      <LoadingOverlay visible={busy} overlayProps={{ blur: 1 }} />
      <Text size="sm" fw={500} truncate px="xs" pb="xs">{folder ? getFolderTitle(folder) : i18n.get('SelectFolder')}</Text>
      <ScrollArea h={`calc(${panelHeight} - 66px)`}>
        {folder && (
          <FolderFiles
            i18n={i18n}
            files={shownFiles}
            selected={shownSelected}
            setSelected={setSelected}
            emptyText={files.length > 0 ? i18n.get('NoFilesMatchFilter') : undefined}
          />
        )}
      </ScrollArea>
      {folder && <FolderStatusBar i18n={i18n} files={shownFiles} selected={shownSelected} />}
    </Paper>
  )

  const actionsPanel = (
    <Paper withBorder p="sm">
      <FolderActions
        i18n={i18n}
        canModify={canModify}
        files={shownFiles}
        selected={shownSelected}
        busy={busy}
        onMark={mark}
        onDelete={remove}
        folder={deletableNode ? folder : undefined}
        folderCounts={deletableNode?.nodeProps as FolderCounts | undefined}
        onDeleteFolder={removeFolder}
        options={options}
      />
    </Paper>
  )

  return (
    <Box>
      <Text size="lg" mb="md">{i18n.get('FolderManagement')}</Text>
      <TextInput
        mb="md"
        leftSection={<IconFilter size={16} />}
        placeholder={i18n.get('FilterByName')}
        value={filter}
        onChange={event => setFilter(event.currentTarget.value)}
        rightSection={filter && <CloseButton size="sm" aria-label={i18n.get('Clear')} onClick={() => setFilter('')} />}
      />
      {wide
        ? (
            <Group gap={0} align="flex-start" wrap="nowrap">
              <Box w={treeWidth} style={{ flexShrink: 0 }}>{treePanel}</Box>
              <PanelSplitter width={treeWidth} min={treeMinWidth} max={treeMaxWidth} onChange={setTreeWidth} />
              <Box miw={0} style={{ flexGrow: 1 }}>{filesPanel}</Box>
              <PanelSplitter width={actionsWidth} min={actionsMinWidth} max={actionsMaxWidth} resizesRight onChange={setActionsWidth} />
              <Box w={actionsWidth} style={{ flexShrink: 0 }}>{actionsPanel}</Box>
            </Group>
          )
        : (
            <Stack>
              {treePanel}
              {filesPanel}
              {actionsPanel}
            </Stack>
          )}
    </Box>
  )
}
// </custom:folders-root>
