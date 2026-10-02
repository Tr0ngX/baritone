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
import baritone.api.utils.Helper;
import baritone.command.defaults.BottingCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * Trình quản lý Tự động Rejoin (Auto Reconnect, Auto Login /dn, Auto Server Menu & Auto Resume Config).
 */
public final class AutoRejoinManager {

    public enum State {
        IDLE,
        COUNTDOWN,
        CONNECTING,
        WAITING_FOR_LOBBY,
        SENDING_LOGIN,
        WAITING_AFTER_LOGIN,
        OPENING_MENU,
        WAITING_FOR_MENU_SCREEN,
        CLICKING_SUB_SERVER,
        WAITING_FOR_SMP_WORLD,
        RESUMING_TASK
    }

    private static volatile State currentState = State.IDLE;
    private static volatile int countdownTicks = 100;
    private static volatile boolean isCountdownCancelled = false;
    private static volatile int stepTicks = 0;

    // Cờ báo hiệu Baritone đang tự động gửi lệnh đăng nhập, tránh MixinClientPlayNetHandler tự chặn/đọc lại
    public static volatile boolean isAutomatingAuth = false;

    // Trạng thái ghi nhớ tiến trình trước khi ngắt kết nối
    private static volatile boolean wasHeavyFarmActive = false;
    private static volatile boolean wasAutoMineActive = false;
    private static volatile boolean wasFarmActive = false;
    private static volatile boolean wasSandActive = false;
    private static volatile String lastServerIp = "kingmc.vn";

    // Tham chiếu widget nút trên DisconnectedScreen
    private static Button reconnectNowButton = null;
    private static Button cancelRejoinButton = null;
    private static Button configButton = null;

    public static volatile boolean isLoggedIn = false;
    private static volatile long lastJoinTimeMs = 0;

    private AutoRejoinManager() {}

    public static boolean isHandlingRejoin() {
        return currentState != State.IDLE;
    }

    public static boolean isInLobby(net.minecraft.world.entity.player.Player player) {
        if (player == null) return false;
        if (currentState != State.IDLE) return true;
        var pos = player.position();
        if (pos != null) {
            // Tọa độ sảnh login KingMC: Y ≈ 41, X ≈ 0.5, Z ≈ 0.8
            if (Math.abs(pos.y - 41.0) <= 5.0 && Math.abs(pos.x) <= 25.0 && Math.abs(pos.z) <= 25.0) {
                return true;
            }
        }
        return false;
    }

    public static State getState() {
        return currentState;
    }

    public static boolean isCountdownRunning() {
        return currentState == State.COUNTDOWN && !isCountdownCancelled;
    }

    public static int getRemainingSeconds() {
        return Math.max(0, (countdownTicks + 19) / 20);
    }

    public static void cancelCountdown() {
        isCountdownCancelled = true;
        currentState = State.IDLE;
        if (reconnectNowButton != null) {
            reconnectNowButton.setMessage(Component.literal("§7[Đã huỷ Rejoin]"));
            reconnectNowButton.active = false;
        }
        if (cancelRejoinButton != null) {
            cancelRejoinButton.setMessage(Component.literal("§c✔ Đã huỷ tự động vào lại"));
            cancelRejoinButton.active = false;
        }
        Helper.HELPER.logDirect("§e[Auto Rejoin] Đã huỷ đếm ngược tự động kết nối lại.");
    }

    /**
     * Bắt đầu quy trình Reconnect ngay lập tức (bỏ qua đếm ngược).
     */
    public static void triggerImmediateReconnect() {
        isCountdownCancelled = false;
        countdownTicks = 0;
        executeConnection();
    }

    /**
     * Hook khởi tạo khi màn hình DisconnectedScreen mở ra.
     */
    public static void onDisconnectedScreenInit(DisconnectedScreen screen, Button backButton, java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> addWidget) {
        AutoRejoinConfig.ensureLoaded();
        isLoggedIn = false;

        // NẾU BỊ KICK BỞI LAVA HOẶC GẶP PLAYER -> TUYỆT ĐỐI KHÔNG REJOIN
        if (AutoLogoutTracker.hasLoggedOut()) {
            currentState = State.IDLE;
            return;
        }

        if (!AutoRejoinConfig.enabled || !AutoRejoinConfig.hasPassword()) {
            currentState = State.IDLE;
            int btnW = backButton != null ? Math.max(200, backButton.getWidth()) : 200;
            int btnX = backButton != null ? backButton.getX() : (screen.width - btnW) / 2;
            int btnY = backButton != null ? backButton.getY() - 24 : screen.height - 85;
            if (btnY < 10) btnY = 10;
            configButton = Button.builder(
                    Component.literal(AutoRejoinConfig.hasPassword() ? "§b⚙ Bật Tự Động Rejoin" : "§c§l🔑 NHẬP MK /dn ĐỂ BẬT REJOIN"),
                    btn -> Minecraft.getInstance().setScreen(new baritone.utils.gui.AutoRejoinScreen(screen))
            ).bounds(btnX, btnY, btnW, 20).build();
            addWidget.accept(configButton);
            return;
        }

        isCountdownCancelled = false;
        countdownTicks = Math.max(3, AutoRejoinConfig.reconnectDelaySeconds) * 20;
        currentState = State.COUNTDOWN;
        stepTicks = 0;

        int btnW = backButton != null ? Math.max(200, backButton.getWidth()) : 200;
        int btnX = backButton != null ? backButton.getX() : (screen.width - btnW) / 2;
        int btnY = backButton != null ? backButton.getY() - 48 : screen.height - 110;
        if (btnY < 10) btnY = 10;

        int halfW = (btnW - 4) / 2;

        // Hàng 1: Nút trạng thái / Kết nối ngay
        reconnectNowButton = Button.builder(
                Component.literal("§e⚡ Kết Nối Ngay (" + getRemainingSeconds() + "s)"),
                btn -> triggerImmediateReconnect()
        ).bounds(btnX, btnY, btnW, 20).build();

        // Hàng 2: [❌ Huỷ Rejoin] | [⚙ Cài Đặt]
        cancelRejoinButton = Button.builder(
                Component.literal("§c❌ Huỷ Rejoin"),
                btn -> cancelCountdown()
        ).bounds(btnX, btnY + 24, halfW, 20).build();

        boolean hasPass = AutoRejoinConfig.password != null && !AutoRejoinConfig.password.trim().isEmpty();
        configButton = Button.builder(
                Component.literal(hasPass ? "§b⚙ Cài Đặt Rejoin" : "§c§l🔑 NHẬP MK /dn NGAY"),
                btn -> {
                    cancelCountdown();
                    Minecraft.getInstance().setScreen(new baritone.utils.gui.AutoRejoinScreen(screen));
                }
        ).bounds(btnX + halfW + 4, btnY + 24, btnW - halfW - 4, 20).build();

        addWidget.accept(reconnectNowButton);
        addWidget.accept(cancelRejoinButton);
        addWidget.accept(configButton);
    }

    /**
     * Hook gọi mỗi tick khi đang ở DisconnectedScreen.
     */
    public static void onDisconnectedScreenTick(DisconnectedScreen screen) {
        if (currentState != State.COUNTDOWN || isCountdownCancelled) {
            return;
        }

        countdownTicks--;
        int sec = getRemainingSeconds();
        if (reconnectNowButton != null) {
            reconnectNowButton.setMessage(Component.literal("§e⚡ Kết Nối Ngay (" + sec + "s)"));
        }

        if (countdownTicks <= 0) {
            executeConnection();
        }
    }

    private static int devAutoConnectTicks = 0;
    private static boolean devAutoConnected = false;

    private static void checkDevAutoConnect(Minecraft mc) {
        if (mc == null || mc.level != null) {
            devAutoConnectTicks = 0;
            return;
        }
        if (!devAutoConnected) {
            String name = mc.getUser() != null ? mc.getUser().getName() : "";
            if ("Dev".equalsIgnoreCase(name) || "GrimTester".equalsIgnoreCase(name)) {
                devAutoConnectTicks++;
                if (devAutoConnectTicks >= 20) {
                    devAutoConnected = true;
                    try {
                        ServerAddress address = ServerAddress.parseString("127.0.0.1:25575");
                        ServerData data = new ServerData("GrimTestServer", "127.0.0.1:25575", ServerData.Type.OTHER);
                        System.out.println("[DevAutoConnect] Automatically connecting to Grim test server at 127.0.0.1:25575 (Username: " + name + ")...");
                        mc.execute(() -> {
                            Screen parent = mc.screen != null ? mc.screen : new TitleScreen();
                            ConnectScreen.startConnecting(parent, mc, address, data, false, null);
                        });
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }
    }

    private static void executeConnection() {
        currentState = State.CONNECTING;
        stepTicks = 0;
        isLoggedIn = false;
        lastJoinTimeMs = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        String targetIp = AutoRejoinConfig.serverIp != null && !AutoRejoinConfig.serverIp.trim().isEmpty()
                ? AutoRejoinConfig.serverIp.trim()
                : (lastServerIp != null && !lastServerIp.trim().isEmpty() ? lastServerIp : "kingmc.vn");

        Helper.HELPER.logDirect("§a[Auto Rejoin] Đang tự động kết nối lại vào máy chủ: " + targetIp);

        try {
            ServerAddress address = ServerAddress.parseString(targetIp);
            ServerData data = new ServerData("KingMC", targetIp, ServerData.Type.OTHER);
            mc.execute(() -> {
                try {
                    ConnectScreen.startConnecting(new TitleScreen(), mc, address, data, false, null);
                } catch (Throwable t) {
                    Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi khi mở ConnectScreen: " + t.getMessage());
                    currentState = State.IDLE;
                }
            });
        } catch (Throwable t) {
            Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi kết nối lại: " + t.getMessage());
            currentState = State.IDLE;
        }
    }

    /**
     * Hook gọi mỗi client tick từ MixinMinecraft.postRunTick.
     * Quản lý State Machine tự động hoá: Sảnh Lobby -> Gửi /dn -> Chuột phải Đồng Hồ -> Click KingSMP -> Khôi phục cấu hình cũ.
     */
    public static void onClientTick(Minecraft mc) {
        if (mc == null) return;
        checkDevAutoConnect(mc);

        // Khi người chơi đang trong game bình thường (State IDLE), ghi nhớ trạng thái và IP
        if (mc.player != null && mc.level != null && mc.getConnection() != null) {
            if (currentState == State.IDLE) {
                // Định kỳ cập nhật IP
                ServerData currentServer = mc.getCurrentServer();
                if (currentServer != null && currentServer.ip != null && !currentServer.ip.trim().isEmpty()) {
                    lastServerIp = currentServer.ip.trim();
                }

                // Ghi nhớ cấu hình đang chạy
                if (Baritone.settings() != null) {
                    if (Baritone.settings().heavyFarmMode.value) {
                        wasHeavyFarmActive = true;
                    }
                    try {
                        var provider = BaritoneAPI.getProvider();
                        var primary = provider != null ? provider.getPrimaryBaritone() : null;
                        if (primary != null) {
                            if (primary.getSandProcess().isActive()) {
                                wasSandActive = true;
                                wasAutoMineActive = false;
                                wasFarmActive = false;
                            } else if (primary.getMineProcess().isActive()) {
                                wasAutoMineActive = true;
                                wasSandActive = false;
                            } else if (primary.getFarmProcess().isActive()) {
                                wasFarmActive = true;
                                wasSandActive = false;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                return;
            }

            // === XỬ LÝ QUY TRÌNH REJOIN TỰ ĐỘNG ===
            handleRejoinAutomationStep(mc);
        }
    }

    private static void handleRejoinAutomationStep(Minecraft mc) {
        if (mc.isSingleplayer()) {
            currentState = State.IDLE;
            return;
        }

        String serverIp = "";
        ServerData currentServer = mc.getCurrentServer();
        if (currentServer != null && currentServer.ip != null) {
            serverIp = currentServer.ip.toLowerCase();
        } else if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            var addr = mc.getConnection().getConnection().getRemoteAddress();
            if (addr != null) {
                serverIp = addr.toString().toLowerCase();
            }
        }

        // Nếu là local test server (localhost/127.0.0.1/25575) thì không thực hiện quy trình đăng nhập Lobby
        if (serverIp.contains("localhost") || serverIp.contains("127.0.0.1") || serverIp.contains("25575") || serverIp.isEmpty()) {
            currentState = State.IDLE;
            return;
        }

        // Nếu không đúng server cấu hình trong AutoRejoinConfig thì cũng bỏ qua
        String targetIp = AutoRejoinConfig.serverIp != null ? AutoRejoinConfig.serverIp.toLowerCase() : "";
        if (!targetIp.isEmpty() && !serverIp.contains(targetIp)) {
            currentState = State.IDLE;
            return;
        }

        stepTicks++;

        switch (currentState) {
            case CONNECTING:
            case WAITING_FOR_LOBBY:
                // Chờ ít nhất 60 ticks (3.0s) và ít nhất 3000ms tính từ lúc kết nối để tránh KingMC rate-limit!
                long timeSinceJoin = System.currentTimeMillis() - lastJoinTimeMs;
                if (stepTicks >= 60 && timeSinceJoin >= 3000) {
                    stepTicks = 0;
                    if (isLoggedIn) {
                        currentState = State.WAITING_AFTER_LOGIN;
                    } else {
                        currentState = State.SENDING_LOGIN;
                    }
                }
                break;

            case SENDING_LOGIN:
                if (isLoggedIn) {
                    stepTicks = 0;
                    currentState = State.WAITING_AFTER_LOGIN;
                    break;
                }
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastJoinTimeMs < 3000 || nowMs - lastAuthTimeMs < 5000) {
                    // Chưa đủ thời gian chờ an toàn (tránh kick "You used a command too fast!")
                    break;
                }
                // Tự động nhập lệnh /dn <mật_khẩu>
                String pass = AutoRejoinConfig.password;
                if (pass != null && !pass.trim().isEmpty()) {
                    try {
                        isAutomatingAuth = true;
                        lastAuthTimeMs = nowMs;
                        mc.player.connection.sendCommand("dn " + pass.trim());
                        Helper.HELPER.logDirect("§a[Auto Rejoin] Đã tự động gửi lệnh đăng nhập /dn ***");
                    } catch (Throwable t) {
                        Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi gửi lệnh /dn: " + t.getMessage());
                    } finally {
                        isAutomatingAuth = false;
                    }
                    stepTicks = 0;
                    currentState = State.WAITING_AFTER_LOGIN;
                } else {
                    Helper.HELPER.logDirect("§e[Auto Rejoin] Chưa có mật khẩu! Gõ /dn <mật_khẩu> để tự động lưu.");
                    stepTicks = 0;
                    currentState = State.IDLE;
                }
                break;

            case WAITING_AFTER_LOGIN:
                // Chờ khoảng 25 ticks (~1.25s) để server xác thực mật khẩu
                if (stepTicks >= 25) {
                    stepTicks = 0;
                    if (AutoRejoinConfig.autoOpenMenu) {
                        currentState = State.OPENING_MENU;
                    } else {
                        currentState = State.WAITING_FOR_SMP_WORLD;
                    }
                }
                break;

            case OPENING_MENU:
                // Cầm đồng hồ (hoặc slot cấu hình / tìm Clock) và chuột phải
                int targetSlot = findClockHotbarSlot(mc);
                mc.player.getInventory().setSelectedSlot(targetSlot);

                try {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    Helper.HELPER.logDirect("§a[Auto Rejoin] Đã chọn Đồng hồ (Slot " + (targetSlot + 1) + ") và chuột phải mở Menu Máy Chủ!");
                } catch (Throwable t) {
                    Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi chuột phải mở menu: " + t.getMessage());
                }

                stepTicks = 0;
                currentState = State.WAITING_FOR_MENU_SCREEN;
                break;

            case WAITING_FOR_MENU_SCREEN:
                // Chờ màn hình chest menu xuất hiện
                if (mc.screen instanceof AbstractContainerScreen<?> containerScreen) {
                    stepTicks = 0;
                    if (AutoRejoinConfig.autoClickSubServer) {
                        currentState = State.CLICKING_SUB_SERVER;
                    } else {
                        currentState = State.WAITING_FOR_SMP_WORLD;
                    }
                } else if (stepTicks > 70) {
                    // Nếu quá 3.5s chưa mở menu, thử chuột phải lại một lần nữa
                    try {
                        int slot = findClockHotbarSlot(mc);
                        mc.player.getInventory().setSelectedSlot(slot);
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    } catch (Throwable ignored) {}
                    stepTicks = 30;
                } else if (stepTicks > 160) {
                    // Nếu quá 8s vẫn không mở được menu, bỏ qua sang đợi vào SMP
                    Helper.HELPER.logDirect("§e[Auto Rejoin] Quá thời gian chờ mở Menu, tiếp tục đợi vào cụm SMP...");
                    stepTicks = 0;
                    currentState = State.WAITING_FOR_SMP_WORLD;
                }
                break;

            case CLICKING_SUB_SERVER:
                if (mc.screen instanceof AbstractContainerScreen<?> containerScreen) {
                    AbstractContainerMenu menu = containerScreen.getMenu();
                    int targetSlotIndex = findSubServerSlot(mc, menu, AutoRejoinConfig.targetSubServer);

                    if (targetSlotIndex >= 0) {
                        try {
                            mc.gameMode.handleInventoryMouseClick(menu.containerId, targetSlotIndex, 0, ClickType.PICKUP, mc.player);
                            Helper.HELPER.logDirect("§a[Auto Rejoin] Đã click vào cụm " + AutoRejoinConfig.targetSubServer + " (Slot " + targetSlotIndex + ")!");
                            stepTicks = 0;
                            currentState = State.WAITING_FOR_SMP_WORLD;
                        } catch (Throwable t) {
                            Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi click cụm máy chủ: " + t.getMessage());
                        }
                    } else if (stepTicks > 40) {
                        // Nếu không tìm thấy bằng tên, thử fallback click slot 20 (vị trí KingSMP trong ảnh)
                        try {
                            if (menu.slots.size() > 20) {
                                mc.gameMode.handleInventoryMouseClick(menu.containerId, 20, 0, ClickType.PICKUP, mc.player);
                                Helper.HELPER.logDirect("§e[Auto Rejoin] Đã click fallback slot 20 (KingSMP)!");
                            }
                        } catch (Throwable ignored) {}
                        stepTicks = 0;
                        currentState = State.WAITING_FOR_SMP_WORLD;
                    }
                } else {
                    stepTicks = 0;
                    currentState = State.WAITING_FOR_SMP_WORLD;
                }
                break;

            case WAITING_FOR_SMP_WORLD:
                // Chờ chuyển server và tải xong thế giới SMP
                // Dấu hiệu: menu đã đóng (mc.screen == null), đã trôi qua ít nhất 50 ticks (~2.5s)
                if (mc.screen == null && stepTicks >= 50) {
                    stepTicks = 0;
                    currentState = State.RESUMING_TASK;
                } else if (stepTicks > 240) { // Timeout 12 giây
                    stepTicks = 0;
                    currentState = State.RESUMING_TASK;
                }
                break;

            case RESUMING_TASK:
                // Đợi thêm 15 ticks để player spawn hoàn tất an toàn
                if (stepTicks >= 15) {
                    if (AutoRejoinConfig.autoResumeTask) {
                        resumePreviousTask();
                    }

                    Helper.HELPER.logDirect("§a§l[Auto Rejoin] ✔ TẤT CẢ QUY TRÌNH HOÀN TẤT! Đã vào cụm máy chủ an toàn.");
                    try {
                        DiscordManager.getInstance().sendAlert(
                                "🔄 [AUTO REJOIN THÀNH CÔNG]",
                                "Bot đã tự động kết nối lại **" + AutoRejoinConfig.targetSubServer + "**, hoàn tất đăng nhập và khôi phục tiến trình cũ an toàn.",
                                0x00FF00
                        );
                    } catch (Throwable ignored) {}

                    stepTicks = 0;
                    currentState = State.IDLE;
                }
                break;

            default:
                break;
        }
    }

    /**
     * Tìm hotbar slot chứa chiếc Đồng Hồ (Clock) hoặc slot cấu hình.
     */
    private static int findClockHotbarSlot(Minecraft mc) {
        if (mc.player == null) return 4;

        // Ưu tiên 1: Quét tìm item Clock trong hotbar 0..8
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() == Items.CLOCK) {
                    return i;
                }
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains("menu") || name.contains("máy chủ") || name.contains("server")) {
                    return i;
                }
            }
        }

        // Ưu tiên 2: Dùng slot cấu hình trong AutoRejoinConfig nếu hợp lệ
        if (AutoRejoinConfig.clockSlot >= 0 && AutoRejoinConfig.clockSlot < 9) {
            return AutoRejoinConfig.clockSlot;
        }

        // Mặc định: slot 4 (hotbar slot 5)
        return 4;
    }

    /**
     * Tìm slot cụm máy chủ trong Chest Menu.
     */
    private static int findSubServerSlot(Minecraft mc, AbstractContainerMenu menu, String targetServer) {
        if (menu == null || targetServer == null) return -1;
        String targetLower = targetServer.trim().toLowerCase(Locale.ROOT);

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String hoverName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (hoverName.contains(targetLower)) {
                return i;
            }

            // Quét lore/tooltip
            try {
                List<Component> tooltip = Screen.getTooltipFromItem(mc, stack);
                for (Component c : tooltip) {
                    String line = c.getString().toLowerCase(Locale.ROOT);
                    if (line.contains(targetLower) || (line.contains("bấm vào để chơi") && hoverName.contains("smp"))) {
                        return i;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    /**
     * Khôi phục cấu hình hoặc tiến trình đang chạy trước đó.
     */
    private static void resumePreviousTask() {
        try {
            if (wasHeavyFarmActive) {
                BottingCommand.setHeavyFarmMode(true);
                Helper.HELPER.logDirect("§a[Auto Rejoin] Đã tự động khôi phục Chế độ Farm Nặng!");
            }

            var provider = BaritoneAPI.getProvider();
            var primary = provider != null ? provider.getPrimaryBaritone() : null;
            if (primary != null) {
                if (wasSandActive) {
                    primary.getCommandManager().execute("sand");
                    Helper.HELPER.logDirect("§a[Auto Rejoin] Đã tự động kích hoạt lại Đào Cát (#sand)!");
                } else if (wasAutoMineActive) {
                    primary.getCommandManager().execute("automine");
                    Helper.HELPER.logDirect("§a[Auto Rejoin] Đã tự động kích hoạt lại AutoMine!");
                } else if (wasFarmActive) {
                    primary.getCommandManager().execute("farm");
                    Helper.HELPER.logDirect("§a[Auto Rejoin] Đã tự động kích hoạt lại Farm!");
                }
            }
        } catch (Throwable t) {
            Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi khi khôi phục tiến trình cũ: " + t.getMessage());
        }
    }

    /**
     * Chạy thử quy trình (Mở Đồng Hồ -> Chọn KingSMP) ngay khi đang ở sảnh Lobby.
     */
    public static void triggerTestProcess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            Helper.HELPER.logDirect("§c[Auto Rejoin Test] Bạn cần phải ở trong game để chạy thử nghiệm!");
            return;
        }

        Helper.HELPER.logDirect("§a[Auto Rejoin Test] Bắt đầu chạy thử nghiệm quy trình chọn cụm máy chủ...");
        stepTicks = 0;
        currentState = State.OPENING_MENU;
    }

    private static volatile long lastAuthTimeMs = 0;

    /**
     * Lắng nghe tin nhắn chat từ Server để tự động đăng nhập lại khi cần (theo chuẩn CheckStatsKingMC-main).
     * Tự động phản hồi khi server nhắc /dn, /login, hoặc khi nhận diện đang ở toạ độ Lobby KingMC.
     */
    public static void onServerChatMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        AutoRejoinConfig.ensureLoaded();
        if (!AutoRejoinConfig.hasPassword() || !AutoRejoinConfig.enabled) return;

        String pass = AutoRejoinConfig.password.trim();
        if (pass.isEmpty()) return;

        String clean = rawMessage.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        // 0. Bỏ qua tin nhắn chat của người chơi thường trong server (có format <Player> hoặc [Rank] Player: hoặc chat:)
        if ((clean.contains("<") && clean.contains(">")) || clean.contains("chat:")) {
            return;
        }

        // 1. Kiểm tra thông báo đăng nhập THÀNH CÔNG hoặc đã kết nối vào cụm
        boolean isSuccessMsg = clean.contains("thành công")
                || clean.contains("bạn đã đăng nhập")
                || clean.contains("đã được kết nối")
                || clean.contains("đã đăng nhập")
                || clean.contains("vào cụm máy chủ")
                || clean.contains("kết nối tới")
                || clean.contains("connected")
                || clean.contains("discord.kingmc.vn");

        if (isSuccessMsg) {
            isLoggedIn = true;
            if (currentState == State.SENDING_LOGIN) {
                stepTicks = 0;
                currentState = State.WAITING_AFTER_LOGIN;
            }
            return;
        }

        // Nếu đã đăng nhập thành công rồi thì TUYỆT ĐỐI KHÔNG gửi lại /dn nữa!
        if (isLoggedIn) {
            return;
        }

        // Cooldown tối thiểu: Phải cách lúc join ít nhất 2.5s và cách lần auth trước ít nhất 6s
        if (now - lastJoinTimeMs < 2500 || now - lastAuthTimeMs < 6000) {
            return;
        }

        // 2. Kiểm tra từ khóa "kingmc.vn" kết hợp với check toạ độ Lobby: X ≈ 0.50, Y ≈ 41.00, Z ≈ 0.80 (như mc-bot.js)
        if (clean.contains("kingmc.vn")) {
            var pos = mc.player.position();
            if (pos != null) {
                double dx = Math.abs(pos.x - 0.50);
                double dy = Math.abs(pos.y - 41.00);
                double dz = Math.abs(pos.z - 0.80);
                if (dx <= 3.0 && dy <= 3.0 && dz <= 3.0) {
                    lastAuthTimeMs = now;
                    Helper.HELPER.logDirect("§a[Auto Rejoin] Xác nhận đang ở Lobby KingMC (Toạ độ + Chat)! Tự động gửi /dn...");
                    try {
                        isAutomatingAuth = true;
                        mc.player.connection.sendCommand("dn " + pass);
                    } catch (Throwable t) {
                        Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi gửi /dn: " + t.getMessage());
                    } finally {
                        isAutomatingAuth = false;
                    }

                    // Sau khi gõ /dn ở lobby, chuyển sang quy trình mở Menu chọn cụm
                    stepTicks = 0;
                    currentState = State.WAITING_AFTER_LOGIN;
                    return;
                }
            }
        }

        // 3. Tự động Đăng ký khi server yêu cầu (/dk, /register, dang ky)
        boolean isRegisterPrompt = clean.contains("/dk <")
                || clean.contains("/dk [")
                || clean.contains("/register <")
                || clean.contains("/register [")
                || clean.contains("dùng lệnh /dk")
                || clean.contains("dùng lệnh /register")
                || clean.contains("vui lòng /dk")
                || clean.contains("vui lòng /register");

        if (isRegisterPrompt) {
            lastAuthTimeMs = now;
            Helper.HELPER.logDirect("§a[Auto Rejoin] Server yêu cầu đăng ký (/register)! Tự động gửi lệnh...");
            try {
                isAutomatingAuth = true;
                mc.player.connection.sendCommand("register " + pass + " " + pass);
            } catch (Throwable t) {
                Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi gửi /register: " + t.getMessage());
            } finally {
                isAutomatingAuth = false;
            }
            return;
        }

        // 4. Tự động Đăng nhập khi server yêu cầu qua chat (/dn, /login)
        // Tuyệt đối KHÔNG chứa từ khóa đơn lẻ 'đăng nhập' vì KingMC gửi 'Đăng nhập thành công!'
        boolean isLoginPrompt = clean.contains("/dn <")
                || clean.contains("/dn [")
                || clean.contains("/login <")
                || clean.contains("/login [")
                || clean.contains("dùng lệnh /dn")
                || clean.contains("dùng lệnh /login")
                || clean.contains("vui lòng /dn")
                || clean.contains("vui lòng /login")
                || clean.contains("nhập mật khẩu để")
                || clean.contains("nhập lại mật khẩu")
                || (clean.contains("/dn") && (clean.contains("mật khẩu") || clean.contains("mat khau") || clean.contains("tài khoản")));

        if (isLoginPrompt) {
            lastAuthTimeMs = now;
            Helper.HELPER.logDirect("§a[Auto Rejoin] Server yêu cầu đăng nhập qua chat! Tự động gửi /dn ***");
            try {
                isAutomatingAuth = true;
                mc.player.connection.sendCommand("dn " + pass);
            } catch (Throwable t) {
                Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi gửi /dn: " + t.getMessage());
            } finally {
                isAutomatingAuth = false;
            }

            // Nếu đang ở IDLE hoặc SENDING_LOGIN, tiếp tục chuyển tiếp sang mở menu chọn cụm
            if (currentState == State.IDLE || currentState == State.SENDING_LOGIN) {
                stepTicks = 0;
                currentState = State.WAITING_AFTER_LOGIN;
            }
        }
    }

    public static void onPasswordEntered(String pass) {
        if (pass == null || pass.trim().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            try {
                isAutomatingAuth = true;
                mc.player.connection.sendCommand("dn " + pass.trim());
                Helper.HELPER.logDirect("§a[Auto Rejoin] Đã tự động gửi lệnh /dn với mật khẩu vừa nhập!");
                lastAuthTimeMs = System.currentTimeMillis();
                isLoggedIn = true;
                if (currentState == State.WAITING_AFTER_LOGIN || currentState == State.SENDING_LOGIN) {
                    stepTicks = 20; // Rút ngắn thời gian chuyển sang mở menu
                }
            } catch (Throwable t) {
                Helper.HELPER.logDirect("§c[Auto Rejoin] Lỗi gửi /dn: " + t.getMessage());
            } finally {
                isAutomatingAuth = false;
            }
        }
    }

    public static void resetState() {
        currentState = State.IDLE;
        stepTicks = 0;
        countdownTicks = 100;
        isCountdownCancelled = false;
        isLoggedIn = false;
    }
}

