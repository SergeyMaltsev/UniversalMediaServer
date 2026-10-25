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
// <custom:folder-reset> Whole file: tests of the "Reset watched" item helpers
package net.pms.store;

import java.util.ArrayList;
import java.util.List;
import net.pms.TestHelper;
import net.pms.configuration.UmsConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class FolderResetActionTest {

	@Test
	public void testSetting() throws ConfigurationException, InterruptedException {
		TestHelper.setLoggingOff();
		UmsConfiguration configuration = new UmsConfiguration(false);
		assertTrue(configuration.isShowFolderResetItem(), "shown by default");
		configuration.getConfiguration().setProperty("show_folder_reset_item", false);
		assertFalse(configuration.isShowFolderResetItem());
	}

	@Test
	public void testMoveToEndWithoutTheItem() {
		List<StoreResource> children = new ArrayList<>();
		assertDoesNotThrow(() -> FolderResetAction.moveToEnd(children), "empty folder");
		assertTrue(children.isEmpty());
	}

}
// </custom:folder-reset>
