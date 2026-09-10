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

/**
 * Mở bảng điều khiển Tr0ngX (cùng màn hình với phím F4).
 * Dùng cho nút [Mở GUI] trong log chat để click đi qua cùng action với GUI.
 */
public class GuiCommand extends Command {

    public GuiCommand(IBaritone baritone) {
        super(baritone, "gui", "autominegui");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        if (baritone instanceof Baritone primary) {
            try {
                primary.getPlayerContext().minecraft().setScreen(new AutoMineScreen(primary));
                return;
            } catch (Throwable ignored) {}
        }
        // Rơi về đây khi chưa vào world: báo thay vì mở màn hình lỗi.
        logDirect("§e[Tr0ngX] Hãy vào world rồi mới mở bảng điều khiển (hoặc bấm F4).");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Mở bảng điều khiển Tr0ngX (F4)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Mở bảng điều khiển Tr0ngX, cùng màn hình với phím F4.",
                "",
                "Usage:",
                "> gui"
        );
    }
}
