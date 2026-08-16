# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — hai lỗi đối xứng nhau: ranh giới QUÁ TO
# (đổi số điện thoại phải tải 501 object, hai người sửa hai đơn khác nhau lại đụng
# nhau) và ranh giới QUÁ NHỎ (bất biến "tổng đơn ≤ hạn mức" bị hai phiên xen kẽ vượt
# qua mà không ai báo lỗi).
# Tại sao cần học: Python không có `private`, không có `const`, không có kiểu ép ở
# mức biên dịch — nghĩa là ranh giới aggregate ở đây KHÔNG được ngôn ngữ bảo vệ chút
# nào. Nhưng bù lại Python cho một thứ hai ngôn ngữ kia không có: đọc được đồ thị
# object lúc chạy. Phần 8 dựng một BÀI TEST KIẾN TRÚC thật — duyệt từ một aggregate
# root và khẳng định không chạm tới root nào khác — sáu dòng, chạy trong CI, và nó
# bắt được đúng cái lỗi mà code review hay bỏ sót.

from dataclasses import dataclass, field
from typing import List


# Định danh là kiểu riêng, không phải str trần. Xem phần 3.
@dataclass(frozen=True)
class MaKhachHang:
    gia_tri: str


@dataclass(frozen=True)
class MaDonHang:
    gia_tri: str


@dataclass(frozen=True)
class DongHang:
    san_pham: str
    don_gia: int
    so_luong: int

    def thanh_tien(self):
        return self.don_gia * self.so_luong


# =====================================================================
# AGGREGATE ĐÚNG KÍCH THƯỚC: DonHang
# Bất biến của nó: TỔNG TIỀN CÁC DÒNG KHÔNG VƯỢT HẠN MỨC.
# =====================================================================
class DonHang:
    HAN_MUC = 50_000_000

    def __init__(self, ma: MaDonHang, ma_khach_hang: MaKhachHang):
        self.ma = ma
        self.ma_khach_hang = ma_khach_hang    # THAM CHIẾU BẰNG ID, không giữ object
        self._cac_dong: List[DongHang] = []   # gạch dưới: cửa sau, không phải cửa chính

    # BẤT BIẾN được kiểm NGAY TẠI ĐÂY, trong cùng một lời gọi ghi dữ liệu.
    def them_dong(self, san_pham, don_gia, so_luong):
        sau_khi_them = self.tong_tien() + don_gia * so_luong
        if sau_khi_them > DonHang.HAN_MUC:
            raise RuntimeError(f"đơn vượt hạn mức {DonHang.HAN_MUC}")
        self._cac_dong.append(DongHang(san_pham, don_gia, so_luong))

    def tong_tien(self):
        return sum(d.thanh_tien() for d in self._cac_dong)

    @property
    def cac_dong(self):
        # CỬA ĐÓNG: trả `tuple`, không trả list nội bộ. Xem phần 7 để biết cái giá của
        # việc quên dòng này.
        return tuple(self._cac_dong)


# =====================================================================
# AGGREGATE ĐÚNG KÍCH THƯỚC: KhachHang — KHÔNG chứa đơn hàng
# =====================================================================
class KhachHang:
    def __init__(self, ma: MaKhachHang, ten, dien_thoai):
        self.ma, self.ten, self.dien_thoai = ma, ten, dien_thoai
        self.phien_ban = 0

    def doi_dien_thoai(self, moi):
        self.dien_thoai = moi
        self.phien_ban += 1


# =====================================================================
# SAI 1 — RANH GIỚI QUÁ TO: khách hàng ôm luôn danh sách đơn
# =====================================================================
class KhachHangQuaTo:
    def __init__(self, ma: MaKhachHang, dien_thoai):
        self.ma, self.dien_thoai = ma, dien_thoai
        self.phien_ban = 0
        self.cac_don: List[DonHang] = []      # <- một dòng, ba hậu quả

    def doi_dien_thoai(self, moi):
        self.dien_thoai = moi
        self.phien_ban += 1

    def them_don(self, d):
        self.cac_don.append(d)
        self.phien_ban += 1


# Kho giả có ĐẾM số object phải tải — để "quá to" thành con số, không thành cảm giác.
class KhoDem:
    def __init__(self):
        self.so_object_da_tai = 0

    def tai_qua_to(self, kh):
        self.so_object_da_tai += 1 + len(kh.cac_don)   # aggregate phải tải TRỌN VẸN
        return kh

    def tai(self, kh):
        self.so_object_da_tai += 1
        return kh


# =====================================================================
# SAI 2 — RANH GIỚI QUÁ NHỎ: dòng hàng thành aggregate riêng
# =====================================================================
@dataclass
class KhoDongRoi:
    dong: List[DongHang] = field(default_factory=list)

    def them(self, d):
        self.dong.append(d)        # không có chỗ nào kiểm hạn mức được

    def tong(self):
        return sum(d.thanh_tien() for d in self.dong)


# ---- Self-check ----
if __name__ == "__main__":
    ma_kh = MaKhachHang("KH-01")

    # ---- 1. PHÉP THỬ RANH GIỚI ----
    # Câu hỏi duy nhất cần hỏi:
    #   "Nếu hai thứ này được sửa trong HAI transaction khác nhau,
    #    có luật nghiệp vụ nào bị phá không?"
    #   CÓ    -> cùng một aggregate.
    #   KHÔNG -> tách ra, tham chiếu bằng id.
    don = DonHang(MaDonHang("DH-01"), ma_kh)
    don.them_dong("laptop", 20_000_000, 2)
    assert don.tong_tien() == 40_000_000, "40 triệu, còn trong hạn mức"

    chan = False
    try:
        don.them_dong("màn hình", 8_000_000, 2)
    except RuntimeError:
        chan = True
    assert chan, "thêm 16 triệu nữa thì vượt 50 triệu -> bị chặn NGAY"
    assert don.tong_tien() == 40_000_000, "và dữ liệu không hề bị sửa dở dang"

    # ---- 2. SAI: RANH GIỚI QUÁ NHỎ -> bất biến không giữ được ----
    kho = KhoDongRoi()
    kho.them(DongHang("laptop", 20_000_000, 2))     # đang có 40 triệu

    doc_boi_a = kho.tong()      # phiên A đọc:  40.000.000
    doc_boi_b = kho.tong()      # phiên B đọc:  40.000.000  <- cùng lúc
    if doc_boi_a + 8_000_000 <= DonHang.HAN_MUC:
        kho.them(DongHang("chuột", 8_000_000, 1))
    if doc_boi_b + 8_000_000 <= DonHang.HAN_MUC:
        kho.them(DongHang("bàn phím", 8_000_000, 1))

    assert kho.tong() == 56_000_000, "tổng thành 56 triệu"
    assert kho.tong() > DonHang.HAN_MUC, "VƯỢT hạn mức — và cả hai phiên đều 'kiểm tra rồi'"
    # Mỗi phiên đều đọc đúng, kiểm đúng, ghi đúng. Cái sai nằm ở RANH GIỚI: hai thứ cùng
    # chịu một bất biến mà lại được sửa trong hai transaction rời nhau. Không `if` nào cứu được.

    don_dung = DonHang(MaDonHang("DH-02"), ma_kh)
    don_dung.them_dong("laptop", 20_000_000, 2)
    don_dung.them_dong("chuột", 8_000_000, 1)
    chan = False
    try:
        don_dung.them_dong("bàn phím", 8_000_000, 1)
    except RuntimeError:
        chan = True
    assert chan, "lệnh thứ hai bị chặn vì bất biến nằm TRONG ranh giới"
    assert don_dung.tong_tien() == 48_000_000, "và tổng dừng đúng chỗ hợp lệ"

    # ---- 3. THAM CHIẾU AGGREGATE KHÁC BẰNG ID, KHÔNG BẰNG OBJECT ----
    assert don.ma_khach_hang == ma_kh, "đơn hàng biết MÃ khách hàng..."
    assert not hasattr(don, "khach_hang"), "...và không có đường nào đi tới object KhachHang"
    # Python không chặn được ở mức biên dịch như Java/C++. Nhưng kiểu riêng vẫn giúp:
    assert MaKhachHang("KH-01") != MaDonHang("KH-01"), \
        "hai loại mã không bao giờ bằng nhau — dataclass so cả KIỂU"
    # Nếu dùng `str` trần thì hai dòng trên bằng nhau, và truyền nhầm mã đơn vào chỗ mã
    # khách sẽ chạy êm cho tới lúc dữ liệu rác lộ ra ở báo cáo.

    # ---- 4. SAI: RANH GIỚI QUÁ TO -> tải 501 object để đổi một số điện thoại ----
    kh_to = KhachHangQuaTo(ma_kh, "0900000000")
    for i in range(500):
        kh_to.them_don(DonHang(MaDonHang(f"DH-{i}"), ma_kh))

    kho_dem = KhoDem()
    kho_dem.tai_qua_to(kh_to).doi_dien_thoai("0911111111")
    assert kho_dem.so_object_da_tai == 501, "đổi MỘT số điện thoại: tải 501 object"
    # Aggregate phải tải trọn vẹn thì bất biến của nó mới kiểm được — đó là luật, không
    # phải chuyện tối ưu. Nên ranh giới to = mọi thao tác đều đắt.

    kh_dung = KhachHang(ma_kh, "Nguyễn Văn A", "0900000000")
    kho_dem2 = KhoDem()
    kho_dem2.tai(kh_dung).doi_dien_thoai("0911111111")
    assert kho_dem2.so_object_da_tai == 1, "ranh giới đúng: tải đúng 1 object"
    assert kho_dem.so_object_da_tai - kho_dem2.so_object_da_tai == 500, "chênh 500 lần tải vô ích"

    # ---- 5. Hậu quả thứ hai của ranh giới quá to: ĐỤNG ĐỘ GIẢ ----
    truoc = kh_to.phien_ban
    kh_to.them_don(DonHang(MaDonHang("DH-A"), ma_kh))   # người dùng 1 tạo đơn A
    kh_to.them_don(DonHang(MaDonHang("DH-B"), ma_kh))   # người dùng 2 tạo đơn B
    assert kh_to.phien_ban == truoc + 2, "hai đơn KHÁC NHAU cùng làm tăng phiên bản KHÁCH HÀNG"
    # Với khoá lạc quan (bài 92), hai người tạo hai đơn không liên quan sẽ báo lỗi "dữ
    # liệu đã bị người khác sửa". Đụng độ này là GIẢ — do ranh giới sai sinh ra.

    pb_truoc = kh_dung.phien_ban
    DonHang(MaDonHang("DH-C"), ma_kh)
    DonHang(MaDonHang("DH-D"), ma_kh)
    assert kh_dung.phien_ban == pb_truoc, "tạo hai đơn: khách hàng không đổi phiên bản"

    # ---- 6. LUẬT "MỘT TRANSACTION = MỘT AGGREGATE" ----
    # Hệ quả trực tiếp của phần 2 và phần 5. Nếu một use case phải sửa hai aggregate
    # cùng lúc, đó là dấu hiệu MỘT trong hai:
    #   (a) ranh giới vẽ sai   -> vẽ lại;
    #   (b) hai thứ đó thật sự không cần đúng đồng thời -> chấp nhận NHẤT QUÁN CUỐI:
    #       aggregate thứ nhất phát ra sự kiện, aggregate thứ hai xử lý sau (bài 84),
    #       và nếu bước sau hỏng thì có hành động bù trừ (bài 97).
    so_root_cham_toi = 1   # "thêm dòng vào đơn" -> chỉ DonHang
    assert so_root_cham_toi == 1, "use case lành mạnh chạm đúng một aggregate root"

    # ---- 7. CÁCH RÒ RỈ RANH GIỚI RIÊNG CỦA PYTHON: trả thẳng list nội bộ ----
    khong_them_duoc = False
    try:
        don.cac_dong.append(DongHang("lén", 1, 1))    # `cac_dong` trả tuple
    except AttributeError:
        khong_them_duoc = True
    assert khong_them_duoc, "trả `tuple` -> không thêm dòng vòng qua cửa được"
    assert len(don.cac_dong) == 1, "và số dòng không đổi"
    # Nếu `cac_dong` trả thẳng `self._cac_dong`, dòng `.append(...)` ở trên chạy êm và
    # bất biến hạn mức mất tác dụng hoàn toàn. Python không có `const` để chặn — nên đây
    # phải là thói quen: aggregate root trả ra `tuple`, `frozenset`, hoặc bản sao.
    #
    # Gạch dưới `_cac_dong` KHÔNG bảo vệ gì cả; nó chỉ là biển báo:
    don._cac_dong.append(DongHang("lén hơn", 1, 1))
    assert len(don.cac_dong) == 2, "gạch dưới không chặn ai — nó là quy ước, không phải khoá"
    don._cac_dong.pop()

    # ---- 8. ĐIỀU CHỈ PYTHON LÀM ĐƯỢC: BÀI TEST KIẾN TRÚC ----
    ROOTS = {DonHang, KhachHang, KhachHangQuaTo}

    def root_khac_cham_toi(goc):
        """Duyệt đồ thị object từ một aggregate root; trả về tên các root khác chạm tới được."""
        da_xet, hang_doi, ket_qua = set(), [goc], set()
        while hang_doi:
            o = hang_doi.pop()
            if id(o) in da_xet:
                continue
            da_xet.add(id(o))
            if o is not goc and type(o) in ROOTS:
                ket_qua.add(type(o).__name__)
                continue                                  # không đi tiếp vào root khác
            if hasattr(o, "__dict__"):
                con = list(vars(o).values())
            elif isinstance(o, (list, tuple, set, frozenset)):
                con = list(o)
            elif isinstance(o, dict):
                con = list(o.keys()) + list(o.values())
            else:
                con = []
            hang_doi += [c for c in con if not isinstance(c, (str, int, float, bool, type(None)))]
        return ket_qua

    assert root_khac_cham_toi(don) == set(), "đơn hàng ĐÚNG: không chạm tới root nào khác"
    assert root_khac_cham_toi(kh_dung) == set(), "khách hàng ĐÚNG: không chạm tới root nào khác"
    assert root_khac_cham_toi(kh_to) == {"DonHang"}, "khách hàng QUÁ TO: ôm luôn DonHang"
    # Đây là một bài test chạy được thật, đặt cạnh test nghiệp vụ trong CI. Nó bắt được
    # đúng thứ code review hay bỏ sót: một ngày nào đó ai đó thêm `self.khach_hang = kh`
    # vào `DonHang` "cho tiện", và ranh giới biến mất mà không ai để ý.

    # ---- 9. Bất biến nào KHÔNG được kéo vào ranh giới ----
    # Cám dỗ lớn nhất: "tổng nợ của khách hàng không quá 200 triệu" — nghe như một bất
    # biến, và nó kéo TOÀN BỘ đơn hàng vào trong KhachHang (phần 4).
    #
    # Câu hỏi phải hỏi tiếp: nếu luật đó bị vượt trong 5 giây rồi được sửa, công ty mất
    # gì? Với hạn mức nợ, thường là "không mất gì, gọi điện đòi là xong". Với tổng tiền
    # một đơn, là "xuất hoá đơn sai, phải huỷ".
    #
    #   Vượt trong chốc lát mà KHÔNG chấp nhận được -> bất biến thật -> chung aggregate
    #   Vượt trong chốc lát mà chấp nhận được       -> luật nghiệp vụ -> kiểm sau, tách ra
    #
    # Rất nhiều "bất biến" hoá ra thuộc loại thứ hai. Hỏi người làm nghiệp vụ, đừng đoán.
    assert DonHang.HAN_MUC == 50_000_000, "hạn mức MỘT ĐƠN: không được vượt dù một giây"

    # ---- 10. Bốn quy tắc rút gọn ----
    #   1. Ranh giới nằm ở nơi một bất biến phải đúng NGAY LẬP TỨC.
    #   2. Tham chiếu aggregate khác BẰNG ID, không giữ object.
    #   3. Một transaction sửa đúng MỘT aggregate.
    #   4. Nghi ngờ thì làm NHỎ. Aggregate nhỏ mà thiếu bất biến thì gộp lại được;
    #      aggregate to thì mọi thao tác đã đắt sẵn và tách ra rất khó.

    print("OK")
