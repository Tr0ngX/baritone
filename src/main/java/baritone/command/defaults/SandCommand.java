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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class SandCommand extends Command {

    public SandCommand(IBaritone baritone) {
        super(baritone, "sand", "autosand", "minesand", "daosand", "daocat", "sandmine");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            String first = args.peek().getValue().toLowerCase();

            // 1. Dừng đào cát: #sand stop / off / tat / dung / 0
            if (first.equals("stop") || first.equals("off") || first.equals("tat") || first.equals("dung") || first.equals("0")) {
                baritone.getSandProcess().cancel();
                return;
            }

            // 2. Giới hạn bán kính: #sand range <bán kính>
            if (first.equals("range") || first.equals("r")) {
                args.getString(); // consume "range"
                int range = args.getAs(Integer.class);
                baritone.getSandProcess().sand(0, range, null);
                return;
            }

            // 3. Đào theo số lượng hoặc bán kính: #sand <số lượng> [bán kính]
            int targetCount = 0;
            boolean isNumber = false;
            try {
                targetCount = Integer.parseInt(first);
                args.getString(); // consume first arg
                isNumber = true;
            } catch (NumberFormatException ignored) {
            }

            if (isNumber) {
                int range = 0;
                if (args.hasAny()) {
                    range = args.getAsOrDefault(Integer.class, 0);
                }
                baritone.getSandProcess().sand(targetCount, range, null);
                return;
            }
        }

        // 4. Mặc định: #sand (đào toàn bộ cát xung quanh không giới hạn)
        baritone.getSandProcess().sand();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String prefix = args.getString().toLowerCase();
            return Stream.of("stop", "range", "64", "128", "256", "512", "1000")
                    .filter(s -> s.startsWith(prefix));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Tự động đào cát siêu tốc không delay quét (Auto Sand Miner)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Lệnh #sand kích hoạt chế độ tự động đào cát chuyên biệt (Auto Sand Miner).",
                "Thuật toán phản xạ trực tiếp cực nhẹ, đào liên tục không đơ/lag, không delay quét lại chunk.",
                "Tự động xả sập các cột cát cao rơi trực tiếp vào tay người chơi.",
                "",
                "Cách dùng:",
                "> sand - Đào toàn bộ cát xung quanh không giới hạn",
                "> sand <số lượng> - Đào đủ số lượng block cát chỉ định rồi dừng (ví dụ: #sand 64)",
                "> sand range <bán kính> - Chỉ đào cát trong bán kính chỉ định (ví dụ: #sand range 30)",
                "> sand stop - Dừng đào cát",
                "",
                "Lệnh tắt (Aliases): #sand, #autosand, #minesand, #daosand, #daocat"
        );
    }
}
