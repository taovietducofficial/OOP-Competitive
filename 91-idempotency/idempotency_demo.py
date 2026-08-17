# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — thử lại sau timeout làm khách bị trừ tiền
# hai lần; "kiểm tra rồi mới làm" vẫn hỏng vì khe hở giữa hai lời gọi; cùng khoá khác
# nội dung phải bị từ chối.
# Tại sao cần học: Python có đúng một nguyên thuỷ cho bài này và hầu như không ai dùng
# nó đúng — `dict.setdefault(k, v)` vừa hỏi vừa giành chỗ trong MỘT lời gọi. Nhưng nó
# trả về giá trị chứ không trả về "tôi có chèn không", nên phải phân biệt bằng `is` chứ
# không phải `==`. Bài cũng chỉ ra `defaultdict` — thứ trông tiện nhất — phá đúng bài
# toán này: chỉ ĐỌC một khoá chưa có là nó đã TẠO ra khoá đó, và câu hỏi "khoá này đã
# xử lý chưa" trả lời sai từ lần thứ hai trở đi.

from collections import defaultdict
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class LenhChuyenTien:
    khoa_idempotency: str
    tu_tai_khoan: str
    so_tien: int


@dataclass(frozen=True)
class BienLai:
    ma_giao_dich: str
    so_du_sau_khi: int


class TaiKhoan:
    def __init__(self, so_du):
        self._so_du = so_du

    def tru(self, t):
        if self._so_du < t:
            raise RuntimeError("không đủ số dư")
        self._so_du -= t

    @property
    def so_du(self):
        return self._so_du


# =====================================================================
# SAI 1 — không có khoá idempotency: thử lại = trừ tiền lần nữa
# =====================================================================
class DichVuNgayTho:
    def __init__(self):
        self.dem = 0

    def chuyen(self, tk, so_tien):
        tk.tru(so_tien)
        self.dem += 1
        return BienLai(f"GD-{self.dem}", tk.so_du)


# =====================================================================
# SAI 2 — "kiểm tra rồi mới làm": có khe hở giữa hai lời gọi
# =====================================================================
class DichVuKiemTraRoiLam:
    def __init__(self):
        self.da_xu_ly = {}
        self.dem = 0

    def da_co(self, khoa):                      # bước 1
        return khoa in self.da_xu_ly

    def lam(self, tk, lenh):                    # bước 2
        tk.tru(lenh.so_tien)
        self.dem += 1
        self.da_xu_ly[lenh.khoa_idempotency] = BienLai(f"GD-{self.dem}", tk.so_du)


# =====================================================================
# ĐÚNG — GIÀNH CHỖ nguyên tử, rồi mới làm
# =====================================================================
@dataclass
class BanGhi:
    van_tay: str
    ket_qua: Optional[BienLai] = None      # None = "đang xử lý, chưa xong"


class DichVuIdempotent:
    def __init__(self):
        self._so = {}
        self.dem = 0
        self.so_lan_thuc_su_tru = 0

    def chuyen(self, tk, lenh):
        van_tay = f"{lenh.tu_tai_khoan}|{lenh.so_tien}"
        cho_moi = BanGhi(van_tay)

        # MỘT lời gọi vừa hỏi vừa giành chỗ. Không có khe hở nào ở giữa.
        ban_ghi = self._so.setdefault(lenh.khoa_idempotency, cho_moi)

        # `setdefault` trả về GIÁ TRỊ, không trả về "tôi có chèn không". Phân biệt bằng
        # `is` — nếu nhận lại đúng object mình vừa tạo thì mình là người giành được chỗ.
        # Dùng `==` ở đây là SAI: hai bản ghi khác nhau vẫn có thể bằng nhau về giá trị.
        if ban_ghi is not cho_moi:
            if ban_ghi.van_tay != van_tay:
                raise RuntimeError("khoá đã dùng cho một lệnh khác")
            if ban_ghi.ket_qua is None:
                raise RuntimeError("lệnh đang được xử lý, hãy thử lại sau")
            return ban_ghi.ket_qua           # phát lại KẾT QUẢ CŨ, không làm lại

        self.so_lan_thuc_su_tru += 1
        tk.tru(lenh.so_tien)
        self.dem += 1
        ban_ghi.ket_qua = BienLai(f"GD-{self.dem}", tk.so_du)
        return ban_ghi.ket_qua

    @property
    def so_khoa(self):
        return len(self._so)


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: thử lại sau timeout = trừ tiền hai lần ----
    # Kịch bản có thật và rất thường: máy chủ xử lý xong, rồi mạng đứt trước khi trả lời.
    # Điện thoại của khách không phân biệt được "chưa xử lý" với "xử lý xong mà mất phản
    # hồi", nên nó thử lại — đúng như mọi thư viện HTTP được cấu hình.
    tk = TaiKhoan(1_000_000)
    ngay_tho = DichVuNgayTho()
    ngay_tho.chuyen(tk, 100_000)          # lần 1: thành công, phản hồi bị mất
    ngay_tho.chuyen(tk, 100_000)          # lần 2: điện thoại tự thử lại
    assert tk.so_du == 800_000, "khách bị trừ 200.000 cho MỘT giao dịch"
    # Không ngoại lệ, không log lỗi. Cả hai lời gọi đều "thành công".

    # ---- 2. CON BUG: "kiểm tra rồi mới làm" vẫn hỏng ----
    tk2 = TaiKhoan(1_000_000)
    va_tam = DichVuKiemTraRoiLam()
    lenh = LenhChuyenTien("KEY-1", "TK-A", 100_000)

    a_thay = va_tam.da_co(lenh.khoa_idempotency)   # phiên A: chưa có
    b_thay = va_tam.da_co(lenh.khoa_idempotency)   # phiên B: cũng chưa có
    if not a_thay:
        va_tam.lam(tk2, lenh)
    if not b_thay:
        va_tam.lam(tk2, lenh)
    assert tk2.so_du == 800_000, "vẫn trừ hai lần — khe hở giữa hai lời gọi là tiền"
    # Bài học chung: MỌI cặp "hỏi rồi làm" trên trạng thái chia sẻ đều có khe hở này.
    # `in` + gán, `SELECT` + `INSERT`, `exists()` + `create()` — cùng một bug.
    #
    # Ở Python còn một lớp bẫy nữa: ngay cả trong MỘT tiến trình, `if k not in d: d[k]=v`
    # KHÔNG nguyên tử — GIL có thể chuyển luồng giữa hai câu lệnh. `setdefault` thì có,
    # vì nó là một lời gọi phương thức của dict cài bằng C.

    # ---- 3. CÁI BẪY RIÊNG CỦA PYTHON: `defaultdict` TẠO khoá khi đọc ----
    so_dd = defaultdict(lambda: BanGhi(""))
    assert len(so_dd) == 0, "sổ rỗng"
    if so_dd["KEY-9"].ket_qua is None:      # chỉ ĐỊNH đọc thôi...
        pass                                # ...nhưng `defaultdict` đã TẠO khoá đó
    assert len(so_dd) == 1, "chỉ đọc mà sổ đã có một bản ghi — không ai tạo nó cả"
    assert "KEY-9" in so_dd, "và từ lần sau, 'khoá này đã dùng chưa' trả lời SAI"
    # Với bài toán idempotency thì đây là thảm hoạ: câu hỏi "khoá này đã xử lý chưa" trở
    # thành "có" cho MỌI khoá từng được hỏi. Và sổ phình lên bằng những bản ghi rỗng, mỗi
    # lần có ai đó gõ sai một khoá.
    #
    # `dict` thường thì an toàn — nó ném `KeyError`:
    so_thuong = {}
    khong_tao = False
    try:
        so_thuong["KEY-9"]
    except KeyError:
        khong_tao = True
    assert khong_tao and len(so_thuong) == 0, "dict thường: đọc khoá thiếu KHÔNG tạo gì"
    # Quy tắc: `defaultdict` chỉ dùng cho gom nhóm/đếm, nơi "chưa có = rỗng" là đúng.
    # Không bao giờ dùng cho sổ trạng thái, nơi "chưa có" và "có" là hai chuyện khác nhau.

    # ---- 4. BẢN ĐÚNG: GIÀNH CHỖ nguyên tử ----
    tk3 = TaiKhoan(1_000_000)
    dv = DichVuIdempotent()
    l1 = LenhChuyenTien("KEY-1", "TK-A", 100_000)

    bl1 = dv.chuyen(tk3, l1)
    bl2 = dv.chuyen(tk3, l1)           # gửi lại y hệt
    bl3 = dv.chuyen(tk3, l1)           # và lần nữa
    assert tk3.so_du == 900_000, "trừ ĐÚNG MỘT lần, dù gọi ba lần"
    assert dv.so_lan_thuc_su_tru == 1, "và chỉ một lần đi vào phần nghiệp vụ"
    assert bl1 == bl2 == bl3, "cả ba lần trả về CÙNG MỘT biên lai"
    # Chi tiết cuối quan trọng hơn vẻ ngoài: idempotent không phải là "lần sau thì bỏ
    # qua" mà là "lần sau trả lại ĐÚNG KẾT QUẢ CŨ". Nếu lần hai trả về `None` hay ném lỗi
    # "đã xử lý", thì phía gọi vẫn không biết mã giao dịch — và nó sẽ thử lại.

    # ---- 5. CÙNG KHOÁ, KHÁC NỘI DUNG: phải TỪ CHỐI ----
    lenh_khac = LenhChuyenTien("KEY-1", "TK-A", 5_000_000)
    tu_choi = False
    try:
        dv.chuyen(tk3, lenh_khac)
    except RuntimeError:
        tu_choi = True
    assert tu_choi, "cùng khoá nhưng số tiền khác -> TỪ CHỐI"
    assert tk3.so_du == 900_000, "và không đụng vào số dư"
    # Nếu chỗ này trả về biên lai cũ (100.000) cho một lệnh 5 triệu, phía gọi sẽ tin rằng
    # 5 triệu đã chuyển xong. Hỏng nặng hơn trừ tiền hai lần: hệ thống vừa NÓI DỐI. Vì
    # vậy bản ghi idempotency phải lưu VÂN TAY của nội dung, không chỉ khoá.

    # ---- 6. AI SINH KHOÁ, VÀ SINH LÚC NÀO ----
    # Khoá phải do PHÍA GỌI sinh, TRƯỚC lần gửi đầu tiên, và giữ nguyên qua mọi lần thử
    # lại. Ba cách sinh khoá SAI hay gặp:
    #   - máy chủ sinh  -> mỗi request một khoá mới, vô dụng hoàn toàn;
    #   - băm nội dung  -> hai lần chuyển 100.000 CỐ Ý cho cùng người bị gộp làm một;
    #   - thời gian     -> thử lại ở mili-giây khác là khoá khác.
    # Cách đúng: UUID sinh ở phía gọi khi NGƯỜI DÙNG bấm nút, không phải khi gửi request.
    dv.chuyen(tk3, LenhChuyenTien("KEY-2", "TK-A", 100_000))
    dv.chuyen(tk3, LenhChuyenTien("KEY-3", "TK-A", 100_000))
    assert tk3.so_du == 700_000, "hai lệnh CỐ Ý giống nhau, hai khoá -> trừ đủ hai lần"
    assert dv.so_khoa == 3, "ba khoá đã dùng"
    # Đây là lý do không được băm nội dung làm khoá: hệ thống không có cách nào tự phân
    # biệt "gửi lại" với "cố ý làm hai lần" — chỉ phía gọi biết.

    # ---- 7. PHÉP TÍNH TUYỆT ĐỐI THÌ TỰ NÓ ĐÃ IDEMPOTENT ----
    so_du_bang = {"TK-A": 500_000}
    so_du_bang["TK-A"] = 400_000
    so_du_bang["TK-A"] = 400_000                # làm lại y hệt
    assert so_du_bang["TK-A"] == 400_000, "GÁN giá trị: chạy bao nhiêu lần cũng thế"

    tuong_doi = 500_000
    tuong_doi -= 100_000
    tuong_doi -= 100_000                        # làm lại y hệt
    assert tuong_doi == 300_000, "CỘNG TRỪ: mỗi lần chạy lại là một lần sai thêm"
    # Nguyên tắc thiết kế: khi được chọn, hãy thiết kế lệnh theo dạng TUYỆT ĐỐI ("đặt
    # trạng thái = ĐÃ GIAO") thay vì TƯƠNG ĐỐI ("tăng số lượng lên 1"). Lệnh tuyệt đối
    # idempotent miễn phí, không cần sổ khoá, không cần dọn dẹp.

    # ---- 8. SỔ KHOÁ PHẢI CÓ HẠN, VÀ PHẢI CÓ PHẠM VI ----
    #   - HẠN: khoá giữ mãi thì sổ lớn vô hạn. Thường giữ 24–72 giờ — đủ dài hơn mọi lịch
    #     thử lại, đủ ngắn để sổ không phình. Sau khi hết hạn, cùng khoá đó được coi là
    #     lệnh mới; đó là đánh đổi CÓ Ý, phải nói ra trong tài liệu API.
    #   - PHẠM VI: khoá phải kèm định danh người gọi. Nếu không, khách A đoán được khoá
    #     của khách B là chặn được giao dịch của người khác.
    # Trong hệ thật, "giành chỗ nguyên tử" chính là RÀNG BUỘC DUY NHẤT của CSDL:
    #     INSERT INTO so_idempotency(khoa, van_tay) VALUES (%s, %s)
    # Insert trùng thì CSDL báo lỗi khoá trùng — đó là `setdefault` ở mức bền vững.
    assert dv.so_khoa == 3, "sổ khoá là dữ liệu THẬT, phải được thiết kế như mọi bảng khác"

    # ---- 9. Vì sao bài này đi liền sau bài 84 ----
    # Outbox, hàng đợi, cơ chế thử lại của HTTP — cả ba đều giao ÍT NHẤT MỘT LẦN. "Đúng
    # một lần" không tồn tại trên mạng: bên gửi không bao giờ phân biệt được "chưa nhận"
    # với "nhận rồi mà mất phản hồi". Nên "đúng một lần" luôn được làm bằng: GIAO ít nhất
    # một lần + XỬ LÝ idempotent. Bài 84 lo nửa đầu, bài này lo nửa sau.

    print("OK")
