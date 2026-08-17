# Ngôn ngữ: Python
# Công dụng: Bản Python của cùng bài học — hai người sửa một bản ghi, số hiệu phiên bản
# phát hiện đụng độ, thử lại "mù" vẫn mất dữ liệu, và vòng lặp đọc-lại/áp-lại/ghi.
# Tại sao cần học: Python có một cách phá hỏng toàn bộ cơ chế này mà Java và C++ không
# có — và nó chỉ là một dòng trông vô hại. Nếu hàm `doc()` trả về CHÍNH object đang nằm
# trong "CSDL" (gán ở Python luôn là chia sẻ tham chiếu), thì hai chuyện xảy ra cùng
# lúc: sửa object đọc ra là đã sửa thẳng CSDL mà không có lệnh ghi nào, và số hiệu phiên
# bản của bản đọc LUÔN bằng số hiệu trong CSDL — nên phép kiểm đụng độ không bao giờ
# phát hiện được gì, mãi mãi. Bài đo cả hai hệ quả đó.

from dataclasses import dataclass, replace
from typing import Dict


@dataclass(frozen=True)          # BẤT BIẾN: xem phần 3 để biết vì sao đây là bắt buộc
class BanGhi:
    ten: str
    han_muc: int
    phien_ban: int


@dataclass                       # khả biến — dùng để cho nổ bug ở phần 3
class BanGhiKhaBien:
    ten: str
    han_muc: int
    phien_ban: int


class Csdl:
    """`cap_nhat` mô phỏng đúng `UPDATE ... WHERE ma=%s AND phien_ban=%s`."""

    def __init__(self):
        self._bang: Dict[str, BanGhi] = {}
        self.so_lan_ghi_thanh_cong = 0
        self.so_lan_dung_do = 0

    def tao(self, ma, bg):
        self._bang[ma] = bg

    def doc(self, ma):
        return self._bang[ma]     # an toàn vì BanGhi bất biến — không ai sửa được nó

    def ghi_de(self, ma, moi):
        """Ghi KHÔNG kiểm phiên bản — "ai ghi sau thì thắng"."""
        self._bang[ma] = moi
        self.so_lan_ghi_thanh_cong += 1

    def cap_nhat(self, ma, moi, phien_ban_ky_vong):
        """Trả về số dòng bị ảnh hưởng, y như driver CSDL thật."""
        hien_tai = self._bang.get(ma)
        if hien_tai is None or hien_tai.phien_ban != phien_ban_ky_vong:
            self.so_lan_dung_do += 1
            return 0                       # 0 dòng -> có người đã sửa trước
        self._bang[ma] = replace(moi, phien_ban=phien_ban_ky_vong + 1)
        self.so_lan_ghi_thanh_cong += 1
        return 1


class CsdlRoRi:
    """Y hệt trên, nhưng lưu bản ghi KHẢ BIẾN và trả về chính object đó."""

    def __init__(self):
        self._bang: Dict[str, BanGhiKhaBien] = {}

    def tao(self, ma, bg):
        self._bang[ma] = bg

    def doc(self, ma):
        return self._bang[ma]              # <- một dòng, và toàn bộ cơ chế sụp đổ

    def cap_nhat(self, ma, moi, phien_ban_ky_vong):
        hien_tai = self._bang.get(ma)
        if hien_tai is None or hien_tai.phien_ban != phien_ban_ky_vong:
            return 0
        self._bang[ma] = BanGhiKhaBien(moi.ten, moi.han_muc, phien_ban_ky_vong + 1)
        return 1


# ---- Self-check ----
if __name__ == "__main__":
    # ---- 1. CON BUG: ai ghi sau thì thắng, và người trước mất trắng ----
    db = Csdl()
    db.tao("KH-01", BanGhi("Nguyễn Văn A", 10_000_000, 1))

    # Hai nhân viên mở cùng một hồ sơ khách hàng lúc 9h00.
    cua_an = db.doc("KH-01")          # An đọc: hạn mức 10 triệu
    cua_binh = db.doc("KH-01")        # Bình đọc: hạn mức 10 triệu

    # An sửa TÊN (khách đổi tên đệm), Bình sửa HẠN MỨC (duyệt nâng hạn).
    db.ghi_de("KH-01", replace(cua_an, ten="Nguyễn Văn An"))
    db.ghi_de("KH-01", replace(cua_binh, han_muc=50_000_000))

    assert db.doc("KH-01").han_muc == 50_000_000, "hạn mức mới của Bình: có"
    assert db.doc("KH-01").ten == "Nguyễn Văn A", "tên mới của An: MẤT"
    # An thấy màn hình báo "lưu thành công", đóng máy, về nhà. Không ngoại lệ, không cảnh
    # báo, và không ai biết cho tới khi khách hàng gọi điện hỏi.
    #
    # Đây KHÔNG phải bài 85. Ở đó hai lần tải nằm trong một use case, và bản đồ định danh
    # cứu được. Ở đây là hai người, hai máy, hai transaction.

    # ---- 2. SỐ HIỆU PHIÊN BẢN: phát hiện được, và phát hiện ĐÚNG LÚC ----
    db2 = Csdl()
    db2.tao("KH-01", BanGhi("Nguyễn Văn A", 10_000_000, 1))
    an_doc = db2.doc("KH-01")         # phiên bản 1
    binh_doc = db2.doc("KH-01")       # phiên bản 1

    assert db2.cap_nhat("KH-01", replace(an_doc, ten="Nguyễn Văn An"), an_doc.phien_ban) == 1, \
        "An ghi trước: thành công, phiên bản -> 2"
    assert db2.doc("KH-01").phien_ban == 2, "phiên bản tự tăng cùng lần ghi"
    assert db2.cap_nhat("KH-01", replace(binh_doc, han_muc=50_000_000), binh_doc.phien_ban) == 0, \
        "Bình ghi sau với phiên bản 1 -> 0 DÒNG bị ảnh hưởng"
    assert db2.so_lan_dung_do == 1, "đụng độ được ĐẾM, không im lặng"
    assert db2.doc("KH-01").ten == "Nguyễn Văn An", "và thay đổi của An còn nguyên"
    # `UPDATE ... WHERE ma=%s AND phien_ban=%s` không cần khoá gì cả. CSDL trả về số dòng
    # bị ảnh hưởng, và `0` là câu trả lời "có người đã sửa trước bạn".

    # ---- 3. CÁI BẪY RIÊNG CỦA PYTHON: trả về CHÍNH object trong kho ----
    db_ro = CsdlRoRi()
    db_ro.tao("KH-01", BanGhiKhaBien("Nguyễn Văn A", 10_000_000, 1))

    doc1 = db_ro.doc("KH-01")
    doc2 = db_ro.doc("KH-01")
    assert doc1 is doc2, "hai lần đọc trả về CÙNG MỘT object — không phải hai bản chụp"

    doc1.han_muc = 50_000_000            # chưa gọi `cap_nhat` lần nào
    assert db_ro.doc("KH-01").han_muc == 50_000_000, \
        "'CSDL' đã đổi mà KHÔNG có lệnh ghi nào — transaction thành trang trí"

    # Và hệ quả nặng hơn: phép kiểm phiên bản trở nên vô nghĩa vĩnh viễn.
    doc2.ten = "Người khác sửa"          # mô phỏng người thứ hai sửa
    assert db_ro.cap_nhat("KH-01", doc1, doc1.phien_ban) == 1, \
        "vẫn ghi thành công: phiên bản của 'bản đã đọc' LUÔN khớp, vì nó chính là bản trong kho"
    # Đọc lại hai dòng trên: cơ chế phát hiện đụng độ vẫn ở đó, vẫn chạy, và nó không bao
    # giờ phát hiện được gì. Đây là dạng bug tệ nhất — một biện pháp an toàn đã hỏng mà
    # mọi test của nó vẫn xanh.
    #
    # Cách chặn, theo thứ tự nên chọn:
    #   1. Bản ghi là `@dataclass(frozen=True)` -> không ai sửa được sau khi đọc (bài 82);
    #   2. hoặc `doc()` trả về `copy.deepcopy(...)` -> tốn, nhưng an toàn;
    #   3. đừng chỉ dựa vào gạch dưới `_bang`: nó không chặn ai (bài 83 phần 7).
    khong_sua_duoc = False
    try:
        db2.doc("KH-01").han_muc = 1
    except Exception:                    # FrozenInstanceError kế thừa AttributeError
        khong_sua_duoc = True
    assert khong_sua_duoc, "bản ghi bất biến: đọc ra rồi thì không sửa lén được"

    # ---- 4. CON BUG: THỬ LẠI "MÙ" ----
    # Phản xạ đầu tiên khi gặp `0 dòng`: đọc lại phiên bản rồi ghi lại. SAI.
    phien_ban_moi = db2.doc("KH-01").phien_ban
    db2.cap_nhat("KH-01", replace(binh_doc, han_muc=50_000_000), phien_ban_moi)
    assert db2.doc("KH-01").ten == "Nguyễn Văn A", "tên của An lại MẤT lần nữa"
    # Bình chỉ lấy phiên bản MỚI nhưng vẫn ghi bằng dữ liệu CŨ (`binh_doc`). Kết quả y hệt
    # phần 1 — chỉ chậm hơn vài mili-giây. Số hiệu phiên bản không tự sửa gì; nó chỉ NÓI
    # cho bạn biết phải đọc lại.

    # ---- 5. BẢN ĐÚNG: đọc lại, ÁP DỤNG LẠI thay đổi, rồi ghi ----
    db3 = Csdl()
    db3.tao("KH-01", BanGhi("Nguyễn Văn A", 10_000_000, 1))
    db3.cap_nhat("KH-01", BanGhi("Nguyễn Văn An", 10_000_000, 0), 1)   # An xong

    han_muc_binh_muon_dat = 50_000_000
    so_lan_thu, xong = 0, False
    while not xong and so_lan_thu < 5:
        so_lan_thu += 1
        tuoi = db3.doc("KH-01")                                   # ĐỌC LẠI dữ liệu mới nhất
        sua = replace(tuoi, han_muc=han_muc_binh_muon_dat)        # ÁP LẠI ý định của Bình
        xong = db3.cap_nhat("KH-01", sua, tuoi.phien_ban) == 1
    assert xong and so_lan_thu == 1, "đọc lại rồi ghi: thành công ngay lần đầu"
    assert db3.doc("KH-01").ten == "Nguyễn Văn An", "tên của An: GIỮ"
    assert db3.doc("KH-01").han_muc == 50_000_000, "hạn mức của Bình: GIỮ"
    assert db3.doc("KH-01").phien_ban == 3, "hai lần ghi, phiên bản 1 -> 3"
    # Ba bước, luôn luôn: ĐỌC LẠI -> ÁP LẠI Ý ĐỊNH -> GHI CÓ KIỂM PHIÊN BẢN.
    # `replace(tuoi, ...)` đọc lên đúng như ý nghĩa của nó: lấy bản MỚI, áp ý định lên.

    # ---- 6. KHÔNG PHẢI Ý ĐỊNH NÀO CŨNG ÁP LẠI ĐƯỢC ----
    #   "đặt hạn mức = 50 triệu"    -> áp lại được (tuyệt đối, bài 91 phần 7)
    #   "tăng hạn mức thêm 10%"     -> áp lại được, vì tính trên bản MỚI đọc
    #   "duyệt vì hạn mức < 20tr"   -> KHÔNG: điều kiện duyệt đã dựa trên số cũ
    # Trường hợp thứ ba phải hỏi lại người dùng. Tự động thử lại ở đây là ra một quyết
    # định nghiệp vụ hộ con người.
    db4 = Csdl()
    db4.tao("KH-01", BanGhi("A", 10_000_000, 1))
    duoc_duyet = db4.doc("KH-01").han_muc < 20_000_000        # điều kiện trên bản CŨ
    db4.cap_nhat("KH-01", BanGhi("A", 90_000_000, 0), 1)      # người khác nâng lên 90tr
    assert duoc_duyet and db4.doc("KH-01").han_muc == 90_000_000, \
        "quyết định 'được duyệt' đã lỗi thời — thử lại tự động sẽ duyệt sai"

    # ---- 7. PHIÊN BẢN ĐẶT Ở ĐÂU: ĐÚNG MỘT CÁI, TRÊN AGGREGATE ROOT ----
    #   - MỖI FIELD một phiên bản  -> An sửa tên, Bình sửa hạn mức: không đụng độ. Nghe
    #     hay, nhưng nó phá BẤT BIẾN: hai người sửa hai field có thể cùng nhau tạo ra
    #     trạng thái vi phạm mà không ai vi phạm riêng lẻ. Đây đúng là lý do bài 83 tồn tại.
    #   - PHIÊN BẢN TOÀN CỤC       -> mọi người đụng độ với mọi người.
    #   - MỘT phiên bản trên ROOT  -> đúng: đơn vị nhất quán = đơn vị đụng độ.
    # Nối tiếp bài 83 phần 5: aggregate càng TO thì đụng độ giả càng nhiều.
    assert db3.doc("KH-01").phien_ban == 3, "một số hiệu cho cả cụm, không phải cho từng field"

    # ---- 8. CẠM BẪY KIỂU DỮ LIỆU CỦA PHIÊN BẢN ----
    # Phiên bản đi qua JSON/HTTP thường về dưới dạng CHUỖI, và Python so sánh im lặng:
    assert "2" != 2, "chuỗi '2' không bằng số 2 — không lỗi, chỉ là False"
    # Nếu client gửi lại `"phien_ban": "2"` mà máy chủ so với số `2`, thì MỌI lần ghi đều
    # bị coi là đụng độ — người dùng không lưu được gì và không ai hiểu vì sao. Chiều
    # ngược lại còn tệ hơn: nếu ai đó "sửa" bằng cách so `str(a) == str(b)` thì `2` và
    # `"2"` khớp, nhưng `2` và `"02"` thì không. Ép kiểu NGAY TẠI BIÊN (bài 76, bài 78).
    assert int("2") == 2, "đổi kiểu một lần ở biên, rồi bên trong chỉ còn số nguyên"

    # ---- 9. LẠC QUAN hay BI QUAN ----
    #
    #             | Khoá LẠC QUAN (phiên bản)          | Khoá BI QUAN (SELECT FOR UPDATE)
    #   ----------|-------------------------------------|----------------------------------
    #   giả định  | đụng độ HIẾM                        | đụng độ THƯỜNG
    #   chi phí   | 0 khi không đụng; thử lại khi đụng  | giữ khoá suốt transaction
    #   rủi ro    | thử lại nhiều lần / đói tài nguyên  | deadlock, chờ, nghẽn cổ chai
    #   hợp với   | web, API, người dùng sửa hồ sơ      | trừ kho, cấp số phiếu, hàng đợi
    #
    # Quy tắc thực dụng: mặc định LẠC QUAN. Chỉ chuyển sang bi quan khi ĐO được rằng tỉ lệ
    # đụng độ cao tới mức thử lại tốn hơn chờ — và trước đó hãy xem lại ranh giới aggregate
    # (bài 83), vì đụng độ cao thường là triệu chứng của ranh giới quá to.
    assert db2.so_lan_dung_do + db3.so_lan_dung_do >= 1, "đụng độ là số liệu — hãy đo nó"

    print("OK")
