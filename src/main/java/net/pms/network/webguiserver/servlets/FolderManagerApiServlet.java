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
// <custom:folders-root> Whole file: API of the "Folder management" page of the web UI
package net.pms.network.webguiserver.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.pms.PMS;
import net.pms.configuration.UmsConfiguration;
import net.pms.configuration.sharedcontent.FolderContent;
import net.pms.configuration.sharedcontent.SharedContent;
import net.pms.configuration.sharedcontent.SharedContentConfiguration;
import net.pms.configuration.sharedcontent.VirtualFolderContent;
import net.pms.database.MediaDatabase;
import net.pms.database.MediaTableFiles;
import net.pms.formats.Format;
import net.pms.formats.FormatFactory;
import net.pms.iam.Account;
import net.pms.iam.AuthService;
import net.pms.iam.Permissions;
import net.pms.media.MediaInfo;
import net.pms.media.MediaStatus;
import net.pms.network.webguiserver.GuiHttpServlet;
import net.pms.renderers.ConnectedRenderers;
import net.pms.renderers.Renderer;
import net.pms.store.MediaInfoStore;
import net.pms.store.MediaStatusStore;
import net.pms.store.MediaStoreIds;
import net.pms.util.FileWatcher;
import net.pms.util.FullyPlayed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Folder management page of the web UI: the folders of the shared content
 * with their file / played counts, the audio / video files of a folder with
 * their playback labels, marking them played / unplayed and deleting them from
 * the disk and the media library.
 *
 * Every path must be inside an active shared folder (a folder of an active
 * virtual folder counts too). A virtual folder is one tree node
 * ({@code "virtual:Name"}) with the content of its folders.
 */
@WebServlet(name = "FolderManagerApiServlet", urlPatterns = {"/v1/api/folders"}, displayName = "Folder Manager Api Servlet")
public class FolderManagerApiServlet extends GuiHttpServlet {

	private static final Logger LOGGER = LoggerFactory.getLogger(FolderManagerApiServlet.class);
	private static final Comparator<File> BY_NAME = Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER);
	/** Tree node value of a virtual folder: this prefix + its name */
	private static final String VIRTUAL_PREFIX = "virtual:";
	/** The media info reading job ("Read media info" button): one at a time */
	private static final Object READ_LOCK = new Object();
	private static Thread readThread;
	private static volatile int readDone;
	private static volatile int readTotal;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			String path = req.getPathInfo();
			if (!"/tree".equals(path) && !"/options".equals(path) && !"/read".equals(path)) {
				respondNotFound(req, resp);
				return;
			}
			Account account = getAccount(req, resp, Permissions.SETTINGS_VIEW | Permissions.SETTINGS_MODIFY);
			if (account == null) {
				return;
			}
			if ("/read".equals(path)) {
				respond(req, resp, getReadStatus().toString(), 200, "application/json");
				return;
			}
			if ("/options".equals(path)) {
				// What the page shows: the disk buttons and the delete warnings depend on these
				JsonObject options = new JsonObject();
				options.addProperty("allowDiskOperations", PMS.getConfiguration().isAllowDiskOperations());
				options.addProperty("useRecycleBin", PMS.getConfiguration().isUseRecycleBin());
				respond(req, resp, options.toString(), 200, "application/json");
				return;
			}
			// "?merge=false": the folders of a virtual folder are separate nodes inside it
			boolean merge = !"false".equals(req.getParameter("merge"));
			// "?filter=words": only the files whose name has all the words, folders without any are left out
			String[] filter = getFilterWords(req.getParameter("filter"));
			JsonArray tree = new JsonArray();
			Connection connection = null;
			try {
				connection = MediaDatabase.getConnectionIfAvailable();
				Set<String> ancestors = new HashSet<>();
				for (SharedContent sharedContent : SharedContentConfiguration.getSharedContentArray()) {
					if (sharedContent instanceof FolderContent folder && isSharedFolder(folder)) {
						addNode(tree, markShared(getFolderNode(connection, getUserId(account), folder.getFile(), ancestors, filter)), filter);
					} else if (sharedContent instanceof VirtualFolderContent virtualFolder && virtualFolder.isActive()) {
						addNode(tree, getVirtualNode(connection, getUserId(account), virtualFolder, VIRTUAL_PREFIX + virtualFolder.getName(), merge, ancestors, filter), filter);
					}
				}
			} finally {
				MediaDatabase.close(connection);
			}
			respond(req, resp, tree.toString(), 200, "application/json");
		} catch (RuntimeException e) {
			LOGGER.error("Folder tree failed: {}", e.getMessage());
			LOGGER.trace("", e);
			respondInternalServerError(req, resp);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			String path = req.getPathInfo();
			if (path == null) {
				respondNotFound(req, resp);
				return;
			}
			int permission = "/files".equals(path) ? Permissions.SETTINGS_VIEW | Permissions.SETTINGS_MODIFY : Permissions.SETTINGS_MODIFY;
			Account account = getAccount(req, resp, permission);
			if (account == null) {
				return;
			}
			boolean deleting = "/delete".equals(path) || "/delete-folder".equals(path);
			if (deleting && !PMS.getConfiguration().isAllowDiskOperations()) {
				respondForbidden(req, resp);
				return;
			}
			JsonObject data = getJsonObjectFromBody(req);
			// The page states the mode it warned about; refuse when the setting changed meanwhile
			if (deleting && data != null && data.has("recycle") && data.get("recycle").getAsBoolean() != PMS.getConfiguration().isUseRecycleBin()) {
				respond(req, resp, "{\"error\":\"RecycleBinSettingChanged\"}", 409, "application/json");
				return;
			}
			String folderPath = data != null && data.has("path") ? data.get("path").getAsString() : "";
			switch (path) {
				case "/files" -> {
					if (folderPath.startsWith(VIRTUAL_PREFIX)) {
						VirtualFolderContent virtualFolder = findVirtualFolder(folderPath);
						if (virtualFolder == null) {
							respondBadRequest(req, resp, "Not a virtual folder");
							return;
						}
						respond(req, resp, getFiles(getFolders(virtualFolder), getUserId(account)).toString(), 200, "application/json");
						return;
					}
					File folder = getSharedFile(folderPath);
					if (folder == null || !folder.isDirectory()) {
						respondBadRequest(req, resp, "Not a shared folder");
						return;
					}
					respond(req, resp, getFiles(List.of(folder), getUserId(account)).toString(), 200, "application/json");
				}
				// Reads the media info (duration, codecs, video size) of the folder's files into the media library
				case "/read" -> {
					List<File> folders;
					if (folderPath.startsWith(VIRTUAL_PREFIX)) {
						VirtualFolderContent virtualFolder = findVirtualFolder(folderPath);
						folders = virtualFolder == null ? null : getFolders(virtualFolder);
					} else {
						File folder = getSharedFile(folderPath);
						folders = folder == null || !folder.isDirectory() ? null : List.of(folder);
					}
					if (folders == null) {
						respondBadRequest(req, resp, "Not a shared folder");
						return;
					}
					respond(req, resp, startRead(folders).toString(), 200, "application/json");
				}
				case "/mark" -> {
					boolean played = data != null && data.has("played") && data.get("played").getAsBoolean();
					respond(req, resp, markFiles(getSharedFiles(data, false), played, getUserId(account)).toString(), 200, "application/json");
				}
				// Files already gone from the disk are only removed from the media library
				case "/delete" -> respond(req, resp, deleteFiles(getSharedFiles(data, true)).toString(), 200, "application/json");
				case "/delete-folder" -> {
					File folder = getSharedFile(folderPath);
					if (folder == null || !folder.isDirectory()) {
						respondBadRequest(req, resp, "Not a folder inside a shared folder");
						return;
					}
					if (holdsSharedFolder(folder)) {
						respondBadRequest(req, resp, "A shared folder or a folder holding one");
						return;
					}
					respond(req, resp, deleteFolder(folder).toString(), 200, "application/json");
				}
				default -> respondNotFound(req, resp);
			}
		} catch (RuntimeException e) {
			LOGGER.error("Folder management request failed: {}", e.getMessage());
			LOGGER.trace("", e);
			respondInternalServerError(req, resp);
		}
	}

	/**
	 * @return the logged account with one of the permissions, or {@code null}
	 * after responding with the error.
	 */
	private static Account getAccount(HttpServletRequest req, HttpServletResponse resp, int permission) throws IOException {
		Account account = AuthService.getAccountLoggedIn(req);
		if (account == null) {
			respondUnauthorized(req, resp);
			return null;
		}
		if (!account.havePermission(permission)) {
			respondForbidden(req, resp);
			return null;
		}
		return account;
	}

	/**
	 * @return the user the playback status is stored for, the same as for the
	 * renderers ({@code Renderer.getAccountUserId()}).
	 */
	private static int getUserId(Account account) {
		return account.getUser() != null && account.getUser().getId() != Integer.MAX_VALUE ? account.getUser().getId() : 0;
	}

	private static boolean isSharedFolder(FolderContent folder) {
		return folder.isActive() && folder.getFile() != null && folder.getFile().isDirectory();
	}

	/**
	 * @return the active shared folders, the folders of the active virtual
	 * folders included.
	 */
	private static List<File> getSharedFolders() {
		return collectFolders(true);
	}

	/**
	 * @return all folders of the shared content configuration, inactive ones
	 * and the folders of (inactive) virtual folders included: none of them may
	 * be deleted with a folder above it.
	 */
	private static List<File> getConfiguredFolders() {
		return collectFolders(false);
	}

	private static List<File> collectFolders(boolean activeOnly) {
		List<File> folders = new ArrayList<>();
		for (SharedContent sharedContent : SharedContentConfiguration.getSharedContentArray()) {
			addFolders(sharedContent, folders, activeOnly);
		}
		return folders;
	}

	private static void addFolders(SharedContent content, List<File> folders, boolean activeOnly) {
		if (content instanceof FolderContent folder && (activeOnly ? isSharedFolder(folder) : folder.getFile() != null)) {
			folders.add(folder.getFile());
		} else if (content instanceof VirtualFolderContent virtualFolder && (!activeOnly || virtualFolder.isActive()) &&
				virtualFolder.getChilds() != null) {
			for (SharedContent child : virtualFolder.getChilds()) {
				addFolders(child, folders, activeOnly);
			}
		}
	}

	/**
	 * @return the active real folders directly in the virtual folder.
	 */
	private static List<File> getFolders(VirtualFolderContent virtualFolder) {
		List<File> folders = new ArrayList<>();
		if (virtualFolder.getChilds() != null) {
			for (SharedContent child : virtualFolder.getChilds()) {
				if (child instanceof FolderContent folder && isSharedFolder(folder) && !folders.contains(folder.getFile())) {
					folders.add(folder.getFile());
				}
			}
		}
		return folders;
	}

	/**
	 * @return the active virtual folders directly in the virtual folder.
	 */
	private static List<VirtualFolderContent> getVirtualFolders(VirtualFolderContent virtualFolder) {
		List<VirtualFolderContent> virtualFolders = new ArrayList<>();
		for (VirtualFolderContent child : virtualFolder.getVirtualFolders()) {
			if (child.isActive()) {
				virtualFolders.add(child);
			}
		}
		return virtualFolders;
	}

	/**
	 * @param value {@code "virtual:Name"}, {@code "virtual:Name/Inner"} for a
	 * virtual folder inside another one (the tree node value)
	 * @return the active virtual folder, {@code null} if there is none with
	 * this name.
	 */
	private static VirtualFolderContent findVirtualFolder(String value) {
		String[] names = value.substring(VIRTUAL_PREFIX.length()).split("/");
		List<VirtualFolderContent> candidates = new ArrayList<>();
		for (SharedContent sharedContent : SharedContentConfiguration.getSharedContentArray()) {
			if (sharedContent instanceof VirtualFolderContent virtualFolder && virtualFolder.isActive()) {
				candidates.add(virtualFolder);
			}
		}
		VirtualFolderContent found = null;
		for (String name : names) {
			found = null;
			for (VirtualFolderContent candidate : candidates) {
				if (name.equals(candidate.getName())) {
					found = candidate;
					break;
				}
			}
			if (found == null) {
				return null;
			}
			candidates = getVirtualFolders(found);
		}
		return found;
	}

	/**
	 * The containment check uses canonical paths (no {@code ..} or symlinks out
	 * of the shared folder; Windows junctions are not resolved, so deleting
	 * also checks {@link #hasLinkOnPath}), but the returned file keeps the path
	 * as the page sent it: the playback status and the media library are keyed
	 * on the shared folder's own (non-canonical) path, the same as for the
	 * renderers.
	 *
	 * @return the file if it is inside an active shared folder, {@code null}
	 * otherwise.
	 */
	private static File getSharedFile(String path) {
		try {
			File file = new File(path);
			if (file.isAbsolute()) {
				String canonical = file.getCanonicalPath();
				for (File folder : getSharedFolders()) {
					String root = folder.getCanonicalPath();
					// A drive root ("E:\") already ends with the separator
					String prefix = root.endsWith(File.separator) ? root : root + File.separator;
					if (canonical.equals(root) || canonical.startsWith(prefix)) {
						return file.toPath().normalize().toFile();
					}
				}
			}
		} catch (IOException e) {
			LOGGER.debug("Invalid path \"{}\": {}", path, e.getMessage());
		}
		LOGGER.warn("Folder management: \"{}\" is not inside a shared folder", path);
		return null;
	}

	/**
	 * @param withMissing also return media files that no longer exist on the
	 * disk (to remove them from the media library)
	 * @return the media files of {@code {"paths": [...]}} inside the shared
	 * folders; other paths are skipped.
	 */
	private static List<File> getSharedFiles(JsonObject data, boolean withMissing) {
		List<File> files = new ArrayList<>();
		if (data != null && data.has("paths") && data.get("paths").isJsonArray()) {
			for (JsonElement path : data.getAsJsonArray("paths")) {
				File file = getSharedFile(path.getAsString());
				if (file != null && isMediaFile(file) && (file.isFile() || withMissing && !file.exists())) {
					files.add(file);
				}
			}
		}
		return files;
	}

	/**
	 * Marks the files played, or unplayed (also clears the stopped position, so
	 * the "[h:mm:ss]" label goes too; no playback is counted). Files in shared
	 * folders without "monitored" are skipped: UMS stores no status there.
	 *
	 * @return {@code {"count": marked, "skipped": not monitored}}
	 */
	private static JsonObject markFiles(List<File> files, boolean played, int userId) {
		int marked = 0;
		int skipped = 0;
		for (File file : files) {
			String path = file.getAbsolutePath();
			if (!MediaStatusStore.isStatusStored(path)) {
				skipped++;
			} else {
				if (played) {
					MediaStatusStore.setFullyPlayed(path, userId, true, null);
				} else {
					MediaStatusStore.clearPlayback(path, userId);
				}
				marked++;
			}
		}
		LOGGER.info("Folder management: {} file(s) marked {}, {} skipped (folder not monitored)", marked, played ? "played" : "unplayed", skipped);
		JsonObject result = new JsonObject();
		result.addProperty("count", marked);
		result.addProperty("skipped", skipped);
		return result;
	}

	/**
	 * @return the key of a folder in the tree's ancestor set: its real path
	 * (junctions resolved, unlike the canonical path on Windows).
	 */
	private static String getTreeKey(File folder) {
		Path real = getRealPath(folder);
		return real != null ? real.toString() : folder.getAbsolutePath();
	}

	private static boolean isMediaFile(File file) {
		Format format = FormatFactory.getAssociatedFormat(file.getAbsolutePath());
		return format != null && (format.isVideo() || format.isAudio());
	}

	private static File[] listMediaFiles(File folder) {
		File[] files = folder.listFiles(file -> file.isFile() && !file.isHidden() && isMediaFile(file));
		if (files == null) {
			return new File[0];
		}
		Arrays.sort(files, BY_NAME);
		return files;
	}

	private static File[] listSubfolders(File folder) {
		List<String> ignored = PMS.getConfiguration().getIgnoredFolderNames();
		File[] subfolders = folder.listFiles(file -> file.isDirectory() && !file.isHidden() && !file.getName().startsWith(".") &&
			!ignored.contains(file.getName()));
		if (subfolders == null) {
			return new File[0];
		}
		Arrays.sort(subfolders, BY_NAME);
		return subfolders;
	}

	/**
	 * Same result as "played" in {@link #getFiles}, but reads the media info
	 * (duration) only for files with a stopped position: the tree counts every
	 * file of the library.
	 */
	private static boolean isPlayed(Connection connection, int userId, File file) {
		MediaStatus status = MediaStatusStore.getMediaStatus(userId, file.getAbsolutePath());
		if (status.isFullyPlayed()) {
			return true;
		}
		double position = status.getLastPlaybackPosition() != null ? status.getLastPlaybackPosition() : 0;
		if (position <= 0) {
			return false;
		}
		MediaInfo mediaInfo = getMediaInfo(connection, file);
		double duration = mediaInfo != null ? mediaInfo.getDurationInSeconds() : 0;
		return FullyPlayed.isPlayed(false, position, duration, PMS.getConfiguration());
	}

	/**
	 * @return the media info stored by the media scan, {@code null} if the file
	 * is not scanned (yet).
	 */
	private static MediaInfo getMediaInfo(Connection connection, File file) {
		if (connection == null) {
			return null;
		}
		try {
			return MediaTableFiles.getMediaInfo(connection, file.getAbsolutePath(), file.lastModified());
		} catch (IOException | SQLException e) {
			LOGGER.debug("No media info for \"{}\": {}", file, e.getMessage());
			return null;
		}
	}

	/**
	 * @return the video codec, for audio files the audio codec, "" if unknown.
	 */
	private static String getCodec(MediaInfo mediaInfo) {
		if (mediaInfo == null) {
			return "";
		}
		if (mediaInfo.getDefaultVideoTrack() != null && mediaInfo.getDefaultVideoTrack().getCodec() != null) {
			return mediaInfo.getDefaultVideoTrack().getCodec();
		}
		if (mediaInfo.getDefaultAudioTrack() != null && mediaInfo.getDefaultAudioTrack().getCodec() != null) {
			return mediaInfo.getDefaultAudioTrack().getCodec();
		}
		return "";
	}

	/**
	 * @return the video size {@code "1920x1080"}, "" for audio files or if
	 * unknown.
	 */
	private static String getResolution(MediaInfo mediaInfo) {
		if (mediaInfo == null || mediaInfo.getDefaultVideoTrack() == null) {
			return "";
		}
		int width = mediaInfo.getDefaultVideoTrack().getWidth();
		int height = mediaInfo.getDefaultVideoTrack().getHeight();
		return width > 0 && height > 0 ? width + "x" + height : "";
	}

	/**
	 * @return {@code {"label", "value", "children", "nodeProps": {"files",
	 * "played", "size"}}} of the folder and its visible subfolders (Mantine Tree
	 * data); the counts and the size (bytes of the audio / video files) include
	 * the subfolders.
	 *
	 * @param ancestors real paths of the folders above: a folder reached again
	 * below itself (a junction or symlink pointing back up the tree) is shown
	 * without its content instead of recursing forever.
	 */
	private static JsonObject getFolderNode(Connection connection, int userId, File folder, Set<String> ancestors, String[] filter) {
		List<File> mediaFiles = new ArrayList<>();
		List<JsonObject> children = new ArrayList<>();
		String key = getTreeKey(folder);
		if (ancestors.add(key)) {
			mediaFiles.addAll(Arrays.asList(listMediaFiles(folder)));
			for (File subfolder : listSubfolders(folder)) {
				children.add(getFolderNode(connection, userId, subfolder, ancestors, filter));
			}
			ancestors.remove(key);
		}
		// A drive root has no name
		String label = folder.getName().isEmpty() ? folder.getAbsolutePath() : folder.getName();
		return getNode(connection, userId, label, folder.getAbsolutePath(), mediaFiles, children, filter);
	}

	/**
	 * @return the lower case words of the name filter, {@code null} for no
	 * filter. The same rule as the page: a file matches when its name has all
	 * the words, in any case.
	 */
	static String[] getFilterWords(String filter) {
		if (filter == null || filter.isBlank()) {
			return null;
		}
		return filter.trim().toLowerCase(Locale.ROOT).split("\\s+");
	}

	static boolean matchesFilter(File file, String[] filter) {
		if (filter == null) {
			return true;
		}
		String name = file.getName().toLowerCase(Locale.ROOT);
		for (String word : filter) {
			if (!name.contains(word)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Adds a top level node; with a filter only when it has matching files.
	 */
	private static void addNode(JsonArray tree, JsonObject node, String[] filter) {
		if (filter == null || node.getAsJsonObject("nodeProps").get("files").getAsInt() > 0) {
			tree.add(node);
		}
	}

	/**
	 * Marks the node of a shared folder itself ({@code "shared": true} in its
	 * nodeProps): the page offers no folder delete for it.
	 */
	private static JsonObject markShared(JsonObject node) {
		node.getAsJsonObject("nodeProps").addProperty("shared", true);
		return node;
	}

	/**
	 * @return the node of a virtual folder (see {@link #getFolderNode}), value
	 * {@code "virtual:Name"} and {@code "virtual": true} in its nodeProps.
	 * Merged: the files and subfolders of all its folders together, as the
	 * renderers show it. Not merged: one node per folder, labeled with its
	 * full path (the folders often have the same name).
	 */
	private static JsonObject getVirtualNode(Connection connection, int userId, VirtualFolderContent virtualFolder, String value,
		boolean merge, Set<String> ancestors, String[] filter) {
		List<File> mediaFiles = new ArrayList<>();
		List<JsonObject> children = new ArrayList<>();
		for (VirtualFolderContent child : getVirtualFolders(virtualFolder)) {
			children.add(getVirtualNode(connection, userId, child, value + "/" + child.getName(), merge, ancestors, filter));
		}
		for (File folder : getFolders(virtualFolder)) {
			String key = getTreeKey(folder);
			if (!merge) {
				JsonObject node = markShared(getFolderNode(connection, userId, folder, ancestors, filter));
				node.addProperty("label", folder.getAbsolutePath());
				children.add(node);
			} else if (ancestors.add(key)) {
				mediaFiles.addAll(Arrays.asList(listMediaFiles(folder)));
				for (File subfolder : listSubfolders(folder)) {
					children.add(getFolderNode(connection, userId, subfolder, ancestors, filter));
				}
				ancestors.remove(key);
			}
		}
		if (merge) {
			children.sort(Comparator.comparing(node -> node.get("label").getAsString(), String.CASE_INSENSITIVE_ORDER));
		}
		JsonObject node = getNode(connection, userId, virtualFolder.getName(), value, mediaFiles, children, filter);
		node.getAsJsonObject("nodeProps").addProperty("virtual", true);
		return node;
	}

	/**
	 * @return the tree node with the counts of its own media files and of its
	 * children. With a filter only the matching files count, and children
	 * without any are left out.
	 */
	private static JsonObject getNode(Connection connection, int userId, String label, String value, List<File> mediaFiles,
		List<JsonObject> children, String[] filter) {
		int files = 0;
		int played = 0;
		long size = 0;
		for (File file : mediaFiles) {
			if (!matchesFilter(file, filter)) {
				continue;
			}
			files++;
			size += file.length();
			if (isPlayed(connection, userId, file)) {
				played++;
			}
		}
		JsonArray childNodes = new JsonArray();
		for (JsonObject child : children) {
			JsonObject childCounts = child.getAsJsonObject("nodeProps");
			if (filter != null && childCounts.get("files").getAsInt() == 0) {
				continue;
			}
			files += childCounts.get("files").getAsInt();
			played += childCounts.get("played").getAsInt();
			size += childCounts.get("size").getAsLong();
			childNodes.add(child);
		}
		JsonObject counts = new JsonObject();
		counts.addProperty("files", files);
		counts.addProperty("played", played);
		counts.addProperty("size", size);
		JsonObject node = new JsonObject();
		node.addProperty("label", label);
		node.addProperty("value", value);
		node.add("children", childNodes);
		node.add("nodeProps", counts);
		return node;
	}

	/**
	 * @return the audio / video files of the folder: path, name, title with the
	 * playback label (the same as the renderers show), size, duration in
	 * seconds, codec, video size, whether it counts as played. Several folders
	 * (a virtual folder): their files together, sorted by name.
	 */
	private static JsonArray getFiles(List<File> folders, int userId) {
		List<File> files = new ArrayList<>();
		for (File folder : folders) {
			files.addAll(Arrays.asList(listMediaFiles(folder)));
		}
		files.sort(BY_NAME);
		JsonArray result = new JsonArray();
		Connection connection = null;
		try {
			connection = MediaDatabase.getConnectionIfAvailable();
			UmsConfiguration configuration = PMS.getConfiguration();
			for (File file : files) {
				MediaStatus status = MediaStatusStore.getMediaStatus(userId, file.getAbsolutePath());
				MediaInfo mediaInfo = getMediaInfo(connection, file);
				double position = status.getLastPlaybackPosition() != null ? status.getLastPlaybackPosition() : 0;
				double duration = mediaInfo != null ? mediaInfo.getDurationInSeconds() : 0;
				JsonObject item = new JsonObject();
				item.addProperty("path", file.getAbsolutePath());
				item.addProperty("name", file.getName());
				item.addProperty("title", FullyPlayed.addPlaybackLabel(file.getName(), status.isFullyPlayed(), position, duration, configuration));
				item.addProperty("size", file.length());
				item.addProperty("duration", duration);
				item.addProperty("codec", getCodec(mediaInfo));
				item.addProperty("resolution", getResolution(mediaInfo));
				item.addProperty("played", FullyPlayed.isPlayed(status.isFullyPlayed(), position, duration, configuration));
				result.add(item);
			}
		} finally {
			MediaDatabase.close(connection);
		}
		return result;
	}

	/**
	 * Deletes the files from the disk and removes them from the media library at
	 * once, the same way as the folder watcher does for files deleted outside
	 * UMS (database, caches, renderers' folders). A file already gone from the
	 * disk is only removed from the media library (counted as deleted). With
	 * {@code use_recycle_bin} the files go to the Recycle Bin.
	 *
	 * @return {@code {"deleted": n, "freed": bytes (moved to the Recycle Bin:
	 * still taken), "failed": [names], "reason": why the last refused file was
	 * refused (see {@link #getRecycleRefusal}, or "Link"), "recycled": the
	 * Recycle Bin was used}}
	 */
	private static JsonObject deleteFiles(List<File> files) {
		boolean recycle = PMS.getConfiguration().isUseRecycleBin();
		int deleted = 0;
		long freed = 0;
		JsonArray failed = new JsonArray();
		String reason = null;
		for (File file : files) {
			if (!file.exists()) {
				removeFromLibrary(file);
				deleted++;
				LOGGER.info("Folder management: \"{}\" is not on the disk, removed from the media library", file);
				continue;
			}
			long size = file.length();
			String refusal = hasLinkOnPath(file, false) ? "Link" : recycle ? getRecycleRefusal(file, size) : null;
			if (refusal != null) {
				LOGGER.warn("Folder management: refused to delete \"{}\": {}", file, refusal);
				failed.add(file.getName());
				reason = refusal;
				continue;
			}
			try {
				delete(file, recycle);
				deleted++;
				freed += size;
				LOGGER.info("Folder management: {} \"{}\"", recycle ? "moved to the Recycle Bin" : "deleted", file);
				removeFromLibrary(file);
			} catch (IOException e) {
				LOGGER.warn("Folder management: cannot delete \"{}\": {}", file, e.getMessage());
				failed.add(file.getName());
			}
		}
		if (deleted > 0) {
			MediaStoreIds.incrementSystemUpdateId();
		}
		return getDeleteResult(deleted, freed, failed, reason, recycle);
	}

	private static JsonObject getDeleteResult(int deleted, long freed, JsonArray failed, String reason, boolean recycled) {
		JsonObject result = new JsonObject();
		result.addProperty("deleted", deleted);
		result.addProperty("freed", freed);
		result.add("failed", failed);
		if (reason != null) {
			result.addProperty("reason", reason);
		}
		result.addProperty("recycled", recycled);
		return result;
	}

	/**
	 * Files and bytes counted while walking a folder.
	 */
	private static final class Totals {
		private int files;
		private long bytes;
	}

	/**
	 * Deletes a file, or a whole folder with {@code recycle}, for good or into
	 * the Recycle Bin. Windows moves to the Recycle Bin only on local drives:
	 * on a network drive the shell deletes for good.
	 */
	private static void delete(File file, boolean recycle) throws IOException {
		if (!recycle) {
			Files.delete(file.toPath());
			return;
		}
		ShFileOperation operation = new ShFileOperation();
		operation.wFunc = ShFileOperation.FO_DELETE;
		// A list ending with two nulls: the path, its null, and the null WString adds
		operation.pFrom = new WString(file.getAbsolutePath() + "\0");
		operation.fFlags = ShFileOperation.FLAGS;
		int result = Shell32W.INSTANCE.SHFileOperationW(operation);
		if (result != 0 || operation.fAnyOperationsAborted) {
			throw new IOException(String.format("SHFileOperation error 0x%X", result));
		}
	}

	/**
	 * SHFILEOPSTRUCTW with the paths as UTF-16 strings. jna-platform's
	 * {@code FileUtils.moveToTrash} passes them through the process encoding
	 * and breaks non-ASCII paths (error 0x7C, invalid path).
	 */
	@Structure.FieldOrder({"hwnd", "wFunc", "pFrom", "pTo", "fFlags", "fAnyOperationsAborted", "hNameMappings", "lpszProgressTitle"})
	public static class ShFileOperation extends Structure {
		private static final int FO_DELETE = 3;
		/** FOF_ALLOWUNDO (Recycle Bin), no confirmation, no progress, no error UI */
		private static final short FLAGS = 0x0040 | 0x0010 | 0x0004 | 0x0400;
		public Pointer hwnd;
		public int wFunc;
		public WString pFrom;
		public WString pTo;
		public short fFlags;
		public boolean fAnyOperationsAborted;
		public Pointer hNameMappings;
		public WString lpszProgressTitle;
	}

	public interface Shell32W extends StdCallLibrary {
		Shell32W INSTANCE = Native.load("shell32", Shell32W.class, W32APIOptions.UNICODE_OPTIONS);

		// The native function name
		@SuppressWarnings("checkstyle:MethodName")
		int SHFileOperationW(ShFileOperation operation);
	}

	/**
	 * @return whether the folder is a shared folder itself or holds one
	 * (deleting it would delete a whole shared folder).
	 */
	private static boolean holdsSharedFolder(File folder) {
		// Real paths: canonical ones keep junctions, so a shared folder reached through a link would not match
		Path real = getRealPath(folder);
		if (real == null) {
			return true;
		}
		for (File sharedFolder : getConfiguredFolders()) {
			Path root = getRealPath(sharedFolder);
			if (root != null && root.startsWith(real)) {
				return true;
			}
		}
		return false;
	}

	private static Path getRealPath(File file) {
		try {
			return file.toPath().toRealPath();
		} catch (IOException | InvalidPathException e) {
			return null;
		}
	}

	/**
	 * Every path element below the shared folder up to the target ({@code
	 * withTarget}) or up to its parent must be a plain folder: a junction or a
	 * symlink on the way could lead the deletion out of the shared folder (the
	 * containment check works on canonical paths, which keep junctions).
	 *
	 * @return whether there is such a link, or the path is not below an active
	 * shared folder
	 */
	private static boolean hasLinkOnPath(File target, boolean withTarget) {
		Path path = target.toPath().toAbsolutePath().normalize();
		for (File sharedFolder : getSharedFolders()) {
			Path root = sharedFolder.toPath().toAbsolutePath().normalize();
			if (path.startsWith(root) && !path.equals(root)) {
				Path current = root;
				int last = withTarget ? path.getNameCount() : path.getNameCount() - 1;
				for (int i = root.getNameCount(); i < last; i++) {
					current = current.resolve(path.getName(i));
					if (isLink(current.toFile())) {
						return true;
					}
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * Windows deletes for good, without asking, what it cannot put into the
	 * Recycle Bin. Such items are refused instead.
	 *
	 * @param size bytes of the file or of the whole folder
	 * @return the refusal reason ({@code "NotFixedDrive"}, {@code
	 * "RecycleBinOff"}, {@code "TooLargeForRecycleBin"}), {@code null} when it
	 * goes to the Recycle Bin
	 */
	private static String getRecycleRefusal(File file, long size) {
		// The Recycle Bin is used on Windows only (Kernel32 and the registry below)
		if (!Platform.isWindows()) {
			return "NotFixedDrive";
		}
		Path root = file.toPath().toAbsolutePath().getRoot();
		String rootName = root == null ? "" : root.toString();
		// Network (UNC) paths and anything but a local fixed drive have no Recycle Bin
		if (rootName.startsWith("\\\\") || Kernel32.INSTANCE.GetDriveType(rootName) != WinBase.DRIVE_FIXED) {
			return "NotFixedDrive";
		}
		char[] volumeName = new char[64];
		if (!Kernel32.INSTANCE.GetVolumeNameForVolumeMountPoint(rootName, volumeName, volumeName.length)) {
			return "NotFixedDrive";
		}
		String volume = Native.toString(volumeName);
		int start = volume.indexOf('{');
		int end = volume.indexOf('}');
		String key = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\BitBucket\\Volume\\" + (start >= 0 && end > start ? volume.substring(start, end + 1) : "");
		long maxBytes;
		if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) {
			if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, "NukeOnDelete") &&
					Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, key, "NukeOnDelete") != 0) {
				return "RecycleBinOff";
			}
			// MaxCapacity is in MB
			maxBytes = Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, "MaxCapacity") ?
				(Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, key, "MaxCapacity") & 0xFFFFFFFFL) * 1024 * 1024 :
				root.toFile().getTotalSpace() / 20;
		} else {
			// No settings for the volume yet: assume the smallest usual default, 5% of the drive
			maxBytes = root.toFile().getTotalSpace() / 20;
		}
		return size > maxBytes ? "TooLargeForRecycleBin" : null;
	}

	/**
	 * Deletes the folder with all its content (any files, not only audio /
	 * video) from the disk; the deleted audio / video files leave the media
	 * library the same way as in {@link #deleteFiles(List)}. A symlink or a
	 * junction inside is deleted as a link: its target's content is not
	 * touched. Files that cannot be deleted stay, with their folders. With
	 * {@code use_recycle_bin} the whole folder goes to the Recycle Bin at once
	 * (links inside stay links there); if that fails nothing is deleted.
	 *
	 * @return {@code {"deleted": files, "freed": bytes, "failed": [names],
	 * "reason": why it was refused, "removed": the folder is gone, "recycled":
	 * the Recycle Bin was used}}
	 */
	private static JsonObject deleteFolder(File folder) {
		boolean recycle = PMS.getConfiguration().isUseRecycleBin();
		JsonArray failed = new JsonArray();
		Totals totals = new Totals();
		// A junction / symlink on the way (the folder itself included) would lead the deletion out
		String reason = hasLinkOnPath(folder, true) ? "Link" : null;
		if (reason == null && recycle) {
			List<File> mediaFiles = new ArrayList<>();
			collectFolderContent(folder, totals, mediaFiles);
			reason = getRecycleRefusal(folder, totals.bytes);
			if (reason == null) {
				// The file watcher's handles on the folder and its subfolders keep Windows from moving it
				List<FileWatcher.Watch> watches = FileWatcher.unwatch(folder.toPath());
				try {
					delete(folder, true);
					mediaFiles.forEach(FolderManagerApiServlet::removeFromLibrary);
				} catch (IOException e) {
					FileWatcher.rewatch(watches, folder.toPath());
					LOGGER.warn("Folder management: cannot move the folder \"{}\" to the Recycle Bin: {}", folder, e.getMessage());
					totals = new Totals();
					failed.add(folder.getName());
				}
			}
		} else if (reason == null) {
			deleteFolderContent(folder, totals, failed);
			try {
				Files.delete(folder.toPath());
			} catch (IOException e) {
				LOGGER.warn("Folder management: cannot delete the folder \"{}\": {}", folder, e.getMessage());
			}
		}
		if (reason != null) {
			LOGGER.warn("Folder management: refused to delete the folder \"{}\": {}", folder, reason);
			totals = new Totals();
			failed.add(folder.getName());
		}
		boolean removed = !folder.exists();
		if (removed) {
			LOGGER.info("Folder management: {} the folder \"{}\" ({} files)", recycle ? "moved to the Recycle Bin" : "deleted", folder, totals.files);
			for (Renderer renderer : ConnectedRenderers.getConnectedRenderers()) {
				if (renderer.hasMediaStore()) {
					renderer.getMediaStore().fileRemoved(folder);
				}
			}
		}
		MediaStoreIds.incrementSystemUpdateId();
		JsonObject result = getDeleteResult(totals.files, totals.bytes, failed, reason, recycle);
		result.addProperty("removed", removed);
		return result;
	}

	/**
	 * Counts the files of the folder (links are not followed) and collects its
	 * audio / video files, without deleting anything.
	 */
	private static void collectFolderContent(File folder, Totals totals, List<File> mediaFiles) {
		File[] children = folder.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			if (isLink(child)) {
				continue;
			}
			if (child.isDirectory()) {
				collectFolderContent(child, totals, mediaFiles);
			} else if (child.isFile()) {
				totals.files++;
				totals.bytes += child.length();
				if (isMediaFile(child)) {
					mediaFiles.add(child);
				}
			}
		}
	}

	private static void deleteFolderContent(File folder, Totals deleted, JsonArray failed) {
		File[] children = folder.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			// Delete only the link, never its target's content
			boolean link = isLink(child);
			if (child.isDirectory() && !link) {
				deleteFolderContent(child, deleted, failed);
			}
			boolean file = !link && child.isFile();
			long size = file ? child.length() : 0;
			try {
				Files.delete(child.toPath());
				if (file) {
					deleted.files++;
					deleted.bytes += size;
					if (isMediaFile(child)) {
						removeFromLibrary(child);
					}
				}
			} catch (IOException e) {
				LOGGER.warn("Folder management: cannot delete \"{}\": {}", child, e.getMessage());
				if (file || link) {
					failed.add(child.getName());
				}
			}
		}
	}

	/**
	 * @return whether the file is a symlink or another reparse point (a Windows
	 * junction is "other", and its canonical path does not show where it
	 * leads); unreadable attributes count as a link, so nothing behind it is
	 * deleted.
	 */
	private static boolean isLink(File file) {
		try {
			BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			return attributes.isSymbolicLink() || attributes.isOther();
		} catch (IOException e) {
			return true;
		}
	}

	/**
	 * Reads the media info of the files of the folders in a background thread,
	 * one job at a time: the same parsing and database update as when a client
	 * browses the files. A job already running is kept (its status is returned).
	 *
	 * @return {@link #getReadStatus()}
	 */
	private static JsonObject startRead(List<File> folders) {
		synchronized (READ_LOCK) {
			if (readThread == null || !readThread.isAlive()) {
				List<File> files = new ArrayList<>();
				for (File folder : folders) {
					files.addAll(Arrays.asList(listMediaFiles(folder)));
				}
				readTotal = files.size();
				readDone = 0;
				readThread = new Thread(() -> {
					for (File file : files) {
						readMediaInfo(file);
						readDone++;
					}
					LOGGER.info("Folder management: media info read for {} file(s)", files.size());
					MediaStoreIds.incrementSystemUpdateId();
				}, "Folder management media info");
				readThread.setDaemon(true);
				readThread.start();
			}
			return getReadStatus();
		}
	}

	private static void readMediaInfo(File file) {
		Format format = FormatFactory.getAssociatedFormat(file.getAbsolutePath());
		if (format == null || !file.isFile()) {
			return;
		}
		try {
			MediaInfoStore.getMediaInfo(file.getAbsolutePath(), file, format, format.getType());
		} catch (RuntimeException e) {
			LOGGER.debug("Folder management: cannot read the media info of \"{}\": {}", file, e.getMessage());
		}
	}

	/**
	 * @return {@code {"running": a job is reading, "done": files read, "total": files of the job}}
	 */
	private static JsonObject getReadStatus() {
		JsonObject status = new JsonObject();
		status.addProperty("running", readThread != null && readThread.isAlive());
		status.addProperty("done", readDone);
		status.addProperty("total", readTotal);
		return status;
	}

	private static void removeFromLibrary(File file) {
		MediaInfoStore.removeMediaEntry(file.getAbsolutePath());
		for (Renderer renderer : ConnectedRenderers.getConnectedRenderers()) {
			// Only existing stores: getMediaStore() would build a whole store for a renderer that never browsed
			if (renderer.hasMediaStore()) {
				renderer.getMediaStore().fileRemoved(file);
			}
		}
	}

}
// </custom:folders-root>
