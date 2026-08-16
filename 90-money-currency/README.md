# 90 — Money & Currency: 100đ chia cho 3 người, mất 1 xu

Đây là value object quan trọng nhất trong mọi hệ thống nghiệp vụ, và là chỗ **mọi người đều tin là
mình làm đúng**. Bài cho nổ bốn con bug, mỗi cái đo được bằng tiền.

## 1. Đề bài

Dựng kiểu `Tien` và chứng minh bằng code:

| # | Bug | Đo bằng gì |
|---|---|---|
| 1 | `double`/`float` làm lệch sổ | `0.1 + 0.2 == 0.30000000000000004`; 10.000 lần cộng 1 xu ≠ 100đ |
| 2 | Cộng hai loại tiền tệ | `100.0 + 50.0 == 150.0` — chạy êm, vô nghĩa |
| 3 | Chia 100 cho 3 làm bốc hơi tiền | `33.33 × 3 = 99.99` |
| 4 | "Tiền tệ nào cũng 2 chữ số thập phân" | `100.000đ × 100 = 10 triệu` — sai 100 lần |

**Ràng buộc:**
- Số tiền lưu bằng **số nguyên đơn vị nhỏ nhất** (xu/cent/fils), không bao giờ bằng số thực.
- Chế độ làm tròn là **tham số bắt buộc**, không có giá trị mặc định.
- Tổng các phần sau khi chia phải **bằng đúng** số ban đầu.

**Input/Output mẫu:**
```
Tien.tu("100.00", USD).chiaDeu(3)
  -> [33.34 USD, 33.33 USD, 33.33 USD]      tổng = 100.00 USD ✅

Tien.tu("100.50", VND)  -> IllegalArgumentException  (0,5 đồng không tồn tại)
Tien<USD> + Tien<VND>   -> lỗi BIÊN DỊCH (C++) / ValueError (Java, Python)
```

## 2. Ý tưởng

### Vì sao `double` sai — và sai như thế nào

`0.1` trong hệ nhị phân là số vô hạn tuần hoàn, y như `1/3` trong hệ thập phân. Không có gì "sửa"
được điều đó — nó là bản chất của kiểu dữ liệu. Cộng 1 xu mười nghìn lần và số dư đã lệch; nhân
với số giao dịch thật của một tháng và kế toán có một khoản chênh không giải thích được.

Cách chữa: **lưu số đơn vị nhỏ nhất bằng số nguyên**. `100.50 USD` là `10050` cent. Mọi phép cộng
trừ trở thành chính xác tuyệt đối, mãi mãi.

### Số chữ số thập phân KHÔNG phải lúc nào cũng là 2

| Tiền tệ | Số chữ số | Đơn vị nhỏ nhất |
|---|---|---|
| VND | **0** | 1 đồng |
| USD | 2 | 1 cent = 1/100 |
| JOD (dinar Jordan) | **3** | 1 fils = 1/1000 |

Hằng số `100` viết cứng trong code là một bug chờ nổ. Với VND nó làm sai **100 lần**; với JOD nó
làm mất một chữ số. Số chữ số là **thuộc tính của tiền tệ**, tra từ bảng.

Hệ quả trực tiếp: `Tien.tu("100.50", VND)` phải **ném ngoại lệ**, không được lặng lẽ làm tròn —
nửa đồng không tồn tại, và một hệ thống làm tròn lén ở đây sẽ làm tròn lén ở mọi nơi khác.

### Chia tiền: thuật toán phân bổ

```
100.00 / 3  →  33.33 mỗi người  →  ×3 = 99.99   ❌ mất 1 cent
```

Cách đúng: chia lấy nguyên, rồi **phát phần dư** cho các phần đầu, mỗi phần 1 đơn vị.

```
10000 cent / 3 = 3333 dư 1  →  [3334, 3333, 3333]  →  tổng = 10000 ✅
```

Không xu nào biến mất, không xu nào sinh ra. **Ai nhận phần dư là một quyết định nghiệp vụ** — có
nơi cho người đầu, có nơi cho người trả tiền, có nơi bốc thăm — nhưng nó phải là một *quyết định*,
không phải hệ quả của việc làm tròn.

Cùng nguyên tắc cho chia theo tỉ lệ: chia xuống, rồi phát phần dư.

### Làm tròn là một quyết định nghiệp vụ

Cùng `5.0025 JOD`, ba chế độ cho ba con số:

| Chế độ | Kết quả |
|---|---|
| `HALF_UP` (làm tròn nửa lên — cái ta học ở trường) | 5.003 |
| `HALF_EVEN` (làm tròn ngân hàng) | 5.002 |
| `DOWN` (cắt cụt) | 5.002 |

Không có cái nào "đúng" — cái đúng là cái luật thuế/kế toán nước đó quy định. Nên phép nhân phải
**bắt buộc nhận** chế độ làm tròn: không có giá trị mặc định nào an toàn, và để mặc định là để
người sau đoán.

### Thứ nguyên: tiền × tiền là vô nghĩa

```
tiền × số   = tiền     (100 USD × 0.1 = 10 USD thuế)
tiền ÷ tiền = TỈ LỆ    (30 USD / 100 USD = 0.3)
tiền × tiền = KHÔNG CÓ NGHĨA — "đô-la bình phương" không tồn tại
```

`std::chrono` dùng đúng nguyên tắc này: `seconds * seconds` cũng không biên dịch. Đây là lý do
`nhan()` nhận một **hệ số**, không nhận một `Tien`.

### `Tien` và `DonGia` là hai kiểu khác nhau

Giá điện `1.234,56 đ/kWh` **không phải tiền** — nó là đơn giá, và nó nhỏ hơn đơn vị tiền nhỏ nhất.
Quy tắc: **số nguyên đơn vị nhỏ cho SỐ TIỀN, kiểu thập phân cho ĐƠN GIÁ và TỈ LỆ**, và chỉ quy về
tiền ở bước cuối.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Cộng / trừ (số nguyên) | **O(1)** | O(1) |
| Cộng / trừ (`BigDecimal`/`Decimal`) | O(số chữ số) | O(số chữ số) |
| `chiaDeu(n)` / `chiaTheo` | O(n) — một lượt chia, một lượt phát dư | O(n) |
| Đọc từ chuỗi | O(độ dài chuỗi) | O(1) |

Điểm cốt lõi không phải tốc độ mà là **độ chính xác**: số nguyên cho sai số đúng bằng 0 ở mọi kích
thước, còn số thực cho sai số tích luỹ tăng theo số phép tính — và đó là thứ duy nhất quan trọng
với tiền.

Ghi chú tràn số: `long`/`long long` chứa ~9,2 tỉ tỉ — thừa cho mọi doanh nghiệp, nhưng **phép nhân
trong `chiaTheo` có thể tràn trước đó rất lâu**. `int` của Python không tràn nên không có vấn đề này.

## 4. Lời giải

- [`MoneyDemo.java`](MoneyDemo.java) — `record Tien(long donViNho, TienTe tienTe)`, phân bổ không
  mất xu, ba chế độ làm tròn. Phần 7 đo cái bẫy Java ít ai biết cho tới lúc bị:
  **`BigDecimal("2.0").equals(BigDecimal("2.00"))` là `false`** (equals so cả số chữ số thập phân),
  nên cùng một số tiền nằm ở **hai chỗ** trong `HashSet`.
- [`MoneyDemo.cpp`](MoneyDemo.cpp) — tiền tệ là **tham số kiểu**: `Tien<USD> + Tien<VND>` **không
  biên dịch được**. Java và Python chặn bằng `if` lúc chạy — bug vẫn nằm trong mã nguồn, chỉ là nổ
  muộn; ở đây nó không tồn tại được. Phần 7 chứng minh bằng `static_assert` +
  `CongDuoc<Tien<USD>, Tien<VND>>::value == false`: trình biên dịch **tự xác nhận** rằng đoạn code
  sai kia là không thể viết ra. Phần 6 lo tràn số nguyên có dấu — ở C++ đó là *hành vi không xác
  định*, nên phải chặn **trước** khi nhân.
- [`money_demo.py`](money_demo.py) — cái bẫy tinh vi nhất trong ba bản: **`Decimal(0.1)` không bằng
  `Decimal("0.1")`**. Hai dòng khác nhau đúng hai dấu nháy, và một trong hai đã hỏng trước khi làm
  gì. Phần 6 đo một thứ hầu như ai cũng đoán sai: **`round(2.5) == 2`** trong Python — `round` mặc
  định làm tròn kiểu ngân hàng.

**Khác biệt giữa ba ngôn ngữ:**

| | Chặn cộng khác tiền tệ | Tràn số | Bẫy riêng |
|---|---|---|---|
| Java | ngoại lệ **lúc chạy** | `long` tràn âm thầm nhưng **xác định** | `BigDecimal.equals` so cả `scale` |
| C++ | ✅ **lỗi biên dịch** (tham số kiểu) | tràn có dấu = **hành vi không xác định** | phải tự chặn tràn trước khi nhân |
| Python | ngoại lệ lúc chạy | `int` **không tràn** | `Decimal(float)` hỏng từ đầu; `round` là half-even |

Một điểm Python **an toàn hơn** Java: `Decimal("2.0") == Decimal("2.00")` là `True`, và hash cũng
khớp — cái bẫy `BigDecimal` không tồn tại. Nhưng `Decimal` vẫn có bối cảnh làm tròn toàn cục (mặc
định 28 chữ số có nghĩa), nên phép **chia** vẫn mất chính xác âm thầm. Số nguyên đơn vị nhỏ nhất
không có vấn đề đó ở bất kỳ đâu.

## 5. Thực tế đi làm

**Cạm bẫy #1 — `double`/`float`/`REAL` cho tiền.** Bao gồm cả cột `FLOAT` trong CSDL và trường số
trong JSON. Kiểm tra ngay hôm nay: `SELECT data_type FROM information_schema.columns WHERE
column_name LIKE '%tien%'`. Kiểu đúng là `DECIMAL(19,4)` hoặc `BIGINT` đơn vị nhỏ nhất.

**Cạm bẫy #2 — JSON làm hỏng số tiền trước khi bạn chạm vào.** `json.loads` mặc định parse số thành
`float`, nên `12345678901234.56` đã sai trước khi tới `Decimal`. Cách chữa: `parse_float=Decimal`,
hoặc — tốt hơn — truyền số tiền qua giao thức dưới dạng **chuỗi** hoặc **số nguyên đơn vị nhỏ nhất**.

**Cạm bẫy #3 — hằng số `100` viết cứng.** `soTien / 100` chạy đúng cho USD, sai 100 lần cho VND,
sai 10 lần cho JOD. Hệ số phải tra từ tiền tệ.

**Cạm bẫy #4 — chia rồi làm tròn từng phần.** Mỗi lần làm tròn độc lập là một lần tiền có thể biến
mất hoặc sinh ra. Luôn dùng thuật toán phân bổ có phát phần dư, và luôn có một `assert` rằng tổng
các phần bằng số ban đầu — đó là bất biến rẻ nhất và đáng giá nhất trong hệ thống thanh toán.

**Cạm bẫy #5 — làm tròn mặc định.** `round()`, `Math.round()`, `setScale(2)` không tham số — cả ba
chọn giúp bạn một luật kế toán. Nếu bạn không viết ra được vì sao chế độ đó đúng cho nghiệp vụ này,
thì bạn chưa chọn, bạn chỉ đang nhận.

**Cạm bẫy #6 — tỉ giá làm mất tiền.** Đổi USD → VND → USD không quay lại con số cũ, và điều đó
đúng: hai phép làm tròn xảy ra. Hệ thống phải ghi **số tiền gốc + tỉ giá + số tiền đích** như ba dữ
kiện riêng, không được coi phép đổi là thuận nghịch.

**Cạm bẫy #7 — số âm và phép chia.** `-7 / 3` trong Java/C++ là `-2` (làm tròn về 0), trong Python
là `-3` (làm tròn xuống). Thuật toán phân bổ ở bài này giả định số dương; với hoàn tiền và điều
chỉnh giảm, hãy có test riêng cho số âm.

**Biến thể phỏng vấn thường hỏi:**
- *"Vì sao không dùng `double` cho tiền?"* — Câu mở đầu; trả lời bằng `0.1 + 0.2` là đủ. Câu hỏi
  thật nằm ngay sau: *"vậy dùng gì?"* — số nguyên đơn vị nhỏ nhất, hoặc kiểu thập phân.
- *"Chia 100đ cho 3 người thế nào?"* — Câu hỏi lọc rất hiệu quả. Người đã làm hệ thống thanh toán
  sẽ nói ngay tới phần dư và ai nhận nó; người chưa làm sẽ nói `33.33`.
- *"`Money` nên bất biến không?"* — Có, và vì ba lý do của value object ([bài 82](../82-entity-value-object/)):
  nó được chia sẻ, nó hay làm khoá trong `Map`, và nó là chỗ đặt luật.
- *"Lưu tiền xuống CSDL kiểu gì?"* — `BIGINT` đơn vị nhỏ nhất **cộng** một cột mã tiền tệ. Lưu
  `DECIMAL` không có tiền tệ là mất một nửa thông tin, và nó sẽ được cộng nhầm.
- *"Tiền tệ nào cũng 2 chữ số thập phân đúng không?"* — Không: VND 0, JOD 3, và một số tiền tệ lịch
  sử có cả 1. Biết điều này ngay lập tức cho thấy bạn đã đọc chuẩn ISO 4217 chứ không đoán.
- *"Ở C++ vì sao dùng tham số kiểu cho tiền tệ?"* — Vì nó biến một ngoại lệ lúc chạy thành một lỗi
  biên dịch, đúng như `std::chrono` làm với đơn vị thời gian. Đổi lại: không dùng được khi tiền tệ
  chỉ biết lúc chạy — lúc đó vẫn cần bản có trường tiền tệ.

## 6. Self-check

```bash
cd 04-competitive/90-money-currency
javac MoneyDemo.java && java MoneyDemo        # in "OK"
g++ -std=c++17 -o sol MoneyDemo.cpp && ./sol  # in "OK"
python money_demo.py                          # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
