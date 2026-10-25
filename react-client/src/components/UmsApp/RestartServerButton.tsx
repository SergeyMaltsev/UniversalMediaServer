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
// <custom:common> Whole file: red "Restart server" icon button in the header, same action and confirmation as in Tools
import { ActionIcon, Button, Group, Modal, ScrollArea, Text, Tooltip } from '@mantine/core'
import { IconRefresh } from '@tabler/icons-react'
import { useState } from 'react'

import { sendAction } from '../../services/actions-service'
import { I18nInterface } from '../../services/i18n-service'
import { SessionInterface, UmsPermission } from '../../services/session-service'
import { showError } from '../../utils/notifications'

export default function RestartServerButton({ i18n, session }: { i18n: I18nInterface, session: SessionInterface }) {
  const [opened, setOpened] = useState(false)
  const canRestartServer = !session.isLogout && !session.player && session.havePermission(UmsPermission.server_restart)

  if (!canRestartServer) {
    return undefined
  }
  return (
    <>
      <Modal
        centered
        scrollAreaComponent={ScrollArea.Autosize}
        opened={opened}
        onClose={() => setOpened(false)}
        title={(<Text c="red">{i18n.get('Warning')}</Text>)}
      >
        <Text fw={600}>{i18n.get('ThisRestartsMediaServices')}</Text>
        <Text>{i18n.get('NetworkConnectionsWillClosed')}</Text>
        <Text>{i18n.get('AreYouSureContinue')}</Text>
        <Group justify="flex-end" mt="md">
          <Button onClick={() => setOpened(false)} variant="default">{i18n.get('Cancel')}</Button>
          <Button
            color="red"
            onClick={() => {
              setOpened(false)
              sendAction('Server.Restart').catch(() => showError({ title: i18n.get('Error'), message: i18n.get('RestartServer') }))
            }}
          >
            {i18n.get('Confirm')}
          </Button>
        </Group>
      </Modal>
      <Tooltip label={i18n.get('RestartServer')}>
        <ActionIcon
          variant="filled"
          color="red"
          size="input-xs"
          onClick={() => setOpened(true)}
          aria-label={i18n.get('RestartServer')}
        >
          <IconRefresh size={16} />
        </ActionIcon>
      </Tooltip>
    </>
  )
}
// </custom:common>
