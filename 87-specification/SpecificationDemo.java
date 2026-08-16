/*
 * Ngôn ngữ: Java
 * Công dụng: Specification — biến một luật nghiệp vụ thành một OBJECT có tên, ghép được
 * bằng và/hoặc/không, GIẢI THÍCH được vì sao trượt, và DỊCH được sang câu truy vấn. Bài
 * cho nổ hai con bug: cùng một luật chép ở ba nơi rồi lệch nhau, và luật trong bộ nhớ
 * lệch với luật trong SQL nên hai màn hình cho hai kết quả khác nhau.
 * Tại sao cần học: Java đã có `Predicate<T>` với `and`/`or`/`negate` sẵn — nên câu hỏi
 * đúng không phải "specification là gì" mà là "vì sao không dùng luôn Predicate". Bài
 * trả lời bằng hai thứ `Predicate` không làm được: nó không có TÊN để in ra cho người
 * dùng biết mình trượt ở đâu, và nó không DỊCH được sang SQL nên luật buộc phải viết
 * hai lần. Cả hai đều là bug thật, và bài đo cả hai.
 */
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class SpecificationDemo {

    record KhachHang(String ma, int tuoi, int diem, boolean biKhoa) { }

    // =====================================================================
    // SPECIFICATION — một luật nghiệp vụ, ba khả năng
    // =====================================================================
    interface DacTa<T> {
        boolean thoaMan(T doiTuong);
        String moTa();                       // 1. có TÊN, đọc lên thành câu (bài 81)
        String dieuKienSql();                // 2. DỊCH được sang truy vấn

        // 3. GIẢI THÍCH được: trượt thì trượt ở mệnh đề nào
        default List<String> lyDoTruot(T t) {
            return thoaMan(t) ? List.of() : List.of(moTa());
        }

        default DacTa<T> va(DacTa<T> khac) { return new Va<>(this, khac); }
        default DacTa<T> hoac(DacTa<T> khac) { return new Hoac<>(this, khac); }
        default DacTa<T> khong() { return new Khong<>(this); }
    }

    record Va<T>(DacTa<T> trai, DacTa<T> phai) implements DacTa<T> {
        @Override public boolean thoaMan(T t) { return trai.thoaMan(t) && phai.thoaMan(t); }
        @Override public String moTa() { return "(" + trai.moTa() + " VÀ " + phai.moTa() + ")"; }
        @Override public String dieuKienSql() { return "(" + trai.dieuKienSql() + " AND " + phai.dieuKienSql() + ")"; }
        @Override public List<String> lyDoTruot(T t) {
            List<String> r = new ArrayList<>(trai.lyDoTruot(t));
            r.addAll(phai.lyDoTruot(t));      // gom lý do của CẢ HAI nhánh
            return r;
        }
    }

    record Hoac<T>(DacTa<T> trai, DacTa<T> phai) implements DacTa<T> {
        @Override public boolean thoaMan(T t) { return trai.thoaMan(t) || phai.thoaMan(t); }
        @Override public String moTa() { return "(" + trai.moTa() + " HOẶC " + phai.moTa() + ")"; }
        @Override public String dieuKienSql() { return "(" + trai.dieuKienSql() + " OR " + phai.dieuKienSql() + ")"; }
        @Override public List<String> lyDoTruot(T t) {
            return thoaMan(t) ? List.of() : List.of(moTa());   // trượt cả hai -> báo cả cụm
        }
    }

    record Khong<T>(DacTa<T> trong) implements DacTa<T> {
        @Override public boolean thoaMan(T t) { return !trong.thoaMan(t); }
        @Override public String moTa() { return "KHÔNG " + trong.moTa(); }
        @Override public String dieuKienSql() { return "NOT (" + trong.dieuKienSql() + ")"; }
    }

    // Ba luật cơ sở — mỗi cái là một câu người làm nghiệp vụ nói ra miệng.
    record DuTuoi(int toiThieu) implements DacTa<KhachHang> {
        @Override public boolean thoaMan(KhachHang k) { return k.tuoi() >= toiThieu; }
        @Override public String moTa() { return "đủ " + toiThieu + " tuổi"; }
        @Override public String dieuKienSql() { return "tuoi >= " + toiThieu; }
    }
    record DuDiem(int toiThieu) implements DacTa<KhachHang> {
        @Override public boolean thoaMan(KhachHang k) { return k.diem() >= toiThieu; }
        @Override public String moTa() { return "đủ " + toiThieu + " điểm tích luỹ"; }
        @Override public String dieuKienSql() { return "diem >= " + toiThieu; }
    }
    record BiKhoa() implements DacTa<KhachHang> {
        @Override public boolean thoaMan(KhachHang k) { return k.biKhoa(); }
        @Override public String moTa() { return "đang bị khoá"; }
        @Override public String dieuKienSql() { return "bi_khoa = 1"; }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        List<KhachHang> danhSach = List.of(
                new KhachHang("KH-1", 25, 150, false),   // đủ điều kiện
                new KhachHang("KH-2", 17, 500, false),   // thiếu tuổi
                new KhachHang("KH-3", 30, 50, false),    // thiếu điểm
                new KhachHang("KH-4", 40, 900, true),    // bị khoá
                new KhachHang("KH-5", 22, 100, false));  // đủ điều kiện (đúng ngưỡng)

        // ---- 1. LUẬT LÀ MỘT OBJECT, GHÉP ĐƯỢC ----
        DacTa<KhachHang> duocVayTinChap =
                new DuTuoi(18).va(new DuDiem(100)).va(new BiKhoa().khong());

        List<KhachHang> hopLe = danhSach.stream().filter(duocVayTinChap::thoaMan).toList();
        check(hopLe.size() == 2, "hai khách đủ điều kiện: KH-1 và KH-5");
        check(duocVayTinChap.moTa().equals("((đủ 18 tuổi VÀ đủ 100 điểm tích luỹ) VÀ KHÔNG đang bị khoá)"),
                "luật tự đọc lên thành câu — dán thẳng vào tài liệu được");

        // ---- 2. CON BUG: cùng một luật chép ở ba nơi ----
        // Không có specification, luật này xuất hiện ở: màn hình đăng ký, job gửi email
        // mời vay, và báo cáo cho phòng rủi ro. Ba chỗ, ba lần gõ tay.
        //
        //   màn hình : k.tuoi() >= 18 && k.diem() >= 100 && !k.biKhoa()
        //   job email: k.tuoi() >= 18 && k.diem() >= 100                  <- QUÊN biKhoa
        //   báo cáo  : k.tuoi() >  18 && k.diem() >= 100 && !k.biKhoa()   <- `>` thay vì `>=`
        Predicate<KhachHang> manHinh = k -> k.tuoi() >= 18 && k.diem() >= 100 && !k.biKhoa();
        Predicate<KhachHang> jobEmail = k -> k.tuoi() >= 18 && k.diem() >= 100;
        Predicate<KhachHang> baoCao = k -> k.tuoi() > 18 && k.diem() >= 100 && !k.biKhoa();

        long nManHinh = danhSach.stream().filter(manHinh).count();
        long nJob = danhSach.stream().filter(jobEmail).count();
        long nBaoCao = danhSach.stream().filter(baoCao).count();
        check(nManHinh == 2 && nJob == 3 && nBaoCao == 2, "ba con số khác nhau cho CÙNG một luật");
        check(nJob - nManHinh == 1, "job gửi lời mời vay cho một khách ĐANG BỊ KHOÁ");
        // Ba dòng trên đều "chạy đúng" theo ý người viết chúng. Không có test nào hỏng,
        // vì mỗi chỗ có test riêng và test đó khớp với code ở chỗ đó. Bug chỉ lộ ra khi
        // ai đó đối chiếu hai màn hình.
        //
        // Với specification, ba chỗ dùng CHUNG một object, nên câu hỏi "luật là gì" có
        // đúng một câu trả lời, ở đúng một dòng code.

        // ---- 3. ĐIỀU `Predicate` KHÔNG LÀM ĐƯỢC (1): GIẢI THÍCH ----
        KhachHang truot = new KhachHang("KH-9", 16, 20, true);
        check(!manHinh.test(truot), "Predicate nói: false");
        // ...và hết. `false` không nói được vì sao. Muốn báo cho khách "bạn chưa đủ tuổi
        // và chưa đủ điểm" thì phải viết LẠI toàn bộ luật lần thứ tư, dưới dạng chuỗi if.

        List<String> lyDo = duocVayTinChap.lyDoTruot(truot);
        check(lyDo.size() == 3, "specification nói: trượt ở BA mệnh đề");
        check(lyDo.get(0).equals("đủ 18 tuổi") && lyDo.get(2).equals("KHÔNG đang bị khoá"),
                "và nói rõ từng mệnh đề nào — dán thẳng vào thông báo lỗi");
        check(duocVayTinChap.lyDoTruot(danhSach.get(0)).isEmpty(), "khách hợp lệ: không lý do nào");
        // Đây là giá trị lớn nhất của specification trong thực tế, và nó ít được nhắc
        // tới nhất: màn hình "vì sao đơn của tôi bị từ chối" sinh ra tự động, luôn khớp
        // với luật thật, không bao giờ lỗi thời.

        // ---- 4. ĐIỀU `Predicate` KHÔNG LÀM ĐƯỢC (2): DỊCH SANG TRUY VẤN ----
        // Đây là con bug thứ hai, và nó tốn tiền nhất. Danh sách 2 triệu khách hàng thì
        // không lọc trong bộ nhớ được — phải lọc bằng SQL. Nên luật được viết LẦN NỮA:
        String sqlGoTay = "SELECT * FROM khach_hang WHERE tuoi >= 18 AND diem >= 100";
        //                                            ^ lại quên `bi_khoa = 0`
        check(!sqlGoTay.contains("bi_khoa"), "SQL gõ tay lệch khỏi luật trong code");

        String sqlTuDacTa = "SELECT * FROM khach_hang WHERE " + duocVayTinChap.dieuKienSql();
        check(sqlTuDacTa.equals(
                "SELECT * FROM khach_hang WHERE ((tuoi >= 18 AND diem >= 100) AND NOT (bi_khoa = 1))"),
                "SQL sinh TỪ CHÍNH luật — không thể lệch");
        // Cùng một object trả lời được cả hai câu hỏi: "khách này có hợp lệ không" (trong
        // bộ nhớ) và "những khách nào hợp lệ" (trong CSDL). Một nguồn sự thật.
        //
        // Ranh giới cần biết: chỉ sinh SQL từ cấu trúc CỦA CHÍNH specification, và các
        // giá trị ngưỡng phải là số/hằng do miền quyết định. Nếu một ngày cần nhét chuỗi
        // từ người dùng vào đây, hãy trả về câu có THAM SỐ (`tuoi >= ?`) cộng danh sách
        // giá trị — đừng nối chuỗi.

        // ---- 5. GHÉP LẠI THÀNH LUẬT MỚI MÀ KHÔNG SỬA GÌ (bài 61) ----
        DacTa<KhachHang> khachVip = new DuDiem(500);
        DacTa<KhachHang> uuDaiDacBiet = duocVayTinChap.hoac(khachVip.va(new BiKhoa().khong()));
        check(uuDaiDacBiet.thoaMan(new KhachHang("KH-2", 17, 500, false)),
                "khách 17 tuổi nhưng 500 điểm: trượt luật cũ, đạt luật mới");
        check(!uuDaiDacBiet.thoaMan(new KhachHang("KH-4", 40, 900, true)),
                "còn khách bị khoá thì vẫn trượt cả hai nhánh");
        // Luật mới ra đời mà KHÔNG sửa một dòng nào của ba luật cơ sở. Đó là mở-đóng
        // (bài 61) áp cho luật nghiệp vụ.

        // ---- 6. KHI NÀO KHÔNG CẦN SPECIFICATION ----
        // Đây là mẫu thiết kế dễ bị lạm dụng. Ba câu hỏi, cần CÓ ít nhất hai:
        //   (a) Luật này có dùng ở NHIỀU HƠN MỘT chỗ không?
        //   (b) Nó có cần GHÉP với luật khác không?
        //   (c) Có ai cần biết VÌ SAO trượt, hoặc cần dịch nó sang truy vấn không?
        // Nếu chỉ có một chỗ dùng, không ghép, không giải thích — thì `if` là đúng, và
        // ba lớp `Va`/`Hoac`/`Khong` chỉ là chi phí.
        //
        // Và nếu luật thuộc về đúng một entity, nó nên là một PHƯƠNG THỨC của entity đó
        // (bài 86 câu hỏi b): `don.quaHan(homNay)` tốt hơn `new DonQuaHan(homNay).thoaMan(don)`.
        check(new DuTuoi(18).thoaMan(danhSach.get(0)), "luật cơ sở vẫn dùng lẻ được");

        // ---- 7. Specification là nền của bài 88 ----
        // Ở đây luật được ghép LÚC VIẾT CODE. Bước tiếp theo là chọn luật LÚC CHẠY —
        // mỗi quốc gia, mỗi hạng khách một luật khác nhau, và code gọi không đổi một chữ.
        // Đó là policy object (bài 88), và nó chỉ là specification + một bảng tra.
        check(duocVayTinChap instanceof Va, "luật ghép vẫn là một object bình thường");

        System.out.println("OK");
    }
}
