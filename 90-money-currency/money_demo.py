# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — `float` làm lệch sổ, cộng khác tiền tệ, chia
# 100 cho 3 làm bốc hơi một xu, và giả định "tiền tệ nào cũng 2 chữ số thập phân".
# Tại sao cần học: Python có `Decimal` — công cụ tốt nhất trong ba ngôn ngữ cho tiền —
# và đúng vì thế nó có cái bẫy tinh vi nhất: `Decimal(0.1)` KHÔNG bằng `Decimal("0.1")`.
# Truyền một `float` vào `Decimal` là đã hỏng trước khi bắt đầu, và dòng đó trông y hệt
# dòng đúng. Bài còn đo một thứ nữa mà hầu như ai cũng đoán sai: `round(2.5)` trong
# Python bằng 2, không bằng 3 — vì `round` mặc định làm tròn kiểu ngân hàng.

from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP, ROUND_HALF_EVEN, ROUND_DOWN
from enum import Enum


class TienTe(Enum):
    """Số chữ số thập phân KHÔNG phải lúc nào cũng là 2. Đây là dữ liệu, không phải hằng."""
    VND = 0    # đồng Việt Nam: không có đơn vị nhỏ hơn
    USD = 2    # 1 dollar = 100 cent
    JOD = 3    # dinar Jordan = 1000 fils

    @property
    def so_chu_so(self):
        return self.value


@dataclass(frozen=True)
class Tien:
    """Tiền = số ĐƠN VỊ NHỎ NHẤT (xu / cent / fils) + loại tiền tệ. `int` của Python
    không tràn, nên đây là biểu diễn chính xác tuyệt đối ở mọi kích thước."""
    don_vi_nho: int
    tien_te: TienTe

    @staticmethod
    def tu(chuoi: str, tt: TienTe):
        # `Decimal(chuoi)` — CHUỖI, không phải float. Xem phần 2.
        b = Decimal(chuoi).scaleb(tt.so_chu_so)
        if b != b.to_integral_value():
            raise ValueError(f"số tiền nhỏ hơn đơn vị nhỏ nhất của {tt.name}")
        return Tien(int(b), tt)

    def _cung_te(self, k):
        if self.tien_te is not k.tien_te:
            raise ValueError(f"không cộng trừ được {self.tien_te.name} với {k.tien_te.name}")

    def __add__(self, k):
        self._cung_te(k)
        return Tien(self.don_vi_nho + k.don_vi_nho, self.tien_te)

    def __sub__(self, k):
        self._cung_te(k)
        return Tien(self.don_vi_nho - k.don_vi_nho, self.tien_te)

    def nhan(self, he_so: Decimal, lam_tron):
        """Tiền × HỆ SỐ = tiền. Tiền × tiền là vô nghĩa — xem phần 6."""
        return Tien(int((Decimal(self.don_vi_nho) * he_so).quantize(Decimal(1), rounding=lam_tron)),
                    self.tien_te)

    def ti_le_so_voi(self, k) -> Decimal:
        """Tiền ÷ tiền = TỈ LỆ, không phải tiền."""
        self._cung_te(k)
        return Decimal(self.don_vi_nho) / Decimal(k.don_vi_nho)

    def chia_deu(self, n: int):
        """Chia đều cho n phần, KHÔNG làm mất một xu nào. Xem phần 4."""
        if n <= 0:
            raise ValueError("số phần phải dương")
        moi_phan, du = divmod(self.don_vi_nho, n)     # phần dư đem chia tiếp, không vứt
        return [Tien(moi_phan + (1 if i < du else 0), self.tien_te) for i in range(n)]

    def chia_theo(self, *ti_le: int):
        tong_ti_le = sum(ti_le)
        ra = [self.don_vi_nho * t // tong_ti_le for t in ti_le]
        du = self.don_vi_nho - sum(ra)               # phần dư do làm tròn xuống
        for i in range(du):
            ra[i] += 1
        return [Tien(x, self.tien_te) for x in ra]

    def __str__(self):
        d = Decimal(self.don_vi_nho).scaleb(-self.tien_te.so_chu_so)
        return f"{d:.{self.tien_te.so_chu_so}f} {self.tien_te.name}"


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: `float` không biểu diễn được 0.1 ----
    assert 0.1 + 0.2 != 0.3, "0.1 + 0.2 KHÁC 0.3 trong số thực dấu phẩy động"
    assert 0.1 + 0.2 == 0.30000000000000004, "nó bằng đúng con số này"
    # Nguyên nhân: 0.1 trong hệ nhị phân là số vô hạn tuần hoàn, y như 1/3 trong hệ thập
    # phân. Không có gì "sửa" được điều đó — nó là bản chất của kiểu dữ liệu.

    vi_float = 0.0
    for _ in range(10_000):
        vi_float += 0.01                     # 10.000 lần cộng 1 xu
    assert vi_float != 100.0, "sau 10.000 giao dịch nhỏ, sổ đã lệch"
    assert Decimal(vi_float) != Decimal(100), \
        "độ lệch là THẬT — đây là giá trị nhị phân chính xác, không phải ảo giác hiển thị"

    vi_int = sum(1 for _ in range(10_000))   # cộng bằng ĐƠN VỊ NHỎ NHẤT
    assert vi_int == 10_000, "cộng bằng số nguyên: chính xác tuyệt đối, mãi mãi"

    # ---- 2. CÁI BẪY TINH VI NHẤT CỦA PYTHON: `Decimal(0.1)` ----
    assert Decimal("0.1") + Decimal("0.2") == Decimal("0.3"), "Decimal từ CHUỖI: đúng"
    assert Decimal(0.1) + Decimal(0.2) != Decimal("0.3"), "Decimal từ FLOAT: SAI ngay từ đầu"
    assert str(Decimal(0.1)).startswith("0.1000000000000000055511151231"), \
        "vì `Decimal(0.1)` chép lại y nguyên sai số nhị phân của float"
    # Hai dòng `Decimal(0.1)` và `Decimal("0.1")` khác nhau đúng hai dấu nháy, và một
    # trong hai đã hỏng trước khi làm gì. Cạm bẫy thật ngoài đời còn kín hơn:
    #     Decimal(du_lieu["so_tien"])     # JSON parse ra float -> hỏng
    #     Decimal(str(du_lieu["so_tien"])) # vá tạm, nhưng float đã sai từ lúc parse
    # Cách đúng duy nhất: đọc JSON bằng `parse_float=Decimal`, hoặc lưu số tiền dưới dạng
    # SỐ NGUYÊN đơn vị nhỏ nhất ngay từ giao thức — như `Tien` trong file này.

    # ---- 3. CON BUG: cộng hai loại tiền tệ ----
    sai = 100.0 + 50.0                       # 100 USD + 50 VND = ?
    assert sai == 150.0, "phép cộng chạy ngon lành, và kết quả hoàn toàn vô nghĩa"
    usd = Tien.tu("100.00", TienTe.USD)
    vnd = Tien.tu("50", TienTe.VND)
    chan = False
    try:
        usd + vnd
    except ValueError:
        chan = True
    assert chan, "value object mang LUẬT: cộng khác tệ bị chặn (bài 82)"
    assert str(usd + Tien.tu("0.50", TienTe.USD)) == "100.50 USD", "cùng tệ thì được"

    # ---- 4. CON BUG: giả định "tiền tệ nào cũng 2 chữ số thập phân" ----
    xu_cua_vnd = 100_000 * 100               # "đổi sang xu" theo thói quen
    assert xu_cua_vnd == 10_000_000, "100.000đ thành 10 triệu — sai 100 lần"
    assert (TienTe.VND.so_chu_so, TienTe.USD.so_chu_so, TienTe.JOD.so_chu_so) == (0, 2, 3), \
        "ba loại tiền tệ, ba số chữ số khác nhau"
    assert Tien.tu("100000", TienTe.VND).don_vi_nho == 100_000, "VND: 1 đồng là đơn vị nhỏ nhất"
    assert Tien.tu("100.00", TienTe.USD).don_vi_nho == 10_000, "USD: 100 đô = 10.000 cent"
    assert Tien.tu("1.500", TienTe.JOD).don_vi_nho == 1_500, "JOD: 1,5 dinar = 1500 fils"

    qua_nho = False
    try:
        Tien.tu("100.50", TienTe.VND)
    except ValueError:
        qua_nho = True
    assert qua_nho, "0,5 đồng KHÔNG tồn tại -> chặn ngay tại biên, không làm tròn lén"

    # ---- 5. CON BUG: chia 100 cho 3 làm bốc hơi tiền ----
    moi_nguoi_float = round(100.0 / 3, 2)
    assert moi_nguoi_float == 33.33, "mỗi người 33,33"
    assert round(moi_nguoi_float * 3, 2) == 99.99, "ba người cộng lại được 99,99 — thiếu 1 xu"
    # 1 xu đó đi đâu? Không đi đâu cả — nó bị làm tròn mất. Nhân với một triệu giao dịch
    # chia hoá đơn mỗi tháng, và kế toán có một khoản chênh không giải thích được.

    tram = Tien.tu("100.00", TienTe.USD)
    ba = tram.chia_deu(3)
    assert sum(t.don_vi_nho for t in ba) == tram.don_vi_nho, \
        "chia đều: tổng các phần BẰNG ĐÚNG số ban đầu"
    assert str(ba[0]) == "33.34 USD", "người đầu nhận thêm 1 cent dư"
    assert str(ba[1]) == "33.33 USD" and str(ba[2]) == "33.33 USD", "hai người sau nhận 33,33"
    # Thuật toán: chia lấy nguyên, rồi PHÁT phần dư cho các phần đầu, mỗi phần 1 đơn vị.
    # Không xu nào biến mất, không xu nào sinh ra. Ai nhận phần dư là một quyết định
    # NGHIỆP VỤ — nhưng nó phải là một quyết định, không phải hệ quả của việc làm tròn.

    theo_ti_le = Tien.tu("100.00", TienTe.USD).chia_theo(3, 7)
    assert str(theo_ti_le[0]) == "30.00 USD" and str(theo_ti_le[1]) == "70.00 USD", "chia 30/70"
    le = Tien.tu("0.05", TienTe.USD).chia_theo(3, 7)
    assert [t.don_vi_nho for t in le] == [2, 3], "5 cent chia 30/70: 2 + 3, phần dư về người đầu"

    # ---- 6. `round()` CỦA PYTHON LÀM TRÒN KIỂU NGÂN HÀNG ----
    assert round(0.5) == 0, "round(0.5) = 0, KHÔNG phải 1"
    assert round(1.5) == 2 and round(2.5) == 2, "1,5 -> 2 và 2,5 -> 2: làm tròn về số CHẴN"
    assert round(3.5) == 4, "3,5 -> 4"
    # Đây là ROUND_HALF_EVEN, chuẩn của IEEE-754 và của nhiều hệ kế toán — nó không thiên
    # vị lên trên như "làm tròn nửa lên" mà ta học ở trường. Hầu như ai cũng đoán sai câu
    # `round(2.5)`, và mã nghiệp vụ dựa vào `round()` mặc định là mã có luật làm tròn mà
    # người viết không hề chọn.

    goc = Tien.tu("10.005", TienTe.JOD)
    assert str(goc.nhan(Decimal("0.5"), ROUND_HALF_UP)) == "5.003 JOD", "HALF_UP: 5,0025 -> 5,003"
    assert str(goc.nhan(Decimal("0.5"), ROUND_HALF_EVEN)) == "5.002 JOD", "HALF_EVEN: -> 5,002"
    assert str(goc.nhan(Decimal("0.5"), ROUND_DOWN)) == "5.002 JOD", "DOWN: cắt cụt"
    # Ba chế độ, ba con số. Không có cái nào "đúng" — cái đúng là cái luật thuế/kế toán
    # của nước đó quy định. Nên `nhan()` BẮT BUỘC nhận chế độ làm tròn: không có giá trị
    # mặc định nào an toàn, và để mặc định là để người sau đoán.

    # ---- 7. THỨ NGUYÊN: tiền × tiền là vô nghĩa ----
    #     tiền × số   = tiền     (100 USD × 0.1 = 10 USD thuế)
    #     tiền ÷ tiền = TỈ LỆ    (30 USD / 100 USD = 0.3)
    #     tiền × tiền = KHÔNG CÓ NGHĨA — "đô-la bình phương" không tồn tại
    assert Tien.tu("30.00", TienTe.USD).ti_le_so_voi(Tien.tu("100.00", TienTe.USD)) == Decimal("0.3"), \
        "tiền ÷ tiền ra một tỉ lệ trần"
    assert str(Tien.tu("100.00", TienTe.USD).nhan(Decimal("0.10"), ROUND_HALF_UP)) == "10.00 USD", \
        "tiền × thuế suất ra tiền"
    khong_nhan_duoc = False
    try:
        usd * usd            # `Tien` không định nghĩa `__mul__`
    except TypeError:
        khong_nhan_duoc = True
    assert khong_nhan_duoc, "tiền × tiền -> TypeError, không phải một con số vô nghĩa"

    # ---- 8. Chỗ Python AN TOÀN HƠN Java ----
    # Java: `BigDecimal("2.0").equals(BigDecimal("2.00"))` là FALSE (equals so cả số chữ
    # số thập phân), nên cùng một số tiền nằm hai chỗ trong `HashSet`. Python thì không:
    assert Decimal("2.0") == Decimal("2.00"), "Decimal của Python so theo GIÁ TRỊ"
    assert len({Decimal("2.0"), Decimal("2.00")}) == 1, "và hash cũng khớp -> một phần tử"
    assert len({Tien.tu("2.0", TienTe.USD), Tien.tu("2.00", TienTe.USD)}) == 1, \
        "còn `Tien` thì an toàn ở mọi ngôn ngữ, vì nó so `int` và `Enum`"
    # Nhưng đừng đổi cái an toàn này lấy sự cẩu thả: `Decimal` vẫn có bối cảnh làm tròn
    # toàn cục (mặc định 28 chữ số có nghĩa), nên phép CHIA vẫn có thể mất chính xác âm
    # thầm. Số nguyên đơn vị nhỏ nhất không có vấn đề đó ở bất kỳ đâu.

    # ---- 9. Ranh giới: khi nào số nguyên đơn vị nhỏ KHÔNG đủ ----
    # `int` của Python không tràn, nên vấn đề duy nhất còn lại là ĐỘ CHIA NHỎ:
    #   - giá đơn vị nhỏ hơn đơn vị tiền nhỏ nhất (giá điện 1.234,56 đ/kWh) -> đó KHÔNG
    #     phải tiền, đó là ĐƠN GIÁ, một kiểu riêng;
    #   - tính lãi kép nhiều kỳ, cần giữ độ chính xác trung gian -> `Decimal` với bối
    #     cảnh đặt tường minh, và chỉ QUY VỀ tiền ở bước cuối.
    # Quy tắc: `int` đơn vị nhỏ cho SỐ TIỀN, `Decimal` cho ĐƠN GIÁ và TỈ LỆ, và hai thứ
    # đó là hai kiểu dữ liệu khác nhau — đúng như phần 7 nói.

    print("OK")
