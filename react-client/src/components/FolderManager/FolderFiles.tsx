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
// <custom:folders-root> Whole file: files of the folder (center panel) of the "Folder management" page
import { Box, Checkbox, Group, Table, Text, Tooltip, UnstyledButton } from '@mantine/core'
import { IconAspectRatio, IconChevronDown, IconChevronUp, IconClock, IconDatabase, IconEye, IconMovie, IconSelector } from '@tabler/icons-react'
import { useMemo, useState } from 'react'

import { I18nInterface } from '../../services/i18n-service'
import { accentColor, FolderFile, formatDuration, formatSize } from '../../services/folder-service'

/** A table column as narrow as its widest cell (1% shrinks it, nowrap keeps the content on one line) */
const fitContent = { width: '1%', whiteSpace: 'nowrap' } as const

type SortColumn = 'name' | 'duration' | 'codec' | 'resolution' | 'size'

const pixels = (resolution: string) => {
  const [width, height] = resolution.split('x').map(Number)
  return width && height ? width * height : 0
}

/** Compares two files by a column, names "naturally" (2 before 10) */
const compareBy = (column: SortColumn) => (a: FolderFile, b: FolderFile): number => {
  switch (column) {
    case 'duration': return a.duration - b.duration
    case 'size': return a.size - b.size
    case 'resolution': return pixels(a.resolution) - pixels(b.resolution)
    case 'codec': return a.codec.localeCompare(b.codec)
    default: return a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' })
  }
}

export default function FolderFiles({
  i18n,
  files,
  selected,
  setSelected,
  emptyText,
}: {
  i18n: I18nInterface
  files: FolderFile[]
  selected: string[]
  setSelected: (paths: string[]) => void
  /** shown instead of "No audio or video files" (all files hidden by the name filter) */
  emptyText?: string
}) {
  const [sortColumn, setSortColumn] = useState<SortColumn>('name')
  const [descending, setDescending] = useState(false)
  const allSelected = files.length > 0 && selected.length === files.length

  const sortedFiles = useMemo(() => {
    const sorted = [...files].sort(compareBy(sortColumn))
    return descending ? sorted.reverse() : sorted
  }, [files, sortColumn, descending])

  const toggle = (path: string) => {
    setSelected(selected.includes(path) ? selected.filter(value => value !== path) : [...selected, path])
  }

  const sortBy = (column: SortColumn) => {
    setDescending(column === sortColumn ? !descending : false)
    setSortColumn(column)
  }

  /**
   * Column title. The name column: its text, it takes the rest of the width. The others: an icon with the
   * name in a hint (as over the count columns of the tree), as narrow as their content.
   */
  const header = (column: SortColumn, label: string, icon?: React.ReactNode, right?: boolean) => {
    const SortIcon = column !== sortColumn ? IconSelector : descending ? IconChevronDown : IconChevronUp
    const button = (
      <UnstyledButton onClick={() => sortBy(column)} w="100%" aria-label={label}>
        <Group gap={4} wrap="nowrap" justify={right ? 'flex-end' : 'flex-start'}>
          {icon
            ? <Box display="flex" style={{ flexShrink: 0 }}>{icon}</Box>
            : <Text size="sm" fw={600} style={{ whiteSpace: 'nowrap' }}>{label}</Text>}
          <SortIcon size={14} style={{ flexShrink: 0, opacity: column === sortColumn ? 1 : 0.4 }} />
        </Group>
      </UnstyledButton>
    )
    return (
      <Table.Th style={icon ? fitContent : undefined}>
        {icon ? <Tooltip label={label} withArrow>{button}</Tooltip> : button}
      </Table.Th>
    )
  }

  if (files.length === 0) {
    return <Text size="sm" c="dimmed" p="sm">{emptyText ?? i18n.get('NoMediaFilesInFolder')}</Text>
  }

  return (
    <Table highlightOnHover stickyHeader verticalSpacing={6}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th w={40}>
            <Checkbox
              aria-label={i18n.get('SelectAll')}
              checked={allSelected}
              indeterminate={selected.length > 0 && !allSelected}
              onChange={() => setSelected(allSelected ? [] : files.map(file => file.path))}
            />
          </Table.Th>
          {header('name', i18n.get('Name'))}
          {header('duration', i18n.get('FileDuration'), <IconClock size={16} />, true)}
          {header('codec', i18n.get('FileCodec'), <IconMovie size={16} />)}
          {header('resolution', i18n.get('FileVideoSize'), <IconAspectRatio size={16} />)}
          {header('size', i18n.get('FileSize'), <IconDatabase size={16} />, true)}
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {sortedFiles.map(file => (
          <Table.Tr
            key={file.path}
            bg={selected.includes(file.path) ? 'var(--mantine-color-blue-light)' : undefined}
            style={{ cursor: 'pointer' }}
            onClick={() => toggle(file.path)}
          >
            <Table.Td>
              <Checkbox
                aria-label={file.name}
                checked={selected.includes(file.path)}
                onChange={() => toggle(file.path)}
                onClick={event => event.stopPropagation()}
              />
            </Table.Td>
            <Table.Td>
              <Group gap={6} wrap="nowrap">
                {file.played && (
                  <Box c={accentColor} display="flex" style={{ flexShrink: 0 }}>
                    <IconEye size={16} />
                  </Box>
                )}
                <Text size="sm" c={file.played ? 'dimmed' : undefined} style={{ wordBreak: 'break-all' }}>{file.title}</Text>
              </Group>
            </Table.Td>
            <Table.Td ta="right" style={fitContent}>
              <Text size="sm" c="dimmed">{formatDuration(file.duration)}</Text>
            </Table.Td>
            <Table.Td style={fitContent}>
              <Text size="sm" c="dimmed" tt="uppercase">{file.codec}</Text>
            </Table.Td>
            <Table.Td style={fitContent}>
              <Text size="sm" c="dimmed">{file.resolution}</Text>
            </Table.Td>
            <Table.Td ta="right" style={fitContent}>
              <Text size="sm" c="dimmed">{formatSize(file.size)}</Text>
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  )
}
// </custom:folders-root>
