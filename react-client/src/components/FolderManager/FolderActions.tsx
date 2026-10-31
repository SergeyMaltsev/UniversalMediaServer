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
// <custom:folders-root> Whole file: actions (right panel) of the "Folder management" page
import { Button, Divider, Group, List, Modal, ScrollArea, Stack, Text } from '@mantine/core'
import { IconAlertTriangle, IconEye, IconEyeCheck, IconEyeOff, IconEyeX, IconFileSearch, IconFolderX, IconTrash, IconTrashX } from '@tabler/icons-react'
import { useState } from 'react'

import { I18nInterface } from '../../services/i18n-service'
import { FolderCounts, FolderFile, FolderOptions, formatSize, ReadStatus } from '../../services/folder-service'

export default function FolderActions({
  i18n,
  canModify,
  files,
  selected,
  busy,
  onMark,
  onDelete,
  folder,
  folderCounts,
  onDeleteFolder,
  options,
  readStatus,
  onRead,
}: {
  i18n: I18nInterface
  canModify: boolean
  files: FolderFile[]
  selected: string[]
  busy: boolean
  onMark: (paths: string[], played: boolean) => void
  onDelete: (paths: string[]) => void
  /** the open folder when it can be deleted (a real folder inside a shared folder), otherwise undefined */
  folder?: string
  /** its counts with the subfolders, from the tree */
  folderCounts?: FolderCounts
  onDeleteFolder: (path: string) => void
  /** allow_disk_operations off (or not loaded yet): no delete buttons at all */
  options?: FolderOptions
  /** the media info reading job, while it runs */
  readStatus?: ReadStatus
  onRead: () => void
}) {
  const recycle = options?.useRecycleBin ?? true
  const [toDelete, setToDelete] = useState<FolderFile[]>([])
  const [folderToDelete, setFolderToDelete] = useState<string>()
  const allPaths = files.map(file => file.path)
  const playedFiles = files.filter(file => file.played)
  const selectedFiles = files.filter(file => selected.includes(file.path))
  const disabled = !canModify || busy

  // The panel width is adjustable: labels wrap by words and are cut with "..."
  // after 4 lines; the full label is the tooltip
  const buttonProps = {
    variant: 'light',
    justify: 'flex-start',
    h: 'auto',
    py: 6,
    styles: {
      label: {
        display: '-webkit-box',
        WebkitBoxOrient: 'vertical',
        WebkitLineClamp: 4,
        overflow: 'hidden',
        whiteSpace: 'normal',
        textAlign: 'left',
      },
    },
  } as const

  const button = (label: string, icon: React.ReactNode, enabled: boolean, onClick: () => void, color?: string) => (
    <Button {...buttonProps} title={label} color={color} leftSection={icon} disabled={disabled || !enabled} onClick={onClick}>
      {label}
    </Button>
  )

  const markButton = (label: string, icon: React.ReactNode, paths: string[], played: boolean) =>
    button(label, icon, paths.length > 0, () => onMark(paths, played))

  const deleteButton = (label: string, icon: React.ReactNode, filesToDelete: FolderFile[]) =>
    button(label, icon, filesToDelete.length > 0, () => setToDelete(filesToDelete), 'red')

  return (
    <Stack gap="xs">
      {button(
        readStatus?.running ? i18n.getFormat(['i18n@ReadingMediaInfo', readStatus.done.toString(), readStatus.total.toString()]) : i18n.get('ReadMediaInfo'),
        <IconFileSearch size={18} />,
        files.length > 0 && !readStatus?.running,
        onRead,
      )}
      <Divider my="xs" />
      {markButton(i18n.get('FolderMarkPlayed'), <IconEye size={18} />, selected, true)}
      {markButton(i18n.get('FolderMarkAllPlayed'), <IconEyeCheck size={18} />, allPaths, true)}
      {markButton(i18n.get('FolderMarkUnplayed'), <IconEyeOff size={18} />, selected, false)}
      {markButton(i18n.get('FolderMarkAllUnplayed'), <IconEyeX size={18} />, allPaths, false)}
      {options?.allowDiskOperations && (
        <>
          <Divider my="xs" />
          {deleteButton(i18n.get('DeletePlayedFromDisk'), <IconTrashX size={18} />, playedFiles)}
          {deleteButton(i18n.get('DeleteSelectedFromDisk'), <IconTrash size={18} />, selectedFiles)}
          <Divider my="xs" />
          {button(i18n.get('DeleteFolderFromDisk'), <IconFolderX size={18} />, !!folder, () => setFolderToDelete(folder), 'red')}
        </>
      )}

      <ConfirmDelete
        i18n={i18n}
        opened={folderToDelete !== undefined}
        title={i18n.get('DeleteFolderFromDisk')}
        icon={<IconFolderX size={18} />}
        onClose={() => setFolderToDelete(undefined)}
        onConfirm={() => folderToDelete && onDeleteFolder(folderToDelete)}
      >
        <Text size="sm" fw={500} style={{ wordBreak: 'break-all' }}>{folderToDelete}</Text>
        <Text size="sm">{i18n.get(recycle ? 'DeleteFolderToRecycleBinWarning' : 'DeleteFolderFromDiskWarning')}</Text>
        {folderCounts && (
          <Text size="sm" c="dimmed">
            {i18n.getFormat(['i18n@FolderMediaFilesCount', folderCounts.files.toString(), formatSize(folderCounts.size)])}
          </Text>
        )}
      </ConfirmDelete>

      <ConfirmDelete
        i18n={i18n}
        opened={toDelete.length > 0}
        title={i18n.get('DeleteFromDisk')}
        icon={<IconTrash size={18} />}
        onClose={() => setToDelete([])}
        onConfirm={() => onDelete(toDelete.map(file => file.path))}
      >
        <Text size="sm">{i18n.getFormat([recycle ? 'i18n@DeleteToRecycleBinWarning' : 'i18n@DeleteFromDiskWarning', toDelete.length.toString()])}</Text>
        <ScrollArea.Autosize mah={240}>
          <List size="sm" spacing={2}>
            {toDelete.map(file => <List.Item key={file.path} style={{ wordBreak: 'break-all' }}>{file.name}</List.Item>)}
          </List>
        </ScrollArea.Autosize>
      </ConfirmDelete>
    </Stack>
  )
}

/** Warning dialog with Cancel and a red Delete button; Delete calls onConfirm, then closes */
function ConfirmDelete({
  i18n,
  opened,
  title,
  icon,
  onClose,
  onConfirm,
  children,
}: {
  i18n: I18nInterface
  opened: boolean
  title: string
  icon: React.ReactNode
  onClose: () => void
  onConfirm: () => void
  children: React.ReactNode
}) {
  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={(
        <Group gap="xs">
          <IconAlertTriangle size={20} color="var(--mantine-color-red-6)" />
          {title}
        </Group>
      )}
      centered
    >
      <Stack>
        {children}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>{i18n.get('Cancel')}</Button>
          <Button
            color="red"
            leftSection={icon}
            onClick={() => {
              onConfirm()
              onClose()
            }}
          >
            {i18n.get('Delete')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}
// </custom:folders-root>
