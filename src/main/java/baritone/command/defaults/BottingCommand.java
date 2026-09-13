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
import baritone.api.utils.Helper;
import baritone.utils.AutoMineConfig;
import baritone.utils.AutoMineScreen;
import baritone.utils.DiscordManager;
import baritone.utils.hud.BottingDashboardOverlay;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class BottingCommand extends Command {

    public BottingCommand(IBaritone baritone) {
        super(baritone, "farmnang", "botting", "heavyfarm", "blackout", "ecobot");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);

        if (!args.hasAny()) {
            boolean newState = !Baritone.settings().heavyFarmMode.value;
            setHeavyFarmMode(newState);
            return;
        }

        String firstArg = args.getString().toLowerCase();

        if (firstArg.equals("on") || firstArg.equals("true") || firstArg.equals("enable") || firstArg.equals("1")) {
            setHeavyFarmMode(true);
            return;
        }

        if (firstArg.equals("off") || firstArg.equals("false") || firstArg.equals("disable") || firstArg.equals("0")) {
            setHeavyFarmMode(false);
            return;
        }

        if (firstArg.equals("name") || firstArg.equals("reveal") || firstArg.equals("spoiler")) {
            BottingDashboardOverlay.toggleNameSpoiler();
            boolean rev = BottingDashboardOverlay.isNameSpoilerRevealed();
            logDirect(rev
                    ? "§a[Farm Nặng] Đã MỞ ô spoiler hiển thị tên tài khoản trên màn hình!"
                    : "§e[Farm Nặng] Đã ĐÓNG ô spoiler che tên tài khoản trên màn hình!");
            return;
        }

        if (firstArg.equals("balance") || firstArg.equals("money") || firstArg.equals("bal") || firstArg.equals("tien")) {
            String bal = DiscordManager.getPlayerBalance();
            logDirect("§a[Số Dư Ví] §f" + bal);
            return;
        }

        if (firstArg.equals("fps")) {
            if (!args.hasAny()) {
                logDirect("§e[Farm Nặng] Giới hạn FPS botting/farm nặng hiện tại: " + Baritone.settings().bottingFps.value + " FPS");
                return;
            }
            try {
                int fps = Integer.parseInt(args.getString());
                if (fps <= 0) fps = 10;
                Baritone.settings().bottingFps.value = fps;
                AutoMineScreen.optBottingFps = fps;
                AutoMineConfig.save();
                logDirect("§a[Farm Nặng] Đã chỉnh giới hạn FPS Farm Nặng thành: §f" + fps + " FPS");
            } catch (NumberFormatException e) {
                logDirect("§c[Farm Nặng] Giá trị FPS không hợp lệ! Vui lòng nhập số (ví dụ: #farmnang fps 10)");
            }
            return;
        }

        // Kiem tra neu nguoi dung go thang so FPS: #farmnang 10 hoac #botting 10
        try {
            int fps = Integer.parseInt(firstArg);
            if (fps <= 0) fps = 10;
            Baritone.settings().bottingFps.value = fps;
            AutoMineScreen.optBottingFps = fps;
            setHeavyFarmMode(true);
            logDirect("§a[Farm Nặng] Đã chỉnh FPS thành " + fps + " và kích hoạt Chế độ Farm Nặng!");
        } catch (NumberFormatException e) {
            logDirect("§eCú pháp: §f#" + label + " [on/off] §7hoặc §f#" + label + " fps <số> §7(ví dụ: #" + label + " fps 10)");
        }
    }

    public static void setHeavyFarmMode(boolean enabled) {
        Baritone.settings().heavyFarmMode.value = enabled;
        Baritone.settings().bottingMode.value = enabled;
        AutoMineScreen.optBottingMode = enabled;
        try {
            AutoMineConfig.save();
        } catch (Throwable ignored) {}
        if (enabled) {
            Helper.HELPER.logDirect("§a[Farm Nặng] ĐÃ BẬT CHẾ ĐỘ FARM NẶNG (Màn hình đen, ngắt render 3D, 10 FPS, cực tối ưu GPU, ép tắt ESP)!");
            Helper.HELPER.logDirect("§7(Ảnh chụp Discord Webhook vẫn tự động chụp hình ảnh 3D thực tế; Tự động tắt khi thoát map)");
        } else {
            Helper.HELPER.logDirect("§c[Farm Nặng] ĐÃ TẮT CHẾ ĐỘ FARM NẶNG (Khôi phục hiển thị thế giới 3D bình thường)!");
        }
    }

    public static void setBottingMode(boolean enabled) {
        setHeavyFarmMode(enabled);
    }

    public static void autoDisableOnLeave() {
        if (Baritone.settings().heavyFarmMode.value || Baritone.settings().bottingMode.value || AutoMineScreen.optBottingMode) {
            Baritone.settings().heavyFarmMode.value = false;
            Baritone.settings().bottingMode.value = false;
            AutoMineScreen.optBottingMode = false;
            try {
                AutoMineConfig.save();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        try {
            if (args.hasExactlyOne()) {
                String prefix = args.getString().toLowerCase();
                return Stream.of("on", "off", "name", "fps", "10", "20")
                        .filter(s -> s.startsWith(prefix));
            }
            if (args.has(2)) {
                String first = args.getString();
                if (first.equalsIgnoreCase("fps")) {
                    String prefix = args.getString().toLowerCase();
                    return Stream.of("5", "10", "15", "20", "30")
                            .filter(s -> s.startsWith(prefix));
                }
            }
        } catch (Exception ignored) {}
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Bật/tắt chế độ farm nặng cực tối ưu (đen màn hình, ngắt 3D, 10 FPS, ép tắt ESP)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Chế độ Farm Nặng (gộp Botting) màn hình đen cực tối ưu khi treo farm nhiều tài khoản:",
                "  - Ngắt 100% render thế giới 3D (GPU ~0%).",
                "  - Khóa framerate về 10 FPS.",
                "  - Tự động tắt khi thoát khỏi map hoặc ngắt kết nối máy chủ.",
                "  - Ép tắt ESP quặng và animation trang trí.",
                "  - Vẫn chụp ảnh màn hình 3D thực tế khi gửi báo cáo Webhook Discord.",
                "",
                "Sử dụng:",
                "  #farmnang - Bật/tắt nhanh chế độ",
                "  #farmnang on / off - Bật hoặc tắt",
                "  #farmnang fps <số> - Đổi mức FPS limit (mặc định: 10)",
                "  #farmnang 10 - Đặt 10 FPS và bật chế độ"
        );
    }
}
