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
import baritone.api.utils.Helper;
import baritone.api.utils.SettingsUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AutoMineConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "automine.json";
    private static volatile boolean loaded = false;

    private AutoMineConfig() {}

    public static class ConfigData {
        // === QUẶNG (Tab ORES) ===
        public boolean oreDiamond = true;
        public boolean oreLapis = true;
        public boolean oreRedstone = true;
        public boolean oreGold = false;
        public boolean oreIron = false;
        public boolean oreEmerald = true;
        public boolean oreDebris = false;
        public boolean oreCopper = false;
        public boolean oreCoal = false;
        public boolean oreQuartz = false;

        // === CÂY (Tab TREES) ===
        public boolean woodOak = true;
        public boolean woodBirch = true;
        public boolean woodSpruce = true;
        public boolean woodJungle = true;
        public boolean woodAcacia = true;
        public boolean woodDarkOak = true;
        public boolean woodMangrove = true;
        public boolean woodCherry = true;
        public boolean woodBamboo = true;
        public boolean woodCrimson = false;
        public boolean woodWarped = false;

        // === DI CHUYỂN (Tab MOVEMENT) ===
        public boolean optZeroDelay = true;
        public boolean optCrawlMode = false;
        public boolean optTunnelBhop = true;
        public boolean optShaftDown = true;
        public boolean optParkour = true;
        public boolean optAutoSprint = true;
        public boolean optOvershoot = true;
        public boolean optWaterSprint = true;
        public boolean optStrictOneDirection = true;
        public boolean clientFreeLook = false;

        // === SINH TỒN (Tab SURVIVAL) ===
        public boolean optAutoTool = true;
        public boolean optAutoEat = true;
        public boolean optAutoTotem = true;
        public boolean optAutoLogout = false; // Anti-Death
        public boolean autoLogoutOnPlayer = false; // Anti-Player
        public boolean optShulkerStorage = true;
        public boolean optAutoDrop = true;
        public boolean optMobAvoid = true;
        public boolean optWaterCheck = true;

        // === GIAO DIỆN (Tab HUD) ===
        public boolean optMiningStats = true;
        public boolean optHideSwing = false;
        public boolean optFastPlace = true;
        public boolean optStreamerMode = false;
        public boolean optHideScoreboard = false;
        public boolean optHidePlayerName = false;

        // === TẦNG Y MỤC TIÊU ===
        public int optTargetY = -54;
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
            Helper.HELPER.logDirect("§c[AutoMineConfig] Lỗi khi đọc file cấu hình automine.json: " + t.getMessage());
        } finally {
            loaded = true;
        }
    }

    private static void applyFromData(ConfigData data) {
        // Quặng
        AutoMineScreen.oreDiamond = data.oreDiamond;
        AutoMineScreen.oreLapis = data.oreLapis;
        AutoMineScreen.oreRedstone = data.oreRedstone;
        AutoMineScreen.oreGold = data.oreGold;
        AutoMineScreen.oreIron = data.oreIron;
        AutoMineScreen.oreEmerald = data.oreEmerald;
        AutoMineScreen.oreDebris = data.oreDebris;
        AutoMineScreen.oreCopper = data.oreCopper;
        AutoMineScreen.oreCoal = data.oreCoal;
        AutoMineScreen.oreQuartz = data.oreQuartz;

        // Cây
        AutoMineScreen.woodOak = data.woodOak;
        AutoMineScreen.woodBirch = data.woodBirch;
        AutoMineScreen.woodSpruce = data.woodSpruce;
        AutoMineScreen.woodJungle = data.woodJungle;
        AutoMineScreen.woodAcacia = data.woodAcacia;
        AutoMineScreen.woodDarkOak = data.woodDarkOak;
        AutoMineScreen.woodMangrove = data.woodMangrove;
        AutoMineScreen.woodCherry = data.woodCherry;
        AutoMineScreen.woodBamboo = data.woodBamboo;
        AutoMineScreen.woodCrimson = data.woodCrimson;
        AutoMineScreen.woodWarped = data.woodWarped;

        // Di chuyển
        AutoMineScreen.optZeroDelay = data.optZeroDelay;
        AutoMineScreen.optCrawlMode = data.optCrawlMode;
        AutoMineScreen.optTunnelBhop = data.optTunnelBhop;
        AutoMineScreen.optShaftDown = data.optShaftDown;
        AutoMineScreen.optParkour = data.optParkour;
        AutoMineScreen.optAutoSprint = data.optAutoSprint;
        AutoMineScreen.optOvershoot = data.optOvershoot;
        AutoMineScreen.optWaterSprint = data.optWaterSprint;
        AutoMineScreen.optStrictOneDirection = data.optStrictOneDirection;

        // Sinh tồn
        AutoMineScreen.optAutoTool = data.optAutoTool;
        AutoMineScreen.optAutoEat = data.optAutoEat;
        AutoMineScreen.optAutoTotem = data.optAutoTotem;
        AutoMineScreen.optAutoLogout = data.optAutoLogout;
        AutoMineScreen.optShulkerStorage = data.optShulkerStorage;
        AutoMineScreen.optAutoDrop = data.optAutoDrop;
        AutoMineScreen.optMobAvoid = data.optMobAvoid;
        AutoMineScreen.optWaterCheck = data.optWaterCheck;

        // Giao diện / HUD
        AutoMineScreen.optMiningStats = data.optMiningStats;
        AutoMineScreen.optHideSwing = data.optHideSwing;
        AutoMineScreen.optFastPlace = data.optFastPlace;
        AutoMineScreen.optStreamerMode = data.optStreamerMode;
        AutoMineScreen.optHideScoreboard = data.optHideScoreboard;
        AutoMineScreen.optHidePlayerName = data.optHidePlayerName;

        // Tầng Y
        AutoMineScreen.optTargetY = data.optTargetY;

        // Đồng bộ vào Baritone.settings()
        Baritone.settings().autoLogoutOnPlayer.value = data.autoLogoutOnPlayer;
        syncToBaritoneSettings(data.clientFreeLook);
    }

    public static void syncToBaritoneSettings(boolean clientFreeLook) {
        try {
            Baritone.settings().clientFreeLook.value = clientFreeLook;
            Baritone.settings().mineStrictOneDirection.value = AutoMineScreen.optStrictOneDirection;
            Baritone.settings().autoLogoutOnDanger.value = AutoMineScreen.optAutoLogout;
            Baritone.settings().autoTool.value = AutoMineScreen.optAutoTool;
            Baritone.settings().autoEat.value = AutoMineScreen.optAutoEat;
            Baritone.settings().autoBuyFood.value = AutoMineScreen.optAutoEat;
            Baritone.settings().autoTotem.value = AutoMineScreen.optAutoTotem;
            Baritone.settings().autoBuyTotem.value = AutoMineScreen.optAutoTotem;
            Baritone.settings().autoShulkerStorage.value = AutoMineScreen.optShulkerStorage;
            Baritone.settings().autoBuyShulker.value = AutoMineScreen.optShulkerStorage;
            Baritone.settings().autoDrop.value = AutoMineScreen.optAutoDrop;
            Baritone.settings().avoidance.value = AutoMineScreen.optMobAvoid;
            Baritone.settings().waterCheck.value = AutoMineScreen.optWaterCheck;
            Baritone.settings().streamerMode.value = AutoMineScreen.optStreamerMode;
            Baritone.settings().hideScoreboard.value = AutoMineScreen.optHideScoreboard;
            Baritone.settings().hidePlayerName.value = AutoMineScreen.optHidePlayerName;
            Baritone.settings().hideSwingAnimation.value = AutoMineScreen.optHideSwing;
            Baritone.settings().rightClickSpeed.value = AutoMineScreen.optFastPlace ? 1 : 4;
            Baritone.settings().allowParkour.value = AutoMineScreen.optParkour;
            Baritone.settings().allowParkourPlace.value = AutoMineScreen.optParkour;
            Baritone.settings().allowParkourAscend.value = AutoMineScreen.optParkour;
            Baritone.settings().allowDiagonalAscend.value = AutoMineScreen.optParkour;
            Baritone.settings().allowDiagonalDescend.value = AutoMineScreen.optParkour;
            Baritone.settings().crawlMineMode.value = AutoMineScreen.optCrawlMode;
            Baritone.settings().tunnelSprintJump.value = AutoMineScreen.optTunnelBhop;
            Baritone.settings().straightDownMine.value = AutoMineScreen.optShaftDown;
            Baritone.settings().allowSprint.value = AutoMineScreen.optAutoSprint;
            Baritone.settings().sprintAscends.value = AutoMineScreen.optAutoSprint;
            Baritone.settings().overshootTraverse.value = AutoMineScreen.optOvershoot;
            Baritone.settings().sprintInWater.value = AutoMineScreen.optWaterSprint;

            int targetY = AutoMineScreen.optTargetY == 999 ? -54 : AutoMineScreen.optTargetY;
            Baritone.settings().legitMineYLevel.value = targetY;
            Baritone.settings().exploreMaintainY.value = targetY;
        } catch (Throwable ignored) {}
    }

    public static synchronized void save() {
        try {
            Path file = getConfigPath();
            if (file.getParent() != null && !Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }

            ConfigData data = new ConfigData();
            // Quặng
            data.oreDiamond = AutoMineScreen.oreDiamond;
            data.oreLapis = AutoMineScreen.oreLapis;
            data.oreRedstone = AutoMineScreen.oreRedstone;
            data.oreGold = AutoMineScreen.oreGold;
            data.oreIron = AutoMineScreen.oreIron;
            data.oreEmerald = AutoMineScreen.oreEmerald;
            data.oreDebris = AutoMineScreen.oreDebris;
            data.oreCopper = AutoMineScreen.oreCopper;
            data.oreCoal = AutoMineScreen.oreCoal;
            data.oreQuartz = AutoMineScreen.oreQuartz;

            // Cây
            data.woodOak = AutoMineScreen.woodOak;
            data.woodBirch = AutoMineScreen.woodBirch;
            data.woodSpruce = AutoMineScreen.woodSpruce;
            data.woodJungle = AutoMineScreen.woodJungle;
            data.woodAcacia = AutoMineScreen.woodAcacia;
            data.woodDarkOak = AutoMineScreen.woodDarkOak;
            data.woodMangrove = AutoMineScreen.woodMangrove;
            data.woodCherry = AutoMineScreen.woodCherry;
            data.woodBamboo = AutoMineScreen.woodBamboo;
            data.woodCrimson = AutoMineScreen.woodCrimson;
            data.woodWarped = AutoMineScreen.woodWarped;

            // Di chuyển
            data.optZeroDelay = AutoMineScreen.optZeroDelay;
            data.optCrawlMode = AutoMineScreen.optCrawlMode;
            data.optTunnelBhop = AutoMineScreen.optTunnelBhop;
            data.optShaftDown = AutoMineScreen.optShaftDown;
            data.optParkour = AutoMineScreen.optParkour;
            data.optAutoSprint = AutoMineScreen.optAutoSprint;
            data.optOvershoot = AutoMineScreen.optOvershoot;
            data.optWaterSprint = AutoMineScreen.optWaterSprint;
            data.optStrictOneDirection = AutoMineScreen.optStrictOneDirection;
            try {
                data.clientFreeLook = Baritone.settings().clientFreeLook.value;
            } catch (Throwable ignored) {
                data.clientFreeLook = false;
            }

            // Sinh tồn
            data.optAutoTool = AutoMineScreen.optAutoTool;
            data.optAutoEat = AutoMineScreen.optAutoEat;
            data.optAutoTotem = AutoMineScreen.optAutoTotem;
            data.optShulkerStorage = AutoMineScreen.optShulkerStorage;
            data.optAutoDrop = AutoMineScreen.optAutoDrop;
            data.optMobAvoid = AutoMineScreen.optMobAvoid;
            data.optWaterCheck = AutoMineScreen.optWaterCheck;

            // Giao diện / HUD
            data.optMiningStats = AutoMineScreen.optMiningStats;
            data.optHideSwing = AutoMineScreen.optHideSwing;
            data.optFastPlace = AutoMineScreen.optFastPlace;
            data.optStreamerMode = AutoMineScreen.optStreamerMode;
            data.optHideScoreboard = AutoMineScreen.optHideScoreboard;
            data.optHidePlayerName = AutoMineScreen.optHidePlayerName;

            // Tầng Y
            data.optTargetY = AutoMineScreen.optTargetY;

            data.optAutoLogout = AutoMineScreen.optAutoLogout;
            data.autoLogoutOnPlayer = Baritone.settings().autoLogoutOnPlayer.value;

            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                GSON.toJson(data, writer);
            }

            // Đồng thời lưu vào file settings.txt của Baritone
            try {
                SettingsUtil.save(Baritone.settings());
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            Helper.HELPER.logDirect("§c[AutoMineConfig] Lỗi khi lưu file cấu hình automine.json: " + t.getMessage());
        }
    }
}
