/*
 * Ngôn ngữ: Java
 * Công dụng: Saga — quy trình nghiệp vụ nhiều bước chạy trên nhiều aggregate, không có
 * transaction chung, và mỗi bước có một HÀNH ĐỘNG BÙ TRỪ. Bài cho nổ ba con bug: bước 3
 * hỏng thì tiền của khách kẹt lại vĩnh viễn; email xác nhận được gửi cho một đơn hàng
 * thất bại vì bước không-bù-trừ-được đặt sai chỗ; và saga chạy dở dang khi tiến trình
 * chết vì trạng thái saga không được lưu.
 * Tại sao cần học: bài 83 cấm sửa hai aggregate trong một transaction, bài 84 nói hai
 * aggregate nói chuyện bằng sự kiện. Câu hỏi còn lại: nếu bước thứ hai HỎNG thì bước thứ
 * nhất đã xảy ra rồi, làm sao? Câu trả lời không phải "rollback" — không có gì để
 * rollback, transaction đã commit. Câu trả lời là BÙ TRỪ: ghi một sự thật nghiệp vụ MỚI
 * để triệt tiêu hậu quả của sự thật cũ. Phân biệt được hai điều đó là toàn bộ bài này.
 */
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SagaDemo {

    // =====================================================================
    // Ba dịch vụ, ba aggregate, ba transaction RỜI NHAU
    // =====================================================================
    static final class Kho {
        int tonKho = 10;
        int soLanTru = 0, soLanTra = 0;
        void tru(int sl) {
            if (tonKho < sl) throw new IllegalStateException("không đủ tồn kho");
            tonKho -= sl; soLanTru++;
        }
        void tra(int sl) { tonKho += sl; soLanTra++; }   // BÙ TRỪ cho `tru`
    }

    static final class Vi {
        long soDu = 1_000_000L;
        int soLanTru = 0, soLanHoan = 0;
        void tru(long t) {
            if (soDu < t) throw new IllegalStateException("không đủ số dư");
            soDu -= t; soLanTru++;
        }
        void hoan(long t) { soDu += t; soLanHoan++; }    // BÙ TRỪ cho `tru`
    }

    static final class VanChuyen {
        boolean seHong = false;
        int soVanDon = 0;
        String taoVanDon() {
            if (seHong) throw new IllegalStateException("đối tác vận chuyển hết chỗ");
            return "VD-" + (++soVanDon);
        }
        void huyVanDon(String ma) { soVanDon--; }        // BÙ TRỪ cho `taoVanDon`
    }

    static final class HopThu {
        int soEmailDaGui = 0;
        void gui(String noiDung) { soEmailDaGui++; }
        // KHÔNG có `thuHoiEmail()`. Đó là toàn bộ vấn đề của phần 3.
    }

    // =====================================================================
    // SAGA — danh sách BƯỚC, mỗi bước kèm HÀNH ĐỘNG BÙ TRỪ của chính nó
    // =====================================================================
    record Buoc(String ten, Runnable lam, Runnable buTru) { }

    static final class Saga {
        private final List<Buoc> cacBuoc = new ArrayList<>();
        final List<String> nhatKy = new ArrayList<>();
        int soLanBuTru = 0;

        Saga them(String ten, Runnable lam, Runnable buTru) {
            cacBuoc.add(new Buoc(ten, lam, buTru));
            return this;
        }

        /** Chạy tới đâu hỏng thì bù trừ NGƯỢC LẠI tới đó, theo thứ tự ĐẢO. */
        boolean chay() {
            List<Buoc> daXong = new ArrayList<>();
            for (Buoc b : cacBuoc) {
                try {
                    b.lam().run();
                    daXong.add(b);
                    nhatKy.add("xong:" + b.ten());
                } catch (RuntimeException e) {
                    nhatKy.add("hỏng:" + b.ten());
                    Collections.reverse(daXong);         // ĐẢO — bù trừ ngược chiều
                    for (Buoc x : daXong) {
                        if (x.buTru() == null) {          // bước KHÔNG bù trừ được
                            nhatKy.add("KHÔNG-BÙ-TRỪ-ĐƯỢC:" + x.ten());
                            continue;
                        }
                        x.buTru().run();
                        soLanBuTru++;
                        nhatKy.add("bù:" + x.ten());
                    }
                    return false;
                }
            }
            return true;
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: không có bù trừ -> tiền của khách kẹt lại ----
        Kho kho = new Kho();
        Vi vi = new Vi();
        VanChuyen vc = new VanChuyen();
        vc.seHong = true;                       // đối tác vận chuyển hết chỗ

        boolean hong = false;
        try {
            kho.tru(2);                         // transaction 1: COMMIT
            vi.tru(500_000L);                   // transaction 2: COMMIT
            vc.taoVanDon();                     // transaction 3: HỎNG
        } catch (IllegalStateException e) { hong = true; }

        check(hong, "đặt hàng thất bại");
        check(kho.tonKho == 8, "nhưng 2 sản phẩm vẫn bị giữ trong kho");
        check(vi.soDu == 500_000L, "và 500.000 của khách đã bị trừ");
        // Không có gì để "rollback": hai transaction đầu đã COMMIT xong từ lâu. Đây chính
        // là hệ quả trực tiếp của luật ở bài 83 — một transaction sửa một aggregate. Luật
        // đó đúng, và cái giá của nó là bài toán này.
        //
        // Trong hệ thật, tiền đó nằm im cho tới khi khách gọi lên khiếu nại. Và với những
        // lỗi hiếm, "cho tới khi khách gọi" có nghĩa là "không bao giờ".

        // ---- 2. SAGA: bù trừ NGƯỢC CHIỀU tới đúng chỗ đã đi qua ----
        Kho kho2 = new Kho();
        Vi vi2 = new Vi();
        VanChuyen vc2 = new VanChuyen();
        vc2.seHong = true;
        final String[] maVanDon = {null};

        Saga saga = new Saga()
                .them("trừ kho", () -> kho2.tru(2), () -> kho2.tra(2))
                .them("trừ tiền", () -> vi2.tru(500_000L), () -> vi2.hoan(500_000L))
                .them("tạo vận đơn", () -> maVanDon[0] = vc2.taoVanDon(),
                        () -> vc2.huyVanDon(maVanDon[0]));

        check(!saga.chay(), "saga thất bại ở bước 3");
        check(kho2.tonKho == 10, "kho được TRẢ LẠI: 10 như ban đầu");
        check(vi2.soDu == 1_000_000L, "tiền được HOÀN: 1.000.000 như ban đầu");
        check(saga.soLanBuTru == 2, "đúng 2 hành động bù trừ — cho 2 bước đã xong");
        check(saga.nhatKy.equals(List.of("xong:trừ kho", "xong:trừ tiền",
                "hỏng:tạo vận đơn", "bù:trừ tiền", "bù:trừ kho")),
                "và bù theo thứ tự ĐẢO: tiền trước, kho sau");
        // Thứ tự đảo không phải chuyện thẩm mỹ. Nếu bước 2 phụ thuộc bước 1 (rất thường),
        // thì bù trừ bước 1 trước khi bù bước 2 sẽ để lại trạng thái vô nghĩa ở giữa.

        // ---- 3. BÙ TRỪ KHÔNG PHẢI ROLLBACK ----
        check(kho2.soLanTru == 1 && kho2.soLanTra == 1, "kho có HAI bút toán, không phải không có gì");
        check(vi2.soLanTru == 1 && vi2.soLanHoan == 1, "ví cũng vậy: trừ rồi hoàn");
        // Đây là điểm quan trọng nhất và hay bị hiểu sai nhất. Rollback XOÁ dấu vết như
        // chưa từng xảy ra. Bù trừ thì KHÔNG: nó ghi thêm một sự thật nghiệp vụ MỚI.
        //
        // Với sổ kế toán, đó là bút toán đảo — và nó PHẢI hiện trên sao kê của khách:
        //   -500.000  thanh toán đơn DH-01
        //   +500.000  hoàn tiền đơn DH-01 (không tạo được vận đơn)
        // Chứ không phải một dòng trống. Khách đã nhìn thấy số dư bị trừ; giấu bút toán
        // hoàn đi là làm sao kê nói dối.

        // ---- 4. CON BUG: bước KHÔNG BÙ TRỪ ĐƯỢC đặt sai chỗ ----
        // Gửi email không có hành động ngược. Đặt nó ở giữa saga:
        Kho kho3 = new Kho();
        Vi vi3 = new Vi();
        VanChuyen vc3 = new VanChuyen();
        HopThu ht3 = new HopThu();
        vc3.seHong = true;

        Saga sagaSai = new Saga()
                .them("trừ kho", () -> kho3.tru(2), () -> kho3.tra(2))
                .them("gửi email xác nhận", () -> ht3.gui("đơn của bạn đã được xác nhận"), null)
                .them("trừ tiền", () -> vi3.tru(500_000L), () -> vi3.hoan(500_000L))
                .them("tạo vận đơn", vc3::taoVanDon, null);

        check(!sagaSai.chay(), "vẫn thất bại ở bước cuối");
        check(kho3.tonKho == 10 && vi3.soDu == 1_000_000L, "kho và tiền đều được bù trừ");
        check(ht3.soEmailDaGui == 1, "NHƯNG email đã bay đi và không thu về được");
        check(sagaSai.nhatKy.contains("KHÔNG-BÙ-TRỪ-ĐƯỢC:gửi email xác nhận"),
                "saga biết mình để lại một hậu quả không xoá được — và ghi nhận điều đó");
        // Đây là bài 84 phần 3 quay lại ở quy mô quy trình. Luật rút ra rất đơn giản và
        // rất đắt nếu quên:
        //
        //   XẾP MỌI BƯỚC KHÔNG BÙ TRỪ ĐƯỢC XUỐNG CUỐI SAGA.
        //
        // Gửi email, gửi SMS, gọi API bên thứ ba không có hàm huỷ, in phiếu — tất cả đi
        // sau cùng, sau khi mọi bước có thể hỏng đã xong.
        Saga sagaDung = new Saga()
                .them("trừ kho", () -> {}, () -> {})
                .them("trừ tiền", () -> {}, () -> {})
                .them("tạo vận đơn", () -> {}, () -> {})
                .them("gửi email xác nhận", () -> {}, null);   // <- CUỐI CÙNG
        check(sagaDung.chay(), "đường thuận lợi: bước không bù trừ được chạy sau cùng");

        // ---- 5. HÀNH ĐỘNG BÙ TRỪ PHẢI IDEMPOTENT ----
        // Saga chạy dở rồi tiến trình chết -> khởi động lại -> chạy bù trừ LẦN NỮA. Nếu
        // `hoan(500_000)` cộng tiền mỗi lần được gọi thì khách được hoàn hai lần.
        Vi viBu = new Vi();
        viBu.tru(500_000L);
        viBu.hoan(500_000L);
        viBu.hoan(500_000L);                    // gọi lại do thử lại
        check(viBu.soDu == 1_500_000L, "hoàn hai lần -> khách được thêm 500.000 từ trên trời");
        check(viBu.soLanHoan == 2, "vì `hoan` là phép TƯƠNG ĐỐI");
        // Cách chữa là bài 91: mỗi hành động bù trừ mang một khoá idempotency (thường là
        // mã saga + số thứ tự bước), và dịch vụ đích bỏ qua lần gọi trùng. Không có bước
        // đó thì cơ chế thử lại — thứ bắt buộc phải có — trở thành máy sinh tiền.

        // ---- 6. TRẠNG THÁI SAGA PHẢI ĐƯỢC LƯU ----
        // Saga trong bài này sống trong bộ nhớ: tiến trình chết giữa chừng là mất sạch,
        // và hệ thống đứng lại ở trạng thái nửa vời không ai biết.
        //
        // Trong hệ thật, saga là một ENTITY (bài 82): có mã, có trạng thái, được lưu sau
        // MỖI bước. Khởi động lại thì đọc lên và đi tiếp — hoặc bù trừ tiếp.
        //   maSaga | buocHienTai | trangThai
        //   SG-01  | 2           | DANG_CHAY
        //   SG-02  | 3           | DANG_BU_TRU
        // Và vì nó là entity có trạng thái, nó cũng cần khoá lạc quan (bài 92) — hai
        // tiến trình cùng tiếp tục một saga là chuyện có thật.
        check(saga.nhatKy.size() == 5, "nhật ký saga là dữ liệu, không phải log gỡ lỗi");

        // ---- 7. ĐIỀU PHỐI hay HỢP XƯỚNG ----
        //
        //   Cách             | Ai biết quy trình        | Thấy được quy trình? | Ghép chặt?
        //   -----------------|--------------------------|----------------------|------------
        //   ĐIỀU PHỐI        | MỘT object saga          | CÓ — đọc một file    | trung tâm biết mọi bước
        //   (bài này)        |                          |                      |
        //   HỢP XƯỚNG        | rải trong các người nghe | KHÔNG — phải lần theo| lỏng hơn
        //                    | sự kiện (bài 84)         | sự kiện qua 5 dịch vụ|
        //
        // Không có cái nào luôn đúng. Quy tắc thực dụng: quy trình có BÙ TRỪ thì dùng điều
        // phối — vì "chạy tới đâu, bù tới đâu" cần một chỗ biết thứ tự. Còn hệ quả phụ
        // độc lập (cộng điểm, gửi thông báo, ghi thống kê) thì dùng hợp xướng.
        //
        // Dấu hiệu chọn sai: phải mở 5 dịch vụ mới trả lời được câu "đơn hàng này đang ở
        // bước nào" — đó là hợp xướng dùng cho việc của điều phối.

        // ---- 8. SAGA KHÔNG PHẢI TRANSACTION ----
        // Ba tính chất bị mất, phải nói ra với nghiệp vụ TRƯỚC khi làm:
        //   - Không cô lập: giữa bước 1 và bước 3, người khác NHÌN THẤY trạng thái nửa
        //     vời (kho đã trừ, tiền chưa trừ). Nếu điều đó không chấp nhận được thì cụm
        //     này phải là MỘT aggregate (bài 83), không phải một saga.
        //   - Không nguyên tử tức thời: có một khoảng thời gian hệ thống ở trạng thái
        //     trung gian. Đó là nhất quán CUỐI, và độ trễ của nó là con số phải đo.
        //   - Bù trừ có thể HỎNG. Lúc đó cần hàng đợi thư chết và con người xử lý tay —
        //     một saga không có đường thoát cho trường hợp này là một saga chưa xong.
        check(kho2.tonKho == 10 && vi2.soDu == 1_000_000L, "cuối cùng thì nhất quán — CUỐI cùng");

        System.out.println("OK");
    }
}
