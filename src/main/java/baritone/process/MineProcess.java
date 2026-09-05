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
import baritone.behavior.LookBehavior;
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
import baritone.utils.ToolSet;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean rescanInProgress = new AtomicBoolean(false);
    private boolean bedrockEscapeActive = false;
    private BetterBlockPos bedrockEscapeOrigin = null;
    private int bedrockEscapeTargetY = -54;
    private int bedrockEscapeTicks = 0;
    private BlockPos tunnelOriginPos = null;
    private BlockPos stairOriginPos = null;
    private BlockPos shaftOriginPos = null;
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
    private BlockPos lastPillarFailPos = null;
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
    private BlockPos lastStuckOrePos = null;
    private static final int RECENT_POS_BUFFER_SIZE = 200; // 10 giây (200 ticks) theo dõi vị trí
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

    private enum ShulkerMode {
        DEPOSIT,
        RETRIEVE_FOOD,
        RETRIEVE_TOOL
    }

    private ShulkerStorageState shulkerState = ShulkerStorageState.IDLE;
    private ShulkerMode shulkerMode = ShulkerMode.DEPOSIT;
    private BlockPos shulkerPlacedPos = null;
    private int shulkerStateTicks = 0;
    private int shulkerHotbarSlot = 1;
    private int shulkerTransferCooldown = 0;
    private int shulkerConsecutiveNoTransfer = 0;
    private int shulkerBoxCountBefore = 0;
    private long lastShulkerFullWarningTime = 0;
    private long lastNoPickaxeWarningTime = 0;
    private final Set<Integer> shulkerUntransferableSlots = new HashSet<>();
    private int shulkerTransferredCount = 0;
    private boolean shulkerClearingInProgress = false;
    private int consecutiveCalcFailures = 0;
    private boolean isChopMode = false;
    private GoalChopTour activeChopTourGoal = null;
    private final Map<BlockPos, Long> ignoredDrops = new HashMap<>();
    private BlockPos dropAttemptPos = null;
    private int dropAttemptTicks = 0;

    public MineProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isChopMode() {
        return isChopMode;
    }

    @Override
    public void setChopMode(boolean chopMode) {
        this.isChopMode = chopMode;
    }

    @Override
    public boolean isActive() {
        return filter != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        this.tickCount++;
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
                if (isChopMode) {
                    // CHẾ ĐỘ CHOP WOOD: TUYỆT ĐỐI KHÔNG DÙNG CẢNH BÁO, KHÔNG CANCEL VÀ KHÔNG STOP!
                    activeChopTourGoal = null;
                    if (lockedTargetOre != null) {
                        blacklist.add(lockedTargetOre);
                        oreMemory.remove(lockedTargetOre);
                        knownOreLocations.remove(lockedTargetOre);
                        lockedTargetOre = null;
                    }
                    forceReroute = true;
                    consecutiveCalcFailures = 0;
                    return new PathingCommand(new GoalRunAway(25, ctx.playerFeet()), PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                consecutiveCalcFailures++;
                if (!knownOreLocations.isEmpty() && Baritone.settings().blacklistClosestOnFailure.value) {
                    logDirect("Unable to find any path to " + filter + ", retrying...");
                    BlockPos targetToBlacklist = lockedTargetOre != null
                            ? lockedTargetOre
                            : knownOreLocations.stream().min(Comparator.comparingDouble(ctx.playerFeet()::distSqr)).orElse(null);

                    if (targetToBlacklist != null) {
                        // Blacklist TOÀN BỘ cụm vỉa quặng (distSqr <= 9) để không bao giờ đào lại nữa!
                        final BlockPos posToBlacklist = targetToBlacklist;
                        List<BlockPos> veinOres = knownOreLocations.stream()
                                .filter(p -> p.equals(posToBlacklist) || p.distSqr(posToBlacklist) <= 9)
                                .collect(Collectors.toList());
                        for (BlockPos p : veinOres) {
                            blacklist.add(p);
                            oreMemory.remove(p);
                        }
                        knownOreLocations.removeIf(blacklist::contains);
                        if (lockedTargetOre != null && (lockedTargetOre.equals(posToBlacklist) || lockedTargetOre.distSqr(posToBlacklist) <= 9)) {
                            lockedTargetOre = null;
                        }
                        logDirect("§c[Blacklist] Đã blacklist vỉa quặng không thể tìm đường tại " + posToBlacklist.toShortString() + " (" + veinOres.size() + " block)!");
                    }
                }

                // Nếu thất bại liên tiếp >= 3 lần (bị kẹt quanh các quặng không thể tới):
                // Lập tức giải phóng toàn bộ quặng đang kẹt, buộc bot đào hầm tiến lên phía trước!
                if (consecutiveCalcFailures >= 3) {
                    logDirect("§e[Mine] Không thể tìm đường tới các quặng xung quanh sau " + consecutiveCalcFailures + " lần thử! Tạm bỏ qua và tiếp tục đào hầm tiến lên phía trước...");
                    for (BlockPos p : knownOreLocations) {
                        blacklist.add(p);
                        oreMemory.remove(p);
                    }
                    knownOreLocations.clear();
                    lockedTargetOre = null;
                    branchPoint = ctx.playerFeet();
                    branchPointRunaway = null;
                    forceReroute = true;
                    consecutiveCalcFailures = 0;
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
        } else {
            if (baritone.getPathingBehavior().isPathing()) {
                consecutiveCalcFailures = 0;
            }
        }

        handleAntiStuck();

        if (Baritone.settings().autoEat.value) {
            PathingCommand eatCmd = handleAutoEat(isSafeToCancel);
            if (eatCmd != null) {
                return eatCmd;
            }
        }

        if (Baritone.settings().autoTool.value) {
            PathingCommand toolCmd = handleAutoTool(isSafeToCancel);
            if (toolCmd != null) {
                return toolCmd;
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

        // 1. ƯU TIÊN SỐ 1 KHI ĐẦY BALO: Auto-Shulker Box (cất toàn bộ quặng & đá vào Shulker Box thay vì vứt bỏ)
        if (Baritone.settings().autoShulkerStorage.value || shulkerState != ShulkerStorageState.IDLE) {
            PathingCommand shulkerCmd = handleShulkerStorage(isSafeToCancel);
            if (shulkerCmd != null) {
                return shulkerCmd;
            }
        }

        // 2. Cơ chế tự động drop đá và quặng không liên quan mỗi 10s (không cần chờ đầy mới vứt):
        if (Baritone.settings().autoDrop.value || !pendingDropSlots.isEmpty()) {
            PathingCommand dropCmd = handleAutoDrop();
            if (dropCmd != null) {
                return dropCmd;
            }
        }
        int mineGoalUpdateInterval = Baritone.settings().mineGoalUpdateInterval.value;
        addNearbyQuick();
        List<BlockPos> curr = new ArrayList<>(knownOreLocations);
        if (mineGoalUpdateInterval != 0 && tickCount % mineGoalUpdateInterval == 0) { // big brain
            if (rescanInProgress.compareAndSet(false, true)) {
                CalculationContext context = new CalculationContext(baritone, true);
                Baritone.getExecutor().execute(() -> {
                    try {
                        rescan(curr, context);
                    } finally {
                        rescanInProgress.set(false);
                    }
                });
            }
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
            // NGUYÊN TẮC: Khi quặng ở trên cao (> feet.getY() + 2) hoặc bot đang ở trên không (không onGround):
            // TUYỆT ĐỐI KHÔNG nhảy lên đập dở! Nhả activeMiningBlock để A* thực hiện xong bước nhảy/kê chân vững vàng trước!
            // Riêng khi chặt cây (Chop Mode), cho phép với tới độ cao +4 block để chặt sạch thân cây khi đứng trên đất!
            int maxReachY = isChopMode ? (ctx.playerFeet().getY() + 4) : (ctx.playerFeet().getY() + 2);
            if (activeMiningBlock.getY() > maxReachY || (!ctx.player().onGround() && !ctx.player().isInWater())) {
                activeMiningBlock = null;
                activeMiningTicks = 0;
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            } else if (!state.isAir() && (filter == null || filter.has(state))) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, activeMiningBlock);
                if (rot.isPresent()) {
                    activeMiningTicks++;
                    if (activeMiningTicks > 60) {
                        logDirect("§c[Mine] Quặng tại " + activeMiningBlock.toShortString() + " không thể đào vỡ sau 60 ticks! Đã thêm vào BLACKLIST vĩnh viễn!");
                        BlockPos target = activeMiningBlock;
                        blacklist.add(target);
                        oreMemory.remove(target);
                        if (knownOreLocations != null) {
                            knownOreLocations.removeIf(p -> p.equals(target) || p.distSqr(target) <= 9);
                        }
                        if (lockedTargetOre != null && (lockedTargetOre.equals(target) || lockedTargetOre.distSqr(target) <= 9)) {
                            lockedTargetOre = null;
                        }
                        activeMiningBlock = null;
                        activeMiningTicks = 0;
                        forceReroute = true;
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    } else {
                        if (!isChopMode) {
                            baritone.getPathingBehavior().cancelSegmentIfSafe();
                        }
                        baritone.getInputOverrideHandler().clearAllKeys();
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        MovementHelper.switchToBestToolFor(ctx, state);
                        if (ctx.isLookingAt(activeMiningBlock) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                } else {
                    // Chưa thể với tới trực tiếp ở góc nhìn hiện tại -> Nhả activeMiningBlock để A* tiếp tục dẫn đường
                    // TUYỆT ĐỐI KHÔNG xóa quặng khỏi knownOreLocations hay oreMemory khi chưa đào vỡ!
                    activeMiningBlock = null;
                    activeMiningTicks = 0;
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                }
            } else {
                // Block đã vỡ thành Air hoặc không còn là quặng mục tiêu: Xóa khỏi bộ nhớ
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
                    if (!isChopMode) {
                        baritone.getPathingBehavior().cancelSegmentIfSafe();
                    }
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
        // CHỈ đào khi đã đứng vững trên sàn (onGround) hoặc trong nước, KHÔNG BAO GIỜ đào khi đang nhảy trên không!
        boolean canDirectMine = (ctx.player().onGround() || ctx.player().isInWater())
                && !baritone.getInputOverrideHandler().isInputForcedDown(Input.JUMP);
        if (canDirectMine) {
            int maxReachY = isChopMode ? (ctx.playerFeet().getY() + 4) : (ctx.playerFeet().getY() + 2);
            Optional<BlockPos> reachableOre = curr.stream()
                    .filter(pos -> ctx.playerFeet().distSqr(pos) <= 25)
                    // QUY TẮC: Đứng vững trên sàn và đào các block trong tầm với trực tiếp
                    .filter(pos -> pos.getY() <= maxReachY)
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
                    if (!isChopMode) {
                        baritone.getPathingBehavior().cancelSegmentIfSafe();
                    }
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
                .filter(pos -> pos.getY() <= ctx.playerFeet().getY() + 2) // Chỉ đào thẳng đứng nếu trong tầm đứng vững trên sàn
                .filter(pos -> !(BlockStateInterface.get(ctx, pos).getBlock() instanceof AirBlock)) // after breaking a block, it takes mineGoalUpdateInterval ticks for it to actually update this list =(
                .min(Comparator.comparingDouble(ctx.playerFeet().above()::distSqr));
        if (shaft.isPresent() && ctx.player().onGround()) {
            BlockPos pos = shaft.get();
            BlockState state = baritone.bsi.get0(pos);
            if (!MovementHelper.avoidBreaking(baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
                if (rot.isPresent() && isSafeToCancel) {
                    baritone.getInputOverrideHandler().clearAllKeys();
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
            branchPoint = ctx.playerFeet();
            branchPointRunaway = null;
            command = updateGoal();
        }
        if (command == null) {
            int y = Baritone.settings().legitMineYLevel.value;
            Goal fallbackGoal = new GoalRunAway(20, y, ctx.playerFeet());
            return new PathingCommand(fallbackGoal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
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
    public void cancel() {
        activeChopTourGoal = null;
        isChopMode = false;
        onLostControl();
        baritone.getPathingBehavior().forceCancel();
        baritone.getInputOverrideHandler().clearAllKeys();
        baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
        if (ctx.player() != null && ctx.player().containerMenu != ctx.player().inventoryMenu) {
            ctx.player().closeContainer();
        }
    }

    @Override
    public void onLostControl() {
        activeChopTourGoal = null;
        if (eatingSlot != -1) {
            try {
                ctx.minecraft().options.keyUse.setDown(false);
            } catch (Exception ignored) {}
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            eatingSlot = -1;
            eatTicks = 0;
        }
        bedrockEscapeActive = false;
        bedrockEscapeOrigin = null;
        bedrockEscapeTicks = 0;
        tunnelOriginPos = null;
        stairOriginPos = null;
        shaftOriginPos = null;
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
        lastPillarFailPos = null;
        pendingDropSlots.clear();
        dropCooldown = 0;
        if (shulkerState != ShulkerStorageState.IDLE) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            shulkerState = ShulkerStorageState.IDLE;
            shulkerPlacedPos = null;
            shulkerStateTicks = 0;
            shulkerBoxCountBefore = 0;
            shulkerUntransferableSlots.clear();
            shulkerTransferredCount = 0;
            shulkerClearingInProgress = false;
            shulkerMode = ShulkerMode.DEPOSIT;
        }
        if (ctx.player() != null && ctx.player().containerMenu != ctx.player().inventoryMenu) {
            ctx.player().closeContainer();
        }
        baritone.getInputOverrideHandler().clearAllKeys();
        baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
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

        // 2. Loại bỏ các vị trí quặng quá cao so với tầng đào hiện tại (tránh nghẽn bộ nhớ)
        int targetY = Baritone.settings().legitMineYLevel.value;
        if (hasReachedTargetY || ctx.playerFeet().y <= targetY + 3) {
            oreMemory.removeIf(pos -> pos.getY() > targetY + 6);
        }

        // 3. Kiểm tra các vị trí trong chunk ĐANG LOAD mà không còn là quặng (đã đào) hoặc không thể đào (bedrock)
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

        // 4. Nếu quặng nằm ngay sát chân/dưới sàn (distSqr <= 2.25) nhưng không thể với tới để đào (unreachable):
        // Xóa khỏi oreMemory và blacklist để tránh loop "cách 0m"!
        oreMemory.removeIf(pos -> {
            if (ctx.playerFeet().distSqr(pos) <= 2.25 && pos.getY() < ctx.playerFeet().getY()) {
                if (!RotationUtils.reachable(ctx, pos).isPresent()) {
                    blacklist.add(pos);
                    return true;
                }
            }
            return false;
        });
    }

    private PathingCommand updateGoal() {
        BlockOptionalMetaLookup filter = filterFilter();
        if (filter == null) {
            return null;
        }

        // === ƯU TIÊN SỐ 1: BẮT BUỘC HÚT SẠCH 100% KIM CƯƠNG / QUẶNG RƠI TRÊN SÀN TRƯỚC KHI ĐI TIẾP ===
        // Trong chế độ chặt cây, nếu đang di chuyển trên tour thì không huỷ tour giữa chừng để nhặt gỗ
        if (!isChopMode || !baritone.getPathingBehavior().isPathing()) {
            boolean isInvFull = ctx.player() != null && ctx.player().getInventory().getFreeSlot() == -1;
            List<BlockPos> droppedItems = droppedItemsScan();
            if (!droppedItems.isEmpty() && !isInvFull) {
                // Lọc bỏ những item rơi nằm ngược hướng hầm đang đào nếu cách xa quá 2 block
                List<BlockPos> validDrops = droppedItems.stream().filter(dropPos -> {
                    if (tunnelDirection != null) {
                        int dot = (dropPos.getX() - ctx.playerFeet().getX()) * tunnelDirection.getStepX() + (dropPos.getZ() - ctx.playerFeet().getZ()) * tunnelDirection.getStepZ();
                        if (dot < 0 && ctx.playerFeet().distSqr(dropPos) > 4.0) {
                            return false; // Nằm ngược hướng đào hầm -> không quay đầu chạy ngược lại!
                        }
                    }
                    return true;
                }).collect(Collectors.toList());

                if (!validDrops.isEmpty()) {
                    Optional<BlockPos> closestDrop = validDrops.stream()
                            .min(Comparator.comparingDouble(ctx.playerFeet()::distSqr));
                    if (closestDrop.isPresent()) {
                        BlockPos dropPos = closestDrop.get();
                        if (dropAttemptPos != null && dropAttemptPos.equals(dropPos)) {
                            dropAttemptTicks++;
                            if (dropAttemptTicks > 40) { // Đứng sát item 2 giây mà không hút được (bị kẹt/vướng)
                                ignoredDrops.put(dropPos, System.currentTimeMillis() + 30000L);
                                dropAttemptPos = null;
                                dropAttemptTicks = 0;
                            }
                        } else {
                            dropAttemptPos = dropPos;
                            dropAttemptTicks = 0;
                        }

                        // Nếu chưa đứng trong bán kính hút đồ (1 block) -> Bước tới nhặt!
                        if (ctx.playerFeet().distSqr(dropPos) > 1.0) {
                            return new PathingCommand(new GoalTwoBlocks(dropPos), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                        }
                    }
                }
            } else {
                dropAttemptPos = null;
                dropAttemptTicks = 0;
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
            // Loại bỏ các quặng nằm quá xa phía sau hướng hầm đang đào (tránh quay xe chạy ngược hầm cũ)
            if (tunnelDirection != null && hasReachedTargetY) {
                allCandidates.removeIf(p -> {
                    int dot = (p.getX() - ctx.playerFeet().getX()) * tunnelDirection.getStepX() + (p.getZ() - ctx.playerFeet().getZ()) * tunnelDirection.getStepZ();
                    return dot < -8; // Ngược hướng quá 8 block -> bỏ qua, đào tiếp về phía trước!
                });
            }
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

                // CHẾ ĐỘ TỰ ĐỘNG CHẶT CÂY (LUMBERJACK) - 1 ĐƯỜNG TÍNH DUY NHẤT & 1 LẦN TÍNH TOÁN SIÊU DÀI:
                if (isChopMode) {
                    boolean isPathing = baritone.getPathingBehavior().isPathing();
                    boolean hasInProgress = baritone.getPathingBehavior().getInProgress().isPresent();

                    // Nếu tour hiện tại vẫn đang chạy và không bị buộc reroute (AntiStuck):
                    if (activeChopTourGoal != null && (isPathing || hasInProgress) && !forceReroute) {
                        knownOreLocations = new CopyOnWriteArrayList<>(locs2);
                        return new PathingCommand(activeChopTourGoal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                    }

                    // Tạo tour mới nối các cây thành 1 đường duy nhất:
                    List<TreeInfo> trees = clusterTrees(ctx, locs2);
                    if (!trees.isEmpty()) {
                        List<TreeInfo> tourTrees = planTreeTour(trees, ctx.playerFeet(), 25);
                        if (!tourTrees.isEmpty()) {
                            List<BlockPos> bases = tourTrees.stream().map(t -> t.baseLog).collect(Collectors.toList());
                            GoalChopTour tourGoal = new GoalChopTour(bases);
                            this.activeChopTourGoal = tourGoal;
                            this.forceReroute = false;
                            this.consecutiveCalcFailures = 0;
                            knownOreLocations = new CopyOnWriteArrayList<>(locs2);
                            logDirect("§a[AutoChop] Khởi động 1 LẦN TÍNH TOÁN SIÊU DÀI cho 1 ĐƯỜNG TÍNH DUY NHẤT nối " + tourTrees.size() + " cây...");
                            return new PathingCommand(tourGoal, PathingCommandType.CANCEL_AND_SET_GOAL);
                        }
                    }
                    this.activeChopTourGoal = null;
                }
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
        if (isChopMode) {
            // Trong chop wood, không dừng lại, tiếp tục di chuyển khám phá các khu rừng xung quanh
            return new PathingCommand(new GoalRunAway(30, ctx.playerFeet()), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        }
        if (!legit && !Baritone.settings().exploreForBlocks.value) {
            return null;
        }
        
        // KHI KHÔNG CÓ QUẶNG TRONG TẦM QUÉT:
        int currentY = ctx.playerFeet().y;

        // Đánh dấu đã chạm tới độ sâu targetY (hoặc xuất phát ngay tại tầng đào)
        if (currentY <= targetY + 1) {
            hasReachedTargetY = true;
        }

        // Kiểm tra xem AntiStuck có yêu cầu thoát bedrock không (thoát lên tầng an toàn Y >= -54 và rời xa điểm kẹt):
        if (bedrockEscapeActive) {
            bedrockEscapeTicks++;
            if (bedrockEscapeTicks > 400) { // 20s timeout an toàn, tránh loop vô hạn
                logDirect("§c[AntiStuck] Hết thời gian thoát kẹt bedrock, reset trạng thái đào hầm...");
                bedrockEscapeActive = false;
                bedrockEscapeOrigin = null;
                bedrockEscapeTicks = 0;
                branchPoint = ctx.playerFeet();
                branchPointRunaway = null;
                forceReroute = true;
            } else {
                int curY = ctx.playerFeet().y;
                // Giai đoạn 1: Đào ngược lên tầng an toàn (safeY >= -54)
                if (curY < bedrockEscapeTargetY) {
                    if (tickCount % 20 == 0) {
                        logDirect("§b[AntiStuck] Đang đào ngược lên tầng an toàn Y=" + bedrockEscapeTargetY + " (hiện tại Y=" + curY + ") để thoát khỏi vùng Bedrock...");
                    }
                    boolean fr = forceReroute;
                    forceReroute = false;
                    return new PathingCommand(new GoalYLevel(bedrockEscapeTargetY), fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                } else {
                    // Giai đoạn 2: Đã đạt độ cao an toàn (curY >= bedrockEscapeTargetY)!
                    // Di chuyển cách xa điểm kẹt bedrock cũ ít nhất 20 block
                    int distAway = bedrockEscapeOrigin != null ? (int) Math.sqrt(ctx.playerFeet().distSqr(bedrockEscapeOrigin)) : 20;
                    if (distAway >= 20) {
                        logDirect("§a[AntiStuck] Đã thoát xa vùng kẹt Bedrock " + distAway + "m! Trở lại trạng thái đào bình thường.");
                        bedrockEscapeActive = false;
                        bedrockEscapeOrigin = null;
                        bedrockEscapeTicks = 0;
                        branchPoint = ctx.playerFeet();
                        branchPointRunaway = null;
                        forceReroute = true;
                    } else {
                        if (tickCount % 20 == 0) {
                            logDirect("§a[AntiStuck] Đang đào ngang tại tầng an toàn Y=" + curY + " để rời khỏi vùng Bedrock (" + distAway + "/20m)...");
                        }
                        boolean fr = forceReroute;
                        forceReroute = false;
                        if (branchPoint == null) {
                            branchPoint = ctx.playerFeet();
                        }
                        if (branchPointRunaway == null) {
                            branchPointRunaway = new GoalRunAway(20, curY, branchPoint);
                        }
                        return new PathingCommand(branchPointRunaway, fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                    }
                }
            }
        }

        // ƯU TIÊN SỐ 1 KHI Ở TRÊN CAO: DÙNG XÔ NƯỚC (WATER BUCKET) ĐỂ TỤT XUỐNG THAY VÌ ĐÀO XUỐNG
        if (currentY > targetY + 3) {
            boolean fr = forceReroute;
            int waterSlot = ctx.player().getInventory().findSlotMatchingItem(new ItemStack(Items.WATER_BUCKET));
            boolean hasWaterBucket = (waterSlot != -1 || ctx.player().getOffhandItem().is(Items.WATER_BUCKET))
                    && ctx.world().dimension() != net.minecraft.world.level.Level.NETHER
                    && Baritone.settings().allowWaterBucketFall.value;

            if (hasWaterBucket && Baritone.settings().preferWaterBucketOverDigging.value) {
                if (waterSlot >= 9) {
                    ((Baritone) baritone).getInventoryBehavior().attemptToPutOnHotbar(waterSlot, s -> s == 8 || s == 7);
                }
                Optional<BlockPos> opening = findNearbyDescentOpening(32, 3);
                if (opening.isPresent()) {
                    BlockPos dropPos = opening.get();
                    int dropAmount = currentY - dropPos.getY();
                    logDirect("§a[WaterDescent] Phát hiện hố/hang mở tụt " + dropAmount + " block! Ưu tiên nhảy đáp nước (MLG Bucket) thay vì đào xuống.");
                    forceReroute = false;
                    return new PathingCommand(new GoalTwoBlocks(dropPos), fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                }
            }
        }

        // CHUẨN GỐC BARITONE CÓ ĐỊNH HƯỚNG: GoalRunAway liên tục đào xuyên đá tiến về phía trước theo tầng targetY
        int y = targetY;
        if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
            net.minecraft.core.Direction dir = ctx.player().getDirection();
            tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
        }
        // Kiểm tra an toàn: Nếu phía trước đường hầm có lồng Spawner trong vòng 16 block -> Tự động chuyển hướng hầm để tránh xa nguy hiểm!
        CalculationContext spawnerCheckCtx = new CalculationContext(baritone);
        BlockPos tunnelAhead = ctx.playerFeet().relative(tunnelDirection, 8);
        if (isNearSpawner(spawnerCheckCtx, tunnelAhead, 12)) {
            tunnelDirection = tunnelDirection.getClockWise();
            logDirect("§c[Mine] Phát hiện lồng Spawner phía trước! Tự động chuyển hướng hầm sang " + tunnelDirection + " để tránh xa nguy hiểm!");
            branchPoint = ctx.playerFeet();
            branchPointRunaway = null;
            forceReroute = true;
        }
        // Đặt branchPoint 16 block phía sau lưng người chơi theo tunnelDirection:
        // Di chuyển về phía trước (tunnelDirection) làm tăng khoảng cách -> heuristic âm hơn (tốt hơn)
        // Lùi lại phía sau làm giảm khoảng cách -> heuristic xấu đi -> triệt tiêu hoàn toàn ping-pong giật lùi!
        BlockPos desiredBranchPoint = ctx.playerFeet().relative(tunnelDirection.getOpposite(), 16);
        if (branchPoint == null || ctx.playerFeet().distSqr(branchPoint) >= 2304) {
            branchPoint = desiredBranchPoint;
            branchPointRunaway = null;
        }
        if (branchPointRunaway == null) {
            branchPointRunaway = new GoalRunAway(48, y, branchPoint);
        }
        boolean fr = forceReroute;
        forceReroute = false;
        return new PathingCommand(branchPointRunaway, fr ? PathingCommandType.CANCEL_AND_SET_GOAL : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
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
        List<BlockPos> freshlyScanned = Collections.emptyList();
        try {
            freshlyScanned = searchWorld(context, filter, Baritone.settings().mineMaxOreLocationsCount.value, already, new ArrayList<>(blacklist), dropped);
        } catch (Exception e) {
            logDebug("searchWorld encountered error: " + e.getMessage());
        }
        oreMemory.addAll(freshlyScanned);
        cleanOreMemory(context, filter);

        List<BlockPos> allCandidates = new ArrayList<>(oreMemory);
        allCandidates.addAll(dropped);
        List<BlockPos> locs = prune(context, allCandidates, filter, Baritone.settings().mineMaxOreLocationsCount.value, blacklist, dropped);

        if (locs.isEmpty() && !Baritone.settings().exploreForBlocks.value) {
            if (isChopMode) {
                return;
            }
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
        long now = System.currentTimeMillis();
        ignoredDrops.entrySet().removeIf(e -> e.getValue() < now);
        List<BlockPos> ret = new ArrayList<>();
        BetterBlockPos pf = ctx.playerFeet();
        for (Entity entity : ((ClientLevel) ctx.world()).entitiesForRendering()) {
            if (entity instanceof ItemEntity && entity.isAlive()) {
                ItemEntity ei = (ItemEntity) entity;
                ItemStack stack = ei.getItem();
                Item item = stack.getItem();
                if (ORE_DROPS.contains(item) || (filter != null && filter.has(stack)) || item.getDescriptionId().contains("ore") || item.getDescriptionId().contains("raw")) {
                    BlockPos pos = entity.blockPosition();
                    if (!ignoredDrops.containsKey(pos) && pos.distSqr(pf) <= 256) { // Trong bán kính 16 block
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

    private boolean isTargetOre(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();

        // 1. Quặng quý hiếm cực đỉnh luôn luôn được giữ (Kim cương, Netherite, Ngọc lục bảo):
        if (item == Items.DIAMOND || item == Items.EMERALD
                || item == Items.ANCIENT_DEBRIS || item == Items.NETHERITE_INGOT
                || item == Items.NETHERITE_SCRAP) {
            return true;
        }

        // 2. Nếu không có filter cụ thể (mine tự do): giữ các quặng quý thông thường
        if (filter == null) {
            return item == Items.LAPIS_LAZULI
                    || item == Items.REDSTONE
                    || item == Items.GOLD_INGOT
                    || item == Items.IRON_INGOT
                    || item == Items.RAW_GOLD
                    || item == Items.RAW_IRON
                    || item == Items.AMETHYST_SHARD;
        }

        // 3. Nếu CÓ filter: kiểm tra xem item hoặc block có khớp với mục tiêu đào của người chơi không
        if (filter.has(stack)) {
            return true;
        }
        if (item instanceof BlockItem bi && filter.has(bi.getBlock())) {
            return true;
        }

        // Kiểm tra theo tên quặng trong filter (ví dụ người chơi gõ #mine diamond_ore -> giữ diamond)
        String iName = item.getDescriptionId().toLowerCase();
        for (BlockOptionalMeta bom : filter.blocks()) {
            Block b = bom.getBlock();
            if (b == null) continue;
            String bName = b.getDescriptionId().toLowerCase();
            if (bName.contains("diamond") && iName.contains("diamond")) return true;
            if (bName.contains("emerald") && iName.contains("emerald")) return true;
            if (bName.contains("iron") && (iName.contains("iron") || iName.contains("raw_iron"))) return true;
            if (bName.contains("gold") && (iName.contains("gold") || iName.contains("raw_gold"))) return true;
            if (bName.contains("copper") && (iName.contains("copper") || iName.contains("raw_copper"))) return true;
            if (bName.contains("coal") && iName.contains("coal")) return true;
            if (bName.contains("lapis") && iName.contains("lapis")) return true;
            if (bName.contains("redstone") && iName.contains("redstone")) return true;
            if (bName.contains("debris") && (iName.contains("debris") || iName.contains("netherite"))) return true;
            if (bName.contains("quartz") && iName.contains("quartz")) return true;
        }

        return false;
    }

    private boolean isProtectedFromDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        // BẢO VỆ TUYỆT ĐỐI SHULKER BOX (ĐẦY HOẶC TRỐNG) - TUYỆT ĐỐI KHÔNG BAO GIỜ VỨT!
        if (isShulkerBox(stack)) return true;
        if (stack.has(DataComponents.CONTAINER)) return true;
        if (stack.has(DataComponents.BUNDLE_CONTENTS)) return true;
        String desc = stack.getItem().getDescriptionId();
        if (desc != null && desc.toLowerCase().contains("shulker")) return true;
        // Bảo vệ Totem, Đồ ăn, Công cụ, Giáp, Quặng mục tiêu
        if (stack.is(Items.TOTEM_OF_UNDYING)) return true;
        if (isGoodFood(stack) || stack.has(DataComponents.FOOD)) return true;
        if (isToolOrEssential(stack)) return true;
        if (isTargetOre(stack)) return true;
        if (stack.has(DataComponents.CUSTOM_NAME) || stack.has(DataComponents.ENCHANTMENTS)) return true;
        return false;
    }

    private int countDroppableTrashSlots() {
        if (ctx.player() == null) return 0;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int keptBuildingBlocks = 0;
        int trashSlots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.get(i);
            if (stack.isEmpty()) continue;
            if (isProtectedFromDrop(stack)) continue;
            if (isBuildingBlock(stack)) {
                if (keptBuildingBlocks < 64) {
                    keptBuildingBlocks += stack.getCount();
                    continue;
                }
            }
            trashSlots++;
        }
        return trashSlots;
    }

    private int countTransferableSlots() {
        if (ctx.player() == null) return 0;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int transferable = 0;
        for (int i = 0; i < 36; i++) {
            if (i == 0) continue; // Luôn bảo vệ ô hotbar slot 0 chứa cúp chính
            ItemStack stack = inv.get(i);
            if (stack.isEmpty()) continue;
            if (shouldKeepInInventory(stack)) continue;
            // Block xây dựng thông thường (đá, đất, v.v.) không phải là món cất vào Shulker Box
            if (isBuildingBlock(stack) && !isTargetOre(stack)) continue;
            transferable++;
        }
        return transferable;
    }

    private PathingCommand handleAutoDrop() {
        if (ctx.player() == null || ctx.player().containerMenu != ctx.player().inventoryMenu) {
            return null;
        }

        if (!Baritone.settings().autoDrop.value) {
            return null;
        }

        // Khi đang thao tác đặt/cất Shulker Box: tạm dừng AutoDrop để không xung đột click chuột
        if (shulkerState != ShulkerStorageState.IDLE) {
            return null;
        }

        // Nếu đang đập block dở (activeMiningBlock hoặc isHittingBlock), hoãn vứt rác cho tới khi block vỡ xong
        boolean isMining = (activeMiningBlock != null)
                || baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)
                || ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).isHittingBlock();

        // 1. Nếu đang có hàng đợi vứt rác → vứt 1 stack mỗi 4 tick (0.2s) để tránh kick packet
        if (!pendingDropSlots.isEmpty()) {
            if (isMining) {
                return null;
            }
            if (dropCooldown > 0) {
                dropCooldown--;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            int slotIndex = pendingDropSlots.remove(0);
            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            if (slotIndex >= 0 && slotIndex < inv.size()) {
                ItemStack stack = inv.get(slotIndex);
                int windowSlot = (slotIndex < 9) ? (slotIndex + 36) : slotIndex;
                ItemStack menuStack = ctx.player().inventoryMenu.getSlot(windowSlot).getItem();

                // KIỂM TRA BẢO VỆ 2 LỚP TRÊN CẢ INV LẪN WINDOW SLOT TRỰC TIẾP:
                // TUYỆT ĐỐI KHÔNG BAO GIỜ vứt Shulker Box (đặc biệt là Shulker Box chứa đồ), Totem, Food, Tools, Quặng mục tiêu
                boolean isProtected = isProtectedFromDrop(stack) || isProtectedFromDrop(menuStack)
                        || isShulkerBox(stack) || isShulkerBox(menuStack)
                        || stack.has(DataComponents.CONTAINER) || menuStack.has(DataComponents.CONTAINER)
                        || (stack.getItem().getDescriptionId() != null && stack.getItem().getDescriptionId().toLowerCase().contains("shulker"))
                        || (menuStack.getItem().getDescriptionId() != null && menuStack.getItem().getDescriptionId().toLowerCase().contains("shulker"));

                if (!stack.isEmpty() && !menuStack.isEmpty() && !isProtected) {
                    // Xoay góc ném: Ưu tiên ném vào hồ Lava gần đó để tiêu hủy, nếu không có thì ném thẳng ra PHÍA SAU LƯNG
                    Rotation dropRot = findBestDropRotation();
                    if (dropRot != null) {
                        baritone.getLookBehavior().updateTarget(dropRot, true);
                        if (!LookBehavior.isF5(ctx)) {
                            ctx.player().setYRot(dropRot.getYaw());
                            ctx.player().setXRot(dropRot.getPitch());
                        }
                        if (ctx.player().connection != null) {
                            ctx.player().connection.send(new ServerboundMovePlayerPacket.Rot(
                                    dropRot.getYaw(),
                                    dropRot.getPitch(),
                                    ctx.player().onGround(),
                                    ctx.player().horizontalCollision
                            ));
                        }
                    }
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 1, ClickType.THROW, ctx.player());
                }
            }
            dropCooldown = 4;
            if (pendingDropSlots.isEmpty()) {
                logDirect("§a[AutoDrop] Đã dọn sạch toàn bộ đá thừa và quặng không liên quan!");
                return null;
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (isMining) {
            return null;
        }

        // 2. CHU KỲ TỰ ĐỘNG VỨT MỖI 10 GIÂY (200 ticks) - KHÔNG CẦN CHỜ ĐẦY MỚI VỨT:
        if (tickCount % 200 == 0) {
            scanAndQueueTrashDrops();
        } else {
            // 3. KHI BALO GẦN ĐẦY (còn <= 3 ô trống): Quét và vứt rác NGAY LẬP TỨC để giải phóng chỗ trống
            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            int emptyCount = 0;
            for (int i = 0; i < 36; i++) {
                if (inv.get(i).isEmpty()) emptyCount++;
            }
            if (emptyCount <= 3 && pendingDropSlots.isEmpty()) {
                scanAndQueueTrashDrops();
            }
        }

        if (!pendingDropSlots.isEmpty()) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return null;
    }

    private BlockPos findNearbyLava() {
        if (ctx.player() == null || ctx.world() == null) return null;
        BetterBlockPos feet = ctx.playerFeet();
        BlockPos bestLava = null;
        double bestLavaDistSq = Double.MAX_VALUE;

        // Quét tìm hồ Lava trong phạm vi 5x3x5 quanh người chơi để tiêu hủy rác
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 2; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    FluidState fluid = ctx.world().getFluidState(p);
                    if (fluid.is(FluidTags.LAVA)) {
                        BlockPos above = p.above();
                        BlockState aboveState = ctx.world().getBlockState(above);
                        // Chỉ ném nếu ô phía trên lava là không khí, có thể đi qua hoặc cũng là lava (không bị bịt kín bởi đá)
                        if (aboveState.isAir() || aboveState.canBeReplaced() || ctx.world().getFluidState(above).is(FluidTags.LAVA)) {
                            double distSq = feet.distSqr(p);
                            if (distSq < bestLavaDistSq) {
                                Vec3 eye = ctx.playerHead();
                                Vec3 target = new Vec3(p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5);
                                ClipContext rayCtx = new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.player());
                                HitResult hit = ctx.world().clip(rayCtx);
                                if (hit.getType() == HitResult.Type.MISS || (hit instanceof BlockHitResult bhr && (bhr.getBlockPos().equals(p) || bhr.getBlockPos().equals(above)))) {
                                    bestLavaDistSq = distSq;
                                    bestLava = p;
                                }
                            }
                        }
                    }
                }
            }
        }
        return bestLava;
    }

    private Rotation findBestDropRotation() {
        if (ctx.player() == null || ctx.world() == null) {
            return null;
        }
        BlockPos lava = findNearbyLava();
        if (lava != null) {
            Vec3 target = new Vec3(lava.getX() + 0.5, lava.getY() + 0.7, lava.getZ() + 0.5);
            return RotationUtils.calcRotationFromVec3d(ctx.playerHead(), target, ctx.playerRotations());
        }

        // Nếu không có hồ Lava gần đó: Ném thẳng ra PHÍA SAU LƯNG (thay vì vứt ra trước mặt)
        float behindYaw;
        if (tunnelDirection != null && tunnelDirection.getAxis().isHorizontal()) {
            behindYaw = tunnelDirection.getOpposite().toYRot();
        } else {
            behindYaw = ctx.playerRotations().getYaw() + 180.0F;
        }
        // Góc cúi nhẹ 20 độ để item văng ra sàn phía sau lưng
        return new Rotation(behindYaw, 20.0F);
    }

    private void scanAndQueueTrashDrops() {
        if (ctx.player() == null) return;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int keptBuildingBlocks = 0;
        int newQueued = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.get(i);
            if (stack.isEmpty()) continue;

            // 1. Giữ các vật phẩm bảo vệ: Shulker Box, Totem, Thức ăn, Công cụ/Cúp, Quặng mục tiêu
            if (isProtectedFromDrop(stack)) continue;

            // 2. Block xây dựng -> Giữ đúng 1 stack (tối đa 64 block) để bắc cầu/kê chân
            if (isBuildingBlock(stack)) {
                if (keptBuildingBlocks < 64) {
                    keptBuildingBlocks += stack.getCount();
                    continue;
                }
            }

            // 3. Toàn bộ đá thừa, quặng không liên quan, rác linh tinh -> nạp vào hàng đợi vứt
            if (!pendingDropSlots.contains(i)) {
                pendingDropSlots.add(i);
                newQueued++;
            }
        }

        if (newQueued > 0) {
            BlockPos lava = findNearbyLava();
            if (lava != null) {
                logDirect("§e[AutoDrop] Phát hiện hồ Lava gần đó! Tự động tiêu hủy " + newQueued + " stack rác vào Lava...");
            } else {
                logDirect("§e[AutoDrop] Tự động vứt " + newQueued + " stack rác ra phía sau lưng (tránh vướng đường đi)...");
            }
            dropCooldown = 0; // Vứt stack đầu tiên ngay lập tức
        }
    }

    public static boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(ItemTags.SHULKER_BOXES)) return true;
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) return true;
        String desc = stack.getItem().getDescriptionId();
        return desc != null && desc.toLowerCase().contains("shulker");
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

    public static boolean isUsableMiningTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean isPick = stack.is(ItemTags.PICKAXES);
        if (!isPick) {
            String desc = stack.getItem().getDescriptionId();
            if (desc != null && desc.toLowerCase(Locale.ROOT).contains("pickaxe")) {
                isPick = true;
            }
        }
        if (!isPick && stack.has(DataComponents.CUSTOM_NAME)) {
            String custom = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (custom.contains("cúp") || custom.contains("cup") || custom.contains("pickaxe")) {
                isPick = true;
            }
        }
        if (!isPick) return false;
        if (Baritone.settings().itemSaver.value && (stack.getDamageValue() + Baritone.settings().itemSaverThreshold.value) >= stack.getMaxDamage() && stack.getMaxDamage() > 1) {
            return false;
        }
        return true;
    }

    public static boolean shulkerContainsFood(ItemStack shulkerStack) {
        if (!isShulkerBox(shulkerStack)) return false;
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return false;
        for (ItemStack item : contents.nonEmptyItems()) {
            if (isGoodFood(item)) return true;
        }
        return false;
    }

    public static boolean shulkerContainsTool(ItemStack shulkerStack) {
        if (!isShulkerBox(shulkerStack)) return false;
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return false;
        for (ItemStack item : contents.nonEmptyItems()) {
            if (isUsableMiningTool(item)) return true;
        }
        return false;
    }

    private int findShulkerBoxWithFoodSlot() {
        if (ctx.player() == null) return -1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int[] slotOrder = new int[36];
        int idx = 0;
        for (int i = 1; i < 9; i++) slotOrder[idx++] = i;
        for (int i = 9; i < 36; i++) slotOrder[idx++] = i;
        slotOrder[idx++] = 0;

        for (int slot : slotOrder) {
            ItemStack stack = inv.get(slot);
            if (shulkerContainsFood(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private int findShulkerBoxWithToolSlot() {
        if (ctx.player() == null) return -1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int[] slotOrder = new int[36];
        int idx = 0;
        for (int i = 1; i < 9; i++) slotOrder[idx++] = i;
        for (int i = 9; i < 36; i++) slotOrder[idx++] = i;
        slotOrder[idx++] = 0;

        for (int slot : slotOrder) {
            ItemStack stack = inv.get(slot);
            if (shulkerContainsTool(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private int findBestHotbarSlotForFood() {
        if (ctx.player() == null) return 1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = 1; i < 9; i++) {
            if (inv.get(i).isEmpty()) return i;
        }
        for (int i = 1; i < 9; i++) {
            if (isBuildingBlock(inv.get(i)) && !isTargetOre(inv.get(i))) return i;
        }
        for (int i = 1; i < 9; i++) {
            ItemStack s = inv.get(i);
            if (!isToolOrEssential(s)) {
                return i;
            }
        }
        return 1;
    }

    private void triggerShulkerRetrieveFood(int shulkerSlot) {
        pendingDropSlots.clear();
        shulkerClearingInProgress = true;
        shulkerMode = ShulkerMode.RETRIEVE_FOOD;
        shulkerBoxCountBefore = countShulkerBoxesInInventory();
        logDirect("§6[AutoShulker] Hết đồ ăn trong balo! Phát hiện có đồ ăn trong Shulker Box (slot " + shulkerSlot + "), đang mở để lấy...");
        shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
        shulkerStateTicks = 0;
        shulkerConsecutiveNoTransfer = 0;
        shulkerUntransferableSlots.clear();
        shulkerTransferredCount = 0;
        baritone.getPathingBehavior().cancelSegmentIfSafe();
        baritone.getInputOverrideHandler().clearAllKeys();
    }

    private void triggerShulkerRetrieveTool(int shulkerSlot) {
        pendingDropSlots.clear();
        shulkerClearingInProgress = true;
        shulkerMode = ShulkerMode.RETRIEVE_TOOL;
        shulkerBoxCountBefore = countShulkerBoxesInInventory();
        logDirect("§6[AutoShulker] Hết Cúp trong balo! Phát hiện có Cúp trong Shulker Box (slot " + shulkerSlot + "), đang mở để lấy...");
        shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
        shulkerStateTicks = 0;
        shulkerConsecutiveNoTransfer = 0;
        shulkerUntransferableSlots.clear();
        shulkerTransferredCount = 0;
        baritone.getPathingBehavior().cancelSegmentIfSafe();
        baritone.getInputOverrideHandler().clearAllKeys();
    }

    private PathingCommand handleAutoTool(boolean isSafeToCancel) {
        if (ctx.player() == null || ctx.player().containerMenu != ctx.player().inventoryMenu) {
            return null;
        }
        if (eatingSlot != -1 || shulkerState != ShulkerStorageState.IDLE) {
            return null;
        }

        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        boolean hasUsablePickaxeOnHotbar = false;
        for (int i = 0; i < 9; i++) {
            if (isUsableMiningTool(inv.get(i))) {
                hasUsablePickaxeOnHotbar = true;
                break;
            }
        }

        if (!hasUsablePickaxeOnHotbar) {
            // 1. Tìm cúp tốt nhất trong Balo (slots 9-35)
            int bestBaloSlot = -1;
            double bestSpeed = -1;
            for (int i = 9; i < 36; i++) {
                ItemStack stack = inv.get(i);
                if (isUsableMiningTool(stack)) {
                    double speed = ToolSet.calculateSpeedVsBlock(stack, Blocks.DEEPSLATE.defaultBlockState());
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestBaloSlot = i;
                    }
                }
            }

            if (bestBaloSlot != -1) {
                ItemStack toolStack = inv.get(bestBaloSlot);
                String toolName = toolStack.getHoverName().getString();
                ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, bestBaloSlot, 0, ClickType.SWAP, ctx.player());
                ctx.player().getInventory().setSelectedSlot(0);
                logDirect("§a[AutoTool] Đã lấy Cúp " + toolName + " từ balo ra hotbar ô 1!");
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            // 2. Nếu cả Balo lẫn Hotbar đều hết Cúp: Tìm trong Shulker Box
            int shulkerSlot = findShulkerBoxWithToolSlot();
            if (shulkerSlot != -1) {
                triggerShulkerRetrieveTool(shulkerSlot);
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            } else {
                if (System.currentTimeMillis() - lastNoPickaxeWarningTime > 20000) {
                    logDirect("§c[AutoTool] CẢNH BÁO: Không tìm thấy Cúp nào trong Balo hoặc Shulker Box!");
                    lastNoPickaxeWarningTime = System.currentTimeMillis();
                }
            }
        }

        return null;
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

    public static boolean isToolOrEssential(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // 1. Shulker Box (cấm nhét Shulker vào trong Shulker Box khác)
        if (isShulkerBox(stack)) return true;

        // 2. DataComponents: Mọi công cụ (Tool), vật phẩm có độ bền (Durability/Max Damage), hoặc vũ khí
        if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.MAX_DAMAGE) || stack.isDamageableItem()) {
            return true;
        }

        // 3. ItemTags chuẩn vanilla: Cúp, Rìu, Xẻng, Kiếm, Cuốc, Giáp
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.ARMOR_ENCHANTABLE)) {
            return true;
        }

        // 4. Các vật phẩm sinh tồn & phòng hộ thiết yếu
        Item item = stack.getItem();
        if (item == Items.WATER_BUCKET
                || item == Items.BUCKET
                || item == Items.TOTEM_OF_UNDYING
                || item == Items.SHIELD
                || item == Items.SHEARS
                || item == Items.BOW
                || item == Items.CROSSBOW
                || item == Items.TRIDENT
                || item == Items.FISHING_ROD
                || item == Items.FLINT_AND_STEEL
                || (item instanceof BlockItem bi && bi.getBlock() instanceof TrapDoorBlock)) {
            return true;
        }

        // 5. Thức ăn
        if (isGoodFood(stack) || stack.has(DataComponents.FOOD)) {
            return true;
        }

        // 6. Nhận diện an toàn qua Description ID (hỗ trợ server custom KingMC)
        String desc = item.getDescriptionId();
        if (desc != null) {
            String lower = desc.toLowerCase(Locale.ROOT);
            if (lower.contains("pickaxe") || lower.contains("shovel") || lower.contains("sword")
                    || lower.contains("hoe") || lower.contains("shears") || lower.contains("shield")
                    || lower.contains("helmet") || lower.contains("chestplate") || lower.contains("leggings")
                    || lower.contains("boots") || lower.contains("bow") || lower.contains("totem")
                    || lower.endsWith("_axe") || lower.contains("_axe_") || lower.contains("axe.") || lower.contains(".axe")) {
                return true;
            }
        }

        // 7. Nhận diện an toàn qua Custom Name (tên vật phẩm hiển thị trên KingMC)
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            String customName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (customName.contains("cúp") || customName.contains("cup")
                    || customName.contains("rìu") || customName.contains("riu")
                    || customName.contains("xẻng") || customName.contains("xeng")
                    || customName.contains("kiếm") || customName.contains("kiem")
                    || customName.contains("pickaxe") || customName.contains("axe")
                    || customName.contains("shovel") || customName.contains("sword")
                    || customName.contains("totem") || customName.contains("giáp") || customName.contains("armor")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tìm ô hotbar tối ưu nhất để swap Shulker Box vào đặt ra đất:
     * - Không bao giờ đè vào ô 0 (cúp đào chính).
     * - Ưu tiên ô trống, hoặc ô chứa đồ rác/quặng/đá.
     */
    private int findBestHotbarSlotForShulker() {
        if (ctx.player() == null) return 1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        // 1. Ưu tiên ô hotbar trống (từ slot 1 đến 8)
        for (int h = 1; h < 9; h++) {
            if (inv.get(h).isEmpty()) return h;
        }
        // 2. Ưu tiên ô hotbar chứa đồ không thiết yếu (quặng, đá thừa, rác)
        for (int h = 1; h < 9; h++) {
            if (!shouldKeepInInventory(inv.get(h))) return h;
        }
        // 3. Ưu tiên ô hotbar chứa block xây dựng (đá/đất)
        for (int h = 1; h < 9; h++) {
            if (isBuildingBlock(inv.get(h)) && !isTargetOre(inv.get(h))) return h;
        }
        // 4. Ưu tiên ô không phải công cụ (Cúp, Rìu, Xẻng, Kiếm, Totem, Xô nước)
        for (int h = 1; h < 9; h++) {
            if (!isToolOrEssential(inv.get(h))) return h;
        }
        // 5. Fallback: slot 1
        return 1;
    }

    /**
     * Kiểm tra xem vật phẩm có thuộc diện BẮT BUỘC GIỮ LẠI trong balo khi cất đồ vào Shulker Box hay không.
     * Quy tắc:
     * - BẢO VỆ TUYỆT ĐỐI TOÀN BỘ CÔNG CỤ & TRANG BỊ: Cúp, Rìu, Xẻng, Kiếm, Cuốc, Giáp, Khiên, Cung, Nỏ.
     * - BẢO VỆ TUYỆT ĐỐI VẬT PHẨM SINH TỒN: Totem of Undying, Xô nước, Thức ăn, Trapdoor, Shulker Box.
     * - Block xây dựng (đá, đất...): Giữ lại 1 stack (tối đa 64) để kê chân / bắc cầu.
     * MỌI THỨ KHÁC (kim cương, vàng, sắt, than, redstone, ngọc lục bảo, đá thừa, rác...) đều CẤT HẾT vào Shulker Box!
     */
    private boolean shouldKeepInInventory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;

        // 1. Tuyệt đối giữ lại mọi công cụ (Cúp, Rìu, Xẻng, Kiếm...), đồ thiết yếu (Totem, Xô nước), đồ ăn và Shulker Box
        if (isToolOrEssential(stack)) {
            return true;
        }

        // 2. Block xây dựng (kê chân/bắc cầu): Giữ lại đúng 1 stack (tối đa 64 block)
        // để không bao giờ cạn throwaway blocks (hasThrowaway = false), khớp với handleAutoDrop
        if (isBuildingBlock(stack)) {
            if (ctx.player() == null) return true;
            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            int kept = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.get(i);
                if (isBuildingBlock(s)) {
                    if (s == stack) {
                        return kept < 64;
                    }
                    kept += s.getCount();
                }
            }
            return kept < 64;
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
        Vec3 head = ctx.playerHead();
        AABB playerBox = ctx.player().getBoundingBox();
        float currentYaw = ctx.playerRotations().getYaw();

        class Candidate {
            final ShulkerPlacementTarget target;
            final double score;

            Candidate(ShulkerPlacementTarget target, double score) {
                this.target = target;
                this.score = score;
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        int[] dyLevels = new int[]{0, 1, -1};
        net.minecraft.core.Direction[] horizontalDirs = new net.minecraft.core.Direction[]{
                net.minecraft.core.Direction.NORTH,
                net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.WEST
        };

        // Quét toàn diện 360 độ quanh người chơi trong bán kính 1 - 3 block
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy : dyLevels) {
                    BlockPos target = feet.offset(dx, dy, dz);
                    BlockState targetState = ctx.world().getBlockState(target);
                    if (!targetState.isAir() && !targetState.canBeReplaced()) continue;

                    // Không được đè lên người chơi
                    AABB targetBox = new AABB(target);
                    if (targetBox.intersects(playerBox)) continue;

                    // 1. Ưu tiên đặt trên sàn (Floor placement with face = UP)
                    BlockPos floor = target.below();
                    BlockState floorState = ctx.world().getBlockState(floor);
                    BlockPos above = target.above();
                    BlockState aboveState = ctx.world().getBlockState(above);

                    if (!floorState.isAir() && floorState.isSolid()
                            && !(floorState.getBlock() instanceof ShulkerBoxBlock)
                            && (aboveState.isAir() || aboveState.canBeReplaced())
                            && !new AABB(above).intersects(playerBox)) {

                        Vec3 hitVec = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
                        double dist = head.distanceTo(hitVec);
                        if (dist >= 1.1 && dist <= 3.8) {
                            ClipContext rayCtx = new ClipContext(head, hitVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.player());
                            HitResult hit = ctx.world().clip(rayCtx);
                            if (hit.getType() == HitResult.Type.MISS || (hit instanceof BlockHitResult bhr && (bhr.getBlockPos().equals(floor) || bhr.getBlockPos().equals(target)))) {
                                Rotation rot = RotationUtils.calcRotationFromVec3d(head, hitVec, ctx.playerRotations());
                                float yawDiff = Math.abs(rot.getYaw() - currentYaw) % 360.0F;
                                if (yawDiff > 180.0F) yawDiff = 360.0F - yawDiff;

                                double distPenalty = Math.abs(dist - 1.8) * 15.0;
                                double heightPenalty = (dy == 0) ? 0.0 : 25.0;
                                double score = yawDiff * 1.0 + distPenalty + heightPenalty;

                                candidates.add(new Candidate(new ShulkerPlacementTarget(target, floor, net.minecraft.core.Direction.UP), score));
                            }
                        }
                    }

                    // 2. Dự phòng: Đặt áp vào vách tường (Wall placement) nếu không có sàn
                    for (net.minecraft.core.Direction wallDir : horizontalDirs) {
                        BlockPos wall = target.relative(wallDir);
                        BlockState wallState = ctx.world().getBlockState(wall);
                        BlockPos openDirPos = target.relative(wallDir.getOpposite());
                        BlockState openDirState = ctx.world().getBlockState(openDirPos);

                        if (!wallState.isAir() && wallState.isSolid()
                                && !(wallState.getBlock() instanceof ShulkerBoxBlock)
                                && (openDirState.isAir() || openDirState.canBeReplaced())
                                && !new AABB(openDirPos).intersects(playerBox)) {

                            Vec3 hitVec = new Vec3(
                                    wall.getX() + 0.5 + wallDir.getOpposite().getStepX() * 0.5,
                                    wall.getY() + 0.5,
                                    wall.getZ() + 0.5 + wallDir.getOpposite().getStepZ() * 0.5
                            );
                            double dist = head.distanceTo(hitVec);
                            if (dist >= 1.1 && dist <= 3.8) {
                                ClipContext rayCtx = new ClipContext(head, hitVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.player());
                                HitResult hit = ctx.world().clip(rayCtx);
                                if (hit.getType() == HitResult.Type.MISS || (hit instanceof BlockHitResult bhr && (bhr.getBlockPos().equals(wall) || bhr.getBlockPos().equals(target)))) {
                                    Rotation rot = RotationUtils.calcRotationFromVec3d(head, hitVec, ctx.playerRotations());
                                    float yawDiff = Math.abs(rot.getYaw() - currentYaw) % 360.0F;
                                    if (yawDiff > 180.0F) yawDiff = 360.0F - yawDiff;

                                    double distPenalty = Math.abs(dist - 1.8) * 15.0;
                                    double score = yawDiff * 1.0 + distPenalty + 50.0;

                                    candidates.add(new Candidate(new ShulkerPlacementTarget(target, wall, wallDir.getOpposite()), score));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        candidates.sort(Comparator.comparingDouble(c -> c.score));
        return Optional.of(candidates.get(0).target);
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

            int transferableSlots = countTransferableSlots();

            // ĐIỀU KIỆN KÍCH HOẠT AUTO SHULKER:
            // CHỈ kích hoạt khi:
            // 1. Phải có ít nhất 1 stack quặng cần cất (transferableSlots > 0)
            // 2. Balo thực sự ĐÃ ĐẦY: chỉ còn tối đa 1 ô trống (emptySlots <= 1)
            // Tuyệt đối KHÔNG cất khi balo còn nhiều ô trống!
            boolean shouldStore = transferableSlots > 0 && emptySlots <= 1;
            if (shouldStore) {
                int shulkerSlot = findBestShulkerBoxSlot();
                if (shulkerSlot != -1) {
                    pendingDropSlots.clear(); // Hủy toàn bộ hàng đợi vứt rác cũ để tránh race condition
                    shulkerClearingInProgress = true;
                    shulkerBoxCountBefore = countShulkerBoxesInInventory();
                    int occupied = getShulkerOccupiedSlots(inv.get(shulkerSlot));
                    String slotDesc = (occupied == 0) ? "trống 100%" : (occupied + "/27 ô đã dùng");
                    logDirect("§a[AutoShulker] Balo đã đầy (còn " + emptySlots + " ô trống)! Tự động cất " + transferableSlots + " stack quặng vào Shulker Box (" + slotDesc + " tại slot " + shulkerSlot + ")...");
                    shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
                    shulkerStateTicks = 0;
                    shulkerConsecutiveNoTransfer = 0;
                    shulkerUntransferableSlots.clear();
                    shulkerTransferredCount = 0;
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
                        logDirect("§c[AutoShulker] Toàn bộ Shulker Box trong balo đều đã đầy (27/27 ô)! Không thể cất thêm đồ.");
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
                int slot = -1;
                if (shulkerMode == ShulkerMode.RETRIEVE_FOOD) {
                    slot = findShulkerBoxWithFoodSlot();
                } else if (shulkerMode == ShulkerMode.RETRIEVE_TOOL) {
                    slot = findShulkerBoxWithToolSlot();
                } else {
                    slot = findBestShulkerBoxSlot();
                }

                if (slot == -1) {
                    if (shulkerMode == ShulkerMode.RETRIEVE_FOOD) {
                        logDirect("§e[AutoShulker] Không tìm thấy Shulker Box chứa đồ ăn trong balo! Hủy quy trình.");
                    } else if (shulkerMode == ShulkerMode.RETRIEVE_TOOL) {
                        logDirect("§e[AutoShulker] Không tìm thấy Shulker Box chứa Cúp trong balo! Hủy quy trình.");
                    } else {
                        logDirect("§e[AutoShulker] Không tìm thấy Shulker Box còn chỗ trống trong balo! Hủy quy trình.");
                    }
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerMode = ShulkerMode.DEPOSIT;
                    shulkerBoxCountBefore = 0;
                    shulkerUntransferableSlots.clear();
                    return null;
                }
                if (shulkerBoxCountBefore <= 0) {
                    shulkerBoxCountBefore = countShulkerBoxesInInventory();
                }
                if (slot < 9 && slot > 0) {
                    shulkerHotbarSlot = slot;
                    shulkerState = ShulkerStorageState.SELECT_SLOT;
                    shulkerStateTicks = 0;
                } else {
                    // Swap vào ô hotbar tốt nhất (slot 1-8, không bao giờ đè cúp slot 0)
                    shulkerHotbarSlot = findBestHotbarSlotForShulker();
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, slot, shulkerHotbarSlot, ClickType.SWAP, ctx.player());
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
                    // Tự động xoay quanh 360 độ để quét tìm vị trí đặt Shulker Box
                    float sweepYaw = (ctx.playerRotations().getYaw() + 30.0F) % 360.0F;
                    Rotation sweepRot = new Rotation(sweepYaw, 25.0F);
                    baritone.getLookBehavior().updateTarget(sweepRot, true);
                    if (!LookBehavior.isF5(ctx)) {
                        ctx.player().setYRot(sweepYaw);
                        ctx.player().setXRot(25.0F);
                    }

                    if (shulkerStateTicks > 24) { // Đã xoay hơn 1 vòng 360 độ (24 ticks = 720 độ) mà vẫn không có chỗ
                        logDirect("§c[AutoShulker] Đã xoay 360 độ nhưng không tìm thấy vị trí thích hợp để đặt Shulker Box! Hủy quy trình...");
                        shulkerState = ShulkerStorageState.IDLE;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                ShulkerPlacementTarget pt = targetOpt.get();
                Vec3 hitVec = pt.face == net.minecraft.core.Direction.UP
                        ? new Vec3(pt.againstPos.getX() + 0.5, pt.againstPos.getY() + 1.0, pt.againstPos.getZ() + 0.5)
                        : new Vec3(pt.againstPos.getX() + 0.5 + pt.face.getStepX() * 0.5, pt.againstPos.getY() + 0.5, pt.againstPos.getZ() + 0.5 + pt.face.getStepZ() * 0.5);
                Rotation aimRot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), hitVec, ctx.playerRotations());
                baritone.getLookBehavior().updateTarget(aimRot, true);
                if (!LookBehavior.isF5(ctx)) {
                    ctx.player().setYRot(aimRot.getYaw());
                    ctx.player().setXRot(aimRot.getPitch());
                }

                shulkerPlacedPos = new BlockPos(pt.placePos.getX(), pt.placePos.getY(), pt.placePos.getZ());
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
                Vec3 hitVec = pt.face == net.minecraft.core.Direction.UP
                        ? new Vec3(againstPure.getX() + 0.5, againstPure.getY() + 1.0, againstPure.getZ() + 0.5)
                        : new Vec3(againstPure.getX() + 0.5 + pt.face.getStepX() * 0.5, againstPure.getY() + 0.5, againstPure.getZ() + 0.5 + pt.face.getStepZ() * 0.5);
                BlockHitResult bhr = new BlockHitResult(hitVec, pt.face, againstPure, false);
                Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), hitVec, ctx.playerRotations());
                baritone.getLookBehavior().updateTarget(rot, true);
                if (!LookBehavior.isF5(ctx)) {
                    ctx.player().setYRot(rot.getYaw());
                    ctx.player().setXRot(rot.getPitch());
                }
                ctx.player().getInventory().setSelectedSlot(shulkerHotbarSlot);
                ctx.playerController().syncHeldItem();
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
                    shulkerUntransferableSlots.clear();
                    shulkerTransferredCount = 0;
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

                // === CHẾ ĐỘ 1: LẤY ĐỒ ĂN TỪ TRONG SHULKER BOX RA BALO ===
                if (shulkerMode == ShulkerMode.RETRIEVE_FOOD) {
                    int foodSlot = -1;
                    for (int b = 0; b < 27; b++) {
                        ItemStack boxItem = ctx.player().containerMenu.getSlot(b).getItem();
                        if (isGoodFood(boxItem)) {
                            foodSlot = b;
                            break;
                        }
                    }
                    if (foodSlot != -1 && shulkerTransferredCount < 2) {
                        ItemStack before = ctx.player().containerMenu.getSlot(foodSlot).getItem().copy();
                        ctx.playerController().windowClick(containerId, foodSlot, 0, ClickType.QUICK_MOVE, ctx.player());
                        ItemStack after = ctx.player().containerMenu.getSlot(foodSlot).getItem();
                        if (before.getCount() == after.getCount()) {
                            logDirect("§c[AutoShulker] Balo đã đầy, không thể lấy thêm đồ ăn từ Shulker Box!");
                            shulkerState = ShulkerStorageState.CLOSE_CONTAINER;
                            shulkerStateTicks = 0;
                            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                        shulkerTransferredCount++;
                        int taken = before.getCount() - after.getCount();
                        logDirect("§a[AutoShulker] Đã lấy " + before.getHoverName().getString() + " (x" + taken + ") từ Shulker Box vào balo!");
                        shulkerTransferCooldown = 2;
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                    shulkerState = ShulkerStorageState.CLOSE_CONTAINER;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // === CHẾ ĐỘ 2: LẤY CÚP/TOOL TỪ TRONG SHULKER BOX RA BALO ===
                if (shulkerMode == ShulkerMode.RETRIEVE_TOOL) {
                    int toolSlot = -1;
                    for (int b = 0; b < 27; b++) {
                        ItemStack boxItem = ctx.player().containerMenu.getSlot(b).getItem();
                        if (isUsableMiningTool(boxItem)) {
                            toolSlot = b;
                            break;
                        }
                    }
                    if (toolSlot != -1 && shulkerTransferredCount < 2) {
                        ItemStack before = ctx.player().containerMenu.getSlot(toolSlot).getItem().copy();
                        ctx.playerController().windowClick(containerId, toolSlot, 0, ClickType.QUICK_MOVE, ctx.player());
                        ItemStack after = ctx.player().containerMenu.getSlot(toolSlot).getItem();
                        if (before.getCount() == after.getCount()) {
                            logDirect("§c[AutoShulker] Balo đã đầy, không thể lấy thêm Cúp từ Shulker Box!");
                            shulkerState = ShulkerStorageState.CLOSE_CONTAINER;
                            shulkerStateTicks = 0;
                            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                        shulkerTransferredCount++;
                        logDirect("§a[AutoShulker] Đã lấy Cúp " + before.getHoverName().getString() + " từ Shulker Box vào balo!");
                        shulkerTransferCooldown = 2;
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                    shulkerState = ShulkerStorageState.CLOSE_CONTAINER;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // === CHẾ ĐỘ 3: MẶC ĐỊNH - CẤT QUẶNG & ĐỒ VÀO SHULKER BOX ===
                int transferSlot = -1;

                // Quét toàn bộ balo và hotbar người chơi trong ContainerMenu (slot 27 đến 62)
                for (int slotId = 27; slotId < 63; slotId++) {
                    // Luôn bảo vệ ô hotbar slot 0 (chứa cúp đào chính, tương ứng slotId 54)
                    if (slotId == 54) continue;

                    ItemStack stack = ctx.player().containerMenu.getSlot(slotId).getItem();
                    if (stack.isEmpty()) continue;
                    // BỎ QUA các món thiết yếu: Cúp, Totem, Xô nước, Đồ ăn (và Shulker Box)
                    if (shouldKeepInInventory(stack)) continue;
                    // BỎ QUA block xây dựng thông thường (không nhét đá/đất vào Shulker Box trừ khi là mục tiêu đào)
                    if (isBuildingBlock(stack) && !isTargetOre(stack)) continue;
                    // BỎ QUA ô đã thử mà không thể chuyển vào Shulker Box (shulker đã đầy hoặc từ chối)
                    if (shulkerUntransferableSlots.contains(slotId)) continue;

                    transferSlot = slotId;
                    break;
                }

                if (transferSlot != -1) {
                    ItemStack before = ctx.player().containerMenu.getSlot(transferSlot).getItem().copy();
                    ctx.playerController().windowClick(containerId, transferSlot, 0, ClickType.QUICK_MOVE, ctx.player());
                    ItemStack after = ctx.player().containerMenu.getSlot(transferSlot).getItem();
                    if (before.getCount() == after.getCount()) {
                        shulkerConsecutiveNoTransfer++;
                        if (shulkerConsecutiveNoTransfer >= 2) {
                            // Không chuyển được (hộp Shulker không còn chỗ chứa món này)
                            shulkerUntransferableSlots.add(transferSlot);
                            shulkerConsecutiveNoTransfer = 0;
                        }
                    } else {
                        shulkerConsecutiveNoTransfer = 0;
                        shulkerTransferredCount++;
                    }
                    shulkerTransferCooldown = 2; // Nhịp 2 tick (0.1s) mượt mà chống kick packet
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Khi đã duyệt hết và không còn món nào có thể chuyển thêm:
                boolean isBoxFull = true;
                for (int b = 0; b < 27; b++) {
                    ItemStack boxItem = ctx.player().containerMenu.getSlot(b).getItem();
                    if (boxItem.isEmpty() || boxItem.getCount() < boxItem.getMaxStackSize()) {
                        isBoxFull = false;
                        break;
                    }
                }

                if (isBoxFull) {
                    logDirect("§6[AutoShulker] Shulker Box đã đầy (27/27 ô)! Đã cất " + shulkerTransferredCount + " stack.");
                } else {
                    logDirect("§a[AutoShulker] Đã cất gọn " + shulkerTransferredCount + " stack vào Shulker Box (giữ nguyên Công cụ, Cúp, Rìu, Xẻng, Totem, Xô nước & Đồ ăn)!");
                }
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
                    shulkerUntransferableSlots.clear();
                    shulkerClearingInProgress = false;
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
                    shulkerPlacedPos = null;
                    shulkerStateTicks = 0;
                    shulkerBoxCountBefore = 0;
                    shulkerUntransferableSlots.clear();

                    // Nếu vừa lấy thức ăn hoặc cúp: Hoàn tất quy trình, quay lại đào tiếp
                    if (shulkerMode == ShulkerMode.RETRIEVE_FOOD || shulkerMode == ShulkerMode.RETRIEVE_TOOL) {
                        shulkerClearingInProgress = false;
                        shulkerState = ShulkerStorageState.IDLE;
                        shulkerMode = ShulkerMode.DEPOSIT;
                        pendingDropSlots.clear();
                        return null;
                    }

                    // Kiểm tra xem người chơi còn món nào cần cất vào Shulker Box tiếp theo không
                    int remainingTransferable = countTransferableSlots();
                    int nextShulkerSlot = findBestShulkerBoxSlot();
                    if (shulkerClearingInProgress && remainingTransferable > 0 && nextShulkerSlot != -1) {
                        logDirect("§a[AutoShulker] Còn " + remainingTransferable + " ô vật phẩm cần cất! Tiếp tục mở Shulker Box tiếp theo...");
                        shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }

                    shulkerClearingInProgress = false;
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerMode = ShulkerMode.DEPOSIT;
                    pendingDropSlots.clear();
                    logDirect("§a[AutoShulker] Đã cất toàn bộ vật phẩm vào Shulker Box (giữ nguyên Công cụ, Cúp, Rìu, Xẻng, Totem, Xô nước & Đồ ăn)! Tiếp tục đào...");
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
                    shulkerUntransferableSlots.clear();
                    shulkerClearingInProgress = false;
                    shulkerMode = ShulkerMode.DEPOSIT;
                }

                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        return null;
    }

    private void handleAntiStuck() {
        if (ctx.player() == null || eatingSlot != -1 || shulkerState != ShulkerStorageState.IDLE) {
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
            double totalDist = 0;
            for (int i = 0; i < RECENT_POS_BUFFER_SIZE; i++) {
                BetterBlockPos p = recentPositions[i];
                if (p == null) continue;
                if (p.x < minX) minX = p.x;
                if (p.x > maxX) maxX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.y > maxY) maxY = p.y;
                if (p.z < minZ) minZ = p.z;
                if (p.z > maxZ) maxZ = p.z;
                if (i > 0) {
                    BetterBlockPos prev = recentPositions[i - 1];
                    if (prev != null) {
                        double ddx = p.x - prev.x;
                        double ddy = p.y - prev.y;
                        double ddz = p.z - prev.z;
                        totalDist += Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    }
                }
            }
            double spanX = maxX - minX;
            double spanY = maxY - minY;
            double spanZ = maxZ - minZ;
            double maxSpan = Math.max(spanX, Math.max(spanY, spanZ));
            // Chỉ coi là ping-pong dao động qua lại nếu thực sự di chuyển (totalDist >= 4.0) trong phạm vi hẹp <= 2.5
            if (maxSpan <= 2.5 && totalDist >= 4.0) {
                pingPongDetected = true;
            }
        }

        // Đang đào block hoặc client đang trực tiếp đập block thì KHÔNG tính là bị kẹt
        boolean isHitting = ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).isHittingBlock();
        boolean isClickingLeft = baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT);
        boolean isMining = (activeMiningBlock != null && activeMiningTicks <= 80) || isHitting || isClickingLeft;
        if (isMining) {
            stuckTicks = 0;
            return;
        }

        // Kiểm tra di chuyển: Bất kỳ thay đổi vị trí block nào (ngang hoặc dọc) đều tính là đã di chuyển
        boolean moved = lastStuckCheckPos != null && !currentFeet.equals(lastStuckCheckPos);
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
            if (lastAntiStuckPos != null && currentFeet.distSqr(lastAntiStuckPos) >= 4) {
                stuckRetries = 0;
                lastAntiStuckPos = null;
                lastStuckOrePos = null;
            }
            // Đã thực sự di chuyển sang block khác → Cho phép nhảy+đặt block trở lại ngay lập tức
            if (Baritone.settings().noPillar.value) {
                pillarFailCount = 0;
                lastPillarFailPos = null;
                Baritone.settings().noPillar.value = false;
                logDirect("§a[AntiPillarLoop] Đã di chuyển sang block khác, cho phép nhảy+đặt block trở lại.");
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
                if (now - lastPillarFailTime > 800) { // ít nhất 800ms giữa các lần nhảy riêng biệt
                    lastPillarFailTime = now;
                    pillarFailCount++;
                    if (pillarFailCount >= 4) {
                        Baritone.settings().noPillar.value = true;
                        lastPillarFailPos = currentFeet;
                        logDirect("§c[AntiPillarLoop] Bot bị kẹt nhảy+đặt block dưới chân (" + pillarFailCount + " lần)! Tạm tắt pillar, đổi hướng tiếp cận...");
                        forceReroute = true;
                        stuckTicks = 0;
                        return;
                    }
                }
            }
        }

        // === PHÁT HIỆN KẸT HÀNH ĐỘNG QUÁ LÂU (>= 200 tick = 10s) HOẶC VÒNG LẶP ĐẶT/ĐÀO HOẶC PING-PONG ===
        if (stuckTicks >= 200 || placeBreakOscillationCount >= 2 || pingPongDetected) {
            if (pingPongDetected) {
                logDirect("§c[AntiStuck] Phát hiện dao động qua lại (ping-pong) trong phạm vi <= 2.5 block (> 10s)! Giải kẹt ngay...");
            } else if (placeBreakOscillationCount >= 2) {
                logDirect("§c[AntiStuck] Phát hiện vòng lặp đặt block rồi đào xuống! Đổi hướng ngay...");
            } else {
                logDirect("§c[AntiStuck] Bị kẹt đứng yên quá 10s (200 ticks)! Giải kẹt ngay...");
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
                    // Xử lý quặng mục tiêu trong bán kính 16 block (distSq <= 256)
                    if (distSq <= 256) {
                        if (lastStuckOrePos == null || !lastStuckOrePos.equals(pos)) {
                            lastStuckOrePos = pos;
                            stuckRetries = 1;
                        }
                        if (stuckRetries < 2) {
                            lockedTargetOre = null;
                            forceReroute = true;
                            baritone.getPathingBehavior().cancelSegmentIfSafe();
                            logDirect("§e[AntiStuck] Thử đổi hướng tiếp cận quặng tại " + pos.toShortString() + " (lần 1)...");
                            return;
                        } else {
                            // Đã thử nhưng vẫn kẹt: Thêm NGAY LẬP TỨC toàn bộ vỉa quặng vào BLACKLIST để không bao giờ đào lại!
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
                            logDirect("§c[AntiStuck] Quặng tại " + pos.toShortString() + " (" + veinOres.size() + " block) không thể tiếp cận/đào được! ĐÃ THÊM VÀO BLACKLIST VĨNH VIỄN!");
                            lockedTargetOre = null;
                            forceReroute = true;
                            stuckRetries = 0;
                            lastStuckOrePos = null;
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
                        shaftOriginPos = null;
                    } else {
                        int escapeY = Math.max(-54, currentFeet.y + 2);
                        logDirect("§c[AntiStuck] Kẹt đào dốc cả 4 hướng! Kích hoạt thoát hiểm lên tầng Y=" + escapeY + " để tìm lối khác...");
                        bedrockEscapeActive = true;
                        bedrockEscapeOrigin = currentFeet;
                        bedrockEscapeTargetY = escapeY;
                        bedrockEscapeTicks = 0;
                    }
                    stairOriginPos = null;
                    tunnelOriginPos = null;
                    branchPoint = null;
                    branchPointRunaway = null;
                    forceReroute = true;
                    return;
                }
                net.minecraft.core.Direction newDir = tunnelDirection.getClockWise();
                tunnelDirection = newDir;
                stairOriginPos = null;
                shaftOriginPos = null;
                tunnelOriginPos = null;
                branchPoint = currentFeet.relative(newDir.getOpposite(), 16);
                branchPointRunaway = null;
                logDirect("§6[AntiStuck] Gặp vật cản khi đào dốc xuống (thử " + stuckRetries + "/4)! Tự động đổi hướng đào sang " + newDir.getName().toUpperCase() + "...");
                forceReroute = true;
                return;
            }

            // 3. Đào hầm tại tầng đáy bị kẹt bedrock:
            // Nếu đang trong chế độ thoát bedrock thì không hủy và không đổi hướng, giữ nguyên để tiếp tục leo lên tầng an toàn:
            if (bedrockEscapeActive) {
                stuckRetries = 0;
                forceReroute = true;
                return;
            }

            if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
                net.minecraft.core.Direction dir = ctx.player().getDirection();
                tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
            }
            // Sau 4 lần thử (đã xoay cả 4 hướng) mà vẫn kẹt = toàn Bedrock -> Kích hoạt cơ chế thoát Bedrock lên tầng an toàn (safeY >= -54)
            if (stuckRetries >= 4) {
                int safeY = Math.max(-54, targetY + 4);
                logDirect("§c[AntiStuck] Bị kẹt bedrock cả 4 hướng! Kích hoạt cơ chế thoát bedrock lên tầng an toàn Y=" + safeY + "...");
                bedrockEscapeActive = true;
                bedrockEscapeOrigin = currentFeet;
                bedrockEscapeTargetY = safeY;
                bedrockEscapeTicks = 0;
                stairOriginPos = null;
                shaftOriginPos = null;
                tunnelOriginPos = null;
                branchPoint = null;
                branchPointRunaway = null;
                stuckRetries = 0;
                Baritone.settings().noPillar.value = false;
                pillarFailCount = 0;
                forceReroute = true;
                return;
            }
            net.minecraft.core.Direction newDir = tunnelDirection.getClockWise();
            tunnelDirection = newDir;
            tunnelOriginPos = null;
            stairOriginPos = null;
            shaftOriginPos = null;
            branchPoint = currentFeet.relative(newDir.getOpposite(), 16);
            branchPointRunaway = null;
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

        // 1. Dưới 7 cục thịt đói (foodLevel <= 14)
        // 2. Mất máu / yếu máu (health < maxHealth && foodLevel < 20)
        boolean lowHunger = foodLevel <= 14;
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

            // 2. If not in hotbar, search main inventory (balo, slots 9 to 35) and swap to hotbar
            if (targetHotbarSlot == -1 && ctx.player().containerMenu == ctx.player().inventoryMenu) {
                int foodBaloSlot = -1;
                for (int i = 9; i < 36; i++) {
                    if (isGoodFood(inv.get(i))) {
                        foodBaloSlot = i;
                        break;
                    }
                }
                if (foodBaloSlot != -1) {
                    targetHotbarSlot = findBestHotbarSlotForFood();
                    ItemStack foodStack = inv.get(foodBaloSlot);
                    String foodName = foodStack.getHoverName().getString();
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, foodBaloSlot, targetHotbarSlot, ClickType.SWAP, ctx.player());
                    logDirect("§a[AutoEat] Đã lấy " + foodName + " từ balo ra hotbar ô " + (targetHotbarSlot + 1) + " để ăn!");
                }
            }

            // 3. If STILL not found (cả hotbar lẫn balo đều không còn đồ ăn): Tìm trong Shulker Box!
            if (targetHotbarSlot == -1 && shulkerState == ShulkerStorageState.IDLE) {
                int shulkerSlot = findShulkerBoxWithFoodSlot();
                if (shulkerSlot != -1) {
                    triggerShulkerRetrieveFood(shulkerSlot);
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
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
                String foodName = ctx.player().getInventory().getItem(targetHotbarSlot).getHoverName().getString();
                logDirect("§a[AutoEat] Bắt đầu ăn: " + foodName + " (Máu: " + (int)health + "/" + (int)maxHealth + " | Đói: " + (foodLevel / 2) + " cục)");
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
                    10,
                    16
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

                // Không nhắm vào quặng ở chunk chưa load nếu ở xa hơn 48 block (distSqr > 2304) để tránh nghẽn pathfinding
                .filter(pos -> ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ()) || pos.distSqr(ctx.getBaritone().getPlayerContext().playerFeet()) <= 2304)

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

                // LỌC TẦNG CAO: Không nhắm vào quặng quá cao so với tầng đào hầm thực tế
                .filter(pos -> {
                    int targetY = Baritone.settings().legitMineYLevel.value;
                    int playerY = ctx.getBaritone().getPlayerContext().playerFeet().y;
                    if (playerY <= targetY + 3) {
                        // Đã ở tầng đào hầm đáy (targetY, ví dụ Y=-58): Chỉ đào quặng trong tầm với của hầm (Y <= targetY + 6)
                        return pos.getY() <= targetY + 6;
                    } else {
                        // Đang trên đường đào dốc đi xuống: Không bao giờ quay ngược lên đào quặng cao hơn vị trí hiện tại
                        return pos.getY() <= playerY + 3;
                    }
                })

                // Né xa toàn bộ quặng nằm trong vùng nguy hiểm của lồng Spawner (mặc định 16 block)
                .filter(pos -> !isNearSpawner(ctx, pos, Baritone.settings().mobSpawnerAvoidanceRadius.value))

                .sorted((a, b) -> {
                    BlockPos p = ctx.getBaritone().getPlayerContext().player().blockPosition();
                    // Phạt nặng chênh lệch độ cao Y (* 5.0) để ưu tiên quặng ngang tầng đào hiện tại
                    double dyA = (a.getY() - p.getY()) * 5.0;
                    double distA = Math.pow(a.getX() - p.getX(), 2) + Math.pow(dyA, 2) + Math.pow(a.getZ() - p.getZ(), 2);
                    double dyB = (b.getY() - p.getY()) * 5.0;
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

    public static boolean isNearSpawner(CalculationContext ctx, BlockPos pos, int radius) {
        if (ctx == null || ctx.world == null || pos == null || radius <= 0) {
            return false;
        }
        int radiusSq = radius * radius;
        // 1. Kiểm tra trong CachedWorld
        String spawnerName = BlockUtils.blockToString(Blocks.SPAWNER);
        if (ctx.worldData != null && ctx.worldData.getCachedWorld() != null) {
            List<BlockPos> cached = ctx.worldData.getCachedWorld().getLocationsOf(spawnerName, 1, pos.getX(), pos.getZ(), 2);
            for (BlockPos sp : cached) {
                if (sp.distSqr(pos) <= radiusSq) {
                    return true;
                }
            }
            List<BlockPos> cachedLegacy = ctx.worldData.getCachedWorld().getLocationsOf("mob_spawner", 1, pos.getX(), pos.getZ(), 2);
            for (BlockPos sp : cachedLegacy) {
                if (sp.distSqr(pos) <= radiusSq) {
                    return true;
                }
            }
        }
        // 2. Kiểm tra trong loaded chunks quanh pos
        if (ctx.bsi != null && ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
            int minCx = (pos.getX() - radius) >> 4;
            int maxCx = (pos.getX() + radius) >> 4;
            int minCz = (pos.getZ() - radius) >> 4;
            int maxCz = (pos.getZ() + radius) >> 4;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    net.minecraft.world.level.chunk.LevelChunk chunk = ctx.world.getChunkSource().getChunk(cx, cz, false);
                    if (chunk != null && !chunk.isEmpty()) {
                        for (BlockPos bp : chunk.getBlockEntitiesPos()) {
                            if (ctx.bsi.get0(bp).is(Blocks.SPAWNER)) {
                                if (bp.distSqr(pos) <= radiusSq) {
                                    return true;
                                }
                            }
                        }
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
        if (state.is(Blocks.SPAWNER) || isNearSpawner(ctx, pos, 6)) {
            return false;
        }
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
        if (isWoodFilter(filter)) {
            this.isChopMode = true;
        }
        this.activeChopTourGoal = null;
        this.desiredQuantity = quantity;
        this.knownOreLocations = new CopyOnWriteArrayList<>();
        this.blacklist.clear();
        this.oreMemory.clear();
        this.branchPoint = null;
        this.branchPointRunaway = null;
        this.anticipatedDrops = new HashMap<>();
        this.currentTunnelTarget = null;
        this.bedrockEscapeActive = false;
        this.bedrockEscapeOrigin = null;
        this.bedrockEscapeTargetY = -54;
        this.bedrockEscapeTicks = 0;
        this.tunnelOriginPos = null;
        this.stairOriginPos = null;
        this.shaftOriginPos = null;
        this.pillarFailCount = 0;
        this.hasReachedTargetY = ctx.player() != null && ctx.playerFeet().y <= Baritone.settings().legitMineYLevel.value + 1;
        this.activeMiningBlock = null;
        this.activeMiningTicks = 0;
        this.lockedTargetOre = null;
        this.stuckTicks = 0;
        this.stuckRetries = 0;
        this.placeBreakOscillationCount = 0;
        this.placedThisCycle = false;
        Arrays.fill(this.recentPositions, null);
        this.recentPosIndex = 0;
        this.recentPosCount = 0;
        this.lastAntiStuckPos = null;
        this.lastPillarFailPos = null;
        this.lastStuckOrePos = null;
        this.shulkerState = ShulkerStorageState.IDLE;
        this.shulkerPlacedPos = null;
        this.shulkerStateTicks = 0;
        this.shulkerBoxCountBefore = 0;
        this.shulkerUntransferableSlots.clear();
        this.shulkerTransferredCount = 0;
        this.shulkerClearingInProgress = false;
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

    public static class GoalDirectionalTunnel implements Goal {
        public final int startX, startZ;
        public final int targetY;
        public final int dx, dz;

        public GoalDirectionalTunnel(BlockPos origin, net.minecraft.core.Direction dir, int targetY) {
            this.startX = origin.getX();
            this.startZ = origin.getZ();
            this.targetY = targetY;
            this.dx = dir.getStepX();
            this.dz = dir.getStepZ();
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            int distFwd = (x - startX) * dx + (z - startZ) * dz;
            int distSide = Math.abs((x - startX) * dz) + Math.abs((z - startZ) * dx);
            return distFwd >= 16 && distSide <= 2 && y >= targetY && y <= targetY + 2;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            int distFwd = (x - startX) * dx + (z - startZ) * dz;
            int remainingFwd = Math.max(0, 16 - distFwd);
            int distSide = Math.abs((x - startX) * dz) + Math.abs((z - startZ) * dx);
            int vertDev = Math.abs(y - targetY);

            return remainingFwd * 5.0 + distSide * 6.0 + vertDev * 10.0;
        }

        @Override
        public double heuristic() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GoalDirectionalTunnel)) return false;
            GoalDirectionalTunnel that = (GoalDirectionalTunnel) o;
            return startX == that.startX && startZ == that.startZ && targetY == that.targetY && dx == that.dx && dz == that.dz;
        }

        @Override
        public int hashCode() {
            return Objects.hash(startX, startZ, targetY, dx, dz);
        }

        @Override
        public String toString() {
            return "GoalDirectionalTunnel{start=" + startX + "," + startZ + ", dir=" + dx + "," + dz + ", targetY=" + targetY + "}";
        }
    }

    public static class GoalStaircaseDescent implements Goal {
        public final int startX, startY, startZ;
        public final int targetY;
        public final int dx, dz;

        public GoalStaircaseDescent(BlockPos origin, net.minecraft.core.Direction dir, int targetY) {
            this.startX = origin.getX();
            this.startY = origin.getY();
            this.startZ = origin.getZ();
            this.targetY = targetY;
            this.dx = dir.getStepX();
            this.dz = dir.getStepZ();
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            int distFwd = (x - startX) * dx + (z - startZ) * dz;
            int distSide = Math.abs((x - startX) * dz) + Math.abs((z - startZ) * dx);
            return distSide <= 2 && (y <= targetY || distFwd >= 12);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            int distFwd = (x - startX) * dx + (z - startZ) * dz;
            int distSide = Math.abs((x - startX) * dz) + Math.abs((z - startZ) * dx);
            int idealY = Math.max(targetY, startY - distFwd);
            int vertDev = Math.abs(y - idealY);

            int remainingY = Math.max(0, y - targetY);
            int remainingFwd = Math.max(0, (startY - targetY) - distFwd);

            return remainingY * 8.0 + remainingFwd * 5.0 + distSide * 6.0 + vertDev * 10.0;
        }

        @Override
        public double heuristic() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GoalStaircaseDescent)) return false;
            GoalStaircaseDescent that = (GoalStaircaseDescent) o;
            return startX == that.startX && startY == that.startY && startZ == that.startZ && targetY == that.targetY && dx == that.dx && dz == that.dz;
        }

        @Override
        public int hashCode() {
            return Objects.hash(startX, startY, startZ, targetY, dx, dz);
        }

        @Override
        public String toString() {
            return "GoalStaircaseDescent{start=" + startX + "," + startY + "," + startZ + ", dir=" + dx + "," + dz + ", targetY=" + targetY + "}";
        }
    }

    public static class TreeInfo {
        public final List<BlockPos> logs = new ArrayList<>();
        public BlockPos baseLog;
        public BetterBlockPos standPos;

        public TreeInfo(BlockPos firstLog) {
            logs.add(firstLog);
            baseLog = firstLog;
        }
    }

    public static List<TreeInfo> clusterTrees(IPlayerContext ctx, List<BlockPos> logPositions) {
        if (logPositions == null || logPositions.isEmpty()) {
            return Collections.emptyList();
        }

        List<TreeInfo> trees = new ArrayList<>();
        List<BlockPos> sorted = new ArrayList<>(logPositions);
        sorted.sort(Comparator.comparingInt(BlockPos::getY));

        for (BlockPos pos : sorted) {
            TreeInfo matchingTree = null;
            for (TreeInfo tree : trees) {
                for (BlockPos existing : tree.logs) {
                    int dx = Math.abs(pos.getX() - existing.getX());
                    int dz = Math.abs(pos.getZ() - existing.getZ());
                    int dy = Math.abs(pos.getY() - existing.getY());
                    if (dx <= 2 && dz <= 2 && dy <= 6) {
                        matchingTree = tree;
                        break;
                    }
                }
                if (matchingTree != null) break;
            }

            if (matchingTree != null) {
                matchingTree.logs.add(pos);
                if (pos.getY() < matchingTree.baseLog.getY()) {
                    matchingTree.baseLog = pos;
                }
            } else {
                trees.add(new TreeInfo(pos));
            }
        }

        for (TreeInfo tree : trees) {
            BlockPos base = tree.baseLog;
            BetterBlockPos bestStand = null;
            double bestDist = Double.MAX_VALUE;

            BlockPos[] neighbors = new BlockPos[] {
                    base.east(), base.west(), base.south(), base.north(),
                    base.east().below(), base.west().below(), base.south().below(), base.north().below()
            };

            for (BlockPos n : neighbors) {
                try {
                    BlockState feetState = ctx.world().getBlockState(n);
                    BlockState headState = ctx.world().getBlockState(n.above());
                    BlockState floorState = ctx.world().getBlockState(n.below());

                    boolean feetPassable = feetState.isAir() || feetState.canBeReplaced() || feetState.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock;
                    boolean headPassable = headState.isAir() || headState.canBeReplaced() || headState.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock;
                    boolean floorSolid = !floorState.isAir() && floorState.isSolid();

                    if (feetPassable && headPassable && floorSolid) {
                        double d = ctx.playerFeet().distSqr(n);
                        if (d < bestDist) {
                            bestDist = d;
                            bestStand = new BetterBlockPos(n);
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (bestStand == null) {
                bestStand = new BetterBlockPos(base);
            }
            tree.standPos = bestStand;
        }

        return trees;
    }

    public static List<TreeInfo> planTreeTour(List<TreeInfo> trees, BetterBlockPos startPos, int maxTrees) {
        if (trees == null || trees.isEmpty()) {
            return Collections.emptyList();
        }

        List<TreeInfo> unvisited = new ArrayList<>(trees);
        List<TreeInfo> tour = new ArrayList<>();
        BetterBlockPos current = startPos;

        int limit = Math.min(unvisited.size(), maxTrees);
        while (!unvisited.isEmpty() && tour.size() < limit) {
            TreeInfo bestTree = null;
            double bestDist = Double.MAX_VALUE;

            for (TreeInfo tree : unvisited) {
                double d = current.distSqr(tree.standPos);
                if (d < bestDist) {
                    bestDist = d;
                    bestTree = tree;
                }
            }

            if (bestTree == null) break;
            tour.add(bestTree);
            unvisited.remove(bestTree);
            current = bestTree.standPos;
        }

        return tour;
    }

    private boolean isWoodFilter(BlockOptionalMetaLookup f) {
        if (f == null || f.blocks().isEmpty()) return false;
        return f.blocks().stream().allMatch(b -> {
            String name = b.getBlock().getDescriptionId().toLowerCase();
            return name.contains("log") || name.contains("wood") || name.contains("stem") || name.contains("hyphae");
        });
    }
}
