/*
 * Ngôn ngữ: Java
 * Công dụng: Khoá lạc quan — hai người sửa cùng một bản ghi, và số hiệu phiên bản là
 * thứ duy nhất phát hiện được điều đó. Bài cho nổ ba con bug: ghi đè mất thay đổi của
 * người khác; thử lại "mù" (giữ nguyên dữ liệu cũ, chỉ tăng phiên bản) vẫn mất y hệt;
 * và đặt phiên bản ở SAI CẤP làm sinh đụng độ giả hoặc bỏ sót đụng độ thật.
 * Tại sao cần học: đây là bài 85 mở rộng sang nhiều tiến trình. Ở đó, bản đồ định danh
 * cứu được "lost update" TRONG một use case; ở đây hai use case chạy trên hai máy chủ,
 * và không bản đồ nào giúp được. Điểm hay của Java: `AtomicLong.compareAndSet` chính là
 * cùng một thuật toán ở mức CPU — đọc, tính, ghi-nếu-chưa-ai-đổi, hỏng thì thử lại. Nhìn
 * thấy hai thứ đó là MỘT giúp hiểu vì sao vòng lặp thử lại là bắt buộc, không phải tuỳ chọn.
 */
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ConcurrencyDemo {

    /** Bản ghi trong CSDL: dữ liệu + SỐ HIỆU PHIÊN BẢN. */
    record BanGhi(String ten, long hanMuc, long phienBan) { }

    static final class LoiDungDo extends RuntimeException {
        LoiDungDo(String m) { super(m); }
    }

    /** CSDL giả. `capNhat` mô phỏng đúng `UPDATE ... WHERE ma=? AND phien_ban=?`. */
    static final class Csdl {
        private final Map<String, BanGhi> bang = new LinkedHashMap<>();
        int soLanGhiThanhCong = 0, soLanDungDo = 0;

        void tao(String ma, BanGhi bg) { bang.put(ma, bg); }
        BanGhi doc(String ma) { return bang.get(ma); }

        /** Ghi KHÔNG kiểm phiên bản — "ai ghi sau thì thắng". */
        void ghiDe(String ma, BanGhi moi) {
            bang.put(ma, moi);
            soLanGhiThanhCong++;
        }

        /** Ghi CÓ kiểm phiên bản. Trả về số dòng bị ảnh hưởng, y như JDBC. */
        int capNhat(String ma, BanGhi moi, long phienBanKyVong) {
            BanGhi hienTai = bang.get(ma);
            if (hienTai == null || hienTai.phienBan() != phienBanKyVong) {
                soLanDungDo++;
                return 0;                        // 0 dòng -> có người đã sửa trước
            }
            bang.put(ma, new BanGhi(moi.ten(), moi.hanMuc(), phienBanKyVong + 1));
            soLanGhiThanhCong++;
            return 1;
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: ai ghi sau thì thắng, và người trước mất trắng ----
        Csdl db = new Csdl();
        db.tao("KH-01", new BanGhi("Nguyễn Văn A", 10_000_000L, 1));

        // Hai nhân viên mở cùng một hồ sơ khách hàng lúc 9h00.
        BanGhi cuaAn = db.doc("KH-01");          // An đọc: hạn mức 10 triệu
        BanGhi cuaBinh = db.doc("KH-01");        // Bình đọc: hạn mức 10 triệu

        // An sửa TÊN (khách đổi tên đệm), Bình sửa HẠN MỨC (duyệt nâng hạn).
        db.ghiDe("KH-01", new BanGhi("Nguyễn Văn An", cuaAn.hanMuc(), 1));
        db.ghiDe("KH-01", new BanGhi(cuaBinh.ten(), 50_000_000L, 1));

        check(db.doc("KH-01").hanMuc() == 50_000_000L, "hạn mức mới của Bình: có");
        check(db.doc("KH-01").ten().equals("Nguyễn Văn A"), "tên mới của An: MẤT");
        // An thấy màn hình báo "lưu thành công", đóng máy, về nhà. Không ngoại lệ, không
        // cảnh báo, và không ai biết cho tới khi khách hàng gọi điện hỏi.
        //
        // Chú ý: đây KHÔNG phải bài 85. Ở đó hai lần tải nằm trong một use case, và bản
        // đồ định danh cứu được. Ở đây là hai người, hai máy, hai transaction — không có
        // bản đồ nào nhìn thấy cả hai.

        // ---- 2. SỐ HIỆU PHIÊN BẢN: phát hiện được, và phát hiện ĐÚNG LÚC ----
        Csdl db2 = new Csdl();
        db2.tao("KH-01", new BanGhi("Nguyễn Văn A", 10_000_000L, 1));

        BanGhi anDoc = db2.doc("KH-01");         // phiên bản 1
        BanGhi binhDoc = db2.doc("KH-01");       // phiên bản 1

        int anGhi = db2.capNhat("KH-01", new BanGhi("Nguyễn Văn An", anDoc.hanMuc(), 0), anDoc.phienBan());
        check(anGhi == 1, "An ghi trước: thành công, phiên bản -> 2");
        check(db2.doc("KH-01").phienBan() == 2, "phiên bản tự tăng cùng lần ghi");

        int binhGhi = db2.capNhat("KH-01", new BanGhi(binhDoc.ten(), 50_000_000L, 0), binhDoc.phienBan());
        check(binhGhi == 0, "Bình ghi sau với phiên bản 1 -> 0 DÒNG bị ảnh hưởng");
        check(db2.soLanDungDo == 1, "đụng độ được ĐẾM, không im lặng");
        check(db2.doc("KH-01").ten().equals("Nguyễn Văn An"), "và thay đổi của An còn nguyên");
        // Điểm cốt lõi: `UPDATE ... WHERE ma=? AND phien_ban=?` không cần khoá gì cả.
        // CSDL trả về số dòng bị ảnh hưởng, và `0` là câu trả lời "có người đã sửa trước
        // bạn". Đây là toàn bộ cơ chế — không có phần nào phức tạp hơn.

        // ---- 3. CON BUG: THỬ LẠI "MÙ" ----
        // Phản xạ đầu tiên khi gặp `0 dòng`: đọc lại phiên bản rồi ghi lại. SAI.
        BanGhi phienBanMoi = db2.doc("KH-01");
        db2.capNhat("KH-01", new BanGhi(binhDoc.ten(), 50_000_000L, 0), phienBanMoi.phienBan());
        check(db2.doc("KH-01").ten().equals("Nguyễn Văn A"), "tên của An lại MẤT lần nữa");
        // Bình chỉ lấy phiên bản MỚI nhưng vẫn ghi bằng dữ liệu CŨ (`binhDoc.ten()`).
        // Kết quả y hệt phần 1 — chỉ chậm hơn vài mili-giây. Số hiệu phiên bản không tự
        // sửa gì; nó chỉ NÓI cho bạn biết phải đọc lại.

        // ---- 4. BẢN ĐÚNG: đọc lại, ÁP DỤNG LẠI thay đổi, rồi ghi ----
        Csdl db3 = new Csdl();
        db3.tao("KH-01", new BanGhi("Nguyễn Văn A", 10_000_000L, 1));
        db3.capNhat("KH-01", new BanGhi("Nguyễn Văn An", 10_000_000L, 0), 1);   // An xong

        long hanMucBinhMuonDat = 50_000_000L;
        int soLanThu = 0;
        boolean xong = false;
        while (!xong && soLanThu < 5) {
            soLanThu++;
            BanGhi tuoi = db3.doc("KH-01");                       // ĐỌC LẠI dữ liệu mới nhất
            BanGhi sua = new BanGhi(tuoi.ten(), hanMucBinhMuonDat, 0);  // ÁP LẠI ý định của Bình
            xong = db3.capNhat("KH-01", sua, tuoi.phienBan()) == 1;
        }
        check(xong && soLanThu == 1, "đọc lại rồi ghi: thành công ngay lần đầu");
        check(db3.doc("KH-01").ten().equals("Nguyễn Văn An"), "tên của An: GIỮ");
        check(db3.doc("KH-01").hanMuc() == 50_000_000L, "hạn mức của Bình: GIỮ");
        check(db3.doc("KH-01").phienBan() == 3, "hai lần ghi, phiên bản 1 -> 3");
        // Ba bước, luôn luôn: ĐỌC LẠI -> ÁP LẠI Ý ĐỊNH -> GHI CÓ KIỂM PHIÊN BẢN.
        // "Ý định" ở đây là `hanMucBinhMuonDat`, không phải cả bản ghi cũ. Phân biệt
        // được Ý ĐỊNH với DỮ LIỆU ĐÃ ĐỌC là điều làm phần 4 khác phần 3.

        // ---- 5. KHÔNG PHẢI Ý ĐỊNH NÀO CŨNG ÁP LẠI ĐƯỢC ----
        // Thử lại tự động chỉ đúng khi ý định KHÔNG phụ thuộc vào dữ liệu đã đọc:
        //   "đặt hạn mức = 50 triệu"    -> áp lại được (tuyệt đối, bài 91 phần 6)
        //   "tăng hạn mức thêm 10%"     -> áp lại được, vì tính trên bản MỚI đọc
        //   "duyệt vì hạn mức < 20tr"   -> KHÔNG: điều kiện duyệt đã dựa trên số cũ
        // Trường hợp thứ ba phải hỏi lại người dùng: hiện màn hình "dữ liệu đã thay đổi,
        // đây là bản mới, bạn có còn muốn duyệt không". Tự động thử lại ở đây là ra một
        // quyết định nghiệp vụ hộ con người.
        Csdl db4 = new Csdl();
        db4.tao("KH-01", new BanGhi("A", 10_000_000L, 1));
        BanGhi doc = db4.doc("KH-01");
        boolean duocDuyet = doc.hanMuc() < 20_000_000L;              // điều kiện dựa trên bản CŨ
        db4.capNhat("KH-01", new BanGhi("A", 90_000_000L, 0), 1);    // người khác nâng lên 90tr
        check(duocDuyet && db4.doc("KH-01").hanMuc() == 90_000_000L,
                "quyết định 'được duyệt' đã lỗi thời — thử lại tự động sẽ duyệt sai");

        // ---- 6. PHIÊN BẢN ĐẶT Ở ĐÂU: ĐÚNG MỘT CÁI, TRÊN AGGREGATE ROOT ----
        // Ba cách đặt, hai cái sai:
        //   - MỖI FIELD một phiên bản  -> An sửa tên, Bình sửa hạn mức: không đụng độ.
        //     Nghe hay, nhưng nó phá BẤT BIẾN: nếu luật là "hạn mức ≤ 20tr với khách
        //     chưa xác minh", hai người sửa hai field có thể cùng nhau tạo ra trạng thái
        //     vi phạm mà không ai vi phạm riêng lẻ. Đây đúng là lý do bài 83 tồn tại.
        //   - PHIÊN BẢN TOÀN CỤC       -> mọi người đụng độ với mọi người.
        //   - MỘT phiên bản trên ROOT  -> đúng: đơn vị nhất quán = đơn vị đụng độ.
        // Hệ quả nối tiếp bài 83 phần 5: aggregate càng TO thì đụng độ giả càng nhiều.
        // Khoá lạc quan không cứu được ranh giới vẽ sai — nó làm hậu quả lộ ra sớm hơn.
        check(db3.doc("KH-01").phienBan() == 3, "một số hiệu cho cả cụm, không phải cho từng field");

        // ---- 7. ĐÂY CHÍNH LÀ compare-and-swap, Ở MỨC ỨNG DỤNG ----
        // `AtomicLong.compareAndSet(kyVong, moi)` làm đúng ba việc như `UPDATE ... WHERE
        // phien_ban = ?`: so với giá trị kỳ vọng, chỉ ghi nếu khớp, báo lại thành/bại.
        AtomicLong o = new AtomicLong(10);
        long docDuoc = o.get();                   // "đọc"
        check(o.compareAndSet(docDuoc, 20), "CAS thành công khi chưa ai đổi");
        check(!o.compareAndSet(docDuoc, 30), "CAS THẤT BẠI khi có người đổi trước");
        check(o.get() == 20, "và không ghi đè gì cả");

        // Và vòng lặp thử lại ở mức CPU có hình dạng giống hệt phần 4:
        int lan = 0;
        while (true) {
            lan++;
            long cu = o.get();                    // ĐỌC LẠI
            if (o.compareAndSet(cu, cu * 2)) break;   // ÁP LẠI Ý ĐỊNH ("nhân đôi") rồi GHI
        }
        check(o.get() == 40 && lan == 1, "cùng ba bước, cùng một thuật toán");
        // Nhìn ra hai thứ này là MỘT giải thích được điều hay bị hỏi: "vì sao phải có
        // vòng lặp?" — vì trong cả hai trường hợp, thất bại KHÔNG phải lỗi, nó là thông
        // tin. Và thông tin đó chỉ dùng được nếu bạn quay lại đọc.

        // ---- 8. LẠC QUAN hay BI QUAN ----
        //
        //             | Khoá LẠC QUAN (phiên bản)   | Khoá BI QUAN (SELECT FOR UPDATE)
        //   ----------|------------------------------|----------------------------------
        //   giả định  | đụng độ HIẾM                 | đụng độ THƯỜNG
        //   chi phí   | 0 khi không đụng; thử lại khi đụng | giữ khoá suốt transaction
        //   rủi ro    | thử lại nhiều lần / đói tài nguyên | deadlock, chờ, nghẽn cổ chai
        //   hợp với   | web, API, người dùng sửa hồ sơ | trừ kho, cấp số phiếu, hàng đợi
        //   người dùng thấy | "dữ liệu đã thay đổi"   | quay vòng chờ
        //
        // Quy tắc thực dụng: mặc định LẠC QUAN. Chỉ chuyển sang bi quan khi đo được rằng
        // tỉ lệ đụng độ cao tới mức thử lại tốn hơn chờ — và trước khi làm thế, hãy xem
        // lại ranh giới aggregate (bài 83), vì đụng độ cao thường là triệu chứng của
        // ranh giới quá to chứ không phải của nghiệp vụ.
        check(db2.soLanDungDo + db3.soLanDungDo >= 1, "đụng độ là số liệu — hãy đo nó");

        System.out.println("OK");
    }
}
