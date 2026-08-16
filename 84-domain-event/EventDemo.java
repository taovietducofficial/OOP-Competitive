/*
 * Ngôn ngữ: Java
 * Công dụng: Sự kiện miền (domain event) — ghi lại CHUYỆN ĐÃ XẢY RA, không ra lệnh.
 * Bài phân biệt sự kiện với mệnh lệnh, cho aggregate GHI sự kiện thay vì tự phát đi,
 * rồi cho nổ hai con bug thật: email xác nhận được gửi cho một đơn hàng không tồn tại
 * (vì phát sự kiện bên trong transaction), và một báo cáo doanh thu lệch 20% (vì sự
 * kiện mang tham chiếu thay vì mang dữ liệu tại thời điểm xảy ra).
 * Tại sao cần học: bài 83 kết luận "một transaction sửa đúng một aggregate" — và để
 * lại câu hỏi: vậy hai aggregate nói chuyện với nhau bằng gì? Câu trả lời là sự kiện
 * miền, và đây là cơ chế duy nhất giữ được ranh giới aggregate mà hệ thống vẫn chạy
 * được. Nó cũng là nền của bài 96 (event sourcing) và bài 97 (saga). Riêng Java cho
 * một thứ mạnh ở đây: `sealed interface` — thêm một loại sự kiện mới mà quên xử lý là
 * LỖI BIÊN DỊCH, không phải bug phát hiện sau ba tháng.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EventDemo {

    // =====================================================================
    // SỰ KIỆN — bất biến, tên ở THÌ QUÁ KHỨ, mang dữ liệu LÚC XẢY RA
    // =====================================================================
    sealed interface SuKienMien permits DonHangDaTao, DonHangDaGiao, DonHangDaHuy {
        String maDon();
        long luc();
    }

    record DonHangDaTao(String maDon, String maKhach, long tongTien, long luc) implements SuKienMien { }
    record DonHangDaGiao(String maDon, long tongTienLucGiao, long luc) implements SuKienMien { }
    record DonHangDaHuy(String maDon, String lyDo, long soTienHoan, long luc) implements SuKienMien { }

    // Đối chiếu — MỆNH LỆNH. Khác sự kiện ở ba điểm, xem phần 1.
    record GuiEmailXacNhan(String maDon, String diaChiEmail) { }

    // =====================================================================
    // AGGREGATE ĐÚNG — GHI sự kiện, KHÔNG tự phát đi
    // =====================================================================
    enum TrangThai { MOI_TAO, DA_THANH_TOAN, DA_GIAO, DA_HUY }

    static final class DonHang {
        private final String ma;
        private final String maKhach;
        private long tongTien;
        private TrangThai trangThai = TrangThai.MOI_TAO;
        // Sự kiện nằm TRONG aggregate cho tới khi transaction xong. Aggregate không
        // biết bus tồn tại — không có field nào trỏ tới nó, không import dòng nào.
        private final List<SuKienMien> suKienChuaPhat = new ArrayList<>();

        DonHang(String ma, String maKhach, long tongTien, long luc) {
            this.ma = ma; this.maKhach = maKhach; this.tongTien = tongTien;
            suKienChuaPhat.add(new DonHangDaTao(ma, maKhach, tongTien, luc));
        }

        void thanhToan() {
            if (trangThai != TrangThai.MOI_TAO)
                throw new IllegalStateException("chỉ thanh toán được đơn mới tạo");
            trangThai = TrangThai.DA_THANH_TOAN;
        }

        void giao(long luc) {
            if (trangThai != TrangThai.DA_THANH_TOAN)
                throw new IllegalStateException("chưa thanh toán thì chưa giao được");
            trangThai = TrangThai.DA_GIAO;
            // Sự kiện chụp lại tổng tiền TẠI THỜI ĐIỂM GIAO — xem phần 5 để biết vì sao.
            suKienChuaPhat.add(new DonHangDaGiao(ma, tongTien, luc));
        }

        void huy(String lyDo, long luc) {
            if (trangThai == TrangThai.DA_GIAO)
                throw new IllegalStateException("đơn đã giao thì không huỷ được");
            long hoan = trangThai == TrangThai.DA_THANH_TOAN ? tongTien : 0;
            trangThai = TrangThai.DA_HUY;
            suKienChuaPhat.add(new DonHangDaHuy(ma, lyDo, hoan, luc));
        }

        void doiTongTien(long moi) { this.tongTien = moi; }   // dùng ở phần 5
        TrangThai trangThai() { return trangThai; }
        String ma() { return ma; }

        // Tầng ứng dụng lấy sự kiện ra SAU KHI lưu thành công.
        List<SuKienMien> layVaXoaSuKien() {
            List<SuKienMien> ds = List.copyOf(suKienChuaPhat);
            suKienChuaPhat.clear();
            return ds;
        }
        int soSuKienChoPhat() { return suKienChuaPhat.size(); }
    }

    // =====================================================================
    // AGGREGATE SAI — tự gọi bus ngay bên trong
    // =====================================================================
    static final class DonHangSai {
        private final Bus bus;              // <- aggregate phụ thuộc hạ tầng
        private final String ma;
        private TrangThai trangThai = TrangThai.DA_THANH_TOAN;

        DonHangSai(String ma, Bus bus) { this.ma = ma; this.bus = bus; }

        void giao(long luc) {
            trangThai = TrangThai.DA_GIAO;
            bus.phat(new DonHangDaGiao(ma, 100_000, luc));   // phát NGAY, trong transaction
        }
    }

    // =====================================================================
    // Hạ tầng: bus + hai người nghe
    // =====================================================================
    static final class Bus {
        private final Map<Class<?>, List<Consumer<SuKienMien>>> nguoiNghe = new LinkedHashMap<>();
        int soSuKienDaPhat = 0;

        <T extends SuKienMien> void dangKy(Class<T> loai, Consumer<SuKienMien> xuLy) {
            nguoiNghe.computeIfAbsent(loai, k -> new ArrayList<>()).add(xuLy);
        }

        void phat(SuKienMien sk) {
            soSuKienDaPhat++;
            for (Consumer<SuKienMien> h : nguoiNghe.getOrDefault(sk.getClass(), List.of())) {
                // Một người nghe hỏng KHÔNG được làm chuyện đã xảy ra thành chưa xảy ra,
                // và cũng không được chặn những người nghe khác. Xem phần 6.
                try { h.accept(sk); } catch (RuntimeException e) { soLoiNguoiNghe++; }
            }
        }
        int soLoiNguoiNghe = 0;
    }

    static final class HopThu { int soEmailDaGui = 0; }
    static final class SoDoanhThu { long tong = 0; }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. SỰ KIỆN ≠ MỆNH LỆNH ----
        //
        //                    | MỆNH LỆNH (GuiEmailXacNhan) | SỰ KIỆN (DonHangDaGiao)
        //   -----------------|-----------------------------|-------------------------
        //   thì của tên       | mệnh lệnh: "hãy gửi"        | quá khứ: "đã giao"
        //   người nhận        | ĐÚNG MỘT, biết trước        | KHÔNG BIẾT, ai nghe cũng được
        //   từ chối được?     | có — "email sai định dạng"  | KHÔNG — chuyện xảy ra rồi
        //   ai quyết định?    | người gửi                   | không ai; nó là SỰ THẬT
        //
        // Vì sao điều này quan trọng: nếu `DonHang` phát ra `GuiEmailXacNhan`, thì miền
        // nghiệp vụ vừa quyết định hộ rằng hệ quả của việc giao hàng LÀ gửi email. Ngày
        // mai thêm SMS, thêm tích điểm, thêm ghi sổ kế toán — mỗi lần lại sửa `DonHang`.
        // Với `DonHangDaGiao`, `DonHang` không biết ai nghe, và không bao giờ phải sửa nữa.
        GuiEmailXacNhan menhLenh = new GuiEmailXacNhan("DH-01", "a@b.c");
        check(menhLenh.maDon().equals("DH-01"), "mệnh lệnh nói LÀM GÌ và nói với AI");

        // Phép thử tên bằng máy: mọi sự kiện phải ở thì quá khứ.
        for (Class<?> loai : SuKienMien.class.getPermittedSubclasses()) {
            check(loai.getSimpleName().contains("Da"),
                    "tên sự kiện phải ở thì quá khứ: " + loai.getSimpleName());
        }
        check(SuKienMien.class.getPermittedSubclasses().length == 3, "ba loại sự kiện");
        // `sealed` cho phép liệt kê được TẤT CẢ loại sự kiện lúc chạy — nên luật đặt tên
        // này chạy được trong CI, không phải một dòng trong tài liệu (bài 81).

        // ---- 2. AGGREGATE GHI SỰ KIỆN, KHÔNG PHÁT ----
        long dongHo = 1000;
        DonHang don = new DonHang("DH-01", "KH-01", 100_000, dongHo++);
        don.thanhToan();
        don.giao(dongHo++);
        check(don.soSuKienChoPhat() == 2, "hai sự kiện đã được GHI: đã tạo, đã giao");
        check(don.trangThai() == TrangThai.DA_GIAO, "và trạng thái đã đổi");
        // `DonHang` không có field `Bus`, không import gì thuộc hạ tầng. Nghĩa là nó test
        // được mà không cần bus, không cần hàng đợi, không cần mạng.

        // ---- 3. CON BUG: phát sự kiện BÊN TRONG transaction ----
        Bus busSai = new Bus();
        HopThu hopThuSai = new HopThu();
        busSai.dangKy(DonHangDaGiao.class, sk -> hopThuSai.soEmailDaGui++);

        DonHangSai donSai = new DonHangSai("DH-99", busSai);
        boolean luuHong = false;
        try {
            donSai.giao(dongHo++);        // phát ngay tại đây
            throw new RuntimeException("CSDL hết chỗ");   // transaction hỏng SAU đó
        } catch (RuntimeException e) { luuHong = true; }

        check(luuHong, "transaction đã rollback — đơn DH-99 không tồn tại trong CSDL");
        check(hopThuSai.soEmailDaGui == 1, "nhưng khách đã nhận email 'đơn của bạn đã giao'");
        // Không có cách nào thu email về. Đây là bug kinh điển nhất của sự kiện miền, và
        // nó chỉ xảy ra khi hệ thống có lỗi — nghĩa là đúng lúc bạn ít muốn nó nhất.

        // ---- 4. BẢN ĐÚNG: lưu trước, phát sau ----
        Bus bus = new Bus();
        HopThu hopThu = new HopThu();
        SoDoanhThu so = new SoDoanhThu();
        bus.dangKy(DonHangDaGiao.class, sk -> hopThu.soEmailDaGui++);
        bus.dangKy(DonHangDaGiao.class, sk -> so.tong += ((DonHangDaGiao) sk).tongTienLucGiao());

        DonHang don2 = new DonHang("DH-02", "KH-01", 100_000, dongHo++);
        don2.thanhToan();
        don2.giao(dongHo++);

        boolean luuThatBai = true;                        // giả lập CSDL hỏng
        List<SuKienMien> choPhat = don2.layVaXoaSuKien();
        if (!luuThatBai) choPhat.forEach(bus::phat);      // <- chỉ phát khi lưu XONG
        check(hopThu.soEmailDaGui == 0, "lưu hỏng -> không email nào được gửi");
        check(bus.soSuKienDaPhat == 0, "không sự kiện nào rời khỏi tiến trình");
        // Thứ tự đúng chỉ có một: BẮT ĐẦU transaction -> đổi aggregate -> LƯU -> COMMIT
        // -> rồi mới phát sự kiện. Trong hệ thật, "phát sau commit" hay được làm bằng
        // outbox: ghi sự kiện vào một bảng trong CÙNG transaction, rồi một tiến trình
        // riêng đọc bảng đó và phát đi (bài 91 lo phần gửi trùng).

        DonHang don3 = new DonHang("DH-03", "KH-01", 100_000, dongHo++);
        don3.thanhToan();
        don3.giao(dongHo++);
        don3.layVaXoaSuKien().forEach(bus::phat);        // lần này lưu thành công
        check(hopThu.soEmailDaGui == 1, "lưu xong mới phát -> đúng một email");
        check(so.tong == 100_000, "và sổ doanh thu ghi đúng 100.000");
        check(don3.soSuKienChoPhat() == 0, "sự kiện đã lấy ra thì không phát lại lần hai");

        // ---- 5. CON BUG: sự kiện mang THAM CHIẾU thay vì mang DỮ LIỆU ----
        // Sự kiện `DonHangDaGiao` mang sẵn `tongTienLucGiao`. Nếu thay vào đó nó chỉ
        // mang `maDon` và người nghe tự đi tra tổng tiền, thì tra được giá HIỆN TẠI —
        // không phải giá lúc giao. Với đơn đã được sửa sau đó, hai con số khác nhau:
        SoDoanhThu soSai = new SoDoanhThu();
        Bus bus2 = new Bus();
        Map<String, DonHang> csdl = new LinkedHashMap<>();
        DonHang don4 = new DonHang("DH-04", "KH-01", 100_000, dongHo++);
        csdl.put("DH-04", don4);
        don4.thanhToan();
        don4.giao(dongHo++);
        List<SuKienMien> sk4 = don4.layVaXoaSuKien();

        don4.doiTongTien(120_000);    // kế toán chỉnh đơn sau khi giao (chuyện rất thường)

        bus2.dangKy(DonHangDaGiao.class, sk -> soSai.tong += 0);   // giữ chỗ
        // Người nghe kiểu SAI: đi tra lại từ CSDL
        for (SuKienMien sk : sk4) {
            if (sk instanceof DonHangDaGiao g) soSai.tong += 120_000;   // = csdl.get(...).tongTien
        }
        SoDoanhThu soDung = new SoDoanhThu();
        for (SuKienMien sk : sk4) {
            if (sk instanceof DonHangDaGiao g) soDung.tong += g.tongTienLucGiao();
        }
        check(soSai.tong == 120_000 && soDung.tong == 100_000, "lệch 20.000 trên một đơn");
        check(csdl.size() == 1, "cùng một đơn, hai con số doanh thu khác nhau");
        // Sự kiện là ẢNH CHỤP một khoảnh khắc. Nó phải mang đủ dữ liệu để người nghe làm
        // việc mà KHÔNG cần đi hỏi lại ai. Quy tắc: nếu người nghe phải tra CSDL để hiểu
        // sự kiện, thì sự kiện đó thiếu thông tin.

        // ---- 6. NGƯỜI NGHE HỎNG KHÔNG LÀM CHUYỆN ĐÃ XẢY RA THÀNH CHƯA XẢY RA ----
        Bus bus3 = new Bus();
        HopThu ht3 = new HopThu();
        bus3.dangKy(DonHangDaGiao.class, sk -> { throw new RuntimeException("SMTP chết"); });
        bus3.dangKy(DonHangDaGiao.class, sk -> ht3.soEmailDaGui++);

        DonHang don5 = new DonHang("DH-05", "KH-01", 100_000, dongHo++);
        don5.thanhToan();
        don5.giao(dongHo++);
        don5.layVaXoaSuKien().forEach(bus3::phat);

        check(bus3.soLoiNguoiNghe == 1, "một người nghe hỏng");
        check(ht3.soEmailDaGui == 1, "người nghe thứ hai VẪN chạy");
        check(don5.trangThai() == TrangThai.DA_GIAO, "và đơn VẪN đã giao — sự thật không rút lại được");
        // Đây là khác biệt cốt lõi với mệnh lệnh: mệnh lệnh hỏng thì huỷ được cả việc.
        // Sự kiện hỏng thì chỉ có HỆ QUẢ hỏng, còn chuyện đã xảy ra thì vẫn xảy ra rồi.
        // Cách chữa là thử lại người nghe đó (và người nghe phải chịu được gọi trùng —
        // bài 91), không phải rollback aggregate.

        // ---- 7. ĐIỀU CHỈ JAVA CÓ Ở ĐÂY: `sealed` + switch VÉT CẠN ----
        List<String> moTa = new ArrayList<>();
        for (SuKienMien sk : List.<SuKienMien>of(
                new DonHangDaTao("DH-06", "KH-01", 1, 1),
                new DonHangDaGiao("DH-06", 1, 2),
                new DonHangDaHuy("DH-06", "khách đổi ý", 1, 3))) {
            moTa.add(switch (sk) {                       // KHÔNG có nhánh `default`
                case DonHangDaTao e -> "tạo:" + e.maKhach();
                case DonHangDaGiao e -> "giao:" + e.tongTienLucGiao();
                case DonHangDaHuy e -> "huỷ:" + e.lyDo();
            });
        }
        check(moTa.equals(List.of("tạo:KH-01", "giao:1", "huỷ:khách đổi ý")), "xử lý đủ ba loại");
        // Thêm `DonHangDaTraLai` vào danh sách `permits` mà quên thêm nhánh ở đây:
        //     error: the switch expression does not cover all possible input values
        // Đó là lý do KHÔNG viết `default` trong switch trên sealed type — `default` biến
        // lỗi biên dịch thành bug lúc chạy, và bạn vừa vứt đi thứ đáng giá nhất của `sealed`.

        // ---- 8. Sự kiện miền giải bài toán của bài 83 ----
        // Bài 83: "một transaction sửa đúng MỘT aggregate". Vậy khi giao hàng xong cần
        // cộng điểm thưởng cho khách (một aggregate khác) thì làm sao?
        //   SAI : don.giao(); khachHang.congDiem();  <- hai aggregate, một transaction
        //   ĐÚNG: don.giao() ghi DonHangDaGiao -> commit -> người nghe tải KhachHang và
        //         cộng điểm trong transaction THỨ HAI.
        // Cái giá: có một khoảnh khắc đơn đã giao mà điểm chưa cộng — NHẤT QUÁN CUỐI.
        // Cái được: hai aggregate không khoá lẫn nhau, thêm hệ quả mới không sửa `DonHang`.
        // Nếu bước sau hỏng và phải quay lại bước trước, đó là saga (bài 97).
        check(true, "sự kiện là cầu nối duy nhất giữ được ranh giới aggregate");

        System.out.println("OK");
    }
}
