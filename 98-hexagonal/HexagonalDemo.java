/*
 * Ngôn ngữ: Java
 * Công dụng: Kiến trúc lục giác (ports & adapters) — miền định nghĩa CỔNG, hạ tầng cung
 * cấp BỘ NỐI, và mọi phụ thuộc chỉ đi vào TRONG. Bài cho nổ ba con bug: miền gọi thẳng
 * hạ tầng nên test phải dựng cả CSDL; cổng được định nghĩa bằng từ ngữ của bộ nối nên đổi
 * hạ tầng là đổi luôn miền; và "cấu trúc thư mục lục giác" mà chiều phụ thuộc vẫn ngược.
 * Tại sao cần học: đây là bài gom lại mọi thứ tầng này đã dựng. Bài 66 nói tầng cao không
 * phụ thuộc chi tiết; bài 85 nói interface kho thuộc về miền; bài 94 nói bọc hệ ngoài lại.
 * Lục giác là cái tên của hình dạng mà ba điều đó cộng lại tạo ra — và điểm mấu chốt ít ai
 * nói rõ: nó KHÔNG phải một cấu trúc thư mục. Nó là một LUẬT VỀ CHIỀU CỦA `import`, và
 * luật đó kiểm được bằng máy.
 */
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HexagonalDemo {

    // =====================================================================
    // MIỀN — không biết CSDL, mạng, đồng hồ hay khung phần mềm nào tồn tại
    // =====================================================================
    record DonHang(String ma, String maKhach, long tongTien, long tao) {
        DonHang {
            if (tongTien <= 0) throw new IllegalArgumentException("tổng tiền phải dương");
        }
    }

    // ---- CỔNG BỊ ĐIỀU KHIỂN (miền GỌI RA ngoài) ----
    // Chú ý từ ngữ: "tìm theo mã", "lưu", "báo cho khách". Không có `ResultSet`, không có
    // `HttpRequest`, không có `Connection`. Cổng nói tiếng NGHIỆP VỤ (bài 81).
    interface KhoDonHang {
        Optional<DonHang> timTheoMa(String ma);
        void luu(DonHang d);
    }
    interface BaoChoKhach { void bao(String maKhach, String noiDung); }
    interface DongHo { long bayGio(); }          // bài 67

    // ---- CỔNG ĐIỀU KHIỂN (thế giới GỌI VÀO miền) ----
    interface DatHang { DonHang thucHien(String maKhach, long tongTien); }

    /** Lõi ứng dụng: cài cổng điều khiển, dùng cổng bị điều khiển. 0 phụ thuộc hạ tầng. */
    static final class DichVuDatHang implements DatHang {
        private final KhoDonHang kho;
        private final BaoChoKhach bao;
        private final DongHo dongHo;
        private int dem = 0;

        DichVuDatHang(KhoDonHang kho, BaoChoKhach bao, DongHo dongHo) {
            this.kho = kho; this.bao = bao; this.dongHo = dongHo;
        }

        @Override public DonHang thucHien(String maKhach, long tongTien) {
            DonHang d = new DonHang("DH-" + (++dem), maKhach, tongTien, dongHo.bayGio());
            kho.luu(d);
            bao.bao(maKhach, "đã tạo đơn " + d.ma());
            return d;
        }
    }

    // =====================================================================
    // BỘ NỐI — hạ tầng. Chúng biết miền; miền KHÔNG biết chúng.
    // =====================================================================
    static final class KhoTrongBoNho implements KhoDonHang {
        final Map<String, DonHang> bang = new LinkedHashMap<>();
        @Override public Optional<DonHang> timTheoMa(String ma) { return Optional.ofNullable(bang.get(ma)); }
        @Override public void luu(DonHang d) { bang.put(d.ma(), d); }
    }

    /** Bộ nối thứ hai: giả lập SQL. Cùng cổng, cài đặt hoàn toàn khác. */
    static final class KhoSql implements KhoDonHang {
        final List<String> cauLenh = new ArrayList<>();
        final Map<String, DonHang> bang = new LinkedHashMap<>();
        @Override public Optional<DonHang> timTheoMa(String ma) {
            cauLenh.add("SELECT * FROM don_hang WHERE ma = '" + ma + "'");
            return Optional.ofNullable(bang.get(ma));
        }
        @Override public void luu(DonHang d) {
            cauLenh.add("INSERT INTO don_hang VALUES ('" + d.ma() + "', ...)");
            bang.put(d.ma(), d);
        }
    }

    static final class BaoGia implements BaoChoKhach {
        final List<String> daBao = new ArrayList<>();
        @Override public void bao(String maKhach, String noiDung) { daBao.add(maKhach + ":" + noiDung); }
    }
    static final class DongHoCoDinh implements DongHo {
        private final long luc;
        DongHoCoDinh(long luc) { this.luc = luc; }
        @Override public long bayGio() { return luc; }
    }

    // =====================================================================
    // BẢN SAI — miền gọi thẳng hạ tầng
    // =====================================================================
    static final class KetNoiCsdl {                       // "hạ tầng"
        static int soLanMoKetNoi = 0;
        static boolean coSan = false;
        KetNoiCsdl() {
            soLanMoKetNoi++;
            if (!coSan) throw new IllegalStateException("không kết nối được CSDL");
        }
    }
    static final class DichVuDatHangSai {
        DonHang thucHien(String maKhach, long tongTien) {
            KetNoiCsdl kn = new KetNoiCsdl();             // <- `new` thẳng hạ tầng
            return new DonHang("DH-X", maKhach, tongTien, System.currentTimeMillis());
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: miền gọi thẳng hạ tầng -> KHÔNG TEST ĐƯỢC ----
        KetNoiCsdl.coSan = false;
        boolean khongTestDuoc = false;
        try { new DichVuDatHangSai().thucHien("KH-01", 100_000L); }
        catch (IllegalStateException e) { khongTestDuoc = true; }
        check(khongTestDuoc, "muốn test một luật nghiệp vụ, phải dựng cả CSDL trước");
        check(KetNoiCsdl.soLanMoKetNoi == 1, "và mỗi lần chạy test là một lần mở kết nối");
        // Hệ quả dây chuyền, theo thứ tự người ta nhận ra: test chậm -> test giòn (hỏng vì
        // CSDL, không phải vì bug) -> không ai chạy test nữa -> không ai viết test nữa.
        // Và nó bắt đầu từ đúng một dòng `new KetNoiCsdl()` nằm sai chỗ.
        //
        // Chú ý: `System.currentTimeMillis()` ở dòng dưới cũng là một lời gọi hạ tầng —
        // và nó làm kết quả không tất định (bài 67).

        // ---- 2. LÕI ỨNG DỤNG CHẠY VỚI 0 HẠ TẦNG ----
        KhoTrongBoNho kho = new KhoTrongBoNho();
        BaoGia bao = new BaoGia();
        DatHang dv = new DichVuDatHang(kho, bao, new DongHoCoDinh(1_700_000_000L));

        DonHang d = dv.thucHien("KH-01", 250_000L);
        check(d.ma().equals("DH-1") && d.tao() == 1_700_000_000L, "kết quả TẤT ĐỊNH");
        check(kho.bang.size() == 1, "đơn đã được lưu");
        check(bao.daBao.equals(List.of("KH-01:đã tạo đơn DH-1")), "và khách đã được báo");
        check(KetNoiCsdl.soLanMoKetNoi == 1, "0 kết nối CSDL nào được mở thêm");
        // Ba dòng dựng bối cảnh, không mock, không khung phần mềm, không tệp cấu hình.
        // Bộ test miền chạy trong mili-giây và không bao giờ hỏng vì mạng.

        // ---- 3. ĐỔI BỘ NỐI: sửa ĐÚNG MỘT DÒNG, ở gốc lắp ráp ----
        KhoSql khoSql = new KhoSql();
        DatHang dvSql = new DichVuDatHang(khoSql, bao, new DongHoCoDinh(1_700_000_000L));
        DonHang d2 = dvSql.thucHien("KH-02", 300_000L);
        check(d2.ma().equals("DH-1"), "cùng logic nghiệp vụ, không sửa một chữ nào trong miền");
        check(khoSql.cauLenh.size() == 1 && khoSql.cauLenh.get(0).startsWith("INSERT"),
                "nhưng lần này nó sinh SQL");
        // "Đổi CSDL sau này" hiếm khi xảy ra thật, và đó KHÔNG phải lý do chính. Lý do
        // chính là: bộ nối thứ hai — bản trong bộ nhớ — cho phép TEST, và nó được dùng
        // hằng ngày. Cái lợi có ngay từ tuần đầu, không phải sau ba năm (bài 68).

        // ---- 4. CỔNG PHẢI NÓI TIẾNG NGHIỆP VỤ, KHÔNG NÓI TIẾNG BỘ NỐI ----
        // Cổng RÒ RỈ (rất hay gặp), viết bằng từ ngữ của cài đặt:
        //     interface KhoDonHang { ResultSet query(String sql); void execute(PreparedStatement p); }
        // Nó "là interface" nên trông như đã đảo ngược phụ thuộc — nhưng chưa. Ba hậu quả:
        //   1. Không viết nổi bản trong bộ nhớ (lấy đâu ra `ResultSet`?) -> mất luôn cái
        //      lợi ở phần 3.
        //   2. Miền vẫn phải `import java.sql` -> chiều phụ thuộc vẫn ngược.
        //   3. Đổi sang kho khoá-giá trị là phải sửa cổng, tức là sửa miền.
        // Phép thử: đọc tên phương thức của cổng lên. Nếu người làm nghiệp vụ hiểu được
        // thì cổng đúng; nếu chỉ lập trình viên hiểu thì đó là bộ nối đội lốt cổng (bài 81).
        for (Method m : KhoDonHang.class.getDeclaredMethods()) {
            check(!m.toString().contains("java.sql") && !m.toString().contains("Http"),
                    "cổng không mang kiểu hạ tầng: " + m.getName());
        }
        check(KhoDonHang.class.getDeclaredMethods().length == 2, "và cổng NHỎ — 2 phương thức (bài 52)");

        // ---- 5. BÀI TEST KIẾN TRÚC: CHIỀU PHỤ THUỘC ----
        // Lục giác KHÔNG phải một cấu trúc thư mục. Đổi tên gói thành `domain/`,
        // `infrastructure/` mà `import` vẫn đi từ trong ra ngoài thì chẳng có gì thay đổi.
        // Luật thật chỉ có một: LÕI KHÔNG ĐƯỢC THAM CHIẾU HẠ TẦNG. Và nó kiểm được:
        List<Class<?>> loi = List.of(DonHang.class, DichVuDatHang.class, KhoDonHang.class,
                                     BaoChoKhach.class, DongHo.class, DatHang.class);
        List<Class<?>> haTang = List.of(KhoTrongBoNho.class, KhoSql.class, BaoGia.class,
                                        DongHoCoDinh.class, KetNoiCsdl.class);
        List<String> viPham = new ArrayList<>();
        for (Class<?> lop : loi) {
            for (Field f : lop.getDeclaredFields())
                if (haTang.contains(f.getType())) viPham.add(lop.getSimpleName() + "." + f.getName());
            for (Method m : lop.getDeclaredMethods()) {
                if (haTang.contains(m.getReturnType())) viPham.add(lop.getSimpleName() + "." + m.getName());
                for (Class<?> t : m.getParameterTypes())
                    if (haTang.contains(t)) viPham.add(lop.getSimpleName() + "." + m.getName());
            }
        }
        check(viPham.isEmpty(), "0 tham chiếu từ lõi ra hạ tầng: " + viPham);
        // Sáu dòng trên chạy trong CI (thực tế dùng ArchUnit). Chúng bắt đúng thời điểm ai
        // đó "cho tiện" nhét một `Connection` vào một lớp miền — thời điểm kiến trúc bắt
        // đầu tan rã, và mọi test nghiệp vụ vẫn xanh.
        //
        // Chiều ngược lại thì BẮT BUỘC phải có: hạ tầng biết miền.
        boolean haTangBietMien = KhoDonHang.class.isAssignableFrom(KhoSql.class);
        check(haTangBietMien, "hạ tầng CÀI cổng của miền — mũi tên chỉ vào trong");

        // ---- 6. HAI LOẠI CỔNG, VÀ VÌ SAO PHẢI PHÂN BIỆT ----
        //
        //   Loại              | Ai gọi ai              | Ví dụ ở đây     | Bộ nối
        //   ------------------|------------------------|-----------------|------------------
        //   Cổng ĐIỀU KHIỂN   | thế giới -> miền       | `DatHang`       | REST, CLI, hàng đợi
        //   Cổng BỊ ĐIỀU KHIỂN| miền -> thế giới       | `KhoDonHang`    | CSDL, SMTP, đồng hồ
        //
        // Cả hai đều do MIỀN định nghĩa — đó là điểm mấu chốt và cũng là chỗ hay sai. Với
        // cổng bị điều khiển thì ai cũng hiểu; với cổng điều khiển thì người ta hay để
        // khung web định nghĩa (controller gọi thẳng vào lớp dịch vụ). Hậu quả: chữ ký use
        // case bị định hình bởi HTTP, và một job nền muốn dùng lại thì phải giả lập request.
        check(DatHang.class.isInterface() && KhoDonHang.class.isInterface(), "hai cổng, cùng nằm ở miền");
        check(DatHang.class.getDeclaredMethods().length == 1, "cổng điều khiển = MỘT use case");

        // ---- 7. GỐC LẮP RÁP: nơi DUY NHẤT biết mọi thứ ----
        // Có đúng một chỗ trong chương trình được phép `new` cả miền lẫn hạ tầng — hàm
        // `main`, hoặc lớp cấu hình. Mọi chỗ khác chỉ nhận phụ thuộc qua constructor (bài 51).
        //
        //   main() -> new DichVuDatHang(new KhoSql(), new GuiEmailThat(), Instant::now)
        //   test() -> new DichVuDatHang(new KhoTrongBoNho(), new BaoGia(), () -> 1_700_000_000L)
        //
        // Hai dòng đó là toàn bộ khác biệt giữa chạy thật và chạy test. Nếu để đổi sang
        // test bạn phải sửa file cấu hình, đặt biến môi trường, hay bật một "profile", thì
        // gốc lắp ráp chưa tồn tại.
        DatHang dvTest = new DichVuDatHang(new KhoTrongBoNho(), new BaoGia(), () -> 42L);
        check(dvTest.thucHien("KH-99", 1L).tao() == 42L, "lắp ráp cho test: một dòng");

        // ---- 8. RANH GIỚI: khi nào KHÔNG cần lục giác ----
        // Nó có chi phí thật: mỗi cổng là một interface, mỗi bộ nối là một lớp, và với một
        // ứng dụng CRUD thuần thì đó là ba lớp cho một việc mà `save()` làm xong.
        // Ba dấu hiệu ĐỦ để cần:
        //   - có luật nghiệp vụ đáng test riêng (không chỉ đọc/ghi bảng);
        //   - có nhiều hơn một đường vào (REST + hàng đợi + job nền);
        //   - có hệ ngoài mà bạn không kiểm soát (bài 94).
        // Thiếu cả ba thì một controller gọi thẳng repository là thiết kế đúng — và biết
        // lúc nào KHÔNG áp dụng một mẫu cũng là một phần của việc hiểu nó.
        check(loi.size() == 6 && haTang.size() == 5, "lõi 6 lớp, hạ tầng 5 — và mũi tên chỉ một chiều");

        System.out.println("OK");
    }
}
