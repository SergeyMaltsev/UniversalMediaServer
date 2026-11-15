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
// <custom:incomplete-files> Whole file: no MediaInfo / FFmpeg parsing of files that are still being downloaded
package net.pms.store;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.pms.PMS;
import net.pms.configuration.UmsConfiguration;
import net.pms.store.item.RealFile;
import net.pms.util.FileUtil;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps unfinished files (torrent downloads, copies in progress) away from
 * MediaInfo and FFmpeg. Torrent clients preallocate the full file size and do
 * not lock the file, so the standard "size stable and unlocked" check passes
 * and MediaInfo scans gigabytes of zeros at 100% CPU.
 *
 * A file is unfinished when it was modified less than
 * {@code incomplete_files_check_seconds} ago (a time in the future does not
 * count), when it cannot be read now, or ({@code incomplete_files_zero_check})
 * when one of a few sampled blocks still consists of zeros only (pieces not
 * downloaded yet, e.g. a queued or paused torrent). The zero check is done for
 * video containers, and for other files only after they were seen being
 * written since the start of UMS: a finished audio file or disc image can
 * contain zeros too (silence in a WAV file, free space in an ISO). Such files are shown without media data and with an hourglass
 * before the title, and are checked again once per interval in the
 * background. A ready file that is not locked by another process is parsed
 * once and the renderers refresh.
 */
public final class IncompleteFiles {

	private static final Logger LOGGER = LoggerFactory.getLogger(IncompleteFiles.class);

	/** Shown before the title of an unfinished file (hourglass) */
	public static final String LABEL = "\u23F3";

	/** Size of one sampled block */
	static final int BLOCK_SIZE = 64 * 1024;

	/** Number of sampled blocks: the first, the last and evenly spaced ones in between */
	static final int BLOCK_COUNT = 16;

	/** Path -> state of the file when it was found unfinished */
	private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();
	/** Extensions of video containers that always get the zero check */
	private static final Set<String> VIDEO_CONTAINERS = Set.of(
		"mkv", "mk3d", "webm", "mp4", "m4v", "mov", "avi", "wmv", "asf", "flv", "ts", "m2ts", "mts", "mpg", "mpeg"
	);

	/** Paths seen being written since the start: other files get the zero check only then */
	private static final Set<String> SEEN_WRITTEN = ConcurrentHashMap.newKeySet();
	private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "Incomplete files check");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});

	private IncompleteFiles() {
	}

	/**
	 * @param lastModified the file time when it was found unfinished
	 * @param length the file size when it was found unfinished
	 * @param zeros found unfinished by a zero block (not by a recent change):
	 * stays unfinished without reading it again until the file changes
	 */
	private record Pending(long lastModified, long length, boolean zeros) {
	}

	/**
	 * @return whether the file must not be parsed now. An unfinished file is
	 * remembered and checked again in the background; an unchanged file found
	 * unfinished by its zero blocks is not read again.
	 */
	public static boolean isIncomplete(File file) {
		int seconds = getCheckSeconds();
		if (file == null || seconds <= 0 || !file.isFile()) {
			return false;
		}
		String path = file.getAbsolutePath();
		long lastModified = file.lastModified();
		long length = file.length();
		Pending pending = PENDING.get(path);
		if (pending != null && pending.zeros() && pending.lastModified() == lastModified && pending.length() == length) {
			return true;
		}
		Pending state = check(file, lastModified, length, seconds);
		if (state == null) {
			PENDING.remove(path);
			return false;
		}
		if (pending == null) {
			LOGGER.debug("File {} is not complete yet ({}), parsing is deferred", path, state.zeros() ? "zero blocks" : "being written or not readable");
		}
		PENDING.put(path, state);
		schedule(seconds);
		return true;
	}

	/**
	 * @return whether the file is known as unfinished (no file access)
	 */
	public static boolean isPending(File file) {
		return file != null && getCheckSeconds() > 0 && PENDING.containsKey(file.getAbsolutePath());
	}

	/**
	 * @return the title with the hourglass before it for an unfinished file
	 */
	public static String addLabel(String title, StoreResource resource) {
		if (resource instanceof RealFile realFile && isPending(realFile.getFile())) {
			return LABEL + " " + title;
		}
		return title;
	}

	/**
	 * @return whether the file was modified less than the check interval ago,
	 * from its time only (no read, nothing remembered); for listings
	 */
	public static boolean isRecentlyModified(File file) {
		int seconds = getCheckSeconds();
		return file != null && seconds > 0 && isRecent(file.lastModified(), seconds);
	}

	/**
	 * @return whether the time is less than the interval away from now. A time
	 * further in the future (wrong clock of a camera or a NAS) does not count:
	 * such a file would be deferred until that time.
	 */
	private static boolean isRecent(long lastModified, int seconds) {
		return Math.abs(System.currentTimeMillis() - lastModified) < seconds * 1000L;
	}

	/**
	 * @return the pending state when the file is unfinished, null when it is ready
	 */
	private static Pending check(File file, long lastModified, long length, int seconds) {
		String path = file.getAbsolutePath();
		if (isRecent(lastModified, seconds)) {
			SEEN_WRITTEN.add(path);
			return new Pending(lastModified, length, false);
		}
		if (isZeroCheck() && (isVideoContainer(file) || SEEN_WRITTEN.contains(path))) {
			try {
				if (hasZeroBlock(file, length)) {
					return new Pending(lastModified, length, true);
				}
			} catch (IOException e) {
				// Read again at the next check: the file may be free then
				LOGGER.trace("Cannot read {}: {}", file, e.getMessage());
				return new Pending(lastModified, length, false);
			}
		}
		// Still open for writing by another process (a stalled copy): not ready
		if (file.canWrite() && FileUtil.isLocked(file)) {
			return new Pending(lastModified, length, false);
		}
		SEEN_WRITTEN.remove(path);
		return null;
	}

	/**
	 * @return whether the file is a video container: its finished content has
	 * no 64 KB runs of zeros, so it always gets the zero check (torrents queued
	 * or paused before the start of UMS). Audio files and disc images can
	 * contain zeros (silence, free space): they are checked only after they
	 * were seen being written.
	 */
	static boolean isVideoContainer(File file) {
		return VIDEO_CONTAINERS.contains(FilenameUtils.getExtension(file.getName()).toLowerCase(Locale.ROOT));
	}

	/**
	 * @return whether one of the sampled blocks consists of zeros only
	 * @throws IOException when the file cannot be read now
	 */
	static boolean hasZeroBlock(File file, long length) throws IOException {
		if (length <= 0) {
			return false;
		}
		int size = (int) Math.min(BLOCK_SIZE, length);
		byte[] buffer = new byte[size];
		long last = length - size;
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			for (int i = 0; i < BLOCK_COUNT; i++) {
				long position = last * i / (BLOCK_COUNT - 1);
				raf.seek(position);
				raf.readFully(buffer);
				if (isZeros(buffer)) {
					return true;
				}
				if (last == 0) {
					break;
				}
			}
			return false;
		}
	}

	private static boolean isZeros(byte[] buffer) {
		for (byte b : buffer) {
			if (b != 0) {
				return false;
			}
		}
		return true;
	}

	private static void schedule(int seconds) {
		if (SCHEDULED.compareAndSet(false, true)) {
			EXECUTOR.schedule(IncompleteFiles::recheck, seconds, TimeUnit.SECONDS);
		}
	}

	/**
	 * Checks the remembered files again; ready files are parsed and the
	 * renderers refresh their folders.
	 */
	private static void recheck() {
		SCHEDULED.set(false);
		int seconds = getCheckSeconds();
		for (Map.Entry<String, Pending> entry : PENDING.entrySet()) {
			String path = entry.getKey();
			File file = new File(path);
			try {
				if (seconds <= 0 || !file.isFile()) {
					PENDING.remove(path);
					SEEN_WRITTEN.remove(path);
					continue;
				}
				long lastModified = file.lastModified();
				long length = file.length();
				Pending pending = entry.getValue();
				if (pending.zeros() && pending.lastModified() == lastModified && pending.length() == length) {
					continue;
				}
				Pending state = check(file, lastModified, length, seconds);
				if (state == null) {
					PENDING.remove(path);
					LOGGER.info("File {} is complete now, parsing it", path);
					MediaScanner.incompleteFileReady(file);
				} else {
					PENDING.put(path, state);
				}
			} catch (RuntimeException e) {
				LOGGER.debug("Error while checking {}: {}", path, e.getMessage());
				LOGGER.trace("", e);
			}
		}
		if (!PENDING.isEmpty() && seconds > 0) {
			schedule(seconds);
		}
	}

	private static int getCheckSeconds() {
		UmsConfiguration configuration = PMS.getConfiguration();
		return configuration != null ? configuration.getIncompleteFilesCheckSeconds() : 0;
	}

	private static boolean isZeroCheck() {
		UmsConfiguration configuration = PMS.getConfiguration();
		return configuration == null || configuration.isIncompleteFilesZeroCheck();
	}

	/**
	 * Forgets all remembered files (tests).
	 */
	static void clear() {
		PENDING.clear();
		SEEN_WRITTEN.clear();
	}
}
// </custom:incomplete-files>
