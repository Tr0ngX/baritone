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

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ theo dõi thống kê nông nghiệp tự động (Farming Stats Tracker).
 * Đếm số lượng nông sản thu hoạch theo từng loại, tốc độ thu hoạch và thời gian farm.
 */
public final class FarmingStatsTracker {

    private static final FarmingStatsTracker INSTANCE = new FarmingStatsTracker();

    public static FarmingStatsTracker getInstance() {
        return INSTANCE;
    }

    public enum CropType {
        WHEAT("Lúa Mì", "Wheat", 0xFFEAB308, Items.WHEAT, Blocks.WHEAT),
        CARROT("Cà Rốt", "Carrot", 0xFFF97316, Items.CARROT, Blocks.CARROTS),
        POTATO("Khoai Tây", "Potato", 0xFFE2B36F, Items.POTATO, Blocks.POTATOES),
        BEETROOT("Củ Dền", "Beetroot", 0xFFDC2626, Items.BEETROOT, Blocks.BEETROOTS),
        MELON("Dưa Hấu", "Melon", 0xFF22C55E, Items.MELON_SLICE, Blocks.MELON),
        PUMPKIN("Bí Ngô", "Pumpkin", 0xFFEA580C, Blocks.PUMPKIN.asItem(), Blocks.PUMPKIN),
        SUGAR_CANE("Mía", "Sugar Cane", 0xFF84CC16, Items.SUGAR_CANE, Blocks.SUGAR_CANE),
        BAMBOO("Tre", "Bamboo", 0xFF10B981, Items.BAMBOO, Blocks.BAMBOO),
        CACTUS("Xương Rồng", "Cactus", 0xFF059669, Blocks.CACTUS.asItem(), Blocks.CACTUS),
        NETHER_WART("Bướu Nether", "Nether Wart", 0xFFB91C1C, Items.NETHER_WART, Blocks.NETHER_WART),
        COCOA("Hạt Ca Cao", "Cocoa Beans", 0xFF92400E, Items.COCOA_BEANS, Blocks.COCOA),
        SWEET_BERRIES("Quả Ngọt", "Sweet Berries", 0xFFE11D48, Items.SWEET_BERRIES, Blocks.SWEET_BERRY_BUSH),
        TORCHFLOWER("Hoa Đuốc", "Torchflower", 0xFFF59E0B, Items.TORCHFLOWER, Blocks.TORCHFLOWER_CROP);

        private final String nameVi;
        private final String nameEn;
        private final int color;
        private final Item dropItem;
        private final Block block;

        CropType(String nameVi, String nameEn, int color, Item dropItem, Block block) {
            this.nameVi = nameVi;
            this.nameEn = nameEn;
            this.color = color;
            this.dropItem = dropItem;
            this.block = block;
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

        public Item getDropItem() {
            return dropItem;
        }

        public Block getBlock() {
            return block;
        }
    }

    private long sessionStartTime = System.currentTimeMillis();
    private final AtomicInteger totalHarvested = new AtomicInteger(0);
    private final Map<CropType, AtomicInteger> cropCounts = new ConcurrentHashMap<>();
    private final Map<Item, Integer> lastInventorySnapshot = new ConcurrentHashMap<>();

    private FarmingStatsTracker() {
        for (CropType type : CropType.values()) {
            cropCounts.put(type, new AtomicInteger(0));
        }
    }

    public void onBlockBroken(BlockState state, BlockPos pos) {
        if (state == null || state.isAir()) {
            return;
        }
        Block block = state.getBlock();
        for (CropType crop : CropType.values()) {
            if (crop.getBlock() == block) {
                // Kiểm tra độ chín
                boolean mature = true;
                if (block instanceof CropBlock) {
                    mature = ((CropBlock) block).isMaxAge(state);
                } else if (block instanceof NetherWartBlock) {
                    mature = state.getValue(NetherWartBlock.AGE) >= 3;
                } else if (block instanceof CocoaBlock) {
                    mature = state.getValue(CocoaBlock.AGE) >= 2;
                } else if (block instanceof SweetBerryBushBlock) {
                    mature = state.getValue(SweetBerryBushBlock.AGE) >= 2;
                }
                if (mature) {
                    recordHarvest(crop, 1);
                }
                break;
            }
        }
    }

    public void recordHarvest(CropType type, int amount) {
        if (type == null || amount <= 0) return;
        totalHarvested.addAndGet(amount);
        cropCounts.computeIfAbsent(type, k -> new AtomicInteger(0)).addAndGet(amount);
    }

    public void onInventoryTick(Player player) {
        if (player == null) return;
        // Kiểm tra số lượng nông sản nhặt thêm trong túi đồ
        for (CropType crop : CropType.values()) {
            Item item = crop.getDropItem();
            if (item == null) continue;
            int currentCount = 0;
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (!stack.isEmpty() && stack.getItem() == item) {
                    currentCount += stack.getCount();
                }
            }
            Integer prev = lastInventorySnapshot.get(item);
            if (prev != null && currentCount > prev) {
                int delta = currentCount - prev;
                // Nếu delta tăng do nhặt nông sản
                cropCounts.computeIfAbsent(crop, k -> new AtomicInteger(0)).addAndGet(delta);
                totalHarvested.addAndGet(delta);
            }
            lastInventorySnapshot.put(item, currentCount);
        }
    }

    public int getTotalHarvested() {
        return totalHarvested.get();
    }

    public int getCount(CropType crop) {
        AtomicInteger count = cropCounts.get(crop);
        return count != null ? count.get() : 0;
    }

    public Map<CropType, Integer> getActiveCrops() {
        Map<CropType, Integer> map = new LinkedHashMap<>();
        for (CropType crop : CropType.values()) {
            int count = getCount(crop);
            if (count > 0) {
                map.put(crop, count);
            }
        }
        return map;
    }

    public long getSessionDurationMs() {
        return Math.max(1L, System.currentTimeMillis() - sessionStartTime);
    }

    public String getFormattedDuration() {
        long seconds = getSessionDurationMs() / 1000L;
        long h = seconds / 3600L;
        long m = (seconds % 3600L) / 60L;
        long s = seconds % 60L;
        if (h > 0) {
            return String.format("%02dh %02dm %02ds", h, m, s);
        } else {
            return String.format("%02dm %02ds", m, s);
        }
    }

    public double getHarvestPerHour() {
        long durationMs = getSessionDurationMs();
        if (durationMs < 5000L) return 0.0;
        double hours = (double) durationMs / (3600.0 * 1000.0);
        return totalHarvested.get() / hours;
    }

    public void resetSession() {
        sessionStartTime = System.currentTimeMillis();
        totalHarvested.set(0);
        for (CropType type : CropType.values()) {
            cropCounts.put(type, new AtomicInteger(0));
        }
        lastInventorySnapshot.clear();
    }
}
