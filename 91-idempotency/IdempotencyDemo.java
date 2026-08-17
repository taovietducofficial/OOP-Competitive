/*
 * Ngôn ngữ: Java
 * Công dụng: Idempotency — cùng một lệnh gửi hai lần chỉ được tính một. Bài cho nổ ba
 * con bug: thử lại sau khi mạng timeout làm khách bị trừ tiền HAI lần; "kiểm tra rồi
 * mới làm" vẫn trừ hai lần khi hai phiên chạy xen kẽ; và cùng một khoá nhưng nội dung
 * khác nhau trả về kết quả của lệnh cũ — thứ còn tệ hơn trừ tiền hai lần.
 * Tại sao cần học: bài 84 kết luận rằng sự kiện phải phát SAU commit, và cách làm thực
 * tế (outbox, hàng đợi, cơ chế thử lại) đều là "giao ÍT NHẤT MỘT LẦN" — nghĩa là gửi
 * trùng không phải rủi ro, nó là điều CHẮC CHẮN xảy ra. Idempotency là thứ duy nhất làm
 * cho điều đó vô hại. Và điểm cốt lõi mà hầu hết bản cài đặt làm sai: nó KHÔNG phải
 * "kiểm tra tồn tại rồi mới làm" — hai lời gọi đó có một khe hở, và khe hở đó là tiền.
 */
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class IdempotencyDemo {

    record LenhChuyenTien(String khoaIdempotency, String tuTaiKhoan, long soTien) { }
    record BienLai(String maGiaoDich, long soDuSauKhi) { }

    static final class TaiKhoan {
        private long soDu;
        TaiKhoan(long soDu) { this.soDu = soDu; }
        void tru(long t) {
            if (soDu < t) throw new IllegalStateException("không đủ số dư");
            soDu -= t;
        }
        long soDu() { return soDu; }
    }

    // =====================================================================
    // SAI 1 — không có khoá idempotency: thử lại = trừ tiền lần nữa
    // =====================================================================
    static final class DichVuNgayTho {
        private int dem = 0;
        BienLai chuyen(TaiKhoan tk, long soTien) {
            tk.tru(soTien);
            return new BienLai("GD-" + (++dem), tk.soDu());
        }
    }

    // =====================================================================
    // SAI 2 — "kiểm tra rồi mới làm": có khe hở giữa hai lời gọi
    // =====================================================================
    static final class DichVuKiemTraRoiLam {
        private final Map<String, BienLai> daXuLy = new LinkedHashMap<>();
        private int dem = 0;

        boolean daCo(String khoa) { return daXuLy.containsKey(khoa); }   // bước 1

        BienLai lam(TaiKhoan tk, LenhChuyenTien lenh) {                   // bước 2
            tk.tru(lenh.soTien());
            BienLai bl = new BienLai("GD-" + (++dem), tk.soDu());
            daXuLy.put(lenh.khoaIdempotency(), bl);
            return bl;
        }
    }

    // =====================================================================
    // ĐÚNG — GIÀNH CHỖ nguyên tử, rồi mới làm
    // =====================================================================
    static final class DichVuIdempotent {
        /** Bản ghi kết quả: `null` nghĩa là "có người đang xử lý, chưa xong". */
        private record BanGhi(String vanTay, BienLai ketQua) { }

        private final ConcurrentHashMap<String, BanGhi> so = new ConcurrentHashMap<>();
        private int dem = 0;
        int soLanThucSuTru = 0;

        BienLai chuyen(TaiKhoan tk, LenhChuyenTien lenh) {
            String vanTay = lenh.tuTaiKhoan() + "|" + lenh.soTien();

            // MỘT lời gọi nguyên tử: vừa hỏi vừa giành chỗ. Không có khe hở nào ở giữa.
            BanGhi cu = so.putIfAbsent(lenh.khoaIdempotency(), new BanGhi(vanTay, null));

            if (cu != null) {
                // Cùng khoá nhưng NỘI DUNG KHÁC -> đây là hai lệnh khác nhau bị trùng
                // khoá, không phải một lệnh gửi lại. Trả kết quả cũ là sai nghiêm trọng.
                if (!cu.vanTay().equals(vanTay))
                    throw new IllegalStateException("khoá đã dùng cho một lệnh khác");
                if (cu.ketQua() == null)
                    throw new IllegalStateException("lệnh đang được xử lý, hãy thử lại sau");
                return cu.ketQua();                    // phát lại KẾT QUẢ CŨ, không làm lại
            }

            // Tới đây thì CHẮC CHẮN chỉ mình ta giành được chỗ.
            soLanThucSuTru++;
            tk.tru(lenh.soTien());
            BienLai bl = new BienLai("GD-" + (++dem), tk.soDu());
            so.put(lenh.khoaIdempotency(), new BanGhi(vanTay, bl));
            return bl;
        }

        int soKhoa() { return so.size(); }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: thử lại sau timeout = trừ tiền hai lần ----
        // Kịch bản có thật và rất thường: máy chủ xử lý xong, rồi mạng đứt trước khi trả
        // lời. Điện thoại của khách không phân biệt được "chưa xử lý" với "xử lý xong mà
        // mất phản hồi", nên nó thử lại — đúng như mọi thư viện HTTP được cấu hình.
        TaiKhoan tk = new TaiKhoan(1_000_000);
        DichVuNgayTho ngayTho = new DichVuNgayTho();
        ngayTho.chuyen(tk, 100_000);          // lần 1: thành công, phản hồi bị mất
        ngayTho.chuyen(tk, 100_000);          // lần 2: điện thoại tự thử lại
        check(tk.soDu() == 800_000, "khách bị trừ 200.000 cho MỘT giao dịch");
        // Không ngoại lệ, không log lỗi. Cả hai lời gọi đều "thành công".

        // ---- 2. CON BUG: "kiểm tra rồi mới làm" vẫn hỏng ----
        // Đây là bản vá đầu tiên ai cũng nghĩ ra, và nó vẫn sai — vì giữa `daCo()` và
        // `lam()` có một khe hở. Hai phiên chạy xen kẽ (hai máy chủ sau bộ cân bằng tải,
        // hoặc hai luồng của cùng máy chủ) là đủ:
        TaiKhoan tk2 = new TaiKhoan(1_000_000);
        DichVuKiemTraRoiLam vaTam = new DichVuKiemTraRoiLam();
        LenhChuyenTien lenh = new LenhChuyenTien("KEY-1", "TK-A", 100_000);

        boolean aThay = vaTam.daCo(lenh.khoaIdempotency());   // phiên A: chưa có
        boolean bThay = vaTam.daCo(lenh.khoaIdempotency());   // phiên B: cũng chưa có
        if (!aThay) vaTam.lam(tk2, lenh);
        if (!bThay) vaTam.lam(tk2, lenh);
        check(tk2.soDu() == 800_000, "vẫn trừ hai lần — khe hở giữa hai lời gọi là tiền");
        // Bài học chung: MỌI cặp "hỏi rồi làm" trên trạng thái chia sẻ đều có khe hở này.
        // `containsKey` + `put`, `SELECT` + `INSERT`, `exists()` + `create()` — cả ba đều
        // là cùng một bug (bài 83 phần 2 là một biến thể khác của nó).

        // ---- 3. BẢN ĐÚNG: GIÀNH CHỖ nguyên tử ----
        TaiKhoan tk3 = new TaiKhoan(1_000_000);
        DichVuIdempotent dv = new DichVuIdempotent();
        LenhChuyenTien l1 = new LenhChuyenTien("KEY-1", "TK-A", 100_000);

        BienLai bl1 = dv.chuyen(tk3, l1);
        BienLai bl2 = dv.chuyen(tk3, l1);           // gửi lại y hệt
        BienLai bl3 = dv.chuyen(tk3, l1);           // và lần nữa
        check(tk3.soDu() == 900_000, "trừ ĐÚNG MỘT lần, dù gọi ba lần");
        check(dv.soLanThucSuTru == 1, "và chỉ một lần đi vào phần nghiệp vụ");
        check(bl1.equals(bl2) && bl2.equals(bl3), "cả ba lần trả về CÙNG MỘT biên lai");
        // Chi tiết cuối quan trọng hơn vẻ ngoài: idempotent không phải là "lần sau thì
        // bỏ qua" mà là "lần sau trả lại ĐÚNG KẾT QUẢ CŨ". Nếu lần hai trả về `null` hay
        // ném lỗi "đã xử lý", thì phía gọi vẫn không biết mã giao dịch — và nó sẽ thử lại.

        // ---- 4. CÙNG KHOÁ, KHÁC NỘI DUNG: phải TỪ CHỐI ----
        LenhChuyenTien lenhKhac = new LenhChuyenTien("KEY-1", "TK-A", 5_000_000);
        boolean tuChoi = false;
        try { dv.chuyen(tk3, lenhKhac); } catch (IllegalStateException e) { tuChoi = true; }
        check(tuChoi, "cùng khoá nhưng số tiền khác -> TỪ CHỐI");
        check(tk3.soDu() == 900_000, "và không đụng vào số dư");
        // Nếu chỗ này trả về biên lai cũ (100.000) cho một lệnh 5 triệu, phía gọi sẽ tin
        // rằng 5 triệu đã chuyển xong. Đó là hỏng nặng hơn trừ tiền hai lần: hệ thống vừa
        // NÓI DỐI. Vì vậy bản ghi idempotency phải lưu VÂN TAY của nội dung, không chỉ khoá.

        // ---- 5. AI SINH KHOÁ, VÀ SINH LÚC NÀO ----
        // Khoá phải do PHÍA GỌI sinh, TRƯỚC lần gửi đầu tiên, và giữ nguyên qua mọi lần
        // thử lại. Ba cách sinh khoá SAI hay gặp:
        //   - máy chủ sinh  -> mỗi request một khoá mới, vô dụng hoàn toàn;
        //   - băm nội dung  -> hai lần chuyển 100.000 CỐ Ý cho cùng người bị gộp làm một;
        //   - thời gian     -> thử lại ở mili-giây khác là khoá khác.
        // Cách đúng: UUID sinh ở phía gọi khi NGƯỜI DÙNG bấm nút, không phải khi gửi request.
        LenhChuyenTien coY1 = new LenhChuyenTien("KEY-2", "TK-A", 100_000);
        LenhChuyenTien coY2 = new LenhChuyenTien("KEY-3", "TK-A", 100_000);
        dv.chuyen(tk3, coY1);
        dv.chuyen(tk3, coY2);
        check(tk3.soDu() == 700_000, "hai lệnh CỐ Ý giống nhau, hai khoá -> trừ đủ hai lần");
        check(dv.soKhoa() == 3, "ba khoá đã dùng");
        // Dòng trên là lý do không được băm nội dung làm khoá: hệ thống không có cách nào
        // tự phân biệt "gửi lại" với "cố ý làm hai lần" — chỉ phía gọi biết, nên chỉ phía
        // gọi được quyền quyết định bằng khoá.

        // ---- 6. PHÉP TÍNH TUYỆT ĐỐI THÌ TỰ NÓ ĐÃ IDEMPOTENT ----
        Map<String, Long> soDu = new LinkedHashMap<>(Map.of("TK-A", 500_000L));
        soDu.put("TK-A", 400_000L);
        soDu.put("TK-A", 400_000L);                 // làm lại y hệt
        check(soDu.get("TK-A") == 400_000L, "GÁN giá trị: chạy bao nhiêu lần cũng thế");

        long tuongDoi = 500_000L;
        tuongDoi -= 100_000L;
        tuongDoi -= 100_000L;                       // làm lại y hệt
        check(tuongDoi == 300_000L, "CỘNG TRỪ: mỗi lần chạy lại là một lần sai thêm");
        // Nguyên tắc thiết kế rút ra: khi được chọn, hãy thiết kế lệnh theo dạng TUYỆT
        // ĐỐI ("đặt trạng thái = ĐÃ GIAO") thay vì TƯƠNG ĐỐI ("tăng số lượng lên 1"). Lệnh
        // tuyệt đối idempotent miễn phí, không cần sổ khoá, không cần dọn dẹp.
        // Không phải lúc nào cũng chọn được — "trừ tiền" vốn là tương đối — nhưng nhiều
        // lệnh tưởng là tương đối thì viết lại được thành tuyệt đối.

        // ---- 7. SỔ KHOÁ PHẢI CÓ HẠN, VÀ PHẢI CÓ PHẠM VI ----
        // Hai điều bản demo này lược bỏ mà hệ thật bắt buộc phải có:
        //   - HẠN: khoá giữ mãi thì sổ lớn vô hạn. Thường giữ 24–72 giờ — đủ dài hơn mọi
        //     lịch thử lại, đủ ngắn để sổ không phình. Sau khi hết hạn, cùng khoá đó được
        //     coi là lệnh mới; đó là đánh đổi CÓ Ý, phải nói ra trong tài liệu API.
        //   - PHẠM VI: khoá phải kèm định danh người gọi. Nếu không, khách A đoán được
        //     khoá của khách B là chặn được giao dịch của người khác.
        // Và trong hệ thật, "giành chỗ nguyên tử" chính là RÀNG BUỘC DUY NHẤT của CSDL:
        //     INSERT INTO so_idempotency(khoa, van_tay) VALUES (?, ?)
        // Insert trùng thì CSDL báo lỗi khoá trùng — đó là `putIfAbsent` ở mức bền vững.
        check(dv.soKhoa() == 3, "sổ khoá là dữ liệu THẬT, phải được thiết kế như mọi bảng khác");

        // ---- 8. Vì sao bài này đi liền sau bài 84 ----
        // Outbox, hàng đợi, cơ chế thử lại của HTTP — cả ba đều giao ÍT NHẤT MỘT LẦN.
        // "Đúng một lần" không tồn tại trên mạng: bên gửi không bao giờ phân biệt được
        // "chưa nhận" với "nhận rồi mà mất phản hồi". Nên "đúng một lần" luôn được làm
        // bằng: GIAO ít nhất một lần + XỬ LÝ idempotent. Bài 84 lo nửa đầu, bài này lo
        // nửa sau, và thiếu nửa nào thì cũng mất tiền.
        System.out.println("OK");
    }
}
