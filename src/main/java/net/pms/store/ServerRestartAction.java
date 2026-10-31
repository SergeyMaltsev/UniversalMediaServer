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
// <custom:reload-media> Whole file: "Restart server" item of the "Control" folder
package net.pms.store;

import net.pms.PMS;
import net.pms.renderers.Renderer;
import net.pms.store.item.VirtualVideoActionLocalized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Restart server" item of the "Control" folder, the same as the standard
 * "Restart server" item of the server settings folder:
 * {@link PMS#resetMediaServer()} (the media server stops, the renderers are
 * reset, the server starts again; UMS keeps running).
 */
public class ServerRestartAction extends VirtualVideoActionLocalized {

	private static final Logger LOGGER = LoggerFactory.getLogger(ServerRestartAction.class);

	public ServerRestartAction(Renderer renderer) {
		super(renderer, "RestartServer", true, "images/store/action-restart.png");
	}

	@Override
	public boolean enable() {
		LOGGER.info("Server restart requested by {}", renderer.getRendererName());
		PMS.get().resetMediaServer();
		return true;
	}

}
// </custom:reload-media>
