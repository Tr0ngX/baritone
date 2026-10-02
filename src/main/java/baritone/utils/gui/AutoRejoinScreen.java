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

package baritone.utils.gui;

import baritone.api.utils.Helper;
import baritone.utils.AutoRejoinConfig;
import baritone.utils.AutoRejoinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Giao diện GUI chuyên dụng để nhập Mật Khẩu Đăng Nhập /dn và Cài đặt Tự Động Rejoin KingMC.
 */
public class AutoRejoinScreen extends Screen {

    private final Screen parent;

    private EditBox passwordBox;
    private EditBox serverIpBox;
    private EditBox targetServerBox;

    private Button toggleEnabledBtn;
    private Button showPasswordBtn;
    private Button delay3sBtn;
    private Button delay5sBtn;
    private Button delay10sBtn;
    private Button delay15sBtn;
    private Button autoOpenMenuBtn;
    private Button autoClickServerBtn;
    private Button autoResumeBtn;

    private boolean showPassword = false;
    private String statusFeedback = "";
    private int feedbackTicks = 0;

    public AutoRejoinScreen(Screen parent) {
        super(Component.literal("Cài Đặt Mật Khẩu /dn & Tự Động Rejoin"));
        this.parent = parent;
        AutoRejoinConfig.ensureLoaded();
    }

    @Override
    protected void init() {
        super.init();

        int cardW = Math.min(440, this.width - 20);
        int cardH = Math.min(320, this.height - 20);
        int cardX = (this.width - cardW) / 2;
        int cardY = (this.height - cardH) / 2;

        int rowY = cardY + 38;
        int inputW = cardW - 30;
        int innerX = cardX + 15;

        // 1. Ô NHẬP MẬT KHẨU ĐĂNG NHẬP (/dn) ĐẶT NGAY TRÊN CÙNG – NỔI BẬT NHẤT
        int passInputW = inputW - 75;
        passwordBox = new EditBox(this.font, innerX, rowY + 12, passInputW, 20, Component.literal("Mật khẩu /dn"));
        passwordBox.setMaxLength(64);
        passwordBox.setValue(AutoRejoinConfig.password != null ? AutoRejoinConfig.password : "");
        passwordBox.setHint(Component.literal("Nhập mật khẩu cho lệnh /dn tại đây..."));
        this.addRenderableWidget(passwordBox);

        // Tự động focus con trỏ vào ô nhập mật khẩu ngay khi mở GUI!
        this.setInitialFocus(passwordBox);

        showPasswordBtn = Button.builder(
                Component.literal(showPassword ? "§a👁 Hiện" : "§7🔒 Ẩn"),
                btn -> {
                    showPassword = !showPassword;
                    btn.setMessage(Component.literal(showPassword ? "§a👁 Hiện" : "§7🔒 Ẩn"));
                }
        ).bounds(innerX + passInputW + 4, rowY + 12, 70, 20).build();
        this.addRenderableWidget(showPasswordBtn);
        rowY += 42;

        // 2. Nút Bật/Tắt Tự Động Rejoin
        toggleEnabledBtn = Button.builder(
                getToggleText(),
                btn -> {
                    String inputPass = passwordBox != null ? passwordBox.getValue().trim() : "";
                    if (!inputPass.isEmpty()) {
                        AutoRejoinConfig.password = inputPass;
                    }
                    if (!AutoRejoinConfig.enabled) {
                        if (!AutoRejoinConfig.hasPassword()) {
                            statusFeedback = "§c⚠ BẠN PHẢI NHẬP MẬT KHẨU /dn VÀO Ô TRÊN TRƯỚC KHI BẬT!";
                            feedbackTicks = 100;
                            this.setFocused(passwordBox);
                            return;
                        }
                        AutoRejoinConfig.enabled = true;
                        statusFeedback = "§a✔ Đã kích hoạt Tự Động Rejoin!";
                        feedbackTicks = 60;
                    } else {
                        AutoRejoinConfig.enabled = false;
                        statusFeedback = "§7Đã tắt Tự Động Rejoin.";
                        feedbackTicks = 40;
                    }
                    btn.setMessage(getToggleText());
                    AutoRejoinConfig.save();
                }
        ).bounds(innerX, rowY, inputW, 20).build();
        this.addRenderableWidget(toggleEnabledBtn);
        rowY += 26;

        // 3. Ô nhập Server IP và Cụm Đích (chia 2 cột)
        int halfW = (inputW - 6) / 2;
        serverIpBox = new EditBox(this.font, innerX, rowY + 12, halfW, 18, Component.literal("Server IP"));
        serverIpBox.setMaxLength(64);
        serverIpBox.setValue(AutoRejoinConfig.serverIp != null ? AutoRejoinConfig.serverIp : "kingmc.vn");
        this.addRenderableWidget(serverIpBox);

        targetServerBox = new EditBox(this.font, innerX + halfW + 6, rowY + 12, halfW, 18, Component.literal("Cụm Máy Chủ"));
        targetServerBox.setMaxLength(64);
        targetServerBox.setValue(AutoRejoinConfig.targetSubServer != null ? AutoRejoinConfig.targetSubServer : "KingSMP");
        this.addRenderableWidget(targetServerBox);
        rowY += 36;

        // 4. Lựa chọn thời gian chờ Reconnect: [ 3s ] [ 5s ] [ 10s ] [ 15s ]
        int segW = (inputW - 9) / 4;
        delay3sBtn = Button.builder(Component.literal(AutoRejoinConfig.reconnectDelaySeconds == 3 ? "§a✔ 3 giây" : "§73s"), btn -> setDelay(3)).bounds(innerX, rowY + 12, segW, 18).build();
        delay5sBtn = Button.builder(Component.literal(AutoRejoinConfig.reconnectDelaySeconds == 5 ? "§a✔ 5 giây" : "§75s"), btn -> setDelay(5)).bounds(innerX + segW + 3, rowY + 12, segW, 18).build();
        delay10sBtn = Button.builder(Component.literal(AutoRejoinConfig.reconnectDelaySeconds == 10 ? "§a✔ 10 giây" : "§710s"), btn -> setDelay(10)).bounds(innerX + (segW + 3) * 2, rowY + 12, segW, 18).build();
        delay15sBtn = Button.builder(Component.literal(AutoRejoinConfig.reconnectDelaySeconds == 15 ? "§a✔ 15 giây" : "§715s"), btn -> setDelay(15)).bounds(innerX + (segW + 3) * 3, rowY + 12, segW, 18).build();
        this.addRenderableWidget(delay3sBtn);
        this.addRenderableWidget(delay5sBtn);
        this.addRenderableWidget(delay10sBtn);
        this.addRenderableWidget(delay15sBtn);
        rowY += 36;

        // 5. Các nút Toggles tự động hoá
        autoOpenMenuBtn = Button.builder(
                getAutoOpenMenuText(),
                btn -> {
                    AutoRejoinConfig.autoOpenMenu = !AutoRejoinConfig.autoOpenMenu;
                    btn.setMessage(getAutoOpenMenuText());
                }
        ).bounds(innerX, rowY, halfW, 18).build();
        this.addRenderableWidget(autoOpenMenuBtn);

        autoClickServerBtn = Button.builder(
                getAutoClickServerText(),
                btn -> {
                    AutoRejoinConfig.autoClickSubServer = !AutoRejoinConfig.autoClickSubServer;
                    btn.setMessage(getAutoClickServerText());
                }
        ).bounds(innerX + halfW + 6, rowY, halfW, 18).build();
        this.addRenderableWidget(autoClickServerBtn);
        rowY += 22;

        autoResumeBtn = Button.builder(
                getAutoResumeText(),
                btn -> {
                    AutoRejoinConfig.autoResumeTask = !AutoRejoinConfig.autoResumeTask;
                    btn.setMessage(getAutoResumeText());
                }
        ).bounds(innerX, rowY, inputW, 18).build();
        this.addRenderableWidget(autoResumeBtn);
        rowY += 26;

        // 6. Hàng nút chân trang: [💾 Lưu Mật Khẩu (Enter)] [🧪 Thử Nghiệm] [❌ Đóng]
        int actionW = (inputW - 8) / 3;
        Button saveBtn = Button.builder(
                Component.literal("§a§l💾 Lưu (Enter)"),
                btn -> {
                    saveSettings();
                    this.onClose();
                }
        ).bounds(innerX, rowY, actionW, 22).build();
        this.addRenderableWidget(saveBtn);

        Button testBtn = Button.builder(
                Component.literal("§e🧪 Test Quy Trình"),
                btn -> {
                    saveSettings();
                    AutoRejoinManager.triggerTestProcess();
                    this.onClose();
                }
        ).bounds(innerX + actionW + 4, rowY, actionW, 22).build();
        this.addRenderableWidget(testBtn);

        Button closeBtn = Button.builder(
                Component.literal("§c❌ Đóng (Esc)"),
                btn -> this.onClose()
        ).bounds(innerX + (actionW + 4) * 2, rowY, actionW, 22).build();
        this.addRenderableWidget(closeBtn);
    }

    private Component getToggleText() {
        if (!AutoRejoinConfig.hasPassword()) {
            return Component.literal("§c✖ Tự Động Rejoin: CHƯA BẬT (Cần nhập mật khẩu trước)");
        }
        return AutoRejoinConfig.enabled
                ? Component.literal("§a✔ Tự Động Rejoin: ĐANG BẬT (Trừ khi gặp Lava/Player)")
                : Component.literal("§7✖ Tự Động Rejoin: ĐÃ TẮT");
    }

    private Component getAutoOpenMenuText() {
        return AutoRejoinConfig.autoOpenMenu
                ? Component.literal("§a✔ Chuột phải Đồng Hồ Lobby")
                : Component.literal("§7✖ Chuột phải Đồng Hồ Lobby");
    }

    private Component getAutoClickServerText() {
        return AutoRejoinConfig.autoClickSubServer
                ? Component.literal("§a✔ Tự click " + AutoRejoinConfig.targetSubServer)
                : Component.literal("§7✖ Tự click " + AutoRejoinConfig.targetSubServer);
    }

    private Component getAutoResumeText() {
        return AutoRejoinConfig.autoResumeTask
                ? Component.literal("§a✔ Tự động tiếp tục Farm / AutoMine cũ khi vào map")
                : Component.literal("§7✖ Không tiếp tục Farm cũ khi vào map");
    }

    private void setDelay(int sec) {
        AutoRejoinConfig.reconnectDelaySeconds = sec;
        if (delay3sBtn != null) delay3sBtn.setMessage(Component.literal(sec == 3 ? "§a✔ 3 giây" : "§73s"));
        if (delay5sBtn != null) delay5sBtn.setMessage(Component.literal(sec == 5 ? "§a✔ 5 giây" : "§75s"));
        if (delay10sBtn != null) delay10sBtn.setMessage(Component.literal(sec == 10 ? "§a✔ 10 giây" : "§710s"));
        if (delay15sBtn != null) delay15sBtn.setMessage(Component.literal(sec == 15 ? "§a✔ 15 giây" : "§715s"));
    }

    private void saveSettings() {
        String enteredPass = "";
        if (passwordBox != null) {
            enteredPass = baritone.command.defaults.AutoRejoinCommand.cleanPassword(passwordBox.getValue());
            AutoRejoinConfig.password = enteredPass;
        }
        if (serverIpBox != null) {
            String ip = serverIpBox.getValue().trim();
            AutoRejoinConfig.serverIp = ip.isEmpty() ? "kingmc.vn" : ip;
        }
        if (targetServerBox != null) {
            String srv = targetServerBox.getValue().trim();
            AutoRejoinConfig.targetSubServer = srv.isEmpty() ? "KingSMP" : srv;
        }

        if (!enteredPass.isEmpty()) {
            AutoRejoinConfig.enabled = true;
            AutoRejoinManager.onPasswordEntered(enteredPass);
            statusFeedback = "§a§l✔ ĐÃ LƯU MẬT KHẨU & TỰ ĐỘNG BẬT REJOIN THÀNH CÔNG!";
            Helper.HELPER.logDirect("§a[Auto Rejoin] Đã lưu mật khẩu /dn và TỰ ĐỘNG BẬT Rejoin KingMC!");
        } else {
            AutoRejoinConfig.enabled = false;
            statusFeedback = "§c⚠ Mật khẩu rỗng! Đã tắt Tự Động Rejoin.";
            Helper.HELPER.logDirect("§c[Auto Rejoin] Mật khẩu đang rỗng, Tự Động Rejoin đã tắt.");
        }

        AutoRejoinConfig.save();
        feedbackTicks = 80;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            saveSettings();
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (feedbackTicks > 0) {
            feedbackTicks--;
            if (feedbackTicks == 0) {
                statusFeedback = "";
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Nền tối mờ phía sau
        guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);

        int cardW = Math.min(440, this.width - 20);
        int cardH = Math.min(320, this.height - 20);
        int cardX = (this.width - cardW) / 2;
        int cardY = (this.height - cardH) / 2;

        // Vẽ Card nền bo khung viền
        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xEE14161C);
        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + 2, 0xFF10B981);
        guiGraphics.fill(cardX, cardY + cardH - 2, cardX + cardW, cardY + cardH, 0xFF10B981);
        guiGraphics.fill(cardX, cardY, cardX + 2, cardY + cardH, 0xFF10B981);
        guiGraphics.fill(cardX + cardW - 2, cardY, cardX + cardW, cardY + cardH, 0xFF10B981);

        // Header Tiêu đề
        guiGraphics.drawCenteredString(this.font, "§a§l🔑 CÀI ĐẶT MẬT KHẨU /dn & TỰ ĐỘNG REJOIN", this.width / 2, cardY + 10, 0xFFFFFF);
        if (!AutoRejoinConfig.hasPassword()) {
            guiGraphics.drawCenteredString(this.font, "§e⚠ BƯỚC 1: Nhập mật khẩu /dn vào ô dưới đây để kích hoạt Rejoin!", this.width / 2, cardY + 22, 0xFFEE55);
        } else {
            guiGraphics.drawCenteredString(this.font, "§7Tự động gõ /dn <mk>, chuột phải Đồng Hồ mở Menu và vào KingSMP", this.width / 2, cardY + 22, 0xAAAAAA);
        }

        int innerX = cardX + 15;
        int halfW = (cardW - 30 - 6) / 2;

        // Nhãn Mật Khẩu
        int passY = cardY + 38;
        String passStatus = (AutoRejoinConfig.password != null && !AutoRejoinConfig.password.isEmpty())
                ? "§a✔ Đã lưu mật khẩu (Sẵn sàng)"
                : "§c§l⚠ BẮT BUỘC PHẢI NHẬP TRƯỚC";
        guiGraphics.drawString(this.font, "§6🔑 Mật Khẩu Đăng Nhập (/dn <mk>): §f" + passStatus, innerX, passY + 2, 0xFFFFFF);

        if (!AutoRejoinConfig.hasPassword() && passwordBox != null) {
            int borderCol = ((System.currentTimeMillis() / 400) % 2 == 0) ? 0xFFFF4444 : 0xFFFFAA00;
            guiGraphics.fill(passwordBox.getX() - 1, passwordBox.getY() - 1, passwordBox.getX() + passwordBox.getWidth() + 1, passwordBox.getY(), borderCol);
            guiGraphics.fill(passwordBox.getX() - 1, passwordBox.getY() + passwordBox.getHeight(), passwordBox.getX() + passwordBox.getWidth() + 1, passwordBox.getY() + passwordBox.getHeight() + 1, borderCol);
            guiGraphics.fill(passwordBox.getX() - 1, passwordBox.getY(), passwordBox.getX(), passwordBox.getY() + passwordBox.getHeight(), borderCol);
            guiGraphics.fill(passwordBox.getX() + passwordBox.getWidth(), passwordBox.getY(), passwordBox.getX() + passwordBox.getWidth() + 1, passwordBox.getY() + passwordBox.getHeight(), borderCol);
        }

        // Nhãn Server IP & Cụm
        int srvY = passY + 42 + 26;
        guiGraphics.drawString(this.font, "§b🌐 Địa Chỉ Server:", innerX, srvY + 2, 0xFFFFFF);
        guiGraphics.drawString(this.font, "§d🎯 Cụm Máy Chủ Mục Tiêu:", innerX + halfW + 6, srvY + 2, 0xFFFFFF);

        // Nhãn Delay
        int delayY = srvY + 36;
        guiGraphics.drawString(this.font, "§6⏱ Thời Gian Chờ Reconnect:", innerX, delayY + 2, 0xFFFFFF);

        // Thông báo phản hồi khi lưu
        if (!statusFeedback.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, statusFeedback, this.width / 2, cardY + cardH - 14, 0xFFFFFF);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (passwordBox != null) {
            String pass = passwordBox.getValue().trim();
            if (!pass.isEmpty() && !pass.equals(AutoRejoinConfig.password)) {
                saveSettings();
            }
        }
        if (this.parent != null) {
            Minecraft.getInstance().setScreen(this.parent);
        } else {
            super.onClose();
        }
    }
}
