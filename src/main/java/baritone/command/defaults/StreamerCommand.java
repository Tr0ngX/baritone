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

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.utils.AutoMineScreen;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class StreamerCommand extends Command {

    public StreamerCommand(IBaritone baritone) {
        super(baritone, "streamer", "streamermode", "hideboard", "hidescoreboard", "hidename");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);

        String sub = args.hasAny() ? args.getString().toLowerCase() : "";

        if (label.equalsIgnoreCase("hideboard") || label.equalsIgnoreCase("hidescoreboard")) {
            boolean newState;
            if (sub.equals("on") || sub.equals("true") || sub.equals("1")) {
                newState = true;
            } else if (sub.equals("off") || sub.equals("false") || sub.equals("0")) {
                newState = false;
            } else {
                newState = !Baritone.settings().hideScoreboard.value;
            }
            Baritone.settings().hideScoreboard.value = newState;
            AutoMineScreen.optHideScoreboard = newState;
            if (!newState && Baritone.settings().streamerMode.value) {
                Baritone.settings().streamerMode.value = false;
                AutoMineScreen.optStreamerMode = false;
            }
            logDirect(newState
                    ? "§a[Streamer] Đã ẨN BẢNG ĐIỂM (Scoreboard Sidebar HUD)!"
                    : "§c[Streamer] Đã HIỆN BẢNG ĐIỂM (Scoreboard Sidebar HUD)!");
            return;
        }

        if (label.equalsIgnoreCase("hidename")) {
            boolean newState;
            if (sub.equals("on") || sub.equals("true") || sub.equals("1")) {
                newState = true;
            } else if (sub.equals("off") || sub.equals("false") || sub.equals("0")) {
                newState = false;
            } else {
                newState = !Baritone.settings().hidePlayerName.value;
            }
            Baritone.settings().hidePlayerName.value = newState;
            AutoMineScreen.optHidePlayerName = newState;
            if (!newState && Baritone.settings().streamerMode.value) {
                Baritone.settings().streamerMode.value = false;
                AutoMineScreen.optStreamerMode = false;
            }
            logDirect(newState
                    ? "§a[Streamer] Đã KÍCH HOẠT ẩn/che tên người chơi (Name Protect)!"
                    : "§c[Streamer] Đã TẮT ẩn/che tên người chơi!");
            return;
        }

        // #streamer or #streamermode
        if (sub.equals("set")) {
            if (!args.hasAny()) {
                logDirect("§c[Streamer] Cách dùng: #" + label + " set <tên_thay_thế>");
                return;
            }
            String newName = args.getString();
            Baritone.settings().censoredPlayerName.value = newName;
            logDirect("§a[Streamer] Đã đổi tên thay thế thành: §f" + newName);
            return;
        }

        boolean newState;
        if (sub.equals("on") || sub.equals("true") || sub.equals("1")) {
            newState = true;
        } else if (sub.equals("off") || sub.equals("false") || sub.equals("0")) {
            newState = false;
        } else {
            newState = !Baritone.settings().streamerMode.value;
        }

        Baritone.settings().streamerMode.value = newState;
        Baritone.settings().hideScoreboard.value = newState;
        Baritone.settings().hidePlayerName.value = newState;
        AutoMineScreen.optStreamerMode = newState;
        AutoMineScreen.optHideScoreboard = newState;
        AutoMineScreen.optHidePlayerName = newState;

        if (newState) {
            logDirect("§a[Streamer] CHẾ ĐỘ STREAMER: BẬT!");
            logDirect("§a  ✔ Ẩn toàn bộ Bảng Điểm bên phải (Scoreboard Sidebar)");
            logDirect("§a  ✔ Che giấu tên nhân vật trong Chat, Tab List, Nametag và Window Title");
        } else {
            logDirect("§c[Streamer] CHẾ ĐỘ STREAMER: TẮT!");
            logDirect("§c  ✔ Đã khôi phục hiển thị Bảng Điểm và Tên nhân vật");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            try {
                String prefix = args.getString().toLowerCase();
                return Stream.of("on", "off", "set").filter(s -> s.startsWith(prefix));
            } catch (Exception ignored) {}
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Bật/tắt chế độ Streamer (ẩn bảng điểm và che tên người chơi)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Lệnh điều khiển chế độ Streamer / Bảo vệ quyền riêng tư:",
                "",
                "Cách dùng:",
                "  #streamer - Bật/tắt toàn bộ chế độ Streamer (Ẩn bảng điểm + che tên)",
                "  #streamer on|off - Bật hoặc tắt chế độ Streamer",
                "  #streamer set <tên> - Đổi tên hiển thị thay thế (mặc định: Protected)",
                "  #hideboard [on|off] - Chỉ bật/tắt ẩn bảng điểm bên phải màn hình",
                "  #hidename [on|off] - Chỉ bật/tắt ẩn/che tên người chơi"
        );
    }
}
