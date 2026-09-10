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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

/**
 * Nút click dùng chung trong log chat ([Mở GUI], [Dừng]).
 * Click đi qua cùng action xử lý với GUI, không chứa tọa độ hay tên người chơi
 * nên an toàn với streamer mode.
 */
public final class ChatButtons {

    private ChatButtons() {}

    public static MutableComponent button(String label, String command, ChatFormatting color, String hoverText) {
        MutableComponent btn = Component.literal("[" + label + "]");
        btn.setStyle(btn.getStyle()
                .withColor(color)
                .withClickEvent(new ClickEvent.RunCommand(FORCE_COMMAND_PREFIX + command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
        return btn;
    }

    public static MutableComponent openGuiButton() {
        return button("Mở GUI", "gui", ChatFormatting.AQUA, "Mở bảng điều khiển Tr0ngX (phím F4)");
    }

    public static MutableComponent stopButton() {
        return button("Dừng", "cancel", ChatFormatting.RED, "Dừng mọi hoạt động của Baritone");
    }
}
