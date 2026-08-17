/*
 * Ngôn ngữ: C++
 * Công dụng: Bài tổng kết bản C++ — một hệ đặt hàng nhỏ nhưng đầy đủ, ghép lại mọi thứ
 * tầng 04-competitive đã dựng, và dùng đúng những công cụ mà chỉ C++ mới có: tiền tệ là
 * THAM SỐ KIỂU (cộng khác tệ không biên dịch được), bảng chuyển trạng thái là `constexpr`
 * được `static_assert` kiểm lúc biên dịch, và saga trong tiến trình là RAII.
 * Tại sao cần học: từng bài trước dạy MỘT thứ và cố tình bỏ qua phần còn lại. Bài này cho
 * thấy chúng là MỘT thiết kế: aggregate cần ranh giới vì có bất biến; bất biến buộc phải
 * tham chiếu bằng id; tham chiếu bằng id buộc phải có sự kiện; sự kiện buộc phải
 * idempotent. Rút một mắt xích ra thì cả chuỗi lỏng.
 */
#include <array>
#include <iostream>
#include <map>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <variant>
#include <vector>
#include <cstdlib>

// =====================================================================
// MIEN · VALUE OBJECT — tien te la THAM SO KIEU (bai 90)
// =====================================================================
struct VND { static constexpr int SO_CHU_SO = 0; static constexpr const char* MA = "VND"; };
struct USD { static constexpr int SO_CHU_SO = 2; static constexpr const char* MA = "USD"; };

template <class TT>
class Tien {
public:
    explicit constexpr Tien(long donViNho = 0) : v_(donViNho) {
        if (v_ < 0) throw std::invalid_argument("so tien khong am");
    }
    constexpr long giaTri() const { return v_; }
    constexpr Tien operator+(const Tien& k) const { return Tien(v_ + k.v_); }
    constexpr Tien operator-(const Tien& k) const { return Tien(v_ - k.v_); }
    constexpr Tien phanTram(int pt) const { return Tien(v_ * pt / 100); }
    constexpr bool operator==(const Tien& k) const { return v_ == k.v_; }
    constexpr bool operator>(const Tien& k) const { return v_ > k.v_; }
private:
    long v_;
};

// Chung minh: cong khac te KHONG BIEN DICH DUOC (bai 90 phan 7).
template <class A, class B, class = void>
struct CongDuoc : std::false_type {};
template <class A, class B>
struct CongDuoc<A, B, std::void_t<decltype(std::declval<A>() + std::declval<B>())>> : std::true_type {};
static_assert(CongDuoc<Tien<VND>, Tien<VND>>::value, "cung te thi cong duoc");
static_assert(!CongDuoc<Tien<VND>, Tien<USD>>::value, "khac te thi KHONG duoc phep cong");

struct MaDonHang { std::string v; };
struct MaKhachHang { std::string v; };
struct DongHang {
    std::string sanPham;
    Tien<VND> donGia;
    int soLuong;
    Tien<VND> thanhTien() const { return Tien<VND>(donGia.giaTri() * soLuong); }
};

// =====================================================================
// MIEN · MAY TRANG THAI — bang `constexpr`, kiem luc bien dich (bai 89)
// =====================================================================
enum class TrangThai { MOI_TAO, DA_THANH_TOAN, DA_GIAO, DA_HUY, SO_LUONG };
enum class SuKienChuyen { THANH_TOAN, GIAO, HUY, SO_LUONG };
constexpr std::size_t N_TT = static_cast<std::size_t>(TrangThai::SO_LUONG);
constexpr std::size_t N_SK = static_cast<std::size_t>(SuKienChuyen::SO_LUONG);
constexpr TrangThai KHONG = TrangThai::SO_LUONG;

constexpr TrangThai BANG[N_TT][N_SK] = {
    /* MOI_TAO       */ {TrangThai::DA_THANH_TOAN, KHONG,              TrangThai::DA_HUY},
    /* DA_THANH_TOAN */ {KHONG,                    TrangThai::DA_GIAO, TrangThai::DA_HUY},
    /* DA_GIAO       */ {KHONG, KHONG, KHONG},
    /* DA_HUY        */ {KHONG, KHONG, KHONG},
};
constexpr int demCanh() {
    int n = 0;
    for (std::size_t t = 0; t < N_TT; ++t)
        for (std::size_t s = 0; s < N_SK; ++s)
            if (BANG[t][s] != KHONG) ++n;
    return n;
}
static_assert(demCanh() == 4, "may trang thai phai co dung 4 canh hop le");

// =====================================================================
// MIEN · SU KIEN (bai 84) — variant, vet can luc bien dich
// =====================================================================
struct DonHangDaTao  { std::string maDon, maKhach; long tong, luc; };
struct DonHangDaGiao { std::string maDon; long tongLucGiao, luc; };
using SuKien = std::variant<DonHangDaTao, DonHangDaGiao>;

// =====================================================================
// MIEN · AGGREGATE ROOT (bai 83 ranh gioi, 92 phien ban, 84 ghi su kien)
// =====================================================================
class DonHang {
public:
    static constexpr long HAN_MUC = 50000000L;

    DonHang(MaDonHang ma, MaKhachHang maKhach, long luc)
        : ma_(std::move(ma)), maKhach_(std::move(maKhach)) {
        suKienChuaPhat_.push_back(DonHangDaTao{ma_.v, maKhach_.v, 0, luc});
    }
    // Entity: KHONG sao chep duoc (bai 82) — dinh danh khong duoc nhan ban.
    DonHang(const DonHang&) = delete;
    DonHang& operator=(const DonHang&) = delete;
    DonHang(DonHang&&) = default;

    void themDong(std::string sp, Tien<VND> donGia, int sl) {
        Tien<VND> sau = tongTien() + Tien<VND>(donGia.giaTri() * sl);
        if (sau.giaTri() > HAN_MUC) throw std::logic_error("don vuot han muc");  // BAT BIEN
        cacDong_.push_back(DongHang{std::move(sp), donGia, sl});
        ++phienBan_;
    }
    void chuyen(SuKienChuyen sk) {
        TrangThai ke = BANG[static_cast<std::size_t>(trangThai_)][static_cast<std::size_t>(sk)];
        if (ke == KHONG) throw std::logic_error("chuyen trang thai khong hop le");
        trangThai_ = ke;
        ++phienBan_;
    }
    void giao(long luc) {
        chuyen(SuKienChuyen::GIAO);                          // nem thi KHONG toi dong duoi
        suKienChuaPhat_.push_back(DonHangDaGiao{ma_.v, tongTien().giaTri(), luc});
    }

    Tien<VND> tongTien() const {
        Tien<VND> t;
        for (const auto& d : cacDong_) t = t + d.thanhTien();
        return t;
    }
    const std::string& ma() const { return ma_.v; }
    const std::string& maKhach() const { return maKhach_.v; }
    TrangThai trangThai() const { return trangThai_; }
    long phienBan() const { return phienBan_; }
    std::size_t soDong() const { return cacDong_.size(); }
    const std::vector<DongHang>& cacDong() const { return cacDong_; }   // `const&` = cua dong
    std::vector<SuKien> layVaXoaSuKien() {
        std::vector<SuKien> ds = std::move(suKienChuaPhat_);
        suKienChuaPhat_.clear();
        return ds;
    }

private:
    MaDonHang ma_;
    MaKhachHang maKhach_;                    // tham chieu aggregate khac BANG ID
    std::vector<DongHang> cacDong_;
    TrangThai trangThai_ = TrangThai::MOI_TAO;
    long phienBan_ = 0;
    std::vector<SuKien> suKienChuaPhat_;     // GHI, khong PHAT
};

// =====================================================================
// MIEN · SPECIFICATION (bai 87) va POLICY (bai 88)
// =====================================================================
struct KetQuaDacTa { bool dat; std::vector<std::string> lyDoTruot; };

static KetQuaDacTa duocGiamGia(const DonHang& d) {
    KetQuaDacTa r{true, {}};
    if (d.tongTien().giaTri() < 1000000L) { r.dat = false; r.lyDoTruot.push_back("don tu 1.000.000 tro len"); }
    if (d.soDong() < 2) { r.dat = false; r.lyDoTruot.push_back("tu 2 dong hang tro len"); }
    return r;
}

enum class QuocGia { VN, US, SO_LUONG };
using BangThue = std::array<int, static_cast<std::size_t>(QuocGia::SO_LUONG)>;
constexpr BangThue THUE_PHAN_TRAM{10, 0};    // VN 10%, US 0% — 0% CO TEN, khong phai thieu
static_assert(THUE_PHAN_TRAM.size() == 2, "du chinh sach cho moi quoc gia");

// =====================================================================
// MIEN · CONG (bai 98) — noi tieng nghiep vu, khong biet ha tang
// =====================================================================
class KhoDonHang {
public:
    virtual ~KhoDonHang() = default;
    virtual DonHang* timTheoMa(const std::string& ma) = 0;
    virtual int luu(DonHang& d, long phienBanKyVong) = 0;      // tra so dong — bai 92
};
class BaoChoKhach {
public:
    virtual ~BaoChoKhach() = default;
    virtual void bao(const std::string& kh, const std::string& noiDung) = 0;
};
class DongHo {
public:
    virtual ~DongHo() = default;
    virtual long bayGio() const = 0;
};

// =====================================================================
// HA TANG · BO NOI + MO HINH DOC (bai 95)
// =====================================================================
struct DongDanhSachDon { std::string maDon, maKhach, trangThai; int soDong; long tongTien; };

class KhoTrongBoNho : public KhoDonHang {
public:
    DonHang* timTheoMa(const std::string& ma) override {
        ++soLuotTruyVan;
        auto it = bang.find(ma);
        return it == bang.end() ? nullptr : &it->second;
    }
    int luu(DonHang& d, long pbKyVong) override {
        ++soLuotTruyVan;
        auto itPb = phienBan.find(d.ma());
        if (itPb != phienBan.end() && itPb->second != pbKyVong) { ++soLanDungDo; return 0; }
        phienBan[d.ma()] = pbKyVong;
        if (bang.find(d.ma()) == bang.end()) bang.emplace(d.ma(), std::move(d));
        return 1;
    }
    // Duong DOC: mot truy van, mo hinh phang — bai 95.
    std::vector<DongDanhSachDon> danhSach() {
        ++soLuotTruyVan;
        std::vector<DongDanhSachDon> ra;
        for (const auto& [k, d] : bang)
            ra.push_back(DongDanhSachDon{d.ma(), d.maKhach(), "?",
                                         static_cast<int>(d.soDong()), d.tongTien().giaTri()});
        return ra;
    }
    std::map<std::string, DonHang> bang;
    std::map<std::string, long> phienBan;
    int soLuotTruyVan = 0, soLanDungDo = 0;
};
class BaoGia : public BaoChoKhach {
public:
    void bao(const std::string& kh, const std::string& nd) override { daBao.push_back(kh + ":" + nd); }
    std::vector<std::string> daBao;
};
class DongHoCoDinh : public DongHo {
public:
    explicit DongHoCoDinh(long l) : l_(l) {}
    long bayGio() const override { return l_; }
private:
    long l_;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    KhoTrongBoNho kho;
    BaoGia bao;
    DongHoCoDinh ho(1700000000L);
    std::vector<SuKien> daPhat;
    std::map<std::string, long> soIdempotency;      // khoa -> so tien phai tra (bai 91)
    int soLanThucSuXuLy = 0;

    // Tang UNG DUNG (bai 86): dieu phoi, khong tinh luat nao cua mien.
    auto datHang = [&](const std::string& khoa, const std::string& maKhach, QuocGia qg,
                       const std::vector<DongHang>& gio, bool luuThatBai) -> long {
        auto it = soIdempotency.find(khoa);
        if (it != soIdempotency.end()) return it->second;    // phat lai KET QUA CU — bai 91

        ++soLanThucSuXuLy;
        DonHang d(MaDonHang{"DH-" + std::to_string(soLanThucSuXuLy)}, MaKhachHang{maKhach},
                  ho.bayGio());
        for (const auto& x : gio) d.themDong(x.sanPham, x.donGia, x.soLuong);

        auto dt = duocGiamGia(d);
        Tien<VND> giam = dt.dat ? d.tongTien().phanTram(5) : Tien<VND>(0);
        Tien<VND> sauGiam = d.tongTien() - giam;
        Tien<VND> phaiTra = sauGiam + sauGiam.phanTram(THUE_PHAN_TRAM[static_cast<std::size_t>(qg)]);

        if (luuThatBai) throw std::logic_error("CSDL hong");
        long pb = d.phienBan();
        kho.luu(d, pb);
        // LUU XONG roi moi phat su kien (bai 84) — khong som hon mot dong.
        for (auto& e : kho.bang.at("DH-" + std::to_string(soLanThucSuXuLy)).layVaXoaSuKien())
            daPhat.push_back(e);
        bao.bao(maKhach, "da tao don DH-" + std::to_string(soLanThucSuXuLy));

        soIdempotency[khoa] = phaiTra.giaTri();
        return phaiTra.giaTri();
    };

    std::vector<DongHang> gioHang{{"laptop", Tien<VND>(20000000L), 1},
                                  {"chuot", Tien<VND>(500000L), 2}};

    // ---- 1. DUONG THUAN LOI, DAU TOI CUOI ----
    long phaiTra = datHang("KEY-1", "KH-01", QuocGia::VN, gioHang, false);
    // 21.000.000 -> giam 5% = 1.050.000 -> con 19.950.000 -> +10% thue = 21.945.000
    check(phaiTra == 21945000L, "giam gia TRUOC, thue SAU (bai 87 + 88)");
    check(kho.bang.size() == 1, "don da duoc luu qua CONG (bai 98)");
    check(bao.daBao.size() == 1, "khach duoc bao o tang UNG DUNG (bai 86)");
    check(daPhat.size() == 1 && std::holds_alternative<DonHangDaTao>(daPhat[0]),
          "su kien duoc PHAT SAU KHI LUU (bai 84)");

    // ---- 2. BAT BIEN CUA AGGREGATE (bai 83) ----
    DonHang* don = kho.timTheoMa("DH-1");
    bool vuot = false;
    try { don->themDong("may chu", Tien<VND>(40000000L), 1); }
    catch (const std::logic_error&) { vuot = true; }
    check(vuot && don->tongTien().giaTri() == 21000000L, "vuot han muc bi chan, du lieu nguyen ven");
    // `cacDong()` tra `const&` -> `don->cacDong().push_back(...)` KHONG BIEN DICH DUOC.
    check(don->cacDong().size() == 2, "cua aggregate dong: khong sua duoc ruot tu ngoai");

    // ---- 3. MAY TRANG THAI (bai 89) ----
    bool khongGiaoDuoc = false;
    try { don->giao(1L); } catch (const std::logic_error&) { khongGiaoDuoc = true; }
    check(khongGiaoDuoc, "chua thanh toan thi chua giao — mac dinh la TU CHOI");
    don->chuyen(SuKienChuyen::THANH_TOAN);
    don->giao(1700000100L);
    check(don->trangThai() == TrangThai::DA_GIAO, "va duong hop le thi di duoc");
    check(don->layVaXoaSuKien().size() == 1, "chuyen trang thai GHI su kien, khong phat");

    // ---- 4. TIEN TE — KHONG BIEN DICH DUOC, khong phai ngoai le luc chay (bai 90) ----
    // Dong duoi la LOI BIEN DICH:  Tien<VND>(1) + Tien<USD>(1);
    check(!CongDuoc<Tien<VND>, Tien<USD>>::value, "cong khac te bi chan o muc TRINH BIEN DICH");
    check(0.1 + 0.2 != 0.3, "va do la ly do khong dung double cho tien");

    // ---- 5. SPECIFICATION GIAI THICH DUOC (bai 87) ----
    long nho = datHang("KEY-2", "KH-02", QuocGia::VN, {{"but", Tien<VND>(10000L), 1}}, false);
    check(nho == 11000L, "don nho: khong giam, chi +10% thue");
    DonHang tam(MaDonHang{"TMP"}, MaKhachHang{"X"}, 0);
    tam.themDong("but", Tien<VND>(10000L), 1);
    auto dt = duocGiamGia(tam);
    check(!dt.dat && dt.lyDoTruot.size() == 2, "va noi ro TRUOT O HAI menh de nao");
    check(dt.lyDoTruot[1] == "tu 2 dong hang tro len", "dan thang vao thong bao");

    // ---- 6. POLICY THEO QUOC GIA (bai 88) ----
    long myQuoc = datHang("KEY-3", "KH-03", QuocGia::US, gioHang, false);
    check(myQuoc == 19950000L, "My: giam 5%, khong thue");
    check(THUE_PHAN_TRAM[static_cast<std::size_t>(QuocGia::VN)] == 10, "bang tra, khong if-else");

    // ---- 7. IDEMPOTENCY (bai 91) ----
    int truoc = soLanThucSuXuLy;
    long lai = datHang("KEY-1", "KH-01", QuocGia::VN, gioHang, false);
    check(soLanThucSuXuLy == truoc, "gui lai cung khoa: KHONG xu ly lan nua");
    check(lai == phaiTra, "va tra ve DUNG ket qua cu, khong phai loi 'da xu ly'");
    check(kho.bang.size() == 3, "van dung 3 don, khong sinh don thu tu");

    // ---- 8. KHOA LAC QUAN (bai 92) ----
    DonHang* d2 = kho.timTheoMa("DH-2");
    check(kho.luu(*d2, d2->phienBan()) == 1, "ghi voi phien ban dung: 1 dong");
    check(kho.luu(*d2, d2->phienBan() - 1) == 0, "ghi voi phien ban CU: 0 DONG — dung do");
    check(kho.soLanDungDo == 1, "va dung do duoc DEM, khong im lang");

    // ---- 9. SU KIEN PHAT SAU COMMIT (bai 84) ----
    std::size_t emailTruoc = bao.daBao.size(), suKienTruoc = daPhat.size();
    bool hong = false;
    try { datHang("KEY-9", "KH-09", QuocGia::VN, gioHang, true); }
    catch (const std::logic_error&) { hong = true; }
    check(hong, "luu hong");
    check(bao.daBao.size() == emailTruoc, "-> 0 email duoc gui");
    check(daPhat.size() == suKienTruoc, "-> 0 su kien roi khoi tien trinh");

    // ---- 10. MO HINH DOC (bai 95) ----
    int tvTruoc = kho.soLuotTruyVan;
    auto danhSach = kho.danhSach();
    check(kho.soLuotTruyVan - tvTruoc == 1, "man hinh danh sach: DUNG MOT luot truy van");
    check(danhSach.size() == 3, "va mo hinh doc PHANG, nam lien nhau trong bo nho");

    // ---- 11. KIEM KIEN TRUC — o C++ phan lon da xong LUC BIEN DICH ----
    // Bon `static_assert` o dau file da chay truoc khi chuong trinh nay ton tai:
    //   - cong khac tien te khong bien dich duoc            (bai 90)
    //   - may trang thai co dung 4 canh                     (bai 89)
    //   - du chinh sach thue cho moi quoc gia               (bai 88)
    // Phan con lai — "mien khong duoc `#include` ha tang" — kiem bang mot lenh `grep`
    // tren thu muc `mien/` (bai 98). Do la phu thuoc VAT LY, nen vi pham con lam moi file
    // cua mien phai dich lai (bai 93).
    check(demCanh() == 4, "va van doc lai duoc luc chay nhu mot hang so");

    // ---- 12. VI SAO 20 BAI NAY LA MOT THIET KE, KHONG PHAI 20 MAU ----
    //
    //   Co BAT BIEN "tong <= han muc"        -> phai co RANH GIOI aggregate    (83)
    //   Ranh gioi -> tham chieu BANG ID      -> hai aggregate khong noi truc tiep
    //   Khong noi truc tiep                  -> phai co SU KIEN MIEN            (84)
    //   Su kien giao it nhat mot lan         -> nguoi nghe phai IDEMPOTENT      (91)
    //   Mot transaction mot aggregate        -> quy trinh nhieu buoc can SAGA   (97)
    //   Nhieu nguoi cung sua                 -> can KHOA LAC QUAN               (92)
    //   Aggregate tai tron ven               -> man hinh danh sach can CQRS     (95)
    //   Luat doi theo ngu canh               -> POLICY, khong phai if-else      (88)
    //   Luat can giai thich + dich sang SQL  -> SPECIFICATION                   (87)
    //   Mien phai test duoc khong CSDL       -> CONG & BO NOI                   (98)
    //   Test khong CSDL                      -> test mien chi la ham + check    (99)
    //
    // Rut mot mat xich ra thi mat ke ben mat ly do ton tai.
    check(soLanThucSuXuLy == 4 && kho.bang.size() == 3,
          "4 lan vao xu ly, 3 don duoc luu — lan thu tu hong va KHONG de lai gi");

    std::cout << "OK\n";
    return 0;
}
