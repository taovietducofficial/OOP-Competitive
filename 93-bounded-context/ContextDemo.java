/*
 * Ngôn ngữ: Java
 * Công dụng: Bounded context — cùng chữ "khách hàng", ba nghĩa khác nhau ở ba đội. Bài
 * cho nổ ba con bug của "một mô hình dùng chung cho cả công ty": đội bán hàng không tạo
 * nổi một khách tiềm năng vì thiếu mã số thuế; chữ "hoàn tất" mang hai nghĩa nên một
 * trong hai đội luôn đọc sai; và mỗi lần thêm một field là ba đội phải cùng lên lịch.
 * Tại sao cần học: đây là bài học kiến trúc đắt nhất trong tầng này, vì nó đi ngược
 * trực giác. Trực giác nói "đừng lặp lại chính mình — một khách hàng thì phải có MỘT
 * lớp KhachHang". Thực tế nói ngược lại: hai đội nói hai ngôn ngữ khác nhau về cùng một
 * con người, và ép họ dùng chung một lớp không loại bỏ sự khác nhau đó — nó chỉ giấu sự
 * khác nhau vào trong những field mà nửa số đội phải bỏ trống.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContextDemo {

    // =====================================================================
    // SAI — MỘT mô hình dùng chung cho cả công ty
    // =====================================================================
    static final class KhachHangChung {
        // Bán hàng cần:
        final String maKhach, ten, nguonKhach, giaiDoanBan;
        // Kế toán cần:
        final String tenPhapNhan, maSoThue, diaChiXuatHoaDon, dieuKhoanThanhToan;
        // Hỗ trợ cần:
        final String email, hangUuTien, ngonNguGiaoTiep;
        // Và một chữ mà cả ba đội cùng dùng, mỗi đội hiểu một kiểu:
        final boolean hoanTat;

        KhachHangChung(String maKhach, String ten, String nguonKhach, String giaiDoanBan,
                       String tenPhapNhan, String maSoThue, String diaChiXuatHoaDon,
                       String dieuKhoanThanhToan, String email, String hangUuTien,
                       String ngonNguGiaoTiep, boolean hoanTat) {
            // Kế toán yêu cầu mã số thuế bắt buộc — hoàn toàn hợp lý VỚI KẾ TOÁN.
            if (maSoThue == null || maSoThue.isBlank())
                throw new IllegalArgumentException("mã số thuế là bắt buộc");
            this.maKhach = maKhach; this.ten = ten; this.nguonKhach = nguonKhach;
            this.giaiDoanBan = giaiDoanBan; this.tenPhapNhan = tenPhapNhan;
            this.maSoThue = maSoThue; this.diaChiXuatHoaDon = diaChiXuatHoaDon;
            this.dieuKhoanThanhToan = dieuKhoanThanhToan; this.email = email;
            this.hangUuTien = hangUuTien; this.ngonNguGiaoTiep = ngonNguGiaoTiep;
            this.hoanTat = hoanTat;
        }
    }

    // =====================================================================
    // ĐÚNG — mỗi bounded context một mô hình, nối nhau BẰNG MÃ
    // =====================================================================

    /** Ngữ cảnh BÁN HÀNG. "Khách hàng" ở đây là một CƠ HỘI đang được theo đuổi. */
    static final class BanHang {
        record KhachHang(String maKhach, String ten, String nguonKhach, GiaiDoan giaiDoan) {
            KhachHang {
                if (ten == null || ten.isBlank())
                    throw new IllegalArgumentException("khách tiềm năng phải có tên");
            }
            /** Với BÁN HÀNG, "hoàn tất" nghĩa là ĐÃ CHỐT ĐƠN. */
            boolean daHoanTat() { return giaiDoan == GiaiDoan.DA_CHOT; }
        }
        enum GiaiDoan { TIEM_NANG, DANG_TU_VAN, DA_CHOT, DA_MAT }
    }

    /** Ngữ cảnh KẾ TOÁN. "Khách hàng" ở đây là một PHÁP NHÂN xuất hoá đơn được. */
    static final class KeToan {
        record BenNhanHoaDon(String maKhach, String tenPhapNhan, String maSoThue,
                             String diaChiXuatHoaDon, boolean daThuDuTien) {
            BenNhanHoaDon {
                if (maSoThue == null || maSoThue.isBlank())
                    throw new IllegalArgumentException("bên nhận hoá đơn phải có mã số thuế");
            }
            /** Với KẾ TOÁN, "hoàn tất" nghĩa là ĐÃ THU ĐỦ TIỀN. */
            boolean daHoanTat() { return daThuDuTien; }
        }
    }

    /** Ngữ cảnh HỖ TRỢ. "Khách hàng" ở đây là một NGƯỜI có thể mở phiếu hỗ trợ. */
    static final class HoTro {
        record NguoiDung(String maKhach, String email, int mucUuTien) { }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: đội bán hàng không tạo nổi một khách tiềm năng ----
        // 9h sáng ở hội chợ. Nhân viên bán hàng gặp một người, có tên và số điện thoại,
        // muốn ghi lại ngay. Người đó chưa phải khách, chưa có công ty, chưa có mã số thuế.
        boolean biChan = false;
        try {
            new KhachHangChung("KH-01", "Chị Hoa ở hội chợ", "hội chợ", "TIEM_NANG",
                    null, null, null, null, null, null, null, false);
        } catch (IllegalArgumentException e) { biChan = true; }
        check(biChan, "không tạo được: mô hình chung đòi mã số thuế");
        // Luật "mã số thuế là bắt buộc" HOÀN TOÀN ĐÚNG — với kế toán. Nó chỉ sai khi bị
        // áp lên một ngữ cảnh mà khái niệm "khách hàng" còn chưa có nghĩa đó.
        //
        // Cách vá mà mọi dự án đều làm, và vì sao nó tệ hơn: đặt `maSoThue` thành tuỳ
        // chọn. Thế là kế toán mất luôn bảo đảm "mọi bên nhận hoá đơn đều có mã số thuế",
        // và phải tự kiểm ở mọi chỗ dùng. Một ràng buộc thật vừa biến thành lời khuyên.

        BanHang.KhachHang ch = new BanHang.KhachHang(
                "KH-01", "Chị Hoa ở hội chợ", "hội chợ", BanHang.GiaiDoan.TIEM_NANG);
        check(ch.ten().equals("Chị Hoa ở hội chợ"), "ngữ cảnh bán hàng: tạo được với 4 field");
        check(BanHang.KhachHang.class.getRecordComponents().length == 4, "và chỉ có 4 field");
        check(KhachHangChung.class.getDeclaredFields().length == 12, "mô hình chung có 12");
        // Bốn so với mười hai. Tám field kia không phải "dữ liệu chưa điền" — chúng là
        // dữ liệu KHÔNG CÓ NGHĨA trong ngữ cảnh này.

        // ---- 2. CON BUG: cùng một chữ, hai nghĩa ----
        // Đơn của chị Hoa: đã chốt bán (bán hàng gọi là "hoàn tất"), nhưng công nợ 30
        // ngày nên chưa thu tiền (kế toán KHÔNG gọi là hoàn tất).
        BanHang.KhachHang daChot = new BanHang.KhachHang(
                "KH-01", "Chị Hoa", "hội chợ", BanHang.GiaiDoan.DA_CHOT);
        KeToan.BenNhanHoaDon chuaThu = new KeToan.BenNhanHoaDon(
                "KH-01", "Công ty Hoa Mai", "0301234567", "12 Lê Lợi", false);

        check(daChot.daHoanTat(), "BÁN HÀNG: hoàn tất = đã chốt -> ĐÚNG");
        check(!chuaThu.daHoanTat(), "KẾ TOÁN: hoàn tất = đã thu tiền -> CHƯA");
        check(daChot.daHoanTat() != chuaThu.daHoanTat(),
                "cùng một khách, cùng một chữ, hai câu trả lời — và cả hai đều đúng");
        // Với mô hình chung, `hoanTat` là MỘT boolean. Ai gán nó? Đội nào gán thì đội kia
        // đọc sai. Không có cách vá nào ngoài việc tách ra thành hai khái niệm — và tách
        // ra thì đã là hai bounded context rồi.
        //
        // Đây là con bug đúng như bài 81 phần 1, nhưng ở quy mô tổ chức: ở đó là hai lập
        // trình viên hiểu khác nhau, ở đây là hai PHÒNG BAN hiểu khác nhau. Và họ đều đúng.

        // ---- 3. NỐI HAI NGỮ CẢNH BẰNG MÃ, KHÔNG BẰNG OBJECT ----
        check(daChot.maKhach().equals(chuaThu.maKhach()), "cùng một con người ngoài đời");
        // Nhưng KHÔNG có phương thức nào đi từ `BanHang.KhachHang` sang
        // `KeToan.BenNhanHoaDon`. Đây chính là bài 83 (tham chiếu bằng id) nâng lên cấp
        // độ tổ chức: hai ngữ cảnh chia sẻ một ĐỊNH DANH, không chia sẻ một MÔ HÌNH.
        //
        // Và trình biên dịch canh giúp: hai lớp cùng tên `KhachHang` ở hai gói khác nhau
        // là HAI KIỂU khác nhau. Dòng dưới không biên dịch được:
        //     KeToan.BenNhanHoaDon x = daChot;
        //     error: incompatible types: BanHang.KhachHang cannot be converted to ...
        check(!BanHang.KhachHang.class.equals(KeToan.BenNhanHoaDon.class),
                "cùng khái niệm ngoài đời, hai kiểu dữ liệu — và đó là điều ta MUỐN");

        // ---- 4. ĐO CHI PHÍ THAY ĐỔI ----
        // Kế toán cần thêm `dieuKhoanThanhToan`.
        //   Mô hình chung: sửa lớp -> ba đội cùng biên dịch lại, cùng test lại, cùng
        //                  triển khai. Muốn ra bản vá thì phải xếp lịch với hai đội không
        //                  liên quan gì tới thay đổi này.
        //   Tách ngữ cảnh: sửa `KeToan.BenNhanHoaDon` -> đúng một đội, đúng một lần triển khai.
        int doiBiAnhHuongMoHinhChung = 3;
        int doiBiAnhHuongKhiTach = 1;
        check(doiBiAnhHuongMoHinhChung == 3 && doiBiAnhHuongKhiTach == 1, "3 đội so với 1");
        // Đây là con số thật sự quyết định. Mô hình chung không làm code chậm đi — nó làm
        // TỔ CHỨC chậm đi, và đó là thứ đắt hơn nhiều.

        // ---- 5. BẢN ĐỒ NGỮ CẢNH: quan hệ giữa các ngữ cảnh có TÊN ----
        //
        //   Quan hệ            | Nghĩa                                  | Khi nào dùng
        //   -------------------|----------------------------------------|-------------------
        //   Đối tác            | hai đội cùng đổi, cùng chịu trách nhiệm| hai đội cùng công ty
        //   Khách/Nhà cung cấp | thượng nguồn nghe hạ nguồn             | có quyền thương lượng
        //   Tuân thủ           | hạ nguồn dùng y nguyên mô hình trên    | bên trên không đổi được
        //   Chống hư hỏng      | hạ nguồn DỊCH mô hình trên sang của mình| mô hình trên xấu (bài 94)
        //   Nhân chung         | hai đội cùng sở hữu một phần mã dùng chung | rất ít, rất nguy hiểm
        //
        // "Nhân chung" là thứ mọi người bắt đầu và hối hận: một thư viện `common-model`
        // mà ba đội cùng sửa. Nó có mọi nhược điểm của mô hình chung, cộng thêm việc
        // không ai sở hữu nó.
        Map<String, String> banDo = new LinkedHashMap<>();
        banDo.put("BanHang -> KeToan", "Khách/Nhà cung cấp: bán hàng chốt đơn, kế toán xuất hoá đơn");
        banDo.put("KeToan -> CongThue", "Tuân thủ: cơ quan thuế không đổi định dạng vì ta");
        banDo.put("HoTro -> BanHang", "Chống hư hỏng: hỗ trợ tự dịch, không phụ thuộc giai đoạn bán");
        check(banDo.size() == 3, "bản đồ ngữ cảnh là một tài liệu THẬT, vẽ được trên một trang giấy");
        // Nếu không vẽ được bản đồ này cho hệ thống của bạn, thì ranh giới ngữ cảnh chưa
        // tồn tại — chỉ có các gói code cùng dùng chung một mô hình.

        // ---- 6. DỊCH Ở BIÊN, MỖI CHIỀU MỘT LẦN ----
        // Khi bán hàng chốt đơn, kế toán cần một bên nhận hoá đơn. Việc DỊCH nằm ở biên,
        // và nó nhận thêm dữ liệu mà ngữ cảnh nguồn không có (mã số thuế do kế toán thu thập).
        List<KeToan.BenNhanHoaDon> soHoaDon = new ArrayList<>();
        if (daChot.daHoanTat()) {
            soHoaDon.add(new KeToan.BenNhanHoaDon(daChot.maKhach(), "Công ty Hoa Mai",
                    "0301234567", "12 Lê Lợi", false));
        }
        check(soHoaDon.size() == 1, "dịch ở biên: một chiều, một chỗ, có tên");
        check(soHoaDon.get(0).maKhach().equals(daChot.maKhach()), "chỉ MÃ đi qua biên");
        // Chỗ dịch này là nơi DUY NHẤT hai ngôn ngữ gặp nhau, nên nó là nơi duy nhất phải
        // sửa khi một bên đổi. Bài 94 nói kỹ về việc bảo vệ mình khi bên kia có mô hình xấu.

        // ---- 7. KHI NÀO KHÔNG TÁCH NGỮ CẢNH ----
        // Bounded context có chi phí thật: mô hình lặp lại, mã dịch ở biên, dữ liệu đồng
        // bộ trễ. Ba dấu hiệu cho thấy CHƯA nên tách:
        //   - Cả hệ thống do MỘT đội làm, và mọi người dùng cùng một bộ từ ngữ.
        //   - Chưa tìm ra được một từ nào mang hai nghĩa (phép thử ở phần 2).
        //   - Số field mà mỗi bên phải bỏ trống còn nhỏ.
        // Ngược lại, ba dấu hiệu ĐÃ đến lúc tách:
        //   - Có field mà nửa số nơi dùng luôn để `null`;
        //   - Có từ mà bạn phải hỏi lại "ý anh là hoàn tất theo nghĩa nào";
        //   - Một thay đổi nhỏ phải xếp lịch với đội không liên quan.
        check(KhachHangChung.class.getDeclaredFields().length
                > BanHang.KhachHang.class.getRecordComponents().length * 2,
                "mô hình chung phình gấp ba — dấu hiệu rõ nhất và đo được nhất");

        System.out.println("OK");
    }
}
