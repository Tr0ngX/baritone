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
import baritone.behavior.EmergencySafetyBehavior;
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
import baritone.utils.AutoLogoutTracker;
import baritone.utils.AutoMineScreen;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import baritone.utils.MiningStatsTracker.WoodType;
import baritone.utils.ToolSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
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
    private boolean activeMiningBlockIsObstructing = false;
    private BlockPos pendingOreAfterObstructing = null;
    private int obstructingTransitionTicks = 0;
    private BlockPos shulkerClearOrigin = null;
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
        SHOP_PREPARE_SLOT,
        SHOP_SEND_CMD,
        SHOP_WAIT_MAIN_MENU,
        SHOP_WAIT_END_MENU,
        SHOP_WAIT_CONFIRM_MENU,
        SHOP_WAIT_RECEIVE,
        SHOP_FOOD_PREPARE_SLOT,
        SHOP_FOOD_SEND_CMD,
        SHOP_FOOD_WAIT_MAIN_MENU,
        SHOP_FOOD_WAIT_FOOD_MENU,
        SHOP_FOOD_SET_QUANTITY,
        SHOP_FOOD_WAIT_CONFIRM_MENU,
        SHOP_FOOD_WAIT_RECEIVE,
        SHOP_TOTEM_PREPARE_SLOT,
        SHOP_TOTEM_SEND_CMD,
        SHOP_TOTEM_WAIT_MAIN_MENU,
        SHOP_TOTEM_WAIT_GEAR_MENU,
        SHOP_TOTEM_WAIT_CONFIRM_MENU,
        SHOP_TOTEM_WAIT_RECEIVE,
        CLEAR_SPACE,
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
        WAIT_FOR_PICKUP,
        ENDER_CHEST_SHOP_PREPARE_SLOT,
        ENDER_CHEST_SHOP_SEND_CMD,
        ENDER_CHEST_SHOP_WAIT_MAIN_MENU,
        ENDER_CHEST_SHOP_WAIT_END_MENU,
        ENDER_CHEST_SHOP_WAIT_CONFIRM_MENU,
        ENDER_CHEST_SHOP_WAIT_RECEIVE,
        ENDER_CHEST_CLEAR_SPACE,
        ENDER_CHEST_SWAP_TO_HOTBAR,
        ENDER_CHEST_SELECT_SLOT,
        ENDER_CHEST_PLACE,
        ENDER_CHEST_WAIT_FOR_BLOCK,
        ENDER_CHEST_OPEN,
        ENDER_CHEST_WAIT_FOR_CONTAINER,
        ENDER_CHEST_TRANSFER_SHULKERS,
        ENDER_CHEST_CLOSE_CONTAINER,
        ENDER_CHEST_WAIT_FOR_CLOSE,
        ENDER_CHEST_MINE,
        ENDER_CHEST_WAIT_FOR_PICKUP
    }

    private enum ShulkerMode {
        DEPOSIT,
        RETRIEVE_FOOD,
        RETRIEVE_TOOL,
        RETRIEVE_TOTEM
    }

    private ShulkerStorageState shulkerState = ShulkerStorageState.IDLE;
    private ShulkerMode shulkerMode = ShulkerMode.DEPOSIT;
    private BlockPos shulkerPlacedPos = null;
    private BlockPos enderChestPlacedPos = null;
    private int shulkerStateTicks = 0;
    private int shulkerHotbarSlot = 1;
    private int shulkerOriginalSlot = -1;
    private int enderChestHotbarSlot = 1;
    private int enderChestOriginalSlot = -1;
    private int enderChestTransferredCount = 0;
    private int enderChestCooldownTicks = 0;
    private int enderChestCountBefore = 0;
    private int enderChestShopPurchasedCountBefore = 0;
    private final Set<Integer> blacklistedFullShulkerSlots = new HashSet<>();
    private int shulkerTransferCooldown = 0;
    private int shulkerConsecutiveNoTransfer = 0;
    private int shulkerBoxCountBefore = 0;
    private long lastShulkerFullWarningTime = 0;
    private long lastNoPickaxeWarningTime = 0;
    private final Set<Integer> shulkerUntransferableSlots = new HashSet<>();
    private int shulkerTransferredCount = 0;
    private boolean shulkerClearingInProgress = false;
    private int shopRetryCount = 0;
    private int shopActionCooldown = 0;
    private int shopPurchasedCountBefore = 0;
    private int foodPurchasedCountBefore = 0;
    private int foodCooldownTicks = 0;
    private int totemPurchasedCountBefore = 0;
    private int totemCooldownTicks = 0;
    private int consecutiveCalcFailures = 0;
    private int shaftConsecutiveFailures = 0;
    private int shulkerCooldownTicks = 0;
    private boolean isChopMode = false;
    private final Map<BlockPos, Long> ignoredDrops = new HashMap<>();
    private BlockPos dropAttemptPos = null;
    private int dropAttemptTicks = 0;
    private boolean wasTunneling = false;

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
    public boolean isTargetBlock(BlockState state) {
        if (state == null) return false;
        if (activeMiningBlockIsObstructing && activeMiningBlock != null && ctx.world() != null) {
            if (ctx.world().getBlockState(activeMiningBlock).equals(state)) {
                return true;
            }
        }
        return filter != null && filter.has(state);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        this.tickCount++;
        this.lastCalcFailed = calcFailed;
        int targetY = Baritone.settings().legitMineYLevel.value;
        if (ctx.playerFeet().y <= targetY) {
            hasReachedTargetY = true;
        } else if (ctx.playerFeet().y > targetY + 3) {
            hasReachedTargetY = false;
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
            int currentY = ctx.playerFeet().y;
            boolean isMining = activeMiningBlock != null
                    || baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)
                    || ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).isHittingBlock();
            if (!isMining) {
                if (isChopMode) {
                    // CHẾ ĐỘ CHOP WOOD: TUYỆT ĐỐI KHÔNG DÙNG CẢNH BÁO, KHÔNG CANCEL VÀ KHÔNG STOP!
                    if (lockedTargetOre != null) {
                        blacklist.add(lockedTargetOre);
                        oreMemory.remove(lockedTargetOre);
                        knownOreLocations.remove(lockedTargetOre);
                        lockedTargetOre = null;
                    }
                    forceReroute = true;
                    consecutiveCalcFailures = 0;
                    return new PathingCommand(new GoalRunAway(25, ctx.playerFeet()), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
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
                    if (currentY > targetY && !hasReachedTargetY) {
                        logDirect("§e[Mine] Không thể tìm đường đào dốc xuống sau " + consecutiveCalcFailures + " lần thử! Tạm đổi trục và tiếp tục...");
                    } else {
                        logDirect("§e[Mine] Không thể tìm đường tới các quặng xung quanh sau " + consecutiveCalcFailures + " lần thử! Tạm bỏ qua và tiếp tục đào hầm tiến lên phía trước...");
                    }
                    for (BlockPos p : knownOreLocations) {
                        blacklist.add(p);
                        oreMemory.remove(p);
                    }
                    knownOreLocations.clear();
                    lockedTargetOre = null;
                    if (tunnelDirection == null && ctx.player() != null) {
                        net.minecraft.core.Direction dir = ctx.player().getDirection();
                        tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
                    }
                    branchPoint = (tunnelDirection != null)
                            ? ctx.playerFeet().relative(tunnelDirection.getOpposite(), 16)
                            : ctx.playerFeet();
                    branchPointRunaway = null;
                    forceReroute = true;
                    consecutiveCalcFailures = 0;
                } else if (Baritone.settings().exploreForBlocks.value || Baritone.settings().legitMine.value) {
                    // When exploring/tunneling, never cancel! Just reset origin and continue tunnel
                    if (tunnelDirection == null && ctx.player() != null) {
                        net.minecraft.core.Direction dir = ctx.player().getDirection();
                        tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
                    }
                    branchPoint = (tunnelDirection != null)
                            ? ctx.playerFeet().relative(tunnelDirection.getOpposite(), 16)
                            : ctx.playerFeet();
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
                shaftConsecutiveFailures = 0;
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

        if (Baritone.settings().autoTotem.value && (tickCount % 4 == 0 || (ctx.player() != null && (ctx.player().isInLava() || ctx.player().getHealth() <= 12.0f)))) {
            handleAutoTotem();
        }

        if (handleAutoLogout()) {
            return null;
        }

        updateLoucaSystem();

        // 1. ƯU TIÊN SỐ 1 KHI ĐẦY BALO: Auto-Shulker Box (cất toàn bộ quặng & đá vào Shulker Box thay vì vứt bỏ)
        if (Baritone.settings().autoShulkerStorage.value || Baritone.settings().autoBuyFood.value || Baritone.settings().autoBuyTotem.value || shulkerState != ShulkerStorageState.IDLE) {
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
            int maxReachY = isChopMode ? (ctx.playerFeet().getY() + 4) : (ctx.playerFeet().getY() + 3);
            if (activeMiningBlock.getY() > maxReachY || (!ctx.player().onGround() && !ctx.player().isInWater())) {
                activeMiningBlock = null;
                activeMiningBlockIsObstructing = false;
                activeMiningTicks = 0;
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            } else if (!state.isAir() && (activeMiningBlockIsObstructing || filter == null || filter.has(state))) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, activeMiningBlock);
                if (rot.isPresent()) {
                    activeMiningTicks++;
                    boolean isHitting = ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).isHittingBlock();
                    if (!isHitting && activeMiningTicks > 60) {
                        // Không thể bắt đầu đập từ vị trí/góc nhìn hiện tại sau 3s -> Nhả để A* tiếp tục dẫn đường
                        activeMiningBlock = null;
                        activeMiningBlockIsObstructing = false;
                        activeMiningTicks = 0;
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    } else if (activeMiningTicks > 160) {
                        logDirect("§c[Mine] Block tại " + activeMiningBlock.toShortString() + " không thể đào vỡ sau 8s (có thể do Claim)! Đã thêm vào BLACKLIST!");
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
                        activeMiningBlockIsObstructing = false;
                        activeMiningTicks = 0;
                        forceReroute = true;
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    } else {
                        if (!isChopMode) {
                            baritone.getPathingBehavior().cancelSegmentIfSafe();
                        }
                        clearMovementKeysKeepAttack();
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        MovementHelper.switchToBestToolFor(ctx, state);
                        if (isAimedAtBlock(activeMiningBlock, rot.get())) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                } else {
                    // Chưa thể với tới trực tiếp ở góc nhìn hiện tại -> Nhả activeMiningBlock để A* tiếp tục dẫn đường
                    activeMiningBlock = null;
                    activeMiningBlockIsObstructing = false;
                    activeMiningTicks = 0;
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                }
            } else {
                // Block đã vỡ thành Air (đã bị remove hoàn toàn)
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                if (activeMiningBlockIsObstructing) {
                    logDirect("§a[AutoMine] Block che chắn tại " + activeMiningBlock.toShortString() + " đã bị loại bỏ hoàn toàn! Chờ 3 tick ổn định trước khi đào quặng...");
                    obstructingTransitionTicks = 3; // Cooldown 3 tick cho camera quay mượt, TUYỆT ĐỐI KHÔNG FLICK NGAY!
                } else {
                    logDirect("§a[SmartMind] +100 Điểm thưởng: Đã khai thác thành công quặng tại " + activeMiningBlock.toShortString() + "! Tiếp tục tiến lên.");
                    blacklist.remove(activeMiningBlock);
                    oreMemory.remove(activeMiningBlock);
                    if (knownOreLocations != null) {
                        knownOreLocations.remove(activeMiningBlock);
                    }
                    if (lockedTargetOre != null && lockedTargetOre.equals(activeMiningBlock)) {
                        lockedTargetOre = null;
                    }
                }
                activeMiningBlock = null;
                activeMiningBlockIsObstructing = false;
                activeMiningTicks = 0;
            }
        }

        // 2. Kiểm tra nếu client game đang trực tiếp đập block mục tiêu:
        BlockPos destroyingPos = ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).getCurrentBlock();
        if (destroyingPos != null && ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).isHittingBlock()) {
            BlockState state = ctx.world().getBlockState(destroyingPos);
            if (!state.isAir() && filter != null && filter.has(state)) {
                if (activeMiningBlock == null || !activeMiningBlock.equals(destroyingPos)) {
                    activeMiningBlock = destroyingPos;
                    activeMiningBlockIsObstructing = false;
                    activeMiningTicks = 0;
                }
                Optional<Rotation> rot = RotationUtils.reachable(ctx, destroyingPos);
                if (rot.isPresent()) {
                    if (!isChopMode) {
                        baritone.getPathingBehavior().cancelSegmentIfSafe();
                    }
                    clearMovementKeysKeepAttack();
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    MovementHelper.switchToBestToolFor(ctx, state);
                    if (isAimedAtBlock(destroyingPos, rot.get())) {
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
            // Trong chế độ chặt cây (Chop Mode): Nếu có gỗ rơi trên mặt đất quanh người (> 1.5 block),
            // tạm ngưng đào thêm cây mới để ưu tiên nhặt sạch toàn bộ gỗ rơi trước!
            if (isChopMode) {
                List<BlockPos> drops = droppedItemsScan();
                if (!drops.isEmpty() && ctx.player() != null && ctx.player().getInventory().getFreeSlot() != -1) {
                    boolean hasPendingDrops = drops.stream().anyMatch(d -> ctx.playerFeet().distSqr(d) > 2.25);
                    if (hasPendingDrops) {
                        canDirectMine = false;
                    }
                }
            }
        }
        if (canDirectMine) {
            // Nếu vừa đào vỡ block che chắn: chờ 3 tick chuyển góc nhìn mượt mà sang quặng, KHÔNG vung cúp vội!
            if (obstructingTransitionTicks > 0) {
                obstructingTransitionTicks--;
                if (pendingOreAfterObstructing != null) {
                    Optional<Rotation> smoothRot = RotationUtils.reachable(ctx, pendingOreAfterObstructing);
                    if (smoothRot.isPresent()) {
                        baritone.getLookBehavior().updateTarget(smoothRot.get(), true);
                    }
                }
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            int maxReachY = isChopMode ? (ctx.playerFeet().getY() + 4) : (ctx.playerFeet().getY() + 3);
            // 1. Quét tìm các quặng mục tiêu HOÀN TOÀN KHÔNG BỊ CHẶN:
            Optional<BlockPos> reachableOre = curr.stream()
                    .filter(pos -> ctx.playerFeet().distSqr(pos) <= 25)
                    // QUY TẮC: Đứng vững trên sàn và đào các block trong tầm với trực tiếp
                    .filter(pos -> pos.getY() <= maxReachY)
                    .filter(pos -> !ctx.world().getBlockState(pos).isAir())
                    .filter(pos -> {
                        BlockState s = ctx.world().getBlockState(pos);
                        return filter.has(s) && !MovementHelper.avoidBreaking(baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), s);
                    })
                    // QUY TẮC CỐT LÕI: TUYỆT ĐỐI KHÔNG ĐÀO QUẶNG TRƯỚC NẾU CÓ BLOCK CHẶN!
                    .filter(pos -> getObstructingBlock(pos).isEmpty())
                    // Quét toàn bộ quặng trong tầm với trực tiếp (<= 5 block) quanh người: khai thác ngay lập tức 100%!
                    .filter(pos -> RotationUtils.reachable(ctx, pos).isPresent())
                    .min(Comparator.comparingDouble(ctx.playerFeet().above()::distSqr));

            if (reachableOre.isPresent()) {
                BlockPos pos = reachableOre.get();
                if (activeMiningBlock == null || !activeMiningBlock.equals(pos)) {
                    activeMiningBlock = pos;
                    activeMiningBlockIsObstructing = false;
                    pendingOreAfterObstructing = null;
                    activeMiningTicks = 0;
                }
                BlockState state = ctx.world().getBlockState(pos);
                Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
                if (rot.isPresent()) {
                    if (!isChopMode) {
                        baritone.getPathingBehavior().cancelSegmentIfSafe();
                    }
                    clearMovementKeysKeepAttack();
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    MovementHelper.switchToBestToolFor(ctx, state);
                    if (isAimedAtBlock(pos, rot.get())) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            } else {
                // 2. Nếu có quặng ở cự ly gần (<= 16 distSqr) nhưng bị 1 block che chắn phía trước:
                // TỰ ĐỘNG ĐÀO BLOCK CHE CHẮN ĐÓ TRƯỚC VÀ ĐẢM BẢO ĐÃ ĐƯỢC REMOVE HOÀN TOÀN MỚI ĐÀO QUẶNG!
                Optional<BlockPos> blockedOre = curr.stream()
                        .filter(pos -> ctx.playerFeet().distSqr(pos) <= 16)
                        .filter(pos -> pos.getY() <= maxReachY)
                        .filter(pos -> !ctx.world().getBlockState(pos).isAir())
                        .filter(pos -> {
                            BlockState s = ctx.world().getBlockState(pos);
                            return filter.has(s);
                        })
                        .filter(pos -> getObstructingBlock(pos).isPresent())
                        .min(Comparator.comparingDouble(ctx.playerFeet().above()::distSqr));

                if (blockedOre.isPresent()) {
                    BlockPos ore = blockedOre.get();
                    BlockPos obs = getObstructingBlock(ore).get();
                    BlockState obsState = ctx.world().getBlockState(obs);
                    if (!obsState.isAir() && obsState.getDestroySpeed(ctx.world(), obs) >= 0
                            && !MovementHelper.avoidBreaking(baritone.bsi, obs.getX(), obs.getY(), obs.getZ(), obsState)) {
                        Optional<Rotation> rotObs = RotationUtils.reachable(ctx, obs);
                        if (rotObs.isPresent()) {
                            if (activeMiningBlock == null || !activeMiningBlock.equals(obs)) {
                                activeMiningBlock = obs;
                                activeMiningBlockIsObstructing = true;
                                pendingOreAfterObstructing = ore;
                                activeMiningTicks = 0;
                                logDirect("§e[AutoMine] Phát hiện block che chắn quặng tại " + obs.toShortString() + "! Đào block này trước và đảm bảo dọn sạch hoàn toàn...");
                            }
                            if (!isChopMode) {
                                baritone.getPathingBehavior().cancelSegmentIfSafe();
                            }
                            clearMovementKeysKeepAttack();
                            baritone.getLookBehavior().updateTarget(rotObs.get(), true);
                            MovementHelper.switchToBestToolFor(ctx, obsState);
                            if (isAimedAtBlock(obs, rotObs.get())) {
                                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                            }
                            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                    }
                }
            }
        }

        Optional<BlockPos> shaft = curr.stream()
                .filter(pos -> pos.getX() == ctx.playerFeet().getX() && pos.getZ() == ctx.playerFeet().getZ())
                .filter(pos -> pos.getY() >= ctx.playerFeet().getY())
                .filter(pos -> pos.getY() <= ctx.playerFeet().getY() + 3) // Chỉ đào thẳng đứng nếu trong tầm đứng vững trên sàn
                .filter(pos -> !(BlockStateInterface.get(ctx, pos).getBlock() instanceof AirBlock)) // after breaking a block, it takes mineGoalUpdateInterval ticks for it to actually update this list =(
                .min(Comparator.comparingDouble(ctx.playerFeet().above()::distSqr));
        if (shaft.isPresent() && ctx.player().onGround()) {
            BlockPos pos = shaft.get();
            BlockState state = baritone.bsi.get0(pos);
            if (!MovementHelper.avoidBreaking(baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
                if (rot.isPresent() && isSafeToCancel) {
                    clearMovementKeysKeepAttack();
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    MovementHelper.switchToBestToolFor(ctx, ctx.world().getBlockState(pos));
                    if (isAimedAtBlock(pos, rot.get())) {
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
            if (Baritone.settings().mineStrictOneDirection.value && tunnelDirection != null) {
                Goal fallbackGoal = new GoalStrictDirection(ctx.playerFeet(), tunnelDirection, 24, targetY, curr);
                return new PathingCommand(fallbackGoal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }
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
        shaftConsecutiveFailures = 0;
        activeMiningBlock = null;
        activeMiningBlockIsObstructing = false;
        pendingOreAfterObstructing = null;
        obstructingTransitionTicks = 0;
        shulkerClearOrigin = null;
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
            shulkerClearOrigin = null;
            shulkerPlacedPos = null;
            shulkerStateTicks = 0;
            shulkerBoxCountBefore = 0;
            shulkerUntransferableSlots.clear();
            shulkerTransferredCount = 0;
            shulkerClearingInProgress = false;
            shulkerMode = ShulkerMode.DEPOSIT;
            shulkerCooldownTicks = 0;
            shopRetryCount = 0;
            shopActionCooldown = 0;
            shopPurchasedCountBefore = 0;
            foodPurchasedCountBefore = 0;
            foodCooldownTicks = 0;
            enderChestPlacedPos = null;
            enderChestTransferredCount = 0;
            enderChestCountBefore = 0;
            enderChestShopPurchasedCountBefore = 0;
            enderChestCooldownTicks = 0;
        }
        shulkerCooldownTicks = 0;
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
    }

    private PathingCommand updateGoal() {
        BlockOptionalMetaLookup filter = filterFilter();
        if (filter == null) {
            return null;
        }

        // === ƯU TIÊN SỐ 1: BẮT BUỘC HÚT SẠCH 100% KIM CƯƠNG / QUẶNG / GỖ RƠI TRÊN SÀN TRƯỚC KHI ĐI TIẾP ===
        // ƯU TIÊN NHẶT GỖ RƠI: Trong chế độ chặt cây hoặc đào quặng, nếu có item rơi quanh người thì luôn hút sạch trước khi chặt tiếp cây!
        boolean isInvFull = ctx.player() != null && ctx.player().getInventory().getFreeSlot() == -1;
        List<BlockPos> droppedItems = droppedItemsScan();
        if (!droppedItems.isEmpty() && !isInvFull) {
            // Lọc bỏ những item rơi nếu ở quá xa phía sau (chỉ bỏ qua nếu > 6 block trong đào thẳng 1 hướng)
            List<BlockPos> validDrops = droppedItems.stream().filter(dropPos -> {
                // Trong chế độ chặt cây (Chop Mode): Nhặt TOÀN BỘ gỗ rơi trong phạm vi bán kính 16 block xung quanh!
                if (isChopMode) {
                    return true;
                }
                // QUY TẮC CỐT LÕI: Item rơi ở cự ly gần (<= 6 block) quanh người TUYỆT ĐỐI BẮT BUỘC HÚT SẠCH 100%!
                if (ctx.playerFeet().distSqr(dropPos) <= 36.0) {
                    return true;
                }
                if (tunnelDirection != null) {
                    int dot = (dropPos.getX() - ctx.playerFeet().getX()) * tunnelDirection.getStepX() + (dropPos.getZ() - ctx.playerFeet().getZ()) * tunnelDirection.getStepZ();
                    if (Baritone.settings().mineStrictOneDirection.value) {
                        if (dot < 0) return false;
                        int perpDist = (tunnelDirection.getAxis() == net.minecraft.core.Direction.Axis.Z)
                                ? Math.abs(dropPos.getX() - ctx.playerFeet().getX())
                                : Math.abs(dropPos.getZ() - ctx.playerFeet().getZ());
                        if (perpDist > 4) return false;
                    } else if (dot < 0 && ctx.playerFeet().distSqr(dropPos) > 16.0) {
                        return false;
                    }
                }
                return true;
            }).collect(Collectors.toList());

                if (!validDrops.isEmpty()) {
                    Optional<BlockPos> closestDrop = validDrops.stream()
                            .min(Comparator.comparingDouble(ctx.playerFeet()::distSqr));
                    if (closestDrop.isPresent()) {
                        BlockPos dropPos = closestDrop.get();
                        double distSq = ctx.playerFeet().distSqr(dropPos);
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

                        // Nếu ở ngay sát item (<= 1.5 block) nhưng chưa hút được: bước thẳng vào tâm ô để hút trọn vẹn!
                        if (distSq <= 1.5) {
                            Vec3 targetVec = new Vec3(dropPos.getX() + 0.5, dropPos.getY() + 0.1, dropPos.getZ() + 0.5);
                            Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), targetVec, ctx.playerRotations());
                            baritone.getLookBehavior().updateTarget(rot, true);
                            return new PathingCommand(new GoalBlock(dropPos), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                        } else {
                            return new PathingCommand(new GoalTwoBlocks(dropPos), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                        }
                    }
                }
            } else {
                dropAttemptPos = null;
                dropAttemptTicks = 0;
            }

        // Phát hiện nhanh quặng lộ ra ngay trước mặt hoặc các vách xung quanh khi di chuyển (phạm vi 5x4x5 quanh người):
        BlockPos feetPos = ctx.playerFeet();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos nearPos = feetPos.offset(dx, dy, dz);
                    if (filter.has(ctx.world().getBlockState(nearPos))) {
                        if (!blacklist.contains(nearPos)) {
                            oreMemory.add(nearPos);
                            if (!knownOreLocations.contains(nearPos)) {
                                knownOreLocations.add(nearPos);
                            }
                        }
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
            if (tunnelDirection != null && (hasReachedTargetY || Baritone.settings().mineStrictOneDirection.value)) {
                allCandidates.removeIf(p -> {
                    if (lockedTargetOre != null && (p.equals(lockedTargetOre) || p.distSqr(lockedTargetOre) <= 64)) {
                        return false;
                    }
                    // QUY TẮC CỐT LÕI: Quặng ở cự ly gần (<= 10 block) quanh người TUYỆT ĐỐI KHÔNG BỎ QUA!
                    if (ctx.playerFeet().distSqr(p) <= 100.0) {
                        return false;
                    }
                    int dot = (p.getX() - ctx.playerFeet().getX()) * tunnelDirection.getStepX() + (p.getZ() - ctx.playerFeet().getZ()) * tunnelDirection.getStepZ();
                    if (Baritone.settings().mineStrictOneDirection.value) {
                        if (dot < 0) return true; // Chỉ bỏ qua quặng phía sau lưng nếu đã đi xa quá 10 block!
                        int perpDist = (tunnelDirection.getAxis() == net.minecraft.core.Direction.Axis.Z)
                                ? Math.abs(p.getX() - ctx.playerFeet().getX())
                                : Math.abs(p.getZ() - ctx.playerFeet().getZ());
                        return perpDist > 8;
                    }
                    return dot < -10;
                });
            }
            if (ctx.playerFeet().y > targetY + 3 && !hasReachedTargetY) {
                allCandidates.removeIf(p -> Math.abs(p.getY() - ctx.playerFeet().y) > 6 || ctx.playerFeet().distSqr(p) > 64);
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
            if (ctx.playerFeet().y > targetY + 3 && !hasReachedTargetY) {
                locs2.removeIf(p -> Math.abs(p.getY() - ctx.playerFeet().y) > 6 || ctx.playerFeet().distSqr(p) > 64);
            }
            
            // CHẾ ĐỘ ĐÀO 1 HƯỚNG DUY NHẤT (STRICT ONE-DIRECTION MINING):
            if (Baritone.settings().mineStrictOneDirection.value && tunnelDirection != null && !isChopMode) {
                locs2.removeIf(p -> {
                    if (lockedTargetOre != null && (p.equals(lockedTargetOre) || p.distSqr(lockedTargetOre) <= 64)) {
                        return false;
                    }
                    // QUY TẮC CỐT LÕI: Quặng ở cự ly gần (<= 10 block) quanh người TUYỆT ĐỐI KHÔNG BỎ QUA!
                    if (ctx.playerFeet().distSqr(p) <= 100.0) {
                        return false;
                    }
                    int forward = (p.getX() - ctx.playerFeet().getX()) * tunnelDirection.getStepX() + (p.getZ() - ctx.playerFeet().getZ()) * tunnelDirection.getStepZ();
                    if (forward < 0) {
                        return true; // Chỉ bỏ qua nếu đã đi xa quá 10 block về phía trước!
                    }
                    int perpDist = (tunnelDirection.getAxis() == net.minecraft.core.Direction.Axis.Z)
                            ? Math.abs(p.getX() - ctx.playerFeet().getX())
                            : Math.abs(p.getZ() - ctx.playerFeet().getZ());
                    return perpDist > 8;
                });
            }
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

                // Cơ chế Hysteresis thông minh: Nếu có quặng ngay sát người (<= 8 block, distSqr <= 64)
                // trong khi lockedTargetOre ở xa hơn (> 8 block), lập tức đổi lockedTargetOre sang quặng sát người!
                if (lockedTargetOre != null && ctx.playerFeet().distSqr(lockedTargetOre) > 64) {
                    Optional<BlockPos> veryClose = locs2.stream()
                            .filter(pos -> ctx.playerFeet().distSqr(pos) <= 64)
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
                    // Bao gồm toàn bộ quặng gần người chơi (<= 16 block) VÀ cụm vỉa quanh lockedTargetOre để không bỏ sót quặng gần!
                    final BlockPos target = lockedTargetOre;
                    targetOres = locs2.stream()
                            .filter(pos -> ctx.playerFeet().distSqr(pos) <= 256 || pos.equals(target) || pos.distSqr(target) <= 100)
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

                // NẾU ĐANG ĐÀO HẦM (TUNNEL) MÀ PHÁT HIỆN QUẶNG:
                // NGAY LẬP TỨC HỦY ĐƯỜNG ĐÀO HẦM ĐỂ BẺ LÁI SANG ĐÀO QUẶNG!
                Goal currentGoal = baritone.getPathingBehavior().getGoal();
                boolean isTunnelGoal = (currentGoal instanceof GoalStrictDirection)
                        || (currentGoal instanceof GoalShaftDown)
                        || (currentGoal instanceof GoalRunAway);

                if (wasTunneling || isTunnelGoal) {
                    wasTunneling = false;
                    logDirect("§a[AutoMine] Phát hiện quặng mục tiêu khi đang đào hầm! Rẽ sang đào quặng...");
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    baritone.getInputOverrideHandler().clearAllKeys();
                    baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    return new PathingCommand(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
                }

                if (fr) {
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    return new PathingCommand(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
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
        if (currentY <= targetY) {
            hasReachedTargetY = true;
        } else if (currentY > targetY + 3) {
            hasReachedTargetY = false;
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
                    return new PathingCommand(new GoalYLevel(bedrockEscapeTargetY), fr ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                } else {
                    // Giai đoạn 2: Đã đạt độ cao an toàn (curY >= bedrockEscapeTargetY)!
                    if (Baritone.settings().mineStrictOneDirection.value && tunnelDirection != null) {
                        logDirect("§a[AntiStuck (1-Dir)] Đã nâng lên tầng an toàn Y=" + curY + "! Tiếp tục đào thẳng theo hướng " + tunnelDirection.getName().toUpperCase() + "...");
                        bedrockEscapeActive = false;
                        bedrockEscapeOrigin = null;
                        bedrockEscapeTicks = 0;
                        // GoalStrictDirection: Đào thẳng phía trước, KHÔNG BAO GIỜ quay ngược!
                        Goal tunnelGoal = new GoalStrictDirection(ctx.playerFeet(), tunnelDirection, 24, curY, null);
                        boolean fr = forceReroute;
                        forceReroute = false;
                        return new PathingCommand(tunnelGoal, fr ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                    }
                    // Di chuyển cách xa điểm kẹt bedrock cũ ít nhất 20 block
                    int distAway = bedrockEscapeOrigin != null ? (int) Math.sqrt(ctx.playerFeet().distSqr(bedrockEscapeOrigin)) : 20;
                    if (distAway >= 20) {
                        logDirect("§a[AntiStuck] Đã thoát xa vùng kẹt Bedrock " + distAway + "m! Trở lại trạng thái đào bình thường.");
                        bedrockEscapeActive = false;
                        bedrockEscapeOrigin = null;
                        bedrockEscapeTicks = 0;
                        branchPoint = (tunnelDirection != null) ? ctx.playerFeet().relative(tunnelDirection.getOpposite(), 16) : ctx.playerFeet();
                        branchPointRunaway = null;
                        forceReroute = true;
                    } else {
                        if (tickCount % 20 == 0) {
                            logDirect("§a[AntiStuck] Đang đào ngang tại tầng an toàn Y=" + curY + " để rời khỏi vùng Bedrock (" + distAway + "/20m)...");
                        }
                        boolean fr = forceReroute;
                        forceReroute = false;
                        if (branchPoint == null) {
                            branchPoint = (tunnelDirection != null) ? ctx.playerFeet().relative(tunnelDirection.getOpposite(), 16) : ctx.playerFeet();
                        }
                        if (branchPointRunaway == null) {
                            branchPointRunaway = new GoalRunAway(20, curY, branchPoint);
                        }
                        return new PathingCommand(branchPointRunaway, fr ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                    }
                }
            }
        }

        // KHI CHƯA ĐẠT ĐỘ SÂU TARGET Y (currentY > targetY + 1 && !hasReachedTargetY):
        if (currentY > targetY + 1 && !hasReachedTargetY) {
            if (Baritone.settings().straightDownMine.value) {
                // CHẾ ĐỘ SHAFT DOWN: ĐÀO THẲNG ĐỨNG XUỐNG DƯỚI TẠI VỊ TRÍ HIỆN TẠI
                if (shaftOriginPos == null || forceReroute
                        || Math.abs(shaftOriginPos.getX() - ctx.playerFeet().x) > 2
                        || Math.abs(shaftOriginPos.getZ() - ctx.playerFeet().z) > 2
                        || shaftOriginPos.getY() - currentY >= 6
                        || (!baritone.getPathingBehavior().isPathing() && shaftOriginPos.getY() > currentY)) {
                    shaftOriginPos = ctx.playerFeet();
                }
                if (tickCount % 40 == 0) {
                    logDirect("§a[AutoMine] Đang đào thẳng đứng (Shaft Down) từ Y=" + currentY + " xuống Y=" + targetY + "...");
                }
                wasTunneling = true;
                boolean fr = forceReroute;
                forceReroute = false;
                Goal shaftGoal = new GoalShaftDown(shaftOriginPos.getX(), shaftOriginPos.getY(), shaftOriginPos.getZ(), targetY);
                return new PathingCommand(shaftGoal, fr ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }

            // ƯU TIÊN SỐ 1 KHI Ở TRÊN CAO (KHÔNG BẬT SHAFT DOWN): DÙNG XÔ NƯỚC (WATER BUCKET) ĐỂ TỤT XUỐNG THAY VÌ ĐÀO XUỐNG
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
                        return new PathingCommand(new GoalTwoBlocks(dropPos), fr ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                    }
                }
            }
        }

        // CHUẨN GỐC BARITONE CÓ ĐỊNH HƯỚNG: Đào xuyên đá tiến về phía trước theo tầng targetY
        int y = targetY;
        if (hasReachedTargetY && Baritone.settings().mineStrictOneDirection.value) {
            y = Math.min(-54, Math.max(targetY, ctx.playerFeet().y));
        }
        if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
            net.minecraft.core.Direction dir = ctx.player().getDirection();
            tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
        }
        // Kiểm tra an toàn: Nếu phía trước đường hầm có lồng Spawner trong vòng 16 block -> Tự động chuyển hướng hầm để tránh xa nguy hiểm (chỉ khi không bật Strict 1-Dir)!
        if (!Baritone.settings().mineStrictOneDirection.value) {
            CalculationContext spawnerCheckCtx = new CalculationContext(baritone);
            BlockPos tunnelAhead = ctx.playerFeet().relative(tunnelDirection, 8);
            if (isNearSpawner(spawnerCheckCtx, tunnelAhead, 12)) {
                tunnelDirection = tunnelDirection.getClockWise();
                logDirect("§c[Mine] Phát hiện lồng Spawner phía trước! Tự động chuyển hướng hầm sang " + tunnelDirection + " để tránh xa nguy hiểm!");
                branchPoint = ctx.playerFeet();
                branchPointRunaway = null;
                forceReroute = true;
            }
        }

        boolean fr = forceReroute;
        forceReroute = false;

        wasTunneling = true;
        // === CHẾ ĐỘ 1 HƯỚNG DUY NHẤT: Dùng GoalStrictDirection thay vì GoalRunAway ===
        // GoalStrictDirection penalizes backward (-forward*100) và lateral (+perp*1000) movement cực mạnh
        // → A* TUYỆT ĐỐI KHÔNG BAO GIỜ tìm được đường đi ngược lại hay rẽ ngang!
        if (Baritone.settings().mineStrictOneDirection.value) {
            Goal tunnelGoal = new GoalStrictDirection(ctx.playerFeet(), tunnelDirection, 24, y, locs);
            return new PathingCommand(tunnelGoal, fr ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        }

        // Chế độ thường: GoalRunAway với branchPoint phía sau lưng
        BlockPos desiredBranchPoint = ctx.playerFeet().relative(tunnelDirection.getOpposite(), 16);
        if (branchPoint == null || ctx.playerFeet().distSqr(branchPoint) >= 256) { // Update mỗi 16 block thay vì 48
            branchPoint = desiredBranchPoint;
            branchPointRunaway = null;
        }
        if (branchPointRunaway == null) {
            branchPointRunaway = new GoalRunAway(48, y, branchPoint);
        }
        return new PathingCommand(branchPointRunaway, fr ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
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
                if (isShulkerBox(stack) || isTargetOre(stack) || ORE_DROPS.contains(item)
                        || (isChopMode && isWoodDrop(stack))
                        || (filter != null && filter.has(stack))
                        || item.getDescriptionId().contains("ore")
                        || item.getDescriptionId().contains("raw")
                        || (isChopMode && (item.getDescriptionId().contains("log") || item.getDescriptionId().contains("wood") || item.getDescriptionId().contains("stem")))) {
                    BlockPos pos = entity.blockPosition();
                    if (!ignoredDrops.containsKey(pos) && pos.distSqr(pf) <= 256) { // Trong bán kính 16 block
                        ret.add(pos);
                    }
                }
            }
        }
        return ret;
    }

    public static boolean isWoodDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item == Items.STICK || item == Items.APPLE) {
            return true;
        }
        for (WoodType wt : WoodType.values()) {
            if (wt.getItemStack().getItem() == item) {
                return true;
            }
        }
        String desc = item.getDescriptionId().toLowerCase();
        return desc.contains("log")
                || desc.contains("wood")
                || desc.contains("stem")
                || desc.contains("hyphae")
                || desc.contains("bamboo")
                || desc.contains("sapling")
                || desc.contains("propagule");
    }

    private final List<Integer> pendingDropSlots = new ArrayList<>();
    private int dropCooldown = 0;

    public static boolean isBuildingBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) {
            return false;
        }
        Block block = bi.getBlock();
        if (block instanceof ShulkerBoxBlock || block instanceof TrapDoorBlock || block instanceof net.minecraft.world.level.block.EnderChestBlock) {
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

        // 2. Chế độ chặt cây (Chop Mode): Giữ tất cả gỗ rơi
        if (isChopMode && isWoodDrop(stack)) {
            return true;
        }

        // 3. Nếu không có filter cụ thể (mine tự do): giữ các quặng quý thông thường
        if (filter == null) {
            return item == Items.LAPIS_LAZULI
                    || item == Items.REDSTONE
                    || item == Items.GOLD_INGOT
                    || item == Items.IRON_INGOT
                    || item == Items.RAW_GOLD
                    || item == Items.RAW_IRON
                    || item == Items.AMETHYST_SHARD;
        }

        // 4. Nếu CÓ filter: kiểm tra xem item hoặc block có khớp với mục tiêu đào của người chơi không
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
        // BẢO VỆ TUYỆT ĐỐI SHULKER BOX (ĐẦY HOẶC TRỐNG) & RƯƠNG ENDER - TUYỆT ĐỐI KHÔNG BAO GIỜ VỨT!
        if (isShulkerBox(stack) || isEnderChest(stack)) return true;
        if (stack.has(DataComponents.CONTAINER)) return true;
        if (stack.has(DataComponents.BUNDLE_CONTENTS)) return true;
        String desc = stack.getItem().getDescriptionId();
        if (desc != null && (desc.toLowerCase().contains("shulker") || desc.toLowerCase().contains("ender_chest"))) return true;
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
        List<Integer> buildingSlots = new ArrayList<>();
        for (int i = 1; i < 36; i++) {
            ItemStack s = inv.get(i);
            if (!s.isEmpty() && !isProtectedFromDrop(s) && isBuildingBlock(s)) {
                buildingSlots.add(i);
            }
        }
        buildingSlots.sort((a, b) -> Integer.compare(inv.get(b).getCount(), inv.get(a).getCount()));

        Set<Integer> keptSlots = new HashSet<>();
        int keptCount = 0;
        for (int slot : buildingSlots) {
            if (keptCount < 64) {
                keptSlots.add(slot);
                keptCount += inv.get(slot).getCount();
            }
        }

        int trashSlots = 0;
        for (int i = 1; i < 36; i++) {
            ItemStack stack = inv.get(i);
            if (stack.isEmpty()) continue;
            if (isProtectedFromDrop(stack)) continue;
            if (isBuildingBlock(stack) && keptSlots.contains(i)) continue;
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
            transferable++;
        }
        return transferable;
    }

    private PathingCommand handleAutoDrop() {
        if (ctx.player() == null || ctx.player().containerMenu != ctx.player().inventoryMenu) {
            return null;
        }

        if (!Baritone.settings().autoDrop.value && pendingDropSlots.isEmpty()) {
            return null;
        }

        // Khi đang thao tác đặt/cất Shulker Box: tạm dừng AutoDrop để không xung đột click chuột
        if (shulkerState != ShulkerStorageState.IDLE) {
            return null;
        }

        // 1. Quét tìm và nạp rác vào hàng đợi (KHÔNG bị chặn bởi việc đang đập block):
        if (pendingDropSlots.isEmpty()) {
            boolean shouldScan = (tickCount % 100 == 0);
            if (!shouldScan) {
                NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
                int emptyCount = 0;
                boolean hasFullExcessStack = false;
                int buildingBlockCount = 0;

                for (int i = 1; i < 36; i++) {
                    ItemStack s = inv.get(i);
                    if (s.isEmpty()) {
                        emptyCount++;
                    } else if (!isProtectedFromDrop(s)) {
                        if (isBuildingBlock(s)) {
                            buildingBlockCount += s.getCount();
                            if (buildingBlockCount > 64 && s.getCount() >= 64) {
                                hasFullExcessStack = true;
                            }
                        } else if (s.getCount() >= 64) {
                            hasFullExcessStack = true;
                        }
                    }
                }
                if (emptyCount <= 3 || hasFullExcessStack) {
                    shouldScan = true;
                }
            }

            if (shouldScan) {
                scanAndQueueTrashDrops();
            }
        }

        // 2. Thực hiện vứt rác từ hàng đợi:
        if (!pendingDropSlots.isEmpty()) {
            // Nếu đang đào quặng quý mục tiêu (activeMiningBlock != null): chờ đào xong quặng quý
            if (activeMiningBlock != null) {
                return null;
            }

            if (dropCooldown > 0) {
                dropCooldown--;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            // Tạm dừng việc đập hầm khi vứt rác
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);

            int slotIndex = pendingDropSlots.remove(0);
            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            if (slotIndex >= 1 && slotIndex < inv.size()) {
                ItemStack stack = inv.get(slotIndex);
                int windowSlot = (slotIndex < 9) ? (slotIndex + 36) : slotIndex;
                ItemStack menuStack = ctx.player().inventoryMenu.getSlot(windowSlot).getItem();

                // KIỂM TRA BẢO VỆ 2 LỚP TRÊN CẢ INV LẪN WINDOW SLOT TRỰC TIẾP:
                // TUYỆT ĐỐI KHÔNG BAO GIỜ vứt Shulker Box, Rương Ender, Totem, Food, Tools, Quặng mục tiêu
                boolean isProtected = isProtectedFromDrop(stack) || isProtectedFromDrop(menuStack)
                        || isShulkerBox(stack) || isShulkerBox(menuStack)
                        || isEnderChest(stack) || isEnderChest(menuStack)
                        || stack.has(DataComponents.CONTAINER) || menuStack.has(DataComponents.CONTAINER)
                        || (stack.getItem().getDescriptionId() != null && (stack.getItem().getDescriptionId().toLowerCase().contains("shulker") || stack.getItem().getDescriptionId().toLowerCase().contains("ender_chest")))
                        || (menuStack.getItem().getDescriptionId() != null && (menuStack.getItem().getDescriptionId().toLowerCase().contains("shulker") || menuStack.getItem().getDescriptionId().toLowerCase().contains("ender_chest")));

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
                    // Button 1 = Vứt trọn vẹn toàn bộ full stack (Ctrl+Q)!
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 1, ClickType.THROW, ctx.player());
                }
            }
            dropCooldown = 3; // 3 ticks (0.15s) cooldown giữa mỗi stack ném
            if (pendingDropSlots.isEmpty()) {
                logDirect("§a[AutoDrop] Đã dọn sạch toàn bộ đá thừa và quặng không liên quan!");
                return null;
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        return null;
    }

    private BlockPos findNearbyLava() {
        if (ctx.player() == null || ctx.world() == null) return null;
        BetterBlockPos feet = ctx.playerFeet();
        BlockPos bestLava = null;
        double bestLavaDistSq = Double.MAX_VALUE;

        // Quét tìm hồ Lava trong tầm ném hiệu quả (tối đa ~3.5 block ngang, -3 đến +1 theo chiều Y)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 13) continue; // Bán kính ném <= 3.6 block để item chắc chắn rơi trúng lava
                for (int dy = -3; dy <= 1; dy++) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    FluidState fluid = ctx.world().getFluidState(p);
                    boolean isLava = fluid.is(FluidTags.LAVA) || ctx.world().getBlockState(p).is(Blocks.LAVA);
                    if (!isLava) continue;

                    BlockPos above = p.above();
                    BlockState aboveState = ctx.world().getBlockState(above);
                    // Khoảng không phía trên ô lava phải thông thoáng để quăng đồ lọt vào
                    boolean aboveOpen = aboveState.isAir() || aboveState.canBeReplaced()
                            || ctx.world().getFluidState(above).is(FluidTags.LAVA)
                            || ctx.world().getBlockState(above).is(Blocks.LAVA);
                    if (!aboveOpen) continue;

                    double distSq = feet.distSqr(p);
                    if (distSq < bestLavaDistSq) {
                        Vec3 eye = ctx.playerHead();
                        Vec3 target = new Vec3(p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5);
                        ClipContext rayCtx = new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.player());
                        HitResult hit = ctx.world().clip(rayCtx);
                        if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 1.2
                                || (hit instanceof BlockHitResult bhr && (bhr.getBlockPos().equals(p) || bhr.getBlockPos().equals(above)))) {
                            bestLavaDistSq = distSq;
                            bestLava = p;
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
            Vec3 target = new Vec3(lava.getX() + 0.5, lava.getY() + 1.05, lava.getZ() + 0.5);
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

        // 1. Phân loại các slot chứa block xây dựng (đá, đất, v.v.):
        List<Integer> buildingSlots = new ArrayList<>();
        for (int i = 1; i < 36; i++) { // Luôn bỏ qua slot 0 (cúp chính)
            ItemStack s = inv.get(i);
            if (!s.isEmpty() && !isProtectedFromDrop(s) && isBuildingBlock(s)) {
                buildingSlots.add(i);
            }
        }
        // Ưu tiên giữ lại stack to nhất (nhiều block nhất) lên đầu
        buildingSlots.sort((a, b) -> Integer.compare(inv.get(b).getCount(), inv.get(a).getCount()));

        Set<Integer> keptSlots = new HashSet<>();
        int keptCount = 0;
        for (int slot : buildingSlots) {
            if (keptCount < 64) {
                keptSlots.add(slot);
                keptCount += inv.get(slot).getCount();
            }
        }

        int newQueued = 0;
        for (int i = 1; i < 36; i++) { // Không vứt slot 0
            ItemStack stack = inv.get(i);
            if (stack.isEmpty()) continue;

            // Bỏ qua item bảo vệ (Shulker Box, Totem, Tool, Food, Target Ore, Enchanted)
            if (isProtectedFromDrop(stack)) continue;

            // Nếu là block xây dựng và nằm trong số slot được giữ lại (tổng <= 64): bỏ qua
            if (isBuildingBlock(stack) && keptSlots.contains(i)) {
                continue;
            }

            // Toàn bộ đá thừa, block thừa, rác không mong muốn -> nạp vào hàng đợi vứt
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

        // 1. Kiểm tra DataComponents.CONTAINER (chuẩn Vanilla Minecraft 1.20.5+)
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            int count = 0;
            for (ItemStack item : contents.nonEmptyItems()) {
                if (!item.isEmpty()) {
                    count++;
                }
            }
            return count;
        }

        // 2. Kiểm tra DataComponents.BLOCK_ENTITY_DATA (chuẩn Paper/Spigot/KingMC lưu trữ Items NBT)
        var blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData != null) {
            CompoundTag tag = blockEntityData.getUnsafe();
            if (tag != null) {
                java.util.Optional<ListTag> listOpt = tag.getList("Items");
                if (listOpt != null && listOpt.isPresent()) {
                    return listOpt.get().size();
                }
            }
        }

        // 3. Kiểm tra DataComponents.CUSTOM_DATA (Paper/Spigot custom plugins)
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag != null) {
                java.util.Optional<ListTag> listOpt = tag.getList("Items");
                if (listOpt != null && listOpt.isPresent()) {
                    return listOpt.get().size();
                }
                var betOpt = tag.getCompound("BlockEntityTag");
                if (betOpt != null && betOpt.isPresent()) {
                    var betList = betOpt.get().getList("Items");
                    if (betList != null && betList.isPresent()) {
                        return betList.get().size();
                    }
                }
            }
        }

        // 4. Kiểm tra qua Lore / Display nếu server KingMC hiển thị số ô hoặc nội dung trong Lore
        var lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                String str = line.getString();
                if (str.contains("27/27")) {
                    return 27;
                }
            }
        }

        return 0; // null component & null NBT = Shulker Box hoàn toàn trống 100%!
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

    public static boolean isShulkerBoxFull(ItemStack stack) {
        return isShulkerBox(stack) && getShulkerOccupiedSlots(stack) >= 27;
    }

    public static boolean isShulkerBoxWithItems(ItemStack stack) {
        return isShulkerBox(stack) && getShulkerOccupiedSlots(stack) > 0;
    }

    private int countShulkerBoxesWithItemsInInventory() {
        if (ctx.player() == null) return 0;
        int count = 0;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.get(i);
            if (isShulkerBoxWithItems(s)) {
                count += s.getCount();
            }
        }
        ItemStack offhand = ctx.player().getOffhandItem();
        if (isShulkerBoxWithItems(offhand)) {
            count += offhand.getCount();
        }
        return count;
    }

    public static boolean isEnderChest(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.ENDER_CHEST)) return true;
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) return true;
        String desc = stack.getItem().getDescriptionId();
        return desc != null && desc.toLowerCase().contains("ender_chest");
    }

    private int countFullShulkerBoxesInInventory() {
        if (ctx.player() == null) return 0;
        int count = 0;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.get(i);
            if (isShulkerBoxFull(s)) {
                count += s.getCount();
            }
        }
        ItemStack offhand = ctx.player().getOffhandItem();
        if (isShulkerBoxFull(offhand)) {
            count += offhand.getCount();
        }
        return count;
    }

    private int countEnderChestsInInventory() {
        if (ctx.player() == null) return 0;
        int count = 0;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.get(i);
            if (isEnderChest(s)) {
                count += s.getCount();
            }
        }
        ItemStack offhand = ctx.player().getOffhandItem();
        if (isEnderChest(offhand)) {
            count += offhand.getCount();
        }
        return count;
    }

    private int findEnderChestSlot() {
        if (ctx.player() == null) return -1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = 1; i < 9; i++) {
            if (isEnderChest(inv.get(i))) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (isEnderChest(inv.get(i))) return i;
        }
        if (isEnderChest(inv.get(0))) return 0;
        return -1;
    }

    private int findSilkTouchPickaxeSlot() {
        if (ctx.player() == null) return -1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        baritone.utils.ToolSet ts = new baritone.utils.ToolSet(ctx.player());
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.get(i);
            if (!s.isEmpty() && (s.is(ItemTags.PICKAXES) || s.getItem().getDescriptionId().toLowerCase().contains("pickaxe")) && ts.hasSilkTouch(s)) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.get(i);
            if (!s.isEmpty() && (s.is(ItemTags.PICKAXES) || s.getItem().getDescriptionId().toLowerCase().contains("pickaxe")) && ts.hasSilkTouch(s)) {
                return i;
            }
        }
        return -1;
    }

    private int findBestShulkerBoxSlot() {
        if (ctx.player() == null) return -1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();

        int bestPartialSlot = -1;
        int maxPartialOccupied = -1;
        int bestEmptySlot = -1;

        // Thứ tự ưu tiên slot: hotbar slot 1-8 trước, rồi balo chính 9-35, cuối cùng slot 0
        int[] slotOrder = new int[36];
        int idx = 0;
        for (int i = 1; i < 9; i++) slotOrder[idx++] = i;
        for (int i = 9; i < 36; i++) slotOrder[idx++] = i;
        slotOrder[idx++] = 0;

        for (int slot : slotOrder) {
            ItemStack stack = inv.get(slot);
            if (!isShulkerBox(stack)) continue;
            // Bỏ qua nếu hộp này đã được đánh dấu là đầy/không thể nhận thêm đồ trong balo
            if (blacklistedFullShulkerSlots.contains(slot)) continue;

            int occupied = getShulkerOccupiedSlots(stack);
            // TUYỆT ĐỐI BỎ QUA nếu Shulker Box đã đầy 27/27 ô!
            if (occupied >= 27) continue;

            // 1. Shulker box đang dùng dở (chưa full, 0 < occupied < 27):
            // ƯU TIÊN SỐ 1: Tiếp tục đặt ra và cho đồ vào tiếp cho đến khi đầy 27/27 ô!
            if (occupied > 0) {
                if (occupied > maxPartialOccupied) {
                    maxPartialOccupied = occupied;
                    bestPartialSlot = slot;
                }
            } else if (occupied == 0 && bestEmptySlot == -1) {
                // 2. Shulker trống hoàn toàn (dự phòng khi không có hộp dùng dở)
                bestEmptySlot = slot;
            }
        }

        if (bestPartialSlot != -1) {
            return bestPartialSlot;
        }
        return bestEmptySlot;
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

    public static boolean shulkerContainsTotem(ItemStack shulkerStack) {
        if (!isShulkerBox(shulkerStack)) return false;
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return false;
        for (ItemStack item : contents.nonEmptyItems()) {
            if (item.is(Items.TOTEM_OF_UNDYING)) return true;
        }
        return false;
    }

    private int findShulkerBoxWithTotemSlot() {
        if (ctx.player() == null) return -1;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int[] slotOrder = new int[36];
        int idx = 0;
        for (int i = 1; i < 9; i++) slotOrder[idx++] = i;
        for (int i = 9; i < 36; i++) slotOrder[idx++] = i;
        slotOrder[idx++] = 0;

        for (int slot : slotOrder) {
            ItemStack stack = inv.get(slot);
            if (shulkerContainsTotem(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private void triggerShulkerRetrieveTotem(int shulkerSlot) {
        pendingDropSlots.clear();
        shulkerClearingInProgress = true;
        shulkerMode = ShulkerMode.RETRIEVE_TOTEM;
        shulkerBoxCountBefore = countShulkerBoxesInInventory();
        logDirect("§6[AutoShulker] Hết Totem trong người! Phát hiện có Totem trong Shulker Box (slot " + shulkerSlot + "), đang mở để lấy...");
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
        double bestDist = 256.0; // Bán kính tối đa 16 block
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

        // 1. Shulker Box & Ender Chest (cấm nhét Shulker vào trong Shulker Box khác hoặc vứt bỏ)
        if (isShulkerBox(stack) || isEnderChest(stack)) return true;

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
                            && !(floorState.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock)
                            && (aboveState.isAir() || aboveState.canBeReplaced())
                            && !new AABB(above).intersects(playerBox)) {

                        Vec3 hitVec = new Vec3(floor.getX() + 0.5, floor.getY() + 0.95, floor.getZ() + 0.5);
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

        if (foodCooldownTicks > 0) {
            foodCooldownTicks--;
        }

        if (totemCooldownTicks > 0) {
            totemCooldownTicks--;
        }

        if (enderChestCooldownTicks > 0) {
            enderChestCooldownTicks--;
        }

        if (shulkerCooldownTicks > 0) {
            shulkerCooldownTicks--;
            if (shulkerCooldownTicks == 0) {
                blacklistedFullShulkerSlots.clear();
            }
            if (shulkerState == ShulkerStorageState.IDLE) {
                // Kiểm tra autoBuyTotem khi shulker đang cooldown
                if (Baritone.settings().autoBuyTotem.value && getTotemCount() == 0 && totemCooldownTicks <= 0) {
                    int shulkerTotemSlot = findShulkerBoxWithTotemSlot();
                    if (shulkerTotemSlot != -1) {
                        triggerShulkerRetrieveTotem(shulkerTotemSlot);
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                    totemPurchasedCountBefore = getTotemCount();
                    logDirect("§e[AutoShop] Hết Totem Bất Tử trong cả tay phụ lẫn balo! Tự động mở /shop để mua Vật tổ trường sinh...");
                    shopRetryCount = 0;
                    shopActionCooldown = 0;
                    shulkerState = ShulkerStorageState.SHOP_TOTEM_PREPARE_SLOT;
                    shulkerStateTicks = 0;
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    baritone.getInputOverrideHandler().clearAllKeys();
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                // Nếu đang IDLE và shulker đang cooldown, vẫn có thể kiểm tra autoBuyFood
                if (Baritone.settings().autoBuyFood.value && countFoodInInventory() == 0 && foodCooldownTicks <= 0) {
                    int shulkerFoodSlot = findShulkerBoxWithFoodSlot();
                    if (shulkerFoodSlot != -1) {
                        triggerShulkerRetrieveFood(shulkerFoodSlot);
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                    foodPurchasedCountBefore = countFoodInInventory();
                    logDirect("§e[AutoShop] Hết đồ ăn trong cả hotbar lẫn balo! Tự động mở /shop để mua 64 Thịt Bò Nướng...");
                    shopRetryCount = 0;
                    shopActionCooldown = 0;
                    shulkerState = ShulkerStorageState.SHOP_FOOD_PREPARE_SLOT;
                    shulkerStateTicks = 0;
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    baritone.getInputOverrideHandler().clearAllKeys();
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                return null;
            }
        }

        // Kích hoạt khi đang IDLE
        if (shulkerState == ShulkerStorageState.IDLE) {
            // ƯU TIÊN SỐ 0A: Khi hết Totem trong cả tay phụ lẫn balo -> Tự động mua Totem từ /shop
            if (Baritone.settings().autoBuyTotem.value && getTotemCount() == 0 && totemCooldownTicks <= 0) {
                int shulkerTotemSlot = findShulkerBoxWithTotemSlot();
                if (shulkerTotemSlot != -1) {
                    triggerShulkerRetrieveTotem(shulkerTotemSlot);
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                totemPurchasedCountBefore = getTotemCount();
                logDirect("§e[AutoShop] Hết Totem Bất Tử trong cả tay phụ lẫn balo! Tự động mở /shop để mua Vật tổ trường sinh...");
                shopRetryCount = 0;
                shopActionCooldown = 0;
                shulkerState = ShulkerStorageState.SHOP_TOTEM_PREPARE_SLOT;
                shulkerStateTicks = 0;
                baritone.getPathingBehavior().cancelSegmentIfSafe();
                baritone.getInputOverrideHandler().clearAllKeys();
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            // ƯU TIÊN SỐ 0: Khi có từ 3 Shulker Box (đầy đồ 27/27 hoặc có đồ) trong người -> Tự động đặt Rương Ender để cất 3 Shulker Box đó
            int fullShulkerCount = countFullShulkerBoxesInInventory();
            int usedShulkerCount = countShulkerBoxesWithItemsInInventory();
            int totalShulkerCount = countShulkerBoxesInInventory();
            boolean triggerEnderChest = (fullShulkerCount >= 3 || usedShulkerCount >= 3 || (totalShulkerCount >= 3 && (fullShulkerCount > 0 || usedShulkerCount >= 2)));
            if (triggerEnderChest && enderChestCooldownTicks <= 0) {
                int ecSlot = findEnderChestSlot();
                int displayCount = Math.max(fullShulkerCount, usedShulkerCount);
                if (displayCount == 0) displayCount = totalShulkerCount;
                if (ecSlot != -1) {
                    logDirect("§a[AutoEnderChest] Phát hiện " + displayCount + " Shulker Box (đầy/có đồ)! Đã có Rương Ender trong người (slot " + ecSlot + "), tiến hành đặt ra để cất 3 Shulker Box...");
                    if (isNearLava(ctx.playerFeet(), 5)) {
                        logDirect("§e[AutoEnderChest] Phát hiện dung nham gần đó (<= 5 block)! Bỏ qua đào mở rộng 3x3 để đảm bảo an toàn.");
                        shulkerState = ShulkerStorageState.ENDER_CHEST_SWAP_TO_HOTBAR;
                    } else {
                        logDirect("§a[AutoEnderChest] Đang dọn dẹp không gian 3x3 quanh vị trí đặt Rương Ender...");
                        shulkerClearOrigin = ctx.playerFeet();
                        shulkerState = ShulkerStorageState.ENDER_CHEST_CLEAR_SPACE;
                    }
                    shulkerStateTicks = 0;
                    enderChestTransferredCount = 0;
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    baritone.getInputOverrideHandler().clearAllKeys();
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                } else if (Baritone.settings().autoBuyShulker.value) {
                    logDirect("§e[AutoShop] Phát hiện " + displayCount + " Shulker Box (đầy/có đồ) nhưng chưa có Rương Ender! Tự động mở /shop để mua Rương Ender...");
                    shopRetryCount = 0;
                    shopActionCooldown = 0;
                    enderChestShopPurchasedCountBefore = countEnderChestsInInventory();
                    shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_PREPARE_SLOT;
                    shulkerStateTicks = 0;
                    enderChestTransferredCount = 0;
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    baritone.getInputOverrideHandler().clearAllKeys();
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }

            NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
            int emptySlots = 0;
            for (int i = 0; i < 36; i++) {
                if (inv.get(i).isEmpty()) emptySlots++;
            }

            int transferableSlots = countTransferableSlots();
            int shulkerSlot = findBestShulkerBoxSlot();

            // ĐIỀU KIỆN KÍCH HOẠT AUTO SHULKER:
            // 1. Phải có Shulker Box còn chỗ (chưa full < 27 ô: ưu tiên đang dùng dở, rồi mới tới hộp trống)
            // 2. Phải có vật phẩm hợp lệ cần cất (transferableSlots > 0)
            // 3. Balo THỰC SỰ ĐÃ ĐẦY: chỉ còn tối đa 1 ô trống (emptySlots <= 1)
            // TUYỆT ĐỐI KHÔNG đặt khi balo chưa đầy!
            boolean shouldStore = shulkerSlot != -1 && transferableSlots > 0 && emptySlots <= 1;
            if (shouldStore) {
                pendingDropSlots.clear(); // Hủy toàn bộ hàng đợi vứt rác cũ để tránh race condition
                shulkerClearingInProgress = true;
                shulkerBoxCountBefore = countShulkerBoxesInInventory();
                int occupied = getShulkerOccupiedSlots(inv.get(shulkerSlot));
                String slotDesc = (occupied == 0) ? "trống 100%" : (occupied + "/27 ô đã dùng");
                logDirect("§a[AutoShulker] Balo đã đầy (còn " + emptySlots + " ô trống)! Tự động lấy Shulker Box (" + slotDesc + " tại slot " + shulkerSlot + ") ra đặt để cất " + transferableSlots + " stack vật phẩm...");
                if (isNearLava(ctx.playerFeet(), 5)) {
                    logDirect("§e[AutoShulker] Phát hiện dung nham gần đó (<= 5 block)! Bỏ qua đào mở rộng 3x3 để đảm bảo an toàn tuyệt đối.");
                    shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
                } else {
                    logDirect("§a[AutoShulker] Đang dọn dẹp không gian 3x3 quanh vị trí đặt Shulker Box để đảm bảo thông thoáng...");
                    shulkerClearOrigin = ctx.playerFeet();
                    shulkerState = ShulkerStorageState.CLEAR_SPACE;
                }
                shulkerStateTicks = 0;
                shulkerConsecutiveNoTransfer = 0;
                shulkerUntransferableSlots.clear();
                shulkerTransferredCount = 0;
                baritone.getPathingBehavior().cancelSegmentIfSafe();
                baritone.getInputOverrideHandler().clearAllKeys();
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            } else if (transferableSlots > 0 && emptySlots <= 2) {
                // Kiểm tra điều kiện mua Shulker Box từ /shop:
                // "thêm nếu ko còn shuker nào hoặc ko còn shuker nào trống thì dùng /shop..."
                if (Baritone.settings().autoBuyShulker.value && shulkerSlot == -1 && shulkerCooldownTicks <= 0) {
                    int totalShulkers = countShulkerBoxesInInventory();
                    String reason = (totalShulkers == 0)
                            ? "Không còn Shulker Box nào trong balo"
                            : "Toàn bộ " + totalShulkers + " Shulker Box trong balo đều đã đầy (27/27)";
                    logDirect("§e[AutoShop] " + reason + " và balo sắp đầy (" + emptySlots + " ô trống)! Tự động dùng /shop để mua Shulker Box mới...");
                    shopRetryCount = 0;
                    shopActionCooldown = 0;
                    shopPurchasedCountBefore = totalShulkers;
                    shulkerState = ShulkerStorageState.SHOP_PREPARE_SLOT;
                    shulkerStateTicks = 0;
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    baritone.getInputOverrideHandler().clearAllKeys();
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                } else {
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
                        shulkerCooldownTicks = 400; // Cooldown 20s để bot tiếp tục đào và dùng AutoDrop
                    }
                }
            }

            // Tự động mua thức ăn từ /shop nếu hết cả trong hotbar lẫn balo
            if (Baritone.settings().autoBuyFood.value && countFoodInInventory() == 0 && foodCooldownTicks <= 0) {
                int shulkerFoodSlot = findShulkerBoxWithFoodSlot();
                if (shulkerFoodSlot != -1) {
                    triggerShulkerRetrieveFood(shulkerFoodSlot);
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                foodPurchasedCountBefore = countFoodInInventory();
                logDirect("§e[AutoShop] Hết đồ ăn trong cả hotbar lẫn balo! Tự động mở /shop để mua 64 Thịt Bò Nướng...");
                shopRetryCount = 0;
                shopActionCooldown = 0;
                shulkerState = ShulkerStorageState.SHOP_FOOD_PREPARE_SLOT;
                shulkerStateTicks = 0;
                baritone.getPathingBehavior().cancelSegmentIfSafe();
                baritone.getInputOverrideHandler().clearAllKeys();
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            return null;
        }

        // Đang trong quy trình Shulker: tạm dừng di chuyển để thao tác
        baritone.getPathingBehavior().cancelSegmentIfSafe();
        baritone.getInputOverrideHandler().clearAllKeys();
        shulkerStateTicks++;

        switch (shulkerState) {
            case SHOP_PREPARE_SLOT -> {
                // YÊU CẦU: "nhớ dành 1 ô trống để cho shuker box vào"
                NonNullList<ItemStack> currentInv = ctx.player().getInventory().getNonEquipmentItems();
                int emptyCount = 0;
                for (int i = 0; i < 36; i++) {
                    if (currentInv.get(i).isEmpty()) emptyCount++;
                }

                if (emptyCount >= 1) {
                    // Đã có ít nhất 1 ô trống sẵn sàng đón nhận Shulker Box
                    shulkerState = ShulkerStorageState.SHOP_SEND_CMD;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 2;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Nếu emptyCount == 0 (balo đầy 100%): Cần vứt bớt 1 stack rác để chừa đúng 1 ô trống!
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int trashSlot = -1;
                // Ưu tiên 1: Tìm rác không được bảo vệ (không phải đồ quý, không phải shulker)
                for (int i = 1; i < 36; i++) {
                    ItemStack s = currentInv.get(i);
                    if (!s.isEmpty() && !isProtectedFromDrop(s)) {
                        trashSlot = i;
                        break;
                    }
                }
                // Ưu tiên 2: Tìm block xây dựng thừa
                if (trashSlot == -1) {
                    for (int i = 1; i < 36; i++) {
                        ItemStack s = currentInv.get(i);
                        if (!s.isEmpty() && isBuildingBlock(s) && !isShulkerBox(s) && !isEnderChest(s)) {
                            trashSlot = i;
                            break;
                        }
                    }
                }
                // Ưu tiên 3: Vật phẩm bất kỳ ngoại trừ Shulker Box, Ender Chest, Tool, Totem, Đồ ăn
                if (trashSlot == -1) {
                    for (int i = 1; i < 36; i++) {
                        ItemStack s = currentInv.get(i);
                        if (!s.isEmpty() && !isShulkerBox(s) && !isEnderChest(s) && !isToolOrEssential(s) && !isGoodFood(s) && !s.is(Items.TOTEM_OF_UNDYING)) {
                            trashSlot = i;
                            break;
                        }
                    }
                }

                if (trashSlot != -1) {
                    int windowSlot = (trashSlot < 9) ? (trashSlot + 36) : trashSlot;
                    logDirect("§e[AutoShop] Balo đầy 100%! Đang vứt 1 stack rác (" + currentInv.get(trashSlot).getHoverName().getString() + ") tại ô " + trashSlot + " để dành 1 ô trống nhận Shulker Box...");
                    Rotation dropRot = findBestDropRotation();
                    if (dropRot != null) {
                        baritone.getLookBehavior().updateTarget(dropRot, true);
                        if (!LookBehavior.isF5(ctx)) {
                            ctx.player().setYRot(dropRot.getYaw());
                            ctx.player().setXRot(dropRot.getPitch());
                        }
                    }
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 1, ClickType.THROW, ctx.player());
                    shopActionCooldown = 5;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 20) {
                    shulkerState = ShulkerStorageState.SHOP_SEND_CMD;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_SEND_CMD -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                    ctx.player().closeContainer();
                    shopActionCooldown = 5;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().connection != null) {
                    logDirect("§a[AutoShop] Gửi lệnh /shop...");
                    ctx.player().connection.sendCommand("shop");
                }
                shulkerState = ShulkerStorageState.SHOP_WAIT_MAIN_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 6;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_WAIT_MAIN_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                boolean isContainerOpen = ctx.player().containerMenu != ctx.player().inventoryMenu;
                if (!isContainerOpen) {
                    if (shulkerStateTicks > 60) {
                        shopRetryCount++;
                        if (shopRetryCount <= 2) {
                            logDirect("§e[AutoShop] Chờ menu SHOP quá 3s! Gửi lại lệnh /shop (lần " + shopRetryCount + ")...");
                            shulkerState = ShulkerStorageState.SHOP_SEND_CMD;
                            shulkerStateTicks = 0;
                        } else {
                            logDirect("§c[AutoShop] Máy chủ không mở menu SHOP! Hủy quy trình mua Shulker.");
                            shulkerState = ShulkerStorageState.IDLE;
                            shulkerCooldownTicks = 300;
                        }
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu SHOP đã mở (Hình 1):
                // YÊU CẦU: "click vào ô thứ 12" -> slot index 11 (End Stone)
                int targetSlot = 11;
                int containerSize = ctx.player().containerMenu.slots.size();
                ItemStack s11 = containerSize > 11 ? ctx.player().containerMenu.getSlot(11).getItem() : ItemStack.EMPTY;
                if (!s11.is(Items.END_STONE)) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        if (s.is(Items.END_STONE) || s.getHoverName().getString().toUpperCase().contains("END")) {
                            targetSlot = i;
                            break;
                        }
                    }
                }

                int containerId = ctx.player().containerMenu.containerId;
                logDirect("§a[AutoShop] Menu SHOP đã mở! Click vào ô thứ 12 (slot " + targetSlot + " - End Stone)...");
                ctx.playerController().windowClick(containerId, targetSlot, 0, ClickType.PICKUP, ctx.player());
                shulkerState = ShulkerStorageState.SHOP_WAIT_END_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 8;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_WAIT_END_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng giữa chừng khi đang chờ SHOP -> END! Thử lại...");
                        shulkerState = ShulkerStorageState.SHOP_SEND_CMD;
                        shulkerStateTicks = 0;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu SHOP -> END đã mở (Hình 2):
                // YÊU CẦU: "rồi lại click tiếp vào shuker box"
                int containerSize = ctx.player().containerMenu.slots.size();
                int shulkerBoxSlot = -1;

                // Kiểm tra ô chuẩn slot 17 (Hình 2: hàng 2, cột cuối = 9 + 8 = 17)
                if (containerSize > 17) {
                    ItemStack s17 = ctx.player().containerMenu.getSlot(17).getItem();
                    if (isShulkerBox(s17) || s17.is(Items.SHULKER_BOX)) {
                        shulkerBoxSlot = 17;
                    }
                }
                // Quét tìm ô Shulker Box nếu slot 17 chưa khớp
                if (shulkerBoxSlot == -1) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        if (isShulkerBox(s) || s.is(Items.SHULKER_BOX) || s.getHoverName().getString().toLowerCase().contains("shulker")) {
                            shulkerBoxSlot = i;
                            break;
                        }
                    }
                }

                if (shulkerBoxSlot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Menu SHOP -> END đã mở! Click vào Shulker Box (ô " + shulkerBoxSlot + ")...");
                    ctx.playerController().windowClick(containerId, shulkerBoxSlot, 0, ClickType.PICKUP, ctx.player());
                    shulkerState = ShulkerStorageState.SHOP_WAIT_CONFIRM_MENU;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 8;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 60) {
                    logDirect("§c[AutoShop] Không tìm thấy Shulker Box trong menu END sau 3s! Đóng menu...");
                    ctx.player().closeContainer();
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_WAIT_CONFIRM_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng giữa chừng khi đang chờ Xác Nhận!");
                        shulkerState = ShulkerStorageState.IDLE;
                        shulkerCooldownTicks = 200;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu Mua Hộp Shulker đã mở (Hình 3):
                // YÊU CẦU: "rồi ấn như hình 3 do it" -> Nút Xác Nhận (kính xanh lá ô 23 với tooltip '✔ XÁC NHẬN')
                int containerSize = ctx.player().containerMenu.slots.size();
                int confirmSlot = -1;

                // Kiểm tra ô chuẩn slot 23 (Hình 3: hàng 3, cột 6 = 18 + 5 = 23)
                if (containerSize > 23) {
                    ItemStack s23 = ctx.player().containerMenu.getSlot(23).getItem();
                    String name = s23.getHoverName().getString().toUpperCase();
                    if (s23.is(Items.LIME_STAINED_GLASS_PANE) || s23.is(Items.GREEN_STAINED_GLASS_PANE)
                            || name.contains("XÁC NHẬN") || name.contains("CONFIRM")) {
                        confirmSlot = 23;
                    }
                }

                // Quét tìm ô kính xanh lá / nút Xác Nhận nếu slot 23 chưa khớp
                if (confirmSlot == -1) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        String name = s.getHoverName().getString().toUpperCase();
                        if (name.contains("XÁC NHẬN") || name.contains("CONFIRM")
                                || s.is(Items.LIME_STAINED_GLASS_PANE) || s.is(Items.GREEN_STAINED_GLASS_PANE)) {
                            confirmSlot = i;
                            break;
                        }
                    }
                }

                if (confirmSlot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Menu Xác Nhận đã mở! Click nút ✔ XÁC NHẬN (ô " + confirmSlot + ")...");
                    ctx.playerController().windowClick(containerId, confirmSlot, 0, ClickType.PICKUP, ctx.player());
                    shulkerState = ShulkerStorageState.SHOP_WAIT_RECEIVE;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 10;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 60) {
                    logDirect("§c[AutoShop] Không tìm thấy nút Xác Nhận sau 3s! Đóng menu...");
                    ctx.player().closeContainer();
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_WAIT_RECEIVE -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Kiểm tra xem đã nhận được Shulker Box mới vào balo chưa
                int currentShulkerCount = countShulkerBoxesInInventory();
                int bestSlot = findBestShulkerBoxSlot();

                if (currentShulkerCount > shopPurchasedCountBefore || bestSlot != -1) {
                    logDirect("§a[AutoShop] Mua Shulker Box thành công! Đã có Shulker Box mới trong balo (Tổng: " + currentShulkerCount + ")!");
                    if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                        ctx.player().closeContainer();
                    }
                    if (ctx.minecraft().screen != null) {
                        ctx.minecraft().setScreen(null);
                    }

                    // Chuyển ngay sang quy trình đặt Shulker Box để cất quặng!
                    shulkerClearingInProgress = true;
                    shulkerBoxCountBefore = countShulkerBoxesInInventory();
                    int occupied = getShulkerOccupiedSlots(ctx.player().getInventory().getNonEquipmentItems().get(bestSlot));
                    String slotDesc = (occupied == 0) ? "trống 100%" : (occupied + "/27 ô đã dùng");
                    int transferable = countTransferableSlots();
                    logDirect("§a[AutoShulker] Tiến hành đặt Shulker Box mới mua (" + slotDesc + " tại ô " + bestSlot + ") ra để cất " + transferable + " stack quặng...");

                    if (isNearLava(ctx.playerFeet(), 5)) {
                        shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
                    } else {
                        shulkerClearOrigin = ctx.playerFeet();
                        shulkerState = ShulkerStorageState.CLEAR_SPACE;
                    }
                    shulkerStateTicks = 0;
                    shulkerConsecutiveNoTransfer = 0;
                    shulkerUntransferableSlots.clear();
                    shulkerTransferredCount = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 40) {
                    if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                        ctx.player().closeContainer();
                    }
                    logDirect("§c[AutoShop] Không nhận được Shulker Box (có thể do không đủ tiền trên server)! Tạm thời tiếp tục đào...");
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerCooldownTicks = 400; // Cooldown 20s
                    return null;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_FOOD_PREPARE_SLOT -> {
                NonNullList<ItemStack> currentInv = ctx.player().getInventory().getNonEquipmentItems();
                int emptyCount = 0;
                for (int i = 0; i < 36; i++) {
                    if (currentInv.get(i).isEmpty()) emptyCount++;
                }

                if (emptyCount >= 1) {
                    shulkerState = ShulkerStorageState.SHOP_FOOD_SEND_CMD;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 2;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int trashSlot = -1;
                for (int i = 1; i < 36; i++) {
                    ItemStack s = currentInv.get(i);
                    if (!s.isEmpty() && !isProtectedFromDrop(s)) {
                        trashSlot = i;
                        break;
                    }
                }
                if (trashSlot == -1) {
                    for (int i = 1; i < 36; i++) {
                        ItemStack s = currentInv.get(i);
                        if (!s.isEmpty() && isBuildingBlock(s) && !isShulkerBox(s) && !isEnderChest(s)) {
                            trashSlot = i;
                            break;
                        }
                    }
                }
                if (trashSlot == -1) {
                    for (int i = 1; i < 36; i++) {
                        ItemStack s = currentInv.get(i);
                        if (!s.isEmpty() && !isShulkerBox(s) && !isEnderChest(s) && !isToolOrEssential(s) && !isGoodFood(s) && !s.is(Items.TOTEM_OF_UNDYING)) {
                            trashSlot = i;
                            break;
                        }
                    }
                }

                if (trashSlot != -1) {
                    int windowSlot = (trashSlot < 9) ? (trashSlot + 36) : trashSlot;
                    logDirect("§e[AutoShop] Balo đầy 100%! Đang vứt 1 stack rác (" + currentInv.get(trashSlot).getHoverName().getString() + ") tại ô " + trashSlot + " để dành 1 ô trống nhận Thịt...");
                    Rotation dropRot = findBestDropRotation();
                    if (dropRot != null) {
                        baritone.getLookBehavior().updateTarget(dropRot, true);
                        if (!LookBehavior.isF5(ctx)) {
                            ctx.player().setYRot(dropRot.getYaw());
                            ctx.player().setXRot(dropRot.getPitch());
                        }
                    }
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 1, ClickType.THROW, ctx.player());
                    shopActionCooldown = 5;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 20) {
                    shulkerState = ShulkerStorageState.SHOP_FOOD_SEND_CMD;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_FOOD_SEND_CMD -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                    ctx.player().closeContainer();
                    shopActionCooldown = 5;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().connection != null) {
                    logDirect("§a[AutoShop] Gửi lệnh /shop để mua thịt...");
                    ctx.player().connection.sendCommand("shop");
                }
                shulkerState = ShulkerStorageState.SHOP_FOOD_WAIT_MAIN_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 6;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_FOOD_WAIT_MAIN_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                boolean isContainerOpen = ctx.player().containerMenu != ctx.player().inventoryMenu;
                if (!isContainerOpen) {
                    if (shulkerStateTicks > 60) {
                        shopRetryCount++;
                        if (shopRetryCount <= 2) {
                            logDirect("§e[AutoShop] Chờ menu SHOP quá 3s! Gửi lại lệnh /shop (lần " + shopRetryCount + ")...");
                            shulkerState = ShulkerStorageState.SHOP_FOOD_SEND_CMD;
                            shulkerStateTicks = 0;
                        } else {
                            logDirect("§c[AutoShop] Máy chủ không mở menu SHOP! Hủy mua đồ ăn.");
                            shulkerState = ShulkerStorageState.IDLE;
                            foodCooldownTicks = 300;
                        }
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu SHOP (Hình 0): Ô 14 là Cooked Beef (danh mục FOOD)
                int targetSlot = 14;
                int containerSize = ctx.player().containerMenu.slots.size();
                ItemStack s14 = containerSize > 14 ? ctx.player().containerMenu.getSlot(14).getItem() : ItemStack.EMPTY;
                if (!s14.is(Items.COOKED_BEEF)) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        if (s.is(Items.COOKED_BEEF) || s.getHoverName().getString().toUpperCase().contains("FOOD")) {
                            targetSlot = i;
                            break;
                        }
                    }
                }

                int containerId = ctx.player().containerMenu.containerId;
                logDirect("§a[AutoShop] Menu SHOP đã mở! Click danh mục FOOD (ô " + targetSlot + ")...");
                ctx.playerController().windowClick(containerId, targetSlot, 0, ClickType.PICKUP, ctx.player());
                shulkerState = ShulkerStorageState.SHOP_FOOD_WAIT_FOOD_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 8;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_FOOD_WAIT_FOOD_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng giữa chừng khi đang chờ SHOP -> FOOD! Thử lại...");
                        shulkerState = ShulkerStorageState.SHOP_FOOD_SEND_CMD;
                        shulkerStateTicks = 0;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu SHOP -> FOOD (Hình 1): Thịt Bò Nướng tại ô 15
                int containerSize = ctx.player().containerMenu.slots.size();
                int beefSlot = -1;

                if (containerSize > 15) {
                    ItemStack s15 = ctx.player().containerMenu.getSlot(15).getItem();
                    if (s15.is(Items.COOKED_BEEF) || s15.getHoverName().getString().toUpperCase().contains("BÒ")) {
                        beefSlot = 15;
                    }
                }
                if (beefSlot == -1) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        if (s.is(Items.COOKED_BEEF) || s.getHoverName().getString().toUpperCase().contains("BÒ") || s.getHoverName().getString().toUpperCase().contains("BEEF")) {
                            beefSlot = i;
                            break;
                        }
                    }
                }

                if (beefSlot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Menu FOOD đã mở! Click chọn Thịt Bò Nướng (ô " + beefSlot + ")...");
                    ctx.playerController().windowClick(containerId, beefSlot, 0, ClickType.PICKUP, ctx.player());
                    shulkerState = ShulkerStorageState.SHOP_FOOD_SET_QUANTITY;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 8;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 60) {
                    logDirect("§c[AutoShop] Không tìm thấy Thịt Bò Nướng trong menu FOOD sau 3s! Đóng menu...");
                    ctx.player().closeContainer();
                    shulkerState = ShulkerStorageState.IDLE;
                    foodCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_FOOD_SET_QUANTITY -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng giữa chừng khi đang chọn số lượng thịt!");
                        shulkerState = ShulkerStorageState.IDLE;
                        foodCooldownTicks = 200;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu Mua Thịt Bò Nướng (Hình 2): Chọn số lượng 64 tại ô 17 (kính xanh lá ngoài cùng bên phải)
                int containerSize = ctx.player().containerMenu.slots.size();
                int qty64Slot = -1;

                if (containerSize > 17) {
                    ItemStack s17 = ctx.player().containerMenu.getSlot(17).getItem();
                    if (s17.is(Items.LIME_STAINED_GLASS_PANE) || s17.is(Items.GREEN_STAINED_GLASS_PANE)) {
                        qty64Slot = 17;
                    }
                }
                if (qty64Slot == -1) {
                    for (int i = 17; i >= 14; i--) {
                        if (i < containerSize) {
                            ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                            if (s.is(Items.LIME_STAINED_GLASS_PANE) || s.is(Items.GREEN_STAINED_GLASS_PANE)) {
                                qty64Slot = i;
                                break;
                            }
                        }
                    }
                }

                if (qty64Slot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Click tăng số lượng lên 64 Thịt Bò Nướng (ô " + qty64Slot + ")...");
                    ctx.playerController().windowClick(containerId, qty64Slot, 0, ClickType.PICKUP, ctx.player());
                }

                shulkerState = ShulkerStorageState.SHOP_FOOD_WAIT_CONFIRM_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 6;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_FOOD_WAIT_CONFIRM_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng khi chờ Xác Nhận mua thịt!");
                        shulkerState = ShulkerStorageState.IDLE;
                        foodCooldownTicks = 200;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Nút Xác Nhận (Hình 3): Kính xanh lá tại hàng 3 (slot 22 hoặc 23) với tooltip ✔ XÁC NHẬN
                int containerSize = ctx.player().containerMenu.slots.size();
                int confirmSlot = -1;

                if (containerSize > 22) {
                    ItemStack s22 = ctx.player().containerMenu.getSlot(22).getItem();
                    String name = s22.getHoverName().getString().toUpperCase();
                    if (s22.is(Items.LIME_STAINED_GLASS_PANE) || s22.is(Items.GREEN_STAINED_GLASS_PANE)
                            || name.contains("XÁC NHẬN") || name.contains("CONFIRM")) {
                        confirmSlot = 22;
                    }
                }
                if (confirmSlot == -1 && containerSize > 23) {
                    ItemStack s23 = ctx.player().containerMenu.getSlot(23).getItem();
                    String name = s23.getHoverName().getString().toUpperCase();
                    if (s23.is(Items.LIME_STAINED_GLASS_PANE) || s23.is(Items.GREEN_STAINED_GLASS_PANE)
                            || name.contains("XÁC NHẬN") || name.contains("CONFIRM")) {
                        confirmSlot = 23;
                    }
                }
                if (confirmSlot == -1) {
                    for (int i = 18; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        String name = s.getHoverName().getString().toUpperCase();
                        if (name.contains("XÁC NHẬN") || name.contains("CONFIRM")
                                || s.is(Items.LIME_STAINED_GLASS_PANE) || s.is(Items.GREEN_STAINED_GLASS_PANE)) {
                            confirmSlot = i;
                            break;
                        }
                    }
                }

                if (confirmSlot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Click nút ✔ XÁC NHẬN mua 64 Thịt Bò Nướng (ô " + confirmSlot + ")...");
                    ctx.playerController().windowClick(containerId, confirmSlot, 0, ClickType.PICKUP, ctx.player());
                    shulkerState = ShulkerStorageState.SHOP_FOOD_WAIT_RECEIVE;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 10;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 60) {
                    logDirect("§c[AutoShop] Không tìm thấy nút Xác Nhận mua thịt sau 3s! Đóng menu...");
                    ctx.player().closeContainer();
                    shulkerState = ShulkerStorageState.IDLE;
                    foodCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_FOOD_WAIT_RECEIVE -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int currentFoodCount = countFoodInInventory();
                if (currentFoodCount > foodPurchasedCountBefore || currentFoodCount > 0) {
                    logDirect("§a[AutoShop] Mua Thịt Bò Nướng thành công! Đã có " + currentFoodCount + " thức ăn trong balo!");
                    if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                        ctx.player().closeContainer();
                    }
                    if (ctx.minecraft().screen != null) {
                        ctx.minecraft().setScreen(null);
                    }
                    // Tự động chuyển ngay 1 stack thịt bò từ balo ra hotbar!
                    ensureFoodInHotbar();
                    shulkerState = ShulkerStorageState.IDLE;
                    foodCooldownTicks = 0;
                    return null;
                }

                if (shulkerStateTicks > 40) {
                    if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                        ctx.player().closeContainer();
                    }
                    logDirect("§c[AutoShop] Không nhận được thịt bò (có thể do không đủ tiền trên server)! Tạm thời tiếp tục đào...");
                    shulkerState = ShulkerStorageState.IDLE;
                    foodCooldownTicks = 400; // Cooldown 20s
                    return null;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_TOTEM_PREPARE_SLOT -> {
                NonNullList<ItemStack> currentInv = ctx.player().getInventory().getNonEquipmentItems();
                int emptyCount = 0;
                for (int i = 0; i < 36; i++) {
                    if (currentInv.get(i).isEmpty()) emptyCount++;
                }

                if (emptyCount >= 1) {
                    shulkerState = ShulkerStorageState.SHOP_TOTEM_SEND_CMD;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 2;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int trashSlot = -1;
                for (int i = 1; i < 36; i++) {
                    ItemStack s = currentInv.get(i);
                    if (!s.isEmpty() && !isProtectedFromDrop(s)) {
                        trashSlot = i;
                        break;
                    }
                }
                if (trashSlot == -1) {
                    for (int i = 1; i < 36; i++) {
                        ItemStack s = currentInv.get(i);
                        if (!s.isEmpty() && isBuildingBlock(s) && !isShulkerBox(s) && !isEnderChest(s)) {
                            trashSlot = i;
                            break;
                        }
                    }
                }
                if (trashSlot == -1) {
                    for (int i = 1; i < 36; i++) {
                        ItemStack s = currentInv.get(i);
                        if (!s.isEmpty() && !isShulkerBox(s) && !isEnderChest(s) && !isToolOrEssential(s) && !isGoodFood(s) && !s.is(Items.TOTEM_OF_UNDYING)) {
                            trashSlot = i;
                            break;
                        }
                    }
                }

                if (trashSlot != -1) {
                    int windowSlot = (trashSlot < 9) ? (trashSlot + 36) : trashSlot;
                    logDirect("§e[AutoShop] Balo đầy 100%! Đang vứt 1 stack rác (" + currentInv.get(trashSlot).getHoverName().getString() + ") tại ô " + trashSlot + " để dành 1 ô trống nhận Totem...");
                    Rotation dropRot = findBestDropRotation();
                    if (dropRot != null) {
                        baritone.getLookBehavior().updateTarget(dropRot, true);
                        if (!LookBehavior.isF5(ctx)) {
                            ctx.player().setYRot(dropRot.getYaw());
                            ctx.player().setXRot(dropRot.getPitch());
                        }
                    }
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 1, ClickType.THROW, ctx.player());
                    shopActionCooldown = 5;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 20) {
                    shulkerState = ShulkerStorageState.SHOP_TOTEM_SEND_CMD;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_TOTEM_SEND_CMD -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                    ctx.player().closeContainer();
                    shopActionCooldown = 5;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().connection != null) {
                    logDirect("§a[AutoShop] Gửi lệnh /shop để mua Totem...");
                    ctx.player().connection.sendCommand("shop");
                }
                shulkerState = ShulkerStorageState.SHOP_TOTEM_WAIT_MAIN_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 6;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_TOTEM_WAIT_MAIN_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                boolean isContainerOpen = ctx.player().containerMenu != ctx.player().inventoryMenu;
                if (!isContainerOpen) {
                    if (shulkerStateTicks > 60) {
                        shopRetryCount++;
                        if (shopRetryCount <= 2) {
                            logDirect("§e[AutoShop] Chờ menu SHOP quá 3s! Gửi lại lệnh /shop (lần " + shopRetryCount + ")...");
                            shulkerState = ShulkerStorageState.SHOP_TOTEM_SEND_CMD;
                            shulkerStateTicks = 0;
                        } else {
                            logDirect("§c[AutoShop] Máy chủ không mở menu SHOP! Hủy mua Totem.");
                            shulkerState = ShulkerStorageState.IDLE;
                            totemCooldownTicks = 300;
                        }
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu SHOP (Hình 0): Ô 13 là Totem of Undying (danh mục GEAR)
                int targetSlot = 13;
                int containerSize = ctx.player().containerMenu.slots.size();
                ItemStack s13 = containerSize > 13 ? ctx.player().containerMenu.getSlot(13).getItem() : ItemStack.EMPTY;
                if (!s13.is(Items.TOTEM_OF_UNDYING)) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        String name = s.getHoverName().getString().toUpperCase();
                        if (s.is(Items.TOTEM_OF_UNDYING) || name.contains("GEAR")) {
                            targetSlot = i;
                            break;
                        }
                    }
                }

                int containerId = ctx.player().containerMenu.containerId;
                logDirect("§a[AutoShop] Menu SHOP đã mở! Click danh mục GEAR (ô " + targetSlot + ")...");
                ctx.playerController().windowClick(containerId, targetSlot, 0, ClickType.PICKUP, ctx.player());
                shulkerState = ShulkerStorageState.SHOP_TOTEM_WAIT_GEAR_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 8;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_TOTEM_WAIT_GEAR_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng giữa chừng khi đang chờ SHOP -> GEAR! Thử lại...");
                        shulkerState = ShulkerStorageState.SHOP_TOTEM_SEND_CMD;
                        shulkerStateTicks = 0;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu SHOP -> GEAR (Hình 1): Vật tổ trường sinh tại ô 13
                int containerSize = ctx.player().containerMenu.slots.size();
                int totemSlot = -1;

                if (containerSize > 13) {
                    ItemStack s13 = ctx.player().containerMenu.getSlot(13).getItem();
                    String name = s13.getHoverName().getString().toLowerCase();
                    if (s13.is(Items.TOTEM_OF_UNDYING) || name.contains("tổ") || name.contains("totem") || name.contains("trường sinh")) {
                        totemSlot = 13;
                    }
                }
                if (totemSlot == -1) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        String name = s.getHoverName().getString().toLowerCase();
                        if (s.is(Items.TOTEM_OF_UNDYING) || name.contains("vật tổ") || name.contains("totem") || name.contains("trường sinh")) {
                            totemSlot = i;
                            break;
                        }
                    }
                }

                if (totemSlot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Menu GEAR đã mở! Click chọn Vật tổ trường sinh (ô " + totemSlot + ")...");
                    ctx.playerController().windowClick(containerId, totemSlot, 0, ClickType.PICKUP, ctx.player());
                    shulkerState = ShulkerStorageState.SHOP_TOTEM_WAIT_CONFIRM_MENU;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 8;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 60) {
                    logDirect("§c[AutoShop] Không tìm thấy Vật tổ trường sinh trong menu GEAR sau 3s! Đóng menu...");
                    ctx.player().closeContainer();
                    shulkerState = ShulkerStorageState.IDLE;
                    totemCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_TOTEM_WAIT_CONFIRM_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng giữa chừng khi đang chờ Xác Nhận mua Totem!");
                        shulkerState = ShulkerStorageState.IDLE;
                        totemCooldownTicks = 200;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu Mua Vật tổ trường sinh (Hình 2): Nút Xác Nhận tại ô 23 (kính xanh lá với tooltip '✔ XÁC NHẬN')
                int containerSize = ctx.player().containerMenu.slots.size();
                int confirmSlot = -1;

                if (containerSize > 23) {
                    ItemStack s23 = ctx.player().containerMenu.getSlot(23).getItem();
                    String name = s23.getHoverName().getString().toUpperCase();
                    if (s23.is(Items.LIME_STAINED_GLASS_PANE) || s23.is(Items.GREEN_STAINED_GLASS_PANE)
                            || name.contains("XÁC NHẬN") || name.contains("CONFIRM")) {
                        confirmSlot = 23;
                    }
                }

                if (confirmSlot == -1) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        String name = s.getHoverName().getString().toUpperCase();
                        if (name.contains("XÁC NHẬN") || name.contains("CONFIRM")
                                || s.is(Items.LIME_STAINED_GLASS_PANE) || s.is(Items.GREEN_STAINED_GLASS_PANE)) {
                            confirmSlot = i;
                            break;
                        }
                    }
                }

                if (confirmSlot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Menu Xác Nhận đã mở! Click nút ✔ XÁC NHẬN mua Vật tổ trường sinh (ô " + confirmSlot + ")...");
                    ctx.playerController().windowClick(containerId, confirmSlot, 0, ClickType.PICKUP, ctx.player());
                    shulkerState = ShulkerStorageState.SHOP_TOTEM_WAIT_RECEIVE;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 10;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 60) {
                    logDirect("§c[AutoShop] Không tìm thấy nút Xác Nhận mua Totem sau 3s! Đóng menu...");
                    ctx.player().closeContainer();
                    shulkerState = ShulkerStorageState.IDLE;
                    totemCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SHOP_TOTEM_WAIT_RECEIVE -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int currentCount = getTotemCount();
                if (currentCount > totemPurchasedCountBefore || currentCount > 0) {
                    logDirect("§a[AutoShop] Mua Vật tổ trường sinh thành công! Đã có Totem trong người (Tổng: " + currentCount + ")!");
                    if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                        ctx.player().closeContainer();
                    }
                    if (ctx.minecraft().screen != null) {
                        ctx.minecraft().setScreen(null);
                    }
                    handleAutoTotem();
                    shulkerState = ShulkerStorageState.IDLE;
                    totemCooldownTicks = 40;
                    return null;
                }

                if (shulkerStateTicks > 40) {
                    if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                        ctx.player().closeContainer();
                    }
                    logDirect("§c[AutoShop] Không nhận được Totem (có thể do không đủ 1.25K tiền trên server)! Tạm hoãn 15s...");
                    shulkerState = ShulkerStorageState.IDLE;
                    totemCooldownTicks = 300;
                    return null;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case CLEAR_SPACE -> {
                if (shulkerClearOrigin == null) {
                    shulkerClearOrigin = ctx.playerFeet();
                }

                // Nếu timeout quá 200 tick (10s), bỏ qua dọn dẹp và đặt luôn
                if (shulkerStateTicks > 200) {
                    logDirect("§e[AutoShulker] Dọn dẹp 3x3 hết thời gian chờ (10s)! Tiến hành đặt Shulker Box...");
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    activeMiningBlock = null;
                    activeMiningBlockIsObstructing = false;
                    activeMiningTicks = 0;
                    shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Kiểm tra nếu block đang đập dở đã vỡ thành Air
                if (activeMiningBlock != null && activeMiningBlockIsObstructing) {
                    BlockState s = ctx.world().getBlockState(activeMiningBlock);
                    if (s.isAir()) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                        activeMiningBlock = null;
                        activeMiningBlockIsObstructing = false;
                        activeMiningTicks = 0;
                    }
                }

                BlockPos targetBreak = null;
                Rotation targetRot = null;
                BlockState targetState = null;

                // Ưu tiên tiếp tục đào block đang đập dở nếu vẫn hợp lệ
                if (activeMiningBlock != null && activeMiningBlockIsObstructing) {
                    BlockState s = ctx.world().getBlockState(activeMiningBlock);
                    if (!s.isAir() && s.getDestroySpeed(ctx.world(), activeMiningBlock) >= 0) {
                        Optional<Rotation> rot = RotationUtils.reachable(ctx, activeMiningBlock);
                        if (rot.isPresent()) {
                            targetBreak = activeMiningBlock;
                            targetRot = rot.get();
                            targetState = s;
                        }
                    }
                }

                if (targetBreak == null) {
                    // Quét vùng 3x3 quanh shulkerClearOrigin
                    // dy: 1 (ngang mắt), 0 (dưới chân), 2 (trên trần đầu)
                    int[] dyOrder = new int[]{1, 0, 2};
                    double bestDist = Double.MAX_VALUE;
                    for (int dy : dyOrder) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockPos p = shulkerClearOrigin.offset(dx, dy, dz);
                                BlockState s = ctx.world().getBlockState(p);
                                if (s.isAir() || s.getBlock() instanceof ShulkerBoxBlock) {
                                    continue;
                                }
                                if (s.getDestroySpeed(ctx.world(), p) < 0) {
                                    continue;
                                }
                                if (isNearLava(p, 2)) {
                                    continue;
                                }
                                if (MovementHelper.avoidBreaking(baritone.bsi, p.getX(), p.getY(), p.getZ(), s)) {
                                    continue;
                                }
                                Optional<Rotation> rot = RotationUtils.reachable(ctx, p);
                                if (rot.isPresent()) {
                                    double d = ctx.playerFeet().distSqr(p);
                                    if (d < bestDist) {
                                        bestDist = d;
                                        targetBreak = p;
                                        targetRot = rot.get();
                                        targetState = s;
                                    }
                                }
                            }
                        }
                    }
                }

                if (targetBreak != null) {
                    activeMiningBlock = targetBreak;
                    activeMiningBlockIsObstructing = true;
                    activeMiningTicks++;
                    clearMovementKeysKeepAttack();
                    baritone.getLookBehavior().updateTarget(targetRot, true);
                    if (!LookBehavior.isF5(ctx)) {
                        ctx.player().setYRot(targetRot.getYaw());
                        ctx.player().setXRot(targetRot.getPitch());
                    }
                    MovementHelper.switchToBestToolFor(ctx, targetState);
                    if (isAimedAtBlock(targetBreak, targetRot)) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    } else {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Đã dọn sạch mọi block cản trở trong vùng 3x3!
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                activeMiningBlock = null;
                activeMiningBlockIsObstructing = false;
                activeMiningTicks = 0;
                logDirect("§a[AutoShulker] Không gian 3x3 đã được dọn sạch sẽ, thông thoáng! Tiến hành đặt Shulker Box...");
                shulkerState = ShulkerStorageState.SWAP_TO_HOTBAR;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case SWAP_TO_HOTBAR -> {
                int slot = -1;
                if (shulkerMode == ShulkerMode.RETRIEVE_FOOD) {
                    slot = findShulkerBoxWithFoodSlot();
                } else if (shulkerMode == ShulkerMode.RETRIEVE_TOOL) {
                    slot = findShulkerBoxWithToolSlot();
                } else if (shulkerMode == ShulkerMode.RETRIEVE_TOTEM) {
                    slot = findShulkerBoxWithTotemSlot();
                } else {
                    slot = findBestShulkerBoxSlot();
                }

                if (slot == -1) {
                    if (shulkerMode == ShulkerMode.RETRIEVE_FOOD) {
                        logDirect("§e[AutoShulker] Không tìm thấy Shulker Box chứa đồ ăn trong balo! Hủy quy trình.");
                    } else if (shulkerMode == ShulkerMode.RETRIEVE_TOOL) {
                        logDirect("§e[AutoShulker] Không tìm thấy Shulker Box chứa Cúp trong balo! Hủy quy trình.");
                    } else if (shulkerMode == ShulkerMode.RETRIEVE_TOTEM) {
                        logDirect("§e[AutoShulker] Không tìm thấy Shulker Box chứa Totem trong balo! Hủy quy trình.");
                    } else {
                        logDirect("§e[AutoShulker] Không tìm thấy Shulker Box còn chỗ trống trong balo! Hủy quy trình.");
                    }
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerMode = ShulkerMode.DEPOSIT;
                    shulkerBoxCountBefore = 0;
                    shulkerUntransferableSlots.clear();
                    shulkerCooldownTicks = 300;
                    return null;
                }
                if (shulkerBoxCountBefore <= 0) {
                    shulkerBoxCountBefore = countShulkerBoxesInInventory();
                }
                shulkerOriginalSlot = slot;
                if (slot < 9 && slot > 0) {
                    shulkerHotbarSlot = slot;
                    shulkerState = ShulkerStorageState.SELECT_SLOT;
                    shulkerStateTicks = 0;
                } else {
                    // Swap vào ô hotbar tốt nhất (slot 1-8, không bao giờ đè cúp slot 0)
                    shulkerHotbarSlot = findBestHotbarSlotForShulker();
                    int containerSlot = slot < 9 ? (slot + 36) : slot;
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, containerSlot, shulkerHotbarSlot, ClickType.SWAP, ctx.player());
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
                        logDirect("§c[AutoShulker] Đã xoay 360 độ nhưng không tìm thấy vị trí thích hợp để đặt Shulker Box! Tạm hoãn 15s để tiếp tục đào...");
                        shulkerState = ShulkerStorageState.IDLE;
                        shulkerCooldownTicks = 300; // Cooldown 15s để bot tiếp tục tiến lên tìm không gian rộng hơn
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                ShulkerPlacementTarget pt = targetOpt.get();
                Vec3 hitVec = pt.face == net.minecraft.core.Direction.UP
                        ? new Vec3(pt.againstPos.getX() + 0.5, pt.againstPos.getY() + 0.95, pt.againstPos.getZ() + 0.5)
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
                // Đảm bảo tay chính đang cầm đúng Shulker Box
                if (!isShulkerBox(ctx.player().getMainHandItem())) {
                    for (int h = 0; h < 9; h++) {
                        if (isShulkerBox(ctx.player().getInventory().getNonEquipmentItems().get(h))) {
                            shulkerHotbarSlot = h;
                            break;
                        }
                    }
                }
                ctx.player().getInventory().setSelectedSlot(shulkerHotbarSlot);
                ctx.playerController().syncHeldItem();
                ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                ctx.player().swing(InteractionHand.MAIN_HAND);
                shulkerState = ShulkerStorageState.WAIT_FOR_BLOCK;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case WAIT_FOR_BLOCK -> {
                if (shulkerPlacedPos != null && ctx.world().getBlockState(shulkerPlacedPos).getBlock() instanceof ShulkerBoxBlock) {
                    shulkerState = ShulkerStorageState.OPEN_BOX;
                    shulkerStateTicks = 0;
                } else if (shulkerStateTicks == 10 && shulkerPlacedPos != null) {
                    // Thử click lại nếu mạng bị trễ packet
                    Optional<ShulkerPlacementTarget> retryOpt = findShulkerPlacePos();
                    if (retryOpt.isPresent()) {
                        ShulkerPlacementTarget pt = retryOpt.get();
                        BlockPos againstPure = new BlockPos(pt.againstPos.getX(), pt.againstPos.getY(), pt.againstPos.getZ());
                        Vec3 hitVec = pt.face == net.minecraft.core.Direction.UP
                                ? new Vec3(againstPure.getX() + 0.5, againstPure.getY() + 1.0, againstPure.getZ() + 0.5)
                                : new Vec3(againstPure.getX() + 0.5 + pt.face.getStepX() * 0.5, againstPure.getY() + 0.5, againstPure.getZ() + 0.5 + pt.face.getStepZ() * 0.5);
                        BlockHitResult bhr = new BlockHitResult(hitVec, pt.face, againstPure, false);
                        ctx.player().getInventory().setSelectedSlot(shulkerHotbarSlot);
                        ctx.playerController().syncHeldItem();
                        ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                        ctx.player().swing(InteractionHand.MAIN_HAND);
                    }
                } else if (shulkerStateTicks > 25) {
                    logDirect("§c[AutoShulker] Không thể đặt Shulker Box (server từ chối hoặc lag)! Tạm hoãn 10s...");
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerCooldownTicks = 200; // Cooldown 10s
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

                // === CHẾ ĐỘ 2B: LẤY TOTEM TỪ TRONG SHULKER BOX RA BALO ===
                if (shulkerMode == ShulkerMode.RETRIEVE_TOTEM) {
                    int totemSlot = -1;
                    for (int b = 0; b < 27; b++) {
                        ItemStack boxItem = ctx.player().containerMenu.getSlot(b).getItem();
                        if (boxItem.is(Items.TOTEM_OF_UNDYING)) {
                            totemSlot = b;
                            break;
                        }
                    }
                    if (totemSlot != -1 && shulkerTransferredCount < 2) {
                        ItemStack before = ctx.player().containerMenu.getSlot(totemSlot).getItem().copy();
                        ctx.playerController().windowClick(containerId, totemSlot, 0, ClickType.QUICK_MOVE, ctx.player());
                        ItemStack after = ctx.player().containerMenu.getSlot(totemSlot).getItem();
                        if (before.getCount() == after.getCount()) {
                            logDirect("§c[AutoShulker] Balo đã đầy, không thể lấy thêm Totem từ Shulker Box!");
                            shulkerState = ShulkerStorageState.CLOSE_CONTAINER;
                            shulkerStateTicks = 0;
                            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                        shulkerTransferredCount++;
                        logDirect("§a[AutoShulker] Đã lấy Totem Bất Tử từ Shulker Box vào balo!");
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
                    // BỎ QUA các món thiết yếu: Cúp, Totem, Xô nước, Đồ ăn, và 1 stack block xây dựng giữ lại
                    if (shouldKeepInInventory(stack)) continue;
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

                if (isBoxFull || shulkerTransferredCount == 0) {
                    if (isBoxFull) {
                        logDirect("§6[AutoShulker] Shulker Box đã đầy 100% (27/27 ô)! Đã cất " + shulkerTransferredCount + " stack.");
                    } else {
                        logDirect("§6[AutoShulker] Shulker Box này không thể nhận thêm vật phẩm nào trong balo! (0 stack được chuyển).");
                    }
                    if (shulkerHotbarSlot >= 0 && shulkerHotbarSlot < 9) {
                        blacklistedFullShulkerSlots.add(shulkerHotbarSlot);
                    }
                    if (shulkerOriginalSlot >= 0 && shulkerOriginalSlot < 36) {
                        blacklistedFullShulkerSlots.add(shulkerOriginalSlot);
                    }
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
                    if (isAimedAtBlock(shulkerPlacedPos, rot.get())) {
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
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
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

                    // Sau khi đã cất xong 1 Shulker Box và nhặt lại vào balo an toàn:
                    // Hoàn tất quy trình ngay lập tức! Tuyệt đối không mở liên hoàn nhiều Shulker Box liên tiếp.
                    shulkerClearingInProgress = false;
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerMode = ShulkerMode.DEPOSIT;
                    pendingDropSlots.clear();
                    shulkerCooldownTicks = 200; // Cooldown 10s để bot tiếp tục đào
                    logDirect("§a[AutoShulker] Đã hoàn tất cất đồ vào Shulker Box an toàn! Tiếp tục hành trình đào...");
                    return null;
                }

                // Nếu chưa nhặt được: xác định vị trí thực tế của Shulker Box rơi trên sàn
                ItemEntity droppedItem = findNearbyDroppedShulker();
                Vec3 targetPos = null;
                BlockPos targetBlock = null;
                if (droppedItem != null) {
                    targetPos = droppedItem.position();
                    targetBlock = droppedItem.blockPosition();
                } else if (shulkerPlacedPos != null) {
                    targetPos = new Vec3(shulkerPlacedPos.getX() + 0.5, shulkerPlacedPos.getY(), shulkerPlacedPos.getZ() + 0.5);
                    targetBlock = shulkerPlacedPos;
                }

                if (targetPos != null && targetBlock != null) {
                    Vec3 playerPos = ctx.player().position();
                    double dx = targetPos.x - playerPos.x;
                    double dz = targetPos.z - playerPos.z;
                    double horizontalDistSq = dx * dx + dz * dz;

                    // Quay mặt nhìn về phía item
                    Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), targetPos, ctx.playerRotations());
                    baritone.getLookBehavior().updateTarget(rot, true);

                    // 1. Nếu item ở ngay sát người chơi (<= 0.35 block): dừng di chuyển để hút
                    if (horizontalDistSq <= 0.12 && Math.abs(targetPos.y - playerPos.y) <= 0.8) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
                        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }

                    // 2. YÊU CẦU: "không tiếp cận được shulker box bị rơi thì ĐÀO ĐẾN ĐÓ cũng được"
                    // Sau 10 tick nếu chưa hút được (do vật cản, chênh lệch độ cao, hoặc rơi vào hố/kẹt trong ngách):
                    if (shulkerStateTicks > 10) {
                        // A) Tự động đập block chắn trực tiếp nếu đang va chạm tường chắn hướng về item
                        if (ctx.player().horizontalCollision) {
                            BlockPos obstacle = ctx.playerFeet().relative(Direction.fromYRot(rot.getYaw()));
                            BlockState obsState = ctx.world().getBlockState(obstacle);
                            if (!obsState.isAir() && !obsState.canBeReplaced() && !(obsState.getBlock() instanceof ShulkerBoxBlock)) {
                                MovementHelper.switchToBestToolFor(ctx, obsState);
                                Optional<Rotation> reachRot = RotationUtils.reachable(ctx, obstacle);
                                if (reachRot.isPresent()) {
                                    baritone.getLookBehavior().updateTarget(reachRot.get(), true);
                                    if (isAimedAtBlock(obstacle, reachRot.get())) {
                                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                                    }
                                }
                            }
                        }

                        // B) Kích hoạt A* Pathfinding để đào thông đường và nhảy tới vị trí Shulker Box rơi!
                        GoalBlock goal = new GoalBlock(targetBlock);
                        return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
                    }

                    // Trong 10 tick đầu: di chuyển bước tới nhặt nhanh
                    if (horizontalDistSq > 0.08) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                    } else {
                        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
                    }

                    if (ctx.player().horizontalCollision || targetPos.y > playerPos.y + 0.5) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                    } else {
                        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                    }
                } else {
                    baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
                    baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                }

                // Báo log định kỳ mỗi 40 tick (2 giây) để người chơi biết bot đang đào tiếp cận
                if (shulkerStateTicks > 0 && shulkerStateTicks % 40 == 0) {
                    logDirect("§e[AutoShulker] Đang đào thông đường tiếp cận để nhặt lại Shulker Box (" + (shulkerStateTicks / 20) + "s)...");
                }

                // Timeout an toàn: 600 tick (30 giây) để bot có đủ thời gian đào qua các vách đá/địa hình
                if (shulkerStateTicks > 600) {
                    baritone.getInputOverrideHandler().clearAllKeys();
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    logDirect("§c[AutoShulker] Quá thời gian chờ nhặt Shulker Box (30s)! Tiếp tục hành trình...");
                    shulkerState = ShulkerStorageState.IDLE;
                    shulkerPlacedPos = null;
                    shulkerStateTicks = 0;
                    shulkerBoxCountBefore = 0;
                    shulkerUntransferableSlots.clear();
                    shulkerClearingInProgress = false;
                    shulkerMode = ShulkerMode.DEPOSIT;
                    shulkerCooldownTicks = 300; // Cooldown 15s
                }

                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            // ==========================================
            // QUY TRÌNH MUA & SỬ DỤNG RƯƠNG ENDER KHI CÓ >= 3 SHULKER BOX ĐẦY
            // ==========================================
            case ENDER_CHEST_SHOP_PREPARE_SLOT -> {
                NonNullList<ItemStack> currentInv = ctx.player().getInventory().getNonEquipmentItems();
                int emptyCount = 0;
                for (int i = 0; i < 36; i++) {
                    if (currentInv.get(i).isEmpty()) emptyCount++;
                }

                if (emptyCount >= 1) {
                    shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_SEND_CMD;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 2;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Nếu emptyCount == 0 (balo đầy 100%): Cần vứt bớt 1 stack rác để chừa đúng 1 ô trống nhận Ender Chest
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int trashSlot = -1;
                for (int i = 1; i < 36; i++) {
                    ItemStack s = currentInv.get(i);
                    if (!s.isEmpty() && !isProtectedFromDrop(s)) {
                        trashSlot = i;
                        break;
                    }
                }
                if (trashSlot == -1) {
                    for (int i = 1; i < 36; i++) {
                        ItemStack s = currentInv.get(i);
                        if (!s.isEmpty() && isBuildingBlock(s) && !isShulkerBox(s) && !isEnderChest(s)) {
                            trashSlot = i;
                            break;
                        }
                    }
                }
                if (trashSlot == -1) {
                    for (int i = 1; i < 36; i++) {
                        ItemStack s = currentInv.get(i);
                        if (!s.isEmpty() && !isShulkerBox(s) && !isEnderChest(s) && !isToolOrEssential(s) && !isGoodFood(s) && !s.is(Items.TOTEM_OF_UNDYING)) {
                            trashSlot = i;
                            break;
                        }
                    }
                }

                if (trashSlot != -1) {
                    int windowSlot = (trashSlot < 9) ? (trashSlot + 36) : trashSlot;
                    logDirect("§e[AutoShop] Balo đầy 100%! Đang vứt 1 stack rác (" + currentInv.get(trashSlot).getHoverName().getString() + ") tại ô " + trashSlot + " để dành 1 ô trống nhận Rương Ender...");
                    Rotation dropRot = findBestDropRotation();
                    if (dropRot != null) {
                        baritone.getLookBehavior().updateTarget(dropRot, true);
                        if (!LookBehavior.isF5(ctx)) {
                            ctx.player().setYRot(dropRot.getYaw());
                            ctx.player().setXRot(dropRot.getPitch());
                        }
                    }
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, windowSlot, 1, ClickType.THROW, ctx.player());
                    shopActionCooldown = 5;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 20) {
                    shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_SEND_CMD;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_SHOP_SEND_CMD -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                    ctx.player().closeContainer();
                    shopActionCooldown = 5;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().connection != null) {
                    logDirect("§a[AutoShop] Gửi lệnh /shop để mua Rương Ender...");
                    ctx.player().connection.sendCommand("shop");
                }
                shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_WAIT_MAIN_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 6;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_SHOP_WAIT_MAIN_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                boolean isContainerOpen = ctx.player().containerMenu != ctx.player().inventoryMenu;
                if (!isContainerOpen) {
                    if (shulkerStateTicks > 60) {
                        shopRetryCount++;
                        if (shopRetryCount <= 2) {
                            logDirect("§e[AutoShop] Chờ menu SHOP quá 3s! Gửi lại lệnh /shop (lần " + shopRetryCount + ")...");
                            shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_SEND_CMD;
                            shulkerStateTicks = 0;
                        } else {
                            logDirect("§c[AutoShop] Máy chủ không mở menu SHOP! Hủy quy trình mua Rương Ender.");
                            shulkerState = ShulkerStorageState.IDLE;
                            enderChestCooldownTicks = 300;
                        }
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu SHOP đã mở (Hình 1): Click vào ô thứ 12 (slot 11 - End Stone)
                int targetSlot = 11;
                int containerSize = ctx.player().containerMenu.slots.size();
                ItemStack s11 = containerSize > 11 ? ctx.player().containerMenu.getSlot(11).getItem() : ItemStack.EMPTY;
                if (!s11.is(Items.END_STONE)) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        if (s.is(Items.END_STONE) || s.getHoverName().getString().toUpperCase().contains("END")) {
                            targetSlot = i;
                            break;
                        }
                    }
                }

                int containerId = ctx.player().containerMenu.containerId;
                logDirect("§a[AutoShop] Menu SHOP đã mở! Click vào ô END (slot " + targetSlot + ")...");
                ctx.playerController().windowClick(containerId, targetSlot, 0, ClickType.PICKUP, ctx.player());
                shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_WAIT_END_MENU;
                shulkerStateTicks = 0;
                shopActionCooldown = 8;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_SHOP_WAIT_END_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng giữa chừng khi đang chờ SHOP -> END! Thử lại...");
                        shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_SEND_CMD;
                        shulkerStateTicks = 0;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu SHOP -> END đã mở (Hình 2):
                // Rương Ender nằm tại ô 9 (hàng 2, cột đầu)
                int containerSize = ctx.player().containerMenu.slots.size();
                int ecSlot = -1;

                if (containerSize > 9) {
                    ItemStack s9 = ctx.player().containerMenu.getSlot(9).getItem();
                    if (isEnderChest(s9) || s9.is(Items.ENDER_CHEST) || s9.getHoverName().getString().toLowerCase().contains("ender")) {
                        ecSlot = 9;
                    }
                }
                if (ecSlot == -1) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        if (isEnderChest(s) || s.is(Items.ENDER_CHEST) || s.getHoverName().getString().toLowerCase().contains("ender")) {
                            ecSlot = i;
                            break;
                        }
                    }
                }

                if (ecSlot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Menu SHOP -> END đã mở! Click vào Rương Ender (ô " + ecSlot + ")...");
                    ctx.playerController().windowClick(containerId, ecSlot, 0, ClickType.PICKUP, ctx.player());
                    shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_WAIT_CONFIRM_MENU;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 8;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 60) {
                    logDirect("§c[AutoShop] Không tìm thấy Rương Ender trong menu END sau 3s! Đóng menu...");
                    ctx.player().closeContainer();
                    shulkerState = ShulkerStorageState.IDLE;
                    enderChestCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_SHOP_WAIT_CONFIRM_MENU -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    if (shulkerStateTicks > 20) {
                        logDirect("§c[AutoShop] Menu bị đóng giữa chừng khi đang chờ Xác Nhận mua Rương Ender!");
                        shulkerState = ShulkerStorageState.IDLE;
                        enderChestCooldownTicks = 200;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Menu Mua Rương Ender (Hình 3): Nút Xác Nhận tại ô 23 (kính xanh lá với tooltip '✔ XÁC NHẬN')
                int containerSize = ctx.player().containerMenu.slots.size();
                int confirmSlot = -1;

                if (containerSize > 23) {
                    ItemStack s23 = ctx.player().containerMenu.getSlot(23).getItem();
                    String name = s23.getHoverName().getString().toUpperCase();
                    if (s23.is(Items.LIME_STAINED_GLASS_PANE) || s23.is(Items.GREEN_STAINED_GLASS_PANE)
                            || name.contains("XÁC NHẬN") || name.contains("CONFIRM")) {
                        confirmSlot = 23;
                    }
                }

                if (confirmSlot == -1) {
                    for (int i = 0; i < Math.min(27, containerSize); i++) {
                        ItemStack s = ctx.player().containerMenu.getSlot(i).getItem();
                        String name = s.getHoverName().getString().toUpperCase();
                        if (name.contains("XÁC NHẬN") || name.contains("CONFIRM")
                                || s.is(Items.LIME_STAINED_GLASS_PANE) || s.is(Items.GREEN_STAINED_GLASS_PANE)) {
                            confirmSlot = i;
                            break;
                        }
                    }
                }

                if (confirmSlot != -1) {
                    int containerId = ctx.player().containerMenu.containerId;
                    logDirect("§a[AutoShop] Menu Xác Nhận đã mở! Click nút ✔ XÁC NHẬN mua Rương Ender (ô " + confirmSlot + ")...");
                    ctx.playerController().windowClick(containerId, confirmSlot, 0, ClickType.PICKUP, ctx.player());
                    shulkerState = ShulkerStorageState.ENDER_CHEST_SHOP_WAIT_RECEIVE;
                    shulkerStateTicks = 0;
                    shopActionCooldown = 10;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 60) {
                    logDirect("§c[AutoShop] Không tìm thấy nút Xác Nhận sau 3s! Đóng menu...");
                    ctx.player().closeContainer();
                    shulkerState = ShulkerStorageState.IDLE;
                    enderChestCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_SHOP_WAIT_RECEIVE -> {
                if (shopActionCooldown > 0) {
                    shopActionCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int currentCount = countEnderChestsInInventory();
                int ecSlot = findEnderChestSlot();

                if (currentCount > enderChestShopPurchasedCountBefore || ecSlot != -1) {
                    logDirect("§a[AutoShop] Mua Rương Ender thành công! Đã có Rương Ender trong balo (slot " + ecSlot + ")!");
                    if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                        ctx.player().closeContainer();
                    }
                    if (ctx.minecraft().screen != null) {
                        ctx.minecraft().setScreen(null);
                    }

                    if (isNearLava(ctx.playerFeet(), 5)) {
                        shulkerState = ShulkerStorageState.ENDER_CHEST_SWAP_TO_HOTBAR;
                    } else {
                        shulkerClearOrigin = ctx.playerFeet();
                        shulkerState = ShulkerStorageState.ENDER_CHEST_CLEAR_SPACE;
                    }
                    shulkerStateTicks = 0;
                    enderChestTransferredCount = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerStateTicks > 40) {
                    if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
                        ctx.player().closeContainer();
                    }
                    if (ctx.minecraft().screen != null) {
                        ctx.minecraft().setScreen(null);
                    }
                    logDirect("§c[AutoShop] Chờ nhận Rương Ender quá thời gian! Kiểm tra lại sau...");
                    shulkerState = ShulkerStorageState.IDLE;
                    enderChestCooldownTicks = 200;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_CLEAR_SPACE -> {
                if (shulkerClearOrigin == null) {
                    shulkerClearOrigin = ctx.playerFeet();
                }

                if (shulkerStateTicks > 200) {
                    logDirect("§e[AutoEnderChest] Dọn dẹp 3x3 hết thời gian chờ (10s)! Tiến hành đặt Rương Ender...");
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    activeMiningBlock = null;
                    activeMiningBlockIsObstructing = false;
                    activeMiningTicks = 0;
                    shulkerState = ShulkerStorageState.ENDER_CHEST_SWAP_TO_HOTBAR;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (activeMiningBlock != null && activeMiningBlockIsObstructing) {
                    BlockState s = ctx.world().getBlockState(activeMiningBlock);
                    if (s.isAir()) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                        activeMiningBlock = null;
                        activeMiningBlockIsObstructing = false;
                        activeMiningTicks = 0;
                    }
                }

                BlockPos targetBreak = null;
                Rotation targetRot = null;
                BlockState targetState = null;

                if (activeMiningBlock != null && activeMiningBlockIsObstructing) {
                    BlockState s = ctx.world().getBlockState(activeMiningBlock);
                    if (!s.isAir() && s.getDestroySpeed(ctx.world(), activeMiningBlock) >= 0) {
                        Optional<Rotation> rot = RotationUtils.reachable(ctx, activeMiningBlock);
                        if (rot.isPresent()) {
                            targetBreak = activeMiningBlock;
                            targetRot = rot.get();
                            targetState = s;
                        }
                    }
                }

                if (targetBreak == null) {
                    int[] dyOrder = new int[]{1, 0, 2};
                    double bestDist = Double.MAX_VALUE;
                    for (int dy : dyOrder) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockPos p = shulkerClearOrigin.offset(dx, dy, dz);
                                BlockState s = ctx.world().getBlockState(p);
                                if (s.isAir() || s.getBlock() instanceof ShulkerBoxBlock || s.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) {
                                    continue;
                                }
                                if (s.getDestroySpeed(ctx.world(), p) < 0 || isNearLava(p, 2) || MovementHelper.avoidBreaking(baritone.bsi, p.getX(), p.getY(), p.getZ(), s)) {
                                    continue;
                                }
                                Optional<Rotation> rot = RotationUtils.reachable(ctx, p);
                                if (rot.isPresent()) {
                                    double d = ctx.playerFeet().distSqr(p);
                                    if (d < bestDist) {
                                        bestDist = d;
                                        targetBreak = p;
                                        targetRot = rot.get();
                                        targetState = s;
                                    }
                                }
                            }
                        }
                    }
                }

                if (targetBreak != null) {
                    activeMiningBlock = targetBreak;
                    activeMiningBlockIsObstructing = true;
                    activeMiningTicks++;
                    clearMovementKeysKeepAttack();
                    baritone.getLookBehavior().updateTarget(targetRot, true);
                    if (!LookBehavior.isF5(ctx)) {
                        ctx.player().setYRot(targetRot.getYaw());
                        ctx.player().setXRot(targetRot.getPitch());
                    }
                    MovementHelper.switchToBestToolFor(ctx, targetState);
                    if (isAimedAtBlock(targetBreak, targetRot)) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    } else {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                activeMiningBlock = null;
                activeMiningBlockIsObstructing = false;
                activeMiningTicks = 0;
                logDirect("§a[AutoEnderChest] Không gian 3x3 đã dọn sạch sẽ! Tiến hành lấy Rương Ender ra tay...");
                shulkerState = ShulkerStorageState.ENDER_CHEST_SWAP_TO_HOTBAR;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_SWAP_TO_HOTBAR -> {
                int ecSlot = findEnderChestSlot();
                if (ecSlot == -1) {
                    logDirect("§c[AutoEnderChest] Không tìm thấy Rương Ender trong balo! Hủy quy trình.");
                    shulkerState = ShulkerStorageState.IDLE;
                    enderChestCooldownTicks = 300;
                    return null;
                }
                enderChestOriginalSlot = ecSlot;
                if (ecSlot < 9 && ecSlot > 0) {
                    enderChestHotbarSlot = ecSlot;
                    shulkerState = ShulkerStorageState.ENDER_CHEST_SELECT_SLOT;
                    shulkerStateTicks = 0;
                } else {
                    enderChestHotbarSlot = findBestHotbarSlotForShulker();
                    int containerSlot = ecSlot < 9 ? (ecSlot + 36) : ecSlot;
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, containerSlot, enderChestHotbarSlot, ClickType.SWAP, ctx.player());
                    shulkerState = ShulkerStorageState.ENDER_CHEST_SELECT_SLOT;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_SELECT_SLOT -> {
                ctx.player().getInventory().setSelectedSlot(enderChestHotbarSlot);
                ctx.playerController().syncHeldItem();

                Optional<ShulkerPlacementTarget> targetOpt = findShulkerPlacePos();
                if (targetOpt.isEmpty()) {
                    float sweepYaw = (ctx.playerRotations().getYaw() + 30.0F) % 360.0F;
                    Rotation sweepRot = new Rotation(sweepYaw, 25.0F);
                    baritone.getLookBehavior().updateTarget(sweepRot, true);
                    if (!LookBehavior.isF5(ctx)) {
                        ctx.player().setYRot(sweepYaw);
                        ctx.player().setXRot(25.0F);
                    }
                    if (shulkerStateTicks > 24) {
                        logDirect("§c[AutoEnderChest] Không tìm thấy vị trí thích hợp để đặt Rương Ender! Tạm hoãn 15s...");
                        shulkerState = ShulkerStorageState.IDLE;
                        enderChestCooldownTicks = 300;
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                ShulkerPlacementTarget pt = targetOpt.get();
                Vec3 hitVec = pt.face == net.minecraft.core.Direction.UP
                        ? new Vec3(pt.againstPos.getX() + 0.5, pt.againstPos.getY() + 0.95, pt.againstPos.getZ() + 0.5)
                        : new Vec3(pt.againstPos.getX() + 0.5 + pt.face.getStepX() * 0.5, pt.againstPos.getY() + 0.5, pt.againstPos.getZ() + 0.5 + pt.face.getStepZ() * 0.5);
                Rotation aimRot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), hitVec, ctx.playerRotations());
                baritone.getLookBehavior().updateTarget(aimRot, true);
                if (!LookBehavior.isF5(ctx)) {
                    ctx.player().setYRot(aimRot.getYaw());
                    ctx.player().setXRot(aimRot.getPitch());
                }

                enderChestPlacedPos = new BlockPos(pt.placePos.getX(), pt.placePos.getY(), pt.placePos.getZ());
                shulkerState = ShulkerStorageState.ENDER_CHEST_PLACE;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_PLACE -> {
                Optional<ShulkerPlacementTarget> targetOpt = findShulkerPlacePos();
                if (targetOpt.isEmpty()) {
                    shulkerState = ShulkerStorageState.IDLE;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                ShulkerPlacementTarget pt = targetOpt.get();
                enderChestPlacedPos = new BlockPos(pt.placePos.getX(), pt.placePos.getY(), pt.placePos.getZ());
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

                if (!isEnderChest(ctx.player().getMainHandItem())) {
                    for (int h = 0; h < 9; h++) {
                        if (isEnderChest(ctx.player().getInventory().getNonEquipmentItems().get(h))) {
                            enderChestHotbarSlot = h;
                            break;
                        }
                    }
                }
                ctx.player().getInventory().setSelectedSlot(enderChestHotbarSlot);
                ctx.playerController().syncHeldItem();
                ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                ctx.player().swing(InteractionHand.MAIN_HAND);
                shulkerState = ShulkerStorageState.ENDER_CHEST_WAIT_FOR_BLOCK;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_WAIT_FOR_BLOCK -> {
                if (enderChestPlacedPos != null && ctx.world().getBlockState(enderChestPlacedPos).getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) {
                    shulkerState = ShulkerStorageState.ENDER_CHEST_OPEN;
                    shulkerStateTicks = 0;
                } else if (shulkerStateTicks == 10 && enderChestPlacedPos != null) {
                    Optional<ShulkerPlacementTarget> retryOpt = findShulkerPlacePos();
                    if (retryOpt.isPresent()) {
                        ShulkerPlacementTarget pt = retryOpt.get();
                        BlockPos againstPure = new BlockPos(pt.againstPos.getX(), pt.againstPos.getY(), pt.againstPos.getZ());
                        Vec3 hitVec = pt.face == net.minecraft.core.Direction.UP
                                ? new Vec3(againstPure.getX() + 0.5, againstPure.getY() + 1.0, againstPure.getZ() + 0.5)
                                : new Vec3(againstPure.getX() + 0.5 + pt.face.getStepX() * 0.5, againstPure.getY() + 0.5, againstPure.getZ() + 0.5 + pt.face.getStepZ() * 0.5);
                        BlockHitResult bhr = new BlockHitResult(hitVec, pt.face, againstPure, false);
                        ctx.player().getInventory().setSelectedSlot(enderChestHotbarSlot);
                        ctx.playerController().syncHeldItem();
                        ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                        ctx.player().swing(InteractionHand.MAIN_HAND);
                    }
                } else if (shulkerStateTicks > 25) {
                    logDirect("§c[AutoEnderChest] Không thể đặt Rương Ender! Tạm hoãn 10s...");
                    shulkerState = ShulkerStorageState.IDLE;
                    enderChestCooldownTicks = 200;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_OPEN -> {
                if (enderChestPlacedPos == null) {
                    shulkerState = ShulkerStorageState.IDLE;
                    return null;
                }
                BlockPos openPos = new BlockPos(enderChestPlacedPos.getX(), enderChestPlacedPos.getY(), enderChestPlacedPos.getZ());
                Vec3 center = new Vec3(openPos.getX() + 0.5, openPos.getY() + 0.5, openPos.getZ() + 0.5);
                BlockHitResult bhr = new BlockHitResult(center, net.minecraft.core.Direction.UP, openPos, false);
                Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), center, ctx.playerRotations());
                baritone.getLookBehavior().updateTarget(rot, true);
                ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                shulkerState = ShulkerStorageState.ENDER_CHEST_WAIT_FOR_CONTAINER;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_WAIT_FOR_CONTAINER -> {
                if (ctx.player().containerMenu instanceof net.minecraft.world.inventory.ChestMenu || (ctx.player().containerMenu != ctx.player().inventoryMenu && ctx.player().containerMenu.slots.size() >= 63)) {
                    shulkerState = ShulkerStorageState.ENDER_CHEST_TRANSFER_SHULKERS;
                    shulkerStateTicks = 0;
                    shulkerTransferCooldown = 10; // Đợi 10 tick (0.5s) để server gửi toàn bộ packet nội dung container
                    enderChestTransferredCount = 0;
                } else if (shulkerStateTicks > 35) {
                    logDirect("§c[AutoEnderChest] Không thể mở Rương Ender! Đang đào thu hồi lại...");
                    shulkerState = ShulkerStorageState.ENDER_CHEST_MINE;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_TRANSFER_SHULKERS -> {
                if (ctx.player().containerMenu == ctx.player().inventoryMenu) {
                    shulkerState = ShulkerStorageState.ENDER_CHEST_MINE;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                if (shulkerTransferCooldown > 0) {
                    shulkerTransferCooldown--;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Kiểm tra xem dữ liệu menu từ server đã đồng bộ về client chưa
                boolean hasAnyPlayerItem = false;
                for (int slotId = 27; slotId < 63; slotId++) {
                    if (!ctx.player().containerMenu.getSlot(slotId).getItem().isEmpty()) {
                        hasAnyPlayerItem = true;
                        break;
                    }
                }
                if (!hasAnyPlayerItem && shulkerStateTicks < 40) {
                    // Chưa nhận được packet nội dung từ server, tiếp tục chờ
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                int containerId = ctx.player().containerMenu.containerId;

                // Kiểm tra xem Rương Ender còn chỗ trống không (ô 0 đến 26)
                int firstEmptyEnderSlot = -1;
                for (int b = 0; b < 27; b++) {
                    ItemStack boxItem = ctx.player().containerMenu.getSlot(b).getItem();
                    if (boxItem.isEmpty()) {
                        firstEmptyEnderSlot = b;
                        break;
                    }
                }

                if (firstEmptyEnderSlot == -1) {
                    logDirect("§6[AutoEnderChest] Rương Ender đã đầy chỗ (27/27 ô)! Đã cất " + enderChestTransferredCount + " Shulker Box.");
                    shulkerState = ShulkerStorageState.ENDER_CHEST_CLOSE_CONTAINER;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Chuyển tối đa 3 Shulker Box vào Rương Ender theo yêu cầu
                if (enderChestTransferredCount >= 3) {
                    logDirect("§a[AutoEnderChest] Đã cất đủ 3 Shulker Box vào Rương Ender thành công!");
                    shulkerState = ShulkerStorageState.ENDER_CHEST_CLOSE_CONTAINER;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Tìm Shulker Box để chuyển vào Rương Ender (slot 27 đến 62)
                int transferSlot = -1;

                // 1. Ưu tiên 1: Shulker Box ĐẦY (27/27)
                for (int slotId = 27; slotId < 63; slotId++) {
                    ItemStack stack = ctx.player().containerMenu.getSlot(slotId).getItem();
                    if (!stack.isEmpty() && isShulkerBoxFull(stack)) {
                        transferSlot = slotId;
                        break;
                    }
                }

                // 2. Ưu tiên 2: Shulker Box có đồ bên trong (> 0 ô chứa)
                if (transferSlot == -1) {
                    for (int slotId = 27; slotId < 63; slotId++) {
                        ItemStack stack = ctx.player().containerMenu.getSlot(slotId).getItem();
                        if (!stack.isEmpty() && isShulkerBox(stack) && getShulkerOccupiedSlots(stack) > 0) {
                            transferSlot = slotId;
                            break;
                        }
                    }
                }

                // 3. Ưu tiên 3: Bất kỳ Shulker Box nào nếu người chơi có từ 2 Shulker Box trở lên trong người
                if (transferSlot == -1) {
                    int totalShulkersInMenu = 0;
                    for (int slotId = 27; slotId < 63; slotId++) {
                        ItemStack stack = ctx.player().containerMenu.getSlot(slotId).getItem();
                        if (!stack.isEmpty() && isShulkerBox(stack)) {
                            totalShulkersInMenu += stack.getCount();
                        }
                    }
                    if (totalShulkersInMenu >= 2) {
                        for (int slotId = 27; slotId < 63; slotId++) {
                            ItemStack stack = ctx.player().containerMenu.getSlot(slotId).getItem();
                            if (!stack.isEmpty() && isShulkerBox(stack)) {
                                transferSlot = slotId;
                                break;
                            }
                        }
                    }
                }

                if (transferSlot != -1) {
                    ItemStack before = ctx.player().containerMenu.getSlot(transferSlot).getItem().copy();
                    // Thử chuyển bằng QUICK_MOVE (Shift-Click)
                    ctx.playerController().windowClick(containerId, transferSlot, 0, ClickType.QUICK_MOVE, ctx.player());
                    ItemStack after = ctx.player().containerMenu.getSlot(transferSlot).getItem();
                    if (before.getCount() != after.getCount() || after.isEmpty()) {
                        enderChestTransferredCount++;
                        logDirect("§a[AutoEnderChest] Đã cất Shulker Box thứ " + enderChestTransferredCount + "/3 vào Rương Ender!");
                        shulkerTransferCooldown = 4; // Nhịp 4 tick mượt mà chống kick
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    } else if (firstEmptyEnderSlot != -1) {
                        // Dự phòng: QUICK_MOVE bị server từ chối -> Thao tác thủ công Click nhặt rồi đặt vào ô trống Rương Ender
                        ctx.playerController().windowClick(containerId, transferSlot, 0, ClickType.PICKUP, ctx.player());
                        ctx.playerController().windowClick(containerId, firstEmptyEnderSlot, 0, ClickType.PICKUP, ctx.player());
                        ItemStack enderSlotItem = ctx.player().containerMenu.getSlot(firstEmptyEnderSlot).getItem();
                        if (!enderSlotItem.isEmpty() && isShulkerBox(enderSlotItem)) {
                            enderChestTransferredCount++;
                            logDirect("§a[AutoEnderChest] Đã cất thủ công Shulker Box thứ " + enderChestTransferredCount + "/3 vào ô " + firstEmptyEnderSlot + " của Rương Ender!");
                            shulkerTransferCooldown = 4;
                            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        } else {
                            logDirect("§6[AutoEnderChest] Không thể chuyển thêm Shulker Box vào Rương Ender!");
                            shulkerState = ShulkerStorageState.ENDER_CHEST_CLOSE_CONTAINER;
                            shulkerStateTicks = 0;
                            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                    }
                }

                // Đảm bảo đã chờ ít nhất 15 tick để dữ liệu đồng bộ chắc chắn trước khi kết luận không còn Shulker Box
                if (shulkerStateTicks < 15) {
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Không còn Shulker Box nào cần chuyển nữa
                logDirect("§a[AutoEnderChest] Đã hoàn tất cất toàn bộ Shulker Box vào Rương Ender (Tổng: " + enderChestTransferredCount + ")!");
                shulkerState = ShulkerStorageState.ENDER_CHEST_CLOSE_CONTAINER;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_CLOSE_CONTAINER -> {
                ctx.player().closeContainer();
                shulkerState = ShulkerStorageState.ENDER_CHEST_WAIT_FOR_CLOSE;
                shulkerStateTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_WAIT_FOR_CLOSE -> {
                if (ctx.player().containerMenu == ctx.player().inventoryMenu || shulkerStateTicks > 6) {
                    shulkerState = ShulkerStorageState.ENDER_CHEST_MINE;
                    shulkerStateTicks = 0;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_MINE -> {
                if (enderChestPlacedPos == null) {
                    shulkerState = ShulkerStorageState.IDLE;
                    return null;
                }
                BlockState state = ctx.world().getBlockState(enderChestPlacedPos);
                if (state.isAir()) {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    enderChestCountBefore = countEnderChestsInInventory();
                    shulkerState = ShulkerStorageState.ENDER_CHEST_WAIT_FOR_PICKUP;
                    shulkerStateTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }

                // Ưu tiên chọn Cúp Silk Touch nếu có trong người để đập ra nguyên vẹn Rương Ender!
                int silkSlot = findSilkTouchPickaxeSlot();
                if (silkSlot >= 9 && silkSlot < 36) {
                    ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, silkSlot, 0, ClickType.SWAP, ctx.player());
                    ctx.player().getInventory().setSelectedSlot(0);
                    ctx.playerController().syncHeldItem();
                } else if (silkSlot >= 0 && silkSlot < 9) {
                    ctx.player().getInventory().setSelectedSlot(silkSlot);
                    ctx.playerController().syncHeldItem();
                } else {
                    MovementHelper.switchToBestToolFor(ctx, state);
                }

                Optional<Rotation> rot = RotationUtils.reachable(ctx, enderChestPlacedPos);
                if (rot.isPresent()) {
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    if (isAimedAtBlock(enderChestPlacedPos, rot.get())) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    }
                }
                if (shulkerStateTicks > 140) {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    logDirect("§c[AutoEnderChest] Quá thời gian đào Rương Ender! Tiếp tục hành trình...");
                    shulkerState = ShulkerStorageState.IDLE;
                    enderChestCooldownTicks = 300;
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case ENDER_CHEST_WAIT_FOR_PICKUP -> {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);

                int currentCount = countEnderChestsInInventory();
                if (currentCount > enderChestCountBefore || shulkerStateTicks > 30) {
                    baritone.getInputOverrideHandler().clearAllKeys();
                    baritone.getPathingBehavior().cancelSegmentIfSafe();
                    logDirect("§a[AutoEnderChest] Đã thu hồi Rương Ender vào balo an toàn! Tiếp tục tự động đào mỏ.");
                    enderChestPlacedPos = null;
                    shulkerStateTicks = 0;
                    enderChestCooldownTicks = 200; // Cooldown 10s trước khi kiểm tra lại
                    shulkerState = ShulkerStorageState.IDLE;
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
        boolean isMining = (activeMiningBlock != null && activeMiningTicks <= 160) || isHitting || isClickingLeft;
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

        // === PHÁT HIỆN KẸT HÀNH ĐỘNG / ĐỨNG YÊN QUÁ LÂU (TĂNG TÍNH TƯ DUY & PHẠT ĐỨNG YÊN) ===
        if (stuckTicks == 50 && !isMining) {
            logDirect("§6[SmartMind] Phạt đứng yên quá 2.5s (-20 điểm)! Đang rà soát giải phóng đường đi...");
        }

        int maxStuckTicks = Baritone.settings().mineStrictOneDirection.value ? 100 : 160; // 5s trong strict 1-dir, 8s thường
        if (stuckTicks >= maxStuckTicks || placeBreakOscillationCount >= 2 || pingPongDetected) {
            if (pingPongDetected) {
                logDirect("§c[AntiStuck] Phát hiện dao động qua lại (ping-pong) trong phạm vi <= 2.5 block! Giải kẹt ngay...");
            } else if (placeBreakOscillationCount >= 2) {
                logDirect("§c[AntiStuck] Phát hiện vòng lặp đặt block rồi đào xuống! Đổi hướng ngay...");
            } else {
                logDirect("§c[AntiStuck] Bị kẹt đứng yên quá " + (maxStuckTicks / 20) + "s! Kích hoạt giải kẹt ngay...");
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
                        if (lastStuckOrePos == null || lastStuckOrePos.distSqr(pos) > 9) {
                            lastStuckOrePos = pos;
                            stuckRetries = 1;
                        }
                        if (stuckRetries >= 3) {
                            // Đã thử nhiều lần (>= 3 lần): Thêm toàn bộ vỉa quặng vào BLACKLIST để không bị kẹt mãi
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
                            logDirect("§c[AntiStuck] Quặng tại " + pos.toShortString() + " (" + veinOres.size() + " block) không thể tiếp cận/kẹt sau " + stuckRetries + " lần thử! Đã BLACKLIST để tiếp tục tiến lên!");
                            lockedTargetOre = null;
                            forceReroute = true;
                            stuckRetries = 0;
                            lastStuckOrePos = null;
                            baritone.getPathingBehavior().cancelSegmentIfSafe();
                            return;
                        } else {
                            lockedTargetOre = null;
                            forceReroute = true;
                            baritone.getPathingBehavior().cancelSegmentIfSafe();
                            logDirect("§e[AntiStuck] Thử đổi hướng tiếp cận quặng tại " + pos.toShortString() + " (lần " + stuckRetries + ")...");
                            return;
                        }
                    }
                }
            }

            // 2. Khi đang đào dốc xuống mà gặp vật cản (CHỈ khi không có quặng nào đang đào): Đổi hướng đào dốc theo chiều kim đồng hồ
            boolean noOres = (knownOreLocations == null || knownOreLocations.isEmpty()) && oreMemory.isEmpty();
            if (noOres && (!hasReachedTargetY || currentFeet.y > targetY + 3)) {
                if (Baritone.settings().straightDownMine.value) {
                    stuckRetries = 0;
                    net.minecraft.core.Direction shiftDir = ctx.player().getDirection().getAxis().isHorizontal()
                            ? ctx.player().getDirection() : net.minecraft.core.Direction.NORTH;
                    shaftOriginPos = currentFeet.relative(shiftDir, 1);
                    logDirect("§6[AntiStuck] Kẹt đào thẳng đứng (Shaft Down)! Dịch chuyển trục đào sang " + shiftDir.getName().toUpperCase() + " 1 block...");
                    forceReroute = true;
                    return;
                }
                if (tunnelDirection == null || !tunnelDirection.getAxis().isHorizontal()) {
                    net.minecraft.core.Direction dir = ctx.player().getDirection();
                    tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
                }
                if (Baritone.settings().mineStrictOneDirection.value) {
                    // CHẾ ĐỘ 1 HƯỚNG: TUYỆT ĐỐI KHÔNG XOAY CHIỀU KIM ĐỒNG HỒ!
                    stairOriginPos = null;
                    shaftOriginPos = null;
                    tunnelOriginPos = null;
                    branchPoint = null;
                    branchPointRunaway = null;
                    currentTunnelTarget = null;
                    if (stuckRetries >= 3) {
                        stuckRetries = 0;
                        logDirect("§c[AntiStuck (1-Dir)] Kẹt đào dốc! Tạm chuyển sang đào thẳng đứng (Shaft Down) để vượt qua vật cản...");
                        Baritone.settings().straightDownMine.value = true;
                    } else {
                        logDirect("§6[AntiStuck (1-Dir)] Đang cố đào thông dốc phía trước theo hướng " + tunnelDirection.getName().toUpperCase() + "...");
                    }
                    forceReroute = true;
                    return;
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
            if (Baritone.settings().mineStrictOneDirection.value) {
                // CHẾ ĐỘ 1 HƯỚNG: TUYỆT ĐỐI KHÔNG XOAY CHIỀU KIM ĐỒNG HỒ!
                tunnelOriginPos = null;
                stairOriginPos = null;
                shaftOriginPos = null;
                branchPoint = null;
                branchPointRunaway = null;
                currentTunnelTarget = null;
                if (stuckRetries >= 2) {
                    int safeY = Math.min(-54, currentFeet.y + 1);
                    logDirect("§6[AntiStuck (1-Dir)] Gặp Bedrock chắn đường! Giữ nguyên hướng " + tunnelDirection.getName().toUpperCase() + ", nâng độ cao lên Y=" + safeY + " để tiếp tục đào thẳng...");
                    bedrockEscapeActive = true;
                    bedrockEscapeOrigin = currentFeet;
                    bedrockEscapeTargetY = safeY;
                    bedrockEscapeTicks = 0;
                    stuckRetries = 0;
                } else {
                    logDirect("§6[AntiStuck (1-Dir)] Đang đào thông vật cản/Bedrock theo hướng " + tunnelDirection.getName().toUpperCase() + " (thử " + stuckRetries + "/2)...");
                }
                forceReroute = true;
                return;
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

        if (foodCooldownTicks > 0) {
            foodCooldownTicks--;
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

        // YÊU CẦU: "nếu còn từ balo thì chuyển ra hot bar" (kể cả khi chưa đói)
        ensureFoodInHotbar();

        // YÊU CẦU: "tự động mua thịt khi hết kể cả trong hot bar lẫn balo ... /shop"
        int foodInInv = countFoodInInventory();
        if (foodInInv == 0 && Baritone.settings().autoBuyFood.value && foodCooldownTicks <= 0 && shulkerState == ShulkerStorageState.IDLE) {
            int shulkerFoodSlot = findShulkerBoxWithFoodSlot();
            if (shulkerFoodSlot != -1) {
                triggerShulkerRetrieveFood(shulkerFoodSlot);
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            foodPurchasedCountBefore = countFoodInInventory();
            logDirect("§e[AutoShop] Hết đồ ăn trong cả hotbar lẫn balo! Tự động mở /shop để mua 64 Thịt Bò Nướng...");
            shopRetryCount = 0;
            shopActionCooldown = 0;
            shulkerState = ShulkerStorageState.SHOP_FOOD_PREPARE_SLOT;
            shulkerStateTicks = 0;
            baritone.getPathingBehavior().cancelSegmentIfSafe();
            baritone.getInputOverrideHandler().clearAllKeys();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
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

    private boolean ensureFoodInHotbar() {
        if (ctx.player() == null) return false;
        if (ctx.player().containerMenu != ctx.player().inventoryMenu) return false;

        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        // Kiểm tra xem hotbar (0-8) đã có thức ăn hay chưa
        for (int i = 0; i < 9; i++) {
            if (isGoodFood(inv.get(i))) {
                return true;
            }
        }

        // Hotbar hết thức ăn: Quét balo (9-35) để chuyển ra hotbar
        int foodBaloSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (isGoodFood(inv.get(i))) {
                foodBaloSlot = i;
                break;
            }
        }

        if (foodBaloSlot != -1) {
            int targetHotbarSlot = findBestHotbarSlotForFood();
            ItemStack foodStack = inv.get(foodBaloSlot);
            String foodName = foodStack.getHoverName().getString();
            ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, foodBaloSlot, targetHotbarSlot, ClickType.SWAP, ctx.player());
            logDirect("§a[AutoEat] Hotbar hết đồ ăn nhưng balo còn! Đã chuyển " + foodName + " từ balo ra hotbar ô " + (targetHotbarSlot + 1) + ".");
            return true;
        }

        return false;
    }

    private int countFoodInInventory() {
        if (ctx.player() == null) return 0;
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.get(i);
            if (isGoodFood(s)) {
                count += s.getCount();
            }
        }
        return count;
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

        // 3. Nếu hết Totem trong cả tay phụ lẫn balo -> Tự động kiểm tra shulker hoặc mở /shop
        if (Baritone.settings().autoBuyTotem.value && totemCooldownTicks <= 0 && shulkerState == ShulkerStorageState.IDLE) {
            int shulkerTotemSlot = findShulkerBoxWithTotemSlot();
            if (shulkerTotemSlot != -1) {
                triggerShulkerRetrieveTotem(shulkerTotemSlot);
                return;
            }
            totemPurchasedCountBefore = 0;
            logDirect("§e[AutoShop] Hết Totem Bất Tử trong cả tay phụ lẫn balo! Tự động mở /shop để mua Vật tổ trường sinh...");
            shopRetryCount = 0;
            shopActionCooldown = 0;
            shulkerState = ShulkerStorageState.SHOP_TOTEM_PREPARE_SLOT;
            shulkerStateTicks = 0;
            baritone.getPathingBehavior().cancelSegmentIfSafe();
            baritone.getInputOverrideHandler().clearAllKeys();
        }
    }

    private int getTotemCount() {
        if (ctx.player() == null) return 0;
        int count = 0;
        ItemStack offhand = ctx.player().getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offhand.isEmpty() && offhand.is(Items.TOTEM_OF_UNDYING)) {
            count += offhand.getCount();
        }
        ItemStack mainhand = ctx.player().getMainHandItem();
        if (!mainhand.isEmpty() && mainhand.is(Items.TOTEM_OF_UNDYING)) {
            count += mainhand.getCount();
        }
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (ItemStack stack : inv) {
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int autoLogoutLavaTicks = 0;

    private boolean handleAutoLogout() {
        boolean checkDanger = Baritone.settings().autoLogoutOnDanger.value;
        boolean checkPlayer = Baritone.settings().autoLogoutOnPlayer.value;
        if (!checkDanger && !checkPlayer) {
            autoLogoutLavaTicks = 0;
            return false;
        }
        if (ctx.player() == null || ctx.world() == null) {
            autoLogoutLavaTicks = 0;
            return false;
        }
        if (ctx.player().isCreative() || ctx.player().isSpectator()) {
            autoLogoutLavaTicks = 0;
            return false;
        }

        // 1. Kiểm tra trạng thái rơi vào / đứng trong hồ Lava (chỉ khi checkDanger bật):
        boolean inLava = false;
        if (checkDanger) {
            inLava = ctx.player().isInLava()
                    || ctx.world().getBlockState(ctx.playerFeet()).is(Blocks.LAVA)
                    || (ctx.player().getDeltaMovement().y < 0 && ctx.world().getBlockState(ctx.playerFeet().below()).is(Blocks.LAVA));

            if (inLava) {
                autoLogoutLavaTicks++;
            } else {
                autoLogoutLavaTicks = 0;
            }
        } else {
            autoLogoutLavaTicks = 0;
        }

        String dangerReason = null;

        // TRƯỜNG HỢP 1: PHÁT HIỆN NGƯỜI CHƠI ĐẾN GẦN (KỂ CẢ DÙNG THUỐC TÀNG HÌNH / INVIS)
        // Ưu tiên cao nhất: nếu phát hiện player khác xâm nhập vùng an toàn -> Logout ngay lập tức!
        if (checkPlayer) {
            AutoLogoutTracker.DetectedPlayerInfo playerThreat = AutoLogoutTracker.scanForNearbyPlayer(ctx);
            if (playerThreat != null) {
                dangerReason = "Phát hiện người chơi: " + playerThreat.getFormattedDescription();
            }
        }

        // TRƯỜNG HỢP 2: LAVA (chỉ khi checkDanger bật)
        if (dangerReason == null && checkDanger && inLava && autoLogoutLavaTicks >= 60) {
            dangerReason = "Rơi vào hồ LAVA liên tục quá 3 giây!";
        }

        // TRƯỜNG HỢP 3: QUÁI ĐÁNH, ĐÓI, TÉ NGÃ, v.v. (Phải Hết Totem + Nửa thanh máu, chỉ khi checkDanger bật)
        if (dangerReason == null && checkDanger) {
            int totemCount = getTotemCount();
            if (totemCount == 0) {
                float health = ctx.player().getHealth();
                float maxHealth = ctx.player().getMaxHealth();
                float threshold = Baritone.settings().autoLogoutHealthThreshold.value;
                boolean lowHealth = (health <= maxHealth * threshold) || (health <= 10.0f);
                if (lowHealth) {
                    String cause = EmergencySafetyBehavior.detectDamageCause(ctx);
                    dangerReason = cause + " (Máu còn: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP) và ĐÃ HẾT TOTEM!";
                }
            }
        }

        if (dangerReason != null) {
            autoLogoutLavaTicks = 0;
            AutoLogoutTracker.performAutoLogout(ctx, dangerReason);
            return true;
        }

        return false;
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
        int r = 6;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -4; dy <= 5; dy++) {
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

                .sorted(Comparator.comparingDouble(ctx.getBaritone().getPlayerContext().player().blockPosition()::distSqr))
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
        if (ctx.player() != null) {
            net.minecraft.core.Direction dir = ctx.player().getDirection();
            this.tunnelDirection = dir.getAxis().isHorizontal() ? dir : net.minecraft.core.Direction.NORTH;
        } else {
            this.tunnelDirection = null;
        }
        this.stairOriginPos = null;
        this.shaftOriginPos = null;
        this.shaftConsecutiveFailures = 0;
        this.pillarFailCount = 0;
        this.hasReachedTargetY = ctx.player() != null && ctx.playerFeet().y <= Baritone.settings().legitMineYLevel.value;
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
        this.shopRetryCount = 0;
        this.shopActionCooldown = 0;
        this.shopPurchasedCountBefore = 0;
        this.foodPurchasedCountBefore = 0;
        this.foodCooldownTicks = 0;
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

    private boolean isWoodFilter(BlockOptionalMetaLookup f) {
        if (f == null || f.blocks().isEmpty()) return false;
        return f.blocks().stream().allMatch(b -> {
            String name = b.getBlock().getDescriptionId().toLowerCase();
            return name.contains("log") || name.contains("wood") || name.contains("stem") || name.contains("hyphae");
        });
    }

    private boolean isAimedAtBlock(BlockPos pos, Rotation targetRot) {
        if (pos == null) {
            return false;
        }
        if (ctx.isLookingAt(pos)) {
            return true;
        }
        if (Baritone.settings().clientFreeLook.value || Baritone.settings().f5FreeLook.value) {
            return true;
        }
        if (targetRot != null) {
            float curYaw = Rotation.normalizeYaw(ctx.playerRotations().getYaw());
            float tarYaw = Rotation.normalizeYaw(targetRot.getYaw());
            float yawDiff = Math.abs(curYaw - tarYaw);
            if (yawDiff > 180.0F) {
                yawDiff = 360.0F - yawDiff;
            }
            float pitchDiff = Math.abs(ctx.playerRotations().getPitch() - targetRot.getPitch());
            if (yawDiff < 10.0F && pitchDiff < 10.0F) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearLava(BlockPos center, int radius) {
        if (ctx.world() == null || center == null) return false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (ctx.world().getFluidState(p).is(FluidTags.LAVA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Optional<BlockPos> getObstructingBlock(BlockPos targetPos) {
        if (ctx.player() == null || ctx.world() == null || targetPos == null) {
            return Optional.empty();
        }
        Vec3 eyePos = ctx.playerHead();
        double dx = eyePos.x - (targetPos.getX() + 0.5);
        double dy = eyePos.y - (targetPos.getY() + 0.5);
        double dz = eyePos.z - (targetPos.getZ() + 0.5);
        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);
        net.minecraft.core.Direction facing;
        if (absX >= absY && absX >= absZ) {
            facing = dx > 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
        } else if (absY >= absX && absY >= absZ) {
            facing = dy > 0 ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.DOWN;
        } else {
            facing = dz > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
        }
        BlockPos adjacent = targetPos.relative(facing);
        if (!adjacent.equals(targetPos)) {
            BlockState adjState = ctx.world().getBlockState(adjacent);
            if (!adjState.isAir() && adjState.isSolid() && (filter == null || !filter.has(adjState))) {
                return Optional.of(adjacent);
            }
        }
        Vec3 targetCenter = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        ClipContext clipCtx = new ClipContext(eyePos, targetCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.player());
        HitResult hit = ctx.world().clip(clipCtx);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = ((BlockHitResult) hit).getBlockPos();
            if (!hitPos.equals(targetPos)) {
                BlockState s = ctx.world().getBlockState(hitPos);
                if (!s.isAir() && s.isSolid() && (filter == null || !filter.has(s))) {
                    return Optional.of(hitPos);
                }
            }
        }
        return Optional.empty();
    }

    private void clearMovementKeysKeepAttack() {
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_BACK, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_LEFT, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_RIGHT, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
    }

    public static class GoalShaftDown implements Goal {
        public final int x, z;
        public final int startY;
        public final int targetY;

        public GoalShaftDown(int x, int startY, int z, int targetY) {
            this.x = x;
            this.startY = startY;
            this.z = z;
            this.targetY = targetY;
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            int horizDev = Math.abs(x - this.x) + Math.abs(z - this.z);
            return (y <= targetY && horizDev <= 1) || ((startY - y) >= 6 && horizDev <= 1);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            int horizDev = Math.abs(x - this.x) + Math.abs(z - this.z);
            int remainingDrop = Math.max(0, y - targetY);
            return remainingDrop * 100.0 + horizDev * 2000.0;
        }

        @Override
        public double heuristic() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GoalShaftDown)) return false;
            GoalShaftDown that = (GoalShaftDown) o;
            return x == that.x && z == that.z && startY == that.startY && targetY == that.targetY;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, startY, z, targetY);
        }

        @Override
        public String toString() {
            return "GoalShaftDown{x=" + x + ", startY=" + startY + ", z=" + z + ", targetY=" + targetY + "}";
        }
    }
}
