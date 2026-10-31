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
// <custom:readme-custom> Whole file: "Custom edition" tab of the About page - README-Custom.md as HTML
import { Typography } from '@mantine/core'
import { useMemo } from 'react'
import showdown from 'showdown'

import readme from '../../../../README-Custom.md?raw'
import { I18nInterface } from '../../services/i18n-service'

const converter = new showdown.Converter({ tables: true, openLinksInNewWindow: true })

/**
 * The section of README-Custom.md for the UI language: "## Русский" for Russian, "## English" otherwise.
 * The HTML comments (custom tags) and the language links line are left out.
 */
const getSection = (markdown: string, russian: boolean): string => {
  const text = markdown.replace(/<!--[\s\S]*?-->/g, '')
  const english = text.indexOf('## English')
  const russianStart = text.indexOf('## Русский')
  if (english < 0 || russianStart < 0) {
    return text
  }
  const section = russian ? text.substring(russianStart) : text.substring(english, russianStart)
  // The section heading itself: the tab already says what it is
  return section.substring(section.indexOf('\n') + 1)
}

export default function CustomEdition({ i18n }: { i18n: I18nInterface }) {
  const russian = i18n.language.toLowerCase().startsWith('ru')
  const html = useMemo(() => converter.makeHtml(getSection(readme, russian)), [russian])
  return (
    <Typography>
      {/* Our own Markdown from the build, not user input */}
      <div dangerouslySetInnerHTML={{ __html: html }} />
    </Typography>
  )
}
// </custom:readme-custom>
