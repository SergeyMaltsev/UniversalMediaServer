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
// <custom:common> Whole file: draggable edge of the left navbar that changes its width
import { Box } from '@mantine/core'
import { useRef, useState } from 'react'

/** Same limits as the "Navbar width" sliders of the Customize page */
const navbarMinWidth = 100
const navbarMaxWidth = 500

/**
 * A thin bar over the inner edge of the navbar (fixed, below the header).
 * Dragging it changes the navbar width; the caller stores it.
 *
 * The pointer is captured, so the drag ends on release anywhere (outside the
 * window, over an iframe) and on a cancelled touch, never staying stuck.
 */
export default function NavbarSplitter({
  width,
  rtl,
  onChange,
}: {
  width: number
  rtl: boolean
  onChange: (width: number) => void
}) {
  const [dragging, setDragging] = useState(false)
  // Pointer x and navbar width when the drag started
  const start = useRef<{ x: number, width: number } | undefined>(undefined)

  const startDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    start.current = { x: event.clientX, width }
    setDragging(true)
  }

  const drag = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!start.current) {
      return
    }
    const delta = rtl ? start.current.x - event.clientX : event.clientX - start.current.x
    onChange(Math.round(Math.min(navbarMaxWidth, Math.max(navbarMinWidth, start.current.width + delta))))
  }

  const stopDrag = () => {
    start.current = undefined
    setDragging(false)
  }

  return (
    <Box
      role="separator"
      aria-orientation="vertical"
      onPointerDown={startDrag}
      onPointerMove={drag}
      onPointerUp={stopDrag}
      onPointerCancel={stopDrag}
      onLostPointerCapture={stopDrag}
      style={{
        position: 'fixed',
        top: 'var(--app-shell-header-offset, 60px)',
        bottom: 0,
        [rtl ? 'right' : 'left']: width - 3,
        width: 6,
        cursor: 'col-resize',
        // No browser panning on touch: the drag gets the pointer moves
        touchAction: 'none',
        // Above the navbar (app z-index 100), below modals (200)
        zIndex: 150,
        background: dragging ? 'var(--mantine-color-blue-light)' : undefined,
      }}
    />
  )
}
// </custom:common>
