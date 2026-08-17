# 100 — Capstone: hai mươi bài này là MỘT thiết kế, không phải hai mươi mẫu

Từng bài trước dạy **một** thứ và cố tình bỏ qua phần còn lại. Bài này ghép chúng thành một hệ đặt
hàng chạy được, và cho thấy điều mà học từng mẫu thiết kế riêng lẻ không bao giờ nói: **mỗi luật tồn
tại vì luật bên cạnh**.

## 1. Đề bài

Một hệ đặt hàng nhỏ, đầy đủ ba tầng, chạy được đầu tới cuối:

```
MIỀN        Tien · MaDonHang · DongHang · TrangThai · SuKien
            DonHang (aggregate root: bất biến, máy trạng thái, phiên bản, ghi sự kiện)
            duocGiamGia (specification) · BANG_THUE (policy)
            Cổng: KhoDonHang · BaoChoKhach · DongHo
ỨNG DỤNG    DichVuDatHang — idempotency, ghép specification + policy, phát sự kiện SAU commit
HẠ TẦNG     KhoTrongBoNho · BaoGia · DongHoCoDinh
ĐỌC         DongDanhSachDon — mô hình phẳng, một truy vấn
```

Mười hai phần self-check, mỗi phần ứng với một bài đã học:

| # | Kiểm | Bài |
|---|---|---|
| 1 | Đường thuận lợi: 21.000.000 → giảm 5% → +thuế 10% = **21.945.000** | 86 · 87 · 88 · 98 |
| 2 | Bất biến hạn mức + cửa aggregate đóng | 83 |
| 3 | Chưa thanh toán thì chưa giao — mặc định là **từ chối** | 89 |
| 4 | Cộng khác tiền tệ bị chặn | 90 |
| 5 | Specification **giải thích** trượt ở hai mệnh đề nào | 87 |
| 6 | Thuế theo quốc gia bằng **bảng tra**, không `if-else` | 88 |
| 7 | Gửi lại cùng khoá → **không** xử lý lần nữa, trả kết quả cũ | 91 |
| 8 | Ghi với phiên bản cũ → **0 dòng**, đụng độ được đếm | 92 |
| 9 | Lưu hỏng → **0 email, 0 sự kiện** rời khỏi tiến trình | 84 |
| 10 | Màn hình danh sách: **đúng một** lượt truy vấn | 95 |
| 11 | **0** tham chiếu từ miền ra hạ tầng; tên sự kiện ở thì quá khứ | 81 · 93 · 94 · 98 |
| 12 | Chuỗi lý do: vì sao 20 bài này khoá vào nhau | — |

**Input/Output mẫu:**
```
datHang("KEY-1", "KH-01", VN, [laptop 20tr ×1, chuột 500k ×2])
  tổng      21.000.000
  giảm 5%    1.050.000   (đủ ≥1tr VÀ ≥2 dòng)
  sau giảm  19.950.000
  thuế 10%   1.995.000
  phải trả  21.945.000

datHang("KEY-1", ...) lần hai  ->  21.945.000, và soLanThucSuXuLy KHÔNG tăng
```

## 2. Ý tưởng

### Chuỗi lý do — phần quan trọng nhất của cả tầng

Không có bài nào trong 20 bài là một lựa chọn độc lập. Chúng khoá vào nhau:

| Vì | Nên |
|---|---|
| Có bất biến *"tổng ≤ hạn mức"* | phải có **ranh giới aggregate** (83) |
| Ranh giới → tham chiếu **bằng id** | hai aggregate không nói trực tiếp với nhau |
| Không nói trực tiếp | phải có **sự kiện miền** (84) |
| Sự kiện giao *ít nhất một lần* | người nghe phải **idempotent** (91) |
| Một transaction một aggregate | quy trình nhiều bước cần **saga** (97) |
| Nhiều người cùng sửa | cần **khoá lạc quan** (92) |
| Aggregate phải tải trọn vẹn | màn hình danh sách cần **CQRS** (95) |
| Luật đổi theo ngữ cảnh | **policy**, không phải `if-else` rải rác (88) |
| Luật cần giải thích + dịch sang SQL | **specification** (87) |
| Miền phải test được không CSDL | **cổng & bộ nối** (98) |
| Test không CSDL | test miền chỉ là **hàm + assert** (99) |

**Rút một mắt xích ra thì mắt kế bên mất lý do tồn tại.** Đó là lý do "áp dụng DDD một nửa" thường
tệ hơn không áp dụng: aggregate có ranh giới nhưng không có sự kiện thì hai nửa hệ thống không nói
chuyện được; có sự kiện nhưng không idempotent thì mất tiền.

### Thứ tự ghép hai luật là một luật thứ ba

```
tổng → giảm giá (specification) → thuế (policy) → phải trả
```

Giảm giá **trước**, thuế **sau** — thuế tính trên số tiền thực trả. Luật này không thuộc
specification cũng không thuộc policy; nó nói về **quan hệ giữa hai cái**, nên nó nằm ở tầng ứng
dụng ([bài 86](../86-domain-service/)). Đảo thứ tự là sai luật thuế ở hầu hết các nước, và không
test nào của riêng specification hay riêng policy bắt được.

### Ba thứ tự bắt buộc, không được đổi

| Trong | Phải là |
|---|---|
| `giao()` | đổi trạng thái **trước** → ghi sự kiện **sau** (ném thì không có sự kiện nào) |
| `thucHien()` | lưu **trước** → phát sự kiện **sau** (bài 84) |
| `huy()` | hỏi phí **trước** → chuyển trạng thái **sau** (bài 89) |

Cả ba đều có chung một hình dạng: **thứ có thể thất bại đi trước, thứ không rút lại được đi sau.**
Đó cũng chính là luật của saga ([bài 97](../97-saga/)).

## 3. Độ phức tạp

| | Đường ghi | Đường đọc |
|---|---|---|
| Tải aggregate | O(k) — k phần tử con, có chặn trên | — |
| Kiểm bất biến | O(k) mỗi lần thêm dòng | — |
| Chuyển trạng thái | O(1) tra bảng | — |
| Tra policy thuế | O(1) `EnumMap` / mảng | — |
| Specification n mệnh đề | O(n), ngắn mạch | — |
| Idempotency | O(1) giành chỗ nguyên tử | — |
| Màn hình danh sách m đơn | — | **1** truy vấn, O(m) dòng phẳng |
| Cùng màn hình qua aggregate | — | **1+2m** truy vấn, O(m·k) object |

Hai dòng cuối là toàn bộ lý do CQRS tồn tại trong hệ này: cùng một dữ liệu, hai con đường, và
đường ghi **không** dùng được cho việc đọc hàng loạt.

## 4. Lời giải

- [`OrderSystemDemo.java`](OrderSystemDemo.java) — `sealed interface SuKien` + `enum` có thân riêng
  từng hằng cho máy trạng thái + `record` cho value object. Phần 11 dùng reflection dựng bài test
  kiến trúc: 0 tham chiếu từ miền ra hạ tầng, và mọi tên sự kiện ở thì quá khứ.
- [`OrderSystemDemo.cpp`](OrderSystemDemo.cpp) — dùng đúng những gì chỉ C++ có: **tiền tệ là tham số
  kiểu** (`Tien<VND> + Tien<USD>` không biên dịch được, có `static_assert` chứng minh), **bảng
  chuyển trạng thái `constexpr`** được `static_assert` đếm cạnh lúc biên dịch, và aggregate `=
  delete` copy để định danh không nhân bản được. Phần lớn "bài test kiến trúc" ở đây **đã chạy xong
  trước khi chương trình tồn tại**.
- [`order_system_demo.py`](order_system_demo.py) — dùng đúng những gì chỉ Python có: **`Protocol`**
  cho cổng (bộ nối thoả cổng mà **không kế thừa gì**), `frozen dataclass` cho value object và sự
  kiện, **`property` không setter** để máy trạng thái không bị vượt mặt, và reflection để kiểm cả
  chiều phụ thuộc lẫn luật đặt tên sự kiện.

**Ba ngôn ngữ, cùng một thiết kế, ba cách ép luật:**

| Luật | Java | C++ | Python |
|---|---|---|---|
| Không cộng khác tiền tệ | ngoại lệ lúc chạy | ✅ **lỗi biên dịch** | ngoại lệ lúc chạy |
| Không quên loại sự kiện | ✅ `sealed` + switch | ✅ `variant` + visit | `else: raise` |
| Máy trạng thái đúng | test lúc chạy | ✅ **`static_assert`** | test lúc chạy |
| Không sửa ruột aggregate | `List.copyOf` | ✅ `const&` — biên dịch | `tuple` |
| Không gán thẳng trạng thái | không có setter | ✅ `private` — biên dịch | `property` không setter |
| Bộ nối không phụ thuộc cổng | ❌ phải `implements` | ❌ phải kế thừa | ✅ **`Protocol`** |

## 5. Thực tế đi làm

**Cạm bẫy #1 — áp dụng nửa vời.** Có `Aggregate` trong tên lớp nhưng vẫn sửa hai aggregate trong
một transaction; có `DomainEvent` nhưng phát trong transaction. Nửa vời **tệ hơn** không áp dụng, vì
nó tạo cảm giác an toàn giả. Chuỗi lý do ở phần 2 là danh sách kiểm tra: nếu có mắt xích thứ nhất
thì phải có mắt xích thứ hai.

**Cạm bẫy #2 — bắt đầu từ mẫu thiết kế thay vì từ bất biến.** Câu hỏi mở đầu không phải *"aggregate
của tôi là gì"* mà là *"luật nào phải đúng ngay lập tức"*. Ranh giới đi theo bất biến, không đi theo
sơ đồ lớp.

**Cạm bẫy #3 — dùng cả 20 mẫu cho một hệ CRUD.** Nếu không có bất biến nào cần đúng tức thời, không
có luật đổi theo ngữ cảnh, không có hệ ngoài — thì controller gọi thẳng repository là **thiết kế
đúng**. Mỗi bài trong tầng này đều có mục *"khi nào KHÔNG cần"*, và mục đó quan trọng ngang phần
còn lại.

**Cạm bẫy #4 — quên rằng thứ tự là một luật.** Ba thứ tự ở phần 2 không có test riêng nào bắt được;
chúng phải nằm trong code review và trong đầu người viết.

**Cạm bẫy #5 — bài test kiến trúc viết sau.** Viết vào tuần đầu thì nó bảo vệ được kiến trúc; viết
sau một năm thì nó chỉ ghi lại đống đổ nát. Ba bài kiểm ở phần 11 mất chưa tới 20 dòng.

**Cạm bẫy #6 — coi tầng này là "kiến trúc cho hệ thống lớn".** Nó là kiến trúc cho **nghiệp vụ
phức tạp**, và một hệ thống nhỏ có thể có nghiệp vụ rất phức tạp (tính lương, tính cước, hoàn tiền).
Ngược lại một hệ thống rất lớn có thể chỉ là CRUD.

**Biến thể phỏng vấn thường hỏi:**
- *"Hãy thiết kế hệ đặt hàng."* — Bắt đầu từ **bất biến**, không từ bảng CSDL. Nói ra được chuỗi lý
  do ở phần 2 quan trọng hơn kể tên 20 mẫu.
- *"Vì sao không sửa hai aggregate trong một transaction?"* — Và câu hỏi tiếp theo luôn là *"vậy làm
  sao giữ nhất quán?"* — sự kiện + nhất quán cuối + saga khi cần bù trừ.
- *"DDD có phù hợp với dự án nhỏ không?"* — Câu hỏi sai chiều: nó phù hợp với **nghiệp vụ phức tạp**,
  không phụ thuộc kích thước dự án. Value object và ubiquitous language thì nên dùng ở mọi quy mô;
  event sourcing thì gần như không bao giờ cần cho một CRUD.
- *"Bắt đầu từ đâu nếu codebase hiện tại là một God Service 3.000 dòng?"* — Từ ngôn ngữ chung (81)
  và value object (82) — hai thứ rẻ nhất, không cần đổi kiến trúc, và làm mọi bước sau dễ hơn.

## 6. Self-check

```bash
cd 04-competitive/100-capstone-order-system
javac OrderSystemDemo.java && java OrderSystemDemo        # in "OK"
g++ -std=c++17 -o sol OrderSystemDemo.cpp && ./sol        # in "OK"
python order_system_demo.py                               # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.

---

**Hết tầng `04-competitive` — và hết series 100 bài.** Xem [INDEX.md](../INDEX.md) để đi lại toàn bộ
tầng, hoặc [02-OOP/README.md](../../README.md) cho cả bốn tầng.
