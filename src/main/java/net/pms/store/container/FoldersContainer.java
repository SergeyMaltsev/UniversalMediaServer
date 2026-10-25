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
// <custom:folders-root> Whole file: the "Folders" container in the root, with an optional user name
package net.pms.store.container;

import net.pms.Messages;
import net.pms.renderers.Renderer;
import org.apache.commons.lang3.StringUtils;

/**
 * The "Folders" container that holds the shared folders in the root.
 *
 * Its system name stays "Folders", so the ids of the folders inside do not
 * change when it is renamed; the shown name is the user's
 * {@code folders_container_name}, or the localized "Folders" when empty.
 */
public class FoldersContainer extends LocalizedStoreContainer {

	public FoldersContainer(Renderer renderer) {
		super(renderer, "Folders");
		updateName();
	}

	/**
	 * Applies the current {@code folders_container_name}; called on every
	 * discovery of the root.
	 */
	public final void updateName() {
		String userName = getUserName();
		setName(userName != null ? userName : Messages.getString("Folders"));
	}

	@Override
	public String getLocalizedDisplayName(String lang) {
		String userName = getUserName();
		return userName != null ? userName : super.getLocalizedDisplayName(lang);
	}

	private String getUserName() {
		String name = renderer.getUmsConfiguration().getFoldersContainerName();
		return StringUtils.isBlank(name) ? null : name;
	}

}
// </custom:folders-root>
