# 85 — Repository & Unit of Work: 50.000đ bay hơi vì một dòng code không tồn tại

Bài 50 dạy repository ở mức *"tách logic khỏi nơi lưu dữ liệu"*. Ở mức miền còn hai câu khó hơn:
**ai quyết định thời điểm ghi**, và **chuyện gì xảy ra khi cùng một aggregate được tải hai lần
trong một use case**. Cả hai dẫn thẳng tới Unit of Work — thứ mọi ORM đều có sẵn nhưng rất ít
người biết mình đang dùng.

## 1. Đề bài

Dựng một kho đơn hàng, rồi chứng minh bằng code ba con bug mà hệ thống **không có** Unit of Work
đều mắc:

| # | Bug | Đo bằng gì |
|---|---|---|
| 1 | Cùng một đơn tải hai lần → **mất thay đổi** | 95.000 thay vì 105.000 |
| 2 | Lệnh thứ hai hỏng → **ghi nửa vời** | `DH-10` nằm trong CSDL dù nghiệp vụ chưa xong |
| 3 | Quên gọi `luu()` → **thay đổi bay hơi** | 50.000 biến mất, không dấu vết |

**Ràng buộc:**
- Giao diện kho nằm ở **tầng miền**; cài đặt nằm ở hạ tầng.
- Kho chỉ trả về **aggregate root**.
- Unit of Work phải rollback **kể cả khi thoát bằng ngoại lệ**, và không ai phải nhớ gọi nó.

**Input/Output mẫu:**
```
KHÔNG có Unit of Work:
  a = kho.tim("DH-01")   -> object #1, 100.000
  b = kho.tim("DH-01")   -> object #2, 100.000     <- HAI object
  a.themPhi(10.000)      -> #1 = 110.000
  b.giamGia(5.000)       -> #2 =  95.000
  kho.luu(a); kho.luu(b) -> CSDL = 95.000          <- phí 10.000 BIẾN MẤT

CÓ Unit of Work:
  a2 = uow.tim("DH-02")  ┐ CÙNG một object
  b2 = uow.tim("DH-02")  ┘
  -> commit -> CSDL = 105.000                      <- cả hai thay đổi đều còn
```

## 2. Ý tưởng

### Unit of Work làm ba việc, và cả ba là hệ quả của một ý

> **Gom mọi thay đổi lại, rồi quyết định ghi hay bỏ *một lần*, ở cuối.**

| Việc | Cơ chế | Chữa bug nào |
|---|---|---|
| **Bản đồ định danh** | một mã → đúng một object trong suốt use case | #1 mất thay đổi |
| **Một điểm quyết định** | `commit()` ghi hết, không commit thì không ghi gì | #2 ghi nửa vời |
| **Theo dõi thay đổi** | object lấy từ UoW thì không cần gọi `luu()` | #3 quên lưu |

### Vì sao bug #1 nguy hiểm hơn vẻ ngoài

Hai chỗ khác nhau trong cùng một use case cùng cần đơn `DH-01` — một hàm tính phí, một hàm tính
khuyến mãi, cả hai đều tự đi tải. Mỗi lần đọc dựng một object mới (đúng như mọi ORM/driver thật
làm), nên hai hàm đang sửa **hai bản sao khác nhau**. Lệnh ghi cuối cùng thắng, và nó ghi đè bằng
một bản đọc từ *trước*.

Đây là **lost update ở ngay trong một tiến trình** — chưa cần hai người dùng, chưa cần hai máy chủ
([bài 92](../92-optimistic-concurrency/) lo trường hợp đó). Không ngoại lệ, không cảnh báo.

### Vì sao bug #3 không bắt được bằng code review

Thứ thiếu là **một dòng không tồn tại**. Compiler không biết, linter không biết, và người review
phải nhớ hết mọi nhánh của mọi hàm. Cách chữa duy nhất có hiệu lực là làm cho dòng đó *không cần
tồn tại*: object lấy từ Unit of Work được theo dõi sẵn, và `commit()` ghi tất cả.

Đây chính là điều `EntityManager` (JPA), `DbContext` (EF), `Session` (Hibernate/SQLAlchemy) làm.
Hiểu nó thì hết ngạc nhiên vì sao *đôi khi sửa một field xong không gọi save mà dữ liệu vẫn đổi*
— hoặc ngược lại.

### Repository chỉ trả aggregate root

```
ĐÚNG : KhoDonHang.timTheoMa("DH-01")   -> DonHang (có luôn các dòng bên trong)
SAI  : KhoDongHang.timTheoDon("DH-01") -> List<DongHang>
```

Cái sai cho phép sửa dòng hàng mà **không đi qua** đơn hàng, nghĩa là bất biến *"tổng ≤ hạn mức"*
mất tác dụng — đúng con bug ở [bài 83](../83-aggregate-boundary/) phần 2.

> **Quy tắc đếm được: số repository = số aggregate root.** Nhiều hơn là dấu hiệu có kho cho thứ
> không phải root.

### Giao diện kho thuộc về miền, cài đặt thuộc về hạ tầng

Phép thử nhanh: **`import` / `#include` nào xuất hiện trong file miền của bạn?** Nếu có
`javax.persistence`, `org.hibernate`, `java.sql`, `<pqxx>`, `sqlalchemy` — thì miền đang phụ thuộc
hạ tầng, và mọi lời hứa còn lại của kiến trúc này đều rỗng ([bài 66](../66-dependency-inversion/),
[98](../98-hexagonal/)).

### Cạm bẫy `Repository<T, ID>` tổng quát

Rất hấp dẫn: một giao diện với `findAll`, `deleteAll`, `count`, `saveAll` dùng chung cho mọi
aggregate. Ba vấn đề, nặng dần:

1. Vi phạm ISP ([bài 52](../52-interface-segregation/)) — kho đơn hàng không có nghĩa gì với `deleteAll()`.
2. Nó nói bằng ngôn ngữ **CSDL**, không nói bằng ngôn ngữ nghiệp vụ ([bài 81](../81-ubiquitous-language/)):
   `timDonQuaHan(ngay)` mang nghĩa; `findByStatusAndDateLessThan` thì không.
3. `findAll()` trên bảng 10 triệu dòng là **khẩu súng đã lên đạn**, và nó nằm sẵn trong mọi kho chỉ
   vì "cho tổng quát".

Kho tốt thường có 3–6 phương thức, tất cả đọc lên thành câu nghiệp vụ.

### Unit of Work ≠ transaction của CSDL

Hai thứ hay bị nhầm là một. UoW là khái niệm ở **tầng ứng dụng**; transaction là cơ chế của CSDL.
Chúng thường trùng ranh giới, nhưng không phải lúc nào cũng — UoW trên kho trong bộ nhớ không có
transaction nào cả, và một saga ([bài 97](../97-saga/)) có nhiều UoW, mỗi cái một transaction
riêng. Nhầm hai thứ dẫn tới thói quen tai hại: mở transaction ở tầng controller và giữ suốt
request, kể cả trong lúc gọi API bên ngoài — khoá CSDL bị giữ vài giây chờ mạng.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| `tim()` — lần đầu | O(1) tra bản đồ + **1 lần đọc CSDL** | O(1) |
| `tim()` — lần thứ hai trở đi | O(1), **0 lần đọc CSDL** | O(0) thêm |
| `commit()` | O(k) — k = số aggregate đã chạm | O(1) |
| Rollback (`close`/`__exit__`/destructor) | O(k) vứt bản đồ | O(1) |
| Bộ nhớ của UoW | — | **O(k)** giữ toàn bộ aggregate đã chạm cho tới commit |

Hai điều đáng chú ý. Thứ nhất, bản đồ định danh **giảm** số lần đọc CSDL — tải cùng một đơn 5 lần
tốn đúng 1 lần đọc. Thứ hai, O(k) bộ nhớ là lý do UoW phải có **phạm vi ngắn**: mở một UoW rồi
duyệt 100.000 bản ghi trong đó là cách chắc chắn nhất để hết RAM.

## 4. Lời giải

- [`UnitOfWorkDemo.java`](UnitOfWorkDemo.java) — `AutoCloseable` + `try-with-resources`. Rollback
  nằm trong `close()`, nên nó chạy cả trên đường ngoại lệ — **nếu** người gọi nhớ viết `try`.
- [`UnitOfWorkDemo.cpp`](UnitOfWorkDemo.cpp) — C++ không cần gì cả: RAII làm rollback thành **hành
  vi mặc định**, và `= delete` copy khiến UoW không thể bị mang ra khỏi phạm vi của nó. Phần 1 cho
  nổ một cái bẫy chỉ C++ mới có: kho trả `std::optional<DonHang>` (**theo giá trị**) làm bản đồ
  định danh trở nên vô nghĩa — mọi sửa đổi rơi vào bản sao, và không dòng nào trông sai cả.
- [`unit_of_work_demo.py`](unit_of_work_demo.py) — `with uow:` đọc lên gần như một câu tiếng Anh.
  Phần 5 là cái bẫy riêng của Python: `__exit__` trả về `True` sẽ **nuốt ngoại lệ**. Rollback vẫn
  đúng, nhưng người gọi không hề biết — nó đi tiếp, gửi email "đơn hàng đã được tạo", trả HTTP 200.
  Đơn hàng thì không tồn tại.

**Khác biệt giữa ba ngôn ngữ:**

| | Cơ chế rollback | Người gọi có thể quên? | Cái bẫy riêng |
|---|---|---|---|
| Java | `AutoCloseable.close()` | ✅ quên `try-with-resources` là mất rollback | — |
| C++ | destructor (RAII) | ❌ không thể quên | kho trả aggregate **theo giá trị** |
| Python | `__exit__` | ✅ quên `with` là mất rollback | `return True` **nuốt ngoại lệ** |

Chi tiết C++ đáng nhớ: `commit()` phải là lệnh **tường minh** vì destructor không được ném ngoại
lệ ([bài 74](../74-resource-lifecycle/)). Destructor chỉ làm việc không bao giờ hỏng — vứt bỏ những
gì chưa ghi.

## 5. Thực tế đi làm

**Cạm bẫy #1 — mỗi lệnh sửa là một `save()`.** Đây là mặc định của mọi codebase chưa có UoW, và nó
sinh ra ghi nửa vời ở mọi nhánh lỗi. Dấu hiệu: hàm nghiệp vụ có 3–4 lời gọi `repository.save()` rải
rác. Gom lại thành một `commit()` ở cuối.

**Cạm bẫy #2 — mỗi hàm tự đi tải aggregate nó cần.** Trông rất sạch (không truyền tham số lằng
nhằng) và là nguyên nhân trực tiếp của bug #1. Truyền aggregate xuống, hoặc dùng UoW làm nơi tra
duy nhất.

**Cạm bẫy #3 — Unit of Work sống quá lâu.** Mở một UoW ở đầu request rồi giữ tới cuối là thói quen
phổ biến nhất, và nó gộp mọi thứ vào một transaction khổng lồ: khoá nhiều, giữ lâu, deadlock. Phạm
vi đúng của UoW là **một use case**, không phải một request.

**Cạm bẫy #4 — repository trả về câu truy vấn.** `IQueryable`, `QuerySet`, `Criteria` — cả ba đều
để hạ tầng rò vào miền: người gọi ghép thêm điều kiện, và câu SQL cuối cùng do tầng ứng dụng quyết
định. Repository trả **dữ liệu**, không trả *khả năng hỏi thêm*.

**Cạm bẫy #5 — dùng repository cho việc đọc báo cáo.** Aggregate là mô hình để **ghi**. Màn hình
danh sách cần 12 cột từ 4 bảng, và tải 500 aggregate để hiển thị nó là sai công cụ. Nhu cầu đọc
giải quyết bằng truy vấn riêng — [bài 95 · CQRS](../95-cqrs-lite/).

**Cạm bẫy #6 — quên rằng ORM *đã* là Unit of Work.** Rất nhiều dự án bọc thêm một lớp
"UnitOfWork" quanh `EntityManager`/`Session`, và giờ có **hai** bản đồ định danh chồng nhau. Nếu
đang dùng ORM đầy đủ, việc cần làm là *lộ ra* UoW có sẵn qua một giao diện của miền, không phải
dựng cái thứ hai.

**Biến thể phỏng vấn thường hỏi:**
- *"Repository khác DAO chỗ nào?"* — DAO là bảng-đối-tượng, một DAO cho một bảng, API kiểu CRUD.
  Repository là **bộ sưu tập aggregate** trong ngôn ngữ nghiệp vụ, một repository cho một aggregate
  root, và nó có thể ghi vào nhiều bảng cho một lần lưu.
- *"Unit of Work để làm gì khi CSDL đã có transaction?"* — Transaction bảo đảm nguyên tử ở tầng
  CSDL; UoW quyết định **cái gì được ghi và khi nào**, cộng với bản đồ định danh — thứ transaction
  hoàn toàn không có. Bug #1 xảy ra bên trong một transaction hoàn hảo.
- *"Bản đồ định danh giải quyết vấn đề gì?"* — Bảo đảm "một aggregate, một object" trong một use
  case. Không có nó thì lost update xảy ra ngay trong một luồng, và số lần đọc CSDL nhân lên theo
  số chỗ cần dữ liệu.
- *"Repository nên trả `null` hay `Optional`?"* — `Optional`/`optional`/`None` có kiểm tra. Nhưng
  câu hỏi hay hơn là: *"không tìm thấy có phải chuyện bất thường không?"* Nếu có, hãy có thêm một
  `layTheoMa()` ném ngoại lệ để chỗ gọi không phải viết `orElseThrow` khắp nơi
  ([bài 77](../77-result-vs-exception/)).
- *"Có nên viết interface repository khi chỉ có một cài đặt?"* — Có, và lý do không phải "để đổi
  CSDL sau này" (hiếm khi xảy ra) mà là **để test miền không cần CSDL** — một fake 5 dòng thay được
  cả Postgres ([bài 68](../68-in-memory-fake/)), và bộ test miền chạy trong mili giây thay vì phút.

## 6. Self-check

```bash
cd 04-competitive/85-repository-unit-of-work
javac UnitOfWorkDemo.java && java UnitOfWorkDemo        # in "OK"
g++ -std=c++17 -o sol UnitOfWorkDemo.cpp && ./sol       # in "OK"
python unit_of_work_demo.py                             # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
