# 93 — Bounded Context: cùng chữ "hoàn tất", hai đội hiểu hai kiểu, và cả hai đều đúng

Đây là bài học kiến trúc đắt nhất trong tầng này, vì nó **đi ngược trực giác**. Trực giác nói
*"đừng lặp lại chính mình — một khách hàng thì phải có MỘT lớp `KhachHang`"*. Thực tế nói ngược
lại: hai đội nói hai ngôn ngữ khác nhau về cùng một con người, và ép họ dùng chung một lớp **không
loại bỏ** sự khác nhau đó — nó chỉ giấu sự khác nhau vào những field mà nửa số đội phải bỏ trống.

## 1. Đề bài

Ba đội — **bán hàng**, **kế toán**, **hỗ trợ** — đều nói về "khách hàng". Chứng minh bằng code:

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Mô hình chung chặn đội bán hàng tạo khách tiềm năng | ngoại lệ "mã số thuế là bắt buộc" |
| 2 | Mô hình riêng: 4 field vs **12** | và 9/12 field của mô hình chung là tuỳ chọn |
| 3 | "Hoàn tất" mang **hai nghĩa** | `daHoanTat()` cho hai câu trả lời ngược nhau, cả hai đúng |
| 4 | Chi phí thay đổi: **3 đội vs 1** | thêm một field cho kế toán |

**Ràng buộc:** hai ngữ cảnh nối nhau **bằng mã**, không bằng object; việc dịch nằm ở **biên**.

**Input/Output mẫu:**
```
KhachHangChung("KH-01", "Chị Hoa ở hội chợ")  -> ValueError: mã số thuế là bắt buộc

BanHang.KhachHang("KH-01", "Chị Hoa", DA_CHOT).daHoanTat()          -> true   (đã chốt)
KeToan.BenNhanHoaDon("KH-01", ..., daThuDuTien=false).daHoanTat()   -> false  (chưa thu)
                                                      ^ cùng một khách, cùng một chữ
```

## 2. Ý tưởng

### Con bug #1: một ràng buộc đúng ở nơi này, sai ở nơi kia

9h sáng ở hội chợ. Nhân viên bán hàng gặp một người, có tên và số điện thoại, muốn ghi lại ngay.
Người đó chưa có công ty, chưa có mã số thuế — và mô hình chung không cho tạo.

Luật *"mã số thuế là bắt buộc"* **hoàn toàn đúng** — với kế toán. Nó chỉ sai khi bị áp lên một ngữ
cảnh mà khái niệm "khách hàng" còn chưa có nghĩa đó.

Cách vá mà mọi dự án đều làm — đặt `maSoThue` thành tuỳ chọn — **tệ hơn**: kế toán mất bảo đảm
*"mọi bên nhận hoá đơn đều có mã số thuế"* và phải tự kiểm ở mọi chỗ dùng. **Một ràng buộc thật vừa
biến thành lời khuyên.**

Đây là cơ chế khiến mô hình chung luôn phình ra và luôn yếu đi: mỗi lần một ngữ cảnh mới cần dùng
nó, một ràng buộc lại bị gỡ.

### Con bug #2: cùng một chữ, hai nghĩa

| Đội | "Hoàn tất" nghĩa là | Với đơn của chị Hoa (đã chốt, công nợ 30 ngày) |
|---|---|---|
| Bán hàng | đã chốt đơn | **có** |
| Kế toán | đã thu đủ tiền | **chưa** |

Với mô hình chung, `hoanTat` là **một** boolean. Ai gán nó? Đội nào gán thì đội kia đọc sai. Không
có cách vá nào ngoài việc tách ra thành hai khái niệm — và tách ra thì đã là hai bounded context
rồi.

Đây là con bug ở [bài 81](../81-ubiquitous-language/) phần 1, nhưng ở **quy mô tổ chức**: ở đó là
hai lập trình viên hiểu khác nhau, ở đây là hai *phòng ban* hiểu khác nhau. Và họ đều đúng.

### Nối bằng mã, không bằng object

```
BanHang.KhachHang { maKhach, ten, nguonKhach, giaiDoan }
KeToan.BenNhanHoaDon { maKhach, tenPhapNhan, maSoThue, ... }
                       ^^^^^^^ chỉ một thứ đi qua biên
```

Đây chính là [bài 83](../83-aggregate-boundary/) (tham chiếu bằng id) nâng lên **cấp độ tổ chức**:
hai ngữ cảnh chia sẻ một **định danh**, không chia sẻ một **mô hình**.

### Bản đồ ngữ cảnh: quan hệ giữa các ngữ cảnh có tên

| Quan hệ | Nghĩa | Khi nào dùng |
|---|---|---|
| Đối tác | hai đội cùng đổi, cùng chịu trách nhiệm | hai đội cùng công ty |
| Khách / Nhà cung cấp | thượng nguồn nghe hạ nguồn | có quyền thương lượng |
| Tuân thủ | hạ nguồn dùng y nguyên mô hình trên | bên trên không đổi được |
| **Chống hư hỏng** | hạ nguồn **dịch** mô hình trên sang của mình | mô hình trên xấu → [bài 94](../94-anti-corruption-layer/) |
| Nhân chung | hai đội cùng sở hữu một phần mã dùng chung | rất ít, rất nguy hiểm |

*Nhân chung* là thứ mọi người bắt đầu và hối hận: một thư viện `common-model` mà ba đội cùng sửa.
Nó có **mọi** nhược điểm của mô hình chung, cộng thêm việc **không ai sở hữu nó**.

> Nếu không vẽ được bản đồ này cho hệ thống của bạn trên một trang giấy, thì ranh giới ngữ cảnh
> chưa tồn tại — chỉ có các gói code cùng dùng chung một mô hình.

### Khi nào KHÔNG tách

Bounded context có chi phí thật: mô hình lặp lại, mã dịch ở biên, dữ liệu đồng bộ trễ.

| Chưa nên tách | Đã đến lúc tách |
|---|---|
| Một đội làm cả hệ thống, cùng một bộ từ ngữ | Có field mà nửa số nơi dùng luôn để `null` |
| Chưa tìm ra từ nào mang hai nghĩa | Có từ phải hỏi lại *"ý anh là hoàn tất theo nghĩa nào"* |
| Số field phải bỏ trống còn nhỏ | Một thay đổi nhỏ phải xếp lịch với đội không liên quan |

Cột phải đều **đo được** — đó là điểm quan trọng: quyết định tách ngữ cảnh không phải chuyện cảm
tính, nó có số liệu.

## 3. Độ phức tạp

| | Chi phí |
|---|---|
| Mô hình chung — thêm một field | **N đội** cùng build lại, test lại, triển khai |
| Tách ngữ cảnh — thêm một field | **1 đội** |
| Tách ngữ cảnh — chi phí thường trực | mã dịch ở biên: O(số cặp ngữ cảnh giao tiếp) |
| Đếm tỉ lệ field tuỳ chọn (Python) | O(số field) — chạy được trong CI |

Đây là bài duy nhất trong tầng mà "độ phức tạp" **không** tính bằng thời gian chạy. Mô hình chung
không làm code chậm đi — nó làm **tổ chức** chậm đi, và đó là thứ đắt hơn nhiều.

Ở C++ có thêm một con số đo bằng đồng hồ thật: mô hình chung sống trong một **header**, và mọi đơn
vị biên dịch `#include` nó sẽ được dịch lại mỗi khi nó đổi một dòng. Một header dùng chung ở 400
file là 400 lần dịch lại — thứ cả đội cảm thấy mỗi ngày.

## 4. Lời giải

- [`ContextDemo.java`](ContextDemo.java) — ba ngữ cảnh là ba lớp lồng, và trình biên dịch canh
  giúp: `BanHang.KhachHang` với `KeToan.BenNhanHoaDon` là **hai kiểu**, gán chéo là lỗi build.
  Reflection đo trực tiếp 4 field vs 12.
- [`ContextDemo.cpp`](ContextDemo.cpp) — namespace làm cùng việc đó, và phần 4 thêm cái giá riêng
  của C++: **thời gian build**. File cũng cảnh báo một lỗ thủng đặc thù: nếu lớp đích có
  constructor một tham số **không `explicit`**, C++ sẽ tự tìm đường chuyển đổi ngầm — và ranh giới
  vừa thủng một lỗ.
- [`context_demo.py`](context_demo.py) — Python **không có gì** canh ranh giới, và hậu quả tinh vi
  hơn *"chạy rồi mới lỗi"*: object của ngữ cảnh sai **chạy êm** qua mọi hàm chỉ dùng thuộc tính
  chung, rồi mới nổ ở một chỗ cách xa nơi gây lỗi. Phần 3 dựng một **chốt ở biên** ba dòng đổi lỗi
  từ *"sâu, mơ hồ"* thành *"ngay, rõ"*.

**Khác biệt giữa ba ngôn ngữ:**

| | Chặn truyền nhầm ngữ cảnh | Cái giá riêng | Cách phòng thân |
|---|---|---|---|
| Java | ✅ lỗi biên dịch (hai kiểu) | — | — |
| C++ | ✅ lỗi biên dịch | thời gian build của header chung | `explicit` cho mọi constructor một tham số |
| Python | ❌ duck typing — **nổ muộn, xa nơi gây lỗi** | — | `isinstance` ở biên + đếm tỉ lệ field `None` |

## 5. Thực tế đi làm

**Cạm bẫy #1 — "một khái niệm thì một lớp".** DRY áp cho **mã nguồn giống nhau**, không áp cho
**khái niệm trùng tên**. Hai đội cùng gọi là "khách hàng" không có nghĩa là họ nói về cùng một thứ.

**Cạm bẫy #2 — thư viện `common-model` / `shared-dto`.** Cách phổ biến nhất để tạo ra một mô hình
chung mà không ai gọi nó là mô hình chung. Dấu hiệu: gói đó có nhiều commit từ mọi đội, và không ai
là chủ sở hữu.

**Cạm bẫy #3 — gỡ ràng buộc để dùng chung được.** Mỗi `Optional`/`nullable` thêm vào là một ràng
buộc nghiệp vụ bị đẩy ra khỏi kiểu dữ liệu và vào trong đầu người viết code. Đếm tỉ lệ field tuỳ
chọn là phép đo sớm nhất — nếu quá nửa, mô hình đã phục vụ quá nhiều ngữ cảnh.

**Cạm bẫy #4 — chia ngữ cảnh theo tầng kỹ thuật.** `web`, `service`, `repository` **không** phải
bounded context — chúng là các tầng của *một* ngữ cảnh. Ranh giới ngữ cảnh cắt theo **nghiệp vụ**,
và mỗi ngữ cảnh có đủ ba tầng của riêng nó.

**Cạm bẫy #5 — đồng bộ dữ liệu giữa hai ngữ cảnh bằng cách chia sẻ bảng CSDL.** Hai ngữ cảnh đọc
ghi chung một bảng là mô hình chung đội lốt hạ tầng: đổi cột là cả hai bên vỡ. Đi qua sự kiện
([bài 84](../84-domain-event/)) hoặc API, không đi qua bảng.

**Cạm bẫy #6 — tách ngữ cảnh quá sớm.** Ba bounded context cho một hệ thống một đội năm người là
mua toàn bộ chi phí mà không có lợi ích nào. Chờ tới khi có **bằng chứng đo được** ở bảng phần 2.

**Cạm bẫy #7 — quên rằng ranh giới ngữ cảnh là ranh giới TỔ CHỨC.** Luật Conway không phải một câu
đùa: nếu hai bounded context do cùng một đội làm, chúng sẽ dần dính vào nhau bất kể bạn vẽ gì trên
sơ đồ. Ranh giới bền vững là ranh giới trùng với ranh giới sở hữu.

**Biến thể phỏng vấn thường hỏi:**
- *"Bounded context là gì?"* — Ranh giới mà bên trong đó **một từ có đúng một nghĩa**. Trả lời được
  bằng ví dụ "hoàn tất" là đủ; trả lời "một microservice" là sai (đó là ranh giới triển khai).
- *"Bounded context có bằng microservice không?"* — Không. Một service có thể chứa nhiều ngữ cảnh
  (giai đoạn đầu), nhưng một ngữ cảnh **không nên** trải trên nhiều service — vì lúc đó bất biến
  của nó nằm ở hai tiến trình ([bài 83](../83-aggregate-boundary/) cạm bẫy #6).
- *"Lặp lại mô hình có vi phạm DRY không?"* — Không, vì hai mô hình đó **không giống nhau**: chúng
  chỉ trùng tên. Và chúng sẽ tiến hoá theo hai hướng khác nhau — điều mà mô hình chung không cho phép.
- *"Làm sao biết đã đến lúc tách?"* — Ba dấu hiệu đo được ở bảng phần 2. Nhấn mạnh cái thứ nhất
  (tỉ lệ field `null`) vì nó tự động hoá được.
- *"Hai ngữ cảnh chia sẻ dữ liệu thế nào?"* — Bằng **mã định danh** cộng sự kiện. Không chia sẻ
  object, không chia sẻ bảng. Và ở biên có một chỗ dịch **có tên**.

## 6. Self-check

```bash
cd 04-competitive/93-bounded-context
javac ContextDemo.java && java ContextDemo        # in "OK"
g++ -std=c++17 -o sol ContextDemo.cpp && ./sol    # in "OK"
python context_demo.py                            # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
