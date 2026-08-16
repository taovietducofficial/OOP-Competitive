# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — luật đổi theo ngữ cảnh (quốc gia, hạng
# khách) mà code gọi không đổi. Ba con bug: chuỗi if-else chép ba nơi rồi một nơi quên
# nước Đức (lệch 19 triệu mỗi đơn); thiếu chính sách thì âm thầm về 0; và bùng nổ tổ
# hợp 4×3 khi trộn hai trục luật.
# Tại sao cần học: Python cho cách đăng ký chính sách gọn nhất trong ba ngôn ngữ — một
# decorator `@dang_ky(QuocGia.DE)` và bảng tra tự điền. Nhưng chính sự gọn đó mở ra cái
# bẫy riêng của Python: bảng chỉ được điền khi MODULE chứa chính sách đó được import.
# Một chính sách nằm trong file chưa ai import là một chính sách không tồn tại — và hệ
# thống sẽ chạy êm với thuế 0%. Bài đo đúng chỗ đó, và dựng bài test chặn nó.

from enum import Enum, auto
from typing import Callable, Dict


class QuocGia(Enum):
    VN = auto()
    JP = auto()
    US = auto()
    DE = auto()


class HangKhach(Enum):
    THUONG = auto()
    BAC = auto()
    VANG = auto()


# =====================================================================
# POLICY — một luật, được CHỌN lúc chạy
# =====================================================================
class ChinhSachThue:
    def tinh_thue(self, tien_hang: int) -> int:
        raise NotImplementedError

    def mo_ta(self) -> str:              # có tên, đọc lên thành câu (bài 81)
        raise NotImplementedError


class ThueTheoTiLe(ChinhSachThue):
    def __init__(self, phan_tram, ten):
        self.phan_tram, self.ten = phan_tram, ten

    def tinh_thue(self, tien_hang):
        return tien_hang * self.phan_tram // 100

    def mo_ta(self):
        return f"{self.ten} {self.phan_tram}%"


class MienThue(ChinhSachThue):
    def __init__(self, ten):
        self.ten = ten

    def tinh_thue(self, tien_hang):
        return 0

    def mo_ta(self):
        return self.ten


BANG_THUE: Dict[QuocGia, ChinhSachThue] = {}


def dang_ky(quoc_gia):
    """Decorator: THÊM MỘT THỊ TRƯỜNG = THÊM MỘT LỚP, không sửa dòng nào."""
    def gan(lop):
        BANG_THUE[quoc_gia] = lop()
        return lop
    return gan


@dang_ky(QuocGia.VN)
class ThueVN(ThueTheoTiLe):
    def __init__(self):
        super().__init__(10, "VAT Việt Nam")


@dang_ky(QuocGia.JP)
class ThueJP(ThueTheoTiLe):
    def __init__(self):
        super().__init__(8, "thuế tiêu dùng Nhật")


@dang_ky(QuocGia.US)
class ThueUS(MienThue):
    def __init__(self):
        super().__init__("không thuế liên bang")


@dang_ky(QuocGia.DE)
class ThueDE(ThueTheoTiLe):
    def __init__(self):
        super().__init__(19, "USt Đức")


def chinh_sach_cho(quoc_gia, bang=None):
    """Tra chính sách: THIẾU thì NỔ, không âm thầm về 0. Xem phần 4."""
    bang = BANG_THUE if bang is None else bang
    cs = bang.get(quoc_gia)
    if cs is None:
        raise RuntimeError(f"chưa có chính sách thuế cho {quoc_gia}")
    return cs


# =====================================================================
# TRỤC THỨ HAI — giảm giá theo hạng khách, ĐỘC LẬP với thuế
# =====================================================================
BANG_GIAM: Dict[HangKhach, Callable[[int], int]] = {
    HangKhach.THUONG: lambda t: 0,
    HangKhach.BAC: lambda t: t * 5 // 100,
    HangKhach.VANG: lambda t: t * 10 // 100,
}


def tinh_tong_phai_tra(tien_hang, quoc_gia, hang):
    """Tầng ứng dụng chỉ GHÉP hai trục lại. Nó không biết nước nào bao nhiêu phần trăm."""
    giam = BANG_GIAM[hang](tien_hang)
    sau_giam = tien_hang - giam
    return sau_giam + chinh_sach_cho(quoc_gia).tinh_thue(sau_giam)


# ---- Self-check ----
if __name__ == "__main__":
    tien = 100_000_000

    # ---- 1. CON BUG: chuỗi if-else chép ở ba nơi ----
    # Ba nơi cùng cần thuế: màn hình thanh toán, sinh hoá đơn, báo cáo doanh thu. Nước
    # Đức được thêm tháng trước, và chỉ hai trong ba nơi được cập nhật.
    def thanh_toan(q, t):
        if q is QuocGia.VN:
            return t * 10 // 100
        if q is QuocGia.JP:
            return t * 8 // 100
        if q is QuocGia.DE:
            return t * 19 // 100
        return 0

    hoa_don = thanh_toan

    def bao_cao(q, t):
        if q is QuocGia.VN:
            return t * 10 // 100
        if q is QuocGia.JP:
            return t * 8 // 100
        return 0                                # <- QUÊN nước Đức

    assert thanh_toan(QuocGia.DE, tien) == 19_000_000, "thanh toán thu đúng 19%"
    assert hoa_don(QuocGia.DE, tien) == 19_000_000, "hoá đơn ghi đúng 19%"
    assert bao_cao(QuocGia.DE, tien) == 0, "báo cáo ghi 0% — lệch 19 triệu mỗi đơn"
    assert thanh_toan(QuocGia.DE, tien) - bao_cao(QuocGia.DE, tien) == 19_000_000, \
        "sổ sách và tiền thật không khớp nhau"
    # Nhánh `return 0` nuốt trọn lỗi — và với thuế thì 0 là một con số hoàn toàn hợp lệ
    # (nước Mỹ đúng là 0%), nên không ai nghi ngờ.

    # ---- 2. POLICY: một nguồn sự thật, ba nơi cùng dùng ----
    assert chinh_sach_cho(QuocGia.DE).tinh_thue(tien) == 19_000_000, "một bảng, một câu trả lời"
    assert chinh_sach_cho(QuocGia.US).tinh_thue(tien) == 0, "Mỹ 0% — nhưng là 0% CÓ TÊN"
    assert chinh_sach_cho(QuocGia.US).mo_ta() == "không thuế liên bang", \
        "và tên đó phân biệt được với 'chưa cấu hình'"
    # Điểm tinh tế nhất của bài: `MienThue` và "thiếu cấu hình" đều cho ra 0, nhưng một
    # cái là QUYẾT ĐỊNH NGHIỆP VỤ còn cái kia là LỖI. Chuỗi if-else không phân biệt được.

    # ---- 3. KIỂM TRA ĐỦ CHÍNH SÁCH BẰNG MÁY ----
    thieu = [q for q in QuocGia if q not in BANG_THUE]
    assert thieu == [], f"thiếu chính sách thuế cho: {thieu}"
    assert len(BANG_THUE) == len(QuocGia), "đủ 4/4 quốc gia"
    # Ba dòng trên là một bài test chạy trong CI. Thêm `QuocGia.FR` vào enum mà quên viết
    # lớp chính sách -> test đỏ NGAY, trước khi có đơn hàng nào từ Pháp.
    #
    # Với chuỗi if-else thì không có cách nào viết bài test tương đương, vì không có gì
    # để mà liệt kê — nhánh `return 0` luôn "xử lý được" mọi giá trị.

    # ---- 4. CÁI BẪY RIÊNG CỦA PYTHON: bảng chỉ đầy khi module ĐƯỢC IMPORT ----
    # `@dang_ky(...)` chỉ chạy lúc định nghĩa lớp, tức là lúc module chứa nó được import.
    # Đặt `ThueDE` trong `chinh_sach/duc.py` mà không ai import file đó -> `BANG_THUE`
    # thiếu `DE`, và hệ thống chạy êm với... thuế 0%.
    bang_mo_phong_thieu_import = {q: cs for q, cs in BANG_THUE.items() if q is not QuocGia.DE}
    assert QuocGia.DE not in bang_mo_phong_thieu_import, "mô phỏng: file chính sách Đức chưa ai import"
    no_len = False
    try:
        chinh_sach_cho(QuocGia.DE, bang_mo_phong_thieu_import)
    except RuntimeError:
        no_len = True
    assert no_len, "tra chính sách phải NỔ khi thiếu — 19 triệu không được im lặng biến mất"

    thue_im_lang = bang_mo_phong_thieu_import.get(QuocGia.DE, MienThue("mặc định")).tinh_thue(tien)
    assert thue_im_lang == 0, "`.get(..., mặc định)` là dòng nguy hiểm nhất trong mã nghiệp vụ"
    # Null Object (bài 64) chỉ đúng khi "không có gì" là hành vi HỢP LỆ. Với thuế thì
    # không: thiếu chính sách là tin xấu, và tin xấu phải kêu to.
    #
    # Cách chặn cái bẫy import: bài test ở phần 3 phải chạy SAU khi import gói chính sách
    # (`import chinh_sach` trong `__init__.py`), và tốt nhất là gói đó tự nạp mọi module
    # con bằng `pkgutil.iter_modules`. Nếu không thì chính bài test cũng sẽ không thấy
    # thiếu gì — nó chỉ kiểm tra được những gì đã được import.

    # ---- 5. HAI TRỤC ĐỘC LẬP: 4 + 3, KHÔNG PHẢI 4 × 3 ----
    so_lop_neu_tron_truc = len(QuocGia) * len(HangKhach)
    so_lop_khi_tach_truc = len(QuocGia) + len(HangKhach)
    assert (so_lop_neu_tron_truc, so_lop_khi_tach_truc) == (12, 7), "12 lớp so với 7"
    # Và con số đó nổ theo cấp số nhân: thêm trục thứ ba (kênh bán) thì 12 -> 36, còn
    # 7 -> 10. Quy tắc: mỗi TRỤC BIẾN THIÊN là một bảng chính sách riêng, và tầng ứng
    # dụng ghép chúng lại (bài 63 · decorator là một cách ghép khác cho cùng bài toán).

    assert tinh_tong_phai_tra(100_000, QuocGia.VN, HangKhach.THUONG) == 110_000, \
        "VN thường: 100.000 + 10% = 110.000"
    assert tinh_tong_phai_tra(100_000, QuocGia.VN, HangKhach.VANG) == 99_000, \
        "VN vàng: giảm 10% còn 90.000, +10% thuế = 99.000"
    assert tinh_tong_phai_tra(100_000, QuocGia.US, HangKhach.VANG) == 90_000, \
        "Mỹ vàng: giảm 10%, không thuế"
    # Chú ý THỨ TỰ: giảm giá TRƯỚC, thuế SAU — thuế tính trên số tiền thực trả. Đây là
    # một luật nghiệp vụ, và nó nằm ở tầng ứng dụng vì nó nói về QUAN HỆ giữa hai chính
    # sách chứ không thuộc chính sách nào. Đảo thứ tự là sai luật thuế ở hầu hết các
    # nước — loại bug không ai phát hiện cho tới lúc bị kiểm toán.

    # ---- 6. THÊM/SỬA THỊ TRƯỜNG: ĐO SỐ CHỖ PHẢI SỬA ----
    # Với if-else: sửa 3 nhánh — và bài học ở phần 1 là khả năng quên một chỗ không phải
    # giả thuyết. Với policy: thêm MỘT lớp, và bài test ở phần 3 canh giúp.
    bang_mo_rong = dict(BANG_THUE)
    truoc = len(bang_mo_rong)
    bang_mo_rong[QuocGia.US] = ThueTheoTiLe(7, "thuế bang California")   # đổi luật Mỹ
    assert len(bang_mo_rong) == truoc, "sửa luật MỘT nước: đúng một dòng, không đụng nước khác"
    assert bang_mo_rong[QuocGia.VN].tinh_thue(tien) == 10_000_000, "Việt Nam không hề hấn gì"

    # ---- 7. POLICY vs STRATEGY vs SPECIFICATION ----
    #
    #   Mẫu           | Trả lời câu hỏi          | Chọn lúc nào  | Ví dụ ở đây
    #   --------------|--------------------------|---------------|------------------
    #   Specification | "có thoả mãn không?"     | ghép lúc viết | duoc_vay_tin_chap (87)
    #   Policy        | "luật ở ngữ cảnh này?"   | tra LÚC CHẠY  | BANG_THUE
    #   Strategy      | "làm bằng cách nào?"     | tra lúc chạy  | thuật toán nén/sắp xếp
    #
    # Policy và Strategy có HÌNH DẠNG giống hệt nhau. Khác nhau ở Ý ĐỊNH: strategy đổi
    # CÁCH LÀM cho cùng một kết quả; policy đổi CHÍNH KẾT QUẢ vì nghiệp vụ ở ngữ cảnh đó
    # khác. Nhầm lẫn không gây bug, nhưng gọi đúng tên giúp người sau biết được phép đổi
    # cái gì mà không phá gì.
    assert chinh_sach_cho(QuocGia.VN).tinh_thue(1000) != chinh_sach_cho(QuocGia.JP).tinh_thue(1000), \
        "policy: hai ngữ cảnh, hai KẾT QUẢ khác nhau — và cả hai đều đúng"

    # ---- 8. RANH GIỚI: khoá tra chính sách phải là KIỂU CỦA MIỀN ----
    # `{"VN": ..., "vn": ..., "VNM": ...}` là cách chắc chắn nhất để có một bug không ai
    # tìm ra. Dùng `Enum` thì gõ sai là `KeyError` ngay, liệt kê được hết (phần 3), và
    # IDE tìm được mọi nơi dùng. Chuỗi chỉ nên xuất hiện ở BIÊN (đọc cấu hình, nhận
    # request) và được đổi sang enum ngay tại đó (bài 76 · fail fast, bài 78 · DTO).
    assert QuocGia["DE"] is QuocGia.DE, "biên đổi chuỗi -> enum một lần, ngay lúc vào"
    sai_khoa = False
    try:
        QuocGia["Duc"]
    except KeyError:
        sai_khoa = True
    assert sai_khoa, "gõ sai mã nước là nổ ngay tại biên, không trôi vào trong"

    print("OK")
