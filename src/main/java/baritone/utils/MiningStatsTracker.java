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

import baritone.api.BaritoneAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Bộ theo dõi và hiển thị thống kê đào khoáng sản (Mining Statistics Tracker).
 * Đếm số lượng block đã đào và số lượng từng loại quặng mục tiêu (chỉ hiển thị các quặng đã chọn).
 */
public final class MiningStatsTracker {

    private static final MiningStatsTracker INSTANCE = new MiningStatsTracker();

    public static MiningStatsTracker getInstance() {
        return INSTANCE;
    }

    public enum OreType {
        DIAMOND("Kim Cương", "Diamond", 0xFF38BDF8, () -> AutoMineScreen.oreDiamond,
                new ItemStack(Items.DIAMOND_ORE),
                Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
        EMERALD("Lục Bảo", "Emerald", 0xFF34D399, () -> AutoMineScreen.oreEmerald,
                new ItemStack(Items.EMERALD_ORE),
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
        ANCIENT_DEBRIS("Debris", "Ancient Debris", 0xFFC084FC, () -> AutoMineScreen.oreDebris,
                new ItemStack(Items.ANCIENT_DEBRIS),
                Blocks.ANCIENT_DEBRIS),
        GOLD("Vàng", "Gold", 0xFFFBBF24, () -> AutoMineScreen.oreGold,
                new ItemStack(Items.GOLD_ORE),
                Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
        IRON("Sắt", "Iron", 0xFFE2E8F0, () -> AutoMineScreen.oreIron,
                new ItemStack(Items.IRON_ORE),
                Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
        REDSTONE("Redstone", "Redstone", 0xFFF87171, () -> AutoMineScreen.oreRedstone,
                new ItemStack(Items.REDSTONE_ORE),
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
        LAPIS("Lapis", "Lapis", 0xFF60A5FA, () -> AutoMineScreen.oreLapis,
                new ItemStack(Items.LAPIS_ORE),
                Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
        COPPER("Đồng", "Copper", 0xFFFB923C, () -> AutoMineScreen.oreCopper,
                new ItemStack(Items.COPPER_ORE),
                Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
        COAL("Than", "Coal", 0xFF94A3B8, () -> AutoMineScreen.oreCoal,
                new ItemStack(Items.COAL_ORE),
                Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
        QUARTZ("Thạch Anh", "Quartz", 0xFFF1F5F9, () -> AutoMineScreen.oreQuartz,
                new ItemStack(Items.NETHER_QUARTZ_ORE),
                Blocks.NETHER_QUARTZ_ORE);

        private final String nameVi;
        private final String nameEn;
        private final int color;
        private final BooleanSupplier selectedSupplier;
        private final ItemStack itemStack;
        private final Block[] matchingBlocks;

        OreType(String nameVi, String nameEn, int color, BooleanSupplier selectedSupplier, ItemStack itemStack, Block... matchingBlocks) {
            this.nameVi = nameVi;
            this.nameEn = nameEn;
            this.color = color;
            this.selectedSupplier = selectedSupplier;
            this.itemStack = itemStack;
            this.matchingBlocks = matchingBlocks;
        }

        public String getNameVi() {
            return nameVi;
        }

        public String getNameEn() {
            return nameEn;
        }

        public int getColor() {
            return color;
        }

        public boolean isSelected() {
            return selectedSupplier.getAsBoolean();
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public boolean matches(Block block) {
            for (Block b : matchingBlocks) {
                if (b == block) {
                    return true;
                }
            }
            return false;
        }
    }

    public enum WoodType {
        OAK("Gỗ Sồi", "Oak", 0xFFB48A55, () -> AutoMineScreen.woodOak,
                new ItemStack(Items.OAK_LOG),
                Blocks.OAK_LOG, Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_OAK_WOOD),
        BIRCH("Bạch Dương", "Birch", 0xFFE2E8F0, () -> AutoMineScreen.woodBirch,
                new ItemStack(Items.BIRCH_LOG),
                Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD),
        SPRUCE("Gỗ Thông", "Spruce", 0xFF6B4226, () -> AutoMineScreen.woodSpruce,
                new ItemStack(Items.SPRUCE_LOG),
                Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_WOOD),
        DARK_OAK("Sồi Sẫm", "Dark Oak", 0xFF4A3525, () -> AutoMineScreen.woodDarkOak,
                new ItemStack(Items.DARK_OAK_LOG),
                Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_WOOD),
        ACACIA("Gỗ Keo", "Acacia", 0xFFFB923C, () -> AutoMineScreen.woodAcacia,
                new ItemStack(Items.ACACIA_LOG),
                Blocks.ACACIA_LOG, Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_ACACIA_WOOD),
        JUNGLE("Gỗ Rừng", "Jungle", 0xFF966F33, () -> AutoMineScreen.woodJungle,
                new ItemStack(Items.JUNGLE_LOG),
                Blocks.JUNGLE_LOG, Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_WOOD),
        CHERRY("Anh Đào", "Cherry", 0xFFF472B6, () -> AutoMineScreen.woodCherry,
                new ItemStack(Items.CHERRY_LOG),
                Blocks.CHERRY_LOG, Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_CHERRY_WOOD),
        MANGROVE("Gỗ Đước", "Mangrove", 0xFFE11D48, () -> AutoMineScreen.woodMangrove,
                new ItemStack(Items.MANGROVE_LOG),
                Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_WOOD),
        BAMBOO("Cây Tre", "Bamboo", 0xFFA3E635, () -> AutoMineScreen.woodBamboo,
                new ItemStack(Items.BAMBOO_BLOCK),
                Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK),
        CRIMSON("Nấm U Ám", "Crimson", 0xFFDC2626, () -> AutoMineScreen.woodCrimson,
                new ItemStack(Items.CRIMSON_STEM),
                Blocks.CRIMSON_STEM, Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_HYPHAE),
        WARPED("Nấm Kỳ Dị", "Warped", 0xFF06B6D4, () -> AutoMineScreen.woodWarped,
                new ItemStack(Items.WARPED_STEM),
                Blocks.WARPED_STEM, Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_STEM, Blocks.STRIPPED_WARPED_HYPHAE);

        private final String nameVi;
        private final String nameEn;
        private final int color;
        private final BooleanSupplier selectedSupplier;
        private final ItemStack itemStack;
        private final Block[] matchingBlocks;

        WoodType(String nameVi, String nameEn, int color, BooleanSupplier selectedSupplier, ItemStack itemStack, Block... matchingBlocks) {
            this.nameVi = nameVi;
            this.nameEn = nameEn;
            this.color = color;
            this.selectedSupplier = selectedSupplier;
            this.itemStack = itemStack;
            this.matchingBlocks = matchingBlocks;
        }

        public String getNameVi() {
            return nameVi;
        }

        public String getNameEn() {
            return nameEn;
        }

        public int getColor() {
            return color;
        }

        public boolean isSelected() {
            return selectedSupplier.getAsBoolean();
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public boolean matches(Block block) {
            for (Block b : matchingBlocks) {
                if (b == block) {
                    return true;
                }
            }
            return false;
        }
    }

    private final AtomicInteger totalBlocksMined = new AtomicInteger(0);
    private final Map<OreType, AtomicInteger> oreCounts = new ConcurrentHashMap<>();
    private final Map<WoodType, AtomicInteger> woodCounts = new ConcurrentHashMap<>();
    private long sessionStartTime = System.currentTimeMillis();
    private volatile long lastBreakTime = 0;
    private volatile BlockPos lastBreakPos = null;

    private MiningStatsTracker() {
        // Singleton
    }

    /**
     * Ghi nhận khi một block bị đập vỡ hoàn toàn.
     */
    public void onBlockBroken(BlockState state, BlockPos pos) {
        if (state == null || state.isAir()) {
            return;
        }
        long now = System.currentTimeMillis();
        // Chống đếm trùng nếu cùng một tọa độ bị gọi nhiều lần trong 500ms
        if (pos != null && pos.equals(lastBreakPos) && (now - lastBreakTime) < 500) {
            return;
        }
        if (pos != null) {
            lastBreakPos = pos.immutable();
        }
        lastBreakTime = now;

        totalBlocksMined.incrementAndGet();

        Block block = state.getBlock();
        for (OreType ore : OreType.values()) {
            if (ore.matches(block)) {
                oreCounts.computeIfAbsent(ore, k -> new AtomicInteger(0)).incrementAndGet();
                break;
            }
        }
        for (WoodType wood : WoodType.values()) {
            if (wood.matches(block)) {
                woodCounts.computeIfAbsent(wood, k -> new AtomicInteger(0)).incrementAndGet();
                break;
            }
        }
    }

    public int getTotalBlocksMined() {
        return totalBlocksMined.get();
    }

    public int getOreCount(OreType ore) {
        AtomicInteger count = oreCounts.get(ore);
        return count != null ? count.get() : 0;
    }

    public int getWoodCount(WoodType wood) {
        AtomicInteger count = woodCounts.get(wood);
        return count != null ? count.get() : 0;
    }

    public int getTotalWoodMined() {
        int sum = 0;
        for (AtomicInteger c : woodCounts.values()) {
            sum += c.get();
        }
        return sum;
    }

    public void reset() {
        totalBlocksMined.set(0);
        oreCounts.clear();
        woodCounts.clear();
        sessionStartTime = System.currentTimeMillis();
        lastBreakPos = null;
        lastBreakTime = 0;
    }

    public boolean hasMinedAny() {
        return totalBlocksMined.get() > 0;
    }

    public int getBlocksPerHour() {
        long elapsedMs = System.currentTimeMillis() - sessionStartTime;
        if (elapsedMs < 3000) {
            return 0;
        }
        double hours = elapsedMs / 3600000.0;
        return (int) (totalBlocksMined.get() / hours);
    }

    public String getFormattedDuration() {
        long seconds = (System.currentTimeMillis() - sessionStartTime) / 1000;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        long h = seconds / 3600;
        if (h > 0) {
            return String.format("%02d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    /**
     * Helper vẽ text luôn đảm bảo có Alpha channel đầy đủ (chống chữ bị tàng hình trong MC 1.21).
     */
    private void drawText(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        int argb = (color & 0xFF000000) == 0 ? (color | 0xFF000000) : color;
        graphics.drawString(font, text, x, y, argb, shadow);
    }

    public static class DisplayRow {
        private final ItemStack itemStack;
        private final String name;
        private final int count;
        private final int color;

        public DisplayRow(ItemStack itemStack, String name, int count, int color) {
            this.itemStack = itemStack;
            this.name = name;
            this.count = count;
            this.color = color;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public int getColor() {
            return color;
        }
    }

    /**
     * Vẽ bảng thống kê HUD trên màn hình game.
     */
    public void renderHud(GuiGraphics guiGraphics, Font font) {
        Minecraft mc = Minecraft.getInstance();
        if (!AutoMineScreen.optMiningStats) {
            return;
        }
        if (mc.options.hideGui) {
            return;
        }
        if (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) {
            return; // Ẩn khi bật F3 debug
        }

        boolean isMining = false;
        boolean isChop = false;
        try {
            if (BaritoneAPI.getProvider() != null && BaritoneAPI.getProvider().getPrimaryBaritone() != null) {
                isMining = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive();
                isChop = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isChopMode();
            }
        } catch (Exception ignored) {
        }

        // Chỉ hiển thị khi đang chạy mine/chop hoặc đã đào/chặt được block
        if (!isMining && totalBlocksMined.get() == 0) {
            return;
        }

        int startX = 8;
        int startY = 8;
        int width = 160;

        renderCard(guiGraphics, font, startX, startY, width, isMining, isChop);
    }

    /**
     * Vẽ card bảng thống kê tại tọa độ chỉ định.
     */
    public void renderCard(GuiGraphics guiGraphics, Font font, int x, int y, int width, boolean isMining) {
        boolean isChop = false;
        try {
            if (BaritoneAPI.getProvider() != null && BaritoneAPI.getProvider().getPrimaryBaritone() != null) {
                isChop = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isChopMode();
            }
        } catch (Exception ignored) {
        }
        renderCard(guiGraphics, font, x, y, width, isMining, isChop);
    }

    /**
     * Vẽ card bảng thống kê tại tọa độ chỉ định hỗ trợ cả Mining và Chop Wood.
     */
    public void renderCard(GuiGraphics guiGraphics, Font font, int x, int y, int width, boolean isMining, boolean isChop) {
        List<DisplayRow> displayRows = new ArrayList<>();

        if (isChop) {
            int selectedCount = 0;
            for (WoodType wood : WoodType.values()) {
                if (wood.isSelected()) {
                    selectedCount++;
                }
            }

            for (WoodType wood : WoodType.values()) {
                if (wood.isSelected()) {
                    int count = getWoodCount(wood);
                    if (count > 0 || selectedCount <= 4) {
                        displayRows.add(new DisplayRow(wood.getItemStack(), wood.getNameVi(), count, wood.getColor()));
                    }
                } else if (getWoodCount(wood) > 0) {
                    displayRows.add(new DisplayRow(wood.getItemStack(), wood.getNameVi(), getWoodCount(wood), wood.getColor()));
                }
            }
            if (displayRows.isEmpty()) {
                int added = 0;
                for (WoodType wood : WoodType.values()) {
                    if (wood.isSelected()) {
                        displayRows.add(new DisplayRow(wood.getItemStack(), wood.getNameVi(), 0, wood.getColor()));
                        if (++added >= 4) break;
                    }
                }
            }
            for (OreType ore : OreType.values()) {
                int count = getOreCount(ore);
                if (count > 0) {
                    displayRows.add(new DisplayRow(ore.getItemStack(), ore.getNameVi(), count, ore.getColor()));
                }
            }
        } else {
            for (OreType ore : OreType.values()) {
                if (ore.isSelected()) {
                    displayRows.add(new DisplayRow(ore.getItemStack(), ore.getNameVi(), getOreCount(ore), ore.getColor()));
                } else if (getOreCount(ore) > 0) {
                    displayRows.add(new DisplayRow(ore.getItemStack(), ore.getNameVi(), getOreCount(ore), ore.getColor()));
                }
            }
            for (WoodType wood : WoodType.values()) {
                int count = getWoodCount(wood);
                if (count > 0) {
                    displayRows.add(new DisplayRow(wood.getItemStack(), wood.getNameVi(), count, wood.getColor()));
                }
            }
        }

        int headerH = 18;
        int timeRowH = 12;
        int totalRowH = 14;
        int rowH = 18;
        int itemsH = displayRows.isEmpty() ? 0 : (displayRows.size() * rowH + 4);
        int cardH = headerH + timeRowH + totalRowH + itemsH + 6;

        int themeColor = isChop ? 0xFF34D399 : 0xFF38BDF8;
        int outlineColor = isChop ? 0x4034D399 : 0x4038BDF8;

        // 1. Nền Card tối mờ chuẩn Sleek Dark
        guiGraphics.fill(x, y, x + width, y + cardH, 0xB8080C14);

        // 2. Viền mỏng Neon
        drawOutline(guiGraphics, x, y, width, cardH, outlineColor);

        // 3. Thanh điểm nhấn neon trên cùng
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 3, themeColor);

        int curY = y + 6;

        // Header Title
        String title = isChop ? "AUTOCHOP STATS" : "AUTOMINE STATS";
        drawText(guiGraphics, font, title, x + 6, curY, themeColor, true);
        String statusDot = isMining ? "§a●" : "§7○";
        drawText(guiGraphics, font, statusDot, x + width - 13, curY, 0xFFFFFFFF, true);
        curY += 13;

        // Time & Rate row
        String timeStr = getFormattedDuration();
        String rateStr = String.format("%,d/h", getBlocksPerHour());
        drawText(guiGraphics, font, "§8" + timeStr + " §7| §e" + rateStr, x + 6, curY, 0xFF94A3B8, true);
        curY += 12;

        // Divider
        guiGraphics.fill(x + 5, curY, x + width - 5, curY + 1, 0x25FFFFFF);
        curY += 3;

        // Total Blocks Mined row
        String totalLabel = isChop ? "Đã chặt:" : "Đã đào:";
        drawText(guiGraphics, font, totalLabel, x + 6, curY, 0xFFE2E8F0, true);
        String unit = isChop ? " log" : " blk";
        String totalStr = String.format("%,d%s", totalBlocksMined.get(), unit);
        int totalW = font.width(totalStr);
        drawText(guiGraphics, font, "§e" + totalStr, x + width - totalW - 6, curY, 0xFFFBBF24, true);
        curY += 13;

        // Selected Items / Wood section (Hiển thị đầy đủ icon 3D Minecraft Item)
        if (!displayRows.isEmpty()) {
            guiGraphics.fill(x + 5, curY, x + width - 5, curY + 1, 0x25FFFFFF);
            curY += 3;

            for (DisplayRow row : displayRows) {
                // Hình icon item 3D Minecraft (16x16)
                guiGraphics.renderFakeItem(row.getItemStack(), x + 5, curY);

                // Tên item tiếng Việt
                drawText(guiGraphics, font, row.getName(), x + 25, curY + 4, 0xFFE2E8F0, true);

                // Số lượng đào / chặt được
                String countStr = String.format("%,d", row.getCount());
                int valW = font.width(countStr);
                int countColor = row.getCount() > 0 ? 0xFFFFFFFF : 0xFF64748B;
                drawText(guiGraphics, font, countStr, x + width - valW - 6, curY + 4, countColor, true);

                curY += rowH;
            }
        }
    }

    private void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
