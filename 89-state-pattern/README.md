# 89 — State Pattern: đơn đã huỷ vẫn được giao, vì một dòng KHÔNG có ở đó

Bài 32 dạy *"mỗi trạng thái là một object"*. Ở mức miền, điều quan trọng hơn là **ai sở hữu bảng
chuyển** — và hệ quả của nó là một tính chất mà không tài liệu nào nhấn đủ mạnh:

> Với `if`/`switch`, nhánh **thiếu** thường là nhánh **cho phép**. Quên một dòng là **mở** một cửa.
> Với bảng chuyển / state object, ô **thiếu** là ô **từ chối**. Quên một dòng là **đóng** một cửa.

Cùng một sơ suất, hai hậu quả ngược nhau. Đó là toàn bộ giá trị của mẫu này.

## 1. Đề bài

Máy trạng thái đơn hàng: `MOI_TAO → DA_THANH_TOAN → DA_GIAO`, huỷ được ở hai trạng thái đầu.
Phí huỷ **đổi theo trạng thái**: 0% khi chưa trả tiền, 10% khi đã trả, không huỷ được khi đã giao.

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | `if` rời rạc → đơn đã huỷ vẫn giao được | `trangThai == 3` sau khi đã là 4 |
| 2 | Bảng chuyển → cùng lời gọi đó **nổ** | ngoại lệ, trạng thái không đổi |
| 3 | Hành vi đổi theo trạng thái | `huy()` trả 0 hoặc 100.000 cho cùng đơn 1 triệu |
| 4 | Máy trạng thái kiểm được bằng máy | 4 cạnh / 12 ô · 0 trạng thái mồ côi |

**Ràng buộc:** trạng thái phải `private`, không setter; không dùng chuỗi làm trạng thái.

**Input/Output mẫu:**
```
SAI  — if rời rạc:
  thanhToan() -> 2 · huy() -> 4 · giao() -> 3      ❌ đã huỷ mà vẫn giao

ĐÚNG — bảng chuyển:
  thanhToan() · huy() -> DA_HUY
  giao()      -> IllegalStateException, trạng thái vẫn DA_HUY

Bảng 4 trạng thái × 3 sự kiện = 12 ô, chỉ 4 ô CHO PHÉP.
```

## 2. Ý tưởng

### Con bug không phải một điều kiện sai — mà là một điều kiện không tồn tại

```java
void giao() {
    // đáng lẽ: if (trangThai != 2) throw ...
    trangThai = 3;
}
```

Đọc hàm này, **không có gì trông sai cả**. Người viết nghĩ *"chỉ đơn đã thanh toán mới gọi
`giao()`"* và bỏ qua. Đó là loại bug code review khó bắt nhất: thứ phải nhìn thấy là một dòng
**vắng mặt**.

Với 4 trạng thái × 3 sự kiện = 12 ô, chỉ **4 ô hợp lệ** — nghĩa là 8 lời gọi phải bị từ chối. Bằng
`if`, mỗi cái trong 8 lời gọi đó cần một dòng do con người nhớ viết. Bằng bảng chuyển, cả 8 đều bị
từ chối vì **không ai viết gì cả**.

### Mặc định là TỪ CHỐI

Java `enum` với thân riêng cho từng hằng thể hiện điều này gọn nhất:

```java
enum TrangThai {
    DA_HUY { /* không override gì cả */ };

    // mặc định của MỌI hằng:
    TrangThai giao() { throw new IllegalStateException("không giao được ở " + this); }
}
```

Người viết `DA_HUY` không phải nghĩ tới việc "cấm giao"; họ chỉ cần **không viết gì**. Đó là khác
biệt giữa *"an toàn nếu nhớ"* và *"an toàn mặc định"*.

### Hành vi đổi theo trạng thái, không chỉ có chuyển tiếp

```java
MOI_TAO       -> phiHuy = 0
DA_THANH_TOAN -> phiHuy = 10%
DA_GIAO       -> không huỷ được
```

Cùng một lời gọi `huy()`, ba kết quả, và **không có `if` nào** trong `DonHang`. Luật phí huỷ nằm ở
đúng nơi nó thuộc về: trong trạng thái quyết định nó. Với `switch`, luật này thành nhánh thứ hai
trong một hàm khác — và hai nhánh đó sẽ lệch nhau ([bài 87](../87-specification/) phần 2).

### Trạng thái phải không gán được từ ngoài

Nếu có setter (hoặc field public), toàn bộ máy trạng thái thành **trang trí** — đúng như mô hình
thiếu máu ở [bài 86](../86-domain-service/). Chú ý: trong bản `if` rời rạc, `trangThai` là public,
và đó là gốc rễ khiến bug ở phần 1 tồn tại được.

### Kiểm máy trạng thái bằng máy

Ba câu hỏi đều trả lời được tự động:

| Câu hỏi | Bắt được bug gì |
|---|---|
| Bảng có đúng n cạnh? | ai đó thêm/bớt cạnh mà không ai để ý |
| Mọi trạng thái có **đến được** từ trạng thái đầu? | trạng thái **mồ côi** — tính năng không bao giờ chạy |
| Ngõ cụt có đúng những cái mình cố ý? | đơn hàng **mắc kẹt vĩnh viễn**, không thông báo |

Bug "trạng thái mồ côi" đáng chú ý: ai đó thêm `TAM_GIU` vào enum, viết đủ hành vi cho nó, nhưng
quên thêm cạnh dẫn **tới** nó — và tính năng "tạm giữ đơn" không bao giờ xảy ra trên production,
không lỗi, không log.

### Khi nào KHÔNG dùng mẫu này

- Chỉ 2 trạng thái, 1 sự kiện → một `boolean` là đủ.
- Quy trình duyệt **do người dùng cấu hình** → bảng chuyển **dữ liệu**, đọc từ CSDL.
- Số trạng thái lớn (>15) và luật giống nhau → bảng gọn hơn nhiều lớp, và in ra được thành sơ đồ.
- Ngược lại, khi mỗi trạng thái có **hành vi** riêng (không chỉ cạnh riêng) → một lớp cho mỗi trạng
  thái. Dấu hiệu đến lúc chuyển: bạn đã có bảng thứ ba, thứ tư cùng khoá theo trạng thái.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Chuyển trạng thái (bảng / đa hình) | **O(1)** | O(1) |
| Chuyển trạng thái (`if`/`switch` n nhánh) | O(n) | O(1) |
| Bảng chuyển | — | O(số trạng thái × số sự kiện) |
| Kiểm đủ cạnh | O(T×S) | O(1) |
| Loang tìm trạng thái mồ côi | O(T×S) mỗi vòng, tối đa T vòng → O(T²·S) | O(T) |
| C++: cả hai kiểm tra trên | **0 lúc chạy** — `static_assert` | 0 |

Con số đáng nhớ không phải O lớn mà là **tỉ lệ ô hợp lệ**: 4/12. Bảng càng thưa thì `if` rời rạc
càng nguy hiểm, vì số dòng bảo vệ phải viết tay chính là số ô rỗng.

## 4. Lời giải

- [`StateDemo.java`](StateDemo.java) — `enum` với **thân riêng cho từng hằng**: vừa liệt kê được
  hết (như enum), vừa đa hình (như lớp). Phương thức mặc định của enum **ném ngoại lệ**, mỗi hằng
  chỉ override những gì nó cho phép — nên "mặc định là từ chối" là tính chất của ngôn ngữ, không
  phải kỷ luật.
- [`StateDemo.cpp`](StateDemo.cpp) — bảng chuyển là `constexpr`, nên *"máy trạng thái có đúng 4
  cạnh"* và *"không có trạng thái mồ côi"* trở thành **`static_assert`**: trình biên dịch chạy
  thuật toán loang trên đồ thị và **từ chối dịch** nếu sai. Một máy trạng thái hỏng không tạo ra
  được file thực thi để mà chạy.
- [`state_demo.py`](state_demo.py) — thêm con bug thứ hai mà chỉ Python mới mắc dễ đến thế: trạng
  thái viết bằng **chuỗi**. Câu bảo vệ `if trang_thai == "da_huy"` *có* ở đó, nhìn thì đúng, và nó
  không bao giờ nổ vì giá trị thật là `"DA_HUY"`. Phần 7 sinh **sơ đồ Mermaid** từ chính bảng
  chuyển — tài liệu không thể lỗi thời.

**Khác biệt giữa ba ngôn ngữ:**

| | Cách biểu diễn | Kiểm máy trạng thái | Bẫy riêng |
|---|---|---|---|
| Java | `enum` có thân riêng từng hằng | test lúc chạy (`values()`) | — |
| C++ | bảng `constexpr` 2 chiều | ✅ **`static_assert` — lúc biên dịch** | mẹo `SO_LUONG` phải ở cuối enum |
| Python | `dict[(trạng thái, sự kiện)]` | test lúc chạy + **sinh sơ đồ** | chuỗi làm trạng thái → guard im lặng |

## 5. Thực tế đi làm

**Cạm bẫy #1 — trạng thái là `int` hoặc `String`.** `1`, `2`, `3` không nói gì
([bài 81](../81-ubiquitous-language/)), và `"DA_GIAO"` gõ sai không ai chặn. Enum, luôn luôn; chuỗi
chỉ sống ở biên (JSON, CSDL) và được đổi ngay tại đó.

**Cạm bẫy #2 — trạng thái có setter.** `don.setTrangThai(DA_GIAO)` vượt mặt toàn bộ máy trạng
thái, và nó sẽ được gọi — thường là từ một job sửa dữ liệu, hoặc từ tầng ORM khi nạp từ CSDL. Nếu
ORM bắt buộc phải có setter, hãy để nó `private`/`protected` và đặt tên là `khoiPhucTrangThai` để
không ai dùng nhầm cho nghiệp vụ.

**Cạm bẫy #3 — luật chuyển rải rác thay vì tập trung.** Dấu hiệu: nhiều hơn một nơi trong codebase
so sánh trạng thái. Câu hỏi kiểm tra: *"muốn biết từ `DA_THANH_TOAN` đi được đâu, tôi phải đọc bao
nhiêu file?"* Nếu nhiều hơn một, bảng chuyển chưa tồn tại thật.

**Cạm bẫy #4 — quên trạng thái kết thúc.** Ngõ cụt ngoài dự kiến nghĩa là đơn hàng **mắc kẹt vĩnh
viễn**: không giao được, không huỷ được, và không có thông báo nào. Danh sách ngõ cụt phải được
nhìn có chủ đích, không phải phát hiện qua khiếu nại của khách.

**Cạm bẫy #5 — trạng thái mồ côi.** Thêm một trạng thái mà quên cạnh dẫn tới nó. Tính năng không
chạy, không lỗi, không log — và người viết vẫn báo cáo "đã xong". Bài kiểm tra loang ở phần 6 mất
15 dòng và bắt được toàn bộ lớp bug này.

**Cạm bẫy #6 — đổi trạng thái rồi mới kiểm tra.** `trangThai = DA_HUY; if (...) throw;` để lại
object ở trạng thái sai khi ngoại lệ bay ra. Luôn hỏi/kiểm **trước**, gán **sau** — như `huy()`
trong cả ba bản: tính phí trước, chuyển trạng thái sau.

**Cạm bẫy #7 — nhét mọi thứ vào máy trạng thái.** Không phải cờ nào cũng là trạng thái. `daInHoaDon`,
`daGuiEmail` là **thuộc tính**, không phải trạng thái — nhét vào làm số trạng thái nhân đôi mỗi lần.
Trạng thái là thứ quyết định **được làm gì tiếp theo**; còn lại là dữ liệu.

**Biến thể phỏng vấn thường hỏi:**
- *"Vì sao không dùng `switch` cho máy trạng thái?"* — Vì nhánh thiếu trong `switch` thường là nhánh
  cho phép. Nói được câu này (kèm ví dụ đơn đã huỷ vẫn giao) là đủ.
- *"State pattern khác Strategy ở đâu?"* — Hình dạng giống nhau; khác ở chỗ **ai đổi**: strategy do
  người ngoài chọn và không tự đổi, state tự chuyển sang state khác theo sự kiện. Và state biết
  **những state khác**, strategy thì không.
- *"Trạng thái nên lưu xuống CSDL thế nào?"* — Lưu **tên** (chuỗi của enum), không lưu số thứ tự.
  Số thứ tự đổi khi ai đó chèn một hằng vào giữa enum, và toàn bộ dữ liệu cũ lệch một bậc — bug
  không thể sửa ngược.
- *"Làm sao biết máy trạng thái đúng?"* — Ba câu hỏi ở phần 2, và cả ba viết được thành test. Ở C++
  còn viết được thành `static_assert`.
- *"Khi nào nên chuyển từ bảng sang một lớp cho mỗi trạng thái?"* — Khi bạn đã có bảng thứ ba, thứ
  tư cùng khoá theo trạng thái (phí huỷ, thời hạn, quyền xem…). Lúc đó dữ liệu của một trạng thái
  đang bị rải ra nhiều bảng, và gộp lại thành một lớp là đúng.

## 6. Self-check

```bash
cd 04-competitive/89-state-pattern
javac StateDemo.java && java StateDemo        # in "OK"
g++ -std=c++17 -o sol StateDemo.cpp && ./sol  # in "OK"
python state_demo.py                          # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
