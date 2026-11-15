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
// <custom:incomplete-files> Whole file: tests of the detection of files being downloaded
package net.pms.store;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import net.pms.PMS;
import net.pms.TestHelper;
import net.pms.configuration.UmsConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IncompleteFilesTest {

	private static final int SIZE = 2 * 1024 * 1024;
	private static final long OLD = System.currentTimeMillis() - 3600 * 1000L;

	@TempDir
	Path dir;

	private UmsConfiguration configuration;

	@BeforeEach
	public void setUp() throws ConfigurationException, InterruptedException {
		TestHelper.setLoggingOff();
		configuration = new UmsConfiguration(false);
		PMS.setConfiguration(configuration);
		IncompleteFiles.clear();
	}

	@AfterEach
	public void tearDown() {
		IncompleteFiles.clear();
	}

	private File write(String name, byte[] data, long lastModified) throws IOException {
		File file = dir.resolve(name).toFile();
		Files.write(file.toPath(), data);
		assertTrue(file.setLastModified(lastModified));
		return file;
	}

	private static byte[] random() {
		byte[] data = new byte[SIZE];
		new Random(1).nextBytes(data);
		return data;
	}

	@Test
	public void testDefaults() {
		assertEquals(60, configuration.getIncompleteFilesCheckSeconds());
		assertTrue(configuration.isIncompleteFilesZeroCheck());
	}

	@Test
	public void testCompleteFile() throws IOException {
		File file = write("complete.mkv", random(), OLD);
		assertFalse(IncompleteFiles.isIncomplete(file));
		assertFalse(IncompleteFiles.isPending(file));
	}

	@Test
	public void testRecentlyModified() throws IOException {
		File file = write("recent.mkv", random(), System.currentTimeMillis());
		assertTrue(IncompleteFiles.isIncomplete(file));
		assertTrue(IncompleteFiles.isPending(file));
	}

	/**
	 * @return a file first seen being written (fresh time), then left alone
	 * (old time): a paused download
	 */
	private File paused(String name, byte[] data) throws IOException {
		File file = write(name, data, System.currentTimeMillis());
		assertTrue(IncompleteFiles.isIncomplete(file), "seen being written");
		assertTrue(file.setLastModified(OLD));
		return file;
	}

	@Test
	public void testPreallocated() throws IOException {
		File file = paused("preallocated.mkv", new byte[SIZE]);
		assertTrue(IncompleteFiles.isIncomplete(file));
		assertTrue(IncompleteFiles.isPending(file));
	}

	@Test
	public void testMissingPiece() throws IOException {
		byte[] data = random();
		// a hole longer than the distance between two sampled blocks
		int hole = SIZE / (IncompleteFiles.BLOCK_COUNT - 1) + IncompleteFiles.BLOCK_SIZE;
		Arrays.fill(data, SIZE / 3, SIZE / 3 + hole, (byte) 0);
		File file = paused("piece.mkv", data);
		assertTrue(IncompleteFiles.isIncomplete(file));
	}

	@Test
	public void testQueuedVideo() throws IOException {
		// A torrent queued or paused before the start: video containers always get the zero check
		File file = write("queued.mkv", new byte[SIZE], OLD);
		assertTrue(IncompleteFiles.isIncomplete(file));
	}

	@Test
	public void testFinishedFileWithZeros() throws IOException {
		// Never seen being written (e.g. silence in a WAV file): no zero check
		File file = write("silence.wav", new byte[SIZE], OLD);
		assertFalse(IncompleteFiles.isIncomplete(file));
		assertFalse(IncompleteFiles.isPending(file));
	}

	@Test
	public void testFutureTime() throws IOException {
		File file = write("future.mkv", random(), System.currentTimeMillis() + 24 * 3600 * 1000L);
		assertFalse(IncompleteFiles.isIncomplete(file));
		assertFalse(IncompleteFiles.isRecentlyModified(file));
	}

	@Test
	public void testRecentlyModifiedHasNoSideEffects() throws IOException {
		File file = write("recent.mkv", random(), System.currentTimeMillis());
		assertTrue(IncompleteFiles.isRecentlyModified(file));
		assertFalse(IncompleteFiles.isPending(file));
	}

	@Test
	public void testZeroCheckOff() throws IOException {
		configuration.getConfiguration().setProperty("incomplete_files_zero_check", false);
		File file = paused("preallocated.mkv", new byte[SIZE]);
		assertFalse(IncompleteFiles.isIncomplete(file));
	}

	@Test
	public void testOff() throws IOException {
		configuration.getConfiguration().setProperty("incomplete_files_check_seconds", 0);
		File file = write("recent.mkv", new byte[SIZE], System.currentTimeMillis());
		assertFalse(IncompleteFiles.isIncomplete(file));
		assertFalse(IncompleteFiles.isPending(file));
	}

	@Test
	public void testBecomesComplete() throws IOException {
		File file = paused("download.mkv", new byte[SIZE]);
		assertTrue(IncompleteFiles.isIncomplete(file));
		Files.write(file.toPath(), random());
		assertTrue(file.setLastModified(OLD + 1000));
		assertFalse(IncompleteFiles.isIncomplete(file));
		assertFalse(IncompleteFiles.isPending(file));
	}

	@Test
	public void testSmallAndEmptyFiles() throws IOException {
		assertFalse(IncompleteFiles.isIncomplete(write("empty.mkv", new byte[0], OLD)));
		byte[] small = new byte[1000];
		new Random(2).nextBytes(small);
		assertFalse(IncompleteFiles.isIncomplete(write("small.mkv", small, OLD)));
	}

}
// </custom:incomplete-files>
