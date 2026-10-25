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
// <custom:folders-root> Whole file: tests of the "Folder management" name filter and settings
package net.pms.network.webguiserver.servlets;

import java.io.File;
import net.pms.TestHelper;
import net.pms.configuration.UmsConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class FolderManagerApiServletTest {

	private static boolean matches(String name, String filter) {
		return matches(new File(name), filter);
	}

	private static boolean matches(File file, String filter) {
		return FolderManagerApiServlet.matchesFilter(file, FolderManagerApiServlet.getFilterWords(filter));
	}

	@Test
	public void testNoFilter() {
		assertNull(FolderManagerApiServlet.getFilterWords(null));
		assertNull(FolderManagerApiServlet.getFilterWords(""));
		assertNull(FolderManagerApiServlet.getFilterWords("   "));
		assertTrue(matches("Anything.mkv", null));
	}

	@Test
	public void testFilterWords() {
		assertArrayEquals(new String[] {"s01", "balthazar"}, FolderManagerApiServlet.getFilterWords("  S01   Balthazar "));
	}

	@Test
	public void testAllWordsAnyCase() {
		assertTrue(matches("Balthazar.S01E04.mkv", "s01e0"));
		assertTrue(matches("Balthazar.S01E04.mkv", "BALTHAZAR e04"), "all words, in any order and case");
		assertFalse(matches("Balthazar.S01E04.mkv", "balthazar e05"), "one word missing");
		assertTrue(matches("Бальтазар.1080p.mkv", "бальтазар"), "Cyrillic, lower case");
	}

	@Test
	public void testOnlyTheFileNameMatches() {
		assertFalse(matches(new File(new File("Balthazar"), "S01E04.mkv"), "balthazar"), "the folder is not part of the name");
	}

	@Test
	public void testSettingsDefaults() throws ConfigurationException, InterruptedException {
		TestHelper.setLoggingOff();
		UmsConfiguration configuration = new UmsConfiguration(false);
		assertFalse(configuration.isShowFoldersContainer());
		assertEquals("", configuration.getFoldersContainerName());
		assertFalse(configuration.isAllowDiskOperations(), "disk operations are off by default");
		assertTrue(configuration.isUseRecycleBin());
		configuration.getConfiguration().setProperty("folders_container_name", "  Video  ");
		assertEquals("Video", configuration.getFoldersContainerName(), "the name is trimmed");
	}

}
// </custom:folders-root>
