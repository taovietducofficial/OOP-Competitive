# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — ba con bug của hệ thống không có Unit of
# Work: ghi nửa vời khi lệnh thứ hai hỏng, MẤT thay đổi khi cùng một đơn được tải hai
# lần, và thay đổi bay hơi vì quên gọi `luu()`.
# Tại sao cần học: Python biến Unit of Work thành đúng thứ nó vốn là — một PHẠM VI —
# nhờ giao thức context manager. `with don_vi_cong_viec() as uow:` đọc lên gần như một
# câu tiếng Anh, và `__exit__` biết cả việc khối lệnh thoát ra bình thường hay thoát ra
# bằng ngoại lệ. Nhưng đúng chỗ đó có một cái bẫy chỉ Python mới có: `__exit__` mà trả
# về `True` sẽ NUỐT ngoại lệ. Một ký tự, và "rollback rồi ném tiếp" biến thành "rollback
# rồi giả vờ mọi thứ ổn" — người gọi vẫn gửi email xác nhận cho một đơn hàng chưa bao
# giờ được lưu.

from dataclasses import dataclass, field, replace
from typing import Dict, Optional


# =====================================================================
# MIỀN — aggregate root, và giao diện kho nằm CÙNG chỗ với nó
# =====================================================================
class DonHang:
    def __init__(self, ma, tong_tien):
        self.ma, self.tong_tien = ma, tong_tien

    def them_phi(self, p):
        self.tong_tien += p

    def giam_gia(self, g):
        self.tong_tien -= g


class KhoDonHang:
    """Giao diện này thuộc về MIỀN, không thuộc về hạ tầng (bài 66). Nó nói bằng ngôn
    ngữ nghiệp vụ — "tìm đơn theo mã" — không nói "SELECT", không nói "queryset"."""

    def tim_theo_ma(self, ma) -> Optional[DonHang]:
        raise NotImplementedError

    def luu(self, don: DonHang):
        raise NotImplementedError


# =====================================================================
# HẠ TẦNG — CSDL giả có ĐẾM, để mọi thứ thành con số
# =====================================================================
@dataclass
class CsdlGia:
    bang: Dict[str, int] = field(default_factory=dict)
    so_lan_doc: int = 0
    so_lan_ghi: int = 0

    def doc(self, ma):
        self.so_lan_doc += 1
        # Mỗi lần đọc dựng một object MỚI — đúng như mọi ORM/driver thật làm.
        return DonHang(ma, self.bang[ma]) if ma in self.bang else None

    def ghi(self, d):
        self.so_lan_ghi += 1
        self.bang[d.ma] = d.tong_tien


class KhoThuan(KhoDonHang):
    """Không có Unit of Work. Mỗi `luu()` là một lần ghi thật, ngay lập tức."""

    def __init__(self, csdl):
        self.csdl = csdl

    def tim_theo_ma(self, ma):
        return self.csdl.doc(ma)

    def luu(self, don):
        self.csdl.ghi(don)


# =====================================================================
# UNIT OF WORK — ở Python nó là một CONTEXT MANAGER, không hơn
# =====================================================================
class DonViCongViec:
    def __init__(self, csdl):
        self.csdl = csdl
        self._theo_doi: Dict[str, DonHang] = {}   # BẢN ĐỒ ĐỊNH DANH
        self._da_commit = False

    # Việc 1 — BẢN ĐỒ ĐỊNH DANH: tải hai lần vẫn ra MỘT object.
    def tim(self, ma):
        if ma in self._theo_doi:
            return self._theo_doi[ma]
        d = self.csdl.doc(ma)
        if d is not None:
            self._theo_doi[ma] = d
        return d

    # Việc 2 — THEO DÕI THAY ĐỔI: object lấy từ đây thì không cần gọi `luu()`.
    def dang_ky_moi(self, d):
        self._theo_doi[d.ma] = d

    # Việc 3 — MỘT ĐIỂM QUYẾT ĐỊNH: ghi hết, hoặc không ghi gì.
    def commit(self):
        for d in self._theo_doi.values():
            self.csdl.ghi(d)
        self._da_commit = True

    @property
    def so_object_dang_theo_doi(self):
        return len(self._theo_doi)

    def __enter__(self):
        return self

    def __exit__(self, loai_loi, loi, vet):
        if not self._da_commit:
            self._theo_doi.clear()        # rollback
        # KHÔNG `return True`. Xem phần 5 — đây là dòng quan trọng nhất của lớp này.
        return False


# =====================================================================
# UoW SAI — `__exit__` trả về True
# =====================================================================
class DonViCongViecNuotLoi(DonViCongViec):
    def __exit__(self, loai_loi, loi, vet):
        if not self._da_commit:
            self._theo_doi.clear()
        return True        # <- một dòng, và ngoại lệ biến mất


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: CÙNG MỘT ĐƠN TẢI HAI LẦN -> MẤT THAY ĐỔI ----
    csdl = CsdlGia()
    csdl.bang["DH-01"] = 100_000
    kho_thuan = KhoThuan(csdl)

    # Hai chỗ khác nhau trong cùng một use case cùng cần đơn DH-01 — chuyện rất thường:
    # một hàm tính phí, một hàm tính khuyến mãi, cả hai đều tự đi tải.
    a = kho_thuan.tim_theo_ma("DH-01")
    b = kho_thuan.tim_theo_ma("DH-01")
    assert a is not b, "HAI object khác nhau cho CÙNG một đơn hàng"

    a.them_phi(10_000)      # +10.000 -> 110.000
    b.giam_gia(5_000)       #  -5.000 ->  95.000  (từ bản CŨ, không thấy phí của a)
    kho_thuan.luu(a)
    kho_thuan.luu(b)
    assert csdl.bang["DH-01"] == 95_000, "phí 10.000 của a BIẾN MẤT — b ghi đè"
    # Không ngoại lệ, không cảnh báo. Lệnh ghi cuối cùng thắng, và nó ghi đè bằng một bản
    # đọc từ trước. Đây là "lost update" ở ngay TRONG một tiến trình — chưa cần hai người
    # dùng, chưa cần hai máy chủ (bài 92 lo trường hợp đó).

    csdl.bang["DH-02"] = 100_000
    with DonViCongViec(csdl) as uow:
        a2 = uow.tim("DH-02")
        b2 = uow.tim("DH-02")
        assert a2 is b2, "CÙNG một object — bản đồ định danh làm việc của nó"
        a2.them_phi(10_000)
        b2.giam_gia(5_000)
        uow.commit()
    assert csdl.bang["DH-02"] == 105_000, "cả hai thay đổi đều còn: 100 + 10 - 5"

    # ---- 2. CON BUG: GHI NỬA VỜI ----
    ghi_truoc = csdl.so_lan_ghi
    hong = False
    try:
        kho_thuan.luu(DonHang("DH-10", 1_000))        # ghi THẬT ngay tại đây
        raise RuntimeError("kiểm tra tồn kho thất bại")
    except RuntimeError:
        hong = True
    assert hong and "DH-10" in csdl.bang, "DH-10 đã nằm trong CSDL dù nghiệp vụ chưa hoàn tất"
    assert csdl.so_lan_ghi == ghi_truoc + 1, "một lần ghi lẻ loi, không ai dọn"

    ghi_truoc = csdl.so_lan_ghi
    hong = False
    try:
        with DonViCongViec(csdl) as uow:
            uow.dang_ky_moi(DonHang("DH-11", 1_000))
            uow.dang_ky_moi(DonHang("DH-12", 2_000))
            assert uow.so_object_dang_theo_doi == 2, "hai object đang chờ, chưa cái nào chạm CSDL"
            raise RuntimeError("kiểm tra tồn kho thất bại")
            # `__exit__` chạy trước khi ngoại lệ thoát ra -> rollback, rồi ném tiếp.
    except RuntimeError:
        hong = True
    assert hong, "vẫn hỏng như trên"
    assert "DH-11" not in csdl.bang and "DH-12" not in csdl.bang, "nhưng CSDL sạch"
    assert csdl.so_lan_ghi == ghi_truoc, "đúng 0 lần ghi — không phải 'ghi rồi xoá'"

    # ---- 3. CON BUG: QUÊN GỌI luu() ----
    c = kho_thuan.tim_theo_ma("DH-01")
    c.them_phi(50_000)
    # ...và ở đây thiếu một dòng `kho_thuan.luu(c)`
    assert csdl.bang["DH-01"] == 95_000, "50.000 vừa bay hơi, không dấu vết"
    # Lỗi này không có cách nào phát hiện bằng đọc code, vì thứ thiếu là một dòng KHÔNG
    # tồn tại. Ở Python còn tệ hơn hai ngôn ngữ kia: không có compiler nào để mà bỏ sót.

    with DonViCongViec(csdl) as uow:
        c2 = uow.tim("DH-02")
        c2.them_phi(50_000)
        uow.commit()                    # KHÔNG có dòng `luu(c2)` nào cả
    assert csdl.bang["DH-02"] == 155_000, "105.000 + 50.000 — không quên được nữa"
    # Đây chính là điều `Session` (SQLAlchemy), `EntityManager` (JPA), `DbContext` (EF)
    # làm. Rất nhiều người dùng nó hằng ngày mà tưởng "ORM tự biết"; thật ra đó là Unit
    # of Work, và hiểu nó thì hết ngạc nhiên vì sao đôi khi sửa một field xong không gọi
    # save mà dữ liệu vẫn đổi — hoặc ngược lại.

    # ---- 4. Bản đồ định danh chỉ hoạt động khi kho trả về CHÍNH object ----
    @dataclass(frozen=True)
    class DonHangBatBien:
        ma: str
        tong_tien: int

    ban1 = DonHangBatBien("DH-00", 100_000)
    ban2 = replace(ban1, tong_tien=110_000)
    assert ban1.tong_tien == 100_000 and ban2.tong_tien == 110_000, "hai bản rời nhau"
    # Nếu kho trả về value object bất biến rồi người gọi `replace()`, thì Unit of Work
    # KHÔNG thấy bản mới — nó vẫn đang theo dõi bản cũ. Aggregate root là ENTITY (bài 82):
    # nó phải được sửa TẠI CHỖ, và kho phải trả về CHÍNH object đang được theo dõi.
    # `frozen=True` đúng cho value object bên trong aggregate, sai cho chính aggregate root.

    # ---- 5. CÁI BẪY RIÊNG CỦA PYTHON: `__exit__` trả về True ----
    ghi_truoc = csdl.so_lan_ghi
    da_chay_tiep = False
    try:
        with DonViCongViecNuotLoi(csdl) as uow:
            uow.dang_ky_moi(DonHang("DH-20", 1_000))
            raise RuntimeError("kiểm tra tồn kho thất bại")
        da_chay_tiep = True          # <- dòng này CHẠY, dù vừa có ngoại lệ ở trên
    except RuntimeError:
        pass

    assert da_chay_tiep, "ngoại lệ bị NUỐT — chương trình đi tiếp như không có gì"
    assert "DH-20" not in csdl.bang, "rollback thì vẫn đúng..."
    assert csdl.so_lan_ghi == ghi_truoc, "...không ghi gì cả"
    # ...và đó chính là chỗ chết người. Rollback ĐÚNG, nhưng người gọi không hề biết. Nó
    # đi tiếp và gửi email "đơn hàng của bạn đã được tạo", ghi log "thành công", trả HTTP
    # 200. Đơn hàng thì không tồn tại.
    #
    # Luật: `__exit__` chỉ được trả về giá trị đúng khi bạn CỐ Ý nuốt ngoại lệ, và với
    # Unit of Work thì không bao giờ. Cách an toàn nhất là đừng viết `return` gì cả —
    # Python trả `None`, và `None` là falsy.
    #
    # Cạm bẫy đi kèm: một `except Exception: pass` bọc quanh cả khối `with` gây hậu quả
    # y hệt, và nó phổ biến hơn nhiều.

    # ---- 6. Repository trả AGGREGATE ROOT, không trả gì khác ----
    # Bài 83: aggregate là đơn vị nhất quán. Nên kho cũng phải là kho của ROOT.
    #   ĐÚNG : KhoDonHang.tim_theo_ma("DH-01")   -> DonHang (có luôn các dòng bên trong)
    #   SAI  : KhoDongHang.tim_theo_don("DH-01") -> list[DongHang]
    # Cái sai cho phép sửa dòng hàng mà không đi qua đơn hàng, nghĩa là bất biến
    # "tổng ≤ hạn mức" mất tác dụng — đúng con bug ở bài 83 phần 2.
    #
    # Quy tắc đếm được: SỐ REPOSITORY = SỐ AGGREGATE ROOT.
    assert len(csdl.bang) >= 3, "một kho cho DonHang, không có kho riêng cho DongHang"

    # ---- 7. Giao diện kho thuộc về MIỀN, cài đặt thuộc về HẠ TẦNG ----
    class KhoAoTest(KhoDonHang):          # fake trong bộ nhớ, 5 dòng (bài 68)
        def __init__(self):
            self.m = {}

        def tim_theo_ma(self, ma):
            return self.m.get(ma)

        def luu(self, don):
            self.m[don.ma] = don

    kho_ao = KhoAoTest()
    kho_ao.luu(DonHang("DH-99", 1))
    assert kho_ao.tim_theo_ma("DH-99") is not None, "fake 5 dòng thay được cả CSDL"
    assert len([t for t in vars(KhoDonHang) if not t.startswith("_")]) == 2, \
        "kho nhỏ: đúng những gì nghiệp vụ cần, không hơn"
    # Cạm bẫy `Repository[T, ID]` tổng quát — `find_all`, `delete_all`, `count`,
    # `filter(**kwargs)` dùng chung cho mọi aggregate. Ba vấn đề, nặng dần:
    #   1. Vi phạm ISP (bài 52): kho đơn hàng không có nghĩa gì với `delete_all()`.
    #   2. Nó nói bằng ngôn ngữ CSDL, không nói bằng ngôn ngữ nghiệp vụ (bài 81):
    #      `tim_don_qua_han(ngay)` mang nghĩa; `filter(status=3, date__lt=x)` thì không.
    #   3. `find_all()` trên bảng 10 triệu dòng là khẩu súng đã lên đạn, và nó nằm sẵn
    #      trong mọi kho chỉ vì "cho tổng quát".
    # Kho tốt thường có 3–6 phương thức, tất cả đọc lên thành câu nghiệp vụ.

    # ---- 8. Ranh giới: Unit of Work KHÔNG phải transaction của CSDL ----
    # Hai thứ hay bị nhầm là một. Unit of Work là khái niệm ở TẦNG ỨNG DỤNG: gom thay
    # đổi, một điểm quyết định. Transaction là cơ chế của CSDL. Chúng thường trùng ranh
    # giới, nhưng không phải lúc nào cũng:
    #   - UoW trên kho trong bộ nhớ thì không có transaction nào cả;
    #   - một saga (bài 97) có nhiều UoW, mỗi cái một transaction riêng.
    # Nhầm hai thứ dẫn tới thói quen tai hại: mở transaction ở tầng view/controller và
    # giữ nó suốt request, kể cả trong lúc gọi API bên ngoài — khoá CSDL bị giữ vài giây
    # chờ mạng.
    assert csdl.so_lan_doc > 0 and csdl.so_lan_ghi > 0, "đếm được cả đọc lẫn ghi"

    print("OK")
