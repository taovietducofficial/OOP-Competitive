# 86 — Domain Service: khi hành vi không thuộc về entity nào

`XxxService` là cái tên bị lạm dụng nhất trong DDD. Đặt một lớp tên như thế rồi đổ mọi thứ vào là
con đường ngắn nhất tới **mô hình thiếu máu** — nơi entity thành cấu trúc dữ liệu và toàn bộ nghiệp
vụ nằm trong các hàm rời. Bài này đưa ra **ba câu hỏi lọc** và **một phép đo chạy được**.

## 1. Đề bài

Mô hình hoá tài khoản ngân hàng và nghiệp vụ chuyển tiền, rồi chứng minh bằng code:

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Luật nằm ngoài entity thì luật bị lách | `soDu == -500.000` mà không ngoại lệ nào |
| 2 | Luật nằm trong entity thì không có đường vòng | gán trực tiếp là lỗi (biên dịch / `AttributeError`) |
| 3 | "Chuyển tiền" **không** thuộc về tài khoản nào | phí 5.000, A giảm 2.005.000, B tăng 2.000.000 |
| 4 | Domain service **không có trạng thái** | 0 field / `__closure__ is None` |

**Ràng buộc:**
- Domain service không được chạm I/O, không giữ repository, không đọc đồng hồ.
- Bảng phí đi vào qua **tham số**, không qua tiêm phụ thuộc.
- Phải phân biệt được ba loại service, và tầng ứng dụng không được tính luật nào.

**Input/Output mẫu:**
```
Mô hình THIẾU MÁU:
  rut_thieu_mau(tk, 200.000)  -> RuntimeError    (luật có hiệu lực)
  tk.so_du = -500.000         -> chạy êm          (đường vòng mở sẵn)

Mô hình ĐÚNG:
  tk.rut(200.000)             -> RuntimeError
  tk.so_du = -500.000         -> AttributeError   (không có setter)

chuyen_tien(A=5.000.000, B=0, 2.000.000, phí{ngưỡng 1tr: 1.000/5.000})
  -> phí 5.000 · A = 2.995.000 · B = 2.000.000
```

## 2. Ý tưởng

### Mô hình thiếu máu: luật ở ngoài thì luật là lời khuyên

```java
tk.setSoDu(-500_000);   // không ai chặn, không ngoại lệ
```

Đây là dấu hiệu nhận biết: **mọi bất biến đều nằm ngoài entity**, nên chúng chỉ đúng với đoạn code
nhớ gọi đúng chỗ. Một `setSoDu` là đủ để toàn bộ luật nghiệp vụ trở thành lời khuyên.

Ba ngôn ngữ mắc bẫy này theo ba cách khác nhau — và Python dễ mắc nhất:

| | Cách rơi vào thiếu máu | Cách chặn |
|---|---|---|
| Java | viết `getX`/`setX` cho mọi field (IDE sinh sẵn) | không có setter; field `private final` khi được |
| C++ | khai báo `struct` thay vì `class` — **một từ khoá** | `class` + field private |
| Python | `@dataclass` — **ba dòng** cho ra entity toàn field public | `@property` không kèm setter |

### Ba câu hỏi lọc

Trước khi tạo bất kỳ domain service nào, trả lời cả ba. Phải **có** cả ba thì mới cần.

| | Câu hỏi | "Chuyển tiền" |
|---|---|---|
| **a** | Đây có phải **luật nghiệp vụ** không? (không phải điều phối, không phải I/O) | có |
| **b** | Nó có thuộc về đúng **một** entity không? *Nếu có → đặt vào entity đó, xong.* | **không** — hai tài khoản ngang nhau |
| **c** | Ép vào một entity có làm entity đó phải **sửa entity khác** không? | có |

Nếu nhét vào entity: `a.chuyenToi(b, 2_000_000)` bắt `TaiKhoan` gọi `b.nap(...)` — một aggregate
sửa một aggregate khác trong cùng lời gọi, đúng thứ [bài 83](../83-aggregate-boundary/) cấm. Và câu
hỏi *"phí do bên nào chịu"* bỗng thành trách nhiệm của lớp `TaiKhoan`, dù nó là luật của **dịch vụ
chuyển tiền** chứ không phải của tài khoản.

### Domain service không có trạng thái, không chạm I/O

```java
static final class DichVuChuyenTien {
    // KHÔNG field. Không repository, không đồng hồ, không cấu hình.
    BienLai chuyen(TaiKhoan tu, TaiKhoan den, long soTien, BieuPhi bieuPhi) { ... }
}
```

Bảng phí đi vào qua **tham số** dưới dạng value object, không phải một repository được tiêm. Hệ quả
trực tiếp: **test nó không cần gì cả** — không mock, không fake, không CSDL.

> Nếu domain service của bạn cần một repository để chạy, thì hoặc nó là application service đội
> lốt, hoặc dữ liệu nó cần phải được **truyền vào**.

### Ba loại "service" — thứ hay bị gộp làm một

|  | **Domain service** | **Application service** | **Infrastructure** |
|---|---|---|---|
| Trả lời | *"luật là gì?"* | *"quy trình là gì?"* | *"làm thế nào?"* |
| Ví dụ | `chuyenTien` | `UngDungChuyenTien` | `GuiThongBao` |
| Có trạng thái | **không** | không | thường có |
| Chạm I/O | **không** | có (qua interface) | **có** |
| Mở transaction | **không** | **có** | không |
| Nằm ở tầng | miền | ứng dụng | hạ tầng |
| Test cần gì | không cần gì | fake ([bài 68](../68-in-memory-fake/)) | môi trường thật |

Sai lầm phổ biến nhất: gộp cột 1 và cột 2 thành một lớp `OrderService` dài 800 dòng, vừa mở
transaction vừa tính luật vừa gửi email.

### Ranh giới ngược lại

Cám dỗ ngược cũng có thật: tạo `DichVuRutTien` cho việc `tk.rut(tien)`. Câu hỏi **(b)** trả lời
*có* — hành vi thuộc về đúng một entity — nên nó phải nằm trong entity, và một service ở đây chỉ
thêm một lớp vô nghĩa.

> **Domain service là ngoại lệ, không phải mặc định.** Nếu miền của bạn có nhiều service hơn
> entity, thì bạn đang viết mô hình thiếu máu và gọi nó là DDD.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| `chuyenTien` | O(1) — hai phép cộng trừ + một lần tra biểu phí | O(1) |
| `BieuPhi.tinhPhi` | O(1) — một so sánh ngưỡng | O(1) |
| Phép đo thiếu máu (Java/Python) | O(số thành viên của lớp) — chỉ chạy lúc test | O(số thành viên) |

Domain service **không** làm chương trình chậm đi: nó chỉ là nơi *đặt* một phép tính vốn phải xảy
ra. Chi phí thật của việc chọn sai chỗ đặt không tính bằng O lớn — nó tính bằng số nơi phải sửa
khi luật đổi.

## 4. Lời giải

- [`DomainServiceDemo.java`](DomainServiceDemo.java) — dùng reflection để **đo** tỉ lệ getter/setter:
  `TaiKhoanThieuMau` 100%, `TaiKhoan` 0%. Đây là một bài test kiến trúc chạy được, không phải một
  cảm giác khi review.
- [`DomainServiceDemo.cpp`](DomainServiceDemo.cpp) — C++ nói ra bản chất thật: domain service là
  một **hàm tự do** trong `namespace mien`, không phải một lớp. Nó không có `this`, không có
  constructor để ai tiêm repository vào — **trạng thái bằng 0 vì không có chỗ nào để cất**.
  Đổi lại, C++ có cái bẫy ngược: `struct` thay vì `class` là đủ để mọi field thành public.
- [`domain_service_demo.py`](domain_service_demo.py) — `@property` không kèm setter chặn
  `tk.so_du = -500_000` ngay tại chỗ. Phần 5 chỉ ra cạm bẫy riêng của Python: **lớp chỉ chứa
  `@staticmethod`** là một namespace đội lốt object — Python đã có namespace, tên nó là *module*.

**Khác biệt giữa ba ngôn ngữ:**

| | Hình dạng tự nhiên của domain service | Chặn ghi thẳng vào field | Đo được thiếu máu bằng máy |
|---|---|---|---|
| Java | lớp không field (bắt buộc phải là lớp) | không có setter | ✅ reflection |
| C++ | **hàm tự do** trong namespace | ✅ `private` — **lỗi biên dịch** | ❌ (nhưng `grep struct` là đủ) |
| Python | **hàm mức module** | `@property` → `AttributeError` | ✅ `vars()` + `property.fset` |

Ghi chú về Python: `tk._so_du = -500_000` thì **vẫn chạy**. `@property` không phải khoá — nó là
một cái cửa có biển "lối này", và nó chặn được đúng thứ cần chặn: người viết code bình thường,
đang vội, không cố tình phá.

## 5. Thực tế đi làm

**Cạm bẫy #1 — `@dataclass` / `struct` / IDE sinh getter-setter làm entity.** Ba cách khác nhau để
tạo ra cùng một thứ: một lược đồ CSDL đội lốt object. Nó *trông* như mô hình miền tử tế, và không
có gì cảnh báo. Phép đo ở phần 2 tồn tại chính vì lý do này.

**Cạm bẫy #2 — tiêm repository vào domain service.** Rất tự nhiên ở Java ("đã là bean rồi thì tiêm
vào thôi"), và nó biến domain service thành application service mà không ai để ý. Từ đó test miền
cần mock, và mọi luật nghiệp vụ đều dính I/O.

**Cạm bẫy #3 — service tính luật, entity chỉ giữ dữ liệu.** Dấu hiệu: `OrderService.calculateTotal(order)`
thay vì `order.total()`. Hỏi: *nếu ngày mai có một chỗ thứ hai cần tổng tiền, nó có gọi đúng hàm
này không?* Trong mô hình thiếu máu, câu trả lời thường là "không, họ tự cộng lại".

**Cạm bẫy #4 — một `XxxService` cho mỗi entity.** `OrderService`, `CustomerService`,
`ProductService` — cấu trúc này không đến từ nghiệp vụ, nó đến từ thói quen. Domain service tốt
mang tên một **động từ**: `chuyenTien`, `tinhLaiSuat`, `kiemTraTrungLap` — và thường có đúng **một**
phương thức công khai.

**Cạm bẫy #5 — gộp domain service với application service.** Lớp vừa mở transaction vừa tính luật
là lớp không test được nhanh, và luật nghiệp vụ trong đó không dùng lại được ở chỗ khác (job nền,
API import hàng loạt). Tách ra: điều phối ở ngoài, luật ở trong.

**Cạm bẫy #6 — dùng domain service để né việc thiết kế.** "Chưa biết đặt cái này ở đâu → cho vào
service" là cách một `UtilService` 2.000 dòng ra đời. Nếu ba câu hỏi lọc không trả lời *có* cả ba,
thì hành vi đó thuộc về một entity hoặc value object nào đó — hãy tìm cho ra chỗ đó.

**Biến thể phỏng vấn thường hỏi:**
- *"Mô hình thiếu máu (anemic domain model) là gì và vì sao nó xấu?"* — Entity chỉ có dữ liệu, luật
  nằm ở service. Xấu vì luật chỉ có hiệu lực với ai nhớ gọi đúng chỗ — chứng minh bằng `setSoDu(-500_000)`.
  Và vì không có một nơi duy nhất để trả lời *"luật là gì"*.
- *"Khi nào dùng domain service?"* — Ba câu hỏi lọc ở phần 2. Nhấn mạnh câu (b): nếu hành vi thuộc
  về đúng một entity thì service là thừa.
- *"Domain service khác application service ra sao?"* — Bảng ở phần 2. Câu ngắn nhất: domain service
  trả lời *"luật là gì"*, application service trả lời *"quy trình là gì"* — và chỉ cái sau được mở
  transaction, được chạm I/O.
- *"Domain service có được gọi repository không?"* — Không. Nếu cần dữ liệu, tầng ứng dụng tải nó
  rồi truyền vào. Ngoại lệ hiếm gặp mà một số tài liệu chấp nhận: truyền vào một **cổng chỉ đọc**
  rất hẹp (ví dụ `KiemTraTrungMa`), nhưng lúc đó nó là một cổng của miền, không phải một repository.
- *"Nếu luật cần dữ liệu của 10.000 bản ghi thì truyền vào kiểu gì?"* — Đó là dấu hiệu luật này
  không thuộc về miền mà thuộc về một truy vấn ([bài 95](../95-cqrs-lite/)), hoặc thứ cần truyền vào
  là *kết quả tổng hợp* chứ không phải 10.000 bản ghi.
- *"Tại sao Python/C++ không cần lớp cho domain service?"* — Vì domain service không phải một
  object; nó là một phép tính không trạng thái. Java bắt phải có lớp nên khái niệm này ở Java hay bị
  hiểu nhầm thành "một thành phần có vòng đời".

## 6. Self-check

```bash
cd 04-competitive/86-domain-service
javac DomainServiceDemo.java && java DomainServiceDemo        # in "OK"
g++ -std=c++17 -o sol DomainServiceDemo.cpp && ./sol          # in "OK"
python domain_service_demo.py                                 # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
