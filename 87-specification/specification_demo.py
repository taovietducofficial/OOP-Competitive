# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — luật nghiệp vụ thành object có tên, ghép
# được, giải thích được, dịch được sang SQL. Hai con bug: cùng một luật chép ba nơi rồi
# lệch nhau, và luật trong bộ nhớ lệch với luật trong SQL.
# Tại sao cần học: Python cho nạp chồng `&`, `|`, `~` nên luật ghép viết được gọn gần
# bằng C++. Nhưng đúng chỗ đó là cái bẫy nguy hiểm nhất trong cả ba bản: từ khoá `and`,
# `or`, `not` KHÔNG nạp chồng được — chúng đi qua `__bool__`. Nghĩa là
# `du_tuoi(18) and du_diem(100)` biên dịch được, chạy được, KHÔNG báo lỗi, và trả về
# đúng một object: cái thứ hai. Vế đầu bị vứt đi im lặng, và luật của bạn vừa mất một
# nửa mà không có dấu vết nào. Bài đo đúng con bug đó.

from dataclasses import dataclass
from typing import List


@dataclass(frozen=True)
class KhachHang:
    ma: str
    tuoi: int
    diem: int
    bi_khoa: bool


# =====================================================================
# SPECIFICATION — một luật nghiệp vụ, ba khả năng
# =====================================================================
class DacTa:
    def thoa_man(self, k) -> bool:
        raise NotImplementedError

    def mo_ta(self) -> str:              # 1. có TÊN (bài 81)
        raise NotImplementedError

    def dieu_kien_sql(self) -> str:      # 2. DỊCH được sang truy vấn
        raise NotImplementedError

    # 3. GIẢI THÍCH được: trượt thì trượt ở mệnh đề nào
    def ly_do_truot(self, k) -> List[str]:
        return [] if self.thoa_man(k) else [self.mo_ta()]

    # Toán tử NẠP CHỒNG ĐƯỢC của Python: & | ~
    def __and__(self, khac):
        return Va(self, khac)

    def __or__(self, khac):
        return Hoac(self, khac)

    def __invert__(self):
        return Khong(self)


@dataclass(frozen=True)
class Va(DacTa):
    trai: DacTa
    phai: DacTa

    def thoa_man(self, k):
        # `and` ở ĐÂY là từ khoá dựng sẵn -> VẪN ngắn mạch. Xem phần 5.
        return self.trai.thoa_man(k) and self.phai.thoa_man(k)

    def mo_ta(self):
        return f"({self.trai.mo_ta()} VÀ {self.phai.mo_ta()})"

    def dieu_kien_sql(self):
        return f"({self.trai.dieu_kien_sql()} AND {self.phai.dieu_kien_sql()})"

    def ly_do_truot(self, k):
        return self.trai.ly_do_truot(k) + self.phai.ly_do_truot(k)   # gom CẢ HAI nhánh


@dataclass(frozen=True)
class Hoac(DacTa):
    trai: DacTa
    phai: DacTa

    def thoa_man(self, k):
        return self.trai.thoa_man(k) or self.phai.thoa_man(k)

    def mo_ta(self):
        return f"({self.trai.mo_ta()} HOẶC {self.phai.mo_ta()})"

    def dieu_kien_sql(self):
        return f"({self.trai.dieu_kien_sql()} OR {self.phai.dieu_kien_sql()})"


@dataclass(frozen=True)
class Khong(DacTa):
    trong: DacTa

    def thoa_man(self, k):
        return not self.trong.thoa_man(k)

    def mo_ta(self):
        return f"KHÔNG {self.trong.mo_ta()}"

    def dieu_kien_sql(self):
        return f"NOT ({self.trong.dieu_kien_sql()})"


# Ba luật cơ sở — mỗi cái là một câu người làm nghiệp vụ nói ra miệng.
@dataclass(frozen=True)
class DuTuoi(DacTa):
    toi_thieu: int

    def thoa_man(self, k):
        return k.tuoi >= self.toi_thieu

    def mo_ta(self):
        return f"đủ {self.toi_thieu} tuổi"

    def dieu_kien_sql(self):
        return f"tuoi >= {self.toi_thieu}"


@dataclass(frozen=True)
class DuDiem(DacTa):
    toi_thieu: int

    def thoa_man(self, k):
        return k.diem >= self.toi_thieu

    def mo_ta(self):
        return f"đủ {self.toi_thieu} điểm tích luỹ"

    def dieu_kien_sql(self):
        return f"diem >= {self.toi_thieu}"


@dataclass(frozen=True)
class BiKhoa(DacTa):
    def thoa_man(self, k):
        return k.bi_khoa

    def mo_ta(self):
        return "đang bị khoá"

    def dieu_kien_sql(self):
        return "bi_khoa = 1"


# ---- Self-check ----
if __name__ == "__main__":
    danh_sach = [
        KhachHang("KH-1", 25, 150, False),   # đủ điều kiện
        KhachHang("KH-2", 17, 500, False),   # thiếu tuổi
        KhachHang("KH-3", 30, 50, False),    # thiếu điểm
        KhachHang("KH-4", 40, 900, True),    # bị khoá
        KhachHang("KH-5", 22, 100, False),   # đủ điều kiện (đúng ngưỡng)
    ]

    # ---- 1. LUẬT LÀ MỘT OBJECT, GHÉP BẰNG TOÁN TỬ ----
    duoc_vay_tin_chap = DuTuoi(18) & DuDiem(100) & ~BiKhoa()

    hop_le = [k for k in danh_sach if duoc_vay_tin_chap.thoa_man(k)]
    assert len(hop_le) == 2, "hai khách đủ điều kiện: KH-1 và KH-5"
    assert duoc_vay_tin_chap.mo_ta() == \
        "((đủ 18 tuổi VÀ đủ 100 điểm tích luỹ) VÀ KHÔNG đang bị khoá)", \
        "luật tự đọc lên thành câu — dán thẳng vào tài liệu được"

    # ---- 2. CON BUG: cùng một luật chép ở ba nơi ----
    #   màn hình : k.tuoi >= 18 and k.diem >= 100 and not k.bi_khoa
    #   job email: k.tuoi >= 18 and k.diem >= 100                    <- QUÊN bi_khoa
    #   báo cáo  : k.tuoi >  18 and k.diem >= 100 and not k.bi_khoa  <- `>` thay vì `>=`
    n_man_hinh = sum(1 for k in danh_sach if k.tuoi >= 18 and k.diem >= 100 and not k.bi_khoa)
    n_job = sum(1 for k in danh_sach if k.tuoi >= 18 and k.diem >= 100)
    n_bao_cao = sum(1 for k in danh_sach if k.tuoi > 18 and k.diem >= 100 and not k.bi_khoa)
    assert (n_man_hinh, n_job, n_bao_cao) == (2, 3, 2), "ba con số khác nhau cho CÙNG một luật"
    assert n_job - n_man_hinh == 1, "job gửi lời mời vay cho một khách ĐANG BỊ KHOÁ"
    # Ba dòng trên đều "chạy đúng" theo ý người viết chúng. Không test nào hỏng, vì mỗi
    # chỗ có test riêng và test đó khớp với code ở chỗ đó.

    # ---- 3. GIẢI THÍCH: trượt ở mệnh đề nào ----
    truot = KhachHang("KH-9", 16, 20, True)
    assert not duoc_vay_tin_chap.thoa_man(truot), "một hàm bool nói: False"
    # ...và hết. `False` không nói được vì sao. Muốn báo cho khách "bạn chưa đủ tuổi và
    # chưa đủ điểm" thì phải viết LẠI toàn bộ luật lần thứ tư, dưới dạng chuỗi if.

    ly_do = duoc_vay_tin_chap.ly_do_truot(truot)
    assert len(ly_do) == 3, "specification nói: trượt ở BA mệnh đề"
    assert ly_do[0] == "đủ 18 tuổi" and ly_do[2] == "KHÔNG đang bị khoá", \
        "và nói rõ từng mệnh đề nào — dán thẳng vào thông báo lỗi"
    assert duoc_vay_tin_chap.ly_do_truot(danh_sach[0]) == [], "khách hợp lệ: không lý do nào"
    # Đây là giá trị lớn nhất của specification trong thực tế và ít được nhắc tới nhất:
    # màn hình "vì sao đơn của tôi bị từ chối" sinh ra tự động, luôn khớp với luật thật.

    # ---- 4. DỊCH SANG TRUY VẤN ----
    sql_go_tay = "SELECT * FROM khach_hang WHERE tuoi >= 18 AND diem >= 100"
    assert "bi_khoa" not in sql_go_tay, "SQL gõ tay lệch khỏi luật trong code"

    sql_tu_dac_ta = "SELECT * FROM khach_hang WHERE " + duoc_vay_tin_chap.dieu_kien_sql()
    assert sql_tu_dac_ta == \
        "SELECT * FROM khach_hang WHERE ((tuoi >= 18 AND diem >= 100) AND NOT (bi_khoa = 1))", \
        "SQL sinh TỪ CHÍNH luật — không thể lệch"
    # Ranh giới: chỉ sinh SQL từ cấu trúc CỦA CHÍNH specification, và ngưỡng phải là
    # số/hằng do miền quyết định. Nếu cần nhét chuỗi từ người dùng vào, hãy trả về câu có
    # THAM SỐ (`tuoi >= ?`) cộng danh sách giá trị — đừng nối chuỗi.

    # ---- 5. CÁI BẪY NGUY HIỂM NHẤT CỦA PYTHON: `and` KHÔNG nạp chồng được ----
    # `&`, `|`, `~` đi qua `__and__`, `__or__`, `__invert__` -> nạp chồng được.
    # `and`, `or`, `not` đi qua `__bool__` -> KHÔNG nạp chồng được, và Python không hề
    # báo lỗi khi bạn dùng nhầm.
    luat_dung = DuTuoi(18) & DuDiem(100)
    luat_sai = DuTuoi(18) and DuDiem(100)        # <- một chữ khác, cả luật khác hẳn

    assert isinstance(luat_dung, Va), "`&` dựng cây hai nhánh"
    assert isinstance(luat_sai, DuDiem), "`and` trả về... ĐÚNG MỘT nhánh: cái thứ hai"
    assert luat_sai == DuDiem(100), "điều kiện tuổi vừa BIẾN MẤT, không lỗi, không cảnh báo"
    # Vì sao: `a and b` nghĩa là "nếu `bool(a)` sai thì trả `a`, ngược lại trả `b`". Mọi
    # object Python đều truthy theo mặc định, nên `DuTuoi(18)` là truthy, và biểu thức
    # trả về `DuDiem(100)`. Không có `__and__` nào được gọi.
    khach_17_tuoi = KhachHang("KH-X", 17, 500, False)
    assert not luat_dung.thoa_man(khach_17_tuoi), "luật đúng: 17 tuổi -> trượt"
    assert luat_sai.thoa_man(khach_17_tuoi), "luật sai: 17 tuổi -> ĐẠT. Cho vay một trẻ vị thành niên"

    # `not` cũng vậy — nó trả về `bool`, không trả về specification:
    khong_dung = ~BiKhoa()
    khong_sai = not BiKhoa()
    assert isinstance(khong_dung, Khong), "`~` dựng nhánh phủ định"
    assert khong_sai is False, "`not` trả về đúng giá trị False — không còn là luật nữa"

    # Cách phòng thân duy nhất có hiệu lực: chặn ở `__bool__`.
    class DacTaAnToan(DacTa):
        def __bool__(self):
            raise TypeError("dùng & | ~ để ghép specification, không dùng and/or/not")

        def thoa_man(self, k):
            return True

        def mo_ta(self):
            return "luôn đúng"

        def dieu_kien_sql(self):
            return "1 = 1"

    no_len = False
    try:
        _ = DacTaAnToan() and DacTaAnToan()
    except TypeError:
        no_len = True
    assert no_len, "`__bool__` ném lỗi -> dùng nhầm `and` là nổ NGAY, không âm thầm"
    # Sáu dòng này nên có trong mọi lớp specification thật. Chúng biến một bug im lặng
    # thành một lỗi lúc chạy — và với luật cho vay tiền thì đó là đổi rất đáng.

    # ---- 6. GHÉP LẠI THÀNH LUẬT MỚI MÀ KHÔNG SỬA GÌ (bài 61) ----
    uu_dai_dac_biet = duoc_vay_tin_chap | (DuDiem(500) & ~BiKhoa())
    assert uu_dai_dac_biet.thoa_man(danh_sach[1]), "khách 17 tuổi nhưng 500 điểm: đạt luật mới"
    assert not uu_dai_dac_biet.thoa_man(danh_sach[3]), "khách bị khoá trượt cả hai nhánh"

    # ---- 7. KHI NÀO KHÔNG CẦN SPECIFICATION ----
    # Mẫu thiết kế dễ bị lạm dụng. Ba câu hỏi, cần CÓ ít nhất hai:
    #   (a) Luật này có dùng ở NHIỀU HƠN MỘT chỗ không?
    #   (b) Nó có cần GHÉP với luật khác không?
    #   (c) Có ai cần biết VÌ SAO trượt, hoặc cần dịch nó sang truy vấn không?
    # Nếu chỉ một chỗ dùng, không ghép, không giải thích — thì `if` là đúng.
    #
    # Và nếu luật thuộc về đúng một entity, nó nên là một PHƯƠNG THỨC của entity đó
    # (bài 86 câu hỏi b): `don.qua_han(hom_nay)` tốt hơn `DonQuaHan(hom_nay).thoa_man(don)`.
    assert DuTuoi(18).thoa_man(danh_sach[0]), "luật cơ sở vẫn dùng lẻ được"

    # ---- 8. Specification là nền của bài 88 ----
    # Ở đây luật được ghép LÚC VIẾT CODE. Bước tiếp theo là chọn luật LÚC CHẠY — mỗi
    # quốc gia, mỗi hạng khách một luật khác nhau, và code gọi không đổi một chữ. Đó là
    # policy object (bài 88), và nó chỉ là specification + một bảng tra.

    print("OK")
