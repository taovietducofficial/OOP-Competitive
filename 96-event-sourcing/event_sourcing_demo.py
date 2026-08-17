# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — trạng thái được TÍNH LẠI bằng cách phát lại
# chuỗi sự kiện. Ba con bug: chỉ lưu trạng thái thì mất sạch lịch sử; hàm phát lại đọc
# cấu hình HIỆN TẠI nên phát lại hôm nay ra số khác hôm qua; và phát lại nghìn sự kiện
# cho mỗi lần đọc.
# Tại sao cần học: Java chặn "quên xử lý một loại sự kiện khi phát lại" bằng `sealed`,
# C++ chứng minh "hàm phát lại không thể ném" bằng `static_assert(noexcept(...))`. Python
# không có công cụ nào trong hai thứ đó — nên bài này tập trung vào cái bẫy nguy hiểm
# nhất mà Python dễ rơi nhất: hàm phát lại ĐỌC THẾ GIỚI BÊN NGOÀI. Một biến toàn cục, một
# `datetime.now()`, một dòng đọc cấu hình — và từ đó cùng một chuỗi sự kiện cho ra hai số
# dư khác nhau ở hai thời điểm. Bài đo đúng độ lệch đó.

from dataclasses import dataclass
from functools import reduce
from typing import List, Union


# =====================================================================
# SỰ KIỆN — bất biến, thì quá khứ, mang dữ liệu LÚC XẢY RA (bài 84)
# =====================================================================
@dataclass(frozen=True)
class DaMoTaiKhoan:
    ma_tk: str
    so_du_ban_dau: int


@dataclass(frozen=True)
class DaNap:
    so_tien: int
    nguon: str


@dataclass(frozen=True)
class DaRut:
    so_tien: int
    ly_do: str


@dataclass(frozen=True)
class DaTinhPhi:
    """Phí đã được TÍNH SẴN lúc phát sinh — xem phần 5 để biết vì sao đây là bắt buộc."""
    so_tien_phi: int
    ti_le_phan_nghin: int


SuKien = Union[DaMoTaiKhoan, DaNap, DaRut, DaTinhPhi]

# Cấu hình toàn cục — dùng để cho nổ bug ở phần 4.
BIEU_PHI_HIEN_TAI_PHAN_NGHIN = 10


# =====================================================================
# AGGREGATE — không lưu trạng thái, chỉ lưu SỰ KIỆN
# =====================================================================
class TaiKhoan:
    def __init__(self):
        self.ma = None
        self.so_du = 0
        self.so_su_kien_da_ap_dung = 0
        self._su_kien_moi: List[SuKien] = []

    @staticmethod
    def phat_lai(lich_su):
        """Dựng lại từ lịch sử. Đây là cách DUY NHẤT tải một aggregate trong ES."""
        tk = TaiKhoan()
        for e in lich_su:
            tk.ap_dung(e)
        return tk

    def ap_dung(self, e):
        """ÁP DỤNG — chỉ đổi trạng thái. Tuyệt đối KHÔNG kiểm tra, KHÔNG đọc gì bên
        ngoài, KHÔNG gọi đồng hồ, KHÔNG ném ngoại lệ. Nó phải cho ra cùng kết quả hôm
        nay, ngày mai, và mười năm nữa với cùng chuỗi sự kiện."""
        if isinstance(e, DaMoTaiKhoan):
            self.ma, self.so_du = e.ma_tk, e.so_du_ban_dau
        elif isinstance(e, DaNap):
            self.so_du += e.so_tien
        elif isinstance(e, DaRut):
            self.so_du -= e.so_tien
        elif isinstance(e, DaTinhPhi):
            self.so_du -= e.so_tien_phi
        else:
            # Python không có `sealed` để bắt lỗi lúc biên dịch như Java. Nhánh này là
            # thứ gần nhất: nổ TO khi có loại sự kiện chưa xử lý, thay vì âm thầm bỏ qua
            # và tính sai trạng thái của mọi bản ghi từng phát ra nó (bài 84 phần 7).
            raise NotImplementedError(f"chưa xử lý loại sự kiện {type(e).__name__}")
        self.so_su_kien_da_ap_dung += 1

    def _ghi_nhan(self, e):
        """QUYẾT ĐỊNH — đây là nơi DUY NHẤT được kiểm tra luật nghiệp vụ."""
        self.ap_dung(e)
        self._su_kien_moi.append(e)

    @staticmethod
    def mo(ma, ban_dau):
        if ban_dau < 0:
            raise ValueError("số dư ban đầu không âm")
        tk = TaiKhoan()
        tk._ghi_nhan(DaMoTaiKhoan(ma, ban_dau))
        return tk

    def nap(self, t, nguon):
        if t <= 0:
            raise ValueError("số tiền nạp phải dương")
        self._ghi_nhan(DaNap(t, nguon))

    def rut(self, t, ly_do, ti_le_phi_phan_nghin):
        phi = t * ti_le_phi_phan_nghin // 1000
        if self.so_du < t + phi:
            raise RuntimeError("không đủ số dư")
        self._ghi_nhan(DaRut(t, ly_do))
        self._ghi_nhan(DaTinhPhi(phi, ti_le_phi_phan_nghin))   # phí CHỐT tại thời điểm này

    @property
    def su_kien_moi(self):
        return tuple(self._su_kien_moi)      # bản chụp — không ai sửa được quá khứ


# =====================================================================
# BẢN SAI — hàm phát lại ĐỌC THẾ GIỚI BÊN NGOÀI
# =====================================================================
class TaiKhoanSai:
    def __init__(self):
        self.so_du = 0

    def ap_dung(self, e):
        if isinstance(e, DaMoTaiKhoan):
            self.so_du = e.so_du_ban_dau
        elif isinstance(e, DaNap):
            self.so_du += e.so_tien
        elif isinstance(e, DaRut):
            # <- một dòng, và toàn bộ lịch sử trở nên không tất định
            phi = e.so_tien * BIEU_PHI_HIEN_TAI_PHAN_NGHIN // 1000
            self.so_du -= e.so_tien + phi
        # (bản này không có DaTinhPhi — vì nó "tính lại được", theo lời người viết)


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: chỉ lưu TRẠNG THÁI thì mất sạch lịch sử ----
    so_du_chi_luu_trang_thai = 1_000_000
    so_du_chi_luu_trang_thai += 200_000      # nạp
    so_du_chi_luu_trang_thai -= 700_000      # rút
    so_du_chi_luu_trang_thai -= 7_000        # phí
    assert so_du_chi_luu_trang_thai == 493_000, "số dư đúng: 493.000"
    # Khách gọi lên hỏi: "vì sao tài khoản tôi còn 493.000?" Câu trả lời duy nhất mà hệ
    # thống đưa ra được là "vì nó bằng 493.000". Không có ai, không có lúc nào, không có
    # vì sao — cột số dư đã bị ghi đè bốn lần và ba giá trị cũ biến mất.

    # ---- 2. EVENT SOURCING: trạng thái là dữ liệu THỪA ----
    tk = TaiKhoan.mo("TK-01", 1_000_000)
    tk.nap(200_000, "chuyển khoản")
    tk.rut(700_000, "mua hàng", 10)          # phí 1% = 7.000
    assert tk.so_du == 493_000, "cùng con số 493.000"

    lich_su = tk.su_kien_moi
    assert len(lich_su) == 4, "và 4 sự kiện giải thích trọn vẹn con số đó"
    assert isinstance(lich_su[0], DaMoTaiKhoan), "mở tài khoản 1.000.000"
    assert lich_su[3] == DaTinhPhi(7_000, 10), "phí 7.000, tỉ lệ 10‰"

    dung_lai = TaiKhoan.phat_lai(lich_su)
    assert dung_lai.so_du == tk.so_du, "phát lại lịch sử cho ra ĐÚNG trạng thái cũ"
    assert dung_lai.ma == "TK-01", "toàn bộ trạng thái, không chỉ số dư"
    assert dung_lai.so_su_kien_da_ap_dung == 4, "bằng cách áp dụng đúng 4 sự kiện"
    # Đây là lời hứa cốt lõi: KHÔNG có cột `so_du` nào trong CSDL. Chỉ có bảng sự kiện.

    # Cách viết Pythonic cho cùng một ý — phát lại LÀ một phép gộp (fold):
    so_du_bang_reduce = reduce(
        lambda s, e: s + (e.so_tien if isinstance(e, DaNap)
                          else -e.so_tien if isinstance(e, DaRut)
                          else -e.so_tien_phi if isinstance(e, DaTinhPhi)
                          else e.so_du_ban_dau),
        lich_su, 0)
    assert so_du_bang_reduce == 493_000, "phát lại = reduce(áp dụng, lịch sử, trạng thái rỗng)"

    # ---- 3. TRUY VẤN THEO THỜI GIAN — miễn phí, và chỉ ES mới có ----
    assert TaiKhoan.phat_lai(lich_su[:2]).so_du == 1_200_000, "số dư TRƯỚC khi rút"
    # "Số dư của khách này lúc 14h ngày 3 tháng trước là bao nhiêu?" — với mô hình chỉ lưu
    # trạng thái, câu này cần một bảng lịch sử riêng mà ai đó phải nhớ ghi. Với ES, nó là
    # một phép cắt danh sách.

    # ---- 4. CÁI BẪY NGUY HIỂM NHẤT: HÀM PHÁT LẠI ĐỌC THẾ GIỚI BÊN NGOÀI ----
    lich_su_don_gian = [DaMoTaiKhoan("TK-02", 1_000_000), DaRut(700_000, "mua hàng")]

    sai_hom_nay = TaiKhoanSai()
    for e in lich_su_don_gian:
        sai_hom_nay.ap_dung(e)
    assert sai_hom_nay.so_du == 293_000, "phát lại HÔM NAY (phí 1%): 293.000"

    BIEU_PHI_HIEN_TAI_PHAN_NGHIN = 20        # sang năm ngân hàng tăng phí lên 2%
    sai_sang_nam = TaiKhoanSai()
    for e in lich_su_don_gian:
        sai_sang_nam.ap_dung(e)
    assert sai_sang_nam.so_du == 286_000, "phát lại SANG NĂM (phí 2%): 286.000"
    assert sai_hom_nay.so_du - sai_sang_nam.so_du == 7_000, \
        "CÙNG một chuỗi sự kiện, HAI số dư khác nhau — lệch 7.000 mỗi lần đọc"
    # Đọc lại: không có giao dịch nào xảy ra giữa hai lần đọc. Chỉ một biến cấu hình đổi,
    # và số dư của MỌI tài khoản trong hệ thống vừa đổi theo. Không log, không cảnh báo,
    # và không có "trạng thái đúng" nào để đối chiếu — vì trạng thái được tính từ hàm này.
    #
    # Ở Python cái bẫy này dễ mắc nhất trong ba ngôn ngữ vì hàm nào cũng đọc được biến
    # toàn cục, `datetime.now()`, biến môi trường, hay một `settings` được import — không
    # có gì trong chữ ký hàm nói rằng nó đang làm thế. Java/C++ ít nhất còn buộc phải khai
    # báo phụ thuộc; Python thì không.
    #
    # Bản đúng đã tránh được vì sự kiện MANG SẴN phí đã chốt:
    BIEU_PHI_HIEN_TAI_PHAN_NGHIN = 10
    assert TaiKhoan.phat_lai(lich_su).so_du == 493_000, "phát lại hôm nay"
    BIEU_PHI_HIEN_TAI_PHAN_NGHIN = 99
    assert TaiKhoan.phat_lai(lich_su).so_du == 493_000, "phát lại với biểu phí khác: Y HỆT"
    # Vì `ap_dung` không đọc biến đó. Quy tắc kiểm tra được bằng mắt: hàm áp dụng chỉ được
    # dùng `self` và tham số `e`. Thấy bất kỳ tên nào khác trong đó là thấy một cái bẫy.

    # ---- 5. HỆ QUẢ: SỰ KIỆN MANG KẾT QUẢ, KHÔNG MANG CÔNG THỨC ----
    phi_tinh_lai_theo_bieu_phi_moi = 700_000 * 20 // 1000
    assert phi_tinh_lai_theo_bieu_phi_moi == 14_000, "phí tính lại: 14.000"
    assert lich_su[3].so_tien_phi == 7_000, "phí THẬT lúc đó: 7.000"
    # Đây là bài 84 phần 5 với hậu quả nặng hơn hẳn: ở đó sự kiện thiếu dữ liệu làm một
    # báo cáo sai; ở đây nó làm SỐ DƯ sai, trên toàn bộ hệ thống, mỗi lần phát lại.

    # Và quá khứ không sửa được:
    khong_sua_duoc = False
    try:
        lich_su[3].so_tien_phi = 14_000
    except Exception:
        khong_sua_duoc = True
    assert khong_sua_duoc, "sự kiện `frozen` — không ai viết lại được lịch sử"

    # ---- 6. ẢNH CHỤP: phát lại 100.000 sự kiện là không dùng được ----
    lich_su_dai = [DaMoTaiKhoan("TK-03", 0)] + [DaNap(1_000, "lãi") for _ in range(1000)]
    khong_anh_chup = TaiKhoan.phat_lai(lich_su_dai)
    assert khong_anh_chup.so_su_kien_da_ap_dung == 1001, "phát lại 1.001 sự kiện MỖI lần đọc"

    anh_chup = {"so_du": khong_anh_chup.so_du, "ma": "TK-03", "den_su_kien_thu": len(lich_su_dai)}
    lich_su_dai += [DaNap(500, "lãi"), DaNap(500, "lãi")]

    so_du_tu_anh_chup = anh_chup["so_du"]
    duoi = lich_su_dai[anh_chup["den_su_kien_thu"]:]
    for e in duoi:
        if isinstance(e, DaNap):
            so_du_tu_anh_chup += e.so_tien
    assert len(duoi) == 2, "có ảnh chụp: chỉ phát lại 2 sự kiện đuôi"
    assert so_du_tu_anh_chup == TaiKhoan.phat_lai(lich_su_dai).so_du, "và cho ra cùng kết quả"
    assert 1001 // 2 > 100, "gấp hơn 500 lần công phát lại"
    # Điều quan trọng nhất về ảnh chụp: nó là BỘ NHỚ ĐỆM, không phải nguồn sự thật. Xoá hết
    # ảnh chụp đi thì hệ thống chỉ chậm, không sai. Nếu xoá ảnh chụp mà mất dữ liệu, thì đó
    # không còn là event sourcing nữa.

    # ---- 7. GIÁ PHẢI TRẢ, NÓI THẲNG ----
    #   - Sự kiện là HỢP ĐỒNG VĨNH VIỄN. Đổi nghĩa một loại sự kiện cũ là viết lại lịch sử;
    #     thêm loại mới thì được, sửa loại cũ thì phải phiên bản hoá (bài 79).
    #   - Truy vấn ("tìm mọi tài khoản số dư < 0") KHÔNG làm trên chuỗi sự kiện được. Bắt
    #     buộc phải có mô hình đọc riêng (bài 95) — nên ES gần như luôn đi kèm CQRS.
    #   - Xoá dữ liệu cá nhân theo yêu cầu pháp lý là bài toán KHÓ, vì bản chất của ES là
    #     không xoá. Phải mã hoá dữ liệu cá nhân và vứt khoá đi.
    # Vì vậy: ES dùng cho những phần mà LỊCH SỬ LÀ NGHIỆP VỤ — sổ kế toán, kho, hồ sơ y
    # tế, audit. Không dùng cho bảng cấu hình và danh mục.
    assert len(lich_su) == 4 and tk.so_du == 493_000, "lịch sử và trạng thái, cùng một nguồn"

    print("OK")
