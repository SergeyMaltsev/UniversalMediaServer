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
// <custom:common> Whole file: tests of clearing the played status without counting a playback
package net.pms.database;

import java.sql.Connection;
import net.pms.PMS;
import net.pms.TestHelper;
import net.pms.configuration.UmsConfiguration;
import net.pms.media.MediaStatus;
import org.apache.commons.configuration2.ex.ConfigurationException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MediaTableFilesStatusClearPlaybackTest {

	@BeforeEach
	public final void setUp() throws ConfigurationException, InterruptedException {
		TestHelper.setLoggingOff();
		PMS.get();
		PMS.setConfiguration(new UmsConfiguration(false));
		MediaDatabase.init();
	}

	@Test
	public void testClearPlayback() throws Exception {
		String file = "ClearPlaybackPlayed.mkv";
		try (Connection connection = MediaDatabase.get().getConnection()) {
			MediaTableFilesStatus.setLastPlayed(connection, file, 0, 1800.0);
			MediaTableFilesStatus.setFullyPlayed(connection, file, 0, true);
			MediaStatus before = MediaTableFilesStatus.getMediaStatus(connection, file, 0);
			assertTrue(before.isFullyPlayed());
			assertEquals(1800.0, before.getLastPlaybackPosition(), 0.001);

			MediaTableFilesStatus.clearPlayback(connection, file, 0);

			MediaStatus after = MediaTableFilesStatus.getMediaStatus(connection, file, 0);
			assertFalse(after.isFullyPlayed(), "played status cleared");
			assertEquals(0.0, after.getLastPlaybackPosition(), 0.001, "stopped position cleared");
			assertEquals(before.getPlaybackCount(), after.getPlaybackCount(), "no playback counted");
		}
	}

	@Test
	public void testOtherUserIsKept() throws Exception {
		String file = "ClearPlaybackOtherUser.mkv";
		try (Connection connection = MediaDatabase.get().getConnection()) {
			MediaTableFilesStatus.setFullyPlayed(connection, file, 0, true);
			MediaTableFilesStatus.setFullyPlayed(connection, file, 1, true);

			MediaTableFilesStatus.clearPlayback(connection, file, 1);

			assertTrue(MediaTableFilesStatus.isFullyPlayed(connection, file, 0));
			assertFalse(MediaTableFilesStatus.isFullyPlayed(connection, file, 1));
		}
	}

	@Test
	public void testNoRecordIsNotCreated() throws Exception {
		String file = "ClearPlaybackNeverPlayed.mkv";
		try (Connection connection = MediaDatabase.get().getConnection()) {
			MediaTableFilesStatus.clearPlayback(connection, file, 0);
			assertNull(MediaTableFilesStatus.isFullyPlayed(connection, file, 0));
		}
	}

}
// </custom:common>
