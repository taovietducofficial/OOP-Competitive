# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — miền định nghĩa CỔNG, hạ tầng cung cấp BỘ NỐI,
# mọi phụ thuộc chỉ đi vào TRONG. Ba con bug: miền gọi thẳng hạ tầng nên không test được;
# cổng nói tiếng bộ nối; và "cấu trúc thư mục lục giác" mà chiều phụ thuộc vẫn ngược.
# Tại sao cần học: Java và C++ đảo ngược phụ thuộc bằng KẾ THỪA — bộ nối phải `implements`
# / kế thừa cổng, tức là hạ tầng vẫn phải import miền. Python có một thứ mạnh hơn hẳn và
# ít người dùng đúng: `typing.Protocol`. Với nó, bộ nối KHÔNG cần biết cổng tồn tại — một
# lớp viết ra từ trước, chưa bao giờ nghe tên miền của bạn, vẫn thoả cổng nếu nó có đúng
# phương thức. Phụ thuộc bằng 0 ở CẢ HAI CHIỀU, và bài này chứng minh điều đó bằng một
# lớp thư viện bên thứ ba không hề liên quan.

from typing import Optional, Protocol, runtime_checkable


# =====================================================================
# MIỀN — không biết CSDL, mạng, đồng hồ hay khung phần mềm nào tồn tại
# =====================================================================
class DonHang:
    def __init__(self, ma, ma_khach, tong_tien, tao):
        if tong_tien <= 0:
            raise ValueError("tổng tiền phải dương")
        self.ma, self.ma_khach, self.tong_tien, self.tao = ma, ma_khach, tong_tien, tao


# ---- CỔNG BỊ ĐIỀU KHIỂN (miền GỌI RA ngoài) ----
# Chú ý từ ngữ: "tìm theo mã", "lưu", "báo cho khách". Không có `Cursor`, không có
# `Response`. Cổng nói tiếng NGHIỆP VỤ (bài 81).
@runtime_checkable
class KhoDonHang(Protocol):
    def tim_theo_ma(self, ma: str) -> Optional[DonHang]: ...
    def luu(self, d: DonHang) -> None: ...


@runtime_checkable
class BaoChoKhach(Protocol):
    def bao(self, ma_khach: str, noi_dung: str) -> None: ...


@runtime_checkable
class DongHo(Protocol):
    def bay_gio(self) -> int: ...            # bài 67


# ---- CỔNG ĐIỀU KHIỂN (thế giới GỌI VÀO miền) ----
@runtime_checkable
class DatHang(Protocol):
    def thuc_hien(self, ma_khach: str, tong_tien: int) -> DonHang: ...


class DichVuDatHang:
    """Lõi ứng dụng: cài cổng điều khiển, dùng cổng bị điều khiển. 0 phụ thuộc hạ tầng."""

    def __init__(self, kho: KhoDonHang, bao: BaoChoKhach, dong_ho: DongHo):
        self._kho, self._bao, self._dong_ho = kho, bao, dong_ho
        self._dem = 0

    def thuc_hien(self, ma_khach, tong_tien):
        self._dem += 1
        d = DonHang(f"DH-{self._dem}", ma_khach, tong_tien, self._dong_ho.bay_gio())
        self._kho.luu(d)
        self._bao.bao(ma_khach, f"đã tạo đơn {d.ma}")
        return d


# =====================================================================
# BỘ NỐI — hạ tầng. Chú ý: KHÔNG lớp nào kế thừa gì cả.
# =====================================================================
class KhoTrongBoNho:
    def __init__(self):
        self.bang = {}

    def tim_theo_ma(self, ma):
        return self.bang.get(ma)

    def luu(self, d):
        self.bang[d.ma] = d


class KhoSql:
    """Bộ nối thứ hai: giả lập SQL. Cùng cổng, cài đặt hoàn toàn khác."""

    def __init__(self):
        self.cau_lenh, self.bang = [], {}

    def tim_theo_ma(self, ma):
        self.cau_lenh.append(f"SELECT * FROM don_hang WHERE ma = '{ma}'")
        return self.bang.get(ma)

    def luu(self, d):
        self.cau_lenh.append(f"INSERT INTO don_hang VALUES ('{d.ma}', ...)")
        self.bang[d.ma] = d


class BaoGia:
    def __init__(self):
        self.da_bao = []

    def bao(self, ma_khach, noi_dung):
        self.da_bao.append(f"{ma_khach}:{noi_dung}")


class DongHoCoDinh:
    def __init__(self, luc):
        self.luc = luc

    def bay_gio(self):
        return self.luc


# =====================================================================
# BẢN SAI — miền gọi thẳng hạ tầng
# =====================================================================
class KetNoiCsdl:
    so_lan_mo_ket_noi = 0
    co_san = False

    def __init__(self):
        KetNoiCsdl.so_lan_mo_ket_noi += 1
        if not KetNoiCsdl.co_san:
            raise RuntimeError("không kết nối được CSDL")


class DichVuDatHangSai:
    def thuc_hien(self, ma_khach, tong_tien):
        KetNoiCsdl()                          # <- khởi tạo thẳng hạ tầng
        import time
        return DonHang("DH-X", ma_khach, tong_tien, int(time.time()))


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: miền gọi thẳng hạ tầng -> KHÔNG TEST ĐƯỢC ----
    khong_test_duoc = False
    try:
        DichVuDatHangSai().thuc_hien("KH-01", 100_000)
    except RuntimeError:
        khong_test_duoc = True
    assert khong_test_duoc, "muốn test một luật nghiệp vụ, phải dựng cả CSDL trước"
    assert KetNoiCsdl.so_lan_mo_ket_noi == 1, "và mỗi lần chạy test là một lần mở kết nối"
    # Hệ quả dây chuyền: test chậm -> test giòn (hỏng vì CSDL, không phải vì bug) -> không
    # ai chạy test nữa -> không ai viết test nữa. Bắt đầu từ đúng một dòng khởi tạo hạ tầng
    # nằm sai chỗ. Và `time.time()` ở dòng dưới cũng là hạ tầng — nó làm kết quả không tất
    # định (bài 67).

    # ---- 2. LÕI ỨNG DỤNG CHẠY VỚI 0 HẠ TẦNG ----
    kho, bao = KhoTrongBoNho(), BaoGia()
    dv = DichVuDatHang(kho, bao, DongHoCoDinh(1_700_000_000))

    d = dv.thuc_hien("KH-01", 250_000)
    assert d.ma == "DH-1" and d.tao == 1_700_000_000, "kết quả TẤT ĐỊNH"
    assert len(kho.bang) == 1, "đơn đã được lưu"
    assert bao.da_bao == ["KH-01:đã tạo đơn DH-1"], "và khách đã được báo"
    assert KetNoiCsdl.so_lan_mo_ket_noi == 1, "0 kết nối CSDL nào được mở thêm"
    # Ba dòng dựng bối cảnh, không mock, không khung phần mềm, không tệp cấu hình.

    # ---- 3. ĐỔI BỘ NỐI: sửa ĐÚNG MỘT DÒNG, ở gốc lắp ráp ----
    kho_sql = KhoSql()
    d2 = DichVuDatHang(kho_sql, bao, DongHoCoDinh(1_700_000_000)).thuc_hien("KH-02", 300_000)
    assert d2.ma == "DH-1", "cùng logic nghiệp vụ, không sửa một chữ nào trong miền"
    assert len(kho_sql.cau_lenh) == 1 and kho_sql.cau_lenh[0].startswith("INSERT"), \
        "nhưng lần này nó sinh SQL"
    # "Đổi CSDL sau này" hiếm khi xảy ra thật, và đó KHÔNG phải lý do chính. Lý do chính
    # là: bộ nối thứ hai — bản trong bộ nhớ — cho phép TEST, và nó được dùng hằng ngày.

    # ---- 4. ĐIỀU CHỈ PYTHON LÀM ĐƯỢC: PHỤ THUỘC BẰNG 0 Ở CẢ HAI CHIỀU ----
    assert KhoDonHang not in KhoTrongBoNho.__mro__, "bộ nối KHÔNG kế thừa cổng"
    assert isinstance(kho, KhoDonHang), "nhưng nó VẪN thoả cổng — vì có đúng phương thức"
    assert isinstance(kho_sql, KhoDonHang), "cả hai bộ nối, không cái nào import cổng"
    # Ở Java/C++, bộ nối phải `implements KhoDonHang` — nghĩa là hạ tầng vẫn phải import
    # miền. Ở Python với `Protocol`, chiều phụ thuộc bằng 0 ở CẢ HAI phía.
    #
    # Hệ quả thực tế: một lớp có sẵn từ thư viện bên thứ ba — viết ra trước khi dự án của
    # bạn tồn tại — vẫn dùng làm bộ nối được, không cần lớp bọc:
    class LopThuVienBenThuBa:
        """Giả lập một lớp cache có sẵn. Nó chưa bao giờ nghe tên `KhoDonHang`."""
        def __init__(self):
            self._d = {}

        def tim_theo_ma(self, ma):
            return self._d.get(ma)

        def luu(self, d):
            self._d[d.ma] = d

    ngoai = LopThuVienBenThuBa()
    assert isinstance(ngoai, KhoDonHang), "lớp bên thứ ba thoả cổng mà không biết cổng tồn tại"
    d3 = DichVuDatHang(ngoai, bao, DongHoCoDinh(7)).thuc_hien("KH-03", 1_000)
    assert d3.tao == 7 and ngoai.tim_theo_ma("DH-1") is not None, "và nó chạy được ngay"
    # Cạm bẫy đi kèm, phải biết: `runtime_checkable` chỉ kiểm TÊN phương thức, KHÔNG kiểm
    # chữ ký. Một lớp có `luu(self)` không tham số vẫn qua được `isinstance`:
    class BoNoiSaiChuKy:
        def tim_theo_ma(self): ...            # thiếu tham số `ma`
        def luu(self): ...                    # thiếu tham số `d`

    assert isinstance(BoNoiSaiChuKy(), KhoDonHang), "isinstance vẫn nói ĐẠT — chỉ kiểm tên"
    no_luc_chay = False
    try:
        DichVuDatHang(BoNoiSaiChuKy(), bao, DongHoCoDinh(1)).thuc_hien("KH-X", 1)
    except TypeError:
        no_luc_chay = True
    assert no_luc_chay, "và nó chỉ nổ lúc CHẠY — nên `mypy` là bắt buộc, không phải tuỳ chọn"

    # ---- 5. CỔNG PHẢI NÓI TIẾNG NGHIỆP VỤ, KHÔNG NÓI TIẾNG BỘ NỐI ----
    # Cổng RÒ RỈ (rất hay gặp):
    #     class KhoDonHang(Protocol):
    #         def execute(self, sql: str) -> Cursor: ...
    # Nó "là Protocol" nên trông như đã đảo ngược phụ thuộc — nhưng chưa. Ba hậu quả:
    #   1. Không viết nổi bản trong bộ nhớ (lấy đâu ra `Cursor`?) -> mất cái lợi ở phần 3;
    #   2. Miền vẫn phải import driver CSDL -> chiều phụ thuộc vẫn ngược;
    #   3. Đổi sang kho khoá-giá trị là phải sửa cổng, tức là sửa miền.
    # Phép thử: đọc tên phương thức của cổng lên. Người làm nghiệp vụ hiểu được thì cổng
    # đúng; chỉ lập trình viên hiểu thì đó là bộ nối đội lốt cổng (bài 81).
    ten_pt = [t for t in vars(KhoDonHang) if not t.startswith("_")]
    assert sorted(ten_pt) == ["luu", "tim_theo_ma"], "cổng NHỎ: 2 phương thức, tiếng nghiệp vụ (bài 52)"

    # ---- 6. BÀI TEST KIẾN TRÚC: CHIỀU PHỤ THUỘC ----
    # Lục giác KHÔNG phải một cấu trúc thư mục. Đổi tên gói thành `domain/`,
    # `infrastructure/` mà `import` vẫn đi từ trong ra ngoài thì chẳng có gì thay đổi.
    # Luật thật chỉ có một: LÕI KHÔNG ĐƯỢC THAM CHIẾU HẠ TẦNG. Và nó kiểm được:
    HA_TANG = {KhoTrongBoNho, KhoSql, BaoGia, DongHoCoDinh, KetNoiCsdl}
    LOI = [DonHang, DichVuDatHang]
    vi_pham = [f"{lop.__name__}.{ten}"
               for lop in LOI for ten, gt in vars(lop).items()
               if gt in HA_TANG]
    assert vi_pham == [], f"0 tham chiếu từ lõi ra hạ tầng: {vi_pham}"
    # Trong dự án thật, bài kiểm tra mạnh hơn là quét `import` của gói `mien/`:
    #     grep -rE '^(from|import) (sqlalchemy|requests|redis|django)' mien/
    # Kết quả rỗng = đạt. Nó bắt đúng thời điểm ai đó "cho tiện" import một driver vào một
    # module miền — thời điểm kiến trúc bắt đầu tan rã, và mọi test nghiệp vụ vẫn xanh.

    # ---- 7. HAI LOẠI CỔNG, VÀ VÌ SAO PHẢI PHÂN BIỆT ----
    #
    #   Loại              | Ai gọi ai              | Ví dụ ở đây     | Bộ nối
    #   ------------------|------------------------|-----------------|------------------
    #   Cổng ĐIỀU KHIỂN   | thế giới -> miền       | `DatHang`       | REST, CLI, hàng đợi
    #   Cổng BỊ ĐIỀU KHIỂN| miền -> thế giới       | `KhoDonHang`    | CSDL, SMTP, đồng hồ
    #
    # Cả hai đều do MIỀN định nghĩa — điểm mấu chốt và cũng là chỗ hay sai. Với cổng bị
    # điều khiển thì ai cũng hiểu; với cổng điều khiển thì người ta hay để khung web định
    # nghĩa (view gọi thẳng vào lớp dịch vụ). Hậu quả: chữ ký use case bị định hình bởi
    # HTTP, và một job nền muốn dùng lại thì phải giả lập request.
    assert isinstance(dv, DatHang), "lõi thoả cổng điều khiển"
    assert len([t for t in vars(DatHang) if not t.startswith("_")]) == 1, \
        "cổng điều khiển = MỘT use case"

    # ---- 8. GỐC LẮP RÁP, VÀ KHI NÀO KHÔNG CẦN LỤC GIÁC ----
    # Có đúng một chỗ được phép khởi tạo cả miền lẫn hạ tầng — `main`, hoặc module cấu
    # hình. Mọi chỗ khác chỉ nhận phụ thuộc qua constructor (bài 51).
    #
    #   main() -> DichVuDatHang(KhoSql(), GuiEmailThat(), DongHoHeThong())
    #   test() -> DichVuDatHang(KhoTrongBoNho(), BaoGia(), DongHoCoDinh(42))
    #
    # Hai dòng đó là toàn bộ khác biệt giữa chạy thật và chạy test. Nếu để đổi sang test
    # bạn phải sửa file cấu hình hay đặt biến môi trường, thì gốc lắp ráp chưa tồn tại.
    #
    # Và lục giác có chi phí thật. Ba dấu hiệu ĐỦ để cần:
    #   - có luật nghiệp vụ đáng test riêng (không chỉ đọc/ghi bảng);
    #   - có nhiều hơn một đường vào (REST + hàng đợi + job nền);
    #   - có hệ ngoài mà bạn không kiểm soát (bài 94).
    # Thiếu cả ba thì một view gọi thẳng ORM là thiết kế đúng — và biết lúc nào KHÔNG áp
    # dụng một mẫu cũng là một phần của việc hiểu nó.
    assert DichVuDatHang(KhoTrongBoNho(), BaoGia(), DongHoCoDinh(42)).thuc_hien("KH-9", 1).tao == 42, \
        "lắp ráp cho test: một dòng"

    print("OK")
