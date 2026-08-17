# 95 — CQRS-lite: 1.000 lượt truy vấn để hiện 500 dòng

Bài 83 nói aggregate là đơn vị **nhất quán**; bài 85 nói repository chỉ trả về **aggregate root**.
Cả hai đều rất tốt cho việc **ghi** — và tệ hại cho màn hình danh sách. Sai lầm phổ biến nhất là
cố làm aggregate phục vụ cả hai, và kết quả là mô hình miền phình ra vì nhu cầu hiển thị.

## 1. Đề bài

Màn hình danh sách đơn hàng: 500 dòng × 5 cột (mã đơn, tên khách, số dòng, tổng tiền, trạng thái).
Dữ liệu nằm ở **hai** aggregate: `DonHang` (có các dòng hàng bên trong) và `KhachHang`.

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Dựng màn hình bằng aggregate → **N+1** | **1.000** lượt truy vấn, **2.500** object |
| 2 | Mô hình đọc phẳng → một truy vấn | **1** lượt, **500** object, cùng kết quả |
| 3 | Mô hình đọc **được** ghép hai aggregate | `tenKhach` nằm ngay trong dòng đọc |
| 4 | Mô hình đọc **không** ghi được | `frozen` / không setter / lỗi biên dịch |

**Ràng buộc:** luật nghiệp vụ không được nằm ở bên đọc; bên đọc không được trả về ruột của
aggregate.

**Input/Output mẫu:**
```
Qua aggregate:  500 × (taiDon + taiKhach) = 1.000 truy vấn
                500 × (1 root + 3 dòng) + 500 khách = 2.500 object

Qua mô hình đọc: 1 truy vấn, 500 object
                DongDanhSachDon("DH-0", "Khách 0", 3, 1.700.000, "MOI_TAO")
```

## 2. Ý tưởng

### Con bug N+1 không phải lỗi của ORM

Nó là hệ quả trực tiếp của việc dùng mô hình **ghi** để trả lời một câu hỏi **đọc**. Aggregate bắt
buộc phải tải **trọn vẹn** ([bài 83](../83-aggregate-boundary/)) — nên mỗi đơn kéo theo cả các dòng
hàng mà màn hình chỉ cần biết *số lượng* của chúng. Và vì đơn tham chiếu khách hàng **bằng id**,
mỗi dòng lại là một lượt truy vấn nữa.

Hai luật đúng của bên ghi cộng lại thành một thảm hoạ ở bên đọc. Đó chính là lý do phải tách.

### Mô hình đọc là MỘT DÒNG TRÊN MÀN HÌNH

```java
record DongDanhSachDon(String maDon, String tenKhach, int soDong,
                       long tongTien, String trangThai) { }
```

Không hành vi, không bất biến, không setter. Nó **không** phải entity, **không** phải value object
của miền.

Điểm giải phóng lớn nhất: nó **ghép dữ liệu của hai aggregate** — điều bên ghi bị cấm làm. Ở bên
đọc điều đó hoàn toàn hợp lệ, vì mô hình đọc **không bao giờ ghi**, nên nó không có bất biến nào để
giữ và không có ranh giới transaction nào để tôn trọng. Nó được ghép bảng thoải mái, đọc chéo ngữ
cảnh, lưu dữ liệu trùng lặp — và không gì trong số đó gây hại, vì **nó không phải nguồn sự thật**.

### Thêm cột hiển thị không được làm bẩn miền

Màn hình cần thêm cột "tên khách" → phản xạ là thêm `tenKhach` vào `DonHang` cho tiện. Ba hậu quả:

- `DonHang` giữ dữ liệu của aggregate khác → phá [bài 83](../83-aggregate-boundary/);
- tên khách đổi thì phải cập nhật mọi đơn cũ — hoặc là hiển thị sai;
- không ai biết `DonHang.tenKhach` là **bản chụp lúc đặt** hay **giá trị hiện tại**.

> Câu hỏi phân biệt: *"nghiệp vụ có cần không?"*, không phải *"màn hình có hiện không?"*. Nếu hoá
> đơn phải in đúng tên lúc đặt, thì đó là value object của miền và nó thuộc về `DonHang`. Nếu chỉ
> để hiển thị, nó thuộc về mô hình đọc.

### Mô hình đọc được phép CŨ

Ở mức "lite" (chung một CSDL) thì luôn tươi. Nhưng ngay khi có bảng đọc riêng cập nhật bằng sự kiện
([bài 84](../84-domain-event/)), màn hình có thể hiện dữ liệu cũ vài giây.

> **Đọc để hiển thị** → dùng mô hình đọc.
> **Đọc để ra quyết định ghi** → phải tải aggregate, và có khoá lạc quan ([bài 92](../92-optimistic-concurrency/)).

Câu hỏi *"màn hình này cũ 2 giây có sao không?"* phải hỏi nghiệp vụ, không được tự quyết.

### "Lite" nghĩa là gì

| Mức | Kho ghi | Kho đọc | Độ trễ | Chi phí |
|---|---|---|---|---|
| Không tách | chung | chung | 0 | N+1, miền bị bẩn |
| **CQRS-lite** (bài này) | chung | chung | **0** | thêm mô hình đọc + câu truy vấn |
| CQRS đầy đủ | chung | **riêng** | có | đồng bộ, hạ tầng, vận hành |

Hàng giữa giải quyết **90%** vấn đề với gần như không có chi phí vận hành: vẫn một CSDL, một
transaction, dữ liệu luôn tươi — chỉ là **đường đọc không đi qua aggregate**. Đừng nhảy sang hàng
cuối khi chưa đo được rằng hàng giữa không đủ.

### Luật nghiệp vụ không được nằm ở bên đọc

Phép thử: **nếu xoá toàn bộ mô hình đọc đi, hệ thống có còn *đúng* không** (chỉ chậm và xấu)? Nếu
câu trả lời là *"không, mất luôn luật X"* thì luật X đang nằm sai chỗ — và nó sẽ có bản sao thứ hai
lệch với bản trong miền ([bài 87](../87-specification/) phần 2).

## 3. Độ phức tạp

| | Truy vấn | Object tải | Nhảy con trỏ |
|---|---|---|---|
| Qua aggregate (n đơn, k dòng/đơn) | **1 + 2n** | n·(1+k) + n | ~n·(1+k) |
| Qua mô hình đọc | **1** | n | 0 — một khối liền |
| Bài này (n=500, k=3) | 1.000 vs **1** | 2.500 vs **500** | 2.500 vs **0** |

Cột cuối chỉ hiện rõ ở C++: aggregate là một cây con trỏ (mỗi đơn một vùng heap, `cacDong_` một
vùng khác), còn mô hình đọc là `vector` liền nhau. Với 500 dòng không ai thấy khác biệt; với 50.000
dòng và một báo cáo chạy mỗi đêm, đó là khác biệt giữa 2 giây và 2 phút — và **không sửa được bằng
cách tối ưu vòng lặp**, vì nguyên nhân nằm ở *chỗ đặt dữ liệu*, không ở code.

## 4. Lời giải

- [`CqrsDemo.java`](CqrsDemo.java) — `record` cho mô hình đọc: không setter nên "dùng mô hình đọc
  để ghi" là lỗi biên dịch. CSDL giả đếm trực tiếp 1.000 vs 1 lượt truy vấn.
- [`CqrsDemo.cpp`](CqrsDemo.cpp) — thêm cái giá thứ hai mà hai ngôn ngữ kia giấu đi: **cách dữ liệu
  nằm trong bộ nhớ**. File đếm **số lần nhảy con trỏ** (2.500 vs 0) và chứng minh 500 dòng đọc nằm
  liền nhau trong một khối bằng phép so địa chỉ.
- [`cqrs_demo.py`](cqrs_demo.py) — hai cái bẫy chỉ Python mới có. Nhẹ: mô hình đọc khả biến thì
  `dong.tong_tien = 999` chạy êm mà **không** ghi vào CSDL — bug im lặng nhất bài. Nặng: nếu truy
  vấn đọc trả về **chính** `self._cac_dong` của aggregate, màn hình danh sách vừa có quyền **ghi
  thẳng vào miền** — phần 6 đẩy một đơn vượt hạn mức bằng một hàm mang tên "truy vấn".

**Khác biệt giữa ba ngôn ngữ:**

| | Chặn ghi qua mô hình đọc | Cái giá được đo thêm | Bẫy riêng |
|---|---|---|---|
| Java | ✅ `record` — lỗi biên dịch | — | — |
| C++ | ✅ ngữ nghĩa giá trị — sửa bản sao | **nhảy con trỏ / cache** | — |
| Python | `frozen=True` → `FrozenInstanceError` | — | truy vấn đọc **trả ruột aggregate** |

## 5. Thực tế đi làm

**Cạm bẫy #1 — dùng repository của aggregate cho màn hình danh sách.** Dấu hiệu: một `findAll()`
trả về `List<DonHang>` rồi tầng trên `map` sang DTO. Chi phí đã trả xong trước khi `map` chạy.

**Cạm bẫy #2 — thêm field vào aggregate vì màn hình cần.** Đây là cách mô hình miền chết dần: sau
hai năm, `DonHang` có 40 field và một nửa chỉ để hiển thị. Câu hỏi kiểm tra ở phần 2.

**Cạm bẫy #3 — luật nghiệp vụ nằm trong câu truy vấn đọc.** `WHERE tong_tien > 10000000 AND ...`
là bản sao thứ hai của một luật đã có trong miền. Chúng sẽ lệch nhau, và bản trong SQL không ai
test.

**Cạm bẫy #4 — đọc bằng mô hình đọc rồi ghi dựa trên nó.** *"Kiểm tra trạng thái ở màn hình rồi
gọi API giao hàng"* là đọc-quyết-định-ghi trên dữ liệu có thể đã cũ. Phải tải aggregate và kiểm lại
([bài 92](../92-optimistic-concurrency/)).

**Cạm bẫy #5 — nhảy thẳng lên CQRS đầy đủ.** Kho đọc riêng kéo theo: đồng bộ bằng sự kiện, xử lý
gửi trùng ([bài 91](../91-idempotency/)), dựng lại khi lệch, giám sát độ trễ. Đó là một hệ thống
thứ hai. Bảng ở phần 2 nói rõ hàng giữa giải quyết phần lớn vấn đề với gần như 0 chi phí vận hành.

**Cạm bẫy #6 — mô hình đọc trả về ruột của aggregate.** Ở Python `return self._cac_dong` là cách
viết tự nhiên nhất và nó biến tầng đọc thành một đường ghi không ai canh. Mô hình đọc phải là **dữ
liệu mới** — bản chụp phẳng, bất biến.

**Cạm bẫy #7 — một mô hình đọc dùng cho mọi màn hình.** `OrderView` với 30 field phục vụ 6 màn hình
là mô hình chung ([bài 93](../93-bounded-context/)) lặp lại ở tầng đọc. Mỗi màn hình một mô hình
đọc — chúng rẻ, không có bất biến, và trùng lặp giữa chúng là vô hại.

**Biến thể phỏng vấn thường hỏi:**
- *"CQRS là gì?"* — Tách mô hình đọc khỏi mô hình ghi. Nói thêm được rằng **không bắt buộc** phải
  có hai kho dữ liệu là điểm phân biệt người đã dùng thật với người đọc blog.
- *"Vì sao không dùng aggregate cho màn hình danh sách?"* — Vì aggregate phải tải trọn vẹn và tham
  chiếu chéo bằng id — hai luật đúng của bên ghi, cộng lại thành N+1 ở bên đọc.
- *"Mô hình đọc có được ghép nhiều aggregate không?"* — Có, và đó là điểm mạnh nhất của nó. Nó
  không có bất biến để giữ.
- *"Dữ liệu đọc cũ thì sao?"* — Hỏi nghiệp vụ. Và phân biệt: đọc để **hiển thị** thì chấp nhận cũ,
  đọc để **ra quyết định ghi** thì không.
- *"Khi nào cần kho đọc riêng?"* — Khi đã đo được rằng truy vấn tối ưu trên kho ghi vẫn không đủ,
  hoặc khi tải đọc lấn át tải ghi. Không phải khi bắt đầu dự án.

## 6. Self-check

```bash
cd 04-competitive/95-cqrs-lite
javac CqrsDemo.java && java CqrsDemo        # in "OK"
g++ -std=c++17 -o sol CqrsDemo.cpp && ./sol # in "OK"
python cqrs_demo.py                         # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
