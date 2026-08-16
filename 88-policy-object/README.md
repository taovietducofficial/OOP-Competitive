# 88 — Policy Object: báo cáo thu 0% thuế Đức, lệch 19 triệu mỗi đơn

Bài 87 ghép luật **lúc viết code**. Bài này chọn luật **lúc chạy** — đó là toàn bộ khác biệt, và
nó quyết định một hệ thống mở rộng sang thị trường mới trong *một ngày* hay trong *một quý*.

## 1. Đề bài

Thuế đổi theo quốc gia, giảm giá đổi theo hạng khách. Ba nơi cùng cần tính thuế: màn hình thanh
toán, sinh hoá đơn, báo cáo doanh thu.

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Chuỗi `if-else` chép ba nơi → một nơi quên nước Đức | 19.000.000 vs **0** trên cùng một đơn |
| 2 | `0% có tên` khác `chưa cấu hình` | `moTa()` = "không thuế liên bang" |
| 3 | Thiếu chính sách phải **nổ**, không âm thầm về 0 | `getOrDefault` trả 0đ vs ném ngoại lệ |
| 4 | Hai trục độc lập → **4+3**, không phải 4×3 | 7 lớp thay vì 12 |

**Ràng buộc:** khoá tra chính sách phải là kiểu của miền (enum), không phải chuỗi; phải kiểm được
bằng máy rằng mọi ngữ cảnh đều có chính sách.

**Input/Output mẫu:**
```
đơn 100.000.000đ, khách ở Đức
  thanh toán -> 19.000.000   ✅
  hoá đơn    -> 19.000.000   ✅
  báo cáo    ->          0   ❌  (thiếu một nhánh if)

tinhTongPhaiTra(100.000, VN, VÀNG)
  giảm 10% -> 90.000 · thuế 10% -> 9.000 · tổng = 99.000
```

## 2. Ý tưởng

### Con bug: nhánh `else` trả 0 nuốt trọn lỗi

```java
if (q == VN) return t * 10 / 100;
if (q == JP) return t * 8  / 100;
return 0;                          // <- nước Đức rơi vào đây
```

Với thuế, **0 là một con số hoàn toàn hợp lệ** (nước Mỹ đúng là 0%), nên không ai nghi ngờ. Không
ngoại lệ, không cảnh báo, và ba nơi có ba bộ test riêng — mỗi bộ đều xanh.

### `MienThue` khác `chưa cấu hình`, dù cả hai đều ra 0

Đây là điểm tinh tế nhất của bài. Một cái là **quyết định nghiệp vụ**, cái kia là **lỗi**. Chuỗi
`if-else` không phân biệt được hai thứ đó; bảng chính sách thì có — vì `MienThue` có `moTa()` là
*"không thuế liên bang"*, còn thiếu chính sách thì ném ngoại lệ.

> `getOrDefault(q, mặc định)` / `.get(q, MienThue())` là một trong những dòng nguy hiểm nhất trong
> mã nghiệp vụ. Null Object ([bài 64](../64-null-object/)) chỉ đúng khi *"không có gì"* là hành vi
> **hợp lệ**. Với thuế thì không: thiếu chính sách là tin xấu, và tin xấu phải kêu to.

### Kiểm đủ chính sách bằng máy

```java
for (QuocGia q : QuocGia.values())
    check(BANG_THUE.containsKey(q), "thiếu chính sách thuế cho " + q);
```

Thêm `QuocGia.FR` vào enum mà quên thêm chính sách → **test đỏ ngay**, trước khi có đơn hàng nào từ
Pháp. Với chuỗi `if-else` thì không viết được bài test tương đương, vì không có gì để liệt kê —
nhánh `else` luôn "xử lý được" mọi giá trị.

Đây là lý do khoá phải là **enum**, không phải chuỗi: `"VN"` / `"vn"` / `"VNM"` không liệt kê được,
gõ sai không ai biết, và IDE không tìm được mọi nơi dùng. Chuỗi chỉ xuất hiện ở **biên** và được
đổi sang enum ngay tại đó ([bài 76](../76-fail-fast/), [78](../78-dto-mapping/)).

### Hai trục độc lập: 4 + 3, không phải 4 × 3

Cám dỗ: một lớp cho mỗi tổ hợp — `ThueVnKhachVang`, `ThueDeKhachBac`… Con số nổ theo cấp số nhân:

| | Trộn trục | Tách trục |
|---|---|---|
| 4 quốc gia × 3 hạng | 12 lớp | **7** |
| thêm trục kênh bán (×3) | 36 lớp | **10** |

Quy tắc: **mỗi trục biến thiên là một bảng chính sách riêng**, và tầng ứng dụng ghép chúng lại.

Chú ý **thứ tự ghép**: giảm giá trước, thuế sau — thuế tính trên số tiền thực trả. Đó là một luật
nghiệp vụ, và nó nằm ở tầng ứng dụng vì nó nói về *quan hệ* giữa hai chính sách chứ không thuộc
chính sách nào. Đảo thứ tự là sai luật thuế ở hầu hết các nước — loại bug không ai phát hiện cho
tới lúc bị kiểm toán.

### Policy vs Strategy vs Specification

| Mẫu | Trả lời câu hỏi | Chọn lúc nào |
|---|---|---|
| **Specification** ([87](../87-specification/)) | *"có thoả mãn không?"* | ghép lúc viết code |
| **Policy** | *"luật ở ngữ cảnh này là gì?"* | tra **lúc chạy** |
| **Strategy** | *"làm bằng cách nào?"* | tra lúc chạy |

Policy và Strategy có **hình dạng giống hệt nhau**. Khác nhau ở **ý định**: strategy đổi *cách làm*
cho cùng một kết quả (sắp xếp nhanh hay chậm, kết quả như nhau); policy đổi *chính kết quả* vì
nghiệp vụ ở ngữ cảnh đó khác. Nhầm lẫn không gây bug, nhưng gọi đúng tên giúp người sau biết được
phép đổi cái gì mà không phá gì.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Chuỗi `if-else` n nhánh | O(n) — và nhánh cuối cùng chậm nhất | O(1) |
| Tra bảng chính sách | **O(1)** — `EnumMap`/mảng theo chỉ số | O(số ngữ cảnh) |
| Policy lúc biên dịch (C++) | **0** — nội tuyến, không gọi ảo | 0 |
| Kiểm đủ chính sách | O(số ngữ cảnh) — chỉ chạy lúc test | O(1) |
| Số lớp khi có k trục, mỗi trục m giá trị | trộn: **O(mᵏ)** · tách: **O(m·k)** | — |

Dòng cuối là con số đáng nhớ nhất của bài: không phải chi phí lúc chạy, mà là **chi phí bảo trì**,
và nó là thứ duy nhất tăng theo cấp số nhân.

## 4. Lời giải

- [`PolicyDemo.java`](PolicyDemo.java) — `EnumMap` + vòng lặp `QuocGia.values()` để kiểm đủ chính
  sách bằng máy. Đây là công cụ mạnh nhất trong ba bản: `enum` của Java liệt kê được **tất cả** ngữ
  cảnh, nên câu hỏi *"đã đủ chưa"* trở thành một dòng test.
- [`PolicyDemo.cpp`](PolicyDemo.cpp) — C++ là ngôn ngữ duy nhất có **hai** cách làm policy hoàn
  toàn khác nhau. Phần 5 đặt cạnh nhau: policy **lúc biên dịch** (`MayTinhTien<ThueVn>` — không gọi
  ảo, nội tuyến được, nhưng không đọc được cấu hình) và policy **lúc chạy** (bảng tra). Mẹo
  `SO_LUONG` ở cuối `enum class` cho phép mảng tự dài ra khi thêm ngữ cảnh — bù cho việc C++ không
  có `values()`.
- [`policy_demo.py`](policy_demo.py) — cách đăng ký gọn nhất: một decorator `@dang_ky(QuocGia.DE)`.
  Phần 4 chỉ ra cái bẫy đi kèm và chỉ Python mới có: **bảng chỉ đầy khi module được import**. Một
  chính sách nằm trong file chưa ai import là một chính sách không tồn tại — và hệ thống chạy êm
  với thuế 0%.

**Khác biệt giữa ba ngôn ngữ:**

| | Cách đăng ký | Liệt kê hết ngữ cảnh | Bẫy riêng |
|---|---|---|---|
| Java | `EnumMap.put` trong khối `static` | ✅ `values()` | — |
| C++ | mảng theo chỉ số enum | ✅ mẹo `SO_LUONG` (hỏng nếu enum gán giá trị tay) | quên `SO_LUONG` phải ở cuối |
| Python | decorator `@dang_ky(...)` | ✅ `for q in QuocGia` | **module chưa import = chính sách không tồn tại** |

Cách chặn bẫy Python: bài test kiểm đủ phải chạy **sau** khi import gói chính sách, và tốt nhất là
gói đó tự nạp mọi module con bằng `pkgutil.iter_modules`. Nếu không, chính bài test cũng chỉ kiểm
được những gì đã được import.

Khi nào dùng policy lúc biên dịch (C++): ngữ cảnh **cố định và biết lúc biên dịch** (kiểu dữ liệu,
chế độ xử lý nội bộ) → template. Ngữ cảnh **đến từ dữ liệu** (quốc gia của khách, hạng thẻ, cấu
hình từng khách) → bảng tra lúc chạy. Thuế thuộc loại thứ hai: không ai muốn biên dịch lại hệ
thống để đổi thuế suất.

## 5. Thực tế đi làm

**Cạm bẫy #1 — nhánh mặc định trả về giá trị "vô hại".** `return 0`, `return giaGoc`,
`return false` — cả ba đều biến một ngữ cảnh chưa xử lý thành một kết quả trông hợp lệ. Nhánh mặc
định của luật nghiệp vụ nên **ném ngoại lệ**, trừ khi bạn viết được ra giấy vì sao giá trị đó là
đúng cho mọi ngữ cảnh chưa biết.

**Cạm bẫy #2 — khoá tra chính sách là chuỗi.** `"VN"`, `"vn"`, `"Vietnam"`, `"VNM"` — bốn cách viết
cho một nước, và không cách nào bị compiler chặn. Enum hoặc value object, luôn luôn; chuỗi chỉ sống
ở biên.

**Cạm bẫy #3 — trộn hai trục biến thiên vào một cây lớp.** Dấu hiệu: tên lớp có hai danh từ ghép
(`ThueVnKhachVang`). Số lớp sẽ nhân lên mỗi khi thêm một trục, và không ai dám thêm trục thứ ba.

**Cạm bẫy #4 — chính sách giữ trạng thái.** Policy phải là **không trạng thái** (như domain
service, [bài 86](../86-domain-service/)) để chia sẻ được một thể hiện duy nhất. Một policy có
`private int soLanGoi` là một policy không dùng được đồng thời, và bug của nó rất khó tái hiện.

**Cạm bẫy #5 — cấu hình chính sách bằng file mà không kiểm tra lúc khởi động.** Đọc thuế suất từ
YAML thì linh hoạt, và cũng có nghĩa là một dòng thiếu trong file chỉ lộ ra khi có đơn hàng từ nước
đó. Kiểm **đủ mọi enum** ngay lúc khởi động (fail fast), đừng đợi tới lúc dùng.

**Cạm bẫy #6 — policy biết quá nhiều về ngữ cảnh.** Nếu `ChinhSachThueVN` cần biết hạng khách, thì
hai trục đang rò vào nhau. Mỗi policy chỉ nhận đúng dữ liệu nó cần tính, và tầng ứng dụng lo việc
ghép.

**Biến thể phỏng vấn thường hỏi:**
- *"Strategy và Policy khác nhau ra sao?"* — Cùng hình dạng, khác ý định: strategy đổi cách làm,
  policy đổi kết quả vì nghiệp vụ ở ngữ cảnh đó khác. Nói thêm được điều này cho thấy bạn phân biệt
  được "mẫu thiết kế" với "vấn đề mẫu thiết kế đó giải".
- *"Làm sao chắc chắn không thiếu chính sách cho một quốc gia?"* — Enum liệt kê hết + một test duyệt
  `values()`. Đây là câu hỏi đo được: nếu ứng viên trả lời "code review kỹ" thì họ chưa gặp bug này.
- *"Chính sách nên đọc từ CSDL hay viết trong code?"* — Tuỳ nhịp thay đổi. Thuế suất đổi vài năm một
  lần → code, có review, có lịch sử git. Khuyến mãi đổi hằng tuần → dữ liệu. Nhưng **cấu trúc** luật
  luôn ở code; chỉ **tham số** mới ở dữ liệu.
- *"Bao nhiêu policy thì là quá nhiều?"* — Không có con số, nhưng có dấu hiệu: nếu số lớp policy
  bằng tích của hai danh sách, bạn đang trộn trục. Tách ra và ghép.
- *"Ở C++ khi nào dùng template thay vì lớp ảo?"* — Khi tập ngữ cảnh cố định và biết lúc biên dịch,
  và khi chi phí gọi ảo thật sự đáng kể (vòng lặp nóng). Với luật nghiệp vụ đọc từ dữ liệu thì
  không bao giờ.

## 6. Self-check

```bash
cd 04-competitive/88-policy-object
javac PolicyDemo.java && java PolicyDemo        # in "OK"
g++ -std=c++17 -o sol PolicyDemo.cpp && ./sol   # in "OK"
python policy_demo.py                           # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
