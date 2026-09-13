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

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.utils.AutoRejoinConfig;
import baritone.utils.AutoRejoinManager;
import baritone.utils.gui.AutoRejoinScreen;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class AutoRejoinCommand extends Command {

    public AutoRejoinCommand(IBaritone baritone) {
        super(baritone, "rejoin", "autorejoin", "reconnect", "dn");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        AutoRejoinConfig.ensureLoaded();

        if (!args.hasAny()) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new AutoRejoinScreen(mc.screen)));
            logDirect("§a[Auto Rejoin] Đã mở màn hình cài đặt Tự động Rejoin & Đăng nhập!");
            return;
        }

        String firstArg = args.getString().toLowerCase();

        if (firstArg.equals("gui") || firstArg.equals("menu") || firstArg.equals("screen") || firstArg.equals("ui")) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new AutoRejoinScreen(mc.screen)));
            logDirect("§a[Auto Rejoin] Đã mở màn hình cài đặt Tự động Rejoin & Đăng nhập!");
            return;
        }

        if (firstArg.equals("on") || firstArg.equals("true") || firstArg.equals("enable") || firstArg.equals("1")) {
            if (!AutoRejoinConfig.hasPassword()) {
                logDirect("§c[Auto Rejoin] Bạn CHƯA CÓ MẬT KHẨU /dn! Đang mở giao diện để bạn nhập mật khẩu trước...");
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.setScreen(new AutoRejoinScreen(mc.screen)));
                return;
            }
            AutoRejoinConfig.enabled = true;
            AutoRejoinConfig.save();
            logDirect("§a[Auto Rejoin] ĐÃ BẬT Tự động Rejoin (Trừ khi bị kick bởi Lava hoặc Player)!");
            return;
        }

        if (firstArg.equals("off") || firstArg.equals("false") || firstArg.equals("disable") || firstArg.equals("0")) {
            AutoRejoinConfig.enabled = false;
            AutoRejoinConfig.save();
            logDirect("§c[Auto Rejoin] ĐÃ TẮT Tự động Rejoin!");
            return;
        }

        if (firstArg.equals("test")) {
            AutoRejoinManager.triggerTestProcess();
            return;
        }

        if (firstArg.equals("pass") || firstArg.equals("password") || firstArg.equals("mk")) {
            if (!args.hasAny()) {
                logDirect("§eCú pháp: §f#" + label + " pass <mật_khẩu>");
                return;
            }
            String newPass = args.getString();
            AutoRejoinConfig.password = newPass;
            AutoRejoinConfig.enabled = true;
            AutoRejoinConfig.save();
            AutoRejoinManager.onPasswordEntered(newPass);
            logDirect("§a§l[Auto Rejoin] Đã lưu mật khẩu /dn và TỰ ĐỘNG BẬT Rejoin thành công!");
            return;
        }

        // Hỗ trợ gõ tắt: #dn <mật_khẩu>
        if (label.equalsIgnoreCase("dn") && !firstArg.equals("status") && !firstArg.equals("server") && !firstArg.equals("target") && !firstArg.equals("delay")) {
            AutoRejoinConfig.password = firstArg;
            AutoRejoinConfig.enabled = true;
            AutoRejoinConfig.save();
            AutoRejoinManager.onPasswordEntered(firstArg);
            logDirect("§a§l[Auto Rejoin] Đã nhận mật khẩu /dn: " + firstArg.replaceAll(".", "*") + " và TỰ ĐỘNG BẬT Rejoin thành công!");
            return;
        }

        if (firstArg.equals("server") || firstArg.equals("ip")) {
            if (!args.hasAny()) {
                logDirect("§eServer IP hiện tại: §f" + AutoRejoinConfig.serverIp);
                return;
            }
            String ip = args.getString();
            AutoRejoinConfig.serverIp = ip;
            AutoRejoinConfig.save();
            logDirect("§a[Auto Rejoin] Đã đổi Server IP thành: §f" + ip);
            return;
        }

        if (firstArg.equals("target") || firstArg.equals("smp") || firstArg.equals("sub")) {
            if (!args.hasAny()) {
                logDirect("§eCụm máy chủ mục tiêu hiện tại: §f" + AutoRejoinConfig.targetSubServer);
                return;
            }
            String sub = args.getString();
            AutoRejoinConfig.targetSubServer = sub;
            AutoRejoinConfig.save();
            logDirect("§a[Auto Rejoin] Đã đổi cụm máy chủ thành: §f" + sub);
            return;
        }

        if (firstArg.equals("delay")) {
            if (!args.hasAny()) {
                logDirect("§eThời gian chờ Rejoin hiện tại: §f" + AutoRejoinConfig.reconnectDelaySeconds + "s");
                return;
            }
            try {
                int sec = Integer.parseInt(args.getString());
                if (sec < 1) sec = 1;
                AutoRejoinConfig.reconnectDelaySeconds = sec;
                AutoRejoinConfig.save();
                logDirect("§a[Auto Rejoin] Đã chỉnh thời gian đếm ngược Reconnect thành: §f" + sec + " giây");
            } catch (NumberFormatException e) {
                logDirect("§cSố giây không hợp lệ!");
            }
            return;
        }

        if (firstArg.equals("status")) {
            logDirect("§6=== TRẠNG THÁI TỰ ĐỘNG REJOIN ===");
            logDirect("§7Trạng thái: " + (AutoRejoinConfig.enabled ? "§aĐANG BẬT" : "§cĐÃ TẮT"));
            logDirect("§7Server IP: §f" + AutoRejoinConfig.serverIp);
            logDirect("§7Cụm mục tiêu: §f" + AutoRejoinConfig.targetSubServer);
            logDirect("§7Mật khẩu /dn: " + (AutoRejoinConfig.password.isEmpty() ? "§cChưa cài đặt" : "§aĐã cài đặt (••••••)"));
            logDirect("§7Thời gian chờ: §f" + AutoRejoinConfig.reconnectDelaySeconds + "s");
            logDirect("§7Tự động chuột phải Đồng hồ: " + (AutoRejoinConfig.autoOpenMenu ? "§aBẬT" : "§cTẮT"));
            logDirect("§7Tự động click KingSMP: " + (AutoRejoinConfig.autoClickSubServer ? "§aBẬT" : "§cTẮT"));
            logDirect("§7Tự động tiếp tục Farm cũ: " + (AutoRejoinConfig.autoResumeTask ? "§aBẬT" : "§cTẮT"));
            return;
        }

        logDirect("§eCú pháp: §f#" + label + " [gui | on | off | test | pass <mk> | server <ip> | target <cụm> | status]");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        try {
            if (args.hasExactlyOne()) {
                String prefix = args.getString().toLowerCase();
                return Stream.of("gui", "on", "off", "test", "pass", "server", "target", "delay", "status")
                        .filter(s -> s.startsWith(prefix));
            }
        } catch (Exception ignored) {}
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Cấu hình tự động kết nối lại, đăng nhập /dn và chọn cụm KingSMP";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Tính năng Tự Động Rejoin an toàn (Auto Reconnect & Lobby Login):",
                "  - Tự động kết nối lại server khi bị ngắt kết nối (mất mạng, server reboot, kick timeout...).",
                "  - BẢO VỆ TUYỆT ĐỐI: KHÔNG BAO GIỜ rejoin nếu bị kick khẩn cấp bởi LAVA hoặc phát hiện PLAYER gần!",
                "  - Tự động gửi lệnh đăng nhập /dn <mật_khẩu>.",
                "  - Cầm chiếc Đồng Hồ (slot 4) và chuột phải mở Menu Máy Chủ Lobby.",
                "  - Tìm kiếm và click vào ô KingSMP.",
                "  - Tự động khôi phục cấu hình / chế độ Farm Nặng / AutoMine khi vào map!",
                "",
                "Sử dụng:",
                "  #rejoin - Mở giao diện cài đặt GUI",
                "  #rejoin on / off - Bật hoặc tắt",
                "  #rejoin pass <mật_khẩu> - Đặt mật khẩu đăng nhập /dn",
                "  #rejoin test - Thử nghiệm quy trình chọn máy chủ ngay tại Lobby",
                "  #rejoin status - Xem toàn bộ trạng thái"
        );
    }
}
