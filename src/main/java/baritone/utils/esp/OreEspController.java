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

package baritone.utils.esp;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.Helper;
import baritone.api.cache.ICachedWorld;
import baritone.api.utils.gui.BaritoneToast;
import baritone.utils.MiningStatsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chu so huu duy nhat cho trang thai Ore ESP.
 *
 * Mọi nơi (GUI, lệnh #set, chế độ farm nặng, tự tắt) đều đi qua controller này,
 * không ai tự ý vẽ ESP riêng. ESP là trạng thái PHIÊN: đổi world, disconnect
 * hay khởi động lại đều tắt, không bao giờ tự bật lại.
 *
 * Luồng dữ liệu: tick (20 tick/lần) đọc cache có sẵn rồi chốt snapshot,
 * render mỗi frame chỉ đọc snapshot, không truy vấn gì thêm.
 */
public final class OreEspController implements AbstractGameEventListener {

    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 24;
    public static final int MAX_BOXES_HARD_CAP = 128;

    private static final int REFRESH_TICKS = 20;
    private static final int SESSION_BLOCK_THRESHOLD = 5000;
    private static final int LOW_FPS_THRESHOLD = 30;
    private static final int LOW_FPS_TICKS_REQUIRED = 100; // ~5 giay
    private static final int REGION_RING = 1; // 3x3 region quanh player, du bao bien region
    private static final Color FALLBACK_COLOR = new Color(0x38BDF8);

    /**
     * Một hộp ESP: vị trí block bất biến + màu quặng.
     */
    public static final class EspBox {
        public final BlockPos pos;
        public final Color color;

        EspBox(BlockPos pos, Color color) {
            this.pos = pos;
            this.color = color;
        }
    }

    private final Baritone baritone;
    private volatile List<EspBox> snapshot = Collections.emptyList();
    private int lowFpsTicks = 0;
    private boolean autoDisableNotified = false;
    private boolean lastFieldValue = false;

    public OreEspController(Baritone baritone) {
        this.baritone = baritone;
        baritone.getGameEventHandler().registerEventListener(this);
        new OreEspRenderer(baritone, this);
    }

    /**
     * Chỉ Baritone chính mới render ESP, tránh vẽ trùng khi có nhiều instance.
     */
    public boolean isPrimary() {
        try {
            return baritone == BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * ESP có đang bật và được phép vẽ không.
     */
    public boolean isActive() {
        try {
            return isPrimary() && Baritone.settings().oreEspEnabled.value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Bật/tắt ESP qua controller. Trả về trạng thái thực tế sau khi áp dụng
     * (chế độ farm nặng có thể từ chối lệnh bật).
     */
    public boolean setEnabled(boolean on) {
        try {
            if (on && Baritone.settings().heavyFarmMode.value) {
                return false;
            }
            Baritone.settings().oreEspEnabled.value = on;
            lastFieldValue = on;
            if (on) {
                autoDisableNotified = false;
                lowFpsTicks = 0;
            } else {
                snapshot = Collections.emptyList();
                lowFpsTicks = 0;
            }
            return on;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Snapshot hiện tại cho renderer. Không bao giờ null.
     */
    public List<EspBox> getSnapshot() {
        return snapshot;
    }

    public int getSnapshotSize() {
        return snapshot.size();
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }
        try {
            boolean field = Baritone.settings().oreEspEnabled.value;
            // Phát hiện lệnh #set ghi field trực tiếp: ép qua cùng luật với GUI.
            if (field != lastFieldValue) {
                lastFieldValue = field;
                if (field) {
                    if (Baritone.settings().heavyFarmMode.value) {
                        Baritone.settings().oreEspEnabled.value = false;
                        lastFieldValue = false;
                        field = false;
                    } else {
                        autoDisableNotified = false;
                        lowFpsTicks = 0;
                    }
                } else {
                    snapshot = Collections.emptyList();
                    lowFpsTicks = 0;
                }
            }
            if (!field || !isPrimary()) {
                return;
            }
            // Chế độ farm nặng ép tắt ngay cả khi đang bật.
            if (Baritone.settings().heavyFarmMode.value) {
                setEnabled(false);
                notifyDisabled("Chế độ farm nặng đang bật");
                autoDisableNotified = true;
                return;
            }
            if (event.getCount() % REFRESH_TICKS == 0) {
                rebuildSnapshot();
            }
            checkAutoDisable();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        // Đổi world / disconnect / join world mới: tắt ESP, xóa snapshot, reset phiên.
        // Tắt lặng lẽ vì đây là hành vi mong đợi, không phải cảnh báo.
        try {
            Baritone.settings().oreEspEnabled.value = false;
        } catch (Throwable ignored) {}
        lastFieldValue = false;
        snapshot = Collections.emptyList();
        lowFpsTicks = 0;
        autoDisableNotified = false;
    }

    /**
     * Điều kiện tự tắt khi AFK farm lớn: đang mine và (đào >= 5000 block/phiên
     * hoặc FPS < 30 liên tục ít nhất 5 giây). Chỉ báo một lần mỗi lần tự tắt.
     */
    private void checkAutoDisable() {
        boolean mining;
        try {
            mining = baritone.getMineProcess().isActive();
        } catch (Throwable ignored) {
            return;
        }
        if (!mining) {
            return;
        }
        int sessionMined = MiningStatsTracker.getInstance().getTotalBlocksMined();
        int fps = 60;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                fps = mc.getFps();
            }
        } catch (Throwable ignored) {}
        if (fps < LOW_FPS_THRESHOLD) {
            lowFpsTicks++;
        } else {
            lowFpsTicks = 0;
        }
        if (sessionMined >= SESSION_BLOCK_THRESHOLD || lowFpsTicks >= LOW_FPS_TICKS_REQUIRED) {
            setEnabled(false);
            if (!autoDisableNotified) {
                autoDisableNotified = true;
                if (sessionMined >= SESSION_BLOCK_THRESHOLD) {
                    notifyDisabled("đào hơn " + SESSION_BLOCK_THRESHOLD + " block/phiên (chống lag khi farm lớn)");
                } else {
                    notifyDisabled("FPS dưới " + LOW_FPS_THRESHOLD + " liên tục 5 giây");
                }
            }
        }
    }

    private void notifyDisabled(String reason) {
        try {
            BaritoneToast.addOrUpdate(
                    Component.literal("Đã tự tắt ESP quặng"),
                    Component.literal(reason + ". Bật lại trong tab Quặng khi cần."));
            Helper.HELPER.logDirect("§e[ESP] Đã tự tắt: " + reason + ".");
        } catch (Throwable ignored) {}
    }

    /**
     * Đọc cache có sẵn (không live-scan, không ép load chunk), lọc theo bán kính
     * và chỉ lấy quặng đang được chọn. Chi phí truy vấn cũng bị giới hạn, không
     * chỉ giới hạn số box cuối cùng.
     */
    private void rebuildSnapshot() {
        Minecraft mc;
        Player player;
        try {
            mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) {
                snapshot = Collections.emptyList();
                return;
            }
            player = mc.player;
        } catch (Throwable ignored) {
            return;
        }
        int radius = clampRadius(Baritone.settings().oreEspRadius.value);
        int maxBoxes = clampBoxes(Baritone.settings().oreEspMaxBoxes.value);
        int candidateBudget = Math.min(maxBoxes * 4, MAX_BOXES_HARD_CAP * 4);

        // Chỉ truy vấn block của quặng đang được chọn, tên gom nhóm để đỡ lặp.
        Map<String, Color> nameToColor = new HashMap<>();
        try {
            for (MiningStatsTracker.OreType ore : MiningStatsTracker.OreType.values()) {
                if (!ore.isSelected()) {
                    continue;
                }
                Color color = new Color(ore.getColor());
                for (Block block : ore.getMatchingBlocks()) {
                    String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
                    nameToColor.putIfAbsent(path, color);
                }
            }
        } catch (Throwable ignored) {
            return;
        }
        if (nameToColor.isEmpty()) {
            snapshot = Collections.emptyList();
            return;
        }

        ICachedWorld cachedWorld;
        try {
            cachedWorld = baritone.getPlayerContext().worldData().getCachedWorld();
            if (cachedWorld == null) {
                snapshot = Collections.emptyList();
                return;
            }
        } catch (Throwable ignored) {
            return;
        }

        int px = player.blockPosition().getX();
        int py = player.blockPosition().getY();
        int pz = player.blockPosition().getZ();
        long radiusSq = (long) radius * radius;
        Set<BlockPos> seen = new HashSet<>();
        List<EspBox> boxes = new ArrayList<>();
        try {
            for (Map.Entry<String, Color> entry : nameToColor.entrySet()) {
                if (boxes.size() >= maxBoxes) {
                    break;
                }
                List<BlockPos> found = cachedWorld.getLocationsOf(
                        entry.getKey(), candidateBudget, px, pz, REGION_RING);
                if (found == null) {
                    continue;
                }
                int taken = 0;
                for (BlockPos pos : found) {
                    if (boxes.size() >= maxBoxes || taken >= candidateBudget) {
                        break;
                    }
                    taken++;
                    long dx = (long) pos.getX() - px;
                    long dy = (long) pos.getY() - py;
                    long dz = (long) pos.getZ() - pz;
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;
                    }
                    BlockPos immutable = pos.immutable();
                    if (!seen.add(immutable)) {
                        continue;
                    }
                    boxes.add(new EspBox(immutable, entry.getValue()));
                }
            }
        } catch (Throwable ignored) {
            return;
        }
        // Ưu tiên quặng gần player trước.
        boxes.sort(Comparator.comparingLong(b -> {
            long dx = (long) b.pos.getX() - px;
            long dy = (long) b.pos.getY() - py;
            long dz = (long) b.pos.getZ() - pz;
            return dx * dx + dy * dy + dz * dz;
        }));
        if (boxes.size() > maxBoxes) {
            boxes = new ArrayList<>(boxes.subList(0, maxBoxes));
        }
        snapshot = Collections.unmodifiableList(boxes);
    }

    public static int clampRadius(int radius) {
        if (radius < MIN_RADIUS) {
            return MIN_RADIUS;
        }
        return Math.min(radius, MAX_RADIUS);
    }

    public static int clampBoxes(int boxes) {
        if (boxes < 1) {
            return 1;
        }
        return Math.min(boxes, MAX_BOXES_HARD_CAP);
    }

    Color fallbackColor() {
        return FALLBACK_COLOR;
    }
}
