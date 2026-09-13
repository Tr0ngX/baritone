/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BalanceParserTest {

    @Test
    public void testThousandSeparators() {
        assertEquals("10 000 $", DiscordManager.parseBalanceFromText("§6Ví: §a10 000 $"));
        assertEquals("10 000 $", DiscordManager.parseBalanceFromText("§6Số dư ví: §a10 000 $"));
        assertEquals("10 000 000 $", DiscordManager.parseBalanceFromText("§eSố dư: §a10 000 000 $"));
        assertEquals("10 000 Xu", DiscordManager.parseBalanceFromText("§6Ví: §a10 000 Xu"));
        assertEquals("10 000 $", DiscordManager.parseBalanceFromText("§6Ví: §a10 000"));
        assertEquals("10,000,000 $", DiscordManager.parseBalanceFromText("§6Số dư ví: 10,000,000 $"));
        assertEquals("10.000.000 VNĐ", DiscordManager.parseBalanceFromText("§6Ví: §a10.000.000 VNĐ"));
        assertEquals("$1,234.56", DiscordManager.parseBalanceFromText("§aBalance: §f$1,234.56"));
        assertEquals("12.5k$", DiscordManager.parseBalanceFromText("§aMoney: §f12.5k$"));
        assertEquals("10M $", DiscordManager.parseBalanceFromText("§6Ví: §a10M"));
        assertEquals("0$", DiscordManager.parseBalanceFromText("§6Số dư: §a0$"));
        assertEquals("500,000₫", DiscordManager.parseBalanceFromText("§6Ví tiền: §a500,000₫"));
    }

    @Test
    public void testRejectUnrelatedLines() {
        assertNull(DiscordManager.parseBalanceFromText("§eTài khoản: §fPlayer10"));
        assertNull(DiscordManager.parseBalanceFromText("§fLevel: 10"));
        assertNull(DiscordManager.parseBalanceFromText("§fPing: 10ms"));
        assertNull(DiscordManager.parseBalanceFromText("§fOnline: 10/100"));
        assertNull(DiscordManager.parseBalanceFromText("§fTop 10 Thợ Mỏ"));
        assertNull(DiscordManager.parseBalanceFromText("10:30:15"));
        assertNull(DiscordManager.parseBalanceFromText("§fBalo: 10/36 ô"));
    }

    @Test
    public void testDeltaFiltering() {
        // Delta / AutoSell messages should not override wallet balance
        String current = DiscordManager.getPlayerBalance();
        DiscordManager.updateBalanceIfDetected("+10 $ (Bán 1 đá cuội)");
        DiscordManager.updateBalanceIfDetected("Đã cộng 10 $ vào tài khoản");
        DiscordManager.updateBalanceIfDetected("Bán tự động: 10 $");
        DiscordManager.updateBalanceIfDetected("Bạn nhận được 10 $ từ việc đào than");
        // Balance should remain unchanged (not overwritten with 10 $)
        assertEquals(current, DiscordManager.getPlayerBalance());
    }

    @Test
    public void testKingSMPTabHeader() {
        assertEquals("136.69M $", DiscordManager.parseBalanceFromText("§a$ §fMONEY §a136.69M"));
        assertEquals("136.69M $", DiscordManager.parseBalanceFromText("$ MONEY 136.69M"));
        assertNull(DiscordManager.parseBalanceFromText("§d★ §fSHARD §d10"));
        assertNull(DiscordManager.parseBalanceFromText("★ SHARD 10"));
        assertNull(DiscordManager.parseBalanceFromText("🗡 KILLS 0"));
        assertNull(DiscordManager.parseBalanceFromText("💀 DEATHS 9"));
        assertNull(DiscordManager.parseBalanceFromText("🗝 KEY 20m 37s"));
        assertNull(DiscordManager.parseBalanceFromText("🕒 PLAYED 3d 0h"));
        assertNull(DiscordManager.parseBalanceFromText("⚡ TEAM xrayteam"));
        assertNull(DiscordManager.parseBalanceFromText("KINGMC.VN (32ms)"));

        String tabHeader = "KingSMP\n\n$ MONEY 136.69M\n★ SHARD 10\n🗡 KILLS 0\n💀 DEATHS 9\n🗝 KEY 20m 37s\n🕒 PLAYED 3d 0h\n⚡ TEAM xrayteam";
        DiscordManager.scanTabListText(tabHeader);
        assertEquals("136.69M $", DiscordManager.getPlayerBalance());
    }

    @Test
    public void testKingSMPScoreboardSidebar() {
        assertEquals("137.92M $", DiscordManager.parseBalanceFromText("§a$ §fMONEY §a137.92M"));
        assertEquals("137.92M $", DiscordManager.parseBalanceFromText("$ MONEY 137.92M"));
        assertEquals("137.92M $", DiscordManager.parseBalanceFromText("$ MONEY   137.92M"));
        assertEquals("137.92M $", DiscordManager.parseBalanceFromText("$ MONEY: 137.92M"));
        assertNull(DiscordManager.parseBalanceFromText("★ SHARD 20"));
        assertNull(DiscordManager.parseBalanceFromText("§5★ §fSHARD §520"));
        assertNull(DiscordManager.parseBalanceFromText("🗡 KILLS 1"));
        assertNull(DiscordManager.parseBalanceFromText("💀 DEATHS 11"));
        assertNull(DiscordManager.parseBalanceFromText("🗝 KEY 9m 52s"));
        assertNull(DiscordManager.parseBalanceFromText("🕒 PLAYED 3d 1h"));
        assertNull(DiscordManager.parseBalanceFromText("⚡ TEAM xrayteam"));
        assertNull(DiscordManager.parseBalanceFromText("KINGMC.VN (29ms)"));
    }
}
