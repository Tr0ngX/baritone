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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * NextGen Tr0ngX ClickGUI (Inspired by LiquidBounce Nextgen & Meteor Client).
 * Giao diện đồ họa tối tân: Tab Bar danh mục, Search Bar tìm kiếm tức thì,
 * Card Modules bo viền neon, Pill Switches xúc giác thị giác, và Hardware Telemetry HUD.
 */
public class AutoMineScreen extends Screen implements Helper {

    private final Baritone baritone;

    // === TRẠNG THÁI CẤU HÌNH QUẶNG MỤC TIÊU ===
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

    // === TRẠNG THÁI CẤU HÌNH TỰ ĐỘNG & SINH TỒN ===
    public static boolean optMiningStats = true;
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
    public static boolean optStreamerMode = false;
    public static boolean optHideScoreboard = false;
    public static boolean optHidePlayerName = false;
    public static boolean optStrictOneDirection = true;
    public static int optTargetY = -54;

    private static final int[] FPS_LEVELS = new int[]{260, 240, 144, 120, 60, 30};

    // === TRẠNG THÁI TAB & TÌM KIẾM LIQUIDBOUNCE NEXTGEN ===
    private static int activeTab = 0;
    private static final String[] TAB_NAMES = new String[]{
            "QUẶNG MỤC TIÊU",
            "DI CHUYỂN & HẦM",
            "SINH TỒN & BẢO VỆ",
            "HIỂN THỊ & HUD",
            "THỐNG KÊ & PHẦN CỨNG"
    };
    private static final String[] TAB_ICONS = new String[]{
            "⛏",
            "⚡",
            "🛡",
            "🎨",
            "📊"
    };

    private String searchQuery = "";
    private boolean searchFocused = false;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Định nghĩa Module hiển thị
    private static class ModuleItem {
        final String name;
        final String desc;
        final String category;
        final int color;
        final java.util.function.BooleanSupplier getter;
        final Runnable toggle;

        ModuleItem(String name, String desc, String category, int color, java.util.function.BooleanSupplier getter, Runnable toggle) {
            this.name = name;
            this.desc = desc;
            this.category = category;
            this.color = color;
            this.getter = getter;
            this.toggle = toggle;
        }
    }

    private final List<ModuleItem> allModules = new ArrayList<>();

    public AutoMineScreen(Baritone baritone) {
        super(Component.literal("TR0NGX NEXTGEN CLICKGUI"));
        this.baritone = baritone;
        initModuleRegistry();
    }

    private void initModuleRegistry() {
        allModules.clear();

        // 1. TAB QUẶNG
        allModules.add(new ModuleItem("Kim Cương", "Khai thác quặng Diamond & Deepslate Diamond", "ORES", 0xFF38BDF8, () -> oreDiamond, () -> oreDiamond = !oreDiamond));
        allModules.add(new ModuleItem("Lục Bảo", "Khai thác quặng Emerald quý hiếm trên núi", "ORES", 0xFF34D399, () -> oreEmerald, () -> oreEmerald = !oreEmerald));
        allModules.add(new ModuleItem("Mảnh Vỡ Cổ Đại", "Ancient Debris tầng Netherite Y=15", "ORES", 0xFFC084FC, () -> oreDebris, () -> oreDebris = !oreDebris));
        allModules.add(new ModuleItem("Vàng", "Quặng Gold thế giới thường và Nether Gold", "ORES", 0xFFFBBF24, () -> oreGold, () -> oreGold = !oreGold));
        allModules.add(new ModuleItem("Sắt", "Quặng Iron & Deepslate Iron", "ORES", 0xFFE2E8F0, () -> oreIron, () -> oreIron = !oreIron));
        allModules.add(new ModuleItem("Redstone", "Quặng Đá đỏ cung cấp năng lượng", "ORES", 0xFFF87171, () -> oreRedstone, () -> oreRedstone = !oreRedstone));
        allModules.add(new ModuleItem("Lapis", "Ngọc Lưu Ly Lapis Lazuli phù phép", "ORES", 0xFF60A5FA, () -> oreLapis, () -> oreLapis = !oreLapis));
        allModules.add(new ModuleItem("Đồng", "Quặng Copper & Deepslate Copper", "ORES", 0xFFFB923C, () -> oreCopper, () -> oreCopper = !oreCopper));
        allModules.add(new ModuleItem("Than", "Quặng Coal cung cấp nhiên liệu", "ORES", 0xFF94A3B8, () -> oreCoal, () -> oreCoal = !oreCoal));
        allModules.add(new ModuleItem("Thạch Anh", "Quặng Nether Quartz thế giới Nether", "ORES", 0xFFF1F5F9, () -> oreQuartz, () -> oreQuartz = !oreQuartz));

        // 2. TAB DI CHUYỂN
        allModules.add(new ModuleItem("ARA* Engine", "Tính toán đường đi Anytime Search 0ms phản xạ", "MOVEMENT", 0xFF38BDF8, () -> optZeroDelay, () -> optZeroDelay = !optZeroDelay));
        allModules.add(new ModuleItem("Crawl 1-Block", "Đào hầm chui 1 block siêu tốc bằng trapdoor", "MOVEMENT", 0xFF60A5FA, () -> optCrawlMode, () -> optCrawlMode = !optCrawlMode));
        allModules.add(new ModuleItem("Tunnel Bhop", "Nhảy liên hoàn trong đường hầm tăng tốc độ", "MOVEMENT", 0xFF34D399, () -> optTunnelBhop, () -> optTunnelBhop = !optTunnelBhop));
        allModules.add(new ModuleItem("Shaft Down", "Đào thẳng xuống tầng an toàn chống rơi tự do", "MOVEMENT", 0xFFFBBF24, () -> optShaftDown, () -> optShaftDown = !optShaftDown));
        allModules.add(new ModuleItem("Parkour", "Tự động nhảy vượt chướng ngại vật & kê block", "MOVEMENT", 0xFFC084FC, () -> optParkour, () -> optParkour = !optParkour));
        allModules.add(new ModuleItem("Auto-Sprint", "Tự động chạy nhanh khi bot di chuyển thẳng", "MOVEMENT", 0xFF38BDF8, () -> optAutoSprint, () -> optAutoSprint = !optAutoSprint));
        allModules.add(new ModuleItem("Overshoot", "Cắt cua tốc độ cao ở các góc rẽ mà không dừng", "MOVEMENT", 0xFFFB923C, () -> optOvershoot, () -> optOvershoot = !optOvershoot));
        allModules.add(new ModuleItem("Water Sprint", "Bơi nước tốc độ cao như trên cạn", "MOVEMENT", 0xFF60A5FA, () -> optWaterSprint, () -> optWaterSprint = !optWaterSprint));
        allModules.add(new ModuleItem("Strict 1-Dir", "Khóa cố định 1 hướng đào không đổi hướng ngẫu nhiên", "MOVEMENT", 0xFFF87171, () -> optStrictOneDirection, () -> {
            optStrictOneDirection = !optStrictOneDirection;
            Baritone.settings().mineStrictOneDirection.value = optStrictOneDirection;
        }));

        // 3. TAB SINH TỒN
        allModules.add(new ModuleItem("Auto-Tool", "Tự động đổi công cụ tối ưu (Cúp, Rìu, Xẻng)", "SURVIVAL", 0xFF38BDF8, () -> optAutoTool, () -> optAutoTool = !optAutoTool));
        allModules.add(new ModuleItem("Auto-Eat", "Tự động ăn thức ăn ngon nhất khi đói < 19", "SURVIVAL", 0xFF34D399, () -> optAutoEat, () -> optAutoEat = !optAutoEat));
        allModules.add(new ModuleItem("Auto-Totem", "Tự động lấy Totem of Undying ra tay phụ khi tụt máu", "SURVIVAL", 0xFFFBBF24, () -> optAutoTotem, () -> optAutoTotem = !optAutoTotem));
        allModules.add(new ModuleItem("Shulker Box", "Tự động đặt Shulker Box cất quặng khi đầy balo", "SURVIVAL", 0xFFC084FC, () -> optShulkerStorage, () -> optShulkerStorage = !optShulkerStorage));
        allModules.add(new ModuleItem("Auto-Drop", "Tự vứt đá/đất/gravel đầy stack về sau hoặc vào lava", "SURVIVAL", 0xFF94A3B8, () -> optAutoDrop, () -> optAutoDrop = !optAutoDrop));
        allModules.add(new ModuleItem("Mob Avoid", "Tự động né quái vật nguy hiểm và Spawner 14m", "SURVIVAL", 0xFFF87171, () -> optMobAvoid, () -> optMobAvoid = !optMobAvoid));
        allModules.add(new ModuleItem("Water Check", "Kiểm tra an toàn chất lỏng chống sặc nước / lava", "SURVIVAL", 0xFF60A5FA, () -> optWaterCheck, () -> optWaterCheck = !optWaterCheck));

        // 4. TAB HIỂN THỊ
        allModules.add(new ModuleItem("Stats HUD", "Bảng thống kê số block & quặng đào ở góc màn hình", "HUD", 0xFF38BDF8, () -> optMiningStats, () -> optMiningStats = !optMiningStats));
        allModules.add(new ModuleItem("No-Swing", "Ẩn animation vung tay phía client chống giật màn hình", "HUD", 0xFF94A3B8, () -> optHideSwing, () -> optHideSwing = !optHideSwing));
        allModules.add(new ModuleItem("FastPlace", "Đặt block tức thì 1-tick (0.05s) mượt mà", "HUD", 0xFF34D399, () -> optFastPlace, () -> optFastPlace = !optFastPlace));
        allModules.add(new ModuleItem("Streamer Mode", "Chế độ Livestream ẩn toàn bộ thông tin nhạy cảm", "HUD", 0xFFC084FC, () -> optStreamerMode, () -> {
            optStreamerMode = !optStreamerMode;
            optHideScoreboard = optStreamerMode;
            optHidePlayerName = optStreamerMode;
            Baritone.settings().streamerMode.value = optStreamerMode;
            Baritone.settings().hideScoreboard.value = optHideScoreboard;
            Baritone.settings().hidePlayerName.value = optHidePlayerName;
        }));
        allModules.add(new ModuleItem("Hide Board", "Ẩn hoàn toàn bảng điểm Scoreboard bên phải", "HUD", 0xFF60A5FA, () -> optHideScoreboard, () -> {
            optHideScoreboard = !optHideScoreboard;
            Baritone.settings().hideScoreboard.value = optHideScoreboard;
        }));
        allModules.add(new ModuleItem("Hide Name", "Che tên người chơi trên Actionbar và thông báo", "HUD", 0xFFFBBF24, () -> optHidePlayerName, () -> {
            optHidePlayerName = !optHidePlayerName;
            Baritone.settings().hidePlayerName.value = optHidePlayerName;
        }));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BaritoneKeyBindings.KEY_AUTOMINE_GUI.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        // Xử lý phím Search Input
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    scrollOffset = 0;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused) {
            if (codePoint >= 32 && codePoint != 127) {
                searchQuery += codePoint;
                scrollOffset = 0;
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scrollOffset = (int) Mth.clamp(scrollOffset - scrollY * 24, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = this.width / 2;
        int panelW = Math.max(540, Math.min((int) (this.width * 0.88f), 780));
        int panelHalfW = panelW / 2;
        int panelX = cx - panelHalfW;

        // 1. Kiểm tra Click vào Tab Bar
        int tabY = 32;
        int tabH = 22;
        int tabW = panelW / TAB_NAMES.length;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tx = panelX + i * tabW;
            if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                activeTab = i;
                searchQuery = "";
                searchFocused = false;
                scrollOffset = 0;
                return true;
            }
        }

        // 2. Kiểm tra Click vào Search Bar
        int searchY = 58;
        int searchH = 20;
        int searchW = panelW;
        if (mouseX >= panelX && mouseX <= panelX + searchW && mouseY >= searchY && mouseY <= searchY + searchH) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // 3. Kiểm tra Click vào các nút Quick Select Ores (nếu đang ở tab Ores hoặc tìm kiếm)
        if (activeTab == 0 && searchQuery.isEmpty()) {
            int quickY = 82;
            int btnW = (panelW - 18) / 4;
            for (int q = 0; q < 4; q++) {
                int qx = panelX + q * (btnW + 6);
                if (mouseX >= qx && mouseX <= qx + btnW && mouseY >= quickY && mouseY <= quickY + 18) {
                    if (q == 0) { // ALL
                        oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = true;
                    } else if (q == 1) { // NONE
                        oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = false;
                    } else if (q == 2) { // INVERT
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
                    } else { // RESET DEFAULT
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
                    }
                    return true;
                }
            }
        }

        // 4. Kiểm tra Click vào Module Cards trong vùng nội dung
        int contentY = (activeTab == 0 && searchQuery.isEmpty()) ? 104 : 84;
        int contentBottom = this.height - 46;
        if (mouseY >= contentY && mouseY <= contentBottom) {
            List<ModuleItem> filtered = getFilteredModules();
            int cardPad = 8;
            int cardCols = panelW > 640 ? 2 : 1;
            int colW = (panelW - (cardCols - 1) * cardPad) / cardCols;
            int cardH = 34;

            for (int i = 0; i < filtered.size(); i++) {
                int col = i % cardCols;
                int row = i / cardCols;
                int cardX = panelX + col * (colW + cardPad);
                int cardY = contentY + row * (cardH + 6) - scrollOffset;

                if (cardY + cardH >= contentY && cardY <= contentBottom) {
                    if (mouseX >= cardX && mouseX <= cardX + colW && mouseY >= cardY && mouseY <= cardY + cardH) {
                        filtered.get(i).toggle.run();
                        return true;
                    }
                }
            }

            // Click vào Setting đặc biệt trong Tab 3 (Target Y và FPS Limiter)
            if (activeTab == 3 && searchQuery.isEmpty()) {
                int extraRowY = contentY + ((filtered.size() + cardCols - 1) / cardCols) * (cardH + 6) - scrollOffset;
                int halfColW = (panelW - 8) / 2;

                // Nút Target Y
                int yBtnX = panelX;
                if (mouseX >= yBtnX && mouseX <= yBtnX + halfColW && mouseY >= extraRowY && mouseY <= extraRowY + cardH) {
                    if (optTargetY == -54) optTargetY = -58;
                    else if (optTargetY == -58) optTargetY = 11;
                    else if (optTargetY == 11) optTargetY = 999;
                    else optTargetY = -54;
                    return true;
                }

                // Nút FPS Limit
                int fpsBtnX = panelX + halfColW + 8;
                if (mouseX >= fpsBtnX && mouseX <= fpsBtnX + halfColW && mouseY >= extraRowY && mouseY <= extraRowY + cardH) {
                    int cur = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
                    int nextIndex = 0;
                    for (int f = 0; f < FPS_LEVELS.length; f++) {
                        if (FPS_LEVELS[f] == cur) {
                            nextIndex = (f + 1) % FPS_LEVELS.length;
                            break;
                        }
                    }
                    baritone.getPlayerContext().minecraft().options.framerateLimit().set(FPS_LEVELS[nextIndex]);
                    return true;
                }
            }
        }

        // 5. Kiểm tra Click vào Floating Bottom Action Bar
        int actionBottomY = this.height - 34;
        int actionGap = 6;
        int actionCount = 5;
        int actionW = (panelW - (actionCount - 1) * actionGap) / actionCount;

        for (int a = 0; a < actionCount; a++) {
            int ax = panelX + a * (actionW + actionGap);
            if (mouseX >= ax && mouseX <= ax + actionW && mouseY >= actionBottomY && mouseY <= actionBottomY + 24) {
                if (a == 0) { // START MINING
                    startAutoMine();
                    this.onClose();
                } else if (a == 1) { // CHOP WOOD
                    startAutoChop();
                    this.onClose();
                } else if (a == 2) { // STOP
                    stopAutoMine();
                    this.onClose();
                } else if (a == 3) { // RESET STATS
                    MiningStatsTracker.getInstance().reset();
                    Helper.HELPER.logDirect("§a[Tr0ngX] Đã reset toàn bộ dữ liệu thống kê đào khoáng!");
                } else { // CLOSE
                    this.onClose();
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<ModuleItem> getFilteredModules() {
        List<ModuleItem> result = new ArrayList<>();
        String query = searchQuery.trim().toLowerCase();

        for (ModuleItem item : allModules) {
            if (!query.isEmpty()) {
                if (item.name.toLowerCase().contains(query) || item.desc.toLowerCase().contains(query)) {
                    result.add(item);
                }
            } else {
                if (activeTab == 0 && item.category.equals("ORES")) result.add(item);
                else if (activeTab == 1 && item.category.equals("MOVEMENT")) result.add(item);
                else if (activeTab == 2 && item.category.equals("SURVIVAL")) result.add(item);
                else if (activeTab == 3 && item.category.equals("HUD")) result.add(item);
            }
        }
        return result;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 1. Nền tối mờ cao cấp chuẩn LiquidBounce Nextgen Dark Glass
        graphics.fillGradient(0, 0, this.width, this.height, ClickGuiTheme.BG_SCREEN_TOP, ClickGuiTheme.BG_SCREEN_BOTTOM);

        int cx = this.width / 2;
        int panelW = Math.max(540, Math.min((int) (this.width * 0.88f), 780));
        int panelHalfW = panelW / 2;
        int panelX = cx - panelHalfW;

        // 2. Header Bar với Logo Neon
        graphics.fill(panelX, 6, panelX + panelW, 7, ClickGuiTheme.ACCENT_CYAN);
        ClickGuiTheme.drawText(graphics, this.font, "TR0NGX NEXTGEN CLICKGUI", panelX + 6, 12, ClickGuiTheme.ACCENT_CYAN, true);
        String versionTag = "v1.21.8 STABLE | ARA* ENGINE READY";
        int vW = this.font.width(versionTag);
        ClickGuiTheme.drawText(graphics, this.font, versionTag, panelX + panelW - vW - 6, 12, ClickGuiTheme.TEXT_MUTED, false);

        // 3. LiquidBounce Nextgen Tab Bar
        int tabY = 28;
        int tabH = 24;
        int tabW = panelW / TAB_NAMES.length;
        graphics.fill(panelX, tabY, panelX + panelW, tabY + tabH, ClickGuiTheme.BG_CARD);
        ClickGuiTheme.drawOutline(graphics, panelX, tabY, panelW, tabH, ClickGuiTheme.BORDER_CARD);

        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tx = panelX + i * tabW;
            boolean isTabActive = (activeTab == i && searchQuery.isEmpty());
            boolean isTabHover = mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + tabH;
            ClickGuiTheme.drawTab(graphics, this.font, TAB_ICONS[i], TAB_NAMES[i], tx, tabY, tabW, tabH, isTabActive, isTabHover, ClickGuiTheme.ACCENT_CYAN);
        }

        // 4. Quick Search Bar
        int searchY = 56;
        int searchH = 20;
        graphics.fill(panelX, searchY, panelX + panelW, searchY + searchH, ClickGuiTheme.BG_INPUT);
        int searchBorder = searchFocused ? ClickGuiTheme.ACCENT_CYAN : ClickGuiTheme.BORDER_CARD;
        ClickGuiTheme.drawOutline(graphics, panelX, searchY, panelW, searchH, searchBorder);

        String searchPrompt = searchQuery.isEmpty() ? (searchFocused ? "" : "🔍 Tìm kiếm tính năng, quặng, phím tắt...") : searchQuery;
        int searchColor = searchQuery.isEmpty() ? ClickGuiTheme.TEXT_DIM : ClickGuiTheme.TEXT_TITLE;
        ClickGuiTheme.drawText(graphics, this.font, searchPrompt, panelX + 8, searchY + 6, searchColor, false);
        if (searchFocused && (System.currentTimeMillis() / 400) % 2 == 0) {
            int cursorX = panelX + 8 + this.font.width(searchQuery);
            graphics.fill(cursorX, searchY + 4, cursorX + 1, searchY + searchH - 4, 0xFF38BDF8);
        }

        // 5. Nút Quick Actions cho Tab Ores
        int contentY = 80;
        if (activeTab == 0 && searchQuery.isEmpty()) {
            int quickY = 80;
            int btnW = (panelW - 18) / 4;
            String[] qNames = new String[]{"[ TẤT CẢ ]", "[ BỎ CHỌN ]", "[ ĐẢO NGƯỢC ]", "[ MẶC ĐỊNH ]"};
            int[] qColors = new int[]{ClickGuiTheme.ACCENT_CYAN, ClickGuiTheme.ACCENT_ROSE, ClickGuiTheme.ACCENT_PURPLE, ClickGuiTheme.ACCENT_AMBER};

            for (int q = 0; q < 4; q++) {
                int qx = panelX + q * (btnW + 6);
                boolean qHover = mouseX >= qx && mouseX <= qx + btnW && mouseY >= quickY && mouseY <= quickY + 18;
                ClickGuiTheme.drawActionButton(graphics, this.font, qNames[q], qx, quickY, btnW, 18, qColors[q], qHover);
            }
            contentY = 104;
        }

        // 6. Danh sách Card Modules (Tabs 0-3 hoặc Search Results)
        int contentBottom = this.height - 44;
        graphics.enableScissor(panelX - 4, contentY, panelX + panelW + 4, contentBottom);

        if (activeTab == 4 && searchQuery.isEmpty()) {
            // TAB 4: LIVE TELEMETRY & HARDWARE DASHBOARD
            renderTelemetryDashboard(graphics, panelX, contentY - scrollOffset, panelW);
        } else {
            List<ModuleItem> filtered = getFilteredModules();
            int cardPad = 8;
            int cardCols = panelW > 640 ? 2 : 1;
            int colW = (panelW - (cardCols - 1) * cardPad) / cardCols;
            int cardH = 34;

            int totalRows = (filtered.size() + cardCols - 1) / cardCols;
            if (activeTab == 3 && searchQuery.isEmpty()) {
                totalRows++; // Hàng thêm cho Y-Level và FPS
            }
            maxScroll = Math.max(0, totalRows * (cardH + 6) - (contentBottom - contentY));

            for (int i = 0; i < filtered.size(); i++) {
                int col = i % cardCols;
                int row = i / cardCols;
                int cardX = panelX + col * (colW + cardPad);
                int cardY = contentY + row * (cardH + 6) - scrollOffset;

                ModuleItem item = filtered.get(i);
                boolean active = item.getter.getAsBoolean();
                boolean hover = mouseX >= cardX && mouseX <= cardX + colW && mouseY >= cardY && mouseY <= cardY + cardH;

                // Card Background & Neon Border
                int cardBg = hover ? ClickGuiTheme.BG_CARD_HOVER : (active ? ClickGuiTheme.BG_CARD_ACTIVE : ClickGuiTheme.BG_CARD);
                int cardBorder = hover ? ClickGuiTheme.BORDER_CARD_HOVER : (active ? (item.color | 0x80000000) : ClickGuiTheme.BORDER_CARD);
                ClickGuiTheme.drawCard(graphics, cardX, cardY, colW, cardH, cardBg, cardBorder);

                // Dấu gạch màu Accent bên cạnh trái Card
                graphics.fill(cardX, cardY, cardX + 3, cardY + cardH, active ? item.color : 0x5064748B);

                // Tên & Mô tả Module
                ClickGuiTheme.drawText(graphics, this.font, item.name, cardX + 8, cardY + 5, active ? ClickGuiTheme.TEXT_TITLE : ClickGuiTheme.TEXT_MUTED, active);
                ClickGuiTheme.drawText(graphics, this.font, item.desc, cardX + 8, cardY + 18, ClickGuiTheme.TEXT_DIM, false);

                // Modern Pill Switch bên phải Card
                int switchW = 34;
                int switchH = 16;
                int switchX = cardX + colW - switchW - 8;
                int switchY = cardY + (cardH - switchH) / 2;
                ClickGuiTheme.drawPillSwitch(graphics, this.font, switchX, switchY, switchW, switchH, active, hover);
            }

            // Thêm mục chọn Target Y & FPS trong Tab 3 (Display)
            if (activeTab == 3 && searchQuery.isEmpty()) {
                int extraRowY = contentY + ((filtered.size() + cardCols - 1) / cardCols) * (cardH + 6) - scrollOffset;
                int halfColW = (panelW - 8) / 2;

                // Target Y Card
                int yBtnX = panelX;
                boolean yHover = mouseX >= yBtnX && mouseX <= yBtnX + halfColW && mouseY >= extraRowY && mouseY <= extraRowY + cardH;
                int yBg = yHover ? ClickGuiTheme.BG_CARD_HOVER : ClickGuiTheme.BG_CARD;
                ClickGuiTheme.drawCard(graphics, yBtnX, extraRowY, halfColW, cardH, yBg, ClickGuiTheme.BORDER_CARD);
                graphics.fill(yBtnX, extraRowY, yBtnX + 3, extraRowY + cardH, ClickGuiTheme.ACCENT_CYAN);
                String yLabel = optTargetY == 999 ? "Hiện tại (Current)" : "Y = " + optTargetY;
                ClickGuiTheme.drawText(graphics, this.font, "Tầng Y Đào: " + yLabel, yBtnX + 8, extraRowY + 6, ClickGuiTheme.TEXT_TITLE, true);
                ClickGuiTheme.drawText(graphics, this.font, "Click để chuyển đổi (-58, -54, 11, Hiện tại)", yBtnX + 8, extraRowY + 19, ClickGuiTheme.TEXT_DIM, false);

                // FPS Limit Card
                int fpsBtnX = panelX + halfColW + 8;
                boolean fpsHover = mouseX >= fpsBtnX && mouseX <= fpsBtnX + halfColW && mouseY >= extraRowY && mouseY <= extraRowY + cardH;
                int fpsBg = fpsHover ? ClickGuiTheme.BG_CARD_HOVER : ClickGuiTheme.BG_CARD;
                ClickGuiTheme.drawCard(graphics, fpsBtnX, extraRowY, halfColW, cardH, fpsBg, ClickGuiTheme.BORDER_CARD);
                graphics.fill(fpsBtnX, extraRowY, fpsBtnX + 3, extraRowY + cardH, ClickGuiTheme.ACCENT_EMERALD);
                int curFpsLimit = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
                String fpsStr = curFpsLimit >= 260 ? "Không giới hạn (Max)" : curFpsLimit + " FPS";
                ClickGuiTheme.drawText(graphics, this.font, "Giới Hạn FPS: " + fpsStr, fpsBtnX + 8, extraRowY + 6, ClickGuiTheme.TEXT_TITLE, true);
                ClickGuiTheme.drawText(graphics, this.font, "Click để chuyển đổi mức FPS mong muốn", fpsBtnX + 8, extraRowY + 19, ClickGuiTheme.TEXT_DIM, false);
            }
        }

        graphics.disableScissor();

        // 7. Vẽ bảng thống kê bên cạnh nếu màn hình có đủ chỗ trống và không phải Tab 4
        if (activeTab != 4 && optMiningStats) {
            boolean isMining = baritone.getMineProcess().isActive();
            int statsW = 148;
            if (this.width - (panelX + panelW) >= statsW + 12) {
                MiningStatsTracker.getInstance().renderCard(graphics, this.font, panelX + panelW + 10, contentY, statsW, isMining);
            } else if (panelX >= statsW + 12) {
                MiningStatsTracker.getInstance().renderCard(graphics, this.font, panelX - statsW - 10, contentY, statsW, isMining);
            }
        }

        // 8. Floating Bottom Action Bar
        int actionBottomY = this.height - 36;
        int actionGap = 6;
        int actionCount = 5;
        int actionW = (panelW - (actionCount - 1) * actionGap) / actionCount;

        String[] aNames = new String[]{
                "⛏ BẮT ĐẦU ĐÀO",
                "🌲 CHẶT CÂY",
                "⏹ DỪNG LẠI",
                "↺ RESET STATS",
                "✕ ĐÓNG (" + BaritoneKeyBindings.KEY_AUTOMINE_GUI.getTranslatedKeyMessage().getString() + ")"
        };
        int[] aColors = new int[]{
                ClickGuiTheme.ACCENT_CYAN,
                ClickGuiTheme.ACCENT_EMERALD,
                ClickGuiTheme.ACCENT_ROSE,
                ClickGuiTheme.ACCENT_AMBER,
                ClickGuiTheme.TEXT_MUTED
        };

        for (int a = 0; a < actionCount; a++) {
            int ax = panelX + a * (actionW + actionGap);
            boolean aHover = mouseX >= ax && mouseX <= ax + actionW && mouseY >= actionBottomY && mouseY <= actionBottomY + 24;
            ClickGuiTheme.drawActionButton(graphics, this.font, aNames[a], ax, actionBottomY, actionW, 24, aColors[a], aHover);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderTelemetryDashboard(GuiGraphics g, int x, int y, int w) {
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        float memPct = (float) usedMem / Math.max(1, maxMem);
        int cores = Runtime.getRuntime().availableProcessors();
        String gpu = GL11.glGetString(GL11.GL_RENDERER);
        if (gpu == null) gpu = "Dedicated GPU";
        if (gpu.length() > 32) gpu = gpu.substring(0, 32) + "...";

        int curFps = baritone.getPlayerContext().minecraft().getFps();
        int fpsColor = curFps >= 60 ? ClickGuiTheme.ACCENT_EMERALD : (curFps >= 30 ? ClickGuiTheme.ACCENT_AMBER : ClickGuiTheme.ACCENT_ROSE);

        int halfW = (w - 12) / 2;

        // Card 1: Hardware Specs
        ClickGuiTheme.drawCard(g, x, y, halfW, 110, ClickGuiTheme.BG_CARD, ClickGuiTheme.BORDER_CARD);
        g.fill(x, y, x + halfW, y + 2, ClickGuiTheme.ACCENT_CYAN);
        ClickGuiTheme.drawText(g, this.font, "PHẦN CỨNG & HIỆU NĂNG", x + 10, y + 8, ClickGuiTheme.ACCENT_CYAN, true);

        ClickGuiTheme.drawText(g, this.font, "FPS: " + curFps, x + 10, y + 26, fpsColor, true);
        ClickGuiTheme.drawText(g, this.font, "CPU: " + cores + " Luồng xử lý", x + 10, y + 42, ClickGuiTheme.TEXT_BODY, false);
        ClickGuiTheme.drawText(g, this.font, "GPU: " + gpu, x + 10, y + 58, ClickGuiTheme.TEXT_MUTED, false);

        ClickGuiTheme.drawText(g, this.font, "Bộ nhớ RAM (" + (int) (memPct * 100) + "%): " + usedMem + "MB / " + maxMem + "MB", x + 10, y + 74, ClickGuiTheme.TEXT_BODY, false);
        ClickGuiTheme.drawProgressBar(g, x + 10, y + 90, halfW - 20, 8, memPct, ClickGuiTheme.ACCENT_EMERALD, 0xFF1E293B);

        // Card 2: Mining Live Telemetry
        int rx = x + halfW + 12;
        ClickGuiTheme.drawCard(g, rx, y, halfW, 110, ClickGuiTheme.BG_CARD, ClickGuiTheme.BORDER_CARD);
        g.fill(rx, y, rx + halfW, y + 2, ClickGuiTheme.ACCENT_EMERALD);
        ClickGuiTheme.drawText(g, this.font, "THỐNG KÊ PHIÊN ĐÀO KHOÁNG", rx + 10, y + 8, ClickGuiTheme.ACCENT_EMERALD, true);

        boolean isMining = baritone.getMineProcess().isActive();
        String statusStr = isMining ? "§a● ĐANG HOẠT ĐỘNG" : "§7○ ĐANG NGHỈ";
        ClickGuiTheme.drawText(g, this.font, "Trạng thái: " + statusStr, rx + 10, y + 26, ClickGuiTheme.TEXT_BODY, false);

        String duration = MiningStatsTracker.getInstance().getFormattedDuration();
        ClickGuiTheme.drawText(g, this.font, "Thời gian phiên: " + duration, rx + 10, y + 42, ClickGuiTheme.TEXT_BODY, false);

        int totalBlocks = MiningStatsTracker.getInstance().getTotalBlocksMined();
        int rate = MiningStatsTracker.getInstance().getBlocksPerHour();
        ClickGuiTheme.drawText(g, this.font, "Đã đào: " + String.format("%,d block", totalBlocks), rx + 10, y + 58, ClickGuiTheme.ACCENT_AMBER, true);
        ClickGuiTheme.drawText(g, this.font, "Tốc độ: " + String.format("%,d block/h", rate), rx + 10, y + 74, ClickGuiTheme.ACCENT_CYAN, false);

        int totalDiamonds = MiningStatsTracker.getInstance().getOreCount(MiningStatsTracker.OreType.DIAMOND);
        ClickGuiTheme.drawText(g, this.font, "Kim cương: " + totalDiamonds + " viên", rx + 10, y + 90, ClickGuiTheme.ACCENT_CYAN, true);
    }

    private void stopAutoMine() {
        baritone.getPathingBehavior().cancelEverything();
        baritone.getPathingBehavior().forceCancel();
        baritone.getMineProcess().cancel();
        baritone.getInputOverrideHandler().clearAllKeys();
        baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
        if (baritone.getPlayerContext().player() != null && baritone.getPlayerContext().player().containerMenu != baritone.getPlayerContext().player().inventoryMenu) {
            baritone.getPlayerContext().player().closeContainer();
        }
        Helper.HELPER.logDirect("§c[Tr0ngX] Đã dừng toàn bộ quá trình đào!");
    }

    private void startAutoChop() {
        IPlayerContext playerCtx = baritone.getPlayerContext();
        if (playerCtx.player() == null) {
            return;
        }

        Baritone.settings().autoTool.value = optAutoTool;
        Baritone.settings().assumeExternalAutoTool.value = false;
        Baritone.settings().allowInventory.value = true;
        Baritone.settings().ticksBetweenInventoryMoves.value = 1;
        Baritone.settings().allowDownward.value = true;
        Baritone.settings().allowBreak.value = true;
        Baritone.settings().allowPlace.value = true;
        Baritone.settings().allowPlaceInFluidsSource.value = true;
        Baritone.settings().allowPlaceInFluidsFlow.value = true;

        Baritone.settings().autoEat.value = optAutoEat;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = optAutoTotem;
        Baritone.settings().avoidance.value = optMobAvoid;
        Baritone.settings().mobAvoidanceRadius.value = optMobAvoid ? 14 : 0;
        Baritone.settings().mobAvoidanceCoefficient.value = optMobAvoid ? 500.0 : 1.0;

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

        Baritone.settings().primaryTimeoutMS.value = 20000L;
        Baritone.settings().failureTimeoutMS.value = 30000L;
        Baritone.settings().planAheadPrimaryTimeoutMS.value = 10000L;
        Baritone.settings().planAheadFailureTimeoutMS.value = 15000L;

        List<BlockOptionalMeta> boms = new ArrayList<>();
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

        boms.add(new BlockOptionalMeta(Blocks.OAK_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.BIRCH_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.SPRUCE_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.DARK_OAK_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.ACACIA_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.JUNGLE_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.CHERRY_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.MANGROVE_WOOD));

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
        Helper.HELPER.logDirect("§a[AutoChop] Đã bắt đầu TỰ ĐỘNG CHẶT CÂY!");

        baritone.getMineProcess().setChopMode(true);
        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }

    private void startAutoMine() {
        IPlayerContext playerCtx = baritone.getPlayerContext();

        Baritone.settings().autoTool.value = optAutoTool;
        Baritone.settings().assumeExternalAutoTool.value = false;
        Baritone.settings().allowInventory.value = true;
        Baritone.settings().ticksBetweenInventoryMoves.value = 1;
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
        Baritone.settings().overshootTraverse.value = optOvershoot;
        Baritone.settings().sprintInWater.value = optWaterSprint;
        Baritone.settings().assumeStep.value = false;
        Baritone.settings().allowWaterBucketFall.value = true;

        Baritone.settings().legitMine.value = false;
        Baritone.settings().exploreForBlocks.value = true;
        Baritone.settings().mineScanDroppedItems.value = true;
        Baritone.settings().blacklistClosestOnFailure.value = true;
        Baritone.settings().mineMaxOreLocationsCount.value = 64;
        Baritone.settings().maxCachedWorldScanCount.value = 64;
        Baritone.settings().extendCacheOnThreshold.value = true;
        Baritone.settings().mineDropLoiterDurationMSThanksLouca.value = 200L;

        Baritone.settings().blockBreakSpeed.value = 6;
        Baritone.settings().rightClickSpeed.value = optFastPlace ? 1 : 4;
        Baritone.settings().hideSwingAnimation.value = optHideSwing;
        Baritone.settings().blockBreakAdditionalPenalty.value = 2.0;
        Baritone.settings().blockPlacementPenalty.value = 0.0;
        Baritone.settings().jumpPenalty.value = 0.0;

        Baritone.settings().useAnytimeSearch.value = true;
        Baritone.settings().anytimeSearchEpsilon.value = 2.0;
        Baritone.settings().planningTickLookahead.value = 400;
        Baritone.settings().mineGoalUpdateInterval.value = 5;
        Baritone.settings().primaryTimeoutMS.value = 2500L;
        Baritone.settings().failureTimeoutMS.value = 4000L;
        Baritone.settings().planAheadPrimaryTimeoutMS.value = 2500L;
        Baritone.settings().planAheadFailureTimeoutMS.value = 4000L;
        Baritone.settings().movementTimeoutTicks.value = 140;

        Baritone.settings().autoEat.value = optAutoEat;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = optAutoTotem;
        Baritone.settings().autoShulkerStorage.value = optShulkerStorage;
        Baritone.settings().autoDrop.value = optAutoDrop;
        Baritone.settings().avoidance.value = optMobAvoid;
        Baritone.settings().streamerMode.value = optStreamerMode;
        Baritone.settings().hideScoreboard.value = optHideScoreboard;
        Baritone.settings().hidePlayerName.value = optHidePlayerName;
        Baritone.settings().mobAvoidanceRadius.value = optMobAvoid ? 14 : 0;
        Baritone.settings().mobAvoidanceCoefficient.value = optMobAvoid ? 500.0 : 1.0;
        Baritone.settings().mobSpawnerAvoidanceRadius.value = optMobAvoid ? 16 : 0;
        Baritone.settings().mobSpawnerAvoidanceCoefficient.value = optMobAvoid ? 500.0 : 1.0;

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
        Baritone.settings().mineStrictOneDirection.value = optStrictOneDirection;

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
        Helper.HELPER.logDirect("§a[Tr0ngX] Bắt đầu đào: " + String.join(", ", oreNames) + " (Tầng Y: " + targetY + ")");

        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }
}
