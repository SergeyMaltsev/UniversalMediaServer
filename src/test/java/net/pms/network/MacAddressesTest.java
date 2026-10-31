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
// <custom:common> Whole file: tests of the MAC address lookup in the ARP table output
package net.pms.network;

import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class MacAddressesTest {

	private static final String WINDOWS = """

		Interface: 192.168.1.10 --- 0x7
		  Internet Address      Physical Address      Type
		  192.168.1.5           aa-bb-cc-dd-ee-0f     dynamic
		  192.168.1.50          11-22-33-44-55-66     dynamic
		""";

	private static final String MACOS = """
		? (192.168.1.50) at 1:22:33:44:55:66 on en0 ifscope [ethernet]
		? (192.168.1.5) at a:bb:c:dd:ee:f on en0 ifscope [ethernet]
		""";

	private static final String LINUX = """
		Address                  HWtype  HWaddress           Flags Mask            Iface
		192.168.1.5              ether   aa:bb:cc:dd:ee:0f   C                     eth0
		""";

	@Test
	public void testWindows() {
		assertEquals("AA:BB:CC:DD:EE:0F", MacAddresses.findMac(WINDOWS, "192.168.1.5"));
		assertEquals("11:22:33:44:55:66", MacAddresses.findMac(WINDOWS, "192.168.1.50"));
	}

	@Test
	public void testMacOsShortOctets() {
		assertEquals("0A:BB:0C:DD:EE:0F", MacAddresses.findMac(MACOS, "192.168.1.5"));
		assertEquals("01:22:33:44:55:66", MacAddresses.findMac(MACOS, "192.168.1.50"));
	}

	@Test
	public void testLinux() {
		assertEquals("AA:BB:CC:DD:EE:0F", MacAddresses.findMac(LINUX, "192.168.1.5"));
	}

	@Test
	public void testIpIsMatchedAsAWholeToken() {
		assertEquals("", MacAddresses.findMac(LINUX, "192.168.1.50"), "192.168.1.5 must not match 192.168.1.50");
		assertEquals("", MacAddresses.findMac(WINDOWS, "192.168.1.1"), "a prefix of an address in the table");
		assertEquals("", MacAddresses.findMac(WINDOWS, "192.168.1.10"), "the interface line has no MAC");
		assertEquals("", MacAddresses.findMac("", "192.168.1.5"));
	}

	@Test
	public void testNormalize() {
		assertEquals("AA:BB:CC:DD:EE:FF", MacAddresses.normalize("aa-bb-cc-dd-ee-ff"));
		assertEquals("0A:0B:0C:0D:0E:0F", MacAddresses.normalize("a:b:c:d:e:f"));
	}

	@Test
	public void testLocalAddressesAreNotLookedUp() throws Exception {
		assertEquals("", MacAddresses.getMacAddress(null, null));
		assertEquals("", MacAddresses.getMacAddress(InetAddress.getLoopbackAddress(), null));
		assertEquals("", MacAddresses.getMacAddress(InetAddress.getByName("0.0.0.0"), null));
	}

}
// </custom:common>
