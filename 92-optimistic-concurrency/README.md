# 92 — Optimistic Concurrency: An sửa tên, Bình sửa hạn mức, và tên biến mất

Bài 85 cứu được *lost update* **trong một use case** bằng bản đồ định danh. Bài này là cùng con bug
đó khi hai người ngồi hai máy: hai transaction, hai tiến trình, và **không bản đồ nào nhìn thấy cả
hai**.

## 1. Đề bài

Hai nhân viên mở cùng một hồ sơ khách hàng lúc 9h00. An sửa **tên**, Bình sửa **hạn mức**.

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Không có phiên bản → thay đổi của An **mất** | tên vẫn là "Nguyễn Văn A" |
| 2 | Có phiên bản → lần ghi thứ hai bị chặn | `capNhat` trả về **0 dòng** |
| 3 | Thử lại **"mù"** vẫn mất y hệt | tên của An mất lần nữa |
| 4 | Đọc lại + áp lại ý định → **cả hai** thay đổi còn | tên "An" **và** hạn mức 50tr, phiên bản 3 |

**Ràng buộc:** một số hiệu phiên bản cho **cả aggregate**, không phải cho từng field; lần ghi phải
tăng phiên bản **cùng lúc** với ghi dữ liệu.

**Input/Output mẫu:**
```
KH-01 { ten: "Nguyễn Văn A", hanMuc: 10tr, phienBan: 1 }

An   đọc pb=1, ghi tên   -> 1 dòng, pb -> 2
Bình đọc pb=1, ghi hạn mức -> 0 DÒNG          <- đụng độ, phát hiện được

Bình đọc LẠI (pb=2), áp lại ý định, ghi -> 1 dòng, pb -> 3
=> { ten: "Nguyễn Văn An", hanMuc: 50tr, pb: 3 }   cả hai đều còn
```

## 2. Ý tưởng

### Cơ chế: toàn bộ nằm trong một câu `UPDATE`

```sql
UPDATE khach_hang SET ten=?, han_muc=?, phien_ban = phien_ban + 1
WHERE ma = ? AND phien_ban = ?
```

Không khoá gì cả. CSDL trả về **số dòng bị ảnh hưởng**, và `0` là câu trả lời *"có người đã sửa
trước bạn"*. Đây là toàn bộ cơ chế — không có phần nào phức tạp hơn.

### Vì sao thử lại "mù" vẫn sai

Phản xạ đầu tiên khi gặp `0 dòng`: đọc lại **phiên bản** rồi ghi lại. Sai — vì dữ liệu vẫn là bản
cũ, chỉ có số hiệu là mới. Kết quả y hệt phần 1, chỉ chậm hơn vài mili-giây.

> Số hiệu phiên bản **không tự sửa gì**. Nó chỉ nói cho bạn biết phải đọc lại.

### Ba bước, luôn luôn

```
ĐỌC LẠI dữ liệu mới nhất  →  ÁP LẠI Ý ĐỊNH  →  GHI CÓ KIỂM PHIÊN BẢN
```

Điểm phân biệt phần 3 với phần 4 là **"ý định" khác với "dữ liệu đã đọc"**. Ý định của Bình là
*"đặt hạn mức = 50 triệu"*, không phải *"ghi lại toàn bộ bản ghi mà tôi đã đọc lúc 9h00"*.

### Không phải ý định nào cũng áp lại được

| Ý định | Thử lại tự động? |
|---|---|
| "đặt hạn mức = 50 triệu" | ✅ tuyệt đối ([bài 91](../91-idempotency/) phần 6) |
| "tăng hạn mức thêm 10%" | ✅ tính trên bản **mới** đọc |
| "duyệt **vì** hạn mức < 20tr" | ❌ điều kiện duyệt đã dựa trên số cũ |

Trường hợp thứ ba phải hỏi lại người dùng: *"dữ liệu đã thay đổi, đây là bản mới, bạn có còn muốn
duyệt không?"*. Tự động thử lại ở đây là **ra một quyết định nghiệp vụ hộ con người**.

### Phiên bản đặt ở đâu

| Cách đặt | Hậu quả |
|---|---|
| Mỗi field một phiên bản | Không đụng độ khi sửa hai field khác nhau — nhưng **phá bất biến**: hai người có thể cùng nhau tạo ra trạng thái vi phạm mà không ai vi phạm riêng lẻ |
| Phiên bản toàn cục | Mọi người đụng độ với mọi người |
| **Một phiên bản trên aggregate root** | ✅ đơn vị nhất quán = đơn vị đụng độ |

Nối tiếp [bài 83](../83-aggregate-boundary/) phần 5: **aggregate càng to, đụng độ giả càng nhiều.**
Khoá lạc quan không cứu được ranh giới vẽ sai — nó làm hậu quả lộ ra sớm hơn.

### Lạc quan hay bi quan

|  | **Lạc quan** (phiên bản) | **Bi quan** (`SELECT FOR UPDATE`) |
|---|---|---|
| Giả định | đụng độ **hiếm** | đụng độ **thường** |
| Chi phí | 0 khi không đụng; thử lại khi đụng | giữ khoá suốt transaction |
| Rủi ro | thử lại nhiều lần, đói tài nguyên | deadlock, chờ, nghẽn cổ chai |
| Hợp với | web, API, người dùng sửa hồ sơ | trừ kho, cấp số phiếu, hàng đợi |

Mặc định **lạc quan**. Chỉ chuyển sang bi quan khi **đo được** rằng tỉ lệ đụng độ cao tới mức thử
lại tốn hơn chờ — và trước đó hãy xem lại ranh giới aggregate, vì đụng độ cao thường là triệu chứng
của ranh giới quá to chứ không phải của nghiệp vụ.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Ghi có kiểm phiên bản | O(1) — thêm một điều kiện vào `WHERE` | O(1) — một cột `BIGINT` |
| Đường thuận lợi (không đụng độ) | **1 lần ghi**, 0 khoá | O(1) |
| Đường đụng độ | **k lần** đọc-ghi, k = số lần thử lại | O(1) |
| Khoá bi quan | 1 lần ghi + **thời gian giữ khoá** | O(số khoá đang giữ) |

Điều đáng nhớ: chi phí lạc quan là **0 khi không có đụng độ** — đó là toàn bộ lý do nó tồn tại.
Nhưng số lần thử lại tăng theo tỉ lệ đụng độ, và ở tỉ lệ cao nó vượt qua chi phí chờ khoá. Đó là
ngưỡng phải **đo**, không phải đoán.

## 4. Lời giải

- [`ConcurrencyDemo.java`](ConcurrencyDemo.java) — phần 7 đặt cạnh nhau hai thứ ít ai nhận ra là
  một: `AtomicLong.compareAndSet(kỳ vọng, mới)` làm **đúng ba việc** như
  `UPDATE ... WHERE phien_ban = ?`. Nhìn ra điều đó giải thích được câu hay bị hỏi — *"vì sao phải
  có vòng lặp?"* — vì thất bại không phải lỗi, nó là **thông tin**, và thông tin đó chỉ dùng được
  nếu bạn quay lại đọc.
- [`ConcurrencyDemo.cpp`](ConcurrencyDemo.cpp) — C++ nói ra điều Java chỉ gợi ý: số hiệu phiên bản
  là lời giải cho một bài toán có tên riêng — **ABA**. `compare_exchange` chỉ so *giá trị*, nên nếu
  giá trị đi A → B → A thì phép so vẫn khớp và bạn ghi đè lên hai thay đổi mà không biết. File cho
  nổ ABA rồi chữa bằng `{giá trị, phiên bản}` — **đúng cùng một cách chữa** với CSDL.
- [`concurrency_demo.py`](concurrency_demo.py) — cách phá hỏng toàn bộ cơ chế mà chỉ Python mới
  có, và nó chỉ là một dòng: `doc()` trả về **chính object** trong kho. Sửa object đọc ra là đã sửa
  thẳng "CSDL" mà không có lệnh ghi nào, **và** số hiệu phiên bản của bản đọc luôn khớp — nên phép
  kiểm đụng độ không bao giờ phát hiện được gì. Một biện pháp an toàn đã hỏng mà mọi test của nó
  vẫn xanh.

**Khác biệt giữa ba ngôn ngữ:**

| | Điểm nhấn riêng | Bẫy riêng |
|---|---|---|
| Java | `compareAndSet` = cùng thuật toán ở mức CPU | — |
| C++ | **ABA**: CAS khớp giá trị dù trạng thái đã đổi hai lần | với con trỏ, ABA còn ghi vào object khác hoàn toàn |
| Python | `@dataclass(frozen=True)` chặn rò rỉ khả biến | `doc()` trả object thật → đụng độ **không bao giờ** bị phát hiện |

Bẫy phụ ở Python đáng nhớ riêng: phiên bản đi qua JSON thường về dưới dạng **chuỗi**, và `"2" != 2`
im lặng. Kết quả: mọi lần ghi đều bị coi là đụng độ, người dùng không lưu được gì, và không ai hiểu
vì sao. Ép kiểu **ngay tại biên** ([bài 76](../76-fail-fast/), [78](../78-dto-mapping/)).

## 5. Thực tế đi làm

**Cạm bẫy #1 — không có phiên bản, và không ai biết là đang mất dữ liệu.** Đây là mặc định của mọi
`UPDATE ... WHERE id = ?`. Không có lỗi, không có log, và người dùng chỉ phát hiện qua khiếu nại.
Thêm một cột `BIGINT` là toàn bộ chi phí của việc chặn nó.

**Cạm bẫy #2 — thử lại mù.** Bắt được `OptimisticLockException` rồi gọi lại đúng hàm với đúng
object cũ là viết một vòng lặp làm hỏng dữ liệu nhanh hơn. Đọc lại **dữ liệu**, không chỉ phiên bản.

**Cạm bẫy #3 — thử lại vô hạn.** Vòng lặp không có giới hạn lần thử là cách một endpoint bận rộn
làm sập CSDL. Đặt trần (3–5 lần), rồi trả lỗi cho người dùng.

**Cạm bẫy #4 — phiên bản trên từng field "cho đỡ đụng độ".** Nghe rất hợp lý và nó phá bất biến của
aggregate. Nếu đụng độ nhiều tới mức phải làm thế, vấn đề là ranh giới aggregate, không phải cơ chế
khoá.

**Cạm bẫy #5 — quên tăng phiên bản trong một đường ghi nào đó.** Một job nền cập nhật bằng SQL thô
mà không tăng `phien_ban` là đủ để cơ chế mất tác dụng ở đúng chỗ nguy hiểm nhất. Tăng phiên bản
phải nằm **trong cùng câu lệnh ghi**, không phải một dòng riêng ai đó có thể quên.

**Cạm bẫy #6 — báo lỗi kỹ thuật cho người dùng.** *"OptimisticLockException"* không nói được gì.
Thông báo đúng là *"Bạn Bình vừa sửa hồ sơ này lúc 9:04. Đây là bản mới nhất — bạn muốn áp lại thay
đổi của mình không?"* — và điều đó chỉ viết được nếu tầng ứng dụng biết **ý định** của người dùng
là gì.

**Cạm bẫy #7 — dùng `updated_at` làm phiên bản.** Hai lần ghi trong cùng một mili-giây có cùng dấu
thời gian, và đồng hồ có thể chạy lùi ([bài 67](../67-clock-injection/)). Bộ đếm **chỉ tăng** là
thứ duy nhất đúng — đó cũng chính là bài học ABA.

**Biến thể phỏng vấn thường hỏi:**
- *"Khoá lạc quan hoạt động thế nào?"* — Một câu `UPDATE` có thêm `AND phien_ban = ?`, và số dòng
  bị ảnh hưởng là câu trả lời. Nói được rằng nó **không dùng khoá nào cả** là điểm cộng.
- *"Lạc quan hay bi quan?"* — Bảng ở phần 2, cộng câu chốt: mặc định lạc quan, chuyển khi **đo
  được** tỉ lệ đụng độ cao.
- *"Bắt được lỗi đụng độ rồi làm gì?"* — Ba khả năng theo bản chất ý định: thử lại tự động (ý định
  tuyệt đối), gộp (hai field không giao nhau và bất biến cho phép), hoặc hỏi người dùng. Trả lời
  "thử lại" cho mọi trường hợp là câu trả lời của người chưa gặp phần 6.
- *"Vì sao dùng bộ đếm mà không dùng dấu thời gian?"* — Độ phân giải và đồng hồ chạy lùi. Câu trả
  lời sâu hơn là ABA: bộ đếm chỉ tăng thì trạng thái không bao giờ "trông giống như cũ".
- *"Đặt phiên bản ở cấp nào?"* — Aggregate root. Và biết vì sao (đơn vị nhất quán = đơn vị đụng độ)
  quan trọng hơn biết câu trả lời.

## 6. Self-check

```bash
cd 04-competitive/92-optimistic-concurrency
javac ConcurrencyDemo.java && java ConcurrencyDemo        # in "OK"
g++ -std=c++17 -o sol ConcurrencyDemo.cpp && ./sol        # in "OK"
python concurrency_demo.py                                # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
