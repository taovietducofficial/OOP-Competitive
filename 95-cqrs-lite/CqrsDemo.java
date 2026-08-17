/*
 * Ngôn ngữ: Java
 * Công dụng: CQRS mức nhẹ — tách MÔ HÌNH ĐỌC khỏi MÔ HÌNH GHI, vẫn dùng chung một CSDL.
 * Bài cho nổ ba con bug: dựng màn hình danh sách bằng aggregate làm 501 lượt truy vấn và
 * tải 3.500 object để hiện 500 dòng; thêm một cột hiển thị làm bẩn mô hình miền; và mô
 * hình đọc bị dùng để GHI nên mọi bất biến bị vượt mặt.
 * Tại sao cần học: đây là bài trả lời một câu hỏi mà bài 83 và 85 cố tình để lại. Ở đó,
 * aggregate là đơn vị NHẤT QUÁN và repository chỉ trả về aggregate root — rất tốt cho
 * việc ghi, và tệ hại cho màn hình danh sách. Sai lầm phổ biến nhất là cố làm aggregate
 * phục vụ cả hai, và kết quả là mô hình miền phình ra vì nhu cầu hiển thị. CQRS-lite
 * nói: đừng. Ghi đi một đường, đọc đi một đường, và hai bên KHÔNG dùng chung mô hình.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CqrsDemo {

    // =====================================================================
    // BÊN GHI — aggregate, đúng như bài 83: có bất biến, có hành vi, có ranh giới
    // =====================================================================
    record DongHang(String sanPham, long donGia, int soLuong) {
        long thanhTien() { return donGia * soLuong; }
    }

    static final class DonHang {
        static final long HAN_MUC = 50_000_000L;
        private final String ma;
        private final String maKhach;              // tham chiếu aggregate khác BẰNG ID
        private final List<DongHang> cacDong = new ArrayList<>();
        private String trangThai = "MOI_TAO";

        DonHang(String ma, String maKhach) { this.ma = ma; this.maKhach = maKhach; }

        void themDong(String sp, long donGia, int sl) {
            if (tongTien() + donGia * sl > HAN_MUC)
                throw new IllegalStateException("đơn vượt hạn mức");
            cacDong.add(new DongHang(sp, donGia, sl));
        }
        void giao() { trangThai = "DA_GIAO"; }
        long tongTien() { return cacDong.stream().mapToLong(DongHang::thanhTien).sum(); }
        String ma() { return ma; }
        String maKhach() { return maKhach; }
        String trangThai() { return trangThai; }
        int soDong() { return cacDong.size(); }
    }

    record KhachHang(String ma, String ten) { }

    // =====================================================================
    // BÊN ĐỌC — mô hình PHẲNG, dựng riêng cho MỘT màn hình
    // =====================================================================
    /**
     * Không có hành vi, không có bất biến, không có setter. Nó không phải entity, không
     * phải value object của miền — nó là MỘT DÒNG TRÊN MÀN HÌNH, và chỉ có thế.
     * Chú ý: nó ghép dữ liệu của HAI aggregate (đơn hàng + khách hàng) — điều mà bên ghi
     * bị cấm làm (bài 83), và bên đọc thì hoàn toàn được phép.
     */
    record DongDanhSachDon(String maDon, String tenKhach, int soDong,
                           long tongTien, String trangThai) { }

    // =====================================================================
    // "CSDL" giả — đếm số lượt truy vấn và số object đã tải
    // =====================================================================
    static final class Csdl {
        final Map<String, DonHang> donHang = new LinkedHashMap<>();
        final Map<String, KhachHang> khachHang = new LinkedHashMap<>();
        int soLuotTruyVan = 0, soObjectDaTai = 0;

        void datLai() { soLuotTruyVan = 0; soObjectDaTai = 0; }

        /** Đường GHI: tải aggregate TRỌN VẸN (bắt buộc, để kiểm bất biến — bài 83). */
        DonHang taiDon(String ma) {
            soLuotTruyVan++;
            DonHang d = donHang.get(ma);
            soObjectDaTai += 1 + d.soDong();       // root + các dòng con
            return d;
        }
        KhachHang taiKhach(String ma) {
            soLuotTruyVan++;
            soObjectDaTai += 1;
            return khachHang.get(ma);
        }

        /** Đường ĐỌC: MỘT truy vấn, trả về đúng những cột màn hình cần. */
        List<DongDanhSachDon> truyVanDanhSach() {
            soLuotTruyVan++;                        // đúng 1 — dù có bao nhiêu đơn
            List<DongDanhSachDon> ra = new ArrayList<>();
            for (DonHang d : donHang.values()) {
                soObjectDaTai += 1;                 // đúng 1 dòng phẳng cho mỗi đơn
                ra.add(new DongDanhSachDon(d.ma(), khachHang.get(d.maKhach()).ten(),
                        d.soDong(), d.tongTien(), d.trangThai()));
            }
            return ra;
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        Csdl db = new Csdl();
        for (int i = 0; i < 500; i++) {
            db.khachHang.put("KH-" + i, new KhachHang("KH-" + i, "Khách " + i));
            DonHang d = new DonHang("DH-" + i, "KH-" + i);
            d.themDong("laptop", 1_000_000L, 1);
            d.themDong("chuột", 200_000L, 2);
            d.themDong("bàn phím", 300_000L, 1);
            db.donHang.put("DH-" + i, d);
        }

        // ---- 1. CON BUG: dựng màn hình danh sách bằng AGGREGATE ----
        // Màn hình cần 5 cột: mã đơn, tên khách, số dòng, tổng tiền, trạng thái.
        db.datLai();
        List<DongDanhSachDon> quaAggregate = new ArrayList<>();
        for (String ma : db.donHang.keySet()) {
            DonHang d = db.taiDon(ma);                       // 1 truy vấn / đơn
            KhachHang k = db.taiKhach(d.maKhach());          // + 1 truy vấn / đơn  <- N+1
            quaAggregate.add(new DongDanhSachDon(d.ma(), k.ten(), d.soDong(),
                    d.tongTien(), d.trangThai()));
        }
        check(db.soLuotTruyVan == 1000, "1.000 lượt truy vấn cho MỘT màn hình");
        check(db.soObjectDaTai == 2500, "và 2.500 object được dựng: 500 đơn × (1 root + 3 dòng) + 500 khách");
        check(quaAggregate.size() == 500, "để hiển thị đúng 500 dòng");
        // Đây là bài toán N+1 kinh điển, và nó KHÔNG phải lỗi của ORM — nó là hệ quả trực
        // tiếp của việc dùng mô hình GHI để trả lời một câu hỏi ĐỌC. Aggregate bắt buộc
        // phải tải trọn vẹn (bài 83), nên mỗi đơn kéo theo cả các dòng hàng mà màn hình
        // chỉ cần biết SỐ LƯỢNG của chúng.

        // ---- 2. BẢN ĐÚNG: một truy vấn, một mô hình phẳng ----
        db.datLai();
        List<DongDanhSachDon> quaModelDoc = db.truyVanDanhSach();
        check(db.soLuotTruyVan == 1, "ĐÚNG MỘT lượt truy vấn");
        check(db.soObjectDaTai == 500, "và đúng 500 object — mỗi dòng màn hình một object");
        check(quaModelDoc.size() == quaAggregate.size(), "cùng kết quả");
        check(quaModelDoc.get(0).equals(quaAggregate.get(0)), "cùng nội dung, từng dòng một");
        check(2500 / 500 == 5, "gấp 5 lần số object, và gấp 1.000 lần số lượt truy vấn");

        // ---- 3. MÔ HÌNH ĐỌC ĐƯỢC PHÉP LÀM ĐIỀU BÊN GHI BỊ CẤM ----
        // `DongDanhSachDon` ghép dữ liệu của HAI aggregate: đơn hàng và khách hàng.
        // Ở bên ghi, điều đó bị cấm (bài 83: tham chiếu bằng id, một transaction một
        // aggregate). Ở bên đọc, nó hoàn toàn hợp lệ — vì mô hình đọc KHÔNG BAO GIỜ GHI,
        // nên nó không có bất biến nào để giữ, không có ranh giới transaction nào để tôn trọng.
        check(quaModelDoc.get(0).tenKhach().equals("Khách 0"), "tên khách nằm ngay trong dòng đọc");
        check(DongDanhSachDon.class.getRecordComponents().length == 5, "5 cột, đúng bằng màn hình");
        // Đây là điểm giải phóng lớn nhất của CQRS: bên đọc được ghép bảng thoải mái, được
        // đọc chéo ngữ cảnh, được lưu dữ liệu trùng lặp — và không có gì trong số đó gây
        // hại, vì nó không phải nguồn sự thật.

        // ---- 4. CON BUG: thêm cột hiển thị làm BẨN mô hình miền ----
        // Màn hình cần thêm cột "tên khách". Với mô hình dùng chung, phản xạ là thêm
        // `tenKhach` vào `DonHang` "cho tiện".
        //   - `DonHang` giờ giữ dữ liệu của aggregate khác -> phá bài 83;
        //   - tên khách đổi thì phải cập nhật mọi đơn hàng cũ -> hoặc là hiển thị sai;
        //   - và không ai biết `DonHang.tenKhach` là bản chụp lúc đặt hay giá trị hiện tại.
        // Với mô hình đọc: thêm một field vào `DongDanhSachDon`, sửa một câu truy vấn.
        // Miền không đổi một chữ.
        check(DonHang.class.getDeclaredFields().length == 5, "DonHang giữ đúng những gì nghiệp vụ cần");
        // Ghi chú: nếu nghiệp vụ THẬT SỰ cần "tên khách tại thời điểm đặt" (hoá đơn phải
        // in đúng tên lúc đó), thì đó là một value object của miền, không phải nhu cầu
        // hiển thị — và lúc đó nó thuộc về `DonHang`. Phân biệt được hai trường hợp này
        // là toàn bộ kỹ năng: câu hỏi là "nghiệp vụ có cần không", không phải "màn hình
        // có hiện không".

        // ---- 5. CON BUG: dùng mô hình ĐỌC để GHI ----
        // `DongDanhSachDon` là `record`: không setter, không hành vi. Dòng
        //     dong.tongTien = 999;
        // KHÔNG BIÊN DỊCH ĐƯỢC. Đó là chủ ý.
        //
        // Nếu nó có setter và ai đó "cập nhật cho nhanh" qua nó, thì bất biến hạn mức
        // (`themDong`) bị vượt mặt hoàn toàn — và đó là bài 83 phần 2 quay lại.
        DonHang don = db.taiDon("DH-0");
        boolean chan = false;
        try { don.themDong("máy chủ", 60_000_000L, 1); }
        catch (IllegalStateException e) { chan = true; }
        check(chan, "bên GHI vẫn giữ bất biến — mọi thay đổi phải đi qua aggregate");
        check(quaModelDoc.get(0).tongTien() == 1_700_000L, "bên ĐỌC chỉ nhìn, không đụng vào");

        // ---- 6. MÔ HÌNH ĐỌC ĐƯỢC PHÉP CŨ ----
        // Ở bản CQRS-lite này, mô hình đọc dựng từ CÙNG một CSDL nên luôn tươi. Bước tiếp
        // theo — bảng đọc riêng, cập nhật bằng sự kiện (bài 84) — thì không:
        List<DongDanhSachDon> anhChup = db.truyVanDanhSach();      // màn hình vừa tải xong
        db.taiDon("DH-1").giao();                                  // ai đó giao hàng NGAY SAU đó
        check(anhChup.get(1).trangThai().equals("MOI_TAO"), "màn hình vẫn hiện trạng thái CŨ");
        check(db.donHang.get("DH-1").trangThai().equals("DA_GIAO"), "trong khi sự thật đã đổi");
        // Câu hỏi phải hỏi nghiệp vụ, KHÔNG được tự quyết: "màn hình này cũ 2 giây có sao
        // không?" Với danh sách đơn hàng thì thường là không. Với số dư tài khoản trước
        // khi bấm nút chuyển tiền thì CÓ — và chỗ đó phải đọc từ bên ghi.
        //
        // Quy tắc: đọc để HIỂN THỊ thì dùng mô hình đọc; đọc để RA QUYẾT ĐỊNH GHI thì phải
        // tải aggregate (và có khoá lạc quan — bài 92).

        // ---- 7. "LITE" NGHĨA LÀ GÌ, VÀ RANH GIỚI Ở ĐÂU ----
        //
        //   Mức                | Kho ghi | Kho đọc     | Độ trễ | Chi phí
        //   -------------------|---------|-------------|--------|------------------
        //   Không tách         | chung   | chung       | 0      | N+1, miền bị bẩn
        //   CQRS-LITE (bài này)| chung   | chung       | 0      | thêm mô hình đọc + truy vấn
        //   CQRS đầy đủ        | chung   | RIÊNG       | có     | đồng bộ, hạ tầng, vận hành
        //
        // Hàng giữa giải quyết được 90% vấn đề với gần như không có chi phí vận hành: vẫn
        // một CSDL, một transaction, dữ liệu luôn tươi — chỉ là ĐƯỜNG ĐỌC không đi qua
        // aggregate. Đừng nhảy sang hàng cuối khi chưa đo được rằng hàng giữa không đủ.
        int soKhoLite = 1, soKhoDayDu = 2;
        check(soKhoLite < soKhoDayDu, "lite: một kho, không đồng bộ, không độ trễ");

        // ---- 8. LUẬT NGHIỆP VỤ KHÔNG ĐƯỢC NẰM Ở BÊN ĐỌC ----
        // Cám dỗ: câu truy vấn danh sách tính luôn "đơn nào được giảm giá". Đừng — lúc đó
        // luật giảm giá có hai bản: một trong miền, một trong SQL, và chúng sẽ lệch
        // (bài 87 phần 2). Bên đọc chỉ được TRÌNH BÀY thứ bên ghi đã quyết định.
        //
        // Phép thử: nếu xoá toàn bộ mô hình đọc đi, hệ thống có còn ĐÚNG không (chỉ chậm
        // và xấu)? Nếu câu trả lời là "không, mất luôn luật X" thì luật X đang nằm sai chỗ.
        long tongTuBenGhi = db.donHang.get("DH-0").tongTien();
        long tongTuBenDoc = quaModelDoc.get(0).tongTien();
        check(tongTuBenGhi == tongTuBenDoc, "bên đọc TRÌNH BÀY lại con số bên ghi tính ra, không tự tính luật");

        System.out.println("OK");
    }
}
