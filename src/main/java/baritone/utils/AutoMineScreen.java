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

    // Trạng thái quặng đã chọn
    public static boolean oreDiamond = true;
    public static boolean oreLapis = true;
    public static boolean oreRedstone = true;
    public static boolean oreGold = true;
    public static boolean oreIron = true;
    public static boolean oreEmerald = true;
    public static boolean oreDebris = true;
    public static boolean oreCopper = false;
    public static boolean oreCoal = false;
    public static boolean oreQuartz = false;

    // Trạng thái tùy chọn tự động
    public static boolean optAutoTool = true;
    public static boolean optAutoEat = true;
    public static boolean optAutoTotem = true;
    public static boolean optAutoDrop = true;
    public static boolean optMobAvoid = true;
    public static boolean optParkour = true;
    public static boolean optZeroDelay = true;
    public static boolean optCrawlMode = false;
    public static boolean optTunnelBhop = true;
    public static int optTargetY = -58;

    private static final int[] FPS_LEVELS = new int[]{260, 240, 144, 120, 60, 30};

    public AutoMineScreen(Baritone baritone) {
        super(Component.literal("BARITONE CONTROL PANEL"));
        this.baritone = baritone;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int leftCol = cx - 215;
        int rightCol = cx + 15;
        int startY = 62;
        int btnW = 96;
        int btnH = 20;
        int gap = 24;

        // CỘT 1: CHỌN QUẶNG (Color-coded Ore Buttons)
        addRenderableWidget(createOreBtn(leftCol, startY, btnW, btnH, "Diamond", 0x38BDF8, oreDiamond, () -> oreDiamond = !oreDiamond));
        addRenderableWidget(createOreBtn(leftCol + 102, startY, btnW, btnH, "Lapis", 0x60A5FA, oreLapis, () -> oreLapis = !oreLapis));

        addRenderableWidget(createOreBtn(leftCol, startY + gap, btnW, btnH, "Redstone", 0xF87171, oreRedstone, () -> oreRedstone = !oreRedstone));
        addRenderableWidget(createOreBtn(leftCol + 102, startY + gap, btnW, btnH, "Gold Ore", 0xFBBF24, oreGold, () -> oreGold = !oreGold));

        addRenderableWidget(createOreBtn(leftCol, startY + gap * 2, btnW, btnH, "Iron Ore", 0xE2E8F0, oreIron, () -> oreIron = !oreIron));
        addRenderableWidget(createOreBtn(leftCol + 102, startY + gap * 2, btnW, btnH, "Emerald", 0x34D399, oreEmerald, () -> oreEmerald = !oreEmerald));

        addRenderableWidget(createOreBtn(leftCol, startY + gap * 3, btnW, btnH, "Debris", 0xC084FC, oreDebris, () -> oreDebris = !oreDebris));
        addRenderableWidget(createOreBtn(leftCol + 102, startY + gap * 3, btnW, btnH, "Copper", 0xFB923C, oreCopper, () -> oreCopper = !oreCopper));

        addRenderableWidget(createOreBtn(leftCol, startY + gap * 4, btnW, btnH, "Coal Ore", 0x94A3B8, oreCoal, () -> oreCoal = !oreCoal));
        addRenderableWidget(createOreBtn(leftCol + 102, startY + gap * 4, btnW, btnH, "Quartz", 0xF1F5F9, oreQuartz, () -> oreQuartz = !oreQuartz));

        addRenderableWidget(Button.builder(Component.literal("[ ALL ]"), b -> {
            oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = true;
            this.rebuildWidgets();
        }).bounds(leftCol, startY + gap * 5, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("[ NONE ]"), b -> {
            oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = false;
            this.rebuildWidgets();
        }).bounds(leftCol + 102, startY + gap * 5, btnW, btnH).build());


        // CỘT 2: CÀI ĐẶT TỰ ĐỘNG & SINH TỒN (Bố cục đôi cân xứng)
        int optW = 200;
        addRenderableWidget(createOptBtn(rightCol, startY, btnW, btnH, "Auto-Tool", optAutoTool, () -> optAutoTool = !optAutoTool));
        addRenderableWidget(createOptBtn(rightCol + 102, startY, btnW, btnH, "Auto-Eat", optAutoEat, () -> optAutoEat = !optAutoEat));

        addRenderableWidget(createOptBtn(rightCol, startY + gap, btnW, btnH, "Auto-Totem", optAutoTotem, () -> optAutoTotem = !optAutoTotem));
        addRenderableWidget(createOptBtn(rightCol + 102, startY + gap, btnW, btnH, "Auto-Drop", optAutoDrop, () -> optAutoDrop = !optAutoDrop));

        addRenderableWidget(createOptBtn(rightCol, startY + gap * 2, btnW, btnH, "Mob Avoid", optMobAvoid, () -> optMobAvoid = !optMobAvoid));
        addRenderableWidget(createOptBtn(rightCol + 102, startY + gap * 2, btnW, btnH, "Parkour", optParkour, () -> optParkour = !optParkour));

        addRenderableWidget(createOptBtn(rightCol, startY + gap * 3, btnW, btnH, "Crawl (1-Block)", optCrawlMode, () -> optCrawlMode = !optCrawlMode));
        addRenderableWidget(createOptBtn(rightCol + 102, startY + gap * 3, btnW, btnH, "ARA* Engine", optZeroDelay, () -> optZeroDelay = !optZeroDelay));

        // Hàng 4 cột 2: Tầng Y và FPS Limit
        String yLabel = optTargetY == 999 ? "Y-Level: Current" : "Y-Level: " + optTargetY;
        addRenderableWidget(Button.builder(Component.literal(yLabel), b -> {
            if (optTargetY == -58) optTargetY = -54;
            else if (optTargetY == -54) optTargetY = 11;
            else if (optTargetY == 11) optTargetY = 999;
            else optTargetY = -58;
            this.rebuildWidgets();
        }).bounds(rightCol, startY + gap * 4, btnW, btnH).build());

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
        }).bounds(rightCol + 102, startY + gap * 4, btnW, btnH).build());

        // Hàng 5 cột 2: Tunnel Bhop (2-Block)
        addRenderableWidget(createOptBtn(rightCol, startY + gap * 5, optW, btnH, "Tunnel Bhop (2-Block)", optTunnelBhop, () -> optTunnelBhop = !optTunnelBhop));


        // HÀNG DƯỚI: NÚT THAO TÁC (Action Controls)
        int bottomY = startY + gap * 7 + 10;
        int actionBtnW = 132;

        addRenderableWidget(Button.builder(Component.literal("START MINING"), b -> {
            startAutoMine();
            this.onClose();
        }).bounds(cx - 206, bottomY, actionBtnW, 22).build());

        addRenderableWidget(Button.builder(Component.literal("STOP MINING"), b -> {
            stopAutoMine();
            this.onClose();
        }).bounds(cx - 66, bottomY, actionBtnW, 22).build());

        addRenderableWidget(Button.builder(Component.literal("CLOSE (F4)"), b -> {
            this.onClose();
        }).bounds(cx + 74, bottomY, actionBtnW, 22).build());
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
        Helper.HELPER.logDirect("[AutoMine] Mining process stopped.");
    }

    private void startAutoMine() {
        IPlayerContext playerCtx = baritone.getPlayerContext();

        // === CẤU HÌNH TỐI ƯU CHO SERVER (BYPASS ANTI-CHEAT KINGMC) ===
        Baritone.settings().autoTool.value = optAutoTool;
        Baritone.settings().assumeExternalAutoTool.value = false; // TẮT giả lập mod ngoài để Baritone tự động đổi cúp/xẻng/rìu
        Baritone.settings().allowInventory.value = true;
        Baritone.settings().ticksBetweenInventoryMoves.value = 1; // 1 tick để server đồng bộ balo
        Baritone.settings().strictLiquidCheck.value = true;
        Baritone.settings().allowDownward.value = true;
        Baritone.settings().allowBreak.value = true;
        Baritone.settings().allowPlace.value = true;
        Baritone.settings().allowPlaceInFluidsSource.value = true;
        Baritone.settings().allowPlaceInFluidsFlow.value = true;
        Baritone.settings().allowSprint.value = false; // TẮT SPRINT khi đào: Người chơi đi bộ bình thường, chống 100% việc lao vào block chưa kịp vỡ bị dựt lùi (rubberband)
        Baritone.settings().sprintAscends.value = false; // Tắt sprint khi nhảy dốc để tránh anti-cheat giật lùi
        Baritone.settings().overshootTraverse.value = false; // Tắt cắt cua để không va chạm block chưa kịp vỡ
        Baritone.settings().assumeStep.value = false; // TẮT Step Hack để server không giật lùi (Far away from path)
        Baritone.settings().allowWaterBucketFall.value = true;

        // X-Ray & World Scanning toàn bộ chunk
        Baritone.settings().legitMine.value = false; // X-Ray quặng
        Baritone.settings().exploreForBlocks.value = true;
        Baritone.settings().mineScanDroppedItems.value = true;
        Baritone.settings().blacklistClosestOnFailure.value = true;
        Baritone.settings().mineMaxOreLocationsCount.value = 256;
        Baritone.settings().maxCachedWorldScanCount.value = 1000;
        Baritone.settings().extendCacheOnThreshold.value = true;
        Baritone.settings().mineDropLoiterDurationMSThanksLouca.value = 200L; // Chờ 200ms để quặng rơi hút vào balo

        // Tốc độ đập block chuẩn Server (Tránh Anti-Cheat hủy packet / rollback block)
        Baritone.settings().blockBreakSpeed.value = 6; // 6 ticks = Chuẩn gốc 100% của Baritone, chống dựt về do FastBreak
        Baritone.settings().blockBreakAdditionalPenalty.value = 2.0; // Chuẩn gốc Baritone
        Baritone.settings().blockPlacementPenalty.value = 0.0;
        Baritone.settings().jumpPenalty.value = 0.0;

        // ARA* Siêu tốc & Lookahead liên tục
        Baritone.settings().useAnytimeSearch.value = true;
        Baritone.settings().anytimeSearchEpsilon.value = 2.0; // Xuất phát 0ms, đường đi ổn định
        Baritone.settings().planningTickLookahead.value = 400; // Tính trước liên tục
        Baritone.settings().mineGoalUpdateInterval.value = 5; // 5 ticks chuẩn gốc Baritone (chống giật cục và chống đổi hướng nửa chừng)
        Baritone.settings().splicePath.value = true;
        Baritone.settings().primaryTimeoutMS.value = 4000L;
        Baritone.settings().failureTimeoutMS.value = 6000L;

        // Sinh tồn & Né quái vật (God Mode)
        Baritone.settings().autoEat.value = optAutoEat;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = optAutoTotem;
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
        
        int targetY = optTargetY == 999 ? (playerCtx.player() != null ? playerCtx.playerFeet().y : -58) : optTargetY;
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

        BaritoneAPI.getProvider().getWorldScanner().repack(playerCtx);
        Helper.HELPER.logDirect("§a[AutoMine] Đã bắt đầu đào: " + String.join(", ", oreNames) + " (Tìm & đào sạch mọi quặng ở mọi tầng Y)");

        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Nền tối mờ chuẩn Sleek Dark (0xD8080C14)
        graphics.fillGradient(0, 0, this.width, this.height, 0xD8080C14, 0xEE0B0F17);

        int cx = this.width / 2;

        // Tiêu đề Header với thanh viền neon
        graphics.fill(cx - 225, 6, cx + 225, 7, 0xFF38BDF8);
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
        if (gpu.length() > 22) {
            gpu = gpu.substring(0, 22) + "..";
        }
        int curFps = baritone.getPlayerContext().minecraft().getFps();
        int curLimit = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
        String limitStr = curLimit >= 260 ? "Unlimited" : curLimit + " FPS";

        // Vẽ Telemetry Card với viền kính mỏng
        graphics.fill(cx - 225, 23, cx + 225, 41, 0x900F172A);
        drawOutline(graphics, cx - 225, 23, 450, 18, 0x3038BDF8);

        String perfLine = "RAM: " + usedMem + "/" + maxMem + "MB (" + usedPct + "%) | CPU: " + cores + " Cores | GPU: " + gpu + " | " + curFps + " FPS (" + limitStr + ")";
        graphics.drawCenteredString(this.font, perfLine, cx, 28, 0x34D399);

        // Khối Card Cột Trái (TARGET ORES)
        int panelBottom = this.height - 40;
        graphics.fill(cx - 225, 47, cx - 8, panelBottom, 0x600F172A);
        drawOutline(graphics, cx - 225, 47, 217, panelBottom - 47, 0x20FFFFFF);
        graphics.fill(cx - 225, 47, cx - 8, 48, 0xFF38BDF8); // Cyan Accent Header Line
        graphics.drawString(this.font, "TARGET ORES & VEINS", cx - 215, 51, 0x38BDF8);

        // Khối Card Cột Phải (AUTOMATION & SAFETY)
        graphics.fill(cx + 8, 47, cx + 225, panelBottom, 0x600F172A);
        drawOutline(graphics, cx + 8, 47, 217, panelBottom - 47, 0x20FFFFFF);
        graphics.fill(cx + 8, 47, cx + 225, 48, 0xFF10B981); // Emerald Accent Header Line
        graphics.drawString(this.font, "AUTOMATION & RAGE ENGINE", cx + 18, 51, 0x34D399);

        // Footer Telemetry Status Line
        String footerStatus = "STATUS: RAGE ENGINE READY | ARA* ANYTIME (EPS=3.0) | 0ms DELAY";
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
