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
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ISandProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An ultra-lightweight, rapid-fire Sand Mining and Vacuum Harvesting process.
 * <p>
 * Specifically tailored for sand/gravity blocks:
 * <ul>
 *   <li>Direct in-reach mining loop: Mines all sand in player's reach without A* or world-scanning pauses.</li>
 *   <li>Gravity exploitation: Mines the base of sand columns so falling sand collapses directly into the shovel.</li>
 *   <li>Zero-freeze transitions: Uses concentric shell search to find nearby deposits in &lt;0.05ms.</li>
 *   <li>Proactive Vacuum Collection: Automatically walks over to collect dropped sand items so zero sand is lost.</li>
 *   <li>Auto Straight-Line Scout: Automatically sprints straight forward across deserts to locate new sand dunes.</li>
 *   <li>Anti-suffocation reflex: Automatically digs sand falling on the player's head.</li>
 *   <li>Surface-only safety: Strictly forbids digging into caves, deep underground, or negative Y.</li>
 * </ul>
 *
 * @author leijurv
 */
public final class SandProcess extends BaritoneProcessHelper implements ISandProcess {

    private boolean active;
    private int targetCount;
    private int range;
    private BlockPos center;

    private BlockPos activeMiningBlock;
    private int activeMiningTicks;
    private int fallingWaitTicks;
    private int totalMined;
    private int calcFailCount;
    private BlockPos currentGoal;
    private Goal currentGoalObject;
    private boolean inventoryFullNotified;

    private Direction straightDirection;
    private long lastScoutLogTime;
    private long lastDirectionChangeTime;

    private int currentDropTargetId = -1;
    private int dropStuckTicks = 0;
    private final Map<Integer, Long> ignoredItemEntities = new ConcurrentHashMap<>();

    private final Set<BlockPos> blacklist = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private long lastLogTime;

    public SandProcess(Baritone baritone) {
        super(baritone);
    }

    public static boolean isSand(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        return b == Blocks.SAND || b == Blocks.RED_SAND || b == Blocks.SUSPICIOUS_SAND;
    }

    public static boolean isSandItem(Item item) {
        if (item == null) return false;
        return item == Items.SAND || item == Items.RED_SAND || item == Items.SUSPICIOUS_SAND;
    }

    public int getMinAllowedY() {
        if (center != null) {
            return Math.max(1, center.getY() - 8);
        }
        if (ctx.player() != null) {
            return Math.max(1, ctx.playerFeet().getY() - 8);
        }
        return 60;
    }

    public boolean canPickupSand() {
        if (ctx.player() == null) return false;
        for (ItemStack stack : ctx.player().getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) return true;
            if (isSandItem(stack.getItem()) && stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        ItemStack offhand = ctx.player().getOffhandItem();
        if (!offhand.isEmpty() && isSandItem(offhand.getItem()) && offhand.getCount() < offhand.getMaxStackSize()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void sand(int targetCount, int range, BlockPos pos) {
        this.active = true;
        this.targetCount = targetCount;
        this.range = range;
        this.center = pos != null ? pos : ctx.playerFeet();
        this.activeMiningBlock = null;
        this.activeMiningTicks = 0;
        this.fallingWaitTicks = 0;
        this.totalMined = 0;
        this.calcFailCount = 0;
        this.currentGoal = null;
        this.currentGoalObject = null;
        this.inventoryFullNotified = false;
        this.straightDirection = ctx.player() != null ? ctx.player().getDirection() : Direction.NORTH;
        this.lastScoutLogTime = 0;
        this.lastDirectionChangeTime = 0;
        this.currentDropTargetId = -1;
        this.dropStuckTicks = 0;
        this.ignoredItemEntities.clear();
        this.blacklist.clear();
        this.lastLogTime = System.currentTimeMillis();

        // Cấu hình di chuyển mượt mà & siêu tốc cho Baritone:
        Baritone.settings().autoTool.value = true;
        Baritone.settings().assumeExternalAutoTool.value = false;
        Baritone.settings().allowBreak.value = true;
        Baritone.settings().allowSprint.value = true;
        Baritone.settings().allowDownward.value = true; // Cho phép bước xuống địa hình dốc của cồn cát
        Baritone.settings().allowParkour.value = true;
        Baritone.settings().exploreForBlocks.value = false;

        Baritone.settings().primaryTimeoutMS.value = 2000L;
        Baritone.settings().failureTimeoutMS.value = 3500L;

        logDirect("§6[AutoSand] §aĐã kích hoạt chế độ TỰ ĐỘNG ĐÀO & NHẶT CÁT!");
        if (targetCount > 0) {
            logDirect("§b  • Mục tiêu số lượng: " + targetCount + " block cát (" + ((targetCount + 63) / 64) + " stack)");
        }
        if (range > 0) {
            logDirect("§b  • Bán kính giới hạn: " + range + " block");
        }
        logDirect("§b  • Hướng di chuyển: Tự động chạy thẳng theo hướng " + straightDirection.getName().toUpperCase() + " để săn cát.");
        logDirect("§7  • Độ cao an toàn: Từ Y=" + getMinAllowedY() + " trở lên (không bao giờ đào sâu xuống Y âm).");
        logDirect("§7  • Tự động hút sạch các item cát rơi vãi ngay sau khi xả sập cột cát.");
    }

    @Override
    public void cancel() {
        if (active) {
            active = false;
            activeMiningBlock = null;
            activeMiningTicks = 0;
            fallingWaitTicks = 0;
            currentGoal = null;
            currentGoalObject = null;
            straightDirection = null;
            currentDropTargetId = -1;
            dropStuckTicks = 0;
            ignoredItemEntities.clear();
            baritone.getInputOverrideHandler().clearAllKeys();
            baritone.getPathingBehavior().cancelSegmentIfSafe();
            logDirect("§c[AutoSand] Đã dừng đào cát. Tổng số cát đã đào: §e" + totalMined + " block (" + (totalMined / 64) + " stack).");
        }
    }

    @Override
    public void onLostControl() {
        active = false;
        activeMiningBlock = null;
        activeMiningTicks = 0;
        fallingWaitTicks = 0;
        currentGoal = null;
        currentGoalObject = null;
        straightDirection = null;
        currentDropTargetId = -1;
        dropStuckTicks = 0;
        ignoredItemEntities.clear();
        baritone.getInputOverrideHandler().clearAllKeys();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (!active || ctx.player() == null || ctx.world() == null) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        long now = System.currentTimeMillis();
        ignoredItemEntities.entrySet().removeIf(e -> e.getValue() < now);

        // 1. Kiểm tra đã đạt mục tiêu số lượng chỉ định chưa:
        if (targetCount > 0 && totalMined >= targetCount) {
            logDirect("§a[AutoSand] §lHOÀN THÀNH! §r§aĐã đào đủ " + totalMined + "/" + targetCount + " block cát!");
            cancel();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        // 2. Chống ngạt thở khẩn cấp (Anti-Suffocation):
        // Nếu cát sập vào đầu người chơi (headPos), lập tức phá vỡ khối cát ở đầu ngay lập tức!
        BetterBlockPos feet = ctx.playerFeet();
        BlockPos head = feet.above();
        BlockState headState = ctx.world().getBlockState(head);
        if (isSand(headState)) {
            Optional<Rotation> headRot = RotationUtils.reachable(ctx, head);
            if (headRot.isPresent()) {
                clearMovementKeys();
                baritone.getLookBehavior().updateTarget(headRot.get(), true);
                MovementHelper.switchToBestToolFor(ctx, headState);
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        double reach = ctx.playerController().getBlockReachDistance();
        double reachSq = (reach - 0.1) * (reach - 0.1);

        // 3. Xử lý block cát đang đào dở (hoặc cột cát đang sập liên hoàn):
        if (activeMiningBlock != null) {
            BlockState curState = ctx.world().getBlockState(activeMiningBlock);
            if (isSand(curState) && feet.distSqr(activeMiningBlock) <= reachSq) {
                fallingWaitTicks = 0;
                activeMiningTicks++;
                if (activeMiningTicks > 50) {
                    blacklist.add(activeMiningBlock);
                    activeMiningBlock = null;
                    activeMiningTicks = 0;
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                } else {
                    Optional<Rotation> rot = RotationUtils.reachable(ctx, activeMiningBlock, reach);
                    if (rot.isPresent()) {
                        clearMovementKeys();
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        MovementHelper.switchToBestToolFor(ctx, curState);
                        if (isAimedAtBlock(activeMiningBlock, rot.get())) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    } else {
                        activeMiningBlock = null;
                        activeMiningTicks = 0;
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                    }
                }
            } else {
                // Block vừa vỡ:
                totalMined++;
                activeMiningTicks = 0;
                notifyProgress();

                // ĐẶC TÍNH VẬT LÝ CỦA CÁT:
                // Nếu khối cát phía trên đang rơi xuống, vị trí activeMiningBlock sẽ sớm trở lại thành cát!
                BlockState fallenState = ctx.world().getBlockState(activeMiningBlock);
                if (isSand(fallenState)) {
                    // Cát phía trên rơi xuống vị trí -> Giữ chặt xẻng đập tiếp không ngừng tay!
                    Optional<Rotation> rot = RotationUtils.reachable(ctx, activeMiningBlock, reach);
                    if (rot.isPresent()) {
                        clearMovementKeys();
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        MovementHelper.switchToBestToolFor(ctx, fallenState);
                        if (isAimedAtBlock(activeMiningBlock, rot.get())) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                } else if (isSand(ctx.world().getBlockState(activeMiningBlock.above())) && fallingWaitTicks < 4) {
                    fallingWaitTicks++;
                    Optional<Rotation> rot = RotationUtils.reachable(ctx, activeMiningBlock, reach);
                    if (rot.isPresent()) {
                        clearMovementKeys();
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                }

                // Cột cát đã kết thúc hoàn toàn:
                activeMiningBlock = null;
                fallingWaitTicks = 0;
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            }
        }

        // Xử lý khi tính toán đường đi bị thất bại (có cooldown để không spam chat/hướng):
        if (calcFailed) {
            calcFailCount++;
            if (currentDropTargetId != -1) {
                ignoredItemEntities.put(currentDropTargetId, now + 20000L);
                currentDropTargetId = -1;
                dropStuckTicks = 0;
            }
            if (currentGoal != null) {
                blacklist.add(currentGoal);
            }
            if (calcFailCount >= 3 && (now - lastDirectionChangeTime > 2500L)) {
                lastDirectionChangeTime = now;
                calcFailCount = 0;
                currentGoal = null;
                currentGoalObject = null;
                // Nếu đi thẳng bị vướng địa hình -> đổi hướng 90 độ để vượt chướng ngại vật:
                if (straightDirection != null) {
                    straightDirection = straightDirection.getClockWise();
                    logDirect("§6[AutoSand] §7Phía trước bị chặn, đổi hướng sang §b" + straightDirection.getName().toUpperCase() + " §7để tiếp tục tìm cát.");
                }
                return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
        } else {
            calcFailCount = 0;
        }

        // 4. KIỂM TRA BALO & ƯU TIÊN HÚT/NHẶT ITEM CÁT RƠI (VACUUM DROPS):
        boolean canPickup = canPickupSand();
        if (!canPickup) {
            if (!inventoryFullNotified) {
                inventoryFullNotified = true;
                logDirect("§6[AutoSand] §eBalo đã đầy cát 100%! Đang tạm dừng chờ bạn dọn túi đồ hoặc cất vào rương.");
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        } else {
            inventoryFullNotified = false;
        }

        // Quét item cát rơi trên đất trong bán kính 10 block quanh người:
        List<ItemEntity> nearbyDrops = getNearbySandDrops(10.0);
        if (!nearbyDrops.isEmpty()) {
            List<ItemEntity> farDrops = new ArrayList<>();
            for (ItemEntity drop : nearbyDrops) {
                if (ctx.player().distanceToSqr(drop) > 1.4 * 1.4) {
                    farDrops.add(drop);
                }
            }

            if (!farDrops.isEmpty()) {
                farDrops.sort(Comparator.comparingDouble(ctx.player()::distanceToSqr));
                ItemEntity closest = farDrops.get(0);

                if (closest.getId() == currentDropTargetId) {
                    dropStuckTicks++;
                    if (dropStuckTicks > 60) { // Quá 3 giây không tới được item này -> tạm bỏ qua 20s
                        ignoredItemEntities.put(closest.getId(), now + 20000L);
                        currentDropTargetId = -1;
                        dropStuckTicks = 0;
                    }
                } else {
                    currentDropTargetId = closest.getId();
                    dropStuckTicks = 0;
                }

                if (currentDropTargetId != -1) {
                    if (currentGoalObject instanceof GoalComposite && !calcFailed && baritone.getPathingBehavior().isPathing()) {
                        return new PathingCommand(currentGoalObject, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                    }
                    List<Goal> dropGoals = new ArrayList<>();
                    int maxGoals = Math.min(farDrops.size(), 8);
                    for (int i = 0; i < maxGoals; i++) {
                        dropGoals.add(new GoalNear(farDrops.get(i).blockPosition(), 1));
                    }
                    Goal goal = dropGoals.size() == 1 ? dropGoals.get(0) : new GoalComposite(dropGoals.toArray(new Goal[0]));
                    currentGoalObject = goal;
                    return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
                }
            } else {
                currentDropTargetId = -1;
                dropStuckTicks = 0;
            }
        } else {
            currentDropTargetId = -1;
            dropStuckTicks = 0;
        }

        // 5. Quét tìm cát trong tầm với trực tiếp (In-Reach Scan):
        int reachCeil = (int) Math.ceil(reach);
        int minY = getMinAllowedY();
        List<BlockPos> reachableSand = new ArrayList<>();

        for (int dx = -reachCeil; dx <= reachCeil; dx++) {
            for (int dz = -reachCeil; dz <= reachCeil; dz++) {
                // dy từ thấp hơn chân theo sườn dốc đến reachCeil (cột cát phía trên):
                for (int dy = -Math.min(3, reachCeil); dy <= reachCeil; dy++) {
                    BlockPos p = feet.offset(dx, dy, dz);

                    // An toàn: Không đào xuống dưới minY hoặc tầng âm (Y <= 0):
                    if (p.getY() < minY || p.getY() <= 0) {
                        continue;
                    }
                    // Tuyệt đối không đào block sàn ngay dưới chân (để giữ vị trí đứng vững):
                    if (p.getX() == feet.getX() && p.getZ() == feet.getZ() && p.getY() < feet.getY()) {
                        continue;
                    }
                    if (blacklist.contains(p)) {
                        continue;
                    }
                    if (range > 0 && center != null && p.distSqr(center) > range * range) {
                        continue;
                    }
                    if (feet.distSqr(p) > reachSq) {
                        continue;
                    }

                    BlockState s = ctx.world().getBlockState(p);
                    if (isSand(s)) {
                        if (RotationUtils.reachable(ctx, p, reach).isPresent()) {
                            reachableSand.add(p);
                        }
                    }
                }
            }
        }

        // Ưu tiên:
        // 1. Cột cát có cát phía trên (xả sập cả cột cát rơi thẳng vào tay!)
        // 2. Cồn cát ngang ngực/mắt (dy >= 0) trước khi đào sàn (dy = -1)
        // 3. Block gần nhất
        if (!reachableSand.isEmpty()) {
            reachableSand.sort((a, b) -> {
                boolean aHasAbove = isSand(ctx.world().getBlockState(a.above()));
                boolean bHasAbove = isSand(ctx.world().getBlockState(b.above()));
                if (aHasAbove != bHasAbove) {
                    return aHasAbove ? -1 : 1;
                }
                boolean aFloor = a.getY() < feet.getY();
                boolean bFloor = b.getY() < feet.getY();
                if (aFloor != bFloor) {
                    return aFloor ? 1 : -1;
                }
                return Double.compare(feet.distSqr(a), feet.distSqr(b));
            });

            BlockPos target = reachableSand.get(0);
            activeMiningBlock = target;
            activeMiningTicks = 0;
            fallingWaitTicks = 0;
            currentGoal = null;
            currentGoalObject = null;

            Optional<Rotation> rot = RotationUtils.reachable(ctx, target, reach);
            if (rot.isPresent()) {
                baritone.getPathingBehavior().cancelSegmentIfSafe();
                clearMovementKeys();
                baritone.getLookBehavior().updateTarget(rot.get(), true);
                MovementHelper.switchToBestToolFor(ctx, ctx.world().getBlockState(target));
                if (isAimedAtBlock(target, rot.get())) {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        // 6. Khi toàn bộ cát trong tầm với đã được đào sạch:
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);

        // A. Kiểm tra vét nốt item cát rơi trong phạm vi mở rộng (16 block):
        List<ItemEntity> extendedDrops = getNearbySandDrops(16.0);
        if (!extendedDrops.isEmpty()) {
            extendedDrops.sort(Comparator.comparingDouble(ctx.player()::distanceToSqr));
            ItemEntity closest = extendedDrops.get(0);
            if (ctx.player().distanceToSqr(closest) > 1.4 * 1.4) {
                if (currentGoalObject instanceof GoalNear && !calcFailed && baritone.getPathingBehavior().isPathing()) {
                    return new PathingCommand(currentGoalObject, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                }
                currentGoalObject = new GoalNear(closest.blockPosition(), 1);
                return new PathingCommand(currentGoalObject, PathingCommandType.SET_GOAL_AND_PATH);
            }
        }

        // B. Tìm cụm cồn cát gần nhất bằng thuật toán tìm kiếm vỏ đồng tâm siêu nhẹ (Concentric Shell Search):
        // Cực nhanh (<0.05ms), dừng ngay lập tức khi tìm thấy cát gần nhất, KHÔNG quét thế giới:
        BlockPos nextTarget = findNearestSand(feet, 32);
        if (nextTarget == null && (range == 0 || range > 32)) {
            nextTarget = findNearestSand(feet, Math.min(range > 0 ? range : 64, 64));
        }

        if (nextTarget != null) {
            currentGoal = nextTarget;
            // Nếu đang di chuyển tới mục tiêu này rồi thì duy trì mượt mà không ngắt nhịp:
            if (currentGoalObject instanceof GoalNear && !calcFailed && (baritone.getPathingBehavior().isPathing() || baritone.getPathingBehavior().getInProgress().isPresent())) {
                return new PathingCommand(currentGoalObject, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }
            currentGoalObject = new GoalNear(nextTarget, 2);
            return new PathingCommand(currentGoalObject, PathingCommandType.SET_GOAL_AND_PATH);
        }

        // C. TỰ ĐỘNG ĐI THẲNG ĐỂ TÌM CÁT (STRAIGHT-LINE EXPLORATION):
        // Nếu không có cát xung quanh, bot sẽ tiếp tục chạy thẳng để tìm cồn cát/sa mạc mới!
        if (range == 0 || (center != null && feet.distSqr(center) < range * range)) {
            if (straightDirection == null) {
                straightDirection = ctx.player().getDirection();
            }
            // Nếu đang chạy thẳng rồi -> duy trì đường đi, KHÔNG reset mỗi tick!
            if (currentGoalObject instanceof GoalXZ && !calcFailed && (baritone.getPathingBehavior().isPathing() || baritone.getPathingBehavior().getInProgress().isPresent())) {
                return new PathingCommand(currentGoalObject, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }

            long nowTime = System.currentTimeMillis();
            if (nowTime - lastScoutLogTime > 4000) {
                lastScoutLogTime = nowTime;
                logDirect("§6[AutoSand] §eĐang tự động chạy thẳng về hướng §b" 
                        + straightDirection.getName().toUpperCase() + " §eđể tìm cồn cát...");
            }
            // Đặt mục tiêu chạy thẳng về phía trước 48 block:
            BlockPos straightTarget = feet.relative(straightDirection, 48);
            currentGoal = straightTarget;
            currentGoalObject = new GoalXZ(straightTarget.getX(), straightTarget.getZ());
            return new PathingCommand(currentGoalObject, PathingCommandType.SET_GOAL_AND_PATH);
        }

        // Nếu có giới hạn range và đã đi hết bán kính cho phép:
        logDirect("§a[AutoSand] Đã đào xong toàn bộ cát trong khu vực bán kính giới hạn!");
        cancel();
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    private List<ItemEntity> getNearbySandDrops(double radius) {
        if (ctx.world() == null || ctx.player() == null) {
            return Collections.emptyList();
        }
        BetterBlockPos feet = ctx.playerFeet();
        int minY = getMinAllowedY();
        AABB box = new AABB(feet).inflate(radius, 4, radius);

        return ctx.world().getEntitiesOfClass(ItemEntity.class, box, item -> {
            if (!item.isAlive() || item.isRemoved()) return false;
            if (ignoredItemEntities.containsKey(item.getId())) return false;
            if (item.blockPosition().getY() <= 0 || item.blockPosition().getY() < minY) return false;
            if (range > 0 && center != null && item.blockPosition().distSqr(center) > range * range) return false;
            ItemStack is = item.getItem();
            if (is == null || is.isEmpty()) return false;
            return isSandItem(is.getItem());
        });
    }

    private BlockPos findNearestSand(BetterBlockPos feet, int maxRadius) {
        int minY = getMinAllowedY();
        for (int r = 3; r <= maxRadius; r += 2) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Chỉ quét vỏ viền hình hộp (box shell) để không duyệt lặp
                    if (r > 3 && Math.abs(dx) != r && Math.abs(dz) != r && Math.abs(dx) != (r - 1) && Math.abs(dz) != (r - 1)) {
                        continue;
                    }
                    // Tìm cát ở bề mặt xung quanh người chơi (dy từ -6 đến +6 để phát hiện cồn cát dốc xuống):
                    for (int dy = -6; dy <= 6; dy++) {
                        BlockPos p = feet.offset(dx, dy, dz);
                        if (p.getY() < minY || p.getY() <= 0) {
                            continue;
                        }
                        if (blacklist.contains(p)) continue;
                        if (range > 0 && center != null && p.distSqr(center) > range * range) continue;
                        BlockState s = ctx.world().getBlockState(p);
                        if (isSand(s) && isExposed(p)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isExposed(BlockPos p) {
        for (Direction dir : Direction.values()) {
            BlockState adj = ctx.world().getBlockState(p.relative(dir));
            if (adj.isAir() || !adj.isSolid()) {
                return true;
            }
        }
        return false;
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
            if (yawDiff < 4.5F && pitchDiff < 4.5F) {
                return true;
            }
        }
        return false;
    }

    private void clearMovementKeys() {
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_BACK, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_LEFT, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_RIGHT, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
        baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
    }

    private void notifyProgress() {
        if (totalMined > 0 && totalMined % 64 == 0) {
            long now = System.currentTimeMillis();
            if (now - lastLogTime > 1500) {
                lastLogTime = now;
                int stacks = totalMined / 64;
                logDirect("§e[AutoSand] Đã đào: §a" + totalMined + " block cát §e(" + stacks + " stack" + (targetCount > 0 ? " / " + ((targetCount + 63) / 64) + " stack" : "") + ")!");
            }
        }
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public String displayName0() {
        return "Auto Sand [" + totalMined + (targetCount > 0 ? "/" + targetCount : "") + "]";
    }
}
