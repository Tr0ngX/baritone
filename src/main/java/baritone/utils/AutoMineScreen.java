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
import baritone.command.defaults.ChatButtons;
import baritone.utils.esp.OreEspController;
import baritone.utils.gui.ModuleRowList;
import baritone.utils.hud.OreHudOverlay;
import baritone.utils.gui.SidebarLayout;
import baritone.utils.hud.HudEditScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NextGen Tr0ngX ClickGUI (Inspired by LiquidBounce Nextgen & Meteor Client).
 * 100% Fully Responsive Layout Engine: Tự động thích ứng co giãn hoàn hảo trên mọi kích thước màn hình
 * và mọi mức GUI Scale (từ 320x240 đến 4K), không bao giờ bị tràn lề, cắt chữ hay che khuất.
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

    // === CÁC LOẠI CÂY (AUTO CHOP) ===
    public static boolean woodOak = true;
    public static boolean woodBirch = true;
    public static boolean woodSpruce = true;
    public static boolean woodJungle = true;
    public static boolean woodAcacia = true;
    public static boolean woodDarkOak = true;
    public static boolean woodMangrove = true;
    public static boolean woodCherry = true;
    public static boolean woodBamboo = true;
    public static boolean woodCrimson = false;
    public static boolean woodWarped = false;

    // === TRẠNG THÁI CẤU HÌNH TỰ ĐỘNG & SINH TỒN ===
    public static boolean optMiningStats = true;
    public static boolean optAutoTool = true;
    public static boolean optAutoEat = true;
    public static boolean optAutoTotem = true;
    public static boolean optAutoLogout = false;
    public static boolean optNeverKick = false;
    public static boolean optAutoLogoutOnlyWhileMining = true;
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
    public static boolean optBottingMode = false;
    public static int optBottingFps = 10;
    public static boolean optStrictOneDirection = true;
    public static int optTargetY = -54;

    private static final int[] FPS_LEVELS = new int[]{260, 240, 144, 120, 60, 30};

    // === TRẠNG THÁI TAB & TÌM KIẾM LIQUIDBOUNCE NEXTGEN ===
    private static int activeTab = 0;
    private static final String[] TAB_FULL_NAMES = new String[]{
            "QUẶNG",
            "CHẶT CÂY",
            "DI CHUYỂN",
            "SINH TỒN",
            "GIAO DIỆN",
            "THỐNG KÊ"
    };
    private static final String[] TAB_SHORT_NAMES = new String[]{
            "Quặng",
            "Cây",
            "Đi Lại",
            "Sống",
            "G.Diện",
            "Chỉ Số"
    };
    private static final ItemStack[] TAB_ITEM_ICONS = new ItemStack[]{
            new ItemStack(Items.DIAMOND_ORE),
            new ItemStack(Items.OAK_LOG),
            new ItemStack(Items.COMPASS),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.ENDER_EYE),
            new ItemStack(Items.WRITABLE_BOOK)
    };

    private static final ItemStack[] ACTION_ITEM_ICONS = new ItemStack[]{
            new ItemStack(Items.DIAMOND_PICKAXE),
            new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.BARRIER),
            new ItemStack(Items.RECOVERY_COMPASS),
            new ItemStack(Items.IRON_DOOR)
    };

    private String searchQuery = "";
    private boolean searchFocused = false;
    private boolean webhookInputFocused = false;
    public static int filterMode = 0; // 0: Tất cả, 1: Đang Bật, 2: Đang Tắt
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // === DROPDOWN LB: row nào đang mở, persist vào automine.json ===
    public static final Set<String> expandedModules = new HashSet<>();

    // === Key hàng đặc biệt (không nằm trong registry module) ===
    private static final String KEY_ESP = "__esp__";
    private static final String KEY_TARGET_Y = "__targety__";
    private static final String KEY_FPS = "__fps__";
    private static final String KEY_HUD_EDIT = "__hudedit__";
    private static final String KEY_HUD_RESET = "__hudreset__";

    // === Gợi ý tầng đào cho từng quặng trong dropdown ===
    private static final Map<String, String> ORE_Y_HINT = new HashMap<>();
    static {
        ORE_Y_HINT.put("Kim Cương", "Tầng gợi ý: Y=-58 (nhiều nhất) hoặc Y=-54 (an toàn)");
        ORE_Y_HINT.put("Lục Bảo", "Tầng gợi ý: núi cao Y>100, hiếm ở tầng sâu");
        ORE_Y_HINT.put("Mảnh Vỡ Cổ Đại", "Tầng gợi ý: Nether Y=15, chống dung nham");
        ORE_Y_HINT.put("Quặng Vàng", "Tầng gợi ý: Nether hoặc Overworld Y=-32..32");
        ORE_Y_HINT.put("Quặng Sắt", "Tầng gợi ý: Y=16 hoặc Y=-24");
        ORE_Y_HINT.put("Đá Đỏ (Redstone)", "Tầng gợi ý: Y=-64..-32");
        ORE_Y_HINT.put("Ngọc Lưu Ly (Lapis)", "Tầng gợi ý: Y=0");
        ORE_Y_HINT.put("Quặng Đồng", "Tầng gợi ý: Y=48 hoặc dripstone");
        ORE_Y_HINT.put("Than Đá", "Tầng gợi ý: mọi tầng núi cao");
        ORE_Y_HINT.put("Thạch Anh Nether", "Tầng gợi ý: Nether mọi độ cao, nhiều XP");
    }

    /**
     * Âm thanh click vanilla nhẹ (volume 0.25). Chỉ phát khi thao tác thực sự
     * đổi trạng thái và công tắc âm thanh đang bật.
     */
    private void playClickSound() {
        try {
            if (!Baritone.settings().guiClickSound.value) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.25F));
            }
        } catch (Throwable ignored) {}
    }

    private OreEspController espController() {
        try {
            return baritone.getOreEspController();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void openHudEditor() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.setScreen(new HudEditScreen(this));
            }
        } catch (Throwable ignored) {}
    }

    // === ENGINE BỐ CỤC ĐÁP ỨNG THÔNG MINH (RESPONSIVE ENGINE) ===
    private static class ResponsiveLayout {
        final int panelX;
        final int panelW;
        final int tabY;
        final int tabH;
        final int tabW;
        final int searchY;
        final int searchH;
        final int searchBoxW;
        final int filterW;
        final int quickY;
        final int quickH;
        final int contentY;
        final int contentBottom;
        final int cardCols;
        final int colW;
        final int cardH;
        final int cardGap;
        final int actionBottomY;
        final int actionH;
        final int actionW;
        final int actionGap;
        final boolean isCompact;

        ResponsiveLayout(int screenW, int screenH, int activeTab, boolean hasSearch) {
            this.isCompact = screenW < 560 || screenH < 340;

            // Tính toán lề an toàn: không bao giờ tràn mép màn hình
            int sidePad = screenW < 400 ? 4 : (screenW < 600 ? 8 : 14);
            int maxW = screenW - sidePad * 2;
            int targetW = (int) (screenW * 0.94f);
            this.panelW = Math.min(maxW, Math.min(760, Math.max(260, targetW)));
            this.panelX = (screenW - this.panelW) / 2;

            // Header và Tab bar (6 tabs)
            int headerH = screenH < 320 ? 12 : 14;
            this.tabY = 4 + headerH + (screenH < 320 ? 2 : 4);
            this.tabH = screenH < 320 ? 18 : 22;
            this.tabW = this.panelW / 6;

            // Search bar & Filter Chips
            this.searchY = this.tabY + this.tabH + 3;
            this.searchH = screenH < 320 ? 16 : 19;
            if (this.panelW >= 480) {
                this.filterW = 156;
                this.searchBoxW = this.panelW - this.filterW - 6;
            } else {
                this.filterW = 0;
                this.searchBoxW = this.panelW;
            }

            // Nút chọn nhanh (Tab 0 Quặng, Tab 1 Cây, Tab 2 Di Chuyển, Tab 3 Sinh Tồn)
            boolean showQuick = (activeTab >= 0 && activeTab <= 3) && !hasSearch;
            this.quickH = showQuick ? (screenH < 320 ? 15 : 18) : 0;
            this.quickY = this.searchY + this.searchH + 3;

            // Vùng nội dung danh sách module
            this.contentY = this.quickY + (showQuick ? (this.quickH + 3) : 0);

            // Bottom action dock
            this.actionH = screenH < 320 ? 18 : 22;
            this.actionBottomY = screenH - this.actionH - (screenH < 320 ? 4 : 6);
            this.contentBottom = this.actionBottomY - (screenH < 320 ? 3 : 5);

            // Cột Card: Tự động chuyển 1 cột trên màn hình hẹp, 2 cột trên màn hình rộng
            this.cardCols = this.panelW >= 520 ? 2 : 1;
            this.cardGap = 6;
            this.colW = (this.panelW - (this.cardCols - 1) * this.cardGap) / this.cardCols;
            this.cardH = screenH < 320 ? 26 : 30;

            // 5 nút hành động dưới cùng
            this.actionGap = 4;
            this.actionW = (this.panelW - 4 * this.actionGap) / 5;
        }
    }

    private static class ModuleItem {
        final ItemStack iconItem;
        final String name;
        final String desc;
        final String details;
        final String category;
        final int color;
        final java.util.function.BooleanSupplier getter;
        final Runnable toggle;

        ModuleItem(ItemStack iconItem, String name, String desc, String details, String category, int color, java.util.function.BooleanSupplier getter, Runnable toggle) {
            this.iconItem = iconItem;
            this.name = name;
            this.desc = desc;
            this.details = details;
            this.category = category;
            this.color = color;
            this.getter = getter;
            this.toggle = toggle;
        }
    }

    private final List<ModuleItem> allModules = new ArrayList<>();

    public AutoMineScreen(Baritone baritone) {
        super(Component.literal("BẢNG ĐIỀU KHIỂN TR0NGX"));
        this.baritone = baritone;
        AutoMineConfig.ensureLoaded();
        initModuleRegistry();
    }

    private void initModuleRegistry() {
        allModules.clear();

        // 1. TAB QUẶNG
        allModules.add(new ModuleItem(new ItemStack(Items.DIAMOND_ORE), "Kim Cương", "Khai thác quặng Kim Cương thường và đá sâu",
                "Tự động tìm kiếm và đào mọi khối quặng Kim Cương xung quanh. Tự chuyển cuốc Gia Tài (Fortune) để nhân số lượng kim cương rơi ra. Tầng lý tưởng: Y=-58 hoặc -54.",
                "ORES", 0xFF38BDF8, () -> oreDiamond, () -> oreDiamond = !oreDiamond));
        allModules.add(new ModuleItem(new ItemStack(Items.EMERALD_ORE), "Lục Bảo", "Khai thác quặng Ngọc Lục Bảo quý hiếm trên núi cao",
                "Dò quét và khai thác quặng Ngọc Lục Bảo đơn lẻ trên các dãy núi cao. Thích hợp để tích lũy tiền tệ giao dịch với dân làng.",
                "ORES", 0xFF34D399, () -> oreEmerald, () -> oreEmerald = !oreEmerald));
        allModules.add(new ModuleItem(new ItemStack(Items.ANCIENT_DEBRIS), "Mảnh Vỡ Cổ Đại", "Mảnh Vỡ Cổ Đại (Netherite) tầng Y=15 thế giới Nether",
                "Tự động dò tìm và đào Mảnh Vỡ Cổ Đại (Debris) cực kỳ quý giá trong Nether để đúc phôi Netherite. Tích hợp né Bedrock và chống dung nham.",
                "ORES", 0xFFC084FC, () -> oreDebris, () -> oreDebris = !oreDebris));
        allModules.add(new ModuleItem(new ItemStack(Items.GOLD_ORE), "Quặng Vàng", "Khai thác quặng Vàng thế giới thường và Nether",
                "Khai thác quặng Vàng thường, vàng đá sâu và Nether Gold. Dùng chế tạo Táo Vàng, Cà Rốt Vàng và giao dịch với Piglin.",
                "ORES", 0xFFFBBF24, () -> oreGold, () -> oreGold = !oreGold));
        allModules.add(new ModuleItem(new ItemStack(Items.IRON_ORE), "Quặng Sắt", "Khai thác quặng Sắt thường và đá sâu",
                "Khai thác quặng Sắt thô để chế tạo giáp, xô nước, cuốc sắt và phễu (Hopper). Nguồn tài nguyên kim loại thiết yếu nhất.",
                "ORES", 0xFFE2E8F0, () -> oreIron, () -> oreIron = !oreIron));
        allModules.add(new ModuleItem(new ItemStack(Items.REDSTONE_ORE), "Đá Đỏ (Redstone)", "Khai thác quặng Đá Đỏ cung cấp năng lượng máy móc",
                "Khai thác bột Đá Đỏ phục vụ mạch tự động hóa, máy móc Piston, đường ray tăng tốc. Phổ biến ở các tầng âm Y=-32 đến Y=-64.",
                "ORES", 0xFFF87171, () -> oreRedstone, () -> oreRedstone = !oreRedstone));
        allModules.add(new ModuleItem(new ItemStack(Items.LAPIS_ORE), "Ngọc Lưu Ly (Lapis)", "Khai thác Ngọc Lưu Ly dùng để phù phép trang bị",
                "Khai thác quặng Lapis Lazuli làm nguyên liệu phù phép (Enchant) trang bị tại Bàn Phù Phép và nhuộm màu xanh. Mật độ cao nhất ở Y=0.",
                "ORES", 0xFF60A5FA, () -> oreLapis, () -> oreLapis = !oreLapis));
        allModules.add(new ModuleItem(new ItemStack(Items.COPPER_ORE), "Quặng Đồng", "Khai thác quặng Đồng thường và đá sâu",
                "Thu thập quặng Đồng để chế tạo Kính Viễn Vọng, Cột Thu Lôi và các khối kiến trúc xây dựng bằng đồng độc đáo.",
                "ORES", 0xFFFB923C, () -> oreCopper, () -> oreCopper = !oreCopper));
        allModules.add(new ModuleItem(new ItemStack(Items.COAL_ORE), "Than Đá", "Khai thác quặng Than Đá cung cấp chất đốt, nhiên liệu",
                "Khai thác quặng Than Đá để lấy chất đốt nung khoáng sản, chế tạo đuốc chiếu sáng và phục vụ sinh tồn đường dài.",
                "ORES", 0xFF94A3B8, () -> oreCoal, () -> oreCoal = !oreCoal));
        allModules.add(new ModuleItem(new ItemStack(Items.NETHER_QUARTZ_ORE), "Thạch Anh Nether", "Khai thác quặng Thạch Anh thế giới Nether",
                "Khai thác Thạch Anh trong Nether giúp kiếm kinh nghiệm (XP) cực nhanh để hồi phục độ bền đồ Mending và làm máy so sánh Redstone.",
                "ORES", 0xFFF1F5F9, () -> oreQuartz, () -> oreQuartz = !oreQuartz));

        // 2. TAB CHẶT CÂY (TREES)
        allModules.add(new ModuleItem(new ItemStack(Items.OAK_LOG), "Gỗ Sồi", "Khai thác thân gỗ Sồi và khối gỗ Sồi",
                "Tự động tìm kiếm và đốn hạ cây gỗ Sồi quanh khu vực. Bot tự chặt từ gốc lên ngọn và tự nhặt khối gỗ rơi xung quanh.",
                "TREES", 0xFFB48A55, () -> woodOak, () -> woodOak = !woodOak));
        allModules.add(new ModuleItem(new ItemStack(Items.BIRCH_LOG), "Gỗ Bạch Dương", "Khai thác thân gỗ Bạch Dương trắng rừng thưa",
                "Khai thác thân gỗ Bạch Dương vỏ trắng, loại gỗ sáng màu được ưa chuộng để lát sàn và làm đồ nội thất trang trí.",
                "TREES", 0xFFE2E8F0, () -> woodBirch, () -> woodBirch = !woodBirch));
        allModules.add(new ModuleItem(new ItemStack(Items.SPRUCE_LOG), "Gỗ Thông", "Khai thác thân gỗ Thông rừng Taiga và vùng tuyết",
                "Khai thác cây gỗ Thông thân thẳng đứng trong rừng Taiga. Cực kỳ thích hợp cho các công trình kiến trúc phong cách Trung Cổ.",
                "TREES", 0xFF6B4226, () -> woodSpruce, () -> woodSpruce = !woodSpruce));
        allModules.add(new ModuleItem(new ItemStack(Items.JUNGLE_LOG), "Gỗ Rừng Nhiệt Đới", "Khai thác cây cổ thụ khổng lồ rừng nhiệt đới",
                "Khai thác cây cổ thụ khổng lồ 2x2 trong rừng rậm nhiệt đới. Bot tự động kê khối hoặc leo lên chặt sạch các nhánh gỗ trên cao.",
                "TREES", 0xFF966F33, () -> woodJungle, () -> woodJungle = !woodJungle));
        allModules.add(new ModuleItem(new ItemStack(Items.ACACIA_LOG), "Gỗ Keo", "Khai thác cây gỗ Keo thảo nguyên Savanna",
                "Khai thác cây gỗ Keo thân cong màu cam ấm áp tại các thảo nguyên Savanna nắng gió.",
                "TREES", 0xFFFB923C, () -> woodAcacia, () -> woodAcacia = !woodAcacia));
        allModules.add(new ModuleItem(new ItemStack(Items.DARK_OAK_LOG), "Gỗ Sồi Sẫm", "Khai thác gỗ Sồi Sẫm tán lá dày rừng u ám",
                "Khai thác cây gỗ Sồi Sẫm thân to 2x2 tán lá rậm rạp tại rừng Dark Forest, dùng làm mái nhà và khung cửa sang trọng.",
                "TREES", 0xFF4A3525, () -> woodDarkOak, () -> woodDarkOak = !woodDarkOak));
        allModules.add(new ModuleItem(new ItemStack(Items.MANGROVE_LOG), "Gỗ Đước", "Khai thác cây gỗ Đước đầm lầy ngập mặn",
                "Khai thác cây gỗ Đước ngập mặn với hệ thống rễ chằng chịt và gỗ màu đỏ sẫm đặc trưng.",
                "TREES", 0xFFE11D48, () -> woodMangrove, () -> woodMangrove = !woodMangrove));
        allModules.add(new ModuleItem(new ItemStack(Items.CHERRY_LOG), "Gỗ Anh Đào", "Khai thác hoa anh đào rực rỡ vùng núi cao",
                "Khai thác cây hoa Anh Đào màu hồng phấn mộng mơ trên đỉnh núi, thu hoạch khối gỗ hoa anh đào tuyệt đẹp.",
                "TREES", 0xFFF472B6, () -> woodCherry, () -> woodCherry = !woodCherry));
        allModules.add(new ModuleItem(new ItemStack(Items.BAMBOO_BLOCK), "Thân Tre", "Khai thác bụi tre và khối thân tre",
                "Khai thác các bụi tre và khối thân tre để ghép thành ván tre, bè tre và giàn giáo leo trèo công trình.",
                "TREES", 0xFFA3E635, () -> woodBamboo, () -> woodBamboo = !woodBamboo));
        allModules.add(new ModuleItem(new ItemStack(Items.CRIMSON_STEM), "Thân Nấm Đỏ (Crimson)", "Khai thác thân nấm đỏ Crimson thế giới Nether",
                "Khai thác thân nấm đỏ Crimson trong rừng nấm Nether. Thân gỗ chống cháy hoàn toàn, không bao giờ bị lửa thiêu rụi.",
                "TREES", 0xFFDC2626, () -> woodCrimson, () -> woodCrimson = !woodCrimson));
        allModules.add(new ModuleItem(new ItemStack(Items.WARPED_STEM), "Thân Nấm Xanh (Warped)", "Khai thác thân nấm xanh Warped thế giới Nether",
                "Khai thác thân nấm xanh Warped ngọc bích trong rừng Nether. Thân gỗ màu xanh biển chống cháy cực đẹp.",
                "TREES", 0xFF06B6D4, () -> woodWarped, () -> woodWarped = !woodWarped));

        // 3. TAB DI CHUYỂN
        allModules.add(new ModuleItem(new ItemStack(Items.NETHER_STAR), "Tìm Đường Siêu Tốc (ARA*)", "Thuật toán tìm đường phản xạ tức thì 0ms",
                "Công nghệ Anytime Repairing A* tiên tiến nhất giúp bot phản xạ né tránh và tính đường đi tức thì trong 0 mili-giây, không bị khựng đơ màn hình.",
                "MOVEMENT", 0xFF38BDF8, () -> optZeroDelay, () -> optZeroDelay = !optZeroDelay));
        allModules.add(new ModuleItem(new ItemStack(Items.OAK_TRAPDOOR), "Đào Hầm Chui 1 Ô", "Đào hầm chui 1 block siêu tốc bằng cửa sập",
                "Bot tự đặt cửa sập (trapdoor) để ép người chơi vào tư thế bò (crawl) cao 1 ô. Giảm 50% khối lượng đá cần đào, tăng gấp đôi tốc độ tìm quặng.",
                "MOVEMENT", 0xFF60A5FA, () -> optCrawlMode, () -> optCrawlMode = !optCrawlMode));
        allModules.add(new ModuleItem(new ItemStack(Items.RABBIT_FOOT), "Nhảy Hầm (Bhop)", "Nhảy liên hoàn trong hầm 2 ô tăng tối đa tốc độ",
                "Tự động căn nhịp đập đầu vào trần hầm 2 block để huỷ độ trễ rơi, biến thành chuỗi nhảy bunny-hop liên tục với vận tốc gấp 2 lần chạy bộ.",
                "MOVEMENT", 0xFF34D399, () -> optTunnelBhop, () -> optTunnelBhop = !optTunnelBhop));
        allModules.add(new ModuleItem(new ItemStack(Items.IRON_PICKAXE), "Đào Thẳng Xuống", "Đào hầm dọc thẳng đứng xuống tầng an toàn chống rơi",
                "Đào hầm dọc thẳng đứng xuống tầng Y mục tiêu cực nhanh mà không bao giờ bị rơi tự do vào hang hở hay vũng dung nham.",
                "MOVEMENT", 0xFFFBBF24, () -> optShaftDown, () -> optShaftDown = !optShaftDown));
        allModules.add(new ModuleItem(new ItemStack(Items.FEATHER), "Vượt Địa Hình (Parkour)", "Tự động nhảy vượt chướng ngại vật & kê khối mở đường",
                "Cho phép bot nhảy qua khe hở 1-4 ô, nhảy chéo góc, nhảy bậc cao và tự động kê khối tạm dưới chân khi vượt địa hình hiểm trở.",
                "MOVEMENT", 0xFFC084FC, () -> optParkour, () -> optParkour = !optParkour));
        allModules.add(new ModuleItem(new ItemStack(Items.GOLDEN_BOOTS), "Tự Chạy Nhanh", "Tự động kích hoạt chạy nhanh khi di chuyển thẳng",
                "Tự động kích hoạt chạy nhanh (Sprint) khi có đường đi thoáng phía trước, giúp tiết kiệm thời gian di chuyển giữa các mỏ quặng.",
                "MOVEMENT", 0xFF38BDF8, () -> optAutoSprint, () -> optAutoSprint = !optAutoSprint));
        allModules.add(new ModuleItem(new ItemStack(Items.FIREWORK_ROCKET), "Cắt Cua Tốc Độ", "Cắt góc cua tốc độ cao ở các ngã rẽ mà không bị khựng",
                "Tối ưu góc rẽ: Bot chủ động nghiêng hướng đi trước khi chạm ngã rẽ để ôm cua mượt mà, giữ nguyên đà chạy không bị giảm tốc độ.",
                "MOVEMENT", 0xFFFB923C, () -> optOvershoot, () -> optOvershoot = !optOvershoot));
        allModules.add(new ModuleItem(new ItemStack(Items.HEART_OF_THE_SEA), "Bơi Nhanh Dưới Nước", "Bơi lội dưới nước tốc độ cao mượt mà như trên cạn",
                "Tự động kích hoạt chế độ bơi lặn tốc độ cao khi chìm trong nước, vượt qua các hồ ngầm và sông sâu mà không bị chậm chạp.",
                "MOVEMENT", 0xFF60A5FA, () -> optWaterSprint, () -> optWaterSprint = !optWaterSprint));
        allModules.add(new ModuleItem(new ItemStack(Items.COMPASS), "Khóa Một Hướng Đào", "Đào cố định duy nhất một hướng thẳng, không quay đầu",
                "Ép bot chỉ đào theo đúng một hướng địa lý duy nhất (Bắc, Nam, Đông hoặc Tây). Không bao giờ quay đầu đào lại hầm cũ khi đi đào đường dài (Strip Mine).",
                "MOVEMENT", 0xFFF87171, () -> optStrictOneDirection, () -> {
            optStrictOneDirection = !optStrictOneDirection;
            Baritone.settings().mineStrictOneDirection.value = optStrictOneDirection;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.SPYGLASS), "Tự Do Xoay Màn Hình", "Tự do quay góc nhìn quan sát, máy chủ vẫn khóa theo bot",
                "Cho phép bạn tự do dùng chuột quay nhìn xung quanh ngắm cảnh trong khi bot vẫn tự lái và đào chuẩn xác dưới nền.",
                "MOVEMENT", 0xFF06B6D4, () -> Baritone.settings().clientFreeLook.value, () -> {
            Baritone.settings().clientFreeLook.value = !Baritone.settings().clientFreeLook.value;
        }));

        // 4. TAB SINH TỒN
        allModules.add(new ModuleItem(new ItemStack(Items.DIAMOND_PICKAXE), "Tự Đổi Dụng Cụ", "Tự động chọn dụng cụ tối ưu nhất (Cuốc, Rìu, Xẻng)",
                "Tự động chọn cuốc, rìu, xẻng hoặc kéo phù hợp nhất cho từng khối block để tốc độ đào nhanh nhất và bảo vệ độ bền dụng cụ.",
                "SURVIVAL", 0xFF38BDF8, () -> optAutoTool, () -> optAutoTool = !optAutoTool));
        allModules.add(new ModuleItem(new ItemStack(Items.GOLDEN_CARROT), "Tự Động Ăn Uống", "Tự động ăn thức ăn ngon nhất khi thanh đói dưới 19",
                "Tự động chọn món ăn có độ hồi phục tốt nhất trong túi đồ và ăn khi thanh đói dưới 19. Tự động mua thức ăn qua /shop nếu hết.",
                "SURVIVAL", 0xFF34D399, () -> optAutoEat, () -> optAutoEat = !optAutoEat));
        allModules.add(new ModuleItem(new ItemStack(Items.TOTEM_OF_UNDYING), "Tự Cầm Totem", "Tự động cầm Totem bất tử ra tay phụ & tự mua /shop khi hết",
                "Kiểm tra tay phụ liên tục: nếu mất Totem, bot sẽ tự lôi Totem dự phòng ra tay phụ trong 1 tick. Tự mở /shop mua thêm khi hết.",
                "SURVIVAL", 0xFFFBBF24, () -> optAutoTotem, () -> optAutoTotem = !optAutoTotem));
        allModules.add(new ModuleItem(new ItemStack(Items.BARRIER), "Tự Thoát Khẩn Cấp", "Tự thoát game khi ở trong hồ lava quá 5s, máu thấp hoặc nguy hiểm",
                "Hệ thống an toàn tuyệt đối (LavaGuard): Tự thoát game khi ở trong hồ lava liên tục quá 5 giây (cả thân và mắt ngập trong lava), hoặc khi máu <= 6 HP. Tự ngắt tính năng sau khi kick để tránh lặp vô hạn!",
                "SURVIVAL", 0xFFF87171, () -> optAutoLogout, () -> {
            optAutoLogout = !optAutoLogout;
            Baritone.settings().autoLogoutOnDanger.value = optAutoLogout;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.PLAYER_HEAD), "Phát Hiện Người Chơi", "Tự ngắt kết nối ngay khi thấy người chơi (kể cả tàng hình)",
                "Quét radar người chơi trong phạm vi 32 ô (bao gồm cả người chơi tàng hình). Tự động ngắt kết nối tức thì để chống bị phục kích PvP.",
                "SURVIVAL", 0xFFEF4444, () -> Baritone.settings().autoLogoutOnPlayer.value, () -> {
            Baritone.settings().autoLogoutOnPlayer.value = !Baritone.settings().autoLogoutOnPlayer.value;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.SHIELD), "Không Bao Giờ Kick", "Tuyệt đối không bao giờ ngắt kết nối (kể cả nguy hiểm hay gặp player)",
                "Chế độ Không Bao Giờ Kick: Vô hiệu hóa 100% mọi hành vi tự động thoát game/ngắt kết nối. Cho phép bạn thoải mái treo máy, đứng ở sảnh hoặc PvP mà không lo bị văng bot.",
                "SURVIVAL", 0xFF10B981, () -> optNeverKick, () -> {
            optNeverKick = !optNeverKick;
            Baritone.settings().neverKick.value = optNeverKick;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.IRON_DOOR), "Bảo Vệ Chỉ Khi Đào", "Chỉ tự ngắt kết nối khi đang đào (#mine). Ở sảnh/đứng yên sẽ KHÔNG kick",
                "Chống kick oan ở Sảnh (Lobby / Hub / Spawn): Khi đang ở khu vực an toàn, sảnh chờ hoặc khi không chạy lệnh đào quặng, bot sẽ KHÔNG BAO GIỜ tự ngắt kết nối dù có người chơi đứng quanh.",
                "SURVIVAL", 0xFF38BDF8, () -> optAutoLogoutOnlyWhileMining, () -> {
            optAutoLogoutOnlyWhileMining = !optAutoLogoutOnlyWhileMining;
            Baritone.settings().autoLogoutOnlyWhileMining.value = optAutoLogoutOnlyWhileMining;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.CHAINMAIL_HELMET), "Bỏ Qua Đồng Đội & Team", "Không kick khi gặp đồng đội cùng Team/Clan hoặc clone chính mình",
                "Tự động nhận diện và bỏ qua người chơi cùng Team, Party, Bang hội (Clan), danh sách Whitelist và các thực thể clone nhái tên chính mình do anti-cheat tạo ra.",
                "SURVIVAL", 0xFF60A5FA, () -> Baritone.settings().autoLogoutIgnoreTeammates.value, () -> {
            Baritone.settings().autoLogoutIgnoreTeammates.value = !Baritone.settings().autoLogoutIgnoreTeammates.value;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.SHULKER_BOX), "Lưu Trữ Shulker Box", "Tự mua /shop, cất đồ vào Shulker và cất vào Rương Ender",
                "Khi túi đồ đầy khoáng sản: Tự đặt Hộp Shulker cất đồ. Khi đầy 3 Shulker, tự đặt Rương Ender cất Shulker vào trong và đập lại bằng Silk Touch.",
                "SURVIVAL", 0xFFC084FC, () -> optShulkerStorage, () -> optShulkerStorage = !optShulkerStorage));
        allModules.add(new ModuleItem(new ItemStack(Items.LAVA_BUCKET), "Tự Động Vứt Rác", "Tự lọc ném bỏ đá cuội/đất/sỏi khi đầy túi đồ",
                "Tự động lọc và ném bỏ các khối đá cuội, đá sâu, đất, sỏi thừa thãi ra phía sau hoặc ném vào dung nham để giải phóng không gian chứa quặng quý.",
                "SURVIVAL", 0xFF94A3B8, () -> optAutoDrop, () -> optAutoDrop = !optAutoDrop));
        allModules.add(new ModuleItem(new ItemStack(Items.ZOMBIE_HEAD), "Tránh Xa Quái Vật", "Tự động né tránh quái vật nguy hiểm và lồng quái trong 14m",
                "Tự động tính toán chi phí đường đi né xa quái vật nguy hiểm (Creeper, Warden, Skeleton) và lồng sinh quái (Spawner) trong bán kính 14-16 mét.",
                "SURVIVAL", 0xFFF87171, () -> optMobAvoid, () -> optMobAvoid = !optMobAvoid));
        allModules.add(new ModuleItem(new ItemStack(Items.WATER_BUCKET), "Kiểm Tra Chất Lỏng", "Quét an toàn nước và dung nham chống sặc nước hoặc bỏng",
                "Rà soát nghiêm ngặt các khối chất lỏng phía trước mặt. Chống đào thủng trần hang bị sạt lở nước hoặc dung nham đổ ụp vào đầu.",
                "SURVIVAL", 0xFF60A5FA, () -> optWaterCheck, () -> optWaterCheck = !optWaterCheck));
        allModules.add(new ModuleItem(new ItemStack(Items.ECHO_SHARD), "Gửi Discord Webhook", "Báo cáo quặng đào thêm, tổng quặng & ảnh màn hình lên Discord",
                "Tự động gửi báo cáo số quặng đào thêm (+delta), tổng tích lũy và ảnh chụp màn hình lên Discord cứ mỗi 5 phút. Nhấp chuột phải hoặc chuyển sang Tab THỐNG KÊ để nhập Webhook URL và cài đặt chụp ảnh!",
                "SURVIVAL", 0xFF5865F2, () -> Baritone.settings().discordWebhookEnabled.value, () -> {
            boolean newVal = !Baritone.settings().discordWebhookEnabled.value;
            Baritone.settings().discordWebhookEnabled.value = newVal;
            AutoMineConfig.save();
        }));

        // 5. TAB GIAO DIỆN
        allModules.add(new ModuleItem(new ItemStack(Items.ITEM_FRAME), "Bảng Thống Kê HUD", "Bảng thống kê số khối & quặng đào ở góc màn hình",
                "Hiển thị bảng nổi nhỏ gọn ở góc màn hình thống kê thời gian đào, tốc độ khối/giờ, số quặng đào được và số kim cương rơi ra theo thời gian thực.",
                "HUD", 0xFF38BDF8, () -> optMiningStats, () -> optMiningStats = !optMiningStats));
        allModules.add(new ModuleItem(new ItemStack(Items.SHEARS), "Ẩn Vung Tay", "Ẩn hiệu ứng vung tay phía người chơi giúp màn hình êm ái",
                "Ẩn hiệu ứng vung tay phía người chơi khi đào/đặt block giúp giảm giật lag, chống rung lắc màn hình và êm mắt khi treo máy lâu.",
                "HUD", 0xFF94A3B8, () -> optHideSwing, () -> optHideSwing = !optHideSwing));
        allModules.add(new ModuleItem(new ItemStack(Items.AMETHYST_SHARD), "Đặt Khối Nhanh", "Đặt khối tức thì 1-tick (0.05s) mượt mà chuẩn anti-cheat",
                "Giảm độ trễ đặt khối từ 4 tick xuống còn 1 tick (0.05 giây), giúp việc kê chân, bắc cầu và lấp hầm diễn ra tức thì và mượt mà.",
                "HUD", 0xFF34D399, () -> optFastPlace, () -> optFastPlace = !optFastPlace));
        allModules.add(new ModuleItem(new ItemStack(Items.ENDER_EYE), "ESP Quặng (chỉ cache)", "Highlight quặng đã chọn trong cache, mặc định TẮT",
                "Vẽ hộp màu quanh quặng đã chọn, CHỈ đọc cache client sẵn có, không live-scan. Mặc định TẮT, tự tắt khi farm lớn. Chỉnh bán kính trong dropdown ESP ở tab Quặng.",
                "HUD", 0xFF38BDF8, () -> Baritone.settings().oreEspEnabled.value, () -> {
            try {
                baritone.getOreEspController().setEnabled(!Baritone.settings().oreEspEnabled.value);
            } catch (Throwable ignored) {
                Baritone.settings().oreEspEnabled.value = !Baritone.settings().oreEspEnabled.value;
            }
            AutoMineConfig.save();
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.SPYGLASS), "ESP xuyên tường", "Vẽ hộp ESP xuyên địa hình (mặc định tắt)",
                "Chỉ bật nơi máy chủ cho phép. Mặc định tắt để ESP trông như highlight quặng lộ thiên.",
                "HUD", 0xFF60A5FA, () -> Baritone.settings().oreEspXray.value, () -> {
            Baritone.settings().oreEspXray.value = !Baritone.settings().oreEspXray.value;
            AutoMineConfig.save();
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.NETHERITE_CHESTPLATE), "Chế độ farm nặng", "Ép ESP tắt, tắt animation trang trí khi treo farm lớn",
                "Khi bật: ESP bị ép tắt và không cho bật lại, animation goal/path chuyển tĩnh, tooltip gọn. Dùng khi treo máy farm hàng nghìn block.",
                "HUD", 0xFFF59E0B, () -> Baritone.settings().heavyFarmMode.value, () -> {
            Baritone.settings().heavyFarmMode.value = !Baritone.settings().heavyFarmMode.value;
            AutoMineConfig.save();
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.OBSIDIAN), "Chế độ Botting (Màn hình đen)", "Cực tối ưu botting: Đen màn hình, ngắt 3D, khóa 10 FPS",
                "Tối ưu cực hạn khi cắm bot/treo farm nhiều acc: Dừng 100% render thế giới 3D (GPU ~0%), khóa 10 FPS native, màn hình đen kèm bảng điều khiển Botting Dashboard. Vẫn tự động chụp ảnh thực tế khi gửi Discord Webhook.",
                "HUD", 0xFF0EA5E9, () -> Baritone.settings().bottingMode.value, () -> {
            boolean next = !Baritone.settings().bottingMode.value;
            Baritone.settings().bottingMode.value = next;
            optBottingMode = next;
            AutoMineConfig.save();
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.NOTE_BLOCK), "Âm thanh giao diện", "Tiếng click vanilla nhẹ khi đổi trạng thái",
                "Phát âm thanh UI_BUTTON_CLICK volume 0.25, chỉ khi thao tác thực sự đổi trạng thái.",
                "HUD", 0xFFA855F7, () -> Baritone.settings().guiClickSound.value, () -> {
            Baritone.settings().guiClickSound.value = !Baritone.settings().guiClickSound.value;
            AutoMineConfig.save();
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.ENDER_EYE), "Chế Độ Phát Sóng", "Chế độ Livestream ẩn toàn bộ thông tin nhạy cảm",
                "Chế độ chuyên dụng cho quay phim/livestream: Ẩn toàn bộ tọa độ, bảng điểm (Scoreboard) và tên người chơi để bảo mật vị trí căn cứ.",
                "HUD", 0xFFC084FC, () -> optStreamerMode, () -> {
            optStreamerMode = !optStreamerMode;
            optHideScoreboard = optStreamerMode;
            optHidePlayerName = optStreamerMode;
            Baritone.settings().streamerMode.value = optStreamerMode;
            Baritone.settings().hideScoreboard.value = optHideScoreboard;
            Baritone.settings().hidePlayerName.value = optHidePlayerName;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.MAP), "Ẩn Bảng Điểm", "Ẩn hoàn toàn bảng điểm Scoreboard bên phải màn hình",
                "Ẩn hoàn toàn bảng điểm Scoreboard bên phải màn hình giúp tầm nhìn rộng rãi, không bị che khuất tầm mắt.",
                "HUD", 0xFF60A5FA, () -> optHideScoreboard, () -> {
            optHideScoreboard = !optHideScoreboard;
            Baritone.settings().hideScoreboard.value = optHideScoreboard;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.NAME_TAG), "Ẩn Tên Tài Khoản", "Che tên người chơi trên thanh trạng thái và thông báo",
                "Che giấu tên tài khoản trên thanh trạng thái (Actionbar) và các thông báo nổi để chống lộ danh tính người chơi.",
                "HUD", 0xFFFBBF24, () -> optHidePlayerName, () -> {
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
    public void onClose() {
        AutoMineConfig.save();
        super.onClose();
    }

    private String getResponsiveTabTitle(int i, int tabW) {
        String full = TAB_FULL_NAMES[i];
        if (this.font.width(full) + 26 <= tabW) {
            return full;
        }
        String sh = TAB_SHORT_NAMES[i];
        if (this.font.width(sh) + 24 <= tabW) {
            return sh;
        }
        return "";
    }

    private String getResponsiveActionTitle(int a, int btnW) {
        if (btnW >= 88) {
            switch (a) {
                case 0: return "BẮT ĐẦU ĐÀO";
                case 1: return "CHẶT CÂY";
                case 2: return "DỪNG LẠI";
                case 3: return "ĐẶT LẠI CHỈ SỐ";
                default: return "ĐÓNG (" + BaritoneKeyBindings.KEY_AUTOMINE_GUI.getTranslatedKeyMessage().getString() + ")";
            }
        } else if (btnW >= 55) {
            switch (a) {
                case 0: return "ĐÀO";
                case 1: return "CHẶT";
                case 2: return "DỪNG";
                case 3: return "LÀM MỚI";
                default: return "ĐÓNG";
            }
        } else {
            return "";
        }
    }

    private String getResponsiveQuickTitle(int q, int btnW, int currentTab) {
        if (currentTab == 0) { // Quặng
            if (btnW >= 70) {
                switch (q) {
                    case 0: return "[ KIM CƯƠNG+ ]";
                    case 1: return "[ TẤT CẢ ]";
                    case 2: return "[ BỎ CHỌN ]";
                    default: return "[ ĐẢO NGƯỢC ]";
                }
            } else {
                switch (q) {
                    case 0: return "[QUÝ]";
                    case 1: return "[TẤT CẢ]";
                    case 2: return "[BỎ]";
                    default: return "[ĐẢO]";
                }
            }
        } else if (currentTab == 1) { // Cây
            if (btnW >= 70) {
                switch (q) {
                    case 0: return "[ GỖ THƯỜNG ]";
                    case 1: return "[ TẤT CẢ ]";
                    case 2: return "[ BỎ CHỌN ]";
                    default: return "[ NETHER ]";
                }
            } else {
                switch (q) {
                    case 0: return "[THƯỜNG]";
                    case 1: return "[TẤT CẢ]";
                    case 2: return "[BỎ]";
                    default: return "[NETHER]";
                }
            }
        } else if (currentTab == 2) { // Di chuyển
            if (btnW >= 70) {
                switch (q) {
                    case 0: return "[ TỐC ĐỘ MAX ]";
                    case 1: return "[ AN TOÀN ]";
                    case 2: return "[ ĐỊA HÌNH ]";
                    default: return "[ MẶC ĐỊNH ]";
                }
            } else {
                switch (q) {
                    case 0: return "[TỐC ĐỘ]";
                    case 1: return "[AN TOÀN]";
                    case 2: return "[PARKOUR]";
                    default: return "[CHUẨN]";
                }
            }
        } else if (currentTab == 3) { // Sinh tồn
            if (btnW >= 70) {
                switch (q) {
                    case 0: return "[ BẢO VỆ MAX ]";
                    case 1: return "[ TREO MÁY ]";
                    case 2: return "[ GIỮ ĐỒ ]";
                    default: return "[ MẶC ĐỊNH ]";
                }
            } else {
                switch (q) {
                    case 0: return "[BẢO VỆ]";
                    case 1: return "[AFK]";
                    case 2: return "[GIỮ]";
                    default: return "[CHUẨN]";
                }
            }
        }
        return "";
    }

    private void applyQuickPreset(int tab, int q) {
        if (tab == 0) { // Quặng
            if (q == 0) {
                oreDiamond = oreEmerald = oreDebris = oreLapis = oreRedstone = true;
                oreGold = oreIron = oreCopper = oreCoal = oreQuartz = false;
            } else if (q == 1) {
                oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = true;
            } else if (q == 2) {
                oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = false;
            } else {
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
            }
        } else if (tab == 1) { // Cây cối
            if (q == 0) {
                woodOak = woodBirch = woodSpruce = woodJungle = woodAcacia = woodDarkOak = woodMangrove = woodCherry = woodBamboo = true;
                woodCrimson = woodWarped = false;
            } else if (q == 1) {
                woodOak = woodBirch = woodSpruce = woodJungle = woodAcacia = woodDarkOak = woodMangrove = woodCherry = woodBamboo = woodCrimson = woodWarped = true;
            } else if (q == 2) {
                woodOak = woodBirch = woodSpruce = woodJungle = woodAcacia = woodDarkOak = woodMangrove = woodCherry = woodBamboo = woodCrimson = woodWarped = false;
            } else {
                woodCrimson = woodWarped = true;
                woodOak = woodBirch = woodSpruce = woodJungle = woodAcacia = woodDarkOak = woodMangrove = woodCherry = woodBamboo = false;
            }
        } else if (tab == 2) { // Di chuyển
            if (q == 0) { // Tối Đa Tốc Độ
                optTunnelBhop = true;
                optAutoSprint = true;
                optZeroDelay = true;
                optOvershoot = true;
                optWaterSprint = true;
            } else if (q == 1) { // An Toàn
                optShaftDown = true;
                optWaterCheck = true;
                optTunnelBhop = false;
                optStrictOneDirection = true;
                Baritone.settings().mineStrictOneDirection.value = true;
            } else if (q == 2) { // Vượt Địa Hình
                optParkour = true;
                optZeroDelay = true;
                optCrawlMode = false;
                Baritone.settings().clientFreeLook.value = true;
            } else { // Mặc Định
                optZeroDelay = true;
                optTunnelBhop = true;
                optAutoSprint = true;
                optShaftDown = true;
                optParkour = true;
                optOvershoot = true;
                optWaterSprint = true;
                optStrictOneDirection = true;
                Baritone.settings().mineStrictOneDirection.value = true;
            }
        } else if (tab == 3) { // Sinh tồn
            if (q == 0) { // Bảo Vệ Tối Đa
                optAutoEat = true;
                optAutoTotem = true;
                optAutoLogout = true;
                Baritone.settings().autoLogoutOnDanger.value = true;
                optMobAvoid = true;
                optWaterCheck = true;
            } else if (q == 1) { // Treo Máy (AFK)
                optAutoEat = true;
                optAutoTotem = true;
                optShulkerStorage = true;
                optAutoDrop = true;
                optAutoTool = true;
            } else if (q == 2) { // Giữ Đồ
                optAutoTool = true;
                optShulkerStorage = true;
                optAutoDrop = false;
                optAutoEat = true;
            } else { // Mặc Định
                optAutoTool = true;
                optAutoEat = true;
                optAutoTotem = true;
                optAutoLogout = false;
                Baritone.settings().autoLogoutOnDanger.value = false;
                optShulkerStorage = true;
                optAutoDrop = true;
                optMobAvoid = true;
                optWaterCheck = true;
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (BaritoneKeyBindings.KEY_AUTOMINE_GUI.matches(event)) {
            this.onClose();
            return true;
        }

        int keyCode = event.key();
        if (webhookInputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                webhookInputFocused = false;
                AutoMineConfig.save();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String cur = Baritone.settings().discordWebhookUrl.value;
                if (cur != null && !cur.isEmpty()) {
                    Baritone.settings().discordWebhookUrl.value = cur.substring(0, cur.length() - 1);
                }
                return true;
            }
            if (isControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                pasteWebhookFromClipboard();
                return true;
            }
        }
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

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (webhookInputFocused) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127) {
                String cur = Baritone.settings().discordWebhookUrl.value;
                if (cur == null) cur = "";
                Baritone.settings().discordWebhookUrl.value = cur + event.codepointAsString();
                return true;
            }
        }
        if (searchFocused) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127) {
                searchQuery += event.codepointAsString();
                scrollOffset = 0;
                return true;
            }
        }
        return super.charTyped(event);
    }

    private boolean isControlDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null) {
                return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void pasteWebhookFromClipboard() {
        try {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.trim().isEmpty()) {
                String clean = clip.trim();
                Baritone.settings().discordWebhookUrl.value = clean;
                Baritone.settings().discordWebhookEnabled.value = true;
                AutoMineConfig.save();
                Helper.HELPER.logDirect("§a[Discord] ✔ Đã dán Discord Webhook URL thành công: " + (clean.length() > 45 ? clean.substring(0, 42) + "..." : clean));
            } else {
                Helper.HELPER.logDirect("§e[Discord] Clipboard trống! Hãy copy link Webhook từ Discord trước.");
            }
        } catch (Throwable t) {
            Helper.HELPER.logDirect("§c[Discord] Không thể đọc Clipboard: " + t.getMessage());
        }
    }

    private void testDiscordWebhook() {
        String url = Baritone.settings().discordWebhookUrl.value;
        if (url == null || url.trim().isEmpty() || !url.startsWith("http")) {
            Helper.HELPER.logDirect("§c[Discord] Vui lòng nhập hoặc bấm [DÁN] Webhook URL trước khi gửi thử!");
            return;
        }
        Baritone.settings().discordWebhookEnabled.value = true;
        AutoMineConfig.save();

        Helper.HELPER.logDirect("§b[Discord] Đang gửi báo cáo farm thử nghiệm (kèm ảnh chụp nếu bật) lên Discord Webhook...");
        DiscordManager.getInstance().sendFarmingReport(true);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        ResponsiveLayout l = new ResponsiveLayout(this.width, this.height, activeTab, !searchQuery.isEmpty());
        SidebarLayout clickSb = new SidebarLayout(l.panelX, l.panelW, this.width);
        int contentX = clickSb.contentX;
        int contentW = clickSb.contentW;
        int clickFilterW = contentW >= 480 ? 156 : 0;
        int clickSearchBoxW = clickFilterW > 0 ? contentW - clickFilterW - 6 : contentW;

        // 1. Click sidebar dọc (hoặc tab ngang khi GUI hẹp)
        if (clickSb.useSidebar) {
            for (int i = 0; i < 6; i++) {
                int ty = clickSb.sidebarButtonY(l.tabY, i);
                if (mouseX >= l.panelX && mouseX <= l.panelX + SidebarLayout.SIDEBAR_W
                        && mouseY >= ty && mouseY <= ty + SidebarLayout.SIDEBAR_BUTTON_H) {
                    if (activeTab != i) {
                        activeTab = i;
                        searchQuery = "";
                        searchFocused = false;
                        scrollOffset = 0;
                        playClickSound();
                    }
                    return true;
                }
            }
        } else {
            for (int i = 0; i < 6; i++) {
                int tx = l.panelX + i * l.tabW;
                if (mouseX >= tx && mouseX <= tx + l.tabW && mouseY >= l.tabY && mouseY <= l.tabY + l.tabH) {
                    if (activeTab != i) {
                        activeTab = i;
                        searchQuery = "";
                        searchFocused = false;
                        scrollOffset = 0;
                        playClickSound();
                    }
                    return true;
                }
            }
        }

        // 2. Click Filter Chips (Tất Cả / Đang Bật / Đang Tắt), đổi filter thì reset scroll
        if (clickFilterW > 0 && mouseY >= l.searchY && mouseY <= l.searchY + l.searchH) {
            int chipStart = contentX + clickSearchBoxW + 6;
            int chipW = (clickFilterW - 4) / 3;
            for (int c = 0; c < 3; c++) {
                int cx = chipStart + c * (chipW + 2);
                if (mouseX >= cx && mouseX <= cx + chipW) {
                    if (filterMode != c) {
                        filterMode = c;
                        scrollOffset = 0;
                        playClickSound();
                    }
                    return true;
                }
            }
        }

        // 3. Click Search Bar & Nút [×] xóa nhanh
        if (mouseX >= contentX && mouseX <= contentX + clickSearchBoxW && mouseY >= l.searchY && mouseY <= l.searchY + l.searchH) {
            if (!searchQuery.isEmpty() && mouseX >= contentX + clickSearchBoxW - 18) {
                searchQuery = "";
                searchFocused = false;
                scrollOffset = 0;
                playClickSound();
                return true;
            }
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // 4. Click Quick Select Presets (áp dụng toàn bộ danh sách quặng)
        if ((activeTab >= 0 && activeTab <= 3) && searchQuery.isEmpty()) {
            int btnW = (contentW - 3 * 4) / 4;
            for (int q = 0; q < 4; q++) {
                int qx = contentX + q * (btnW + 4);
                if (mouseX >= qx && mouseX <= qx + btnW && mouseY >= l.quickY && mouseY <= l.quickY + l.quickH) {
                    applyQuickPreset(activeTab, q);
                    AutoMineConfig.save();
                    playClickSound();
                    return true;
                }
            }
        }

        // 4. Kiểm tra Click vào Module Cards hoặc Tab 5 Dashboard
        if (mouseY >= l.contentY && mouseY <= l.contentBottom) {
            if (activeTab == 5 && searchQuery.isEmpty()) {
                boolean isCompact = contentW < 540;
                int cardH = 96;
                int card3Y = isCompact ? (l.contentY + cardH + 6 + cardH + 6) : (l.contentY + cardH + 6);
                card3Y -= scrollOffset;
                int card3W = contentW;
                int card3H = 92;

                // Kiểm tra click trong Card 3 (Discord Webhook)
                if (mouseX >= contentX && mouseX <= contentX + card3W && mouseY >= card3Y && mouseY <= card3Y + card3H) {
                    int toggleW = 46;
                    int toggleH = 14;
                    int toggleX = contentX + card3W - toggleW - 8;
                    int toggleY = card3Y + 4;

                    // Toggle Bật/Tắt
                    if (mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= toggleY && mouseY <= toggleY + toggleH) {
                        boolean newVal = !Baritone.settings().discordWebhookEnabled.value;
                        Baritone.settings().discordWebhookEnabled.value = newVal;
                        AutoMineConfig.save();
                        if (newVal) {
                            Helper.HELPER.logDirect("§a[Discord] ✔ Đã BẬT gửi báo cáo kết quả farm lên Discord Webhook!");
                        } else {
                            Helper.HELPER.logDirect("§c[Discord] ✖ Đã TẮT gửi Discord Webhook!");
                        }
                        return true;
                    }

                    // Nút chu kỳ (Interval: 1p, 3p, 5p, 10p, 15p, 30p)
                    int intvW = 76;
                    int intvH = 14;
                    int intvX = toggleX - intvW - 6;
                    int intvY = toggleY;
                    if (mouseX >= intvX && mouseX <= intvX + intvW && mouseY >= intvY && mouseY <= intvY + intvH) {
                        int cur = Baritone.settings().discordWebhookInterval.value;
                        int next;
                        if (cur < 60) next = 60;
                        else if (cur < 180) next = 180;
                        else if (cur < 300) next = 300;
                        else if (cur < 600) next = 600;
                        else if (cur < 900) next = 900;
                        else if (cur < 1800) next = 1800;
                        else next = 60;
                        Baritone.settings().discordWebhookInterval.value = next;
                        AutoMineConfig.save();
                        Helper.HELPER.logDirect("§b[Discord] Đã đổi chu kỳ gửi báo cáo farm thành " + (next >= 60 ? (next / 60) + " phút." : next + " giây."));
                        return true;
                    }

                    // Nút Chụp ảnh màn hình (Capture Screen)
                    int capW = 82;
                    int capH = 14;
                    int capX = intvX - capW - 6;
                    int capY = toggleY;
                    if (mouseX >= capX && mouseX <= capX + capW && mouseY >= capY && mouseY <= capY + capH) {
                        boolean newVal = !Baritone.settings().discordCaptureScreen.value;
                        Baritone.settings().discordCaptureScreen.value = newVal;
                        AutoMineConfig.save();
                        Helper.HELPER.logDirect("§a[Discord] " + (newVal ? "✔ Đã BẬT chụp ảnh màn hình đính kèm Discord!" : "✖ Đã TẮT chụp ảnh màn hình (chỉ gửi tin nhắn chữ)!"));
                        return true;
                    }

                    // Row 2: Input & Buttons
                    int row2Y = card3Y + 22;
                    int row2H = 20;
                    int btnPasteW = 86;
                    int btnTestW = 68;
                    int btnClearW = 36;
                    int rightButtonsW = btnPasteW + btnTestW + btnClearW + 10;
                    int inputX = contentX + 8;
                    int inputW = card3W - 16 - rightButtonsW;

                    // Click ô nhập URL
                    if (mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= row2Y && mouseY <= row2Y + row2H) {
                        webhookInputFocused = true;
                        searchFocused = false;
                        return true;
                    }

                    // Nút Dán [📋 DÁN]
                    int btnPasteX = inputX + inputW + 6;
                    if (mouseX >= btnPasteX && mouseX <= btnPasteX + btnPasteW && mouseY >= row2Y && mouseY <= row2Y + row2H) {
                        pasteWebhookFromClipboard();
                        return true;
                    }

                    // Nút Gửi thử [🚀 GỬI THỬ]
                    int btnTestX = btnPasteX + btnPasteW + 4;
                    if (mouseX >= btnTestX && mouseX <= btnTestX + btnTestW && mouseY >= row2Y && mouseY <= row2Y + row2H) {
                        testDiscordWebhook();
                        return true;
                    }

                    // Nút Xóa [✕]
                    int btnClearX = btnTestX + btnTestW + 4;
                    if (mouseX >= btnClearX && mouseX <= btnClearX + btnClearW && mouseY >= row2Y && mouseY <= row2Y + row2H) {
                        Baritone.settings().discordWebhookUrl.value = "";
                        AutoMineConfig.save();
                        Helper.HELPER.logDirect("§e[Discord] Đã xóa Discord Webhook URL.");
                        return true;
                    }
                }
                webhookInputFocused = false;
            } else {
                if (handleModuleListClick(mouseX, mouseY, button, contentX, l.contentY, contentW, l.contentBottom)) {
                    return true;
                }
            }
        }

        // 5. Kiểm tra Click vào Bottom Action Bar
        for (int a = 0; a < 5; a++) {
            int ax = l.panelX + a * (l.actionW + l.actionGap);
            if (mouseX >= ax && mouseX <= ax + l.actionW && mouseY >= l.actionBottomY && mouseY <= l.actionBottomY + l.actionH) {
                if (a == 0) {
                    playClickSound();
                    startAutoMine();
                    this.onClose();
                } else if (a == 1) {
                    playClickSound();
                    startAutoChop();
                    this.onClose();
                } else if (a == 2) {
                    playClickSound();
                    stopAutoMine();
                    this.onClose();
                } else if (a == 3) {
                    MiningStatsTracker.getInstance().reset();
                    Helper.HELPER.logDirect("§a[Tr0ngX] Đã reset toàn bộ thống kê đào khoáng!");
                    playClickSound();
                } else {
                    this.onClose();
                }
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Click danh sách dropdown. Thân row và mũi tên chỉ expand/collapse,
     * pill switch mới toggle — một click không bao giờ làm cả hai.
     * Chuột phải lên row cũng chỉ expand. Phần ngoài scissor không nhận click.
     */
    private boolean handleModuleListClick(double mouseX, double mouseY, int button,
                                          int contentX, int contentY, int contentW, int contentBottom) {
        if (mouseY < contentY || mouseY > contentBottom) {
            return false;
        }
        List<ModuleItem> filtered = getFilteredModules();
        Map<String, ModuleItem> map = buildKeyMap(filtered);
        List<ModuleRowList.Entry> entries = layoutModuleEntries(filtered, contentX, contentY, contentW);

        // Nút trong body dropdown (kill ESP, stepper, xray, webhook) kiểm tra trước.
        for (ModuleRowList.Entry entry : entries) {
            if (entry.isHeader || !entry.expanded) {
                continue;
            }
            if (mouseY < entry.y + ModuleRowList.ROW_H || mouseY > entry.y + entry.totalH()) {
                continue;
            }
            if (mouseX < entry.x || mouseX > entry.x + entry.w) {
                continue;
            }
            if (entry.key.equals(KEY_ESP)) {
                int[] kill = espKillRect(entry);
                if (inside(mouseX, mouseY, kill) && espEnabled()) {
                    OreEspController controller = espController();
                    if (controller != null) {
                        controller.setEnabled(false);
                    } else {
                        Baritone.settings().oreEspEnabled.value = false;
                    }
                    AutoMineConfig.save();
                    playClickSound();
                    return true;
                }
                int[] minus = espMinusRect(entry);
                int[] plus = espPlusRect(entry);
                if (inside(mouseX, mouseY, minus) || inside(mouseX, mouseY, plus)) {
                    int radius = OreEspController.clampRadius(Baritone.settings().oreEspRadius.value);
                    if (inside(mouseX, mouseY, minus)) {
                        radius -= 8;
                    } else {
                        radius += 8;
                    }
                    Baritone.settings().oreEspRadius.value = OreEspController.clampRadius(radius);
                    AutoMineConfig.save();
                    playClickSound();
                    return true;
                }
                int[] xray = espXraySwitchRect(entry);
                if (inside(mouseX, mouseY, xray)) {
                    Baritone.settings().oreEspXray.value = !Baritone.settings().oreEspXray.value;
                    AutoMineConfig.save();
                    playClickSound();
                    return true;
                }
                return true; // Click trong body ESP nhưng không trúng nút: nuốt sự kiện.
            }
            if (!entry.key.equals(KEY_ESP) && !entry.key.equals(KEY_TARGET_Y)
                    && !entry.key.equals(KEY_FPS) && !entry.key.equals(KEY_HUD_EDIT)
                    && !entry.key.equals(KEY_HUD_RESET)) {
                ModuleItem item = map.get(entry.key);
                if (item != null && isWebhookModule(item)) {
                    int textW = Math.max(40, contentW - 16);
                    int[] btn = webhookConfigButtonRect(entry, item, textW);
                    if (inside(mouseX, mouseY, btn)) {
                        activeTab = 5;
                        webhookInputFocused = true;
                        searchFocused = false;
                        scrollOffset = 0;
                        playClickSound();
                        return true;
                    }
                    return true;
                }
            }
        }

        ModuleRowList.Hit hit = ModuleRowList.hitTest(entries, mouseX, mouseY);
        if (hit == null) {
            return false;
        }
        ModuleRowList.Entry entry = hit.entry;
        if (entry.key.equals(KEY_ESP)) {
            if (hit.zone == ModuleRowList.Zone.SWITCH) {
                OreEspController controller = espController();
                boolean target = !espEnabled();
                boolean applied;
                if (controller != null) {
                    applied = controller.setEnabled(target);
                } else {
                    Baritone.settings().oreEspEnabled.value = target;
                    applied = target;
                }
                AutoMineConfig.save();
                if (applied == target) {
                    playClickSound();
                    if (target && Baritone.settings().heavyFarmMode.value) {
                        Helper.HELPER.logDirect("§e[ESP] Chế độ farm nặng đang bật, không thể bật ESP.");
                    }
                }
                return true;
            }
            toggleExpanded(entry.key);
            return true;
        }
        if (entry.key.equals(KEY_TARGET_Y)) {
            cycleTargetY();
            return true;
        }
        if (entry.key.equals(KEY_FPS)) {
            cycleFps();
            return true;
        }
        if (entry.key.equals(KEY_HUD_EDIT)) {
            playClickSound();
            openHudEditor();
            return true;
        }
        if (entry.key.equals(KEY_HUD_RESET)) {
            OreHudOverlay.getInstance().getConfig().resetPosition();
            playClickSound();
            return true;
        }
        ModuleItem item = map.get(entry.key);
        if (item == null) {
            return false;
        }
        if (hit.zone == ModuleRowList.Zone.SWITCH) {
            try {
                item.toggle.run();
            } catch (Throwable ignored) {}
            AutoMineConfig.save();
            playClickSound();
            return true;
        }
        // ARROW và BODY (trái hay phải chuột) đều chỉ expand/collapse.
        toggleExpanded(entry.key);
        return true;
    }

    private static boolean inside(double mouseX, double mouseY, int[] rect) {
        return mouseX >= rect[0] && mouseX <= rect[0] + rect[2] && mouseY >= rect[1] && mouseY <= rect[1] + rect[3];
    }

    private void toggleExpanded(String key) {
        if (expandedModules.contains(key)) {
            expandedModules.remove(key);
        } else {
            expandedModules.add(key);
        }
        AutoMineConfig.save();
        playClickSound();
    }

    private void cycleTargetY() {
        if (optTargetY == -54) {
            optTargetY = -58;
        } else if (optTargetY == -58) {
            optTargetY = 11;
        } else if (optTargetY == 11) {
            optTargetY = 999;
        } else {
            optTargetY = -54;
        }
        AutoMineConfig.save();
        playClickSound();
    }

    private void cycleFps() {
        try {
            int cur = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
            int nextIndex = 0;
            for (int f = 0; f < FPS_LEVELS.length; f++) {
                if (FPS_LEVELS[f] == cur) {
                    nextIndex = (f + 1) % FPS_LEVELS.length;
                    break;
                }
            }
            baritone.getPlayerContext().minecraft().options.framerateLimit().set(FPS_LEVELS[nextIndex]);
            playClickSound();
        } catch (Throwable ignored) {}
    }

    private List<ModuleItem> getFilteredModules() {
        List<ModuleItem> result = new ArrayList<>();
        String query = searchQuery.trim().toLowerCase();

        for (ModuleItem item : allModules) {
            boolean match = false;
            if (!query.isEmpty()) {
                match = item.name.toLowerCase().contains(query) || item.desc.toLowerCase().contains(query);
            } else {
                if (activeTab == 0 && item.category.equals("ORES")) match = true;
                else if (activeTab == 1 && item.category.equals("TREES")) match = true;
                else if (activeTab == 2 && item.category.equals("MOVEMENT")) match = true;
                else if (activeTab == 3 && item.category.equals("SURVIVAL")) match = true;
                else if (activeTab == 4 && item.category.equals("HUD")) match = true;
            }
            if (!match) continue;

            // Áp dụng bộ lọc trạng thái (0: Tất Cả, 1: Đang Bật, 2: Đang Tắt)
            if (filterMode == 1 && !item.getter.getAsBoolean()) continue;
            if (filterMode == 2 && item.getter.getAsBoolean()) continue;

            result.add(item);
        }
        return result;
    }

    // === DROPDOWN LB: key, sections, layout dùng chung cho render + click ===

    private static String moduleKey(ModuleItem item) {
        return item.category + " " + item.name;
    }

    private Map<String, ModuleItem> buildKeyMap(List<ModuleItem> filtered) {
        Map<String, ModuleItem> map = new LinkedHashMap<>();
        for (ModuleItem item : filtered) {
            map.put(moduleKey(item), item);
        }
        return map;
    }

    private boolean isPreciousOre(ModuleItem item) {
        return item.category.equals("ORES")
                && (item.name.equals("Kim Cương") || item.name.equals("Lục Bảo") || item.name.equals("Mảnh Vỡ Cổ Đại"));
    }

    private boolean isWebhookModule(ModuleItem item) {
        return item.name.equals("Gửi Discord Webhook");
    }

    /**
     * Dựng sections hiển thị. Tab Quặng có thêm hàng ESP + 2 nhóm quặng.
     * Tab Giao Diện có thêm hàng Tầng Y / FPS / Chỉnh HUD ở cuối.
     */
    private List<ModuleRowList.Section> buildSections(List<ModuleItem> filtered) {
        List<ModuleRowList.Section> sections = new ArrayList<>();
        String query = searchQuery.trim().toLowerCase();
        if (!query.isEmpty()) {
            List<String> keys = new ArrayList<>();
            if ("esp quặng (chỉ cache)".contains(query) || "esp".contains(query)) {
                keys.add(KEY_ESP);
            }
            if ("chỉnh hud kéo thả".contains(query) || "hud".contains(query)) {
                keys.add(KEY_HUD_EDIT);
            }
            if ("reset vị trí hud".contains(query)) {
                keys.add(KEY_HUD_RESET);
            }
            for (ModuleItem item : filtered) {
                keys.add(moduleKey(item));
            }
            sections.add(new ModuleRowList.Section(null, keys));
            return sections;
        }
        if (activeTab == 0) {
            List<String> esp = new ArrayList<>();
            esp.add(KEY_ESP);
            sections.add(new ModuleRowList.Section(null, esp));
            List<String> precious = new ArrayList<>();
            List<String> normal = new ArrayList<>();
            for (ModuleItem item : filtered) {
                if (isPreciousOre(item)) {
                    precious.add(moduleKey(item));
                } else {
                    normal.add(moduleKey(item));
                }
            }
            if (!precious.isEmpty()) {
                sections.add(new ModuleRowList.Section("QUẶNG QUÝ HIẾM", precious));
            }
            if (!normal.isEmpty()) {
                sections.add(new ModuleRowList.Section("QUẶNG THƯỜNG", normal));
            }
        } else if (activeTab == 4) {
            List<String> keys = new ArrayList<>();
            for (ModuleItem item : filtered) {
                keys.add(moduleKey(item));
            }
            sections.add(new ModuleRowList.Section(null, keys));
            List<String> extra = new ArrayList<>();
            extra.add(KEY_TARGET_Y);
            extra.add(KEY_FPS);
            extra.add(KEY_HUD_EDIT);
            extra.add(KEY_HUD_RESET);
            sections.add(new ModuleRowList.Section("HIỂN THỊ & HIỆU NĂNG", extra));
        } else {
            List<String> keys = new ArrayList<>();
            for (ModuleItem item : filtered) {
                keys.add(moduleKey(item));
            }
            sections.add(new ModuleRowList.Section(null, keys));
        }
        return sections;
    }

    private static final int ESP_BODY_H = 90;

    /**
     * Chiều cao dropdown body, phải khớp tuyệt đối với nội dung render.
     */
    private int bodyHeightFor(String key, ModuleItem item, int textW) {
        if (key.equals(KEY_ESP)) {
            return ESP_BODY_H;
        }
        if (key.equals(KEY_TARGET_Y) || key.equals(KEY_FPS) || key.equals(KEY_HUD_EDIT)
                || key.equals(KEY_HUD_RESET)) {
            return 0;
        }
        if (item == null) {
            return 0;
        }
        int lines = wrapText(item.details, Math.max(40, textW)).size();
        int height = 6 + lines * 12 + 8 + 12 + 6;
        if (isWebhookModule(item)) {
            height += 24;
        }
        return height;
    }

    private List<ModuleRowList.Entry> layoutModuleEntries(List<ModuleItem> filtered, int contentX, int contentY, int contentW) {
        List<ModuleRowList.Section> sections = buildSections(filtered);
        int textW = Math.max(40, contentW - 16);
        Map<String, ModuleItem> map = buildKeyMap(filtered);
        return ModuleRowList.layout(sections, expandedModules,
                key -> bodyHeightFor(key, map.get(key), textW), contentX, contentY - scrollOffset, contentW);
    }

    // === Rect nút trong body ESP, dùng chung cho render (hover) và click ===
    private int[] espKillRect(ModuleRowList.Entry entry) {
        return new int[]{entry.x + 8, entry.y + ModuleRowList.ROW_H + 6, entry.w - 16, 18};
    }

    private int[] espMinusRect(ModuleRowList.Entry entry) {
        int rowY = entry.y + ModuleRowList.ROW_H + 30;
        int plusX = entry.x + entry.w - 8 - 22;
        int valX = plusX - 34;
        return new int[]{valX - 26, rowY, 22, 16};
    }

    private int[] espValueRect(ModuleRowList.Entry entry) {
        int rowY = entry.y + ModuleRowList.ROW_H + 30;
        int plusX = entry.x + entry.w - 8 - 22;
        return new int[]{plusX - 34, rowY, 30, 16};
    }

    private int[] espPlusRect(ModuleRowList.Entry entry) {
        int rowY = entry.y + ModuleRowList.ROW_H + 30;
        int plusX = entry.x + entry.w - 8 - 22;
        return new int[]{plusX, rowY, 22, 16};
    }

    private int[] espXraySwitchRect(ModuleRowList.Entry entry) {
        int rowY = entry.y + ModuleRowList.ROW_H + 52;
        return new int[]{entry.x + entry.w - 8 - 32, rowY, 32, 16};
    }

    private int[] webhookConfigButtonRect(ModuleRowList.Entry entry, ModuleItem item, int textW) {
        int lines = wrapText(item.details, Math.max(40, textW)).size();
        int btnY = entry.y + ModuleRowList.ROW_H + 6 + lines * 12 + 8 + 12 + 6;
        return new int[]{entry.x + 8, btnY, entry.w - 16, 18};
    }

    private boolean hoveredEspRow = false;
    private int hoveredEspButton = 0; // 0: không, 1: kill, 2: minus, 3: plus, 4: xray
    private String hoveredSpecialKey = null;
    private boolean hoveredWebhookButton = false;
    private boolean hoveredYSetting = false;
    private boolean hoveredFpsSetting = false;

    /**
     * Vẽ danh sách dropdown. Trả về module đang hover để hiện tooltip.
     */
    private ModuleItem renderModuleList(GuiGraphics graphics, int contentX, int contentY, int contentW, int contentBottom, int mouseX, int mouseY) {
        hoveredEspRow = false;
        hoveredEspButton = 0;
        hoveredSpecialKey = null;
        hoveredWebhookButton = false;

        List<ModuleItem> filtered = getFilteredModules();
        Map<String, ModuleItem> map = buildKeyMap(filtered);
        List<ModuleRowList.Entry> entries = layoutModuleEntries(filtered, contentX, contentY, contentW);
        maxScroll = Math.max(0, ModuleRowList.totalHeight(entries) - (contentBottom - contentY));
        int clamped = Math.max(0, Math.min(scrollOffset, maxScroll));
        if (clamped != scrollOffset) {
            scrollOffset = clamped;
            entries = layoutModuleEntries(filtered, contentX, contentY, contentW);
        }

        ModuleItem hovered = null;
        int textW = Math.max(40, contentW - 16);
        for (ModuleRowList.Entry entry : entries) {
            if (entry.y + entry.totalH() < contentY || entry.y > contentBottom) {
                continue;
            }
            if (entry.isHeader) {
                ClickGuiTheme.drawGroupHeader(graphics, this.font, entry.groupTitle, entry.x, entry.y, entry.w, ClickGuiTheme.ACCENT_CYAN);
                continue;
            }
            boolean inBounds = mouseY >= contentY && mouseY <= contentBottom;
            boolean rowHover = inBounds && mouseX >= entry.x && mouseX <= entry.x + entry.w
                    && mouseY >= entry.y && mouseY <= entry.y + ModuleRowList.ROW_H;
            if (entry.key.equals(KEY_ESP)) {
                renderEspRow(graphics, entry, rowHover, mouseX, mouseY, inBounds);
                if (rowHover) {
                    hoveredEspRow = true;
                }
            } else if (entry.key.equals(KEY_TARGET_Y) || entry.key.equals(KEY_FPS)
                    || entry.key.equals(KEY_HUD_EDIT) || entry.key.equals(KEY_HUD_RESET)) {
                renderSpecialRow(graphics, entry, rowHover);
                if (rowHover) {
                    hoveredSpecialKey = entry.key;
                }
            } else {
                ModuleItem item = map.get(entry.key);
                if (item == null) {
                    continue;
                }
                renderModuleRow(graphics, entry, item, rowHover, mouseX, mouseY, inBounds, textW);
                if (rowHover) {
                    hovered = item;
                }
            }
        }
        hoveredYSetting = KEY_TARGET_Y.equals(hoveredSpecialKey);
        hoveredFpsSetting = KEY_FPS.equals(hoveredSpecialKey);
        return hovered;
    }

    private void renderModuleRow(GuiGraphics graphics, ModuleRowList.Entry entry, ModuleItem item,
                                 boolean rowHover, int mouseX, int mouseY, boolean inBounds, int textW) {
        boolean active;
        try {
            active = item.getter.getAsBoolean();
        } catch (Throwable ignored) {
            active = false;
        }
        ClickGuiTheme.drawModuleRow(graphics, entry.x, entry.y, entry.w, ModuleRowList.ROW_H,
                item.color, active, rowHover, entry.expanded);

        boolean arrowHover = inBounds && rowHover
                && mouseX >= entry.arrowX && mouseX <= entry.arrowX + ModuleRowList.ARROW_W;
        ClickGuiTheme.drawExpandArrow(graphics, this.font, entry.arrowX + 5,
                entry.y + (ModuleRowList.ROW_H - 8) / 2, entry.expanded, arrowHover, item.color);

        // Icon item thật sau mũi tên.
        boolean hasIcon = item.iconItem != null && !item.iconItem.isEmpty();
        int iconX = entry.x + ModuleRowList.ARROW_W + 4;
        if (hasIcon) {
            int iconY = entry.y + (ModuleRowList.ROW_H - 16) / 2;
            int iconBg = active ? ((item.color & 0x00FFFFFF) | 0x2A000000) : 0x14FFFFFF;
            int iconBorder = active ? ((item.color & 0x00FFFFFF) | 0x60000000) : 0x20FFFFFF;
            graphics.fill(iconX, iconY - 2, iconX + 18, iconY + 18, iconBg);
            ClickGuiTheme.drawOutline(graphics, iconX, iconY - 2, 18, 20, iconBorder);
            graphics.renderFakeItem(item.iconItem, iconX + 1, iconY);
        }

        ClickGuiTheme.drawPillSwitch(graphics, this.font, entry.switchX, entry.switchY,
                ModuleRowList.SWITCH_W, ModuleRowList.SWITCH_H, active, rowHover);

        int textStartX = hasIcon ? iconX + 22 : entry.x + ModuleRowList.ARROW_W + 6;
        int textMaxW = Math.max(20, entry.switchX - textStartX - 4);
        String name = item.name;
        if (this.font.width(name) > textMaxW) {
            name = this.font.plainSubstrByWidth(name, Math.max(10, textMaxW - 6)) + "..";
        }
        int titleColor = active ? ClickGuiTheme.TEXT_TITLE : (rowHover ? ClickGuiTheme.TEXT_BODY : ClickGuiTheme.TEXT_MUTED);
        ClickGuiTheme.drawText(graphics, this.font, name, textStartX, entry.y + 4, titleColor, active);

        String desc = item.desc;
        if (this.font.width(desc) > textMaxW) {
            desc = this.font.plainSubstrByWidth(desc, Math.max(10, textMaxW - 6)) + "..";
        }
        ClickGuiTheme.drawText(graphics, this.font, desc, textStartX, entry.y + 15,
                rowHover ? ClickGuiTheme.TEXT_MUTED : ClickGuiTheme.TEXT_DIM, false);

        if (entry.expanded) {
            renderModuleBody(graphics, entry, item, textW, mouseX, mouseY, inBounds);
        }
    }

    private void renderModuleBody(GuiGraphics graphics, ModuleRowList.Entry entry, ModuleItem item,
                                  int textW, int mouseX, int mouseY, boolean inBounds) {
        int bodyY = entry.y + ModuleRowList.ROW_H;
        ClickGuiTheme.drawDropdownBody(graphics, entry.x, bodyY, entry.w, entry.bodyH, item.color);
        int tx = entry.x + 8;
        int lineW = Math.max(40, textW);
        List<String> lines = wrapText(item.details, lineW);
        int curY = bodyY + 6;
        for (String line : lines) {
            ClickGuiTheme.drawText(graphics, this.font, line, tx, curY, 0xFFCBD5E1, false);
            curY += 12;
        }
        curY += 8;
        String hint = ORE_Y_HINT.get(item.name);
        if (hint == null) {
            hint = "Thân row: đóng/mở • Pill switch: BẬT/TẮT";
        }
        if (this.font.width(hint) > lineW) {
            hint = this.font.plainSubstrByWidth(hint, Math.max(10, lineW - 6)) + "..";
        }
        ClickGuiTheme.drawText(graphics, this.font, hint, tx, curY, (item.color & 0x00FFFFFF) | 0xE0000000, false);
        curY += 12 + 6;

        if (isWebhookModule(item)) {
            int[] btn = webhookConfigButtonRect(entry, item, textW);
            boolean btnHover = inBounds && mouseX >= btn[0] && mouseX <= btn[0] + btn[2]
                    && mouseY >= btn[1] && mouseY <= btn[1] + btn[3];
            if (btnHover) {
                hoveredWebhookButton = true;
            }
            ClickGuiTheme.drawActionButton(graphics, this.font, ItemStack.EMPTY, "Mở cài đặt webhook",
                    btn[0], btn[1], btn[2], btn[3], 0xFF5865F2, btnHover);
        }
    }

    private void renderEspRow(GuiGraphics graphics, ModuleRowList.Entry entry, boolean rowHover,
                              int mouseX, int mouseY, boolean inBounds) {
        boolean on = espEnabled();
        int accent = ClickGuiTheme.ACCENT_CYAN;
        ClickGuiTheme.drawModuleRow(graphics, entry.x, entry.y, entry.w, ModuleRowList.ROW_H,
                accent, on, rowHover, entry.expanded);

        boolean arrowHover = inBounds && rowHover
                && mouseX >= entry.arrowX && mouseX <= entry.arrowX + ModuleRowList.ARROW_W;
        ClickGuiTheme.drawExpandArrow(graphics, this.font, entry.arrowX + 5,
                entry.y + (ModuleRowList.ROW_H - 8) / 2, entry.expanded, arrowHover, accent);

        int iconX = entry.x + ModuleRowList.ARROW_W + 4;
        int iconY = entry.y + (ModuleRowList.ROW_H - 16) / 2;
        graphics.fill(iconX, iconY - 2, iconX + 18, iconY + 18, on ? 0x2A38BDF8 : 0x14FFFFFF);
        ClickGuiTheme.drawOutline(graphics, iconX, iconY - 2, 18, 20, on ? 0x6038BDF8 : 0x20FFFFFF);
        graphics.renderFakeItem(new ItemStack(Items.ENDER_EYE), iconX + 1, iconY);

        ClickGuiTheme.drawPillSwitch(graphics, this.font, entry.switchX, entry.switchY,
                ModuleRowList.SWITCH_W, ModuleRowList.SWITCH_H, on, rowHover);

        int textStartX = iconX + 22;
        int textMaxW = Math.max(20, entry.switchX - textStartX - 4);
        String name = "ESP Quặng (chỉ cache)";
        if (this.font.width(name) > textMaxW) {
            name = this.font.plainSubstrByWidth(name, Math.max(10, textMaxW - 6)) + "..";
        }
        int titleColor = on ? ClickGuiTheme.TEXT_TITLE : (rowHover ? ClickGuiTheme.TEXT_BODY : ClickGuiTheme.TEXT_MUTED);
        ClickGuiTheme.drawText(graphics, this.font, name, textStartX, entry.y + 4, titleColor, on);

        int radius = OreEspController.clampRadius(Baritone.settings().oreEspRadius.value);
        String desc = on ? ("Đang bật • bán kính " + radius + " • tối đa 128 box") : "Đang tắt (mặc định)";
        if (this.font.width(desc) > textMaxW) {
            desc = this.font.plainSubstrByWidth(desc, Math.max(10, textMaxW - 6)) + "..";
        }
        ClickGuiTheme.drawText(graphics, this.font, desc, textStartX, entry.y + 15,
                rowHover ? ClickGuiTheme.TEXT_MUTED : ClickGuiTheme.TEXT_DIM, false);

        if (entry.expanded) {
            renderEspBody(graphics, entry, on, mouseX, mouseY, inBounds);
        }
    }

    private void renderEspBody(GuiGraphics graphics, ModuleRowList.Entry entry, boolean on,
                               int mouseX, int mouseY, boolean inBounds) {
        int bodyY = entry.y + ModuleRowList.ROW_H;
        ClickGuiTheme.drawDropdownBody(graphics, entry.x, bodyY, entry.w, entry.bodyH, ClickGuiTheme.ACCENT_CYAN);

        int[] kill = espKillRect(entry);
        boolean killHover = inBounds && mouseX >= kill[0] && mouseX <= kill[0] + kill[2]
                && mouseY >= kill[1] && mouseY <= kill[1] + kill[3];
        if (killHover) {
            hoveredEspButton = 1;
        }
        ClickGuiTheme.drawEspKillButton(graphics, this.font,
                on ? "TẮT ESP NGAY" : "ESP ĐANG TẮT",
                kill[0], kill[1], kill[2], kill[3], on, killHover);

        int rowY = bodyY + 30;
        int radius = OreEspController.clampRadius(Baritone.settings().oreEspRadius.value);
        ClickGuiTheme.drawText(graphics, this.font, "Bán kính quét", entry.x + 8, rowY + 4, 0xFFE2E8F0, false);
        int[] minus = espMinusRect(entry);
        int[] value = espValueRect(entry);
        int[] plus = espPlusRect(entry);
        boolean minusHover = inBounds && mouseX >= minus[0] && mouseX <= minus[0] + minus[2]
                && mouseY >= minus[1] && mouseY <= minus[1] + minus[3];
        boolean plusHover = inBounds && mouseX >= plus[0] && mouseX <= plus[0] + plus[2]
                && mouseY >= plus[1] && mouseY <= plus[1] + plus[3];
        if (minusHover) {
            hoveredEspButton = 2;
        } else if (plusHover) {
            hoveredEspButton = 3;
        }
        drawStepperBox(graphics, minus[0], minus[1], minus[2], minus[3], "−", minusHover);
        String radiusText = radius + "";
        ClickGuiTheme.drawText(graphics, this.font, radiusText,
                value[0] + (value[2] - this.font.width(radiusText)) / 2, value[1] + 4, 0xFFFFFFFF, false);
        drawStepperBox(graphics, plus[0], plus[1], plus[2], plus[3], "+", plusHover);

        int xrayY = bodyY + 52;
        ClickGuiTheme.drawText(graphics, this.font, "Xuyên tường (chỉ nơi cho phép)",
                entry.x + 8, xrayY + 4, 0xFFE2E8F0, false);
        int[] xray = espXraySwitchRect(entry);
        boolean xrayOn = Baritone.settings().oreEspXray.value;
        boolean xrayHover = inBounds && mouseX >= xray[0] && mouseX <= xray[0] + xray[2]
                && mouseY >= xray[1] && mouseY <= xray[1] + xray[3];
        if (xrayHover) {
            hoveredEspButton = 4;
        }
        ClickGuiTheme.drawPillSwitch(graphics, this.font, xray[0], xray[1], xray[2], xray[3], xrayOn, xrayHover);

        String info = "Tối đa 128 box • chỉ cache • tự tắt khi farm lớn";
        int infoW = entry.w - 16;
        if (this.font.width(info) > infoW) {
            info = this.font.plainSubstrByWidth(info, Math.max(10, infoW - 6)) + "..";
        }
        ClickGuiTheme.drawText(graphics, this.font, info, entry.x + 8, bodyY + 72, ClickGuiTheme.TEXT_DIM, false);
    }

    private void drawStepperBox(GuiGraphics graphics, int x, int y, int w, int h, String text, boolean hover) {
        graphics.fill(x, y, x + w, y + h, hover ? 0x3538BDF8 : 0x2038BDF8);
        ClickGuiTheme.drawOutline(graphics, x, y, w, h, hover ? 0xA038BDF8 : 0x6038BDF8);
        ClickGuiTheme.drawText(graphics, this.font, text,
                x + (w - this.font.width(text)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, hover);
    }

    private void renderSpecialRow(GuiGraphics graphics, ModuleRowList.Entry entry, boolean rowHover) {
        int accent = ClickGuiTheme.ACCENT_CYAN;
        ItemStack icon = ItemStack.EMPTY;
        String title = "";
        String sub = "";
        String right = null;
        if (entry.key.equals(KEY_TARGET_Y)) {
            accent = ClickGuiTheme.ACCENT_CYAN;
            icon = new ItemStack(Items.COMPASS);
            title = "Tầng Y: " + (optTargetY == 999 ? "Hiện tại" : "Y=" + optTargetY);
            sub = "(-58, -54, 11, Hiện tại)";
        } else if (entry.key.equals(KEY_FPS)) {
            accent = ClickGuiTheme.ACCENT_EMERALD;
            icon = new ItemStack(Items.CLOCK);
            int curFpsLimit = 60;
            try {
                curFpsLimit = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
            } catch (Throwable ignored) {}
            title = "FPS Limit: " + (curFpsLimit >= 260 ? "Max" : curFpsLimit + " FPS");
            sub = "Click để đổi mức FPS";
        } else if (entry.key.equals(KEY_HUD_EDIT)) {
            accent = ClickGuiTheme.ACCENT_PURPLE;
            icon = new ItemStack(Items.ITEM_FRAME);
            title = "Chỉnh HUD (kéo-thả)";
            sub = "Mở trình chỉnh vị trí và tỉ lệ";
            right = "MỞ →";
        } else if (entry.key.equals(KEY_HUD_RESET)) {
            accent = ClickGuiTheme.ACCENT_AMBER;
            icon = new ItemStack(Items.RECOVERY_COMPASS);
            title = "Reset vị trí HUD";
            sub = "Chỉ reset vị trí, không xóa thống kê";
            right = "RESET";
        }
        ClickGuiTheme.drawDoubleBezelCard(graphics, entry.x, entry.y, entry.w, ModuleRowList.ROW_H, accent, false, rowHover);
        graphics.fill(entry.x + 1, entry.y + 2, entry.x + 3, entry.y + ModuleRowList.ROW_H - 2, accent);
        if (!icon.isEmpty()) {
            int iconY = entry.y + (ModuleRowList.ROW_H - 16) / 2;
            graphics.fill(entry.x + 6, iconY - 2, entry.x + 24, iconY + 18, rowHover ? 0x22FFFFFF : 0x14FFFFFF);
            ClickGuiTheme.drawOutline(graphics, entry.x + 6, iconY - 2, 18, 20, rowHover ? 0x40FFFFFF : 0x20FFFFFF);
            graphics.renderFakeItem(icon, entry.x + 7, iconY);
        }
        int textX = entry.x + 30;
        int textMaxW = entry.w - 60 - (right != null ? 40 : 0);
        if (this.font.width(title) > textMaxW) {
            title = this.font.plainSubstrByWidth(title, Math.max(10, textMaxW - 6)) + "..";
        }
        ClickGuiTheme.drawText(graphics, this.font, title, textX, entry.y + 3, ClickGuiTheme.TEXT_TITLE, true);
        ClickGuiTheme.drawText(graphics, this.font, sub, textX, entry.y + 14, ClickGuiTheme.TEXT_DIM, false);
        if (right != null) {
            ClickGuiTheme.drawText(graphics, this.font, right, entry.x + entry.w - 8 - this.font.width(right),
                    entry.y + 9, rowHover ? 0xFFFFFFFF : ClickGuiTheme.TEXT_MUTED, rowHover);
        }
    }

    private boolean espEnabled() {
        try {
            return Baritone.settings().oreEspEnabled.value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Nền tối mờ chuẩn LiquidBounce Nextgen Dark Glass
        graphics.fillGradient(0, 0, this.width, this.height, ClickGuiTheme.BG_SCREEN_TOP, ClickGuiTheme.BG_SCREEN_BOTTOM);

        ResponsiveLayout l = new ResponsiveLayout(this.width, this.height, activeTab, !searchQuery.isEmpty());

        ModuleItem hoveredItem = null;
        hoveredYSetting = false;
        hoveredFpsSetting = false;
        int hoveredActionIdx = -1;
        int hoveredQuickIdx = -1;
        int hoveredTabIdx = -1;
        boolean hoveredSearch = false;
        int hoveredFilterIdx = -1;
        int hoveredDiscordBtn = -1;

        // 1. Header Bar với Logo Neon, Live Status Badge & Version Tag
        ClickGuiTheme.drawGlowPanel(graphics, l.panelX, 3, l.panelW, this.height - 6, ClickGuiTheme.ACCENT_CYAN);
        graphics.fill(l.panelX, 3, l.panelX + l.panelW, 4, ClickGuiTheme.ACCENT_CYAN);
        ClickGuiTheme.drawText(graphics, this.font, "TR0NGX", l.panelX + 6, 7, ClickGuiTheme.ACCENT_CYAN, true);
        int logoW = this.font.width("TR0NGX");
        graphics.fill(l.panelX + 8 + logoW, 7, l.panelX + 9 + logoW, 15, 0x5064748B);
        ClickGuiTheme.drawText(graphics, this.font, "BẢNG ĐIỀU KHIỂN NEXTGEN", l.panelX + 13 + logoW, 7, ClickGuiTheme.TEXT_MUTED, false);

        boolean isMining = baritone.getMineProcess().isActive();
        boolean isChop = baritone.getMineProcess().isChopMode();
        String liveBadge = isMining ? (isChop ? "● ĐANG CHẶT GỖ" : "● ĐANG ĐÀO QUẶNG") : "● SẴN SÀNG";
        int liveColor = isMining ? (isChop ? ClickGuiTheme.ACCENT_EMERALD : ClickGuiTheme.ACCENT_CYAN) : 0xFF34D399;
        if (l.panelW >= 460) {
            int liveW = this.font.width(liveBadge) + 8;
            int liveX = l.panelX + l.panelW - liveW - (l.panelW >= 560 ? 110 : 6);
            graphics.fill(liveX, 5, liveX + liveW, 16, (liveColor & 0x00FFFFFF) | 0x25000000);
            ClickGuiTheme.drawOutline(graphics, liveX, 5, liveW, 11, (liveColor & 0x00FFFFFF) | 0x60000000);
            ClickGuiTheme.drawText(graphics, this.font, liveBadge, liveX + 4, 7, liveColor, true);
        }

        if (l.panelW >= 560) {
            String versionTag = "v1.21.11 STABLE";
            int vW = this.font.width(versionTag);
            ClickGuiTheme.drawText(graphics, this.font, versionTag, l.panelX + l.panelW - vW - 6, 7, ClickGuiTheme.TEXT_DIM, false);
        }

        // 2. Sidebar dọc kiểu LiquidBounce (fallback tab ngang khi GUI hẹp)
        SidebarLayout sb = new SidebarLayout(l.panelX, l.panelW, this.width);
        int contentX = sb.contentX;
        int contentW = sb.contentW;
        if (sb.useSidebar) {
            graphics.fill(l.panelX, l.tabY, l.panelX + SidebarLayout.SIDEBAR_W, l.tabY + sb.sidebarHeight(6), ClickGuiTheme.BG_SIDEBAR);
            ClickGuiTheme.drawOutline(graphics, l.panelX, l.tabY, SidebarLayout.SIDEBAR_W, sb.sidebarHeight(6), ClickGuiTheme.BORDER_CARD);
            for (int i = 0; i < 6; i++) {
                int ty = sb.sidebarButtonY(l.tabY, i);
                boolean isTabActive = (activeTab == i && searchQuery.isEmpty());
                boolean isTabHover = mouseX >= l.panelX && mouseX <= l.panelX + SidebarLayout.SIDEBAR_W
                        && mouseY >= ty && mouseY <= ty + SidebarLayout.SIDEBAR_BUTTON_H;
                if (isTabHover) {
                    hoveredTabIdx = i;
                }
                ClickGuiTheme.drawSidebarButton(graphics, this.font, TAB_ITEM_ICONS[i], TAB_FULL_NAMES[i],
                        l.panelX, ty, SidebarLayout.SIDEBAR_W, SidebarLayout.SIDEBAR_BUTTON_H,
                        isTabActive, isTabHover, ClickGuiTheme.ACCENT_CYAN);
            }
        } else {
            graphics.fill(l.panelX, l.tabY, l.panelX + l.panelW, l.tabY + l.tabH, ClickGuiTheme.BG_CARD);
            ClickGuiTheme.drawOutline(graphics, l.panelX, l.tabY, l.panelW, l.tabH, ClickGuiTheme.BORDER_CARD);
            for (int i = 0; i < 6; i++) {
                int tx = l.panelX + i * l.tabW;
                boolean isTabActive = (activeTab == i && searchQuery.isEmpty());
                boolean isTabHover = mouseX >= tx && mouseX <= tx + l.tabW && mouseY >= l.tabY && mouseY <= l.tabY + l.tabH;
                if (isTabHover) {
                    hoveredTabIdx = i;
                }
                String tabTitle = getResponsiveTabTitle(i, l.tabW);
                ClickGuiTheme.drawTab(graphics, this.font, TAB_ITEM_ICONS[i], tabTitle, tx, l.tabY, l.tabW, l.tabH, isTabActive, isTabHover, ClickGuiTheme.ACCENT_CYAN);
            }
        }

        // 3. Search Bar + 3 Filter Chips (nằm trong content phải của sidebar)
        int filterW = contentW >= 480 ? 156 : 0;
        int searchBoxW = filterW > 0 ? contentW - filterW - 6 : contentW;
        graphics.fill(contentX, l.searchY, contentX + searchBoxW, l.searchY + l.searchH, ClickGuiTheme.BG_INPUT);
        int searchBorder = searchFocused ? ClickGuiTheme.ACCENT_CYAN : ClickGuiTheme.BORDER_CARD;
        ClickGuiTheme.drawOutline(graphics, contentX, l.searchY, searchBoxW, l.searchH, searchBorder);

        boolean searchHover = mouseX >= contentX && mouseX <= contentX + searchBoxW && mouseY >= l.searchY && mouseY <= l.searchY + l.searchH;
        if (searchHover) {
            hoveredSearch = true;
        }

        String searchPrompt = searchQuery.isEmpty() ? (searchFocused ? "" : "⌕  Tìm kiếm tính năng, quặng...") : searchQuery;
        int searchColor = searchQuery.isEmpty() ? ClickGuiTheme.TEXT_DIM : ClickGuiTheme.TEXT_TITLE;
        int searchPromptY = l.searchY + (l.searchH - 8) / 2;
        ClickGuiTheme.drawText(graphics, this.font, searchPrompt, contentX + 6, searchPromptY, searchColor, false);
        if (searchFocused && (System.currentTimeMillis() / 400) % 2 == 0) {
            int cursorX = contentX + 6 + this.font.width(searchQuery);
            graphics.fill(cursorX, l.searchY + 3, cursorX + 1, l.searchY + l.searchH - 3, ClickGuiTheme.ACCENT_CYAN);
        }

        if (!searchQuery.isEmpty()) {
            int clearX = contentX + searchBoxW - 14;
            int clearY = l.searchY + (l.searchH - 10) / 2;
            boolean clearHover = mouseX >= clearX - 2 && mouseX <= clearX + 10 && mouseY >= clearY && mouseY <= clearY + 10;
            ClickGuiTheme.drawText(graphics, this.font, "×", clearX, clearY, clearHover ? 0xFFFFFFFF : ClickGuiTheme.TEXT_MUTED, clearHover);
        }

        if (filterW > 0) {
            int chipStart = contentX + searchBoxW + 6;
            int chipW = (filterW - 4) / 3;
            String[] filterLabels = new String[]{"Tất Cả", "Đang Bật", "Đang Tắt"};
            int[] filterAccents = new int[]{ClickGuiTheme.ACCENT_CYAN, ClickGuiTheme.ACCENT_EMERALD, ClickGuiTheme.ACCENT_ROSE};
            for (int c = 0; c < 3; c++) {
                int cx = chipStart + c * (chipW + 2);
                boolean cHover = mouseX >= cx && mouseX <= cx + chipW && mouseY >= l.searchY && mouseY <= l.searchY + l.searchH;
                if (cHover) {
                    hoveredFilterIdx = c;
                }
                boolean cActive = (filterMode == c);
                ClickGuiTheme.drawFilterChip(graphics, this.font, filterLabels[c], cx, l.searchY, chipW, l.searchH, cActive, cHover, filterAccents[c]);
            }
        }

        // 4. Quick Action Presets (Tab 0, 1, 2, 3) — áp dụng toàn bộ danh sách, không chỉ kết quả lọc
        if ((activeTab >= 0 && activeTab <= 3) && searchQuery.isEmpty()) {
            int btnW = (contentW - 3 * 4) / 4;
            int[] qColors = new int[]{ClickGuiTheme.ACCENT_CYAN, ClickGuiTheme.ACCENT_EMERALD, ClickGuiTheme.ACCENT_PURPLE, ClickGuiTheme.ACCENT_AMBER};

            for (int q = 0; q < 4; q++) {
                int qx = contentX + q * (btnW + 4);
                boolean qHover = mouseX >= qx && mouseX <= qx + btnW && mouseY >= l.quickY && mouseY <= l.quickY + l.quickH;
                if (qHover) {
                    hoveredQuickIdx = q;
                }
                String qTitle = getResponsiveQuickTitle(q, btnW, activeTab);
                ClickGuiTheme.drawActionButton(graphics, this.font, ItemStack.EMPTY, qTitle, qx, l.quickY, btnW, l.quickH, qColors[q], qHover);
            }
        }

        // 5. Danh sách module dropdown LB (Scissor Box an toàn)
        graphics.enableScissor(contentX - 1, l.contentY, contentX + contentW + 1, l.contentBottom);

        if (activeTab == 5 && searchQuery.isEmpty()) {
            boolean isCompact = contentW < 540;
            int totalTab5H = isCompact ? (96 + 6 + 96 + 6 + 94) : (96 + 6 + 94);
            maxScroll = Math.max(0, totalTab5H - (l.contentBottom - l.contentY));
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            hoveredDiscordBtn = renderTelemetryDashboard(graphics, contentX, l.contentY - scrollOffset, contentW, mouseX, mouseY);
        } else {
            ModuleItem hovered = renderModuleList(graphics, contentX, l.contentY, contentW, l.contentBottom, mouseX, mouseY);
            if (hovered != null) {
                hoveredItem = hovered;
            }
        }

        graphics.disableScissor();

        // 6. Thanh cuộn Scrollbar mỏng phản hồi thị giác
        if (maxScroll > 0) {
            int scrollTrackH = l.contentBottom - l.contentY;
            int scrollThumbH = Math.max(14, (int) (scrollTrackH * ((float) scrollTrackH / (scrollTrackH + maxScroll))));
            int scrollThumbY = l.contentY + (int) ((float) scrollOffset / maxScroll * (scrollTrackH - scrollThumbH));
            int scrollX = l.panelX + l.panelW - 2;
            graphics.fill(scrollX, l.contentY, scrollX + 2, l.contentBottom, 0x25FFFFFF);
            graphics.fill(scrollX, scrollThumbY, scrollX + 2, scrollThumbY + scrollThumbH, ClickGuiTheme.ACCENT_CYAN);
        }

        // 7. Thẻ thống kê bên cạnh chỉ hiển thị khi đã ấn Start và màn hình còn đủ chỗ trống
        if (activeTab != 4 && optMiningStats && isMining) {
            int statsW = 148;
            if (this.width - (l.panelX + l.panelW) >= statsW + 10) {
                MiningStatsTracker.getInstance().renderCard(graphics, this.font, l.panelX + l.panelW + 8, l.contentY, statsW, isMining);
            } else if (l.panelX >= statsW + 10) {
                MiningStatsTracker.getInstance().renderCard(graphics, this.font, l.panelX - statsW - 8, l.contentY, statsW, isMining);
            }
        }

        // 8. Floating Bottom Action Dock (100% Responsive Titles)
        int[] aColors = new int[]{
                ClickGuiTheme.ACCENT_CYAN,
                ClickGuiTheme.ACCENT_EMERALD,
                ClickGuiTheme.ACCENT_ROSE,
                ClickGuiTheme.ACCENT_AMBER,
                ClickGuiTheme.TEXT_MUTED
        };

        for (int a = 0; a < 5; a++) {
            int ax = l.panelX + a * (l.actionW + l.actionGap);
            boolean aHover = mouseX >= ax && mouseX <= ax + l.actionW && mouseY >= l.actionBottomY && mouseY <= l.actionBottomY + l.actionH;
            if (aHover) {
                hoveredActionIdx = a;
            }
            String aTitle = getResponsiveActionTitle(a, l.actionW);
            ClickGuiTheme.drawActionButton(graphics, this.font, ACTION_ITEM_ICONS[a], aTitle, ax, l.actionBottomY, l.actionW, l.actionH, aColors[a], aHover);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        // 9. Lớp hiển thị Tooltip giải thích tính năng khi di chuột (Topmost)
        if (hoveredDiscordBtn > 0) {
            switch (hoveredDiscordBtn) {
                case 1:
                    drawModernTooltip(graphics, "Dán Webhook URL", null, "Dán nhanh từ Clipboard",
                            "Tự động đọc đường dẫn Webhook URL vừa copy từ Discord trong bộ nhớ tạm (Clipboard) và lưu vào cấu hình.",
                            "§8[Chuột trái] §7Dán & Lưu ngay",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
                case 2:
                    drawModernTooltip(graphics, "Gửi Thử Nghiệm", null, "Kiểm tra kết nối Discord Webhook",
                            "Gửi ngay 1 tin nhắn Embed chứa tên tài khoản, thời gian hoạt động, số quặng và máu lên kênh Discord để xác nhận webhook hoạt động.",
                            "§8[Chuột trái] §7Gửi tin nhắn test ngay",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);
                    break;
                case 3:
                    drawModernTooltip(graphics, "Xóa Webhook URL", null, "Xóa đường dẫn đã lưu",
                            "Xóa trắng địa chỉ Webhook URL hiện tại để nhập hoặc dán địa chỉ mới.",
                            "§8[Chuột trái] §7Xóa URL",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_ROSE);
                    break;
                case 4:
                    drawModernTooltip(graphics, "Bật/Tắt Webhook", null, "Kích hoạt gửi báo cáo Discord",
                            "Cho phép hoặc tạm dừng việc tự động gửi báo cáo kết quả farm và cảnh báo khẩn cấp lên Discord.",
                            "§8[Chuột trái] §7Chuyển đổi Bật / Tắt",
                            mouseX, mouseY, 0xFF5865F2);
                    break;
                case 5:
                    drawModernTooltip(graphics, "Chu Kỳ Gửi Báo Cáo", null, "Tần suất gửi kết quả farm định kỳ",
                            "Nhấn chuột để chuyển đổi chu kỳ gửi báo cáo farm: 1p -> 3p -> 5p -> 10p -> 15p -> 30p một lần.",
                            "§8[Chuột trái] §7Đổi chu kỳ gửi",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
                case 6:
                    drawModernTooltip(graphics, "Ô Nhập Webhook URL", null, "Địa chỉ Discord Webhook",
                            "Nhấp chuột vào ô này để gõ hoặc dùng tổ hợp phím Ctrl+V để dán trực tiếp link Webhook của bạn.",
                            "§8[Chuột trái] §7Chọn để gõ / paste",
                            mouseX, mouseY, 0xFF5865F2);
                    break;
                case 7:
                    drawModernTooltip(graphics, "Ảnh Chụp Màn Hình", Baritone.settings().discordCaptureScreen.value, "Đính kèm ảnh vào Discord",
                            "Tự động chụp ảnh góc nhìn game hiện tại và đính kèm vào tin nhắn báo cáo kết quả farm định kỳ.",
                            "§8[Chuột trái] §7Bật / Tắt chụp ảnh",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
            }
        } else if (hoveredEspRow) {
            String espDesc = espEnabled() ? "Highlight quặng đang bật" : "Highlight quặng đang tắt (mặc định)";
            String espDetail = "Chỉ đọc cache client sẵn có, không live-scan. Nút TẮT ESP NGAY luôn đứng đầu dropdown. "
                    + "Bán kính 8–64 block, tối đa 128 box, tự tắt khi farm lớn.";
            String espFooter = hoveredEspButton == 1 ? "§8[Chuột trái] §7Tắt ESP ngay"
                    : (hoveredEspButton == 2 || hoveredEspButton == 3 ? "§8[Chuột trái] §7Đổi bán kính (bước 8)"
                    : (hoveredEspButton == 4 ? "§8[Chuột trái] §7Bật/tắt xuyên tường"
                    : "§8[Switch] §7Bật/tắt • §8[Thân row] §7Đóng/mở"));
            drawModernTooltip(graphics, "ESP Quặng (chỉ cache)", espEnabled(), espDesc, espDetail,
                    espFooter, mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
        } else if (hoveredWebhookButton) {
            drawModernTooltip(graphics, "Mở cài đặt webhook", null, "Sang tab Chỉ Số nhập URL",
                    "Mở tab Chỉ Số để dán Discord Webhook URL, gửi thử và đổi chu kỳ báo cáo.",
                    "§8[Chuột trái] §7Mở tab Chỉ Số",
                    mouseX, mouseY, 0xFF5865F2);
        } else if (KEY_HUD_RESET.equals(hoveredSpecialKey)) {
            drawModernTooltip(graphics, "Reset vị trí HUD", null, "Đưa HUD về góc mặc định",
                    "Chỉ reset vị trí về (8,8), không xóa số liệu thống kê. Tỉ lệ và trạng thái thu gọn được giữ nguyên.",
                    "§8[Chuột trái] §7Reset vị trí",
                    mouseX, mouseY, ClickGuiTheme.ACCENT_AMBER);
        } else if (KEY_HUD_EDIT.equals(hoveredSpecialKey)) {
            drawModernTooltip(graphics, "Chỉnh HUD (kéo-thả)", null, "Mở trình chỉnh vị trí và tỉ lệ",
                    "Kéo thanh tiêu đề HUD để di chuyển, đổi Nhỏ 0.8x / Vừa 1.0x / Lớn 1.25x, thu gọn hoặc reset vị trí. Chỉ lưu khi thả chuột hoặc đổi setting.",
                    "§8[Chuột trái] §7Mở trình chỉnh HUD",
                    mouseX, mouseY, ClickGuiTheme.ACCENT_PURPLE);
        } else if (hoveredItem != null) {
            drawModernTooltip(graphics,
                    hoveredItem.name,
                    hoveredItem.getter.getAsBoolean(),
                    hoveredItem.desc,
                    hoveredItem.details,
                    "§8[Switch] §7Bật/tắt • §8[Thân row] §7Đóng/mở chi tiết",
                    mouseX, mouseY, hoveredItem.color);
        } else if (hoveredYSetting) {
            drawModernTooltip(graphics,
                    "Tầng Đào Y Mục Tiêu",
                    null,
                    "Độ cao khai thác khoáng sản tối ưu",
                    "Nhấn chuột để đổi tầng Y mong muốn: Y=-58 (Nhiều Kim Cương nhất), Y=-54 (Tầng an toàn), Y=11 (Nether/Cũ), hoặc Tầng hiện tại.",
                    "§8[Chuột trái] §7Thay đổi tầng đào Y",
                    mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
        } else if (hoveredFpsSetting) {
            drawModernTooltip(graphics,
                    "Giới Hạn FPS",
                    null,
                    "Tối ưu hóa hiệu năng & nhiệt độ máy",
                    "Nhấn chuột để chuyển đổi giữa các mức FPS (30, 60, 120, 144, 240, Tối Đa) giúp máy chạy mát và tiết kiệm điện khi treo đào lâu.",
                    "§8[Chuột trái] §7Thay đổi mức FPS",
                    mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);
        } else if (hoveredActionIdx >= 0) {
            drawActionTooltip(graphics, hoveredActionIdx, mouseX, mouseY);
        } else if (hoveredQuickIdx >= 0) {
            drawQuickTooltip(graphics, hoveredQuickIdx, activeTab, mouseX, mouseY);
        } else if (hoveredTabIdx >= 0) {
            drawTabTooltip(graphics, hoveredTabIdx, mouseX, mouseY);
        } else if (hoveredFilterIdx >= 0) {
            String fTitle = hoveredFilterIdx == 0 ? "Bộ Lọc: Tất Cả" : (hoveredFilterIdx == 1 ? "Bộ Lọc: Đang Bật" : "Bộ Lọc: Đang Tắt");
            String fDesc = hoveredFilterIdx == 0 ? "Hiển thị toàn bộ module trong chuyên mục này" : (hoveredFilterIdx == 1 ? "Chỉ hiển thị các module đang được BẬT" : "Chỉ hiển thị các module đang TẮT");
            int fColor = hoveredFilterIdx == 0 ? ClickGuiTheme.ACCENT_CYAN : (hoveredFilterIdx == 1 ? ClickGuiTheme.ACCENT_EMERALD : ClickGuiTheme.ACCENT_ROSE);
            drawModernTooltip(graphics, fTitle, null, fDesc,
                    "Nhấn chuột để lọc nhanh các tính năng theo trạng thái Bật / Tắt, giúp quản lý cài đặt dễ dàng và trực quan.",
                    "§8[Chuột trái] §7Áp dụng bộ lọc này",
                    mouseX, mouseY, fColor);
        } else if (hoveredSearch && searchQuery.isEmpty()) {
            drawModernTooltip(graphics,
                    "Thanh Tìm Kiếm",
                    null,
                    "Lọc nhanh các tính năng và quặng",
                    "Nhập từ khóa để tìm kiếm tức thì theo tên hoặc mô tả của mọi tính năng trong Baritone.",
                    "§8[Bàn phím] §7Gõ để lọc tính năng",
                    mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
        }
    }

    private int renderTelemetryDashboard(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        float memPct = (float) usedMem / Math.max(1, maxMem);
        int cores = Runtime.getRuntime().availableProcessors();
        String gpu = GL11.glGetString(GL11.GL_RENDERER);
        if (gpu == null) gpu = "Dedicated GPU";
        if (gpu.length() > 24) gpu = gpu.substring(0, 24) + "..";

        int curFps = baritone.getPlayerContext().minecraft().getFps();
        int fpsColor = curFps >= 60 ? ClickGuiTheme.ACCENT_EMERALD : (curFps >= 30 ? ClickGuiTheme.ACCENT_AMBER : ClickGuiTheme.ACCENT_ROSE);

        boolean isCompact = w < 540;
        int colW = isCompact ? w : (w - 8) / 2;
        int cardH = 96;

        // Card 1: Hardware Specs (Double-Bezel Architecture)
        ClickGuiTheme.drawDoubleBezelCard(g, x, y, colW, cardH, ClickGuiTheme.ACCENT_CYAN, false, false);
        g.fill(x + 1, y + 2, x + 3, y + cardH - 2, ClickGuiTheme.ACCENT_CYAN);
        ClickGuiTheme.drawText(g, this.font, "PHẦN CỨNG & HIỆU NĂNG", x + 8, y + 6, ClickGuiTheme.ACCENT_CYAN, true);

        ClickGuiTheme.drawText(g, this.font, "FPS: " + curFps, x + 8, y + 22, fpsColor, true);
        ClickGuiTheme.drawText(g, this.font, "CPU: " + cores + " Cores", x + 8, y + 36, ClickGuiTheme.TEXT_BODY, false);
        ClickGuiTheme.drawText(g, this.font, "GPU: " + gpu, x + 8, y + 50, ClickGuiTheme.TEXT_MUTED, false);

        ClickGuiTheme.drawText(g, this.font, "RAM (" + (int) (memPct * 100) + "%): " + usedMem + "/" + maxMem + "MB", x + 8, y + 64, ClickGuiTheme.TEXT_BODY, false);
        ClickGuiTheme.drawProgressBar(g, x + 8, y + 78, colW - 16, 7, memPct, ClickGuiTheme.ACCENT_EMERALD, 0xFF1E293B);

        // Card 2: Mining Live Telemetry (Double-Bezel Architecture)
        int rx = isCompact ? x : (x + colW + 8);
        int ry = isCompact ? (y + cardH + 6) : y;

        ClickGuiTheme.drawDoubleBezelCard(g, rx, ry, colW, cardH, ClickGuiTheme.ACCENT_EMERALD, false, false);
        g.fill(rx + 1, ry + 2, rx + 3, ry + cardH - 2, ClickGuiTheme.ACCENT_EMERALD);
        ClickGuiTheme.drawText(g, this.font, "THỐNG KÊ PHIÊN ĐÀO", rx + 8, ry + 6, ClickGuiTheme.ACCENT_EMERALD, true);

        boolean isMining = baritone.getMineProcess().isActive();
        boolean isChop = baritone.getMineProcess().isChopMode();
        String statusStr = isMining ? (isChop ? "§a● ĐANG CHẶT CÂY" : "§a● ĐANG ĐÀO") : "§7○ NGHỈ";
        ClickGuiTheme.drawText(g, this.font, "Trạng thái: " + statusStr, rx + 8, ry + 22, ClickGuiTheme.TEXT_BODY, false);

        String duration = MiningStatsTracker.getInstance().getFormattedDuration();
        ClickGuiTheme.drawText(g, this.font, "Thời gian: " + duration, rx + 8, ry + 36, ClickGuiTheme.TEXT_BODY, false);

        int totalBlocks = MiningStatsTracker.getInstance().getTotalBlocksMined();
        int rate = MiningStatsTracker.getInstance().getBlocksPerHour();
        String blockLabel = isChop ? "Đã chặt: " : "Đã đào: ";
        String unit = isChop ? " log" : " block";
        ClickGuiTheme.drawText(g, this.font, blockLabel + String.format("%,d%s", totalBlocks, unit), rx + 8, ry + 50, ClickGuiTheme.ACCENT_AMBER, true);
        ClickGuiTheme.drawText(g, this.font, "Tốc độ: " + String.format("%,d%s/h", rate, unit), rx + 8, ry + 64, ClickGuiTheme.ACCENT_CYAN, false);

        if (isChop) {
            int totalWood = MiningStatsTracker.getInstance().getTotalWoodMined();
            ClickGuiTheme.drawText(g, this.font, "Gỗ khai thác: " + totalWood + " khúc", rx + 8, ry + 78, 0xFF34D399, true);
        } else {
            int totalDiamondGems = MiningStatsTracker.getInstance().getDiamondDropCount();
            int totalDiamondOres = MiningStatsTracker.getInstance().getOreCount(MiningStatsTracker.OreType.DIAMOND);
            String diamText = "Kim cương: " + totalDiamondGems + " cục (" + totalDiamondOres + " quặng)";
            ClickGuiTheme.drawText(g, this.font, diamText, rx + 8, ry + 78, ClickGuiTheme.ACCENT_CYAN, true);
        }

        // Card 3: Discord Webhook & Remote Control
        int card3Y = isCompact ? (ry + cardH + 6) : (y + cardH + 6);
        int card3W = w;
        int card3H = 92;
        int hoveredBtn = -1;

        boolean hookEnabled = Baritone.settings().discordWebhookEnabled.value;
        ClickGuiTheme.drawDoubleBezelCard(g, x, card3Y, card3W, card3H, 0xFF5865F2, hookEnabled, false);
        g.fill(x + 1, card3Y + 2, x + 3, card3Y + card3H - 2, 0xFF5865F2);
        ClickGuiTheme.drawText(g, this.font, "DISCORD WEBHOOK & BÁO CÁO KẾT QUẢ FARM", x + 8, card3Y + 6, 0xFF5865F2, true);

        // Header controls: Pill Toggle Switch, Interval & Screen Capture
        int toggleW = 46;
        int toggleH = 14;
        int toggleX = x + card3W - toggleW - 8;
        int toggleY = card3Y + 4;
        boolean hoverToggle = mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= toggleY && mouseY <= toggleY + toggleH;
        if (hoverToggle) hoveredBtn = 4;

        int toggleBg = hookEnabled ? 0x4010B981 : 0x30334155;
        int toggleBorder = hookEnabled ? 0x9034D399 : 0x5064748B;
        int toggleTextColor = hookEnabled ? 0xFF34D399 : 0xFF94A3B8;
        g.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleBg);
        ClickGuiTheme.drawOutline(g, toggleX, toggleY, toggleW, toggleH, toggleBorder);
        String toggleTxt = hookEnabled ? "● BẬT" : "○ TẮT";
        ClickGuiTheme.drawText(g, this.font, toggleTxt, toggleX + (toggleW - font.width(toggleTxt)) / 2, toggleY + 3, toggleTextColor, false);

        int intvW = 76;
        int intvH = 14;
        int intvX = toggleX - intvW - 6;
        int intvY = toggleY;
        boolean hoverIntv = mouseX >= intvX && mouseX <= intvX + intvW && mouseY >= intvY && mouseY <= intvY + intvH;
        if (hoverIntv) hoveredBtn = 5;

        int curIntv = Baritone.settings().discordWebhookInterval.value;
        String intvText = "Chu kỳ: " + (curIntv >= 60 ? (curIntv / 60) + "p" : curIntv + "s");
        g.fill(intvX, intvY, intvX + intvW, intvY + intvH, hoverIntv ? 0x3538BDF8 : 0x2038BDF8);
        ClickGuiTheme.drawOutline(g, intvX, intvY, intvW, intvH, hoverIntv ? 0xA038BDF8 : 0x6038BDF8);
        ClickGuiTheme.drawText(g, this.font, intvText, intvX + (intvW - font.width(intvText)) / 2, intvY + 3, ClickGuiTheme.ACCENT_CYAN, false);

        int capW = 82;
        int capH = 14;
        int capX = intvX - capW - 6;
        int capY = toggleY;
        boolean hoverCap = mouseX >= capX && mouseX <= capX + capW && mouseY >= capY && mouseY <= capY + capH;
        if (hoverCap) hoveredBtn = 7;

        boolean capEnabled = Baritone.settings().discordCaptureScreen.value;
        int capBg = capEnabled ? 0x4010B981 : 0x30334155;
        int capBorder = capEnabled ? 0x9034D399 : 0x5064748B;
        int capTextColor = capEnabled ? 0xFF34D399 : 0xFF94A3B8;
        g.fill(capX, capY, capX + capW, capY + capH, capBg);
        ClickGuiTheme.drawOutline(g, capX, capY, capW, capH, capBorder);
        String capTxt = capEnabled ? "📷 Ảnh: BẬT" : "📷 Ảnh: TẮT";
        ClickGuiTheme.drawText(g, this.font, capTxt, capX + (capW - font.width(capTxt)) / 2, capY + 3, capTextColor, false);

        // Row 2: Input Box & Buttons
        int row2Y = card3Y + 22;
        int row2H = 20;
        int btnPasteW = 86;
        int btnTestW = 68;
        int btnClearW = 36;
        int rightButtonsW = btnPasteW + btnTestW + btnClearW + 10;
        int inputX = x + 8;
        int inputW = card3W - 16 - rightButtonsW;

        boolean hoverInput = mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= row2Y && mouseY <= row2Y + row2H;
        if (hoverInput) hoveredBtn = 6;

        g.fill(inputX, row2Y, inputX + inputW, row2Y + row2H, ClickGuiTheme.BG_INPUT);
        int inputBorder = webhookInputFocused ? 0xFF5865F2 : (hoverInput ? 0x805865F2 : 0x405865F2);
        ClickGuiTheme.drawOutline(g, inputX, row2Y, inputW, row2H, inputBorder);

        String currentUrl = Baritone.settings().discordWebhookUrl.value;
        if (currentUrl == null || currentUrl.isEmpty()) {
            String hint = webhookInputFocused ? "" : "⌕ Nhấp để gõ URL hoặc bấm nút [DÁN CLIPBOARD] bên cạnh...";
            ClickGuiTheme.drawText(g, this.font, hint, inputX + 6, row2Y + 6, ClickGuiTheme.TEXT_DIM, false);
        } else {
            String disp = currentUrl;
            if (this.font.width(disp) > inputW - 14) {
                disp = this.font.plainSubstrByWidth(disp, Math.max(10, inputW - 24)) + "...";
            }
            ClickGuiTheme.drawText(g, this.font, disp, inputX + 6, row2Y + 6, ClickGuiTheme.TEXT_TITLE, false);
        }

        if (webhookInputFocused && (System.currentTimeMillis() / 400) % 2 == 0) {
            String typed = (currentUrl == null) ? "" : currentUrl;
            int curX = inputX + 6 + Math.min(inputW - 14, this.font.width(typed));
            g.fill(curX, row2Y + 3, curX + 1, row2Y + row2H - 3, 0xFF5865F2);
        }

        // Action Buttons
        int btnPasteX = inputX + inputW + 6;
        boolean hoverPaste = mouseX >= btnPasteX && mouseX <= btnPasteX + btnPasteW && mouseY >= row2Y && mouseY <= row2Y + row2H;
        if (hoverPaste) hoveredBtn = 1;
        ClickGuiTheme.drawActionButton(g, this.font, ItemStack.EMPTY, "📋 DÁN", btnPasteX, row2Y, btnPasteW, row2H, ClickGuiTheme.ACCENT_CYAN, hoverPaste);

        int btnTestX = btnPasteX + btnPasteW + 4;
        boolean hoverTest = mouseX >= btnTestX && mouseX <= btnTestX + btnTestW && mouseY >= row2Y && mouseY <= row2Y + row2H;
        if (hoverTest) hoveredBtn = 2;
        ClickGuiTheme.drawActionButton(g, this.font, ItemStack.EMPTY, "🚀 GỬI THỬ", btnTestX, row2Y, btnTestW, row2H, ClickGuiTheme.ACCENT_EMERALD, hoverTest);

        int btnClearX = btnTestX + btnTestW + 4;
        boolean hoverClear = mouseX >= btnClearX && mouseX <= btnClearX + btnClearW && mouseY >= row2Y && mouseY <= row2Y + row2H;
        if (hoverClear) hoveredBtn = 3;
        ClickGuiTheme.drawActionButton(g, this.font, ItemStack.EMPTY, "✕", btnClearX, row2Y, btnClearW, row2H, ClickGuiTheme.ACCENT_ROSE, hoverClear);

        // Guidance & Status Lines below input
        if (currentUrl == null || currentUrl.trim().isEmpty() || !currentUrl.startsWith("http")) {
            ClickGuiTheme.drawText(g, this.font, "⚠ Chưa cài đặt Webhook URL! Copy link webhook trong Discord rồi bấm [📋 DÁN] hoặc gõ trực tiếp.", x + 8, card3Y + 47, 0xFFFBBF24, false);
        } else {
            String safeUrl = currentUrl.length() > 55 ? currentUrl.substring(0, 52) + "..." : currentUrl;
            ClickGuiTheme.drawText(g, this.font, "✔ Đã cấu hình: " + safeUrl, x + 8, card3Y + 47, 0xFF34D399, false);
        }
        ClickGuiTheme.drawText(g, this.font, "📊 Nội dung: Cứ mỗi " + (curIntv >= 60 ? (curIntv / 60) + " phút" : curIntv + "s") + " gửi số quặng đào thêm (+delta), tổng tích lũy và thời gian farm.", x + 8, card3Y + 61, ClickGuiTheme.TEXT_MUTED, false);
        ClickGuiTheme.drawText(g, this.font, "📷 Ảnh chụp màn hình: " + (capEnabled ? "BẬT (đính kèm ảnh game thực tế)" : "TẮT (chỉ gửi báo cáo chữ)") + " • Port lệnh: " + Baritone.settings().discordHttpPort.value, x + 8, card3Y + 75, ClickGuiTheme.TEXT_DIM, false);

        return hoveredBtn;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] paragraphs = text.split("\n");
        for (String para : paragraphs) {
            String[] words = para.split(" ");
            StringBuilder currentLine = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                if (currentLine.length() == 0) {
                    currentLine.append(word);
                } else {
                    String test = currentLine + " " + word;
                    if (this.font.width(test) <= maxWidth) {
                        currentLine.append(" ").append(word);
                    } else {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    }
                }
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }

    private void drawModernTooltip(GuiGraphics graphics, String title, Boolean isActive, String desc, String details, String footerHint, int mouseX, int mouseY, int accentColor) {
        int maxTextWidth = Math.min(280, Math.max(200, (int) (this.width * 0.42f)));
        List<String> detailLines = wrapText(details, maxTextWidth);

        // Huy hiệu trạng thái Pill Badge
        String badgeText = null;
        int badgeW = 0;
        if (isActive != null) {
            badgeText = isActive ? "● BẬT" : "○ TẮT";
            badgeW = this.font.width(badgeText) + 8;
        }

        int titleW = this.font.width(title);
        int topRowW = titleW + (badgeW > 0 ? badgeW + 16 : 0);
        int descW = (desc != null && !desc.isEmpty()) ? this.font.width(desc) : 0;
        int footerW = (footerHint != null && !footerHint.isEmpty()) ? this.font.width(footerHint) : 0;

        int maxContentW = Math.max(topRowW, Math.max(descW, footerW));
        for (String line : detailLines) {
            maxContentW = Math.max(maxContentW, this.font.width(line));
        }

        int padX = 12;
        int padY = 9;
        int boxW = Math.max(180, maxContentW + padX * 2);
        int lineH = 12; // 12px thoáng đãng chuẩn font Unicode, chống dính dấu tiếng Việt

        int totalH = padY + 10; // Top pad + Title row (10)
        if (desc != null && !desc.isEmpty()) {
            totalH += 13; // Subtitle row
        }
        if (!detailLines.isEmpty()) {
            totalH += 7 + detailLines.size() * lineH; // Divider (7) + detail lines
        }
        if (footerHint != null && !footerHint.isEmpty()) {
            totalH += 14; // Divider + footer
        }
        totalH += padY; // Bottom pad

        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 10;

        if (tooltipX + boxW > this.width - 6) {
            tooltipX = mouseX - boxW - 8;
        }
        if (tooltipX < 6) {
            tooltipX = 6;
        }

        if (tooltipY + totalH > this.height - 6) {
            tooltipY = this.height - totalH - 6;
        }
        if (tooltipY < 6) {
            tooltipY = 6;
        }

        // 1. Lớp đổ bóng ngoài Ambient Shadow
        graphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + boxW + 2, tooltipY + totalH + 2, 0x30000000);
        graphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + boxW + 1, tooltipY + totalH + 1, 0x40000000);

        // 2. Nền Dark Glass đa tầng (Midnight Deep Slate)
        graphics.fillGradient(tooltipX, tooltipY, tooltipX + boxW, tooltipY + totalH, 0xF40F172A, 0xF90A0E1A);

        // 3. Viền tinh tế (Subtle Neon Border)
        int borderColor = (accentColor & 0x00FFFFFF) | 0x75000000;
        ClickGuiTheme.drawOutline(graphics, tooltipX, tooltipY, boxW, totalH, borderColor);

        // 4. Thanh phát sáng trên cùng (Top Neon Highlight Bar) kèm ánh sáng loang nhẹ (gọn ở farm nặng)
        graphics.fill(tooltipX + 1, tooltipY + 1, tooltipX + boxW - 1, tooltipY + 3, accentColor);
        if (!ClickGuiTheme.heavyMode()) {
            graphics.fillGradient(tooltipX + 1, tooltipY + 3, tooltipX + boxW - 1, tooltipY + 8, (accentColor & 0x00FFFFFF) | 0x25000000, 0x00000000);
        }

        int curY = tooltipY + padY + 1;
        int textX = tooltipX + padX;

        // 5. Header: Tiêu đề + Huy hiệu Pill Badge
        ClickGuiTheme.drawText(graphics, this.font, title, textX, curY, ClickGuiTheme.TEXT_TITLE, true);
        if (isActive != null) {
            int badgeX = tooltipX + boxW - padX - badgeW;
            int badgeY = curY - 1;
            int badgeBg = isActive ? 0x4010B981 : 0x30334155;
            int badgeBorder = isActive ? 0x9034D399 : 0x5064748B;
            int badgeTextColor = isActive ? 0xFF34D399 : 0xFF94A3B8;

            graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 11, badgeBg);
            ClickGuiTheme.drawOutline(graphics, badgeX, badgeY, badgeW, 11, badgeBorder);
            ClickGuiTheme.drawText(graphics, this.font, badgeText, badgeX + 4, badgeY + 2, badgeTextColor, false);
        }
        curY += 13;

        // 6. Subtitle / Mô tả ngắn gọn
        if (desc != null && !desc.isEmpty()) {
            ClickGuiTheme.drawText(graphics, this.font, desc, textX, curY, (accentColor & 0x00FFFFFF) | 0xE0000000, false);
            curY += 13;
        }

        // 7. Thân chi tiết với dãn dòng 12px thoáng đãng
        if (!detailLines.isEmpty()) {
            graphics.fill(textX, curY, tooltipX + boxW - padX, curY + 1, 0x20FFFFFF);
            curY += 6;
            for (String line : detailLines) {
                ClickGuiTheme.drawText(graphics, this.font, line, textX, curY, 0xFFCBD5E1, false);
                curY += lineH;
            }
        }

        // 8. Footer phím tắt thao tác
        if (footerHint != null && !footerHint.isEmpty()) {
            curY += 1;
            graphics.fill(textX, curY, tooltipX + boxW - padX, curY + 1, 0x14FFFFFF);
            curY += 5;
            ClickGuiTheme.drawText(graphics, this.font, footerHint, textX, curY, 0xFF64748B, false);
        }
    }

    private void drawActionTooltip(GuiGraphics graphics, int a, int mouseX, int mouseY) {
        switch (a) {
            case 0:
                drawModernTooltip(graphics, "Bắt Đầu Đào Quặng", null, "Khởi chạy quy trình tự động đào khoáng",
                        "Khởi động thuật toán tìm đường ARA* và tiến trình đào khoáng sản theo danh sách quặng đã chọn trong tab Quặng.",
                        "§8[Chuột trái] §7Khai thác ngay",
                        mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                break;
            case 1:
                drawModernTooltip(graphics, "Bắt Đầu Chặt Cây", null, "Khởi chạy quy trình tự động chặt cây",
                        "Tự động quét thế giới xung quanh và di chuyển đốn hạ các loại cây đã chọn trong tab Chặt Cây.",
                        "§8[Chuột trái] §7Đốn gỗ ngay",
                        mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);
                break;
            case 2:
                drawModernTooltip(graphics, "Dừng Toàn Bộ", null, "Hủy bỏ mọi hoạt động ngay lập tức",
                        "Dừng tìm đường, ngừng đập khối, xóa phím bấm và đóng mọi giao diện rương ngay lập tức.",
                        "§8[Chuột trái] §7Dừng khẩn cấp",
                        mouseX, mouseY, ClickGuiTheme.ACCENT_ROSE);
                break;
            case 3:
                drawModernTooltip(graphics, "Đặt Lại Chỉ Số", null, "Làm mới bảng thống kê phiên đào",
                        "Xóa toàn bộ số liệu thời gian đào, tổng số khối đã đập và số lượng quặng/kim cương thu thập về 0.",
                        "§8[Chuột trái] §7Xóa số liệu cũ",
                        mouseX, mouseY, ClickGuiTheme.ACCENT_AMBER);
                break;
            case 4:
                drawModernTooltip(graphics, "Đóng Bảng Điều Khiển", null, "Lưu cài đặt và quay lại game",
                        "Tự động lưu toàn bộ cấu hình đã chỉnh vào file automine_config.json và đóng giao diện này.",
                        "§8[Chuột trái] §7Lưu & Thoát",
                        mouseX, mouseY, ClickGuiTheme.TEXT_MUTED);
                break;
        }
    }

    private void drawQuickTooltip(GuiGraphics graphics, int q, int tab, int mouseX, int mouseY) {
        if (tab == 0) { // Ores
            switch (q) {
                case 0:
                    drawModernTooltip(graphics, "Chọn Quặng Quý (Kim Cương+)", null, "Bật các khoáng sản giá trị cao nhất",
                            "Kích hoạt Kim Cương, Ngọc Lục Bảo, Mảnh Cổ Đại, Ngọc Lưu Ly và Đá Đỏ. Bỏ qua than, sắt, đồng để tối ưu không gian túi đồ.",
                            "§8[Chuột trái] §7Áp dụng quặng quý",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
                case 1:
                    drawModernTooltip(graphics, "Chọn Tất Cả Quặng", null, "Bật toàn bộ 10 loại khoáng sản",
                            "Tự động kích hoạt toàn bộ các loại quặng: Kim Cương, Mảnh Cổ Đại, Ngọc Lục Bảo, Vàng, Sắt, Đá Đỏ, Ngọc Lưu Ly, Đồng, Than Đá, Thạch Anh.",
                            "§8[Chuột trái] §7Áp dụng chọn hết",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);
                    break;
                case 2:
                    drawModernTooltip(graphics, "Bỏ Chọn Toàn Bộ", null, "Tắt hết tất cả quặng",
                            "Tắt chọn toàn bộ quặng để bạn có thể chọn thủ công từng loại quặng mong muốn.",
                            "§8[Chuột trái] §7Áp dụng bỏ chọn",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_ROSE);
                    break;
                case 3:
                    drawModernTooltip(graphics, "Đảo Ngược Lựa Chọn", null, "Đảo trạng thái các quặng",
                            "Quặng nào đang Bật sẽ chuyển thành Tắt, và quặng nào đang Tắt sẽ chuyển thành Bật.",
                            "§8[Chuột trái] §7Áp dụng đảo ngược",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_PURPLE);
                    break;
            }
        } else if (tab == 1) { // Trees
            switch (q) {
                case 0:
                    drawModernTooltip(graphics, "Gỗ Thế Giới Thường", null, "Khai thác cây ở Overworld",
                            "Kích hoạt gỗ Sồi, Bạch Dương, Thông, Rừng Rậm, Keo, Sồi Sẫm, Đước, Hoa Anh Đào và Tre.",
                            "§8[Chuột trái] §7Áp dụng Overworld",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
                case 1:
                    drawModernTooltip(graphics, "Chọn Tất Cả Cây", null, "Bật toàn bộ 11 loại gỗ",
                            "Kích hoạt toàn bộ các loại gỗ trong Overworld và Nether: Sồi, Bạch Dương, Rừng Rậm, Hoa Anh Đào, Tre, Rừng Đỏ, Rừng Xanh, v.v.",
                            "§8[Chuột trái] §7Áp dụng chọn hết",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);
                    break;
                case 2:
                    drawModernTooltip(graphics, "Bỏ Chọn Toàn Bộ", null, "Tắt hết tất cả loại cây",
                            "Tắt chọn toàn bộ cây để bạn tự chọn thủ công những loại gỗ cần đốn hạ.",
                            "§8[Chuột trái] §7Áp dụng bỏ chọn",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_ROSE);
                    break;
                case 3:
                    drawModernTooltip(graphics, "Chỉ Gỗ Nether", null, "Khai thác thân nấm Nether",
                            "Chỉ kích hoạt khai thác thân nấm đỏ Crimson và nấm xanh Warped chống cháy trong thế giới Nether.",
                            "§8[Chuột trái] §7Áp dụng Nether",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_AMBER);
                    break;
            }
        } else if (tab == 2) { // Movement
            switch (q) {
                case 0:
                    drawModernTooltip(graphics, "Cấu Hình Tốc Độ Cực Hạn", null, "Khai thác tốc độ di chuyển tối đa",
                            "Bật đồng thời: Nhảy hầm (Bhop) không delay, Tự chạy nhanh, Phản xạ ARA* 0ms, Ôm cua mượt mà và Bơi nhanh dưới nước.",
                            "§8[Chuột trái] §7Áp dụng tốc độ cao",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
                case 1:
                    drawModernTooltip(graphics, "Cấu Hình Di Chuyển An Toàn", null, "Chống rơi hang và bảo toàn tính mạng",
                            "Bật Đào dọc an toàn chống rơi hang sâu, Rà soát chất lỏng nước/dung nham, Khóa 1 hướng đào và Tắt nhảy hầm.",
                            "§8[Chuột trái] §7Áp dụng an toàn",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);
                    break;
                case 2:
                    drawModernTooltip(graphics, "Cấu Hình Vượt Địa Hình", null, "Vượt hang động và núi non hiểm trở",
                            "Bật Parkour nhảy vực khe hở 1-4 ô, Tự do xoay nhìn quan sát cảnh quan và phản xạ tính đường ARA* 0ms.",
                            "§8[Chuột trái] §7Áp dụng địa hình",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_PURPLE);
                    break;
                case 3:
                    drawModernTooltip(graphics, "Cấu Hình Di Chuyển Mặc Định", null, "Cân bằng tốc độ và vượt chướng ngại",
                            "Khôi phục các tùy chọn di chuyển về trạng thái cân bằng chuẩn mực nhất của Baritone.",
                            "§8[Chuột trái] §7Khôi phục mặc định",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_AMBER);
                    break;
            }
        } else if (tab == 3) { // Survival
            switch (q) {
                case 0:
                    drawModernTooltip(graphics, "Cấu Hình Bảo Vệ Tối Đa", null, "Bảo vệ sinh mạng và trang bị tuyệt đối",
                            "Kích hoạt Tự ăn uống, Cầm Totem tay phụ tức thì, Tự ngắt kết nối an toàn khi chìm Lava 3s hoặc máu <= 6 HP, Né quái vật.",
                            "§8[Chuột trái] §7Áp dụng bảo vệ",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
                case 1:
                    drawModernTooltip(graphics, "Cấu Hình Treo Máy (AFK)", null, "Tự động hóa hoàn toàn khi rời máy",
                            "Tự ăn, Cầm Totem, Tự mua sắm /shop và cất đồ vào Shulker Box / Rương Ender, Tự vứt rác giải phóng túi đồ.",
                            "§8[Chuột trái] §7Áp dụng treo máy",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);
                    break;
                case 2:
                    drawModernTooltip(graphics, "Cấu Hình Giữ Toàn Bộ Khoáng Sản", null, "Không vứt bỏ bất kỳ tài nguyên nào",
                            "Tự chọn dụng cụ tối ưu độ bền, Cất trữ khoáng sản vào Shulker Box và TẮT tính năng tự động vứt rác.",
                            "§8[Chuột trái] §7Áp dụng giữ đồ",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_PURPLE);
                    break;
                case 3:
                    drawModernTooltip(graphics, "Cấu Hình Sinh Tồn Mặc Định", null, "Thiết lập sinh tồn chuẩn mực",
                            "Khôi phục cài đặt sinh tồn về trạng thái cân bằng an toàn mặc định.",
                            "§8[Chuột trái] §7Khôi phục mặc định",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_AMBER);
                    break;
            }
        }
    }

    private void drawTabTooltip(GuiGraphics graphics, int tab, int mouseX, int mouseY) {
        boolean isTabActive = (activeTab == tab && searchQuery.isEmpty());
        String tabHint = isTabActive ? "§a● Tab đang mở" : "§8[Chuột trái] §7Chuyển sang tab này";
        switch (tab) {
            case 0:
                drawModernTooltip(graphics, "Tab Quặng (Khoáng Sản)", isTabActive, "Cấu hình danh sách quặng khai thác",
                        "Tùy chọn 10 loại khoáng sản (Kim Cương, Mảnh Cổ Đại, Ngọc Lục Bảo, v.v.) muốn bot tự động tìm kiếm.",
                        tabHint,
                        mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                break;
            case 1:
                drawModernTooltip(graphics, "Tab Chặt Cây", isTabActive, "Cấu hình danh sách cây gỗ khai thác",
                        "Tùy chọn 11 loại gỗ (Sồi, Bạch Dương, Tre, Anh Đào, v.v.) muốn bot tự động đốn hạ.",
                        tabHint,
                        mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);
                break;
            case 2:
                drawModernTooltip(graphics, "Tab Đi Lại (Di Chuyển)", isTabActive, "Cấu hình vượt địa hình & di chuyển",
                        "Tùy chỉnh Chạy Nhanh, Nhảy Parkour, Vượt Nước, Đào 1 Block (Crawl) và Đào Thẳng Xuống.",
                        tabHint,
                        mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                break;
            case 3:
                drawModernTooltip(graphics, "Tab Sinh Tồn", isTabActive, "Cấu hình bảo vệ mạng sống & túi đồ",
                        "Tự Ăn, Tự Cầm Totem, Tự Đăng Xuất Khi Máu Thấp, Cất Đồ Shulker và Tự Lọc Ném Bỏ Rác.",
                        tabHint,
                        mouseX, mouseY, ClickGuiTheme.ACCENT_ROSE);
                break;
            case 4:
                drawModernTooltip(graphics, "Tab Giao Diện", isTabActive, "Cấu hình hiển thị & hiệu năng",
                        "Tùy chỉnh HUD thống kê, Đặt khối 1-tick, Giới hạn FPS, Chế độ Streamer và Tầng Y đào.",
                        tabHint,
                        mouseX, mouseY, ClickGuiTheme.ACCENT_PURPLE);
                break;
            case 5:
                drawModernTooltip(graphics, "Tab Chỉ Số (Giám Sát)", isTabActive, "Bảng thống kê phần cứng & phiên đào",
                        "Theo dõi thời gian thực FPS, CPU, RAM, GPU, thời gian đào, tốc độ khối/giờ và số kim cương thu được.",
                        tabHint,
                        mouseX, mouseY, ClickGuiTheme.ACCENT_AMBER);
                break;
        }
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
        Helper.HELPER.logDirect(Component.literal("§e[Quặng] Đã dừng  "),
                ChatButtons.openGuiButton());
    }

    private void startAutoChop() {
        AutoMineConfig.save();
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
        Baritone.settings().autoBuyTotem.value = optAutoTotem;
        Baritone.settings().autoLogoutOnDanger.value = optAutoLogout;
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

        // Phản xạ nhanh chuẩn upstream Baritone (chống đơ/lag/anti-cheat flags):
        Baritone.settings().primaryTimeoutMS.value = 2500L;
        Baritone.settings().failureTimeoutMS.value = 4000L;
        Baritone.settings().planAheadPrimaryTimeoutMS.value = 2500L;
        Baritone.settings().planAheadFailureTimeoutMS.value = 4000L;

        List<BlockOptionalMeta> boms = new ArrayList<>();
        if (woodOak) {
            boms.add(new BlockOptionalMeta(Blocks.OAK_LOG));
            boms.add(new BlockOptionalMeta(Blocks.OAK_WOOD));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_OAK_LOG));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_OAK_WOOD));
        }
        if (woodBirch) {
            boms.add(new BlockOptionalMeta(Blocks.BIRCH_LOG));
            boms.add(new BlockOptionalMeta(Blocks.BIRCH_WOOD));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_BIRCH_LOG));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_BIRCH_WOOD));
        }
        if (woodSpruce) {
            boms.add(new BlockOptionalMeta(Blocks.SPRUCE_LOG));
            boms.add(new BlockOptionalMeta(Blocks.SPRUCE_WOOD));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_SPRUCE_LOG));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_SPRUCE_WOOD));
        }
        if (woodDarkOak) {
            boms.add(new BlockOptionalMeta(Blocks.DARK_OAK_LOG));
            boms.add(new BlockOptionalMeta(Blocks.DARK_OAK_WOOD));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_DARK_OAK_LOG));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_DARK_OAK_WOOD));
        }
        if (woodAcacia) {
            boms.add(new BlockOptionalMeta(Blocks.ACACIA_LOG));
            boms.add(new BlockOptionalMeta(Blocks.ACACIA_WOOD));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_ACACIA_LOG));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_ACACIA_WOOD));
        }
        if (woodJungle) {
            boms.add(new BlockOptionalMeta(Blocks.JUNGLE_LOG));
            boms.add(new BlockOptionalMeta(Blocks.JUNGLE_WOOD));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_JUNGLE_LOG));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_JUNGLE_WOOD));
        }
        if (woodCherry) {
            boms.add(new BlockOptionalMeta(Blocks.CHERRY_LOG));
            boms.add(new BlockOptionalMeta(Blocks.CHERRY_WOOD));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_CHERRY_LOG));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_CHERRY_WOOD));
        }
        if (woodMangrove) {
            boms.add(new BlockOptionalMeta(Blocks.MANGROVE_LOG));
            boms.add(new BlockOptionalMeta(Blocks.MANGROVE_WOOD));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_MANGROVE_LOG));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_MANGROVE_WOOD));
        }
        if (woodBamboo) {
            boms.add(new BlockOptionalMeta(Blocks.BAMBOO_BLOCK));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_BAMBOO_BLOCK));
        }
        if (woodCrimson) {
            boms.add(new BlockOptionalMeta(Blocks.CRIMSON_STEM));
            boms.add(new BlockOptionalMeta(Blocks.CRIMSON_HYPHAE));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_CRIMSON_STEM));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_CRIMSON_HYPHAE));
        }
        if (woodWarped) {
            boms.add(new BlockOptionalMeta(Blocks.WARPED_STEM));
            boms.add(new BlockOptionalMeta(Blocks.WARPED_HYPHAE));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_WARPED_STEM));
            boms.add(new BlockOptionalMeta(Blocks.STRIPPED_WARPED_HYPHAE));
        }

        if (boms.isEmpty()) {
            Helper.HELPER.logDirect("§c[AutoChop] Bạn chưa chọn loại cây nào để chặt! Hãy mở tab CHẶT CÂY để bật chọn loại cây muốn khai thác.");
            return;
        }

        BaritoneAPI.getProvider().getWorldScanner().repack(playerCtx);
        Helper.HELPER.logDirect(Component.literal("§b[Cây] Đang chặt cây đã chọn  "),
                ChatButtons.openGuiButton(),
                Component.literal(" "),
                ChatButtons.stopButton());

        MiningStatsTracker.getInstance().reset();
        baritone.getMineProcess().setChopMode(true);
        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }

    private void startAutoMine() {
        AutoMineConfig.save();
        IPlayerContext playerCtx = baritone.getPlayerContext();
        if (playerCtx.player() == null || playerCtx.world() == null) {
            Helper.HELPER.logDirect("§e[Tr0ngX] Hãy vào world rồi mới bắt đầu đào!");
            return;
        }

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
        Baritone.settings().autoBuyFood.value = optAutoEat;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = optAutoTotem;
        Baritone.settings().autoBuyTotem.value = optAutoTotem;
        Baritone.settings().autoLogoutOnDanger.value = optAutoLogout;
        Baritone.settings().neverKick.value = optNeverKick;
        Baritone.settings().autoLogoutOnlyWhileMining.value = optAutoLogoutOnlyWhileMining;
        Baritone.settings().autoShulkerStorage.value = optShulkerStorage;
        Baritone.settings().autoBuyShulker.value = optShulkerStorage;
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
            oreNames.add("Kim Cương");
        }
        if (oreLapis) {
            boms.add(new BlockOptionalMeta(Blocks.LAPIS_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_LAPIS_ORE));
            oreNames.add("Ngọc Lưu Ly");
        }
        if (oreRedstone) {
            boms.add(new BlockOptionalMeta(Blocks.REDSTONE_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_REDSTONE_ORE));
            oreNames.add("Đá Đỏ");
        }
        if (oreGold) {
            boms.add(new BlockOptionalMeta(Blocks.GOLD_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_GOLD_ORE));
            boms.add(new BlockOptionalMeta(Blocks.NETHER_GOLD_ORE));
            oreNames.add("Vàng");
        }
        if (oreIron) {
            boms.add(new BlockOptionalMeta(Blocks.IRON_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_IRON_ORE));
            oreNames.add("Sắt");
        }
        if (oreEmerald) {
            boms.add(new BlockOptionalMeta(Blocks.EMERALD_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_EMERALD_ORE));
            oreNames.add("Ngọc Lục Bảo");
        }
        if (oreDebris) {
            boms.add(new BlockOptionalMeta(Blocks.ANCIENT_DEBRIS));
            oreNames.add("Mảnh Cổ Đại");
        }
        if (oreCopper) {
            boms.add(new BlockOptionalMeta(Blocks.COPPER_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_COPPER_ORE));
            oreNames.add("Đồng");
        }
        if (oreCoal) {
            boms.add(new BlockOptionalMeta(Blocks.COAL_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_COAL_ORE));
            oreNames.add("Than Đá");
        }
        if (oreQuartz) {
            boms.add(new BlockOptionalMeta(Blocks.NETHER_QUARTZ_ORE));
            oreNames.add("Thạch Anh");
        }

        if (boms.isEmpty()) {
            boms.add(new BlockOptionalMeta(Blocks.DIAMOND_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_DIAMOND_ORE));
            oreNames.add("Kim Cương (Mặc Định)");
        }

        baritone.getPathingBehavior().cancelSegmentIfSafe();
        BaritoneAPI.getProvider().getWorldScanner().repack(playerCtx);
        Helper.HELPER.logDirect(Component.literal("§b[Quặng] Đang đào: " + String.join(", ", oreNames) + " (Y=" + targetY + ")  "),
                ChatButtons.openGuiButton(),
                Component.literal(" "),
                ChatButtons.stopButton());

        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }
}
