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
import baritone.api.BaritoneAPI;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * NextGen High-Taste AutoMine Screen (F4)
 * Clean typography, modern card panels, hardware telemetry HUD,
 * custom color-coded ore toggles, and zero tacky unicode emojis.
 */
public class AutoMineScreen extends Screen implements Helper {

    private final Baritone baritone;

    // Trạng thái quặng đã chọn (Mặc định chuẩn theo giao diện)
    public static boolean oreDiamond = true;
    public static boolean oreLapis = true;
    public static boolean oreRedstone = true;
    public static boolean oreGold = false;
    public static boolean oreIron = false;
    public static boolean oreEmerald = true;
    public static boolean oreDebris = false;
    public static boolean oreCopper = false;
    public static boolean oreCoal = false;
    public static boolean oreQuartz = false;

    // Trạng thái tùy chọn tự động
    public static boolean optAutoTool = true;
    public static boolean optAutoEat = true;
    public static boolean optAutoTotem = true;
    public static boolean optAutoDrop = true;
    public static boolean optShulkerStorage = true;
    public static boolean optMobAvoid = true;
    public static boolean optParkour = true;
    public static boolean optZeroDelay = true;
    public static boolean optCrawlMode = false;
    public static boolean optTunnelBhop = true;
    public static boolean optShaftDown = true;
    public static boolean optWaterCheck = true;
    public static boolean optAutoSprint = true;
    public static boolean optFastBreak = true;
    public static boolean optHideSwing = false;
    public static boolean optFastPlace = true;
    public static boolean optOvershoot = true;
    public static boolean optWaterSprint = true;
    public static int optTargetY = -54;

    private static final int[] FPS_LEVELS = new int[]{260, 240, 144, 120, 60, 30};

    // Responsive Layout Fields
    private int panelTotalW;
    private int panelHalfW;
    private int colW;
    private int leftCardX;
    private int rightCardX;
    private int btnW;
    private int btnH;
    private int startY;
    private int gap;
    private int leftSub1;
    private int leftSub2;
    private int rightSub1;
    private int rightSub2;
    private int panelBottom;
    private int bottomY;
    private int actionBtnW;
    private int actionGap;
    private int actionStartX;

    public AutoMineScreen(Baritone baritone) {
        super(Component.literal("BARITONE CONTROL PANEL"));
        this.baritone = baritone;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        this.width = width;
        this.height = height;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        // Tự động thích ứng co giãn linh hoạt theo độ rộng cửa sổ (Responsive Width)
        panelTotalW = Math.max(430, Math.min((int) (this.width * 0.85f), 700));
        if (panelTotalW > this.width - 24) {
            panelTotalW = this.width - 24;
        }
        panelHalfW = panelTotalW / 2;
        int cardGap = 12;
        colW = (panelTotalW - cardGap) / 2;

        leftCardX = cx - panelHalfW;
        rightCardX = leftCardX + colW + cardGap;

        // Mỗi cột Card chứa 2 nút con cân đối
        int btnPad = 6;
        btnW = (colW - 16 - btnPad) / 2;
        btnH = 19;
        gap = this.height < 320 ? 20 : 22;
        startY = this.height < 320 ? 46 : 52;

        leftSub1 = leftCardX + 8;
        leftSub2 = leftSub1 + btnW + btnPad;

        rightSub1 = rightCardX + 8;
        rightSub2 = rightSub1 + btnW + btnPad;

        // CỘT 1: CHỌN QUẶNG (Color-coded Ore Buttons)
        addRenderableWidget(createOreBtn(leftSub1, startY, btnW, btnH, "Diamond", 0x38BDF8, oreDiamond, () -> oreDiamond = !oreDiamond));
        addRenderableWidget(createOreBtn(leftSub2, startY, btnW, btnH, "Lapis", 0x60A5FA, oreLapis, () -> oreLapis = !oreLapis));

        addRenderableWidget(createOreBtn(leftSub1, startY + gap, btnW, btnH, "Redstone", 0xF87171, oreRedstone, () -> oreRedstone = !oreRedstone));
        addRenderableWidget(createOreBtn(leftSub2, startY + gap, btnW, btnH, "Gold Ore", 0xFBBF24, oreGold, () -> oreGold = !oreGold));

        addRenderableWidget(createOreBtn(leftSub1, startY + gap * 2, btnW, btnH, "Iron Ore", 0xE2E8F0, oreIron, () -> oreIron = !oreIron));
        addRenderableWidget(createOreBtn(leftSub2, startY + gap * 2, btnW, btnH, "Emerald", 0x34D399, oreEmerald, () -> oreEmerald = !oreEmerald));

        addRenderableWidget(createOreBtn(leftSub1, startY + gap * 3, btnW, btnH, "Debris", 0xC084FC, oreDebris, () -> oreDebris = !oreDebris));
        addRenderableWidget(createOreBtn(leftSub2, startY + gap * 3, btnW, btnH, "Copper", 0xFB923C, oreCopper, () -> oreCopper = !oreCopper));

        addRenderableWidget(createOreBtn(leftSub1, startY + gap * 4, btnW, btnH, "Coal Ore", 0x94A3B8, oreCoal, () -> oreCoal = !oreCoal));
        addRenderableWidget(createOreBtn(leftSub2, startY + gap * 4, btnW, btnH, "Quartz", 0xF1F5F9, oreQuartz, () -> oreQuartz = !oreQuartz));

        addRenderableWidget(Button.builder(Component.literal("[ ALL ]"), b -> {
            oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = true;
            this.rebuildWidgets();
        }).bounds(leftSub1, startY + gap * 5, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("[ NONE ]"), b -> {
            oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = false;
            this.rebuildWidgets();
        }).bounds(leftSub2, startY + gap * 5, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("[ INVERT ]"), b -> {
            oreDiamond = !oreDiamond;
            oreLapis = !oreLapis;
            oreRedstone = !oreRedstone;
            oreGold = !oreGold;
            oreIron = !oreIron;
            oreEmerald = !oreEmerald;
            oreDebris = !oreDebris;
            oreCopper = !oreCopper;
            oreCoal = !oreCoal;
            oreQuartz = !oreQuartz;
            this.rebuildWidgets();
        }).bounds(leftSub1, startY + gap * 6, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("[ RESET ]"), b -> {
            oreDiamond = true;
            oreLapis = true;
            oreRedstone = true;
            oreGold = false;
            oreIron = false;
            oreEmerald = true;
            oreDebris = false;
            oreCopper = false;
            oreCoal = false;
            oreQuartz = false;
            this.rebuildWidgets();
        }).bounds(leftSub2, startY + gap * 6, btnW, btnH).build());

        // Hàng 7 cột 1: Overshoot & Water Sprint
        addRenderableWidget(createOptBtn(leftSub1, startY + gap * 7, btnW, btnH, "Overshoot", optOvershoot, () -> optOvershoot = !optOvershoot));
        addRenderableWidget(createOptBtn(leftSub2, startY + gap * 7, btnW, btnH, "Water Sprint", optWaterSprint, () -> optWaterSprint = !optWaterSprint));


        // CỘT 2: CÀI ĐẶT TỰ ĐỘNG & SINH TỒN (Bố cục đôi cân xứng)
        addRenderableWidget(createOptBtn(rightSub1, startY, btnW, btnH, "Auto-Tool", optAutoTool, () -> optAutoTool = !optAutoTool));
        addRenderableWidget(createOptBtn(rightSub2, startY, btnW, btnH, "Auto-Eat", optAutoEat, () -> optAutoEat = !optAutoEat));

        addRenderableWidget(createOptBtn(rightSub1, startY + gap, btnW, btnH, "Auto-Totem", optAutoTotem, () -> optAutoTotem = !optAutoTotem));
        addRenderableWidget(createOptBtn(rightSub2, startY + gap, btnW, btnH, "Shulker Box", optShulkerStorage, () -> optShulkerStorage = !optShulkerStorage));

        addRenderableWidget(createOptBtn(rightSub1, startY + gap * 2, btnW, btnH, "Mob Avoid", optMobAvoid, () -> optMobAvoid = !optMobAvoid));
        addRenderableWidget(createOptBtn(rightSub2, startY + gap * 2, btnW, btnH, "Parkour", optParkour, () -> optParkour = !optParkour));

        addRenderableWidget(createOptBtn(rightSub1, startY + gap * 3, btnW, btnH, "Crawl 1-Block", optCrawlMode, () -> optCrawlMode = !optCrawlMode));
        addRenderableWidget(createOptBtn(rightSub2, startY + gap * 3, btnW, btnH, "ARA* Engine", optZeroDelay, () -> optZeroDelay = !optZeroDelay));

        // Hàng 4 cột 2: Tầng Y và FPS Limit
        String yLabel = optTargetY == 999 ? "Y-Level: Current" : "Y-Level: " + optTargetY;
        addRenderableWidget(Button.builder(Component.literal(yLabel), b -> {
            if (optTargetY == -54) optTargetY = -58;
            else if (optTargetY == -58) optTargetY = 11;
            else if (optTargetY == 11) optTargetY = 999;
            else optTargetY = -54;
            this.rebuildWidgets();
        }).bounds(rightSub1, startY + gap * 4, btnW, btnH).build());

        int currentLimit = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
        String fpsLimitText = currentLimit >= 260 ? "FPS: Unlimited" : "FPS: " + currentLimit;
        addRenderableWidget(Button.builder(Component.literal(fpsLimitText), b -> {
            int cur = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
            int nextIndex = 0;
            for (int i = 0; i < FPS_LEVELS.length; i++) {
                if (FPS_LEVELS[i] == cur) {
                    nextIndex = (i + 1) % FPS_LEVELS.length;
                    break;
                }
            }
            baritone.getPlayerContext().minecraft().options.framerateLimit().set(FPS_LEVELS[nextIndex]);
            this.rebuildWidgets();
        }).bounds(rightSub2, startY + gap * 4, btnW, btnH).build());

        // Hàng 5 cột 2: Tunnel Bhop & Shaft Down
        addRenderableWidget(createOptBtn(rightSub1, startY + gap * 5, btnW, btnH, "Tunnel Bhop", optTunnelBhop, () -> optTunnelBhop = !optTunnelBhop));
        addRenderableWidget(createOptBtn(rightSub2, startY + gap * 5, btnW, btnH, "Shaft Down", optShaftDown, () -> optShaftDown = !optShaftDown));

        // Hàng 6 cột 2: Water Check & Auto-Sprint
        addRenderableWidget(createOptBtn(rightSub1, startY + gap * 6, btnW, btnH, "Water Check", optWaterCheck, () -> optWaterCheck = !optWaterCheck));
        addRenderableWidget(createOptBtn(rightSub2, startY + gap * 6, btnW, btnH, "Auto-Sprint", optAutoSprint, () -> optAutoSprint = !optAutoSprint));

        // Hàng 7 cột 2: No-Swing & FastPlace
        addRenderableWidget(createOptBtn(rightSub1, startY + gap * 7, btnW, btnH, "No-Swing", optHideSwing, () -> optHideSwing = !optHideSwing));
        addRenderableWidget(createOptBtn(rightSub2, startY + gap * 7, btnW, btnH, "FastPlace", optFastPlace, () -> optFastPlace = !optFastPlace));


        // HÀNG DƯỚI: NÚT THAO TÁC (Responsive Action Controls)
        panelBottom = startY + gap * 8 + 4;
        bottomY = panelBottom + 8;
        actionGap = 6;
        actionBtnW = (panelTotalW - (actionGap * 3)) / 4;
        actionStartX = leftCardX;

        addRenderableWidget(Button.builder(Component.literal("START MINING"), b -> {
            startAutoMine();
            this.onClose();
        }).bounds(actionStartX, bottomY, actionBtnW, 22).build());

        addRenderableWidget(Button.builder(Component.literal("CHOP WOOD \uD83E\uDE93"), b -> {
            startAutoChop();
            this.onClose();
        }).bounds(actionStartX + (actionBtnW + actionGap), bottomY, actionBtnW, 22).build());

        addRenderableWidget(Button.builder(Component.literal("STOP"), b -> {
            stopAutoMine();
            this.onClose();
        }).bounds(actionStartX + (actionBtnW + actionGap) * 2, bottomY, actionBtnW, 22).build());

        addRenderableWidget(Button.builder(Component.literal("CLOSE (F4)"), b -> {
            this.onClose();
        }).bounds(actionStartX + (actionBtnW + actionGap) * 3, bottomY, actionBtnW, 22).build());
    }

    private Button createOreBtn(int x, int y, int w, int h, String name, int activeColor, boolean state, Runnable toggle) {
        String label = (state ? "[ON] " : "[--] ") + name;
        return Button.builder(Component.literal(label), b -> {
            toggle.run();
            this.rebuildWidgets();
        }).bounds(x, y, w, h).build();
    }

    private Button createOptBtn(int x, int y, int w, int h, String name, boolean state, Runnable toggle) {
        String label = name + (state ? " [ON]" : " [--]");
        return Button.builder(Component.literal(label), b -> {
            toggle.run();
            this.rebuildWidgets();
        }).bounds(x, y, w, h).build();
    }

    private void stopAutoMine() {
        baritone.getCommandManager().execute("stop");
        Helper.HELPER.logDirect("[AutoMine] Process stopped.");
    }

    private void startAutoChop() {
        IPlayerContext playerCtx = baritone.getPlayerContext();
        if (playerCtx.player() == null) {
            return;
        }

        // Tối ưu hóa các cài đặt cho tự động chặt cây (Lumberjack)
        Baritone.settings().autoTool.value = optAutoTool;
        Baritone.settings().assumeExternalAutoTool.value = false;
        Baritone.settings().allowInventory.value = true;
        Baritone.settings().ticksBetweenInventoryMoves.value = 1;
        Baritone.settings().allowDownward.value = true;
        Baritone.settings().allowBreak.value = true;
        Baritone.settings().allowPlace.value = true;
        Baritone.settings().allowPlaceInFluidsSource.value = true;
        Baritone.settings().allowPlaceInFluidsFlow.value = true;

        // Sinh tồn & Né quái
        Baritone.settings().autoEat.value = optAutoEat;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = optAutoTotem;
        Baritone.settings().avoidance.value = optMobAvoid;
        Baritone.settings().mobAvoidanceRadius.value = optMobAvoid ? 14 : 0;
        Baritone.settings().mobAvoidanceCoefficient.value = optMobAvoid ? 500.0 : 1.0;

        // Parkour để leo cành cây/nhảy qua lá
        Baritone.settings().allowParkour.value = optParkour;
        Baritone.settings().allowParkourPlace.value = optParkour;
        Baritone.settings().allowParkourAscend.value = optParkour;
        Baritone.settings().allowDiagonalAscend.value = optParkour;
        Baritone.settings().allowDiagonalDescend.value = optParkour;
        Baritone.settings().noPillar.value = false;

        Baritone.settings().legitMine.value = false;
        Baritone.settings().exploreForBlocks.value = true;
        Baritone.settings().mineScanDroppedItems.value = true;
        Baritone.settings().blacklistClosestOnFailure.value = true;
        Baritone.settings().mineMaxOreLocationsCount.value = 256;
        Baritone.settings().maxCachedWorldScanCount.value = 1000;
        Baritone.settings().extendCacheOnThreshold.value = true;

        List<BlockOptionalMeta> boms = new ArrayList<>();
        // Tất cả loại gỗ thân cây trong Minecraft (Logs & Stems)
        boms.add(new BlockOptionalMeta(Blocks.OAK_LOG));
        boms.add(new BlockOptionalMeta(Blocks.BIRCH_LOG));
        boms.add(new BlockOptionalMeta(Blocks.SPRUCE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.DARK_OAK_LOG));
        boms.add(new BlockOptionalMeta(Blocks.ACACIA_LOG));
        boms.add(new BlockOptionalMeta(Blocks.JUNGLE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.CHERRY_LOG));
        boms.add(new BlockOptionalMeta(Blocks.MANGROVE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.CRIMSON_STEM));
        boms.add(new BlockOptionalMeta(Blocks.WARPED_STEM));

        // Các khối gỗ 6 mặt (Wood)
        boms.add(new BlockOptionalMeta(Blocks.OAK_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.BIRCH_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.SPRUCE_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.DARK_OAK_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.ACACIA_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.JUNGLE_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.CHERRY_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.MANGROVE_WOOD));

        // Gỗ đã lột vỏ (Stripped Logs & Stems)
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_OAK_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_BIRCH_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_SPRUCE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_DARK_OAK_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_ACACIA_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_JUNGLE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_CHERRY_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_MANGROVE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_CRIMSON_STEM));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_WARPED_STEM));

        BaritoneAPI.getProvider().getWorldScanner().repack(playerCtx);
        Helper.HELPER.logDirect("§a[AutoChop] Đã bắt đầu TỰ ĐỘNG CHẶT CÂY (Tất cả loại gỗ: Sồi, Bạch Dương, Thông, Sồi Sẫm, Keo, Rừng, Anh Đào, Đước...)!");

        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }

    private void startAutoMine() {
        IPlayerContext playerCtx = baritone.getPlayerContext();

        // === CẤU HÌNH TỐI ƯU CHO SERVER (BYPASS ANTI-CHEAT KINGMC) ===
        Baritone.settings().autoTool.value = optAutoTool;
        Baritone.settings().assumeExternalAutoTool.value = false; // TẮT giả lập mod ngoài để Baritone tự động đổi cúp/xẻng/rìu
        Baritone.settings().allowInventory.value = true;
        Baritone.settings().ticksBetweenInventoryMoves.value = 1; // 1 tick để server đồng bộ balo
        Baritone.settings().strictLiquidCheck.value = true;
        Baritone.settings().antiLavaOnly.value = true;
        Baritone.settings().waterCheck.value = optWaterCheck;
        Baritone.settings().allowDownward.value = true;
        Baritone.settings().allowBreak.value = true;
        Baritone.settings().allowPlace.value = true;
        Baritone.settings().allowPlaceInFluidsSource.value = true;
        Baritone.settings().allowPlaceInFluidsFlow.value = true;
        Baritone.settings().allowSprint.value = optAutoSprint;
        Baritone.settings().sprintAscends.value = optAutoSprint;
        Baritone.settings().overshootTraverse.value = optOvershoot; // BẬT CẮT CUA TỐC ĐỘ CAO
        Baritone.settings().sprintInWater.value = optWaterSprint; // BẬT SPRINT TRONG NƯỚC
        Baritone.settings().assumeStep.value = false; // TẮT Step Hack để server không giật lùi (Far away from path)
        Baritone.settings().allowWaterBucketFall.value = true;

        // X-Ray & World Scanning toàn bộ chunk
        Baritone.settings().legitMine.value = false; // X-Ray quặng
        Baritone.settings().exploreForBlocks.value = true;
        Baritone.settings().mineScanDroppedItems.value = true;
        Baritone.settings().blacklistClosestOnFailure.value = true;
        Baritone.settings().mineMaxOreLocationsCount.value = 64;
        Baritone.settings().maxCachedWorldScanCount.value = 64;
        Baritone.settings().extendCacheOnThreshold.value = true;
        Baritone.settings().mineDropLoiterDurationMSThanksLouca.value = 200L; // Chờ 200ms để quặng rơi hút vào balo

        // Tốc độ đập block chuẩn gốc Minecraft & Baritone (6 ticks)
        Baritone.settings().blockBreakSpeed.value = 6;
        Baritone.settings().rightClickSpeed.value = optFastPlace ? 1 : 4; // 1 tick đặt block tức thì (0.05s)
        Baritone.settings().hideSwingAnimation.value = optHideSwing;
        Baritone.settings().blockBreakAdditionalPenalty.value = 2.0;
        Baritone.settings().blockPlacementPenalty.value = 0.0;
        Baritone.settings().jumpPenalty.value = 0.0;

        // ARA* Siêu tốc & Lookahead liên tục
        Baritone.settings().useAnytimeSearch.value = true;
        Baritone.settings().anytimeSearchEpsilon.value = 2.0; // Xuất phát 0ms, đường đi ổn định
        Baritone.settings().planningTickLookahead.value = 400; // Tính trước liên tục
        Baritone.settings().mineGoalUpdateInterval.value = 5; // 5 ticks chuẩn gốc Baritone (chống giật cục và chống đổi hướng nửa chừng)
        Baritone.settings().primaryTimeoutMS.value = 2500L; // 2.5s timeout nhanh nhạy, phản xạ tức thì
        Baritone.settings().failureTimeoutMS.value = 4000L;
        Baritone.settings().planAheadPrimaryTimeoutMS.value = 2500L;
        Baritone.settings().planAheadFailureTimeoutMS.value = 4000L;
        Baritone.settings().movementTimeoutTicks.value = 140; // 7s (140 ticks) cho mỗi movement

        // Sinh tồn & Né quái vật (God Mode)
        Baritone.settings().autoEat.value = optAutoEat;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = optAutoTotem;
        Baritone.settings().autoShulkerStorage.value = optShulkerStorage;
        Baritone.settings().autoDrop.value = optAutoDrop;
        Baritone.settings().avoidance.value = optMobAvoid;
        Baritone.settings().mobAvoidanceRadius.value = optMobAvoid ? 14 : 0;
        Baritone.settings().mobAvoidanceCoefficient.value = optMobAvoid ? 500.0 : 1.0;
        Baritone.settings().mobSpawnerAvoidanceRadius.value = optMobAvoid ? 16 : 0;
        Baritone.settings().mobSpawnerAvoidanceCoefficient.value = optMobAvoid ? 200.0 : 1.0;

        // Parkour & Leo dốc chéo
        Baritone.settings().allowParkour.value = optParkour;
        Baritone.settings().allowParkourPlace.value = optParkour;
        Baritone.settings().allowParkourAscend.value = optParkour;
        Baritone.settings().allowDiagonalAscend.value = optParkour;
        Baritone.settings().allowDiagonalDescend.value = optParkour;

        Baritone.settings().crawlMineMode.value = optCrawlMode;
        Baritone.settings().tunnelSprintJump.value = optTunnelBhop;
        Baritone.settings().fastJump.value = true;
        Baritone.settings().straightDownMine.value = optShaftDown;
        Baritone.settings().preferWaterBucketOverDigging.value = true;
        
        int targetY = optTargetY == 999 ? -54 : optTargetY;
        Baritone.settings().legitMineYLevel.value = targetY;
        Baritone.settings().exploreMaintainY.value = targetY;

        List<BlockOptionalMeta> boms = new ArrayList<>();
        List<String> oreNames = new ArrayList<>();

        if (oreDiamond) {
            boms.add(new BlockOptionalMeta(Blocks.DIAMOND_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_DIAMOND_ORE));
            oreNames.add("Diamond");
        }
        if (oreLapis) {
            boms.add(new BlockOptionalMeta(Blocks.LAPIS_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_LAPIS_ORE));
            oreNames.add("Lapis");
        }
        if (oreRedstone) {
            boms.add(new BlockOptionalMeta(Blocks.REDSTONE_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_REDSTONE_ORE));
            oreNames.add("Redstone");
        }
        if (oreGold) {
            boms.add(new BlockOptionalMeta(Blocks.GOLD_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_GOLD_ORE));
            boms.add(new BlockOptionalMeta(Blocks.NETHER_GOLD_ORE));
            oreNames.add("Gold");
        }
        if (oreIron) {
            boms.add(new BlockOptionalMeta(Blocks.IRON_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_IRON_ORE));
            oreNames.add("Iron");
        }
        if (oreEmerald) {
            boms.add(new BlockOptionalMeta(Blocks.EMERALD_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_EMERALD_ORE));
            oreNames.add("Emerald");
        }
        if (oreDebris) {
            boms.add(new BlockOptionalMeta(Blocks.ANCIENT_DEBRIS));
            oreNames.add("Ancient Debris");
        }
        if (oreCopper) {
            boms.add(new BlockOptionalMeta(Blocks.COPPER_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_COPPER_ORE));
            oreNames.add("Copper");
        }
        if (oreCoal) {
            boms.add(new BlockOptionalMeta(Blocks.COAL_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_COAL_ORE));
            oreNames.add("Coal");
        }
        if (oreQuartz) {
            boms.add(new BlockOptionalMeta(Blocks.NETHER_QUARTZ_ORE));
            oreNames.add("Quartz");
        }

        if (boms.isEmpty()) {
            boms.add(new BlockOptionalMeta(Blocks.DIAMOND_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_DIAMOND_ORE));
            oreNames.add("Diamond (Default)");
        }

        baritone.getPathingBehavior().cancelSegmentIfSafe();
        BaritoneAPI.getProvider().getWorldScanner().repack(playerCtx);
        Helper.HELPER.logDirect("§a[AutoMine] Đã bắt đầu đào: " + String.join(", ", oreNames) + " (Tầng Y mục tiêu: " + targetY + ")");

        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Nền tối mờ chuẩn Sleek Dark (0xD8080C14)
        graphics.fillGradient(0, 0, this.width, this.height, 0xD8080C14, 0xEE0B0F17);

        int cx = this.width / 2;

        // Tiêu đề Header với thanh viền neon responsive
        graphics.fill(cx - panelHalfW, 6, cx + panelHalfW, 7, 0xFF38BDF8);
        graphics.drawCenteredString(this.font, "BARITONE NEXTGEN CONTROL PANEL", cx, 10, 0xFFFFFF);

        // Hardware Telemetry HUD (RAM / CPU / GPU / FPS)
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        int usedPct = (int) (usedMem * 100 / Math.max(1, maxMem));
        int cores = Runtime.getRuntime().availableProcessors();
        String gpu = GL11.glGetString(GL11.GL_RENDERER);
        if (gpu == null || gpu.trim().isEmpty()) {
            gpu = "Unknown GPU";
        }
        if (gpu.length() > 24) {
            gpu = gpu.substring(0, 24) + "..";
        }
        int curFps = baritone.getPlayerContext().minecraft().getFps();
        int curLimit = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
        String limitStr = curLimit >= 260 ? "Unlimited" : curLimit + " FPS";

        // Vẽ Telemetry Card với viền kính mỏng co giãn responsive
        graphics.fill(cx - panelHalfW, 23, cx + panelHalfW, 41, 0x900F172A);
        drawOutline(graphics, cx - panelHalfW, 23, panelTotalW, 18, 0x3038BDF8);

        String perfLine = "RAM: " + usedMem + "/" + maxMem + "MB (" + usedPct + "%) | CPU: " + cores + " Cores | GPU: " + gpu + " | " + curFps + " FPS (" + limitStr + ")";
        graphics.drawCenteredString(this.font, perfLine, cx, 28, 0x34D399);

        // Khối Card Cột Trái (TARGET ORES)
        graphics.fill(leftCardX, 47, leftCardX + colW, panelBottom, 0x600F172A);
        drawOutline(graphics, leftCardX, 47, colW, panelBottom - 47, 0x20FFFFFF);
        graphics.fill(leftCardX, 47, leftCardX + colW, 48, 0xFF38BDF8); // Cyan Accent Header Line
        graphics.drawString(this.font, "TARGET ORES & VEINS", leftCardX + 10, 51, 0x38BDF8);

        // Khối Card Cột Phải (AUTOMATION & SAFETY)
        graphics.fill(rightCardX, 47, rightCardX + colW, panelBottom, 0x600F172A);
        drawOutline(graphics, rightCardX, 47, colW, panelBottom - 47, 0x20FFFFFF);
        graphics.fill(rightCardX, 47, rightCardX + colW, 48, 0xFF10B981); // Emerald Accent Header Line
        graphics.drawString(this.font, "AUTOMATION & RAGE ENGINE", rightCardX + 10, 51, 0x34D399);

        // Footer Telemetry Status Line
        String footerStatus = "STATUS: RAGE ENGINE READY | ARA* ANYTIME (EPS=3.0) | ANTI-LAVA 100% | 0ms DELAY";
        graphics.drawCenteredString(this.font, footerStatus, cx, this.height - 14, 0x64748B);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
