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
// <custom:position-tracker> Whole file: tests of the renderer position tracker settings and guards
package net.pms.store;

import java.util.Map;
import net.pms.TestHelper;
import net.pms.configuration.UmsConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class PlaybackPositionTrackerTest {

	@Test
	public void testLabelResetSeconds() throws ConfigurationException, InterruptedException {
		TestHelper.setLoggingOff();
		UmsConfiguration configuration = new UmsConfiguration(false);
		assertEquals(2, configuration.getLabelResetSeconds(), "default");
		configuration.getConfiguration().setProperty("label_reset_seconds", 0);
		assertEquals(0, configuration.getLabelResetSeconds(), "0 = never");
		configuration.getConfiguration().setProperty("label_reset_seconds", -5);
		assertEquals(0, configuration.getLabelResetSeconds(), "negative values mean never");
	}

	@Test
	public void testNoRendererIsIgnored() {
		assertDoesNotThrow(() -> PlaybackPositionTracker.update(null, Map.of("TransportState", "PLAYING")));
		assertFalse(PlaybackPositionTracker.isTrackedByRenderer(null, "Movie.mkv"));
		assertNull(PlaybackPositionTracker.consumeReportedPosition(null, "Movie.mkv"));
		assertDoesNotThrow(() -> PlaybackPositionTracker.startedFromBeginning(null));
	}

}
// </custom:position-tracker>
