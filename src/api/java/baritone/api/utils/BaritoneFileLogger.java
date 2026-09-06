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

package baritone.api.utils;

import net.minecraft.client.Minecraft;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Trình ghi log chuyên biệt (Dedicated File Logger) dành riêng cho Baritone.
 * Ghi toàn bộ tiến trình, phát hiện kẹt, thống kê, tính toán đường đi và cảnh báo
 * ra tệp tin logs/baritone.log và baritone/baritone.log mà không trộn lẫn với log chung.
 */
public final class BaritoneFileLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)§[0-9a-fk-or]");

    private static final ConcurrentLinkedQueue<String> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static volatile BufferedWriter mainWriter;
    private static volatile BufferedWriter mirrorWriter;
    private static volatile Thread workerThread;

    private BaritoneFileLogger() {}

    public static synchronized void init() {
        if (INITIALIZED.get()) {
            return;
        }

        try {
            Path gameDir;
            try {
                if (Minecraft.getInstance() != null && Minecraft.getInstance().gameDirectory != null) {
                    gameDir = Minecraft.getInstance().gameDirectory.toPath();
                } else {
                    gameDir = Paths.get(".");
                }
            } catch (Throwable t) {
                gameDir = Paths.get(".");
            }

            // 1. Thư mục logs/ và file logs/baritone.log
            Path logsDir = gameDir.resolve("logs");
            Files.createDirectories(logsDir);
            Path primaryLog = logsDir.resolve("baritone.log");

            // Rollover: Nếu file đã tồn tại từ phiên chơi trước, đổi tên thành baritone-previous.log
            if (Files.exists(primaryLog) && Files.size(primaryLog) > 0) {
                try {
                    Path prevLog = logsDir.resolve("baritone-previous.log");
                    Files.move(primaryLog, prevLog, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }

            // 2. Thư mục baritone/ và file baritone/baritone.log
            Path baritoneDir = gameDir.resolve("baritone");
            Files.createDirectories(baritoneDir);
            Path secondaryLog = baritoneDir.resolve("baritone.log");

            mainWriter = Files.newBufferedWriter(primaryLog, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            try {
                mirrorWriter = Files.newBufferedWriter(secondaryLog, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {}

            INITIALIZED.set(true);

            // Bắt đầu luồng ghi nền (Daemon Thread) đảm bảo 0% gián đoạn game/render/pathfinding
            workerThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        drainQueue();
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Throwable ignored) {}
                }
                drainQueue();
            }, "Baritone-FileLogger");
            workerThread.setDaemon(true);
            workerThread.start();

            // Hook khi JVM tắt để flush toàn bộ dữ liệu còn lại trong hàng đợi
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    drainQueue();
                    closeWriters();
                } catch (Throwable ignored) {}
            }, "Baritone-FileLogger-Shutdown"));

            info("=== BẮT ĐẦU PHIÊN GHI LOG CHUYÊN BIỆT BARITONE (Minecraft 1.21.8) ===");
            info("Đường dẫn log chính: " + primaryLog.toAbsolutePath());
            if (secondaryLog != null) {
                info("Đường dẫn log phụ:   " + secondaryLog.toAbsolutePath());
            }
        } catch (Throwable t) {
            System.err.println("[BaritoneFileLogger] Không thể khởi tạo file log Baritone: " + t.getMessage());
        }
    }

    private static void drainQueue() {
        if (!INITIALIZED.get()) return;

        String line;
        boolean flushed = false;
        while ((line = QUEUE.poll()) != null) {
            try {
                if (mainWriter != null) {
                    mainWriter.write(line);
                    mainWriter.newLine();
                    flushed = true;
                }
                if (mirrorWriter != null) {
                    mirrorWriter.write(line);
                    mirrorWriter.newLine();
                }
            } catch (IOException ignored) {}
        }

        if (flushed) {
            try {
                if (mainWriter != null) mainWriter.flush();
                if (mirrorWriter != null) mirrorWriter.flush();
            } catch (IOException ignored) {}
        }
    }

    private static synchronized void closeWriters() {
        try {
            if (mainWriter != null) {
                mainWriter.flush();
                mainWriter.close();
                mainWriter = null;
            }
            if (mirrorWriter != null) {
                mirrorWriter.flush();
                mirrorWriter.close();
                mirrorWriter = null;
            }
        } catch (IOException ignored) {}
    }

    public static void log(String level, String message) {
        if (message == null) return;
        if (!INITIALIZED.get()) {
            init();
        }

        String clean = COLOR_CODE_PATTERN.matcher(message).replaceAll("").trim();
        if (clean.isEmpty()) return;

        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String thread = Thread.currentThread().getName();
        String entry = String.format("[%s] [%-5s] [%s] %s", timestamp, level, thread, clean);

        QUEUE.offer(entry);
    }

    public static void info(String message) {
        log("INFO", message);
    }

    public static void warn(String message) {
        log("WARN", message);
    }

    public static void error(String message) {
        log("ERROR", message);
    }

    public static void debug(String message) {
        log("DEBUG", message);
    }

    public static void error(String message, Throwable t) {
        log("ERROR", message);
        if (t != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            for (String line : sw.toString().split("\\R")) {
                if (!line.trim().isEmpty()) {
                    log("ERROR", "  " + line);
                }
            }
        }
    }
}
