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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Layout danh sách module full-width kiểu dropdown.
 *
 * Một nguồn sự thật duy nhất cho cả render và hit-test click: caller dựng
 * layout một lần rồi dùng cùng danh sách Entry cho cả hai, nên preset/filter/
 * scroll không bao giờ làm lệch hitbox. Phần ngoài vùng scissor không được
 * nhận click — caller tự kiểm tra Y nằm trong [contentY, contentBottom].
 */
public final class ModuleRowList {

    public static final int ROW_H = 26;
    public static final int ARROW_W = 20;
    public static final int SWITCH_W = 32;
    public static final int SWITCH_H = 16;
    public static final int GROUP_H = 16;
    public static final int ROW_GAP = 3;

    private ModuleRowList() {}

    /**
     * Vùng click trong một Entry.
     */
    public enum Zone {
        /** Mũi tên trái: expand/collapse. */
        ARROW,
        /** Pill switch phải: toggle duy nhất. */
        SWITCH,
        /** Thân row: expand/collapse, KHÔNG toggle. */
        BODY,
        /** Header nhóm hoặc vùng body dropdown: không làm gì. */
        NONE
    }

    /**
     * Một nhóm gồm header + các key module, giữ đúng thứ tự hiển thị.
     */
    public static final class Section {
        public final String header; // null = không vẽ header
        public final List<String> keys;

        public Section(String header, List<String> keys) {
            this.header = header;
            this.keys = keys;
        }
    }

    /**
     * Một dòng đã đặt vị trí: header nhóm hoặc row module (+body dropdown nếu mở).
     */
    public static final class Entry {
        public final String key; // null khi là header nhóm
        public final String groupTitle;
        public final boolean isHeader;
        public final int x;
        public final int y;
        public final int w;
        public final int h;
        public final int bodyH;
        public final boolean expanded;
        public final int arrowX;
        public final int switchX;
        public final int switchY;

        Entry(String key, String groupTitle, boolean isHeader,
              int x, int y, int w, int h, int bodyH, boolean expanded) {
            this.key = key;
            this.groupTitle = groupTitle;
            this.isHeader = isHeader;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.bodyH = bodyH;
            this.expanded = expanded;
            this.arrowX = x + 2;
            this.switchX = x + w - SWITCH_W - 6;
            this.switchY = y + (ROW_H - SWITCH_H) / 2;
        }

        /**
         * Chiều cao chiếm chỗ (row + body nếu mở).
         */
        public int totalH() {
            return h + (expanded ? bodyH : 0);
        }
    }

    public static final class Hit {
        public final Entry entry;
        public final Zone zone;

        Hit(Entry entry, Zone zone) {
            this.entry = entry;
            this.zone = zone;
        }
    }

    /**
     * Dựng layout từ trên xuống. bodyHeightOf nhận key và trả về chiều cao
     * dropdown body (0 = không có body).
     */
    public static List<Entry> layout(List<Section> sections, Set<String> expanded,
                                     Function<String, Integer> bodyHeightOf,
                                     int x, int y, int w) {
        List<Entry> entries = new ArrayList<>();
        int curY = y;
        for (Section section : sections) {
            if (section.header != null) {
                entries.add(new Entry(null, section.header, true, x, curY, w, GROUP_H, 0, false));
                curY += GROUP_H + 2;
            }
            for (String key : section.keys) {
                boolean open = expanded.contains(key);
                int bodyH = open ? Math.max(0, bodyHeightOf.apply(key)) : 0;
                entries.add(new Entry(key, section.header, false, x, curY, w, ROW_H, bodyH, open));
                curY += ROW_H + bodyH + ROW_GAP;
            }
        }
        return entries;
    }

    public static int totalHeight(List<Entry> entries) {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.totalH() + (entry.isHeader ? 2 : ROW_GAP);
        }
        return total;
    }

    /**
     * Hit-test theo đúng rect đã layout. Trả về null khi không trúng entry nào.
     */
    public static Hit hitTest(List<Entry> entries, double mouseX, double mouseY) {
        for (Entry entry : entries) {
            if (entry.isHeader) {
                continue;
            }
            if (mouseX < entry.x || mouseX > entry.x + entry.w) {
                continue;
            }
            if (mouseY >= entry.y && mouseY <= entry.y + ROW_H) {
                if (mouseX >= entry.arrowX && mouseX <= entry.arrowX + ARROW_W) {
                    return new Hit(entry, Zone.ARROW);
                }
                if (mouseX >= entry.switchX && mouseX <= entry.switchX + SWITCH_W
                        && mouseY >= entry.switchY && mouseY <= entry.switchY + SWITCH_H) {
                    return new Hit(entry, Zone.SWITCH);
                }
                return new Hit(entry, Zone.BODY);
            }
        }
        return null;
    }
}
