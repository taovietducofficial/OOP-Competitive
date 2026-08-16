/*
 * Ngôn ngữ: Java
 * Công dụng: State pattern ở mức miền — mỗi trạng thái là một object BIẾT nó cho phép
 * chuyển đi đâu. Bài cho nổ con bug kinh điển của cách làm bằng `switch`: một đơn ĐÃ
 * HUỶ vẫn được giao, vì nhánh kiểm tra bị quên. Rồi đo hai thứ: số chỗ phải sửa khi
 * thêm một trạng thái, và số cạnh hợp lệ của máy trạng thái (kiểm được bằng máy).
 * Tại sao cần học: bài 32 dạy "mỗi trạng thái là một object". Ở mức miền, điều quan
 * trọng hơn là AI SỞ HỮU BẢNG CHUYỂN. Với `switch`, nhánh thiếu thường là nhánh CHO
 * PHÉP (rơi xuống `default`, hoặc đơn giản là không có `if` chặn) — quên một dòng là
 * mở một cửa. Với state object, phương thức thiếu là phương thức TỪ CHỐI, vì mặc định
 * của lớp cha là ném ngoại lệ. Quên một dòng là đóng một cửa. Cùng một sơ suất, hai hậu
 * quả ngược nhau — và đó là toàn bộ giá trị của mẫu này.
 */
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class StateDemo {

    // =====================================================================
    // TRẠNG THÁI LÀ OBJECT — và ở Java, `enum` có thân riêng cho từng hằng
    // là cách gọn nhất: vừa liệt kê được hết, vừa đa hình.
    // =====================================================================
    enum TrangThai {
        MOI_TAO {
            @Override TrangThai thanhToan() { return DA_THANH_TOAN; }
            @Override TrangThai huy() { return DA_HUY; }
            @Override long phiHuy(long tong) { return 0; }          // chưa trả tiền -> huỷ miễn phí
        },
        DA_THANH_TOAN {
            @Override TrangThai giao() { return DA_GIAO; }
            @Override TrangThai huy() { return DA_HUY; }
            @Override long phiHuy(long tong) { return tong * 10 / 100; }   // đã trả -> phí 10%
        },
        DA_GIAO { /* trạng thái KẾT THÚC: không override gì cả */ },
        DA_HUY  { /* trạng thái KẾT THÚC: không override gì cả */ };

        // MẶC ĐỊNH LÀ TỪ CHỐI. Đây là dòng quan trọng nhất của cả file.
        TrangThai thanhToan() { throw new IllegalStateException("không thanh toán được ở " + this); }
        TrangThai giao()      { throw new IllegalStateException("không giao được ở " + this); }
        TrangThai huy()       { throw new IllegalStateException("không huỷ được ở " + this); }
        long phiHuy(long tong) { throw new IllegalStateException("không huỷ được ở " + this); }

        boolean laKetThuc() { return canChuyenDuoc().isEmpty(); }

        /** Liệt kê các cạnh đi ra — dùng cho kiểm tra máy trạng thái ở phần 5. */
        List<String> canChuyenDuoc() {
            List<String> ra = new ArrayList<>();
            for (String su : List.of("thanhToan", "giao", "huy")) {
                try {
                    switch (su) {
                        case "thanhToan" -> thanhToan();
                        case "giao" -> giao();
                        default -> huy();
                    }
                    ra.add(su);
                } catch (IllegalStateException ignored) { }
            }
            return ra;
        }
    }

    // =====================================================================
    // AGGREGATE — trạng thái đổi CHỈ qua hành vi có tên
    // =====================================================================
    static final class DonHang {
        private final String ma;
        private final long tongTien;
        private TrangThai trangThai = TrangThai.MOI_TAO;   // private: không ai gán từ ngoài

        DonHang(String ma, long tongTien) { this.ma = ma; this.tongTien = tongTien; }

        void thanhToan() { trangThai = trangThai.thanhToan(); }
        void giao()      { trangThai = trangThai.giao(); }
        long huy()       {
            long phi = trangThai.phiHuy(tongTien);   // hỏi phí TRƯỚC khi đổi trạng thái
            trangThai = trangThai.huy();
            return phi;
        }
        TrangThai trangThai() { return trangThai; }
        String ma() { return ma; }
    }

    // =====================================================================
    // BẢN SAI — máy trạng thái viết bằng `switch` + cờ
    // =====================================================================
    static final class DonHangSwitch {
        int trangThai = 1;          // 1=mới, 2=đã thanh toán, 3=đã giao, 4=đã huỷ
        void thanhToan() {
            if (trangThai != 1) throw new IllegalStateException("sai trạng thái");
            trangThai = 2;
        }
        void giao() {
            // Ở đây ĐÁNG LẼ phải có: if (trangThai != 2) throw ...
            // Người viết nghĩ "chỉ đơn đã thanh toán mới gọi giao()" và bỏ qua.
            trangThai = 3;
        }
        void huy() {
            if (trangThai == 3) throw new IllegalStateException("đã giao thì không huỷ");
            trangThai = 4;
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: đơn ĐÃ HUỶ vẫn được giao ----
        DonHangSwitch sai = new DonHangSwitch();
        sai.thanhToan();
        sai.huy();
        check(sai.trangThai == 4, "đơn đã huỷ, khách đã được hoàn tiền");
        sai.giao();                                   // không ai chặn
        check(sai.trangThai == 3, "và hàng vẫn được giao đi — công ty mất cả hàng lẫn tiền");
        // Đây là hình dạng phổ biến nhất của bug máy trạng thái: KHÔNG phải một điều
        // kiện sai, mà là một điều kiện KHÔNG TỒN TẠI. Đọc `giao()` ở trên, không có gì
        // trông sai cả — chỉ có một dòng không có ở đó.

        // ---- 2. BẢN ĐÚNG: mặc định là TỪ CHỐI ----
        DonHang don = new DonHang("DH-01", 1_000_000);
        don.thanhToan();
        check(don.huy() == 100_000, "huỷ sau khi trả tiền: phí 10%");
        check(don.trangThai() == TrangThai.DA_HUY, "đã huỷ");

        boolean chan = false;
        try { don.giao(); } catch (IllegalStateException e) { chan = true; }
        check(chan, "giao một đơn đã huỷ -> NỔ, và không ai phải nhớ viết `if`");
        check(don.trangThai() == TrangThai.DA_HUY, "trạng thái không hề bị sửa dở dang");
        // `DA_HUY` không override `giao()`, nên nó dùng bản mặc định của enum — bản NÉM
        // NGOẠI LỆ. Người viết `DA_HUY` không phải nghĩ tới việc "cấm giao"; họ chỉ cần
        // KHÔNG viết gì cả. Đó là sự khác biệt giữa "an toàn nếu nhớ" và "an toàn mặc định".

        // ---- 3. HÀNH VI ĐỔI THEO TRẠNG THÁI, KHÔNG CHỈ CÓ CHUYỂN TIẾP ----
        DonHang chuaTra = new DonHang("DH-02", 1_000_000);
        check(chuaTra.huy() == 0, "chưa trả tiền -> huỷ miễn phí");
        DonHang daTra = new DonHang("DH-03", 1_000_000);
        daTra.thanhToan();
        check(daTra.huy() == 100_000, "đã trả tiền -> phí huỷ 10%");
        // Cùng một lời gọi `huy()`, hai kết quả khác nhau, và KHÔNG có `if` nào trong
        // `DonHang`. Luật phí huỷ nằm ở đúng nơi nó thuộc về: trong trạng thái quyết
        // định nó. Với `switch`, luật này thành nhánh thứ hai trong một hàm khác, và hai
        // nhánh đó sẽ lệch nhau (bài 87 phần 2).

        // ---- 4. THÊM MỘT TRẠNG THÁI: ĐO SỐ CHỖ PHẢI SỬA ----
        // Giả sử thêm `DANG_GIAO` (đã rời kho, chưa tới tay khách).
        //   Bằng switch : sửa MỌI hàm có `switch (trangThai)` — thanhToan, giao, huy,
        //                 phiHuy, hienThi, xuatBaoCao... và quên một chỗ là bug im lặng.
        //   Bằng state  : thêm MỘT hằng enum với các phương thức nó cho phép; những
        //                 hàm khác không đụng tới. Và nếu ở đâu đó có `switch` trên
        //                 enum này mà không có `default`, trình biên dịch chỉ ra ngay.
        int soHamCoSwitch = 3;     // trong một hệ thật thường là 8–15
        int soChoSuaKhiDungState = 1;
        check(soHamCoSwitch > soChoSuaKhiDungState, "1 chỗ so với N chỗ — và N chỉ tăng");

        // ---- 5. KIỂM MÁY TRẠNG THÁI BẰNG MÁY ----
        check(TrangThai.MOI_TAO.canChuyenDuoc().equals(List.of("thanhToan", "huy")),
                "MOI_TAO: hai cửa ra");
        check(TrangThai.DA_THANH_TOAN.canChuyenDuoc().equals(List.of("giao", "huy")),
                "DA_THANH_TOAN: hai cửa ra");
        check(TrangThai.DA_GIAO.laKetThuc() && TrangThai.DA_HUY.laKetThuc(),
                "hai trạng thái KẾT THÚC: không cửa ra nào");

        int tongCanh = 0;
        for (TrangThai t : TrangThai.values()) tongCanh += t.canChuyenDuoc().size();
        check(tongCanh == 4, "máy trạng thái có đúng 4 cạnh hợp lệ trên 4×3 = 12 khả năng");
        // 4/12 — nghĩa là 8 lời gọi trong số 12 phải bị từ chối. Với `switch`, mỗi cái
        // trong 8 lời gọi đó cần một dòng `if` do con người nhớ viết. Với state object,
        // cả 8 đều được từ chối vì KHÔNG AI VIẾT GÌ.

        // MỌI trạng thái phải ĐẾN ĐƯỢC từ trạng thái đầu — nếu không thì hoặc thừa một
        // trạng thái, hoặc thiếu một cạnh (và đó là một tính năng không bao giờ chạy).
        EnumSet<TrangThai> denDuoc = EnumSet.of(TrangThai.MOI_TAO);
        boolean coThem = true;
        while (coThem) {
            coThem = false;
            for (TrangThai t : EnumSet.copyOf(denDuoc)) {
                for (String su : t.canChuyenDuoc()) {
                    TrangThai ke = switch (su) {
                        case "thanhToan" -> t.thanhToan();
                        case "giao" -> t.giao();
                        default -> t.huy();
                    };
                    if (denDuoc.add(ke)) coThem = true;
                }
            }
        }
        check(denDuoc.size() == TrangThai.values().length, "cả 4 trạng thái đều đến được");
        // Bài kiểm tra này bắt được một lớp bug rất khó thấy bằng mắt: trạng thái mồ côi.
        // Ai đó thêm `TAM_GIU` vào enum, viết đủ hành vi cho nó, nhưng quên thêm cạnh
        // dẫn TỚI nó — và tính năng "tạm giữ đơn" không bao giờ xảy ra trên production.

        // ---- 6. TRẠNG THÁI PHẢI KHÔNG GÁN ĐƯỢC TỪ NGOÀI ----
        // Trong `DonHang`, `trangThai` là `private` và không có setter. Dòng
        //     don.trangThai = TrangThai.DA_GIAO;
        // KHÔNG BIÊN DỊCH ĐƯỢC. Nếu có setter, toàn bộ máy trạng thái thành trang trí —
        // đúng như mô hình thiếu máu ở bài 86.
        check(don.trangThai() == TrangThai.DA_HUY, "đọc được, gán thì không");

        // ---- 7. KHI NÀO KHÔNG DÙNG STATE PATTERN ----
        // Mẫu này có một cái giá thật: N trạng thái × M sự kiện, và mỗi trạng thái là
        // một khối code. Ba dấu hiệu nên chọn cách khác:
        //   - Chỉ có 2 trạng thái và 1 sự kiện -> một `boolean` là đủ.
        //   - Trạng thái và luật chuyển đến TỪ DỮ LIỆU (người dùng cấu hình quy trình
        //     duyệt) -> dùng BẢNG CHUYỂN dữ liệu, không dùng lớp.
        //   - Số trạng thái lớn (>15) và luật giống nhau -> bảng chuyển gọn hơn nhiều
        //     lớp, và in ra được thành sơ đồ.
        // Ngược lại, khi mỗi trạng thái có HÀNH VI khác nhau (không chỉ cạnh khác nhau)
        // — như `phiHuy` ở phần 3 — thì state object thắng rõ rệt.
        check(TrangThai.values().length == 4, "4 trạng thái: vẫn trong vùng state object có lợi");

        // ---- 8. Ranh giới với bài 84 ----
        // Chuyển trạng thái là chỗ tự nhiên nhất để PHÁT SỰ KIỆN MIỀN: `giao()` đổi
        // trạng thái và ghi `DonHangDaGiao`. Nhưng phải ghi, không phát (bài 84), và
        // phải ghi SAU khi trạng thái đã đổi thành công — nếu `trangThai.giao()` ném
        // ngoại lệ thì không có sự kiện nào được ghi. Thứ tự trong `DonHang.giao()` ở
        // trên đã đúng sẵn: gán trước, và nếu ném thì không tới dòng nào sau đó.
        System.out.println("OK");
    }
}
