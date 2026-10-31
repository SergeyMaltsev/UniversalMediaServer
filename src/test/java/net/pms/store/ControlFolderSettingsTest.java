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
// <custom:reload-media> Whole file: tests of the "Control" folder settings
package net.pms.store;

import com.sun.jna.Platform;
import net.pms.TestHelper;
import net.pms.configuration.UmsConfiguration;
import net.pms.store.container.ControlFolder;
import org.apache.commons.configuration2.ex.ConfigurationException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ControlFolderSettingsTest {

	private UmsConfiguration configuration;

	@BeforeEach
	public void setUp() throws ConfigurationException, InterruptedException {
		TestHelper.setLoggingOff();
		configuration = new UmsConfiguration(false);
	}

	@Test
	public void testDefaults() {
		assertTrue(configuration.isShowControlFolder());
		assertTrue(configuration.isShowReloadMediaItem());
		assertTrue(configuration.isShowRestartServerItem());
		assertTrue(configuration.isShowHibernateItem());
		assertTrue(configuration.isShowShutdownItem());
	}

	@Test
	public void testFolderShown() {
		assertTrue(ControlFolder.isShown(configuration));
		configuration.getConfiguration().setProperty("show_control_folder", false);
		assertFalse(ControlFolder.isShown(configuration), "switched off");
	}

	@Test
	public void testFolderHiddenWithoutItems() {
		configuration.getConfiguration().setProperty("show_reload_media_item", false);
		configuration.getConfiguration().setProperty("show_restart_server_item", false);
		configuration.getConfiguration().setProperty("show_hibernate_item", false);
		configuration.getConfiguration().setProperty("show_shutdown_item", false);
		assertFalse(ControlFolder.isShown(configuration), "no item switched on");
		configuration.getConfiguration().setProperty("show_restart_server_item", true);
		assertTrue(ControlFolder.isShown(configuration), "one item is enough");
	}

	@Test
	public void testPowerItemsOnWindowsOnly() {
		assertEquals(Platform.isWindows(), PowerAction.isSupported());
	}

}
// </custom:reload-media>
