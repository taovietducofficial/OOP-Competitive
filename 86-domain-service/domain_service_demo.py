# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — domain service là chỗ đặt hành vi không
# thuộc về entity nào; ba loại "service" phải phân biệt; và hai lỗi đối xứng: mô hình
# THIẾU MÁU và nhét hành vi liên-aggregate vào một entity.
# Tại sao cần học: Python là ngôn ngữ dễ rơi vào mô hình thiếu máu nhất trong ba ngôn
# ngữ — `@dataclass` cho ra một entity toàn field public trong ba dòng, và gạch dưới
# `_so_du` không chặn được ai. Nhưng Python cũng cho công cụ phòng thủ tốt nhất:
# `@property` không kèm setter làm cho `tk.so_du = -500_000` ném lỗi NGAY, và `vars()`
# cho phép ĐO tỉ lệ thiếu máu của mô hình bằng một bài test chạy trong CI. Bài dựng
# đúng hai thứ đó.

from dataclasses import dataclass


# =====================================================================
# SAI 1 — MÔ HÌNH THIẾU MÁU: dataclass toàn field public + hàm rời
# =====================================================================
@dataclass
class TaiKhoanThieuMau:
    ma: str
    so_du: int          # <- cửa mở toang, và ở Python không cần viết setter nào


def rut_thieu_mau(tk, tien):
    """Luật "không được âm" nằm ở ĐÂY, nên nó chỉ có hiệu lực với ai đi qua đây."""
    if tk.so_du < tien:
        raise RuntimeError("không đủ số dư")
    tk.so_du -= tien


# =====================================================================
# ĐÚNG — entity giữ luật của chính nó
# =====================================================================
class TaiKhoan:
    def __init__(self, ma, so_du):
        if so_du < 0:
            raise ValueError("số dư ban đầu không âm")
        self._ma, self._so_du = ma, so_du

    # Luật nằm TRONG entity -> không có đường vòng nào.
    def rut(self, tien):
        if tien <= 0:
            raise ValueError("số tiền rút phải dương")
        if self._so_du < tien:
            raise RuntimeError("không đủ số dư")
        self._so_du -= tien

    def nap(self, tien):
        if tien <= 0:
            raise ValueError("số tiền nạp phải dương")
        self._so_du += tien

    # `property` KHÔNG kèm setter: đọc được, gán vào là lỗi. Đây là thứ gần nhất với
    # `private` mà Python có, và nó đủ để chặn đường vòng.
    @property
    def ma(self):
        return self._ma

    @property
    def so_du(self):
        return self._so_du


@dataclass(frozen=True)
class BieuPhi:
    """Bảng phí là VALUE OBJECT truyền vào, không phải repository được tiêm."""
    nguong: int
    phi_thap: int
    phi_cao: int

    def tinh_phi(self, so_tien):
        return self.phi_thap if so_tien <= self.nguong else self.phi_cao


@dataclass(frozen=True)
class BienLaiChuyenTien:
    tu_tai_khoan: str
    den_tai_khoan: str
    so_tien: int
    phi: int


# =====================================================================
# DOMAIN SERVICE — ở Python nó là một HÀM Ở MỨC MODULE, không phải một lớp
# =====================================================================
def chuyen_tien(tu: TaiKhoan, den: TaiKhoan, so_tien: int, bieu_phi: BieuPhi):
    """Chuyển tiền dính tới HAI tài khoản ngang nhau. Đặt nó vào `TaiKhoan.chuyen_toi()`
    là bắt một aggregate sửa một aggregate khác — đúng thứ bài 83 cấm. Đặt nó vào tầng
    ứng dụng thì luật tính phí (một luật NGHIỆP VỤ) rời khỏi miền.

    Hàm này không có biến toàn cục, không đọc đồng hồ, không chạm CSDL. Trạng thái bằng
    0 không phải vì kỷ luật — vì nó là một HÀM, nó không có chỗ nào để mà cất trạng thái.
    """
    if tu.ma == den.ma:
        raise ValueError("không chuyển cho chính mình")
    phi = bieu_phi.tinh_phi(so_tien)
    tu.rut(so_tien + phi)       # mỗi entity vẫn tự giữ luật của nó
    den.nap(so_tien)
    return BienLaiChuyenTien(tu.ma, den.ma, so_tien, phi)


# =====================================================================
# Ba loại "service" — thứ hay bị gộp làm một
# =====================================================================
class KhoTaiKhoan:                       # cổng (port) của MIỀN
    def tim(self, ma):
        raise NotImplementedError

    def luu(self, tk):
        raise NotImplementedError


class GuiThongBao:                       # HẠ TẦNG
    def gui(self, ma, noi_dung):
        raise NotImplementedError


class UngDungChuyenTien:
    """Ứng dụng: điều phối. Tải, gọi miền, lưu, phát thông báo. Không tính luật nào."""

    def __init__(self, kho, thong_bao):
        self.kho, self.thong_bao = kho, thong_bao
        self.so_lan_goi_mien = 0

    def thuc_hien(self, ma_tu, ma_den, so_tien):
        tu, den = self.kho.tim(ma_tu), self.kho.tim(ma_den)
        self.so_lan_goi_mien += 1
        bl = chuyen_tien(tu, den, so_tien, BieuPhi(1_000_000, 1_000, 5_000))
        self.kho.luu(tu)
        self.kho.luu(den)
        self.thong_bao.gui(ma_tu, f"đã chuyển {so_tien}")
        return bl


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. MÔ HÌNH THIẾU MÁU: luật ở ngoài thì luật bị lách ----
    tm = TaiKhoanThieuMau("TK-01", 100_000)
    chan = False
    try:
        rut_thieu_mau(tm, 200_000)
    except RuntimeError:
        chan = True
    assert chan, "đi qua hàm dịch vụ thì luật có hiệu lực..."

    tm.so_du = -500_000      # ...và đây là đường vòng, mở sẵn cho tất cả mọi người
    assert tm.so_du == -500_000, "số dư ÂM, không ai chặn, không ngoại lệ"
    # Ở Python cái bẫy này dễ mắc nhất trong ba ngôn ngữ: `@dataclass` cho ra một entity
    # toàn field public trong ba dòng, và nó TRÔNG như một mô hình miền tử tế.

    tk = TaiKhoan("TK-01", 100_000)
    chan = False
    try:
        tk.rut(200_000)
    except RuntimeError:
        chan = True
    assert chan and tk.so_du == 100_000, "luật nằm TRONG entity -> không có đường vòng"

    khong_gan_duoc = False
    try:
        tk.so_du = -500_000
    except AttributeError:
        khong_gan_duoc = True
    assert khong_gan_duoc, "`property` không kèm setter: gán vào là lỗi NGAY"
    # Lưu ý ranh giới: `tk._so_du = -500_000` thì VẪN chạy (bài 83 phần 7). `property`
    # không phải khoá — nó là một cái cửa có biển "lối này", và nó chặn được đúng thứ cần
    # chặn: người viết code bình thường, đang vội, không cố tình phá.

    # ---- 2. PHÉP ĐO CHẠY ĐƯỢC: mô hình của bạn thiếu máu bao nhiêu phần trăm? ----
    def ti_le_hanh_vi(lop):
        """Tỉ lệ phương thức thật sự là HÀNH VI, trên tổng số thành viên công khai."""
        cong_khai = [t for t in vars(lop) if not t.startswith("_")]
        hanh_vi = [t for t in cong_khai if callable(vars(lop)[t])]
        return len(hanh_vi), len(cong_khai)

    assert ti_le_hanh_vi(TaiKhoanThieuMau)[0] == 0, "TaiKhoanThieuMau: 0 hành vi"
    hv, tong = ti_le_hanh_vi(TaiKhoan)
    assert hv == 2 and tong == 4, "TaiKhoan: 2 hành vi (rút/nạp) + 2 property chỉ đọc"
    assert not any(isinstance(vars(TaiKhoan)[t], property) and vars(TaiKhoan)[t].fset
                   for t in vars(TaiKhoan) if isinstance(vars(TaiKhoan)[t], property)), \
        "không property nào có setter — mọi thay đổi đi qua một hành vi có tên"
    # Ba dòng trên là một bài test kiến trúc chạy được: quét các lớp trong gói `mien/`,
    # fail nếu có lớp nào 0 hành vi hoặc có property kèm setter. Nó bắt đúng thời điểm
    # mô hình bắt đầu trượt về phía lược đồ CSDL đội lốt object.

    # ---- 3. KHI NÀO THÌ THẬT SỰ CẦN DOMAIN SERVICE ----
    # Ba câu hỏi, phải trả lời CÓ cả ba:
    #   (a) Hành vi này có phải LUẬT NGHIỆP VỤ không? (không phải điều phối, không phải I/O)
    #   (b) Nó có thuộc về đúng MỘT entity không? — nếu CÓ thì đặt vào entity đó, xong.
    #   (c) Ép nó vào một entity có làm entity đó phải sửa entity khác không?
    # "Chuyển tiền" trả lời: (a) có, (b) KHÔNG — hai tài khoản ngang nhau, (c) có.
    # => domain service.
    a, b = TaiKhoan("TK-A", 5_000_000), TaiKhoan("TK-B", 0)
    bl = chuyen_tien(a, b, 2_000_000, BieuPhi(1_000_000, 1_000, 5_000))
    assert bl.phi == 5_000, "trên 1 triệu -> phí cao"
    assert a.so_du == 2_995_000, "trừ cả tiền lẫn phí"
    assert b.so_du == 2_000_000, "bên nhận không chịu phí"

    # ---- 4. DOMAIN SERVICE KHÔNG CÓ CHỖ ĐỂ CẤT TRẠNG THÁI ----
    # `chuyen_tien` là một hàm mức module. Không `self`, không field, không constructor
    # để ai đó tiêm một repository vào. Muốn nó biết biểu phí thì phải TRUYỀN biểu phí.
    x, y = TaiKhoan("TK-X", 10_000), TaiKhoan("TK-Y", 0)
    assert chuyen_tien(x, y, 5_000, BieuPhi(1_000_000, 1_000, 5_000)).phi == 1_000, \
        "test domain service: hai dòng, không hạ tầng nào"
    assert chuyen_tien.__closure__ is None, "hàm không bắt biến ngoài — thật sự không trạng thái"
    # Nếu một ngày ai đó cần thêm repository vào domain service, đó là tín hiệu rõ ràng:
    # hoặc nó là application service đội lốt, hoặc dữ liệu nó cần phải được TRUYỀN VÀO.

    # ---- 5. CẠM BẪY RIÊNG CỦA PYTHON: lớp toàn `@staticmethod` ----
    class DichVuChuyenTien:          # <- KHÔNG nên viết thế này
        @staticmethod
        def chuyen(tu, den, so_tien, bieu_phi):
            return chuyen_tien(tu, den, so_tien, bieu_phi)

    assert DichVuChuyenTien.chuyen is not None, "chạy được, nhưng lớp này thừa"
    # Một lớp chỉ chứa `@staticmethod` là một namespace đội lốt object — Python đã có
    # namespace, tên nó là MODULE. Lớp thừa này gây ba hại nhỏ mà cộng lại thì lớn:
    #   1. Dụ người sau thêm `__init__(self, repo)` vì "đã là lớp rồi thì tiêm vào cho tiện";
    #   2. Làm cho hàm khó import lẻ và khó thay thế trong test;
    #   3. Nói dối về bản chất — domain service KHÔNG phải một object, nó là một phép tính.
    # Ở Java bạn buộc phải có lớp; ở Python thì không, nên đừng tự trói mình.

    # ---- 6. BA LOẠI SERVICE — bảng phân biệt ----
    #
    #                | Domain service      | Application service   | Infrastructure
    #   -------------|---------------------|-----------------------|----------------
    #   trả lời       | "luật là gì?"       | "quy trình là gì?"    | "làm thế nào?"
    #   ví dụ         | chuyen_tien()       | UngDungChuyenTien     | GuiThongBao
    #   có trạng thái | KHÔNG               | không                 | thường có
    #   chạm I/O      | KHÔNG               | có (qua interface)    | CÓ
    #   mở transaction| KHÔNG               | CÓ                    | không
    #   nằm ở tầng    | miền                | ứng dụng              | hạ tầng
    #   test cần gì   | không cần gì        | fake (bài 68)         | môi trường thật
    #
    # Sai lầm phổ biến nhất: gộp cột 1 và cột 2 thành một lớp `OrderService` dài 800
    # dòng, vừa mở transaction vừa tính luật vừa gửi email.
    class KhoBoNho(KhoTaiKhoan):
        def __init__(self):
            self.m = {}

        def tim(self, ma):
            return self.m.get(ma)

        def luu(self, tk):
            self.m[tk.ma] = tk

    class ThongBaoGia(GuiThongBao):
        def __init__(self):
            self.da_gui = []

        def gui(self, ma, noi_dung):
            self.da_gui.append(f"{ma}:{noi_dung}")

    kho, tb = KhoBoNho(), ThongBaoGia()
    kho.luu(TaiKhoan("TK-A", 5_000_000))
    kho.luu(TaiKhoan("TK-B", 0))
    ud = UngDungChuyenTien(kho, tb)
    ud.thuc_hien("TK-A", "TK-B", 500_000)
    assert kho.tim("TK-A").so_du == 4_499_000, "500.000 + phí thấp 1.000"
    assert len(tb.da_gui) == 1, "tầng ứng dụng lo thông báo — miền không biết email tồn tại"
    assert ud.so_lan_goi_mien == 1, "và nó gọi miền đúng một lần, không tự tính luật"

    # ---- 7. CẠM BẪY: `XxxService` thành thùng rác ----
    # Dấu hiệu nhận biết, theo thứ tự nặng dần:
    #   1. Tên là danh từ chung: `OrderService`, `UserManager`, `DataHandler` (bài 81).
    #   2. Nó có field là repository VÀ đồng thời chứa luật nghiệp vụ.
    #   3. Nó có phương thức thứ 15.
    #   4. Entity tương ứng chỉ còn field public (phép đo ở phần 2).
    # Domain service tốt thường là ĐÚNG MỘT hàm và tên là một ĐỘNG TỪ nghiệp vụ:
    # `chuyen_tien`, `tinh_lai_suat`, `kiem_tra_trung_lap`.

    # ---- 8. RANH GIỚI: khi nào KHÔNG cần domain service ----
    # Cám dỗ ngược lại cũng có thật: tạo `rut_tien(tk, tien)` cho việc `tk.rut(tien)`.
    # Câu hỏi (b) ở phần 3 trả lời CÓ — hành vi thuộc về đúng một entity — nên nó phải
    # nằm trong entity, và một hàm rời ở đây chỉ thêm một lớp vô nghĩa.
    #
    # Quy tắc: domain service là NGOẠI LỆ, không phải mặc định. Nếu miền của bạn có
    # nhiều service hơn entity, thì bạn đang viết mô hình thiếu máu và gọi nó là DDD.

    print("OK")
