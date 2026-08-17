# 98 — Hexagonal: lục giác không phải một cấu trúc thư mục

Bài này gom lại mọi thứ tầng đã dựng. Bài 66 nói tầng cao không phụ thuộc chi tiết; bài 85 nói
interface kho thuộc về miền; bài 94 nói bọc hệ ngoài lại. **Lục giác là cái tên của hình dạng mà
ba điều đó cộng lại tạo ra** — và điểm mấu chốt ít ai nói rõ:

> Nó **không** phải một cấu trúc thư mục. Nó là một **luật về chiều của `import`**, và luật đó
> kiểm được bằng máy.

Đổi tên gói thành `domain/`, `infrastructure/` mà `import` vẫn đi từ trong ra ngoài thì chẳng có gì
thay đổi.

## 1. Đề bài

Use case "đặt hàng" cần: lưu đơn, báo cho khách, biết bây giờ là mấy giờ.

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Miền gọi thẳng hạ tầng → **không test được** | phải dựng CSDL mới chạy được một luật |
| 2 | Lõi chạy với **0** hạ tầng | 3 dòng dựng bối cảnh, kết quả tất định |
| 3 | Đổi bộ nối → sửa **1 dòng** ở gốc lắp ráp | cùng logic, lần này sinh SQL |
| 4 | Chiều phụ thuộc kiểm được bằng máy | **0** tham chiếu từ lõi ra hạ tầng |

**Ràng buộc:** cổng phải nói tiếng nghiệp vụ; cả cổng điều khiển lẫn cổng bị điều khiển đều do
**miền** định nghĩa.

**Input/Output mẫu:**
```
new DichVuDatHang(khoTrongBoNho, baoGia, () -> 1_700_000_000)
   .thucHien("KH-01", 250.000)
-> DonHang("DH-1", "KH-01", 250.000, 1.700.000.000)     ← tất định, 0 kết nối CSDL
```

## 2. Ý tưởng

### Hai loại cổng, và vì sao phải phân biệt

| Loại | Ai gọi ai | Ví dụ | Bộ nối |
|---|---|---|---|
| Cổng **điều khiển** | thế giới → miền | `DatHang` | REST, CLI, hàng đợi |
| Cổng **bị điều khiển** | miền → thế giới | `KhoDonHang`, `DongHo` | CSDL, SMTP, đồng hồ |

**Cả hai đều do miền định nghĩa** — đó là điểm mấu chốt và cũng là chỗ hay sai. Với cổng bị điều
khiển thì ai cũng hiểu; với cổng điều khiển thì người ta hay để khung web định nghĩa (controller
gọi thẳng vào lớp dịch vụ). Hậu quả: chữ ký use case bị định hình bởi HTTP, và một job nền muốn
dùng lại thì phải giả lập request.

### Cổng phải nói tiếng nghiệp vụ, không nói tiếng bộ nối

```java
// CỔNG RÒ RỈ — "là interface" nên trông như đã đảo ngược, nhưng chưa:
interface KhoDonHang { ResultSet query(String sql); }
```

Ba hậu quả:

1. **Không viết nổi bản trong bộ nhớ** (lấy đâu ra `ResultSet`?) → mất luôn cái lợi lớn nhất.
2. Miền vẫn phải `import java.sql` → chiều phụ thuộc **vẫn ngược**.
3. Đổi sang kho khoá-giá trị là phải sửa cổng, tức là **sửa miền**.

Phép thử: **đọc tên phương thức của cổng lên**. Người làm nghiệp vụ hiểu được thì cổng đúng; chỉ
lập trình viên hiểu thì đó là bộ nối đội lốt cổng ([bài 81](../81-ubiquitous-language/)).

### Vì sao "đổi CSDL sau này" KHÔNG phải lý do chính

Nó hiếm khi xảy ra thật. Lý do chính là: **bộ nối thứ hai — bản trong bộ nhớ — cho phép test, và nó
được dùng hằng ngày**. Cái lợi có ngay từ tuần đầu, không phải sau ba năm
([bài 68](../68-in-memory-fake/)).

Hệ quả dây chuyền khi thiếu nó, theo thứ tự người ta nhận ra: test chậm → test giòn (hỏng vì CSDL,
không phải vì bug) → không ai chạy test nữa → không ai viết test nữa. Và nó bắt đầu từ **đúng một
dòng** `new KetNoiCsdl()` nằm sai chỗ.

### Gốc lắp ráp

Có đúng **một** chỗ trong chương trình được phép khởi tạo cả miền lẫn hạ tầng — `main`, hoặc lớp
cấu hình. Mọi chỗ khác chỉ nhận phụ thuộc qua constructor ([bài 51](../51-dependency-injection/)).

```
main() -> new DichVuDatHang(new KhoSql(),        new GuiEmailThat(), Instant::now)
test() -> new DichVuDatHang(new KhoTrongBoNho(), new BaoGia(),       () -> 1_700_000_000L)
```

Hai dòng đó là **toàn bộ** khác biệt giữa chạy thật và chạy test. Nếu để đổi sang test bạn phải sửa
file cấu hình, đặt biến môi trường, hay bật một "profile", thì gốc lắp ráp chưa tồn tại.

### Khi nào KHÔNG cần lục giác

Nó có chi phí thật: mỗi cổng một interface, mỗi bộ nối một lớp. Với một ứng dụng CRUD thuần thì đó
là ba lớp cho một việc mà `save()` làm xong. Ba dấu hiệu **đủ** để cần:

- có luật nghiệp vụ đáng test riêng (không chỉ đọc/ghi bảng);
- có nhiều hơn một đường vào (REST + hàng đợi + job nền);
- có hệ ngoài mà bạn không kiểm soát ([bài 94](../94-anti-corruption-layer/)).

Thiếu cả ba thì một controller gọi thẳng repository là **thiết kế đúng** — và biết lúc nào *không*
áp dụng một mẫu cũng là một phần của việc hiểu nó.

## 3. Độ phức tạp

| | Chi phí |
|---|---|
| Gọi qua cổng ảo | một lời gọi gián tiếp — không đáng kể so với một lượt truy vấn CSDL |
| Gọi qua cổng template (C++) | **0** — nội tuyến được |
| Test lõi | **0** hạ tầng: mili-giây, không mạng, tất định |
| Test qua hạ tầng thật | trăm mili-giây → giây, và hỏng vì lý do không liên quan |
| Thêm một cổng | +1 interface, +1 bộ nối thật, +1 bộ nối giả |

Con số quyết định không nằm ở thời gian chạy mà ở **thời gian phản hồi của bộ test**: bộ test miền
chạy trong mili-giây được chạy sau mỗi lần lưu file; bộ test cần CSDL được chạy trước khi về nhà.

## 4. Lời giải

- [`HexagonalDemo.java`](HexagonalDemo.java) — hai loại cổng là hai interface trong miền; phần 5 là
  một **bài test kiến trúc** bằng reflection: quét field/tham số/kiểu trả về của mọi lớp lõi, fail
  nếu có kiểu hạ tầng lọt vào (thực tế dùng ArchUnit).
- [`HexagonalDemo.cpp`](HexagonalDemo.cpp) — ở C++ "chiều phụ thuộc" có một phiên bản **cứng hơn**:
  chiều của `#include`. Vi phạm không chỉ làm bẩn kiến trúc mà còn khiến mọi file của miền dịch lại
  khi thư viện hạ tầng đổi một dòng ([bài 93](../93-bounded-context/)). Phần 7 cho lựa chọn thứ hai
  mà hai ngôn ngữ kia không có: **cổng dưới dạng tham số template** — đảo ngược phụ thuộc với 0 lời
  gọi ảo.
- [`hexagonal_demo.py`](hexagonal_demo.py) — Python có thứ mạnh nhất trong ba: **`typing.Protocol`**.
  Bộ nối **không cần biết cổng tồn tại** — phụ thuộc bằng 0 ở *cả hai* chiều. File chứng minh bằng
  một lớp "thư viện bên thứ ba" chưa bao giờ nghe tên miền của bạn mà vẫn cắm vào chạy được. Kèm
  cạm bẫy: `runtime_checkable` chỉ kiểm **tên** phương thức, không kiểm chữ ký.

**Khác biệt giữa ba ngôn ngữ:**

| | Cách khai báo cổng | Bộ nối phải biết cổng? | Kiểm chiều phụ thuộc |
|---|---|---|---|
| Java | `interface` | ✅ phải `implements` | reflection / ArchUnit |
| C++ | lớp trừu tượng **hoặc** tham số template | ✅ phải kế thừa (bản ảo) | `grep` trên `#include` — **phụ thuộc vật lý** |
| Python | **`Protocol`** (kiểu cấu trúc) | ❌ **không cần biết gì** | `grep` trên `import` + `mypy` |

Ghi chú Python quan trọng: vì `isinstance` với `Protocol` chỉ kiểm tên phương thức, một bộ nối sai
chữ ký vẫn "đạt" và chỉ nổ lúc chạy. **`mypy` là bắt buộc, không phải tuỳ chọn.**

## 5. Thực tế đi làm

**Cạm bẫy #1 — thư mục đúng, `import` sai.** Đổi tên gói thành `domain/`, `infrastructure/` rồi
tuyên bố đã làm lục giác. Luật thật chỉ có một, và nó nằm ở chiều mũi tên. Viết bài test cho nó
ngay ngày đầu.

**Cạm bẫy #2 — cổng mang kiểu của hạ tầng.** `save(Entity)` với `Entity` là lớp của ORM, `send(HttpRequest)`,
`query(...) -> ResultSet`. Dấu hiệu: bạn không viết nổi một bộ nối trong bộ nhớ cho cổng đó.

**Cạm bẫy #3 — để khung phần mềm định nghĩa cổng điều khiển.** Controller gọi thẳng lớp dịch vụ,
và chữ ký use case dần mang hình dạng của HTTP (nhận `Request`, trả `ResponseEntity`). Một job nền
muốn dùng lại luật đó thì phải giả lập một request.

**Cạm bẫy #4 — cổng béo.** Một `Repository` với 25 phương thức là vi phạm ISP
([bài 52](../52-interface-segregation/)) và khiến bộ nối giả phải cài 25 hàm rỗng. Cổng tốt có 2–5
phương thức, tất cả đọc lên thành câu nghiệp vụ.

**Cạm bẫy #5 — quên rằng đồng hồ và số ngẫu nhiên cũng là hạ tầng.** `System.currentTimeMillis()`,
`random()`, `UUID.randomUUID()` nằm trong miền làm test mất tính tất định
([bài 67](../67-clock-injection/)). Chúng phải đi qua cổng như mọi thứ khác.

**Cạm bẫy #6 — gốc lắp ráp nằm rải rác.** Nếu `new KhoSql()` xuất hiện ở năm chỗ, thì không có gốc
lắp ráp — và việc chuyển sang bản test cần sửa năm chỗ. Một chỗ duy nhất, `main`.

**Cạm bẫy #7 — áp lục giác lên mọi thứ.** Một dịch vụ CRUD 300 dòng không cần sáu interface. Ba dấu
hiệu ở phần 2 là điều kiện đủ; thiếu cả ba thì đừng.

**Biến thể phỏng vấn thường hỏi:**
- *"Kiến trúc lục giác là gì?"* — Miền định nghĩa cổng, hạ tầng cung cấp bộ nối, mọi phụ thuộc chỉ
  đi vào trong. Nói thêm *"nó không phải cấu trúc thư mục"* là điểm phân biệt.
- *"Cổng điều khiển và cổng bị điều khiển khác nhau ra sao?"* — Chiều của lời gọi. Và **cả hai** đều
  do miền định nghĩa — đây là phần người ta hay trả lời thiếu.
- *"Lợi ích lớn nhất là gì?"* — **Test.** Không phải "đổi CSDL". Ứng viên trả lời "để đổi database
  sau này" thường chưa dùng thật.
- *"Làm sao đảm bảo chiều phụ thuộc không bị vi phạm?"* — Bài test kiến trúc (ArchUnit / `grep` trên
  `import`/`#include`). Trả lời "code review" cho thấy chưa gặp lúc nó bị vi phạm.
- *"Lục giác, clean architecture, onion — khác nhau chỗ nào?"* — Cùng một luật (phụ thuộc hướng vào
  trong), khác nhau ở số tầng và cách gọi tên. Biết chúng **cùng một ý** quan trọng hơn thuộc sơ đồ
  của từng cái.

## 6. Self-check

```bash
cd 04-competitive/98-hexagonal
javac HexagonalDemo.java && java HexagonalDemo        # in "OK"
g++ -std=c++17 -o sol HexagonalDemo.cpp && ./sol      # in "OK"
python hexagonal_demo.py                              # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
