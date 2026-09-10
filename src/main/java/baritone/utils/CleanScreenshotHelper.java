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

import baritone.api.utils.BaritoneFileLogger;
import baritone.api.utils.Helper;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Helper chụp ảnh màn hình Siêu Sạch (Clean Screenshot Capture).
 * Chụp framebuffer ngay tại thời điểm render xong Thế giới + HUD Thống kê đào khoáng,
 * TRƯỚC KHI bất kỳ Screen nào (Game Menu ESC, Túi đồ E, GUI F4) được vẽ đè lên.
 */
public final class CleanScreenshotHelper implements Helper {

    private static final AtomicBoolean CAPTURE_REQUESTED = new AtomicBoolean(false);
    private static volatile Consumer<byte[]> pendingCallback = null;

    private CleanScreenshotHelper() {}

    /**
     * Yêu cầu chụp một bức ảnh màn hình sạch sẽ (không dính ESC / E / GUI).
     * Bức ảnh sẽ được snapshot ở frame render tiếp theo.
     *
     * @param callback Nhận mảng byte PNG của ảnh chụp sạch, hoặc null nếu timeout/thất bại.
     */
    public static void requestCleanScreenshot(Consumer<byte[]> callback) {
        pendingCallback = callback;
        CAPTURE_REQUESTED.set(true);

        // Đặt timeout 2.5s đề phòng trường hợp game đang ở trạng thái không render level
        Util.ioPool().execute(() -> {
            try {
                Thread.sleep(2500L);
                if (CAPTURE_REQUESTED.compareAndSet(true, false)) {
                    Consumer<byte[]> cb = pendingCallback;
                    pendingCallback = null;
                    if (cb != null) {
                        BaritoneFileLogger.warn("[CleanScreenshot] Timeout 2.5s khi chờ chụp màn hình sạch.");
                        cb.accept(null);
                    }
                }
            } catch (InterruptedException ignored) {}
        });
    }

    /**
     * Kiểm tra xem hiện tại có đang yêu cầu chụp ảnh sạch hay không.
     */
    public static boolean isCaptureRequested() {
        return CAPTURE_REQUESTED.get();
    }

    /**
     * Thực hiện snapshot trực tiếp trên Render Thread ngay sau khi vẽ xong In-Game HUD + OreHudOverlay
     * và trước khi Screen (ESC / E / GUI) được render.
     */
    public static void captureOnRenderThread(RenderTarget renderTarget) {
        if (!CAPTURE_REQUESTED.compareAndSet(true, false)) {
            return;
        }

        Consumer<byte[]> callback = pendingCallback;
        pendingCallback = null;

        if (renderTarget == null || renderTarget.width <= 0 || renderTarget.height <= 0) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        try {
            Screenshot.takeScreenshot(renderTarget, nativeImage -> {
                if (nativeImage == null) {
                    if (callback != null) {
                        callback.accept(null);
                    }
                    return;
                }

                // Đẩy công việc ghi file và đọc byte sang IO thread
                Util.ioPool().execute(() -> {
                    byte[] imageBytes = null;
                    try {
                        Minecraft mc = Minecraft.getInstance();
                        File screenshotsDir = new File(mc.gameDirectory, "screenshots");
                        if (!screenshotsDir.exists()) {
                            screenshotsDir.mkdirs();
                        }
                        File targetFile = new File(screenshotsDir, "discord_clean_farm_report.png");
                        nativeImage.writeToFile(targetFile);
                        imageBytes = Files.readAllBytes(targetFile.toPath());
                        BaritoneFileLogger.info("[CleanScreenshot] Đã chụp ảnh màn hình sạch thành công: " + targetFile.getName());
                    } catch (Throwable t) {
                        BaritoneFileLogger.error("[CleanScreenshot] Lỗi xử lý ảnh chụp sạch: " + t.getMessage());
                    } finally {
                        try {
                            nativeImage.close();
                        } catch (Throwable ignored) {}
                    }

                    if (callback != null) {
                        callback.accept(imageBytes);
                    }
                });
            });
        } catch (Throwable t) {
            BaritoneFileLogger.error("[CleanScreenshot] Lỗi snapshot framebuffer: " + t.getMessage());
            if (callback != null) {
                callback.accept(null);
            }
        }
    }
}
