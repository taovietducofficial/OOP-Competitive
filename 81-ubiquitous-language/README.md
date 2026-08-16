# 81 — Ubiquitous Language: 300.000đ doanh thu không tồn tại, chỉ vì một cái tên

Tầng `04-competitive` mở đầu không phải bằng một mẫu thiết kế, mà bằng thứ nằm **trước** mọi mẫu
thiết kế: **từ ngữ**. Nếu tên trong code không phải tên người làm nghiệp vụ nói ra miệng, thì mọi
cuộc trao đổi giữa hai bên đều phải đi qua một bảng dịch nằm trong đầu ai đó — và bảng dịch đó sẽ
sai.

## 1. Đề bài

Cùng một nghiệp vụ — sổ đơn hàng của một cửa hàng — viết **hai lần**, bằng hai bộ từ ngữ.

| Bản | Kiểu dữ liệu | Câu hỏi "doanh thu là bao nhiêu?" trả lời thế nào |
|---|---|---|
| **Bản 1** — từ ngữ lập trình viên | `DataRecord { id, amt, status:int, flag1, flag2 }` | `sum(amt) where status >= 3` |
| **Bản 2** — từ ngữ nghiệp vụ | `DonHang { maDon, soTien, trangThai:enum, laKhachThanThiet, giaoNhanhTrongNgay }` | `sum(soTien) where trangThai.laHoanTat()` |

Bảng trạng thái mà cả hai bản cùng mô hình hóa:

| Số | Tên nghiệp vụ | Đã hoàn tất? | Đã kết thúc? | Được hoàn tiền? |
|---|---|---|---|---|
| 1 | mới tạo | không | không | không |
| 2 | đã thanh toán | không | không | không |
| 3 | **đã giao** | **có** | **có** | không |
| 4 | **đã huỷ** | **không** | **có** | **có** |

**Ràng buộc:**
- Bản 1 phải chạy được và "trông đúng" — đây không phải code cố tình viết xấu, đây là code
  bình thường mà ai cũng viết.
- Phải chỉ ra được **con bug cụ thể** mà cách đặt tên của bản 1 sinh ra, và đo được nó bằng tiền.
- Phải phân biệt hai khái niệm nghiệp vụ khác nhau mà bản 1 gộp làm một.

**Input/Output mẫu:**
```
Bốn đơn: DH-1 100.000 (đã giao) · DH-2 200.000 (đã thanh toán)
         DH-3 300.000 (ĐÃ HUỶ)  · DH-4 400.000 (đã giao)

Bản 1: calcTotal()          -> 800.000   <- SAI
Bản 2: doanhThuDaHoanTat()  -> 500.000   <- ĐÚNG
Chênh:                         300.000   = đúng số tiền của đơn ĐÃ HUỶ
```

## 2. Ý tưởng

**`status >= 3` sai vì con số có thứ tự, còn nghiệp vụ thì không.**

Người viết `calcTotal()` hiểu "status >= 3 nghĩa là đã xong", và anh ta **đúng** — theo cách anh
ta hiểu chữ "xong". Vấn đề là chữ "xong" trong đầu anh ta không phải chữ "xong" trong đầu kế toán.
Với kế toán, "đã huỷ" và "đã giao" là hai thứ đối lập nhau; với dãy số 1-2-3-4 thì 4 chỉ đơn giản
là *lớn hơn* 3.

Và thứ tự đó do **lập trình viên tình cờ đánh số**, không phải do nghiệp vụ quyết định. Nếu hôm
đó anh ta đánh `4 = đã giao, 3 = đã huỷ` thì code y hệt sẽ cho kết quả khác.

**Điểm chí mạng: cùng một biểu thức, một chỗ đúng một chỗ sai.**

```
countDone()  = đếm nơi status >= 3   -> 3 đơn.  ĐÚNG (nếu ý là "đã kết thúc")
calcTotal()  = cộng nơi status >= 3  -> 800k.   SAI  (vì ý phải là "đã hoàn tất")
```

Không có gì trong mã nguồn nói cho bạn biết chỗ nào là chỗ nào. Hai khái niệm nghiệp vụ **khác
nhau** — *kết thúc* và *hoàn tất* — bị viết bằng **cùng một dòng code**. Đó không phải lỗi cẩu
thả; đó là hệ quả trực tiếp của việc mô hình không có tên cho hai khái niệm đó.

**Cách sửa không phải "đổi tên biến cho đẹp".** Cách sửa là: đưa câu hỏi nghiệp vụ vào chính kiểu
dữ liệu, để nó có **đúng một** câu trả lời:

```java
enum TrangThaiDonHang {
    MOI_TAO, DA_THANH_TOAN, DA_GIAO, DA_HUY;

    boolean laHoanTat()   { return this == DA_GIAO; }                  // <- một chỗ
    boolean laKetThuc()   { return this == DA_GIAO || this == DA_HUY; }// <- một chỗ
    boolean duocHoanTien(){ return this == DA_HUY; }                   // <- một chỗ
}
```

Bây giờ hai khái niệm có hai cái tên, và không ai gõ nhầm cái này thành cái kia được.

**Phép thử để biết mình đã đạt hay chưa: đọc tên lên thành lời.**

| Bản 1 | Bản 2 |
|---|---|
| "data processor calc total" | "sổ đơn hàng, doanh thu đã hoàn tất" |
| "record status greater than 3" | "trạng thái đơn hàng là hoàn tất" |
| `flag1` | `laKhachThanThiet` |
| `flag2` | `giaoNhanhTrongNgay` |

Cột trái không đọc được thành câu. Cột phải thì đọc được — và phép thử thật là **nói to câu đó
cho người làm nghiệp vụ nghe**. Nếu họ gật đầu, bạn đã có ngôn ngữ chung. Nếu họ hỏi lại "ý em là
gì?", bạn vừa tìm ra một chỗ mô hình sai.

**Ngôn ngữ chung không phải là "đặt tên tiếng Việt".** Nó là: **không tồn tại bảng dịch nào** giữa
lời người làm nghiệp vụ nói và chữ trong mã nguồn. Bản 1 cần một bảng chín mục chỉ cho *một* lớp:

| Trong code | Thật ra là |
|---|---|
| `DataRecord` | đơn hàng |
| `amt` | số tiền |
| `status=1..4` | mới tạo / đã thanh toán / đã giao / đã huỷ |
| `flag1` | khách thân thiết |
| `flag2` | giao nhanh trong ngày |
| `calcTotal` | doanh thu (?) |

Dấu `(?)` ở dòng cuối chính là con bug. Không ai chắc `calcTotal` là doanh thu gì.

**Ranh giới — từ ngữ nào KHÔNG thuộc ngôn ngữ chung.** Ba loại nằm ngoài: thuật toán và cấu trúc
dữ liệu (`binarySearch`, `HashMap`), biến cục bộ ngắn trong vòng lặp ba dòng (`i`, `n`), và hạ
tầng (`connectionPool`, `retryPolicy`). Ngôn ngữ chung áp cho **mô hình miền** — nơi hai bên phải
nói chuyện được với nhau. Ép nó lên mọi dòng code là hiểu sai bài này.

**Luật cuối, và là luật quan trọng nhất: ngôn ngữ đi HAI CHIỀU.** Nếu người làm nghiệp vụ nói "đơn
treo" mà code không có khái niệm đó, thì hoặc bạn thiếu một trạng thái, hoặc chính họ cũng chưa
định nghĩa rõ từ đó — cả hai đều là một cuộc trao đổi cần xảy ra. Ngược lại, nếu code có
`TRANG_THAI_TAM` mà không ai bên nghiệp vụ biết nó là gì, thì đó là khái niệm lập trình viên bịa
ra, và nó sẽ trôi dần khỏi thực tế cho tới lúc gây ra một con số sai như ở trên.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| `doanhThuDaHoanTat()` / `calcTotal()` | O(n) — quét một lượt danh sách đơn | O(1) |
| Kiểm chứng bảng thuật ngữ bằng reflection | O(số lớp × số thành viên) — chỉ chạy lúc test | O(tổng số tên) |

Bài này **không đổi độ phức tạp** so với bản 1 — đó là điểm đáng chú ý. Ngôn ngữ chung không tốn
gì lúc chạy; cái nó tiết kiệm là thời gian của con người và tiền của công ty.

## 4. Lời giải

- [`LanguageDemo.java`](LanguageDemo.java) — bản đầy đủ nhất. `enum` có phương thức trả lời câu
  hỏi nghiệp vụ, và phần 6 dựng một **bài test kiểm chứng bảng thuật ngữ bằng reflection**: duyệt
  tên lớp + phương thức khai báo + hằng enum, khẳng định mọi từ nghiệp vụ đều xuất hiện.
- [`LanguageDemo.cpp`](LanguageDemo.cpp) — góc nhìn riêng của C++: với `enum class`, dòng
  `if (d.trangThai >= 3)` **không biên dịch được**. Con bug ở bản 1 không viết ra được. File cũng
  chỉ ra `enum` trần (không có `class`) vẫn tự chuyển thành `int` — và cái bẫy quay lại nguyên vẹn.
- [`language_demo.py`](language_demo.py) — góc nhìn riêng của Python: `Enum` thuần chặn phép so
  sánh với số (ném `TypeError`), còn `IntEnum` thì **không** — cái bẫy quay lại y hệt. Phần 6 dùng
  `dir()` + `vars()` + `dataclasses.fields()` để viết bài test bảng thuật ngữ gọn trong sáu dòng,
  cộng chiều ngược lại: API của mô hình miền **không được** chứa `flag`, `data`, `process`,
  `handle`, `temp`, `misc`, `util`, `manager`.

**Khác biệt giữa ba ngôn ngữ:**

| | Chặn `trangThai >= 3` | Chặn `int x = trangThai` | Kiểm chứng thuật ngữ bằng máy |
|---|---|---|---|
| Java | ✅ lỗi biên dịch (enum là lớp thật) | ✅ lỗi biên dịch | ✅ `Class.getDeclaredMethods()` |
| C++ | ✅ lỗi biên dịch — **chỉ khi dùng `enum class`** | ✅ lỗi biên dịch | ❌ không có reflection |
| Python | ✅ `TypeError` lúc chạy — **chỉ khi dùng `Enum`, không phải `IntEnum`** | ⚠️ không chặn | ✅ gọn nhất, `dir()` + `vars()` |

Một chi tiết dễ sập ở Python: `dir()` trên một `Enum` **không** liệt kê phương thức viết trong
thân lớp, chỉ liệt kê hằng thành viên. Phải đọc thêm `vars()`. File `.py` có ghi chú đúng chỗ đó.

## 5. Thực tế đi làm

**Cạm bẫy #1 — "đổi tên là refactor cosmetic, để sau".** Bài này cho thấy nó không phải cosmetic:
`status >= 3` đã đưa 300.000đ doanh thu không tồn tại vào báo cáo tài chính. Không ngoại lệ, không
log cảnh báo, và cả hai hàm đều "chạy đúng" theo ý người viết chúng. Bug đặt tên không nổ ngay —
nó nổ ở kỳ quyết toán.

**Cạm bẫy #2 — dùng số/`IntEnum`/`enum` trần cho trạng thái.** Khoảnh khắc trạng thái so sánh
được với số là khoảnh khắc ai đó sẽ viết `>= 3`. Java không cho phép; C++ chỉ an toàn với
`enum class`; Python chỉ an toàn với `Enum` thuần. `IntEnum` và `enum` trần chỉ nên dùng khi
**bắt buộc** phải tương thích với một giao thức cũ hoặc code C — không bao giờ trong mô hình miền.

**Cạm bẫy #3 — hai khái niệm nghiệp vụ, một cái tên.** "Kết thúc" và "hoàn tất" là ví dụ kinh
điển; ngoài đời còn có "đã gửi" vs "đã nhận", "huỷ" vs "hoàn tiền", "khách" vs "người thanh toán".
Dấu hiệu nhận biết: bạn thấy mình giải thích *"cái này thì có, trừ trường hợp…"* — chữ "trừ
trường hợp" nghĩa là đang có hai khái niệm bị nhét vào một cái tên.

**Cạm bẫy #4 — đặt tên theo cấu trúc dữ liệu thay vì theo nghiệp vụ.** `OrderList`, `UserMap`,
`ProductArray` nói cho bạn biết code lưu bằng gì, chứ không nói nghiệp vụ là gì — và khi đổi từ
`List` sang `Set` thì cái tên thành nói dối. `SoDonHang` (sổ đơn hàng) không có vấn đề đó, vì
"sổ" là từ nghiệp vụ chứ không phải từ kỹ thuật.

**Cạm bẫy #5 — dịch từ ngữ khi đi qua ranh giới đội.** Đội backend gọi là `customer`, đội thanh
toán gọi là `payer`, đội CSKH gọi là "thuê bao". Ba từ, ba mô hình, và mọi tích hợp giữa họ đều
là một phép dịch có thể sai. Nếu đúng là **ba nghiệp vụ khác nhau** thì ba tên là đúng — đó là
[bài 93 · Bounded Context](../93-bounded-context/). Nếu là **cùng một nghiệp vụ**, thì phải chốt
một từ duy nhất.

**Biến thể phỏng vấn thường hỏi:**
- *"Đặt tên biến cho hay có thật sự quan trọng không, hay chỉ là chuyện thẩm mỹ?"* — Trả lời bằng
  ví dụ này: `status >= 3` không phải xấu về thẩm mỹ, nó **sai về tiền**. Và nó sai được chính là
  vì mô hình không có tên cho khái niệm "hoàn tất".
- *"Team em có nên bắt buộc đặt tên bằng tiếng Việt không?"* — Sai câu hỏi. Việc cần bắt buộc là
  **không có bảng dịch**. Nếu người làm nghiệp vụ nói tiếng Việt, tên tiếng Việt cho mô hình miền
  là hợp lý; nếu công ty làm việc bằng tiếng Anh thì tên tiếng Anh — miễn là **cùng một từ** ở cả
  hai phía.
- *"Làm sao ép được ngôn ngữ chung khi team 30 người?"* — Bằng máy, không bằng lời nhắc trong code
  review. Phần 6 của cả file Java lẫn Python là một bài test chạy trong CI: mọi từ trong bảng
  thuật ngữ phải xuất hiện trong API của gói `mien/`, và gói `mien/` không được chứa `flag`,
  `data`, `tmp`, `manager`.
- *"Bảng thuật ngữ (glossary) để ở đâu?"* — Nếu để trong Confluence thì sáu tháng nữa nó sai. Để
  nó **trong mã nguồn**, dưới dạng danh sách hằng mà bài test đọc — lúc đó nó không thể lỗi thời
  mà không ai biết.
- *"Khi phát hiện tên trong code sai so với nghiệp vụ, sửa ngay hay ghi nợ kỹ thuật?"* — Sửa ngay,
  vì cái giá tăng theo số nơi cái tên đó lan tới. Đổi tên là refactor an toàn nhất tồn tại: IDE
  làm được tự động, trình biên dịch bắt hết chỗ sót (ở Java/C++), và không đổi hành vi.

## 6. Self-check

```bash
cd 04-competitive/81-ubiquitous-language
javac LanguageDemo.java && java LanguageDemo        # in "OK"
g++ -std=c++17 -o sol LanguageDemo.cpp && ./sol     # in "OK"
python language_demo.py                             # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
