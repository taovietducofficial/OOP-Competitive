/*
 * Ngôn ngữ: Java
 * Công dụng: Test mô hình miền — không khung phần mềm, không CSDL, không mock. Bài cho nổ
 * ba con bug của cách test phổ biến: test bám vào CÁCH LÀM nên refactor không đổi hành vi
 * vẫn làm đỏ 3 test; test dựng dữ liệu bằng 12 dòng nên không ai đọc được nó kiểm gì; và
 * bản giả nói dối so với bản thật mà không test nào phát hiện.
 * Tại sao cần học: cả tầng này đã dựng một mô hình miền không phụ thuộc hạ tầng (bài 98).
 * Bài này thu hoạch: khi miền không biết CSDL, test của nó chỉ còn là HÀM + ASSERT — đúng
 * như mọi file self-check trong series này. Không phải "chúng ta lười không dùng JUnit";
 * đó là bằng chứng rằng mô hình miền đã tách sạch. Nếu test miền của bạn cần một khung
 * phần mềm để chạy, thì thứ bạn đang test không phải miền.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DomainTestDemo {

    // =====================================================================
    // MIỀN — đối tượng được test
    // =====================================================================
    record DongHang(String sanPham, long donGia, int soLuong) {
        long thanhTien() { return donGia * soLuong; }
    }

    static final class DonHang {
        static final long HAN_MUC = 50_000_000L;
        private final String ma;
        private final List<DongHang> cacDong = new ArrayList<>();
        int soLanGoiKho = 0;                       // để đo ở phần 1

        DonHang(String ma) { this.ma = ma; }

        void themDong(String sp, long donGia, int sl, KhoHang kho) {
            if (tongTien() + donGia * sl > HAN_MUC)
                throw new IllegalStateException("đơn vượt hạn mức");
            kho.giuCho(sp, sl);
            soLanGoiKho++;
            cacDong.add(new DongHang(sp, donGia, sl));
        }

        /** Phiên bản gộp: giữ chỗ MỘT lần cho nhiều dòng. Hành vi ngoài KHÔNG đổi. */
        void themNhieuDong(List<DongHang> ds, KhoHang kho) {
            long them = ds.stream().mapToLong(DongHang::thanhTien).sum();
            if (tongTien() + them > HAN_MUC) throw new IllegalStateException("đơn vượt hạn mức");
            Map<String, Integer> gop = new LinkedHashMap<>();
            for (DongHang d : ds) gop.merge(d.sanPham(), d.soLuong(), Integer::sum);
            kho.giuChoNhieu(gop);                  // MỘT lượt gọi thay vì n lượt
            soLanGoiKho++;
            cacDong.addAll(ds);
        }

        long tongTien() { return cacDong.stream().mapToLong(DongHang::thanhTien).sum(); }
        int soDong() { return cacDong.size(); }
        String ma() { return ma; }

        /** Chia đều tiền đơn cho n người — bất biến ở phần 3 (bài 90). */
        List<Long> chiaDeu(int n) {
            long tong = tongTien(), moi = tong / n, du = tong % n;
            List<Long> ra = new ArrayList<>();
            for (int i = 0; i < n; i++) ra.add(moi + (i < du ? 1 : 0));
            return ra;
        }
    }

    interface KhoHang {
        void giuCho(String sanPham, int soLuong);
        void giuChoNhieu(Map<String, Integer> gop);
    }

    /** Bản GIẢ — dùng cho test. Nó là một cài đặt thật, không phải mock. */
    static final class KhoGia implements KhoHang {
        final Map<String, Integer> daGiu = new LinkedHashMap<>();
        int soLuotGoi = 0;
        @Override public void giuCho(String sp, int sl) { soLuotGoi++; daGiu.merge(sp, sl, Integer::sum); }
        @Override public void giuChoNhieu(Map<String, Integer> gop) {
            soLuotGoi++;
            gop.forEach((k, v) -> daGiu.merge(k, v, Integer::sum));
        }
    }

    /** Bản GIẢ NÓI DỐI — cho phần 4. Nó quên mất luật "không giữ chỗ số lượng âm". */
    static final class KhoGiaNoiDoi implements KhoHang {
        final Map<String, Integer> daGiu = new LinkedHashMap<>();
        @Override public void giuCho(String sp, int sl) { daGiu.merge(sp, sl, Integer::sum); }
        @Override public void giuChoNhieu(Map<String, Integer> gop) { daGiu.putAll(gop); }
    }

    /** Bản "thật" — có thêm một luật mà bản giả nói dối không có. */
    static final class KhoThat implements KhoHang {
        final Map<String, Integer> daGiu = new LinkedHashMap<>();
        @Override public void giuCho(String sp, int sl) {
            if (sl <= 0) throw new IllegalArgumentException("số lượng giữ chỗ phải dương");
            daGiu.merge(sp, sl, Integer::sum);
        }
        @Override public void giuChoNhieu(Map<String, Integer> gop) {
            gop.forEach(this::giuCho);
        }
    }

    // =====================================================================
    // BỘ DỰNG DỮ LIỆU TEST — phần 2
    // =====================================================================
    static final class DonHangBuilder {
        private String ma = "DH-MAU";
        private final List<DongHang> dong = new ArrayList<>();
        DonHangBuilder ma(String m) { this.ma = m; return this; }
        DonHangBuilder voiDong(String sp, long gia, int sl) { dong.add(new DongHang(sp, gia, sl)); return this; }
        DonHang dung(KhoHang kho) {
            DonHang d = new DonHang(ma);
            if (!dong.isEmpty()) d.themNhieuDong(dong, kho);
            return d;
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: test bám vào CÁCH LÀM, không bám vào HÀNH VI ----
        // Ba "test" dưới đây kiểm SỐ LƯỢT GỌI kho — thứ mà mock framework khuyến khích
        // (`verify(kho, times(3)).giuCho(...)`).
        KhoGia khoA = new KhoGia();
        DonHang donA = new DonHang("DH-01");
        donA.themDong("laptop", 1_000_000L, 1, khoA);
        donA.themDong("chuột", 200_000L, 2, khoA);
        donA.themDong("bàn phím", 300_000L, 1, khoA);
        check(khoA.soLuotGoi == 3, "test-theo-cách-làm: 'kho phải được gọi ĐÚNG 3 lần'");

        // Hôm nay ai đó gộp ba lượt gọi thành một để giảm tải cho kho. HÀNH VI NGOÀI KHÔNG
        // ĐỔI: cùng số dòng, cùng tổng tiền, cùng số lượng được giữ.
        KhoGia khoB = new KhoGia();
        DonHang donB = new DonHang("DH-01");
        donB.themNhieuDong(List.of(new DongHang("laptop", 1_000_000L, 1),
                                   new DongHang("chuột", 200_000L, 2),
                                   new DongHang("bàn phím", 300_000L, 1)), khoB);

        check(khoB.soLuotGoi == 1, "test-theo-cách-làm giờ ĐỎ: 1 ≠ 3");
        check(donA.tongTien() == donB.tongTien(), "nhưng tổng tiền GIỐNG HỆT");
        check(donA.soDong() == donB.soDong(), "cùng số dòng");
        check(khoA.daGiu.equals(khoB.daGiu), "và kho giữ chỗ ĐÚNG NHƯ NHAU");
        // Ba dòng cuối là test-theo-HÀNH-VI, và cả ba vẫn xanh. Đó là toàn bộ khác biệt:
        //   - Test hành vi hỏng khi NGHIỆP VỤ sai      -> tín hiệu THẬT.
        //   - Test cách làm hỏng khi CODE ĐỔI          -> tín hiệu GIẢ.
        // Bộ test đầy tín hiệu giả là bộ test bị tắt sau ba tháng. Quy tắc: đừng kiểm "đã
        // gọi hàm nào bao nhiêu lần"; kiểm "kết quả có đúng không, trạng thái có đúng không".
        //
        // Ngoại lệ hợp lệ duy nhất: khi VIỆC GỌI CHÍNH LÀ hành vi cần kiểm — "đã gửi đúng
        // một email cho khách" (bài 84 phần 4). Lúc đó số lượt gọi là nghiệp vụ, không phải
        // chi tiết cài đặt.

        // ---- 2. BỘ DỰNG DỮ LIỆU: test đọc lên phải nói được nó kiểm gì ----
        // Không có bộ dựng, mỗi test mở đầu bằng 10 dòng nhiễu và người đọc không thấy
        // được ĐIỀU GÌ trong dữ liệu là quan trọng với test này.
        KhoGia kho2 = new KhoGia();
        DonHang ganHanMuc = new DonHangBuilder()
                .ma("DH-02")
                .voiDong("máy chủ", 49_000_000L, 1)      // <- chi tiết DUY NHẤT quan trọng
                .dung(kho2);
        boolean vuotHanMuc = false;
        try { ganHanMuc.themDong("laptop", 2_000_000L, 1, kho2); }
        catch (IllegalStateException e) { vuotHanMuc = true; }
        check(vuotHanMuc, "đơn 49 triệu + 2 triệu -> vượt hạn mức 50 triệu");
        // Đọc ba dòng trên là biết ngay test kiểm gì. Bộ dựng có giá trị mặc định cho MỌI
        // thứ, và test chỉ nói ra thứ nó QUAN TÂM — đó là toàn bộ mục đích của nó.

        // ---- 3. BẤT BIẾN: kiểm với NGHÌN đầu vào, không phải ba ----
        // Test theo ví dụ trả lời "với đầu vào này thì sao". Test theo bất biến trả lời
        // "với MỌI đầu vào thì điều gì luôn đúng" — và nó bắt được những ca mà không ai
        // nghĩ ra để viết ví dụ.
        Random rnd = new Random(42);                 // hạt giống CỐ ĐỊNH -> tái hiện được
        int soCaChay = 0;
        for (int i = 0; i < 1000; i++) {
            KhoGia k = new KhoGia();
            long gia = 1 + rnd.nextInt(1_000_000);
            int sl = 1 + rnd.nextInt(40);
            int nguoi = 1 + rnd.nextInt(9);
            DonHang d = new DonHangBuilder().voiDong("x", gia, sl).dung(k);
            List<Long> phan = d.chiaDeu(nguoi);
            long tongPhan = phan.stream().mapToLong(Long::longValue).sum();
            check(tongPhan == d.tongTien(), "BẤT BIẾN: tổng các phần = tổng ban đầu");
            check(phan.size() == nguoi, "BẤT BIẾN: đúng số phần được yêu cầu");
            long max = phan.stream().mapToLong(Long::longValue).max().orElse(0);
            long min = phan.stream().mapToLong(Long::longValue).min().orElse(0);
            check(max - min <= 1, "BẤT BIẾN: chênh lệch giữa các phần không quá 1 đơn vị");
            soCaChay++;
        }
        check(soCaChay == 1000, "1.000 ca sinh ngẫu nhiên, 3 bất biến, 0 dòng dữ liệu gõ tay");
        // Ba bất biến đó là bài 90 phần 4 viết dưới dạng test. Chúng đắt hơn ví dụ ở chỗ
        // phải NGHĨ RA được, và rẻ hơn ở chỗ không phải bảo trì bảng dữ liệu.
        // Hạt giống cố định là bắt buộc: một test đỏ ngẫu nhiên mà không tái hiện được thì
        // vô dụng — và tệ hơn, nó sẽ bị đánh dấu "bỏ qua".

        // ---- 4. CON BUG: BẢN GIẢ NÓI DỐI ----
        // Bản giả không có luật "số lượng giữ chỗ phải dương". Test dùng nó thì xanh:
        DonHang donC = new DonHang("DH-03");
        KhoGiaNoiDoi noiDoi = new KhoGiaNoiDoi();
        donC.themDong("laptop", 1_000_000L, -5, noiDoi);        // số lượng ÂM
        check(noiDoi.daGiu.get("laptop") == -5, "bản giả nhận số lượng âm — test XANH");

        // ...và bản thật thì nổ, trên production.
        boolean banThatNo = false;
        try { new DonHang("DH-04").themDong("laptop", 1_000_000L, -5, new KhoThat()); }
        catch (IllegalArgumentException e) { banThatNo = true; }
        check(banThatNo, "bản thật ném ngoại lệ — bug đi thẳng ra production");
        // Cách chữa là BỘ KIỂM TRA HỢP ĐỒNG (bài 68): một bộ test viết một lần, chạy trên
        // MỌI cài đặt của cổng. Bản giả nào không qua được thì không được dùng.
        List<KhoHang> moiCaiDat = List.of(new KhoGia(), new KhoThat());
        int soCaiDatQua = 0;
        for (KhoHang k : moiCaiDat) {
            boolean chan = false;
            try { k.giuCho("x", -1); } catch (IllegalArgumentException e) { chan = true; }
            if (chan) soCaiDatQua++;
        }
        check(soCaiDatQua == 1, "chạy hợp đồng trên 2 cài đặt -> lộ ra ngay cái nào nói dối");
        // (Ở đây `KhoGia` cũng trượt — đúng như dự định: bộ kiểm tra hợp đồng vừa chỉ ra
        //  rằng bản giả trong bộ test này CẦN được sửa cho khớp với bản thật.)

        // ---- 5. TEST MIỀN KHÔNG CẦN KHUNG PHẦN MỀM ----
        // Toàn bộ file này — và mọi file self-check trong series — là hàm + `assert`. Không
        // JUnit, không mock, không `@SpringBootTest`, 0 mili-giây khởi động.
        //
        // Đó không phải "lười không dùng JUnit". Nó là BẰNG CHỨNG rằng mô hình miền đã
        // tách sạch khỏi hạ tầng (bài 98). Phép thử ngược lại cũng đúng và rất hữu ích:
        //
        //   NẾU TEST MIỀN CỦA BẠN CẦN MỘT KHUNG PHẦN MỀM ĐỂ CHẠY,
        //   THÌ THỨ BẠN ĐANG TEST KHÔNG PHẢI MIỀN.
        //
        // Trong dự án thật vẫn nên dùng JUnit — vì nó cho báo cáo, chạy song song, tích hợp
        // CI. Nhưng test miền phải chạy được KHÔNG CẦN nó, và điều đó phải đúng ngay từ đầu.
        check(donA.ma().equals("DH-01"), "một hàm, một assert, không hạ tầng nào");

        // ---- 6. CÁI GÌ KHÔNG NÊN TEST ----
        //   - Getter/setter thuần: `check(d.ma().equals("DH-01"))` không kiểm được luật nào.
        //   - Thư viện và khung phần mềm: `HashMap` đã được test rồi.
        //   - Cài đặt riêng tư: nếu phải đổi `private` thành `public` để test, thì test đó
        //     đang bám vào cách làm (phần 1) — hãy test qua cửa chính.
        //   - Bộ nối hạ tầng bằng test miền: bộ nối cần test tích hợp riêng, ít và chậm.
        // Ngược lại, thứ ĐÁNG test nhất là những chỗ có `if` mang nghĩa nghiệp vụ: hạn
        // mức, chuyển trạng thái (bài 89), luật giá, luật chia tiền.
        check(DonHang.HAN_MUC == 50_000_000L, "luật nghiệp vụ có `if` -> đáng test");

        // ---- 7. ĐẶT TÊN TEST NHƯ MỘT CÂU NGHIỆP VỤ ----
        // Ở series này, "tên test" là chuỗi thông báo trong `check(...)`. Nguyên tắc giống
        // hệt với `@Test void ...`:
        //   TỆ : "test1", "testThemDong", "kiểm tra hàm themDong"
        //   TỐT: "đơn 49 triệu + 2 triệu -> vượt hạn mức 50 triệu"
        // Tên tốt nói ĐIỀU KIỆN và KẾT QUẢ MONG ĐỢI, bằng từ ngữ nghiệp vụ (bài 81). Khi
        // test đỏ lúc 2 giờ sáng, dòng chữ đó là toàn bộ thứ người trực có.
        check(true, "tên test là tài liệu duy nhất không bao giờ lỗi thời");

        System.out.println("OK");
    }
}
