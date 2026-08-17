# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — quy trình nhiều bước, mỗi bước có hành động bù
# trừ. Ba con bug: bước 3 hỏng thì tiền khách kẹt lại; bước không-bù-trừ-được đặt sai chỗ;
# và hành động bù trừ không idempotent nên thử lại là hoàn tiền hai lần.
# Tại sao cần học: Java phải dựng một lớp `Saga` để giữ danh sách bước và chạy ngược, C++
# dùng RAII. Python có sẵn thứ đó trong thư viện chuẩn và hầu như không ai biết:
# `contextlib.ExitStack`. Ba dòng — `stack.callback(bù_trừ)` sau mỗi bước, `stack.pop_all()`
# khi thành công — là một saga hoàn chỉnh, chạy bù trừ theo đúng thứ tự đảo trên mọi đường
# thoát kể cả ngoại lệ. Bài dựng đúng thứ đó, rồi chỉ ra chỗ nó KHÔNG đủ.

from contextlib import ExitStack


# =====================================================================
# Ba dịch vụ, ba aggregate, ba transaction RỜI NHAU
# =====================================================================
class Kho:
    def __init__(self):
        self.ton_kho, self.so_lan_tru, self.so_lan_tra = 10, 0, 0

    def tru(self, sl):
        if self.ton_kho < sl:
            raise RuntimeError("không đủ tồn kho")
        self.ton_kho -= sl
        self.so_lan_tru += 1

    def tra(self, sl):                       # BÙ TRỪ cho `tru`
        self.ton_kho += sl
        self.so_lan_tra += 1


class Vi:
    def __init__(self):
        self.so_du, self.so_lan_tru, self.so_lan_hoan = 1_000_000, 0, 0

    def tru(self, t):
        if self.so_du < t:
            raise RuntimeError("không đủ số dư")
        self.so_du -= t
        self.so_lan_tru += 1

    def hoan(self, t):                       # BÙ TRỪ cho `tru`
        self.so_du += t
        self.so_lan_hoan += 1


class VanChuyen:
    def __init__(self):
        self.se_hong, self.so_van_don = False, 0

    def tao_van_don(self):
        if self.se_hong:
            raise RuntimeError("đối tác vận chuyển hết chỗ")
        self.so_van_don += 1
        return f"VD-{self.so_van_don}"


class HopThu:
    def __init__(self):
        self.so_email_da_gui = 0

    def gui(self, noi_dung):
        self.so_email_da_gui += 1
    # KHÔNG có `thu_hoi_email()`. Đó là toàn bộ vấn đề của phần 4.


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: không có bù trừ -> tiền của khách kẹt lại ----
    kho, vi, vc = Kho(), Vi(), VanChuyen()
    vc.se_hong = True                        # đối tác vận chuyển hết chỗ

    hong = False
    try:
        kho.tru(2)                           # transaction 1: COMMIT
        vi.tru(500_000)                      # transaction 2: COMMIT
        vc.tao_van_don()                     # transaction 3: HỎNG
    except RuntimeError:
        hong = True

    assert hong, "đặt hàng thất bại"
    assert kho.ton_kho == 8, "nhưng 2 sản phẩm vẫn bị giữ trong kho"
    assert vi.so_du == 500_000, "và 500.000 của khách đã bị trừ"
    # Không có gì để "rollback": hai transaction đầu đã COMMIT xong từ lâu. Đây chính là
    # hệ quả trực tiếp của luật ở bài 83 — một transaction sửa một aggregate. Luật đó
    # đúng, và cái giá của nó là bài toán này.
    #
    # Trong hệ thật, tiền đó nằm im cho tới khi khách gọi lên khiếu nại. Và với những lỗi
    # hiếm, "cho tới khi khách gọi" có nghĩa là "không bao giờ".

    # ---- 2. SAGA BẰNG `ExitStack`: bù trừ NGƯỢC CHIỀU, tự động ----
    kho2, vi2, vc2 = Kho(), Vi(), VanChuyen()
    vc2.se_hong = True
    saga_hong = False
    try:
        with ExitStack() as stack:
            kho2.tru(2)
            stack.callback(kho2.tra, 2)          # đăng ký bù trừ NGAY SAU khi làm

            vi2.tru(500_000)
            stack.callback(vi2.hoan, 500_000)

            vc2.tao_van_don()                    # <- ném ngoại lệ tại đây
            stack.pop_all()                      # không bao giờ tới dòng này
    except RuntimeError:
        saga_hong = True
    # Ra khỏi khối `with` -> `ExitStack` chạy các callback theo thứ tự ĐẢO.

    assert saga_hong, "saga thất bại ở bước 3"
    assert kho2.ton_kho == 10, "kho được TRẢ LẠI: 10 như ban đầu"
    assert vi2.so_du == 1_000_000, "tiền được HOÀN: 1.000.000 như ban đầu"
    assert kho2.so_lan_tra == 1 and vi2.so_lan_hoan == 1, "mỗi bước đã xong được bù đúng một lần"
    # `stack.pop_all()` là chi tiết quan trọng nhất và dễ quên nhất: nó chuyển toàn bộ
    # callback sang một stack mới rồi vứt đi, nghĩa là "thành công, đừng bù trừ gì cả".
    # Quên dòng đó thì saga LUÔN bù trừ, kể cả khi mọi bước đều thành công.
    #
    # Thứ tự đảo không phải chuyện thẩm mỹ. Nếu bước 2 phụ thuộc bước 1 (rất thường), thì
    # bù trừ bước 1 trước khi bù bước 2 sẽ để lại trạng thái vô nghĩa ở giữa.

    # ---- 3. BÙ TRỪ KHÔNG PHẢI ROLLBACK ----
    assert (kho2.so_lan_tru, kho2.so_lan_tra) == (1, 1), "kho có HAI bút toán, không phải không có gì"
    assert (vi2.so_lan_tru, vi2.so_lan_hoan) == (1, 1), "ví cũng vậy: trừ rồi hoàn"
    # Điểm quan trọng nhất và hay bị hiểu sai nhất. Rollback XOÁ dấu vết như chưa từng xảy
    # ra. Bù trừ thì KHÔNG: nó ghi thêm một sự thật nghiệp vụ MỚI.
    #
    # Với sổ kế toán, đó là bút toán đảo — và nó PHẢI hiện trên sao kê của khách:
    #   -500.000  thanh toán đơn DH-01
    #   +500.000  hoàn tiền đơn DH-01 (không tạo được vận đơn)
    # Chứ không phải một dòng trống. Khách đã nhìn thấy số dư bị trừ; giấu bút toán hoàn
    # đi là làm sao kê nói dối.

    # ---- 4. CON BUG: bước KHÔNG BÙ TRỪ ĐƯỢC đặt sai chỗ ----
    kho3, vi3, vc3, ht3 = Kho(), Vi(), VanChuyen(), HopThu()
    vc3.se_hong = True
    hong3 = False
    try:
        with ExitStack() as stack:
            kho3.tru(2)
            stack.callback(kho3.tra, 2)

            ht3.gui("đơn của bạn đã được xác nhận")   # <- KHÔNG đăng ký bù trừ được
            # stack.callback(...) — không tồn tại `thu_hoi_email()`

            vi3.tru(500_000)
            stack.callback(vi3.hoan, 500_000)

            vc3.tao_van_don()
            stack.pop_all()
    except RuntimeError:
        hong3 = True

    assert hong3 and kho3.ton_kho == 10 and vi3.so_du == 1_000_000, "kho và tiền đều được bù trừ"
    assert ht3.so_email_da_gui == 1, "NHƯNG email đã bay đi và không thu về được"
    # Đây là bài 84 phần 3 quay lại ở quy mô quy trình. Luật rút ra rất đơn giản và rất
    # đắt nếu quên:
    #
    #   XẾP MỌI BƯỚC KHÔNG BÙ TRỪ ĐƯỢC XUỐNG CUỐI SAGA.
    #
    # Gửi email, gửi SMS, gọi API bên thứ ba không có hàm huỷ, in phiếu — tất cả đi sau
    # cùng, sau khi mọi bước có thể hỏng đã xong.
    kho4, vi4, vc4, ht4 = Kho(), Vi(), VanChuyen(), HopThu()
    with ExitStack() as stack:
        kho4.tru(2)
        stack.callback(kho4.tra, 2)
        vi4.tru(500_000)
        stack.callback(vi4.hoan, 500_000)
        vc4.tao_van_don()
        ht4.gui("đơn của bạn đã được xác nhận")       # <- CUỐI CÙNG
        stack.pop_all()
    assert (kho4.ton_kho, vi4.so_du, ht4.so_email_da_gui) == (8, 500_000, 1), \
        "đường thuận lợi: không bù trừ gì, và email gửi đúng một lần"
    assert kho4.so_lan_tra == 0, "`pop_all()` -> không callback nào chạy"

    # ---- 5. HÀNH ĐỘNG BÙ TRỪ PHẢI IDEMPOTENT ----
    vi_bu = Vi()
    vi_bu.tru(500_000)
    vi_bu.hoan(500_000)
    vi_bu.hoan(500_000)                      # gọi lại do thử lại
    assert vi_bu.so_du == 1_500_000, "hoàn hai lần -> khách được thêm 500.000 từ trên trời"
    assert vi_bu.so_lan_hoan == 2, "vì `hoan` là phép TƯƠNG ĐỐI"
    # Cách chữa là bài 91: mỗi hành động bù trừ mang một khoá idempotency (thường là mã
    # saga + số thứ tự bước), và dịch vụ đích bỏ qua lần gọi trùng. Không có bước đó thì
    # cơ chế thử lại — thứ bắt buộc phải có — trở thành máy sinh tiền.

    # ---- 6. GIỚI HẠN: `ExitStack` KHÔNG chạy khi tiến trình bị giết ----
    # `ExitStack` xử lý hoàn hảo: ngoại lệ, `return` sớm, `break` — mọi đường thoát khỏi
    # khối `with`. Nhưng có ba thứ nó KHÔNG cứu được:
    #   - `os._exit()`, `kill -9`, máy chủ mất điện, container bị thu hồi;
    #   - `sys.exit()` trong một luồng khác làm chết tiến trình;
    #   - bước thứ hai nằm ở MỘT MÁY KHÁC, và máy đó không biết máy này vừa chết.
    # Cả ba đều để lại trạng thái nửa vời mà không ai dọn.
    #
    # Vì vậy: `ExitStack` đủ cho saga TRONG MỘT TIẾN TRÌNH. Với saga phân tán, trạng thái
    # saga phải được LƯU sau MỖI bước, và có một tiến trình riêng quét những saga đang dở
    # để tiếp tục hoặc bù trừ:
    #   ma_saga | buoc_hien_tai | trang_thai
    #   SG-01   | 2             | DANG_CHAY
    #   SG-02   | 3             | DANG_BU_TRU
    # Và vì nó là entity có trạng thái, nó cũng cần khoá lạc quan (bài 92) — hai tiến
    # trình cùng tiếp tục một saga là chuyện có thật.
    trang_thai_saga = {"ma_saga": "SG-01", "buoc_hien_tai": 2, "trang_thai": "DANG_BU_TRU"}
    assert trang_thai_saga["trang_thai"] == "DANG_BU_TRU", "saga là ENTITY, không phải biến cục bộ"

    # ---- 7. ĐIỀU PHỐI hay HỢP XƯỚNG ----
    #
    #   Cách      | Ai biết quy trình              | Thấy được quy trình?  | Ghép chặt?
    #   ----------|--------------------------------|-----------------------|------------
    #   ĐIỀU PHỐI | MỘT object saga (bài này)      | CÓ — đọc một file     | trung tâm biết mọi bước
    #   HỢP XƯỚNG | rải trong người nghe sự kiện   | KHÔNG — lần theo 5 DV | lỏng hơn
    #
    # Quy tắc thực dụng: quy trình có BÙ TRỪ thì dùng điều phối — vì "chạy tới đâu, bù tới
    # đó" cần một chỗ biết thứ tự. Còn hệ quả phụ độc lập (cộng điểm, gửi thông báo, ghi
    # thống kê) thì dùng hợp xướng (bài 84).
    #
    # Dấu hiệu chọn sai: phải mở 5 dịch vụ mới trả lời được câu "đơn hàng này đang ở bước
    # nào" — đó là hợp xướng dùng cho việc của điều phối.

    # ---- 8. SAGA KHÔNG PHẢI TRANSACTION ----
    # Ba tính chất bị mất, phải nói ra với nghiệp vụ TRƯỚC khi làm:
    #   - Không cô lập: giữa bước 1 và bước 3, người khác NHÌN THẤY trạng thái nửa vời
    #     (kho đã trừ, tiền chưa trừ). Nếu điều đó không chấp nhận được thì cụm này phải
    #     là MỘT aggregate (bài 83), không phải một saga.
    #   - Không nguyên tử tức thời: có một khoảng thời gian hệ thống ở trạng thái trung
    #     gian. Đó là nhất quán CUỐI, và độ trễ của nó là con số phải đo.
    #   - Bù trừ có thể HỎNG. Lúc đó cần hàng đợi thư chết và con người xử lý tay — một
    #     saga không có đường thoát cho trường hợp này là một saga chưa xong.
    assert kho2.ton_kho == 10 and vi2.so_du == 1_000_000, "cuối cùng thì nhất quán — CUỐI cùng"

    print("OK")
