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
// <custom:playback-labels> Whole file: tests of the "[*]" / "[h:mm:ss]" title labels
package net.pms.util;

import net.pms.TestHelper;
import net.pms.configuration.UmsConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FullyPlayedLabelTest {

	private static final String NAME = "Movie.mkv";
	private static final double HOUR = 3600;

	private UmsConfiguration configuration;

	@BeforeEach
	public void setUp() throws ConfigurationException, InterruptedException {
		TestHelper.setLoggingOff();
		configuration = new UmsConfiguration(false);
	}

	private void set(String key, Object value) {
		configuration.getConfiguration().setProperty(key, value);
	}

	private String label(boolean fullyPlayed, double position, double duration) {
		return FullyPlayed.addPlaybackLabel(NAME, fullyPlayed, position, duration, configuration);
	}

	@Test
	public void testDefaults() {
		assertTrue(configuration.isShowProgressLabel());
		assertTrue(configuration.isShowPlayedLabel());
		assertTrue(configuration.isPlaybackLabelAtStart());
		assertEquals(60, configuration.getProgressLabelMinSeconds());
		assertEquals(120, configuration.getPlayedLabelSecondsToEnd());
	}

	@Test
	public void testNegativeSecondsAreZero() {
		set("progress_label_min_seconds", -10);
		set("played_label_seconds_to_end", -5);
		assertEquals(0, configuration.getProgressLabelMinSeconds());
		assertEquals(0, configuration.getPlayedLabelSecondsToEnd());
	}

	@Test
	public void testFormatPosition() {
		assertEquals("00:00", FullyPlayed.formatPosition(0));
		assertEquals("00:59", FullyPlayed.formatPosition(59.9));
		assertEquals("59:59", FullyPlayed.formatPosition(3599));
		assertEquals("1:00:00", FullyPlayed.formatPosition(3600));
		assertEquals("1:02:05", FullyPlayed.formatPosition(3725));
	}

	@Test
	public void testIsPlayed() {
		assertTrue(FullyPlayed.isPlayed(true, 0, 0, configuration), "marked fully played");
		assertTrue(FullyPlayed.isPlayed(false, HOUR - 120, HOUR, configuration), "120 s before the end");
		assertFalse(FullyPlayed.isPlayed(false, HOUR - 121, HOUR, configuration), "more than 120 s before the end");
		assertFalse(FullyPlayed.isPlayed(false, 50, 0, configuration), "unknown duration");
		assertFalse(FullyPlayed.isPlayed(false, 0, 90, configuration), "a short file never started");
	}

	@Test
	public void testLabels() {
		assertEquals("[*] " + NAME, label(true, 0, HOUR), "played");
		assertEquals("[*] " + NAME, label(false, HOUR - 60, HOUR), "stopped near the end");
		assertEquals("[30:00] " + NAME, label(false, HOUR / 2, HOUR), "stopped in the middle");
		assertEquals("[1:15:00] " + NAME, label(false, 4500, 2 * HOUR), "hours past one hour");
		assertEquals("[01:00] " + NAME, label(false, 60, HOUR), "60 s from the start");
		assertEquals(NAME, label(false, 59, HOUR), "less than 60 s from the start");
		assertEquals("[10:00] " + NAME, label(false, 600, 0), "unknown duration: position label, never played");
		assertEquals(NAME, label(false, 0, HOUR), "never played");
	}

	@Test
	public void testShortFile() {
		// Shorter than 60 + 120 seconds: no label from the position, only the fully played mark
		assertEquals(NAME, label(false, 100, 170), "near the end of a short file");
		assertEquals(NAME, label(false, 60, 170), "past 60 s of a short file");
		assertEquals("[*] " + NAME, label(true, 0, 170), "marked fully played");
		assertEquals("[*] " + NAME, label(false, 170, 180), "exactly 60 + 120 s long");
	}

	@Test
	public void testLabelAtTheEnd() {
		set("playback_label_position", "end");
		assertFalse(configuration.isPlaybackLabelAtStart());
		assertEquals(NAME + " [*]", label(true, 0, HOUR));
		assertEquals(NAME + " [30:00]", label(false, HOUR / 2, HOUR));
	}

	@Test
	public void testLabelsSwitchedOff() {
		set("show_played_label", false);
		set("show_progress_label", false);
		assertEquals(NAME, label(true, 0, HOUR));
		assertEquals(NAME, label(false, HOUR / 2, HOUR));
	}

	@Test
	public void testThresholds() {
		set("progress_label_min_seconds", 0);
		set("played_label_seconds_to_end", 1800);
		assertEquals("[00:10] " + NAME, label(false, 10, HOUR), "any position from 0 s");
		assertEquals("[*] " + NAME, label(false, HOUR / 2, HOUR), "played from 30 minutes before the end");
	}

}
// </custom:playback-labels>