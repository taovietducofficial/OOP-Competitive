/*
 * Ngôn ngữ: Java
 * Công dụng: Cùng một nghiệp vụ, viết hai lần. Bản đầu dùng từ ngữ của lập trình
 * viên — `DataRecord`, `flag1`, `status = 3`, `processData()`. Bản sau dùng đúng
 * từ ngữ mà người làm nghiệp vụ nói ra miệng. Bài chứng minh bằng một con bug
 * THẬT: hai người đọc `status >= 3` theo hai cách khác nhau, và báo cáo doanh thu
 * cộng nhầm đơn đã huỷ vào doanh thu.
 * Tại sao cần học: "đặt tên cho dễ hiểu" nghe như lời khuyên về thẩm mỹ. Không
 * phải. Khi tên trong code khác tên trong nghiệp vụ, mọi cuộc trao đổi đều cần
 * một bảng dịch trong đầu — và bảng dịch đó là nơi bug sinh ra. Bài này cho thấy
 * bảng dịch ấy tốn bao nhiêu, đo bằng đúng một con số sai trong báo cáo tài chính.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LanguageDemo {

    // =====================================================================
    // BẢN 1 — từ ngữ của LẬP TRÌNH VIÊN
    // =====================================================================
    // Người làm nghiệp vụ đọc file này không hiểu một dòng nào. Và quan trọng hơn:
    // lập trình viên đọc nó cũng phải TRA BẢNG mới biết `3` nghĩa là gì.
    static final class DataRecord {
        String id;
        long amt;
        int status;   // 1=?, 2=?, 3=?, 4=?  <- bảng dịch nằm trong đầu ai đó
        boolean flag1;
        boolean flag2;

        DataRecord(String id, long amt, int status, boolean flag1, boolean flag2) {
            this.id = id; this.amt = amt; this.status = status;
            this.flag1 = flag1; this.flag2 = flag2;
        }
    }

    static final class DataProcessor {
        private final List<DataRecord> records = new ArrayList<>();

        void add(DataRecord r) { records.add(r); }

        // Người viết hàm này hiểu: "status >= 3 nghĩa là đã xong".
        // Và anh ta ĐÚNG — theo cách anh ta hiểu chữ "xong".
        long calcTotal() {
            long t = 0;
            for (DataRecord r : records) if (r.status >= 3) t += r.amt;
            return t;
        }

        // Người viết hàm này (ba tháng sau, đội khác) cũng hiểu "status >= 3 là đã xong".
        int countDone() {
            int n = 0;
            for (DataRecord r : records) if (r.status >= 3) n++;
            return n;
        }

        // Còn hàm này thì đúng, vì người viết nó tình cờ nhớ rằng 4 là huỷ.
        long calcRefundable() {
            long t = 0;
            for (DataRecord r : records) if (r.status == 4) t += r.amt;
            return t;
        }
    }

    // =====================================================================
    // BẢN 2 — từ ngữ của NGƯỜI LÀM NGHIỆP VỤ
    // =====================================================================
    // Đọc enum này lên là hiểu, không cần tra bảng. Và câu hỏi nghiệp vụ được TRẢ
    // LỜI ngay trong enum, thay vì để mỗi nơi gọi tự suy ra từ con số.
    enum TrangThaiDonHang {
        MOI_TAO,
        DA_THANH_TOAN,
        DA_GIAO,
        DA_HUY;

        // Đây là chỗ mấu chốt: câu hỏi nghiệp vụ "đơn này đã hoàn tất chưa" có ĐÚNG MỘT
        // câu trả lời, và nó nằm ở đây. Không nơi gọi nào phải tự định nghĩa lại.
        boolean laHoanTat() { return this == DA_GIAO; }

        // "Kết thúc" và "hoàn tất" là HAI khái niệm khác nhau trong nghiệp vụ — và đó
        // chính là chỗ bản 1 nhầm. Ở đây chúng có hai cái tên khác nhau.
        boolean laKetThuc() { return this == DA_GIAO || this == DA_HUY; }

        boolean duocHoanTien() { return this == DA_HUY; }
    }

    record DonHang(String maDon, long soTien, TrangThaiDonHang trangThai,
                   boolean laKhachThanThiet, boolean giaoNhanhTrongNgay) { }

    static final class SoDonHang {
        private final List<DonHang> cacDon = new ArrayList<>();

        void ghiNhan(DonHang d) { cacDon.add(d); }

        // Tên hàm là câu người làm nghiệp vụ nói ra miệng.
        long doanhThuDaHoanTat() {
            long t = 0;
            for (DonHang d : cacDon) if (d.trangThai().laHoanTat()) t += d.soTien();
            return t;
        }

        int soDonDaKetThuc() {
            int n = 0;
            for (DonHang d : cacDon) if (d.trangThai().laKetThuc()) n++;
            return n;
        }

        long soTienPhaiHoanLai() {
            long t = 0;
            for (DonHang d : cacDon) if (d.trangThai().duocHoanTien()) t += d.soTien();
            return t;
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // Cùng bốn đơn hàng, biểu diễn hai cách.
        //   DH-1: 100.000 đã giao       -> hoàn tất, tính vào doanh thu
        //   DH-2: 200.000 đã thanh toán -> chưa giao, CHƯA tính
        //   DH-3: 300.000 đã huỷ        -> KHÔNG tính vào doanh thu, và phải hoàn tiền
        //   DH-4: 400.000 đã giao       -> hoàn tất
        DataProcessor cu = new DataProcessor();
        cu.add(new DataRecord("DH-1", 100_000, 3, true, false));
        cu.add(new DataRecord("DH-2", 200_000, 2, false, false));
        cu.add(new DataRecord("DH-3", 300_000, 4, true, true));
        cu.add(new DataRecord("DH-4", 400_000, 3, false, true));

        SoDonHang moi = new SoDonHang();
        moi.ghiNhan(new DonHang("DH-1", 100_000, TrangThaiDonHang.DA_GIAO, true, false));
        moi.ghiNhan(new DonHang("DH-2", 200_000, TrangThaiDonHang.DA_THANH_TOAN, false, false));
        moi.ghiNhan(new DonHang("DH-3", 300_000, TrangThaiDonHang.DA_HUY, true, true));
        moi.ghiNhan(new DonHang("DH-4", 400_000, TrangThaiDonHang.DA_GIAO, false, true));

        // ---- 1. CON BUG: `status >= 3` cộng nhầm đơn đã huỷ vào doanh thu ----
        check(cu.calcTotal() == 800_000, "bản cũ: 100.000 + 300.000 + 400.000 = 800.000");
        check(moi.doanhThuDaHoanTat() == 500_000, "bản mới: 100.000 + 400.000 = 500.000");
        check(cu.calcTotal() != moi.doanhThuDaHoanTat(), "HAI CON SỐ KHÁC NHAU — và bản cũ SAI");
        check(cu.calcTotal() - moi.doanhThuDaHoanTat() == 300_000,
                "chênh đúng bằng số tiền của đơn ĐÃ HUỶ");
        // 300.000đ doanh thu không tồn tại vừa đi vào báo cáo tài chính. Không ngoại lệ,
        // không cảnh báo, và cả hai hàm đều "chạy đúng" theo ý người viết chúng.

        // ---- 2. Vì sao nó xảy ra: `>=` giả định thứ tự có Ý NGHĨA ----
        // `status >= 3` chỉ đúng nếu 4 là "xong hơn" 3. Trong nghiệp vụ thì không:
        // ĐÃ HUỶ không phải một dạng "đã giao ở mức cao hơn". Nhưng con số thì không
        // nói được điều đó — nó chỉ có thứ tự, không có ý nghĩa.
        check(TrangThaiDonHang.DA_HUY.ordinal() > TrangThaiDonHang.DA_GIAO.ordinal(),
                "enum cũng có thứ tự, nhưng KHÔNG ai viết `>= DA_GIAO` vì nó đọc lên vô nghĩa");
        check(!TrangThaiDonHang.DA_HUY.laHoanTat(), "phải hỏi laHoanTat(), và câu trả lời rõ ràng");
        // Đây là điểm tinh tế nhất của bài: enum không chỉ "dễ đọc hơn". Nó làm cho phép
        // so sánh SAI trở thành phép so sánh mà không ai muốn viết.

        // ---- 3. Hai khái niệm khác nhau, hai cái tên khác nhau ----
        check(moi.soDonDaKetThuc() == 3, "KẾT THÚC: đã giao (2) + đã huỷ (1) = 3 đơn");
        check(moi.doanhThuDaHoanTat() == 500_000, "HOÀN TẤT: chỉ đã giao");
        check(cu.countDone() == 3, "bản cũ dùng CÙNG điều kiện cho cả hai câu hỏi");
        // `countDone()` tình cờ ĐÚNG (nếu ý là "kết thúc"), còn `calcTotal()` thì SAI.
        // Cùng một biểu thức `status >= 3`, một chỗ đúng một chỗ sai — và không có gì
        // trong code nói cho bạn biết chỗ nào là chỗ nào.

        // ---- 4. Phép thử: ĐỌC TÊN LÊN THÀNH LỜI ----
        //
        //   bản cũ                            | bản mới
        //   ----------------------------------|--------------------------------
        //   "data processor calc total"       | "sổ đơn hàng, doanh thu đã hoàn tất"
        //   "record status greater than 3"    | "trạng thái đơn hàng là hoàn tất"
        //   "flag1"                           | "là khách thân thiết"
        //   "flag2"                           | "giao nhanh trong ngày"
        //
        // Cột trái không đọc được thành câu tiếng Việt. Cột phải thì đọc được — và đó
        // chính là phép thử: NÓI TO câu đó cho người làm nghiệp vụ nghe. Nếu họ gật
        // đầu, tên đúng. Nếu họ hỏi lại "flag1 là gì?", bạn vừa tìm ra một chỗ cần sửa.
        check(moi.soTienPhaiHoanLai() == 300_000, "và câu 'số tiền phải hoàn lại' cũng đọc được");

        // ---- 5. Bảng dịch — thứ đáng lẽ không nên tồn tại ----
        // Với bản cũ, mọi cuộc trao đổi đều cần bảng này, và nó chỉ tồn tại trong đầu
        // vài người:
        Map<String, String> bangDich = new LinkedHashMap<>();
        bangDich.put("DataRecord", "đơn hàng");
        bangDich.put("amt", "số tiền");
        bangDich.put("status=1", "mới tạo");
        bangDich.put("status=2", "đã thanh toán");
        bangDich.put("status=3", "đã giao");
        bangDich.put("status=4", "đã huỷ");
        bangDich.put("flag1", "khách thân thiết");
        bangDich.put("flag2", "giao nhanh trong ngày");
        bangDich.put("calcTotal", "doanh thu (?)");
        check(bangDich.size() == 9, "chín mục phải nhớ, chỉ cho MỘT lớp");

        // Với bản mới, bảng dịch RỖNG — tên trong code CHÍNH LÀ tên nghiệp vụ.
        Map<String, String> bangDichMoi = new LinkedHashMap<>();
        check(bangDichMoi.isEmpty(), "bảng dịch rỗng — đó là toàn bộ mục tiêu của bài này");
        // Ngôn ngữ chung không phải là "đặt tên tiếng Việt". Nó là: KHÔNG CÓ bảng dịch
        // nào giữa lời người làm nghiệp vụ nói và chữ trong mã nguồn.

        // ---- 6. Kiểm chứng bằng máy: mọi từ nghiệp vụ đều xuất hiện trong API ----
        List<String> tuNghiepVu = List.of("donHang", "doanhThu", "hoanTat", "huy", "hoanTien",
                "thanThiet", "giaoNhanh");
        List<String> tenTrongApi = new ArrayList<>();
        // Tên LỚP cũng là một phần của ngôn ngữ — thường là phần quan trọng nhất,
        // vì nó đặt tên cho chính KHÁI NIỆM chứ không chỉ cho một thao tác.
        for (var lop : List.of(SoDonHang.class, DonHang.class, TrangThaiDonHang.class)) {
            tenTrongApi.add(lop.getSimpleName());
            for (var m : lop.getDeclaredMethods()) tenTrongApi.add(m.getName());
        }
        for (var e : TrangThaiDonHang.values()) tenTrongApi.add(e.name());

        String tatCa = String.join(" ", tenTrongApi).toLowerCase();
        for (String tu : tuNghiepVu) {
            check(tatCa.contains(tu.toLowerCase()), "từ nghiệp vụ '" + tu + "' có trong API");
        }
        // Sáu dòng trên là một bài test dùng được thật: nó chặn việc ai đó thêm một khái
        // niệm nghiệp vụ mới mà đặt tên là `tmp2` hoặc `handleCase3`.

        // ---- 7. Ranh giới: từ ngữ nào KHÔNG thuộc ngôn ngữ chung ----
        // Không phải mọi tên đều phải là tiếng nghiệp vụ. Ba loại tên nằm ngoài:
        //   - thuật toán và cấu trúc dữ liệu: `binarySearch`, `LinkedHashMap` — đó là
        //     ngôn ngữ chung của LẬP TRÌNH VIÊN, và nó cũng là một ngôn ngữ chung hợp lệ;
        //   - biến cục bộ ngắn trong một vòng lặp ba dòng: `i`, `n`, `t`;
        //   - hạ tầng: `connectionPool`, `retryPolicy`.
        // Ngôn ngữ chung áp cho MÔ HÌNH MIỀN — nơi người làm nghiệp vụ và lập trình viên
        // phải nói chuyện được với nhau. Ép nó lên mọi dòng code là hiểu sai.
        check(bangDich.containsKey("flag1"), "`flag1` là tên miền -> phải sửa");
        check(!bangDich.containsKey("LinkedHashMap"), "`LinkedHashMap` là tên kỹ thuật -> giữ nguyên");

        // ---- 8. Và luật quan trọng nhất: NGÔN NGỮ ĐI HAI CHIỀU ----
        // Nếu người làm nghiệp vụ nói "đơn treo", mà trong code không có khái niệm đó,
        // thì hoặc bạn thiếu một trạng thái, hoặc họ đang dùng một từ mà chính họ cũng
        // chưa định nghĩa rõ. Cả hai trường hợp đều là một cuộc trao đổi cần xảy ra —
        // và mã nguồn vừa làm lộ ra điều đó.
        //
        // Ngược lại, nếu trong code có `TRANG_THAI_TAM` mà không ai bên nghiệp vụ biết
        // nó là gì, thì đó là một khái niệm do lập trình viên bịa ra — và nó sẽ trôi
        // dần khỏi thực tế cho tới lúc gây ra một con số sai như ở phần 1.
        check(TrangThaiDonHang.values().length == 4, "bốn trạng thái, và cả bốn đều có tên nghiệp vụ");

        System.out.println("OK");
    }
}
