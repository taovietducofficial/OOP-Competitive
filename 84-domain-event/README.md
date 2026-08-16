# 84 — Domain Event: khách nhận email về một đơn hàng không tồn tại

Bài 83 kết luận *"một transaction sửa đúng một aggregate"* rồi để lại một câu hỏi treo: **vậy hai
aggregate nói chuyện với nhau bằng gì?** Câu trả lời là sự kiện miền — thứ **ghi lại chuyện đã xảy
ra**, không ra lệnh cho ai. Và nó có đúng một cách làm sai, xuất hiện ở gần như mọi dự án lần đầu
dùng nó.

## 1. Đề bài

Cho quy trình đơn hàng (tạo → thanh toán → giao / huỷ), hãy để aggregate **ghi lại** những gì đã
xảy ra, rồi chứng minh bằng code bốn điều:

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Phát sự kiện **trong** transaction gửi email cho đơn không tồn tại | `soEmailDaGui == 1` sau khi rollback |
| 2 | Ghi trước, phát sau commit thì không email nào lọt ra | `soEmailDaGui == 0` |
| 3 | Sự kiện mang tham chiếu → báo cáo lệch | 120.000 vs 100.000 trên một đơn |
| 4 | Người nghe hỏng **không** làm chuyện đã xảy ra thành chưa xảy ra | trạng thái vẫn `DA_GIAO` |

**Ràng buộc:**
- Aggregate **không được** biết bus tồn tại — không field, không import.
- Sự kiện **bất biến**, tên ở **thì quá khứ**, mang dữ liệu **tại thời điểm xảy ra**.
- Thêm một loại sự kiện mới mà quên xử lý phải bị phát hiện — càng sớm càng tốt.

**Input/Output mẫu:**
```
SAI — phát trong transaction:
  don.giao()  -> phát DonHangDaGiao -> email đã gửi
  lưu CSDL    -> HỎNG -> rollback
  => đơn DH-99 không tồn tại, khách vẫn có email "đơn của bạn đã giao"

ĐÚNG — ghi rồi phát sau commit:
  don.giao()          -> ghi sự kiện vào aggregate
  lưu CSDL            -> HỎNG -> rollback
  => 0 email, 0 sự kiện rời khỏi tiến trình
```

## 2. Ý tưởng

### Sự kiện ≠ mệnh lệnh

|  | **Mệnh lệnh** `GuiEmailXacNhan` | **Sự kiện** `DonHangDaGiao` |
|---|---|---|
| Thì của tên | mệnh lệnh: "hãy gửi" | quá khứ: "đã giao" |
| Người nhận | đúng **một**, biết trước | **không biết**, ai nghe cũng được |
| Từ chối được? | có — "email sai định dạng" | **không** — chuyện xảy ra rồi |
| Ai quyết định? | người gửi | không ai; nó là **sự thật** |

Vì sao điều này quan trọng: nếu `DonHang` phát ra `GuiEmailXacNhan`, thì miền nghiệp vụ vừa quyết
định hộ rằng *hệ quả của việc giao hàng là gửi email*. Ngày mai thêm SMS, thêm tích điểm, thêm ghi
sổ kế toán — mỗi lần lại sửa `DonHang`. Với `DonHangDaGiao`, `DonHang` không biết ai nghe, và
không bao giờ phải sửa nữa.

### Aggregate GHI sự kiện, không PHÁT

```java
void giao(long luc) {
    if (trangThai != DA_THANH_TOAN) throw new IllegalStateException(...);
    trangThai = DA_GIAO;
    suKienChuaPhat.add(new DonHangDaGiao(ma, tongTien, luc));   // GHI, không gọi bus
}
```

Aggregate không có field `Bus`, không import gì thuộc hạ tầng. Hai hệ quả: nó test được mà không
cần bus/hàng đợi/mạng, và **thời điểm phát** do tầng ứng dụng quyết định — đúng chỗ cần quyết định.

### Con bug: phát trong transaction

```
don.giao()   -> phát ngay -> email bay đi
lưu CSDL     -> hỏng      -> rollback
```

Đơn hàng không tồn tại, email đã gửi, và **không có cách nào thu về**. Bug này chỉ xảy ra khi hệ
thống có lỗi — nghĩa là đúng lúc bạn ít muốn nó nhất, và đúng lúc test không chạy tới.

Thứ tự đúng chỉ có một:

```
BẮT ĐẦU transaction → đổi aggregate → LƯU → COMMIT → rồi mới phát sự kiện
```

Trong hệ thật, "phát sau commit" hay được làm bằng **outbox**: ghi sự kiện vào một bảng trong
*cùng* transaction, rồi một tiến trình riêng đọc bảng đó và phát đi. Đổi lại, sự kiện có thể được
phát **hai lần** khi tiến trình đó chết giữa chừng — nên người nghe phải chịu được gọi trùng
([bài 91](../91-idempotency/)).

### Sự kiện mang DỮ LIỆU, không mang tham chiếu

```java
record DonHangDaGiao(String maDon, long tongTienLucGiao, long luc) { }
//                                 ^^^^^^^^^^^^^^^^^^^^ ảnh chụp, không phải con trỏ
```

Nếu sự kiện chỉ mang `maDon` và người nghe tự đi tra tổng tiền, thì nó tra được **giá hiện tại**,
không phải giá lúc giao. Kế toán chỉnh đơn sau khi giao là chuyện rất thường — và báo cáo doanh
thu lệch ngay.

> **Quy tắc:** nếu người nghe phải tra CSDL để *hiểu* sự kiện, thì sự kiện đó thiếu thông tin.

### Người nghe hỏng không rút lại được sự thật

Đây là khác biệt cốt lõi với mệnh lệnh: mệnh lệnh hỏng thì huỷ được cả việc. Sự kiện hỏng thì chỉ
có **hệ quả** hỏng, còn chuyện đã xảy ra thì vẫn xảy ra rồi. Cách chữa là **thử lại người nghe đó**
— không phải rollback aggregate. Và một người nghe hỏng không được chặn những người nghe khác.

### Cách nó giải bài toán của bài 83

Giao hàng xong cần cộng điểm thưởng cho khách — một aggregate khác:

```
SAI : don.giao(); khachHang.congDiem();          <- hai aggregate, một transaction
ĐÚNG: don.giao() ghi DonHangDaGiao -> commit
      -> người nghe tải KhachHang, cộng điểm trong transaction THỨ HAI
```

**Cái giá:** có một khoảnh khắc đơn đã giao mà điểm chưa cộng — *nhất quán cuối*.
**Cái được:** hai aggregate không khoá lẫn nhau, và thêm hệ quả mới không phải sửa `DonHang`.

Nếu bước sau hỏng và phải quay lại bước trước, đó là [saga (bài 97)](../97-saga/).

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Ghi một sự kiện vào aggregate | O(1) | O(số sự kiện trong một transaction) — nhỏ, có chặn |
| Phát một sự kiện | O(số người nghe của loại đó) | O(1) |
| Tra bảng người nghe | O(1) — băm theo kiểu | O(số loại × số người nghe) |
| `std::visit` / `switch` trên sealed | O(1) — nhảy bảng, quyết định lúc biên dịch | O(1) |

Chi phí thật của sự kiện miền không nằm ở đây mà ở **độ trễ nhất quán**: giữa lúc commit và lúc
người nghe chạy xong, hệ thống ở trạng thái "đã giao nhưng chưa cộng điểm". Đó là cái giá phải
hỏi nghiệp vụ trước khi chọn, không phải cái giá tính bằng O lớn.

## 4. Lời giải

- [`EventDemo.java`](EventDemo.java) — `sealed interface SuKienMien permits ...` + `switch` **không
  có `default`**. Thêm một loại sự kiện mà quên nhánh xử lý là *lỗi biên dịch*. `getPermittedSubclasses()`
  còn cho phép kiểm luật đặt tên thì quá khứ bằng máy.
- [`EventDemo.cpp`](EventDemo.cpp) — `std::variant` + `std::visit` đạt cùng mức vét cạn, **không cần
  lớp cha, không `virtual`, không cấp phát động**. Phần 5 nói thêm điều hai ngôn ngữ kia giấu đi:
  nếu sự kiện giữ `DonHang*` thay vì giữ giá trị, thì aggregate bị huỷ trước khi người nghe chạy
  — rất dễ xảy ra vì người nghe chạy *sau* commit — và đọc con trỏ đó là hành vi không xác định.
- [`event_demo.py`](event_demo.py) — cái bẫy tinh vi nhất trong ba bản: `match` **trông y hệt**
  `switch` của Java nhưng không khớp nhánh nào thì lặng lẽ trả `None`. Một nhánh nghiệp vụ biến
  mất không dấu vết. File dựng hai **bài test kiến trúc** bù lại: (1) mọi sự kiện phải ở thì quá
  khứ *và* `frozen=True`; (2) liệt kê loại sự kiện chưa có người nghe nào.

**Khác biệt giữa ba ngôn ngữ:**

| | Chặn "quên xử lý loại mới" | Chặn sửa sự kiện | Kiểm luật đặt tên bằng máy |
|---|---|---|---|
| Java | ✅ `sealed` + switch không `default` — **biên dịch** | `record` → không setter | ✅ `getPermittedSubclasses()` |
| C++ | ✅ `std::visit` không lambda bắt-tất-cả — **biên dịch** | struct copy theo giá trị | ❌ không có reflection |
| Python | ❌ `match` im lặng — phải tự viết `case _: raise` | `frozen=True` → `FrozenInstanceError` | ✅ `__name__` + `__dataclass_params__` |

Điểm chung của hai cột đầu: **đừng viết nhánh bắt-tất-cả**. `default` trong Java và
`[](auto&&){...}` trong C++ đều biến lỗi biên dịch thành bug lúc chạy — vứt đi đúng thứ đáng giá
nhất của `sealed`/`variant`.

## 5. Thực tế đi làm

**Cạm bẫy #1 — phát sự kiện trong transaction.** Cạm bẫy số một, và nó *chạy đúng* trong mọi test
vì test không rollback. Dấu hiệu nhận biết trong code: aggregate có field `eventPublisher`,
`applicationEventPublisher`, `bus`. Nếu aggregate biết bus tồn tại thì bug này đã có sẵn ở đó.

**Cạm bẫy #2 — đặt tên sự kiện ở thì mệnh lệnh.** `SendConfirmationEmail`, `UpdateInventory`,
`NotifyCustomer` — ba cái tên nói rằng miền nghiệp vụ đang ra lệnh cho hạ tầng. Đổi thành
`OrderShipped` là đổi cả kiến trúc, không chỉ đổi chữ ([bài 81](../81-ubiquitous-language/)).

**Cạm bẫy #3 — sự kiện mang object thay vì mang dữ liệu.** `new OrderShipped(order)` tiện và sai:
người nghe đọc `order.getTotal()` lúc nào cũng ra giá *hiện tại*. Sự kiện là ảnh chụp — nó phải
sao dữ liệu, và ở C++ thì đây còn là chuyện an toàn bộ nhớ chứ không chỉ là con số đúng/sai.

**Cạm bẫy #4 — coi sự kiện như lời gọi hàm đồng bộ.** "Phát sự kiện rồi đọc kết quả ngay" là một
mệnh lệnh đội lốt. Nếu bạn cần kết quả trả về, thứ bạn cần là mệnh lệnh, không phải sự kiện. Dấu
hiệu: sau `bus.phat(...)` có một dòng đọc lại dữ liệu và mong nó đã đổi.

**Cạm bẫy #5 — người nghe không chịu được gọi trùng.** Outbox, hàng đợi, cơ chế thử lại — cả ba
đều có thể giao sự kiện hai lần. Nếu người nghe cộng điểm thưởng mà không kiểm tra đã cộng chưa,
khách được cộng đôi. Đây là lý do [bài 91](../91-idempotency/) tồn tại và vì sao nó đi ngay sau
loạt bài này.

**Cạm bẫy #6 — thứ tự người nghe trở thành ngầm định.** "Người nghe A phải chạy trước B" nghĩa là
bạn đang dùng bus để làm việc của một quy trình. Nếu thứ tự thật sự quan trọng, hãy viết nó ra
thành một saga có tên ([bài 97](../97-saga/)), đừng dựa vào thứ tự đăng ký.

**Cạm bẫy #7 — sự kiện phình thành log.** Không phải mọi thay đổi đều là sự kiện miền. `FieldChanged`,
`EntityUpdated` là log kỹ thuật, không mang nghĩa nghiệp vụ, và không ai nghe được chúng một cách
có ý nghĩa. Phép thử: **người làm nghiệp vụ có gọi tên chuyện này không?** Không → nó không phải
sự kiện miền.

**Biến thể phỏng vấn thường hỏi:**
- *"Sự kiện khác mệnh lệnh chỗ nào?"* — Bốn điểm ở bảng phần 2. Điểm quan trọng nhất: sự kiện
  **không từ chối được**, vì nó nói về quá khứ.
- *"Phát sự kiện trước hay sau khi commit?"* — Sau, luôn luôn. Và câu hỏi tiếp theo người phỏng vấn
  sẽ hỏi là *"vậy nếu tiến trình chết giữa commit và phát thì sao?"* — trả lời: outbox, cộng với
  người nghe chịu được gọi trùng.
- *"Sự kiện nên mang bao nhiêu dữ liệu?"* — Đủ để mọi người nghe làm việc mà không phải tra lại.
  Không nhiều hơn — sự kiện là hợp đồng công khai, thêm field vào nó là chuyện không rút lại được
  ([bài 79](../79-contract-evolution/)).
- *"Người nghe ném ngoại lệ thì có rollback aggregate không?"* — Không. Chuyện đã xảy ra rồi. Thử
  lại người nghe; nếu thật sự cần quay ngược, đó là hành động bù trừ, và bạn đang cần saga.
- *"Sự kiện miền và sự kiện tích hợp (integration event) khác nhau ra sao?"* — Sự kiện miền ở
  trong tiến trình, dùng ngôn ngữ của miền, đổi thoải mái. Sự kiện tích hợp đi ra ngoài ranh giới
  dịch vụ, là hợp đồng với đội khác, và phải phiên bản hoá. Đừng phát thẳng sự kiện miền ra ngoài
  — dịch nó qua một lớp ranh giới ([bài 93](../93-bounded-context/), [94](../94-anti-corruption-layer/)).

## 6. Self-check

```bash
cd 04-competitive/84-domain-event
javac EventDemo.java && java EventDemo        # in "OK"
g++ -std=c++17 -o sol EventDemo.cpp && ./sol  # in "OK"
python event_demo.py                          # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
