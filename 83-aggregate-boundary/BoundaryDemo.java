/*
 * Ngôn ngữ: Java
 * Công dụng: Quyết định RANH GIỚI của một aggregate — cụm object nào phải nằm chung,
 * cụm nào phải tách ra. Bài cho nổ hai lỗi đối xứng nhau: ranh giới QUÁ TO (đổi số
 * điện thoại khách hàng phải tải 501 object và hai người sửa hai đơn khác nhau lại
 * đụng nhau) và ranh giới QUÁ NHỎ (bất biến "tổng đơn ≤ hạn mức" bị hai phiên chạy
 * xen kẽ vượt qua mà không ai báo lỗi).
 * Tại sao cần học: bài 71 dạy "một cụm, một cửa" — nhưng không trả lời câu hỏi khó
 * hơn: cụm đó TO ĐẾN ĐÂU? Đây là quyết định thiết kế đắt nhất trong một hệ thống
 * nghiệp vụ, vì nó quyết định luôn ranh giới transaction, ranh giới khoá, và mức
 * đồng thời mà hệ thống chịu được. Và nó có một luật rõ ràng: ranh giới aggregate
 * nằm đúng ở nơi một BẤT BIẾN phải đúng NGAY LẬP TỨC — không sớm hơn, không muộn hơn.
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BoundaryDemo {

    // Định danh là kiểu riêng, không phải String trần. Xem phần 3 để biết vì sao.
    record MaKhachHang(String giaTri) { }
    record MaDonHang(String giaTri) { }

    // =====================================================================
    // AGGREGATE ĐÚNG KÍCH THƯỚC: DonHang
    // Bất biến của nó: TỔNG TIỀN CÁC DÒNG KHÔNG VƯỢT HẠN MỨC.
    // Bất biến đó dính tới các dòng hàng -> các dòng hàng nằm TRONG ranh giới.
    // =====================================================================
    record DongHang(String sanPham, long donGia, int soLuong) {
        long thanhTien() { return donGia * soLuong; }
    }

    static final class DonHang {
        static final long HAN_MUC = 50_000_000L;

        private final MaDonHang ma;
        private final MaKhachHang maKhachHang;   // THAM CHIẾU BẰNG ID, không giữ object
        private final List<DongHang> cacDong = new ArrayList<>();

        DonHang(MaDonHang ma, MaKhachHang maKhachHang) {
            this.ma = Objects.requireNonNull(ma);
            this.maKhachHang = Objects.requireNonNull(maKhachHang);
        }

        // BẤT BIẾN được kiểm NGAY TẠI ĐÂY, trong cùng một lời gọi ghi dữ liệu.
        // Đây chính là lý do `cacDong` phải nằm trong ranh giới: nếu nó ở ngoài,
        // câu lệnh `if` dưới không còn nghĩa lý gì (xem phần 2).
        void themDong(String sanPham, long donGia, int soLuong) {
            long sauKhiThem = tongTien() + donGia * soLuong;
            if (sauKhiThem > HAN_MUC)
                throw new IllegalStateException("đơn vượt hạn mức " + HAN_MUC);
            cacDong.add(new DongHang(sanPham, donGia, soLuong));
        }

        long tongTien() { return cacDong.stream().mapToLong(DongHang::thanhTien).sum(); }
        int soDong() { return cacDong.size(); }
        MaKhachHang maKhachHang() { return maKhachHang; }
        MaDonHang ma() { return ma; }

        // CỬA ĐÓNG: trả bản chỉ đọc. Không ai thêm dòng mà không đi qua themDong().
        List<DongHang> cacDong() { return List.copyOf(cacDong); }
    }

    // =====================================================================
    // AGGREGATE ĐÚNG KÍCH THƯỚC: KhachHang — KHÔNG chứa đơn hàng
    // =====================================================================
    static final class KhachHang {
        private final MaKhachHang ma;
        private String ten, dienThoai;
        private long phienBan = 0;               // dùng ở bài 92

        KhachHang(MaKhachHang ma, String ten, String dienThoai) {
            this.ma = ma; this.ten = ten; this.dienThoai = dienThoai;
        }
        void doiDienThoai(String moi) { this.dienThoai = moi; phienBan++; }
        MaKhachHang ma() { return ma; }
        long phienBan() { return phienBan; }
    }

    // =====================================================================
    // SAI 1 — RANH GIỚI QUÁ TO: khách hàng ôm luôn danh sách đơn
    // =====================================================================
    static final class KhachHangQuaTo {
        final MaKhachHang ma;
        String dienThoai;
        long phienBan = 0;
        final List<DonHang> cacDon = new ArrayList<>();   // <- một dòng, ba hậu quả

        KhachHangQuaTo(MaKhachHang ma, String dienThoai) { this.ma = ma; this.dienThoai = dienThoai; }
        void doiDienThoai(String moi) { this.dienThoai = moi; phienBan++; }
        void themDon(DonHang d) { cacDon.add(d); phienBan++; }
    }

    // Kho giả có ĐẾM số object phải tải — để "quá to" thành con số, không thành cảm giác.
    static final class KhoDem {
        int soObjectDaTai = 0;
        KhachHangQuaTo taiQuaTo(KhachHangQuaTo kh) {
            soObjectDaTai += 1 + kh.cacDon.size();   // aggregate phải tải TRỌN VẸN
            return kh;
        }
        KhachHang tai(KhachHang kh) { soObjectDaTai += 1; return kh; }
    }

    // =====================================================================
    // SAI 2 — RANH GIỚI QUÁ NHỎ: dòng hàng thành aggregate riêng
    // =====================================================================
    static final class KhoDongRoi {
        private final List<DongHang> dong = new ArrayList<>();
        long tong() { return dong.stream().mapToLong(DongHang::thanhTien).sum(); }
        void them(DongHang d) { dong.add(d); }     // không có chỗ nào kiểm hạn mức được
        int soDong() { return dong.size(); }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        MaKhachHang maKh = new MaKhachHang("KH-01");

        // ---- 1. PHÉP THỬ RANH GIỚI ----
        // Câu hỏi duy nhất cần hỏi:
        //   "Nếu hai thứ này được sửa trong HAI transaction khác nhau,
        //    có luật nghiệp vụ nào bị phá không?"
        //   CÓ    -> cùng một aggregate.
        //   KHÔNG -> tách ra, tham chiếu bằng id.
        //
        //   đơn hàng <-> dòng hàng của nó   : CÓ (tổng ≤ hạn mức) -> chung
        //   đơn hàng <-> khách hàng         : KHÔNG                -> tách
        DonHang don = new DonHang(new MaDonHang("DH-01"), maKh);
        don.themDong("laptop", 20_000_000L, 2);
        check(don.tongTien() == 40_000_000L, "40 triệu, còn trong hạn mức");

        boolean chan = false;
        try { don.themDong("màn hình", 8_000_000L, 2); }
        catch (IllegalStateException e) { chan = true; }
        check(chan, "thêm 16 triệu nữa thì vượt 50 triệu -> bị chặn NGAY");
        check(don.tongTien() == 40_000_000L, "và dữ liệu không hề bị sửa dở dang");

        // ---- 2. SAI: RANH GIỚI QUÁ NHỎ -> bất biến không giữ được ----
        // Nếu `DongHang` là aggregate riêng, câu kiểm hạn mức phải nằm ở tầng ứng dụng:
        //     tong = kho.tong();  if (tong + moi <= HAN_MUC) kho.them(...)
        // Hai phiên chạy xen kẽ là đủ để phá:
        KhoDongRoi kho = new KhoDongRoi();
        kho.them(new DongHang("laptop", 20_000_000L, 2));   // đang có 40 triệu

        long docBoiA = kho.tong();        // phiên A đọc:  40.000.000
        long docBoiB = kho.tong();        // phiên B đọc:  40.000.000  <- cùng lúc
        if (docBoiA + 8_000_000L <= DonHang.HAN_MUC) kho.them(new DongHang("chuột", 8_000_000L, 1));
        if (docBoiB + 8_000_000L <= DonHang.HAN_MUC) kho.them(new DongHang("bàn phím", 8_000_000L, 1));

        check(kho.tong() == 56_000_000L, "tổng thành 56 triệu");
        check(kho.tong() > DonHang.HAN_MUC, "VƯỢT hạn mức — và cả hai phiên đều 'kiểm tra rồi'");
        // Không ngoại lệ, không cảnh báo. Mỗi phiên đều đọc đúng, kiểm đúng, ghi đúng.
        // Cái sai nằm ở RANH GIỚI: hai thứ cùng chịu một bất biến mà lại được sửa trong
        // hai transaction rời nhau. Không có `if` nào cứu được điều đó.
        //
        // Với ranh giới đúng, tình huống y hệt bị chặn, vì cả hai lệnh đều đi qua CÙNG
        // MỘT object `DonHang` và bất biến được kiểm lại ở lần ghi thứ hai:
        DonHang donDung = new DonHang(new MaDonHang("DH-02"), maKh);
        donDung.themDong("laptop", 20_000_000L, 2);
        donDung.themDong("chuột", 8_000_000L, 1);
        chan = false;
        try { donDung.themDong("bàn phím", 8_000_000L, 1); }
        catch (IllegalStateException e) { chan = true; }
        check(chan, "lệnh thứ hai bị chặn vì bất biến nằm TRONG ranh giới");
        check(donDung.tongTien() == 48_000_000L, "và tổng dừng đúng chỗ hợp lệ");

        // ---- 3. THAM CHIẾU AGGREGATE KHÁC BẰNG ID, KHÔNG BẰNG OBJECT ----
        check(don.maKhachHang().equals(maKh), "đơn hàng biết MÃ khách hàng...");
        // ...và KHÔNG có cách nào đi từ đơn hàng tới object KhachHang để sửa nó.
        // Đây không phải kỷ luật, đây là kiểu dữ liệu: `DonHang` không có field
        // `KhachHang`, nên dòng `don.khachHang().doiDienThoai(...)` KHÔNG BIÊN DỊCH ĐƯỢC.
        //
        // Và vì `MaKhachHang` là kiểu riêng chứ không phải `String`, dòng này cũng vậy:
        //     new DonHang(new MaDonHang("DH-03"), new MaDonHang("DH-01"));
        //     error: incompatible types: MaDonHang cannot be converted to MaKhachHang
        // Dùng `String` trần thì lỗi đó chạy được và sinh dữ liệu rác.
        check(!maKh.equals(new MaDonHang("KH-01")), "hai loại mã không bao giờ bằng nhau");

        // ---- 4. SAI: RANH GIỚI QUÁ TO -> tải 501 object để đổi một số điện thoại ----
        KhachHangQuaTo khTo = new KhachHangQuaTo(maKh, "0900000000");
        for (int i = 0; i < 500; i++) khTo.themDon(new DonHang(new MaDonHang("DH-" + i), maKh));

        KhoDem khoDem = new KhoDem();
        khoDem.taiQuaTo(khTo).doiDienThoai("0911111111");
        check(khoDem.soObjectDaTai == 501, "đổi MỘT số điện thoại: tải 501 object");
        // Aggregate phải tải trọn vẹn thì bất biến của nó mới kiểm được — đó là luật,
        // không phải chuyện tối ưu. Nên ranh giới to = mọi thao tác đều đắt.

        KhoDem khoDem2 = new KhoDem();
        khoDem2.tai(new KhachHang(maKh, "Nguyễn Văn A", "0900000000")).doiDienThoai("0911111111");
        check(khoDem2.soObjectDaTai == 1, "ranh giới đúng: tải đúng 1 object");
        check(500 == khoDem.soObjectDaTai - khoDem2.soObjectDaTai, "chênh 500 lần tải vô ích");

        // ---- 5. Hậu quả thứ hai của ranh giới quá to: ĐỤNG ĐỘ GIẢ ----
        long truoc = khTo.phienBan;
        khTo.themDon(new DonHang(new MaDonHang("DH-A"), maKh));   // người dùng 1 tạo đơn A
        khTo.themDon(new DonHang(new MaDonHang("DH-B"), maKh));   // người dùng 2 tạo đơn B
        check(khTo.phienBan == truoc + 2, "hai đơn KHÁC NHAU cùng làm tăng phiên bản KHÁCH HÀNG");
        // Với khoá lạc quan (bài 92), hai người tạo hai đơn hoàn toàn không liên quan sẽ
        // báo lỗi "dữ liệu đã bị người khác sửa". Đụng độ này là GIẢ — nó do ranh giới
        // sai sinh ra, không do nghiệp vụ. Aggregate càng to, tỉ lệ đụng độ giả càng cao.
        //
        // Với ranh giới đúng, tạo đơn không đụng gì tới khách hàng:
        KhachHang khDung = new KhachHang(maKh, "Nguyễn Văn A", "0900000000");
        long pbTruoc = khDung.phienBan();
        new DonHang(new MaDonHang("DH-C"), maKh);
        new DonHang(new MaDonHang("DH-D"), maKh);
        check(khDung.phienBan() == pbTruoc, "tạo hai đơn: khách hàng không đổi phiên bản");

        // ---- 6. LUẬT "MỘT TRANSACTION = MỘT AGGREGATE" ----
        // Đây là hệ quả trực tiếp của phần 2 và phần 5, không phải một luật riêng.
        // Nếu một use case phải sửa hai aggregate cùng lúc, đó là dấu hiệu MỘT trong hai:
        //   (a) ranh giới vẽ sai   -> vẽ lại;
        //   (b) hai thứ đó thật sự không cần đúng đồng thời -> chấp nhận NHẤT QUÁN CUỐI:
        //       aggregate thứ nhất phát ra sự kiện, aggregate thứ hai xử lý sau (bài 84),
        //       và nếu bước sau hỏng thì có hành động bù trừ (bài 97).
        //
        // Phép đếm: một use case chạm bao nhiêu ROOT?
        int soRootChamToi = 1;   // "thêm dòng vào đơn" -> chỉ DonHang
        check(soRootChamToi == 1, "use case lành mạnh chạm đúng một aggregate root");

        // ---- 7. Bất biến nào KHÔNG được kéo vào ranh giới ----
        // Cám dỗ lớn nhất: "tổng nợ của khách hàng không quá 200 triệu" — nghe như một
        // bất biến, và nó kéo TOÀN BỘ đơn hàng vào trong KhachHang (phần 4).
        //
        // Câu hỏi phải hỏi tiếp: nếu luật đó bị vượt trong 5 giây rồi được sửa, công ty
        // mất gì? Với hạn mức nợ, câu trả lời thường là "không mất gì, gọi điện đòi là
        // xong". Với tổng tiền một đơn, câu trả lời là "xuất hoá đơn sai, phải huỷ".
        //
        //   Vượt trong chốc lát mà KHÔNG chấp nhận được -> bất biến thật -> chung aggregate
        //   Vượt trong chốc lát mà chấp nhận được       -> luật nghiệp vụ -> kiểm sau, tách ra
        //
        // Rất nhiều "bất biến" hoá ra thuộc loại thứ hai. Hỏi người làm nghiệp vụ, đừng đoán.
        check(DonHang.HAN_MUC == 50_000_000L, "hạn mức MỘT ĐƠN: không được vượt dù một giây");

        // ---- 8. Bốn quy tắc rút gọn ----
        //   1. Ranh giới nằm ở nơi một bất biến phải đúng NGAY LẬP TỨC.
        //   2. Tham chiếu aggregate khác BẰNG ID, không giữ object.
        //   3. Một transaction sửa đúng MỘT aggregate.
        //   4. Nghi ngờ thì làm NHỎ. Aggregate nhỏ mà thiếu bất biến thì gộp lại được;
        //      aggregate to thì mọi thao tác đã đắt sẵn và tách ra rất khó.
        check(don.cacDong().size() == 1 && don.soDong() == 1, "và cửa vẫn đóng: bản chỉ đọc");
        boolean cuaDong = false;
        try { don.cacDong().add(new DongHang("lén", 1, 1)); }
        catch (UnsupportedOperationException e) { cuaDong = true; }
        check(cuaDong, "không ai thêm dòng mà không đi qua themDong()");

        System.out.println("OK");
    }
}
