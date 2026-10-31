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
// <custom:folder-reset> Whole file: "Reset watched" item at the end of a folder
package net.pms.store;

import java.util.ArrayList;
import java.util.List;
import net.pms.renderers.Renderer;
import net.pms.store.item.RealFile;
import net.pms.store.item.VirtualVideoActionLocalized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The last item of a folder with media files. Playing it clears the played
 * status ("[*]") and the stopped position ("[h:mm:ss]") of every file in the
 * folder, in the server database too. A standard action item, the same as
 * "Restart server" of the server settings folder.
 */
public class FolderResetAction extends VirtualVideoActionLocalized {

	private static final Logger LOGGER = LoggerFactory.getLogger(FolderResetAction.class);

	public FolderResetAction(Renderer renderer) {
		super(renderer, "ResetFolderPlayback", true, null);
	}

	/**
	 * Adds the item to the end of the folder if it is enabled and the folder
	 * holds at least one file.
	 */
	public static void addTo(StoreContainer folder) {
		if (!folder.renderer.getUmsConfiguration().isShowFolderResetItem()) {
			return;
		}
		for (StoreResource child : folder.getChildren()) {
			if (child instanceof RealFile) {
				folder.addChild(new FolderResetAction(folder.renderer), true, true);
				return;
			}
		}
	}

	/**
	 * Moves the item back to the end after the folder was sorted.
	 */
	public static void moveToEnd(List<StoreResource> children) {
		for (int i = 0; i < children.size() - 1; i++) {
			if (children.get(i) instanceof FolderResetAction action) {
				children.remove(i);
				children.add(action);
				return;
			}
		}
	}

	@Override
	public boolean enable() {
		StoreContainer folder = getParent();
		if (folder == null) {
			return true;
		}
		int userId = renderer.getAccountUserId();
		int cleared = 0;
		for (StoreResource child : new ArrayList<>(folder.getChildren())) {
			// clearPlayback does not count a playback and skips files without a label or outside "monitored" folders
			if (child instanceof RealFile realFile && realFile.getFile() != null &&
					MediaStatusStore.clearPlayback(realFile.getFile().getAbsolutePath(), userId)) {
				cleared++;
			}
		}
		LOGGER.info("Playback labels cleared for {} file(s) in {}", cleared, folder.getDisplayName());
		if (cleared > 0) {
			folder.notifyRefresh();
		}
		return true;
	}

}
// </custom:folder-reset>
