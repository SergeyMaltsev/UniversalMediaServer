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
// <custom:reload-media> Whole file: "Reload media library" item in the root
package net.pms.store;

import net.pms.renderers.Renderer;
import net.pms.store.item.VirtualVideoActionLocalized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Reload media library" item of the "Control" folder. Playing it starts the
 * standard media library scan (all files of all shared folders are read into
 * the database, the same as "Scan media library" in the web UI). A standard
 * action item, the same as "Restart server" of the server settings folder.
 */
public class MediaRescanAction extends VirtualVideoActionLocalized {

	private static final Logger LOGGER = LoggerFactory.getLogger(MediaRescanAction.class);

	public MediaRescanAction(Renderer renderer) {
		super(renderer, "ReloadMediaLibrary", true, null);
	}

	@Override
	public boolean enable() {
		if (MediaScanner.isMediaScanRunning()) {
			LOGGER.info("Media library reload requested by {}: a scan is already running", renderer.getRendererName());
		} else {
			LOGGER.info("Media library reload requested by {}", renderer.getRendererName());
			MediaScanner.startMediaScan();
		}
		return true;
	}

}
// </custom:reload-media>
