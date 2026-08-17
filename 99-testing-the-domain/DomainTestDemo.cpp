/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — test mô hình miền không khung phần mềm, không
 * CSDL, không mock. Ba con bug: test bám vào CÁCH LÀM; test dựng dữ liệu quá nhiễu; và
 * bản giả nói dối so với bản thật.
 * Tại sao cần học: C++ có một loại test mà Java và Python không có — test chạy LÚC BIÊN
 * DỊCH. Với `constexpr` + `static_assert`, một bất biến của miền được kiểm trước khi
 * chương trình tồn tại, tốn 0 mili-giây lúc chạy, và code sai thì KHÔNG TẠO RA ĐƯỢC file
 * thực thi. Đó là vòng phản hồi nhanh nhất có thể: không phải "test đỏ sau 3 giây" mà là
 * "build đỏ ngay trong trình soạn thảo". Bài chỉ ra phần nào của miền đưa được vào đó, và
 * phần nào thì không.
 */
#include <iostream>
#include <map>
#include <random>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// MIEN — doi tuong duoc test
// =====================================================================
struct DongHang {
    std::string sanPham;
    long donGia;
    int soLuong;
    constexpr long thanhTien() const { return donGia * soLuong; }
};

class KhoHang {
public:
    virtual ~KhoHang() = default;
    virtual void giuCho(const std::string& sp, int sl) = 0;
    virtual void giuChoNhieu(const std::map<std::string, int>& gop) = 0;
};

class DonHang {
public:
    static constexpr long HAN_MUC = 50000000L;

    explicit DonHang(std::string ma) : ma_(std::move(ma)) {}

    void themDong(const std::string& sp, long donGia, int sl, KhoHang& kho) {
        if (tongTien() + donGia * sl > HAN_MUC) throw std::logic_error("don vuot han muc");
        kho.giuCho(sp, sl);
        cacDong_.push_back(DongHang{sp, donGia, sl});
    }

    // Phien ban gop: giu cho MOT lan cho nhieu dong. Hanh vi ngoai KHONG doi.
    void themNhieuDong(const std::vector<DongHang>& ds, KhoHang& kho) {
        long them = 0;
        for (const auto& d : ds) them += d.thanhTien();
        if (tongTien() + them > HAN_MUC) throw std::logic_error("don vuot han muc");
        std::map<std::string, int> gop;
        for (const auto& d : ds) gop[d.sanPham] += d.soLuong;
        kho.giuChoNhieu(gop);                          // MOT luot goi thay vi n luot
        cacDong_.insert(cacDong_.end(), ds.begin(), ds.end());
    }

    long tongTien() const {
        long t = 0;
        for (const auto& d : cacDong_) t += d.thanhTien();
        return t;
    }
    std::size_t soDong() const { return cacDong_.size(); }
    const std::string& ma() const { return ma_; }

private:
    std::string ma_;
    std::vector<DongHang> cacDong_;
};

// Chia deu tien cho n phan — bat bien o phan 3 (bai 90). `constexpr` de dung o phan 5.
constexpr long phanThuI(long tong, int n, int i) {
    return tong / n + (i < tong % n ? 1 : 0);
}

// Ban GIA — mot cai dat that, khong phai mock.
class KhoGia : public KhoHang {
public:
    void giuCho(const std::string& sp, int sl) override { ++soLuotGoi; daGiu[sp] += sl; }
    void giuChoNhieu(const std::map<std::string, int>& gop) override {
        ++soLuotGoi;
        for (const auto& [k, v] : gop) daGiu[k] += v;
    }
    std::map<std::string, int> daGiu;
    int soLuotGoi = 0;
};

// Ban "that" — co them mot luat ma ban gia khong co.
class KhoThat : public KhoHang {
public:
    void giuCho(const std::string& sp, int sl) override {
        if (sl <= 0) throw std::invalid_argument("so luong giu cho phai duong");
        daGiu[sp] += sl;
    }
    void giuChoNhieu(const std::map<std::string, int>& gop) override {
        for (const auto& [k, v] : gop) giuCho(k, v);
    }
    std::map<std::string, int> daGiu;
};

// BO DUNG DU LIEU TEST — phan 2
class DonHangBuilder {
public:
    DonHangBuilder& ma(std::string m) { ma_ = std::move(m); return *this; }
    DonHangBuilder& voiDong(std::string sp, long gia, int sl) {
        dong_.push_back(DongHang{std::move(sp), gia, sl});
        return *this;
    }
    DonHang dung(KhoHang& kho) {
        DonHang d(ma_);
        if (!dong_.empty()) d.themNhieuDong(dong_, kho);
        return d;
    }
private:
    std::string ma_ = "DH-MAU";
    std::vector<DongHang> dong_;
};

// =====================================================================
// PHAN 5 — TEST CHAY LUC BIEN DICH
// =====================================================================
constexpr long tongPhan(long tong, int n) {
    long s = 0;
    for (int i = 0; i < n; ++i) s += phanThuI(tong, n, i);
    return s;
}
constexpr long chenhLech(long tong, int n) {
    long lon = phanThuI(tong, n, 0), nho = phanThuI(tong, n, n - 1);
    return lon - nho;
}
// Ba bat bien cua bai 90, kiem TRUOC khi chuong trinh ton tai:
static_assert(tongPhan(100, 3) == 100, "tong cac phan phai bang tong ban dau");
static_assert(tongPhan(1, 7) == 1, "ke ca khi tong nho hon so phan");
static_assert(tongPhan(999999937, 9) == 999999937, "va voi so nguyen to lon");
static_assert(chenhLech(100, 3) <= 1, "chenh lech giua cac phan khong qua 1 don vi");
static_assert(chenhLech(1, 7) <= 1, "ke ca truong hop bien");

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CON BUG: test bam vao CACH LAM, khong bam vao HANH VI ----
    KhoGia khoA;
    DonHang donA("DH-01");
    donA.themDong("laptop", 1000000L, 1, khoA);
    donA.themDong("chuot", 200000L, 2, khoA);
    donA.themDong("ban phim", 300000L, 1, khoA);
    check(khoA.soLuotGoi == 3, "test-theo-cach-lam: 'kho phai duoc goi DUNG 3 lan'");

    // Hom nay ai do gop ba luot goi thanh mot. HANH VI NGOAI KHONG DOI.
    KhoGia khoB;
    DonHang donB("DH-01");
    donB.themNhieuDong({{"laptop", 1000000L, 1}, {"chuot", 200000L, 2}, {"ban phim", 300000L, 1}}, khoB);

    check(khoB.soLuotGoi == 1, "test-theo-cach-lam gio DO: 1 != 3");
    check(donA.tongTien() == donB.tongTien(), "nhung tong tien GIONG HET");
    check(donA.soDong() == donB.soDong(), "cung so dong");
    check(khoA.daGiu == khoB.daGiu, "va kho giu cho DUNG NHU NHAU");
    // Ba dong cuoi la test-theo-HANH-VI, va ca ba van xanh. Khac biet:
    //   - Test hanh vi hong khi NGHIEP VU sai  -> tin hieu THAT.
    //   - Test cach lam hong khi CODE DOI      -> tin hieu GIA.
    // Bo test day tin hieu gia la bo test bi tat sau ba thang.
    //
    // Ngoai le hop le duy nhat: khi VIEC GOI CHINH LA hanh vi can kiem — "da gui dung mot
    // email cho khach" (bai 84 phan 4).

    // ---- 2. BO DUNG DU LIEU: test doc len phai noi duoc no kiem gi ----
    KhoGia kho2;
    DonHang ganHanMuc = DonHangBuilder()
                            .ma("DH-02")
                            .voiDong("may chu", 49000000L, 1)   // <- chi tiet DUY NHAT quan trong
                            .dung(kho2);
    bool vuotHanMuc = false;
    try { ganHanMuc.themDong("laptop", 2000000L, 1, kho2); }
    catch (const std::logic_error&) { vuotHanMuc = true; }
    check(vuotHanMuc, "don 49 trieu + 2 trieu -> vuot han muc 50 trieu");
    // Bo dung co gia tri mac dinh cho MOI thu, va test chi noi ra thu no QUAN TAM.

    // ---- 3. BAT BIEN: kiem voi NGHIN dau vao, khong phai ba ----
    std::mt19937 rnd(42);                         // hat giong CO DINH -> tai hien duoc
    std::uniform_int_distribution<long> dGia(1, 1000000);
    std::uniform_int_distribution<int> dSl(1, 40), dNguoi(1, 9);
    int soCaChay = 0;
    for (int i = 0; i < 1000; ++i) {
        KhoGia k;
        DonHang d = DonHangBuilder().voiDong("x", dGia(rnd), dSl(rnd)).dung(k);
        int nguoi = dNguoi(rnd);
        long tong = d.tongTien(), s = 0, lon = 0, nho = tong;
        for (int j = 0; j < nguoi; ++j) {
            long p = phanThuI(tong, nguoi, j);
            s += p;
            if (p > lon) lon = p;
            if (p < nho) nho = p;
        }
        check(s == tong, "BAT BIEN: tong cac phan = tong ban dau");
        check(lon - nho <= 1, "BAT BIEN: chenh lech giua cac phan khong qua 1 don vi");
        ++soCaChay;
    }
    check(soCaChay == 1000, "1.000 ca sinh ngau nhien, 0 dong du lieu go tay");
    // Hat giong co dinh la bat buoc: mot test do ngau nhien ma khong tai hien duoc thi vo
    // dung — va te hon, no se bi danh dau "bo qua".

    // ---- 4. CON BUG: BAN GIA NOI DOI ----
    KhoGia khoNoiDoi;
    khoNoiDoi.giuCho("laptop", -5);               // so luong AM
    check(khoNoiDoi.daGiu["laptop"] == -5, "ban gia nhan so luong am — test XANH");

    bool banThatNo = false;
    try { KhoThat().giuCho("laptop", -5); }
    catch (const std::invalid_argument&) { banThatNo = true; }
    check(banThatNo, "ban that nem ngoai le — bug di thang ra production");
    // Cach chua la BO KIEM TRA HOP DONG (bai 68): mot bo test viet mot lan, chay tren MOI
    // cai dat cua cong. Ban gia nao khong qua duoc thi khong duoc dung.
    KhoGia g;
    KhoThat t;
    std::vector<KhoHang*> moiCaiDat{&g, &t};
    int soCaiDatQua = 0;
    for (KhoHang* k : moiCaiDat) {
        bool chan = false;
        try { k->giuCho("x", -1); } catch (const std::invalid_argument&) { chan = true; }
        if (chan) ++soCaiDatQua;
    }
    check(soCaiDatQua == 1, "chay hop dong tren 2 cai dat -> lo ra ngay cai nao noi doi");

    // ---- 5. DIEU CHI C++ CO: TEST CHAY LUC BIEN DICH ----
    // Nam `static_assert` o dau file da chay XONG truoc khi chuong trinh nay ton tai.
    // Chung kiem dung ba bat bien o phan 3, nhung:
    //   - ton 0 mili-giay luc chay;
    //   - khong the bi "bo qua", khong the bi tat bang co dong lenh;
    //   - code sai thi KHONG TAO RA DUOC file thuc thi.
    // Do la vong phan hoi nhanh nhat co the: khong phai "test do sau 3 giay" ma la "build
    // do ngay trong trinh soan thao".
    //
    // Gioi han — phan nao cua mien dua vao day duoc:
    //   DUOC   : phep tinh thuan tren so/kieu don gian (chia tien, chuyen doi don vi,
    //            bang chuyen trang thai — bai 89 phan 5).
    //   KHONG  : bat cu thu gi dung `std::string` phuc tap, cap phat dong, hay ngoai le.
    // Nen `static_assert` KHONG thay the duoc bo test — no phu them mot lop, cho dung
    // nhung bat bien thuan tuy nhat va quan trong nhat.
    constexpr long kiemLucBienDich = tongPhan(1700000, 3);
    static_assert(kiemLucBienDich == 1700000, "bat bien cua don hang o phan 1");
    check(kiemLucBienDich == 1700000, "va van doc duoc luc chay nhu mot hang so");

    // ---- 6. TEST MIEN KHONG CAN KHUNG PHAN MEM ----
    // Toan bo file nay — va moi file self-check trong series — la ham + `check`. Khong
    // Google Test, khong mock, khong fixture, 0 mili-giay khoi dong.
    //
    // Do khong phai "luoi khong dung Google Test". No la BANG CHUNG rang mo hinh mien da
    // tach sach khoi ha tang (bai 98). Phep thu nguoc lai cung dung:
    //
    //   NEU TEST MIEN CUA BAN CAN MOT KHUNG PHAN MEM DE CHAY,
    //   THI THU BAN DANG TEST KHONG PHAI MIEN.
    check(donA.ma() == "DH-01", "mot ham, mot check, khong ha tang nao");

    // ---- 7. CAI GI KHONG NEN TEST, VA DAT TEN THE NAO ----
    //   - Getter thuan: `check(d.ma() == "DH-01")` khong kiem duoc luat nao.
    //   - Thu vien chuan: `std::map` da duoc test roi.
    //   - Cai dat rieng tu: neu phai doi `private` thanh `public` de test, thi test do
    //     dang bam vao cach lam (phan 1) — hay test qua cua chinh.
    // Nguoc lai, thu DANG test nhat la nhung cho co `if` mang nghia nghiep vu: han muc,
    // chuyen trang thai (bai 89), luat gia, luat chia tien.
    //
    // Va ten test la mot cau nghiep vu:
    //   TE : "test1", "testThemDong"
    //   TOT: "don 49 trieu + 2 trieu -> vuot han muc 50 trieu"
    // Khi test do luc 2 gio sang, dong chu do la toan bo thu nguoi truc co.
    check(DonHang::HAN_MUC == 50000000L, "luat nghiep vu co `if` -> dang test");

    std::cout << "OK\n";
    return 0;
}
