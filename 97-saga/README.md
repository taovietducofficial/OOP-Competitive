# 97 — Saga: bước 3 hỏng, và 500.000 của khách kẹt lại vĩnh viễn

Bài 83 cấm sửa hai aggregate trong một transaction; bài 84 nói chúng nói chuyện bằng sự kiện. Câu
hỏi còn lại: **nếu bước thứ hai hỏng thì bước thứ nhất đã xảy ra rồi, làm sao?**

Câu trả lời **không** phải "rollback" — không có gì để rollback, transaction đã commit. Câu trả lời
là **bù trừ**: ghi một sự thật nghiệp vụ **mới** để triệt tiêu hậu quả của sự thật cũ. Phân biệt
được hai điều đó là toàn bộ bài này.

## 1. Đề bài

Đặt hàng = 3 bước trên 3 aggregate, 3 transaction rời nhau: trừ kho → trừ tiền → tạo vận đơn.
Bước 3 hỏng.

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Không có bù trừ → tiền kẹt | kho 8, số dư 500.000 sau khi đặt hàng **thất bại** |
| 2 | Có bù trừ → về đúng ban đầu, **thứ tự đảo** | kho 10, số dư 1.000.000, bù tiền **trước** kho |
| 3 | Bù trừ **không phải** rollback | kho có **2** bút toán (trừ + trả), không phải 0 |
| 4 | Bước không-bù-trừ-được đặt sai chỗ | email đã gửi cho một đơn thất bại |
| 5 | Bù trừ không idempotent → hoàn 2 lần | số dư 1.500.000 — khách được thêm 500.000 |

**Ràng buộc:** mỗi bước đăng ký hành động bù trừ **ngay sau** khi làm; bước không bù trừ được phải
nằm **cuối**.

**Input/Output mẫu:**
```
trừ kho(2)      -> COMMIT   + đăng ký bù: trả kho(2)
trừ tiền(500k)  -> COMMIT   + đăng ký bù: hoàn(500k)
tạo vận đơn     -> HỎNG
  => bù ngược:  hoàn(500k), trả kho(2)
  => kho 10, số dư 1.000.000, 2 hành động bù trừ
```

## 2. Ý tưởng

### Bù trừ ≠ rollback

Đây là điểm hay bị hiểu sai nhất.

| | Rollback | **Bù trừ** |
|---|---|---|
| Dấu vết | **xoá sạch**, như chưa từng xảy ra | **giữ nguyên**, ghi thêm sự thật mới |
| Sao kê của khách | không có gì | `-500.000` rồi `+500.000` |
| Ai làm | CSDL | **nghiệp vụ** |
| Có thể hỏng không | không | **có** |

Với sổ kế toán, bù trừ là **bút toán đảo** — và nó *phải* hiện trên sao kê. Khách đã nhìn thấy số
dư bị trừ; giấu bút toán hoàn đi là làm sao kê nói dối.

### Thứ tự đảo không phải chuyện thẩm mỹ

Nếu bước 2 phụ thuộc bước 1 (rất thường), bù trừ bước 1 trước khi bù bước 2 sẽ để lại trạng thái vô
nghĩa ở giữa. Chạy tới đâu, bù ngược tới đó, theo **thứ tự đảo**.

### Xếp mọi bước KHÔNG bù trừ được xuống cuối

Gửi email, gửi SMS, gọi API bên thứ ba không có hàm huỷ, in phiếu — không cái nào có hành động
ngược. Đặt chúng ở giữa saga nghĩa là một đơn hàng thất bại vẫn gửi email "đã xác nhận".

> **Luật:** mọi bước không bù trừ được đi **sau cùng**, sau khi mọi bước có thể hỏng đã xong.

Đây là [bài 84](../84-domain-event/) phần 3 quay lại ở quy mô quy trình.

### Bù trừ phải idempotent

Saga chạy dở rồi tiến trình chết → khởi động lại → chạy bù trừ **lần nữa**. Nếu `hoan(500.000)` cộng
tiền mỗi lần được gọi thì khách được hoàn hai lần.

Cách chữa là [bài 91](../91-idempotency/): mỗi hành động bù trừ mang một khoá idempotency (thường
là *mã saga + số thứ tự bước*). Không có bước đó thì **cơ chế thử lại — thứ bắt buộc phải có —
trở thành máy sinh tiền**.

### Trạng thái saga phải được lưu

Saga là một **entity** ([bài 82](../82-entity-value-object/)): có mã, có trạng thái, được lưu sau
**mỗi** bước.

```
ma_saga | buoc_hien_tai | trang_thai
SG-01   | 2             | DANG_CHAY
SG-02   | 3             | DANG_BU_TRU
```

Và vì là entity có trạng thái, nó cũng cần khoá lạc quan ([bài 92](../92-optimistic-concurrency/))
— hai tiến trình cùng tiếp tục một saga là chuyện có thật.

### Điều phối hay hợp xướng

| | **Điều phối** (bài này) | **Hợp xướng** |
|---|---|---|
| Ai biết quy trình | một object saga | rải trong người nghe sự kiện |
| Thấy được quy trình? | **có** — đọc một file | không — lần theo 5 dịch vụ |
| Ghép chặt? | trung tâm biết mọi bước | lỏng hơn |

Quy trình **có bù trừ** → điều phối, vì *"chạy tới đâu, bù tới đó"* cần một chỗ biết thứ tự. Hệ quả
phụ **độc lập** (cộng điểm, gửi thông báo, thống kê) → hợp xướng.

Dấu hiệu chọn sai: phải mở 5 dịch vụ mới trả lời được *"đơn hàng này đang ở bước nào"*.

### Saga không phải transaction — ba tính chất bị mất

- **Không cô lập**: giữa bước 1 và bước 3, người khác *nhìn thấy* trạng thái nửa vời. Nếu điều đó
  không chấp nhận được thì cụm này phải là **một aggregate**, không phải một saga.
- **Không nguyên tử tức thời**: có một khoảng hệ thống ở trạng thái trung gian — nhất quán *cuối*,
  và độ trễ của nó là con số phải đo.
- **Bù trừ có thể hỏng**. Lúc đó cần hàng đợi thư chết và con người xử lý tay. Một saga không có
  đường thoát cho trường hợp này là một saga **chưa xong**.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Đường thuận lợi (n bước) | O(n) | O(n) — danh sách bù trừ đã đăng ký |
| Hỏng ở bước k | O(k) làm + **O(k) bù trừ** | O(k) |
| Lưu trạng thái saga | +1 lần ghi **sau mỗi bước** → O(n) lượt ghi | O(1) một dòng |
| Khôi phục sau khi tiến trình chết | O(số saga đang dở) mỗi lần quét | O(1) |

Dòng thứ ba là chi phí thật ít ai tính trước: saga bền vững **nhân đôi số lượt ghi** — mỗi bước
nghiệp vụ kèm một lượt ghi trạng thái saga. Đó là cái giá để sống sót qua việc tiến trình bị giết,
và nó phải được chấp nhận có ý thức.

## 4. Lời giải

- [`SagaDemo.java`](SagaDemo.java) — saga là một **object tường minh**: danh sách `Buoc(tên, làm,
  bù trừ)`, chạy tới đâu hỏng thì đảo danh sách đã xong và bù ngược. `buTru == null` đánh dấu bước
  không bù trừ được, và nhật ký ghi lại `KHÔNG-BÙ-TRỪ-ĐƯỢC:...` — saga **biết** mình để lại hậu quả
  không xoá được.
- [`SagaDemo.cpp`](SagaDemo.cpp) — saga trong một tiến trình **chính là RAII**: một lớp bảo vệ phạm
  vi gom hành động bù trừ, destructor chạy chúng theo thứ tự đảo trên mọi đường thoát. Không `try`,
  không `finally`, không ai quên được. Phần 6 nói thẳng giới hạn: **destructor không chạy khi tiến
  trình bị giết** — nên RAII đủ cho saga trong bộ nhớ và tuyệt đối không đủ cho saga phân tán.
- [`saga_demo.py`](saga_demo.py) — Python có sẵn thứ này trong thư viện chuẩn và hầu như không ai
  biết: **`contextlib.ExitStack`**. `stack.callback(bù_trừ)` sau mỗi bước, `stack.pop_all()` khi
  thành công — ba dòng là một saga hoàn chỉnh. Chi tiết dễ quên nhất: **quên `pop_all()` thì saga
  luôn bù trừ**, kể cả khi mọi bước đều thành công.

**Khác biệt giữa ba ngôn ngữ:**

| | Cơ chế saga trong tiến trình | Chạy trên đường ngoại lệ? | Điểm dễ sai nhất |
|---|---|---|---|
| Java | lớp `Saga` tường minh | có (bắt trong vòng lặp) | quên đăng ký bù trừ cho một bước |
| C++ | **RAII** — destructor | ✅ tự động, không thể quên | destructor **không được ném** (bài 74) |
| Python | **`ExitStack`** (thư viện chuẩn) | ✅ tự động | **quên `pop_all()`** → luôn bù trừ |

Điểm chung của cả ba: chúng chỉ giải quyết saga **trong một tiến trình**. Saga phân tán cần trạng
thái được lưu — không cơ chế ngôn ngữ nào thay thế được điều đó.

## 5. Thực tế đi làm

**Cạm bẫy #1 — cố dùng transaction phân tán (2PC) thay vì saga.** Nó khoá tài nguyên trên nhiều hệ
suốt thời gian giao dịch, và khi một bên treo thì cả cụm treo theo. Gần như mọi hệ hiện đại chọn
saga chính vì lý do này.

**Cạm bẫy #2 — coi bù trừ là "undo".** `deleteOrder()` để bù cho `createOrder()` xoá luôn bằng
chứng rằng đơn từng tồn tại. Bù trừ đúng là `cancelOrder(lý do)` — đơn vẫn còn, trạng thái là "đã
huỷ", và lịch sử đầy đủ.

**Cạm bẫy #3 — bước không bù trừ được nằm giữa.** Kiểm tra nhanh: đi qua từng bước và hỏi *"nếu
bước sau hỏng, tôi lấy lại được cái này không?"*. Mọi câu trả lời "không" phải bị đẩy xuống cuối.

**Cạm bẫy #4 — bù trừ không idempotent.** Kết hợp với cơ chế thử lại là mất tiền. Đây là lý do bài
91 đi ngay trước bài này.

**Cạm bẫy #5 — trạng thái saga chỉ nằm trong bộ nhớ.** Chạy hoàn hảo trong test, và để lại rác mỗi
lần triển khai (pod bị thu hồi giữa saga). Saga bền vững phải lưu sau mỗi bước.

**Cạm bẫy #6 — không có đường xử lý khi bù trừ hỏng.** Hoàn tiền thất bại vì cổng thanh toán chết.
Cần hàng đợi thư chết, cảnh báo, và một màn hình cho con người xử lý tay — không phải một dòng
`log.error` rồi thôi.

**Cạm bẫy #7 — dùng saga cho thứ đáng lẽ là một aggregate.** Nếu nghiệp vụ không chấp nhận được
trạng thái nửa vời (dù chỉ 200ms), thì đó không phải saga — đó là một bất biến, và cụm dữ liệu đó
phải nằm chung một aggregate ([bài 83](../83-aggregate-boundary/)).

**Biến thể phỏng vấn thường hỏi:**
- *"Saga là gì?"* — Chuỗi transaction cục bộ, mỗi cái có một hành động bù trừ, chạy tới đâu hỏng thì
  bù ngược tới đó. Nói thêm *"bù trừ không phải rollback"* là điểm phân biệt.
- *"Vì sao không dùng transaction phân tán?"* — Khoá tài nguyên trên nhiều hệ, một bên treo thì cả
  cụm treo, và không phải hệ nào cũng hỗ trợ.
- *"Bước không bù trừ được thì làm sao?"* — Xếp xuống cuối. Nếu không xếp được (nghiệp vụ bắt buộc
  gửi email trước), thì phải đổi thiết kế nghiệp vụ, không phải đổi code.
- *"Điều phối hay hợp xướng?"* — Có bù trừ → điều phối. Hệ quả phụ độc lập → hợp xướng. Và nói được
  cái giá của mỗi bên.
- *"Bù trừ hỏng thì sao?"* — Thử lại (nên bù trừ phải idempotent), rồi hàng đợi thư chết, rồi con
  người. Trả lời "không hỏng được" cho thấy chưa vận hành hệ thật.

## 6. Self-check

```bash
cd 04-competitive/97-saga
javac SagaDemo.java && java SagaDemo        # in "OK"
g++ -std=c++17 -o sol SagaDemo.cpp && ./sol # in "OK"
python saga_demo.py                         # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
