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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

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
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // === ENGINE BỐ CỤC ĐÁP ỨNG THÔNG MINH (RESPONSIVE ENGINE) ===
    private static class ResponsiveLayout {
        final int panelX;
        final int panelW;
        final int tabY;
        final int tabH;
        final int tabW;
        final int searchY;
        final int searchH;
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

            // Search bar
            this.searchY = this.tabY + this.tabH + 3;
            this.searchH = screenH < 320 ? 16 : 19;

            // Nút chọn nhanh (Tab 0 Quặng hoặc Tab 1 Cây)
            boolean showQuick = (activeTab == 0 || activeTab == 1) && !hasSearch;
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
        allModules.add(new ModuleItem(new ItemStack(Items.BARRIER), "Tự Thoát Khẩn Cấp", "Tự thoát game khi gặp Lava, máu thấp hoặc nguy hiểm",
                "Hệ thống an toàn tuyệt đối: Tự thoát game khi chìm trong hồ Lava liên tục đúng 3 giây, hoặc khi máu <= 6 HP. Tự ngắt tính năng sau khi kick để tránh lặp vô hạn!",
                "SURVIVAL", 0xFFF87171, () -> optAutoLogout, () -> {
            optAutoLogout = !optAutoLogout;
            Baritone.settings().autoLogoutOnDanger.value = optAutoLogout;
        }));
        allModules.add(new ModuleItem(new ItemStack(Items.PLAYER_HEAD), "Phát Hiện Người Chơi", "Tự ngắt kết nối ngay khi thấy người chơi (kể cả tàng hình)",
                "Quét radar người chơi trong phạm vi 32 ô (bao gồm cả người chơi tàng hình). Tự động ngắt kết nối tức thì để chống bị phục kích PvP.",
                "SURVIVAL", 0xFFEF4444, () -> Baritone.settings().autoLogoutOnPlayer.value, () -> {
            Baritone.settings().autoLogoutOnPlayer.value = !Baritone.settings().autoLogoutOnPlayer.value;
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
        if (currentTab == 1) {
            if (btnW >= 70) {
                switch (q) {
                    case 0: return "[ TẤT CẢ ]";
                    case 1: return "[ CÂY THƯỜNG ]";
                    case 2: return "[ BỎ CHỌN ]";
                    default: return "[ MẶC ĐỊNH ]";
                }
            } else {
                switch (q) {
                    case 0: return "[TẤT CẢ]";
                    case 1: return "[THƯỜNG]";
                    case 2: return "[BỎ CHỌN]";
                    default: return "[CHUẨN]";
                }
            }
        }
        if (btnW >= 70) {
            switch (q) {
                case 0: return "[ TẤT CẢ ]";
                case 1: return "[ BỎ CHỌN ]";
                case 2: return "[ ĐẢO NGƯỢC ]";
                default: return "[ MẶC ĐỊNH ]";
            }
        } else {
            switch (q) {
                case 0: return "[TẤT CẢ]";
                case 1: return "[BỎ CHỌN]";
                case 2: return "[ĐẢO NGƯỢC]";
                default: return "[CHUẨN]";
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

        // 1. Kiểm tra Click vào Tab Bar (6 tabs)
        for (int i = 0; i < 6; i++) {
            int tx = l.panelX + i * l.tabW;
            if (mouseX >= tx && mouseX <= tx + l.tabW && mouseY >= l.tabY && mouseY <= l.tabY + l.tabH) {
                activeTab = i;
                searchQuery = "";
                searchFocused = false;
                scrollOffset = 0;
                return true;
            }
        }

        // 2. Kiểm tra Click vào Search Bar
        if (mouseX >= l.panelX && mouseX <= l.panelX + l.panelW && mouseY >= l.searchY && mouseY <= l.searchY + l.searchH) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // 3. Kiểm tra Click vào Quick Select Buttons (Tab 0 Ores hoặc Tab 1 Trees)
        if ((activeTab == 0 || activeTab == 1) && searchQuery.isEmpty()) {
            int btnW = (l.panelW - 3 * 4) / 4;
            for (int q = 0; q < 4; q++) {
                int qx = l.panelX + q * (btnW + 4);
                if (mouseX >= qx && mouseX <= qx + btnW && mouseY >= l.quickY && mouseY <= l.quickY + l.quickH) {
                    if (activeTab == 0) {
                        if (q == 0) {
                            oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = true;
                        } else if (q == 1) {
                            oreDiamond = oreLapis = oreRedstone = oreGold = oreIron = oreEmerald = oreDebris = oreCopper = oreCoal = oreQuartz = false;
                        } else if (q == 2) {
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
                        } else {
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
                    } else if (activeTab == 1) {
                        if (q == 0) {
                            woodOak = woodBirch = woodSpruce = woodJungle = woodAcacia = woodDarkOak = woodMangrove = woodCherry = woodBamboo = woodCrimson = woodWarped = true;
                        } else if (q == 1) {
                            woodOak = woodBirch = woodSpruce = woodJungle = woodAcacia = woodDarkOak = woodMangrove = woodCherry = woodBamboo = true;
                            woodCrimson = woodWarped = false;
                        } else if (q == 2) {
                            woodOak = woodBirch = woodSpruce = woodJungle = woodAcacia = woodDarkOak = woodMangrove = woodCherry = woodBamboo = woodCrimson = woodWarped = false;
                        } else {
                            woodOak = woodBirch = woodSpruce = woodJungle = woodAcacia = woodDarkOak = woodMangrove = woodCherry = woodBamboo = true;
                            woodCrimson = false;
                            woodWarped = false;
                        }
                    }
                    AutoMineConfig.save();
                    return true;
                }
            }
        }

        // 4. Kiểm tra Click vào Module Cards
        if (mouseY >= l.contentY && mouseY <= l.contentBottom) {
            List<ModuleItem> filtered = getFilteredModules();
            for (int i = 0; i < filtered.size(); i++) {
                int col = i % l.cardCols;
                int row = i / l.cardCols;
                int cardX = l.panelX + col * (l.colW + l.cardGap);
                int cardY = l.contentY + row * (l.cardH + 4) - scrollOffset;

                if (cardY + l.cardH >= l.contentY && cardY <= l.contentBottom) {
                    if (mouseX >= cardX && mouseX <= cardX + l.colW && mouseY >= cardY && mouseY <= cardY + l.cardH) {
                        ModuleItem item = filtered.get(i);
                        item.toggle.run();
                        AutoMineConfig.save();
                        return true;
                    }
                }
            }

            // Click vào Target Y & FPS Limiter trong Tab 4 (HUD)
            if (activeTab == 4 && searchQuery.isEmpty()) {
                int extraRowY = l.contentY + ((filtered.size() + l.cardCols - 1) / l.cardCols) * (l.cardH + 4) - scrollOffset;
                int halfColW = (l.panelW - 6) / 2;

                int yBtnX = l.panelX;
                if (mouseX >= yBtnX && mouseX <= yBtnX + halfColW && mouseY >= extraRowY && mouseY <= extraRowY + l.cardH) {
                    if (optTargetY == -54) optTargetY = -58;
                    else if (optTargetY == -58) optTargetY = 11;
                    else if (optTargetY == 11) optTargetY = 999;
                    else optTargetY = -54;
                    AutoMineConfig.save();
                    return true;
                }

                int fpsBtnX = l.panelX + halfColW + 6;
                if (mouseX >= fpsBtnX && mouseX <= fpsBtnX + halfColW && mouseY >= extraRowY && mouseY <= extraRowY + l.cardH) {
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

        // 5. Kiểm tra Click vào Bottom Action Bar
        for (int a = 0; a < 5; a++) {
            int ax = l.panelX + a * (l.actionW + l.actionGap);
            if (mouseX >= ax && mouseX <= ax + l.actionW && mouseY >= l.actionBottomY && mouseY <= l.actionBottomY + l.actionH) {
                if (a == 0) {
                    startAutoMine();
                    this.onClose();
                } else if (a == 1) {
                    startAutoChop();
                    this.onClose();
                } else if (a == 2) {
                    stopAutoMine();
                    this.onClose();
                } else if (a == 3) {
                    MiningStatsTracker.getInstance().reset();
                    Helper.HELPER.logDirect("§a[Tr0ngX] Đã reset toàn bộ thống kê đào khoáng!");
                } else {
                    this.onClose();
                }
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
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
                else if (activeTab == 1 && item.category.equals("TREES")) result.add(item);
                else if (activeTab == 2 && item.category.equals("MOVEMENT")) result.add(item);
                else if (activeTab == 3 && item.category.equals("SURVIVAL")) result.add(item);
                else if (activeTab == 4 && item.category.equals("HUD")) result.add(item);
            }
        }
        return result;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Nền tối mờ chuẩn LiquidBounce Nextgen Dark Glass
        graphics.fillGradient(0, 0, this.width, this.height, ClickGuiTheme.BG_SCREEN_TOP, ClickGuiTheme.BG_SCREEN_BOTTOM);

        ResponsiveLayout l = new ResponsiveLayout(this.width, this.height, activeTab, !searchQuery.isEmpty());

        ModuleItem hoveredItem = null;
        boolean hoveredYSetting = false;
        boolean hoveredFpsSetting = false;
        int hoveredActionIdx = -1;
        int hoveredQuickIdx = -1;
        int hoveredTabIdx = -1;
        boolean hoveredSearch = false;

        // 1. Header Bar với Logo Neon, Ambient Glow & Version Tag
        ClickGuiTheme.drawGlowPanel(graphics, l.panelX, 3, l.panelW, this.height - 6, ClickGuiTheme.ACCENT_CYAN);
        graphics.fill(l.panelX, 3, l.panelX + l.panelW, 4, ClickGuiTheme.ACCENT_CYAN);
        ClickGuiTheme.drawText(graphics, this.font, "BẢNG ĐIỀU KHIỂN TR0NGX", l.panelX + 4, 7, ClickGuiTheme.ACCENT_CYAN, true);
        if (l.panelW >= 420) {
            String versionTag = "v1.21.11 ỔN ĐỊNH | ARA* ENGINE";
            int vW = this.font.width(versionTag);
            ClickGuiTheme.drawText(graphics, this.font, versionTag, l.panelX + l.panelW - vW - 4, 7, ClickGuiTheme.TEXT_MUTED, false);
        }

        // 2. LiquidBounce Nextgen Tab Bar (100% Responsive Tab Titles)
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

        // 3. Quick Search Bar
        graphics.fill(l.panelX, l.searchY, l.panelX + l.panelW, l.searchY + l.searchH, ClickGuiTheme.BG_INPUT);
        int searchBorder = searchFocused ? ClickGuiTheme.ACCENT_CYAN : ClickGuiTheme.BORDER_CARD;
        ClickGuiTheme.drawOutline(graphics, l.panelX, l.searchY, l.panelW, l.searchH, searchBorder);

        boolean searchHover = mouseX >= l.panelX && mouseX <= l.panelX + l.panelW && mouseY >= l.searchY && mouseY <= l.searchY + l.searchH;
        if (searchHover) {
            hoveredSearch = true;
        }

        String searchPrompt = searchQuery.isEmpty() ? (searchFocused ? "" : "Tìm kiếm tính năng, quặng...") : searchQuery;
        int searchColor = searchQuery.isEmpty() ? ClickGuiTheme.TEXT_DIM : ClickGuiTheme.TEXT_TITLE;
        int searchPromptY = l.searchY + (l.searchH - 8) / 2;
        ClickGuiTheme.drawText(graphics, this.font, searchPrompt, l.panelX + 6, searchPromptY, searchColor, false);
        if (searchFocused && (System.currentTimeMillis() / 400) % 2 == 0) {
            int cursorX = l.panelX + 6 + this.font.width(searchQuery);
            graphics.fill(cursorX, l.searchY + 3, cursorX + 1, l.searchY + l.searchH - 3, 0xFF38BDF8);
        }

        // 4. Quick Action Buttons (Tab 0 Ores hoặc Tab 1 Trees)
        if ((activeTab == 0 || activeTab == 1) && searchQuery.isEmpty()) {
            int btnW = (l.panelW - 3 * 4) / 4;
            int[] qColors = new int[]{ClickGuiTheme.ACCENT_CYAN, ClickGuiTheme.ACCENT_ROSE, ClickGuiTheme.ACCENT_PURPLE, ClickGuiTheme.ACCENT_AMBER};

            for (int q = 0; q < 4; q++) {
                int qx = l.panelX + q * (btnW + 4);
                boolean qHover = mouseX >= qx && mouseX <= qx + btnW && mouseY >= l.quickY && mouseY <= l.quickY + l.quickH;
                if (qHover) {
                    hoveredQuickIdx = q;
                }
                String qTitle = getResponsiveQuickTitle(q, btnW, activeTab);
                ClickGuiTheme.drawActionButton(graphics, this.font, ItemStack.EMPTY, qTitle, qx, l.quickY, btnW, l.quickH, qColors[q], qHover);
            }
        }

        // 5. Danh sách Card Modules (Scissor Box an toàn)
        graphics.enableScissor(l.panelX - 1, l.contentY, l.panelX + l.panelW + 1, l.contentBottom);

        if (activeTab == 5 && searchQuery.isEmpty()) {
            renderTelemetryDashboard(graphics, l.panelX, l.contentY - scrollOffset, l.panelW);
        } else {
            List<ModuleItem> filtered = getFilteredModules();
            int totalRows = (filtered.size() + l.cardCols - 1) / l.cardCols;
            if (activeTab == 4 && searchQuery.isEmpty()) {
                totalRows++;
            }
            maxScroll = Math.max(0, totalRows * (l.cardH + 4) - (l.contentBottom - l.contentY));

            int switchW = l.isCompact ? 28 : 32;
            int switchH = l.isCompact ? 14 : 16;

            for (int i = 0; i < filtered.size(); i++) {
                int col = i % l.cardCols;
                int row = i / l.cardCols;
                int cardX = l.panelX + col * (l.colW + l.cardGap);
                int cardY = l.contentY + row * (l.cardH + 4) - scrollOffset;

                ModuleItem item = filtered.get(i);
                boolean active = item.getter.getAsBoolean();
                boolean hover = mouseX >= cardX && mouseX <= cardX + l.colW && mouseY >= cardY && mouseY <= cardY + l.cardH;
                if (hover && mouseY >= l.contentY && mouseY <= l.contentBottom) {
                    hoveredItem = item;
                }

                int cardBg = hover ? ClickGuiTheme.BG_CARD_HOVER : (active ? ClickGuiTheme.BG_CARD_ACTIVE : ClickGuiTheme.BG_CARD);
                int cardBorder = hover ? ClickGuiTheme.BORDER_CARD_HOVER : (active ? (item.color | 0x80000000) : ClickGuiTheme.BORDER_CARD);
                ClickGuiTheme.drawGlowingCard(graphics, cardX, cardY, l.colW, l.cardH, cardBg, cardBorder, item.color, active, hover);

                // Đường accent bên trái
                graphics.fill(cardX, cardY, cardX + 3, cardY + l.cardH, active ? item.color : 0x5064748B);

                // Vẽ Item Icon thật 16x16 (Minecraft Item Icon) với nền kính mờ bảo vệ
                boolean hasItemIcon = (item.iconItem != null && !item.iconItem.isEmpty());
                if (hasItemIcon) {
                    int iconY = cardY + (l.cardH - 16) / 2;
                    graphics.fill(cardX + 4, iconY - 2, cardX + 24, iconY + 18, active ? 0x2538BDF8 : 0x12FFFFFF);
                    graphics.renderFakeItem(item.iconItem, cardX + 6, iconY);
                }

                // Switch viên thuốc
                int switchX = cardX + l.colW - switchW - 6;
                int switchY = cardY + (l.cardH - switchH) / 2;
                ClickGuiTheme.drawPillSwitch(graphics, this.font, switchX, switchY, switchW, switchH, active, hover);

                // Text Module bắt đầu sau Icon, có cắt ngắn an toàn không bao giờ đè switch
                int textStartX = hasItemIcon ? (cardX + 26) : (cardX + 8);
                int textMaxW = switchX - textStartX - 4;
                String name = item.name;
                if (this.font.width(name) > textMaxW) {
                    name = this.font.plainSubstrByWidth(name, Math.max(10, textMaxW - 6)) + "..";
                }
                int titleY = cardY + (l.isCompact ? 3 : 5);
                ClickGuiTheme.drawText(graphics, this.font, name, textStartX, titleY, active ? ClickGuiTheme.TEXT_TITLE : ClickGuiTheme.TEXT_MUTED, active);

                if (!l.isCompact || l.cardH >= 28) {
                    String desc = item.desc;
                    if (item.name.equals("Auto-Logout") && AutoLogoutTracker.hasLoggedOut()) {
                        desc = String.format(java.util.Locale.ROOT, "Toạ độ: X:%.1f Y:%.1f Z:%.1f",
                                AutoLogoutTracker.getLastX(),
                                AutoLogoutTracker.getLastY(),
                                AutoLogoutTracker.getLastZ());
                    }
                    if (this.font.width(desc) > textMaxW) {
                        desc = this.font.plainSubstrByWidth(desc, Math.max(10, textMaxW - 6)) + "..";
                    }
                    int descY = cardY + (l.isCompact ? 14 : 17);
                    ClickGuiTheme.drawText(graphics, this.font, desc, textStartX, descY, ClickGuiTheme.TEXT_DIM, false);
                }
            }

            // Target Y & FPS Limiter trong Tab 4 (HUD)
            if (activeTab == 4 && searchQuery.isEmpty()) {
                int extraRowY = l.contentY + ((filtered.size() + l.cardCols - 1) / l.cardCols) * (l.cardH + 4) - scrollOffset;
                int halfColW = (l.panelW - 6) / 2;

                int yBtnX = l.panelX;
                boolean yHover = mouseX >= yBtnX && mouseX <= yBtnX + halfColW && mouseY >= extraRowY && mouseY <= extraRowY + l.cardH;
                if (yHover && mouseY >= l.contentY && mouseY <= l.contentBottom) {
                    hoveredYSetting = true;
                }
                int yBg = yHover ? ClickGuiTheme.BG_CARD_HOVER : ClickGuiTheme.BG_CARD;
                ClickGuiTheme.drawCard(graphics, yBtnX, extraRowY, halfColW, l.cardH, yBg, ClickGuiTheme.BORDER_CARD);
                graphics.fill(yBtnX, extraRowY, yBtnX + 3, extraRowY + l.cardH, ClickGuiTheme.ACCENT_CYAN);
                graphics.renderFakeItem(new ItemStack(Items.COMPASS), yBtnX + 6, extraRowY + (l.cardH - 16) / 2);
                String yLabel = optTargetY == 999 ? "Hiện tại" : "Y=" + optTargetY;
                ClickGuiTheme.drawText(graphics, this.font, "Tầng Y: " + yLabel, yBtnX + 26, extraRowY + 5, ClickGuiTheme.TEXT_TITLE, true);
                ClickGuiTheme.drawText(graphics, this.font, "(-58, -54, 11, Hiện tại)", yBtnX + 26, extraRowY + 16, ClickGuiTheme.TEXT_DIM, false);

                int fpsBtnX = l.panelX + halfColW + 6;
                boolean fpsHover = mouseX >= fpsBtnX && mouseX <= fpsBtnX + halfColW && mouseY >= extraRowY && mouseY <= extraRowY + l.cardH;
                if (fpsHover && mouseY >= l.contentY && mouseY <= l.contentBottom) {
                    hoveredFpsSetting = true;
                }
                int fpsBg = fpsHover ? ClickGuiTheme.BG_CARD_HOVER : ClickGuiTheme.BG_CARD;
                ClickGuiTheme.drawCard(graphics, fpsBtnX, extraRowY, halfColW, l.cardH, fpsBg, ClickGuiTheme.BORDER_CARD);
                graphics.fill(fpsBtnX, extraRowY, fpsBtnX + 3, extraRowY + l.cardH, ClickGuiTheme.ACCENT_EMERALD);
                graphics.renderFakeItem(new ItemStack(Items.CLOCK), fpsBtnX + 6, extraRowY + (l.cardH - 16) / 2);
                int curFpsLimit = baritone.getPlayerContext().minecraft().options.framerateLimit().get();
                String fpsStr = curFpsLimit >= 260 ? "Max" : curFpsLimit + " FPS";
                ClickGuiTheme.drawText(graphics, this.font, "FPS Limit: " + fpsStr, fpsBtnX + 26, extraRowY + 5, ClickGuiTheme.TEXT_TITLE, true);
                ClickGuiTheme.drawText(graphics, this.font, "Click để đổi mức FPS", fpsBtnX + 26, extraRowY + 16, ClickGuiTheme.TEXT_DIM, false);
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
        if (activeTab != 4 && optMiningStats) {
            boolean isMining = baritone.getMineProcess().isActive();
            if (isMining) {
                int statsW = 148;
                if (this.width - (l.panelX + l.panelW) >= statsW + 10) {
                    MiningStatsTracker.getInstance().renderCard(graphics, this.font, l.panelX + l.panelW + 8, l.contentY, statsW, isMining);
                } else if (l.panelX >= statsW + 10) {
                    MiningStatsTracker.getInstance().renderCard(graphics, this.font, l.panelX - statsW - 8, l.contentY, statsW, isMining);
                }
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
        if (hoveredItem != null) {
            drawModernTooltip(graphics,
                    hoveredItem.name,
                    hoveredItem.getter.getAsBoolean(),
                    hoveredItem.desc,
                    hoveredItem.details,
                    "§8[Chuột trái] §7Chuyển đổi Bật / Tắt",
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

    private void renderTelemetryDashboard(GuiGraphics g, int x, int y, int w) {
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
        int cardCols = isCompact ? 1 : 2;
        int colW = isCompact ? w : (w - 8) / 2;
        int cardH = 96;

        // Card 1: Hardware Specs
        ClickGuiTheme.drawCard(g, x, y, colW, cardH, ClickGuiTheme.BG_CARD, ClickGuiTheme.BORDER_CARD);
        g.fill(x, y, x + colW, y + 2, ClickGuiTheme.ACCENT_CYAN);
        ClickGuiTheme.drawText(g, this.font, "PHẦN CỨNG & HIỆU NĂNG", x + 8, y + 6, ClickGuiTheme.ACCENT_CYAN, true);

        ClickGuiTheme.drawText(g, this.font, "FPS: " + curFps, x + 8, y + 22, fpsColor, true);
        ClickGuiTheme.drawText(g, this.font, "CPU: " + cores + " Cores", x + 8, y + 36, ClickGuiTheme.TEXT_BODY, false);
        ClickGuiTheme.drawText(g, this.font, "GPU: " + gpu, x + 8, y + 50, ClickGuiTheme.TEXT_MUTED, false);

        ClickGuiTheme.drawText(g, this.font, "RAM (" + (int) (memPct * 100) + "%): " + usedMem + "/" + maxMem + "MB", x + 8, y + 64, ClickGuiTheme.TEXT_BODY, false);
        ClickGuiTheme.drawProgressBar(g, x + 8, y + 78, colW - 16, 7, memPct, ClickGuiTheme.ACCENT_EMERALD, 0xFF1E293B);

        // Card 2: Mining Live Telemetry
        int rx = isCompact ? x : (x + colW + 8);
        int ry = isCompact ? (y + cardH + 6) : y;

        ClickGuiTheme.drawCard(g, rx, ry, colW, cardH, ClickGuiTheme.BG_CARD, ClickGuiTheme.BORDER_CARD);
        g.fill(rx, ry, rx + colW, ry + 2, ClickGuiTheme.ACCENT_EMERALD);
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

        // 4. Thanh phát sáng trên cùng (Top Neon Highlight Bar) kèm ánh sáng loang nhẹ
        graphics.fill(tooltipX + 1, tooltipY + 1, tooltipX + boxW - 1, tooltipY + 3, accentColor);
        graphics.fillGradient(tooltipX + 1, tooltipY + 3, tooltipX + boxW - 1, tooltipY + 8, (accentColor & 0x00FFFFFF) | 0x25000000, 0x00000000);

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
                    drawModernTooltip(graphics, "Chọn Tất Cả Quặng", null, "Bật toàn bộ 10 loại khoáng sản",
                            "Tự động kích hoạt toàn bộ các loại quặng: Kim Cương, Mảnh Cổ Đại, Ngọc Lục Bảo, Vàng, Sắt, Đá Đỏ, Ngọc Lưu Ly, Đồng, Than Đá, Thạch Anh.",
                            "§8[Chuột trái] §7Áp dụng chọn hết",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
                case 1:
                    drawModernTooltip(graphics, "Bỏ Chọn Toàn Bộ", null, "Tắt hết tất cả quặng",
                            "Tắt chọn toàn bộ quặng để bạn có thể chọn thủ công từng loại quặng mong muốn.",
                            "§8[Chuột trái] §7Áp dụng bỏ chọn",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_ROSE);
                    break;
                case 2:
                    drawModernTooltip(graphics, "Đảo Ngược Lựa Chọn", null, "Đảo trạng thái các quặng",
                            "Quặng nào đang Bật sẽ chuyển thành Tắt, và quặng nào đang Tắt sẽ chuyển thành Bật.",
                            "§8[Chuột trái] §7Áp dụng đảo ngược",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_PURPLE);
                    break;
                case 3:
                    drawModernTooltip(graphics, "Bộ Quặng Chuẩn", null, "Chọn lọc quặng quý giá trị cao",
                            "Chỉ chọn Kim Cương, Ngọc Lục Bảo, Ngọc Lưu Ly và Đá Đỏ giúp tối ưu diện tích túi đồ.",
                            "§8[Chuột trái] §7Áp dụng mặc định",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_AMBER);
                    break;
            }
        } else if (tab == 1) { // Trees
            switch (q) {
                case 0:
                    drawModernTooltip(graphics, "Chọn Tất Cả Cây", null, "Bật toàn bộ 11 loại gỗ",
                            "Kích hoạt toàn bộ các loại gỗ trong Overworld và Nether: Sồi, Bạch Dương, Rừng Rậm, Hoa Anh Đào, Tre, Rừng Đỏ, Rừng Xanh, v.v.",
                            "§8[Chuột trái] §7Áp dụng chọn hết",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
                    break;
                case 1:
                    drawModernTooltip(graphics, "Gỗ Thế Giới Thường", null, "Chỉ chọn cây ở Overworld",
                            "Chỉ chọn các loại gỗ mặt đất, bỏ chọn gỗ Rừng Đỏ (Crimson) và Rừng Xanh (Warped) ở Nether.",
                            "§8[Chuột trái] §7Áp dụng Overworld",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_ROSE);
                    break;
                case 2:
                    drawModernTooltip(graphics, "Bỏ Chọn Toàn Bộ", null, "Tắt hết tất cả loại cây",
                            "Tắt chọn toàn bộ cây để bạn tự chọn thủ công những loại gỗ cần đốn hạ.",
                            "§8[Chuột trái] §7Áp dụng bỏ chọn",
                            mouseX, mouseY, ClickGuiTheme.ACCENT_PURPLE);
                    break;
                case 3:
                    drawModernTooltip(graphics, "Gỗ Thông Dụng", null, "Khai thác các loại gỗ cơ bản",
                            "Bật chọn các loại cây gỗ phổ biến nhất trong thế giới.",
                            "§8[Chuột trái] §7Áp dụng thông dụng",
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
        Helper.HELPER.logDirect("§c[Tr0ngX] Đã dừng toàn bộ quá trình đào!");
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
        Helper.HELPER.logDirect("§a[AutoChop] Đã bắt đầu TỰ ĐỘNG CHẶT CÂY!");

        MiningStatsTracker.getInstance().reset();
        baritone.getMineProcess().setChopMode(true);
        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }

    private void startAutoMine() {
        AutoMineConfig.save();
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
        Baritone.settings().autoBuyFood.value = optAutoEat;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = optAutoTotem;
        Baritone.settings().autoBuyTotem.value = optAutoTotem;
        Baritone.settings().autoLogoutOnDanger.value = optAutoLogout;
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
        Helper.HELPER.logDirect("§a[Tr0ngX] Bắt đầu đào: " + String.join(", ", oreNames) + " (Tầng Y: " + targetY + ")");

        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }
}
