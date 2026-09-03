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
import baritone.api.pathing.goals.*;
import baritone.api.process.IMineProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.*;
import baritone.api.utils.input.Input;
import baritone.cache.CachedChunk;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.stream.Collectors;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;

/**
 * Mine blocks of a certain type
 *
 * @author leijurv
 */
public final class MineProcess extends BaritoneProcessHelper implements IMineProcess {

    private static final Set<Item> JUNK_BLOCKS = Set.of(
            Blocks.COBBLESTONE.asItem(),
            Blocks.COBBLED_DEEPSLATE.asItem(),
            Blocks.DEEPSLATE.asItem(),
            Blocks.DIORITE.asItem(),
            Blocks.ANDESITE.asItem(),
            Blocks.GRANITE.asItem(),
            Blocks.TUFF.asItem(),
            Blocks.GRAVEL.asItem(),
            Blocks.DIRT.asItem(),
            Blocks.NETHERRACK.asItem(),
            Blocks.BASALT.asItem(),
            Blocks.BLACKSTONE.asItem(),
            Blocks.CALCITE.asItem()
    );

    private static final Set<Item> ORE_DROPS = Set.of(
            Items.DIAMOND,
            Items.LAPIS_LAZULI,
            Items.REDSTONE,
            Items.RAW_IRON,
            Items.RAW_GOLD,
            Items.RAW_COPPER,
            Items.EMERALD,
            Items.COAL,
            Items.ANCIENT_DEBRIS,
            Items.AMETHYST_SHARD,
            Items.QUARTZ,
            Items.IRON_NUGGET,
            Items.GOLD_NUGGET,
            Blocks.DIAMOND_ORE.asItem(),
            Blocks.DEEPSLATE_DIAMOND_ORE.asItem(),
            Blocks.IRON_ORE.asItem(),
            Blocks.DEEPSLATE_IRON_ORE.asItem(),
            Blocks.GOLD_ORE.asItem(),
            Blocks.DEEPSLATE_GOLD_ORE.asItem(),
            Blocks.COPPER_ORE.asItem(),
            Blocks.DEEPSLATE_COPPER_ORE.asItem(),
            Blocks.REDSTONE_ORE.asItem(),
            Blocks.DEEPSLATE_REDSTONE_ORE.asItem(),
            Blocks.LAPIS_ORE.asItem(),
            Blocks.DEEPSLATE_LAPIS_ORE.asItem(),
            Blocks.EMERALD_ORE.asItem(),
            Blocks.DEEPSLATE_EMERALD_ORE.asItem(),
            Blocks.COAL_ORE.asItem(),
            Blocks.DEEPSLATE_COAL_ORE.asItem(),
            Blocks.NETHER_QUARTZ_ORE.asItem(),
            Blocks.NETHER_GOLD_ORE.asItem()
    );

    private BlockOptionalMetaLookup filter;
    private List<BlockPos> knownOreLocations;
    private List<BlockPos> blacklist; // inaccessible
    private Map<BlockPos, Long> anticipatedDrops;
    private BlockPos branchPoint;
    private GoalRunAway branchPointRunaway;
    private BlockPos tunnelOrigin;
    private net.minecraft.core.Direction tunnelDirection;
    private int eatingSlot = -1;
    private int eatTicks = 0;
    private int desiredQuantity;
    private int tickCount;
    private BetterBlockPos lastStuckCheckPos = null;
    private int stuckTicks = 0;
    private int stuckRetries = 0;
    private boolean forceReroute = false;
    private BlockPos currentTunnelTarget = null;
    private int pillarFailCount = 0;
    private long lastPillarFailTime = 0;
    private boolean hasReachedTargetY = false;

    public MineProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return filter != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (desiredQuantity > 0) {
            int curr = ctx.player().getInventory().getNonEquipmentItems().stream()
                    .filter(stack -> filter.has(stack))
                    .mapToInt(ItemStack::getCount).sum();
            if (curr >= desiredQuantity) {
                logDirect("Have " + curr + " valid items");
                cancel();
                return null;
            }
        }
        if (calcFailed) {
            if (!knownOreLocations.isEmpty() && Baritone.settings().blacklistClosestOnFailure.value) {
                logDirect("Unable to find any path to " + filter + ", retrying...");
                knownOreLocations.stream().min(Comparator.comparingDouble(ctx.playerFeet()::distSqr)).ifPresent(blacklist::add);
                knownOreLocations.removeIf(blacklist::contains);
            } else if (Baritone.settings().exploreForBlocks.value || Baritone.settings().legitMine.value) {
                // When exploring/tunneling, never cancel! Just reset origin and continue tunnel
                if (tunnelDirection == null) {
                    tunnelDirection = ctx.player().getDirection().getAxis().isHorizontal() ? ctx.player().getDirection() : net.minecraft.core.Direction.NORTH;
                }
                branchPoint = ctx.playerFeet();
                branchPointRunaway = null;
            } else {
                logDirect("Unable to find any path to " + filter + ", canceling mine");
                if (Baritone.settings().notificationOnMineFail.value) {
                    logNotification("Unable to find any path to " + filter + ", canceling mine", true);
                }
                cancel();
                return null;
            }
        }

        handleAntiStuck();

        if (Baritone.settings().autoEat.value) {
            PathingCommand eatCmd = handleAutoEat(isSafeToCancel);
            if (eatCmd != null) {
                return eatCmd;
            }
        }

        if (Baritone.settings().crawlMineMode.value) {
            PathingCommand crawlCmd = handleCrawlState(isSafeToCancel);
            if (crawlCmd != null) {
                return crawlCmd;
            }
        }

        if (Baritone.settings().autoTotem.value && tickCount % 20 == 0) {
            handleAutoTotem();
        }

        updateLoucaSystem();
        // Gọi mỗi tick nếu đang có hàng đợi vứt rác, ngược lại quét mỗi 40 tick
        if (!pendingDropSlots.isEmpty() || tickCount % 40 == 0) {
            cleanInventoryIfFull();
        }
        int mineGoalUpdateInterval = Baritone.settings().mineGoalUpdateInterval.value;
        List<BlockPos> curr = new ArrayList<>(knownOreLocations);
        if (mineGoalUpdateInterval != 0 && tickCount++ % mineGoalUpdateInterval == 0) { // big brain
            CalculationContext context = new CalculationContext(baritone, true);
            Baritone.getExecutor().execute(() -> rescan(curr, context));
        }
        if (Baritone.settings().legitMine.value) {
            if (!addNearby()) {
                cancel();
                return null;
            }
        }
        Optional<BlockPos> shaft = curr.stream()
                .filter(pos -> pos.getX() == ctx.playerFeet().getX() && pos.getZ() == ctx.playerFeet().getZ())
                .filter(pos -> pos.getY() >= ctx.playerFeet().getY())
                .filter(pos -> !(BlockStateInterface.get(ctx, pos).getBlock() instanceof AirBlock)) // after breaking a block, it takes mineGoalUpdateInterval ticks for it to actually update this list =(
                .min(Comparator.comparingDouble(ctx.playerFeet().above()::distSqr));
        baritone.getInputOverrideHandler().clearAllKeys();
        if (shaft.isPresent() && ctx.player().onGround()) {
            BlockPos pos = shaft.get();
            BlockState state = baritone.bsi.get0(pos);
            if (!MovementHelper.avoidBreaking(baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
                if (rot.isPresent() && isSafeToCancel) {
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    MovementHelper.switchToBestToolFor(ctx, ctx.world().getBlockState(pos));
                    if (ctx.isLookingAt(pos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }
        }

        PathingCommand command = updateGoal();
        if (command == null) {
            // none in range
            cancel();
            return null;
        }
        return command;
    }


    private void updateLoucaSystem() {
        Map<BlockPos, Long> copy = new HashMap<>(anticipatedDrops);
        ctx.getSelectedBlock().ifPresent(pos -> {
            if (knownOreLocations.contains(pos)) {
                copy.put(pos, System.currentTimeMillis() + Baritone.settings().mineDropLoiterDurationMSThanksLouca.value);
            }
        });
        // elaborate dance to avoid concurrentmodificationexcepption since rescan thread reads this
        // don't want to slow everything down with a gross lock do we now
        for (BlockPos pos : anticipatedDrops.keySet()) {
            if (copy.get(pos) < System.currentTimeMillis()) {
                copy.remove(pos);
            }
        }
        anticipatedDrops = copy;
    }

    @Override
    public void onLostControl() {
        if (eatingSlot != -1) {
            try {
                ctx.minecraft().options.keyUse.setDown(false);
            } catch (Exception ignored) {}
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            eatingSlot = -1;
            eatTicks = 0;
        }
        tunnelOrigin = null;
        tunnelDirection = null;
        branchPoint = null;
        branchPointRunaway = null;
        lastStuckCheckPos = null;
        stuckTicks = 0;
        stuckRetries = 0;
        mine(0, (BlockOptionalMetaLookup) null);
    }

    @Override
    public String displayName0() {
        return "Mine " + filter;
    }

    private PathingCommand updateGoal() {
        BlockOptionalMetaLookup filter = filterFilter();
        if (filter == null) {
            return null;
        }

        // === ƯU TIÊN SỐ 1: BẮT BUỘC HÚT SẠCH 100% KIM CƯƠNG / QUẶNG RƠI TRÊN SÀN TRƯỚC KHI ĐI TIẾP ===
        List<BlockPos> droppedItems = droppedItemsScan();
        if (!droppedItems.isEmpty()) {
            Optional<BlockPos> closestDrop = droppedItems.stream()
                    .min(Comparator.comparingDouble(ctx.playerFeet()::distSqr));
            if (closestDrop.isPresent()) {
                BlockPos dropPos = closestDrop.get();
                // Nếu chưa đứng trúng vật phẩm rơi (cách quá 0.4 block) -> Bước thẳng tới nhặt ngay lập tức!
                if (ctx.playerFeet().distSqr(dropPos) > 0.16) {
                    return new PathingCommand(new GoalBlock(dropPos), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                }
            }
        }

        // Phát hiện nhanh quặng lộ ra ngay trước mặt hoặc các vách xung quanh khi di chuyển (6 hướng, cực kỳ nhẹ 0.001ms):
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos nearPos = ctx.playerFeet().relative(dir);
            if (filter.has(ctx.world().getBlockState(nearPos))) {
                if (!blacklist.contains(nearPos) && !knownOreLocations.contains(nearPos)) {
                    knownOreLocations.add(nearPos);
                }
            }
        }

        boolean legit = Baritone.settings().legitMine.value;
        List<BlockPos> locs = knownOreLocations;
        if (!locs.isEmpty()) {
            CalculationContext context = new CalculationContext(baritone);
            List<BlockPos> locs2 = prune(context, new ArrayList<>(locs), filter, Baritone.settings().mineMaxOreLocationsCount.value, blacklist, droppedItemsScan());
            if (!locs2.isEmpty()) {
                currentTunnelTarget = null;
                // CLUSTER-LOCK: Ưu tiên đào sạch cụm quặng gần trước (bán kính 8 block)
                // Chỉ khi không còn quặng gần mới đi tới cụm xa!
                List<BlockPos> nearbyOres = locs2.stream()
                        .filter(pos -> ctx.playerFeet().distSqr(pos) <= 64) // 8 * 8 = 64
                        .collect(java.util.stream.Collectors.toList());
                List<BlockPos> targetOres = nearbyOres.isEmpty() ? locs2 : nearbyOres;
                Goal goal = new GoalComposite(targetOres.stream().map(loc -> coalesce(loc, locs2, context)).toArray(Goal[]::new));
                knownOreLocations = locs2;
                boolean isPathing = baritone.getPathingBehavior().isPathing();
                boolean fr = forceReroute;
                forceReroute = false;
                if (fr) {
                    return new PathingCommand(goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                // Nếu đang di chuyển trên đường thì giữ REVALIDATE để không bị softCancel khựng lại
                return new PathingCommand(goal, (legit && !isPathing) ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }
        }

        // we don't know any ore locations at the moment
        if (!legit && !Baritone.settings().exploreForBlocks.value) {
            return null;
        }
        
        // KHI KHÔNG CÓ QUẶNG TRONG TẦM QUÉT:
        int targetY = Baritone.settings().legitMineYLevel.value;
        int currentY = ctx.playerFeet().y;

        // Đánh dấu đã chạm tới độ sâu targetY (hoặc xuất phát ngay tại tầng đào)
        if (currentY <= targetY) {
            hasReachedTargetY = true;
        }

        // KHI NGƯỜI CHƠI CHƯA XUỐNG TỚI TẦNG TARGET Y (ví dụ Y=-58 từ mặt đất):
        // CHỈ kích hoạt đào thẳng đứng / đào dốc khi:
        // 1. Chưa từng chạm tới tầng đào (hasReachedTargetY == false) VÀ currentY > targetY
        // 2. HOẶC người chơi bị văng lên quá xa khỏi tầng đào (currentY > targetY + 3)
        // Tuyệt đối KHÔNG kích hoạt khi đang đào hầm ngang mà chỉ bước lên 1-2 block chướng ngại vật!
        if (!hasReachedTargetY || currentY > targetY + 3) {
            boolean fr = forceReroute;
            forceReroute = false;

            if (Baritone.settings().straightDownMine.value) {
                // CHẾ ĐỘ 1: ĐÀO THẲNG ĐỨNG XUỐNG DƯỚI (SHAFT DOWN) SIÊU TỐC
                // Giữ nguyên tọa độ X, Z hiện tại, đào từng chặng 2 block xuống dưới để A* tính toán 0ms!
                int drop = Math.min(2, currentY - targetY);
                BlockPos shaftTarget = new BlockPos(ctx.playerFeet().x, currentY - drop, ctx.playerFeet().z);
                return new PathingCommand(new GoalTwoBlocks(shaftTarget), fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            } else {
                // CHẾ ĐỘ 2: ĐÀO CẦU THANG DỐC 1:1 (STAIRCASE DESCENT)
                if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
                    net.minecraft.core.Direction dir = ctx.player().getDirection();
                    tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
                }
                int drop = Math.min(4, currentY - targetY);
                BlockPos stairPos = new BlockPos(
                        ctx.playerFeet().x + tunnelDirection.getStepX() * drop,
                        currentY - drop,
                        ctx.playerFeet().z + tunnelDirection.getStepZ() * drop
                );
                return new PathingCommand(new GoalTwoBlocks(stairPos), fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }
        }

        // KHI ĐÃ TỚI ĐÚNG TẦNG TARGET Y (Y <= -58):
        // Kiểm tra xem AntiStuck có yêu cầu đào ngược lên không (thoát bedrock):
        if (tunnelOrigin != null && tunnelOrigin.getY() > currentY) {
            // Đào bậc thang ngược LÊN để thoát tầng bedrock
            if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
                net.minecraft.core.Direction dir = ctx.player().getDirection();
                tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
            }
            int rise = Math.min(3, tunnelOrigin.getY() - currentY);
            BlockPos escapePos = new BlockPos(
                    ctx.playerFeet().x + tunnelDirection.getStepX() * rise,
                    currentY + rise,
                    ctx.playerFeet().z + tunnelDirection.getStepZ() * rise
            );
            logDirect("§b[AntiStuck] Đang đào ngược lên Y=" + (currentY + rise) + " để thoát bedrock...");
            boolean fr = forceReroute;
            forceReroute = false;
            return new PathingCommand(new GoalTwoBlocks(escapePos), fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        }

        // Lúc này mới bắt đầu đào ngang thẳng tiến 16 block phía trước!
        if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
            net.minecraft.core.Direction dir = ctx.player().getDirection();
            tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
        }
        // Điểm waypoint ổn định: neo ở targetY (ví dụ -58), không reset chỉ vì bot vừa bước lên 1 bậc block!
        if (currentTunnelTarget == null || ctx.playerFeet().distSqr(currentTunnelTarget) <= 9 || forceReroute) {
            currentTunnelTarget = new BlockPos(
                    ctx.playerFeet().x + tunnelDirection.getStepX() * 16,
                    targetY,
                    ctx.playerFeet().z + tunnelDirection.getStepZ() * 16
            );
        }
        if (forceReroute) {
            forceReroute = false;
            return new PathingCommand(new GoalTwoBlocks(currentTunnelTarget), PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        return new PathingCommand(new GoalTwoBlocks(currentTunnelTarget), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    private void rescan(List<BlockPos> already, CalculationContext context) {
        BlockOptionalMetaLookup filter = filterFilter();
        if (filter == null) {
            return;
        }
        if (Baritone.settings().legitMine.value) {
            return;
        }
        List<BlockPos> dropped = droppedItemsScan();
        List<BlockPos> locs = searchWorld(context, filter, Baritone.settings().mineMaxOreLocationsCount.value, already, blacklist, dropped);
        locs.addAll(dropped);
        if (locs.isEmpty() && !Baritone.settings().exploreForBlocks.value) {
            logDirect("No locations for " + filter + " known, cancelling");
            if (Baritone.settings().notificationOnMineFail.value) {
                logNotification("No locations for " + filter + " known, cancelling", true);
            }
            cancel();
            return;
        }
        knownOreLocations = locs;
    }

    private boolean internalMiningGoal(BlockPos pos, CalculationContext context, List<BlockPos> locs) {
        // Here, BlockStateInterface is used because the position may be in a cached chunk (the targeted block is one that is kept track of)
        if (locs.contains(pos)) {
            return true;
        }
        BlockState state = context.bsi.get0(pos);
        if (Baritone.settings().internalMiningAirException.value && state.getBlock() instanceof AirBlock) {
            return true;
        }
        return filter.has(state) && plausibleToBreak(context, pos);
    }

    private Goal coalesce(BlockPos loc, List<BlockPos> locs, CalculationContext context) {
        boolean assumeVerticalShaftMine = !(baritone.bsi.get0(loc.above()).getBlock() instanceof FallingBlock);
        if (!Baritone.settings().forceInternalMining.value) {
            if (assumeVerticalShaftMine) {
                // we can get directly below the block
                return new GoalThreeBlocks(loc);
            } else {
                // we need to get feet or head into the block
                return new GoalTwoBlocks(loc);
            }
        }
        boolean upwardGoal = internalMiningGoal(loc.above(), context, locs);
        boolean downwardGoal = internalMiningGoal(loc.below(), context, locs);
        boolean doubleDownwardGoal = internalMiningGoal(loc.below(2), context, locs);
        if (upwardGoal == downwardGoal) { // symmetric
            if (doubleDownwardGoal && assumeVerticalShaftMine) {
                // we have a checkerboard like pattern
                // this one, and the one two below it
                // therefore it's fine to path to immediately below this one, since your feet will be in the doubleDownwardGoal
                // but only if assumeVerticalShaftMine
                return new GoalThreeBlocks(loc);
            } else {
                // this block has nothing interesting two below, but is symmetric vertically so we can get either feet or head into it
                return new GoalTwoBlocks(loc);
            }
        }
        if (upwardGoal) {
            // downwardGoal known to be false
            // ignore the gap then potential doubleDownward, because we want to path feet into this one and head into upwardGoal
            return new GoalBlock(loc);
        }
        // upwardGoal known to be false, downwardGoal known to be true
        if (doubleDownwardGoal && assumeVerticalShaftMine) {
            // this block and two below it are goals
            // path into the center of the one below, because that includes directly below this one
            return new GoalTwoBlocks(loc.below());
        }
        // upwardGoal false, downwardGoal true, doubleDownwardGoal false
        // just this block and the one immediately below, no others
        return new GoalBlock(loc.below());
    }

    private static class GoalThreeBlocks extends GoalTwoBlocks {

        public GoalThreeBlocks(BlockPos pos) {
            super(pos);
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            return x == this.x && (y == this.y || y == this.y - 1 || y == this.y - 2) && z == this.z;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            int xDiff = x - this.x;
            int yDiff = y - this.y;
            int zDiff = z - this.z;
            return GoalBlock.calculate(xDiff, yDiff < -1 ? yDiff + 2 : yDiff == -1 ? 0 : yDiff, zDiff);
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 393857768;
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalThreeBlocks{x=%s,y=%s,z=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z)
            );
        }
    }

    public List<BlockPos> droppedItemsScan() {
        if (!Baritone.settings().mineScanDroppedItems.value || ctx.world() == null) {
            return Collections.emptyList();
        }
        List<BlockPos> ret = new ArrayList<>();
        BetterBlockPos pf = ctx.playerFeet();
        for (Entity entity : ((ClientLevel) ctx.world()).entitiesForRendering()) {
            if (entity instanceof ItemEntity && entity.isAlive()) {
                ItemEntity ei = (ItemEntity) entity;
                ItemStack stack = ei.getItem();
                Item item = stack.getItem();
                if (ORE_DROPS.contains(item) || (filter != null && filter.has(stack)) || item.getDescriptionId().contains("ore") || item.getDescriptionId().contains("raw")) {
                    BlockPos pos = entity.blockPosition();
                    if (pos.distSqr(pf) <= 256) { // Trong bán kính 16 block
                        ret.add(pos);
                    }
                }
            }
        }
        return ret;
    }

    private final List<Integer> pendingDropSlots = new ArrayList<>();
    private int dropCooldown = 0;

    private void cleanInventoryIfFull() {
        if (ctx.player() == null || ctx.player().containerMenu != ctx.player().inventoryMenu) {
            return;
        }

        // Nếu đang có hàng đợi vứt rác → vứt 1 stack mỗi 5 tick (tránh bị server kick vì spam packet)
        if (!pendingDropSlots.isEmpty()) {
            if (dropCooldown > 0) {
                dropCooldown--;
                return;
            }
            int slotIndex = pendingDropSlots.remove(0);
            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            if (slotIndex >= 0 && slotIndex < inv.size()) {
                ItemStack stack = inv.get(slotIndex);
                if (!stack.isEmpty() && JUNK_BLOCKS.contains(stack.getItem())) {
                    // Chuyển item vào hotbar slot 0 (nếu chưa ở đó) rồi vứt cả stack
                    if (slotIndex >= 9) {
                        // Item trong balo chính (slot 9-35): swap vào hotbar slot 0 trước
                        int windowSlot = slotIndex; // slot 9-35 trong inventory = windowSlot 9-35
                        ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 0, ClickType.SWAP, ctx.player());
                        // Giờ item ở hotbar slot 0, vứt nó
                        ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, 36, 1, ClickType.THROW, ctx.player());
                        // Swap lại item cũ từ hotbar 0 về vị trí ban đầu
                        ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 0, ClickType.SWAP, ctx.player());
                    } else {
                        // Item đã ở hotbar (slot 0-8): windowSlot = slot + 36
                        int windowSlot = slotIndex + 36;
                        ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 1, ClickType.THROW, ctx.player());
                    }
                }
            }
            dropCooldown = 4; // Chờ 4 tick trước khi vứt stack tiếp theo (≈ 0.2 giây)
            if (pendingDropSlots.isEmpty()) {
                logDirect("§a[AutoDrop] Đã dọn sạch toàn bộ rác trong balo!");
            }
            return;
        }

        // Chỉ quét lại khi không có hàng đợi đang xử lý
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            if (inv.get(i).isEmpty()) {
                emptySlots++;
            }
        }

        // Khi balo còn 5 ô trống trở xuống: Quét và nạp hàng đợi vứt rác
        if (emptySlots <= 5) {
            int keptBuildingBlocks = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.get(i);
                if (!stack.isEmpty() && JUNK_BLOCKS.contains(stack.getItem())) {
                    // Giữ lại đúng 1 stack 64 block Cobblestone/Cobbled Deepslate để bắc cầu
                    if (keptBuildingBlocks < 64 && (stack.is(Items.COBBLED_DEEPSLATE) || stack.is(Items.COBBLESTONE))) {
                        keptBuildingBlocks += stack.getCount();
                        continue;
                    }
                    pendingDropSlots.add(i);
                }
            }
            if (!pendingDropSlots.isEmpty()) {
                logDirect("§e[AutoDrop] Balo gần đầy! Đang dọn " + pendingDropSlots.size() + " stack rác...");
                dropCooldown = 0; // Bắt đầu vứt ngay
            }
        }
    }

    private void handleAntiStuck() {
        if (ctx.player() == null || eatingSlot != -1) {
            return;
        }
        BetterBlockPos currentFeet = ctx.playerFeet();
        if (lastStuckCheckPos != null && currentFeet.distSqr(lastStuckCheckPos) < 1.5) {
            stuckTicks++;
        } else {
            lastStuckCheckPos = currentFeet;
            stuckTicks = 0;
            stuckRetries = 0;
            // Đã di chuyển thành công → Reset trạng thái noPillar
            if (Baritone.settings().noPillar.value) {
                pillarFailCount = 0;
                Baritone.settings().noPillar.value = false;
                logDirect("§a[AntiPillarLoop] Đã di chuyển thành công, cho phép nhảy+đặt block trở lại.");
            }
        }

        // === PHÁT HIỆN PILLAR LOOP ===
        // Khi bị kẹt 1 chỗ > 60 tick (3 giây), kiểm tra xem bot có đang cố nhảy+đặt block không
        // Nếu đúng → bật noPillar để ép A* tìm đường khác
        if (stuckTicks >= 60 && !Baritone.settings().noPillar.value) {
            // Phát hiện: player đang nhảy lên rồi rơi xuống = cùng vị trí X,Z nhưng Y dao động
            boolean isJumping = ctx.player().getDeltaMovement().y > 0.1 || !ctx.player().onGround();
            boolean sameXZ = lastStuckCheckPos != null
                    && Math.abs(currentFeet.x - lastStuckCheckPos.x) < 2
                    && Math.abs(currentFeet.z - lastStuckCheckPos.z) < 2;

            if (isJumping && sameXZ) {
                pillarFailCount++;
                if (pillarFailCount >= 2) {
                    Baritone.settings().noPillar.value = true;
                    logDirect("§c[AntiPillarLoop] Bot bị kẹt nhảy+đặt block dưới chân (" + pillarFailCount + " lần)! Tạm tắt pillar, buộc đổi hướng...");
                    forceReroute = true;
                    stuckTicks = 0;
                    return;
                }
            }
        }

        // Bị kẹt 1 chỗ quá 120 tick (6 giây):
        if (stuckTicks >= 120) {
            stuckTicks = 0;
            stuckRetries++;

            int targetY = Baritone.settings().legitMineYLevel.value;
            // ƯU TIÊN SỐ 1 KHI BỊ KẸT: ĐÀO XUỐNG DƯỚI NẾU CHƯA ĐẠT TẦNG TARGET Y (Y=-58)!
            if (!hasReachedTargetY || currentFeet.y > targetY + 3) {
                if (tunnelDirection != null) {
                    net.minecraft.core.Direction newDir = (stuckRetries % 2 == 1) ? tunnelDirection.getClockWise() : tunnelDirection.getCounterClockWise();
                    tunnelDirection = newDir;
                    logDirect("§6[AntiStuck] Gặp vật cản khi đào dốc xuống! Tự động đổi hướng đào sang " + newDir.getName().toUpperCase() + "...");
                }
                forceReroute = true;
                return;
            }

            // Phương án 2: Nếu bị kẹt không thể tới được quặng gần nhất -> Blacklist quặng đó và đổi sang quặng khác
            if (knownOreLocations != null && !knownOreLocations.isEmpty()) {
                knownOreLocations.stream()
                        .min(Comparator.comparingDouble(currentFeet::distSqr))
                        .ifPresent(pos -> {
                            blacklist.add(pos);
                            knownOreLocations.remove(pos);
                            logDirect("§e[AntiStuck] Quặng tại " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " bị chặn/không tới được! Đang tự động đổi sang vỉa quặng khác...");
                        });
                forceReroute = true;
                return;
            }

            // Phương án 3: Đào hầm tại tầng đáy bị kẹt bedrock
            if (tunnelDirection != null) {
                // SAU 4 LẦN THỬ (đã xoay cả 4 hướng) MÀ VẪN KẸT = TOÀN BEDROCK!
                // -> ĐÀO NGƯỢC LÊN 3 block để thoát khỏi tầng bedrock, rồi đào ngang ở tầng cao hơn
                if (stuckRetries >= 4) {
                    int escapeY = Math.min(currentFeet.y + 3, targetY + 5);
                    logDirect("§c[AntiStuck] Bị kẹt bedrock cả 4 hướng! Đào ngược lên Y=" + escapeY + " để thoát...");
                    // Cập nhật tunnelOrigin lên tầng mới
                    tunnelOrigin = new BlockPos(currentFeet.x, escapeY, currentFeet.z);
                    stuckRetries = 0;
                    forceReroute = true;
                    return;
                }
                net.minecraft.core.Direction newDir = (stuckRetries % 2 == 1) ? tunnelDirection.getClockWise() : tunnelDirection.getCounterClockWise();
                tunnelDirection = newDir;
                tunnelOrigin = new BlockPos(currentFeet.x, targetY, currentFeet.z);
                logDirect("§6[AntiStuck] Bị kẹt hầm/gặp Bedrock tại tầng đáy! Tự động chuyển hướng đào hầm sang " + newDir.getName().toUpperCase() + "!");
                forceReroute = true;
                return;
            }

            // Phương án 4: Khắc phục kẹt cát/sỏi sập trúng đầu
            BlockPos head = currentFeet.above();
            BlockState headState = ctx.world().getBlockState(head);
            if (headState.isSuffocating(ctx.world(), head) || !headState.isAir()) {
                ctx.playerController().clickBlock(head, net.minecraft.core.Direction.UP);
            }
            forceReroute = true;
        }
    }

    private PathingCommand handleAutoEat(boolean isSafeToCancel) {
        if (ctx.player() == null) {
            return null;
        }

        // If currently in the middle of eating:
        if (eatingSlot != -1) {
            eatTicks++;
            try {
                ctx.minecraft().options.keyUse.setDown(true);
            } catch (Exception ignored) {}
            baritone.getInputOverrideHandler().clearAllKeys();

            // Minecraft eating takes 32 ticks
            if (ctx.player().isUsingItem() || eatTicks < 35) {
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            } else {
                // Done eating this food item!
                try {
                    ctx.minecraft().options.keyUse.setDown(false);
                } catch (Exception ignored) {}
                eatingSlot = -1;
                eatTicks = 0;
            }
        }

        // Check if player needs to eat:
        int foodLevel = ctx.player().getFoodData().getFoodLevel();
        float health = ctx.player().getHealth();
        float maxHealth = ctx.player().getMaxHealth();

        // 1. Dưới 5 cục thịt đói (foodLevel <= 10, vì mỗi cục thịt = 2 foodLevel)
        // 2. Mất máu / yếu máu (health < maxHealth && foodLevel < 20)
        boolean lowHunger = foodLevel <= 10;
        boolean lowHealth = health < maxHealth && foodLevel < 20;

        if (lowHunger || lowHealth) {
            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            
            // 1. Check hotbar first (slots 0 to 8)
            int targetHotbarSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (isGoodFood(inv.get(i))) {
                    targetHotbarSlot = i;
                    break;
                }
            }

            // 2. If not in hotbar, search main inventory (slots 9 to 35) and swap to hotbar slot 1
            if (targetHotbarSlot == -1) {
                for (int i = 9; i < 36; i++) {
                    if (isGoodFood(inv.get(i))) {
                        ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, i, 1, ClickType.SWAP, ctx.player());
                        targetHotbarSlot = 1;
                        break;
                    }
                }
            }

            if (targetHotbarSlot != -1) {
                ctx.player().getInventory().setSelectedSlot(targetHotbarSlot);
                ctx.playerController().syncHeldItem();
                ctx.playerController().processRightClick(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND);
                try {
                    ctx.minecraft().options.keyUse.setDown(true);
                } catch (Exception ignored) {}
                baritone.getInputOverrideHandler().clearAllKeys();
                eatingSlot = targetHotbarSlot;
                eatTicks = 0;
                logDirect("§a[AutoEat] Bắt đầu ăn: " + inv.get(targetHotbarSlot).getHoverName().getString() + " (Máu: " + (int)health + "/" + (int)maxHealth + " | Đói: " + (foodLevel / 2) + " cục)");
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        return null;
    }

    private static boolean isGoodFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item == Items.ROTTEN_FLESH
                || item == Items.PUFFERFISH
                || item == Items.POISONOUS_POTATO
                || item == Items.SPIDER_EYE
                || item == Items.CHORUS_FRUIT) {
            return false;
        }
        return stack.has(DataComponents.FOOD) || stack.getItem().components().has(DataComponents.FOOD);
    }

    private void handleAutoTotem() {
        if (ctx.player() == null || ctx.player().containerMenu != ctx.player().inventoryMenu) {
            return;
        }
        ItemStack offhand = ctx.player().getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        // 1. Search main inventory first (slots 9 to 35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.get(i);
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, i, 40, ClickType.SWAP, ctx.player());
                logDirect("§6[AutoTotem] Đã tự động trang bị Totem Bất Tử vào tay phụ (Offhand)!");
                return;
            }
        }
        // 2. Search hotbar (slots 1 to 8, avoiding pickaxe in slot 0)
        for (int i = 1; i < 9; i++) {
            ItemStack stack = inv.get(i);
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, i + 36, 40, ClickType.SWAP, ctx.player());
                logDirect("§6[AutoTotem] Đã tự động trang bị Totem Bất Tử vào tay phụ (Offhand)!");
                return;
            }
        }
    }

    private int crawlCooldown = 0;

    private boolean isCrawling() {
        return ctx.player() != null && ctx.player().getPose() == Pose.SWIMMING;
    }

    private int findTrapDoorSlot() {
        if (ctx.player() == null) return -1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.get(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof TrapDoorBlock) {
                return i;
            }
        }
        return -1;
    }

    private PathingCommand handleCrawlState(boolean isSafeToCancel) {
        if (!Baritone.settings().crawlMineMode.value || ctx.player() == null) {
            return null;
        }

        if (isCrawling()) {
            return null; // Đang ở tư thế crawl 1 block, tiếp tục đào bình thường
        }

        if (crawlCooldown > 0) {
            crawlCooldown--;
            return null;
        }

        int trapdoorSlot = findTrapDoorSlot();
        if (trapdoorSlot == -1) {
            logDirect("§c[CrawlMine] Không tìm thấy Trapdoor (Cửa sập) trong balo! Tự động tắt Crawl Mode.");
            Baritone.settings().crawlMineMode.value = false;
            return null;
        }

        // Đưa trapdoor lên hotbar nếu đang ở balo chính
        if (trapdoorSlot >= 9) {
            ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, trapdoorSlot, 0, ClickType.SWAP, ctx.player());
            trapdoorSlot = 0;
        }
        ctx.player().getInventory().setSelectedSlot(trapdoorSlot);

        // Kiểm tra block phía trên đầu
        BlockPos headPos = ctx.playerFeet().above();
        BlockState headState = ctx.world().getBlockState(headPos);

        // Nếu block trên đầu đã có TrapDoor -> click chuột phải để gập xuống ép người chơi crawl
        if (headState.getBlock() instanceof TrapDoorBlock) {
            Optional<Rotation> rot = RotationUtils.reachable(ctx, headPos);
            if (rot.isPresent()) {
                baritone.getLookBehavior().updateTarget(rot.get(), true);
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                crawlCooldown = 10;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        // Nếu chưa có trapdoor -> Đặt trapdoor lên block trên trần
        BlockPos ceilingPos = ctx.playerFeet().above(2);
        BlockState ceilingState = ctx.world().getBlockState(ceilingPos);
        if (!ceilingState.isAir()) {
            Optional<Rotation> rot = RotationUtils.reachable(ctx, ceilingPos);
            if (rot.isPresent()) {
                baritone.getLookBehavior().updateTarget(rot.get(), true);
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                crawlCooldown = 10;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        return null;
    }

    public static List<BlockPos> searchWorld(CalculationContext ctx, BlockOptionalMetaLookup filter, int max, List<BlockPos> alreadyKnown, List<BlockPos> blacklist, List<BlockPos> dropped) {
        List<BlockPos> locs = new ArrayList<>();
        List<Block> untracked = new ArrayList<>();
        for (BlockOptionalMeta bom : filter.blocks()) {
            Block block = bom.getBlock();
            if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
                BetterBlockPos pf = ctx.baritone.getPlayerContext().playerFeet();

                // maxRegionDistanceSq 2 means adjacent directly or adjacent diagonally; nothing further than that
                locs.addAll(ctx.worldData.getCachedWorld().getLocationsOf(
                        BlockUtils.blockToString(block),
                        Baritone.settings().maxCachedWorldScanCount.value,
                        pf.x,
                        pf.z,
                        2
                ));
            } else {
                untracked.add(block);
            }
        }

        locs = prune(ctx, locs, filter, max, blacklist, dropped);

        if (!untracked.isEmpty() || (Baritone.settings().extendCacheOnThreshold.value && locs.size() < max)) {
            locs.addAll(BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(
                    ctx.getBaritone().getPlayerContext(),
                    filter,
                    max,
                    384, // Quét toàn bộ chiều cao thế giới (-64 đến 320), kim cương ở mọi tầng Y đều được phát hiện!
                    32
            )); // maxSearchRadius is NOT sq
        }

        locs.addAll(alreadyKnown);

        return prune(ctx, locs, filter, max, blacklist, dropped);
    }

    private boolean addNearby() {
        List<BlockPos> dropped = droppedItemsScan();
        knownOreLocations.addAll(dropped);
        BlockPos playerFeet = ctx.playerFeet();
        BlockStateInterface bsi = new BlockStateInterface(ctx);


        BlockOptionalMetaLookup filter = filterFilter();
        if (filter == null) {
            return false;
        }

        int searchDist = 10;
        double fakedBlockReachDistance = 20; // at least 10 * sqrt(3) with some extra space to account for positioning within the block
        for (int x = playerFeet.getX() - searchDist; x <= playerFeet.getX() + searchDist; x++) {
            for (int y = playerFeet.getY() - searchDist; y <= playerFeet.getY() + searchDist; y++) {
                for (int z = playerFeet.getZ() - searchDist; z <= playerFeet.getZ() + searchDist; z++) {
                    // crucial to only add blocks we can see because otherwise this
                    // is an x-ray and it'll get caught
                    if (filter.has(bsi.get0(x, y, z))) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if ((Baritone.settings().legitMineIncludeDiagonals.value && knownOreLocations.stream().anyMatch(ore -> ore.distSqr(pos) <= 2 /* sq means this is pytha dist <= sqrt(2) */)) || RotationUtils.reachable(ctx, pos, fakedBlockReachDistance).isPresent()) {
                            knownOreLocations.add(pos);
                        }
                    }
                }
            }
        }
        knownOreLocations = prune(new CalculationContext(baritone), knownOreLocations, filter, Baritone.settings().mineMaxOreLocationsCount.value, blacklist, dropped);
        return true;
    }

    private static List<BlockPos> prune(CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, List<BlockPos> blacklist, List<BlockPos> dropped) {
        dropped.removeIf(drop -> {
            for (BlockPos pos : locs2) {
                if (pos.distSqr(drop) <= 9 && filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ())) && MineProcess.plausibleToBreak(ctx, pos)) { // TODO maybe drop also has to be supported? no lava below?
                    return true;
                }
            }
            return false;
        });
        List<BlockPos> locs = locs2
                .stream()
                .distinct()

                // remove any that are within loaded chunks that aren't actually what we want
                .filter(pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ()) || filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ())) || dropped.contains(pos))

                // remove any that are implausible to mine (encased in bedrock, or touching lava)
                .filter(pos -> MineProcess.plausibleToBreak(ctx, pos))

                .filter(pos -> {
                    if (Baritone.settings().allowOnlyExposedOres.value) {
                        return isNextToAir(ctx, pos);
                    } else {
                        return true;
                    }
                })

                .filter(pos -> pos.getY() >= Baritone.settings().minYLevelWhileMining.value + ctx.world.dimensionType().minY())

                .filter(pos -> pos.getY() <= Baritone.settings().maxYLevelWhileMining.value)

                .sorted((a, b) -> {
                    BlockPos p = ctx.getBaritone().getPlayerContext().player().blockPosition();
                    double dyA = (a.getY() - p.getY()) * 3.0;
                    double distA = Math.pow(a.getX() - p.getX(), 2) + Math.pow(dyA, 2) + Math.pow(a.getZ() - p.getZ(), 2);
                    double dyB = (b.getY() - p.getY()) * 3.0;
                    double distB = Math.pow(b.getX() - p.getX(), 2) + Math.pow(dyB, 2) + Math.pow(b.getZ() - p.getZ(), 2);
                    return Double.compare(distA, distB);
                })
                .collect(Collectors.toList());

        if (locs.size() > max) {
            return locs.subList(0, max);
        }
        return locs;
    }

    public static boolean isNextToAir(CalculationContext ctx, BlockPos pos) {
        int radius = Baritone.settings().allowOnlyExposedOresDistance.value;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius
                            && MovementHelper.isTransparent(ctx.getBlock(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {
        BlockState state = ctx.bsi.get0(pos);
        if (MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), state, true) >= COST_INF) {
            return false;
        }
        if (MovementHelper.avoidBreaking(ctx.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
            return false;
        }

        // bedrock above and below makes it implausible, otherwise we're good
        return !(ctx.bsi.get0(pos.above()).getBlock() == Blocks.BEDROCK && ctx.bsi.get0(pos.below()).getBlock() == Blocks.BEDROCK);
    }

    @Override
    public void mineByName(int quantity, String... blocks) {
        mine(quantity, new BlockOptionalMetaLookup(blocks));
    }

    @Override
    public void mine(int quantity, BlockOptionalMetaLookup filter) {
        this.filter = filter;
        if (this.filterFilter() == null) {
            this.filter = null;
        }
        this.desiredQuantity = quantity;
        this.knownOreLocations = new ArrayList<>();
        this.blacklist = new ArrayList<>();
        this.branchPoint = null;
        this.branchPointRunaway = null;
        this.anticipatedDrops = new HashMap<>();
        this.currentTunnelTarget = null;
        this.pillarFailCount = 0;
        this.hasReachedTargetY = false;
        Baritone.settings().noPillar.value = false;
        if (filter != null) {
            rescan(new ArrayList<>(), new CalculationContext(baritone));
        }
    }

    private BlockOptionalMetaLookup filterFilter() {
        if (this.filter == null) {
            return null;
        }
        if (!Baritone.settings().allowBreak.value) {
            BlockOptionalMetaLookup f = new BlockOptionalMetaLookup(this.filter.blocks()
                    .stream()
                    .filter(e -> Baritone.settings().allowBreakAnyway.value.contains(e.getBlock()))
                    .toArray(BlockOptionalMeta[]::new));
            if (f.blocks().isEmpty()) {
                logDirect("Unable to mine when allowBreak is false and target block is not in allowBreakAnyway!");
                return null;
            }
            return f;
        }
        return filter;
    }
}
