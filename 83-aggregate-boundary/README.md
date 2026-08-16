# 83 — Aggregate Boundary: cụm đó to đến đâu?

Bài 71 dạy "một cụm, một cửa". Bài này trả lời câu hỏi khó hơn nhiều — **cụm đó to đến đâu** — và
cho thấy hai lỗi đối xứng nhau: quá to thì mọi thao tác đắt và người dùng đụng nhau vô cớ; quá nhỏ
thì bất biến không giữ được và không có dòng `if` nào cứu nổi.

## 1. Đề bài

Cho ba khái niệm — **khách hàng**, **đơn hàng**, **dòng hàng** — và một luật nghiệp vụ: *tổng tiền
các dòng của một đơn không được vượt 50.000.000đ*. Hãy vẽ ranh giới aggregate, rồi **chứng minh
bằng code** rằng hai cách vẽ khác đều hỏng.

| Cách vẽ | Ranh giới | Hậu quả phải đo được |
|---|---|---|
| Quá to | `KhachHang` ⊃ `DonHang` ⊃ `DongHang` | Đổi một số điện thoại phải tải bao nhiêu object? |
| **Đúng** | `KhachHang` · `DonHang` ⊃ `DongHang` | Bất biến giữ được, tải 1 object |
| Quá nhỏ | `KhachHang` · `DonHang` · `DongHang` | Hai phiên xen kẽ đẩy tổng lên bao nhiêu? |

**Ràng buộc:**
- Bất biến phải được kiểm **bên trong** aggregate, không phải ở tầng ứng dụng.
- Aggregate khác được tham chiếu **bằng id**, không giữ object.
- Phải cho nổ được lỗi "quá nhỏ" một cách **tất định** — không dùng thread, không dựa vào may rủi.

**Input/Output mẫu:**
```
Ranh giới QUÁ NHỎ — hai phiên chạy xen kẽ:
  đang có 40.000.000
  phiên A đọc 40tr -> 40+8 = 48tr ≤ 50tr -> ghi
  phiên B đọc 40tr -> 40+8 = 48tr ≤ 50tr -> ghi
  tổng cuối = 56.000.000        <- VƯỢT, cả hai đều "đã kiểm tra"

Ranh giới QUÁ TO — đổi một số điện thoại:
  tải 501 object (1 khách + 500 đơn)     vs   ranh giới đúng: tải 1 object
```

## 2. Ý tưởng

### Luật duy nhất

> **Ranh giới aggregate nằm đúng ở nơi một BẤT BIẾN phải đúng NGAY LẬP TỨC.**

Không sớm hơn, không muộn hơn. Mọi quy tắc khác trong bài này đều là hệ quả của câu đó.

Phép thử để áp dụng nó:

> *"Nếu hai thứ này được sửa trong **hai transaction khác nhau**, có luật nghiệp vụ nào bị phá không?"*
> **Có** → cùng một aggregate. **Không** → tách ra, tham chiếu bằng id.

| Cặp | Trả lời | Kết luận |
|---|---|---|
| đơn hàng ↔ dòng hàng của nó | Có — tổng có thể vượt hạn mức | **chung** |
| đơn hàng ↔ khách hàng | Không | **tách** |

### Vì sao "quá nhỏ" không cứu được bằng một dòng `if`

Nếu `DongHang` là aggregate riêng, câu kiểm hạn mức buộc phải nằm ở tầng ứng dụng:

```java
long tong = kho.tong();
if (tong + moi <= HAN_MUC) kho.them(...);
```

Đọc — kiểm — ghi, ba bước, và giữa bước 1 và bước 3 có một khoảng trống. Hai phiên chạy xen kẽ:

```
A đọc 40tr          B đọc 40tr
A kiểm 48 ≤ 50 ✓    B kiểm 48 ≤ 50 ✓
A ghi               B ghi
                    -> tổng 56tr
```

Cả hai phiên **đều đọc đúng, kiểm đúng, ghi đúng**. Không ngoại lệ, không cảnh báo. Cái sai không
nằm ở dòng code nào — nó nằm ở **ranh giới**. Khi hai thứ cùng chịu một bất biến mà lại được sửa
trong hai transaction rời nhau, không có `if` nào cứu được.

Với ranh giới đúng, tình huống y hệt bị chặn: cả hai lệnh đều đi qua **cùng một object** `DonHang`,
và bất biến được kiểm lại ở lần ghi thứ hai.

### Vì sao "quá to" đắt — hai lần

**Hậu quả 1 — mọi thao tác đều đắt.** Aggregate phải tải **trọn vẹn** thì bất biến của nó mới kiểm
được; đó là luật, không phải chuyện tối ưu. Nên `KhachHang` ôm 500 đơn nghĩa là đổi một số điện
thoại cũng phải tải 501 object.

**Hậu quả 2 — đụng độ giả.** Với khoá lạc quan ([bài 92](../92-optimistic-concurrency/)), mỗi lần
sửa aggregate là một lần tăng số hiệu phiên bản. Hai người tạo **hai đơn hoàn toàn không liên
quan** của cùng một khách hàng sẽ cùng tăng phiên bản của `KhachHang` → một người nhận lỗi *"dữ
liệu đã bị người khác sửa"*. Đụng độ này là **giả** — nó do ranh giới sai sinh ra, không do nghiệp
vụ. Aggregate càng to, tỉ lệ đụng độ giả càng cao.

### Tham chiếu bằng id, không bằng object

```java
class DonHang {
    private final MaKhachHang maKhachHang;   // <- id, không phải KhachHang
}
```

Đây không phải kỷ luật, đây là **kiểu dữ liệu**: `DonHang` không có field `KhachHang`, nên dòng
`don.khachHang().doiDienThoai(...)` không biên dịch được. Và vì `MaKhachHang` là **kiểu riêng** chứ
không phải `String`, truyền nhầm mã đơn vào chỗ mã khách cũng là lỗi biên dịch.

### Bất biến nào KHÔNG được kéo vào ranh giới

Cám dỗ lớn nhất: *"tổng nợ của khách hàng không quá 200 triệu"* — nghe như một bất biến, và nó kéo
toàn bộ đơn hàng vào trong `KhachHang`.

Câu hỏi phải hỏi tiếp: **nếu luật đó bị vượt trong 5 giây rồi được sửa, công ty mất gì?**

| Trả lời | Loại | Xử lý |
|---|---|---|
| "Không mất gì, gọi điện đòi là xong" | luật nghiệp vụ | kiểm sau, **tách ra** |
| "Xuất hoá đơn sai, phải huỷ" | bất biến thật | **chung aggregate** |

Rất nhiều "bất biến" hoá ra thuộc loại thứ nhất. Hỏi người làm nghiệp vụ, đừng đoán.

### Bốn quy tắc rút gọn

1. Ranh giới nằm ở nơi một bất biến phải đúng **ngay lập tức**.
2. Tham chiếu aggregate khác **bằng id**, không giữ object.
3. Một transaction sửa đúng **một** aggregate.
4. **Nghi ngờ thì làm nhỏ.** Aggregate nhỏ mà thiếu bất biến thì gộp lại được; aggregate to thì
   mọi thao tác đã đắt sẵn và tách ra rất khó.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Tải aggregate đúng kích thước | O(k) — k = số phần tử con, nhỏ và có chặn trên | O(k) |
| Tải aggregate quá to | O(n) — n = số đơn của khách, **không có chặn trên** | O(n) |
| Kiểm bất biến trong `themDong()` | O(k) mỗi lần thêm (hoặc O(1) nếu giữ tổng luỹ kế) | O(1) |
| Bài test kiến trúc (Python, phần 8) | O(số object chạm tới được) — chỉ chạy lúc test | O(số object) |

Điểm cốt lõi không phải hằng số mà là **chặn trên**: một aggregate lành mạnh có số phần tử con
*bị nghiệp vụ giới hạn* (một đơn hàng khó có 10.000 dòng). Aggregate mà số con tăng vô hạn theo
thời gian là aggregate vẽ sai — dấu hiệu nhận biết sớm nhất, thấy trước cả khi hệ thống chậm.

## 4. Lời giải

- [`BoundaryDemo.java`](BoundaryDemo.java) — `record MaKhachHang` / `record MaDonHang` làm định
  danh thành kiểu riêng, nên truyền nhầm là lỗi biên dịch. Cửa đóng bằng `List.copyOf`.
- [`BoundaryDemo.cpp`](BoundaryDemo.cpp) — ở C++ ranh giới aggregate **là quyền sở hữu bộ nhớ**:
  root giữ con theo giá trị (`std::vector<DongHang>`), nên con không thể sống lâu hơn cha. Phần 8
  chỉ ra cách rò rỉ riêng của C++: trả `std::vector<DongHang>&` thay vì
  `const std::vector<DongHang>&` — **thiếu một chữ `const`** là cửa mở toang, và code gọi
  `don.cacDong().push_back(...)` trông vẫn như một dòng bình thường.
- [`boundary_demo.py`](boundary_demo.py) — Python không bảo vệ gì cả (phần 7 chứng minh gạch dưới
  `_cac_dong` chặn được đúng **không ai**). Bù lại, phần 8 dựng một **bài test kiến trúc** thật:
  duyệt đồ thị object từ một aggregate root và khẳng định không chạm tới root nào khác. Nó bắt
  đúng thứ code review hay bỏ sót — hôm nào đó ai thêm `self.khach_hang = kh` "cho tiện".

**Khác biệt giữa ba ngôn ngữ:**

| | Chặn sửa con từ ngoài | Chặn tham chiếu chéo aggregate | Cách rò rỉ dễ mắc nhất |
|---|---|---|---|
| Java | `List.copyOf` → `UnsupportedOperationException` lúc chạy | ✅ kiểu riêng, lỗi biên dịch | trả thẳng `cacDong` |
| C++ | ✅ `const&` → **lỗi biên dịch** | ✅ `explicit` + kiểu riêng, lỗi biên dịch | quên `const` ở kiểu trả về |
| Python | trả `tuple` → `AttributeError` lúc chạy | ❌ chỉ phát hiện được lúc chạy | trả thẳng `self._cac_dong` |

## 5. Thực tế đi làm

**Cạm bẫy #1 — vẽ ranh giới theo cây quan hệ CSDL.** `khach_hang` ← `don_hang` ← `dong_hang` là ba
bảng có khoá ngoại, và trực giác nói "khách hàng chứa đơn hàng". Nhưng khoá ngoại là chuyện lưu
trữ, còn aggregate là chuyện **bất biến**. Hai thứ trùng nhau ở tầng dưới cùng (đơn ⊃ dòng) và
lệch nhau ở tầng trên — và đúng chỗ lệch đó là chỗ hệ thống chết.

**Cạm bẫy #2 — vẽ ranh giới theo màn hình.** "Màn hình chi tiết khách hàng hiện cả danh sách đơn,
nên `KhachHang` phải chứa `List<DonHang>`." Không. Màn hình cần **đọc** cả hai; aggregate quyết
định cách **ghi**. Nhu cầu đọc giải quyết bằng một truy vấn riêng, không tạo mô hình đọc bằng mô
hình ghi — đó là [bài 95 · CQRS](../95-cqrs-lite/).

**Cạm bẫy #3 — aggregate có số con tăng không giới hạn.** `SanPham` chứa `List<LichSuGia>`,
`TaiKhoan` chứa `List<GiaoDich>` — hôm nay 10 phần tử, ba năm nữa 200.000. Mỗi lần đổi tên sản
phẩm phải tải cả lịch sử giá. Câu hỏi kiểm tra: *"cái danh sách này có bao giờ ngừng lớn không?"*
Không → nó là aggregate riêng.

**Cạm bẫy #4 — sửa hai aggregate trong một transaction.** Nó chạy được, nên không ai thấy vấn đề
cho tới lúc có tải. Hai aggregate trong một transaction nghĩa là khoá hai vùng dữ liệu cùng lúc,
và deadlock chỉ là chuyện thời gian. Nếu use case thật sự cần cả hai, đó là dấu hiệu **ranh giới
vẽ sai** hoặc **cần nhất quán cuối** ([bài 84](../84-domain-event/), [97](../97-saga/)).

**Cạm bẫy #5 — giữ object của aggregate khác "cho tiện".** `don.getKhachHang().getTen()` tiện thật,
và nó lôi theo: khách hàng phải được tải mỗi lần tải đơn, ai cũng có thể sửa khách hàng qua đường
đơn hàng, và ranh giới transaction nhoè đi. Nếu chỉ cần cái tên để in, hãy sao nó thành một value
object trong đơn (`tenKhachLucDat`) — dữ liệu tại thời điểm đặt hàng thường mới là thứ nghiệp vụ
muốn, chứ không phải tên hiện tại.

**Cạm bẫy #6 — chia aggregate quá nhỏ vì "microservice".** Tách nhỏ để dễ mở rộng là đúng, nhưng
tách xuyên qua một bất biến là mua lấy đúng bug ở phần 2 — vĩnh viễn, vì lúc đó hai nửa còn nằm ở
hai tiến trình. Ranh giới dịch vụ phải **bao ngoài** ranh giới aggregate, không được cắt ngang nó.

**Biến thể phỏng vấn thường hỏi:**
- *"Aggregate nên to hay nhỏ?"* — Nhỏ, và có một lý do bất đối xứng: aggregate nhỏ mà thiếu bất
  biến thì gộp lại được (refactor cục bộ); aggregate to thì mọi thao tác đã đắt sẵn và tách ra
  đụng tới mọi chỗ.
- *"Làm sao giữ được luật 'tổng nợ khách ≤ 200 triệu' khi đơn hàng và khách hàng là hai
  aggregate?"* — Không giữ tức thời. Kiểm ở tầng ứng dụng khi tạo đơn (chấp nhận có thể lọt trong
  chốc lát), rồi phát hiện và bù trừ sau bằng sự kiện. Và trước đó, hỏi nghiệp vụ xem lọt trong
  chốc lát có thật sự chết người không — thường là không.
- *"Vì sao tham chiếu bằng id mà không bằng object?"* — Ba lý do: (1) giữ ranh giới transaction rõ
  ràng; (2) không lôi theo dữ liệu không cần khi tải; (3) cho phép hai aggregate nằm ở hai kho lưu
  trữ / hai dịch vụ khác nhau về sau mà không phải sửa mô hình miền.
- *"Aggregate và bảng CSDL có phải một không?"* — Không. Một aggregate thường trải trên nhiều
  bảng, và một bảng có thể phục vụ nhiều mô hình đọc. Nhầm hai thứ này là gốc của cạm bẫy #1.
- *"Team em kiểm tra ranh giới bằng cách nào?"* — Bằng máy, đừng bằng code review. Xem phần 8 của
  file Python: một hàm 15 dòng duyệt đồ thị object từ mỗi root và fail nếu chạm tới root khác. Java
  thì dùng ArchUnit; C++ thì dựa vào chính trình biên dịch (kiểu riêng + không có field trỏ chéo).

## 6. Self-check

```bash
cd 04-competitive/83-aggregate-boundary
javac BoundaryDemo.java && java BoundaryDemo        # in "OK"
g++ -std=c++17 -o sol BoundaryDemo.cpp && ./sol     # in "OK"
python boundary_demo.py                             # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
