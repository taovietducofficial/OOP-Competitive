# 96 — Event Sourcing: "vì sao số dư là 493.000?" — "vì nó bằng 493.000"

Bài 84 dạy sự kiện là thứ **ghi lại chuyện đã xảy ra**. Bài này đi tới kết luận cuối của ý đó: nếu
sự kiện đã ghi đủ mọi chuyện đã xảy ra, thì **trạng thái hiện tại là dữ liệu thừa** — tính lại được
bất cứ lúc nào.

Đổi lại, một luật mới xuất hiện và nó tuyệt đối: **hàm phát lại không được kiểm tra gì, không được
đọc gì bên ngoài.** Vi phạm luật đó thì lịch sử của bạn ngừng tải được — và ở đây không có bản sao
nào khác.

## 1. Đề bài

Tài khoản: mở 1.000.000 → nạp 200.000 → rút 700.000 (phí 1%). Số dư 493.000.

| # | Phải chứng minh | Đo bằng gì |
|---|---|---|
| 1 | Chỉ lưu trạng thái → **0** câu hỏi lịch sử trả lời được | một con số, không có vì sao |
| 2 | Phát lại 4 sự kiện → **cùng** trạng thái, kèm lời giải thích | `soDu == 493.000`, 4 sự kiện |
| 3 | Truy vấn theo thời gian **miễn phí** | phát lại 2 sự kiện đầu → 1.200.000 |
| 4 | Hàm phát lại "bẩn" → cùng lịch sử, **hai** số dư | 293.000 vs 286.000, lệch 7.000 |
| 5 | Ảnh chụp → phát lại **2** thay vì **1.001** sự kiện | gấp hơn 500 lần |

**Ràng buộc:** kiểm tra luật chỉ nằm ở hàm **quyết định**; hàm **áp dụng** chỉ đổi trạng thái.

**Input/Output mẫu:**
```
[DaMoTaiKhoan(1.000.000), DaNap(200.000), DaRut(700.000), DaTinhPhi(7.000, 10‰)]
   phát lại  ->  TaiKhoan{ma="TK-01", soDu=493.000}
   [:2]      ->  soDu = 1.200.000          (số dư trước khi rút)
```

## 2. Ý tưởng

### Hai hàm, hai trách nhiệm — và đây là toàn bộ bài học

| | `quyetDinh()` (rút, nạp…) | `apDung()` (phát lại) |
|---|---|---|
| Kiểm tra luật | **có** | **không bao giờ** |
| Đọc thế giới bên ngoài | được | **không bao giờ** |
| Ném ngoại lệ | được | **không bao giờ** |
| Chạy khi nào | lúc có lệnh mới | **mỗi lần tải aggregate** |

### Con bug: đặt kiểm tra vào hàm phát lại

Hôm nay ngân hàng ban hành hạn mức rút 1.000.000/lần. Ai đó "cho chắc" thêm câu kiểm tra vào hàm
áp dụng. Kết quả: **mọi tài khoản từng rút 3.000.000 năm ngoái đều ngừng tồn tại**.

Đọc lại cho kỹ — đây không phải "một giao dịch bị từ chối". Đây là *cả aggregate không tải được*:
không đọc được số dư, không mở được màn hình, không xử lý được giao dịch mới. Và trong ES **không
có bản sao nào khác** để khôi phục, vì chuỗi sự kiện **là** dữ liệu.

> Sự kiện đã xảy ra thì đã xảy ra. Luật mới chỉ áp cho **quyết định mới**.

### Con bug: hàm phát lại đọc thế giới bên ngoài

```python
def ap_dung(self, e):
    if isinstance(e, DaRut):
        phi = e.so_tien * BIEU_PHI_HIEN_TAI // 1000   # <- một dòng
        self.so_du -= e.so_tien + phi
```

Không có giao dịch nào xảy ra. Chỉ một biến cấu hình đổi, và số dư của **mọi** tài khoản trong hệ
thống vừa đổi theo — 293.000 hôm nay, 286.000 sang năm. Không log, không cảnh báo, và **không có
"trạng thái đúng" nào để đối chiếu**, vì trạng thái được tính từ chính hàm này.

Quy tắc kiểm tra được bằng mắt: **hàm áp dụng chỉ được dùng `this`/`self` và tham số sự kiện.** Thấy
bất kỳ tên nào khác trong đó là thấy một cái bẫy.

### Hệ quả: sự kiện mang KẾT QUẢ, không mang CÔNG THỨC

`DaTinhPhi(soTienPhi=7.000, tiLe=10‰)` — phí đã chốt, không phải "hãy tính 1% của số rút". Đây là
[bài 84](../84-domain-event/) phần 5 với hậu quả nặng hơn hẳn: ở đó sự kiện thiếu dữ liệu làm một
*báo cáo* sai; ở đây nó làm **số dư** sai, trên toàn bộ hệ thống, mỗi lần phát lại.

### Ảnh chụp là BỘ NHỚ ĐỆM, không phải nguồn sự thật

Phát lại 1.001 sự kiện cho mỗi lần đọc là không dùng được. Ảnh chụp = trạng thái tại sự kiện thứ N
+ phần đuôi phát lại từ đó → 2 sự kiện thay vì 1.001.

> Phép thử: **xoá hết ảnh chụp đi thì hệ thống chỉ chậm, không sai.** Nếu xoá ảnh chụp mà mất dữ
> liệu, thì đó không còn là event sourcing nữa.

### Giá phải trả, nói thẳng

- Sự kiện là **hợp đồng vĩnh viễn**. Thêm loại mới thì được; sửa nghĩa loại cũ là **viết lại lịch
  sử**, và phải phiên bản hoá ([bài 79](../79-contract-evolution/)).
- Truy vấn (*"tìm mọi tài khoản số dư < 0"*) **không** làm trên chuỗi sự kiện được → bắt buộc có mô
  hình đọc riêng ([bài 95](../95-cqrs-lite/)). **ES gần như luôn đi kèm CQRS.**
- Xoá dữ liệu cá nhân theo yêu cầu pháp lý là bài toán **khó**, vì bản chất ES là không xoá. Cách
  thực dụng: mã hoá dữ liệu cá nhân và vứt khoá đi.

Vì vậy: ES dùng cho phần mà **lịch sử là nghiệp vụ** — sổ kế toán, kho, hồ sơ y tế, audit. Không
dùng cho bảng cấu hình và danh mục.

## 3. Độ phức tạp

| | Time | Space |
|---|---|---|
| Ghi một sự kiện | **O(1)** — chỉ nối thêm, không `UPDATE` | O(1) |
| Tải aggregate, không ảnh chụp | **O(n)** — n = tổng số sự kiện, tăng mãi | O(1) |
| Tải aggregate, có ảnh chụp mỗi k sự kiện | **O(k)** | O(kích thước ảnh chụp) |
| Truy vấn theo thời gian | O(số sự kiện tới thời điểm đó) | O(1) |
| Truy vấn ngang (*"ai số dư < 0"*) | **không làm được** trên log → cần mô hình đọc | O(kích thước mô hình đọc) |

Dòng thứ hai là lý do ảnh chụp không phải tuỳ chọn: **O(n) với n tăng mãi** nghĩa là hệ thống chậm
dần theo tuổi đời, và nó chậm ở *mọi thao tác đọc*.

Dòng cuối là lý do ES đi kèm CQRS: chuỗi sự kiện trả lời rất tốt câu hỏi *"chuyện gì đã xảy ra với
X"* và hoàn toàn không trả lời được *"những X nào thoả điều kiện Y"*.

## 4. Lời giải

- [`EventSourcingDemo.java`](EventSourcingDemo.java) — `sealed interface SuKien` + `switch` **không
  `default`** trong `apDung`: thêm một loại sự kiện mà quên xử lý ở hàm phát lại là **lỗi biên
  dịch**. Với ES, quên một nhánh nghĩa là tính sai trạng thái của *mọi* bản ghi từng phát ra sự
  kiện đó.
- [`EventSourcingDemo.cpp`](EventSourcingDemo.cpp) — biến luật *"hàm phát lại không được ném"* từ
  quy ước thành **bằng chứng kiểm tra lúc biên dịch**: `apDung` khai báo `noexcept`, và
  `static_assert(noexcept(tk.apDung(e)))` xác nhận điều đó. Ai thêm `throw` vào đó phải bỏ
  `noexcept` — và `static_assert` đổ ngay. Phần 7 còn đo việc chuỗi sự kiện là **một khối bộ nhớ
  liền nhau**, cùng cái giá của `variant` (to bằng biến thể lớn nhất).
- [`event_sourcing_demo.py`](event_sourcing_demo.py) — Python không có công cụ nào trong hai thứ
  trên, nên bài tập trung vào cái bẫy Python dễ rơi nhất: **hàm phát lại đọc biến toàn cục**. Phần
  4 đo trực tiếp: cùng một chuỗi sự kiện, hai số dư, lệch 7.000 mỗi lần đọc.

**Khác biệt giữa ba ngôn ngữ:**

| | Chặn "quên loại sự kiện" | Chặn "hàm phát lại ném" | Bẫy riêng |
|---|---|---|---|
| Java | ✅ `sealed` + switch — **biên dịch** | quy ước | — |
| C++ | ✅ quá tải `capNhat` — **biên dịch** | ✅ `noexcept` + `static_assert` | `sizeof(variant)` = biến thể lớn nhất |
| Python | `else: raise NotImplementedError` — lúc chạy | quy ước | **đọc biến toàn cục / `now()`** trong hàm phát lại |

Bản Python cũng cho thấy cách viết Pythonic nhất cho phát lại: nó **là** một phép gộp —
`reduce(áp_dụng, lịch_sử, trạng_thái_rỗng)`.

## 5. Thực tế đi làm

**Cạm bẫy #1 — kiểm tra luật trong hàm phát lại.** Cạm bẫy chết người nhất của ES, và nó rất tự
nhiên: "kiểm hai lần cho chắc". Hậu quả không phải một lỗi mà là **mất quyền truy cập dữ liệu**.

**Cạm bẫy #2 — hàm phát lại đọc cấu hình / đồng hồ / CSDL.** Cùng bản chất, khó thấy hơn. Kiểm tra
bằng mắt: hàm áp dụng chỉ được chạm `self` và tham số.

**Cạm bẫy #3 — sự kiện mang công thức thay vì kết quả.** `DaTinhPhi(tiLe=1%)` thay vì
`DaTinhPhi(soTien=7.000)`. Nó "gọn hơn" cho tới ngày biểu phí đổi.

**Cạm bẫy #4 — sửa sự kiện cũ trong CSDL để "vá dữ liệu".** Đây là viết lại lịch sử theo nghĩa đen.
Cách đúng là ghi một sự kiện **điều chỉnh** mới (`DaDieuChinhSoDu`) — y như kế toán không tẩy sổ mà
ghi bút toán đảo.

**Cạm bẫy #5 — không có ảnh chụp.** Chạy được ba tháng đầu, rồi chậm dần và không ai biết vì sao.
Ảnh chụp phải có từ đầu, và phải **dựng lại được** từ log bất cứ lúc nào.

**Cạm bẫy #6 — dùng ES cho toàn hệ thống.** Bảng danh mục tỉnh/thành không cần lịch sử. ES cho
phần *lịch sử là nghiệp vụ*, phần còn lại dùng CRUD bình thường — và hai phần đó là hai bounded
context ([bài 93](../93-bounded-context/)).

**Cạm bẫy #7 — quên rằng mô hình đọc phải dựng lại được.** Nếu mô hình đọc bị lệch (và nó sẽ lệch),
cách chữa là **xoá đi và phát lại từ log**. Nếu làm không được thì mô hình đọc đã trở thành nguồn
sự thật thứ hai.

**Biến thể phỏng vấn thường hỏi:**
- *"Event sourcing là gì?"* — Lưu **chuỗi sự kiện** làm nguồn sự thật; trạng thái hiện tại được tính
  lại từ đó. Nói thêm "trạng thái là dữ liệu thừa" cho thấy đã hiểu đúng hướng.
- *"Vì sao hàm phát lại không được kiểm tra luật?"* — Vì luật đổi theo thời gian, còn lịch sử thì
  không. Kèm ví dụ hạn mức rút là đủ.
- *"Ảnh chụp có phải nguồn sự thật không?"* — Không, nó là bộ nhớ đệm. Phép thử: xoá đi thì chậm,
  không sai.
- *"Làm sao truy vấn dữ liệu trong ES?"* — Không truy vấn trên log; dựng mô hình đọc từ sự kiện
  ([bài 95](../95-cqrs-lite/)). Đây là câu hỏi tách người đã dùng ES thật với người mới đọc lý thuyết.
- *"ES và audit log khác nhau ra sao?"* — Audit log là **bản sao phụ**, xoá đi hệ thống vẫn chạy. Sự
  kiện trong ES **là** dữ liệu, xoá đi thì mất tất cả. Nhiều dự án tưởng mình làm ES nhưng thật ra
  đang ghi audit log bên cạnh một bảng trạng thái.
- *"Xử lý GDPR / quyền được quên thế nào?"* — Mã hoá dữ liệu cá nhân trong sự kiện bằng một khoá
  riêng cho mỗi người, xoá khoá khi có yêu cầu. Sự kiện còn nguyên, nội dung thành không đọc được.

## 6. Self-check

```bash
cd 04-competitive/96-event-sourcing
javac EventSourcingDemo.java && java EventSourcingDemo        # in "OK"
g++ -std=c++17 -o sol EventSourcingDemo.cpp && ./sol          # in "OK"
python event_sourcing_demo.py                                 # in "OK"
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
