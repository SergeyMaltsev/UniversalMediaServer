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
// <custom-remove:common>
// import { Text } from '@mantine/core'
// </custom-remove:common>
// <custom:common> Version and build time on the right of the status bar
import { Group, Text } from '@mantine/core'
import axios from 'axios'
import { useContext, useEffect, useState } from 'react'

import I18nContext from '../../contexts/i18n-context'
import { aboutApiUrl } from '../../utils'
// </custom:common>

import { SessionInterface } from '../../services/session-service'

// <custom-remove:common>
// export default function StatusLine({ session }: { session: SessionInterface }) {
//   return session.statusLine
//     ? (
//         <Text>{session.statusLine}</Text>
//       )
//     : undefined
// }
// </custom-remove:common>
// <custom:common> Status text on the left, "UMS <version> custom edition - <build time>" on the right (always shown)
export default function StatusLine({ session }: { session: SessionInterface }) {
  const [build, setBuild] = useState('')
  const { serverConnected } = useContext(I18nContext)

  // Again on every reconnect: after an install and restart the tab shows the new version
  useEffect(() => {
    if (!serverConnected) {
      return
    }
    axios.get(aboutApiUrl)
      .then((response) => {
        const { app, version, buildTime } = response.data as { app: string, version: string, buildTime: string }
        setBuild(`${app} ${version} custom edition` + (buildTime ? ` - ${buildTime}` : ''))
      })
      .catch(() => setBuild(''))
  }, [serverConnected])

  return (
    <Group justify="space-between" wrap="nowrap" px="xs" h="100%">
      <Text size="sm" truncate>{session.statusLine}</Text>
      <Text size="xs" c="dimmed" style={{ flexShrink: 0 }}>{build}</Text>
    </Group>
  )
}
// </custom:common>
