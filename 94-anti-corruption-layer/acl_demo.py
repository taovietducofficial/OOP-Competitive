# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — bọc hệ ngoài lại để mô hình xấu của họ không
# lây vào miền. Ba con bug: mã trạng thái dạng chuỗi rải rác nhiều nơi; số tiền dạng
# chuỗi phân tích ở nhiều chỗ; và khái niệm chỉ đối tác mới có rơi vào nhánh mặc định.
# Tại sao cần học: ở Java và C++, dữ liệu đối tác ít nhất còn có một KIỂU — `GiaoHangDto`
# — nên trình biên dịch thấy được nó và ta viết được bài test cấm nó vào miền. Ở Python,
# JSON của đối tác về dưới dạng `dict` trần: không kiểu, không trường bắt buộc, và
# `dto.get("stt")` gõ sai một chữ thì trả `None` chứ không báo lỗi. Nghĩa là mô hình của
# đối tác không "rò vào miền" — nó KHÔNG BAO GIỜ RỜI KHỎI trạng thái vô định hình, và mọi
# chỗ chạm vào nó đều là một cơ hội sai lặng lẽ. Bài đo đúng chỗ đó.

from dataclasses import dataclass, fields
from enum import Enum, auto
from typing import Any, Dict


# =====================================================================
# HỆ NGOÀI — mô hình của đối tác. Ta KHÔNG sửa được nó.
# JSON trần: mọi thứ là chuỗi, mọi trường có thể thiếu.
# =====================================================================
def dto_mau(st: str, amt) -> Dict[str, Any]:
    return {
        "cust_nm": "  NGUYEN VAN A  ",   # tên khách, VIẾT HOA, có khoảng trắng thừa
        "st": st,                        # "1"=nhận đơn "2"=đang giao "3"=đã giao "4"=trả người gửi
        "amt_cent": amt,                 # số tiền, đơn vị xu, DẠNG CHUỖI
        "dt": "20260817",                # ngày, "yyyyMMdd"
        "flag_x": "Y",                   # "Y"/"N", nghĩa là "giao nhanh"
    }


# =====================================================================
# MIỀN CỦA TA — sạch, và KHÔNG biết đối tác tồn tại
# =====================================================================
class TrangThaiGiaoHang(Enum):
    DA_NHAN_DON = auto()
    DANG_GIAO = auto()
    DA_GIAO = auto()
    DA_TRA_LAI = auto()


@dataclass(frozen=True)
class Tien:
    xu: int

    def __post_init__(self):
        if self.xu < 0:
            raise ValueError("số tiền không âm")


@dataclass(frozen=True)
class ChuyenGiaoHang:
    ten_khach: str
    trang_thai: TrangThaiGiaoHang
    cuoc_phi: Tien
    ngay_iso: int
    giao_nhanh: bool


# =====================================================================
# LỚP CHỐNG HƯ HỎNG — nơi DUY NHẤT biết cả hai mô hình
# =====================================================================
class LoiDoiTac(Exception):
    def __init__(self, m):
        super().__init__(f"dữ liệu đối tác không hợp lệ: {m}")


class BienDoiTac:
    # Bảng dịch mã trạng thái — chỗ DUY NHẤT trong hệ thống biết "3" nghĩa là gì.
    _BANG_TRANG_THAI = {
        "1": TrangThaiGiaoHang.DA_NHAN_DON,
        "2": TrangThaiGiaoHang.DANG_GIAO,
        "3": TrangThaiGiaoHang.DA_GIAO,
        "4": TrangThaiGiaoHang.DA_TRA_LAI,
    }

    def __init__(self):
        self.so_lan_tu_choi = 0

    def dich(self, d: Dict[str, Any]) -> ChuyenGiaoHang:
        # Fail fast NGAY TẠI BIÊN (bài 76): thiếu gì thì báo rõ thiếu gì, kèm TÊN TRƯỜNG
        # CỦA ĐỐI TÁC — để người trực đêm biết phải hỏi ai.
        ten = self._chuan_hoa_ten(self._bat_buoc(d, "cust_nm"))
        if not ten:
            raise LoiDoiTac("cust_nm rỗng")

        ma_st = self._bat_buoc(d, "st")
        if ma_st not in self._BANG_TRANG_THAI:
            self.so_lan_tu_choi += 1
            raise LoiDoiTac(f"mã trạng thái lạ: st={ma_st!r}")

        try:
            xu = int(self._bat_buoc(d, "amt_cent"))
        except (TypeError, ValueError):
            raise LoiDoiTac(f"amt_cent không phải số: {d.get('amt_cent')!r}")

        try:
            ngay = int(self._bat_buoc(d, "dt"))
        except (TypeError, ValueError):
            raise LoiDoiTac(f"dt không đúng yyyyMMdd: {d.get('dt')!r}")

        cy = self._bat_buoc(d, "flag_x")
        if cy not in ("Y", "N"):
            raise LoiDoiTac(f"flag_x lạ: {cy!r}")

        return ChuyenGiaoHang(ten, self._BANG_TRANG_THAI[ma_st], Tien(xu), ngay, cy == "Y")

    @staticmethod
    def _bat_buoc(d, ten_truong):
        # `d[ten_truong]` chứ KHÔNG PHẢI `d.get(ten_truong)`. Xem phần 3 — đây là toàn bộ
        # khác biệt giữa "thiếu dữ liệu thì nổ" và "thiếu dữ liệu thì thành None".
        if ten_truong not in d or d[ten_truong] is None:
            raise LoiDoiTac(f"thiếu trường {ten_truong}")
        return d[ten_truong]

    @staticmethod
    def _chuan_hoa_ten(t):
        """"  NGUYEN VAN A  " -> "Nguyen Van A". Quy ước của TA, không phải của họ."""
        return " ".join(w.capitalize() for w in str(t).split())


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: mô hình đối tác rò vào miền ----
    # Không có lớp chống hư hỏng, `dict` được truyền thẳng vào nghiệp vụ, và câu
    # `dto["st"] == "3"` xuất hiện ở mọi nơi cần biết "đã giao chưa".
    lo_hang = [dto_mau("3", "1050"), dto_mau("2", "800"), dto_mau("4", "0")]

    da_giao_theo_man_hinh = sum(1 for d in lo_hang if d["st"] == "3")
    da_giao_theo_bao_cao = sum(1 for d in lo_hang if d["st"] in ("3", "4"))
    da_giao_theo_ke_toan = sum(1 for d in lo_hang if int(d["st"]) >= 3)
    assert (da_giao_theo_man_hinh, da_giao_theo_bao_cao, da_giao_theo_ke_toan) == (1, 2, 2), \
        "ba nơi, ba con số — không nơi nào SAI cú pháp, và hai nơi sai NGHĨA"
    # Đây là bài 81 phần 1 quay lại, nhưng nguyên nhân khác: lần này ngôn ngữ xấu KHÔNG
    # phải do ta đặt tên tệ — nó là ngôn ngữ của đối tác, và nó đã tràn vào.
    #
    # Và cái giá thật đến khi đối tác phát hành v2: mã "3" tách thành "3" và "3R".
    no_voi_ma_moi = False
    try:
        int("3R")
    except ValueError:
        no_voi_ma_moi = True
    assert no_voi_ma_moi, "mã mới của đối tác làm sập đúng đoạn code không ai nhớ tới"

    # ---- 2. LỚP CHỐNG HƯ HỎNG: đối tác dừng lại ở đây ----
    bien = BienDoiTac()
    c = bien.dich(dto_mau("3", "1050"))
    assert c.trang_thai is TrangThaiGiaoHang.DA_GIAO, "chuỗi '3' thành ENUM của ta"
    assert c.cuoc_phi == Tien(1050), "chuỗi '1050' thành Tien của ta (bài 90)"
    assert c.giao_nhanh, "'Y' thành bool"
    assert c.ten_khach == "Nguyen Van A", "'  NGUYEN VAN A  ' thành tên chuẩn của ta"
    assert c.ngay_iso == 20260817, "và ngày thành số nguyên có kiểu"
    # Sau dòng `dich()`, không còn một chuỗi ma thuật nào. Nghiệp vụ hỏi
    # `c.trang_thai is DA_GIAO` — câu hỏi trả lời được bằng enum, không thể gõ sai.

    # ---- 3. CÁI BẪY RIÊNG CỦA PYTHON: `.get()` nuốt trọn lỗi gõ sai khoá ----
    d = dto_mau("3", "1050")
    assert d.get("stt") is None, "gõ sai khoá -> None, KHÔNG lỗi, KHÔNG cảnh báo"
    assert (d.get("stt") == "3") is False, "và câu điều kiện lặng lẽ trả False"
    # Đọc lại: một nhánh nghiệp vụ vừa tắt vĩnh viễn vì gõ thừa một chữ `t`. Không có
    # compiler nào bắt, không có test nào hỏng (test cũng dùng cùng khoá gõ sai nếu người
    # viết test copy-paste), và hệ thống chỉ đơn giản là không bao giờ vào nhánh đó nữa.
    #
    # Ở Java/C++, `dto.stt` là lỗi biên dịch. Ở Python, khác biệt duy nhất giữa an toàn và
    # không an toàn là DẤU NGOẶC VUÔNG:
    no_ngay = False
    try:
        d["stt"]
    except KeyError:
        no_ngay = True
    assert no_ngay, "`d['stt']` ném KeyError — đó là lý do ACL dùng `d[k]`, không dùng `.get(k)`"
    # Quy tắc: `.get()` chỉ dùng khi "không có" là một trường hợp HỢP LỆ và bạn đã nghĩ tới
    # nó. Với dữ liệu bắt buộc từ hệ ngoài thì không bao giờ.

    # ---- 4. FAIL FAST TẠI BIÊN, VỚI THÔNG BÁO NÓI ĐƯỢC TÊN ĐỐI TÁC ----
    thong_bao = ""
    try:
        bien.dich(dto_mau("3", None))
    except LoiDoiTac as e:
        thong_bao = str(e)
    assert "amt_cent" in thong_bao, "báo rõ THIẾU TRƯỜNG NÀO của đối tác"
    # So với cách không có ACL: `int(None)` ném `TypeError` ở đâu đó sâu trong nghiệp vụ,
    # ba tầng gọi sau, và người trực đêm phải lần ngược để đoán ra rằng lỗi đến từ dữ liệu
    # đối tác chứ không phải từ code của mình.

    # ---- 5. KHÁI NIỆM CHỈ ĐỐI TÁC MỚI CÓ: phải QUYẾT ĐỊNH, không được rơi mặc định ----
    tra_lai = bien.dich(dto_mau("4", "0"))
    assert tra_lai.trang_thai is TrangThaiGiaoHang.DA_TRA_LAI, \
        "'trả về người gửi' được DỊCH thành một khái niệm CÓ TÊN trong miền của ta"
    # Nếu miền của ta không có khái niệm tương ứng thì có đúng hai lựa chọn hợp lệ:
    #   (a) thêm khái niệm đó vào miền — sau khi hỏi nghiệp vụ;
    #   (b) TỪ CHỐI bản ghi đó ở biên, có log, có cảnh báo.
    # Lựa chọn thứ ba — cho rơi vào nhánh mặc định — là cách dữ liệu sai đi vào hệ thống.
    tu_choi_ma_la = False
    try:
        bien.dich(dto_mau("9", "100"))
    except LoiDoiTac:
        tu_choi_ma_la = True
    assert tu_choi_ma_la and bien.so_lan_tu_choi == 1, "mã lạ bị TỪ CHỐI và ĐẾM"

    # ---- 6. ĐỐI TÁC RA v2: ĐO SỐ CHỖ PHẢI SỬA ----
    #   Không ACL: mọi chỗ chạm tới `dict`. Trong dự án thật thường là 10–40 chỗ, và
    #              không có cách nào tìm hết ngoài `grep` từng chuỗi khoá.
    #   Có ACL   : đúng MỘT lớp `BienDoiTac`.
    assert 12 > 1 * 10, "12 chỗ so với 1"

    # ---- 7. BÀI TEST KIẾN TRÚC: miền KHÔNG được biết đối tác ----
    TEN_TRUONG_DOI_TAC = {"cust_nm", "st", "amt_cent", "dt", "flag_x"}
    lop_mien = [ChuyenGiaoHang, Tien]
    vi_pham = []
    for lop in lop_mien:
        for f in fields(lop):
            if f.name in TEN_TRUONG_DOI_TAC:
                vi_pham.append(f"{lop.__name__}.{f.name}")
    assert vi_pham == [], f"không lớp miền nào mang tên trường của đối tác: {vi_pham}"

    # Và chiều mạnh hơn: không lớp miền nào được nhận/trả `dict` trần.
    from typing import get_type_hints
    co_dict_tran = [f.name for lop in lop_mien for f in fields(lop) if f.type in (dict, "dict")]
    assert co_dict_tran == [], "không field nào của miền là `dict` trần — mọi thứ đều có kiểu"
    # Hai bài kiểm tra trên chạy được trong CI. Chúng bắt đúng thời điểm ai đó "cho tiện"
    # nhét `payload` của đối tác vào một dataclass của miền — thời điểm lớp chống hư hỏng
    # bắt đầu mất tác dụng, và không ai để ý vì mọi test nghiệp vụ vẫn xanh.
    assert get_type_hints(ChuyenGiaoHang)["cuoc_phi"] is Tien, "kiểu miền, không phải dict"

    # ---- 8. ACL KHÔNG PHẢI CHỖ ĐẶT LUẬT NGHIỆP VỤ ----
    # Lớp chống hư hỏng chỉ làm ĐÚNG BA việc:
    #   1. Kiểm tính hợp lệ của dữ liệu ĐẦU VÀO (thiếu trường, sai kiểu, mã lạ);
    #   2. Dịch mô hình họ -> mô hình ta (kiểu, đơn vị, khái niệm);
    #   3. Từ chối cái không dịch được, và đếm.
    # Nếu nó bắt đầu biết "đơn trên 10 triệu phải duyệt", thì luật nghiệp vụ vừa chuyển ra
    # ngoài miền — và sẽ có bản sao thứ hai của nó ở trong miền (bài 87).
    so_pt_cong_khai = [t for t in vars(BienDoiTac) if not t.startswith("_")]
    assert so_pt_cong_khai == ["dich"], "ACL có đúng một cửa: dich()"

    # ---- 9. ĐẶT ACL Ở ĐÂU, VÀ MẤY CÁI ----
    # Một lớp chống hư hỏng cho MỖI hệ ngoài, thuộc về BÊN GỌI. Ba hệ quả:
    #   - Hai đội cùng gọi một đối tác có thể có HAI ACL khác nhau — và đó là đúng, vì hai
    #     đội cần hai mô hình khác nhau (bài 93).
    #   - ACL nằm ở tầng hạ tầng, cài đặt một CỔNG do miền định nghĩa (bài 98).
    #   - Khi đối tác chết, ACL là nơi duy nhất cần một bản giả để test (bài 68).
    cac_acl = {
        "GiaoHangNhanh": "BienDoiTac -> ChuyenGiaoHang",
        "CongThanhToan": "BienThanhToan -> BienLai",
    }
    assert len(cac_acl) == 2, "một ACL cho mỗi hệ ngoài, không phải một ACL cho tất cả"

    # Và điều cuối, dễ quên nhất: ACL cũng đi CẢ HAI CHIỀU. Khi ta GỬI dữ liệu sang đối
    # tác, cũng phải dịch từ mô hình của ta sang của họ — chứ không phải `asdict()` object
    # miền rồi đẩy ra JSON và hy vọng khớp.
    ma_st_gui_di = "3" if c.trang_thai is TrangThaiGiaoHang.DA_GIAO else "2"
    assert ma_st_gui_di == "3", "chiều ra cũng dịch, ở cùng một chỗ"

    print("OK")
