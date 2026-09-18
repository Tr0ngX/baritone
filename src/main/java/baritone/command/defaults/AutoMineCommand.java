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
import baritone.api.command.datatypes.ForBlockOptionalMeta;
import baritone.api.command.exception.CommandException;
import baritone.api.utils.BlockOptionalMeta;
import baritone.utils.AutoMineConfig;
import baritone.utils.AutoMineScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class AutoMineCommand extends Command {

    public AutoMineCommand(IBaritone baritone) {
        super(baritone, "automine", "am", "diamondmine", "orefarm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            String firstArg = args.peek().getValue().toLowerCase();

            // 1. Ép Tắt: #automine off / stop / 0 / tat / dung
            if (firstArg.equals("off") || firstArg.equals("stop") || firstArg.equals("0") || firstArg.equals("tat") || firstArg.equals("dung")) {
                AutoMineScreen.stopAutoMine(baritone);
                return;
            }

            // 2. Ép Bật: #automine on / start / 1 / bat / dao
            if (firstArg.equals("on") || firstArg.equals("start") || firstArg.equals("1") || firstArg.equals("bat") || firstArg.equals("dao")) {
                args.getString();
                AutoMineConfig.load();
                AutoMineScreen.startAutoMine(baritone);
                return;
            }

            // 3. Tùy biến đào khối chỉ định: #automine <blocks...>
            AutoMineConfig.load();
            List<BlockOptionalMeta> boms = new ArrayList<>();
            while (args.hasAny()) {
                boms.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
            }
            AutoMineScreen.startAutoMineWithBlocks(baritone, boms);
            return;
        }

        // === 1 LỆNH DUY NHẤT TỰ ĐỘNG BẬT TẮT (TOGGLE) ===
        // Y nguyên 100% logic của H:\baritone:
        // - Nếu đang đào -> Tự động dừng an toàn
        // - Nếu chưa đào -> Nạp config automine.json, tự động chọn đào cái gì và tắt cái gì, bắt đầu đào!
        if (baritone.getMineProcess().isActive()) {
            AutoMineScreen.stopAutoMine(baritone);
        } else {
            AutoMineConfig.load();
            AutoMineScreen.startAutoMine(baritone);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String prefix = args.getString().toLowerCase();
            return Stream.of("on", "off", "stop", "start")
                    .filter(s -> s.startsWith(prefix));
        }
        while (args.has(2)) {
            args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
        }
        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "1 lệnh tự động bật/tắt AutoMine theo đúng cấu hình quặng và tối ưu server";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Lệnh điều khiển AutoMine 1 chạm (Toggle):",
                "  - Nếu đang đào -> Tự động dừng an toàn.",
                "  - Nếu chưa đào -> Tự động nạp cấu hình quặng từ automine.json, kích hoạt toàn bộ cơ chế bảo vệ, tối ưu di chuyển và bắt đầu đào.",
                "",
                "Sử dụng:",
                "> #automine (hoặc #am) - Tự động Bật / Tắt theo trạng thái hiện tại",
                "> #automine on / off - Ép Bật hoặc Tắt",
                "> #automine <blocks...> - Đào theo danh sách block tùy chọn"
        );
    }
}
