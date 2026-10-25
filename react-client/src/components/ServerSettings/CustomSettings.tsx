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
// <custom:custom-settings> Whole file: "Custom Settings" tab with the settings of the custom patches
import { Accordion, Button, Checkbox, Code, Group, Modal, NumberInput, ScrollArea, Select, Stack, Text, TextInput, Tooltip } from '@mantine/core'
import { UseFormReturnType } from '@mantine/form'
import { IconRefresh } from '@tabler/icons-react'
import axios, { AxiosResponse } from 'axios'
import { useEffect, useState } from 'react'

import { I18nInterface } from '../../services/i18n-service'
import { actionsApiUrl, allowHtml, defaultTooltipSettings } from '../../utils'
import { showError } from '../../utils/notifications'

export default function CustomSettings({
  i18n,
  canModify,
  canRestartServer,
  form,
}: {
  i18n: I18nInterface
  canModify: boolean
  /** "New server UUID" restarts the media server: the server_restart permission, like the action */
  canRestartServer: boolean
  form: UseFormReturnType<Record<string, unknown>, (values: Record<string, unknown>) => Record<string, unknown>>
}) {
  // Server UUID (patch server-uuid): shown from GET actions, replaced by the "Server.NewUuid" action
  const [serverUuid, setServerUuid] = useState('')
  const [newUuidOpened, setNewUuidOpened] = useState(false)

  useEffect(() => {
    axios.get(actionsApiUrl)
      .then((response: AxiosResponse) => setServerUuid(response.data.serverUuid ?? ''))
      .catch(() => {})
  }, [])

  const renewServerUuid = () => {
    axios.post(actionsApiUrl, { operation: 'Server.NewUuid' })
      .then((response: AxiosResponse) => setServerUuid(response.data.serverUuid ?? ''))
      .catch(() => showError({ message: i18n.get('Error') }))
  }

  return (
    <Accordion multiple defaultValue={['CustomRootFolders', 'CustomControlFolder', 'CustomServerUuid']}>
      <Accordion.Item value="CustomRootFolders">
        <Accordion.Control>{i18n.get('CustomRootFolders')}</Accordion.Control>
        <Accordion.Panel>
          <Stack>
            <Tooltip label={allowHtml(i18n.get('FoldersContainerWillAvailable'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify}
                label={i18n.get('ShowFoldersContainer')}
                {...form.getInputProps('show_folders_container', { type: 'checkbox' })}
              />
            </Tooltip>
            <TextInput
              label={i18n.get('FoldersContainerName')}
              placeholder={i18n.get('Folders')}
              disabled={!canModify || !form.values['show_folders_container']}
              {...form.getInputProps('folders_container_name')}
            />
            <Tooltip label={allowHtml(i18n.get('AllowDiskOperationsTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify}
                label={i18n.get('AllowDiskOperations')}
                {...form.getInputProps('allow_disk_operations', { type: 'checkbox' })}
              />
            </Tooltip>
            <Tooltip label={allowHtml(i18n.get('UseRecycleBinTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify || !form.values['allow_disk_operations']}
                label={i18n.get('UseRecycleBin')}
                {...form.getInputProps('use_recycle_bin', { type: 'checkbox' })}
              />
            </Tooltip>
            <Tooltip label={allowHtml(i18n.get('PlaybackLabelPositionTooltip'))} {...defaultTooltipSettings}>
              <Select
                label={i18n.get('PlaybackLabelPosition')}
                data={[
                  { value: 'start', label: i18n.get('PlaybackLabelAtStart') },
                  { value: 'end', label: i18n.get('PlaybackLabelAtEnd') },
                ]}
                allowDeselect={false}
                disabled={!canModify}
                {...form.getInputProps('playback_label_position')}
              />
            </Tooltip>
            <Tooltip label={allowHtml(i18n.get('ProgressLabelTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify}
                label={i18n.get('ShowProgressLabel')}
                {...form.getInputProps('show_progress_label', { type: 'checkbox' })}
              />
            </Tooltip>
            <NumberInput
              label={i18n.get('ProgressLabelMinPercent')}
              min={0}
              max={100}
              suffix="%"
              disabled={!canModify || !form.values['show_progress_label']}
              {...form.getInputProps('progress_label_min_percent')}
            />
            <Tooltip label={allowHtml(i18n.get('PlayedLabelTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify}
                label={i18n.get('ShowPlayedLabel')}
                {...form.getInputProps('show_played_label', { type: 'checkbox' })}
              />
            </Tooltip>
            <NumberInput
              label={i18n.get('PlayedLabelPercent')}
              min={0}
              max={100}
              suffix="%"
              disabled={!canModify}
              {...form.getInputProps('played_label_percent')}
            />
            <Tooltip label={allowHtml(i18n.get('LabelResetTooltip'))} {...defaultTooltipSettings}>
              <NumberInput
                label={i18n.get('LabelResetSeconds')}
                min={0}
                max={600}
                disabled={!canModify}
                {...form.getInputProps('label_reset_seconds')}
              />
            </Tooltip>
            <Tooltip label={allowHtml(i18n.get('FolderResetItemTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify}
                label={i18n.get('ShowFolderResetItem')}
                {...form.getInputProps('show_folder_reset_item', { type: 'checkbox' })}
              />
            </Tooltip>
          </Stack>
        </Accordion.Panel>
      </Accordion.Item>
      <Accordion.Item value="CustomControlFolder">
        <Accordion.Control>{i18n.get('CustomControlFolder')}</Accordion.Control>
        <Accordion.Panel>
          <Stack>
            <Tooltip label={allowHtml(i18n.get('ControlFolderTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify}
                label={i18n.get('ShowControlFolder')}
                {...form.getInputProps('show_control_folder', { type: 'checkbox' })}
              />
            </Tooltip>
            <Tooltip label={allowHtml(i18n.get('ReloadMediaItemTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify || !form.values['show_control_folder']}
                label={i18n.get('ShowReloadMediaItem')}
                {...form.getInputProps('show_reload_media_item', { type: 'checkbox' })}
              />
            </Tooltip>
            <Tooltip label={allowHtml(i18n.get('RestartServerItemTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify || !form.values['show_control_folder']}
                label={i18n.get('ShowRestartServerItem')}
                {...form.getInputProps('show_restart_server_item', { type: 'checkbox' })}
              />
            </Tooltip>
            <Tooltip label={allowHtml(i18n.get('PowerItemTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify || !form.values['show_control_folder']}
                label={i18n.get('ShowHibernateItem')}
                {...form.getInputProps('show_hibernate_item', { type: 'checkbox' })}
              />
            </Tooltip>
            <Tooltip label={allowHtml(i18n.get('PowerItemTooltip'))} {...defaultTooltipSettings}>
              <Checkbox
                disabled={!canModify || !form.values['show_control_folder']}
                label={i18n.get('ShowShutdownItem')}
                {...form.getInputProps('show_shutdown_item', { type: 'checkbox' })}
              />
            </Tooltip>
          </Stack>
        </Accordion.Panel>
      </Accordion.Item>
      <Accordion.Item value="CustomServerUuid">
        <Accordion.Control>{i18n.get('CustomServerUuid')}</Accordion.Control>
        <Accordion.Panel>
          <Modal
            centered
            scrollAreaComponent={ScrollArea.Autosize}
            opened={newUuidOpened}
            onClose={() => setNewUuidOpened(false)}
            title={(<Text c="red">{i18n.get('Warning')}</Text>)}
          >
            <Text fw={600}>{i18n.get('NewServerUuidConfirm')}</Text>
            <Text>{i18n.get('NetworkConnectionsWillClosed')}</Text>
            <Text>{i18n.get('AreYouSureContinue')}</Text>
            <Group justify="flex-end" mt="md">
              <Button onClick={() => setNewUuidOpened(false)} variant="default">{i18n.get('Cancel')}</Button>
              <Button
                color="red"
                onClick={() => {
                  setNewUuidOpened(false)
                  renewServerUuid()
                }}
              >
                {i18n.get('Confirm')}
              </Button>
            </Group>
          </Modal>
          <Stack>
            <Text size="sm">{i18n.get('ServerUuidNotes')}</Text>
            <Group>
              <Code>{serverUuid}</Code>
              <Button
                color="red"
                size="compact-md"
                leftSection={<IconRefresh size={16} />}
                disabled={!canRestartServer}
                onClick={() => setNewUuidOpened(true)}
              >
                {i18n.get('NewServerUuid')}
              </Button>
            </Group>
          </Stack>
        </Accordion.Panel>
      </Accordion.Item>
    </Accordion>
  )
}
// </custom:custom-settings>
