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
// <custom:reload-media> Whole file: "Control" folder in the root
package net.pms.store.container;

import net.pms.configuration.UmsConfiguration;
import net.pms.renderers.Renderer;
import net.pms.store.MediaRescanAction;
import net.pms.store.PowerAction;
import net.pms.store.ServerRestartAction;

/**
 * The "Control" folder, last in the root: "Reload media library",
 * "Restart server", "Hibernate computer", "Shut down computer", each one
 * switched in the Custom Settings.
 */
public class ControlFolder extends LocalizedStoreContainer {

	public ControlFolder(Renderer renderer) {
		super(renderer, "ControlFolder");
	}

	/**
	 * @return whether the folder is shown: switched on and holds an item
	 */
	public static boolean isShown(UmsConfiguration configuration) {
		return configuration.isShowControlFolder() && (configuration.isShowReloadMediaItem() || configuration.isShowRestartServerItem() ||
			PowerAction.isSupported() && (configuration.isShowHibernateItem() || configuration.isShowShutdownItem()));
	}

	@Override
	public synchronized void discoverChildren() {
		UmsConfiguration configuration = renderer.getUmsConfiguration();
		if (configuration.isShowReloadMediaItem()) {
			addChild(new MediaRescanAction(renderer));
		}
		if (configuration.isShowRestartServerItem()) {
			addChild(new ServerRestartAction(renderer));
		}
		if (PowerAction.isSupported()) {
			if (configuration.isShowHibernateItem()) {
				addChild(new PowerAction(renderer, PowerAction.Mode.HIBERNATE));
			}
			if (configuration.isShowShutdownItem()) {
				addChild(new PowerAction(renderer, PowerAction.Mode.SHUT_DOWN));
			}
		}
	}

}
// </custom:reload-media>
