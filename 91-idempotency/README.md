# 91 — Idempotency: mạng đứt một lần, khách bị trừ tiền hai lần

Bài 84 kết luận sự kiện phải phát **sau** commit, và mọi cách làm thực tế — outbox, hàng đợi, cơ
chế thử lại của HTTP — đều là **giao ít nhất một lần**. Nghĩa là gửi trùng không phải rủi ro, nó
là điều **chắc chắn xảy ra**. Idempotency là thứ duy nhất làm cho điều đó vô hại.

## 1. Đề bài

Lệnh chuyển tiền có thể được gửi lại bất kỳ lúc nào. Chứng minh bằng code:

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Không có khoá → thử lại = trừ hai lần | số dư 800.000 thay vì 900.000 |
| 2 | "Kiểm tra rồi mới làm" **vẫn hỏng** | vẫn 800.000, khe hở giữa hai lời gọi |
| 3 | Giành chỗ nguyên tử → gọi 3 lần, trừ 1 lần | `soLanThucSuTru == 1`, ba biên lai **bằng nhau** |
| 4 | Cùng khoá + nội dung khác → **từ chối** | ngoại lệ, số dư không đổi |

**Ràng buộc:** khoá do phía gọi sinh; lần gửi lại phải trả về **đúng kết quả cũ**, không phải "đã
xử lý"; bản ghi phải lưu **vân tay** của nội dung.

**Input/Output mẫu:**
```
chuyen(KEY-1, TK-A, 100.000)  -> GD-1, số dư 900.000
chuyen(KEY-1, TK-A, 100.000)  -> GD-1, số dư 900.000     (kết quả CŨ, không làm lại)
chuyen(KEY-1, TK-A, 5.000.000)-> lỗi "khoá đã dùng cho một lệnh khác"
chuyen(KEY-2, TK-A, 100.000)  -> GD-2, số dư 800.000     (khoá khác = lệnh khác)
```

## 2. Ý tưởng

### Vì sao "kiểm tra rồi mới làm" vẫn sai

Đây là bản vá đầu tiên ai cũng nghĩ ra:

```
A: daCo(KEY-1)? -> chưa
B: daCo(KEY-1)? -> chưa      <- cùng lúc
A: lam()  -> trừ 100.000
B: lam()  -> trừ 100.000
```

Giữa `daCo()` và `lam()` có một **khe hở**, và khe hở đó là tiền. Bài học chung: **mọi cặp "hỏi rồi
làm" trên trạng thái chia sẻ** đều có khe hở này — `containsKey` + `put`, `SELECT` + `INSERT`,
`exists()` + `create()`. ([Bài 83](../83-aggregate-boundary/) phần 2 là một biến thể khác của cùng
lỗi.)

### Cách đúng: một lời gọi vừa hỏi vừa giành chỗ

| Ngôn ngữ | Nguyên thuỷ | Trả về |
|---|---|---|
| Java | `ConcurrentHashMap.putIfAbsent(k, v)` | `null` = tôi vừa chèn |
| C++ | `std::map::insert({k, v})` | `.second == true` = tôi vừa chèn |
| Python | `dict.setdefault(k, v)` | so bằng **`is`** với object mình vừa tạo |
| CSDL | `INSERT` + ràng buộc **duy nhất** | lỗi khoá trùng = người khác giành trước |

Dòng cuối là bản thật: trong hệ có nhiều tiến trình, "giành chỗ nguyên tử" chính là ràng buộc duy
nhất của CSDL. `putIfAbsent` chỉ là phiên bản trong-bộ-nhớ của cùng ý tưởng.

### Idempotent ≠ "lần sau thì bỏ qua"

Lần gửi lại phải trả về **đúng kết quả cũ**. Nếu nó trả `null` hoặc ném "đã xử lý", phía gọi vẫn
không biết mã giao dịch — và nó sẽ thử lại. Vòng lặp đó không bao giờ dừng.

### Cùng khoá, khác nội dung → phải từ chối

Nếu trả biên lai cũ (100.000) cho một lệnh 5 triệu, phía gọi tin rằng 5 triệu đã chuyển xong. Đó là
hỏng **nặng hơn** trừ tiền hai lần: hệ thống vừa *nói dối*. Vì vậy bản ghi phải lưu **vân tay của
nội dung**, không chỉ khoá.

### Ai sinh khoá, và sinh lúc nào

Khoá do **phía gọi** sinh, **trước** lần gửi đầu tiên, giữ nguyên qua mọi lần thử lại. Ba cách sinh
khoá sai hay gặp:

| Cách sinh | Vì sao sai |
|---|---|
| Máy chủ sinh | mỗi request một khoá mới → vô dụng hoàn toàn |
| Băm nội dung | hai lần chuyển 100.000 **cố ý** bị gộp làm một |
| Thời gian | thử lại ở mili-giây khác là khoá khác |

Cách đúng: UUID sinh ở phía gọi khi **người dùng bấm nút**, không phải khi gửi request. Lý do sâu
hơn: hệ thống không có cách nào tự phân biệt *"gửi lại"* với *"cố ý làm hai lần"* — chỉ phía gọi
biết, nên chỉ phía gọi được quyền quyết định bằng khoá.

### Phép tính tuyệt đối thì tự nó đã idempotent

```
soDu["TK-A"] = 400_000;   // chạy bao nhiêu lần cũng thế   ✅
soDu -= 100_000;          // mỗi lần chạy lại là một lần sai thêm  ❌
```

Nguyên tắc thiết kế: khi được chọn, hãy viết lệnh theo dạng **tuyệt đối** (*"đặt trạng thái = ĐÃ
GIAO"*) thay vì **tương đối** (*"tăng số lượng lên 1"*). Lệnh tuyệt đối idempotent miễn phí — không
sổ khoá, không dọn dẹp. Không phải lúc nào cũng chọn được, nhưng nhiều lệnh tưởng là tương đối thì
viết lại được thành tuyệt đối.

### Sổ khoá phải có hạn và có phạm vi

- **Hạn**: giữ 24–72 giờ — dài hơn mọi lịch thử lại, ngắn đủ để sổ không phình. Sau hạn, cùng khoá
  đó được coi là lệnh mới; đó là đánh đổi **có ý**, phải nói ra trong tài liệu API.
- **Phạm vi**: khoá phải kèm định danh người gọi. Nếu không, khách A đoán được khoá của khách B là
  **chặn được giao dịch của người khác**.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Giành chỗ (`putIfAbsent` / `insert` / `setdefault`) | **O(1)** băm · O(log n) cây | O(1) |
| "Kiểm tra rồi làm" | O(1) × 2 lời gọi — **và một khe hở** | O(1) |
| Sổ khoá | — | **O(số lệnh trong cửa sổ hạn)** |
| Dọn khoá hết hạn | O(số khoá hết hạn) | — |

Chi phí thật của idempotency là dòng thứ ba: sổ khoá là **dữ liệu thật**, lớn theo lưu lượng, và
phải được thiết kế (chỉ mục, phân vùng, dọn dẹp) như mọi bảng khác. Bỏ qua điều này là cách phổ
biến nhất để một cơ chế đúng về logic làm sập hệ thống về vận hành.

## 4. Lời giải

- [`IdempotencyDemo.java`](IdempotencyDemo.java) — `ConcurrentHashMap.putIfAbsent` là nguyên thuỷ,
  bản ghi lưu vân tay + kết quả, và trạng thái "đang xử lý" (`ketQua == null`) để lần gửi lại **khi
  chưa xong** không bị coi là trùng.
- [`IdempotencyDemo.cpp`](IdempotencyDemo.cpp) — `std::map::insert` trả `pair<iterator, bool>`, và
  cái `bool` đó **chính là** câu trả lời cần thiết. Phần 3 cho nổ cái bẫy C++ phá đúng bài toán
  này: **`so[khoa]` — chỉ đọc thôi — đã chèn một bản ghi rỗng vào map**. Sổ tự đầy lên bằng những
  khoá không ai tạo, và câu hỏi *"khoá này đã dùng chưa"* trả lời sai từ lần thứ hai.
- [`idempotency_demo.py`](idempotency_demo.py) — `dict.setdefault` là nguyên thuỷ, nhưng nó trả về
  **giá trị** chứ không trả về "tôi có chèn không" → phải phân biệt bằng **`is`**, không phải `==`.
  Phần 3 chỉ ra `defaultdict` mắc **đúng cùng cái bẫy** với `operator[]` của C++.

**Khác biệt giữa ba ngôn ngữ:**

| | Nguyên thuỷ giành chỗ | Cách biết "tôi vừa chèn" | Bẫy riêng |
|---|---|---|---|
| Java | `putIfAbsent` | trả về `null` | — |
| C++ | `map::insert` | `.second` | **`operator[]` chèn khi đọc** |
| Python | `dict.setdefault` | so `is` với object vừa tạo | **`defaultdict` tạo khoá khi đọc** |

Ghi chú Python: ngay cả trong một tiến trình, `if k not in d: d[k] = v` **không nguyên tử** — GIL
có thể chuyển luồng giữa hai câu lệnh. `setdefault` thì có, vì nó là một lời gọi phương thức của
`dict` cài bằng C.

## 5. Thực tế đi làm

**Cạm bẫy #1 — tin rằng "đúng một lần" tồn tại.** Nó không tồn tại trên mạng: bên gửi không bao giờ
phân biệt được *"chưa nhận"* với *"nhận rồi mà mất phản hồi"*. "Đúng một lần" luôn được làm bằng
**giao ít nhất một lần + xử lý idempotent**. Thiếu nửa nào cũng mất tiền.

**Cạm bẫy #2 — "kiểm tra rồi mới làm".** Bản vá đầu tiên ai cũng viết, và nó đúng trong mọi test
đơn luồng. Chỉ hỏng trên production, lúc có tải, và mỗi lần hỏng là một khiếu nại của khách.

**Cạm bẫy #3 — lần gửi lại ném lỗi thay vì trả kết quả cũ.** `409 Conflict` cho một request thử lại
là sai: phía gọi sẽ thử tiếp. Trả `200` kèm **đúng biên lai cũ** mới là idempotent.

**Cạm bẫy #4 — không lưu vân tay nội dung.** Khoá trùng với nội dung khác là một lỗi lập trình của
phía gọi, và trả kết quả cũ cho nó là biến lỗi của họ thành mất tiền của bạn.

**Cạm bẫy #5 — sổ khoá không có hạn.** Bảng chỉ ghi vào, không bao giờ xoá, và ba năm sau nó là
bảng lớn nhất trong CSDL. Đặt hạn ngay từ ngày đầu, và ghi hạn đó vào tài liệu API.

**Cạm bẫy #6 — khoá không có phạm vi người gọi.** Khoá toàn cục nghĩa là ai đoán được khoá của
người khác thì chặn được giao dịch của họ — một lỗ hổng từ chối dịch vụ nhắm đúng một nạn nhân.

**Cạm bẫy #7 — idempotent ở tầng sai.** Chặn trùng ở tầng API mà không chặn ở tầng xử lý hàng đợi
thì hàng đợi vẫn giao trùng. Nơi cần idempotent là **nơi có tác dụng phụ**, không phải nơi nhận
request.

**Biến thể phỏng vấn thường hỏi:**
- *"Idempotent nghĩa là gì?"* — Gọi n lần cho ra cùng trạng thái và **cùng kết quả trả về** như gọi
  một lần. Vế thứ hai là vế người ta hay quên, và là vế quyết định.
- *"Vì sao `if not exists then create` không đủ?"* — Khe hở giữa hai lời gọi. Nói được thêm rằng
  cách chữa là một thao tác nguyên tử (ràng buộc duy nhất của CSDL) là đủ.
- *"Khoá idempotency nên do ai sinh?"* — Phía gọi, khi người dùng bấm nút. Câu hỏi tiếp: *"vì sao
  không băm nội dung?"* — vì hệ thống không phân biệt được gửi lại với cố ý làm hai lần.
- *"`PUT` và `POST` cái nào idempotent?"* — `PUT` (tuyệt đối) có, `POST` (tạo mới) không — đó chính
  là phần 6: tuyệt đối vs tương đối. `DELETE` cũng idempotent về trạng thái, dù mã trả về có thể khác.
- *"Giữ khoá bao lâu?"* — Dài hơn tổng thời gian của lịch thử lại dài nhất, thường 24–72 giờ. Và
  phải nói ra trong tài liệu, vì sau hạn đó hành vi đổi.

## 6. Self-check

```bash
cd 04-competitive/91-idempotency
javac IdempotencyDemo.java && java IdempotencyDemo        # in "OK"
g++ -std=c++17 -o sol IdempotencyDemo.cpp && ./sol        # in "OK"
python idempotency_demo.py                                # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
