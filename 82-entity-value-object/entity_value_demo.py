# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — hai phép thử để quyết định entity hay
# value object, và các con bug thật mà chọn sai loại sinh ra: hai khách hàng chưa
# lưu gộp làm một, địa chỉ khả biến bị chia sẻ, entity bị nhân bản bởi một hàm
# tưởng là vô hại.
# Tại sao cần học: Python cho value object gần như miễn phí bằng
# `@dataclass(frozen=True)` — nhưng chính sự tiện lợi đó dựng ra cái bẫy lớn nhất:
# `@dataclass` MẶC ĐỊNH sinh `__eq__` so sánh TẤT CẢ field và đặt `__hash__ = None`.
# Dán nó lên một entity là bạn vừa nhận một lớp so sánh theo trạng thái khả biến và
# không bỏ vào `set` được. Bài cũng chỉ ra `dataclasses.replace()`: đúng tuyệt đối
# với value object, và là máy sinh bug khi gọi trên entity.

import copy
from dataclasses import dataclass, field, replace
from typing import List


# =====================================================================
# VALUE OBJECT — `frozen=True` cho đủ bốn thứ cần thiết trong một dòng
# =====================================================================
@dataclass(frozen=True)
class Tien:
    so_tien: int
    tien_te: str

    def __post_init__(self):
        if self.so_tien < 0:
            raise ValueError("số tiền không được âm")
        if len(self.tien_te) != 3:
            raise ValueError("mã tiền tệ phải đúng 3 ký tự")

    # Value object mang LUẬT của chính nó, không chỉ mang dữ liệu.
    def cong(self, khac):
        if self.tien_te != khac.tien_te:
            raise ValueError("không cộng được hai loại tiền tệ")
        return Tien(self.so_tien + khac.so_tien, self.tien_te)   # TRẢ VỀ CÁI MỚI


@dataclass(frozen=True)
class DiaChi:
    duong: str
    phuong: str
    tinh: str

    def __post_init__(self):
        if not self.duong.strip():
            raise ValueError("đường không được rỗng")

    # "Đổi" một value object = tạo cái mới. Ở Python không cần viết `voi_duong()`
    # bằng tay — `replace()` làm sẵn (xem phần 6).


# =====================================================================
# ENTITY — `eq=False` để KHÔNG lấy equals của dataclass, rồi tự viết theo định danh
# =====================================================================
@dataclass(eq=False)
class DiemGiao:
    ma: str            # ĐỊNH DANH: gán lúc tạo, không bao giờ đổi
    dia_chi: DiaChi    # thuộc tính: đổi thoải mái
    nguoi_phu_trach: str

    def __post_init__(self):
        if not self.ma.strip():
            raise ValueError("điểm giao phải có mã ngay lúc tạo")

    # Entity so sánh CHỈ theo định danh. Không field nào khác được xuất hiện ở đây —
    # nếu có, sửa một thuộc tính là mất phần tử trong `set` (bài 75).
    def __eq__(self, khac):
        return isinstance(khac, DiemGiao) and self.ma == khac.ma

    def __hash__(self):
        return hash(self.ma)


# =====================================================================
# BẢN SAI 1 — dán `@dataclass` mặc định lên một entity
# =====================================================================
@dataclass
class DiemGiaoSai:
    ma: str
    dia_chi: str
    nguoi_phu_trach: str
    # Không viết dòng nào, nhưng `@dataclass` vừa lặng lẽ làm hai việc:
    #   1. sinh `__eq__` so sánh CẢ BA field — kể cả hai field khả biến;
    #   2. đặt `__hash__ = None` -> lớp này KHÔNG bỏ vào `set`/`dict` được.


# =====================================================================
# BẢN SAI 2 — entity lấy định danh từ CSDL
# =====================================================================
@dataclass
class KhachHangSai:
    ten: str
    id: int = 0        # 0 = "chưa lưu". Quy ước phổ biến nhất, và sai nhất.

    def __eq__(self, k):
        return isinstance(k, KhachHangSai) and self.id == k.id

    def __hash__(self):
        return hash(self.id)


# BẢN ĐÚNG — định danh sinh trong miền, có ngay từ lúc tạo
class KhachHang:
    _dem = 0

    def __init__(self, ten):
        KhachHang._dem += 1
        self.ma = f"KH-{KhachHang._dem}"    # có định danh TRƯỚC khi chạm CSDL
        self.ten = ten

    def __eq__(self, k):
        return isinstance(k, KhachHang) and self.ma == k.ma

    def __hash__(self):
        return hash(self.ma)


# =====================================================================
# BẢN SAI 3 — "value object" nhưng khả biến
# =====================================================================
@dataclass
class DiaChiSai:
    duong: str


@dataclass
class DonHangSai:
    dia_chi_giao: DiaChiSai


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. HAI PHÉP THỬ để quyết định entity hay value object ----
    #
    # Phép thử A — "đổi HẾT thuộc tính, còn là cùng một thứ không?"
    kho = DiemGiao("DG-01", DiaChi("12 Lê Lợi", "Bến Nghé", "TP.HCM"), "anh Nam")
    tham_chieu_cu = kho
    kho.dia_chi = DiaChi("45 Nguyễn Huệ", "Bến Nghé", "TP.HCM")
    kho.nguoi_phu_trach = "chị Lan"
    assert kho == tham_chieu_cu, "đổi hết thuộc tính, vẫn là cùng điểm giao -> ENTITY"

    # Phép thử B — "hai cái giống hệt nhau, thay cho nhau được không?"
    a, b = Tien(50_000, "VND"), Tien(50_000, "VND")
    assert a == b, "hai tờ 50.000đ thay cho nhau được -> VALUE OBJECT"
    assert a is not b, "vẫn là hai object khác nhau — và điều đó KHÔNG quan trọng"

    # ---- 2. CÙNG MỘT KHÁI NIỆM, HAI VAI TRÒ — tuỳ NGỮ CẢNH ----
    dc_don = DiaChi("12 Lê Lợi", "Bến Nghé", "TP.HCM")
    dc_khac = DiaChi("12 Lê Lợi", "Bến Nghé", "TP.HCM")
    assert dc_don == dc_khac, "trong đơn hàng: hai địa chỉ giống nhau LÀ MỘT"

    kho_a = DiemGiao("DG-01", dc_don, "anh Nam")
    kho_b = DiemGiao("DG-02", dc_khac, "chị Lan")
    assert kho_a != kho_b, "trong vận chuyển: cùng địa chỉ vẫn là HAI điểm giao"
    assert kho_a.dia_chi == kho_b.dia_chi, "dù thuộc tính địa chỉ của chúng bằng nhau"
    # Câu hỏi "cái này là entity hay value object" KHÔNG có câu trả lời chung. Nó phụ
    # thuộc vào việc nghiệp vụ có cần theo dõi CÁI CỤ THỂ NÀY qua thời gian hay không.

    # ---- 3. CÁI BẪY LỚN NHẤT CỦA PYTHON: `@dataclass` mặc định trên entity ----
    s1 = DiemGiaoSai("DG-01", "12 Lê Lợi", "anh Nam")
    s2 = DiemGiaoSai("DG-01", "12 Lê Lợi", "anh Nam")
    assert s1 == s2, "hai object này bằng nhau..."
    s2.nguoi_phu_trach = "chị Lan"
    assert s1 != s2, "...cho tới khi đổi người phụ trách. CÙNG MÃ mà không còn bằng nhau"
    # Đọc lại: hai bản ghi của CÙNG MỘT điểm giao (mã DG-01) vừa trở thành hai thứ khác
    # nhau, chỉ vì một thuộc tính đổi. Đó là định nghĩa của việc mất định danh.

    khong_bam_duoc = False
    try:
        hash(s1)
    except TypeError:
        khong_bam_duoc = True
    assert khong_bam_duoc, "`@dataclass` mặc định đặt __hash__ = None -> không bỏ vào set được"
    # Hai dòng trên là hệ quả của MỘT dòng `@dataclass` không tham số. Python không cảnh
    # báo gì; lỗi chỉ nổ khi ai đó thử `set(cac_diem_giao)` — thường là ở production.
    #
    # Ba cách khai báo, ba ý nghĩa hoàn toàn khác nhau:
    #   @dataclass                  -> so theo mọi field, KHÔNG hash được   (túi dữ liệu)
    #   @dataclass(frozen=True)     -> so theo mọi field, hash được         (VALUE OBJECT)
    #   @dataclass(eq=False) + tự viết __eq__/__hash__ theo id  -> (ENTITY)

    # ---- 4. CON BUG: định danh do CSDL cấp -> hai khách gộp làm một ----
    gio_sai = {KhachHangSai("Nguyễn Văn A"), KhachHangSai("Trần Thị B")}
    assert len(gio_sai) == 1, "HAI khách hàng khác nhau, set chỉ giữ MỘT"
    # Mất một khách hàng. Không ngoại lệ, không log. Bug này chỉ xuất hiện khi xử lý
    # theo lô (nhập file CSV, tạo hàng loạt) — nghĩa là nó qua được mọi test thủ công.

    gio_dung = {KhachHang("Nguyễn Văn A"), KhachHang("Trần Thị B")}
    assert len(gio_dung) == 2, "định danh sinh trong miền -> hai khách, giữ đúng hai"
    # Hệ quả thực tế: test không cần CSDL, và có thể gửi entity qua hàng đợi trước khi
    # lưu — thứ mà kiểu id-tự-tăng không cho phép.

    # ---- 5. CON BUG: value object khả biến bị chia sẻ ----
    chung = DiaChiSai("12 Lê Lợi")
    don1, don2 = DonHangSai(chung), DonHangSai(chung)   # vô tình dùng chung một object
    don1.dia_chi_giao.duong = "45 Nguyễn Huệ"           # chỉ định sửa đơn 1
    assert don2.dia_chi_giao.duong == "45 Nguyễn Huệ", \
        "đơn 2 bị đổi địa chỉ theo — dù không ai đụng vào nó"
    # Hàng của đơn 2 vừa được giao sai địa chỉ. Bug ALIASING, và ở Python nó DỄ xảy ra
    # nhất trong ba ngôn ngữ, vì gán luôn là chia sẻ tham chiếu, không bao giờ là sao chép.

    cua_don1 = cua_don2 = DiaChi("12 Lê Lợi", "Bến Nghé", "TP.HCM")
    cua_don1 = replace(cua_don1, duong="45 Nguyễn Huệ")   # tạo CÁI MỚI, gán lại
    assert cua_don1.duong == "45 Nguyễn Huệ", "đơn 1 đổi"
    assert cua_don2.duong == "12 Lê Lợi", "đơn 2 không hề hấn gì"
    # Vì bất biến, chia sẻ object là AN TOÀN — thậm chí còn tiết kiệm bộ nhớ.

    # ---- 6. `dataclasses.replace()`: đúng với VO, là máy sinh bug với ENTITY ----
    t2 = replace(a, so_tien=70_000)
    assert t2 == Tien(70_000, "VND") and a == Tien(50_000, "VND"), \
        "replace trên value object: đúng hoàn toàn, bản gốc không đổi"

    kho_nhan_ban = replace(kho_a, nguoi_phu_trach="chị Lan")
    assert kho_nhan_ban == kho_a, "hai object này LÀ CÙNG MỘT điểm giao (cùng mã)..."
    assert kho_nhan_ban.nguoi_phu_trach != kho_a.nguoi_phu_trach, "...nhưng trạng thái KHÁC NHAU"
    assert kho_nhan_ban is not kho_a, "và là hai object rời nhau trong bộ nhớ"
    # Cùng một cái kho, đồng thời do anh Nam và chị Lan phụ trách. Ngoài đời không tồn
    # tại. Từ đây trở đi, mọi thay đổi trên bản này sẽ không đến bản kia — dữ liệu trôi
    # ra khỏi nhau, và không ai biết bản nào mới đúng.
    #
    # `copy.copy()` và `copy.deepcopy()` gây đúng cùng một chuyện:
    ban_sao = copy.deepcopy(kho_a)
    assert ban_sao == kho_a and ban_sao is not kho_a, "deepcopy cũng nhân bản định danh"
    #
    # C++ dập được lỗi này ở mức trình biên dịch (`DiemGiao(const DiemGiao&) = delete`).
    # Python thì không có công cụ tương đương — nên nó phải thành LUẬT ĐỘI: entity chỉ
    # được sửa tại chỗ, không bao giờ đi qua `replace`/`copy`. Nếu muốn ép bằng máy, cách
    # thực dụng nhất là chặn ngay trong lớp:
    #     def __copy__(self): raise RuntimeError("không được nhân bản entity")
    #     def __deepcopy__(self, memo): raise RuntimeError("không được nhân bản entity")

    # ---- 7. Cạm bẫy: `frozen=True` là BẤT BIẾN NÔNG ----
    @dataclass(frozen=True)
    class GioHang:
        ma: str
        mat_hang: List[str] = field(default_factory=list)

    ds = ["bút"]
    gh = GioHang("GH-1", ds)
    ds.append("vở")                       # sửa từ BÊN NGOÀI dataclass
    assert len(gh.mat_hang) == 2, "frozen=True KHÔNG làm cho list bên trong bất biến"
    # `frozen` chỉ chặn việc GÁN LẠI field, không chặn việc sửa thứ field đó trỏ tới
    # (bài 73). Và hệ quả kéo theo: object này hash không được, dù mang tiếng frozen.
    khong_bam_duoc = False
    try:
        hash(gh)
    except TypeError:
        khong_bam_duoc = True
    assert khong_bam_duoc, "frozen chứa list -> hash() vẫn ném TypeError"

    @dataclass(frozen=True)
    class GioHangDung:
        ma: str
        mat_hang: tuple = ()

        def __post_init__(self):
            object.__setattr__(self, "mat_hang", tuple(self.mat_hang))   # sao chép phòng vệ

    ds2 = ["bút"]
    ghd = GioHangDung("GH-2", ds2)
    ds2.append("vở")
    assert len(ghd.mat_hang) == 1, "chuyển sang tuple chặn được rò rỉ khả biến"
    assert hash(ghd) == hash(GioHangDung("GH-2", ["bút"])), "và hash lại dùng được"
    # Quy tắc: value object trong Python chỉ được chứa `tuple`/`frozenset`/value object
    # khác. Thấy `List` hay `Dict` trong một dataclass frozen là thấy một cái bẫy.

    # ---- 8. Bảng quyết định ----
    #
    #   Câu hỏi                                         | Trả lời CÓ -> loại nào
    #   ------------------------------------------------|-----------------------
    #   Đổi hết thuộc tính, còn là cùng một thứ?         | ENTITY
    #   Hai cái giống hệt thì thay cho nhau được?        | VALUE OBJECT
    #   Nghiệp vụ cần lịch sử của CÁI NÀY?               | ENTITY
    #   Có thể chia sẻ tự do giữa nhiều chủ sở hữu?      | VALUE OBJECT
    #
    # Quy tắc thực dụng: MẶC ĐỊNH là value object. Chỉ nâng lên entity khi có một câu
    # hỏi nghiệp vụ thật sự cần theo dõi cái cụ thể đó qua thời gian. Entity đắt hơn
    # nhiều — nó cần định danh, cần kho lưu trữ, cần vòng đời, và không chia sẻ tự do được.
    assert True, "mặc định là value object; entity phải có lý do"

    print("OK")
