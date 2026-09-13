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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.BaritoneFileLogger;
import baritone.api.utils.Helper;
import baritone.utils.AutoLogoutTracker;
import baritone.api.event.events.type.EventState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Hành vi Bảo hộ Khẩn cấp (Emergency Safety Behavior).
 * Tự động ngắt kết nối / Logout ngay lập tức khi:
 * 1. Ở trong hồ lava liên tục quá 5 giây (LavaGuard packet-level + debounce logic).
 * 2. Máu tụt nguy kịch (<= 6 HP) hoặc máu thấp + hết Totem.
 * 3. Phát hiện người chơi khác đến gần.
 * Nhằm bảo toàn tuyệt đối 100% trang bị và tính mạng của người chơi.
 */
public final class EmergencySafetyBehavior extends Behavior implements Helper {

    private int tickCount = 0;
    private static final long MAX_LAVA_TIME_MS = 5000L; // Đúng 5 giây ngâm mình trong dung nham theo yêu cầu người dùng
    // Thời điểm bắt đầu dính hồ lava hoặc nhận sát thương lava
    private static volatile long lavaStartTime = 0L;
    // Thời điểm gần nhất nhận sát thương dung nham từ server
    private static volatile long lastLavaDamageTime = 0L;
    // Thông tin chi tiết quái vật tấn công gần nhất
    private static volatile String lastMobThreatInfo = null;
    private static volatile long lastMobThreatTime = 0L;

    public EmergencySafetyBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onReceivePacket(PacketEvent event) {
        if (event.getState() != EventState.PRE) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundDamageEventPacket damagePacket) {
            handleDamagePacket(damagePacket);
        } else if (packet instanceof ClientboundSetHealthPacket healthPacket) {
            handleHealthPacket(healthPacket);
        }
    }

    private void handleDamagePacket(ClientboundDamageEventPacket packet) {
        if (ctx.player() == null) return;
        if (packet.entityId() != ctx.player().getId()) return;

        boolean isLavaOrFire = false;
        try {
            var sourceType = packet.sourceType();
            if (sourceType != null) {
                if (sourceType.is(DamageTypeTags.IS_FIRE)) {
                    isLavaOrFire = true;
                } else {
                    String path = sourceType.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                    if (path.contains("lava") || path.contains("fire")) {
                        isLavaOrFire = true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (isLavaOrFire) {
            long now = System.currentTimeMillis();
            lastLavaDamageTime = now;
            if (lavaStartTime == 0L) {
                lavaStartTime = now;
            }

            // Nếu đang bật AutoLogoutOnDanger, đang trong hồ dung nham và máu <= 6 HP: kick ngay!
            if (!Baritone.settings().neverKick.value && Baritone.settings().autoLogoutOnDanger.value) {
                if (ctx.player().getHealth() <= 6.0f && (ctx.player().isInLava() || (ctx.world() != null && isInsideLavaPool(ctx.world(), ctx.player())))) {
                    lavaStartTime = 0L;
                    AutoLogoutTracker.performAutoLogout(ctx, "Rơi vào HỒ DUNG NHAM và MÁU NGUY KỊCH (Server Damage: Fire/Lava)!");
                }
            }
        }

        // Nhận diện chi tiết quái vật gây sát thương từ gói tin packet
        try {
            int causeId = packet.sourceCauseId();
            int directId = packet.sourceDirectId();
            Entity attacker = null;
            if (ctx.world() != null) {
                if (causeId > 0) attacker = ctx.world().getEntity(causeId);
                if (attacker == null && directId > 0) attacker = ctx.world().getEntity(directId);
            }
            if (attacker instanceof LivingEntity mob && !(attacker instanceof Player)) {
                lastMobThreatInfo = formatMobInfo(ctx, mob);
                lastMobThreatTime = System.currentTimeMillis();
            }
        } catch (Throwable ignored) {}
    }

    private void handleHealthPacket(ClientboundSetHealthPacket packet) {
        if (ctx.player() == null) return;
        if (Baritone.settings().neverKick.value) return;
        if (!Baritone.settings().autoLogoutOnDanger.value) return;

        float serverHealth = packet.getHealth();
        long now = System.currentTimeMillis();
        boolean inLava = (ctx.world() != null && isInsideLavaPool(ctx.world(), ctx.player()))
                || (ctx.player() != null && ctx.player().isInLava())
                || (now - lastLavaDamageTime < 1500L);

        if (inLava && serverHealth <= 6.0f) {
            AutoLogoutTracker.performAutoLogout(ctx, "Đang ở trong HỒ LAVA và MÁU NGUY KỊCH (Server Health: " + String.format(java.util.Locale.ROOT, "%.1f", serverHealth) + " HP)!");
            lavaStartTime = 0L;
        } else if (serverHealth <= 6.0f) {
            String mobDetail = getDetailedMobThreatInfo(ctx);
            if (mobDetail != null) {
                AutoLogoutTracker.performAutoLogout(ctx, "Bị quái vật [" + mobDetail + "] tấn công NGUY KỊCH (Máu còn: " + String.format(java.util.Locale.ROOT, "%.1f", serverHealth) + " HP)!");
            } else {
                String cause = detectDamageCause(ctx);
                AutoLogoutTracker.performAutoLogout(ctx, cause + " (Máu nguy kịch: " + String.format(java.util.Locale.ROOT, "%.1f", serverHealth) + " HP)!");
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT || event.getState() == EventState.PRE) {
            return;
        }

        tickCount++;

        // Thời gian chờ an toàn (Grace Period) sau khi vừa vào lại thế giới (chỉ áp dụng chống quét Player ở spawn)
        boolean inGracePeriod = AutoLogoutTracker.getJoinGraceTicks() > 0;
        if (inGracePeriod) {
            AutoLogoutTracker.decrementJoinGraceTicks();
        }

        // Vệ binh bảo vệ: Không bao giờ kick khi bật neverKick
        if (Baritone.settings().neverKick.value) {
            lavaStartTime = 0L;
            return;
        }

        boolean checkDanger = Baritone.settings().autoLogoutOnDanger.value;
        boolean checkPlayer = Baritone.settings().autoLogoutOnPlayer.value;
        if (!checkDanger && !checkPlayer) {
            lavaStartTime = 0L;
            return;
        }
        if (ctx.player() == null || ctx.world() == null) {
            lavaStartTime = 0L;
            return;
        }
        // Không logout nếu đang ở chế độ Sáng tạo (Creative) hoặc Khán giả (Spectator)
        if (ctx.player().isCreative() || ctx.player().isSpectator()) {
            lavaStartTime = 0L;
            return;
        }

        String dangerReason = null;

        // =========================================================================
        // TRƯỜNG HỢP 1: LAVA GUARD - Ở TRONG HỒ LAVA (5 GIÂY HOẶC MÁU NGUY KỊCH)
        // TUYỆT ĐỐI KHÔNG BỊ CHẶN BỞI SAFE-ZONE / LOBBY
        // =========================================================================
        if (checkDanger) {
            long nowMs = System.currentTimeMillis();
            boolean currentlyInLava = ctx.player().isInLava() || isInsideLavaPool(ctx.world(), ctx.player());
            boolean recentLavaDamage = (nowMs - lastLavaDamageTime < 1500L);

            if (currentlyInLava || recentLavaDamage) {
                if (lavaStartTime == 0L) {
                    lavaStartTime = nowMs;
                }
                float curHealth = ctx.player().getHealth();

                if (nowMs - lavaStartTime >= MAX_LAVA_TIME_MS) {
                    lavaStartTime = 0L;
                    dangerReason = "Bạn đã ở trong hồ dung nham liên tục 5 giây!";
                } else if (curHealth <= 6.0f) {
                    lavaStartTime = 0L;
                    dangerReason = "Đang ở trong HỒ LAVA và MÁU NGUY KỊCH (còn " + String.format(java.util.Locale.ROOT, "%.1f", curHealth) + " HP)!";
                }
            } else {
                // Thoát hoàn toàn khỏi lava và không còn dính sát thương lửa quá 1.5s mới reset timer
                lavaStartTime = 0L;
            }
        } else {
            lavaStartTime = 0L;
        }

        // =========================================================================
        // TRƯỜNG HỢP 2: MẤT MÁU NGUY HIỂM / QUÁI ĐÁNH / ĐÓI / TÉ NGÃ / HẾT TOTEM
        // TUYỆT ĐỐI KHÔNG BỊ CHẶN BỞI SAFE-ZONE / LOBBY (LOG RÕ CON QUÁI VẬT)
        // =========================================================================
        if (dangerReason == null && checkDanger) {
            int totemCount = getTotemCount();
            float health = ctx.player().getHealth();
            float maxHealth = ctx.player().getMaxHealth();
            float threshold = Baritone.settings().autoLogoutHealthThreshold.value;
            boolean lowHealth = (health <= maxHealth * threshold) || (health <= 10.0f);

            String mobDetail = getDetailedMobThreatInfo(ctx);

            if (totemCount == 0 && lowHealth) {
                if (mobDetail != null) {
                    dangerReason = "Bị quái vật [" + mobDetail + "] tấn công và ĐÃ HẾT TOTEM (Máu còn: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP)!";
                } else {
                    String cause = detectDamageCause(ctx);
                    dangerReason = cause + " (Máu còn: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP) và ĐÃ HẾT TOTEM!";
                }
            } else if (health <= 6.0f) {
                if (mobDetail != null) {
                    dangerReason = "Bị quái vật [" + mobDetail + "] tấn công NGUY KỊCH (Máu còn: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP)!";
                } else {
                    String cause = detectDamageCause(ctx);
                    dangerReason = cause + " (Máu nguy kịch: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP)!";
                }
            }
        }

        // =========================================================================
        // TRƯỜNG HỢP 3: PHÁT HIỆN NGƯỜI CHƠI ĐẾN GẦN (KỂ CẢ DÙNG THUỐC TÀNG HÌNH / INVIS)
        // (Chỉ trường hợp này mới kiểm tra Safezone / Lobby để không kick khi ở sảnh)
        // =========================================================================
        if (dangerReason == null && checkPlayer && !inGracePeriod) {
            boolean inSafezone = AutoLogoutTracker.isInLobbyOrSafezone(ctx);
            boolean onlyMiningBlocked = Baritone.settings().autoLogoutOnlyWhileMining.value && !AutoLogoutTracker.isBaritoneBusyMining();

            if (!inSafezone && !onlyMiningBlocked) {
                AutoLogoutTracker.DetectedPlayerInfo playerThreat = AutoLogoutTracker.scanForNearbyPlayer(ctx);
                if (playerThreat != null) {
                    dangerReason = "Phát hiện người chơi: " + playerThreat.getFormattedDescription();
                }
            }
        }

        if (dangerReason != null) {
            lavaStartTime = 0L;
            AutoLogoutTracker.performAutoLogout(ctx, dangerReason);
        }
    }

    public static boolean isInsideLavaPool(BlockGetter world, Player player) {
        if (world == null || player == null) return false;
        if (player.isInLava()) return true;

        double x = player.getX();
        double z = player.getZ();
        double feetY = player.getBoundingBox().minY + 0.1;
        double bodyY = (player.getBoundingBox().minY + player.getBoundingBox().maxY) * 0.5;

        return isPointInsideLava(world, x, feetY, z) || isPointInsideLava(world, x, bodyY, z);
    }

    public static boolean isPointInsideLava(
            BlockGetter world,
            double x,
            double y,
            double z
    ) {
        BlockPos pos = BlockPos.containing(x, y, z);
        FluidState fluid = world.getFluidState(pos);

        if (!fluid.is(FluidTags.LAVA)) {
            return false;
        }

        // Lava chảy có thể không cao hết 1 block.
        // Phải kiểm tra mặt chất lỏng thật, không chỉ loại block.
        double lavaSurfaceY = pos.getY() + fluid.getHeight(world, pos);

        return y < lavaSurfaceY - 0.001;
    }

    public static String formatMobInfo(baritone.api.utils.IPlayerContext ctx, LivingEntity mob) {
        if (mob == null || ctx == null || ctx.player() == null) return null;
        try {
            String customName = mob.getCustomName() != null ? mob.getCustomName().getString() : null;
            String typeName = mob.getType().getDescription().getString();
            String displayName = (customName != null && !customName.isEmpty()) ? customName : mob.getDisplayName().getString();

            double dist = ctx.player().distanceTo(mob);
            double mx = mob.getX();
            double my = mob.getY();
            double mz = mob.getZ();
            float mobHp = mob.getHealth();
            float mobMaxHp = mob.getMaxHealth();

            StringBuilder sb = new StringBuilder();
            sb.append(displayName);
            if (!displayName.equalsIgnoreCase(typeName)) {
                sb.append(" (").append(typeName).append(")");
            }
            sb.append(String.format(java.util.Locale.ROOT, " • Cách: %.1fm • Vị trí: [%.1f, %.1f, %.1f]", dist, mx, my, mz));
            if (mobMaxHp > 0) {
                sb.append(String.format(java.util.Locale.ROOT, " • Máu: %.0f/%.0f HP", mobHp, mobMaxHp));
            }
            return sb.toString();
        } catch (Throwable t) {
            return mob.getName().getString();
        }
    }

    public static String getDetailedMobThreatInfo(baritone.api.utils.IPlayerContext ctx) {
        if (ctx == null || ctx.player() == null) return null;

        // 1. Quái vật gây sát thương gần nhất từ gói tin DamagePacket trong vòng 3 giây
        long now = System.currentTimeMillis();
        if (lastMobThreatInfo != null && (now - lastMobThreatTime < 3000L)) {
            return lastMobThreatInfo;
        }

        // 2. getLastHurtByMob từ entity Player
        try {
            LivingEntity mob = ctx.player().getLastHurtByMob();
            if (mob != null && mob.isAlive()) {
                return formatMobInfo(ctx, mob);
            }
        } catch (Throwable ignored) {}

        // 3. getLastAttacker
        try {
            LivingEntity attacker = ctx.player().getLastAttacker();
            if (attacker != null && attacker.isAlive()) {
                return formatMobInfo(ctx, attacker);
            }
        } catch (Throwable ignored) {}

        // 4. Quét tìm quái vật gần nhất trong phạm vi 8 block
        if (ctx.world() != null) {
            try {
                var monsters = ctx.world().getEntitiesOfClass(
                        Monster.class,
                        ctx.player().getBoundingBox().inflate(8.0)
                );
                Monster closest = null;
                double minDist = Double.MAX_VALUE;
                for (Monster m : monsters) {
                    if (m != null && m.isAlive()) {
                        double d = ctx.player().distanceTo(m);
                        if (d < minDist) {
                            minDist = d;
                            closest = m;
                        }
                    }
                }
                if (closest != null) {
                    return formatMobInfo(ctx, closest);
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public static String detectDamageCause(baritone.api.utils.IPlayerContext ctx) {
        if (ctx == null || ctx.player() == null) return "Bị mất máu nguy hiểm";

        // 1. Quái vật gây sát thương gần nhất (chi tiết tên, loại, tọa độ, khoảng cách)
        String mobInfo = getDetailedMobThreatInfo(ctx);
        if (mobInfo != null) {
            return "Bị quái vật [" + mobInfo + "] tấn công";
        }

        // 3. Đói ăn kiệt sức (thanh đói = 0)
        try {
            if (ctx.player().getFoodData().getFoodLevel() == 0) {
                return "Bị ĐÓI ĂN kiệt sức (Thanh đói = 0)";
            }
        } catch (Throwable ignored) {}

        // 4. Bị té ngã từ trên cao (Fall Damage)
        try {
            if (ctx.player().fallDistance > 2.0f) {
                return "Bị TÉ NGÃ từ trên cao (Fall Damage)";
            }
        } catch (Throwable ignored) {}

        // 5. Bị bốc cháy / lửa thiêu
        try {
            if (ctx.player().isOnFire()) {
                return "Bị BỐC CHÁY (Lửa thiêu)";
            }
        } catch (Throwable ignored) {}

        // 6. Chết đuối / ngạt nước
        try {
            if (ctx.player().getAirSupply() <= 0) {
                return "Bị CHẾT ĐUỐI (Ngạt nước)";
            }
        } catch (Throwable ignored) {}

        // 7. Ngạt thở trong tường
        try {
            if (ctx.player().isInWall()) {
                return "Bị NGẠT THỞ trong tường";
            }
        } catch (Throwable ignored) {}

        // 8. DamageSource từ Minecraft nếu có
        try {
            DamageSource src = ctx.player().getLastDamageSource();
            if (src != null) {
                String msgId = src.getMsgId();
                if (msgId != null) {
                    if (msgId.contains("mob") || msgId.contains("arrow")) {
                        return "Bị quái vật tấn công";
                    } else if (msgId.contains("fall")) {
                        return "Bị TÉ NGÃ từ trên cao (Fall Damage)";
                    } else if (msgId.contains("starve")) {
                        return "Bị ĐÓI ĂN kiệt sức";
                    } else if (msgId.contains("explosion")) {
                        return "Bị NỔ sát thương";
                    } else if (msgId.contains("fire") || msgId.contains("lava")) {
                        return "Bị BỐC CHÁY (Lửa thiêu)";
                    } else if (msgId.contains("drown")) {
                        return "Bị CHẾT ĐUỐI";
                    }
                }
            }
        } catch (Throwable ignored) {}

        return "Bị mất máu nguy hiểm";
    }

    @Override
    public void onWorldEvent(baritone.api.event.events.WorldEvent event) {
        if (event.getWorld() == null) {
            lavaStartTime = 0L;
            lastLavaDamageTime = 0L;
        } else {
            AutoLogoutTracker.onWorldJoined();
        }
    }

    private int getTotemCount() {
        if (ctx.player() == null) return 0;
        int count = 0;
        // Kiểm tra tay phụ (Offhand)
        ItemStack offhand = ctx.player().getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offhand.isEmpty() && offhand.is(Items.TOTEM_OF_UNDYING)) {
            count += offhand.getCount();
        }
        // Kiểm tra tay chính (Mainhand)
        ItemStack mainhand = ctx.player().getMainHandItem();
        if (!mainhand.isEmpty() && mainhand.is(Items.TOTEM_OF_UNDYING)) {
            count += mainhand.getCount();
        }
        // Kiểm tra toàn bộ balo và hotbar
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (ItemStack stack : inv) {
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
