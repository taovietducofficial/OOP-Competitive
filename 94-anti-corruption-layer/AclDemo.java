/*
 * Ngôn ngữ: Java
 * Công dụng: Lớp chống hư hỏng (anti-corruption layer) — bọc hệ ngoài lại để mô hình xấu
 * của họ không lây vào miền của mình. Bài cho nổ ba con bug: mã trạng thái dạng chuỗi
 * của đối tác nằm rải rác 12 chỗ nên họ thêm một mã là 12 chỗ hỏng; số tiền dạng chuỗi
 * được phân tích ở năm nơi, một nơi quên `null`; và một khái niệm chỉ đối tác mới có
 * ("trả về người gửi") lặng lẽ rơi vào nhánh mặc định.
 * Tại sao cần học: bài 93 nói hai ngữ cảnh phải có hai mô hình. Bài này là trường hợp
 * khó nhất của điều đó — khi ngữ cảnh bên kia KHÔNG PHẢI của bạn, mô hình của họ xấu, và
 * bạn không có quyền thương lượng. Lớp chống hư hỏng là biên giới nơi mọi thứ xấu dừng
 * lại: sau nó, miền của bạn không biết đối tác tồn tại.
 */
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AclDemo {

    // =====================================================================
    // HỆ NGOÀI — mô hình của đối tác. Ta KHÔNG sửa được nó.
    // =====================================================================
    /** Bản ghi thô từ API đối tác. Mọi thứ là chuỗi, mọi thứ có thể null. */
    static final class GiaoHangDto {
        String cust_nm;     // tên khách, VIẾT HOA, có khoảng trắng thừa
        String st;          // "1"=nhận đơn "2"=đang giao "3"=đã giao "4"=trả người gửi
        String amt_cent;    // số tiền, đơn vị xu, DẠNG CHUỖI
        String dt;          // ngày, "yyyyMMdd"
        String flag_x;      // "Y"/"N", nghĩa là "giao nhanh"
    }

    static GiaoHangDto dtoMau(String st, String amt) {
        GiaoHangDto d = new GiaoHangDto();
        d.cust_nm = "  NGUYEN VAN A  ";
        d.st = st;
        d.amt_cent = amt;
        d.dt = "20260817";
        d.flag_x = "Y";
        return d;
    }

    // =====================================================================
    // MIỀN CỦA TA — sạch, và KHÔNG biết đối tác tồn tại
    // =====================================================================
    enum TrangThaiGiaoHang { DA_NHAN_DON, DANG_GIAO, DA_GIAO, DA_TRA_LAI }

    record Tien(long xu) {
        Tien { if (xu < 0) throw new IllegalArgumentException("số tiền không âm"); }
    }

    record ChuyenGiaoHang(String tenKhach, TrangThaiGiaoHang trangThai,
                          Tien cuocPhi, int ngayISO, boolean giaoNhanh) { }

    // =====================================================================
    // LỚP CHỐNG HƯ HỎNG — nơi DUY NHẤT biết cả hai mô hình
    // =====================================================================
    static final class LoiDoiTac extends RuntimeException {
        LoiDoiTac(String m) { super("dữ liệu đối tác không hợp lệ: " + m); }
    }

    static final class BienDoiTac {
        /** Bảng dịch mã trạng thái — chỗ DUY NHẤT trong hệ thống biết "3" nghĩa là gì. */
        private static final Map<String, TrangThaiGiaoHang> BANG_TRANG_THAI = Map.of(
                "1", TrangThaiGiaoHang.DA_NHAN_DON,
                "2", TrangThaiGiaoHang.DANG_GIAO,
                "3", TrangThaiGiaoHang.DA_GIAO,
                "4", TrangThaiGiaoHang.DA_TRA_LAI);

        int soLanTuChoi = 0;

        public ChuyenGiaoHang dich(GiaoHangDto d) {
            // Fail fast NGAY TẠI BIÊN (bài 76): thiếu gì thì báo rõ thiếu gì, kèm tên
            // trường CỦA ĐỐI TÁC — để người trực đêm biết phải hỏi ai.
            String ten = batBuoc(d.cust_nm, "cust_nm").trim();
            if (ten.isEmpty()) throw new LoiDoiTac("cust_nm rỗng");

            TrangThaiGiaoHang tt = BANG_TRANG_THAI.get(batBuoc(d.st, "st"));
            if (tt == null) {
                soLanTuChoi++;
                throw new LoiDoiTac("mã trạng thái lạ: st=" + d.st);
            }

            long xu;
            try { xu = Long.parseLong(batBuoc(d.amt_cent, "amt_cent")); }
            catch (NumberFormatException e) { throw new LoiDoiTac("amt_cent không phải số: " + d.amt_cent); }

            int ngay;
            try { ngay = Integer.parseInt(batBuoc(d.dt, "dt")); }
            catch (NumberFormatException e) { throw new LoiDoiTac("dt không đúng yyyyMMdd: " + d.dt); }

            // "Y"/"N" của họ thành `boolean` của ta. Mọi giá trị khác là dữ liệu bẩn.
            String cy = batBuoc(d.flag_x, "flag_x");
            if (!cy.equals("Y") && !cy.equals("N")) throw new LoiDoiTac("flag_x lạ: " + cy);

            return new ChuyenGiaoHang(chuanHoaTen(ten), tt, new Tien(xu), ngay, cy.equals("Y"));
        }

        private static String batBuoc(String v, String tenTruong) {
            if (v == null) throw new LoiDoiTac("thiếu trường " + tenTruong);
            return v;
        }

        /** "  NGUYEN VAN A  " -> "Nguyen Van A". Quy ước của TA, không phải của họ. */
        private static String chuanHoaTen(String t) {
            String[] tu = t.toLowerCase().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String x : tu) {
                if (x.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));
            }
            return sb.toString();
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: mô hình đối tác rò vào miền ----
        // Không có lớp chống hư hỏng, `GiaoHangDto` được truyền thẳng vào nghiệp vụ, và
        // câu `"3".equals(dto.st)` xuất hiện ở mọi nơi cần biết "đã giao chưa".
        List<GiaoHangDto> loHang = List.of(dtoMau("3", "1050"), dtoMau("2", "800"), dtoMau("4", "0"));

        int daGiaoTheoManHinh = 0, daGiaoTheoBaoCao = 0, daGiaoTheoKeToan = 0;
        for (GiaoHangDto d : loHang) {
            if ("3".equals(d.st)) daGiaoTheoManHinh++;              // màn hình
            if ("3".equals(d.st) || "4".equals(d.st)) daGiaoTheoBaoCao++;  // báo cáo (hiểu khác)
            if (Integer.parseInt(d.st) >= 3) daGiaoTheoKeToan++;    // kế toán (hiểu khác nữa)
        }
        check(daGiaoTheoManHinh == 1 && daGiaoTheoBaoCao == 2 && daGiaoTheoKeToan == 2,
                "ba nơi, ba con số — không nơi nào SAI cú pháp, và hai nơi sai NGHĨA");
        // Đây là bài 81 phần 1 quay lại, nhưng nguyên nhân khác: lần này ngôn ngữ xấu
        // KHÔNG phải do ta đặt tên tệ — nó là ngôn ngữ của đối tác, và nó đã tràn vào.
        //
        // Và cái giá thật đến khi đối tác phát hành v2: mã "3" tách thành "3" (giao thành
        // công) và "3R" (giao lại lần hai). Mọi chỗ so chuỗi phải tìm và sửa — và cái
        // `Integer.parseInt(d.st)` ở dòng kế toán thì NÉM NGOẠI LỆ với "3R".
        boolean noVoiMaMoi = false;
        try { Integer.parseInt("3R"); } catch (NumberFormatException e) { noVoiMaMoi = true; }
        check(noVoiMaMoi, "mã mới của đối tác làm sập đúng đoạn code không ai nhớ tới");

        // ---- 2. LỚP CHỐNG HƯ HỎNG: đối tác dừng lại ở đây ----
        BienDoiTac bien = new BienDoiTac();
        ChuyenGiaoHang c = bien.dich(dtoMau("3", "1050"));
        check(c.trangThai() == TrangThaiGiaoHang.DA_GIAO, "chuỗi '3' thành ENUM của ta");
        check(c.cuocPhi().equals(new Tien(1050)), "chuỗi '1050' thành Tien của ta (bài 90)");
        check(c.giaoNhanh(), "'Y' thành boolean");
        check(c.tenKhach().equals("Nguyen Van A"), "'  NGUYEN VAN A  ' thành tên chuẩn của ta");
        check(c.ngayISO() == 20260817, "và ngày thành số nguyên có kiểu");
        // Sau dòng `dich()`, không còn một chuỗi ma thuật nào. Nghiệp vụ hỏi
        // `c.trangThai() == DA_GIAO` — một câu hỏi trả lời được bằng enum, không thể gõ sai.

        // ---- 3. FAIL FAST TẠI BIÊN, VỚI THÔNG BÁO NÓI ĐƯỢC TÊN ĐỐI TÁC ----
        GiaoHangDto thieu = dtoMau("3", null);
        String thongBao = "";
        try { bien.dich(thieu); } catch (LoiDoiTac e) { thongBao = e.getMessage(); }
        check(thongBao.contains("amt_cent"), "báo rõ THIẾU TRƯỜNG NÀO của đối tác");
        // So với cách không có ACL: `Long.parseLong(null)` ném `NumberFormatException`
        // ở đâu đó sâu trong nghiệp vụ, ba tầng gọi sau, và người trực đêm phải lần ngược
        // để đoán ra rằng lỗi đến từ dữ liệu đối tác chứ không phải từ code của mình.

        // ---- 4. KHÁI NIỆM CHỈ ĐỐI TÁC MỚI CÓ: phải QUYẾT ĐỊNH, không được rơi mặc định ----
        ChuyenGiaoHang traLai = bien.dich(dtoMau("4", "0"));
        check(traLai.trangThai() == TrangThaiGiaoHang.DA_TRA_LAI,
                "'trả về người gửi' được DỊCH thành một khái niệm CÓ TÊN trong miền của ta");
        // Đây là phần khó nhất của lớp chống hư hỏng, và là phần hay bị bỏ qua: nó không
        // chỉ đổi tên field, nó dịch KHÁI NIỆM. Nếu miền của ta không có khái niệm tương
        // ứng thì có đúng hai lựa chọn hợp lệ:
        //   (a) thêm khái niệm đó vào miền (như `DA_TRA_LAI` ở đây) — sau khi hỏi nghiệp vụ;
        //   (b) TỪ CHỐI bản ghi đó ở biên, có log, có cảnh báo.
        // Lựa chọn thứ ba — cho rơi vào nhánh mặc định — là cách dữ liệu sai đi vào hệ
        // thống mà không ai biết.
        boolean tuChoiMaLa = false;
        try { bien.dich(dtoMau("9", "100")); } catch (LoiDoiTac e) { tuChoiMaLa = true; }
        check(tuChoiMaLa && bien.soLanTuChoi == 1, "mã lạ bị TỪ CHỐI và ĐẾM, không rơi mặc định");

        // ---- 5. ĐỐI TÁC RA v2: ĐO SỐ CHỖ PHẢI SỬA ----
        // Đối tác đổi `cust_nm` -> `customer_name` và thêm mã "3R".
        //   Không ACL: mọi file chạm tới DTO. Trong dự án thật thường là 10–40 chỗ, và
        //              không có cách nào tìm hết ngoài `grep` từng chuỗi.
        //   Có ACL   : đúng MỘT lớp `BienDoiTac`. Trình biên dịch chỉ ra hết.
        int choPhaiSuaKhongAcl = 12;
        int choPhaiSuaCoAcl = 1;
        check(choPhaiSuaKhongAcl > choPhaiSuaCoAcl * 10, "12 chỗ so với 1");

        // ---- 6. BÀI TEST KIẾN TRÚC: miền KHÔNG được biết đối tác ----
        // Quét mọi thành viên của các lớp miền, khẳng định không kiểu nào của đối tác lọt vào.
        List<Class<?>> lopMien = List.of(ChuyenGiaoHang.class, Tien.class, TrangThaiGiaoHang.class);
        List<String> viPham = new ArrayList<>();
        for (Class<?> lop : lopMien) {
            for (Field f : lop.getDeclaredFields())
                if (f.getType() == GiaoHangDto.class) viPham.add(lop.getSimpleName() + "." + f.getName());
            for (Method m : lop.getDeclaredMethods()) {
                if (m.getReturnType() == GiaoHangDto.class) viPham.add(lop.getSimpleName() + "." + m.getName());
                for (Class<?> t : m.getParameterTypes())
                    if (t == GiaoHangDto.class) viPham.add(lop.getSimpleName() + "." + m.getName());
            }
        }
        check(viPham.isEmpty(), "không lớp miền nào chạm tới kiểu của đối tác: " + viPham);
        // Bảy dòng trên chạy được trong CI. Chúng bắt đúng thời điểm ai đó "cho tiện" nhét
        // `GiaoHangDto` vào một phương thức của miền — thời điểm mà lớp chống hư hỏng bắt
        // đầu mất tác dụng, và không ai để ý vì mọi test nghiệp vụ vẫn xanh.

        // ---- 7. ACL KHÔNG PHẢI CHỖ ĐẶT LUẬT NGHIỆP VỤ ----
        // Cám dỗ: đã dịch rồi thì tiện tay tính luôn phí, kiểm luôn hạn mức. Đừng.
        // Lớp chống hư hỏng chỉ làm ĐÚNG BA việc:
        //   1. Kiểm tính hợp lệ của dữ liệu ĐẦU VÀO (thiếu trường, sai kiểu, mã lạ);
        //   2. Dịch mô hình họ -> mô hình ta (kiểu, đơn vị, khái niệm);
        //   3. Từ chối cái không dịch được, và đếm.
        // Nếu nó bắt đầu biết "đơn trên 10 triệu phải duyệt", thì luật nghiệp vụ vừa
        // chuyển ra ngoài miền — và sẽ có bản sao thứ hai của nó ở trong miền (bài 87).
        long soPhuongThucCongKhai = java.util.Arrays.stream(BienDoiTac.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).count();
        check(soPhuongThucCongKhai == 1, "ACL có đúng một cửa: dich()");

        // ---- 8. ĐẶT ACL Ở ĐÂU, VÀ MẤY CÁI ----
        // Một lớp chống hư hỏng cho MỖI hệ ngoài, thuộc về BÊN GỌI. Ba hệ quả:
        //   - Hai đội cùng gọi một đối tác thì có thể có HAI ACL khác nhau — và đó là
        //     đúng, vì hai đội cần hai mô hình khác nhau (bài 93).
        //   - ACL nằm ở tầng hạ tầng, cài đặt một CỔNG do miền định nghĩa (bài 98).
        //   - Khi đối tác chết, ACL là nơi duy nhất cần một bản giả để test (bài 68).
        Map<String, String> cacAcl = new LinkedHashMap<>();
        cacAcl.put("GiaoHangNhanh", "BienDoiTac -> ChuyenGiaoHang");
        cacAcl.put("CongThanhToan", "BienThanhToan -> BienLai");
        check(cacAcl.size() == 2, "một ACL cho mỗi hệ ngoài, không phải một ACL cho tất cả");

        // Và điều cuối, dễ quên nhất: ACL cũng cần đi CẢ HAI CHIỀU. Khi ta GỬI dữ liệu
        // sang đối tác, cũng phải dịch từ mô hình của ta sang của họ — chứ không phải
        // serialize thẳng object miền ra JSON và hy vọng khớp.
        Optional<String> maStGuiDi = Optional.of(c.trangThai() == TrangThaiGiaoHang.DA_GIAO ? "3" : "2");
        check(maStGuiDi.get().equals("3"), "chiều ra cũng dịch, ở cùng một chỗ");

        System.out.println("OK");
    }
}
