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
// import { AppShell, Box, Burger, Center, Group, Loader, useDirection } from '@mantine/core'
// import { useLocalStorage } from '@mantine/hooks'
// </custom-remove:common>
// <custom:common> Theme breakpoints and media queries for the draggable navbar edge
import { AppShell, Box, Burger, Center, Group, Loader, useDirection, useMantineTheme } from '@mantine/core'
import { useLocalStorage, useMediaQuery } from '@mantine/hooks'
// </custom:common>
import { IconServer, IconServerOff } from '@tabler/icons-react'
import { Route, Routes, Navigate } from 'react-router'

import { I18nInterface } from '../../services/i18n-service'
import { PlayerInterface } from '../../services/player-service'
import { SessionInterface, UmsPermission } from '../../services/session-service'
import About from '../About/About'
import Accounts from '../Accounts/Accounts'
import Actions from '../Actions/Actions'
import BrowserSettings from '../BrowserSettings/BrowserSettings'
import ColorSchemeButton from './ColorSchemeButton'
// <custom:folders-root> "Folder management" page
import FolderManager from '../FolderManager/FolderManager'
// </custom:folders-root>
import Home from '../Home/Home'
import LanguagesMenu from '../UmsApp/LanguagesMenu'
import Login from '../Login/Login'
import Logs from '../Logs/Logs'
import ModeButtons from './ModeButtons'
// <custom:common> Draggable navbar edge
import NavbarSplitter from './NavbarSplitter'
// </custom:common>
import Player from '../Player/Player'
// <custom:common> Red "Restart server" button in the header
import RestartServerButton from './RestartServerButton'
// </custom:common>
import ServerSettings from '../ServerSettings/ServerSettings'
import SessionNavbar from './SessionNavbar'
import SharedContent from '../SharedContent/SharedContent'
import StatusLine from './StatusLine'
import UserMenu from './UserMenu'
import WebSocketClient from './WebSocketClient'
import KnowledgeBaseButton from './KnowledgeBaseButton'

export default function UmsAppShell({ i18n, session, player }: { i18n: I18nInterface, session: SessionInterface, player: PlayerInterface }) {
  const { dir } = useDirection()
  // <custom-remove:common>
  // const [navbarWidthSmall] = useLocalStorage<number>({
  //   key: 'mantine-navbar-width-sm',
  //   defaultValue: 250,
  // })
  // const [navbarWidthMedium] = useLocalStorage<number>({
  //   key: 'mantine-navbar-width-md',
  //   defaultValue: 300,
  // })
  // const [navbarWidthLarge] = useLocalStorage<number>({
  //   key: 'mantine-navbar-width-lg',
  //   defaultValue: 400,
  // })
  // </custom-remove:common>
  // <custom:common> Navbar width setters for the draggable navbar edge (NavbarSplitter)
  const [navbarWidthSmall, setNavbarWidthSmall] = useLocalStorage<number>({
    key: 'mantine-navbar-width-sm',
    defaultValue: 250,
  })
  const [navbarWidthMedium, setNavbarWidthMedium] = useLocalStorage<number>({
    key: 'mantine-navbar-width-md',
    defaultValue: 300,
  })
  const [navbarWidthLarge, setNavbarWidthLarge] = useLocalStorage<number>({
    key: 'mantine-navbar-width-lg',
    defaultValue: 400,
  })
  // The width of the current viewport (AppShell breakpoints sm / md / lg); no edge below sm (mobile navbar)
  const theme = useMantineTheme()
  const isLarge = useMediaQuery(`(min-width: ${theme.breakpoints.lg})`)
  const isMedium = useMediaQuery(`(min-width: ${theme.breakpoints.md})`)
  const isSmall = useMediaQuery(`(min-width: ${theme.breakpoints.sm})`)
  const [navbarWidth, setNavbarWidth] = isLarge
    ? [navbarWidthLarge, setNavbarWidthLarge]
    : isMedium ? [navbarWidthMedium, setNavbarWidthMedium] : [navbarWidthSmall, setNavbarWidthSmall]
  // </custom:common>
  return (
    <div dir={dir} className="bodyBackgroundImageScreen">
      <WebSocketClient i18n={i18n} session={session} player={player} />
      <AppShell
        padding="md"
        navbar={session.hasNavbar
          ? {
              width: { sm: navbarWidthSmall, md: navbarWidthMedium, lg: navbarWidthLarge },
              breakpoint: 'sm',
              collapsed: { mobile: !session.navbarOpened, desktop: false },
            }
          : undefined}
        header={{ height: 60 }}
        // <custom-remove:common>
        // footer={session.statusLine ? { height: 30 } : undefined}
        // </custom-remove:common>
        // <custom:common> The status bar is always shown: it holds the version and build time
        footer={{ height: 30 }}
        // </custom:common>
        bg="transparentBg"
      >
        <SessionNavbar i18n={i18n} session={session} />
        {/* <custom:common> Draggable navbar edge, the width is stored per viewport size */}
        {session.hasNavbar && isSmall && (
          <NavbarSplitter width={navbarWidth} rtl={dir === 'rtl'} onChange={setNavbarWidth} />
        )}
        {/* </custom:common> */}
        <AppShell.Header
          p="xs"
          bg="transparentBg"
        >
          <Group grow preventGrowOverflow={false} gap="xs">
            <Group justify="flex-start" gap="xs">
              {(session.hasNavbar)
                && (
                  <Burger
                    hiddenFrom="sm"
                    opened={session.navbarOpened}
                    onClick={() => session.setNavbarOpened(!session.navbarOpened)}
                    size="sm"
                  />
                )}
              {/* <custom:logo> The fork's logo at the start of the header */}
              <img src="/icon-256.png" alt="Universal Media Server" width={36} height={36} style={{ display: 'block', flexShrink: 0 }} />
              {/* </custom:logo> */}
              <ModeButtons i18n={i18n} session={session} />
              <ColorSchemeButton i18n={i18n} />
              <LanguagesMenu i18n={i18n} />
              <KnowledgeBaseButton i18n={i18n} />
            </Group>
            <Group justify="flex-end">
              <UserMenu i18n={i18n} session={session}></UserMenu>
              {/* <custom:common> Red "Restart server" icon at the right edge of the header */}
              <RestartServerButton i18n={i18n} session={session} />
              {/* </custom:common> */}
            </Group>
          </Group>
        </AppShell.Header>
        <AppShell.Main>
          {(session.initialized && session.player)
            ? (
                (!session.authenticate || (session.account && !session.isLogout))
                  ? (
                      <Routes>
                        <Route path="about" element={<About i18n={i18n} session={session} />}></Route>
                        <Route path="customize" element={<BrowserSettings i18n={i18n} session={session} />}></Route>
                        <Route path="player" element={<Player i18n={i18n} session={session} player={player} />}></Route>
                        <Route path="player/:req/:id" element={<Player i18n={i18n} session={session} player={player} />}></Route>
                        <Route index element={<Player i18n={i18n} session={session} player={player} />} />
                      </Routes>
                    )
                  : (
                      <Login i18n={i18n} session={session} />
                    )
              )
            : session.account && !session.isLogout
              ? (
                  <Routes>
                    <Route path="about" element={<About i18n={i18n} session={session} />}></Route>
                    <Route path="accounts" element={<Accounts i18n={i18n} session={session} />}></Route>
                    <Route path="actions" element={<Actions i18n={i18n} session={session} />}></Route>
                    <Route path="customize" element={<BrowserSettings i18n={i18n} session={session} />}></Route>
                    {/* <custom:folders-root> "Folder management" page */}
                    <Route path="folders" element={<FolderManager i18n={i18n} session={session} />}></Route>
                    {/* </custom:folders-root> */}
                    <Route path="logs" element={<Logs i18n={i18n} session={session} />}></Route>
                    <Route path="player" element={<Player i18n={i18n} session={session} player={player} />}></Route>
                    <Route path="player/:req/:id" element={<Player i18n={i18n} session={session} player={player} />}></Route>
                    <Route path="settings" element={<ServerSettings i18n={i18n} session={session} />}></Route>
                    <Route path="shared" element={<SharedContent i18n={i18n} session={session} />}></Route>
                    {session.havePermission(UmsPermission.settings_view)
                      ? <Route index element={<Home i18n={i18n} session={session} />} />
                      : <Route index element={<Player i18n={i18n} session={session} player={player} />} />}
                    <Route
                      path="/*"
                      element={<Navigate replace to="/" />}
                    />
                  </Routes>
                )
              : session.initialized
                ? (
                    <Login i18n={i18n} session={session} />
                  )
                : (
                    <Center>
                      <Box style={{ maxWidth: 1024 }} mx="auto">
                        { i18n.serverReadyState === 0
                          ? (
                              <Group style={{ marginTop: '150px' }}>
                                <Loader type="dots" />
                                <IconServer size="4rem" />
                                <Loader type="dots" />
                              </Group>
                            )
                          : i18n.serverReadyState === 3
                            ? (
                                <Group style={{ marginTop: '150px' }}>
                                  <IconServerOff size="4rem" />
                                </Group>
                              )
                            : <Loader size="xl" type="dots" style={{ marginTop: '150px' }} />}
                      </Box>
                    </Center>
                  )}
        </AppShell.Main>
        <AppShell.Footer>
          <StatusLine session={session} />
        </AppShell.Footer>
      </AppShell>
    </div>
  )
}
