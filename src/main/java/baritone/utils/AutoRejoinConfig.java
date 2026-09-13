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

import baritone.api.utils.Helper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Quản lý lưu trữ và nạp cấu hình Tự động Rejoin (Auto Reconnect & Lobby Login).
 * Cấu hình lưu trữ tại .minecraft/baritone/autorejoin.json.
 */
public final class AutoRejoinConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "autorejoin.json";
    private static volatile boolean loaded = false;

    // === CÁC GIÁ TRỊ CẤU HÌNH ===
    public static boolean enabled = false;
    public static String password = "";
    public static String serverIp = "kingmc.vn";
    public static String targetSubServer = "KingSMP";
    public static int reconnectDelaySeconds = 5;
    public static boolean autoOpenMenu = true;
    public static boolean autoClickSubServer = true;
    public static boolean autoResumeTask = true;
    public static int clockSlot = 4; // Slot index 4 (hotbar slot 5)

    private AutoRejoinConfig() {}

    public static boolean hasPassword() {
        return password != null && !password.trim().isEmpty();
    }

    public static class ConfigData {
        public boolean enabled = false;
        public String password = "";
        public String serverIp = "kingmc.vn";
        public String targetSubServer = "KingSMP";
        public int reconnectDelaySeconds = 5;
        public boolean autoOpenMenu = true;
        public boolean autoClickSubServer = true;
        public boolean autoResumeTask = true;
        public int clockSlot = 4;
    }

    private static Path getConfigPath() {
        Path baseDir = Paths.get(".");
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().gameDirectory != null) {
                baseDir = Minecraft.getInstance().gameDirectory.toPath();
            }
        } catch (Throwable ignored) {}
        return baseDir.resolve("baritone").resolve(FILE_NAME);
    }

    public static synchronized void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    public static synchronized void load() {
        try {
            Path file = getConfigPath();
            if (Files.exists(file)) {
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    ConfigData data = GSON.fromJson(reader, ConfigData.class);
                    if (data != null) {
                        applyFromData(data);
                    }
                }
            } else {
                save();
            }
        } catch (Throwable t) {
            Helper.HELPER.logDirect("§c[AutoRejoinConfig] Lỗi khi đọc file cấu hình autorejoin.json: " + t.getMessage());
        } finally {
            loaded = true;
        }
    }

    private static void applyFromData(ConfigData data) {
        password = data.password != null ? data.password : "";
        enabled = data.enabled && hasPassword();
        serverIp = data.serverIp != null && !data.serverIp.trim().isEmpty() ? data.serverIp.trim() : "kingmc.vn";
        targetSubServer = data.targetSubServer != null && !data.targetSubServer.trim().isEmpty() ? data.targetSubServer.trim() : "KingSMP";
        reconnectDelaySeconds = data.reconnectDelaySeconds > 0 ? data.reconnectDelaySeconds : 5;
        autoOpenMenu = data.autoOpenMenu;
        autoClickSubServer = data.autoClickSubServer;
        autoResumeTask = data.autoResumeTask;
        clockSlot = (data.clockSlot >= 0 && data.clockSlot < 9) ? data.clockSlot : 4;
    }

    public static synchronized void save() {
        try {
            Path file = getConfigPath();
            if (file.getParent() != null && !Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }

            ConfigData data = new ConfigData();
            data.enabled = enabled;
            data.password = password != null ? password : "";
            data.serverIp = serverIp != null ? serverIp : "kingmc.vn";
            data.targetSubServer = targetSubServer != null ? targetSubServer : "KingSMP";
            data.reconnectDelaySeconds = reconnectDelaySeconds;
            data.autoOpenMenu = autoOpenMenu;
            data.autoClickSubServer = autoClickSubServer;
            data.autoResumeTask = autoResumeTask;
            data.clockSlot = clockSlot;

            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (Throwable t) {
            Helper.HELPER.logDirect("§c[AutoRejoinConfig] Lỗi khi lưu file cấu hình autorejoin.json: " + t.getMessage());
        }
    }
}
