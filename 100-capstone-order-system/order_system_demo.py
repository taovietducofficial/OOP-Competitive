# Ngôn ngữ: Python
# Công dụng: Bài tổng kết bản Python — một hệ đặt hàng nhỏ nhưng đầy đủ, ghép lại mọi thứ
# tầng 04-competitive đã dựng, và dùng đúng những công cụ mà chỉ Python mới có: `Protocol`
# cho cổng (bộ nối không cần biết cổng tồn tại), `frozen dataclass` cho value object và sự
# kiện, `property` không setter cho bất biến, và reflection để dựng bài test kiến trúc.
# Tại sao cần học: từng bài trước dạy MỘT thứ và cố tình bỏ qua phần còn lại. Bài này cho
# thấy chúng là MỘT thiết kế: aggregate cần ranh giới vì có bất biến; bất biến buộc phải
# tham chiếu bằng id; tham chiếu bằng id buộc phải có sự kiện; sự kiện buộc phải
# idempotent. Rút một mắt xích ra thì cả chuỗi lỏng.

from dataclasses import dataclass, field, fields
from enum import Enum, auto
from typing import Callable, Dict, List, Optional, Protocol, runtime_checkable


# =====================================================================
# MIỀN · VALUE OBJECT  (bài 82, 90)
# =====================================================================
class TienTe(Enum):
    VND = 0
    USD = 2


@dataclass(frozen=True)
class Tien:
    don_vi_nho: int
    tien_te: TienTe = TienTe.VND

    def __post_init__(self):
        if self.don_vi_nho < 0:
            raise ValueError("số tiền không âm")

    def _cung_te(self, k):
        if self.tien_te is not k.tien_te:
            raise ValueError("không cộng trừ khác tệ")

    def __add__(self, k):
        self._cung_te(k)
        return Tien(self.don_vi_nho + k.don_vi_nho, self.tien_te)

    def __sub__(self, k):
        self._cung_te(k)
        return Tien(self.don_vi_nho - k.don_vi_nho, self.tien_te)

    def phan_tram(self, pt):
        return Tien(self.don_vi_nho * pt // 100, self.tien_te)


@dataclass(frozen=True)
class MaDonHang:
    v: str


@dataclass(frozen=True)
class MaKhachHang:
    v: str


@dataclass(frozen=True)
class DongHang:
    san_pham: str
    don_gia: Tien
    so_luong: int

    def thanh_tien(self):
        return Tien(self.don_gia.don_vi_nho * self.so_luong, self.don_gia.tien_te)


# =====================================================================
# MIỀN · MÁY TRẠNG THÁI  (bài 89)
# =====================================================================
class TrangThai(Enum):
    MOI_TAO = auto()
    DA_THANH_TOAN = auto()
    DA_GIAO = auto()
    DA_HUY = auto()


class SuKienChuyen(Enum):
    THANH_TOAN = auto()
    GIAO = auto()
    HUY = auto()


BANG_CHUYEN = {
    (TrangThai.MOI_TAO, SuKienChuyen.THANH_TOAN): TrangThai.DA_THANH_TOAN,
    (TrangThai.MOI_TAO, SuKienChuyen.HUY): TrangThai.DA_HUY,
    (TrangThai.DA_THANH_TOAN, SuKienChuyen.GIAO): TrangThai.DA_GIAO,
    (TrangThai.DA_THANH_TOAN, SuKienChuyen.HUY): TrangThai.DA_HUY,
    # DA_GIAO, DA_HUY: không dòng nào -> mặc định là TỪ CHỐI.
}


# =====================================================================
# MIỀN · SỰ KIỆN  (bài 84) — bất biến, thì quá khứ
# =====================================================================
@dataclass(frozen=True)
class DonHangDaTao:
    ma_don: str
    ma_khach: str
    tong: int
    luc: int


@dataclass(frozen=True)
class DonHangDaGiao:
    ma_don: str
    tong_luc_giao: int
    luc: int


CAC_LOAI_SU_KIEN = (DonHangDaTao, DonHangDaGiao)


# =====================================================================
# MIỀN · AGGREGATE ROOT  (bài 83 ranh giới, 92 phiên bản, 84 ghi sự kiện)
# =====================================================================
class DonHang:
    HAN_MUC = Tien(50_000_000)

    def __init__(self, ma: MaDonHang, ma_khach: MaKhachHang, luc: int):
        self._ma, self._ma_khach = ma, ma_khach       # tham chiếu aggregate khác BẰNG ID
        self._cac_dong: List[DongHang] = []
        self._trang_thai = TrangThai.MOI_TAO
        self._phien_ban = 0
        self._su_kien_chua_phat = [DonHangDaTao(ma.v, ma_khach.v, 0, luc)]   # GHI, không PHÁT

    def them_dong(self, sp, don_gia, sl):
        sau = self.tong_tien() + Tien(don_gia.don_vi_nho * sl, don_gia.tien_te)
        if sau.don_vi_nho > DonHang.HAN_MUC.don_vi_nho:
            raise RuntimeError("đơn vượt hạn mức")                          # BẤT BIẾN — bài 83
        self._cac_dong.append(DongHang(sp, don_gia, sl))
        self._phien_ban += 1

    def chuyen(self, sk):
        ke = BANG_CHUYEN.get((self._trang_thai, sk))
        if ke is None:
            raise RuntimeError(f"không {sk.name} được ở {self._trang_thai.name}")
        self._trang_thai = ke
        self._phien_ban += 1

    def giao(self, luc):
        self.chuyen(SuKienChuyen.GIAO)                # ném thì KHÔNG tới dòng dưới
        self._su_kien_chua_phat.append(DonHangDaGiao(self._ma.v, self.tong_tien().don_vi_nho, luc))

    def tong_tien(self):
        t = Tien(0)
        for d in self._cac_dong:
            t = t + d.thanh_tien()
        return t

    # `property` không setter: cách DUY NHẤT đổi trạng thái là qua hành vi có tên (bài 86)
    @property
    def ma(self):
        return self._ma

    @property
    def ma_khach(self):
        return self._ma_khach

    @property
    def trang_thai(self):
        return self._trang_thai

    @property
    def phien_ban(self):
        return self._phien_ban

    @property
    def so_dong(self):
        return len(self._cac_dong)

    @property
    def cac_dong(self):
        return tuple(self._cac_dong)                  # cửa đóng — bài 83 phần 7

    def lay_va_xoa_su_kien(self):
        ds, self._su_kien_chua_phat = self._su_kien_chua_phat, []
        return ds


# =====================================================================
# MIỀN · SPECIFICATION (bài 87) và POLICY (bài 88)
# =====================================================================
@dataclass(frozen=True)
class KetQuaDacTa:
    dat: bool
    ly_do_truot: tuple


def duoc_giam_gia(d: DonHang) -> KetQuaDacTa:
    ly_do = []
    if d.tong_tien().don_vi_nho < 1_000_000:
        ly_do.append("đơn từ 1.000.000 trở lên")
    if d.so_dong < 2:
        ly_do.append("từ 2 dòng hàng trở lên")
    return KetQuaDacTa(not ly_do, tuple(ly_do))


class QuocGia(Enum):
    VN = auto()
    US = auto()


THUE_PHAN_TRAM = {QuocGia.VN: 10, QuocGia.US: 0}     # 0% CÓ TÊN, không phải thiếu cấu hình


# =====================================================================
# MIỀN · CỔNG  (bài 98) — `Protocol`: bộ nối không cần biết cổng tồn tại
# =====================================================================
@runtime_checkable
class KhoDonHang(Protocol):
    def tim_theo_ma(self, ma: str) -> Optional[DonHang]: ...
    def luu(self, d: DonHang, phien_ban_ky_vong: int) -> int: ...


@runtime_checkable
class BaoChoKhach(Protocol):
    def bao(self, kh: str, noi_dung: str) -> None: ...


# =====================================================================
# HẠ TẦNG · BỘ NỐI + MÔ HÌNH ĐỌC (bài 95). Chú ý: KHÔNG lớp nào kế thừa gì.
# =====================================================================
@dataclass(frozen=True)
class DongDanhSachDon:
    ma_don: str
    ma_khach: str
    so_dong: int
    tong_tien: int
    trang_thai: str


class KhoTrongBoNho:
    def __init__(self):
        self.bang: Dict[str, DonHang] = {}
        self.phien_ban: Dict[str, int] = {}
        self.so_luot_truy_van = 0
        self.so_lan_dung_do = 0

    def tim_theo_ma(self, ma):
        self.so_luot_truy_van += 1
        return self.bang.get(ma)

    def luu(self, d, phien_ban_ky_vong):
        self.so_luot_truy_van += 1
        hien_tai = self.phien_ban.get(d.ma.v)
        if hien_tai is not None and hien_tai != phien_ban_ky_vong:
            self.so_lan_dung_do += 1
            return 0                                  # bài 92
        self.bang[d.ma.v] = d
        self.phien_ban[d.ma.v] = phien_ban_ky_vong
        return 1

    def danh_sach(self):
        """Đường ĐỌC: một truy vấn, mô hình phẳng — bài 95."""
        self.so_luot_truy_van += 1
        return [DongDanhSachDon(d.ma.v, d.ma_khach.v, d.so_dong,
                                d.tong_tien().don_vi_nho, d.trang_thai.name)
                for d in self.bang.values()]


class BaoGia:
    def __init__(self):
        self.da_bao = []

    def bao(self, kh, noi_dung):
        self.da_bao.append(f"{kh}:{noi_dung}")


# ---- Self-check ----
if __name__ == "__main__":
    kho, bao = KhoTrongBoNho(), BaoGia()
    da_phat: List[object] = []
    so_idempotency: Dict[str, int] = {}
    dem = {"xu_ly": 0}
    dong_ho: Callable[[], int] = lambda: 1_700_000_000

    # Tầng ỨNG DỤNG (bài 86): điều phối, không tính luật nào của miền.
    def dat_hang(khoa, ma_khach, quoc_gia, gio, luu_that_bai=False):
        if khoa in so_idempotency:
            return so_idempotency[khoa]               # phát lại KẾT QUẢ CŨ — bài 91
        dem["xu_ly"] += 1
        d = DonHang(MaDonHang(f"DH-{dem['xu_ly']}"), MaKhachHang(ma_khach), dong_ho())
        for x in gio:
            d.them_dong(x.san_pham, x.don_gia, x.so_luong)

        dt = duoc_giam_gia(d)
        giam = d.tong_tien().phan_tram(5) if dt.dat else Tien(0)
        sau_giam = d.tong_tien() - giam
        phai_tra = sau_giam + sau_giam.phan_tram(THUE_PHAN_TRAM[quoc_gia])

        if luu_that_bai:
            raise RuntimeError("CSDL hỏng")
        kho.luu(d, d.phien_ban)
        # LƯU XONG rồi mới phát sự kiện (bài 84) — không sớm hơn một dòng.
        da_phat.extend(d.lay_va_xoa_su_kien())
        bao.bao(ma_khach, f"đã tạo đơn {d.ma.v}")

        so_idempotency[khoa] = phai_tra.don_vi_nho
        return phai_tra.don_vi_nho

    gio_hang = [DongHang("laptop", Tien(20_000_000), 1), DongHang("chuột", Tien(500_000), 2)]

    # ---- 1. ĐƯỜNG THUẬN LỢI, ĐẦU TỚI CUỐI ----
    phai_tra = dat_hang("KEY-1", "KH-01", QuocGia.VN, gio_hang)
    # 21.000.000 -> giảm 5% = 1.050.000 -> còn 19.950.000 -> +10% thuế = 21.945.000
    assert phai_tra == 21_945_000, "giảm giá TRƯỚC, thuế SAU (bài 87 + 88)"
    assert len(kho.bang) == 1, "đơn đã được lưu qua CỔNG (bài 98)"
    assert len(bao.da_bao) == 1, "khách được báo ở tầng ỨNG DỤNG (bài 86)"
    assert len(da_phat) == 1 and isinstance(da_phat[0], DonHangDaTao), \
        "sự kiện được PHÁT SAU KHI LƯU (bài 84)"
    assert isinstance(kho, KhoDonHang) and isinstance(bao, BaoChoKhach), \
        "bộ nối thoả CỔNG mà KHÔNG kế thừa gì — `Protocol` (bài 98)"

    # ---- 2. BẤT BIẾN CỦA AGGREGATE (bài 83) ----
    don = kho.tim_theo_ma("DH-1")
    vuot = False
    try:
        don.them_dong("máy chủ", Tien(40_000_000), 1)
    except RuntimeError:
        vuot = True
    assert vuot and don.tong_tien().don_vi_nho == 21_000_000, "vượt hạn mức bị chặn, dữ liệu nguyên vẹn"
    khong_them_duoc = False
    try:
        don.cac_dong.append(DongHang("lén", Tien(1), 1))
    except AttributeError:
        khong_them_duoc = True
    assert khong_them_duoc, "cửa aggregate đóng: `tuple` không thêm được"
    khong_gan_duoc = False
    try:
        don.trang_thai = TrangThai.DA_GIAO
    except AttributeError:
        khong_gan_duoc = True
    assert khong_gan_duoc, "`property` không setter -> máy trạng thái không bị vượt mặt (bài 86)"

    # ---- 3. MÁY TRẠNG THÁI (bài 89) ----
    khong_giao_duoc = False
    try:
        don.giao(1)
    except RuntimeError:
        khong_giao_duoc = True
    assert khong_giao_duoc, "chưa thanh toán thì chưa giao — mặc định là TỪ CHỐI"
    don.chuyen(SuKienChuyen.THANH_TOAN)
    don.giao(1_700_000_100)
    assert don.trang_thai is TrangThai.DA_GIAO, "và đường hợp lệ thì đi được"
    assert len(don.lay_va_xoa_su_kien()) == 1, "chuyển trạng thái GHI sự kiện, không phát"

    # ---- 4. TIỀN TỆ (bài 90) ----
    khac_te = False
    try:
        Tien(1, TienTe.VND) + Tien(1, TienTe.USD)
    except ValueError:
        khac_te = True
    assert khac_te, "cộng khác tệ bị chặn"
    assert 0.1 + 0.2 != 0.3, "và đó là lý do không dùng float cho tiền"
    assert isinstance(Tien(1).don_vi_nho, int), "số nguyên đơn vị nhỏ nhất — chính xác tuyệt đối"

    # ---- 5. SPECIFICATION GIẢI THÍCH ĐƯỢC (bài 87) ----
    nho = dat_hang("KEY-2", "KH-02", QuocGia.VN, [DongHang("bút", Tien(10_000), 1)])
    assert nho == 11_000, "đơn nhỏ: không giảm, chỉ +10% thuế"
    tam = DonHang(MaDonHang("TMP"), MaKhachHang("X"), 0)
    tam.them_dong("bút", Tien(10_000), 1)
    dt = duoc_giam_gia(tam)
    assert not dt.dat and len(dt.ly_do_truot) == 2, "và nói rõ TRƯỢT Ở HAI mệnh đề nào"
    assert dt.ly_do_truot[1] == "từ 2 dòng hàng trở lên", "dán thẳng vào thông báo"

    # ---- 6. POLICY THEO QUỐC GIA (bài 88) ----
    my_quoc = dat_hang("KEY-3", "KH-03", QuocGia.US, gio_hang)
    assert my_quoc == 19_950_000, "Mỹ: giảm 5%, không thuế"
    assert [q for q in QuocGia if q not in THUE_PHAN_TRAM] == [], "đủ chính sách cho mọi quốc gia"

    # ---- 7. IDEMPOTENCY (bài 91) ----
    truoc = dem["xu_ly"]
    lai = dat_hang("KEY-1", "KH-01", QuocGia.VN, gio_hang)
    assert dem["xu_ly"] == truoc, "gửi lại cùng khoá: KHÔNG xử lý lần nữa"
    assert lai == phai_tra, "và trả về ĐÚNG kết quả cũ, không phải lỗi 'đã xử lý'"
    assert len(kho.bang) == 3, "vẫn đúng 3 đơn, không sinh đơn thứ tư"

    # ---- 8. KHOÁ LẠC QUAN (bài 92) ----
    d2 = kho.tim_theo_ma("DH-2")
    assert kho.luu(d2, d2.phien_ban) == 1, "ghi với phiên bản đúng: 1 dòng"
    assert kho.luu(d2, d2.phien_ban - 1) == 0, "ghi với phiên bản CŨ: 0 DÒNG — đụng độ"
    assert kho.so_lan_dung_do == 1, "và đụng độ được ĐẾM, không im lặng"

    # ---- 9. SỰ KIỆN PHÁT SAU COMMIT (bài 84) ----
    email_truoc, su_kien_truoc = len(bao.da_bao), len(da_phat)
    hong = False
    try:
        dat_hang("KEY-9", "KH-09", QuocGia.VN, gio_hang, luu_that_bai=True)
    except RuntimeError:
        hong = True
    assert hong, "lưu hỏng"
    assert len(bao.da_bao) == email_truoc, "-> 0 email được gửi"
    assert len(da_phat) == su_kien_truoc, "-> 0 sự kiện rời khỏi tiến trình"

    # ---- 10. MÔ HÌNH ĐỌC (bài 95) ----
    tv_truoc = kho.so_luot_truy_van
    danh_sach = kho.danh_sach()
    assert kho.so_luot_truy_van - tv_truoc == 1, "màn hình danh sách: ĐÚNG MỘT lượt truy vấn"
    assert len(danh_sach) == 3 and danh_sach[0].ma_khach == "KH-01", \
        "và mô hình đọc GHÉP hai aggregate — điều bên ghi bị cấm"

    # ---- 11. BÀI TEST KIẾN TRÚC (bài 81, 93, 94, 98) ----
    HA_TANG = {KhoTrongBoNho, BaoGia, DongDanhSachDon}
    MIEN = [DonHang, Tien, DongHang, KetQuaDacTa]
    vi_pham = [f"{lop.__name__}.{ten}" for lop in MIEN
               for ten, gt in vars(lop).items() if isinstance(gt, type) and gt in HA_TANG]
    assert vi_pham == [], f"0 tham chiếu từ MIỀN ra HẠ TẦNG: {vi_pham}"
    for loai in CAC_LOAI_SU_KIEN:
        assert "Da" in loai.__name__, f"tên sự kiện ở thì quá khứ: {loai.__name__} (bài 81)"
        assert loai.__dataclass_params__.frozen, f"sự kiện phải bất biến: {loai.__name__}"
    assert all(f.type is not dict for f in fields(DongDanhSachDon)), \
        "mô hình đọc có KIỂU, không phải `dict` trần (bài 94)"

    # ---- 12. VÌ SAO 20 BÀI NÀY LÀ MỘT THIẾT KẾ, KHÔNG PHẢI 20 MẪU ----
    #
    #   Có BẤT BIẾN "tổng ≤ hạn mức"        -> phải có RANH GIỚI aggregate    (83)
    #   Ranh giới -> tham chiếu BẰNG ID     -> hai aggregate không nói trực tiếp
    #   Không nói trực tiếp                 -> phải có SỰ KIỆN MIỀN            (84)
    #   Sự kiện giao ít nhất một lần        -> người nghe phải IDEMPOTENT      (91)
    #   Một transaction một aggregate       -> quy trình nhiều bước cần SAGA   (97)
    #   Nhiều người cùng sửa                -> cần KHOÁ LẠC QUAN               (92)
    #   Aggregate tải trọn vẹn              -> màn hình danh sách cần CQRS     (95)
    #   Luật đổi theo ngữ cảnh              -> POLICY, không phải if-else      (88)
    #   Luật cần giải thích + dịch sang SQL -> SPECIFICATION                   (87)
    #   Miền phải test được không CSDL      -> CỔNG & BỘ NỐI                   (98)
    #   Test không CSDL                     -> test miền chỉ là hàm + assert   (99)
    #
    # Rút một mắt xích ra thì mắt kế bên mất lý do tồn tại. Đó là điều mà học từng mẫu
    # thiết kế riêng lẻ không bao giờ nói cho bạn.
    assert dem["xu_ly"] == 4 and len(kho.bang) == 3, \
        "4 lần vào xử lý, 3 đơn được lưu — lần thứ tư hỏng và KHÔNG để lại gì"

    print("OK")
