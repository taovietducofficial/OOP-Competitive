# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — máy trạng thái đơn hàng, con bug "đơn đã huỷ
# vẫn được giao", và hành vi (phí huỷ) đổi theo trạng thái.
# Tại sao cần học: Python không có compiler để chặn gì cả, nên bài này thêm hai con bug
# mà chỉ Python mới mắc dễ đến thế: trạng thái viết bằng CHUỖI (`"da_giao"` khác
# `"DA_GIAO"`, và câu lệnh bảo vệ im lặng không bao giờ nổ), và trạng thái gán thẳng từ
# bên ngoài (`don.trang_thai = ...` vượt mặt toàn bộ máy trạng thái). Bù lại, Python đọc
# được bảng chuyển lúc chạy, nên phần 6 phân tích đồ thị — trạng thái mồ côi, ngõ cụt —
# và phần 7 SINH RA SƠ ĐỒ máy trạng thái từ chính bảng đó, để tài liệu không bao giờ
# lệch khỏi code.

from enum import Enum, auto


class TrangThai(Enum):
    MOI_TAO = auto()
    DA_THANH_TOAN = auto()
    DA_GIAO = auto()
    DA_HUY = auto()


class SuKien(Enum):
    THANH_TOAN = auto()
    GIAO = auto()
    HUY = auto()


# =====================================================================
# BẢNG CHUYỂN — chỗ DUY NHẤT trả lời "được làm gì ở trạng thái nào"
# =====================================================================
BANG_CHUYEN = {
    (TrangThai.MOI_TAO, SuKien.THANH_TOAN): TrangThai.DA_THANH_TOAN,
    (TrangThai.MOI_TAO, SuKien.HUY): TrangThai.DA_HUY,
    (TrangThai.DA_THANH_TOAN, SuKien.GIAO): TrangThai.DA_GIAO,
    (TrangThai.DA_THANH_TOAN, SuKien.HUY): TrangThai.DA_HUY,
    # DA_GIAO và DA_HUY: KHÔNG có dòng nào -> hai trạng thái kết thúc.
}

# Phí huỷ đổi theo trạng thái. `None` = không huỷ được.
PHI_HUY_PHAN_TRAM = {
    TrangThai.MOI_TAO: 0,          # chưa trả tiền -> huỷ miễn phí
    TrangThai.DA_THANH_TOAN: 10,   # đã trả -> phí 10%
    TrangThai.DA_GIAO: None,
    TrangThai.DA_HUY: None,
}


# =====================================================================
# AGGREGATE — trạng thái đổi CHỈ qua hành vi có tên
# =====================================================================
class DonHang:
    def __init__(self, ma, tong_tien):
        self.ma, self.tong_tien = ma, tong_tien
        self._trang_thai = TrangThai.MOI_TAO

    @property
    def trang_thai(self):
        return self._trang_thai      # đọc được, gán vào là AttributeError

    def _chuyen(self, su_kien):
        ke = BANG_CHUYEN.get((self._trang_thai, su_kien))
        if ke is None:
            raise RuntimeError(f"không {su_kien.name} được ở trạng thái {self._trang_thai.name}")
        self._trang_thai = ke

    def thanh_toan(self):
        self._chuyen(SuKien.THANH_TOAN)

    def giao(self):
        self._chuyen(SuKien.GIAO)

    def huy(self):
        pt = PHI_HUY_PHAN_TRAM[self._trang_thai]
        if pt is None:
            raise RuntimeError(f"không huỷ được ở {self._trang_thai.name}")
        phi = self.tong_tien * pt // 100      # hỏi phí TRƯỚC khi đổi trạng thái
        self._chuyen(SuKien.HUY)
        return phi


# =====================================================================
# BẢN SAI 1 — máy trạng thái viết bằng `if` rời rạc, trạng thái là số
# =====================================================================
class DonHangIf:
    def __init__(self):
        self.trang_thai = 1      # 1=mới, 2=đã thanh toán, 3=đã giao, 4=đã huỷ

    def thanh_toan(self):
        if self.trang_thai != 1:
            raise RuntimeError("sai trạng thái")
        self.trang_thai = 2

    def giao(self):
        # Ở đây ĐÁNG LẼ phải có: if self.trang_thai != 2: raise ...
        # Người viết nghĩ "chỉ đơn đã thanh toán mới gọi giao()" và bỏ qua.
        self.trang_thai = 3

    def huy(self):
        if self.trang_thai == 3:
            raise RuntimeError("đã giao thì không huỷ")
        self.trang_thai = 4


# =====================================================================
# BẢN SAI 2 — trạng thái là CHUỖI
# =====================================================================
class DonHangChuoi:
    def __init__(self):
        self.trang_thai = "MOI_TAO"

    def giao(self):
        if self.trang_thai == "da_huy":      # <- viết thường; giá trị thật là "DA_HUY"
            raise RuntimeError("đã huỷ thì không giao")
        self.trang_thai = "DA_GIAO"

    def huy(self):
        self.trang_thai = "DA_HUY"


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: đơn ĐÃ HUỶ vẫn được giao ----
    sai = DonHangIf()
    sai.thanh_toan()
    sai.huy()
    assert sai.trang_thai == 4, "đơn đã huỷ, khách đã được hoàn tiền"
    sai.giao()                                   # không ai chặn
    assert sai.trang_thai == 3, "và hàng vẫn được giao đi — công ty mất cả hàng lẫn tiền"
    # Hình dạng phổ biến nhất của bug máy trạng thái: KHÔNG phải một điều kiện sai, mà là
    # một điều kiện KHÔNG TỒN TẠI. Đọc `giao()` ở trên, không có gì trông sai cả — chỉ có
    # một dòng không có ở đó.

    # ---- 2. CON BUG RIÊNG CỦA PYTHON: trạng thái là chuỗi ----
    chuoi = DonHangChuoi()
    chuoi.huy()
    assert chuoi.trang_thai == "DA_HUY", "đã huỷ"
    chuoi.giao()                                 # câu bảo vệ SO SÁNH SAI CHỮ HOA/THƯỜNG
    assert chuoi.trang_thai == "DA_GIAO", "vẫn giao — vì `\"DA_HUY\" == \"da_huy\"` là False"
    # Câu lệnh bảo vệ CÓ ở đó, nhìn thì đúng, và nó không bao giờ nổ. Với `Enum`, dòng
    # tương đương gõ sai là `AttributeError` ngay lập tức:
    go_sai = False
    try:
        _ = TrangThai.da_huy
    except AttributeError:
        go_sai = True
    assert go_sai, "gõ sai tên hằng enum là nổ NGAY, không âm thầm trả False"
    # Đây là lý do trạng thái không bao giờ nên là chuỗi trong mã nghiệp vụ. Chuỗi chỉ
    # sống ở BIÊN (JSON, CSDL) và được đổi sang enum ngay tại đó (bài 76, bài 78).

    # ---- 3. BẢN ĐÚNG: mặc định là TỪ CHỐI ----
    don = DonHang("DH-01", 1_000_000)
    don.thanh_toan()
    assert don.huy() == 100_000, "huỷ sau khi trả tiền: phí 10%"
    assert don.trang_thai is TrangThai.DA_HUY, "đã huỷ"

    chan = False
    try:
        don.giao()
    except RuntimeError:
        chan = True
    assert chan, "giao một đơn đã huỷ -> NỔ, và không ai phải nhớ viết `if`"
    assert don.trang_thai is TrangThai.DA_HUY, "trạng thái không hề bị sửa dở dang"
    # `DA_HUY` không có dòng nào trong `BANG_CHUYEN`. Người viết nó không phải nghĩ tới
    # việc "cấm giao"; họ chỉ cần KHÔNG viết gì cả. Đó là khác biệt giữa "an toàn nếu
    # nhớ" và "an toàn mặc định".

    # ---- 4. HÀNH VI ĐỔI THEO TRẠNG THÁI, KHÔNG CHỈ CÓ CHUYỂN TIẾP ----
    chua_tra = DonHang("DH-02", 1_000_000)
    assert chua_tra.huy() == 0, "chưa trả tiền -> huỷ miễn phí"
    da_tra = DonHang("DH-03", 1_000_000)
    da_tra.thanh_toan()
    assert da_tra.huy() == 100_000, "đã trả tiền -> phí huỷ 10%"
    # Cùng một lời gọi `huy()`, hai kết quả khác nhau, và KHÔNG có `if` nào trong logic
    # nghiệp vụ — chỉ có một phép tra bảng.

    # ---- 5. TRẠNG THÁI PHẢI KHÔNG GÁN ĐƯỢC TỪ NGOÀI ----
    khong_gan_duoc = False
    try:
        don.trang_thai = TrangThai.DA_GIAO
    except AttributeError:
        khong_gan_duoc = True
    assert khong_gan_duoc, "`property` không setter -> máy trạng thái không bị vượt mặt"
    assert don.trang_thai is TrangThai.DA_HUY, "và trạng thái giữ nguyên"
    # Chú ý `DonHangIf` ở phần 1: `trang_thai` của nó là thuộc tính công khai, và đó là
    # gốc rễ khiến bug ở phần 1 tồn tại được. Nếu có setter, toàn bộ máy trạng thái thành
    # trang trí — đúng như mô hình thiếu máu ở bài 86.
    # (Như mọi khi: `don._trang_thai = ...` vẫn chạy. `property` là cái cửa có biển báo,
    #  không phải cái khoá — bài 83 phần 7.)

    # ---- 6. PHÂN TÍCH ĐỒ THỊ MÁY TRẠNG THÁI ----
    assert len(BANG_CHUYEN) == 4, "4 cạnh hợp lệ trên 4×3 = 12 khả năng"
    # 4/12 — nghĩa là 8 lời gọi trong số 12 phải bị từ chối. Với `if` rời rạc, mỗi cái
    # trong 8 lời gọi đó cần một dòng do con người nhớ viết.

    def den_duoc_tu(bat_dau):
        """Loang trên đồ thị chuyển: trạng thái nào tới được từ `bat_dau`."""
        tham, hang_doi = {bat_dau}, [bat_dau]
        while hang_doi:
            t = hang_doi.pop()
            for (nguon, _su), dich in BANG_CHUYEN.items():
                if nguon is t and dich not in tham:
                    tham.add(dich)
                    hang_doi.append(dich)
        return tham

    den = den_duoc_tu(TrangThai.MOI_TAO)
    mo_coi = [t.name for t in TrangThai if t not in den]
    assert mo_coi == [], f"trạng thái mồ côi (không cạnh nào dẫn tới): {mo_coi}"
    # Bài kiểm tra này bắt một lớp bug rất khó thấy bằng mắt: ai đó thêm `TAM_GIU` vào
    # enum, viết đủ hành vi cho nó, nhưng quên thêm cạnh dẫn TỚI nó — và tính năng "tạm
    # giữ đơn" không bao giờ xảy ra trên production.

    ngo_cut = [t.name for t in TrangThai
               if not any(nguon is t for (nguon, _s) in BANG_CHUYEN)]
    assert ngo_cut == ["DA_GIAO", "DA_HUY"], "đúng hai trạng thái kết thúc, và cả hai đều CỐ Ý"
    # Ngõ cụt không phải lỗi — nhưng danh sách này phải được nhìn có chủ đích. Một ngõ
    # cụt ngoài dự kiến nghĩa là đơn hàng mắc kẹt vĩnh viễn, và không có thông báo nào.

    # ---- 7. SINH SƠ ĐỒ TỪ CHÍNH BẢNG — tài liệu không thể lỗi thời ----
    dong = ["stateDiagram-v2", "    [*] --> MOI_TAO"]
    for (nguon, su), dich in BANG_CHUYEN.items():
        dong.append(f"    {nguon.name} --> {dich.name}: {su.name}")
    so_do = "\n".join(dong)
    assert so_do.count("-->") == 5, "một mũi tên khởi tạo + 4 cạnh"
    assert "DA_THANH_TOAN --> DA_GIAO: GIAO" in so_do, "sơ đồ khớp 100% với luật đang chạy"
    # Bảy dòng trên sinh ra một sơ đồ Mermaid dán thẳng vào tài liệu được. Vì nó đọc từ
    # `BANG_CHUYEN`, nó KHÔNG THỂ lệch khỏi code — khác hẳn cái sơ đồ vẽ tay trong wiki
    # mà lần cuối ai đó sửa là hai năm trước.

    # ---- 8. KHI NÀO KHÔNG DÙNG MẪU NÀY ----
    # Bảng chuyển là cách gọn nhất khi mỗi trạng thái chỉ khác nhau ở CẠNH. Ba trường hợp
    # nên chọn cách khác:
    #   - Mỗi trạng thái có HÀNH VI phức tạp riêng (không chỉ cạnh) -> một lớp cho mỗi
    #     trạng thái, như bài 32. Ở đây `PHI_HUY_PHAN_TRAM` còn là một bảng nữa; khi có
    #     bảng thứ tư, thứ năm thì đã đến lúc gộp chúng vào lớp trạng thái.
    #   - Quy trình duyệt do người dùng cấu hình -> bảng chuyển đọc từ CSDL.
    #   - Chỉ 2 trạng thái, 1 sự kiện -> một `bool` là đủ.

    # ---- 9. Ranh giới với bài 84 ----
    # Chuyển trạng thái là chỗ tự nhiên nhất để GHI SỰ KIỆN MIỀN: `giao()` đổi trạng thái
    # rồi ghi `DonHangDaGiao`. Nhưng phải GHI, không PHÁT (bài 84), và phải ghi SAU khi
    # trạng thái đã đổi thành công — nếu `_chuyen()` ném ngoại lệ thì không có sự kiện nào
    # được ghi. Thứ tự trong `DonHang` ở trên đã đúng sẵn.

    print("OK")
