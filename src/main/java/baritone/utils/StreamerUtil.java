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

import baritone.Baritone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Tiện ích hỗ trợ chế độ Streamer / Privacy Protection:
 * - Ẩn bảng điểm bên phải (Scoreboard Sidebar)
 * - Ẩn / che tên người chơi trong chat, bảng Tab, nametag, v.v.
 */
public final class StreamerUtil {

    private StreamerUtil() {}

    public static String getLocalPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return "";
        }
        LocalPlayer player = mc.player;
        if (player != null && player.getGameProfile() != null) {
            String name = player.getGameProfile().getName();
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        if (mc.getUser() != null) {
            String name = mc.getUser().getName();
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        return "";
    }

    public static boolean isStreamerModeActive() {
        return Baritone.settings().streamerMode.value;
    }

    public static boolean isHideScoreboardActive() {
        return Baritone.settings().hideScoreboard.value || Baritone.settings().streamerMode.value;
    }

    public static boolean isHidePlayerNameActive() {
        return Baritone.settings().hidePlayerName.value || Baritone.settings().streamerMode.value;
    }

    public static String getCensoredName() {
        String name = Baritone.settings().censoredPlayerName.value;
        return (name != null && !name.trim().isEmpty()) ? name.trim() : "Protected";
    }

    public static String censorString(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (!isHidePlayerNameActive()) {
            return text;
        }
        String localName = getLocalPlayerName();
        if (localName.isEmpty()) {
            return text;
        }
        return replaceIgnoreCase(text, localName, getCensoredName());
    }

    public static Component censorComponent(Component component) {
        if (component == null) {
            return null;
        }
        if (!isHidePlayerNameActive()) {
            return component;
        }

        String localName = getLocalPlayerName();
        if (localName.isEmpty()) {
            return component;
        }

        String raw = component.getString();
        if (!containsIgnoreCase(raw, localName)) {
            return component;
        }

        String replacement = getCensoredName();
        return censorComponentInternal(component, localName, replacement);
    }

    private static MutableComponent censorComponentInternal(Component component, String target, String replacement) {
        MutableComponent copy;
        if (component.getContents() instanceof PlainTextContents plain) {
            String text = plain.text();
            String replaced = replaceIgnoreCase(text, target, replacement);
            copy = Component.literal(replaced).withStyle(component.getStyle());
        } else if (component.getContents() instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs();
            Object[] newArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component c) {
                    newArgs[i] = censorComponent(c);
                } else if (args[i] instanceof String s) {
                    newArgs[i] = replaceIgnoreCase(s, target, replacement);
                } else {
                    newArgs[i] = args[i];
                }
            }
            copy = Component.translatable(translatable.getKey(), newArgs).withStyle(component.getStyle());
        } else {
            String text = component.getString();
            String replaced = replaceIgnoreCase(text, target, replacement);
            copy = Component.literal(replaced).withStyle(component.getStyle());
            return copy;
        }

        for (Component sibling : component.getSiblings()) {
            copy.append(censorComponentInternal(sibling, target, replacement));
        }
        return copy;
    }

    public static boolean containsIgnoreCase(String source, String target) {
        if (source == null || target == null || target.isEmpty()) {
            return false;
        }
        return source.toLowerCase().contains(target.toLowerCase());
    }

    public static String replaceIgnoreCase(String source, String target, String replacement) {
        if (source == null || target == null || target.isEmpty() || replacement == null) {
            return source;
        }
        StringBuilder sb = new StringBuilder();
        int start = 0;
        String lowerSource = source.toLowerCase();
        String lowerTarget = target.toLowerCase();
        int idx;
        while ((idx = lowerSource.indexOf(lowerTarget, start)) != -1) {
            sb.append(source, start, idx);
            sb.append(replacement);
            start = idx + target.length();
        }
        sb.append(source.substring(start));
        return sb.toString();
    }
}
