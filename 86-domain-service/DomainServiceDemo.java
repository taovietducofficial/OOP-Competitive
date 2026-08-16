/*
 * Ngôn ngữ: Java
 * Công dụng: Domain service — chỗ đặt hành vi KHÔNG thuộc về entity nào. Bài phân biệt
 * ba loại "service" hay bị gộp làm một (miền / ứng dụng / hạ tầng), rồi cho nổ hai lỗi
 * đối xứng: mô hình THIẾU MÁU (entity chỉ có getter/setter, mọi luật nằm ngoài nên bất
 * biến bị lách dễ dàng) và ngược lại — nhét hành vi liên-aggregate vào một entity, khiến
 * nó phải sửa aggregate khác.
 * Tại sao cần học: "domain service" là khái niệm bị lạm dụng nhất trong DDD. Đặt tên một
 * lớp là `XxxService` rồi đổ mọi thứ vào đó là con đường ngắn nhất tới mô hình thiếu máu
 * — nơi entity thành cấu trúc dữ liệu và toàn bộ nghiệp vụ nằm trong các hàm tĩnh. Bài
 * này đưa ra ba câu hỏi lọc, và một phép đếm để biết mình đã rơi vào bẫy chưa.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DomainServiceDemo {

    // =====================================================================
    // SAI 1 — MÔ HÌNH THIẾU MÁU: entity chỉ là túi dữ liệu
    // =====================================================================
    static final class TaiKhoanThieuMau {
        private String ma;
        private long soDu;
        TaiKhoanThieuMau(String ma, long soDu) { this.ma = ma; this.soDu = soDu; }
        String getMa() { return ma; }
        long getSoDu() { return soDu; }
        void setSoDu(long soDu) { this.soDu = soDu; }   // <- cửa mở toang
    }

    static final class TaiKhoanService {
        // Luật "không được âm" nằm ở ĐÂY, nên nó chỉ có hiệu lực với ai đi qua đây.
        static void rut(TaiKhoanThieuMau tk, long tien) {
            if (tk.getSoDu() < tien) throw new IllegalStateException("không đủ số dư");
            tk.setSoDu(tk.getSoDu() - tien);
        }
    }

    // =====================================================================
    // ĐÚNG — entity giữ luật của chính nó
    // =====================================================================
    static final class TaiKhoan {
        private final String ma;
        private long soDu;
        TaiKhoan(String ma, long soDu) {
            if (soDu < 0) throw new IllegalArgumentException("số dư ban đầu không âm");
            this.ma = ma; this.soDu = soDu;
        }
        // Luật nằm TRONG entity -> không có đường vòng nào.
        void rut(long tien) {
            if (tien <= 0) throw new IllegalArgumentException("số tiền rút phải dương");
            if (soDu < tien) throw new IllegalStateException("không đủ số dư");
            soDu -= tien;
        }
        void nap(long tien) {
            if (tien <= 0) throw new IllegalArgumentException("số tiền nạp phải dương");
            soDu += tien;
        }
        String ma() { return ma; }
        long soDu() { return soDu; }
    }

    // =====================================================================
    // DOMAIN SERVICE — hành vi thuộc về MIỀN nhưng không thuộc về entity nào
    // =====================================================================
    record BienLaiChuyenTien(String tuTaiKhoan, String denTaiKhoan, long soTien, long phi) { }

    /**
     * Chuyển tiền dính tới HAI tài khoản ngang nhau. Đặt nó vào `TaiKhoan.chuyenToi(khac)`
     * là bắt một aggregate sửa một aggregate khác — đúng thứ bài 83 cấm. Đặt nó vào tầng
     * ứng dụng thì luật tính phí (một luật NGHIỆP VỤ) rời khỏi miền.
     * Nên nó là domain service: thuộc miền, KHÔNG có trạng thái, KHÔNG chạm I/O.
     */
    static final class DichVuChuyenTien {
        // KHÔNG có field. Không repository, không đồng hồ, không cấu hình. Mọi thứ cần
        // biết đều đi vào qua tham số — đó là điều làm nó test được không cần gì cả.
        BienLaiChuyenTien chuyen(TaiKhoan tu, TaiKhoan den, long soTien, BieuPhi bieuPhi) {
            if (tu.ma().equals(den.ma()))
                throw new IllegalArgumentException("không chuyển cho chính mình");
            long phi = bieuPhi.tinhPhi(soTien);
            tu.rut(soTien + phi);      // mỗi entity vẫn tự giữ luật của nó
            den.nap(soTien);
            return new BienLaiChuyenTien(tu.ma(), den.ma(), soTien, phi);
        }
    }

    // Bảng phí là VALUE OBJECT truyền vào, không phải repository được tiêm.
    record BieuPhi(long nguong, long phiThap, long phiCao) {
        long tinhPhi(long soTien) { return soTien <= nguong ? phiThap : phiCao; }
    }

    // =====================================================================
    // Ba loại "service" — thứ hay bị gộp làm một
    // =====================================================================
    interface KhoTaiKhoan { TaiKhoan tim(String ma); void luu(TaiKhoan tk); }
    interface GuiThongBao { void gui(String maTaiKhoan, String noiDung); }   // HẠ TẦNG

    /** Ứng dụng: điều phối. Mở/đóng transaction, tải, gọi miền, lưu, phát thông báo. */
    static final class UngDungChuyenTien {
        private final KhoTaiKhoan kho;
        private final GuiThongBao thongBao;
        private final DichVuChuyenTien mien = new DichVuChuyenTien();
        int soLanGoiMien = 0;

        UngDungChuyenTien(KhoTaiKhoan kho, GuiThongBao thongBao) {
            this.kho = kho; this.thongBao = thongBao;
        }

        BienLaiChuyenTien thucHien(String maTu, String maDen, long soTien) {
            TaiKhoan tu = kho.tim(maTu);
            TaiKhoan den = kho.tim(maDen);
            soLanGoiMien++;
            BienLaiChuyenTien bl = mien.chuyen(tu, den, soTien, new BieuPhi(1_000_000, 1_000, 5_000));
            kho.luu(tu);
            kho.luu(den);
            thongBao.gui(maTu, "đã chuyển " + soTien);
            return bl;
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. MÔ HÌNH THIẾU MÁU: luật ở ngoài thì luật bị lách ----
        TaiKhoanThieuMau tm = new TaiKhoanThieuMau("TK-01", 100_000);
        boolean chan = false;
        try { TaiKhoanService.rut(tm, 200_000); } catch (IllegalStateException e) { chan = true; }
        check(chan, "đi qua service thì luật có hiệu lực...");

        tm.setSoDu(-500_000);      // ...và đây là đường vòng, mở sẵn cho tất cả mọi người
        check(tm.getSoDu() == -500_000, "số dư ÂM, không ai chặn, không ngoại lệ");
        // Đây là dấu hiệu nhận biết mô hình thiếu máu: mọi bất biến đều nằm ngoài entity,
        // nên chúng chỉ đúng với đoạn code nhớ gọi đúng chỗ. Một `setSoDu` là đủ để
        // toàn bộ luật nghiệp vụ trở thành lời khuyên.

        TaiKhoan tk = new TaiKhoan("TK-01", 100_000);
        chan = false;
        try { tk.rut(200_000); } catch (IllegalStateException e) { chan = true; }
        check(chan && tk.soDu() == 100_000, "luật nằm TRONG entity -> không có đường vòng");
        // Và không tồn tại `setSoDu` để mà lách: cách DUY NHẤT đổi số dư là `rut`/`nap`.

        // ---- 2. PHÉP ĐẾM: bạn đã rơi vào mô hình thiếu máu chưa? ----
        long pmThieuMau = java.util.Arrays.stream(TaiKhoanThieuMau.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("get") || m.getName().startsWith("set")).count();
        long tongTM = TaiKhoanThieuMau.class.getDeclaredMethods().length;
        check(pmThieuMau == tongTM, "TaiKhoanThieuMau: 100% phương thức là getter/setter");

        long pmDung = java.util.Arrays.stream(TaiKhoan.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("get") || m.getName().startsWith("set")).count();
        check(pmDung == 0, "TaiKhoan: 0 getter/setter — mọi phương thức là một HÀNH VI");
        // Phép đo chạy được: tỉ lệ getter/setter trên tổng số phương thức của các lớp
        // miền. Gần 100% nghĩa là miền của bạn là một lược đồ CSDL đội lốt object.

        // ---- 3. KHI NÀO THÌ THẬT SỰ CẦN DOMAIN SERVICE ----
        // Ba câu hỏi, phải trả lời CÓ cả ba:
        //   (a) Hành vi này có phải LUẬT NGHIỆP VỤ không? (không phải điều phối, không phải I/O)
        //   (b) Nó có thuộc về đúng MỘT entity không? — nếu CÓ thì đặt vào entity đó, xong.
        //   (c) Ép nó vào một entity có làm entity đó phải sửa entity khác không?
        // "Chuyển tiền" trả lời: (a) có, (b) KHÔNG — hai tài khoản ngang nhau, (c) có.
        // => domain service.
        TaiKhoan a = new TaiKhoan("TK-A", 5_000_000);
        TaiKhoan b = new TaiKhoan("TK-B", 0);
        BienLaiChuyenTien bl = new DichVuChuyenTien().chuyen(a, b, 2_000_000, new BieuPhi(1_000_000, 1_000, 5_000));
        check(bl.phi() == 5_000, "trên 1 triệu -> phí cao");
        check(a.soDu() == 2_995_000, "trừ cả tiền lẫn phí");
        check(b.soDu() == 2_000_000, "bên nhận không chịu phí");

        // Nếu nhét vào entity: `a.chuyenToi(b, 2_000_000)` thì `TaiKhoan` phải gọi
        // `b.nap(...)` — một aggregate sửa một aggregate khác trong cùng lời gọi, đúng
        // thứ bài 83 cấm. Và câu hỏi "phí do bên nào chịu" bỗng thành trách nhiệm của
        // lớp `TaiKhoan`, dù nó là luật của DỊCH VỤ CHUYỂN TIỀN chứ không phải của tài khoản.

        // ---- 4. DOMAIN SERVICE KHÔNG CÓ TRẠNG THÁI, KHÔNG CHẠM I/O ----
        check(DichVuChuyenTien.class.getDeclaredFields().length == 0,
                "domain service không có field nào — không kho, không đồng hồ, không cấu hình");
        // Hệ quả trực tiếp: nó test được không cần gì cả. Không mock, không fake, không
        // CSDL. Nếu domain service của bạn cần một repository để chạy, thì hoặc nó là
        // application service đội lốt, hoặc dữ liệu nó cần phải được TRUYỀN VÀO (như
        // `BieuPhi` ở trên) thay vì để nó tự đi lấy.

        // ---- 5. BA LOẠI SERVICE — bảng phân biệt ----
        //
        //                | Domain service      | Application service   | Infrastructure
        //   -------------|---------------------|-----------------------|----------------
        //   trả lời       | "luật là gì?"       | "quy trình là gì?"    | "làm thế nào?"
        //   ví dụ         | DichVuChuyenTien    | UngDungChuyenTien     | GuiThongBao
        //   có trạng thái | KHÔNG               | không                 | thường có
        //   chạm I/O      | KHÔNG               | có (qua interface)    | CÓ
        //   mở transaction| KHÔNG               | CÓ                    | không
        //   nằm ở tầng    | miền                | ứng dụng              | hạ tầng
        //   test cần gì   | không cần gì        | fake (bài 68)         | môi trường thật
        //
        // Sai lầm phổ biến nhất: gộp cột 1 và cột 2 thành một lớp `OrderService` dài 800
        // dòng, vừa mở transaction vừa tính luật vừa gửi email.
        Map<String, TaiKhoan> csdl = new LinkedHashMap<>();
        csdl.put("TK-A", new TaiKhoan("TK-A", 5_000_000));
        csdl.put("TK-B", new TaiKhoan("TK-B", 0));
        List<String> daGui = new ArrayList<>();

        UngDungChuyenTien ud = new UngDungChuyenTien(
                new KhoTaiKhoan() {
                    @Override public TaiKhoan tim(String ma) { return csdl.get(ma); }
                    @Override public void luu(TaiKhoan tk) { csdl.put(tk.ma(), tk); }
                },
                (ma, noiDung) -> daGui.add(ma + ":" + noiDung));

        ud.thucHien("TK-A", "TK-B", 500_000);
        check(csdl.get("TK-A").soDu() == 4_499_000, "500.000 + phí thấp 1.000");
        check(daGui.size() == 1, "tầng ứng dụng lo thông báo — miền không biết email tồn tại");
        check(ud.soLanGoiMien == 1, "và nó gọi miền đúng một lần, không tự tính luật");

        // ---- 6. CẠM BẪY: `XxxService` thành thùng rác ----
        // Dấu hiệu nhận biết, theo thứ tự nặng dần:
        //   1. Tên là danh từ chung: `OrderService`, `UserManager`, `DataHandler` (bài 81).
        //   2. Nó có field là repository VÀ đồng thời chứa luật nghiệp vụ.
        //   3. Nó có phương thức thứ 15.
        //   4. Entity tương ứng chỉ còn getter/setter (phép đếm ở phần 2).
        // Domain service tốt thường có ĐÚNG MỘT phương thức công khai và tên là một
        // ĐỘNG TỪ nghiệp vụ: `DichVuChuyenTien.chuyen`, `TinhLaiSuat.cho`,
        // `KiemTraTrungLap.giua`.
        long soPmCongKhai = java.util.Arrays.stream(DichVuChuyenTien.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic()).count();
        check(soPmCongKhai == 1, "một domain service, một việc");

        // ---- 7. RANH GIỚI: khi nào KHÔNG cần domain service ----
        // Cám dỗ ngược lại cũng có thật: tạo `DichVuRutTien` cho việc `tk.rut(tien)`.
        // Câu hỏi (b) ở phần 3 trả lời CÓ — hành vi thuộc về đúng một entity — nên nó
        // phải nằm trong entity, và một service ở đây chỉ thêm một lớp vô nghĩa.
        //
        // Quy tắc: domain service là NGOẠI LỆ, không phải mặc định. Nếu miền của bạn có
        // nhiều service hơn entity, thì bạn đang viết mô hình thiếu máu và gọi nó là DDD.
        check(true, "mặc định: hành vi nằm trong entity/value object; service phải có lý do");

        System.out.println("OK");
    }
}
