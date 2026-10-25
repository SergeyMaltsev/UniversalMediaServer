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
// <custom:common> Whole file: own names of renderers, set in the web UI
package net.pms.renderers;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.util.HashMap;
import java.util.Map;
import net.pms.PMS;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Own names of renderers by UUID, shown instead of the name the device
 * reports (UPnP friendlyName), e.g. to tell two "movian" apart. Stored in
 * UMS.conf ({@code renderers_name}) the same way as {@link RendererUser}: a
 * JSON map with {@code "} written as {@code |}, so names cannot contain them.
 */
public class RendererNames {

	private static final Logger LOGGER = LoggerFactory.getLogger(RendererNames.class);
	private static final Gson GSON = new Gson();
	private static Map<String, String> names;

	private RendererNames() {
	}

	private static Map<String, String> getNames() {
		if (names == null) {
			names = new HashMap<>();
			String value = PMS.getConfiguration().getRenderersName().replace('|', '"');
			try {
				Map<String, String> stored = GSON.fromJson(value, new TypeToken<Map<String, String>>() { }.getType());
				if (stored != null) {
					names.putAll(stored);
				}
			} catch (JsonSyntaxException e) {
				LOGGER.error("Renderer names: error reading configuration value {}", value, e);
			}
		}
		return names;
	}

	/**
	 * @return the own name of the renderer, {@code null} if it has none.
	 */
	public static synchronized String getName(String uuid) {
		return StringUtils.isBlank(uuid) ? null : getNames().get(uuid.toLowerCase());
	}

	/**
	 * Sets the own name of the renderer; a blank name removes it (the device's
	 * own name is shown again). The connected renderer's GUI is updated.
	 */
	public static synchronized void setName(String uuid, String name) {
		if (StringUtils.isBlank(uuid)) {
			return;
		}
		String key = uuid.toLowerCase();
		String clean = StringUtils.trimToEmpty(StringUtils.remove(StringUtils.remove(name, '|'), '"'));
		if (clean.isEmpty()) {
			getNames().remove(key);
		} else {
			getNames().put(key, clean);
		}
		LOGGER.info("Renderer {} renamed to \"{}\"", key, clean);
		PMS.getConfiguration().setRenderersName(GSON.toJson(getNames()).replace('"', '|'));
		Renderer renderer = ConnectedRenderers.getRendererByUUID(key);
		if (renderer != null) {
			renderer.updateRendererGui();
		}
	}

}
// </custom:common>
