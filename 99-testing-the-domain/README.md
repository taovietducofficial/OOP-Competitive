# 99 — Testing the Domain: nếu test miền cần một framework, thì thứ bạn test không phải miền

Cả tầng này đã dựng một mô hình miền không phụ thuộc hạ tầng ([bài 98](../98-hexagonal/)). Bài này
là phần thu hoạch: khi miền không biết CSDL, **test của nó chỉ còn là hàm + assert** — đúng như mọi
file self-check trong series.

Đó không phải "lười không dùng JUnit/pytest". Nó là **bằng chứng** rằng mô hình đã tách sạch.

## 1. Đề bài

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Test bám vào **cách làm** → refactor không đổi hành vi vẫn làm đỏ | `soLuotGoi` 3 → 1, hành vi **y hệt** |
| 2 | Bộ dựng dữ liệu → test đọc lên là biết nó kiểm gì | 3 dòng thay vì 12 |
| 3 | Bất biến → **1.000** ca sinh ngẫu nhiên, 0 dòng gõ tay | tổng các phần = tổng ban đầu |
| 4 | Bản giả **nói dối** → bug đi thẳng ra production | giả nhận `-5`, thật ném ngoại lệ |

**Ràng buộc:** không framework, không mock, không CSDL; hạt giống ngẫu nhiên phải cố định.

**Input/Output mẫu:**
```
themDong × 3   -> kho gọi 3 lần   ┐ hành vi ngoài GIỐNG HỆT:
themNhieuDong  -> kho gọi 1 lần   ┘ cùng tổng tiền, cùng số dòng, cùng số lượng giữ chỗ

=> test "kho phải được gọi đúng 3 lần"  ĐỎ   ← tín hiệu GIẢ
=> test "tổng tiền == 1.700.000"        XANH ← tín hiệu THẬT
```

## 2. Ý tưởng

### Test hành vi, không test cách làm

| | Hỏng khi nào | Tín hiệu |
|---|---|---|
| Test **hành vi** (kết quả, trạng thái) | nghiệp vụ sai | **thật** |
| Test **cách làm** (đã gọi hàm nào mấy lần) | code đổi | **giả** |

Bộ test đầy tín hiệu giả là bộ test **bị tắt sau ba tháng**. Quy tắc: đừng kiểm *"đã gọi hàm nào bao
nhiêu lần"*; kiểm *"kết quả có đúng không, trạng thái có đúng không"*.

Đây chính là thứ mock framework khuyến khích — `verify(kho, times(3))`, `assert_called_once_with`.
Ngoại lệ hợp lệ **duy nhất**: khi việc gọi *chính là* hành vi cần kiểm — *"đã gửi đúng một email cho
khách"* ([bài 84](../84-domain-event/) phần 4). Lúc đó số lượt gọi là nghiệp vụ, không phải chi tiết
cài đặt.

### Bộ dựng dữ liệu: nói ra thứ test QUAN TÂM

```java
DonHang ganHanMuc = new DonHangBuilder()
        .voiDong("máy chủ", 49_000_000L, 1)   // <- chi tiết DUY NHẤT quan trọng
        .dung(kho);
```

Bộ dựng có giá trị mặc định cho **mọi** thứ; test chỉ nói ra thứ nó quan tâm. Không có nó, mỗi test
mở đầu bằng 10 dòng nhiễu và người đọc không thấy được **điều gì** trong dữ liệu là quan trọng.

### Bất biến: kiểm với nghìn đầu vào, không phải ba

Test theo **ví dụ** trả lời *"với đầu vào này thì sao"*. Test theo **bất biến** trả lời *"với mọi
đầu vào thì điều gì luôn đúng"* — và nó bắt được những ca không ai nghĩ ra để viết ví dụ.

```
∀ tổng, ∀ n:  sum(chiaDeu(n)) == tổng
              len(chiaDeu(n)) == n
              max - min <= 1
```

**Hạt giống cố định là bắt buộc.** Một test đỏ ngẫu nhiên mà không tái hiện được thì vô dụng — và
tệ hơn, nó sẽ bị đánh dấu "bỏ qua".

### Bản giả nói dối

Bản giả nhận số lượng `-5` và test xanh; bản thật ném ngoại lệ và bug đi thẳng ra production. Cách
chữa là **bộ kiểm tra hợp đồng** ([bài 68](../68-in-memory-fake/)): một bộ test viết **một lần**,
chạy trên **mọi** cài đặt của cổng. Bản giả nào không qua được thì không được dùng.

### Cái gì KHÔNG nên test

- Getter/setter thuần — không kiểm được luật nào.
- Thư viện và framework — `HashMap`/`dict` đã được test rồi.
- Cài đặt riêng tư — nếu phải đổi `private` thành `public` để test, test đó đang bám vào cách làm.
- Bộ nối hạ tầng bằng test miền — chúng cần test tích hợp riêng, ít và chậm.

Ngược lại, thứ **đáng test nhất** là mọi chỗ có `if` mang nghĩa nghiệp vụ: hạn mức, chuyển trạng
thái ([bài 89](../89-state-pattern/)), luật giá, luật chia tiền.

### Đặt tên test như một câu nghiệp vụ

```
TỆ : "test1", "testThemDong", "kiểm tra hàm themDong"
TỐT: "đơn 49 triệu + 2 triệu -> vượt hạn mức 50 triệu"
```

Tên tốt nói **điều kiện** và **kết quả mong đợi**, bằng từ ngữ nghiệp vụ
([bài 81](../81-ubiquitous-language/)). Khi test đỏ lúc 2 giờ sáng, dòng chữ đó là toàn bộ thứ người
trực có.

## 3. Độ phức tạp

| | Thời gian mỗi lần chạy |
|---|---|
| Test miền (hàm + assert) | **mili-giây**, 0 khởi động |
| Test miền qua JUnit/pytest | mili-giây + vài trăm ms khởi động |
| Test qua `@SpringBootTest` / DB thật | **giây** — và hỏng vì lý do không liên quan |
| `static_assert` (C++) | **0** — chạy lúc biên dịch |
| 1.000 ca bất biến | mili-giây (chỉ là một vòng lặp) |

Con số quyết định là **thời gian phản hồi**: bộ test chạy trong mili-giây được chạy sau mỗi lần lưu
file; bộ test cần CSDL được chạy trước khi về nhà. Sự khác nhau đó quyết định bug bị bắt sau 5 giây
hay sau 5 giờ.

## 4. Lời giải

- [`DomainTestDemo.java`](DomainTestDemo.java) — đo trực tiếp tín hiệu giả: gộp 3 lượt gọi kho thành
  1 làm test-theo-cách-làm đỏ trong khi cả ba test-theo-hành-vi vẫn xanh. Kèm bộ dựng dữ liệu, 1.000
  ca bất biến, và bộ kiểm tra hợp đồng chạy trên hai cài đặt.
- [`DomainTestDemo.cpp`](DomainTestDemo.cpp) — C++ có một loại test hai ngôn ngữ kia không có: **test
  chạy lúc biên dịch**. `constexpr` + `static_assert` kiểm bất biến chia tiền **trước khi chương
  trình tồn tại**: 0 mili-giây lúc chạy, không thể bị "bỏ qua", và code sai thì **không tạo ra được
  file thực thi**. File cũng nói rõ giới hạn — phần nào của miền đưa vào đó được, phần nào không.
- [`domain_test_demo.py`](domain_test_demo.py) — nói thẳng một điều về **chính series này**:
  `assert` **biến mất hoàn toàn** dưới `python -O`. File chạy hai tiến trình con để chứng minh, rồi
  vạch ranh giới: `assert` cho **test** và **bất biến nội bộ**; validate dữ liệu ở biên thì tuyệt
  đối không.

**Khác biệt giữa ba ngôn ngữ:**

| | Cơ chế test nhanh nhất | Điều phải biết |
|---|---|---|
| Java | hàm + `check` (mili-giây) | — |
| C++ | **`static_assert`** — 0ms, chạy lúc biên dịch | chỉ dùng được cho phép tính thuần (`constexpr`) |
| Python | hàm + `assert` | **`python -O` xoá sạch `assert`** |

Hệ quả cho series này: mọi file self-check `.py` phải chạy **không có `-O`**. Chạy `python -O bai.py`
sẽ in `OK` trong 0 giây mà không kiểm một dòng nào — kiểu "xanh" nguy hiểm nhất tồn tại.

## 5. Thực tế đi làm

**Cạm bẫy #1 — mock mọi thứ.** Test đầy `verify(...)` là test khoá chặt cài đặt: mọi refactor đều
đỏ, nên không ai dám refactor. Dùng **bản giả** (một cài đặt thật, đơn giản) thay cho mock, và kiểm
**trạng thái cuối** thay cho lượt gọi.

**Cạm bẫy #2 — bản giả không khớp bản thật.** Bug ở phần 4, và nó âm thầm. Bộ kiểm tra hợp đồng
chung là cách duy nhất phát hiện.

**Cạm bẫy #3 — test cần CSDL để chạy.** Hệ quả dây chuyền: test chậm → test giòn → không ai chạy →
không ai viết. Nếu test miền cần CSDL, vấn đề nằm ở kiến trúc chứ không ở bộ test
([bài 98](../98-hexagonal/)).

**Cạm bẫy #4 — test ngẫu nhiên không có hạt giống.** Đỏ một lần trong 200 lần chạy, không tái hiện
được, và bị đánh dấu "bỏ qua" trong tuần. Hạt giống cố định, và khi tìm ra ca lỗi thì **thêm nó
thành một test theo ví dụ**.

**Cạm bẫy #5 — dùng `assert` để validate dữ liệu người dùng (Python).** Chạy dưới `-O` là mọi
validate biến mất. Ở biên phải `if ...: raise ValueError(...)`
([bài 76](../76-fail-fast/), [94](../94-anti-corruption-layer/)).

**Cạm bẫy #6 — đo độ phủ thay vì đo giá trị.** 100% độ phủ với toàn test getter là 0 giá trị. Câu
hỏi đúng: *"nếu tôi cố tình làm sai một luật nghiệp vụ, có test nào đỏ không?"* — đó là **kiểm thử
đột biến**, và nó đo đúng thứ độ phủ không đo được.

**Cạm bẫy #7 — test đặt tên theo hàm.** `testThemDong` không nói được điều gì khi nó đỏ. Tên test là
tài liệu duy nhất không bao giờ lỗi thời — vì nó chạy.

**Biến thể phỏng vấn thường hỏi:**
- *"Test đơn vị khác test tích hợp ở đâu?"* — Ranh giới hữu ích nhất không phải "một lớp hay nhiều
  lớp", mà là **có chạm hạ tầng không**. Test miền không chạm gì, chạy mili-giây, chạy sau mỗi lần
  lưu file.
- *"Vì sao không nên mock nhiều?"* — Vì mock khoá cài đặt, và test đỏ mỗi lần refactor sẽ bị tắt.
  Kèm ví dụ gộp 3 lượt gọi thành 1 là đủ.
- *"Fake và mock khác nhau ra sao?"* — Fake là một **cài đặt thật** (đơn giản hơn); mock là một
  object ghi lại lượt gọi. Fake test *hành vi*, mock test *tương tác*.
- *"Property-based testing là gì?"* — Kiểm một **bất biến** với nhiều đầu vào sinh tự động, thay vì
  kiểm ví dụ. Nói được một bất biến cụ thể (tổng các phần = tổng ban đầu) quan trọng hơn nhắc tên
  thư viện.
- *"Bao nhiêu độ phủ là đủ?"* — Sai câu hỏi. Câu đúng: *"những `if` mang nghĩa nghiệp vụ đã được
  phủ chưa"*. Một hệ thống 60% độ phủ nhưng phủ đủ luật tốt hơn 95% phủ toàn getter.

## 6. Self-check

```bash
cd 04-competitive/99-testing-the-domain
javac DomainTestDemo.java && java DomainTestDemo        # in "OK"
g++ -std=c++17 -o sol DomainTestDemo.cpp && ./sol       # in "OK"
python domain_test_demo.py                              # in "OK"  (KHÔNG dùng -O)
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
