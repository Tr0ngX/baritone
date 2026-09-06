# ⛏️ Baritone 1.21.8 - Custom Edition (Bypass Anti-Cheat & AutoMine Suite)

Phiên bản **Baritone** được tối ưu hóa chuyên sâu cho **Minecraft 1.21.8 Fabric**, tích hợp Menu đồ họa AutoMine (GUI), hệ thống sinh tồn tự động (AutoEat, AutoTotem, AutoDrop) và bộ thuật toán điều hướng / đào bới được tinh chỉnh chống giật lùi (Anti-Rubberband) để hoạt động mượt mà trên các máy chủ nhiều người chơi (Multiplayer Server như **KingMC**).

---

## 🌟 1. Danh sách các cải tiến đột phá so với Baritone gốc

### 🖥️ 1.1. Menu Đồ Họa AutoMine Sleek Dark (Phím `F4`)
* **Giao diện trực quan**: Không cần nhớ hay gõ các dòng lệnh dài phức tạp trong chat. Chỉ cần nhấn **`F4`** (hoặc gõ lệnh `#automine`), giao diện điều khiển hiện đại chuẩn Sleek Dark sẽ xuất hiện.
* **Tùy chọn quặng linh hoạt**: Dễ dàng click bật/tắt từng loại quặng muốn đào:
  * 💎 Kim cương (`Diamond`)
  * 🔵 Lapis Lazuli
  * 🔴 Redstone
  * 🟡 Vàng (`Gold`)
  * ⚪ Sắt (`Iron`)
  * 🟢 Ngọc lục bảo (`Emerald`)
  * 🟤 Mảnh Netherite (`Ancient Debris`)
  * 🟠 Đồng (`Copper`)
  * ⚫ Than đá (`Coal`)
  * ⚪ Thạch anh (`Quartz`)
* **Tùy chỉnh tầng Y mong muốn**: Tự động nhận diện tầng đào tối ưu hoặc chọn các tầng chiến lược (-58, -54, -48, -40, 11, v.v.).

### 🛡️ 1.2. Tối ưu hóa Bypass Anti-Cheat chuyên sâu (Server KingMC & Paper/Purpur)
* **Tốc độ phá khối chuẩn Server (`blockBreakSpeed = 6`)**: Đồng bộ 100% nhịp đập block với server, loại bỏ triệt để hiện tượng bị server hủy gói tin phá khối hoặc rollback block (đập xong block tự hồi lại).
* **Chống giật lùi khi di chuyển (Anti-Rubberband)**:
  * Tự động tắt Sprint khi đào hầm và leo dốc, giúp nhân vật bước đi ổn định, không bao giờ bị giật lùi về sau do đâm vào block chưa kịp vỡ.
  * Tắt Step Hack ảo (`assumeStep = false`), leo trèo tuân thủ hoàn toàn cơ chế vật lý Vanilla để tránh cảnh báo *Far away from path*.
* **Cơ chế chống kẹt thông minh (Smart Anti-Stuck)**:
  * Khi gặp vật cản hoặc tầng đá nền (Bedrock) không thể phá, bot tự động đổi hướng 90 độ mở nhánh hầm mới.
  * Tự động nhận diện và phá tan cát/sỏi sập trúng đầu gây ngạt thở.
  * Khi đào dốc gặp vật cản, tự động rẽ nhánh đào bậc thang thoát hiểm tiếp tục hành trình.

### 🍖 1.3. Hệ thống Sinh tồn Tự động Tích hợp
* **AutoEat (Tự động ăn thức ăn)**:
  * Khi thanh đói dưới 5 cục thịt (`foodLevel <= 10`) hoặc khi bị mất máu (`health < maxHealth`), bot tự động cầm thức ăn ngon nhất trong Hotbar hoặc Balo lên ăn.
  * Tự động lọc bỏ các loại thức ăn có hại (Thịt thối Rotten Flesh, Cá nóc, Khoai tây độc, Mắt nhện, Quả Chorus).
* **AutoTotem (Tự động cầm Totem)**:
  * Tự động tìm kiếm Totem Bất Tử trong balo và trang bị ngay lập tức vào tay phụ (Offhand), giữ an toàn tuyệt đối khi rơi xuống dung nham hoặc gặp quái vật.
* **AutoDrop (Tự động dọn rác)**:
  * Khi balo chỉ còn $\le 5$ ô trống, bot sẽ tự động vứt toàn bộ đá cuội (`Cobblestone`), cuội đá phiến (`Cobbled Deepslate`), đất, sỏi thừa ra ngoài.
  * Tự động giữ lại đúng 1 stack 64 block để phục vụ việc bắc cầu, leo trèo vượt địa hình; nhường toàn bộ không gian balo để chứa quặng quý.

### ⚡ 1.4. Tối ưu hóa Hiệu năng 0% Lag & Ưu tiên Kim Cương
* **Thuật toán quét chạy ngầm (`Background Scanner`)**: Quá trình quét X-Ray quặng chạy hoàn toàn trong luồng nền (background executor thread), không ngốn tài nguyên CPU trên luồng hiển thị chính, duy trì FPS ổn định 60+ FPS.
* **Hút sạch vật phẩm rơi (`Auto Vacuum`)**: Quặng sau khi vỡ rơi ra sàn sẽ được bot bước tới hút sạch 100% trước khi tiếp tục hành trình sang vị trí khác.
* **Thuật toán đào dốc bậc thang 1:1 (`Staircase Descent`)**: Khi ở mặt đất hoặc tầng cao, bot tự động đào bậc thang dốc 45 độ chuẩn xác đi xuống tầng kim cương mong muốn (Y = -58).

---

## 🚀 2. Hướng dẫn Cài đặt

1. **Yêu cầu môi trường**:
   * Minecraft: **1.21.8**
   * Mod Loader: **Fabric Loader** (phiên bản mới nhất cho 1.21.8)
   * Fabric API tương ứng cho 1.21.8
2. **Cài đặt Mod**:
   * Tải file `baritone-fabric-1.21.8.jar` từ mục **Releases** của repository này.
   * Sao chép file jar vào thư mục `.minecraft/mods/`:
     ```text
     Windows: %appdata%\.minecraft\mods\
     macOS: ~/Library/Application Support/minecraft/mods/
     Linux: ~/.minecraft/mods/
     ```
   * Khởi động game bằng Fabric Launcher.

---

## 🎮 3. Hướng dẫn Sử dụng

### Cách 1: Sử dụng Menu Giao Diện (Khuyên dùng)
* Nhấn phím **`F4`** trên bàn phím bất cứ lúc nào trong game.
* Click chọn các loại quặng bạn muốn bot đào (ví dụ: click chọn `Diamond`, `Lapis`, `Redstone`...).
* Tùy chỉnh các chế độ hỗ trợ:
  * `Auto Tool`: Tự động chọn cúp/xẻng phù hợp nhất.
  * `Auto Eat`: Tự động ăn khi đói hoặc mất máu.
  * `Auto Totem`: Tự động cầm Totem Bất Tử vào tay phụ.
  * `Mob Avoidance`: Tự động né tránh tầm nhìn của quái vật.
  * `Parkour`: Cho phép nhảy vượt hố dung nham/khe núi.
* Nhấn nút **`START MINING`** để bot bắt đầu tự động tìm và đào quặng.
* Khi muốn dừng, nhấn lại **`F4`** và chọn **`STOP MINING`** (hoặc gõ `#stop` trong chat).

### Cách 2: Sử dụng Dòng Lệnh trong Chat
| Lệnh Chat | Chức năng |
| :--- | :--- |
| `#automine` | Mở giao diện cài đặt AutoMine (tương đương phím F4) |
| `#automine start` | Bắt đầu đào ngay với cấu hình đã lưu trước đó |
| `#automine stop` hoặc `#stop` | Dừng ngay toàn bộ quá trình đào |
| `#mine diamond_ore deepslate_diamond_ore` | Đào kim cương bằng lệnh truyền thống của Baritone |

---

## 🛠️ 4. Hướng dẫn Biên dịch từ Mã Nguồn (Build from Source)

Nếu bạn muốn tự chỉnh sửa mã nguồn và build file jar:

```powershell
# Clone repository
git clone https://github.com/Tr0ngX/baritone.git
cd baritone

# Thiết lập Java 21 và Build bản Fabric
./gradlew :fabric:build
```

File mod hoàn thiện sẽ nằm tại:
`fabric/build/libs/baritone-fabric-1.15.0-3-g77758cfe-dirty.jar`

---

## 📄 Bản quyền & Ghi công

Dự án này được fork và phát triển dựa trên mã nguồn mở [Baritone](https://github.com/cabaletta/baritone) theo giấy phép **LGPL-3.0**.
Toàn bộ các bản vá chống Anti-Cheat, giao diện đồ họa AutoMine, AutoEat, AutoTotem và AutoDrop được tinh chỉnh và đóng góp bởi **Tr0ngX**.
