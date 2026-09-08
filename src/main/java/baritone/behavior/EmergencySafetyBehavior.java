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
import baritone.api.event.events.TickEvent;
import baritone.api.utils.BaritoneFileLogger;
import baritone.api.utils.Helper;
import baritone.api.utils.SettingsUtil;
import baritone.utils.AutoLogoutTracker;
import baritone.utils.AutoMineConfig;
import baritone.utils.AutoMineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import baritone.api.event.events.type.EventState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Hành vi Bảo hộ Khẩn cấp (Emergency Safety Behavior).
 * Tự động ngắt kết nối / Logout ngay lập tức khi:
 * 1. Rơi vào hồ Lava và không còn bất kỳ Totem nào.
 * 2. Máu tụt còn nửa thanh (<= 50% max HP hoặc <= 10 HP) và không còn bất kỳ Totem nào.
 * Nhằm bảo toàn tuyệt đối 100% trang bị và tính mạng của người chơi.
 */
public final class EmergencySafetyBehavior extends Behavior implements Helper {

    private int tickCount = 0;
    private int lavaTicks = 0;

    public EmergencySafetyBehavior(Baritone baritone) {
        super(baritone);
    }

    public static boolean isTouchingLava(baritone.api.utils.IPlayerContext ctx) {
        if (ctx == null || ctx.player() == null || ctx.world() == null) {
            return false;
        }

        // 1. Kiểm tra cờ vanilla của Player
        if (ctx.player().isInLava() || ctx.player().isEyeInFluid(FluidTags.LAVA)) {
            return true;
        }

        // 2. Kiểm tra khối ngay dưới chân (playerFeet) và khối bên dưới 1 ô
        if (ctx.world().getFluidState(ctx.playerFeet()).is(FluidTags.LAVA)
                || ctx.world().getBlockState(ctx.playerFeet()).is(Blocks.LAVA)
                || ctx.world().getFluidState(ctx.playerFeet().below()).is(FluidTags.LAVA)
                || ctx.world().getBlockState(ctx.playerFeet().below()).is(Blocks.LAVA)) {
            return true;
        }

        // 3. Quét toàn bộ hộp va chạm AABB của người chơi mở rộng xuống 0.2 block
        AABB box = ctx.player().getBoundingBox();
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY - 0.2);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (ctx.world().getFluidState(pos).is(FluidTags.LAVA)
                            || ctx.world().getBlockState(pos).is(Blocks.LAVA)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT || event.getState() == EventState.PRE) {
            return;
        }

        tickCount++;

        // Thời gian chờ an toàn (Grace Period) sau khi vừa vào lại thế giới
        if (AutoLogoutTracker.getJoinGraceTicks() > 0) {
            AutoLogoutTracker.decrementJoinGraceTicks();
            return;
        }

        boolean checkDanger = Baritone.settings().autoLogoutOnDanger.value;
        boolean checkPlayer = Baritone.settings().autoLogoutOnPlayer.value;
        if (!checkDanger && !checkPlayer) {
            lavaTicks = 0;
            return;
        }
        if (ctx.player() == null || ctx.world() == null) {
            lavaTicks = 0;
            return;
        }
        // Không logout nếu đang ở chế độ Sáng tạo (Creative) hoặc Khán giả (Spectator)
        if (ctx.player().isCreative() || ctx.player().isSpectator()) {
            lavaTicks = 0;
            return;
        }

        // 1. Kiểm tra trạng thái rơi vào và thực sự bốc cháy trong hồ Lava (chỉ khi checkDanger bật):
        boolean hasFireResistance = ctx.player().hasEffect(MobEffects.FIRE_RESISTANCE);
        boolean touchingLava = isTouchingLava(ctx);
        boolean isBurning = ctx.player().isOnFire() || ctx.player().getRemainingFireTicks() > 0;

        if (checkDanger) {
            if (!hasFireResistance && touchingLava) {
                // Đang tiếp xúc trực tiếp trong hồ lava và không có kháng lửa: tích lũy nhanh
                lavaTicks = Math.min(100, lavaTicks + 1);
            } else if (!hasFireResistance && isBurning && lavaTicks > 0) {
                // Đang bốc cháy sau khi rời lava: giữ nguyên đếm, không decay ngay
            } else {
                // Đã an toàn: decay từ từ (-1 mỗi tick) chống nhảy/bobbing làm reset oan
                lavaTicks = Math.max(0, lavaTicks - 1);
            }
        } else {
            lavaTicks = 0;
        }

        String dangerReason = null;

        // TRƯỜNG HỢP 1: PHÁT HIỆN NGƯỜI CHƠI ĐẾN GẦN (KỂ CẢ DÙNG THUỐC TÀNG HÌNH / INVIS)
        // Ưu tiên cao nhất: nếu phát hiện player khác xâm nhập vùng an toàn -> Logout ngay lập tức!
        if (checkPlayer) {
            AutoLogoutTracker.DetectedPlayerInfo playerThreat = AutoLogoutTracker.scanForNearbyPlayer(ctx);
            if (playerThreat != null) {
                dangerReason = "Phát hiện người chơi: " + playerThreat.getFormattedDescription();
            }
        }

        // TRƯỜNG HỢP 2: LAVA (chỉ khi checkDanger bật và không có thuốc kháng lửa)
        if (dangerReason == null && checkDanger && !hasFireResistance) {
            // A. Khẩn cấp cực độ: Rơi vào Lava VÀ bị trừ máu (hurtTime > 0) hoặc máu <= 14 HP (sau 10 ticks = 0.5s)
            if (touchingLava && (ctx.player().hurtTime > 0 || ctx.player().getHealth() <= 14.0f) && lavaTicks >= 10) {
                dangerReason = "Bị rơi vào hồ LAVA và đang nhận sát thương liên tục!";
            }
            // B. Rơi vào hồ Lava quá 2 giây (40 ticks)
            else if (lavaTicks >= 40) {
                dangerReason = "Rơi vào hồ LAVA liên tục quá 2 giây!";
            }
        }

        // TRƯỜNG HỢP 3: MẤT MÁU NGUY HIỂM / QUÁI ĐÁNH / ĐÓI / TÉ NGÃ
        if (dangerReason == null && checkDanger) {
            int totemCount = getTotemCount();
            float health = ctx.player().getHealth();
            float maxHealth = ctx.player().getMaxHealth();
            float threshold = Baritone.settings().autoLogoutHealthThreshold.value;
            boolean lowHealth = (health <= maxHealth * threshold) || (health <= 10.0f);

            // Nếu đang trong lava hoặc đang cháy mà máu tụt nguy hiểm (<= 12 HP):
            // KICK NGAY LẬP TỨC kể cả CÒN TOTEM để không nổ totem oan uổng trong lửa/lava!
            if (!hasFireResistance && (touchingLava || isBurning) && health <= 12.0f) {
                dangerReason = "Bị bốc cháy/rơi vào Lava nguy kịch (Máu còn: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP, còn " + totemCount + " Totem)!";
            } else if (totemCount == 0 && lowHealth) {
                String cause = detectDamageCause(ctx);
                dangerReason = cause + " (Máu còn: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP) và ĐÃ HẾT TOTEM!";
            } else if (health <= 6.0f) {
                // Máu cực kỳ nguy kịch (<= 3 tim): Kể cả còn Totem cũng kick để bảo toàn tính mạng
                String cause = detectDamageCause(ctx);
                dangerReason = cause + " (Máu nguy kịch: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP)!";
            }
        }

        if (dangerReason != null) {
            lavaTicks = 0;
            AutoLogoutTracker.performAutoLogout(ctx, dangerReason);
        }
    }

    public static String detectDamageCause(baritone.api.utils.IPlayerContext ctx) {
        if (ctx == null || ctx.player() == null) return "Bị mất máu nguy hiểm";

        // 1. Quái vật gây sát thương gần nhất
        try {
            LivingEntity mob = ctx.player().getLastHurtByMob();
            if (mob != null) {
                return "Bị quái vật (" + mob.getDisplayName().getString() + ") tấn công";
            }
        } catch (Throwable ignored) {}

        // 2. Quái vật ở cự ly nguy hiểm sát người chơi (5 block)
        if (ctx.world() != null) {
            try {
                var monsters = ctx.world().getEntitiesOfClass(
                        Monster.class,
                        ctx.player().getBoundingBox().inflate(5.0)
                );
                if (!monsters.isEmpty()) {
                    return "Bị quái vật (" + monsters.get(0).getDisplayName().getString() + ") tấn công";
                }
            } catch (Throwable ignored) {}
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
            lavaTicks = 0;
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
