/*
 * Ngôn ngữ: Java
 * Công dụng: Repository (kho aggregate) và Unit of Work (đơn vị công việc) — gom nhiều
 * thay đổi thành MỘT lần lưu. Bài cho nổ ba con bug mà mọi hệ thống không có Unit of
 * Work đều mắc: ghi nửa vời khi lệnh thứ hai hỏng, MẤT thay đổi khi cùng một đơn hàng
 * được tải hai lần, và thay đổi bay hơi vì ai đó quên gọi `luu()`.
 * Tại sao cần học: bài 50 dạy repository ở mức "tách logic khỏi nơi lưu dữ liệu". Ở
 * mức miền, repository còn phải trả lời hai câu khó hơn: ai quyết định thời điểm ghi,
 * và chuyện gì xảy ra khi cùng một aggregate được tải hai lần trong một use case. Hai
 * câu đó dẫn thẳng tới Unit of Work — thứ mà mọi ORM đều có sẵn (EntityManager,
 * DbContext, Session) nhưng rất ít người biết mình đang dùng, nên cũng không biết vì
 * sao đôi khi dữ liệu biến mất.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UnitOfWorkDemo {

    // =====================================================================
    // MIỀN — aggregate root, và interface kho nằm CÙNG chỗ với nó
    // =====================================================================
    static final class DonHang {
        private final String ma;
        private long tongTien;
        DonHang(String ma, long tongTien) { this.ma = ma; this.tongTien = tongTien; }
        void themPhi(long p) { tongTien += p; }
        void giamGia(long g) { tongTien -= g; }
        String ma() { return ma; }
        long tongTien() { return tongTien; }
    }

    // Interface này thuộc về MIỀN, không thuộc về hạ tầng (bài 66). Nó nói bằng ngôn
    // ngữ nghiệp vụ — "tìm đơn theo mã" — không nói "SELECT", không nói "collection".
    interface KhoDonHang {
        Optional<DonHang> timTheoMa(String ma);
        void luu(DonHang don);
    }

    // =====================================================================
    // HẠ TẦNG — CSDL giả có ĐẾM, để mọi thứ thành con số
    // =====================================================================
    static final class CsdlGia {
        final Map<String, Long> bang = new LinkedHashMap<>();
        int soLanDoc = 0, soLanGhi = 0;

        DonHang doc(String ma) {
            soLanDoc++;
            // Mỗi lần đọc dựng một object MỚI — đúng như mọi ORM/driver thật làm.
            return bang.containsKey(ma) ? new DonHang(ma, bang.get(ma)) : null;
        }
        void ghi(DonHang d) { soLanGhi++; bang.put(d.ma(), d.tongTien()); }
    }

    // Kho THUẦN — không có Unit of Work. Mỗi `luu()` là một lần ghi thật, ngay lập tức.
    static final class KhoThuan implements KhoDonHang {
        private final CsdlGia csdl;
        KhoThuan(CsdlGia csdl) { this.csdl = csdl; }
        @Override public Optional<DonHang> timTheoMa(String ma) {
            return Optional.ofNullable(csdl.doc(ma));
        }
        @Override public void luu(DonHang don) { csdl.ghi(don); }
    }

    // =====================================================================
    // UNIT OF WORK — ba việc, và cả ba đều là hệ quả của một ý duy nhất:
    // "gom mọi thay đổi lại, quyết định ghi hay bỏ MỘT LẦN, ở cuối"
    // =====================================================================
    static final class DonViCongViec implements AutoCloseable {
        private final CsdlGia csdl;
        // BẢN ĐỒ ĐỊNH DANH: một mã -> đúng một object trong suốt use case này.
        private final Map<String, DonHang> theoDoi = new LinkedHashMap<>();
        private boolean daCommit = false;

        DonViCongViec(CsdlGia csdl) { this.csdl = csdl; }

        // Việc 1 — BẢN ĐỒ ĐỊNH DANH: tải hai lần vẫn ra một object.
        Optional<DonHang> tim(String ma) {
            if (theoDoi.containsKey(ma)) return Optional.of(theoDoi.get(ma));
            DonHang d = csdl.doc(ma);
            if (d != null) theoDoi.put(ma, d);
            return Optional.ofNullable(d);
        }

        // Việc 2 — THEO DÕI THAY ĐỔI: object đã lấy từ đây thì không cần gọi `luu()`.
        void dangKyMoi(DonHang d) { theoDoi.put(d.ma(), d); }

        // Việc 3 — MỘT ĐIỂM QUYẾT ĐỊNH: ghi hết, hoặc không ghi gì.
        void commit() {
            for (DonHang d : theoDoi.values()) csdl.ghi(d);
            daCommit = true;
        }

        // Ra khỏi khối `try` mà chưa commit = rollback. Không ai phải nhớ gọi nó.
        @Override public void close() { if (!daCommit) theoDoi.clear(); }

        int soObjectDangTheoDoi() { return theoDoi.size(); }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: CÙNG MỘT ĐƠN TẢI HAI LẦN -> MẤT THAY ĐỔI ----
        CsdlGia csdl = new CsdlGia();
        csdl.bang.put("DH-01", 100_000L);
        KhoDonHang khoThuan = new KhoThuan(csdl);

        // Hai chỗ khác nhau trong cùng một use case cùng cần đơn DH-01 — chuyện rất
        // thường: một hàm tính phí, một hàm tính khuyến mãi, cả hai đều tự đi tải.
        DonHang a = khoThuan.timTheoMa("DH-01").orElseThrow();
        DonHang b = khoThuan.timTheoMa("DH-01").orElseThrow();
        check(a != b, "HAI object khác nhau cho CÙNG một đơn hàng");

        a.themPhi(10_000);      // +10.000 -> 110.000
        b.giamGia(5_000);       //  -5.000 ->  95.000  (từ bản CŨ, không thấy phí của a)
        khoThuan.luu(a);
        khoThuan.luu(b);
        check(csdl.bang.get("DH-01") == 95_000L, "phí 10.000 của a BIẾN MẤT — b ghi đè");
        // Không ngoại lệ, không cảnh báo. Lệnh ghi cuối cùng thắng, và nó ghi đè bằng
        // một bản đọc từ trước. Đây là "lost update" ở ngay TRONG một tiến trình — chưa
        // cần hai người dùng, chưa cần hai máy chủ (bài 92 lo trường hợp đó).

        // BẢN ĐÚNG — Unit of Work giữ bản đồ định danh:
        csdl.bang.put("DH-02", 100_000L);
        try (DonViCongViec uow = new DonViCongViec(csdl)) {
            DonHang a2 = uow.tim("DH-02").orElseThrow();
            DonHang b2 = uow.tim("DH-02").orElseThrow();
            check(a2 == b2, "CÙNG một object — bản đồ định danh làm việc của nó");
            a2.themPhi(10_000);
            b2.giamGia(5_000);
            uow.commit();
        }
        check(csdl.bang.get("DH-02") == 105_000L, "cả hai thay đổi đều còn: 100 + 10 - 5");

        // ---- 2. CON BUG: GHI NỬA VỜI ----
        int ghiTruoc = csdl.soLanGhi;
        boolean hong = false;
        try {
            khoThuan.luu(new DonHang("DH-10", 1_000L));    // ghi THẬT ngay tại đây
            if (true) throw new RuntimeException("kiểm tra tồn kho thất bại");
        } catch (RuntimeException e) { hong = true; }
        check(hong && csdl.bang.containsKey("DH-10"),
                "DH-10 đã nằm trong CSDL dù nghiệp vụ chưa hoàn tất");
        check(csdl.soLanGhi == ghiTruoc + 1, "một lần ghi lẻ loi, không ai dọn");
        // Dữ liệu rác nửa vời. Và nó không bị phát hiện, vì về mặt kỹ thuật mọi lệnh
        // đều "thành công".

        // BẢN ĐÚNG — không commit thì không có gì được ghi:
        ghiTruoc = csdl.soLanGhi;
        hong = false;
        try (DonViCongViec uow = new DonViCongViec(csdl)) {
            uow.dangKyMoi(new DonHang("DH-11", 1_000L));
            uow.dangKyMoi(new DonHang("DH-12", 2_000L));
            check(uow.soObjectDangTheoDoi() == 2, "hai object đang chờ, chưa cái nào chạm CSDL");
            throw new RuntimeException("kiểm tra tồn kho thất bại");
            // `close()` chạy trước khi ngoại lệ thoát ra -> rollback.
        } catch (RuntimeException e) { hong = true; }
        check(hong, "vẫn hỏng như trên");
        check(!csdl.bang.containsKey("DH-11") && !csdl.bang.containsKey("DH-12"),
                "nhưng CSDL sạch: không ghi cái nào");
        check(csdl.soLanGhi == ghiTruoc, "đúng 0 lần ghi — không phải 'ghi rồi xoá'");

        // ---- 3. CON BUG: QUÊN GỌI luu() ----
        DonHang c = khoThuan.timTheoMa("DH-01").orElseThrow();
        c.themPhi(50_000);
        // ...và ở đây thiếu một dòng `khoThuan.luu(c);`
        check(csdl.bang.get("DH-01") == 95_000L, "50.000 vừa bay hơi, không dấu vết");
        // Lỗi này không có cách nào phát hiện bằng đọc code, vì thứ thiếu là một dòng
        // KHÔNG tồn tại. Compiler không biết, linter không biết, code review thì phải
        // nhớ hết mọi nhánh.

        // BẢN ĐÚNG — object lấy từ Unit of Work thì được theo dõi sẵn:
        try (DonViCongViec uow = new DonViCongViec(csdl)) {
            DonHang c2 = uow.tim("DH-02").orElseThrow();
            c2.themPhi(50_000);
            uow.commit();                  // KHÔNG có dòng `luu(c2)` nào cả
        }
        check(csdl.bang.get("DH-02") == 155_000L, "105.000 + 50.000 — không quên được nữa");
        // Đây chính là điều `EntityManager` (JPA), `DbContext` (EF), `Session`
        // (Hibernate/SQLAlchemy) làm. Rất nhiều người dùng nó hằng ngày mà tưởng "ORM tự
        // biết"; thật ra đó là Unit of Work, và hiểu nó thì hết ngạc nhiên vì sao đôi khi
        // sửa một field xong không gọi save mà dữ liệu vẫn đổi — hoặc ngược lại.

        // ---- 4. Repository trả AGGREGATE ROOT, không trả gì khác ----
        // Bài 83: aggregate là đơn vị nhất quán. Nên kho cũng phải là kho của ROOT.
        //   ĐÚNG : KhoDonHang.timTheoMa("DH-01")  -> DonHang (có luôn các dòng bên trong)
        //   SAI  : KhoDongHang.timTheoDon("DH-01") -> List<DongHang>
        // Cái sai cho phép sửa dòng hàng mà không đi qua đơn hàng, nghĩa là bất biến
        // "tổng ≤ hạn mức" mất tác dụng — đúng con bug ở bài 83 phần 2.
        //
        // Quy tắc đếm được: SỐ REPOSITORY = SỐ AGGREGATE ROOT. Nhiều hơn là dấu hiệu có
        // kho cho thứ không phải root.
        check(csdl.bang.size() >= 2, "một kho cho DonHang, không có kho riêng cho DongHang");

        // ---- 5. Interface kho thuộc về MIỀN, cài đặt thuộc về HẠ TẦNG ----
        // `KhoDonHang` khai báo cạnh `DonHang`; `KhoThuan` và `DonViCongViec` thì không.
        // Nghĩa là miền biên dịch được mà không cần JDBC, không cần driver, không cần
        // Hibernate — và test miền chạy trong vài mili giây (bài 66, bài 98, bài 99).
        //
        // Phép thử: `import` nào xuất hiện trong file miền của bạn? Nếu có
        // `javax.persistence`, `org.hibernate`, `java.sql` — thì miền đang phụ thuộc hạ
        // tầng, và mọi lời hứa còn lại của kiến trúc này đều rỗng.
        KhoDonHang khoAoTest = new KhoDonHang() {          // fake trong bộ nhớ, 4 dòng
            private final Map<String, DonHang> m = new LinkedHashMap<>();
            @Override public Optional<DonHang> timTheoMa(String ma) { return Optional.ofNullable(m.get(ma)); }
            @Override public void luu(DonHang don) { m.put(don.ma(), don); }
        };
        khoAoTest.luu(new DonHang("DH-99", 1L));
        check(khoAoTest.timTheoMa("DH-99").isPresent(), "fake 4 dòng thay được cả CSDL (bài 68)");

        // ---- 6. Cạm bẫy: `Repository<T, ID>` tổng quát ----
        // Rất hấp dẫn: một interface với `findAll`, `deleteAll`, `count`, `findByExample`,
        // `saveAll`... dùng chung cho mọi aggregate. Ba vấn đề, theo thứ tự nặng dần:
        //   1. Vi phạm ISP (bài 52): kho đơn hàng không có nghĩa gì với `deleteAll()`.
        //   2. Nó nói bằng ngôn ngữ CSDL, không nói bằng ngôn ngữ nghiệp vụ — đúng thứ
        //      bài 81 cảnh báo. `timDonQuaHan(ngay)` mang nghĩa; `findByStatusAndDateLessThan`
        //      thì không.
        //   3. `findAll()` trên một bảng 10 triệu dòng là một khẩu súng đã lên đạn, và nó
        //      nằm sẵn trong mọi kho chỉ vì "cho tổng quát".
        // Kho tốt thường có 3–6 phương thức, tất cả đều đọc lên thành câu nghiệp vụ.
        check(KhoDonHang.class.getDeclaredMethods().length == 2,
                "kho nhỏ: đúng những gì nghiệp vụ cần, không hơn");

        // ---- 7. Ranh giới: Unit of Work KHÔNG phải transaction của CSDL ----
        // Hai thứ hay bị nhầm là một. Unit of Work là khái niệm ở TẦNG ỨNG DỤNG: gom
        // thay đổi, một điểm quyết định. Transaction là cơ chế của CSDL. Chúng thường
        // trùng ranh giới (mở UoW = mở transaction), nhưng không phải lúc nào cũng:
        //   - UoW trên kho trong bộ nhớ thì không có transaction nào cả;
        //   - một saga (bài 97) có nhiều UoW, mỗi cái một transaction riêng.
        // Nhầm hai thứ dẫn tới một thói quen tai hại: mở transaction ở tầng controller
        // và giữ nó suốt request, kể cả trong lúc gọi API bên ngoài. Kết quả là khoá
        // CSDL bị giữ vài giây chờ mạng.
        check(csdl.soLanDoc > 0 && csdl.soLanGhi > 0, "đếm được cả đọc lẫn ghi — vì có ranh giới rõ");

        System.out.println("OK");
    }
}
