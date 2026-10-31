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
// <custom:position-tracker> Whole file: renderer reported position, clear the label on restart
package net.pms.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.pms.renderers.Renderer;
import net.pms.store.item.RealFile;
import net.pms.util.FullyPlayed;
import net.pms.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the played status in the server database that the title labels
 * ("[*]", "[h:mm:ss]") are built from.
 *
 * - For UPnP renderers that report their position (GetPositionInfo RelTime)
 *   the last reported position is handed to the standard
 *   {@link net.pms.store.container.MediaMonitor#stopped} logic when playback
 *   stops, instead of the guess from how long the HTTP stream stayed open
 *   (wrong for clients that buffer ahead). Fully played actions, refreshes
 *   and thresholds stay the standard ones.
 * - Playing a file from its beginning (no seek) for a while clears its label.
 *
 * All state is kept per renderer and file.
 */
public final class PlaybackPositionTracker {

	private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackPositionTracker.class);
	private static final String MEDIA_PATH = "/ums/media/";

	/** How long the HTTP based guess stays disabled for a file after its renderer reported the stop */
	private static final long HANDLED_WINDOW_MS = 2 * 60 * 1000;

	/**
	 * A playing entry without any renderer event for this long is stale (the
	 * device was switched off or left without STOPPED): it is dropped. Not while
	 * paused: the renderer is polled only while it plays.
	 */
	private static final long STALE_MS = 5 * 60 * 1000;

	/** Renderer id -> position of the file it is playing now */
	private static final Map<String, Position> CURRENT = new ConcurrentHashMap<>();

	/** Renderer id + path -> stopped position waiting for MediaMonitor */
	private static final Map<String, Double> REPORTED = new ConcurrentHashMap<>();

	/** Renderer id + path -> time the renderer reported the stop */
	private static final Map<String, Long> HANDLED = new ConcurrentHashMap<>();

	/**
	 * @param updated time of the last renderer event for this playback
	 * @param paused the last event was PAUSED_PLAYBACK
	 */
	private record Position(String objectId, String path, double seconds, long updated, boolean paused) {

		private Position touched(boolean nowPaused) {
			return new Position(objectId, path, seconds, System.currentTimeMillis(), nowPaused);
		}
	}

	private PlaybackPositionTracker() {
	}

	/**
	 * Called with the renderer state every time it changes (AVTransport events
	 * and the one second GetPositionInfo poll while playing).
	 */
	public static void update(Renderer renderer, Map<String, String> data) {
		if (renderer == null || data == null || renderer.getId() == null) {
			return;
		}
		String rendererId = renderer.getId();
		String state = data.get("TransportState");

		if ("PLAYING".equals(state) || "PAUSED_PLAYBACK".equals(state)) {
			boolean paused = "PAUSED_PLAYBACK".equals(state);
			String objectId = getObjectId(StringUtils.defaultIfBlank(data.get("TrackURI"), data.get("AVTransportURI")));
			Position previous = CURRENT.get(rendererId);
			if (previous != null && !previous.objectId().equals(objectId)) {
				// Another UMS file, or a stream of another server (objectId null): the previous file stopped
				if (CURRENT.remove(rendererId, previous)) {
					finishAsync(renderer, previous);
				}
				// RelTime may still belong to the previous track: take the new one from the next poll
				return;
			}
			if (objectId == null) {
				return;
			}
			double seconds = parseTime(data.get("RelTime"));
			if (seconds <= 0) {
				if (previous != null) {
					CURRENT.replace(rendererId, previous, previous.touched(paused));
				}
				return;
			}
			// Resolve the file once per playback, the position is polled every second
			String path = previous != null ? previous.path() : getFilePath(renderer, objectId);
			if (path != null) {
				CURRENT.put(rendererId, new Position(objectId, path, seconds, System.currentTimeMillis(), paused));
			}
		} else if ("STOPPED".equals(state) || "NO_MEDIA_PRESENT".equals(state)) {
			Position previous = CURRENT.remove(rendererId);
			if (previous != null) {
				finishAsync(renderer, previous);
			}
		}
	}

	/**
	 * Called by {@link net.pms.store.container.MediaMonitor#stopped}.
	 *
	 * @return the stopped position the renderer reported for this file (once),
	 * or {@code null} if there is none.
	 */
	public static Double consumeReportedPosition(Renderer renderer, String path) {
		if (renderer == null || renderer.getId() == null || path == null) {
			return null;
		}
		return REPORTED.remove(key(renderer.getId(), path));
	}

	/**
	 * @return whether this renderer reports the position of this file: it is
	 * playing it now, or reported its stop a moment ago. The HTTP stream based
	 * guess must not be used then.
	 */
	public static boolean isTrackedByRenderer(Renderer renderer, String path) {
		if (renderer == null || renderer.getId() == null || path == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		Position current = CURRENT.get(renderer.getId());
		if (current != null && !current.paused() && now - current.updated() > STALE_MS) {
			// No renderer event for a long time (device off, left without STOPPED): forget the entry,
			// so the standard HTTP stream based guess handles this stop
			CURRENT.remove(renderer.getId(), current);
			current = null;
		}
		if (current != null && path.equals(current.path())) {
			return true;
		}
		HANDLED.values().removeIf(time -> now - time > HANDLED_WINDOW_MS);
		return HANDLED.containsKey(key(renderer.getId(), path));
	}

	/**
	 * Called on the disposable "StartPlaying Event" thread when a file starts
	 * playing. If it started from the beginning and still plays from there
	 * after {@code label_reset_seconds} (no seek, not a probe or thumbnail
	 * request), its playback label is cleared.
	 */
	public static void startedFromBeginning(StoreItem item) {
		if (!(item instanceof RealFile realFile) || realFile.getFile() == null || realFile.getLastStartPosition() > 0 ||
				realFile.getDefaultRenderer() == null) {
			return;
		}
		long confirmMs = realFile.getDefaultRenderer().getUmsConfiguration().getLabelResetSeconds() * 1000L;
		if (confirmMs <= 0) {
			return;
		}
		long startTime = realFile.getLastStartSystemTime();
		try {
			Thread.sleep(confirmMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		Renderer renderer = realFile.getDefaultRenderer();
		boolean stillFromStart = realFile.getLastStartSystemTime() == startTime &&
			realFile.getLastStartPosition() <= 0 &&
			renderer != null && renderer.getPlayingRes() == realFile;
		String path = realFile.getFile().getAbsolutePath();
		if (stillFromStart && MediaStatusStore.clearPlayback(path, renderer.getAccountUserId())) {
			LOGGER.debug("{} plays from the beginning: playback label cleared", path);
			if (realFile.getParent() != null) {
				realFile.getParent().notifyRefresh();
			}
		}
	}

	private static void finishAsync(Renderer renderer, Position position) {
		Thread thread = new Thread(() -> finish(renderer, position), "Playback position commit");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Hands the reported position to the standard stop logic
	 * (MediaStore -> MediaMonitor.stopped).
	 */
	private static void finish(Renderer renderer, Position position) {
		String key = key(renderer.getId(), position.path());
		HANDLED.put(key, System.currentTimeMillis());
		REPORTED.put(key, position.seconds());
		try {
			MediaStore mediaStore = renderer.getMediaStore();
			if (mediaStore != null && mediaStore.getResource(position.objectId()) instanceof RealFile item) {
				LOGGER.debug("{} stopped on {} at {} (reported by the renderer)", position.path(), renderer.getRendererName(), FullyPlayed.formatPosition(position.seconds()));
				mediaStore.stopPlaying(item);
			}
		} finally {
			// Not consumed when the file is outside the monitored folders
			REPORTED.remove(key);
		}
	}

	private static String key(String rendererId, String path) {
		return rendererId + "|" + path;
	}

	private static String getFilePath(Renderer renderer, String objectId) {
		if (renderer.getMediaStore().getResource(objectId) instanceof RealFile realFile && realFile.getFile() != null) {
			return realFile.getFile().getAbsolutePath();
		}
		return null;
	}

	/**
	 * @return the store object id from a UMS media URL
	 * (http://host:port/ums/media/{renderer uuid}/{object id}/{name}), or
	 * {@code null} for any other URL.
	 */
	private static String getObjectId(String uri) {
		if (StringUtils.isBlank(uri)) {
			return null;
		}
		int start = uri.indexOf(MEDIA_PATH);
		if (start < 0) {
			return null;
		}
		String[] parts = uri.substring(start + MEDIA_PATH.length()).split("/");
		return parts.length > 1 && StringUtils.isNotBlank(parts[1]) ? parts[1] : null;
	}

	private static double parseTime(String time) {
		if (StringUtils.isBlank(time) || "NOT_IMPLEMENTED".equals(time)) {
			return 0;
		}
		try {
			return StringUtil.convertStringToTime(time);
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}

}
// </custom:position-tracker>
