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

package baritone.utils.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Vị trí và tỉ lệ HUD đào quặng, lưu riêng trong hud.json.
 *
 * Load chịu được file thiếu/hỏng (dùng mặc định), validate số hữu hạn và
 * scale chỉ nhận 0.8 / 1.0 / 1.25 (giá trị lạ sẽ snap về gần nhất).
 * Ghi file an toàn qua file tạm + di chuyển nguyên tử, không bao giờ làm
 * crash luồng render khi I/O lỗi.
 */
public final class OreHudConfig {

    public static final float SCALE_SMALL = 0.8f;
    public static final float SCALE_MEDIUM = 1.0f;
    public static final float SCALE_LARGE = 1.25f;
    public static final int DEFAULT_X = 8;
    public static final int DEFAULT_Y = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "hud.json";

    public int x = DEFAULT_X;
    public int y = DEFAULT_Y;
    public float scale = SCALE_MEDIUM;
    public boolean collapsed = false;

    private static Path getConfigPath() {
        Path baseDir = Paths.get(".");
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().gameDirectory != null) {
                baseDir = Minecraft.getInstance().gameDirectory.toPath();
            }
        } catch (Throwable ignored) {}
        return baseDir.resolve("baritone").resolve(FILE_NAME);
    }

    /**
     * Đọc config, lỗi thì trả về mặc định chứ không ném exception.
     */
    public static OreHudConfig load() {
        OreHudConfig config = new OreHudConfig();
        try {
            Path file = getConfigPath();
            if (!Files.exists(file)) {
                return config;
            }
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                OreHudConfig data = GSON.fromJson(reader, OreHudConfig.class);
                if (data != null) {
                    config.x = data.x;
                    config.y = data.y;
                    config.scale = data.scale;
                    config.collapsed = data.collapsed;
                }
            }
        } catch (Throwable ignored) {}
        config.validate();
        return config;
    }

    /**
     * Ghi config, lỗi thì bỏ qua lặng lẽ (không crash render).
     */
    public void save() {
        try {
            validate();
            Path file = getConfigPath();
            if (file.getParent() != null && !Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {}
    }

    public void resetPosition() {
        x = DEFAULT_X;
        y = DEFAULT_Y;
        save();
    }

    /**
     * Ép scale về mức gần nhất trong 3 mức cho phép.
     */
    public void cycleScale() {
        if (scale < 0.9f) {
            scale = SCALE_MEDIUM;
        } else if (scale < 1.1f) {
            scale = SCALE_LARGE;
        } else {
            scale = SCALE_SMALL;
        }
        save();
    }

    public String scaleLabel() {
        if (scale < 0.9f) {
            return "Nhỏ 0.8x";
        } else if (scale < 1.1f) {
            return "Vừa 1.0x";
        }
        return "Lớn 1.25x";
    }

    private void validate() {
        if (!Float.isFinite(scale)) {
            scale = SCALE_MEDIUM;
        }
        float best = SCALE_MEDIUM;
        float bestDiff = Math.abs(scale - SCALE_MEDIUM);
        float[] allowed = new float[]{SCALE_SMALL, SCALE_MEDIUM, SCALE_LARGE};
        for (float candidate : allowed) {
            float diff = Math.abs(scale - candidate);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = candidate;
            }
        }
        scale = best;
        if (!isFiniteInt(x)) {
            x = DEFAULT_X;
        }
        if (!isFiniteInt(y)) {
            y = DEFAULT_Y;
        }
    }

    private static boolean isFiniteInt(int value) {
        return value > -100000 && value < 100000;
    }
}
