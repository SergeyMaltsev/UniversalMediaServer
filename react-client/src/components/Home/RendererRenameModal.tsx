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
// <custom:common> Whole file: rename a renderer (own name shown instead of the device's one)
import { Button, Group, Modal, TextInput } from '@mantine/core'
import axios from 'axios'
import { useEffect, useState } from 'react'

import { Renderer } from '../../services/home-service'
import { I18nInterface } from '../../services/i18n-service'
import { renderersApiUrl } from '../../utils'
import { showError } from '../../utils/notifications'

export default function RendererRenameModal({
  i18n,
  renderer,
  onClose,
}: {
  i18n: I18nInterface
  renderer: Renderer | null
  onClose: () => void
}) {
  const [name, setName] = useState('')
  // One save at a time: repeated Enter / clicks while the request runs are ignored
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setName(renderer?.isRenamed ? renderer.name : '')
  }, [renderer])

  const save = async (value: string) => {
    if (!renderer || saving) {
      return
    }
    setSaving(true)
    try {
      // The updated card comes back through the renderers websocket
      await axios.post(renderersApiUrl + 'renderers', { rule: renderer.uuid, name: value })
      onClose()
    }
    catch {
      showError({ title: i18n.get('Error'), message: i18n.get('ConfigurationNotSaved') })
    }
    setSaving(false)
  }

  return (
    <Modal centered opened={renderer != null} title={i18n.get('RenameRenderer')} onClose={onClose}>
      <TextInput
        data-autofocus
        label={i18n.get('RendererOwnName')}
        description={i18n.get('RendererOwnNameDescription')}
        placeholder={renderer?.deviceName}
        value={name}
        onChange={event => setName(event.currentTarget.value)}
        // Not while an IME composition is confirmed with Enter
        onKeyDown={event => event.key === 'Enter' && !event.nativeEvent.isComposing && save(name)}
      />
      <Group justify="flex-end" mt="lg">
        {renderer?.isRenamed && (
          <Button variant="default" disabled={saving} onClick={() => save('')}>{i18n.get('RestoreDeviceName')}</Button>
        )}
        <Button loading={saving} onClick={() => save(name)}>{i18n.get('Apply')}</Button>
      </Group>
    </Modal>
  )
}
// </custom:common>
