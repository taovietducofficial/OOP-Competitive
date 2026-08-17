# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — một mô hình dùng chung cho cả công ty chặn
# đội bán hàng tạo khách tiềm năng, làm chữ "hoàn tất" mang hai nghĩa, và biến mọi thay
# đổi nhỏ thành việc của ba đội.
# Tại sao cần học: Java và C++ có trình biên dịch canh ranh giới ngữ cảnh — hai lớp cùng
# tên ở hai gói là hai kiểu, và truyền nhầm là lỗi build. Python thì KHÔNG có gì cả, và
# hậu quả tinh vi hơn "chạy rồi mới lỗi": một object của ngữ cảnh sai đi qua biên vẫn
# chạy êm qua mọi hàm chỉ dùng những thuộc tính chung, rồi mới nổ ở một chỗ CÁCH XA nơi
# gây lỗi hàng chục dòng. Bài đo đúng khoảng cách đó, và dựng một chốt chặn ở biên.

from dataclasses import dataclass, fields
from enum import Enum, auto
from typing import Optional


# =====================================================================
# SAI — MỘT mô hình dùng chung cho cả công ty
# =====================================================================
@dataclass
class KhachHangChung:
    # Bán hàng cần:
    ma_khach: str
    ten: str
    nguon_khach: Optional[str] = None
    giai_doan_ban: Optional[str] = None
    # Kế toán cần:
    ten_phap_nhan: Optional[str] = None
    ma_so_thue: Optional[str] = None
    dia_chi_xuat_hoa_don: Optional[str] = None
    dieu_khoan_thanh_toan: Optional[str] = None
    # Hỗ trợ cần:
    email: Optional[str] = None
    hang_uu_tien: Optional[str] = None
    ngon_ngu_giao_tiep: Optional[str] = None
    # Và một chữ mà cả ba đội cùng dùng, mỗi đội hiểu một kiểu:
    hoan_tat: bool = False

    def __post_init__(self):
        # Kế toán yêu cầu mã số thuế bắt buộc — hoàn toàn hợp lý VỚI KẾ TOÁN.
        if not self.ma_so_thue:
            raise ValueError("mã số thuế là bắt buộc")


# =====================================================================
# ĐÚNG — mỗi bounded context một mô hình, nối nhau BẰNG MÃ
# =====================================================================
class GiaiDoan(Enum):
    TIEM_NANG = auto()
    DANG_TU_VAN = auto()
    DA_CHOT = auto()
    DA_MAT = auto()


class BanHang:
    """Ngữ cảnh BÁN HÀNG. "Khách hàng" ở đây là một CƠ HỘI đang được theo đuổi."""

    @dataclass(frozen=True)
    class KhachHang:
        ma_khach: str
        ten: str
        nguon_khach: str
        giai_doan: GiaiDoan

        def __post_init__(self):
            if not self.ten.strip():
                raise ValueError("khách tiềm năng phải có tên")

        def da_hoan_tat(self):
            """Với BÁN HÀNG, "hoàn tất" nghĩa là ĐÃ CHỐT ĐƠN."""
            return self.giai_doan is GiaiDoan.DA_CHOT


class KeToan:
    """Ngữ cảnh KẾ TOÁN. "Khách hàng" ở đây là một PHÁP NHÂN xuất hoá đơn được."""

    @dataclass(frozen=True)
    class BenNhanHoaDon:
        ma_khach: str
        ten_phap_nhan: str
        ma_so_thue: str
        dia_chi_xuat_hoa_don: str
        da_thu_du_tien: bool

        def __post_init__(self):
            if not self.ma_so_thue.strip():
                raise ValueError("bên nhận hoá đơn phải có mã số thuế")

        def da_hoan_tat(self):
            """Với KẾ TOÁN, "hoàn tất" nghĩa là ĐÃ THU ĐỦ TIỀN."""
            return self.da_thu_du_tien


class HoTro:
    """Ngữ cảnh HỖ TRỢ. "Khách hàng" ở đây là một NGƯỜI có thể mở phiếu hỗ trợ."""

    @dataclass(frozen=True)
    class NguoiDung:
        ma_khach: str
        email: str
        muc_uu_tien: int


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: đội bán hàng không tạo nổi một khách tiềm năng ----
    # 9h sáng ở hội chợ. Nhân viên bán hàng gặp một người, có tên và số điện thoại, muốn
    # ghi lại ngay. Người đó chưa phải khách, chưa có công ty, chưa có mã số thuế.
    bi_chan = False
    try:
        KhachHangChung(ma_khach="KH-01", ten="Chị Hoa ở hội chợ")
    except ValueError:
        bi_chan = True
    assert bi_chan, "không tạo được: mô hình chung đòi mã số thuế"
    # Luật "mã số thuế là bắt buộc" HOÀN TOÀN ĐÚNG — với kế toán. Nó chỉ sai khi bị áp lên
    # một ngữ cảnh mà khái niệm "khách hàng" còn chưa có nghĩa đó.
    #
    # Cách vá mà mọi dự án đều làm, và vì sao nó tệ hơn: bỏ luôn ràng buộc. Thế là kế toán
    # mất bảo đảm "mọi bên nhận hoá đơn đều có mã số thuế", và phải tự kiểm ở mọi chỗ dùng.

    ch = BanHang.KhachHang("KH-01", "Chị Hoa ở hội chợ", "hội chợ", GiaiDoan.TIEM_NANG)
    assert ch.ten == "Chị Hoa ở hội chợ", "ngữ cảnh bán hàng: tạo được với 4 field"
    assert len(fields(BanHang.KhachHang)) == 4, "và chỉ có 4 field"
    assert len(fields(KhachHangChung)) == 12, "mô hình chung có 12"
    # Bốn so với mười hai. Tám field kia không phải "dữ liệu chưa điền" — chúng là dữ liệu
    # KHÔNG CÓ NGHĨA trong ngữ cảnh này. Và chúng đều là `Optional[...] = None`, thứ nói
    # thẳng ra rằng mô hình không biết cái gì là bắt buộc với ai.
    so_optional = sum(1 for f in fields(KhachHangChung) if f.default is None)
    assert so_optional == 9, "9/12 field mặc định None — mô hình không còn ràng buộc được gì"
    # Phép đếm này chạy được trong CI trên mọi dataclass của bạn. Tỉ lệ field tuỳ chọn
    # cao là dấu hiệu định lượng sớm nhất của một mô hình đang phục vụ quá nhiều ngữ cảnh.

    # ---- 2. CON BUG: cùng một chữ, hai nghĩa ----
    # Đơn của chị Hoa: đã chốt bán (bán hàng gọi là "hoàn tất"), nhưng công nợ 30 ngày nên
    # chưa thu tiền (kế toán KHÔNG gọi là hoàn tất).
    da_chot = BanHang.KhachHang("KH-01", "Chị Hoa", "hội chợ", GiaiDoan.DA_CHOT)
    chua_thu = KeToan.BenNhanHoaDon("KH-01", "Công ty Hoa Mai", "0301234567", "12 Lê Lợi", False)

    assert da_chot.da_hoan_tat(), "BÁN HÀNG: hoàn tất = đã chốt -> ĐÚNG"
    assert not chua_thu.da_hoan_tat(), "KẾ TOÁN: hoàn tất = đã thu tiền -> CHƯA"
    assert da_chot.da_hoan_tat() != chua_thu.da_hoan_tat(), \
        "cùng một khách, cùng một chữ, hai câu trả lời — và cả hai đều đúng"
    # Với mô hình chung, `hoan_tat` là MỘT bool. Ai gán nó? Đội nào gán thì đội kia đọc
    # sai. Đây là con bug đúng như bài 81 phần 1, nhưng ở quy mô tổ chức: ở đó là hai lập
    # trình viên hiểu khác nhau, ở đây là hai PHÒNG BAN hiểu khác nhau. Và họ đều đúng.

    # ---- 3. CÁI BẪY RIÊNG CỦA PYTHON: duck typing xoá ranh giới ----
    def ghi_log(kh):
        """Hàm dùng chung — chỉ chạm thuộc tính có ở MỌI ngữ cảnh."""
        return f"KH {kh.ma_khach}"

    def in_nhan_gui(ben_nhan):
        """Hàm của KẾ TOÁN — cần thuộc tính chỉ kế toán mới có."""
        return f"{ben_nhan.ten_phap_nhan} - MST {ben_nhan.ma_so_thue}"

    assert ghi_log(da_chot) == "KH KH-01", "object của BÁN HÀNG: chạy êm"
    assert ghi_log(chua_thu) == "KH KH-01", "object của KẾ TOÁN: cũng chạy êm"
    # Hai dòng trên là vấn đề. Object sai đi qua biên KHÔNG nổ ngay — nó chạy êm qua mọi
    # hàm chỉ dùng thuộc tính chung, và trôi sâu vào hệ thống.
    no_muon = False
    try:
        in_nhan_gui(da_chot)          # truyền nhầm object của BÁN HÀNG
    except AttributeError:
        no_muon = True
    assert no_muon, "và chỉ nổ ở hàm ĐẦU TIÊN chạm tới thuộc tính riêng của kế toán"
    # Ở Java/C++ dòng truyền nhầm đó là lỗi biên dịch, ngay tại chỗ gõ sai. Ở Python nó
    # nổ cách đó có thể hàng chục dòng và vài lớp gọi — và thông báo lỗi
    # (`'KhachHang' object has no attribute 'ten_phap_nhan'`) không nói gì về nguyên nhân
    # thật là "object này thuộc ngữ cảnh khác".
    #
    # Cách chặn: một CHỐT ở biên. Ba dòng, và nó đổi lỗi từ "sâu, mơ hồ" thành "ngay, rõ".
    def chot_bien(obj, lop_mong_doi):
        if not isinstance(obj, lop_mong_doi):
            raise TypeError(f"cần {lop_mong_doi.__qualname__}, nhận {type(obj).__qualname__} "
                            f"— object này thuộc bounded context khác")
        return obj

    no_ngay = False
    try:
        in_nhan_gui(chot_bien(da_chot, KeToan.BenNhanHoaDon))
    except TypeError as e:
        no_ngay = "bounded context khác" in str(e)
    assert no_ngay, "chốt ở biên: nổ NGAY, và thông báo nói đúng nguyên nhân"

    # ---- 4. NỐI HAI NGỮ CẢNH BẰNG MÃ, KHÔNG BẰNG OBJECT ----
    assert da_chot.ma_khach == chua_thu.ma_khach, "cùng một con người ngoài đời"
    assert not hasattr(da_chot, "ma_so_thue"), "nhưng bán hàng không biết mã số thuế tồn tại"
    assert not hasattr(chua_thu, "giai_doan"), "và kế toán không biết giai đoạn bán là gì"
    # Đây chính là bài 83 (tham chiếu bằng id) nâng lên cấp độ tổ chức: hai ngữ cảnh chia
    # sẻ một ĐỊNH DANH, không chia sẻ một MÔ HÌNH.

    # ---- 5. ĐO CHI PHÍ THAY ĐỔI ----
    # Kế toán cần thêm `dieu_khoan_thanh_toan`.
    #   Mô hình chung: sửa lớp -> ba đội cùng test lại, cùng triển khai. Muốn ra bản vá
    #                  thì phải xếp lịch với hai đội không liên quan gì tới thay đổi này.
    #   Tách ngữ cảnh: sửa `KeToan.BenNhanHoaDon` -> đúng một đội, một lần triển khai.
    assert (3, 1) == (3, 1), "3 đội so với 1"
    # Mô hình chung không làm code chậm đi — nó làm TỔ CHỨC chậm đi, và đó là thứ đắt hơn.

    # ---- 6. BẢN ĐỒ NGỮ CẢNH: quan hệ giữa các ngữ cảnh có TÊN ----
    #
    #   Quan hệ            | Nghĩa                                   | Khi nào dùng
    #   -------------------|-----------------------------------------|-------------------
    #   Đối tác            | hai đội cùng đổi, cùng chịu trách nhiệm | hai đội cùng công ty
    #   Khách/Nhà cung cấp | thượng nguồn nghe hạ nguồn              | có quyền thương lượng
    #   Tuân thủ           | hạ nguồn dùng y nguyên mô hình trên     | bên trên không đổi được
    #   Chống hư hỏng      | hạ nguồn DỊCH mô hình trên sang của mình| mô hình trên xấu (bài 94)
    #   Nhân chung         | hai đội cùng sở hữu một phần mã dùng chung | rất ít, rất nguy hiểm
    #
    # "Nhân chung" là thứ mọi người bắt đầu và hối hận: một gói `common_models` mà ba đội
    # cùng sửa. Nó có mọi nhược điểm của mô hình chung, cộng thêm việc không ai sở hữu nó.
    ban_do = {
        "BanHang -> KeToan": "Khách/Nhà cung cấp: bán hàng chốt đơn, kế toán xuất hoá đơn",
        "KeToan -> CongThue": "Tuân thủ: cơ quan thuế không đổi định dạng vì ta",
        "HoTro -> BanHang": "Chống hư hỏng: hỗ trợ tự dịch, không phụ thuộc giai đoạn bán",
    }
    assert len(ban_do) == 3, "bản đồ ngữ cảnh là tài liệu THẬT, vẽ được trên một trang giấy"
    # Nếu không vẽ được bản đồ này cho hệ thống của bạn, thì ranh giới ngữ cảnh chưa tồn
    # tại — chỉ có các gói code cùng dùng chung một mô hình.

    # ---- 7. DỊCH Ở BIÊN, MỖI CHIỀU MỘT LẦN ----
    so_hoa_don = []
    if da_chot.da_hoan_tat():
        so_hoa_don.append(KeToan.BenNhanHoaDon(
            da_chot.ma_khach, "Công ty Hoa Mai", "0301234567", "12 Lê Lợi", False))
    assert len(so_hoa_don) == 1, "dịch ở biên: một chiều, một chỗ, có tên"
    assert so_hoa_don[0].ma_khach == da_chot.ma_khach, "chỉ MÃ đi qua biên"
    # Chỗ dịch này là nơi DUY NHẤT hai ngôn ngữ gặp nhau, nên nó là nơi duy nhất phải sửa
    # khi một bên đổi. Bài 94 nói kỹ về việc bảo vệ mình khi bên kia có mô hình xấu.

    # ---- 8. KHI NÀO KHÔNG TÁCH NGỮ CẢNH ----
    # Bounded context có chi phí thật: mô hình lặp lại, mã dịch ở biên, dữ liệu đồng bộ
    # trễ. Ba dấu hiệu cho thấy CHƯA nên tách:
    #   - Cả hệ thống do MỘT đội làm, và mọi người dùng cùng một bộ từ ngữ.
    #   - Chưa tìm ra được một từ nào mang hai nghĩa (phép thử ở phần 2).
    #   - Số field mà mỗi bên phải bỏ trống còn nhỏ.
    # Ngược lại, ba dấu hiệu ĐÃ đến lúc tách:
    #   - Có field mà nửa số nơi dùng luôn để `None` (phép đếm ở phần 1);
    #   - Có từ mà bạn phải hỏi lại "ý anh là hoàn tất theo nghĩa nào";
    #   - Một thay đổi nhỏ phải xếp lịch với đội không liên quan.
    assert so_optional / len(fields(KhachHangChung)) > 0.5, \
        "quá nửa số field là tuỳ chọn — dấu hiệu rõ nhất và đo được nhất"

    print("OK")
