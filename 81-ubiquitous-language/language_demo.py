# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học. Cùng một nghiệp vụ viết hai lần, và một
# con bug thật: `status >= 3` cộng nhầm đơn đã huỷ vào doanh thu.
# Tại sao cần học: Python cho phép kiểm chứng ngôn ngữ chung bằng chính máy — nó
# tự đọc được tên lớp, tên phương thức, tên thuộc tính lúc chạy. Bài dựng một bài
# test THẬT: "mọi từ nghiệp vụ trong bảng thuật ngữ đều phải xuất hiện trong API
# của mô hình miền". Đó là thứ biến "đặt tên cho đúng" từ một lời khuyên thành một
# ràng buộc chạy trong CI — và nó chặn được việc ai đó thêm một khái niệm nghiệp
# vụ mới rồi đặt tên là `tmp2` hoặc `handle_case_3`.

from dataclasses import dataclass, fields
from enum import Enum


# =====================================================================
# BẢN 1 — từ ngữ của LẬP TRÌNH VIÊN
# =====================================================================
@dataclass
class DataRecord:
    id: str
    amt: int
    status: int   # 1=?, 2=?, 3=?, 4=?  <- bảng dịch nằm trong đầu ai đó
    flag1: bool
    flag2: bool


class DataProcessor:
    def __init__(self):
        self.records = []

    def add(self, r):
        self.records.append(r)

    # Người viết hàm này hiểu "status >= 3 nghĩa là đã xong". Và anh ta ĐÚNG —
    # theo cách anh ta hiểu chữ "xong".
    def calc_total(self):
        return sum(r.amt for r in self.records if r.status >= 3)

    # Người viết hàm này (ba tháng sau, đội khác) cũng hiểu "status >= 3 là đã xong".
    def count_done(self):
        return sum(1 for r in self.records if r.status >= 3)

    def calc_refundable(self):
        return sum(r.amt for r in self.records if r.status == 4)


# =====================================================================
# BẢN 2 — từ ngữ của NGƯỜI LÀM NGHIỆP VỤ
# =====================================================================
class TrangThaiDonHang(Enum):
    MOI_TAO = "MOI_TAO"
    DA_THANH_TOAN = "DA_THANH_TOAN"
    DA_GIAO = "DA_GIAO"
    DA_HUY = "DA_HUY"

    # Câu hỏi nghiệp vụ có ĐÚNG MỘT câu trả lời, và nó nằm ở đây.
    def la_hoan_tat(self):
        return self is TrangThaiDonHang.DA_GIAO

    # "Kết thúc" và "hoàn tất" là HAI khái niệm khác nhau — đó chính là chỗ bản 1 nhầm.
    def la_ket_thuc(self):
        return self in (TrangThaiDonHang.DA_GIAO, TrangThaiDonHang.DA_HUY)

    def duoc_hoan_tien(self):
        return self is TrangThaiDonHang.DA_HUY


@dataclass(frozen=True)
class DonHang:
    ma_don: str
    so_tien: int
    trang_thai: TrangThaiDonHang
    la_khach_than_thiet: bool
    giao_nhanh_trong_ngay: bool


class SoDonHang:
    def __init__(self):
        self._cac_don = []

    def ghi_nhan(self, d):
        self._cac_don.append(d)

    # Tên hàm là câu người làm nghiệp vụ nói ra miệng.
    def doanh_thu_da_hoan_tat(self):
        return sum(d.so_tien for d in self._cac_don if d.trang_thai.la_hoan_tat())

    def so_don_da_ket_thuc(self):
        return sum(1 for d in self._cac_don if d.trang_thai.la_ket_thuc())

    def so_tien_phai_hoan_lai(self):
        return sum(d.so_tien for d in self._cac_don if d.trang_thai.duoc_hoan_tien())


# ---- Self-check ----
if __name__ == "__main__":
    # Cùng bốn đơn hàng, biểu diễn hai cách.
    cu = DataProcessor()
    for r in [DataRecord("DH-1", 100_000, 3, True, False),
              DataRecord("DH-2", 200_000, 2, False, False),
              DataRecord("DH-3", 300_000, 4, True, True),    # ĐÃ HUỶ
              DataRecord("DH-4", 400_000, 3, False, True)]:
        cu.add(r)

    moi = SoDonHang()
    for d in [DonHang("DH-1", 100_000, TrangThaiDonHang.DA_GIAO, True, False),
              DonHang("DH-2", 200_000, TrangThaiDonHang.DA_THANH_TOAN, False, False),
              DonHang("DH-3", 300_000, TrangThaiDonHang.DA_HUY, True, True),
              DonHang("DH-4", 400_000, TrangThaiDonHang.DA_GIAO, False, True)]:
        moi.ghi_nhan(d)

    # ---- 1. CON BUG: `status >= 3` cộng nhầm đơn đã huỷ vào doanh thu ----
    assert cu.calc_total() == 800_000, "bản cũ: 100.000 + 300.000 + 400.000"
    assert moi.doanh_thu_da_hoan_tat() == 500_000, "bản mới: 100.000 + 400.000"
    assert cu.calc_total() - moi.doanh_thu_da_hoan_tat() == 300_000, \
        "chênh đúng bằng số tiền của đơn ĐÃ HUỶ"
    # 300.000đ doanh thu không tồn tại vừa đi vào báo cáo tài chính. Không ngoại lệ,
    # không cảnh báo, và cả hai hàm đều "chạy đúng" theo ý người viết chúng.

    # ---- 2. Enum của Python chặn được phép so sánh vô nghĩa ----
    khong_so_sanh_duoc = False
    try:
        _ = TrangThaiDonHang.DA_HUY >= 3
    except TypeError:
        khong_so_sanh_duoc = True
    assert khong_so_sanh_duoc, "Enum không so sánh được với số — cái bẫy bị chặn"
    # Nhưng chú ý: `IntEnum` thì SO SÁNH ĐƯỢC, và cái bẫy quay lại nguyên vẹn:
    from enum import IntEnum

    class TrangThaiCu(IntEnum):
        MOI_TAO = 1
        DA_THANH_TOAN = 2
        DA_GIAO = 3
        DA_HUY = 4

    assert TrangThaiCu.DA_HUY >= 3, "IntEnum tự chuyển thành số — cái bẫy quay lại"
    assert TrangThaiCu.DA_HUY > TrangThaiCu.DA_GIAO, "và phép so sánh sai chạy bình thường"
    # `IntEnum` chỉ nên dùng khi bạn BẮT BUỘC phải tương thích với một giao thức cũ
    # dùng số. Với mô hình miền thì luôn là `Enum` thuần.

    # ---- 3. Vì sao `>=` sai về BẢN CHẤT ----
    # `status >= 3` chỉ đúng nếu 4 là "xong hơn" 3. Trong nghiệp vụ thì không: ĐÃ HUỶ
    # không phải một dạng "đã giao ở mức cao hơn". Con số có thứ tự, nhưng không có Ý
    # NGHĨA — và thứ tự đó do lập trình viên tình cờ đánh số, không phải do nghiệp vụ.
    assert not TrangThaiDonHang.DA_HUY.la_hoan_tat(), "phải hỏi la_hoan_tat()"

    # ---- 4. Hai khái niệm khác nhau, hai cái tên khác nhau ----
    assert moi.so_don_da_ket_thuc() == 3, "KẾT THÚC: đã giao (2) + đã huỷ (1)"
    assert moi.doanh_thu_da_hoan_tat() == 500_000, "HOÀN TẤT: chỉ đã giao"
    assert cu.count_done() == 3, "bản cũ dùng CÙNG điều kiện cho cả hai câu hỏi"
    # `count_done()` tình cờ ĐÚNG (nếu ý là "kết thúc"), còn `calc_total()` thì SAI.
    # Cùng một biểu thức, một chỗ đúng một chỗ sai — và không có gì nói cho bạn biết.

    # ---- 5. Bảng dịch — thứ đáng lẽ không nên tồn tại ----
    bang_dich = {
        "DataRecord": "đơn hàng", "amt": "số tiền",
        "status=1": "mới tạo", "status=2": "đã thanh toán",
        "status=3": "đã giao", "status=4": "đã huỷ",
        "flag1": "khách thân thiết", "flag2": "giao nhanh trong ngày",
        "calc_total": "doanh thu (?)",
    }
    assert len(bang_dich) == 9, "chín mục phải nhớ, chỉ cho MỘT lớp"
    assert {} == {}, "với bản mới, bảng dịch RỖNG — tên trong code CHÍNH LÀ tên nghiệp vụ"
    # Ngôn ngữ chung không phải là "đặt tên tiếng Việt". Nó là: KHÔNG CÓ bảng dịch nào
    # giữa lời người làm nghiệp vụ nói và chữ trong mã nguồn.

    # ---- 6. Điều chỉ Python làm gọn: KIỂM CHỨNG BẰNG MÁY ----
    # Bảng thuật ngữ của dự án — do người làm nghiệp vụ duyệt, không phải lập trình viên bịa.
    TU_NGHIEP_VU = ["don_hang", "doanh_thu", "hoan_tat", "ket_thuc", "huy",
                    "hoan_tien", "than_thiet", "giao_nhanh"]

    def ten_trong_api(*cac_lop):
        """Gom mọi tên công khai của mô hình miền: lớp, phương thức, thuộc tính, hằng enum."""
        ten = []
        for lop in cac_lop:
            # Tên lớp — thường là phần QUAN TRỌNG NHẤT của ngôn ngữ chung.
            ten.append(lop.__name__)
            # `dir()` không đủ: với `Enum`, nó chỉ liệt kê hằng thành viên, KHÔNG liệt
            # kê phương thức viết trong thân lớp. Phải đọc thêm `vars()` — nơi giữ
            # đúng những gì khai báo trực tiếp trong lớp đó.
            ten += [t for t in dir(lop) if not t.startswith("_")]
            ten += [t for t in vars(lop) if not t.startswith("_")]
            if hasattr(lop, "__dataclass_fields__"):
                ten += [f.name for f in fields(lop)]
        return " ".join(ten).lower()

    api = ten_trong_api(SoDonHang, DonHang, TrangThaiDonHang)
    for tu in TU_NGHIEP_VU:
        assert tu.replace("_", "") in api.replace("_", ""), f"từ nghiệp vụ '{tu}' có trong API"
    # Sáu dòng trên là một bài test dùng được thật, chạy trong CI. Nó chặn việc ai đó
    # thêm một khái niệm nghiệp vụ mới rồi đặt tên là `tmp2` hoặc `handle_case_3`.

    # Và chiều ngược lại cũng kiểm được: API KHÔNG được chứa từ vô nghĩa.
    TU_CAM = ["flag", "data", "process", "handle", "temp", "misc", "util", "manager"]
    for tu in TU_CAM:
        assert tu not in api, f"API của mô hình miền không được chứa từ vô nghĩa '{tu}'"
    api_cu = ten_trong_api(DataProcessor, DataRecord)
    assert any(tu in api_cu for tu in TU_CAM), "bản cũ thì vi phạm ngay — 'data', 'flag', 'process'"

    # ---- 7. Ranh giới: từ ngữ nào KHÔNG thuộc ngôn ngữ chung ----
    # Không phải mọi tên đều phải là tiếng nghiệp vụ. Ba loại nằm ngoài:
    #   - thuật toán và cấu trúc dữ liệu: `bisect`, `defaultdict`;
    #   - biến cục bộ ngắn trong một vòng lặp ba dòng: `i`, `n`, `d`;
    #   - hạ tầng: `connection_pool`, `retry_policy`.
    # Ngôn ngữ chung áp cho MÔ HÌNH MIỀN — nơi người làm nghiệp vụ và lập trình viên
    # phải nói chuyện được với nhau. Ép nó lên mọi dòng code là hiểu sai.
    #
    # Chú ý điều này khi dùng bài test ở phần 6: nó chỉ nên chạy trên gói `mien/`,
    # không chạy trên gói `hatang/`.
    assert "flag1" in bang_dich, "`flag1` là tên miền -> phải sửa"
    assert "defaultdict" not in bang_dich, "`defaultdict` là tên kỹ thuật -> giữ nguyên"

    # ---- 8. Luật quan trọng nhất: NGÔN NGỮ ĐI HAI CHIỀU ----
    # Nếu người làm nghiệp vụ nói "đơn treo" mà trong code không có khái niệm đó, thì
    # hoặc bạn thiếu một trạng thái, hoặc họ đang dùng một từ mà chính họ cũng chưa
    # định nghĩa rõ. Cả hai đều là một cuộc trao đổi cần xảy ra — và mã nguồn vừa làm
    # lộ ra điều đó.
    #
    # Ngược lại, nếu trong code có `TRANG_THAI_TAM` mà không ai bên nghiệp vụ biết nó
    # là gì, thì đó là khái niệm do lập trình viên bịa ra — và nó sẽ trôi dần khỏi thực
    # tế cho tới lúc gây ra một con số sai như ở phần 1.
    assert len(TrangThaiDonHang) == 4, "bốn trạng thái, và cả bốn đều có tên nghiệp vụ"

    print("OK")
