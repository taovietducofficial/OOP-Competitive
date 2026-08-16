/*
 * Ngôn ngữ: Java
 * Công dụng: Tiền là một value object, không phải một con số. Bài cho nổ bốn con bug
 * thật: `double` làm lệch sổ sau vài nghìn giao dịch; cộng hai loại tiền tệ ra một con
 * số vô nghĩa; chia 100đ cho 3 người làm bốc hơi 1 xu; và giả định "tiền tệ nào cũng 2
 * chữ số thập phân" làm sai 100 lần với đồng Việt Nam.
 * Tại sao cần học: đây là value object quan trọng nhất trong mọi hệ thống nghiệp vụ, và
 * là chỗ mọi người đều tin là mình làm đúng. Riêng Java có một cái bẫy ít ai biết cho
 * tới lúc bị: `BigDecimal` — thứ được khuyên dùng cho tiền — có `equals()` so cả SỐ CHỮ
 * SỐ THẬP PHÂN, nên `2.0` và `2.00` là hai phần tử khác nhau trong `HashSet`. Bài đo
 * đúng chỗ đó.
 */
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MoneyDemo {

    // Số chữ số thập phân KHÔNG phải lúc nào cũng là 2. Đây là dữ liệu, không phải hằng số.
    enum TienTe {
        VND(0),   // đồng Việt Nam: không có đơn vị nhỏ hơn
        USD(2),   // 1 dollar = 100 cent
        JOD(3);   // dinar Jordan = 1000 fils

        final int soChuSoThapPhan;
        TienTe(int n) { this.soChuSoThapPhan = n; }
        long heSo() { return (long) Math.pow(10, soChuSoThapPhan); }
    }

    /**
     * Tiền = số ĐƠN VỊ NHỎ NHẤT (xu / cent / fils) + loại tiền tệ.
     * Dùng `long` chứ không dùng `double`: mọi phép cộng trừ là chính xác tuyệt đối.
     */
    record Tien(long donViNho, TienTe tienTe) implements Comparable<Tien> {

        static Tien tu(String nguyenVaLe, TienTe tt) {
            BigDecimal b = new BigDecimal(nguyenVaLe).movePointRight(tt.soChuSoThapPhan);
            if (b.stripTrailingZeros().scale() > 0)
                throw new IllegalArgumentException("số tiền nhỏ hơn đơn vị nhỏ nhất của " + tt);
            return new Tien(b.longValueExact(), tt);
        }

        Tien cong(Tien k) { cungTe(k); return new Tien(donViNho + k.donViNho, tienTe); }
        Tien tru(Tien k) { cungTe(k); return new Tien(donViNho - k.donViNho, tienTe); }

        /** Tiền × HỆ SỐ = tiền. Tiền × tiền là vô nghĩa — xem phần 6. */
        Tien nhan(BigDecimal heSo, RoundingMode lamTron) {
            return new Tien(BigDecimal.valueOf(donViNho).multiply(heSo)
                    .setScale(0, lamTron).longValueExact(), tienTe);
        }

        /** Tiền ÷ tiền = TỈ LỆ, không phải tiền. */
        BigDecimal tiLeSoVoi(Tien k) {
            cungTe(k);
            return BigDecimal.valueOf(donViNho).divide(BigDecimal.valueOf(k.donViNho), 6, RoundingMode.HALF_UP);
        }

        /** Chia đều cho n phần, KHÔNG làm mất một xu nào. Xem phần 4. */
        List<Tien> chiaDeu(int n) {
            if (n <= 0) throw new IllegalArgumentException("số phần phải dương");
            long moiPhan = donViNho / n;
            long du = donViNho % n;                  // phần dư đem chia tiếp, không vứt
            List<Tien> ra = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ra.add(new Tien(moiPhan + (i < du ? 1 : 0), tienTe));
            return ra;
        }

        /** Chia theo tỉ lệ, cũng không mất xu nào. */
        List<Tien> chiaTheo(int... tiLe) {
            long tongTiLe = 0;
            for (int t : tiLe) tongTiLe += t;
            List<Tien> ra = new ArrayList<>(tiLe.length);
            long daChia = 0;
            for (int i = 0; i < tiLe.length; i++) {
                long phan = donViNho * tiLe[i] / tongTiLe;
                ra.add(new Tien(phan, tienTe));
                daChia += phan;
            }
            long du = donViNho - daChia;            // phần dư do làm tròn xuống
            for (int i = 0; i < du; i++) ra.set(i, new Tien(ra.get(i).donViNho + 1, tienTe));
            return ra;
        }

        private void cungTe(Tien k) {
            if (tienTe != k.tienTe)
                throw new IllegalArgumentException("không cộng trừ được " + tienTe + " với " + k.tienTe);
        }

        @Override public int compareTo(Tien k) { cungTe(k); return Long.compare(donViNho, k.donViNho); }

        @Override public String toString() {
            return BigDecimal.valueOf(donViNho).movePointLeft(tienTe.soChuSoThapPhan)
                    .setScale(tienTe.soChuSoThapPhan) + " " + tienTe;
        }
    }

    // ---- Self-check ----
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // ---- 1. CON BUG: `double` không biểu diễn được 0.1 ----
        check(0.1 + 0.2 != 0.3, "0.1 + 0.2 KHÁC 0.3 trong số thực dấu phẩy động");
        check(0.1 + 0.2 == 0.30000000000000004, "nó bằng đúng con số này");
        // Nguyên nhân: 0.1 trong hệ nhị phân là số vô hạn tuần hoàn, y như 1/3 trong hệ
        // thập phân. Không có gì "sửa" được điều đó — nó là bản chất của kiểu dữ liệu.

        double viDouble = 0.0;
        for (int i = 0; i < 10_000; i++) viDouble += 0.01;      // 10.000 lần cộng 1 xu
        check(viDouble != 100.0, "sau 10.000 giao dịch nhỏ, sổ đã lệch");
        check(new BigDecimal(viDouble).compareTo(new BigDecimal("100")) != 0,
                "độ lệch là THẬT — đây là giá trị nhị phân chính xác, không phải ảo giác hiển thị");

        long viLong = 0;
        for (int i = 0; i < 10_000; i++) viLong += 1;           // cộng bằng ĐƠN VỊ NHỎ NHẤT
        check(viLong == 10_000, "cộng bằng số nguyên: chính xác tuyệt đối, mãi mãi");

        // ---- 2. CON BUG: cộng hai loại tiền tệ ----
        double sai = 100.0 + 50.0;                    // 100 USD + 50 VND = ?
        check(sai == 150.0, "phép cộng chạy ngon lành, và kết quả hoàn toàn vô nghĩa");
        Tien usd = Tien.tu("100.00", TienTe.USD);
        Tien vnd = Tien.tu("50", TienTe.VND);
        boolean chan = false;
        try { usd.cong(vnd); } catch (IllegalArgumentException e) { chan = true; }
        check(chan, "value object mang LUẬT: cộng khác tệ bị chặn (bài 82)");
        check(usd.cong(Tien.tu("0.50", TienTe.USD)).toString().equals("100.50 USD"), "cùng tệ thì được");

        // ---- 3. CON BUG: giả định "tiền tệ nào cũng 2 chữ số thập phân" ----
        long xuCuaVnd = 100_000L * 100;               // "đổi sang xu" theo thói quen
        check(xuCuaVnd == 10_000_000L, "100.000đ thành 10 triệu — sai 100 lần");
        check(TienTe.VND.heSo() == 1 && TienTe.USD.heSo() == 100 && TienTe.JOD.heSo() == 1000,
                "ba loại tiền tệ, ba hệ số khác nhau");
        check(Tien.tu("100000", TienTe.VND).donViNho() == 100_000L, "VND: 1 đồng là đơn vị nhỏ nhất");
        check(Tien.tu("100.00", TienTe.USD).donViNho() == 10_000L, "USD: 100 đô = 10.000 cent");
        check(Tien.tu("1.500", TienTe.JOD).donViNho() == 1_500L, "JOD: 1,5 dinar = 1500 fils");

        boolean quaNho = false;
        try { Tien.tu("100.50", TienTe.VND); } catch (IllegalArgumentException e) { quaNho = true; }
        check(quaNho, "0,5 đồng KHÔNG tồn tại -> chặn ngay tại biên, không làm tròn lén");

        // ---- 4. CON BUG: chia 100 cho 3 làm bốc hơi tiền ----
        double moiNguoiDouble = Math.round(100.0 / 3 * 100) / 100.0;
        check(moiNguoiDouble == 33.33, "mỗi người 33,33");
        check(moiNguoiDouble * 3 == 99.99, "ba người cộng lại được 99,99 — thiếu 1 xu");
        // 1 xu đó đi đâu? Không đi đâu cả — nó bị làm tròn mất. Nhân với một triệu giao
        // dịch chia hoá đơn mỗi tháng, và kế toán có một khoản chênh không giải thích được.

        Tien tram = Tien.tu("100.00", TienTe.USD);
        List<Tien> ba = tram.chiaDeu(3);
        long tong = ba.stream().mapToLong(Tien::donViNho).sum();
        check(tong == tram.donViNho(), "chia đều: tổng các phần BẰNG ĐÚNG số ban đầu");
        check(ba.get(0).toString().equals("33.34 USD"), "người đầu nhận thêm 1 cent dư");
        check(ba.get(1).toString().equals("33.33 USD") && ba.get(2).toString().equals("33.33 USD"),
                "hai người sau nhận 33,33");
        // Thuật toán: chia lấy nguyên, rồi PHÁT phần dư cho các phần đầu, mỗi phần 1 đơn
        // vị. Không xu nào biến mất, không xu nào sinh ra. Ai nhận phần dư là một quyết
        // định NGHIỆP VỤ — có nơi cho người đầu, có nơi cho người trả tiền, có nơi bốc
        // thăm — nhưng nó phải là một quyết định, không phải hệ quả của việc làm tròn.

        List<Tien> theoTiLe = Tien.tu("100.00", TienTe.USD).chiaTheo(3, 7);
        check(theoTiLe.get(0).toString().equals("30.00 USD")
                && theoTiLe.get(1).toString().equals("70.00 USD"), "chia 30/70");
        List<Tien> le = Tien.tu("0.05", TienTe.USD).chiaTheo(3, 7);
        check(le.get(0).donViNho() + le.get(1).donViNho() == 5, "5 cent chia 30/70 vẫn đủ 5 cent");

        // ---- 5. LÀM TRÒN LÀ MỘT QUYẾT ĐỊNH NGHIỆP VỤ, KHÔNG PHẢI MẶC ĐỊNH ----
        Tien goc = Tien.tu("10.005", TienTe.JOD);
        check(goc.nhan(new BigDecimal("0.5"), RoundingMode.HALF_UP).toString().equals("5.003 JOD"),
                "HALF_UP: 5,0025 -> 5,003");
        check(goc.nhan(new BigDecimal("0.5"), RoundingMode.HALF_EVEN).toString().equals("5.002 JOD"),
                "HALF_EVEN (làm tròn ngân hàng): 5,0025 -> 5,002");
        check(goc.nhan(new BigDecimal("0.5"), RoundingMode.DOWN).toString().equals("5.002 JOD"),
                "DOWN: cắt cụt");
        // Ba chế độ, ba con số. Không có cái nào "đúng" — cái đúng là cái luật thuế/kế
        // toán của nước đó quy định. Nên `nhan()` BẮT BUỘC nhận chế độ làm tròn: không có
        // giá trị mặc định nào an toàn, và để mặc định là để người sau đoán.

        // ---- 6. THỨ NGUYÊN: tiền × tiền là vô nghĩa ----
        // `Tien.nhan` nhận `BigDecimal` (hệ số, thuế suất, tỉ giá), KHÔNG nhận `Tien`.
        // Dòng `usd.nhan(vnd)` không biên dịch được, và đó là ý đồ:
        //     tiền × số   = tiền     (100 USD × 0.1 = 10 USD thuế)
        //     tiền ÷ tiền = TỈ LỆ    (30 USD / 100 USD = 0.3)
        //     tiền × tiền = KHÔNG CÓ NGHĨA — "đô-la bình phương" không tồn tại
        check(Tien.tu("30.00", TienTe.USD).tiLeSoVoi(Tien.tu("100.00", TienTe.USD))
                .compareTo(new BigDecimal("0.300000")) == 0, "tiền ÷ tiền ra một tỉ lệ trần");
        check(Tien.tu("100.00", TienTe.USD).nhan(new BigDecimal("0.10"), RoundingMode.HALF_UP)
                .toString().equals("10.00 USD"), "tiền × thuế suất ra tiền");

        // ---- 7. CÁI BẪY RIÊNG CỦA JAVA: `BigDecimal.equals` ----
        BigDecimal haiChan = new BigDecimal("2.0");
        BigDecimal haiHai = new BigDecimal("2.00");
        check(!haiChan.equals(haiHai), "2.0 KHÔNG equals 2.00 — vì `scale` khác nhau");
        check(haiChan.compareTo(haiHai) == 0, "nhưng compareTo nói chúng bằng nhau");
        Set<BigDecimal> tap = new HashSet<>(List.of(haiChan, haiHai));
        check(tap.size() == 2, "và HashSet giữ CẢ HAI — cùng một số tiền nằm ở hai chỗ");
        // Hậu quả thật: gộp giao dịch theo số tiền, đối chiếu sao kê, khoá `Map` là số
        // tiền — cả ba đều hỏng âm thầm. Nếu buộc phải dùng `BigDecimal` cho tiền, LUÔN
        // so bằng `compareTo`, và luôn `setScale` cố định trước khi bỏ vào `Set`/`Map`.
        //
        // Bản `record Tien` ở trên không có vấn đề này, vì nó so `long` và `enum`:
        Set<Tien> tapTien = new HashSet<>(List.of(Tien.tu("2.00", TienTe.USD), Tien.tu("2.0", TienTe.USD)));
        check(tapTien.size() == 1, "hai cách viết, một số tiền, một phần tử");

        // ---- 8. Ranh giới: khi nào `long` đơn vị nhỏ KHÔNG đủ ----
        // `long` chứa được ~9,2 tỉ tỉ. Với VND (hệ số 1) là ~9 tỉ tỉ đồng — thừa cho mọi
        // doanh nghiệp. Nhưng có ba trường hợp phải dùng `BigDecimal`:
        //   - giá đơn vị nhỏ hơn đơn vị tiền nhỏ nhất (giá điện 1.234,56 đ/kWh, giá dầu
        //     tính tới 4 chữ số) -> đó KHÔNG phải tiền, đó là ĐƠN GIÁ, kiểu riêng;
        //   - tính lãi kép nhiều kỳ, cần giữ độ chính xác trung gian;
        //   - tiền mã hoá (18 chữ số thập phân) -> `BigInteger`, và `long` tràn thật.
        // Quy tắc: `long` đơn vị nhỏ cho SỐ TIỀN, `BigDecimal` cho ĐƠN GIÁ và TỈ LỆ, và
        // hai thứ đó là hai kiểu dữ liệu khác nhau — đúng như phần 6 nói.
        check(Long.MAX_VALUE / 100 > 92_000_000_000_000_000L, "long thừa sức cho tiền thật");

        System.out.println("OK");
    }
}
