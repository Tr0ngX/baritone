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
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.utils.Helper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Hành vi Anti-Aim (Cấu hình cố định: Spin Right 90° & UpAndDown 90° Instant).
 * Xoay góc nhìn của nhân vật trên Server để đánh lạc hướng đối thủ, chống Aimbot/KillAura,
 * hỗ trợ chế độ Silent chống chóng mặt camera người chơi.
 */
public final class AntiAimBehavior extends Behavior implements Helper {

    private float currentYaw = 0.0f;
    private float currentPitch = 0.0f;

    private float prevYaw = 0.0f;
    private float prevPitch = 0.0f;
    private boolean hasModifiedRotation = false;

    private boolean pitchingUp = true;

    public AntiAimBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!Baritone.settings().antiAim.value) {
            hasModifiedRotation = false;
            return;
        }

        if (ctx == null || ctx.player() == null || ctx.world() == null) {
            hasModifiedRotation = false;
            return;
        }

        // Nếu Baritone đang chủ động nhắm mục tiêu để đập block hoặc đặt block:
        // Tạm nhường quyền cho LookBehavior để không làm hỏng thao tác đào / xây dựng
        if (baritone.getLookBehavior().hasTarget()) {
            hasModifiedRotation = false;
            return;
        }

        // Nếu người chơi đang tự tay đào block bằng chuột trái:
        // Tạm dừng để server raycast đào block chính xác không bị trượt
        if (ctx.minecraft().gameMode != null && ((baritone.utils.accessor.IPlayerControllerMP) ctx.minecraft().gameMode).isHittingBlock()) {
            hasModifiedRotation = false;
            return;
        }

        LocalPlayer player = ctx.player();

        switch (event.getState()) {
            case PRE: {
                this.prevYaw = player.getYRot();
                this.prevPitch = player.getXRot();

                updateRotations(player);

                player.setYRot(this.currentYaw);
                player.setXRot(this.currentPitch);
                this.hasModifiedRotation = true;
                break;
            }
            case POST: {
                if (this.hasModifiedRotation) {
                    if (Baritone.settings().antiAimSilent.value) {
                        // Khôi phục góc quay visual cho người chơi để camera không bị chóng mặt
                        player.setYRot(this.prevYaw);
                        player.setXRot(this.prevPitch);

                        // Cập nhật góc quay đầu và thân (hiển thị khi F5 và cho các animation)
                        player.setYHeadRot(this.currentYaw);
                        player.yHeadRotO = this.currentYaw;
                        player.setYBodyRot(this.currentYaw);
                    }
                    this.hasModifiedRotation = false;
                }
                break;
            }
            default:
                break;
        }
    }

    private void updateRotations(LocalPlayer player) {
        // CẤU HÌNH CỐ ĐỊNH DUY NHẤT (LOCKED 1 CONFIG):
        // - Yaw Mode: Spin (Right, 90° / tick)
        // - Pitch Mode: UpAndDown (90° / tick)
        // - Instant Rotation: true
        currentYaw = Mth.wrapDegrees(currentYaw + 90.0f);

        if (pitchingUp) {
            currentPitch -= 90.0f;
            if (currentPitch <= -90.0f) {
                currentPitch = -90.0f;
                pitchingUp = false;
            }
        } else {
            currentPitch += 90.0f;
            if (currentPitch >= 90.0f) {
                currentPitch = 90.0f;
                pitchingUp = true;
            }
        }
        currentPitch = Mth.clamp(currentPitch, -90.0f, 90.0f);
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        currentYaw = 0.0f;
        currentPitch = 0.0f;
        hasModifiedRotation = false;
    }
}
