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
import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private volatile List<BlockPos> knownOreLocations = new CopyOnWriteArrayList<>();
    private final Set<BlockPos> blacklist = ConcurrentHashMap.newKeySet(); // inaccessible
    private final Set<BlockPos> oreMemory = ConcurrentHashMap.newKeySet(); // persistent ore memory across unloaded chunks
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
    private BlockPos lastPlacedBlockPos = null;
    private long lastPlacedBlockTime = 0;
    private boolean placedThisCycle = false;
    private BlockPos lastBrokenBlockPos = null;
    private long lastBrokenBlockTime = 0;
    private int placeBreakOscillationCount = 0;
    private boolean lastCalcFailed = false;
    private BlockPos activeMiningBlock = null;
    private int activeMiningTicks = 0;
    private BlockPos lockedTargetOre = null;
    private static final int RECENT_POS_BUFFER_SIZE = 120;
    private final BetterBlockPos[] recentPositions = new BetterBlockPos[RECENT_POS_BUFFER_SIZE];
    private int recentPosIndex = 0;
    private int recentPosCount = 0;
    private BetterBlockPos lastAntiStuckPos = null;

    private enum ShulkerStorageState {
        IDLE,
        SWAP_TO_HOTBAR,
        SELECT_SLOT,
        PLACE_BOX,
        WAIT_FOR_BLOCK,
        OPEN_BOX,
        WAIT_FOR_CONTAINER,
        TRANSFER_ITEMS,
        CLOSE_CONTAINER,
        WAIT_FOR_CLOSE,
        MINE_BOX,
        WAIT_FOR_PICKUP
    }

    private ShulkerStorageState shulkerState = ShulkerStorageState.IDLE;
    private BlockPos shulkerPlacedPos = null;
    private int shulkerStateTicks = 0;
    private int shulkerHotbarSlot = 1;
    private int shulkerTransferCooldown = 0;
    private int shulkerConsecutiveNoTransfer = 0;
    private int shulkerBoxCountBefore = 0;
    private long lastShulkerFullWarningTime = 0;

    public MineProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return filter != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        this.lastCalcFailed = calcFailed;
        int targetY = Baritone.settings().legitMineYLevel.value;
        if (ctx.playerFeet().y <= targetY) {
            hasReachedTargetY = true;
        }
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
            boolean isMining = activeMiningBlock != null
                    || baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)
                    || ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).isHittingBlock();
            if (!isMining) {
                if (!knownOreLocations.isEmpty() && Baritone.settings().blacklistClosestOnFailure.value) {
                    logDirect("Unable to find any path to " + filter + ", retrying...");
                    knownOreLocations.stream().min(Comparator.comparingDouble(ctx.playerFeet()::distSqr)).ifPresent(pos -> {
                        blacklist.add(pos);
                        oreMemory.remove(pos);
                    });
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

        // Dọn rác balo trước: vứt sạch đồ rác (chỉ giữ Shulker, 1 stack block, Totem, Thức ăn & Cúp)
        if (!pendingDropSlots.isEmpty() || tickCount % 20 == 0) {
            cleanInventoryIfFull();
        }

        if (Baritone.settings().autoShulkerStorage.value || shulkerState != ShulkerStorageState.IDLE) {
            PathingCommand shulkerCmd = handleShulkerStorage(isSafeToCancel);
            if (shulkerCmd != null) {
                return shulkerCmd;
            }
        }
        int mineGoalUpdateInterval = Baritone.settings().mineGoalUpdateInterval.value;
        addNearbyQuick();
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
        // === CƠ CHẾ PHẢI ĐÀO XONG MỚI TIẾP TỤC (FINISH MINING BEFORE CONTINUING) ===
        // 1. Nếu đang đào dở một block (activeMiningBlock), TIẾP TỤC ĐÀO ĐẾN CÙNG:
        if (activeMiningBlock != null) {
            BlockState state = ctx.world().getBlockState(activeMiningBlock);
            if (!state.isAir() && (filter == null || filter.has(state))) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, activeMiningBlock);
                if (rot.isPresent()) {
                    activeMiningTicks++;
                    if (activeMiningTicks > 80) {
                        logDirect("§c[Mine] Block tại " + activeMiningBlock.toShortString() + " không vỡ sau 80 ticks (claim/phantom)! Blacklisting...");
                        blacklist.add(activeMiningBlock);
                        oreMemory.remove(activeMiningBlock);
                        if (knownOreLocations != null) {
                            knownOreLocations.remove(activeMiningBlock);
                        }
                        if (lockedTargetOre != null && lockedTargetOre.equals(activeMiningBlock)) {
                            lockedTargetOre = null;
                        }
                        activeMiningBlock = null;
                        activeMiningTicks = 0;
                        forceReroute = true;
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    } else {
                        baritone.getPathingBehavior().cancelSegmentIfSafe();
                        baritone.getInputOverrideHandler().clearAllKeys();
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        MovementHelper.switchToBestToolFor(ctx, state);
                        if (ctx.isLookingAt(activeMiningBlock) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                }
            }
            if (activeMiningBlock != null) {
                // Đã đào vỡ block hoặc không còn với tới được
                blacklist.remove(activeMiningBlock);
                oreMemory.remove(activeMiningBlock);
                if (knownOreLocations != null) {
                    knownOreLocations.remove(activeMiningBlock);
                }
                if (lockedTargetOre != null && lockedTargetOre.equals(activeMiningBlock)) {
                    lockedTargetOre = null;
                }
                activeMiningBlock = null;
                activeMiningTicks = 0;
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            }
        }

        // 2. Kiểm tra nếu client game đang trực tiếp đập block mục tiêu:
        BlockPos destroyingPos = ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).getCurrentBlock();
        if (destroyingPos != null && ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).isHittingBlock()) {
            BlockState state = ctx.world().getBlockState(destroyingPos);
            if (!state.isAir() && filter != null && filter.has(state)) {
                if (activeMiningBlock == null || !activeMiningBlock.equals(destroyingPos)) {
                    activeMiningBlock = destroyingPos;
                    activeMiningTicks = 0;
                }
                Optional<Rotation> rot = RotationUtils.reachable(ctx, destroyingPos);
                if (rot.isPresent()) {
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    baritone.getInputOverrideHandler().clearAllKeys();
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    MovementHelper.switchToBestToolFor(ctx, state);
                    if (ctx.isLookingAt(destroyingPos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }
        }

        // 3. Quét tìm BẤT KỲ quặng mục tiêu nào đang nằm trong tầm với (Reachable) quanh người:
        // Đào trực tiếp ngay tại chỗ mà không cần A* di chuyển hay huỷ đường!
        boolean canDirectMine = ctx.player().onGround() || ctx.player().isInWater() || ctx.player().getDeltaMovement().y > -0.5;
        if (canDirectMine) {
            Optional<BlockPos> reachableOre = curr.stream()
                    .filter(pos -> ctx.playerFeet().distSqr(pos) <= 25)
                    .filter(pos -> !ctx.world().getBlockState(pos).isAir())
                    .filter(pos -> {
                        BlockState s = ctx.world().getBlockState(pos);
                        return filter.has(s) && !MovementHelper.avoidBreaking(baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), s);
                    })
                    .filter(pos -> RotationUtils.reachable(ctx, pos).isPresent())
                    .min(Comparator.comparingDouble(ctx.playerFeet().above()::distSqr));

            if (reachableOre.isPresent()) {
                BlockPos pos = reachableOre.get();
                if (activeMiningBlock == null || !activeMiningBlock.equals(pos)) {
                    activeMiningBlock = pos;
                    activeMiningTicks = 0;
                }
                BlockState state = ctx.world().getBlockState(pos);
                Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
                if (rot.isPresent()) {
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    baritone.getInputOverrideHandler().clearAllKeys();
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    MovementHelper.switchToBestToolFor(ctx, state);
                    if (ctx.isLookingAt(pos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
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
            if (knownOreLocations.contains(pos) || oreMemory.contains(pos)) {
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
        activeMiningBlock = null;
        activeMiningTicks = 0;
        lockedTargetOre = null;
        recentPosIndex = 0;
        recentPosCount = 0;
        lastAntiStuckPos = null;
        if (shulkerState != ShulkerStorageState.IDLE) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            shulkerState = ShulkerStorageState.IDLE;
            shulkerPlacedPos = null;
            shulkerStateTicks = 0;
            shulkerBoxCountBefore = 0;
        }
        mine(0, (BlockOptionalMetaLookup) null);
    }

    @Override
    public String displayName0() {
        return "Mine " + filter;
    }

    private void cleanOreMemory(CalculationContext context, BlockOptionalMetaLookup filter) {
        if (filter == null || oreMemory.isEmpty() || ctx.world() == null) {
            return;
        }
        // 1. Loại bỏ các vị trí đã bị blacklist
        oreMemory.removeIf(blacklist::contains);

        // 2. Kiểm tra các vị trí trong chunk ĐANG LOAD mà không còn là quặng (đã đào) hoặc không thể đào (bedrock)
        oreMemory.removeIf(pos -> {
            net.minecraft.world.level.chunk.LevelChunk chunk = ctx.world().getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
            if (chunk != null && !chunk.isEmpty()) {
                BlockState state = chunk.getBlockState(pos);
                if (!filter.has(state)) {
                    return true; // Đã đào xong / không còn là quặng mục tiêu
                }
                if (!MineProcess.plausibleToBreak(context, pos)) {
                    blacklist.add(pos); // Không thể đào (bị bao bọc bởi bedrock) -> blacklist
                    return true;
                }
            }
            // Chunk KHÔNG LOAD hoặc CHƯA ĐẦY ĐỦ DATA: Tuyệt đối giữ nguyên trong oreMemory, không được xóa!
            return false;
        });
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
                if (!blacklist.contains(nearPos)) {
                    oreMemory.add(nearPos);
                    if (!knownOreLocations.contains(nearPos)) {
                        knownOreLocations.add(nearPos);
                    }
                }
            }
        }

        boolean legit = Baritone.settings().legitMine.value;
        int targetY = Baritone.settings().legitMineYLevel.value;
        if (ctx.playerFeet().y <= targetY) {
            hasReachedTargetY = true;
        }
        List<BlockPos> locs = knownOreLocations;

        // Nếu knownOreLocations rỗng nhưng trong oreMemory vẫn còn quặng đã lưu (ở chunk xa đã unload):
        // Lập tức nạp lại từ oreMemory để tiếp tục đào!
        if (locs.isEmpty() && !oreMemory.isEmpty()) {
            CalculationContext context = new CalculationContext(baritone);
            cleanOreMemory(context, filter);
            List<BlockPos> allCandidates = new ArrayList<>(oreMemory);
            allCandidates.addAll(droppedItemsScan());
            locs = prune(context, allCandidates, filter, Baritone.settings().mineMaxOreLocationsCount.value, blacklist, droppedItemsScan());
            if (!locs.isEmpty()) {
                knownOreLocations = new CopyOnWriteArrayList<>(locs);
                logDirect("§a[OreMemory] Chuyển hướng tới " + locs.size() + " quặng đã lưu trong bộ nhớ (cách " + (int)Math.sqrt(ctx.playerFeet().distSqr(locs.get(0))) + "m)!");
            }
        }
        if (!locs.isEmpty()) {
            CalculationContext context = new CalculationContext(baritone);
            List<BlockPos> locs2 = prune(context, new ArrayList<>(locs), filter, Baritone.settings().mineMaxOreLocationsCount.value, blacklist, droppedItemsScan());
            if (!locs2.isEmpty()) {
                currentTunnelTarget = null;
                // TARGET LOCK / HYSTERESIS:
                // Tránh GoalComposite bị dao động qua lại giữa cụm gần và cụm xa khi bot di chuyển ở ngưỡng ranh giới (8 block).
                // Duy trì lockedTargetOre cố định cho đến khi quặng này bị đào vỡ hoặc bị blacklist.
                boolean lockedValid = lockedTargetOre != null
                        && !blacklist.contains(lockedTargetOre)
                        && locs2.contains(lockedTargetOre)
                        && !(BlockStateInterface.get(ctx, lockedTargetOre).getBlock() instanceof AirBlock);

                if (forceReroute) {
                    lockedValid = false;
                }

                if (!lockedValid) {
                    lockedTargetOre = null;
                }

                // Cơ chế Hysteresis thông minh: Nếu có quặng ngay sát người (<= 4 block, distSqr <= 16)
                // trong khi lockedTargetOre ở xa hơn (> 4 block), lập tức đổi lockedTargetOre sang quặng sát người!
                if (lockedTargetOre != null && ctx.playerFeet().distSqr(lockedTargetOre) > 16) {
                    Optional<BlockPos> veryClose = locs2.stream()
                            .filter(pos -> ctx.playerFeet().distSqr(pos) <= 16)
                            .min(Comparator.comparingDouble(ctx.playerFeet()::distSqr));
                    if (veryClose.isPresent()) {
                        lockedTargetOre = veryClose.get();
                    }
                }

                if (lockedTargetOre == null) {
                    // Ưu tiên quặng gần (trong vòng 8 block)
                    List<BlockPos> nearbyOres = locs2.stream()
                            .filter(pos -> ctx.playerFeet().distSqr(pos) <= 64)
                            .collect(Collectors.toList());
                    if (!nearbyOres.isEmpty()) {
                        lockedTargetOre = nearbyOres.stream()
                                .min(Comparator.comparingDouble(ctx.playerFeet()::distSqr))
                                .orElse(null);
                    } else {
                        lockedTargetOre = locs2.stream()
                                .min(Comparator.comparingDouble(ctx.playerFeet()::distSqr))
                                .orElse(null);
                    }
                }

                List<BlockPos> targetOres;
                if (lockedTargetOre != null) {
                    // Gom toàn bộ quặng trong cùng cụm (bán kính 10 block quanh lockedTargetOre) để GoalComposite bao trùm cả vỉa
                    final BlockPos target = lockedTargetOre;
                    targetOres = locs2.stream()
                            .filter(pos -> pos.equals(target) || pos.distSqr(target) <= 100)
                            .collect(Collectors.toList());
                    if (targetOres.isEmpty()) {
                        targetOres = Collections.singletonList(target);
                    }
                } else {
                    targetOres = locs2;
                }

                Goal goal = new GoalComposite(targetOres.stream().map(loc -> coalesce(loc, locs2, context)).toArray(Goal[]::new));
                knownOreLocations = new CopyOnWriteArrayList<>(locs2);
                boolean isPathing = baritone.getPathingBehavior().isPathing();
                boolean fr = forceReroute;
                forceReroute = false;
                if (fr) {
                    return new PathingCommand(goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                // Nếu đang di chuyển trên đường thì giữ REVALIDATE để không bị softCancel khựng lại
                return new PathingCommand(goal, (legit && !isPathing) ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            } else {
                lockedTargetOre = null;
            }
        } else {
            lockedTargetOre = null;
        }

        // we don't know any ore locations at the moment
        if (!legit && !Baritone.settings().exploreForBlocks.value) {
            return null;
        }
        
        // KHI KHÔNG CÓ QUẶNG TRONG TẦM QUÉT:
        int currentY = ctx.playerFeet().y;

        // Đánh dấu đã chạm tới độ sâu targetY (hoặc xuất phát ngay tại tầng đào)
        if (currentY <= targetY) {
            hasReachedTargetY = true;
        }

        // Kiểm tra xem AntiStuck có yêu cầu đào ngược lên không (thoát bedrock / chướng ngại vật):
        if (tunnelOrigin != null && tunnelOrigin.getY() > currentY) {
            if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
                net.minecraft.core.Direction dir = ctx.player().getDirection();
                tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
            }
            int rise = 1;
            BlockPos escapePos = new BlockPos(
                    ctx.playerFeet().x + tunnelDirection.getStepX() * rise,
                    currentY + rise,
                    ctx.playerFeet().z + tunnelDirection.getStepZ() * rise
            );
            logDirect("§b[AntiStuck] Đang đào ngược lên Y=" + (currentY + rise) + " để thoát kẹt...");
            boolean fr = forceReroute;
            forceReroute = false;
            return new PathingCommand(new GoalTwoBlocks(escapePos), fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        } else if (tunnelOrigin != null && currentY >= tunnelOrigin.getY()) {
            tunnelOrigin = null;
        }

        // KHI NGƯỜI CHƠI CHƯA XUỐNG TỚI TẦNG TARGET Y (ví dụ Y=-58 từ mặt đất):
        // CHỈ kích hoạt đào thẳng đứng / đào dốc khi:
        // 1. Chưa từng chạm tới tầng đào (hasReachedTargetY == false) VÀ currentY > targetY
        // 2. HOẶC người chơi bị văng lên quá xa khỏi tầng đào (currentY > targetY + 3)
        // Tuyệt đối KHÔNG kích hoạt khi đang đào hầm ngang mà chỉ bước lên 1-2 block chướng ngại vật!
        if (!hasReachedTargetY || currentY > targetY + 3) {
            boolean fr = forceReroute;
            forceReroute = false;

            // KIỂM TRA ƯU TIÊN SỐ 1: DÙNG XÔ NƯỚC (WATER BUCKET) ĐỂ TỤT XUỐNG THAY VÌ ĐÀO XUỐNG
            int waterSlot = ctx.player().getInventory().findSlotMatchingItem(new ItemStack(Items.WATER_BUCKET));
            boolean hasWaterBucket = (waterSlot != -1 || ctx.player().getOffhandItem().is(Items.WATER_BUCKET))
                    && ctx.world().dimension() != net.minecraft.world.level.Level.NETHER
                    && Baritone.settings().allowWaterBucketFall.value;

            if (hasWaterBucket && Baritone.settings().preferWaterBucketOverDigging.value) {
                // Tự động chuyển xô nước lên hotbar nếu đang ở trong balo (dùng slot 8 hoặc 7 để không ghi đè slot 0 của cúp)
                if (waterSlot >= 9) {
                    ((Baritone) baritone).getInventoryBehavior().attemptToPutOnHotbar(waterSlot, s -> s == 8 || s == 7);
                }

                // Quét tìm hố sâu / vách núi / hang động mở có độ tụt lớn gần đây để nhảy đáp nước (quét bán kính rộng 32 block)
                Optional<BlockPos> opening = findNearbyDescentOpening(32, 3);
                if (opening.isPresent()) {
                    BlockPos dropPos = opening.get();
                    int dropAmount = currentY - dropPos.getY();
                    logDirect("§a[WaterDescent] Phát hiện hố/hang mở tụt " + dropAmount + " block! Ưu tiên nhảy đáp nước (MLG Bucket) thay vì đào xuống.");
                    return new PathingCommand(new GoalTwoBlocks(dropPos), fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                }
            }

            // CHỈ KHI KHÔNG TÌM ĐƯỢC HỐ/HANG MỞ MỚI TIẾN HÀNH ĐÀO XUỐNG:
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
        // Quét tìm quặng mới trong các chunk đang load của thế giới
        List<BlockPos> freshlyScanned = searchWorld(context, filter, Baritone.settings().mineMaxOreLocationsCount.value, Collections.emptyList(), new ArrayList<>(blacklist), dropped);
        oreMemory.addAll(freshlyScanned);
        cleanOreMemory(context, filter);

        List<BlockPos> allCandidates = new ArrayList<>(oreMemory);
        allCandidates.addAll(dropped);
        List<BlockPos> locs = prune(context, allCandidates, filter, Baritone.settings().mineMaxOreLocationsCount.value, blacklist, dropped);

        if (locs.isEmpty() && !Baritone.settings().exploreForBlocks.value) {
            logDirect("No locations for " + filter + " known, cancelling");
            if (Baritone.settings().notificationOnMineFail.value) {
                logNotification("No locations for " + filter + " known, cancelling", true);
            }
            cancel();
            return;
        }
        knownOreLocations = new CopyOnWriteArrayList<>(locs);
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

    public static boolean isBuildingBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) {
            return false;
        }
        Block block = bi.getBlock();
        if (block instanceof ShulkerBoxBlock || block instanceof TrapDoorBlock) {
            return false;
        }
        return block == Blocks.COBBLESTONE
                || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.DEEPSLATE
                || block == Blocks.STONE
                || block == Blocks.DIRT
                || block == Blocks.TUFF
                || block == Blocks.ANDESITE
                || block == Blocks.DIORITE
                || block == Blocks.GRANITE
                || block == Blocks.NETHERRACK
                || block == Blocks.BASALT
                || block == Blocks.BLACKSTONE
                || block == Blocks.CALCITE
                || block == Blocks.SANDSTONE
                || block == Blocks.END_STONE;
    }

    private boolean isValuableOreOrTarget(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.DIAMOND
                || item == Items.EMERALD
                || item == Items.ANCIENT_DEBRIS
                || item == Items.NETHERITE_INGOT
                || item == Items.NETHERITE_SCRAP
                || item == Items.LAPIS_LAZULI
                || item == Items.REDSTONE
                || item == Items.GOLD_INGOT
                || item == Items.IRON_INGOT
                || item == Items.RAW_GOLD
                || item == Items.RAW_IRON
                || item == Items.AMETHYST_SHARD) {
            return true;
        }
        if (filter != null && filter.has(stack)) {
            return true;
        }
        if (item instanceof BlockItem bi) {
            Block block = bi.getBlock();
            if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                    || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                    || block == Blocks.ANCIENT_DEBRIS
                    || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
                    || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                    || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                    || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
                return true;
            }
        }
        return false;
    }

    private boolean isProtectedFromDrop(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (isShulkerBox(stack)) return true;
        if (stack.is(Items.TOTEM_OF_UNDYING)) return true;
        if (isGoodFood(stack) || stack.has(DataComponents.FOOD)) return true;
        if (isToolOrEssential(stack)) return true;
        if (isValuableOreOrTarget(stack)) return true;
        return false;
    }

    private void cleanInventoryIfFull() {
        if (ctx.player() == null || ctx.player().containerMenu != ctx.player().inventoryMenu) {
            return;
        }

        if (!Baritone.settings().autoDrop.value) {
            return;
        }

        // Nếu đang có hàng đợi vứt rác → vứt 1 stack mỗi 4 tick (0.2s) để tránh kick packet
        if (!pendingDropSlots.isEmpty()) {
            if (dropCooldown > 0) {
                dropCooldown--;
                return;
            }
            int slotIndex = pendingDropSlots.remove(0);
            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            if (slotIndex >= 0 && slotIndex < inv.size()) {
                ItemStack stack = inv.get(slotIndex);
                // Kiểm tra an toàn trước khi ném: KHÔNG BAO GIỜ vứt Shulker Box, Totem, Food, Tools, Ores
                if (!stack.isEmpty() && !isProtectedFromDrop(stack)) {
                    // Trong InventoryMenu:
                    // Hotbar (slotIndex 0-8) -> windowSlot 36-44
                    // Balo chính (slotIndex 9-35) -> windowSlot 9-35
                    int windowSlot = (slotIndex < 9) ? (slotIndex + 36) : slotIndex;
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 1, ClickType.THROW, ctx.player());
                }
            }
            dropCooldown = 4;
            if (pendingDropSlots.isEmpty()) {
                logDirect("§a[AutoDrop] Đã dọn sạch toàn bộ rác trong balo!");
            }
            return;
        }

        // Đếm số ô trống trong balo
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
                if (stack.isEmpty()) continue;

                // 1. Shulker Box -> Luôn giữ (Yêu cầu người dùng: "trừ shuker box")
                if (isShulkerBox(stack)) continue;

                // 2. Totem of Undying -> Luôn giữ (Yêu cầu người dùng: "và cả totem")
                if (stack.is(Items.TOTEM_OF_UNDYING)) continue;

                // 3. Thức ăn -> Luôn giữ (Yêu cầu người dùng: "+ thức ăn")
                if (isGoodFood(stack) || stack.has(DataComponents.FOOD)) continue;

                // 4. Công cụ, vũ khí, giáp, xô nước, cửa sập -> Luôn giữ
                if (isToolOrEssential(stack)) continue;

                // 5. Quặng quý & Khoáng sản mục tiêu đào -> Luôn giữ
                if (isValuableOreOrTarget(stack)) continue;

                // 6. Block xây dựng -> Giữ đúng 1 stack (tối đa 64 block) để bắc cầu/kê chân (Yêu cầu người dùng: "+ 1 stack block")
                if (isBuildingBlock(stack)) {
                    if (keptBuildingBlocks < 64) {
                        keptBuildingBlocks += stack.getCount();
                        continue;
                    }
                }

                // CÒN LẠI VỨT ALL: nạp slot vào hàng đợi vứt rác
                pendingDropSlots.add(i);
            }

            if (!pendingDropSlots.isEmpty()) {
                logDirect("§e[AutoDrop] Balo gần đầy (" + emptySlots + " ô trống)! Đang vứt " + pendingDropSlots.size() + " stack rác (giữ Shulker, 1 stack block, Totem, Thức ăn & Cúp, vứt all còn lại)...");
                dropCooldown = 0; // Vứt stack đầu tiên ngay
            }
        }
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    public static int getShulkerOccupiedSlots(ItemStack stack) {
        if (!isShulkerBox(stack)) return 999;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return 0; // null component = Shulker Box hoàn toàn trống!
        }
        int count = 0;
        for (ItemStack item : contents.nonEmptyItems()) {
            if (!item.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int countShulkerBoxesInInventory() {
        if (ctx.player() == null) return 0;
        int count = 0;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.get(i);
            if (isShulkerBox(s)) {
                count += s.getCount();
            }
        }
        ItemStack offhand = ctx.player().getOffhandItem();
        if (isShulkerBox(offhand)) {
            count += offhand.getCount();
        }
        return count;
    }

    private int findBestShulkerBoxSlot() {
        if (ctx.player() == null) return -1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int bestSlot = -1;
        int minOccupied = 27; // Chỉ nhận Shulker còn ô trống (< 27 ô)

        // Thứ tự ưu tiên slot: hotbar slot 1-8 trước, rồi balo chính 9-35, cuối cùng slot 0
        int[] slotOrder = new int[36];
        int idx = 0;
        for (int i = 1; i < 9; i++) slotOrder[idx++] = i;
        for (int i = 9; i < 36; i++) slotOrder[idx++] = i;
        slotOrder[idx++] = 0;

        for (int slot : slotOrder) {
            ItemStack stack = inv.get(slot);
            if (!isShulkerBox(stack)) continue;

            int occupied = getShulkerOccupiedSlots(stack);
            // ƯU TIÊN TUYỆT ĐỐI: Shulker trống hoàn toàn (0 ô chứa đồ)!
            if (occupied == 0) {
                return slot;
            }

            // Ưu tiên tiếp theo: Shulker còn nhiều chỗ trống nhất (occupied nhỏ nhất < 27)
            if (occupied < minOccupied) {
                minOccupied = occupied;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private ItemEntity findNearbyDroppedShulker() {
        if (ctx.world() == null || ctx.player() == null) return null;
        ItemEntity best = null;
        double bestDist = 36.0; // Bán kính tối đa 6 block
        for (Entity entity : ((ClientLevel) ctx.world()).entitiesForRendering()) {
            if (entity instanceof ItemEntity ei && ei.isAlive()) {
                if (isShulkerBox(ei.getItem())) {
                    double dist = entity.distanceToSqr(ctx.player());
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = ei;
                    }
                }
            }
        }
        return best;
    }

    private boolean isToolOrEssential(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.MAX_DAMAGE) || stack.isDamageableItem()) {
            return true;
        }
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
            return true;
        }
        if (isGoodFood(stack)) {
            return true;
        }
        Item item = stack.getItem();
        if (item == Items.WATER_BUCKET
                || item == Items.TOTEM_OF_UNDYING
                || item == Items.SHIELD
                || stack.is(ItemTags.ARMOR_ENCHANTABLE)
                || (item instanceof BlockItem bi && bi.getBlock() instanceof TrapDoorBlock)
                || isShulkerBox(stack)) {
            return true;
        }
        return false;
    }

    private static class ShulkerPlacementTarget {
        final BlockPos placePos;
        final BlockPos againstPos;
        final net.minecraft.core.Direction face;

        ShulkerPlacementTarget(BlockPos placePos, BlockPos againstPos, net.minecraft.core.Direction face) {
            this.placePos = new BlockPos(placePos.getX(), placePos.getY(), placePos.getZ());
            this.againstPos = new BlockPos(againstPos.getX(), againstPos.getY(), againstPos.getZ());
            this.face = face;
        }
    }

    private Optional<ShulkerPlacementTarget> findShulkerPlacePos() {
        if (ctx.player() == null || ctx.world() == null) return Optional.empty();
        BetterBlockPos feet = ctx.playerFeet();
        net.minecraft.core.Direction playerFacing = ctx.player().getDirection();
        List<net.minecraft.core.Direction> dirs = new ArrayList<>();
        if (playerFacing.getAxis().isHorizontal()) {
            dirs.add(playerFacing);
        }
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (d.getAxis().isHorizontal() && !dirs.contains(d)) {
                dirs.add(d);
            }
        }

        AABB playerBox = ctx.player().getBoundingBox();

        for (net.minecraft.core.Direction dir : dirs) {
            BlockPos target = new BlockPos(feet.x + dir.getStepX(), feet.y + dir.getStepY(), feet.z + dir.getStepZ());
            BlockState targetState = ctx.world().getBlockState(target);
            boolean targetPassable = targetState.isAir() || targetState.canBeReplaced();
            if (!targetPassable) continue;

            // Block phía trên target phải là Air để nắp Shulker bung lên được
            BlockPos above = new BlockPos(target.getX(), target.getY() + 1, target.getZ());
            BlockState aboveState = ctx.world().getBlockState(above);
            if (!aboveState.isAir()) continue;

            // Block dưới target phải là solid để làm sàn đặt
            BlockPos floor = new BlockPos(target.getX(), target.getY() - 1, target.getZ());
            BlockState floorState = ctx.world().getBlockState(floor);
            if (floorState.isAir() || !floorState.isSolid()) continue;

            // Không được đè lên người chơi
            AABB targetBox = new AABB(target);
            if (targetBox.intersects(playerBox)) continue;

            return Optional.of(new ShulkerPlacementTarget(target, floor, net.minecraft.core.Direction.UP));
        }

        return Optional.empty();
    }

    private PathingCommand handleShulkerStorage(boolean isSafeToCancel) {
        if (ctx.player() == null) return null;

        // Kích hoạt khi đang IDLE
        if (shulkerState == ShulkerStorageState.IDLE) {
            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            int emptySlots = 0;
            for (int i = 0; i < 36; i++) {
                if (inv.get(i).isEmpty()) emptySlots++;
            }
            // Khi balo còn <= 4 ô trống và đã dọn sạch rác rơi (không còn rác trong hàng đợi):
            if (emptySlots <= 4 && pendingDropSlots.isEmpty()) {
                int shulkerSlot = findBestShulkerBoxSlot();
                if (shulkerSlot != -1) {
                    shulkerBoxCountBefore = countShulkerBoxesInInventory();
                    int occupied = getShulkerOccupiedSlots(inv.get(shulkerSlot));
                    String slotDesc = (occupied == 0) ? "trống 100%" : (occupied + "/27 ô đã dùng");
                    logDirect("§a[AutoShulker] Balo gần đầy (" + emptySlots + " ô trống)! Chọn Shulker Box (" + slotDesc + ") tại slot " + shulkerSlot + " để cất đồ...");
                    shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
                    shulkerStateTicks = 0;
                    shulkerConsecutiveNoTransfer = 0;
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    baritone.getInputOverrideHandler().clearAllKeys();
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                } else {
                    // Nếu người chơi có Shulker nhưng tất cả đều đầy
                    boolean hasAnyShulker = false;
                    for (int i = 0; i < 36; i++) {
                        if (isShulkerBox(inv.get(i))) {
                            hasAnyShulker = true;
                            break;
                        }
                    }
                    if (hasAnyShulker && (System.currentTimeMillis() - lastShulkerFullWarningTime > 20000)) {
                        logDirect("§c[AutoShulker] Toàn bộ Shulker Box trong balo đều đã đầy (27/27 ô)! Không thể cất thêm quặng.");
                        lastShulkerFullWarningTime = System.currentTimeMillis();
                    }
                }
            }
            return null;
        }

        // Đang trong quy trình Shulker: tạm dừng di chuyển để thao tác
        baritone.getPathingBehavior().cancelSegmentIfSafe();
        baritone.getInputOverrideHandler().clearAllKeys();
        shulkerStateTicks++;

        switch (shulkerState) {
            case SWAP_TO_HOTBAR -> {
                int slot = findBestShulkerBoxSlot();
                if (slot == -1) {
                    logDirect("§e[AutoShulker] Không tìm thấy Shulker Box còn chỗ trống trong balo! Hủy quy trình.");
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerBoxCountBefore = 0;
                    return null;
                }
                if (shulkerBoxCountBefore <= 0) {
                    shulkerBoxCountBefore = countShulkerBoxesInInventory();
                }
                if (slot < 9) {
                    shulkerHotbarSlot = slot;
                    shulkerState = ShulkerStorageState.SELECT_SLOT;
                    shulkerStateTicks = 0;
                } else {
                    // Swap vào hotbar slot 1 (slot 0 dành cho cúp đào)
                    shulkerHotbarSlot = 1;
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, slot, 1, ClickType.SWAP, ctx.player());
                    shulkerState = ShulkerStorageState.SELECT_SLOT;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SELECT_SLOT -> {
                ctx.player().getInventory().setSelectedSlot(shulkerHotbarSlot);
                ctx.playerController().syncHeldItem();
                Optional<ShulkerPlacementTarget> targetOpt = findShulkerPlacePos();
                if (targetOpt.isEmpty()) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShulker] Không tìm thấy vị trí thích hợp để đặt Shulker Box! Hủy quy trình...");
                        shulkerState = ShulkerStorageState.IDLE;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                shulkerPlacedPos = new BlockPos(targetOpt.get().placePos.getX(), targetOpt.get().placePos.getY(), targetOpt.get().placePos.getZ());
                shulkerState = ShulkerStorageState.PLACE_BOX;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case PLACE_BOX -> {
                Optional<ShulkerPlacementTarget> targetOpt = findShulkerPlacePos();
                if (targetOpt.isEmpty()) {
                    shulkerState = ShulkerStorageState.IDLE;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                ShulkerPlacementTarget pt = targetOpt.get();
                shulkerPlacedPos = new BlockPos(pt.placePos.getX(), pt.placePos.getY(), pt.placePos.getZ());
                BlockPos againstPure = new BlockPos(pt.againstPos.getX(), pt.againstPos.getY(), pt.againstPos.getZ());
                Vec3 hitVec = new Vec3(againstPure.getX() + 0.5, againstPure.getY() + 1.0, againstPure.getZ() + 0.5);
                BlockHitResult bhr = new BlockHitResult(hitVec, pt.face, againstPure, false);
                Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), hitVec, ctx.playerRotations());
                baritone.getLookBehavior().updateTarget(rot, true);
                ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                shulkerState = ShulkerStorageState.WAIT_FOR_BLOCK;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case WAIT_FOR_BLOCK -> {
                if (shulkerPlacedPos != null && ctx.world().getBlockState(shulkerPlacedPos).getBlock() instanceof ShulkerBoxBlock) {
                    shulkerState = ShulkerStorageState.OPEN_BOX;
                    shulkerStateTicks = 0;
                } else if (shulkerStateTicks > 15) {
                    logDirect("§c[AutoShulker] Không thể đặt Shulker Box (server từ chối hoặc lag)! Hủy quy trình...");
                    shulkerState = ShulkerStorageState.IDLE;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case OPEN_BOX -> {
                if (shulkerPlacedPos == null) {
                    shulkerState = ShulkerStorageState.IDLE;
                    return null;
                }
                BlockPos openPos = new BlockPos(shulkerPlacedPos.getX(), shulkerPlacedPos.getY(), shulkerPlacedPos.getZ());
                Vec3 center = new Vec3(openPos.getX() + 0.5, openPos.getY() + 0.5, openPos.getZ() + 0.5);
                BlockHitResult bhr = new BlockHitResult(center, net.minecraft.core.Direction.UP, openPos, false);
                Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), center, ctx.playerRotations());
                baritone.getLookBehavior().updateTarget(rot, true);
                ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                shulkerState = ShulkerStorageState.WAIT_FOR_CONTAINER;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case WAIT_FOR_CONTAINER -> {
                if (ctx.player().containerMenu instanceof ShulkerBoxMenu || (ctx.player().containerMenu != ctx.player().inventoryMenu && ctx.player().containerMenu.slots.size() >= 63)) {
                    shulkerState = ShulkerStorageState.TRANSFER_ITEMS;
                    shulkerStateTicks = 0;
                    shulkerTransferCooldown = 0;
                    shulkerConsecutiveNoTransfer = 0;
                } else if (shulkerStateTicks > 25) {
                    logDirect("§c[AutoShulker] Không thể mở Shulker Box! Đang đào thu hồi lại...");
                    shulkerState = ShulkerStorageState.MINE_BOX;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case TRANSFER_ITEMS -> {
                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    shulkerState = ShulkerStorageState.MINE_BOX;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerTransferCooldown > 0) {
                    shulkerTransferCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int containerId = ctx.player().containerMenu.containerId;
                int keptBuildingBlocks = 0;
                int transferSlot = -1;

                for (int slotId = 27; slotId < 63; slotId++) {
                    ItemStack stack = ctx.player().containerMenu.getSlot(slotId).getItem();
                    if (stack.isEmpty()) continue;
                    if (isToolOrEssential(stack)) continue;

                    if (isBuildingBlock(stack)) {
                        if (keptBuildingBlocks < 64) {
                            keptBuildingBlocks += stack.getCount();
                            continue;
                        }
                    }

                    transferSlot = slotId;
                    break;
                }

                if (transferSlot != -1 && shulkerConsecutiveNoTransfer < 3) {
                    ItemStack before = ctx.player().containerMenu.getSlot(transferSlot).getItem().copy();
                    ctx.playerController().windowClick(containerId, transferSlot, 0, ClickType.QUICK_MOVE, ctx.player());
                    ItemStack after = ctx.player().containerMenu.getSlot(transferSlot).getItem();
                    if (before.getCount() == after.getCount()) {
                        shulkerConsecutiveNoTransfer++;
                    } else {
                        shulkerConsecutiveNoTransfer = 0;
                    }
                    shulkerTransferCooldown = 2;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                logDirect("§a[AutoShulker] Đã cất gọn toàn bộ quặng và vật phẩm vào Shulker Box!");
                shulkerState = ShulkerStorageState.CLOSE_CONTAINER;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case CLOSE_CONTAINER -> {
                ctx.player().closeContainer();
                shulkerState = ShulkerStorageState.WAIT_FOR_CLOSE;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case WAIT_FOR_CLOSE -> {
                if (ctx.player().containerMenu == ctx.player().inventoryMenu || shulkerStateTicks > 6) {
                    shulkerState = ShulkerStorageState.MINE_BOX;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case MINE_BOX -> {
                if (shulkerPlacedPos == null) {
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerBoxCountBefore = 0;
                    return null;
                }
                BlockState state = ctx.world().getBlockState(shulkerPlacedPos);
                if (state.isAir()) {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    if (shulkerBoxCountBefore <= 0) {
                        shulkerBoxCountBefore = countShulkerBoxesInInventory() + 1;
                    }
                    shulkerState = ShulkerStorageState.WAIT_FOR_PICKUP;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                ctx.player().getInventory().setSelectedSlot(0);
                ctx.playerController().syncHeldItem();
                MovementHelper.switchToBestToolFor(ctx, state);

                Optional<Rotation> rot = RotationUtils.reachable(ctx, shulkerPlacedPos);
                if (rot.isPresent()) {
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    if (ctx.isLookingAt(shulkerPlacedPos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    }
                }
                if (shulkerStateTicks > 140) {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    logDirect("§c[AutoShulker] Quá thời gian đào Shulker Box! Tiếp tục hành trình...");
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerBoxCountBefore = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case WAIT_FOR_PICKUP -> {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);

                int currentShulkerCount = countShulkerBoxesInInventory();
                // BẮT BUỘC: Đã nhặt được Shulker Box vào balo (tổng số lượng Shulker Box trong balo >= số lượng trước khi đặt)
                if (currentShulkerCount >= shulkerBoxCountBefore) {
                    baritone.getInputOverrideHandler().clearAllKeys();
                    logDirect("§a[AutoShulker] Đã thu hồi và nhặt Shulker Box vào balo an toàn (Tổng: " + currentShulkerCount + ")!");
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerPlacedPos = null;
                    shulkerStateTicks = 0;
                    shulkerBoxCountBefore = 0;
                    return null;
                }

                // Nếu chưa nhặt được: xác định vị trí thực tế của Shulker Box rơi trên sàn
                ItemEntity droppedItem = findNearbyDroppedShulker();
                Vec3 targetPos = null;
                if (droppedItem != null) {
                    targetPos = droppedItem.position();
                } else if (shulkerPlacedPos != null) {
                    targetPos = new Vec3(shulkerPlacedPos.getX() + 0.5, shulkerPlacedPos.getY(), shulkerPlacedPos.getZ() + 0.5);
                }

                if (targetPos != null) {
                    Vec3 playerPos = ctx.player().position();
                    double dx = targetPos.x - playerPos.x;
                    double dz = targetPos.z - playerPos.z;
                    double horizontalDistSq = dx * dx + dz * dz;

                    // Quay mặt nhìn về phía item
                    Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), targetPos, ctx.playerRotations());
                    baritone.getLookBehavior().updateTarget(rot, true);

                    // Di chuyển bước tới vị trí item nếu còn cách xa
                    if (horizontalDistSq > 0.08) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                    } else {
                        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
                    }

                    // Tự động nhảy nếu gặp vật cản hoặc chênh lệch độ cao
                    if (ctx.player().horizontalCollision || targetPos.y > playerPos.y + 0.5) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                    } else {
                        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                    }
                } else {
                    baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
                    baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                }

                // Báo log định kỳ mỗi 40 tick (2 giây) để người chơi biết bot đang nhặt đồ
                if (shulkerStateTicks > 0 && shulkerStateTicks % 40 == 0) {
                    logDirect("§e[AutoShulker] Đang di chuyển để nhặt lại Shulker Box (" + (shulkerStateTicks / 20) + "s)...");
                }

                // Timeout an toàn: 200 tick (10 giây). Tránh kẹt vô hạn nếu Shulker Box rơi vào void hoặc bị người khác nhặt mất
                if (shulkerStateTicks > 200) {
                    baritone.getInputOverrideHandler().clearAllKeys();
                    logDirect("§c[AutoShulker] Quá thời gian chờ nhặt Shulker Box (10s)! Tiếp tục hành trình...");
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerPlacedPos = null;
                    shulkerStateTicks = 0;
                    shulkerBoxCountBefore = 0;
                }

                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        return null;
    }

    private void handleAntiStuck() {
        if (ctx.player() == null || eatingSlot != -1) {
            return;
        }

        // Theo dõi hành động đặt block và đào block để phát hiện vòng lặp "đặt lên rồi đào xuống"
        HitResult hit = ctx.objectMouseOver();
        if (hit instanceof BlockHitResult bhr) {
            BlockPos targetedBlock = bhr.getBlockPos();
            boolean isLeft = baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT);
            boolean isRight = baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_RIGHT);
            long now = System.currentTimeMillis();

            if (isRight) {
                BlockPos placePos = targetedBlock.relative(bhr.getDirection());
                if (!placePos.equals(lastPlacedBlockPos)) {
                    lastPlacedBlockPos = placePos;
                    lastPlacedBlockTime = now;
                    placedThisCycle = true;
                }
            }
            if (isLeft && placedThisCycle) {
                lastBrokenBlockPos = targetedBlock;
                lastBrokenBlockTime = now;
                if ((lastPlacedBlockPos != null && targetedBlock.equals(lastPlacedBlockPos) && (now - lastPlacedBlockTime) < 2500)
                        || (targetedBlock.equals(ctx.playerFeet()) && (now - lastPlacedBlockTime) < 2500)) {
                    placeBreakOscillationCount++;
                    placedThisCycle = false;
                }
            }
        }

        BetterBlockPos currentFeet = ctx.playerFeet();

        // Cập nhật circular buffer 120 ticks để phát hiện bot bị kẹt dao động qua lại (ping-pong loop)
        recentPositions[recentPosIndex] = currentFeet;
        recentPosIndex = (recentPosIndex + 1) % RECENT_POS_BUFFER_SIZE;
        if (recentPosCount < RECENT_POS_BUFFER_SIZE) {
            recentPosCount++;
        }

        boolean pingPongDetected = false;
        if (recentPosCount >= RECENT_POS_BUFFER_SIZE) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < RECENT_POS_BUFFER_SIZE; i++) {
                BetterBlockPos p = recentPositions[i];
                if (p == null) continue;
                if (p.x < minX) minX = p.x;
                if (p.x > maxX) maxX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.y > maxY) maxY = p.y;
                if (p.z < minZ) minZ = p.z;
                if (p.z > maxZ) maxZ = p.z;
            }
            double spanX = maxX - minX;
            double spanY = maxY - minY;
            double spanZ = maxZ - minZ;
            double maxSpan = Math.max(spanX, Math.max(spanY, spanZ));
            if (maxSpan <= 2.5) {
                pingPongDetected = true;
            }
        }

        // Đang đào block với thời gian hợp lệ (<= 80 ticks) thì KHÔNG tính là bị kẹt
        boolean isMining = activeMiningBlock != null && activeMiningTicks <= 80;
        if (isMining) {
            stuckTicks = 0;
            return;
        }

        double dx = 0;
        double dz = 0;
        if (lastStuckCheckPos != null) {
            dx = currentFeet.x - lastStuckCheckPos.x;
            dz = currentFeet.z - lastStuckCheckPos.z;
        }
        // Kiểm tra di chuyển: Di chuyển theo mặt phẳng ngang >= 2 block HOẶC di chuyển độ cao Y khi chạm đất/bơi trong nước
        boolean movedHorizontally = lastStuckCheckPos != null && (dx * dx + dz * dz) >= 2.0;
        boolean movedVertically = lastStuckCheckPos != null && (ctx.player().onGround() || ctx.player().isInWater()) && currentFeet.y != lastStuckCheckPos.y;
        boolean moved = movedHorizontally || movedVertically;
        boolean samePosition = lastStuckCheckPos != null && !moved;
        boolean pathCalcInProgress = baritone.getPathingBehavior().getInProgress().isPresent()
                && baritone.getPathingBehavior().getCurrent() == null;

        if (samePosition) {
            if (!pathCalcInProgress) {
                stuckTicks++;
            }
        } else {
            lastStuckCheckPos = currentFeet;
            stuckTicks = 0;
            placeBreakOscillationCount = 0;
            placedThisCycle = false;
            if (lastAntiStuckPos != null && currentFeet.distSqr(lastAntiStuckPos) > 16) {
                stuckRetries = 0;
                lastAntiStuckPos = null;
            }
            // Đã thực sự di chuyển → Cho phép nhảy+đặt block trở lại
            if (Baritone.settings().noPillar.value) {
                pillarFailCount = 0;
                Baritone.settings().noPillar.value = false;
                logDirect("§a[AntiPillarLoop] Đã di chuyển thành công, cho phép nhảy+đặt block trở lại.");
            }
        }

        // === PHÁT HIỆN PILLAR LOOP ===
        // Khi bị kẹt 1 chỗ > 60 tick (3 giây), kiểm tra xem bot có đang cố nhảy+đặt block không
        if (stuckTicks >= 60 && !Baritone.settings().noPillar.value) {
            boolean isMobKnockback = ctx.player().hurtTime > 0;
            boolean isJumpIntent = baritone.getInputOverrideHandler().isInputForcedDown(Input.JUMP)
                    || ctx.minecraft().options.keyJump.isDown();
            boolean isJumping = !isMobKnockback && isJumpIntent && (ctx.player().getDeltaMovement().y > 0.1 || !ctx.player().onGround());
            if (isJumping && samePosition) {
                long now = System.currentTimeMillis();
                if (now - lastPillarFailTime > 600) { // ít nhất 600ms (12 tick) giữa các lần nhảy riêng biệt
                    lastPillarFailTime = now;
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
        }

        // === PHÁT HIỆN KẸT HÀNH ĐỘNG QUÁ LÂU (>= 120 tick = 6s) HOẶC VÒNG LẶP ĐẶT/ĐÀO HOẶC PING-PONG ===
        if (stuckTicks >= 120 || placeBreakOscillationCount >= 2 || pingPongDetected) {
            if (pingPongDetected) {
                logDirect("§c[AntiStuck] Phát hiện dao động qua lại (ping-pong) trong phạm vi <= 2.5 block (> 120 ticks)! Giải kẹt ngay...");
            } else if (placeBreakOscillationCount >= 2) {
                logDirect("§c[AntiStuck] Phát hiện vòng lặp đặt block rồi đào xuống! Đổi hướng ngay...");
            } else {
                logDirect("§c[AntiStuck] Bị kẹt đứng yên quá 120 ticks! Giải kẹt ngay...");
            }
            stuckTicks = 0;
            recentPosCount = 0;
            placeBreakOscillationCount = 0;
            placedThisCycle = false;
            lastAntiStuckPos = currentFeet;
            stuckRetries++;

            // Khắc phục cát/sỏi sập trúng đầu
            BlockPos head = currentFeet.above();
            BlockState headState = ctx.world().getBlockState(head);
            if (headState.isSuffocating(ctx.world(), head) || !headState.isAir()) {
                ctx.playerController().clickBlock(head, net.minecraft.core.Direction.UP);
            }

            int targetY = Baritone.settings().legitMineYLevel.value;

            // 1. ƯU TIÊN SỐ 1: Nếu kẹt khi đang tiếp cận quặng ở cự ly gần:
            if ((knownOreLocations != null && !knownOreLocations.isEmpty()) || !oreMemory.isEmpty()) {
                List<BlockPos> candidates = (knownOreLocations != null && !knownOreLocations.isEmpty())
                        ? knownOreLocations : new ArrayList<>(oreMemory);
                Optional<BlockPos> closestCandidate = candidates.stream()
                        .min(Comparator.comparingDouble(currentFeet::distSqr));
                if (closestCandidate.isPresent()) {
                    BlockPos pos = closestCandidate.get();
                    double distSq = currentFeet.distSqr(pos);
                    // CHỈ xử lý theo quặng nếu quặng ở gần (<= 7 block, distSq <= 49).
                    // Nếu quặng ở xa (> 7 block), bot bị kẹt là do chướng ngại hầm trên đường đi, KHÔNG ĐƯỢC blacklist quặng!
                    if (distSq <= 49) {
                        if (stuckRetries < 2) {
                            // Lần đầu bị kẹt: CHỈ reroute/reset path, KHÔNG blacklist vội quặng còn ngon!
                            logDirect("§e[AntiStuck] Tạm thời khựng khi đến quặng tại " + pos.toShortString() + " (thử 1/2)! Đang thử đổi góc tiếp cận...");
                            lockedTargetOre = null;
                            forceReroute = true;
                            return;
                        } else {
                            // Đã kẹt liên tiếp >= 2 lần ngay tại quặng này: Lúc này mới blacklist vỉa quặng không tới được
                            List<BlockPos> veinOres = candidates.stream()
                                    .filter(p -> p.equals(pos) || p.distSqr(pos) <= 9)
                                    .collect(Collectors.toList());
                            for (BlockPos p : veinOres) {
                                blacklist.add(p);
                                oreMemory.remove(p);
                            }
                            if (knownOreLocations != null) {
                                knownOreLocations.removeIf(blacklist::contains);
                            }
                            logDirect("§c[AntiStuck] Vỉa quặng tại " + pos.toShortString() + " (" + veinOres.size() + " block) không thể tiếp cận sau 2 lần thử! Bỏ qua vỉa này...");
                            lockedTargetOre = null;
                            forceReroute = true;
                            return;
                        }
                    }
                }
            }

            // 2. Khi đang đào dốc xuống mà gặp vật cản (CHỈ khi không có quặng nào đang đào): Đổi hướng đào dốc theo chiều kim đồng hồ
            boolean noOres = (knownOreLocations == null || knownOreLocations.isEmpty()) && oreMemory.isEmpty();
            if (noOres && (!hasReachedTargetY || currentFeet.y > targetY + 3)) {
                if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
                    net.minecraft.core.Direction dir = ctx.player().getDirection();
                    tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
                }
                if (stuckRetries >= 4) {
                    stuckRetries = 0;
                    int drop = currentFeet.y - targetY;
                    if (drop > 1) {
                        logDirect("§c[AntiStuck] Kẹt đào dốc cả 4 hướng! Chuyển sang đào thẳng đứng (Shaft Down) để vượt qua vật cản...");
                        Baritone.settings().straightDownMine.value = true;
                    } else {
                        int escapeY = currentFeet.y + 2;
                        logDirect("§c[AntiStuck] Kẹt đào dốc cả 4 hướng! Bước ngược lên Y=" + escapeY + " để tìm lối khác...");
                        tunnelOrigin = new BlockPos(currentFeet.x, escapeY, currentFeet.z);
                    }
                    forceReroute = true;
                    return;
                }
                net.minecraft.core.Direction newDir = tunnelDirection.getClockWise();
                tunnelDirection = newDir;
                logDirect("§6[AntiStuck] Gặp vật cản khi đào dốc xuống (thử " + stuckRetries + "/4)! Tự động đổi hướng đào sang " + newDir.getName().toUpperCase() + "...");
                forceReroute = true;
                return;
            }

            // 3. Đào hầm tại tầng đáy bị kẹt bedrock:
            if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
                net.minecraft.core.Direction dir = ctx.player().getDirection();
                tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
            }
            // Sau 4 lần thử (đã xoay cả 4 hướng) mà vẫn kẹt = toàn Bedrock -> Đào ngược lên 2 block
            if (stuckRetries >= 4) {
                int escapeY = Math.min(currentFeet.y + 2, targetY + 5);
                logDirect("§c[AntiStuck] Bị kẹt bedrock cả 4 hướng! Đào ngược lên Y=" + escapeY + " để thoát...");
                tunnelOrigin = new BlockPos(currentFeet.x, escapeY, currentFeet.z);
                stuckRetries = 0;
                forceReroute = true;
                return;
            }
            net.minecraft.core.Direction newDir = tunnelDirection.getClockWise();
            tunnelDirection = newDir;
            tunnelOrigin = new BlockPos(currentFeet.x, targetY, currentFeet.z);
            currentTunnelTarget = null;
            logDirect("§6[AntiStuck] Bị kẹt hầm/gặp Bedrock tại tầng đáy! Tự động chuyển hướng đào hầm sang " + newDir.getName().toUpperCase() + "!");
            forceReroute = true;
            return;
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

    private Optional<BlockPos> findNearbyDescentOpening(int maxHorizontalRadius, int minDrop) {
        if (ctx.world() == null || ctx.player() == null) {
            return Optional.empty();
        }
        BetterBlockPos feet = ctx.playerFeet();
        int targetY = Baritone.settings().legitMineYLevel.value;
        if (feet.y <= targetY) {
            return Optional.empty();
        }

        BlockPos bestCandidate = null;
        int maxDropFound = 0;
        int bestScore = 0;

        for (int r = 0; r <= maxHorizontalRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int x = feet.x + dx;
                    int z = feet.z + dz;

                    int maxYScan = feet.y + 1;
                    int minYScan = Math.max(targetY, feet.y - Baritone.settings().maxFallHeightBucket.value);

                    int currentAirSpan = 0;
                    int airTopY = -1;

                    for (int y = maxYScan; y >= minYScan; y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = ctx.world().getBlockState(pos);
                        boolean isPassable = state.isAir()
                                || state.getBlock() instanceof AirBlock
                                || state.getBlock() == Blocks.WATER
                                || state.getFluidState().getType() instanceof net.minecraft.world.level.material.WaterFluid;

                        if (isPassable) {
                            if (currentAirSpan == 0) {
                                airTopY = y;
                            }
                            currentAirSpan++;
                        } else {
                            if (currentAirSpan >= minDrop && MovementHelper.canWalkOn(ctx, pos) && state.getBlock() != Blocks.LAVA) {
                                int landingY = y + 1;
                                int drop = feet.y - landingY;

                                if (drop >= minDrop) {
                                    boolean isOpenFromFeet = airTopY >= feet.y - 1;
                                    int ceilingThickness = feet.y - airTopY;
                                    if (!isOpenFromFeet && ceilingThickness > 2) {
                                        // Trần đá quá dày (> 2 block), không phải hố mở để nhảy nước
                                        currentAirSpan = 0;
                                        airTopY = -1;
                                        continue;
                                    }
                                    int score = drop * 10;
                                    if (isOpenFromFeet) {
                                        score += 200;
                                    } else {
                                        score += 100 - ceilingThickness * 20;
                                    }
                                    score -= (int) (Math.sqrt(dx * dx + dz * dz) * 3);

                                    if (score > bestScore) {
                                        bestScore = score;
                                        maxDropFound = drop;
                                        bestCandidate = new BlockPos(x, landingY, z);
                                    }
                                }
                            }
                            currentAirSpan = 0;
                            airTopY = -1;
                        }
                    }
                }
            }

            if (bestCandidate != null && maxDropFound >= 15 && bestScore >= 300) {
                break;
            }
        }
        return Optional.ofNullable(bestCandidate);
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
                        if (!blacklist.contains(pos)) {
                            if ((Baritone.settings().legitMineIncludeDiagonals.value && (knownOreLocations.stream().anyMatch(ore -> ore.distSqr(pos) <= 2 /* sq means this is pytha dist <= sqrt(2) */) || oreMemory.stream().anyMatch(ore -> ore.distSqr(pos) <= 2))) || RotationUtils.reachable(ctx, pos, fakedBlockReachDistance).isPresent()) {
                                oreMemory.add(pos);
                                knownOreLocations.add(pos);
                            }
                        }
                    }
                }
            }
        }
        CalculationContext context = new CalculationContext(baritone);
        cleanOreMemory(context, filter);
        List<BlockPos> allCandidates = new ArrayList<>(oreMemory);
        allCandidates.addAll(dropped);
        knownOreLocations = new CopyOnWriteArrayList<>(prune(context, allCandidates, filter, Baritone.settings().mineMaxOreLocationsCount.value, blacklist, dropped));
        return true;
    }

    private void addNearbyQuick() {
        BlockOptionalMetaLookup f = filterFilter();
        if (f == null || ctx.world() == null || ctx.player() == null) {
            return;
        }
        BetterBlockPos feet = ctx.playerFeet();
        int r = 5;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -3; dy <= 4; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(feet.x + dx, feet.y + dy, feet.z + dz);
                    BlockState state = ctx.world().getBlockState(pos);
                    if (f.has(state)) {
                        if (!blacklist.contains(pos)) {
                            oreMemory.add(pos);
                            if (!knownOreLocations.contains(pos)) {
                                knownOreLocations.add(pos);
                            }
                        }
                    } else if (state.isAir()) {
                        oreMemory.remove(pos);
                        knownOreLocations.remove(pos);
                        if (lockedTargetOre != null && lockedTargetOre.equals(pos)) {
                            lockedTargetOre = null;
                        }
                    }
                }
            }
        }
    }

    private static List<BlockPos> prune(CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, Collection<BlockPos> blacklist, List<BlockPos> dropped) {
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

                .filter(pos -> !blacklist.contains(pos))

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
            return new ArrayList<>(locs.subList(0, max));
        }
        return locs;
    }

    public static boolean isNextToAir(CalculationContext ctx, BlockPos pos) {
        if (!ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
            return true; // Giữ lại quặng trong chunk chưa load
        }
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
        if (!ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
            return true; // Giữ lại quặng trong chunk chưa load
        }
        BlockState state = ctx.bsi.get0(pos);
        if (MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), state, true) >= COST_INF) {
            return false;
        }
        if (MovementHelper.avoidBreaking(ctx.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
            return false;
        }

        // Cả trên và dưới đều là bedrock -> Không thể đào
        if (ctx.bsi.get0(pos.above()).getBlock() == Blocks.BEDROCK && ctx.bsi.get0(pos.below()).getBlock() == Blocks.BEDROCK) {
            return false;
        }

        // Bị bao vây bởi 4 mặt bedrock trở lên -> Không có không gian tiếp cận
        int bedrockCount = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (ctx.bsi.get0(pos.relative(dir)).getBlock() == Blocks.BEDROCK) {
                bedrockCount++;
            }
        }
        return bedrockCount < 4;
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
        this.knownOreLocations = new CopyOnWriteArrayList<>();
        this.blacklist.clear();
        this.oreMemory.clear();
        this.branchPoint = null;
        this.branchPointRunaway = null;
        this.anticipatedDrops = new HashMap<>();
        this.currentTunnelTarget = null;
        this.pillarFailCount = 0;
        this.hasReachedTargetY = false;
        this.activeMiningBlock = null;
        this.activeMiningTicks = 0;
        this.lockedTargetOre = null;
        this.recentPosIndex = 0;
        this.recentPosCount = 0;
        this.lastAntiStuckPos = null;
        this.shulkerState = ShulkerStorageState.IDLE;
        this.shulkerPlacedPos = null;
        this.shulkerStateTicks = 0;
        this.shulkerBoxCountBefore = 0;
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
