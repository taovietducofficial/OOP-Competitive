/*
 * Ngôn ngữ: Java
 * Công dụng: Bài tổng kết — một hệ đặt hàng nhỏ nhưng đầy đủ, ghép lại mọi thứ tầng
 * 04-competitive đã dựng: ngôn ngữ chung, value object & entity, ranh giới aggregate, sự
 * kiện miền, kho & đơn vị công việc, dịch vụ miền, specification, policy, máy trạng thái,
 * tiền tệ, idempotency, khoá lạc quan, cổng & bộ nối, và mô hình đọc.
 * Tại sao cần học: từng bài trước dạy MỘT thứ và cố tình bỏ qua phần còn lại. Bài này cho
 * thấy chúng không phải 20 mẫu thiết kế rời rạc — chúng là MỘT thiết kế, và mỗi luật tồn
 * tại vì luật bên cạnh. Aggregate cần ranh giới vì có bất biến; bất biến buộc phải tham
 * chiếu bằng id; tham chiếu bằng id buộc phải có sự kiện; sự kiện buộc phải idempotent.
 * Rút một mắt xích ra thì cả chuỗi lỏng, và bài này để bạn thấy toàn bộ chuỗi cùng lúc.
 */
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class OrderSystemDemo {

    // =====================================================================
    // MIỀN · VALUE OBJECT  (bài 82, 90)
    // =====================================================================
    enum TienTe { VND(0), USD(2); final int soChuSo; TienTe(int n) { soChuSo = n; } }

    record Tien(long donViNho, TienTe tienTe) {
        Tien { if (donViNho < 0) throw new IllegalArgumentException("số tiền không âm"); }
        Tien cong(Tien k) {
            if (tienTe != k.tienTe) throw new IllegalArgumentException("không cộng khác tệ");
            return new Tien(donViNho + k.donViNho, tienTe);
        }
        Tien nhanPhanTram(int pt) { return new Tien(donViNho * pt / 100, tienTe); }
        Tien tru(Tien k) {
            if (tienTe != k.tienTe) throw new IllegalArgumentException("không trừ khác tệ");
            return new Tien(donViNho - k.donViNho, tienTe);
        }
    }

    record MaDonHang(String giaTri) { }
    record MaKhachHang(String giaTri) { }
    record DongHang(String sanPham, Tien donGia, int soLuong) {
        Tien thanhTien() { return new Tien(donGia.donViNho() * soLuong, donGia.tienTe()); }
    }

    // =====================================================================
    // MIỀN · MÁY TRẠNG THÁI  (bài 89)
    // =====================================================================
    enum TrangThai {
        MOI_TAO   { @Override TrangThai thanhToan() { return DA_THANH_TOAN; }
                    @Override TrangThai huy() { return DA_HUY; } },
        DA_THANH_TOAN { @Override TrangThai giao() { return DA_GIAO; }
                        @Override TrangThai huy() { return DA_HUY; } },
        DA_GIAO { }, DA_HUY { };
        // MẶC ĐỊNH LÀ TỪ CHỐI — quên viết = đóng cửa, không phải mở cửa.
        TrangThai thanhToan() { throw new IllegalStateException("không thanh toán được ở " + this); }
        TrangThai giao()      { throw new IllegalStateException("không giao được ở " + this); }
        TrangThai huy()       { throw new IllegalStateException("không huỷ được ở " + this); }
    }

    // =====================================================================
    // MIỀN · SỰ KIỆN  (bài 84)
    // =====================================================================
    sealed interface SuKien permits DonHangDaTao, DonHangDaGiao { MaDonHang maDon(); }
    record DonHangDaTao(MaDonHang maDon, MaKhachHang maKhach, Tien tong, long luc) implements SuKien { }
    record DonHangDaGiao(MaDonHang maDon, Tien tongLucGiao, long luc) implements SuKien { }

    // =====================================================================
    // MIỀN · AGGREGATE ROOT  (bài 83 ranh giới, 92 phiên bản, 84 ghi sự kiện)
    // =====================================================================
    static final class DonHang {
        static final Tien HAN_MUC = new Tien(50_000_000L, TienTe.VND);

        private final MaDonHang ma;
        private final MaKhachHang maKhach;          // tham chiếu aggregate khác BẰNG ID
        private final List<DongHang> cacDong = new ArrayList<>();
        private TrangThai trangThai = TrangThai.MOI_TAO;
        private long phienBan = 0;                  // bài 92
        private final List<SuKien> suKienChuaPhat = new ArrayList<>();   // GHI, không PHÁT

        DonHang(MaDonHang ma, MaKhachHang maKhach, long luc) {
            this.ma = ma; this.maKhach = maKhach;
            suKienChuaPhat.add(new DonHangDaTao(ma, maKhach, tongTien(), luc));
        }

        void themDong(String sp, Tien donGia, int sl) {
            Tien sauKhiThem = tongTien().cong(new Tien(donGia.donViNho() * sl, donGia.tienTe()));
            if (sauKhiThem.donViNho() > HAN_MUC.donViNho())
                throw new IllegalStateException("đơn vượt hạn mức");   // BẤT BIẾN — bài 83
            cacDong.add(new DongHang(sp, donGia, sl));
            phienBan++;
        }
        void thanhToan() { trangThai = trangThai.thanhToan(); phienBan++; }
        void giao(long luc) {
            trangThai = trangThai.giao();                // ném thì KHÔNG tới dòng dưới
            suKienChuaPhat.add(new DonHangDaGiao(ma, tongTien(), luc));
            phienBan++;
        }

        Tien tongTien() {
            Tien t = new Tien(0, TienTe.VND);
            for (DongHang d : cacDong) t = t.cong(d.thanhTien());
            return t;
        }
        MaDonHang ma() { return ma; }
        MaKhachHang maKhach() { return maKhach; }
        TrangThai trangThai() { return trangThai; }
        long phienBan() { return phienBan; }
        int soDong() { return cacDong.size(); }
        List<DongHang> cacDong() { return List.copyOf(cacDong); }        // cửa đóng
        List<SuKien> layVaXoaSuKien() {
            List<SuKien> ds = List.copyOf(suKienChuaPhat);
            suKienChuaPhat.clear();
            return ds;
        }
    }

    // =====================================================================
    // MIỀN · SPECIFICATION  (bài 87) và POLICY  (bài 88)
    // =====================================================================
    interface DacTa { boolean thoaMan(DonHang d); String moTa(); }

    record DonTuNhat(long nguong) implements DacTa {
        @Override public boolean thoaMan(DonHang d) { return d.tongTien().donViNho() >= nguong; }
        @Override public String moTa() { return "đơn từ " + nguong + " trở lên"; }
    }
    record TuNDongTroLen(int n) implements DacTa {
        @Override public boolean thoaMan(DonHang d) { return d.soDong() >= n; }
        @Override public String moTa() { return "từ " + n + " dòng hàng trở lên"; }
    }
    record Va(DacTa a, DacTa b) implements DacTa {
        @Override public boolean thoaMan(DonHang d) { return a.thoaMan(d) && b.thoaMan(d); }
        @Override public String moTa() { return "(" + a.moTa() + " VÀ " + b.moTa() + ")"; }
        List<String> lyDoTruot(DonHang d) {
            List<String> r = new ArrayList<>();
            if (!a.thoaMan(d)) r.add(a.moTa());
            if (!b.thoaMan(d)) r.add(b.moTa());
            return r;
        }
    }

    enum QuocGia { VN, US }
    interface ChinhSachThue { Tien tinhThue(Tien tienHang); String moTa(); }
    static final Map<QuocGia, ChinhSachThue> BANG_THUE = new EnumMap<>(QuocGia.class);
    static {
        BANG_THUE.put(QuocGia.VN, new ChinhSachThue() {
            @Override public Tien tinhThue(Tien t) { return t.nhanPhanTram(10); }
            @Override public String moTa() { return "VAT Việt Nam 10%"; }
        });
        BANG_THUE.put(QuocGia.US, new ChinhSachThue() {
            @Override public Tien tinhThue(Tien t) { return new Tien(0, t.tienTe()); }
            @Override public String moTa() { return "không thuế liên bang"; }
        });
    }

    // =====================================================================
    // MIỀN · CỔNG  (bài 98) — nói tiếng nghiệp vụ, không biết hạ tầng
    // =====================================================================
    interface KhoDonHang {
        Optional<DonHang> timTheoMa(MaDonHang ma);
        int luu(DonHang d, long phienBanKyVong);      // trả số dòng — bài 92
    }
    interface BaoChoKhach { void bao(MaKhachHang kh, String noiDung); }
    interface DongHo { long bayGio(); }

    // =====================================================================
    // ỨNG DỤNG  (bài 86) — điều phối; idempotency (91); phát sự kiện SAU khi lưu (84)
    // =====================================================================
    record LenhDatHang(String khoaIdempotency, MaKhachHang maKhach, QuocGia quocGia,
                       List<DongHang> dong) { }
    record KetQuaDatHang(MaDonHang maDon, Tien phaiTra, Tien giamGia, List<String> lyDoKhongGiam) { }

    static final class DichVuDatHang {
        private final KhoDonHang kho;
        private final BaoChoKhach bao;
        private final DongHo dongHo;
        private final Consumer<SuKien> bus;
        private final Map<String, KetQuaDatHang> soIdempotency = new LinkedHashMap<>();
        private final Va duocGiamGia = new Va(new DonTuNhat(1_000_000L), new TuNDongTroLen(2));
        int soLanThucSuXuLy = 0;
        boolean luuThatBai = false;

        DichVuDatHang(KhoDonHang kho, BaoChoKhach bao, DongHo dongHo, Consumer<SuKien> bus) {
            this.kho = kho; this.bao = bao; this.dongHo = dongHo; this.bus = bus;
        }

        KetQuaDatHang thucHien(LenhDatHang l) {
            // Bài 91. Trong hệ thật, hai dòng này phải là MỘT thao tác nguyên tử
            // (`INSERT` + ràng buộc duy nhất); ở đây một luồng nên viết thẳng cho rõ ý.
            KetQuaDatHang daCo = soIdempotency.get(l.khoaIdempotency());
            if (daCo != null) return daCo;                   // phát lại KẾT QUẢ CŨ, không làm lại

            soLanThucSuXuLy++;
            long luc = dongHo.bayGio();
            DonHang d = new DonHang(new MaDonHang("DH-" + soLanThucSuXuLy), l.maKhach(), luc);
            for (DongHang x : l.dong()) d.themDong(x.sanPham(), x.donGia(), x.soLuong());

            Tien giam = duocGiamGia.thoaMan(d) ? d.tongTien().nhanPhanTram(5) : new Tien(0, TienTe.VND);
            Tien sauGiam = d.tongTien().tru(giam);
            Tien phaiTra = sauGiam.cong(BANG_THUE.get(l.quocGia()).tinhThue(sauGiam));

            if (luuThatBai) throw new IllegalStateException("CSDL hỏng");
            kho.luu(d, d.phienBan());
            // LƯU XONG rồi mới phát sự kiện (bài 84) — không sớm hơn một dòng.
            d.layVaXoaSuKien().forEach(bus);
            bao.bao(l.maKhach(), "đã tạo đơn " + d.ma().giaTri());

            KetQuaDatHang kq = new KetQuaDatHang(d.ma(), phaiTra, giam, duocGiamGia.lyDoTruot(d));
            soIdempotency.put(l.khoaIdempotency(), kq);
            return kq;
        }
    }

    // =====================================================================
    // HẠ TẦNG · BỘ NỐI  (bài 98) + MÔ HÌNH ĐỌC  (bài 95)
    // =====================================================================
    record DongDanhSachDon(String maDon, String maKhach, int soDong, long tongTien, String trangThai) { }

    static final class KhoTrongBoNho implements KhoDonHang {
        final Map<String, DonHang> bang = new LinkedHashMap<>();
        final Map<String, Long> phienBan = new LinkedHashMap<>();
        int soLuotTruyVan = 0, soLanDungDo = 0;

        @Override public Optional<DonHang> timTheoMa(MaDonHang ma) {
            soLuotTruyVan++;
            return Optional.ofNullable(bang.get(ma.giaTri()));
        }
        @Override public int luu(DonHang d, long pbKyVong) {
            soLuotTruyVan++;
            Long hienTai = phienBan.get(d.ma().giaTri());
            if (hienTai != null && hienTai != pbKyVong) { soLanDungDo++; return 0; }  // bài 92
            bang.put(d.ma().giaTri(), d);
            phienBan.put(d.ma().giaTri(), pbKyVong);
            return 1;
        }
        /** Đường ĐỌC: một truy vấn, mô hình phẳng — bài 95. */
        List<DongDanhSachDon> danhSach() {
            soLuotTruyVan++;
            List<DongDanhSachDon> ra = new ArrayList<>();
            for (DonHang d : bang.values())
                ra.add(new DongDanhSachDon(d.ma().giaTri(), d.maKhach().giaTri(),
                        d.soDong(), d.tongTien().donViNho(), d.trangThai().name()));
            return ra;
        }
    }
    static final class BaoGia implements BaoChoKhach {
        final List<String> daBao = new ArrayList<>();
        @Override public void bao(MaKhachHang kh, String nd) { daBao.add(kh.giaTri() + ":" + nd); }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        KhoTrongBoNho kho = new KhoTrongBoNho();
        BaoGia bao = new BaoGia();
        List<SuKien> daPhat = new ArrayList<>();
        DichVuDatHang dv = new DichVuDatHang(kho, bao, () -> 1_700_000_000L, daPhat::add);

        List<DongHang> gioHang = List.of(
                new DongHang("laptop", new Tien(20_000_000L, TienTe.VND), 1),
                new DongHang("chuột", new Tien(500_000L, TienTe.VND), 2));

        // ---- 1. ĐƯỜNG THUẬN LỢI, ĐẦU TỚI CUỐI ----
        KetQuaDatHang kq = dv.thucHien(new LenhDatHang("KEY-1", new MaKhachHang("KH-01"),
                QuocGia.VN, gioHang));
        // 21.000.000 -> giảm 5% = 1.050.000 -> còn 19.950.000 -> +10% thuế = 21.945.000
        check(kq.giamGia().donViNho() == 1_050_000L, "đủ điều kiện giảm 5% (bài 87)");
        check(kq.phaiTra().donViNho() == 21_945_000L, "giảm giá TRƯỚC, thuế SAU (bài 88)");
        check(kho.bang.size() == 1, "đơn đã được lưu qua CỔNG, không qua CSDL nào (bài 98)");
        check(bao.daBao.size() == 1, "khách được báo ở tầng ỨNG DỤNG (bài 86)");
        check(daPhat.size() == 1 && daPhat.get(0) instanceof DonHangDaTao,
                "sự kiện được PHÁT SAU KHI LƯU (bài 84)");

        // ---- 2. BẤT BIẾN CỦA AGGREGATE (bài 83) ----
        DonHang don = kho.timTheoMa(kq.maDon()).orElseThrow();
        boolean vuot = false;
        try { don.themDong("máy chủ", new Tien(40_000_000L, TienTe.VND), 1); }
        catch (IllegalStateException e) { vuot = true; }
        check(vuot && don.tongTien().donViNho() == 21_000_000L, "vượt hạn mức bị chặn, dữ liệu nguyên vẹn");
        boolean cuaDong = false;
        try { don.cacDong().add(new DongHang("lén", new Tien(1, TienTe.VND), 1)); }
        catch (UnsupportedOperationException e) { cuaDong = true; }
        check(cuaDong, "cửa aggregate đóng: không sửa được ruột từ ngoài");

        // ---- 3. MÁY TRẠNG THÁI (bài 89) ----
        boolean khongGiaoDuoc = false;
        try { don.giao(1L); } catch (IllegalStateException e) { khongGiaoDuoc = true; }
        check(khongGiaoDuoc, "chưa thanh toán thì chưa giao — mặc định là TỪ CHỐI");
        don.thanhToan();
        don.giao(1_700_000_100L);
        check(don.trangThai() == TrangThai.DA_GIAO, "và đường hợp lệ thì đi được");
        check(don.layVaXoaSuKien().size() == 1, "chuyển trạng thái GHI sự kiện, không phát");

        // ---- 4. TIỀN TỆ (bài 90) ----
        boolean khacTe = false;
        try { new Tien(1, TienTe.VND).cong(new Tien(1, TienTe.USD)); }
        catch (IllegalArgumentException e) { khacTe = true; }
        check(khacTe, "cộng khác tệ bị chặn");
        check(0.1 + 0.2 != 0.3, "và đó là lý do không dùng double cho tiền");

        // ---- 5. SPECIFICATION GIẢI THÍCH ĐƯỢC (bài 87) ----
        KetQuaDatHang nho = dv.thucHien(new LenhDatHang("KEY-2", new MaKhachHang("KH-02"),
                QuocGia.VN, List.of(new DongHang("bút", new Tien(10_000L, TienTe.VND), 1))));
        check(nho.giamGia().donViNho() == 0, "đơn nhỏ: không giảm");
        check(nho.lyDoKhongGiam().size() == 2, "và nói rõ TRƯỢT Ở HAI mệnh đề nào");
        check(nho.lyDoKhongGiam().get(1).equals("từ 2 dòng hàng trở lên"), "dán thẳng vào thông báo");

        // ---- 6. POLICY THEO QUỐC GIA (bài 88) ----
        KetQuaDatHang myQuoc = dv.thucHien(new LenhDatHang("KEY-3", new MaKhachHang("KH-03"),
                QuocGia.US, gioHang));
        check(myQuoc.phaiTra().donViNho() == 19_950_000L, "Mỹ: giảm 5%, không thuế");
        for (QuocGia q : QuocGia.values()) check(BANG_THUE.containsKey(q), "đủ chính sách cho " + q);

        // ---- 7. IDEMPOTENCY (bài 91) ----
        int truoc = dv.soLanThucSuXuLy;
        KetQuaDatHang lai = dv.thucHien(new LenhDatHang("KEY-1", new MaKhachHang("KH-01"),
                QuocGia.VN, gioHang));
        check(dv.soLanThucSuXuLy == truoc, "gửi lại cùng khoá: KHÔNG xử lý lần nữa");
        check(lai.equals(kq), "và trả về ĐÚNG kết quả cũ, không phải lỗi 'đã xử lý'");
        check(kho.bang.size() == 3, "vẫn đúng 3 đơn, không sinh đơn thứ tư");

        // ---- 8. KHOÁ LẠC QUAN (bài 92) ----
        DonHang d2 = kho.timTheoMa(new MaDonHang("DH-2")).orElseThrow();
        check(kho.luu(d2, d2.phienBan()) == 1, "ghi với phiên bản đúng: 1 dòng");
        check(kho.luu(d2, d2.phienBan() - 1) == 0, "ghi với phiên bản CŨ: 0 DÒNG — đụng độ");
        check(kho.soLanDungDo == 1, "và đụng độ được ĐẾM, không im lặng");

        // ---- 9. SỰ KIỆN PHÁT SAU COMMIT (bài 84) ----
        int emailTruoc = bao.daBao.size(), suKienTruoc = daPhat.size();
        dv.luuThatBai = true;
        boolean hong = false;
        try { dv.thucHien(new LenhDatHang("KEY-9", new MaKhachHang("KH-09"), QuocGia.VN, gioHang)); }
        catch (IllegalStateException e) { hong = true; }
        dv.luuThatBai = false;
        check(hong, "lưu hỏng");
        check(bao.daBao.size() == emailTruoc, "-> 0 email được gửi");
        check(daPhat.size() == suKienTruoc, "-> 0 sự kiện rời khỏi tiến trình");

        // ---- 10. MÔ HÌNH ĐỌC (bài 95) ----
        int tvTruoc = kho.soLuotTruyVan;
        List<DongDanhSachDon> danhSach = kho.danhSach();
        check(kho.soLuotTruyVan - tvTruoc == 1, "màn hình danh sách: ĐÚNG MỘT lượt truy vấn");
        check(danhSach.size() == 3 && danhSach.get(0).maKhach().equals("KH-01"),
                "và mô hình đọc GHÉP hai aggregate — điều bên ghi bị cấm");

        // ---- 11. BÀI TEST KIẾN TRÚC (bài 93, 94, 98) ----
        List<Class<?>> mien = List.of(DonHang.class, Tien.class, DongHang.class, TrangThai.class);
        List<Class<?>> haTang = List.of(KhoTrongBoNho.class, BaoGia.class, DongDanhSachDon.class);
        List<String> viPham = new ArrayList<>();
        for (Class<?> lop : mien)
            for (Field f : lop.getDeclaredFields())
                if (haTang.contains(f.getType())) viPham.add(lop.getSimpleName() + "." + f.getName());
        check(viPham.isEmpty(), "0 tham chiếu từ MIỀN ra HẠ TẦNG: " + viPham);
        for (Class<?> lop : SuKien.class.getPermittedSubclasses())
            check(lop.getSimpleName().contains("Da"), "tên sự kiện ở thì quá khứ (bài 81)");

        // ---- 12. VÌ SAO 20 BÀI NÀY LÀ MỘT THIẾT KẾ, KHÔNG PHẢI 20 MẪU ----
        //
        //   Có BẤT BIẾN "tổng ≤ hạn mức"        -> phải có RANH GIỚI aggregate    (83)
        //   Ranh giới -> tham chiếu BẰNG ID     -> hai aggregate không nói trực tiếp
        //   Không nói trực tiếp                 -> phải có SỰ KIỆN MIỀN            (84)
        //   Sự kiện giao ít nhất một lần        -> người nghe phải IDEMPOTENT      (91)
        //   Một transaction một aggregate       -> quy trình nhiều bước cần SAGA   (97)
        //   Nhiều người cùng sửa                -> cần KHOÁ LẠC QUAN               (92)
        //   Aggregate tải trọn vẹn              -> màn hình danh sách cần CQRS     (95)
        //   Luật đổi theo ngữ cảnh              -> POLICY, không phải if-else      (88)
        //   Luật cần giải thích + dịch sang SQL -> SPECIFICATION                   (87)
        //   Miền phải test được không CSDL      -> CỔNG & BỘ NỐI                   (98)
        //   Test không CSDL                     -> test miền chỉ là hàm + assert   (99)
        //
        // Rút một mắt xích ra thì mắt kế bên mất lý do tồn tại. Đó là điều mà học từng
        // mẫu thiết kế riêng lẻ không bao giờ nói cho bạn.
        check(dv.soLanThucSuXuLy == 4 && kho.bang.size() == 3,
                "4 lần vào xử lý, 3 đơn được lưu — lần thứ tư hỏng và KHÔNG để lại gì");

        System.out.println("OK");
    }
}
