/*
 * Ngôn ngữ: Java
 * Công dụng: Phân biệt ENTITY (có định danh, sống qua thời gian) với VALUE OBJECT
 * (chỉ là một giá trị, thay thế cho nhau được). Bài dựng hai phép thử chạy được để
 * quyết định một khái niệm thuộc loại nào, rồi cho nổ hai con bug thật: hai khách
 * hàng chưa lưu bị gộp làm một vì id do CSDL cấp, và hai đơn hàng dùng chung một
 * địa chỉ có setter — sửa đơn này thì đơn kia đổi theo.
 * Tại sao cần học: bài 53 dạy "so theo giá trị hay theo định danh". Ở mức miền,
 * câu hỏi khó hơn nhiều: CÙNG MỘT khái niệm có thể là value object trong ngữ cảnh
 * này và entity trong ngữ cảnh khác — "địa chỉ" trong đơn hàng khác hẳn "điểm giao"
 * trong hệ thống vận chuyển. Chọn sai loại không gây lỗi biên dịch; nó gây mất dữ
 * liệu âm thầm. Và điểm chí mạng ít người để ý: entity phải có định danh NGAY LÚC
 * TẠO, không phải lúc lưu xuống CSDL.
 */
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class EntityValueDemo {

    // =====================================================================
    // VALUE OBJECT — không có định danh, chỉ có giá trị
    // =====================================================================
    // `record` cho sẵn equals/hashCode theo TẤT CẢ thành phần, và mọi field là final.
    // Đúng bốn thứ một value object cần, không phải viết dòng nào.
    record Tien(long soTien, String tienTe) {
        Tien {   // constructor gọn — validate ngay tại biên, không có đường vòng
            if (soTien < 0) throw new IllegalArgumentException("số tiền không được âm");
            if (tienTe == null || tienTe.length() != 3)
                throw new IllegalArgumentException("mã tiền tệ phải đúng 3 ký tự");
        }

        // Value object KHÔNG phải túi dữ liệu. Nó mang luật của chính nó.
        Tien cong(Tien khac) {
            if (!tienTe.equals(khac.tienTe))
                throw new IllegalArgumentException("không cộng được hai loại tiền tệ");
            return new Tien(soTien + khac.soTien, tienTe);   // TRẢ VỀ CÁI MỚI
        }
    }

    record DiaChi(String duong, String phuong, String tinh) {
        DiaChi {
            if (duong == null || duong.isBlank())
                throw new IllegalArgumentException("đường không được rỗng");
        }
        // "Đổi" một value object = tạo cái mới. Không có setter, không thể có.
        DiaChi voiDuong(String duongMoi) { return new DiaChi(duongMoi, phuong, tinh); }
    }

    // =====================================================================
    // ENTITY — có định danh, và định danh đó sống lâu hơn mọi thuộc tính
    // =====================================================================
    static final class DiemGiao {
        private final String ma;    // ĐỊNH DANH: bất biến trọn đời, gán lúc tạo
        private DiaChi diaChi;      // thuộc tính: đổi thoải mái
        private String nguoiPhuTrach;

        DiemGiao(String ma, DiaChi diaChi, String nguoiPhuTrach) {
            if (ma == null || ma.isBlank())
                throw new IllegalArgumentException("điểm giao phải có mã ngay lúc tạo");
            this.ma = ma;
            this.diaChi = Objects.requireNonNull(diaChi);
            this.nguoiPhuTrach = nguoiPhuTrach;
        }

        void doiDiaChi(DiaChi moi) { this.diaChi = Objects.requireNonNull(moi); }
        void doiNguoiPhuTrach(String moi) { this.nguoiPhuTrach = moi; }
        DiaChi diaChi() { return diaChi; }   // an toàn: DiaChi bất biến, trả thẳng được

        // Entity so sánh CHỈ theo định danh. Không có field nào khác được xuất hiện
        // ở đây — nếu có, thì sửa một thuộc tính là mất phần tử trong HashSet (bài 75).
        @Override public boolean equals(Object o) {
            return o instanceof DiemGiao dg && ma.equals(dg.ma);
        }
        @Override public int hashCode() { return ma.hashCode(); }
        @Override public String toString() { return "DiemGiao[" + ma + "]"; }
    }

    // =====================================================================
    // BẢN SAI 1 — entity lấy định danh từ CSDL
    // =====================================================================
    static final class KhachHangSai {
        long id = 0;              // 0 = "chưa lưu". Đây là quy ước phổ biến nhất, và sai nhất.
        final String ten;
        KhachHangSai(String ten) { this.ten = ten; }
        void luuVaoDb(long idTuDb) { this.id = idTuDb; }

        @Override public boolean equals(Object o) {
            return o instanceof KhachHangSai k && id == k.id;
        }
        @Override public int hashCode() { return Long.hashCode(id); }
    }

    // BẢN ĐÚNG — định danh sinh trong miền, có ngay từ dòng `new`
    static final class KhachHang {
        private final String ma;
        private final String ten;
        KhachHang(String ten) {
            this.ma = "KH-" + UUID.randomUUID();   // có định danh TRƯỚC khi chạm CSDL
            this.ten = ten;
        }
        String ma() { return ma; }
        String ten() { return ten; }
        @Override public boolean equals(Object o) {
            return o instanceof KhachHang k && ma.equals(k.ma);
        }
        @Override public int hashCode() { return ma.hashCode(); }
    }

    // =====================================================================
    // BẢN SAI 2 — "value object" nhưng có setter
    // =====================================================================
    static final class DiaChiSai {
        String duong;
        DiaChiSai(String duong) { this.duong = duong; }
        void setDuong(String d) { this.duong = d; }   // <- một dòng này phá tất cả
        @Override public boolean equals(Object o) {
            return o instanceof DiaChiSai d && duong.equals(d.duong);
        }
        @Override public int hashCode() { return duong.hashCode(); }
    }

    static final class DonHangSai {
        DiaChiSai diaChiGiao;
        DonHangSai(DiaChiSai d) { this.diaChiGiao = d; }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. HAI PHÉP THỬ để quyết định entity hay value object ----
        //
        // Phép thử A — "đổi HẾT thuộc tính, còn là cùng một thứ không?"
        DiemGiao kho = new DiemGiao("DG-01", new DiaChi("12 Lê Lợi", "Bến Nghé", "TP.HCM"), "anh Nam");
        DiemGiao thamChieuCu = kho;
        kho.doiDiaChi(new DiaChi("45 Nguyễn Huệ", "Bến Nghé", "TP.HCM"));
        kho.doiNguoiPhuTrach("chị Lan");
        check(kho.equals(thamChieuCu), "đổi hết thuộc tính, vẫn là cùng điểm giao -> ENTITY");
        // Đường đổi tên, người phụ trách nghỉ việc — cái kho vẫn là cái kho đó. Nghiệp
        // vụ quan tâm tới VẬT, không quan tâm tới mô tả của vật.

        // Phép thử B — "hai cái giống hệt nhau, thay cho nhau được không?"
        Tien a = new Tien(50_000, "VND");
        Tien b = new Tien(50_000, "VND");
        check(a.equals(b), "hai tờ 50.000đ thay cho nhau được -> VALUE OBJECT");
        check(a != b, "vẫn là hai object khác nhau trong bộ nhớ — và điều đó KHÔNG quan trọng");
        // Không ai hỏi "đây có phải ĐÚNG tờ 50.000 hôm qua không". Với entity thì có.

        // ---- 2. CÙNG MỘT KHÁI NIỆM, HAI VAI TRÒ — tuỳ NGỮ CẢNH ----
        // "Địa chỉ" trong ĐƠN HÀNG là value object: hai địa chỉ giống hệt thì giao tới
        // đâu cũng thế, đổi địa chỉ = thay nguyên cái mới.
        DiaChi dcDon = new DiaChi("12 Lê Lợi", "Bến Nghé", "TP.HCM");
        DiaChi dcKhac = new DiaChi("12 Lê Lợi", "Bến Nghé", "TP.HCM");
        check(dcDon.equals(dcKhac), "trong đơn hàng: hai địa chỉ giống nhau LÀ MỘT");

        // "Điểm giao" trong hệ VẬN CHUYỂN là entity: hai kho khác nhau vẫn có thể ở
        // cùng một địa chỉ (chung toà nhà), và chúng KHÔNG phải một.
        DiemGiao khoA = new DiemGiao("DG-01", dcDon, "anh Nam");
        DiemGiao khoB = new DiemGiao("DG-02", dcKhac, "chị Lan");
        check(!khoA.equals(khoB), "trong vận chuyển: cùng địa chỉ vẫn là HAI điểm giao");
        check(khoA.diaChi().equals(khoB.diaChi()), "dù thuộc tính địa chỉ của chúng bằng nhau");
        // Đây là điểm nâng cao so với bài 53: câu hỏi "cái này là entity hay value
        // object" KHÔNG có câu trả lời chung. Nó phụ thuộc vào việc nghiệp vụ có cần
        // theo dõi CÁI CỤ THỂ NÀY qua thời gian hay không.

        // ---- 3. CON BUG 1: định danh do CSDL cấp -> hai khách gộp làm một ----
        KhachHangSai s1 = new KhachHangSai("Nguyễn Văn A");
        KhachHangSai s2 = new KhachHangSai("Trần Thị B");
        Set<KhachHangSai> gioSai = new HashSet<>(List.of(s1, s2));
        check(gioSai.size() == 1, "HAI khách hàng khác nhau, HashSet chỉ giữ MỘT");
        check(s1.equals(s2), "vì cả hai đều có id = 0 — 'chưa lưu' bị hiểu là 'cùng một người'");
        // Mất một khách hàng. Không ngoại lệ, không log. Bug này chỉ xuất hiện khi xử lý
        // theo lô (nhập file CSV, tạo hàng loạt) — nghĩa là nó qua được mọi test thủ công.

        s1.luuVaoDb(101);
        s2.luuVaoDb(102);
        check(!s1.equals(s2), "lưu xong thì hết bằng nhau — nhưng dữ liệu đã mất từ trước rồi");
        // Và tệ hơn: định danh của object vừa ĐỔI giữa chừng. Nếu s1 đang nằm trong một
        // HashSet nào đó, nó vừa trở nên không tìm lại được (bài 75).

        // BẢN ĐÚNG: định danh có ngay từ `new`, không cần biết CSDL tồn tại
        KhachHang d1 = new KhachHang("Nguyễn Văn A");
        KhachHang d2 = new KhachHang("Trần Thị B");
        check(new HashSet<>(List.of(d1, d2)).size() == 2, "hai khách, giữ đúng hai");
        check(!d1.ma().equals(d2.ma()), "và mỗi người có định danh riêng trước khi chạm CSDL");
        // Hệ quả thực tế: test không cần CSDL, và có thể gửi entity qua hàng đợi trước
        // khi lưu — thứ mà kiểu id-tự-tăng không cho phép.

        // ---- 4. CON BUG 2: value object có setter, bị chia sẻ giữa hai chủ ----
        DiaChiSai chung = new DiaChiSai("12 Lê Lợi");
        DonHangSai don1 = new DonHangSai(chung);
        DonHangSai don2 = new DonHangSai(chung);   // vô tình dùng chung một object

        don1.diaChiGiao.setDuong("45 Nguyễn Huệ");   // chỉ định sửa đơn 1
        check(don2.diaChiGiao.duong.equals("45 Nguyễn Huệ"),
                "đơn 2 bị đổi địa chỉ theo — dù không ai đụng vào nó");
        // Hàng của đơn 2 vừa được giao sai địa chỉ. Đây là bug ALIASING, và nó chỉ tồn
        // tại được vì value object có setter.

        // Với value object bất biến, cùng tình huống đó KHÔNG xảy ra được:
        DiaChi chungDung = new DiaChi("12 Lê Lợi", "Bến Nghé", "TP.HCM");
        DiaChi cuaDon1 = chungDung;
        DiaChi cuaDon2 = chungDung;
        cuaDon1 = cuaDon1.voiDuong("45 Nguyễn Huệ");   // tạo CÁI MỚI, gán lại
        check(cuaDon1.duong().equals("45 Nguyễn Huệ"), "đơn 1 đổi");
        check(cuaDon2.duong().equals("12 Lê Lợi"), "đơn 2 không hề hấn gì");
        // Vì bất biến, chia sẻ object là AN TOÀN — thậm chí còn tiết kiệm bộ nhớ.

        // ---- 5. Vì sao equals của entity chỉ được chứa ĐỊNH DANH ----
        Set<DiemGiao> tuyen = new HashSet<>();
        tuyen.add(kho);
        kho.doiDiaChi(new DiaChi("99 Hai Bà Trưng", "Đa Kao", "TP.HCM"));
        check(tuyen.contains(kho), "sửa thuộc tính xong vẫn tìm lại được trong Set");
        check(tuyen.size() == 1, "và không sinh bản sao");
        // Nếu equals/hashCode của DiemGiao có thêm `diaChi`, dòng `contains` trên đã
        // trả về false và phần tử thành rác không xoá được (bài 75).

        // Chiều ngược lại: value object BẮT BUỘC so theo toàn bộ giá trị.
        Set<Tien> vi = new HashSet<>(List.of(new Tien(50_000, "VND"), new Tien(50_000, "VND")));
        check(vi.size() == 1, "hai giá trị bằng nhau là MỘT — đó chính là điều ta muốn");

        // ---- 6. Value object mang LUẬT, không chỉ mang dữ liệu ----
        check(a.cong(b).equals(new Tien(100_000, "VND")), "cộng cùng tệ thì được");
        boolean chan = false;
        try { a.cong(new Tien(10, "USD")); } catch (IllegalArgumentException e) { chan = true; }
        check(chan, "cộng khác tệ bị chặn — luật nằm TRONG kiểu dữ liệu, không rải khắp nơi");
        // Đây là lợi ích lớn nhất của value object mà bài 53 chưa nói tới: nó là chỗ
        // duy nhất để đặt luật, nên luật không thể bị quên ở một nhánh code nào đó.
        // Bài 90 đi sâu vào riêng Money.

        // ---- 7. Cạm bẫy của `record`: BẤT BIẾN NÔNG ----
        record GioHang(String ma, List<String> matHang) { }
        List<String> ds = new ArrayList<>(List.of("bút"));
        GioHang gh = new GioHang("GH-1", ds);
        ds.add("vở");                                  // sửa từ BÊN NGOÀI record
        check(gh.matHang().size() == 2, "record KHÔNG làm cho List bên trong bất biến");
        // `record` chỉ khoá cái THAM CHIẾU, không khoá thứ nó trỏ tới (bài 73). Value
        // object chứa collection phải tự sao chép phòng vệ trong constructor gọn:
        //     GioHang { matHang = List.copyOf(matHang); }
        record GioHangDung(String ma, List<String> matHang) {
            GioHangDung { matHang = List.copyOf(matHang); }
        }
        List<String> ds2 = new ArrayList<>(List.of("bút"));
        GioHangDung ghd = new GioHangDung("GH-2", ds2);
        ds2.add("vở");
        check(ghd.matHang().size() == 1, "sao chép phòng vệ chặn được rò rỉ khả biến");

        // ---- 8. Bảng quyết định ----
        //
        //   Câu hỏi                                         | Trả lời CÓ -> loại nào
        //   ------------------------------------------------|-----------------------
        //   Đổi hết thuộc tính, còn là cùng một thứ?         | ENTITY
        //   Hai cái giống hệt thì thay cho nhau được?        | VALUE OBJECT
        //   Nghiệp vụ cần lịch sử của CÁI NÀY?               | ENTITY
        //   Có thể chia sẻ tự do giữa nhiều chủ sở hữu?      | VALUE OBJECT
        //
        // Quy tắc thực dụng: MẶC ĐỊNH là value object. Chỉ nâng lên entity khi có một
        // câu hỏi nghiệp vụ thật sự cần theo dõi cái cụ thể đó qua thời gian. Entity
        // đắt hơn nhiều — nó cần định danh, cần kho lưu trữ, cần vòng đời, và nó không
        // chia sẻ tự do được.
        check(true, "mặc định là value object; entity phải có lý do");

        System.out.println("OK");
    }
}
