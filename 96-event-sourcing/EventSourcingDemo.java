/*
 * Ngôn ngữ: Java
 * Công dụng: Event sourcing — trạng thái KHÔNG được lưu, nó được TÍNH LẠI bằng cách phát
 * lại chuỗi sự kiện. Bài cho nổ ba con bug: chỉ lưu trạng thái thì mất sạch lịch sử và
 * không ai trả lời được "vì sao số dư là 500.000"; hàm phát lại có kiểm tra hợp lệ nên
 * một luật mới làm KHÔNG TẢI ĐƯỢC dữ liệu cũ; và phát lại 100.000 sự kiện cho mỗi lần
 * đọc mà không có ảnh chụp.
 * Tại sao cần học: bài 84 dạy sự kiện là thứ GHI LẠI chuyện đã xảy ra. Bài này đi tới
 * kết luận cuối của ý đó: nếu sự kiện đã ghi đủ mọi chuyện đã xảy ra, thì trạng thái hiện
 * tại là dữ liệu THỪA — tính lại được bất cứ lúc nào. Đổi lại, một luật mới xuất hiện và
 * nó tuyệt đối: HÀM PHÁT LẠI KHÔNG ĐƯỢC KIỂM TRA GÌ, KHÔNG ĐƯỢC ĐỌC GÌ BÊN NGOÀI. Vi
 * phạm luật đó thì lịch sử của bạn ngừng tải được — và ở đây không có bản sao nào khác.
 */
import java.util.ArrayList;
import java.util.List;

public class EventSourcingDemo {

    // =====================================================================
    // SỰ KIỆN — bất biến, thì quá khứ, mang dữ liệu LÚC XẢY RA (bài 84)
    // =====================================================================
    sealed interface SuKien permits DaMoTaiKhoan, DaNap, DaRut, DaTinhPhi { }

    record DaMoTaiKhoan(String maTk, long soDuBanDau) implements SuKien { }
    record DaNap(long soTien, String nguon) implements SuKien { }
    record DaRut(long soTien, String lyDo) implements SuKien { }
    /** Phí đã được TÍNH SẴN lúc phát sinh — xem phần 4 để biết vì sao đây là bắt buộc. */
    record DaTinhPhi(long soTienPhi, int tiLePhanNghin) implements SuKien { }

    // =====================================================================
    // AGGREGATE — không lưu trạng thái, chỉ lưu SỰ KIỆN
    // =====================================================================
    static final class TaiKhoan {
        private String ma;
        private long soDu;
        private int soSuKienDaApDung;
        private final List<SuKien> suKienMoi = new ArrayList<>();

        /** Dựng lại từ lịch sử. Đây là cách DUY NHẤT tải một aggregate trong ES. */
        static TaiKhoan phatLai(List<SuKien> lichSu) {
            TaiKhoan tk = new TaiKhoan();
            for (SuKien e : lichSu) tk.apDung(e);
            return tk;
        }

        /**
         * ÁP DỤNG — chỉ đổi trạng thái, tuyệt đối KHÔNG kiểm tra, KHÔNG đọc gì bên ngoài,
         * KHÔNG gọi đồng hồ, KHÔNG ném ngoại lệ. Nó phải cho ra cùng kết quả hôm nay,
         * ngày mai, và mười năm nữa với cùng chuỗi sự kiện.
         *
         * Không có `default` — thêm một loại sự kiện mà quên xử lý ở đây là LỖI BIÊN DỊCH.
         * Với event sourcing, quên một nhánh nghĩa là tính sai trạng thái của mọi bản ghi
         * từng phát ra sự kiện đó (bài 84 phần 7).
         */
        private void apDung(SuKien e) {
            switch (e) {
                case DaMoTaiKhoan x -> { ma = x.maTk(); soDu = x.soDuBanDau(); }
                case DaNap x -> soDu += x.soTien();
                case DaRut x -> soDu -= x.soTien();
                case DaTinhPhi x -> soDu -= x.soTienPhi();
            }
            soSuKienDaApDung++;
        }

        /** QUYẾT ĐỊNH — đây là nơi DUY NHẤT được kiểm tra luật nghiệp vụ. */
        private void ghiNhan(SuKien e) { apDung(e); suKienMoi.add(e); }

        static TaiKhoan mo(String ma, long banDau) {
            if (banDau < 0) throw new IllegalArgumentException("số dư ban đầu không âm");
            TaiKhoan tk = new TaiKhoan();
            tk.ghiNhan(new DaMoTaiKhoan(ma, banDau));
            return tk;
        }
        void nap(long t, String nguon) {
            if (t <= 0) throw new IllegalArgumentException("số tiền nạp phải dương");
            ghiNhan(new DaNap(t, nguon));
        }
        void rut(long t, String lyDo, int tiLePhiPhanNghin) {
            long phi = t * tiLePhiPhanNghin / 1000;
            if (soDu < t + phi) throw new IllegalStateException("không đủ số dư");
            ghiNhan(new DaRut(t, lyDo));
            ghiNhan(new DaTinhPhi(phi, tiLePhiPhanNghin));   // phí CHỐT tại thời điểm này
        }

        long soDu() { return soDu; }
        String ma() { return ma; }
        int soSuKienDaApDung() { return soSuKienDaApDung; }
        List<SuKien> suKienMoi() { return List.copyOf(suKienMoi); }
    }

    // =====================================================================
    // BẢN SAI — hàm phát lại có KIỂM TRA
    // =====================================================================
    static final class TaiKhoanSai {
        long soDu;
        static final long HAN_MUC_RUT_MOI = 1_000_000L;   // luật MỚI, ban hành hôm nay

        void apDungCoKiemTra(SuKien e) {
            if (e instanceof DaRut r && r.soTien() > HAN_MUC_RUT_MOI)
                throw new IllegalStateException("vượt hạn mức rút");   // <- thảm hoạ
            if (e instanceof DaMoTaiKhoan x) soDu = x.soDuBanDau();
            else if (e instanceof DaNap x) soDu += x.soTien();
            else if (e instanceof DaRut x) soDu -= x.soTien();
            else if (e instanceof DaTinhPhi x) soDu -= x.soTienPhi();
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: chỉ lưu TRẠNG THÁI thì mất sạch lịch sử ----
        long soDuChiLuuTrangThai = 1_000_000L;
        soDuChiLuuTrangThai += 200_000L;      // nạp
        soDuChiLuuTrangThai -= 700_000L;      // rút
        soDuChiLuuTrangThai -= 7_000L;        // phí
        check(soDuChiLuuTrangThai == 493_000L, "số dư đúng: 493.000");
        // Khách gọi lên hỏi: "vì sao tài khoản tôi còn 493.000?" Câu trả lời duy nhất mà
        // hệ thống đưa ra được là "vì nó bằng 493.000". Không có ai, không có lúc nào,
        // không có vì sao — cột số dư đã bị ghi đè bốn lần và ba giá trị cũ biến mất.
        int soCauTraLoiDuocTuTrangThai = 0;
        check(soCauTraLoiDuocTuTrangThai == 0, "0 câu hỏi lịch sử trả lời được");

        // ---- 2. EVENT SOURCING: trạng thái là dữ liệu THỪA ----
        TaiKhoan tk = TaiKhoan.mo("TK-01", 1_000_000L);
        tk.nap(200_000L, "chuyển khoản");
        tk.rut(700_000L, "mua hàng", 10);      // phí 1% = 7.000
        check(tk.soDu() == 493_000L, "cùng con số 493.000");

        List<SuKien> lichSu = tk.suKienMoi();
        check(lichSu.size() == 4, "và 4 sự kiện giải thích trọn vẹn con số đó");
        check(lichSu.get(0) instanceof DaMoTaiKhoan, "mở tài khoản 1.000.000");
        check(lichSu.get(3) instanceof DaTinhPhi p && p.soTienPhi() == 7_000L, "phí 7.000, tỉ lệ 10‰");

        TaiKhoan dungLai = TaiKhoan.phatLai(lichSu);
        check(dungLai.soDu() == tk.soDu(), "phát lại lịch sử cho ra ĐÚNG trạng thái cũ");
        check(dungLai.ma().equals("TK-01"), "toàn bộ trạng thái, không chỉ số dư");
        check(dungLai.soSuKienDaApDung() == 4, "bằng cách áp dụng đúng 4 sự kiện");
        // Đây là lời hứa cốt lõi: KHÔNG có cột `so_du` nào trong CSDL. Chỉ có bảng sự
        // kiện, và số dư là một hàm của nó.

        // ---- 3. TRUY VẤN THEO THỜI GIAN — miễn phí, và chỉ ES mới có ----
        TaiKhoan truocKhiRut = TaiKhoan.phatLai(lichSu.subList(0, 2));
        check(truocKhiRut.soDu() == 1_200_000L, "số dư TRƯỚC khi rút: phát lại 2 sự kiện đầu");
        // "Số dư của khách này lúc 14h ngày 3 tháng trước là bao nhiêu?" — với mô hình chỉ
        // lưu trạng thái, câu này cần một bảng lịch sử riêng mà ai đó phải nhớ ghi. Với
        // ES, nó là `subList` cộng một vòng lặp.

        // ---- 4. CON BUG: HÀM PHÁT LẠI CÓ KIỂM TRA ----
        // Hôm nay ngân hàng ban hành hạn mức rút mới: tối đa 1.000.000/lần. Ai đó "cho
        // chắc" thêm câu kiểm tra vào hàm áp dụng.
        TaiKhoanSai sai = new TaiKhoanSai();
        boolean khongTaiDuoc = false;
        try {
            for (SuKien e : List.of(new DaMoTaiKhoan("TK-02", 5_000_000L),
                                    new DaRut(3_000_000L, "mua xe"),   // hợp lệ NĂM NGOÁI
                                    new DaNap(1_000_000L, "lương")))
                sai.apDungCoKiemTra(e);
        } catch (IllegalStateException e) { khongTaiDuoc = true; }
        check(khongTaiDuoc, "KHÔNG TẢI ĐƯỢC tài khoản — vì quá khứ không thoả luật hôm nay");
        // Đọc lại: đây không phải "một giao dịch bị từ chối". Đây là **cả tài khoản biến
        // mất khỏi hệ thống**. Không đọc được số dư, không mở được màn hình, không xử lý
        // được giao dịch mới. Và không có bản sao nào khác để mà khôi phục — trong ES,
        // chuỗi sự kiện LÀ dữ liệu.
        //
        // Luật tuyệt đối: KIỂM TRA nằm ở hàm QUYẾT ĐỊNH (`rut`), ÁP DỤNG chỉ đổi trạng
        // thái. Sự kiện đã xảy ra thì đã xảy ra — luật mới chỉ áp cho quyết định MỚI.
        TaiKhoan dung = TaiKhoan.phatLai(List.of(new DaMoTaiKhoan("TK-02", 5_000_000L),
                                                  new DaRut(3_000_000L, "mua xe"),
                                                  new DaNap(1_000_000L, "lương")));
        check(dung.soDu() == 3_000_000L, "hàm áp dụng không kiểm gì -> tải được bình thường");
        boolean luatMoiVanApDung = false;
        try { dung.rut(3_000_000L, "mua xe nữa", 10); }
        catch (IllegalStateException e) { luatMoiVanApDung = true; }
        check(luatMoiVanApDung, "và luật mới vẫn chặn được QUYẾT ĐỊNH mới");

        // ---- 5. HỆ QUẢ THỨ HAI: SỰ KIỆN PHẢI TỰ ĐỦ ----
        // `DaTinhPhi` mang sẵn `soTienPhi`, không mang "hãy tính 1% của số rút". Nếu hàm
        // áp dụng phải TỰ TÍNH phí theo biểu phí hiện tại, thì phát lại năm sau sẽ cho ra
        // số dư khác — vì biểu phí đã đổi.
        long phiTinhLaiTheoBieuPhiMoi = 700_000L * 20 / 1000;   // biểu phí năm sau: 2%
        check(phiTinhLaiTheoBieuPhiMoi == 14_000L, "phí tính lại hôm nay: 14.000");
        check(((DaTinhPhi) lichSu.get(3)).soTienPhi() == 7_000L, "phí THẬT lúc đó: 7.000");
        check(phiTinhLaiTheoBieuPhiMoi != ((DaTinhPhi) lichSu.get(3)).soTienPhi(),
                "lệch 7.000 — và mọi tài khoản trong hệ thống đều lệch cùng lúc");
        // Đây là bài 84 phần 5 với hậu quả nặng hơn hẳn: ở đó sự kiện thiếu dữ liệu làm
        // một báo cáo sai; ở đây nó làm SỐ DƯ sai, trên toàn bộ hệ thống, mỗi lần phát lại.
        // Quy tắc: sự kiện mang KẾT QUẢ, không mang CÔNG THỨC.

        // ---- 6. ẢNH CHỤP: phát lại 100.000 sự kiện là không dùng được ----
        List<SuKien> lichSuDai = new ArrayList<>();
        lichSuDai.add(new DaMoTaiKhoan("TK-03", 0));
        for (int i = 0; i < 1000; i++) lichSuDai.add(new DaNap(1_000L, "lãi"));
        TaiKhoan khongAnhChup = TaiKhoan.phatLai(lichSuDai);
        check(khongAnhChup.soSuKienDaApDung() == 1001, "phát lại 1.001 sự kiện cho MỖI lần đọc");

        // Ảnh chụp = trạng thái tại sự kiện thứ N, cộng phần đuôi phát lại từ đó.
        record AnhChup(long soDu, String ma, int denSuKienThu) { }
        AnhChup ac = new AnhChup(khongAnhChup.soDu(), "TK-03", 1001);
        lichSuDai.add(new DaNap(500L, "lãi"));
        lichSuDai.add(new DaNap(500L, "lãi"));
        long soDuTuAnhChup = ac.soDu();
        int soSuKienPhaiPhatLai = 0;
        for (SuKien e : lichSuDai.subList(ac.denSuKienThu(), lichSuDai.size())) {
            if (e instanceof DaNap n) soDuTuAnhChup += n.soTien();
            soSuKienPhaiPhatLai++;
        }
        check(soSuKienPhaiPhatLai == 2, "có ảnh chụp: chỉ phát lại 2 sự kiện đuôi");
        check(soDuTuAnhChup == TaiKhoan.phatLai(lichSuDai).soDu(), "và cho ra cùng kết quả");
        check(1001 / 2 > 100, "gấp hơn 500 lần công phát lại");
        // Điều quan trọng nhất về ảnh chụp: nó là BỘ NHỚ ĐỆM, không phải nguồn sự thật.
        // Xoá hết ảnh chụp đi thì hệ thống chỉ chậm, không sai. Nếu xoá ảnh chụp mà mất
        // dữ liệu, thì đó không còn là event sourcing nữa.

        // ---- 7. GIÁ PHẢI TRẢ, NÓI THẲNG ----
        //   - Sự kiện là HỢP ĐỒNG VĨNH VIỄN. Đổi nghĩa một loại sự kiện cũ là viết lại
        //     lịch sử; thêm loại mới thì được, sửa loại cũ thì phải phiên bản hoá (bài 79).
        //   - Truy vấn ("tìm mọi tài khoản số dư < 0") KHÔNG làm trên chuỗi sự kiện được.
        //     Bắt buộc phải có mô hình đọc riêng, cập nhật bằng chính các sự kiện đó
        //     (bài 95) — nên ES gần như luôn đi kèm CQRS.
        //   - Xoá dữ liệu cá nhân theo yêu cầu pháp lý là bài toán KHÓ, vì bản chất của
        //     ES là không xoá. Phải mã hoá dữ liệu cá nhân và vứt khoá đi.
        // Vì vậy: ES dùng cho những phần mà LỊCH SỬ LÀ NGHIỆP VỤ — sổ kế toán, kho, hồ sơ
        // y tế, audit. Không dùng cho bảng cấu hình và danh mục.
        check(lichSu.size() == 4 && tk.soDu() == 493_000L, "lịch sử và trạng thái, cùng một nguồn");

        System.out.println("OK");
    }
}
