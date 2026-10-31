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
package net.pms.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.pms.Messages;
// <custom:playback-labels> Label settings
import net.pms.PMS;
import net.pms.configuration.UmsConfiguration;
// </custom:playback-labels>
import net.pms.formats.Format;
import net.pms.image.BufferedImageFilter;
import net.pms.image.NonGeometricBufferedImageOp;
// <custom:playback-labels> Played position from the server database
import net.pms.media.MediaStatus;
// </custom:playback-labels>
import net.pms.media.MediaType;
import net.pms.store.StoreItem;
import net.pms.store.StoreResource;
// <custom:playback-labels> Plain files get no title suffix
import net.pms.store.item.RealFile;
// </custom:playback-labels>
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An utility class for handling "fully played" functionality.
 */
public class FullyPlayed {
	private static final Logger LOGGER = LoggerFactory.getLogger(FullyPlayed.class);
	private static final String THUMBNAIL_OVERLAY_RESOURCE_PATH = "/resources/images/store/fullyplayed-overlay.png";
	private static final Color THUMBNAIL_OVERLAY_BACKGROUND_COLOR = new Color(0.0f, 0.0f, 0.0f, 0.5f);
	private static final int BLANK_IMAGE_RESOLUTION = 256;

	/** A static cache of the fully played overlay as a {@link BufferedImage} */
	private static final BufferedImage THUMBNAIL_OVERLAY_IMAGE = getThumbnailOverlayImage();

	/** A static cache of the fully played overlay {@link BufferedImageFilter} */
	private static final FullyPlayerOverlayFilter OVERLAY_FILTER_INSTANCE = new FullyPlayerOverlayFilter(StoreResource.THUMBNAIL_HINTS);

	// Hide the constructor
	private FullyPlayed() {
	}

	/**
	 * @return The cached {@link FullyPlayerOverlayFilter} instance.
	 */
	public static BufferedImageFilter getOverlayFilter() {
		return OVERLAY_FILTER_INSTANCE;
	}

	/**
	 * Prefixes the specified string with a "fully played" text.
	 *
	 * @param displayName the {@link String} to prefix.
	 * @param resource the {@link StoreResource} representing the media.
	 * @return The prefixed {@link String}.
	 */
	public static String addFullyPlayedNamePrefix(String displayName, StoreResource resource) {
		MediaType mediaType;
		if (resource.getMediaInfo() != null) {
			mediaType = resource.getMediaInfo().getMediaType();
		} else if (resource instanceof StoreItem item && item.getFormat() != null) {
			mediaType = switch (item.getFormat().getType()) {
				case Format.AUDIO -> MediaType.AUDIO;
				case Format.IMAGE -> MediaType.IMAGE;
				case Format.VIDEO -> MediaType.VIDEO;
				default -> MediaType.UNKNOWN;
			};
		} else {
			mediaType = MediaType.UNKNOWN;
		}
		return switch (mediaType) {
			case IMAGE -> String.format("[%s] %s", Messages.getString("Viewed"), displayName);
			case VIDEO -> String.format("[%s] %s", Messages.getString("Watched"), displayName);
			default -> String.format("[%s] %s", Messages.getString("Played"), displayName);
		};
	}

	// <custom:playback-labels> "<name> [*]" / "<name> [h:mm:ss]" labels from the server database
	/**
	 * Appends the playback label from the server database to the name,
	 * with the plugin's own settings (independent of "resume"):
	 * <ul>
	 * <li>{@code "<name> [*]"} - played: marked fully played in the database,
	 * or stopped within {@code played_label_seconds_to_end} of the end
	 * ({@code show_played_label});</li>
	 * <li>{@code "<name> [h:mm:ss]"} - stopped position from
	 * {@code progress_label_min_seconds} on, hours only past one hour
	 * ({@code show_progress_label});</li>
	 * <li>nothing otherwise.</li>
	 * </ul>
	 *
	 * @param displayName the {@link String} to label.
	 * @param resource the {@link StoreResource} representing the media.
	 * @return The labelled {@link String}.
	 */
	public static String addPlaybackLabel(String displayName, StoreResource resource) {
		UmsConfiguration configuration = resource.getDefaultRenderer() != null ?
			resource.getDefaultRenderer().getUmsConfiguration() : PMS.getConfiguration();
		MediaStatus status = resource.getMediaStatus();
		double position = status != null && status.getLastPlaybackPosition() != null ? status.getLastPlaybackPosition() : 0;
		double duration = resource.getMediaInfo() != null ? resource.getMediaInfo().getDurationInSeconds() : 0;
		return addPlaybackLabel(displayName, resource.isFullyPlayed(), position, duration, configuration);
	}

	/**
	 * Same as {@link #addPlaybackLabel(String, StoreResource)} from the raw
	 * status values (folder management page of the web UI).
	 *
	 * @param fullyPlayed marked fully played in the database
	 * @param position the stopped position in seconds, 0 if none
	 * @param duration the duration in seconds, 0 if unknown
	 */
	public static String addPlaybackLabel(String displayName, boolean fullyPlayed, double position, double duration, UmsConfiguration configuration) {
		if (isPlayed(fullyPlayed, position, duration, configuration)) {
			return configuration.isShowPlayedLabel() ? withLabel(displayName, "[*]", configuration) : displayName;
		}
		if (configuration.isShowProgressLabel() && !isTooShort(duration, configuration) && position > 0 &&
				position >= configuration.getProgressLabelMinSeconds()) {
			return withLabel(displayName, "[" + formatPosition(position) + "]", configuration);
		}
		return displayName;
	}

	/**
	 * @return whether the media counts as played for the "[*]" label: marked
	 * fully played, or stopped within {@code played_label_seconds_to_end} of the end.
	 * A file shorter than both thresholds together gets no label from its
	 * position (only from the fully played mark).
	 */
	public static boolean isPlayed(boolean fullyPlayed, double position, double duration, UmsConfiguration configuration) {
		return fullyPlayed || duration > 0 && !isTooShort(duration, configuration) && position > 0 &&
			position >= duration - configuration.getPlayedLabelSecondsToEnd();
	}

	/**
	 * @return whether the known duration is shorter than {@code progress_label_min_seconds} +
	 * {@code played_label_seconds_to_end}: the position labels make no sense then.
	 */
	private static boolean isTooShort(double duration, UmsConfiguration configuration) {
		return duration > 0 && duration < configuration.getProgressLabelMinSeconds() + configuration.getPlayedLabelSecondsToEnd();
	}

	/**
	 * @return the label before the title ({@code playback_label_position =
	 * start}, default: long titles are cut on the screen) or after it.
	 */
	private static String withLabel(String displayName, String label, UmsConfiguration configuration) {
		return configuration.isPlaybackLabelAtStart() ? label + " " + displayName : displayName + " " + label;
	}

	/**
	 * @return the title suffix to show: none for plain files, the standard one
	 * inside the #--TRANSCODE--# folder and for virtual items (e.g. DVD titles),
	 * where the suffix is what tells the entries apart.
	 */
	public static String getTitleSuffix(StoreResource resource) {
		if (resource instanceof RealFile && !resource.isInsideTranscodeFolder()) {
			return null;
		}
		return resource.getDisplayNameSuffix();
	}

	/**
	 * @return {@code "mm:ss"}, or {@code "h:mm:ss"} from one hour on.
	 */
	public static String formatPosition(double seconds) {
		long total = (long) seconds;
		long hours = total / 3600;
		long minutes = (total % 3600) / 60;
		long secs = total % 60;
		return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, secs) : String.format("%02d:%02d", minutes, secs);
	}
	// </custom:playback-labels>

	private static BufferedImage getThumbnailOverlayImage() {
		BufferedImage tmpThumbnailOverlayImage = null;
		try {
			tmpThumbnailOverlayImage = ImageIO.read(FullyPlayed.class.getResourceAsStream(THUMBNAIL_OVERLAY_RESOURCE_PATH));
		} catch (IOException e) {
			LOGGER.error("Error reading fully played overlay image \"{}\": {}", THUMBNAIL_OVERLAY_RESOURCE_PATH, e.getMessage());
			LOGGER.trace("", e);
		}
		return tmpThumbnailOverlayImage;
	}

	/**
	 * A {@link BufferedImageFilter} implementation that applies the
	 * "fully played" overlay on the specified {@link BufferedImage}.
	 *
	 * @author Nadahar
	 */
	public static class FullyPlayerOverlayFilter extends NonGeometricBufferedImageOp implements BufferedImageFilter {

		/**
		 * Creates a new fully played filter instance.
		 *
		 * @param hints the {@link RenderingHints} or {@code null}.
		 */
		public FullyPlayerOverlayFilter(RenderingHints hints) {
			super(hints);
		}

		@Override
		public String getDescription() {
			return toString();
		}

		@Override
		public String toString() {
			return "Fully played image overlay";
		}

		@Override
		public BufferedImage filter(BufferedImage source, BufferedImage destination) {
			return filter(source, destination, true).getBufferedImage();
		}

		@Override
		public BufferedImageFilterResult filter(BufferedImage source) {
			return filter(source, null, true);
		}

		@Override
		public BufferedImageFilterResult filter(BufferedImage source, BufferedImage destination, boolean modifySource) {
			if (THUMBNAIL_OVERLAY_IMAGE == null) {
				return new BufferedImageFilterResult(source, false, true);
			}

			boolean sameInstance = true;
			// Create a blank image if the input is missing
			if (source == null) {
				source = new BufferedImage(BLANK_IMAGE_RESOLUTION, BLANK_IMAGE_RESOLUTION, BufferedImage.TYPE_3BYTE_BGR);
				sameInstance = false;
			}

			// Create new destination or reuse source according to modifySource
			if (destination == null) {
				if (modifySource) {
					destination = source;
				} else {
					destination = createCompatibleDestImage(source, null);
					sameInstance = false;
				}
			} else {
				sameInstance = source == destination;
			}

			int overlayResolution = (int) Math.round((Math.min(source.getWidth(), source.getHeight())) * 0.6);
			int overlayHorizontalPosition = (source.getWidth() - overlayResolution) / 2;
			int overlayVerticalPosition = (source.getHeight() - overlayResolution) / 2;

			Graphics2D g2d = destination.createGraphics();
			try {
				if (hints != null) {
					g2d.setRenderingHints(hints);
				}
				if (source != destination) {
					g2d.drawImage(source, 0, 0, null);
				}
				g2d.setPaint(THUMBNAIL_OVERLAY_BACKGROUND_COLOR);
				g2d.fillRect(0, 0, source.getWidth(), source.getHeight());
				g2d.drawImage(
					THUMBNAIL_OVERLAY_IMAGE,
					overlayHorizontalPosition,
					overlayVerticalPosition,
					overlayResolution,
					overlayResolution,
					null
				);
			} finally {
				g2d.dispose();
			}

			return new BufferedImageFilterResult(destination, true, sameInstance);
		}
	}

}
