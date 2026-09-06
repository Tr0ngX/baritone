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
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class BaritoneKeyBindings {

    public static final String CATEGORY = "category.baritone";

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

    private static boolean registered = false;

    /**
     * Đảm bảo category "category.baritone" được thêm vào CATEGORY_SORT_ORDER của KeyMapping
     * để tránh NullPointerException khi Minecraft so sánh danh mục trong Key Binds screen.
     */
    public static void registerCategory() {
        if (registered) return;
        try {
            // 1. Thử qua MixinKeyMapping
            try {
                Class<?> mixinClass = Class.forName("baritone.launch.mixins.MixinKeyMapping");
                Method method = mixinClass.getMethod("getCategorySortOrder");
                @SuppressWarnings("unchecked")
                Map<String, Integer> map = (Map<String, Integer>) method.invoke(null);
                if (map != null && !map.containsKey(CATEGORY)) {
                    int max = map.values().stream().max(Integer::compareTo).orElse(0);
                    map.put(CATEGORY, max + 1);
                    registered = true;
                    return;
                }
            } catch (Throwable ignored) {}

            // 2. Fallback qua reflection quét static Map trong KeyMapping
            for (Field f : KeyMapping.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType()) && Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> map = (Map<String, Integer>) f.get(null);
                    if (map != null) {
                        if (map.containsKey("key.categories.movement") || map.containsKey(KeyMapping.CATEGORY_MOVEMENT) || !map.isEmpty()) {
                            if (!map.containsKey(CATEGORY)) {
                                int max = map.values().stream().max(Integer::compareTo).orElse(0);
                                map.put(CATEGORY, max + 1);
                                registered = true;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Merge toàn bộ Baritone KeyMappings vào mảng keyMappings của Options.
     */
    public static KeyMapping[] process(KeyMapping[] existing) {
        registerCategory();
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
