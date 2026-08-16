# 82 — Entity vs Value Object: câu hỏi không có câu trả lời chung

Bài 53 dạy "so theo giá trị hay so theo định danh". Ở mức miền, câu hỏi khó hơn nhiều: **cùng một
khái niệm có thể là value object trong ngữ cảnh này và entity trong ngữ cảnh khác**. Chọn sai
không gây lỗi biên dịch — nó gây mất dữ liệu âm thầm.

## 1. Đề bài

Mô hình hoá bốn khái niệm và **quyết định loại cho từng cái**, rồi chứng minh quyết định đó bằng
code chạy được.

| Khái niệm | Loại | Vì sao |
|---|---|---|
| `Tien(soTien, tienTe)` | value object | Hai tờ 50.000đ thay cho nhau được |
| `DiaChi(duong, phuong, tinh)` | value object *(trong đơn hàng)* | Hai địa chỉ giống hệt thì giao tới đâu cũng thế |
| `DiemGiao(ma, diaChi, nguoiPhuTrach)` | **entity** *(trong vận chuyển)* | Hai kho cùng toà nhà vẫn là hai kho |
| `KhachHang(ma, ten)` | **entity** | Đổi tên vẫn là người đó |

**Ràng buộc:**
- Phải dựng được **hai phép thử chạy được**, không phải hai câu định nghĩa.
- Entity phải có định danh **ngay lúc `new`**, không đợi CSDL cấp.
- Value object phải **bất biến** và mang luật của chính nó.
- Phải cho nổ ít nhất ba con bug thật, mỗi cái đo được bằng một `assert`.

**Input/Output mẫu:**
```
Phép thử A — đổi HẾT thuộc tính:
  DiemGiao("DG-01", "12 Lê Lợi", "anh Nam")
  -> đổi địa chỉ, đổi người phụ trách
  -> vẫn == bản gốc          => ENTITY

Phép thử B — hai cái giống hệt:
  Tien(50000,"VND") == Tien(50000,"VND")   => VALUE OBJECT

Bug: hai khách hàng chưa lưu (id = 0)
  HashSet{khachA, khachB}.size() == 1      <- mất một khách hàng
```

## 2. Ý tưởng

### Hai phép thử, không phải hai định nghĩa

Định nghĩa sách vở ("entity có định danh") không dùng được lúc thiết kế, vì câu hỏi thật là *cái
này CÓ CẦN định danh không*. Hai phép thử sau thì trả lời được ngay:

| Phép thử | Cách làm | Kết luận |
|---|---|---|
| **A** | Đổi **hết** thuộc tính. Còn là cùng một thứ không? | Có → **entity** |
| **B** | Hai cái mọi thuộc tính bằng nhau. Thay cho nhau được không? | Có → **value object** |

Cái kho đổi tên đường, đổi người phụ trách — vẫn là cái kho đó. Nghiệp vụ quan tâm tới **vật**,
không quan tâm tới mô tả của vật. Còn hai tờ 50.000đ thì không ai hỏi "đây có phải *đúng* tờ hôm
qua không".

### Cùng một khái niệm, hai vai trò

Đây là điểm nâng cao so với bài 53. "Địa chỉ" trong **đơn hàng** là value object — hai địa chỉ
giống nhau là một, đổi địa chỉ nghĩa là thay nguyên cái mới. "Điểm giao" trong hệ **vận chuyển**
là entity — hai kho khác nhau vẫn có thể ở *cùng một địa chỉ* (chung toà nhà), và chúng không
phải một.

Không có câu trả lời chung. Câu hỏi quyết định là: **nghiệp vụ có cần theo dõi cái cụ thể này qua
thời gian không?**

### Định danh phải có TRƯỚC khi chạm CSDL

Đây là chỗ sai phổ biến nhất, và nó không giống một lỗi:

```java
class KhachHang {
    long id = 0;   // 0 = "chưa lưu"
    equals/hashCode theo id
}
```

Hai khách hàng vừa `new`, cả hai `id = 0`, `equals` trả về `true` → `HashSet` giữ **một**. Mất một
khách hàng, không ngoại lệ, không log. Và bug này chỉ nổ khi xử lý **theo lô** — nhập file CSV,
tạo hàng loạt — nghĩa là nó qua được mọi test thủ công.

Tệ hơn: khi lưu xong, `id` đổi từ `0` thành `101`. Định danh của object vừa thay đổi giữa chừng —
nếu nó đang nằm trong một `HashSet` nào đó thì nó vừa trở nên không tìm lại được (bài 75).

Cách sửa: sinh định danh **trong miền** (`UUID`, hoặc mã nghiệp vụ như `DH-2026-0001`). Hệ quả
thực tế lớn hơn vẻ ngoài: test không cần CSDL, và entity gửi được qua hàng đợi *trước khi* lưu.

### Value object phải bất biến, vì nó được chia sẻ

```java
DiaChiSai chung = new DiaChiSai("12 Lê Lợi");
don1.diaChiGiao = chung;
don2.diaChiGiao = chung;          // vô tình dùng chung
don1.diaChiGiao.setDuong("45 Nguyễn Huệ");
// -> đơn 2 vừa được giao sai địa chỉ
```

Một setter trên value object là đủ để tạo bug aliasing. Bất biến làm cho việc chia sẻ trở nên an
toàn tuyệt đối — và còn tiết kiệm bộ nhớ.

### Entity so sánh CHỈ theo định danh

Nếu `equals` của entity có thêm một field khả biến, thì sửa thuộc tính = mất phần tử trong
`HashSet` (bài 75). Đây là quy tắc không có ngoại lệ: **`equals` của entity chứa đúng một thứ, là
định danh.**

Chiều ngược lại cũng tuyệt đối: `equals` của value object chứa **toàn bộ** giá trị.

### Value object mang LUẬT, không chỉ mang dữ liệu

```java
Tien cong(Tien khac) {
    if (!tienTe.equals(khac.tienTe))
        throw new IllegalArgumentException("không cộng được hai loại tiền tệ");
    return new Tien(soTien + khac.soTien, tienTe);
}
```

Đây là lợi ích lớn nhất mà bài 53 chưa nói tới: value object là **chỗ duy nhất** để đặt luật, nên
luật không thể bị quên ở một nhánh code nào đó. [Bài 90](../90-money-currency/) đi sâu vào riêng
`Money`.

### Quy tắc thực dụng

**Mặc định là value object.** Chỉ nâng lên entity khi có một câu hỏi nghiệp vụ thật sự cần theo
dõi cái cụ thể đó qua thời gian. Entity đắt hơn nhiều — nó cần định danh, cần kho lưu trữ, cần
vòng đời, và không chia sẻ tự do được.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| `equals`/`hashCode` của entity | O(1) — chỉ băm định danh | O(1) |
| `equals`/`hashCode` của value object | O(số field) | O(1) |
| Tạo bản mới qua `withX()` / `replace()` | O(số field) | O(1) thêm một object |
| Sao chép phòng vệ collection trong VO | O(n) — **một lần lúc tạo** | O(n) |

Đáng chú ý: value object bất biến **không** làm chương trình chậm đi như trực giác gợi ý, vì nó
được chia sẻ tự do thay vì phải sao chép phòng vệ ở mỗi lần trả ra.

## 4. Lời giải

- [`EntityValueDemo.java`](EntityValueDemo.java) — `record` cho value object (equals/hashCode/bất
  biến trong một dòng), lớp thường cho entity. Phần 7 cho nổ **bất biến nông**: `record` chứa
  `List` vẫn sửa được từ bên ngoài, và cách chữa bằng `List.copyOf` trong constructor gọn.
- [`EntityValueDemo.cpp`](EntityValueDemo.cpp) — C++ **đảo ngược bài toán**: ngữ nghĩa giá trị là
  mặc định nên value object gần như cho không, còn entity mới nguy hiểm. File cho thấy
  `auto sao = *kho;` sinh ra hai object **cùng định danh, khác trạng thái** — mâu thuẫn nghiệp vụ
  không tồn tại ngoài đời — rồi dập nó bằng `DiemGiao(const DiemGiao&) = delete`.
- [`entity_value_demo.py`](entity_value_demo.py) — cái bẫy lớn nhất của Python: `@dataclass` **mặc
  định** sinh `__eq__` theo mọi field và đặt `__hash__ = None`. Dán nó lên entity là nhận một lớp
  so sánh theo trạng thái khả biến và không bỏ vào `set` được. File cũng chỉ ra
  `dataclasses.replace()`: đúng tuyệt đối với VO, là máy sinh bug khi gọi trên entity.

**Khác biệt giữa ba ngôn ngữ:**

| | Value object cho sẵn | Chặn nhân bản entity | Cái bẫy riêng |
|---|---|---|---|
| Java | `record` — equals/hashCode/final | ❌ chỉ là quy ước | `record` chứa `List` = bất biến nông |
| C++ | mặc định của ngôn ngữ (ngữ nghĩa giá trị) | ✅ `= delete` — **lỗi biên dịch** | `const` trên field xoá luôn `operator=` |
| Python | `@dataclass(frozen=True)` | ❌ phải tự chặn `__copy__` | `@dataclass` trần trên entity phá cả `eq` lẫn `hash` |

Ba dòng khai báo Python, ba ý nghĩa hoàn toàn khác nhau — nhớ bảng này là đủ:

```python
@dataclass                                       # so mọi field, KHÔNG hash được  -> túi dữ liệu
@dataclass(frozen=True)                          # so mọi field, hash được        -> VALUE OBJECT
@dataclass(eq=False) + __eq__/__hash__ theo id   #                                -> ENTITY
```

## 5. Thực tế đi làm

**Cạm bẫy #1 — để CSDL cấp định danh.** `@GeneratedValue` / `AUTO_INCREMENT` / `id = 0` là mặc
định của mọi ORM, và nó nghĩa là entity **không có định danh** cho tới lúc lưu. Hệ quả: không bỏ
vào `Set` được, không so sánh được, không gửi qua hàng đợi được, và test nào cũng phải có CSDL.
Sinh `UUID` hoặc mã nghiệp vụ trong constructor giải quyết cả bốn.

**Cạm bẫy #2 — dùng `@dataclass` / `record` / `data class` cho entity.** Cả ba đều sinh `equals`
theo **mọi field**. Với entity, đó là sai định nghĩa: hai bản ghi của cùng một khách hàng, một bản
mới đổi số điện thoại, sẽ được coi là hai khách hàng khác nhau. Entity không bao giờ là một
data class.

**Cạm bẫy #3 — value object có setter.** Một setter là đủ để biến việc chia sẻ object thành bug
aliasing, và làm nó không dùng được làm key trong `Map`. Nếu thấy mình muốn viết `setX()` trên một
value object, thứ thật sự cần là `withX()` trả về bản mới.

**Cạm bẫy #4 — bất biến nông.** `record`, `frozen=True`, `final` — cả ba chỉ khoá **tham chiếu**,
không khoá thứ nó trỏ tới. Value object chứa `List`/`Map` phải sao chép phòng vệ (`List.copyOf`)
hoặc chuyển sang kiểu bất biến (`tuple`, `frozenset`). Dấu hiệu nhanh: nếu `hash()` ném lỗi thì
object đó không thật sự bất biến.

**Cạm bẫy #5 — nhân bản entity.** `clone()`, `copy.deepcopy()`, `dataclasses.replace()`,
`auto x = *entity` — bốn cách gõ khác nhau cho cùng một tai nạn: hai object cùng định danh, khác
trạng thái, và không ai biết bản nào đúng. C++ chặn được ở mức trình biên dịch; Java và Python
phải chặn bằng luật đội (hoặc `__copy__` ném lỗi).

**Cạm bẫy #6 — nâng mọi thứ lên entity "cho chắc".** Entity kéo theo kho lưu trữ, vòng đời, và
ràng buộc không được chia sẻ. Một `DiaChi` bị nâng lên entity nghĩa là mỗi lần đổi địa chỉ phải
đi hỏi "còn ai đang dùng địa chỉ này không" — câu hỏi vô nghĩa với nghiệp vụ. Mặc định là value
object, và bắt entity phải chứng minh lý do tồn tại.

**Biến thể phỏng vấn thường hỏi:**
- *"Địa chỉ là entity hay value object?"* — Câu trả lời đúng là **"trong ngữ cảnh nào?"**. Trong
  đơn hàng: value object. Trong hệ quản lý điểm giao: entity. Người hỏi đang kiểm tra xem bạn có
  biết câu hỏi này phụ thuộc ngữ cảnh hay không.
- *"Entity của bạn có nên `equals` theo id không, khi id chưa được cấp?"* — Đúng câu hỏi. Nếu id
  chưa được cấp thì entity chưa hoàn chỉnh — và cách sửa không phải là thêm nhánh `if (id == 0)`,
  mà là cấp id ngay lúc tạo.
- *"Vì sao value object phải bất biến?"* — Ba lý do, theo thứ tự quan trọng: (1) nó được chia sẻ,
  bất biến làm việc chia sẻ an toàn; (2) nó hay làm key trong `Map`; (3) nó là chỗ đặt luật, và
  luật chỉ chắc chắn khi trạng thái không đổi sau khi validate.
- *"Value object có được chứa entity không?"* — Không. Value object bất biến, entity thì đổi trạng
  thái — chứa entity nghĩa là value object đổi theo, và mất luôn tính chất "hai cái bằng nhau thì
  mãi bằng nhau". Chiều ngược lại (entity chứa value object) thì bình thường và rất nên.
- *"Team em toàn dùng ORM, entity nào cũng là `@Entity` với id tự tăng. Sửa thế nào?"* — Không
  cần đập đi. Tách **mô hình miền** (có định danh riêng, không biết ORM) khỏi **bản ghi lưu trữ**
  (có id tự tăng), và ánh xạ giữa hai bên — chính là [bài 78 · DTO Mapping](../78-dto-mapping/) áp
  cho tầng lưu trữ, và [bài 85](../85-repository-unit-of-work/) nói cách làm cho gọn.

## 6. Self-check

```bash
cd 04-competitive/82-entity-value-object
javac EntityValueDemo.java && java EntityValueDemo        # in "OK"
g++ -std=c++17 -o sol EntityValueDemo.cpp && ./sol        # in "OK"
python entity_value_demo.py                               # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
