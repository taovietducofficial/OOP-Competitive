# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — tách mô hình ĐỌC khỏi mô hình GHI. Ba con bug:
# dựng màn hình danh sách bằng aggregate làm 1.000 lượt truy vấn và tải 2.500 object;
# thêm cột hiển thị làm bẩn mô hình miền; và mô hình đọc bị dùng để GHI.
# Tại sao cần học: ở Java và C++, mô hình đọc là một `record`/`struct` không có setter —
# nên "dùng mô hình đọc để ghi" là lỗi biên dịch, và cái bẫy đó không tồn tại. Ở Python
# thì nó tồn tại theo hai cách, và cả hai đều trông vô hại: mô hình đọc là dataclass khả
# biến (sửa nó tưởng như sửa dữ liệu), và tệ hơn — nếu truy vấn đọc trả về CHÍNH danh
# sách dòng hàng của aggregate thay vì một bản chụp, thì màn hình danh sách vừa có quyền
# ghi thẳng vào miền, vượt mặt mọi bất biến. Bài đo cả hai.

from dataclasses import dataclass, field, FrozenInstanceError
from typing import Dict, List


# =====================================================================
# BÊN GHI — aggregate, đúng như bài 83: có bất biến, có hành vi, có ranh giới
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

    def __init__(self, ma, ma_khach):
        self._ma, self._ma_khach = ma, ma_khach
        self._cac_dong: List[DongHang] = []
        self._trang_thai = "MOI_TAO"

    def them_dong(self, sp, don_gia, sl):
        if self.tong_tien() + don_gia * sl > DonHang.HAN_MUC:
            raise RuntimeError("đơn vượt hạn mức")
        self._cac_dong.append(DongHang(sp, don_gia, sl))

    def giao(self):
        self._trang_thai = "DA_GIAO"

    def tong_tien(self):
        return sum(d.thanh_tien() for d in self._cac_dong)

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
    def so_dong(self):
        return len(self._cac_dong)


@dataclass(frozen=True)
class KhachHang:
    ma: str
    ten: str


# =====================================================================
# BÊN ĐỌC — mô hình PHẲNG, dựng riêng cho MỘT màn hình
# =====================================================================
@dataclass(frozen=True)      # `frozen` KHÔNG phải để tối ưu — xem phần 6
class DongDanhSachDon:
    """Không hành vi, không bất biến, không setter. Nó không phải entity, không phải
    value object của miền — nó là MỘT DÒNG TRÊN MÀN HÌNH, và chỉ có thế.
    Chú ý: nó ghép dữ liệu của HAI aggregate — điều bên ghi bị cấm làm (bài 83)."""
    ma_don: str
    ten_khach: str
    so_dong: int
    tong_tien: int
    trang_thai: str


# =====================================================================
# "CSDL" giả — đếm số lượt truy vấn và số object đã tải
# =====================================================================
@dataclass
class Csdl:
    don_hang: Dict[str, DonHang] = field(default_factory=dict)
    khach_hang: Dict[str, KhachHang] = field(default_factory=dict)
    so_luot_truy_van: int = 0
    so_object_da_tai: int = 0

    def dat_lai(self):
        self.so_luot_truy_van = 0
        self.so_object_da_tai = 0

    def tai_don(self, ma):
        """Đường GHI: tải aggregate TRỌN VẸN (bắt buộc, để kiểm bất biến — bài 83)."""
        self.so_luot_truy_van += 1
        d = self.don_hang[ma]
        self.so_object_da_tai += 1 + d.so_dong          # root + các dòng con
        return d

    def tai_khach(self, ma):
        self.so_luot_truy_van += 1
        self.so_object_da_tai += 1
        return self.khach_hang[ma]

    def truy_van_danh_sach(self):
        """Đường ĐỌC: MỘT truy vấn, trả về đúng những cột màn hình cần."""
        self.so_luot_truy_van += 1                      # đúng 1 — dù có bao nhiêu đơn
        ra = []
        for d in self.don_hang.values():
            self.so_object_da_tai += 1                  # đúng 1 dòng phẳng cho mỗi đơn
            ra.append(DongDanhSachDon(d.ma, self.khach_hang[d.ma_khach].ten,
                                      d.so_dong, d.tong_tien(), d.trang_thai))
        return ra


# ---- Self-check ----
if __name__ == "__main__":
    db = Csdl()
    for i in range(500):
        db.khach_hang[f"KH-{i}"] = KhachHang(f"KH-{i}", f"Khách {i}")
        d = DonHang(f"DH-{i}", f"KH-{i}")
        d.them_dong("laptop", 1_000_000, 1)
        d.them_dong("chuột", 200_000, 2)
        d.them_dong("bàn phím", 300_000, 1)
        db.don_hang[f"DH-{i}"] = d

    # ---- 1. CON BUG: dựng màn hình danh sách bằng AGGREGATE ----
    # Màn hình cần 5 cột: mã đơn, tên khách, số dòng, tổng tiền, trạng thái.
    db.dat_lai()
    qua_aggregate = []
    for ma in db.don_hang:
        d = db.tai_don(ma)                          # 1 truy vấn / đơn
        k = db.tai_khach(d.ma_khach)                # + 1 truy vấn / đơn  <- N+1
        qua_aggregate.append(DongDanhSachDon(d.ma, k.ten, d.so_dong, d.tong_tien(), d.trang_thai))
    assert db.so_luot_truy_van == 1000, "1.000 lượt truy vấn cho MỘT màn hình"
    assert db.so_object_da_tai == 2500, "và 2.500 object: 500 đơn × (1 root + 3 dòng) + 500 khách"
    assert len(qua_aggregate) == 500, "để hiển thị đúng 500 dòng"
    # Đây là bài toán N+1 kinh điển, và nó KHÔNG phải lỗi của ORM — nó là hệ quả trực tiếp
    # của việc dùng mô hình GHI để trả lời một câu hỏi ĐỌC. Aggregate bắt buộc phải tải
    # trọn vẹn (bài 83), nên mỗi đơn kéo theo cả các dòng hàng mà màn hình chỉ cần biết
    # SỐ LƯỢNG của chúng.

    # ---- 2. BẢN ĐÚNG: một truy vấn, một mô hình phẳng ----
    db.dat_lai()
    qua_model_doc = db.truy_van_danh_sach()
    assert db.so_luot_truy_van == 1, "ĐÚNG MỘT lượt truy vấn"
    assert db.so_object_da_tai == 500, "và đúng 500 object — mỗi dòng màn hình một object"
    assert qua_model_doc == qua_aggregate, "cùng kết quả, từng dòng một"
    assert 2500 // 500 == 5, "gấp 5 lần số object, và gấp 1.000 lần số lượt truy vấn"

    # ---- 3. MÔ HÌNH ĐỌC ĐƯỢC PHÉP LÀM ĐIỀU BÊN GHI BỊ CẤM ----
    # `DongDanhSachDon` ghép dữ liệu của HAI aggregate: đơn hàng và khách hàng. Ở bên ghi
    # điều đó bị cấm (bài 83); ở bên đọc nó hoàn toàn hợp lệ — vì mô hình đọc KHÔNG BAO
    # GIỜ GHI, nên nó không có bất biến nào để giữ, không có ranh giới transaction nào để
    # tôn trọng.
    assert qua_model_doc[0].ten_khach == "Khách 0", "tên khách nằm ngay trong dòng đọc"
    assert len(DongDanhSachDon.__dataclass_fields__) == 5, "5 cột, đúng bằng màn hình"
    # Đây là điểm giải phóng lớn nhất của CQRS: bên đọc được ghép bảng thoải mái, đọc chéo
    # ngữ cảnh, lưu dữ liệu trùng lặp — và không gì trong số đó gây hại, vì nó không phải
    # nguồn sự thật.

    # ---- 4. CON BUG: thêm cột hiển thị làm BẨN mô hình miền ----
    # Màn hình cần thêm cột "tên khách". Với mô hình dùng chung, phản xạ là thêm `ten_khach`
    # vào `DonHang` "cho tiện".
    #   - `DonHang` giờ giữ dữ liệu của aggregate khác -> phá bài 83;
    #   - tên khách đổi thì phải cập nhật mọi đơn hàng cũ -> hoặc là hiển thị sai;
    #   - và không ai biết `DonHang.ten_khach` là bản chụp lúc đặt hay giá trị hiện tại.
    # Với mô hình đọc: thêm một field vào `DongDanhSachDon`, sửa một câu truy vấn.
    assert not hasattr(db.don_hang["DH-0"], "ten_khach"), "miền không biết tên khách là gì"
    # Ghi chú: nếu nghiệp vụ THẬT SỰ cần "tên khách tại thời điểm đặt" (hoá đơn phải in
    # đúng tên lúc đó), thì đó là value object của miền, không phải nhu cầu hiển thị — và
    # lúc đó nó thuộc về `DonHang`. Câu hỏi là "nghiệp vụ có cần không", không phải "màn
    # hình có hiện không".

    # ---- 5. CON BUG: dùng mô hình ĐỌC để GHI ----
    khong_ghi_duoc = False
    try:
        qua_model_doc[0].tong_tien = 999
    except FrozenInstanceError:
        khong_ghi_duoc = True
    assert khong_ghi_duoc, "`frozen=True` chặn việc 'sửa cho nhanh' qua mô hình đọc"
    # Nếu `DongDanhSachDon` là dataclass thường, dòng trên chạy êm — và nó KHÔNG ghi vào
    # CSDL. Người viết tưởng đã sửa dữ liệu; thật ra chỉ sửa một bản sao trong bộ nhớ của
    # màn hình. Đó là bug im lặng nhất trong cả bài: không lỗi, không hiệu lực.
    don = db.tai_don("DH-0")
    chan = False
    try:
        don.them_dong("máy chủ", 60_000_000, 1)
    except RuntimeError:
        chan = True
    assert chan, "bên GHI vẫn giữ bất biến — mọi thay đổi phải đi qua aggregate"
    assert qua_model_doc[0].tong_tien == 1_700_000, "bên ĐỌC chỉ nhìn, không đụng vào"

    # ---- 6. CÁI BẪY NẶNG HƠN CỦA PYTHON: truy vấn đọc TRẢ VỀ RUỘT CỦA AGGREGATE ----
    def truy_van_ro_ri(csdl, ma):
        """Truy vấn "cho nhanh": trả thẳng danh sách dòng hàng của aggregate."""
        return csdl.don_hang[ma]._cac_dong          # <- một dòng, và ranh giới biến mất

    dong_ro_ri = truy_van_ro_ri(db, "DH-1")
    truoc = db.don_hang["DH-1"].tong_tien()
    dong_ro_ri.append(DongHang("máy chủ", 60_000_000, 1))     # màn hình vừa GHI vào miền
    sau = db.don_hang["DH-1"].tong_tien()
    assert sau - truoc == 60_000_000, "một truy vấn ĐỌC vừa đẩy đơn hàng vượt hạn mức"
    assert sau > DonHang.HAN_MUC, "và bất biến 'tổng ≤ 50 triệu' bị vượt mặt hoàn toàn"
    dong_ro_ri.pop()                                          # dọn lại cho các phần sau
    # Đây là bài 83 phần 7 quay lại ở một vỏ bọc mới, và lần này nó nguy hiểm hơn vì mã
    # gây lỗi nằm ở tầng ĐỌC — nơi không ai đi tìm bug ghi dữ liệu. Ở Java/C++ điều này
    # khó xảy ra hơn (`List.copyOf`, `const&`); ở Python thì `return self._cac_dong` là
    # cách viết tự nhiên nhất, và nó sai.
    #
    # Luật: mô hình đọc phải là DỮ LIỆU MỚI — bản chụp phẳng, bất biến — không bao giờ là
    # tham chiếu vào ruột của aggregate.
    assert isinstance(qua_model_doc[0], DongDanhSachDon), "bản chụp phẳng, không phải ruột"

    # ---- 7. MÔ HÌNH ĐỌC ĐƯỢC PHÉP CŨ ----
    anh_chup = db.truy_van_danh_sach()             # màn hình vừa tải xong
    db.tai_don("DH-1").giao()                      # ai đó giao hàng NGAY SAU đó
    assert anh_chup[1].trang_thai == "MOI_TAO", "màn hình vẫn hiện trạng thái CŨ"
    assert db.don_hang["DH-1"].trang_thai == "DA_GIAO", "trong khi sự thật đã đổi"
    # Câu hỏi phải hỏi nghiệp vụ, KHÔNG được tự quyết: "màn hình này cũ 2 giây có sao
    # không?" Với danh sách đơn hàng thì thường là không. Với số dư tài khoản trước khi
    # bấm nút chuyển tiền thì CÓ — và chỗ đó phải đọc từ bên ghi.
    #
    # Quy tắc: đọc để HIỂN THỊ thì dùng mô hình đọc; đọc để RA QUYẾT ĐỊNH GHI thì phải tải
    # aggregate (và có khoá lạc quan — bài 92).

    # ---- 8. "LITE" NGHĨA LÀ GÌ, VÀ RANH GIỚI Ở ĐÂU ----
    #
    #   Mức                | Kho ghi | Kho đọc | Độ trễ | Chi phí
    #   -------------------|---------|---------|--------|------------------
    #   Không tách         | chung   | chung   | 0      | N+1, miền bị bẩn
    #   CQRS-LITE (bài này)| chung   | chung   | 0      | thêm mô hình đọc + truy vấn
    #   CQRS đầy đủ        | chung   | RIÊNG   | có     | đồng bộ, hạ tầng, vận hành
    #
    # Hàng giữa giải quyết được 90% vấn đề với gần như không có chi phí vận hành: vẫn một
    # CSDL, một transaction, dữ liệu luôn tươi — chỉ là ĐƯỜNG ĐỌC không đi qua aggregate.
    # Đừng nhảy sang hàng cuối khi chưa đo được rằng hàng giữa không đủ.

    # ---- 9. LUẬT NGHIỆP VỤ KHÔNG ĐƯỢC NẰM Ở BÊN ĐỌC ----
    # Cám dỗ: câu truy vấn danh sách tính luôn "đơn nào được giảm giá". Đừng — lúc đó luật
    # giảm giá có hai bản: một trong miền, một trong SQL, và chúng sẽ lệch (bài 87 phần 2).
    #
    # Phép thử: nếu xoá toàn bộ mô hình đọc đi, hệ thống có còn ĐÚNG không (chỉ chậm và
    # xấu)? Nếu câu trả lời là "không, mất luôn luật X" thì luật X đang nằm sai chỗ.
    assert db.don_hang["DH-0"].tong_tien() == qua_model_doc[0].tong_tien, \
        "bên đọc TRÌNH BÀY lại con số bên ghi tính ra, không tự tính luật"

    print("OK")
