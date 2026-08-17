# 94 — Anti-Corruption Layer: mô hình xấu của đối tác dừng lại ở biên

Bài 93 nói hai ngữ cảnh phải có hai mô hình. Bài này là trường hợp **khó nhất** của điều đó — khi
ngữ cảnh bên kia **không phải của bạn**, mô hình của họ xấu, và bạn **không có quyền thương lượng**.
Lớp chống hư hỏng là biên giới nơi mọi thứ xấu dừng lại: sau nó, miền của bạn không biết đối tác
tồn tại.

## 1. Đề bài

API đối tác trả về bản ghi giao hàng, mọi thứ là chuỗi, mọi trường có thể thiếu:

```json
{ "cust_nm": "  NGUYEN VAN A  ", "st": "3", "amt_cent": "1050",
  "dt": "20260817", "flag_x": "Y" }
```

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Mô hình đối tác rò vào miền → mỗi nơi hiểu một kiểu | **1 · 2 · 2** cho cùng câu hỏi "đã giao chưa" |
| 2 | Sau ACL, miền chỉ thấy enum / `Tien` / `bool` | `trangThai == DA_GIAO`, `Tien(1050)` |
| 3 | Thiếu trường → nổ **tại biên**, nói rõ tên trường **của đối tác** | thông báo chứa `amt_cent` |
| 4 | Mã lạ (`"9"`) bị **từ chối và đếm**, không rơi mặc định | `soLanTuChoi == 1` |

**Ràng buộc:** không lớp nào của miền được chạm tới kiểu/tên trường của đối tác — và điều đó phải
**kiểm được bằng máy**.

**Input/Output mẫu:**
```
dich({ "st": "3", "amt_cent": "1050", ... })
  -> ChuyenGiaoHang("Nguyen Van A", DA_GIAO, Tien(1050), 20260817, true)

dich({ "st": "3", "amt_cent": null, ... })  -> LoiDoiTac: thiếu trường amt_cent
dich({ "st": "9", ... })                    -> LoiDoiTac: mã trạng thái lạ: st=9
```

## 2. Ý tưởng

### Con bug: ngôn ngữ của đối tác tràn vào

```java
if ("3".equals(dto.st))                       // màn hình  -> 1
if ("3".equals(dto.st) || "4".equals(dto.st)) // báo cáo   -> 2
if (Integer.parseInt(dto.st) >= 3)            // kế toán   -> 2
```

Ba nơi, ba con số, không nơi nào sai cú pháp và hai nơi sai **nghĩa**. Đây là
[bài 81](../81-ubiquitous-language/) phần 1 quay lại, nhưng nguyên nhân khác: lần này ngôn ngữ xấu
**không phải do ta đặt tên tệ** — nó là ngôn ngữ của đối tác, và nó đã tràn vào.

Cái giá thật đến khi đối tác ra v2 và mã `"3"` tách thành `"3"` / `"3R"`:

| Ngôn ngữ | Chuyện gì xảy ra với `"3R"` |
|---|---|
| Java | `Integer.parseInt("3R")` → `NumberFormatException` — **sập, nhưng thấy được** |
| C++ | `std::atoi("3R")` → **trả về `3`**, không lỗi — **sai lặng lẽ** |
| Python | `int("3R")` → `ValueError` — sập, thấy được |

Dòng C++ là kiểu hỏng tệ nhất: báo cáo vẫn "chạy đúng" và vẫn ra số sai.

### ACL làm đúng ba việc

1. **Kiểm tính hợp lệ** của dữ liệu đầu vào — thiếu trường, sai kiểu, mã lạ.
2. **Dịch** mô hình họ → mô hình ta: kiểu, đơn vị, khái niệm, *và quyền sở hữu bộ nhớ*.
3. **Từ chối** cái không dịch được, và **đếm**.

Nó **không** đặt luật nghiệp vụ. Nếu ACL bắt đầu biết *"đơn trên 10 triệu phải duyệt"*, thì luật
nghiệp vụ vừa chuyển ra ngoài miền — và sẽ có bản sao thứ hai của nó ở trong miền
([bài 87](../87-specification/)).

### Dịch KHÁI NIỆM, không chỉ đổi tên field

Đây là phần khó nhất và hay bị bỏ qua. Đối tác có mã `"4"` = *"trả về người gửi"*. Nếu miền của ta
không có khái niệm đó thì có **đúng hai** lựa chọn hợp lệ:

- (a) **thêm** khái niệm vào miền (`DA_TRA_LAI`) — sau khi hỏi nghiệp vụ;
- (b) **từ chối** bản ghi đó ở biên, có log, có cảnh báo.

Lựa chọn thứ ba — cho rơi vào nhánh mặc định — là cách dữ liệu sai đi vào hệ thống mà không ai biết.

### Fail fast tại biên, với thông báo nói được tên đối tác

```
LoiDoiTac: dữ liệu đối tác không hợp lệ: thiếu trường amt_cent
```

So với cách không có ACL: một `NullPointerException`/`TypeError` ở đâu đó sâu trong nghiệp vụ, ba
tầng gọi sau, và người trực đêm phải lần ngược để đoán ra rằng lỗi đến từ **dữ liệu đối tác** chứ
không phải từ code của mình.

### ACL đi CẢ HAI CHIỀU

Dễ quên nhất: khi ta **gửi** dữ liệu sang đối tác, cũng phải dịch từ mô hình của ta sang của họ —
chứ không phải serialize thẳng object miền ra JSON và hy vọng khớp.

### Đặt ở đâu, mấy cái

Một ACL cho **mỗi hệ ngoài**, thuộc về **bên gọi**. Ba hệ quả:

- Hai đội cùng gọi một đối tác có thể có **hai** ACL khác nhau — và đó là **đúng**, vì hai đội cần
  hai mô hình khác nhau ([bài 93](../93-bounded-context/)).
- ACL nằm ở tầng hạ tầng, cài đặt một **cổng** do miền định nghĩa ([bài 98](../98-hexagonal/)).
- Khi đối tác chết, ACL là nơi duy nhất cần một bản giả để test ([bài 68](../68-in-memory-fake/)).

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| `dich()` một bản ghi | O(số trường) — một lượt kiểm + tra bảng O(1) | O(kích thước bản ghi) |
| Sao chép sang kiểu tự sở hữu (C++) | O(độ dài chuỗi) — **một lần, tại biên** | O(độ dài chuỗi) |
| Bài test kiến trúc | O(số lớp miền × số thành viên) — chỉ lúc test | O(1) |
| Đối tác ra v2 | **1 chỗ sửa** (có ACL) vs ~12 chỗ (không ACL) | — |

Chi phí lúc chạy của ACL là **một lượt duyệt trường**, và nó đắt hơn "dùng thẳng DTO" đúng một
phép sao chép. Dòng cuối là con số thật sự quyết định: chi phí **bảo trì** khi phía bên kia đổi —
thứ chắc chắn sẽ xảy ra và không do bạn kiểm soát.

## 4. Lời giải

- [`AclDemo.java`](AclDemo.java) — bảng dịch mã trạng thái là chỗ **duy nhất** biết `"3"` nghĩa là
  gì. Phần 6 là một **bài test kiến trúc** bằng reflection: quét field/tham số/kiểu trả về của mọi
  lớp miền, fail nếu kiểu của đối tác lọt vào.
- [`AclDemo.cpp`](AclDemo.cpp) — ACL ở C++ còn một nhiệm vụ hai ngôn ngữ kia không có: **biên là
  nơi quyền sở hữu bộ nhớ đổi chủ**. SDK trả `const char*` trỏ vào bộ đệm của họ; file mô phỏng
  việc họ ghi đè bộ đệm đó và chứng minh bản đã dịch **vẫn nguyên vẹn** vì nó đã sao sang
  `std::string`. Giữ nguyên con trỏ là một quả bom hẹn giờ *không xác định*.
- [`acl_demo.py`](acl_demo.py) — ở Python dữ liệu đối tác thậm chí **không có kiểu**: nó là `dict`
  trần. Phần 3 đo cái bẫy đặc trưng: `d.get("stt")` gõ thừa một chữ trả `None` — **một nhánh nghiệp
  vụ vừa tắt vĩnh viễn**, không lỗi, không cảnh báo. Khác biệt duy nhất giữa an toàn và không an
  toàn là dấu ngoặc vuông: `d[k]` ném `KeyError`.

**Khác biệt giữa ba ngôn ngữ:**

| | Dữ liệu đối tác có kiểu? | Bẫy riêng | Nhiệm vụ thêm của ACL |
|---|---|---|---|
| Java | ✅ DTO có kiểu | — | — |
| C++ | ✅ struct | `atoi` **nuốt** ký tự lạ, trả số sai | **sao chép** sang kiểu tự sở hữu |
| Python | ❌ `dict` trần | `.get()` **nuốt** khoá gõ sai, trả `None` | ép kiểu cho từng trường |

Quy tắc Python rút ra: `.get()` chỉ dùng khi *"không có"* là một trường hợp **hợp lệ** và bạn đã
nghĩ tới nó. Với dữ liệu bắt buộc từ hệ ngoài thì không bao giờ.

## 5. Thực tế đi làm

**Cạm bẫy #1 — dùng thẳng DTO của đối tác làm mô hình miền.** Tiết kiệm được một lớp hôm nay, và
trả giá ở mọi lần đối tác đổi. Dấu hiệu: tên trường trong nghiệp vụ của bạn là `cust_nm`, `st`,
`amt_cent`.

**Cạm bẫy #2 — ACL chỉ đổi tên field.** Đổi `cust_nm` → `tenKhach` mà vẫn giữ chuỗi `"3"` là chưa
dịch gì cả. ACL thật đổi **kiểu** (chuỗi → enum), **đơn vị** (xu chuỗi → `Tien`), và **khái niệm**.

**Cạm bẫy #3 — nhánh mặc định nuốt mã lạ.** `default: return DANG_GIAO` là cách một mã trạng thái
mới của đối tác đi vào hệ thống dưới một cái tên sai. Mã không dịch được phải **từ chối và đếm** —
và số đếm đó phải có cảnh báo.

**Cạm bẫy #4 — ACL biết luật nghiệp vụ.** Khi đã dịch rồi thì rất tiện tay tính luôn phí. Đừng: ACL
là *hạ tầng*, luật là *miền*, và trộn chúng lại tạo ra bản sao thứ hai của luật.

**Cạm bẫy #5 — không có ACL cho chiều ra.** Serialize thẳng object miền ra JSON gửi cho đối tác là
để mô hình **của bạn** rò sang **họ** — và từ đó bạn không đổi được tên field nội bộ nữa mà không
phá tích hợp.

**Cạm bẫy #6 — một ACL dùng chung cho nhiều đối tác.** `PartnerAdapter` với `if (partner == "A")`
là mô hình chung ở bài 93 lặp lại ở tầng hạ tầng. Một ACL cho một đối tác.

**Cạm bẫy #7 — quên rằng ACL là nơi duy nhất cần fake.** Nếu test tích hợp của bạn phải gọi API
thật, thì ACL chưa nằm sau một cổng của miền — và bộ test sẽ chậm, giòn, và phụ thuộc mạng.

**Biến thể phỏng vấn thường hỏi:**
- *"Anti-corruption layer là gì?"* — Một lớp dịch ở biên giữa miền của bạn và một hệ ngoài có mô
  hình khác/xấu, để mô hình của họ không lây vào. Nói thêm "và nó dịch **khái niệm**, không chỉ tên
  field" là điểm phân biệt.
- *"Khi nào KHÔNG cần ACL?"* — Khi mô hình của hệ ngoài đã trùng với mô hình miền của bạn, hoặc khi
  bạn có quyền yêu cầu họ đổi (quan hệ *khách/nhà cung cấp* ở bài 93). Với API bên thứ ba thì gần
  như luôn cần.
- *"ACL đặt ở tầng nào?"* — Hạ tầng, cài đặt một cổng do miền định nghĩa. Miền định nghĩa *cần gì*,
  hạ tầng lo *lấy từ đâu* ([bài 98](../98-hexagonal/)).
- *"Làm sao đảm bảo ACL không bị vòng qua?"* — Bài test kiến trúc: quét mô hình miền, fail nếu có
  kiểu/tên trường của đối tác. Trả lời "code review" cho thấy chưa gặp lúc nó bị vòng qua.
- *"Đối tác trả về mã trạng thái mới thì làm gì?"* — Từ chối, đếm, cảnh báo — rồi **hỏi nghiệp vụ**
  xem khái niệm đó có tương ứng gì trong miền không. Không tự quyết bằng một nhánh `default`.

## 6. Self-check

```bash
cd 04-competitive/94-anti-corruption-layer
javac AclDemo.java && java AclDemo        # in "OK"
g++ -std=c++17 -o sol AclDemo.cpp && ./sol # in "OK"
python acl_demo.py                         # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
