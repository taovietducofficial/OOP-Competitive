# 87 — Specification: cùng một luật, ba nơi, ba kết quả khác nhau

Java đã có `Predicate<T>` với `and`/`or`/`negate` sẵn. Nên câu hỏi đúng không phải *"specification
là gì"* mà là **"vì sao không dùng luôn `Predicate`"**. Câu trả lời là hai thứ `Predicate` không
làm được: nó không có **tên** để nói cho người dùng biết họ trượt ở đâu, và nó không **dịch** được
sang SQL nên luật buộc phải viết hai lần.

## 1. Đề bài

Luật cho vay tín chấp: *đủ 18 tuổi **và** đủ 100 điểm tích luỹ **và** không đang bị khoá*. Luật này
dùng ở ba nơi — màn hình đăng ký, job gửi email mời vay, báo cáo phòng rủi ro — và còn phải chạy
được trên 2 triệu bản ghi trong CSDL.

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Chép luật ba nơi → ba nơi lệch nhau | 2 · **3** · 2 khách hợp lệ cho *cùng* một luật |
| 2 | Specification **giải thích** được vì sao trượt | 3 mệnh đề trượt, có tên từng cái |
| 3 | Specification **dịch** được sang SQL | câu SQL sinh ra khớp 100% với luật trong code |
| 4 | Ghép luật mới **không sửa** luật cũ | `hoặc` một nhánh mới, 0 dòng cũ bị đụng |

**Ràng buộc:** luật phải là object có tên; ghép bằng `và`/`hoặc`/`không`; không được nối chuỗi SQL
từ dữ liệu người dùng.

**Input/Output mẫu:**
```
KhachHang("KH-9", tuổi 16, điểm 20, bị khoá)

Predicate          -> false
                      (và hết. không nói được vì sao)

Specification      -> ["đủ 18 tuổi", "đủ 100 điểm tích luỹ", "KHÔNG đang bị khoá"]
   .dieuKienSql()  -> ((tuoi >= 18 AND diem >= 100) AND NOT (bi_khoa = 1))
```

## 2. Ý tưởng

### Con bug: một luật, ba bản chép tay

```
màn hình : tuoi >= 18 && diem >= 100 && !biKhoa     -> 2 khách
job email: tuoi >= 18 && diem >= 100                -> 3 khách   <- QUÊN biKhoa
báo cáo  : tuoi >  18 && diem >= 100 && !biKhoa     -> 2 khách   <- `>` thay `>=`
```

Ba dòng đều "chạy đúng" theo ý người viết chúng. **Không test nào hỏng**, vì mỗi chỗ có test riêng
và test đó khớp với code ở chỗ đó. Bug chỉ lộ ra khi ai đó đối chiếu hai màn hình — hoặc khi một
khách hàng đang bị khoá nhận được lời mời vay tiền.

### Ba khả năng của một specification

| Khả năng | Phương thức | `Predicate` có? |
|---|---|---|
| Trả lời đúng/sai | `thoaMan(t)` | ✅ |
| **Có tên**, đọc lên thành câu | `moTa()` | ❌ |
| **Giải thích** trượt ở đâu | `lyDoTruot(t)` | ❌ |
| **Dịch** sang truy vấn | `dieuKienSql()` | ❌ |
| Ghép và/hoặc/không | `va` / `hoac` / `khong` | ✅ |

Khả năng **giải thích** là thứ ít được nhắc tới nhất và có giá trị thực tế cao nhất: màn hình *"vì
sao đơn của tôi bị từ chối"* sinh ra tự động, luôn khớp với luật thật, không bao giờ lỗi thời. Với
`Predicate`, muốn có màn hình đó bạn phải viết lại toàn bộ luật **lần thứ tư** dưới dạng chuỗi `if`.

Khả năng **dịch sang truy vấn** giải quyết con bug tốn tiền nhất: 2 triệu bản ghi thì không lọc
trong bộ nhớ được, nên luật được gõ lại bằng SQL — và bản SQL lệch. Cùng một object trả lời được cả
hai câu: *"khách này có hợp lệ không"* và *"những khách nào hợp lệ"*.

> Ranh giới an toàn: chỉ sinh SQL từ **cấu trúc của chính specification**, ngưỡng là số/hằng do miền
> quyết định. Nếu cần nhét dữ liệu người dùng vào, trả về câu có **tham số** (`tuoi >= ?`) kèm danh
> sách giá trị — đừng nối chuỗi.

### Ghép luật mới mà không sửa luật cũ

```java
DacTa<KhachHang> uuDaiDacBiet = duocVayTinChap.hoac(khachVip.va(new BiKhoa().khong()));
```

Luật mới ra đời mà không sửa một dòng nào của ba luật cơ sở — mở-đóng
([bài 61](../61-open-closed/)) áp cho luật nghiệp vụ.

### Khi nào KHÔNG cần specification

Mẫu thiết kế này dễ bị lạm dụng. Ba câu hỏi, cần **có ít nhất hai**:

- (a) Luật này có dùng ở **nhiều hơn một** chỗ không?
- (b) Nó có cần **ghép** với luật khác không?
- (c) Có ai cần biết **vì sao trượt**, hoặc cần **dịch sang truy vấn** không?

Chỉ một chỗ dùng, không ghép, không giải thích → `if` là đúng, và ba lớp `Va`/`Hoac`/`Khong` chỉ là
chi phí. Và nếu luật thuộc về đúng **một** entity, nó nên là phương thức của entity đó
([bài 86](../86-domain-service/) câu hỏi b): `don.quaHan(homNay)` tốt hơn
`new DonQuaHan(homNay).thoaMan(don)`.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| `thoaMan` trên cây n mệnh đề | O(n) — và **ngắn mạch**, thường ít hơn | O(chiều sâu cây) ngăn xếp |
| `lyDoTruot` | O(n) — **không** ngắn mạch, phải duyệt hết để gom đủ lý do | O(số mệnh đề trượt) |
| `dieuKienSql` | O(n) | O(độ dài câu) |
| Lọc danh sách m phần tử | O(m·n) trong bộ nhớ · **O(1) lần gọi CSDL** nếu dịch sang SQL | — |

Dòng cuối là lý do khả năng dịch sang truy vấn đáng giá: lọc 2 triệu bản ghi trong bộ nhớ là
O(m·n) *cộng* chi phí tải 2 triệu bản ghi; đẩy xuống CSDL biến nó thành một câu truy vấn có chỉ mục.

## 4. Lời giải

- [`SpecificationDemo.java`](SpecificationDemo.java) — `interface` với `default` method cho
  `va`/`hoac`/`khong`, các mệnh đề là `record`. So sánh trực tiếp với `Predicate<KhachHang>` để cho
  thấy đúng hai thứ `Predicate` thiếu.
- [`SpecificationDemo.cpp`](SpecificationDemo.cpp) — bản đọc gọn nhất trong ba bản, nhờ nạp chồng
  toán tử: `duTuoi(18) && duDiem(100) && !biKhoa()`. Phần 5 đo cái bẫy đi kèm: **`&&` nạp chồng mất
  tính ngắn mạch** nếu toán tử nhận `bool`. Cách thoát: toán tử phải **dựng cây**, không **tính**.
- [`specification_demo.py`](specification_demo.py) — cái bẫy nguy hiểm nhất trong cả ba bản:
  `and`/`or`/`not` **không nạp chồng được** (chúng đi qua `__bool__`). `DuTuoi(18) and DuDiem(100)`
  chạy êm và trả về **đúng một nhánh** — điều kiện tuổi biến mất, và hệ thống cho một người 17 tuổi
  vay tiền.

**Khác biệt giữa ba ngôn ngữ:**

| | Cú pháp ghép | Bẫy riêng | Cách chặn bẫy |
|---|---|---|---|
| Java | `a.va(b).khong()` | — (dài dòng nhưng không mơ hồ) | — |
| C++ | `a && b && !c` | `&&` nạp chồng **mất ngắn mạch** nếu nhận `bool` | toán tử dựng cây, `thoaMan` mới dùng `&&` dựng sẵn |
| Python | `a & b & ~c` | `and`/`or`/`not` **im lặng** trả về sai thứ | `def __bool__: raise TypeError(...)` |

Sáu dòng `__bool__` đó nên có trong mọi lớp specification thật ở Python. Chúng biến một bug im lặng
thành lỗi lúc chạy — với luật cho vay tiền thì đó là đổi rất đáng.

## 5. Thực tế đi làm

**Cạm bẫy #1 — luật sống ở hai nơi: code và SQL.** Đây là dạng phổ biến nhất, và nó không bao giờ
được phát hiện bởi test, vì hai nơi có hai bộ test khác nhau. Nếu chưa dịch được specification sang
truy vấn, ít nhất hãy có **một** test đối chiếu: chạy cả hai trên cùng bộ dữ liệu và so kết quả.

**Cạm bẫy #2 — dùng lambda/`Predicate` rồi phải viết lại luật để báo lỗi.** Dấu hiệu: có một hàm
`kiemTra()` trả `boolean` và ngay dưới nó là một hàm `thongBaoLoi()` lặp lại đúng những điều kiện
đó bằng chuỗi `if`. Hai hàm này sẽ lệch nhau, chỉ là vấn đề thời gian.

**Cạm bẫy #3 — specification cho mọi thứ.** Ba lớp `Va`/`Hoac`/`Khong` cộng một lớp cho mỗi mệnh đề
là chi phí thật. Với một luật dùng ở một chỗ, `if` ngắn hơn, dễ đọc hơn, và đúng hơn.

**Cạm bẫy #4 — sinh SQL bằng nối chuỗi.** `dieuKienSql()` trả chuỗi là tiện cho việc dạy, nhưng
trong hệ thật hãy trả về **câu có tham số + danh sách giá trị**. Một specification nhận chuỗi từ
người dùng rồi nối thẳng vào là một lỗ SQL injection có kiến trúc đàng hoàng.

**Cạm bẫy #5 — specification biết về CSDL.** Nếu mệnh đề của bạn gọi repository để lấy dữ liệu
trước khi trả lời, nó không còn là specification nữa — nó là một truy vấn đội lốt, và nó không dùng
được cho việc kiểm tra một object đang nằm trong bộ nhớ. Dữ liệu cần thiết phải nằm sẵn trên object
được kiểm.

**Cạm bẫy #6 — `lyDoTruot` ngắn mạch theo `thoaMan`.** Nếu gom lý do bằng cách dừng ở mệnh đề sai
đầu tiên, người dùng phải sửa hồ sơ ba lần liên tiếp, mỗi lần bị báo một lỗi mới. `thoaMan` ngắn
mạch (nhanh); `lyDoTruot` **không** ngắn mạch (đầy đủ) — hai hàm, hai mục tiêu khác nhau.

**Biến thể phỏng vấn thường hỏi:**
- *"Specification khác `Predicate`/lambda ở đâu?"* — Có tên, giải thích được, dịch được sang truy
  vấn. Ba thứ đó không phải trang trí: mỗi thứ chặn một bug cụ thể.
- *"Vì sao không để luật ngay trong entity?"* — Nếu luật thuộc về một entity và không cần ghép thì
  **nên** để trong entity. Specification dành cho luật *về* entity mà thay đổi độc lập với entity —
  và nhất là luật thay đổi theo khách hàng / quốc gia ([bài 88](../88-policy-object/)).
- *"Làm sao đảm bảo luật trong bộ nhớ và luật trong CSDL giống nhau?"* — Sinh câu truy vấn từ chính
  specification. Nếu không làm được (ORM hạn chế), thì phải có test đối chiếu hai đường.
- *"`lyDoTruot` của `hoặc` nên trả về gì?"* — Cả cụm, không phải từng nhánh. "Bạn cần *hoặc* đủ 500
  điểm *hoặc* là khách VIP" là một thông báo dùng được; liệt kê hai lỗi rời rạc thì không.
- *"Ở Python vì sao phải dùng `&` chứ không dùng `and`?"* — Vì `and` không nạp chồng được và nó im
  lặng trả về sai thứ. Đây là câu hỏi lọc rất hiệu quả: người đã từng bị dính sẽ nhớ ngay.

## 6. Self-check

```bash
cd 04-competitive/87-specification
javac SpecificationDemo.java && java SpecificationDemo        # in "OK"
g++ -std=c++17 -o sol SpecificationDemo.cpp && ./sol          # in "OK"
python specification_demo.py                                  # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
