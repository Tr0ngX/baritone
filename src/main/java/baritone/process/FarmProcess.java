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

package baritone.process;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.process.IFarmProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.FarmingStatsTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class FarmProcess extends BaritoneProcessHelper implements IFarmProcess {

    private boolean active;

    private List<BlockPos> locations;
    private int tickCount;

    private int range;
    private BlockPos center;

    private boolean isWaitingForGrowth;
    private int idleScanCount;
    private int calcFailCount;
    private final Map<BlockPos, Item> plannedPlantMap = new ConcurrentHashMap<>();

    private static final List<Item> FARMLAND_PLANTABLE = Arrays.asList(
            Items.BEETROOT_SEEDS,
            Items.MELON_SEEDS,
            Items.WHEAT_SEEDS,
            Items.PUMPKIN_SEEDS,
            Items.POTATO,
            Items.CARROT,
            Items.TORCHFLOWER_SEEDS,
            Items.PITCHER_POD
    );

    private static final List<Item> PICKUP_DROPPED = Arrays.asList(
            Items.BEETROOT_SEEDS,
            Items.BEETROOT,
            Items.MELON_SEEDS,
            Items.MELON_SLICE,
            Blocks.MELON.asItem(),
            Items.WHEAT_SEEDS,
            Items.WHEAT,
            Items.PUMPKIN_SEEDS,
            Blocks.PUMPKIN.asItem(),
            Items.POTATO,
            Items.CARROT,
            Items.NETHER_WART,
            Items.COCOA_BEANS,
            Blocks.SUGAR_CANE.asItem(),
            Blocks.BAMBOO.asItem(),
            Blocks.CACTUS.asItem(),
            Items.SWEET_BERRIES,
            Items.GLOW_BERRIES,
            Items.TORCHFLOWER,
            Items.TORCHFLOWER_SEEDS,
            Items.PITCHER_POD,
            Items.PITCHER_PLANT,
            Items.BONE_MEAL
    );

    public FarmProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void farm(int range, BlockPos pos) {
        if (pos == null) {
            center = baritone.getPlayerContext().playerFeet();
        } else {
            center = pos;
        }
        this.range = range;
        active = true;
        locations = null;
        isWaitingForGrowth = false;
        idleScanCount = 0;
        calcFailCount = 0;
        plannedPlantMap.clear();
    }

    private enum Harvest {
        WHEAT((CropBlock) Blocks.WHEAT),
        CARROTS((CropBlock) Blocks.CARROTS),
        POTATOES((CropBlock) Blocks.POTATOES),
        BEETROOT((CropBlock) Blocks.BEETROOTS),
        TORCHFLOWER((CropBlock) Blocks.TORCHFLOWER_CROP),
        PUMPKIN(Blocks.PUMPKIN, state -> true),
        MELON(Blocks.MELON, state -> true),
        NETHERWART(Blocks.NETHER_WART, state -> state.getValue(NetherWartBlock.AGE) >= 3),
        COCOA(Blocks.COCOA, state -> state.getValue(CocoaBlock.AGE) >= 2),
        SWEET_BERRY(Blocks.SWEET_BERRY_BUSH, state -> state.getValue(SweetBerryBushBlock.AGE) >= 2),
        PITCHER(Blocks.PITCHER_CROP, state -> state.getValue(PitcherCropBlock.AGE) >= 4),
        SUGARCANE(Blocks.SUGAR_CANE, null) {
            @Override
            public boolean readyToHarvest(Level world, BlockPos pos, BlockState state) {
                if (Baritone.settings().replantCrops.value) {
                    return world.getBlockState(pos.below()).getBlock() instanceof SugarCaneBlock;
                }
                return true;
            }
        },
        BAMBOO(Blocks.BAMBOO, null) {
            @Override
            public boolean readyToHarvest(Level world, BlockPos pos, BlockState state) {
                if (Baritone.settings().replantCrops.value) {
                    return world.getBlockState(pos.below()).getBlock() instanceof BambooStalkBlock;
                }
                return true;
            }
        },
        CACTUS(Blocks.CACTUS, null) {
            @Override
            public boolean readyToHarvest(Level world, BlockPos pos, BlockState state) {
                if (Baritone.settings().replantCrops.value) {
                    return world.getBlockState(pos.below()).getBlock() instanceof CactusBlock;
                }
                return true;
            }
        };
        public final Block block;
        public final Predicate<BlockState> readyToHarvest;

        Harvest(CropBlock blockCrops) {
            this(blockCrops, blockCrops::isMaxAge);
            // max age is 7 for wheat, carrots, and potatoes, but 3 for beetroot
        }

        Harvest(Block block, Predicate<BlockState> readyToHarvest) {
            this.block = block;
            this.readyToHarvest = readyToHarvest;
        }

        public boolean readyToHarvest(Level world, BlockPos pos, BlockState state) {
            return readyToHarvest.test(state);
        }
    }

    private boolean readyForHarvest(Level world, BlockPos pos, BlockState state) {
        for (Harvest harvest : Harvest.values()) {
            if (harvest.block == state.getBlock()) {
                return harvest.readyToHarvest(world, pos, state);
            }
        }
        return false;
    }

    private boolean isPlantable(ItemStack stack) {
        return !stack.isEmpty() && FARMLAND_PLANTABLE.contains(stack.getItem());
    }

    private boolean hasItemInInventory(Item item) {
        if (ctx.player() == null || item == null) return false;
        for (ItemStack stack : ctx.player().getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private Item getSeedForCrop(Block b) {
        if (b == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (b == Blocks.CARROTS) return Items.CARROT;
        if (b == Blocks.POTATOES) return Items.POTATO;
        if (b == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (b == Blocks.TORCHFLOWER_CROP) return Items.TORCHFLOWER_SEEDS;
        if (b == Blocks.PITCHER_CROP) return Items.PITCHER_POD;
        if (b == Blocks.NETHER_WART) return Items.NETHER_WART;
        if (b == Blocks.COCOA) return Items.COCOA_BEANS;
        return null;
    }

    private boolean isPlannedOrAnyPlantable(BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item planned = plannedPlantMap.get(pos);
        if (planned != null && hasItemInInventory(planned)) {
            return stack.getItem() == planned;
        }
        return isPlantable(stack);
    }

    private boolean hasFarmLandOrCrops() {
        if (locations == null || locations.isEmpty()) {
            return false;
        }
        for (BlockPos pos : locations) {
            BlockState state = ctx.world().getBlockState(pos);
            Block b = state.getBlock();
            if (b == Blocks.FARMLAND || b == Blocks.SOUL_SAND || b instanceof CropBlock
                    || b == Blocks.SUGAR_CANE || b == Blocks.BAMBOO || b == Blocks.CACTUS
                    || b == Blocks.COCOA || b == Blocks.NETHER_WART || b == Blocks.SWEET_BERRY_BUSH
                    || b == Blocks.TORCHFLOWER_CROP || b == Blocks.PITCHER_CROP) {
                return true;
            }
        }
        return false;
    }

    private boolean isBoneMeal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().equals(Items.BONE_MEAL);
    }

    private boolean isNetherWart(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().equals(Items.NETHER_WART);
    }

    private boolean isCocoa(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().equals(Items.COCOA_BEANS);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        FarmingStatsTracker.getInstance().onInventoryTick(ctx.player());
        if (Baritone.settings().mineGoalUpdateInterval.value != 0 && tickCount++ % Baritone.settings().mineGoalUpdateInterval.value == 0) {
            ArrayList<Block> scan = new ArrayList<>();
            for (Harvest harvest : Harvest.values()) {
                scan.add(harvest.block);
            }
            if (Baritone.settings().replantCrops.value) {
                scan.add(Blocks.FARMLAND);
                scan.add(Blocks.JUNGLE_LOG);
                if (Baritone.settings().replantNetherWart.value) {
                    scan.add(Blocks.SOUL_SAND);
                }
            }

            Baritone.getExecutor().execute(() -> locations = BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(ctx, scan, Baritone.settings().farmMaxScanSize.value, 10, 10));
        }
        if (locations == null) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        if (Baritone.settings().farmUsingSelection.value) {
            ISelection selection = baritone.getSelectionManager().getLastSelection();
            if (selection != null) {
                locations.removeIf(pos -> !selection.aabb().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            }
        }
        List<BlockPos> toBreak = new ArrayList<>();
        List<BlockPos> openFarmland = new ArrayList<>();
        List<BlockPos> bonemealable = new ArrayList<>();
        List<BlockPos> openSoulsand = new ArrayList<>();
        List<BlockPos> openLog = new ArrayList<>();
        for (BlockPos pos : locations) {
            //check if the target block is out of range.
            if (range != 0 && pos.distSqr(center) > range * range) {
                continue;
            }

            BlockState state = ctx.world().getBlockState(pos);
            boolean airAbove = ctx.world().getBlockState(pos.above()).getBlock() instanceof AirBlock;
            if (state.getBlock() == Blocks.FARMLAND) {
                if (airAbove) {
                    openFarmland.add(pos);
                }
                continue;
            }
            if (state.getBlock() == Blocks.SOUL_SAND) {
                if (airAbove) {
                    openSoulsand.add(pos);
                }
                continue;
            }
            if (state.getBlock() == Blocks.JUNGLE_LOG) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (ctx.world().getBlockState(pos.relative(direction)).getBlock() instanceof AirBlock) {
                        openLog.add(pos);
                        break;
                    }
                }
                continue;
            }
            if (readyForHarvest(ctx.world(), pos, state)) {
                toBreak.add(pos);
                Item seed = getSeedForCrop(state.getBlock());
                if (seed != null) {
                    plannedPlantMap.put(pos, seed);
                }
                continue;
            }
            if (state.getBlock() instanceof BonemealableBlock) {
                BonemealableBlock ig = (BonemealableBlock) state.getBlock();
                if (ig.isValidBonemealTarget(ctx.world(), pos, state) && ig.isBonemealSuccess(ctx.world(), ctx.world().random, pos, state)) {
                    bonemealable.add(pos);
                }
            }
        }

        baritone.getInputOverrideHandler().clearAllKeys();
        BetterBlockPos playerPos = ctx.playerFeet();
        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        for (BlockPos pos : toBreak) {
            if (playerPos.distSqr(pos) > blockReachDistance * blockReachDistance) {
                continue;
            }
            Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
            if (rot.isPresent() && isSafeToCancel) {
                baritone.getLookBehavior().updateTarget(rot.get(), true);
                MovementHelper.switchToBestToolFor(ctx, ctx.world().getBlockState(pos));
                if (ctx.isLookingAt(pos)) {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }
        ArrayList<BlockPos> both = new ArrayList<>(openFarmland);
        both.addAll(openSoulsand);
        for (BlockPos pos : both) {
            if (playerPos.distSqr(pos) > blockReachDistance * blockReachDistance) {
                continue;
            }
            boolean soulsand = openSoulsand.contains(pos);
            Optional<Rotation> rot = RotationUtils.reachableOffset(ctx, pos, new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5), blockReachDistance, false);
            if (rot.isPresent() && isSafeToCancel && baritone.getInventoryBehavior().throwaway(true, soulsand ? this::isNetherWart : (stack -> isPlannedOrAnyPlantable(pos, stack)))) {
                HitResult result = RayTraceUtils.rayTraceTowards(ctx.player(), rot.get(), blockReachDistance);
                if (result instanceof BlockHitResult && ((BlockHitResult) result).getDirection() == Direction.UP) {
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    if (ctx.isLookingAt(pos)) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                        plannedPlantMap.remove(pos);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }
        }
        for (BlockPos pos : openLog) {
            if (playerPos.distSqr(pos) > blockReachDistance * blockReachDistance) {
                continue;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (!(ctx.world().getBlockState(pos.relative(dir)).getBlock() instanceof AirBlock)) {
                    continue;
                }
                Vec3 faceCenter = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(dir.getUnitVec3i()).scale(0.5));
                Optional<Rotation> rot = RotationUtils.reachableOffset(ctx, pos, faceCenter, blockReachDistance, false);
                if (rot.isPresent() && isSafeToCancel && baritone.getInventoryBehavior().throwaway(true, this::isCocoa)) {
                    HitResult result = RayTraceUtils.rayTraceTowards(ctx.player(), rot.get(), blockReachDistance);
                    if (result instanceof BlockHitResult && ((BlockHitResult) result).getDirection() == dir) {
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        if (ctx.isLookingAt(pos)) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                }
            }
        }
        for (BlockPos pos : bonemealable) {
            if (playerPos.distSqr(pos) > blockReachDistance * blockReachDistance) {
                continue;
            }
            Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
            if (rot.isPresent() && isSafeToCancel && baritone.getInventoryBehavior().throwaway(true, this::isBoneMeal)) {
                baritone.getLookBehavior().updateTarget(rot.get(), true);
                if (ctx.isLookingAt(pos)) {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        if (calcFailed) {
            calcFailCount++;
            if (calcFailCount < 5) {
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            logDirect("Farm failed: Không thể tìm đường đến mục tiêu");
            if (Baritone.settings().notificationOnFarmFail.value) {
                logNotification("Farm failed", true);
            }
            onLostControl();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        } else {
            calcFailCount = 0;
        }

        List<Goal> goalz = new ArrayList<>();
        for (BlockPos pos : toBreak) {
            goalz.add(new BuilderProcess.GoalBreak(pos));
        }
        if (baritone.getInventoryBehavior().throwaway(false, this::isPlantable)) {
            for (BlockPos pos : openFarmland) {
                goalz.add(new GoalBlock(pos.above()));
            }
        }
        if (baritone.getInventoryBehavior().throwaway(false, this::isNetherWart)) {
            for (BlockPos pos : openSoulsand) {
                goalz.add(new GoalBlock(pos.above()));
            }
        }
        if (baritone.getInventoryBehavior().throwaway(false, this::isCocoa)) {
            for (BlockPos pos : openLog) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (ctx.world().getBlockState(pos.relative(direction)).getBlock() instanceof AirBlock) {
                        goalz.add(new GoalGetToBlock(pos.relative(direction)));
                    }
                }
            }
        }
        if (baritone.getInventoryBehavior().throwaway(false, this::isBoneMeal)) {
            for (BlockPos pos : bonemealable) {
                goalz.add(new GoalBlock(pos));
            }
        }
        for (Entity entity : ctx.entities()) {
            if (entity instanceof ItemEntity && entity.onGround()) {
                ItemEntity ei = (ItemEntity) entity;
                if (PICKUP_DROPPED.contains(ei.getItem().getItem())) {
                    // +0.1 because of farmland's 0.9375 dummy height lol
                    goalz.add(new GoalBlock(new BetterBlockPos(entity.position().x, entity.position().y + 0.1, entity.position().z)));
                }
            }
        }
        if (goalz.isEmpty()) {
            if (hasFarmLandOrCrops()) {
                idleScanCount = 0;
                if (!isWaitingForGrowth) {
                    isWaitingForGrowth = true;
                    logDirect("§e[Farm] Cây trồng đang phát triển. Tự động chờ đợt thu hoạch tiếp theo...");
                }
                if (center != null && playerPos.distSqr(center) > 4 * 4) {
                    return new PathingCommand(new GoalBlock(center), PathingCommandType.SET_GOAL_AND_PATH);
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            idleScanCount++;
            if (idleScanCount >= 10) {
                logDirect("Farm failed: Không có cây trồng trong khu vực");
                if (Baritone.settings().notificationOnFarmFail.value) {
                    logNotification("Farm failed", true);
                }
                onLostControl();
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        isWaitingForGrowth = false;
        idleScanCount = 0;
        return new PathingCommand(new GoalComposite(goalz.toArray(new Goal[0])), PathingCommandType.SET_GOAL_AND_PATH);
    }

    @Override
    public void onLostControl() {
        active = false;
        isWaitingForGrowth = false;
        idleScanCount = 0;
        calcFailCount = 0;
        plannedPlantMap.clear();
    }

    @Override
    public String displayName0() {
        return isWaitingForGrowth ? "Farming (Chờ cây lớn)" : "Farming";
    }
}
