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
// <custom:common> Whole file: MAC addresses of LAN devices from the system ARP table
package net.pms.network;

import com.sun.jna.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MAC address of a device on the local network, read from the system ARP
 * table ({@code arp -a <ip>}: Windows, Linux and macOS).
 *
 * The lookup never blocks the caller: it returns the cached address and, if
 * there is none, runs arp in a background thread (one per address, at most
 * once per {@link #RETRY_MS} after a failure). When the address is found the
 * caller's callback runs, e.g. to update the renderer card.
 */
public class MacAddresses {

	private static final Logger LOGGER = LoggerFactory.getLogger(MacAddresses.class);
	private static final Pattern MAC = Pattern.compile("(?i)\\b([0-9a-f]{1,2}[:-]){5}[0-9a-f]{1,2}\\b");
	private static final long RETRY_MS = 60 * 1000;
	private static final long ARP_TIMEOUT_S = 5;
	private static final Map<String, String> FOUND = new ConcurrentHashMap<>();
	private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
	private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();

	private MacAddresses() {
	}

	/**
	 * @param onFound runs (in the lookup thread) when the address was not
	 * cached and has just been found
	 * @return {@code "AA:BB:CC:DD:EE:FF"}, or "" if not known yet or unknown
	 * (this computer, not in the ARP table, other network).
	 */
	public static String getMacAddress(InetAddress address, Runnable onFound) {
		if (address == null || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
			return "";
		}
		String ip = address.getHostAddress();
		String mac = FOUND.get(ip);
		if (mac != null) {
			return mac;
		}
		Long failed = FAILED.get(ip);
		if ((failed == null || System.currentTimeMillis() - failed >= RETRY_MS) && RUNNING.add(ip)) {
			Thread thread = new Thread(() -> lookUp(ip, onFound), "MAC address lookup");
			thread.setDaemon(true);
			thread.start();
		}
		return "";
	}

	private static void lookUp(String ip, Runnable onFound) {
		try {
			String mac = readArpTable(ip);
			if (mac.isEmpty()) {
				FAILED.put(ip, System.currentTimeMillis());
				return;
			}
			FOUND.put(ip, mac);
			FAILED.remove(ip);
		} finally {
			RUNNING.remove(ip);
		}
		if (onFound != null) {
			onFound.run();
		}
	}

	private static String readArpTable(String ip) {
		List<String> command = new ArrayList<>(List.of("arp", "-a"));
		if (!Platform.isWindows()) {
			// No reverse DNS lookup: it can hang
			command.add("-n");
		}
		command.add(ip);
		Process process = null;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).start();
			if (!process.waitFor(ARP_TIMEOUT_S, TimeUnit.SECONDS)) {
				LOGGER.debug("MAC address of {}: arp timed out", ip);
				return "";
			}
			String output;
			try (InputStream in = process.getInputStream()) {
				output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			return findMac(output, ip);
		} catch (IOException e) {
			LOGGER.debug("MAC address of {}: arp failed: {}", ip, e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
		}
		return "";
	}

	/**
	 * @return the MAC address on the line of the ARP table output that holds
	 * the IP address, normalized; "" if there is none.
	 */
	static String findMac(String arpOutput, String ip) {
		for (String line : arpOutput.split("\\R")) {
			Matcher matcher = MAC.matcher(line);
			// Whole tokens: "192.168.1.5" must not match "192.168.1.50" (BSD arp lists the whole table)
			if (Arrays.asList(line.trim().split("[\\s()]+")).contains(ip) && matcher.find()) {
				return normalize(matcher.group());
			}
		}
		return "";
	}

	/**
	 * @return {@code "AA:BB:CC:DD:EE:FF"} from {@code aa-bb-cc-dd-ee-ff} or
	 * {@code a:b:c:d:e:f} (macOS drops leading zeros).
	 */
	static String normalize(String mac) {
		StringBuilder result = new StringBuilder();
		for (String part : mac.split("[:-]")) {
			if (!result.isEmpty()) {
				result.append(':');
			}
			result.append(part.length() == 1 ? "0" + part : part);
		}
		return result.toString().toUpperCase(Locale.ROOT);
	}

}
// </custom:common>
