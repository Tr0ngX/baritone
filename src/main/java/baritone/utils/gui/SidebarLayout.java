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

/**
 * Chia panel thành sidebar trái + content phải kiểu LiquidBounce.
 *
 * Khi GUI hẹp (&lt;560px theo tọa độ GUI-scaled) thì bỏ sidebar, caller vẽ
 * tab ngang có cuộn thay thế để không ép 6 tab vào một hàng quá chật.
 */
public final class SidebarLayout {

    public static final int SIDEBAR_W = 118;
    public static final int SIDEBAR_GAP = 6;
    public static final int SIDEBAR_BUTTON_H = 28;

    public final int panelX;
    public final int panelW;
    public final boolean useSidebar;
    public final int contentX;
    public final int contentW;

    public SidebarLayout(int panelX, int panelW, int screenW) {
        this.panelX = panelX;
        this.panelW = panelW;
        this.useSidebar = screenW >= 560 && panelW >= 380;
        if (useSidebar) {
            this.contentX = panelX + SIDEBAR_W + SIDEBAR_GAP;
            this.contentW = panelW - SIDEBAR_W - SIDEBAR_GAP;
        } else {
            this.contentX = panelX;
            this.contentW = panelW;
        }
    }

    /**
     * Y của nút sidebar thứ i (6 nút dọc).
     */
    public int sidebarButtonY(int topY, int index) {
        return topY + index * (SIDEBAR_BUTTON_H + 2);
    }

    public int sidebarHeight(int count) {
        return count * (SIDEBAR_BUTTON_H + 2) - 2;
    }
}
