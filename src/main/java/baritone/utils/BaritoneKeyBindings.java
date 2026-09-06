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

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BaritoneKeyBindings {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("baritone", "main"));

    public static final KeyMapping KEY_AUTOMINE_GUI = new KeyMapping(
            "key.baritone.automine_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F4,
            CATEGORY
    );

    public static final KeyMapping KEY_CANCEL = new KeyMapping(
            "key.baritone.cancel",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    public static final KeyMapping KEY_PAUSE = new KeyMapping(
            "key.baritone.pause",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    public static final KeyMapping[] ALL_KEYS = new KeyMapping[]{
            KEY_AUTOMINE_GUI,
            KEY_CANCEL,
            KEY_PAUSE
    };

    /**
     * Trong 1.21.11, KeyMapping.Category.register đã tự động đăng ký vào SORT_ORDER.
     */
    public static void registerCategory() {
    }

    /**
     * Merge toàn bộ Baritone KeyMappings vào mảng keyMappings của Options.
     */
    public static KeyMapping[] process(KeyMapping[] existing) {
        if (existing == null) {
            return ALL_KEYS.clone();
        }
        List<KeyMapping> list = new ArrayList<>(Arrays.asList(existing));
        for (KeyMapping km : ALL_KEYS) {
            if (!list.contains(km)) {
                list.add(km);
            }
        }
        return list.toArray(new KeyMapping[0]);
    }
}
