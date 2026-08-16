/*
 * Ngôn ngữ: Java
 * Công dụng: Policy object — luật nghiệp vụ đổi theo NGỮ CẢNH (quốc gia, hạng khách)
 * mà code gọi không đổi một chữ. Bài cho nổ ba con bug: chuỗi if-else chép ở ba nơi rồi
 * một nơi quên mất nước Đức nên thu thuế 0%; thiếu chính sách thì âm thầm rơi về 0 thay
 * vì báo lỗi; và bùng nổ tổ hợp 4×3 = 12 lớp khi hai trục luật bị trộn làm một.
 * Tại sao cần học: bài 87 ghép luật LÚC VIẾT CODE. Bài này chọn luật LÚC CHẠY — đó là
 * toàn bộ khác biệt, và nó là thứ quyết định một hệ thống có mở rộng sang thị trường
 * mới trong một ngày hay trong một quý. Riêng Java cho một công cụ mạnh ở đây: `enum`
 * liệt kê được TẤT CẢ ngữ cảnh, nên "đã đủ chính sách cho mọi quốc gia chưa" là một
 * bài test chạy được, không phải một câu hỏi lúc review.
 */
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PolicyDemo {

    enum QuocGia { VN, JP, US, DE }
    enum HangKhach { THUONG, BAC, VANG }

    // =====================================================================
    // POLICY — một luật, được CHỌN lúc chạy
    // =====================================================================
    interface ChinhSachThue {
        long tinhThue(long tienHang);
        String moTa();                       // có tên, đọc lên thành câu (bài 81)
    }

    record ThueTheoTiLe(int phanTram, String ten) implements ChinhSachThue {
        @Override public long tinhThue(long tienHang) { return tienHang * phanTram / 100; }
        @Override public String moTa() { return ten + " " + phanTram + "%"; }
    }
    record MienThue(String ten) implements ChinhSachThue {
        @Override public long tinhThue(long tienHang) { return 0; }
        @Override public String moTa() { return ten; }
    }

    // Bảng tra: THÊM MỘT THỊ TRƯỜNG = THÊM MỘT DÒNG, không sửa dòng nào.
    static final Map<QuocGia, ChinhSachThue> BANG_THUE = new EnumMap<>(QuocGia.class);
    static {
        BANG_THUE.put(QuocGia.VN, new ThueTheoTiLe(10, "VAT Việt Nam"));
        BANG_THUE.put(QuocGia.JP, new ThueTheoTiLe(8, "thuế tiêu dùng Nhật"));
        BANG_THUE.put(QuocGia.US, new MienThue("không thuế liên bang"));
        BANG_THUE.put(QuocGia.DE, new ThueTheoTiLe(19, "USt Đức"));
    }

    // Tra chính sách: THIẾU thì NỔ, không âm thầm về 0. Xem phần 3.
    static ChinhSachThue chinhSachCho(QuocGia q) {
        ChinhSachThue cs = BANG_THUE.get(q);
        if (cs == null) throw new IllegalStateException("chưa có chính sách thuế cho " + q);
        return cs;
    }

    // =====================================================================
    // TRỤC THỨ HAI — giảm giá theo hạng khách, ĐỘC LẬP với thuế
    // =====================================================================
    interface ChinhSachGiamGia { long tinhGiam(long tienHang); }

    static final Map<HangKhach, ChinhSachGiamGia> BANG_GIAM = new EnumMap<>(HangKhach.class);
    static {
        BANG_GIAM.put(HangKhach.THUONG, t -> 0);
        BANG_GIAM.put(HangKhach.BAC, t -> t * 5 / 100);
        BANG_GIAM.put(HangKhach.VANG, t -> t * 10 / 100);
    }

    /** Tầng ứng dụng chỉ GHÉP hai trục lại. Nó không biết nước nào bao nhiêu phần trăm. */
    static long tinhTongPhaiTra(long tienHang, QuocGia quocGia, HangKhach hang) {
        long giam = BANG_GIAM.get(hang).tinhGiam(tienHang);
        long sauGiam = tienHang - giam;
        return sauGiam + chinhSachCho(quocGia).tinhThue(sauGiam);
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: chuỗi if-else chép ở ba nơi ----
        // Ba nơi cùng cần thuế: màn hình thanh toán, sinh hoá đơn, báo cáo doanh thu.
        // Nước Đức được thêm vào tháng trước, và chỉ hai trong ba nơi được cập nhật.
        java.util.function.BiFunction<QuocGia, Long, Long> thanhToan = (q, t) ->
                q == QuocGia.VN ? t * 10 / 100 : q == QuocGia.JP ? t * 8 / 100
                        : q == QuocGia.DE ? t * 19 / 100 : 0L;
        java.util.function.BiFunction<QuocGia, Long, Long> hoaDon = (q, t) ->
                q == QuocGia.VN ? t * 10 / 100 : q == QuocGia.JP ? t * 8 / 100
                        : q == QuocGia.DE ? t * 19 / 100 : 0L;
        java.util.function.BiFunction<QuocGia, Long, Long> baoCao = (q, t) ->
                q == QuocGia.VN ? t * 10 / 100 : q == QuocGia.JP ? t * 8 / 100 : 0L;
        //                                                        ^ QUÊN nước Đức

        long tien = 100_000_000L;
        check(thanhToan.apply(QuocGia.DE, tien) == 19_000_000L, "thanh toán thu đúng 19%");
        check(hoaDon.apply(QuocGia.DE, tien) == 19_000_000L, "hoá đơn ghi đúng 19%");
        check(baoCao.apply(QuocGia.DE, tien) == 0L, "báo cáo ghi 0% — lệch 19 triệu mỗi đơn");
        check(thanhToan.apply(QuocGia.DE, tien) - baoCao.apply(QuocGia.DE, tien) == 19_000_000L,
                "sổ sách và tiền thật không khớp nhau");
        // Không ngoại lệ, không cảnh báo. Nhánh `else` trả 0 nuốt trọn lỗi — và với thuế
        // thì 0 là một con số hoàn toàn hợp lệ (nước Mỹ đúng là 0%), nên không ai nghi ngờ.

        // ---- 2. POLICY: một nguồn sự thật, ba nơi cùng dùng ----
        check(chinhSachCho(QuocGia.DE).tinhThue(tien) == 19_000_000L, "một bảng, một câu trả lời");
        check(chinhSachCho(QuocGia.US).tinhThue(tien) == 0L, "Mỹ 0% — nhưng là 0% CÓ TÊN");
        check(chinhSachCho(QuocGia.US).moTa().equals("không thuế liên bang"),
                "và tên đó phân biệt được với 'chưa cấu hình'");
        // Đây là điểm tinh tế nhất của bài: `MienThue` và "thiếu cấu hình" đều cho ra 0,
        // nhưng một cái là QUYẾT ĐỊNH NGHIỆP VỤ còn cái kia là LỖI. Chuỗi if-else không
        // phân biệt được hai thứ đó; bảng chính sách thì có.

        // ---- 3. ĐIỀU CHỈ `enum` LÀM ĐƯỢC: KIỂM TRA ĐỦ CHÍNH SÁCH BẰNG MÁY ----
        for (QuocGia q : QuocGia.values()) {
            check(BANG_THUE.containsKey(q), "thiếu chính sách thuế cho " + q);
        }
        check(BANG_THUE.size() == QuocGia.values().length, "đủ 4/4 quốc gia");
        // Bốn dòng trên là một bài test chạy trong CI. Thêm `QuocGia.FR` vào enum mà quên
        // thêm dòng vào bảng -> test đỏ NGAY, trước khi có đơn hàng nào từ Pháp.
        //
        // Với chuỗi if-else thì không có cách nào viết bài test tương đương, vì không có
        // gì để mà liệt kê — nhánh `else` luôn "xử lý được" mọi giá trị.

        // ---- 4. THIẾU CHÍNH SÁCH PHẢI NỔ, KHÔNG ĐƯỢC ÂM THẦM VỀ 0 ----
        Map<QuocGia, ChinhSachThue> bangThieu = new LinkedHashMap<>(BANG_THUE);
        bangThieu.remove(QuocGia.DE);
        long thueImLang = bangThieu.getOrDefault(QuocGia.DE, new MienThue("mặc định")).tinhThue(tien);
        check(thueImLang == 0L, "getOrDefault: thiếu chính sách -> 0 đồng, không ai biết");

        boolean noLen = false;
        try { chinhSachCho(null); } catch (IllegalStateException e) { noLen = true; }
        check(noLen, "tra chính sách phải NỔ khi thiếu — 19 triệu không được im lặng biến mất");
        // `getOrDefault(..., mặc định)` là một trong những dòng nguy hiểm nhất trong mã
        // nghiệp vụ. Null Object (bài 64) chỉ đúng khi "không có gì" là hành vi HỢP LỆ.
        // Với thuế thì không: thiếu chính sách là tin xấu, và tin xấu phải kêu to.

        // ---- 5. HAI TRỤC ĐỘC LẬP: 4 + 3, KHÔNG PHẢI 4 × 3 ----
        // Cám dỗ: một lớp cho mỗi tổ hợp — `ThueVnKhachVang`, `ThueDeKhachBac`, ...
        int soLopNeuTronTruc = QuocGia.values().length * HangKhach.values().length;
        int soLopKhiTachTruc = QuocGia.values().length + HangKhach.values().length;
        check(soLopNeuTronTruc == 12 && soLopKhiTachTruc == 7, "12 lớp so với 7");
        // Và con số đó nổ theo cấp số nhân: thêm trục thứ ba (kênh bán: online/đại lý/
        // cửa hàng) thì 12 -> 36, còn 7 -> 10. Quy tắc: mỗi TRỤC BIẾN THIÊN là một bảng
        // chính sách riêng, và tầng ứng dụng ghép chúng lại (bài 63 · decorator là một
        // cách ghép khác cho cùng bài toán này).

        check(tinhTongPhaiTra(100_000L, QuocGia.VN, HangKhach.THUONG) == 110_000L,
                "VN thường: 100.000 + 10% = 110.000");
        check(tinhTongPhaiTra(100_000L, QuocGia.VN, HangKhach.VANG) == 99_000L,
                "VN vàng: giảm 10% còn 90.000, +10% thuế = 99.000");
        check(tinhTongPhaiTra(100_000L, QuocGia.US, HangKhach.VANG) == 90_000L,
                "Mỹ vàng: giảm 10%, không thuế");
        // Chú ý THỨ TỰ: giảm giá TRƯỚC, thuế SAU — thuế tính trên số tiền thực trả. Đây
        // là một luật nghiệp vụ, và nó nằm ở tầng ứng dụng vì nó nói về QUAN HỆ giữa hai
        // chính sách chứ không thuộc chính sách nào. Đảo thứ tự là sai luật thuế ở hầu
        // hết các nước — và đó là loại bug không ai phát hiện cho tới lúc bị kiểm toán.

        // ---- 6. THÊM THỊ TRƯỜNG MỚI: ĐO SỐ CHỖ PHẢI SỬA ----
        // Với if-else: sửa 3 nhánh (thanh toán, hoá đơn, báo cáo) — và bài học ở phần 1
        // là khả năng quên một chỗ không phải giả thuyết.
        // Với policy: thêm MỘT dòng vào `BANG_THUE`, và bài test ở phần 3 canh giúp.
        Map<QuocGia, ChinhSachThue> bangMoRong = new LinkedHashMap<>(BANG_THUE);
        int truoc = bangMoRong.size();
        bangMoRong.put(QuocGia.US, new ThueTheoTiLe(7, "thuế bang California"));  // đổi luật Mỹ
        check(bangMoRong.size() == truoc, "sửa luật MỘT nước: đúng một dòng, không đụng nước khác");
        check(bangMoRong.get(QuocGia.VN).tinhThue(tien) == 10_000_000L, "Việt Nam không hề hấn gì");

        // ---- 7. POLICY vs STRATEGY vs SPECIFICATION ----
        //
        //   Mẫu           | Trả lời câu hỏi          | Chọn lúc nào  | Ví dụ ở đây
        //   --------------|--------------------------|---------------|------------------
        //   Specification | "có thoả mãn không?"     | ghép lúc viết | duocVayTinChap (87)
        //   Policy        | "luật ở ngữ cảnh này?"   | tra LÚC CHẠY  | BANG_THUE
        //   Strategy      | "làm bằng cách nào?"     | tra lúc chạy  | thuật toán nén/sắp xếp
        //
        // Policy và Strategy có HÌNH DẠNG giống hệt nhau — cùng là một interface với
        // nhiều cài đặt và một chỗ chọn. Khác nhau ở Ý ĐỊNH: strategy đổi CÁCH LÀM cho
        // cùng một kết quả (sắp xếp nhanh hay chậm, kết quả như nhau); policy đổi CHÍNH
        // KẾT QUẢ vì nghiệp vụ ở ngữ cảnh đó khác. Nhầm lẫn hai thứ không gây bug, nhưng
        // gọi đúng tên giúp người sau biết được phép đổi cái gì mà không phá gì.
        check(chinhSachCho(QuocGia.VN).tinhThue(1000) != chinhSachCho(QuocGia.JP).tinhThue(1000),
                "policy: hai ngữ cảnh, hai KẾT QUẢ khác nhau — và cả hai đều đúng");

        // ---- 8. RANH GIỚI: khoá tra chính sách phải là KIỂU CỦA MIỀN ----
        // `Map<String, ChinhSachThue>` với khoá `"VN"`, `"vn"`, `"VNM"` là cách chắc chắn
        // nhất để có một bug không ai tìm ra. Dùng `enum` (hoặc value object) thì:
        //   - gõ sai là lỗi biên dịch;
        //   - liệt kê được hết -> viết được bài test ở phần 3;
        //   - IDE tìm được mọi nơi dùng.
        // Chuỗi chỉ nên xuất hiện ở BIÊN (đọc file cấu hình, nhận request), và được đổi
        // sang enum ngay tại đó (bài 76 · fail fast, bài 78 · DTO mapping).
        check(QuocGia.valueOf("DE") == QuocGia.DE, "biên đổi chuỗi -> enum một lần, ngay lúc vào");

        System.out.println("OK");
    }
}
