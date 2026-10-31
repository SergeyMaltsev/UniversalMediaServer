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
// <custom:folders-root> Whole file: status bar under the files of the "Folder management" page
import { Group, Text } from '@mantine/core'
import { IconEye, IconMovie, IconSquareCheck } from '@tabler/icons-react'

import { I18nInterface } from '../../services/i18n-service'
import { accentColor, FolderFile, formatSize } from '../../services/folder-service'

function Total({ icon, label, files, color }: { icon: React.ReactNode, label: string, files: FolderFile[], color?: string }) {
  const size = files.reduce((sum, file) => sum + file.size, 0)
  return (
    <Group gap={4} wrap="nowrap" c={color ?? 'dimmed'}>
      {icon}
      <Text size="xs">{`${label}: ${files.length} (${formatSize(size)})`}</Text>
    </Group>
  )
}

/** Count and size of the selected, played and all files of the folder */
export default function FolderStatusBar({
  i18n,
  files,
  selected,
}: {
  i18n: I18nInterface
  files: FolderFile[]
  selected: string[]
}) {
  return (
    <Group gap="lg" px="xs" pt={6} style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
      <Total
        icon={<IconSquareCheck size={14} />}
        label={i18n.get('FilesSelected')}
        files={files.filter(file => selected.includes(file.path))}
      />
      <Total
        icon={<IconEye size={14} />}
        label={i18n.get('FilesPlayed')}
        files={files.filter(file => file.played)}
        color={accentColor}
      />
      <Total icon={<IconMovie size={14} />} label={i18n.get('FilesTotal')} files={files} />
    </Group>
  )
}
// </custom:folders-root>
