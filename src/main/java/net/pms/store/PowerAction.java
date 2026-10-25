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
// <custom:reload-media> Whole file: "Hibernate computer" / "Shut down computer" items of the "Control" folder
package net.pms.store;

import com.sun.jna.Platform;
import java.io.IOException;
import net.pms.renderers.Renderer;
import net.pms.store.item.VirtualVideoActionLocalized;
import net.pms.util.ProcessUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernates or shuts down the computer UMS runs on (Windows only). Standard
 * action items, the same as "Shut down computer" of the server settings
 * folder.
 */
public class PowerAction extends VirtualVideoActionLocalized {

	private static final Logger LOGGER = LoggerFactory.getLogger(PowerAction.class);

	public enum Mode {
		HIBERNATE,
		SHUT_DOWN
	}

	private final Mode mode;

	public PowerAction(Renderer renderer, Mode mode) {
		super(renderer, mode == Mode.HIBERNATE ? "HibernateComputer" : "ControlShutDown", true, "images/store/action-shutdown.png");
		this.mode = mode;
	}

	/**
	 * @return whether the computer can be hibernated / shut down from here
	 */
	public static boolean isSupported() {
		return Platform.isWindows();
	}

	@Override
	public boolean enable() {
		LOGGER.info("{} requested by {}", mode == Mode.HIBERNATE ? "Hibernation" : "Shutdown", renderer.getRendererName());
		if (mode == Mode.SHUT_DOWN) {
			// Standard UMS shutdown: shutdown.exe -s -t 0, then UMS exits cleanly
			ProcessUtil.shutDownComputer();
			// Shutdown failed if we get here
			return false;
		}
		try {
			new ProcessBuilder("shutdown.exe", "/h").start();
			return true;
		} catch (IOException e) {
			LOGGER.error("Hibernation failed: {}", e.getMessage());
			return false;
		}
	}

}
// </custom:reload-media>
