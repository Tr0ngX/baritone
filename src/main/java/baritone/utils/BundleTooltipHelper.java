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
import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ công cụ phân tích và hiển thị nội dung chi tiết bên trong Túi đồ (Bundle).
 * Hỗ trợ hiển thị tên, số lượng, toàn bộ Enchantment, hiệu ứng thuốc, độ bền, đồ ăn và hộp lồng nhau.
 */
public final class BundleTooltipHelper {

    private BundleTooltipHelper() {}

    public static boolean isEnabled() {
        try {
            return Baritone.settings().bundleInspector.value;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean isShiftDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null) {
                return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Nối thêm thông tin chi tiết các vật phẩm bên trong Túi đồ vào tooltip của Bundle.
     */
    public static void appendBundleTooltip(
            ItemStack bundleStack,
            BundleContents contents,
            List<Component> tooltipList,
            Item.TooltipContext context,
            TooltipFlag tooltipFlag
    ) {
        if (!isEnabled()) return;

        if (contents == null || contents.isEmpty()) {
            tooltipList.add(Component.literal("§8▪ (Túi đồ đang trống - Kéo thả vật phẩm vào)"));
            return;
        }

        int totalItemCount = 0;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : contents.items()) {
            if (!it.isEmpty()) {
                items.add(it);
                totalItemCount += it.getCount();
            }
        }

        if (items.isEmpty()) {
            tooltipList.add(Component.literal("§8▪ (Túi đồ đang trống)"));
            return;
        }

        float fullness = BundleItem.getFullnessDisplay(bundleStack);
        int percent = Math.round(fullness * 100.0f);
        String percentColor = percent >= 100 ? "§c" : percent >= 75 ? "§6" : "§b";

        tooltipList.add(Component.literal("§6📦 Bên trong Túi đồ: §e" + totalItemCount + " §7vật phẩm (" + percentColor + percent + "% §7đầy)"));

        boolean hasShift = isShiftDown();
        int maxDisplay = hasShift ? items.size() : Math.min(items.size(), 10);

        for (int i = 0; i < maxDisplay; i++) {
            ItemStack stack = items.get(i);
            appendItemSummary(stack, tooltipList, hasShift);
        }

        if (!hasShift && items.size() > 10) {
            int remaining = items.size() - 10;
            tooltipList.add(Component.literal("§8... và còn §e" + remaining + " §8loại vật phẩm khác §7(Giữ §fSHIFT §7để xem hết)"));
        }
    }

    /**
     * Hiển thị tóm tắt từng item stack bên trong Bundle kèm theo enchants, thuốc, độ bền.
     */
    private static void appendItemSummary(ItemStack stack, List<Component> tooltipList, boolean expanded) {
        int count = stack.getCount();
        Component name = stack.getHoverName();

        MutableComponent line = Component.literal("§f▫ ");
        if (count > 1) {
            line.append(Component.literal("§e" + count + "x "));
        }
        line.append(name);
        tooltipList.add(line);

        // 1. Kiểm tra Enchantments (Cả trang bị thường lẫn Sách Phù Phép Stored Enchantments):
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        ItemEnchantments storedEnchants = stack.get(DataComponents.STORED_ENCHANTMENTS);

        List<Component> enchantList = new ArrayList<>();
        if (enchants != null && !enchants.isEmpty()) {
            extractEnchantments(enchants, enchantList);
        }
        if (storedEnchants != null && !storedEnchants.isEmpty()) {
            extractEnchantments(storedEnchants, enchantList);
        }

        if (!enchantList.isEmpty()) {
            if (expanded || enchantList.size() <= 4) {
                MutableComponent enchantsLine = Component.literal("§7   ✧ ");
                for (int i = 0; i < enchantList.size(); i++) {
                    if (i > 0) enchantsLine.append(Component.literal("§7, "));
                    enchantsLine.append(enchantList.get(i));
                }
                tooltipList.add(enchantsLine);
            } else {
                MutableComponent enchantsLine = Component.literal("§7   ✧ ");
                for (int i = 0; i < 3; i++) {
                    if (i > 0) enchantsLine.append(Component.literal("§7, "));
                    enchantsLine.append(enchantList.get(i));
                }
                enchantsLine.append(Component.literal("§7, và +" + (enchantList.size() - 3) + "..."));
                tooltipList.add(enchantsLine);
            }
        }

        // 2. Hiệu ứng Thuốc (Potion Contents):
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            List<String> effectStrs = new ArrayList<>();
            for (MobEffectInstance effect : potion.getAllEffects()) {
                String effectName = effect.getEffect().value().getDisplayName().getString();
                String amp = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
                int durTicks = effect.getDuration();
                String timeStr = durTicks > 0 ? String.format(" (%d:%02d)", durTicks / 1200, (durTicks % 1200) / 20) : "";
                effectStrs.add("§a" + effectName + amp + "§7" + timeStr);
            }
            if (!effectStrs.isEmpty()) {
                tooltipList.add(Component.literal("§7   ✦ " + String.join("§7, ", effectStrs)));
            }
        }

        // 3. Độ bền (Durability):
        if (stack.isDamageableItem()) {
            int maxDmg = stack.getMaxDamage();
            int curDmg = stack.getDamageValue();
            int curDur = maxDmg - curDmg;
            int durPercent = Math.round((float) curDur / (float) maxDmg * 100.0f);
            String durColor = durPercent > 50 ? "§a" : durPercent > 20 ? "§e" : "§c";
            tooltipList.add(Component.literal("§7   ❤ Độ bền: " + durColor + curDur + "§7/" + maxDmg + " (" + durColor + durPercent + "%§7)"));
        }

        // 4. Shulker Box / Hộp chứa đồ lồng bên trong:
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            int cCount = 0;
            for (ItemStack cIt : container.nonEmptyItems()) {
                cCount += cIt.getCount();
            }
            if (cCount > 0) {
                tooltipList.add(Component.literal("§7   📦 Chứa: §e" + cCount + " §7vật phẩm bên trong Hộp"));
            }
        }

        // 5. Bundle con lồng bên trong:
        BundleContents subBundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (subBundle != null && !subBundle.isEmpty()) {
            int subCount = 0;
            for (ItemStack sIt : subBundle.items()) {
                subCount += sIt.getCount();
            }
            tooltipList.add(Component.literal("§7   🎒 Chứa: §e" + subCount + " §7vật phẩm trong Túi đồ con"));
        }

        // 6. Pháo hoa:
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        if (fireworks != null) {
            tooltipList.add(Component.literal("§7   🚀 Thời lượng bay: §f" + fireworks.flightDuration()));
        }

        // 7. Thức ăn (khi giữ SHIFT):
        if (expanded) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                tooltipList.add(Component.literal("§7   🍗 Hồi phục: §e+" + food.nutrition() + " thức ăn§7, §b" + String.format("%.1f", food.saturation()) + " độ no"));
            }
        }
    }

    private static void extractEnchantments(ItemEnchantments enchants, List<Component> outList) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            Component fullName = Enchantment.getFullname(holder, level);
            boolean isCurse = holder.is(EnchantmentTags.CURSE);

            if (isCurse) {
                outList.add(Component.literal("§c" + fullName.getString()));
            } else {
                outList.add(Component.literal("§b" + fullName.getString()));
            }
        }
    }

    /**
     * Khi cuộn chuột hoặc di chuột vào 1 ô trong lưới Bundle, hiển thị FULL TOOLTIP của item đó.
     */
    public static void renderSelectedItemFullTooltip(Font font, GuiGraphics guiGraphics, int x, int y, int width, ItemStack itemStack) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || itemStack == null || itemStack.isEmpty()) return;

        List<Component> lines = Screen.getTooltipFromItem(mc, itemStack);
        if (lines.isEmpty()) return;

        List<ClientTooltipComponent> components = new ArrayList<>();
        for (Component line : lines) {
            components.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }

        itemStack.getTooltipImage().ifPresent(img -> {
            try {
                components.add(1, ClientTooltipComponent.create(img));
            } catch (Throwable ignored) {}
        });

        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        int posX = x + width / 2 - 12 - maxWidth / 2;
        int tooltipHeight = components.size() * 10 + 15;
        int posY = y - tooltipHeight;
        if (posY < 5) {
            posY = y + 25;
        }

        guiGraphics.renderTooltip(
                font,
                components,
                posX,
                posY,
                DefaultTooltipPositioner.INSTANCE,
                itemStack.get(DataComponents.TOOLTIP_STYLE)
        );
    }
}
