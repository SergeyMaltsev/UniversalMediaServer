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
// <custom:folders-root> Whole file: draggable splitter between two panels of the "Folder management" page
import { Box } from '@mantine/core'
import { useState } from 'react'

/**
 * A vertical bar between two panels: dragging it changes the width of the
 * panel on its left, or with {@code resizesRight} the one on its right
 * (between min and max).
 */
export default function PanelSplitter({
  width,
  min,
  max,
  resizesRight = false,
  onChange,
}: {
  width: number
  min: number
  max: number
  resizesRight?: boolean
  onChange: (width: number) => void
}) {
  const [dragging, setDragging] = useState(false)

  const startDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    event.preventDefault()
    const startX = event.clientX
    const startWidth = width
    const move = (moveEvent: PointerEvent) => {
      const delta = resizesRight ? startX - moveEvent.clientX : moveEvent.clientX - startX
      onChange(Math.min(max, Math.max(min, startWidth + delta)))
    }
    const stop = () => {
      setDragging(false)
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', stop)
    }
    setDragging(true)
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop)
  }

  return (
    <Box
      role="separator"
      aria-orientation="vertical"
      onPointerDown={startDrag}
      w={8}
      mx={4}
      style={{
        flexShrink: 0,
        alignSelf: 'stretch',
        cursor: 'col-resize',
        borderRadius: 4,
        background: dragging ? 'var(--mantine-color-blue-light)' : undefined,
      }}
    />
  )
}
// </custom:folders-root>
