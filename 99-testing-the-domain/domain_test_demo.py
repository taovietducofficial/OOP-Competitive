# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — test mô hình miền không khung phần mềm, không
# CSDL, không mock. Ba con bug: test bám vào CÁCH LÀM; test dựng dữ liệu quá nhiễu; và
# bản giả nói dối so với bản thật.
# Tại sao cần học: bài này có một điều phải nói thẳng về chính series này. Mọi file
# self-check Python ở đây dùng `assert` trần — và `assert` BIẾN MẤT HOÀN TOÀN khi chạy
# `python -O`. Không phải "bị bỏ qua", mà là không được biên dịch vào bytecode. Nghĩa là
# một bộ test viết bằng `assert` chạy dưới cờ tối ưu sẽ báo XANH trong 0 giây mà không
# kiểm gì cả. Bài đo đúng điều đó, và chỉ ra ranh giới: `assert` dùng cho TEST và cho
# BẤT BIẾN NỘI BỘ; validate dữ liệu ở biên thì tuyệt đối không.

import random
from dataclasses import dataclass, field
from typing import Dict, List


# =====================================================================
# MIỀN — đối tượng được test
# =====================================================================
@dataclass(frozen=True)
class DongHang:
    san_pham: str
    don_gia: int
    so_luong: int

    def thanh_tien(self):
        return self.don_gia * self.so_luong


class DonHang:
    HAN_MUC = 50_000_000

    def __init__(self, ma):
        self.ma = ma
        self._cac_dong: List[DongHang] = []

    def them_dong(self, sp, don_gia, sl, kho):
        if self.tong_tien() + don_gia * sl > DonHang.HAN_MUC:
            raise RuntimeError("đơn vượt hạn mức")
        kho.giu_cho(sp, sl)
        self._cac_dong.append(DongHang(sp, don_gia, sl))

    def them_nhieu_dong(self, ds, kho):
        """Phiên bản gộp: giữ chỗ MỘT lần cho nhiều dòng. Hành vi ngoài KHÔNG đổi."""
        them = sum(d.thanh_tien() for d in ds)
        if self.tong_tien() + them > DonHang.HAN_MUC:
            raise RuntimeError("đơn vượt hạn mức")
        gop: Dict[str, int] = {}
        for d in ds:
            gop[d.san_pham] = gop.get(d.san_pham, 0) + d.so_luong
        kho.giu_cho_nhieu(gop)                       # MỘT lượt gọi thay vì n lượt
        self._cac_dong.extend(ds)

    def tong_tien(self):
        return sum(d.thanh_tien() for d in self._cac_dong)

    @property
    def so_dong(self):
        return len(self._cac_dong)

    def chia_deu(self, n):
        """Chia đều tiền đơn cho n người — bất biến ở phần 3 (bài 90)."""
        moi, du = divmod(self.tong_tien(), n)
        return [moi + (1 if i < du else 0) for i in range(n)]


class KhoGia:
    """Bản GIẢ — một cài đặt thật, không phải mock."""

    def __init__(self):
        self.da_giu: Dict[str, int] = {}
        self.so_luot_goi = 0

    def giu_cho(self, sp, sl):
        self.so_luot_goi += 1
        self.da_giu[sp] = self.da_giu.get(sp, 0) + sl

    def giu_cho_nhieu(self, gop):
        self.so_luot_goi += 1
        for k, v in gop.items():
            self.da_giu[k] = self.da_giu.get(k, 0) + v


class KhoThat:
    """Bản "thật" — có thêm một luật mà bản giả không có."""

    def __init__(self):
        self.da_giu: Dict[str, int] = {}

    def giu_cho(self, sp, sl):
        if sl <= 0:
            raise ValueError("số lượng giữ chỗ phải dương")
        self.da_giu[sp] = self.da_giu.get(sp, 0) + sl

    def giu_cho_nhieu(self, gop):
        for k, v in gop.items():
            self.giu_cho(k, v)


# BỘ DỰNG DỮ LIỆU TEST — phần 2
@dataclass
class DonHangBuilder:
    _ma: str = "DH-MAU"
    _dong: List[DongHang] = field(default_factory=list)

    def ma(self, m):
        self._ma = m
        return self

    def voi_dong(self, sp, gia, sl):
        self._dong.append(DongHang(sp, gia, sl))
        return self

    def dung(self, kho):
        d = DonHang(self._ma)
        if self._dong:
            d.them_nhieu_dong(self._dong, kho)
        return d


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: test bám vào CÁCH LÀM, không bám vào HÀNH VI ----
    kho_a, don_a = KhoGia(), DonHang("DH-01")
    don_a.them_dong("laptop", 1_000_000, 1, kho_a)
    don_a.them_dong("chuột", 200_000, 2, kho_a)
    don_a.them_dong("bàn phím", 300_000, 1, kho_a)
    assert kho_a.so_luot_goi == 3, "test-theo-cách-làm: 'kho phải được gọi ĐÚNG 3 lần'"
    # Đây chính là thứ `unittest.mock` khuyến khích: `kho.giu_cho.assert_called_once_with(...)`,
    # `assert kho.giu_cho.call_count == 3`.

    # Hôm nay ai đó gộp ba lượt gọi thành một. HÀNH VI NGOÀI KHÔNG ĐỔI.
    kho_b, don_b = KhoGia(), DonHang("DH-01")
    don_b.them_nhieu_dong([DongHang("laptop", 1_000_000, 1),
                           DongHang("chuột", 200_000, 2),
                           DongHang("bàn phím", 300_000, 1)], kho_b)

    assert kho_b.so_luot_goi == 1, "test-theo-cách-làm giờ ĐỎ: 1 ≠ 3"
    assert don_a.tong_tien() == don_b.tong_tien(), "nhưng tổng tiền GIỐNG HỆT"
    assert don_a.so_dong == don_b.so_dong, "cùng số dòng"
    assert kho_a.da_giu == kho_b.da_giu, "và kho giữ chỗ ĐÚNG NHƯ NHAU"
    # Ba dòng cuối là test-theo-HÀNH-VI, và cả ba vẫn xanh. Khác biệt:
    #   - Test hành vi hỏng khi NGHIỆP VỤ sai  -> tín hiệu THẬT.
    #   - Test cách làm hỏng khi CODE ĐỔI      -> tín hiệu GIẢ.
    # Bộ test đầy tín hiệu giả là bộ test bị tắt sau ba tháng.
    #
    # Ngoại lệ hợp lệ duy nhất: khi VIỆC GỌI CHÍNH LÀ hành vi cần kiểm — "đã gửi đúng một
    # email cho khách" (bài 84 phần 4).

    # ---- 2. BỘ DỰNG DỮ LIỆU: test đọc lên phải nói được nó kiểm gì ----
    kho2 = KhoGia()
    gan_han_muc = (DonHangBuilder()
                   .ma("DH-02")
                   .voi_dong("máy chủ", 49_000_000, 1)     # <- chi tiết DUY NHẤT quan trọng
                   .dung(kho2))
    vuot_han_muc = False
    try:
        gan_han_muc.them_dong("laptop", 2_000_000, 1, kho2)
    except RuntimeError:
        vuot_han_muc = True
    assert vuot_han_muc, "đơn 49 triệu + 2 triệu -> vượt hạn mức 50 triệu"
    # Bộ dựng có giá trị mặc định cho MỌI thứ, và test chỉ nói ra thứ nó QUAN TÂM.

    # ---- 3. BẤT BIẾN: kiểm với NGHÌN đầu vào, không phải ba ----
    rnd = random.Random(42)                       # hạt giống CỐ ĐỊNH -> tái hiện được
    so_ca_chay = 0
    for _ in range(1000):
        k = KhoGia()
        d = DonHangBuilder().voi_dong("x", rnd.randint(1, 1_000_000), rnd.randint(1, 40)).dung(k)
        nguoi = rnd.randint(1, 9)
        phan = d.chia_deu(nguoi)
        assert sum(phan) == d.tong_tien(), "BẤT BIẾN: tổng các phần = tổng ban đầu"
        assert len(phan) == nguoi, "BẤT BIẾN: đúng số phần được yêu cầu"
        assert max(phan) - min(phan) <= 1, "BẤT BIẾN: chênh lệch không quá 1 đơn vị"
        so_ca_chay += 1
    assert so_ca_chay == 1000, "1.000 ca sinh ngẫu nhiên, 3 bất biến, 0 dòng dữ liệu gõ tay"
    # Test theo ví dụ trả lời "với đầu vào này thì sao". Test theo bất biến trả lời "với
    # MỌI đầu vào thì điều gì luôn đúng" — và nó bắt được những ca không ai nghĩ ra để
    # viết ví dụ. (Thư viện `hypothesis` làm việc này chuyên nghiệp hơn nhiều, kể cả tự
    # thu nhỏ ca lỗi; mười dòng trên là để thấy ý tưởng không có gì huyền bí.)
    # Hạt giống cố định là bắt buộc: một test đỏ ngẫu nhiên mà không tái hiện được thì vô
    # dụng — và tệ hơn, nó sẽ bị đánh dấu "bỏ qua".

    # ---- 4. CON BUG: BẢN GIẢ NÓI DỐI ----
    kho_noi_doi = KhoGia()
    kho_noi_doi.giu_cho("laptop", -5)             # số lượng ÂM
    assert kho_noi_doi.da_giu["laptop"] == -5, "bản giả nhận số lượng âm — test XANH"

    ban_that_no = False
    try:
        KhoThat().giu_cho("laptop", -5)
    except ValueError:
        ban_that_no = True
    assert ban_that_no, "bản thật ném ngoại lệ — bug đi thẳng ra production"
    # Cách chữa là BỘ KIỂM TRA HỢP ĐỒNG (bài 68): một bộ test viết một lần, chạy trên MỌI
    # cài đặt của cổng. Bản giả nào không qua được thì không được dùng.
    so_cai_dat_qua = 0
    for k in (KhoGia(), KhoThat()):
        try:
            k.giu_cho("x", -1)
        except ValueError:
            so_cai_dat_qua += 1
    assert so_cai_dat_qua == 1, "chạy hợp đồng trên 2 cài đặt -> lộ ra ngay cái nào nói dối"

    # ---- 5. ĐIỀU PHẢI NÓI THẲNG VỀ `assert` CỦA PYTHON ----
    # `assert` là câu lệnh của ngôn ngữ, và cờ `-O` XOÁ nó khỏi bytecode. Không phải "bỏ
    # qua lúc chạy" — nó không tồn tại trong file đã biên dịch.
    import subprocess
    import sys
    ma_thu = "assert False, 'phải nổ'\nprint('KHONG NO')"
    binh_thuong = subprocess.run([sys.executable, "-c", ma_thu], capture_output=True, text=True)
    toi_uu = subprocess.run([sys.executable, "-O", "-c", ma_thu], capture_output=True, text=True)
    assert binh_thuong.returncode != 0, "chạy thường: AssertionError, thoát khác 0"
    assert toi_uu.returncode == 0 and "KHONG NO" in toi_uu.stdout, \
        "chạy `python -O`: assert BIẾN MẤT, chương trình đi tiếp như không có gì"
    # Hệ quả cho chính series này: mọi file self-check `.py` ở đây phải chạy KHÔNG có `-O`.
    # Chạy `python -O bai.py` sẽ in "OK" trong 0 giây mà không kiểm một dòng nào — và đó
    # là kiểu "xanh" nguy hiểm nhất tồn tại.
    #
    # Ranh giới phải nhớ:
    #   `assert` DÙNG ĐƯỢC : trong test, và cho bất biến NỘI BỘ ("chỗ này không thể xảy ra").
    #   `assert` KHÔNG ĐƯỢC: validate dữ liệu từ người dùng / hệ ngoài (bài 76, bài 94).
    #                        Ở đó phải `if ...: raise ValueError(...)` — vì nó phải sống sót
    #                        qua `-O`, và vì nó là một quyết định nghiệp vụ, không phải một
    #                        lời khẳng định của lập trình viên.
    # Chú ý `DonHang.them_dong` ở trên dùng `if ... raise RuntimeError`, KHÔNG dùng
    # `assert` — đúng theo ranh giới này.

    # ---- 6. TEST MIỀN KHÔNG CẦN KHUNG PHẦN MỀM ----
    # Toàn bộ file này — và mọi file self-check trong series — là hàm + `assert`. Không
    # pytest, không mock, không fixture, 0 mili-giây khởi động.
    #
    # Đó không phải "lười không dùng pytest". Nó là BẰNG CHỨNG rằng mô hình miền đã tách
    # sạch khỏi hạ tầng (bài 98). Phép thử ngược lại cũng đúng:
    #
    #   NẾU TEST MIỀN CỦA BẠN CẦN MỘT KHUNG PHẦN MỀM ĐỂ CHẠY,
    #   THÌ THỨ BẠN ĐANG TEST KHÔNG PHẢI MIỀN.
    #
    # Trong dự án thật vẫn nên dùng pytest — vì nó cho báo cáo, chạy song song, tích hợp
    # CI, và `assert` của nó được viết lại để in ra giá trị hai vế. Nhưng test miền phải
    # chạy được KHÔNG CẦN nó.
    assert don_a.ma == "DH-01", "một hàm, một assert, không hạ tầng nào"

    # ---- 7. CÁI GÌ KHÔNG NÊN TEST, VÀ ĐẶT TÊN THẾ NÀO ----
    #   - Getter thuần: `assert d.ma == "DH-01"` không kiểm được luật nào.
    #   - Thư viện chuẩn: `dict` đã được test rồi.
    #   - Cài đặt riêng tư: nếu phải chạm `_cac_dong` để test, thì test đó đang bám vào
    #     cách làm (phần 1) — hãy test qua cửa chính.
    # Ngược lại, thứ ĐÁNG test nhất là những chỗ có `if` mang nghĩa nghiệp vụ: hạn mức,
    # chuyển trạng thái (bài 89), luật giá, luật chia tiền.
    #
    # Và tên test là một câu nghiệp vụ:
    #   TỆ : "test1", "test_them_dong"
    #   TỐT: "đơn 49 triệu + 2 triệu -> vượt hạn mức 50 triệu"
    # Khi test đỏ lúc 2 giờ sáng, dòng chữ đó là toàn bộ thứ người trực có.
    assert DonHang.HAN_MUC == 50_000_000, "luật nghiệp vụ có `if` -> đáng test"

    print("OK")
