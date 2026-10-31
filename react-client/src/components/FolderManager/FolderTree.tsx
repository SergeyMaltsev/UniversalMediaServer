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
// <custom:folders-root> Whole file: folder tree (left panel) of the "Folder management" page
import { Box, Group, Text, Tooltip, Tree, TreeNodeData, UseTreeReturnType } from '@mantine/core'
import { IconChevronRight, IconDatabase, IconEye, IconFolder, IconFolderOpen, IconFolders, IconMovie } from '@tabler/icons-react'

import { I18nInterface } from '../../services/i18n-service'
import { accentColor, FolderCounts, formatSize } from '../../services/folder-service'

/** Which count columns are shown, in this order: played, files, size */
export interface FolderColumns {
  played: boolean
  files: boolean
  size: boolean
}

interface Column {
  key: keyof FolderColumns
  width: number
  icon: React.ReactNode
  hint: string
  color: string
  value: (counts: FolderCounts) => string
}

const getColumns = (i18n: I18nInterface, shown: FolderColumns): Column[] => [
  {
    key: 'played' as const,
    width: 36,
    icon: <IconEye size={14} />,
    hint: i18n.get('FolderPlayedCount'),
    color: accentColor,
    // Empty when nothing is played: the column stays aligned
    value: (counts: FolderCounts) => counts.played > 0 ? counts.played.toString() : '',
  },
  {
    key: 'files' as const,
    width: 40,
    icon: <IconMovie size={14} />,
    hint: i18n.get('FolderFilesCount'),
    color: 'dimmed',
    value: (counts: FolderCounts) => counts.files.toString(),
  },
  {
    key: 'size' as const,
    width: 64,
    icon: <IconDatabase size={14} />,
    hint: i18n.get('FolderSizeHint'),
    color: 'dimmed',
    value: (counts: FolderCounts) => counts.files > 0 ? formatSize(counts.size) : '',
  },
].filter(column => shown[column.key])

/** Icons with hints over the count columns of the tree */
export function FolderTreeHeader({ i18n, shown }: { i18n: I18nInterface, shown: FolderColumns }) {
  return (
    <Group gap={6} justify="flex-end" wrap="nowrap" pb={4}>
      {getColumns(i18n, shown).map(column => (
        <Tooltip key={column.key} label={column.hint} withArrow>
          <Group w={column.width} justify="center" c={column.color} style={{ flexShrink: 0 }}>
            {column.icon}
          </Group>
        </Tooltip>
      ))}
    </Group>
  )
}

export default function FolderTree({
  i18n,
  tree,
  data,
  selected,
  shown,
  onSelect,
}: {
  i18n: I18nInterface
  tree: UseTreeReturnType
  data: TreeNodeData[]
  selected?: string
  shown: FolderColumns
  onSelect: (path: string) => void
}) {
  const columns = getColumns(i18n, shown)

  return (
    <Tree
      data={data}
      tree={tree}
      levelOffset={16}
      renderNode={({ node, expanded, hasChildren, elementProps }) => {
        const isSelected = node.value === selected
        const counts = node.nodeProps as FolderCounts | undefined
        return (
          <Group
            {...elementProps}
            gap={6}
            wrap="nowrap"
            py={3}
            onClick={(event) => {
              elementProps.onClick(event)
              onSelect(node.value)
            }}
          >
            {hasChildren
              ? <IconChevronRight size={14} style={{ flexShrink: 0, transform: expanded ? 'rotate(90deg)' : undefined }} />
              : <Box w={14} style={{ flexShrink: 0 }} />}
            {counts?.virtual
              ? <IconFolders size={16} style={{ flexShrink: 0 }} />
              : expanded
                ? <IconFolderOpen size={16} style={{ flexShrink: 0 }} />
                : <IconFolder size={16} style={{ flexShrink: 0 }} />}
            <Text size="sm" truncate c={isSelected ? accentColor : undefined} fw={isSelected ? 600 : undefined} style={{ flexGrow: 1 }}>
              {node.label}
            </Text>
            {counts && columns.map(column => (
              <Text key={column.key} size="xs" w={column.width} ta="right" c={column.color} style={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
                {column.value(counts)}
              </Text>
            ))}
          </Group>
        )
      }}
    />
  )
}
// </custom:folders-root>
