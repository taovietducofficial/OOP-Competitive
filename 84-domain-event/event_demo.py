# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — aggregate GHI sự kiện thay vì tự phát, và
# hai con bug thật: email xác nhận gửi cho một đơn hàng không tồn tại (phát sự kiện
# trong transaction), và báo cáo doanh thu lệch (sự kiện mang tham chiếu thay vì mang
# dữ liệu tại thời điểm xảy ra).
# Tại sao cần học: Java chặn "quên xử lý một loại sự kiện" bằng `sealed`, C++ bằng
# `std::visit` — cả hai đều ở mức biên dịch. Python KHÔNG có gì tương đương, và cái
# bẫy ở đây tinh vi hơn nhiều so với vẻ ngoài: câu lệnh `match` trông y hệt `switch`
# của Java nhưng khi không khớp nhánh nào thì nó KHÔNG báo lỗi — nó lặng lẽ không làm
# gì. Thêm một loại sự kiện mới là thêm một nhánh nghiệp vụ biến mất không dấu vết.
# Bù lại, Python đọc được lớp lúc chạy, nên bài dựng hai bài test kiến trúc chặn đúng
# hai lỗi đó.

from dataclasses import dataclass
from typing import List


# =====================================================================
# SỰ KIỆN — bất biến, tên ở THÌ QUÁ KHỨ, mang dữ liệu LÚC XẢY RA
# =====================================================================
@dataclass(frozen=True)
class DonHangDaTao:
    ma_don: str
    ma_khach: str
    tong_tien: int
    luc: int


@dataclass(frozen=True)
class DonHangDaGiao:
    ma_don: str
    tong_tien_luc_giao: int
    luc: int


@dataclass(frozen=True)
class DonHangDaHuy:
    ma_don: str
    ly_do: str
    so_tien_hoan: int
    luc: int


CAC_LOAI_SU_KIEN = (DonHangDaTao, DonHangDaGiao, DonHangDaHuy)


# Đối chiếu — MỆNH LỆNH. Khác sự kiện ở ba điểm, xem phần 1.
@dataclass(frozen=True)
class GuiEmailXacNhan:
    ma_don: str
    dia_chi_email: str


# =====================================================================
# AGGREGATE ĐÚNG — GHI sự kiện, KHÔNG tự phát đi
# =====================================================================
class TrangThai:
    MOI_TAO, DA_THANH_TOAN, DA_GIAO, DA_HUY = "MOI_TAO", "DA_THANH_TOAN", "DA_GIAO", "DA_HUY"


class DonHang:
    def __init__(self, ma, ma_khach, tong_tien, luc):
        self.ma, self.ma_khach, self.tong_tien = ma, ma_khach, tong_tien
        self.trang_thai = TrangThai.MOI_TAO
        # Sự kiện nằm TRONG aggregate cho tới khi transaction xong. Aggregate không
        # biết bus tồn tại — không có field nào trỏ tới nó, không import dòng nào.
        self._su_kien_chua_phat: List[object] = [DonHangDaTao(ma, ma_khach, tong_tien, luc)]

    def thanh_toan(self):
        if self.trang_thai != TrangThai.MOI_TAO:
            raise RuntimeError("chỉ thanh toán được đơn mới tạo")
        self.trang_thai = TrangThai.DA_THANH_TOAN

    def giao(self, luc):
        if self.trang_thai != TrangThai.DA_THANH_TOAN:
            raise RuntimeError("chưa thanh toán thì chưa giao được")
        self.trang_thai = TrangThai.DA_GIAO
        # Sự kiện chụp lại tổng tiền TẠI THỜI ĐIỂM GIAO — xem phần 5.
        self._su_kien_chua_phat.append(DonHangDaGiao(self.ma, self.tong_tien, luc))

    def huy(self, ly_do, luc):
        if self.trang_thai == TrangThai.DA_GIAO:
            raise RuntimeError("đơn đã giao thì không huỷ được")
        hoan = self.tong_tien if self.trang_thai == TrangThai.DA_THANH_TOAN else 0
        self.trang_thai = TrangThai.DA_HUY
        self._su_kien_chua_phat.append(DonHangDaHuy(self.ma, ly_do, hoan, luc))

    # Tầng ứng dụng lấy sự kiện ra SAU KHI lưu thành công.
    def lay_va_xoa_su_kien(self):
        ds, self._su_kien_chua_phat = self._su_kien_chua_phat, []
        return ds

    @property
    def so_su_kien_cho_phat(self):
        return len(self._su_kien_chua_phat)


# =====================================================================
# AGGREGATE SAI — tự gọi bus ngay bên trong
# =====================================================================
class DonHangSai:
    def __init__(self, ma, bus):
        self.ma, self.bus = ma, bus           # <- aggregate phụ thuộc hạ tầng
        self.trang_thai = TrangThai.DA_THANH_TOAN

    def giao(self, luc):
        self.trang_thai = TrangThai.DA_GIAO
        self.bus.phat(DonHangDaGiao(self.ma, 100_000, luc))   # phát NGAY, trong transaction


# =====================================================================
# Hạ tầng: bus + người nghe
# =====================================================================
class Bus:
    def __init__(self):
        self.nguoi_nghe = {}
        self.so_su_kien_da_phat = 0
        self.so_loi_nguoi_nghe = 0

    def dang_ky(self, loai, xu_ly):
        self.nguoi_nghe.setdefault(loai, []).append(xu_ly)

    def phat(self, sk):
        self.so_su_kien_da_phat += 1
        for h in self.nguoi_nghe.get(type(sk), []):
            # Một người nghe hỏng KHÔNG được làm chuyện đã xảy ra thành chưa xảy ra,
            # và cũng không được chặn những người nghe khác. Xem phần 6.
            try:
                h(sk)
            except Exception:
                self.so_loi_nguoi_nghe += 1


class HopThu:
    def __init__(self):
        self.so_email_da_gui = 0


class SoDoanhThu:
    def __init__(self):
        self.tong = 0


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. SỰ KIỆN ≠ MỆNH LỆNH ----
    #
    #                    | MỆNH LỆNH (GuiEmailXacNhan) | SỰ KIỆN (DonHangDaGiao)
    #   -----------------|-----------------------------|-------------------------
    #   thì của tên       | mệnh lệnh: "hãy gửi"        | quá khứ: "đã giao"
    #   người nhận        | ĐÚNG MỘT, biết trước        | KHÔNG BIẾT, ai nghe cũng được
    #   từ chối được?     | có — "email sai định dạng"  | KHÔNG — chuyện xảy ra rồi
    #   ai quyết định?    | người gửi                   | không ai; nó là SỰ THẬT
    #
    # Nếu `DonHang` phát ra `GuiEmailXacNhan`, miền nghiệp vụ vừa quyết định hộ rằng hệ
    # quả của việc giao hàng LÀ gửi email. Ngày mai thêm SMS, thêm tích điểm, thêm ghi sổ
    # kế toán — mỗi lần lại sửa `DonHang`. Với `DonHangDaGiao`, `DonHang` không biết ai
    # nghe, và không bao giờ phải sửa nữa.
    menh_lenh = GuiEmailXacNhan("DH-01", "a@b.c")
    assert menh_lenh.ma_don == "DH-01", "mệnh lệnh nói LÀM GÌ và nói với AI"

    # BÀI TEST KIẾN TRÚC 1 — mọi sự kiện phải ở thì quá khứ (bài 81 áp cho sự kiện).
    for loai in CAC_LOAI_SU_KIEN:
        assert "Da" in loai.__name__, f"tên sự kiện phải ở thì quá khứ: {loai.__name__}"
        assert getattr(loai, "__dataclass_params__").frozen, \
            f"sự kiện phải bất biến: {loai.__name__}"
    # Dòng thứ hai chặn một lỗi hay gặp: ai đó thêm sự kiện mới mà quên `frozen=True`,
    # và từ đó người nghe sửa được sự kiện — nghĩa là sửa được QUÁ KHỨ.

    # ---- 2. AGGREGATE GHI SỰ KIỆN, KHÔNG PHÁT ----
    dong_ho = 1000
    don = DonHang("DH-01", "KH-01", 100_000, dong_ho); dong_ho += 1
    don.thanh_toan()
    don.giao(dong_ho); dong_ho += 1
    assert don.so_su_kien_cho_phat == 2, "hai sự kiện đã được GHI: đã tạo, đã giao"
    assert don.trang_thai == TrangThai.DA_GIAO, "và trạng thái đã đổi"
    assert not hasattr(don, "bus"), "aggregate không biết bus tồn tại — nên test không cần bus"

    # ---- 3. CON BUG: phát sự kiện BÊN TRONG transaction ----
    bus_sai = Bus()
    hop_thu_sai = HopThu()
    bus_sai.dang_ky(DonHangDaGiao, lambda sk: setattr(hop_thu_sai, "so_email_da_gui",
                                                      hop_thu_sai.so_email_da_gui + 1))
    don_sai = DonHangSai("DH-99", bus_sai)
    luu_hong = False
    try:
        don_sai.giao(dong_ho); dong_ho += 1     # phát ngay tại đây
        raise RuntimeError("CSDL hết chỗ")      # transaction hỏng SAU đó
    except RuntimeError:
        luu_hong = True

    assert luu_hong, "transaction đã rollback — đơn DH-99 không tồn tại trong CSDL"
    assert hop_thu_sai.so_email_da_gui == 1, "nhưng khách đã nhận email 'đơn của bạn đã giao'"
    # Không có cách nào thu email về. Bug kinh điển nhất của sự kiện miền, và nó chỉ xảy
    # ra khi hệ thống có lỗi — nghĩa là đúng lúc bạn ít muốn nó nhất.

    # ---- 4. BẢN ĐÚNG: lưu trước, phát sau ----
    bus, hop_thu, so = Bus(), HopThu(), SoDoanhThu()

    def gui_email(sk):
        hop_thu.so_email_da_gui += 1

    def ghi_doanh_thu(sk):
        so.tong += sk.tong_tien_luc_giao

    bus.dang_ky(DonHangDaGiao, gui_email)
    bus.dang_ky(DonHangDaGiao, ghi_doanh_thu)

    don2 = DonHang("DH-02", "KH-01", 100_000, dong_ho); dong_ho += 1
    don2.thanh_toan()
    don2.giao(dong_ho); dong_ho += 1

    luu_that_bai = True                          # giả lập CSDL hỏng
    cho_phat = don2.lay_va_xoa_su_kien()
    if not luu_that_bai:
        for sk in cho_phat:
            bus.phat(sk)
    assert hop_thu.so_email_da_gui == 0, "lưu hỏng -> không email nào được gửi"
    assert bus.so_su_kien_da_phat == 0, "không sự kiện nào rời khỏi tiến trình"
    # Thứ tự đúng chỉ có một: BẮT ĐẦU transaction -> đổi aggregate -> LƯU -> COMMIT ->
    # rồi mới phát sự kiện. Trong hệ thật, "phát sau commit" hay được làm bằng outbox:
    # ghi sự kiện vào một bảng trong CÙNG transaction, rồi một tiến trình riêng đọc bảng
    # đó và phát đi (bài 91 lo phần gửi trùng).

    don3 = DonHang("DH-03", "KH-01", 100_000, dong_ho); dong_ho += 1
    don3.thanh_toan()
    don3.giao(dong_ho); dong_ho += 1
    for sk in don3.lay_va_xoa_su_kien():         # lần này lưu thành công
        bus.phat(sk)
    assert hop_thu.so_email_da_gui == 1, "lưu xong mới phát -> đúng một email"
    assert so.tong == 100_000, "và sổ doanh thu ghi đúng 100.000"
    assert don3.so_su_kien_cho_phat == 0, "sự kiện đã lấy ra thì không phát lại lần hai"

    # ---- 5. CON BUG: sự kiện mang THAM CHIẾU thay vì mang DỮ LIỆU ----
    don4 = DonHang("DH-04", "KH-01", 100_000, dong_ho); dong_ho += 1
    don4.thanh_toan()
    don4.giao(dong_ho); dong_ho += 1
    sk4 = don4.lay_va_xoa_su_kien()

    don4.tong_tien = 120_000     # kế toán chỉnh đơn sau khi giao (chuyện rất thường)

    so_sai, so_dung = SoDoanhThu(), SoDoanhThu()
    for sk in sk4:
        if isinstance(sk, DonHangDaGiao):
            so_sai.tong += don4.tong_tien        # người nghe đi TRA LẠI object -> giá HIỆN TẠI
            so_dung.tong += sk.tong_tien_luc_giao  # sự kiện mang sẵn giá LÚC GIAO
    assert so_sai.tong == 120_000 and so_dung.tong == 100_000, "lệch 20.000 trên một đơn"
    # Sự kiện là ẢNH CHỤP một khoảnh khắc. Nó phải mang đủ dữ liệu để người nghe làm việc
    # mà KHÔNG cần đi hỏi lại ai. Quy tắc: nếu người nghe phải tra CSDL để hiểu sự kiện,
    # thì sự kiện đó thiếu thông tin.
    #
    # Ở Python cái bẫy này khó thấy hơn hai ngôn ngữ kia, vì gán luôn là chia sẻ tham
    # chiếu: `DonHangDaGiao(ma_don=..., don=don4)` trông vô hại và chạy êm — cho tới lúc
    # ai đó sửa `don4`. `frozen=True` KHÔNG cứu được: nó khoá field của sự kiện, không
    # khoá object mà field đó trỏ tới (bài 73, bài 82).
    khong_sua_duoc = False
    try:
        sk4[0].tong_tien_luc_giao = 999
    except Exception:
        khong_sua_duoc = True
    assert khong_sua_duoc, "sự kiện bất biến: không ai sửa được quá khứ"

    # ---- 6. NGƯỜI NGHE HỎNG KHÔNG LÀM CHUYỆN ĐÃ XẢY RA THÀNH CHƯA XẢY RA ----
    bus3, ht3 = Bus(), HopThu()

    def nguoi_nghe_hong(sk):
        raise RuntimeError("SMTP chết")

    def nguoi_nghe_lanh(sk):
        ht3.so_email_da_gui += 1

    bus3.dang_ky(DonHangDaGiao, nguoi_nghe_hong)
    bus3.dang_ky(DonHangDaGiao, nguoi_nghe_lanh)

    don5 = DonHang("DH-05", "KH-01", 100_000, dong_ho); dong_ho += 1
    don5.thanh_toan()
    don5.giao(dong_ho); dong_ho += 1
    for sk in don5.lay_va_xoa_su_kien():
        bus3.phat(sk)

    assert bus3.so_loi_nguoi_nghe == 1, "một người nghe hỏng"
    assert ht3.so_email_da_gui == 1, "người nghe thứ hai VẪN chạy"
    assert don5.trang_thai == TrangThai.DA_GIAO, "và đơn VẪN đã giao — sự thật không rút lại được"
    # Khác biệt cốt lõi với mệnh lệnh: mệnh lệnh hỏng thì huỷ được cả việc. Sự kiện hỏng
    # thì chỉ có HỆ QUẢ hỏng. Cách chữa là thử lại người nghe đó (và người nghe phải chịu
    # được gọi trùng — bài 91), không phải rollback aggregate.

    # ---- 7. CÁI BẪY RIÊNG CỦA PYTHON: `match` KHÔNG vét cạn ----
    @dataclass(frozen=True)
    class DonHangDaTraLai:      # loại sự kiện MỚI, thêm hôm nay
        ma_don: str
        ly_do: str
        luc: int

    def mo_ta_im_lang(sk):
        match sk:
            case DonHangDaTao():
                return "tạo"
            case DonHangDaGiao():
                return "giao"
            case DonHangDaHuy():
                return "huỷ"
        # Không nhánh nào khớp -> rơi tới đây -> trả None. KHÔNG lỗi, KHÔNG cảnh báo.

    assert mo_ta_im_lang(DonHangDaTraLai("DH-07", "hàng lỗi", 9)) is None, \
        "`match` không khớp nhánh nào thì lặng lẽ trả None"
    # Đọc lại dòng trên: một nhánh nghiệp vụ vừa biến mất không dấu vết. Java báo lỗi
    # biên dịch, C++ báo lỗi biên dịch, Python trả `None` và đi tiếp. Cách chữa DUY NHẤT
    # là tự dựng bức tường:
    def mo_ta_on_ao(sk):
        match sk:
            case DonHangDaTao():
                return "tạo"
            case DonHangDaGiao():
                return "giao"
            case DonHangDaHuy():
                return "huỷ"
            case _:
                raise NotImplementedError(f"chưa xử lý loại sự kiện {type(sk).__name__}")

    no_len = False
    try:
        mo_ta_on_ao(DonHangDaTraLai("DH-07", "hàng lỗi", 9))
    except NotImplementedError:
        no_len = True
    assert no_len, "`case _: raise` biến im lặng thành tiếng nổ — luôn viết nhánh này"
    assert mo_ta_on_ao(DonHangDaGiao("DH-07", 1, 9)) == "giao", "và ba loại cũ vẫn chạy"

    # BÀI TEST KIẾN TRÚC 2 — mọi loại sự kiện phải có ít nhất một người nghe.
    def loai_khong_ai_nghe(bus, cac_loai):
        return [c.__name__ for c in cac_loai if c not in bus.nguoi_nghe]

    assert loai_khong_ai_nghe(bus, [DonHangDaGiao]) == [], "DonHangDaGiao có người nghe"
    assert loai_khong_ai_nghe(bus, CAC_LOAI_SU_KIEN) == ["DonHangDaTao", "DonHangDaHuy"], \
        "hai loại sự kiện đang phát ra mà không ai xử lý"
    # Test này không nói "sai" — có sự kiện chưa ai nghe là chuyện bình thường và hợp lệ.
    # Nó nói "đây là danh sách, hãy nhìn vào nó có chủ đích". Rất nhiều bug "tính năng
    # không chạy" nằm đúng ở dòng này: sự kiện phát ra đều đặn, người nghe chưa ai viết.

    # ---- 8. Sự kiện miền giải bài toán của bài 83 ----
    # Bài 83: "một transaction sửa đúng MỘT aggregate". Vậy khi giao hàng xong cần cộng
    # điểm thưởng cho khách (một aggregate khác) thì làm sao?
    #   SAI : don.giao(); khach_hang.cong_diem()  <- hai aggregate, một transaction
    #   ĐÚNG: don.giao() ghi DonHangDaGiao -> commit -> người nghe tải KhachHang và cộng
    #         điểm trong transaction THỨ HAI.
    # Cái giá: có một khoảnh khắc đơn đã giao mà điểm chưa cộng — NHẤT QUÁN CUỐI.
    # Cái được: hai aggregate không khoá lẫn nhau, thêm hệ quả mới không sửa `DonHang`.
    # Nếu bước sau hỏng và phải quay lại bước trước, đó là saga (bài 97).

    print("OK")
