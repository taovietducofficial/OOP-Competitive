/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — `double` làm lệch sổ, cộng khác tiền tệ, chia
 * 100 cho 3 làm bốc hơi một xu, và giả định "tiền tệ nào cũng 2 chữ số thập phân".
 * Tại sao cần học: Java và Python chặn phép cộng khác tiền tệ bằng một câu `if` LÚC
 * CHẠY — nghĩa là bug đó vẫn tồn tại trong mã nguồn, chỉ là nó nổ muộn. C++ làm được
 * điều mạnh hơn hẳn: tiền tệ là THAM SỐ KIỂU, nên `Tien<USD> + Tien<VND>` KHÔNG BIÊN
 * DỊCH ĐƯỢC. Đây chính xác là cách `std::chrono` ngăn bạn cộng giây với mét. Và phần 7
 * chứng minh điều đó bằng `static_assert` — trình biên dịch tự xác nhận rằng dòng code
 * sai kia không dịch được, ngay trong bài self-check này.
 */
#include <iostream>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <vector>
#include <climits>
#include <cstdlib>

// =====================================================================
// TIEN TE la KIEU, khong phai gia tri. So chu so thap phan KHONG phai luc nao cung la 2.
// =====================================================================
struct VND { static constexpr int SO_CHU_SO = 0; static constexpr const char* MA = "VND"; };
struct USD { static constexpr int SO_CHU_SO = 2; static constexpr const char* MA = "USD"; };
struct JOD { static constexpr int SO_CHU_SO = 3; static constexpr const char* MA = "JOD"; };

// Tien = so DON VI NHO NHAT (xu / cent / fils), giu trong so nguyen. Khong `double`.
template <class TT>
class Tien {
public:
    explicit constexpr Tien(long long donViNho) : donViNho_(donViNho) {}

    long long donViNho() const { return donViNho_; }

    // Chi cong tru duoc voi CUNG mot `TT`. Khac te la loi bien dich, khong phai ngoai le.
    Tien operator+(const Tien& k) const { return Tien(donViNho_ + k.donViNho_); }
    Tien operator-(const Tien& k) const { return Tien(donViNho_ - k.donViNho_); }
    bool operator==(const Tien& k) const { return donViNho_ == k.donViNho_; }

    // Chia deu cho n phan, KHONG lam mat mot xu nao. Xem phan 4.
    std::vector<Tien> chiaDeu(int n) const {
        if (n <= 0) throw std::invalid_argument("so phan phai duong");
        long long moiPhan = donViNho_ / n;
        long long du = donViNho_ % n;              // phan du dem chia tiep, khong vut
        std::vector<Tien> ra;
        for (int i = 0; i < n; ++i) ra.push_back(Tien(moiPhan + (i < du ? 1 : 0)));
        return ra;
    }

    // Chia theo ti le, cung khong mat xu nao — va co CHAN TRAN SO. Xem phan 6.
    std::vector<Tien> chiaTheo(const std::vector<int>& tiLe) const {
        long long tongTiLe = 0;
        for (int t : tiLe) tongTiLe += t;
        std::vector<Tien> ra;
        long long daChia = 0;
        for (int t : tiLe) {
            if (t != 0 && donViNho_ > LLONG_MAX / t)
                throw std::overflow_error("so tien qua lon: phep nhan se tran");
            long long phan = donViNho_ * t / tongTiLe;
            ra.push_back(Tien(phan));
            daChia += phan;
        }
        long long du = donViNho_ - daChia;         // phan du do lam tron xuong
        for (long long i = 0; i < du; ++i) ra[static_cast<std::size_t>(i)] = Tien(ra[static_cast<std::size_t>(i)].donViNho_ + 1);
        return ra;
    }

    std::string chuoi() const {
        std::string s = std::to_string(donViNho_ < 0 ? -donViNho_ : donViNho_);
        while (static_cast<int>(s.size()) <= TT::SO_CHU_SO) s = "0" + s;
        if (TT::SO_CHU_SO > 0) s.insert(s.size() - TT::SO_CHU_SO, ".");
        return (donViNho_ < 0 ? "-" : "") + s + " " + TT::MA;
    }

private:
    long long donViNho_;
};

// Doc tu chuoi thap phan — KHONG di qua `double` mot buoc nao.
template <class TT>
Tien<TT> tuChuoi(const std::string& s) {
    auto cham = s.find('.');
    std::string nguyen = (cham == std::string::npos) ? s : s.substr(0, cham);
    std::string le = (cham == std::string::npos) ? "" : s.substr(cham + 1);
    while (static_cast<int>(le.size()) < TT::SO_CHU_SO) le += '0';
    for (std::size_t i = static_cast<std::size_t>(TT::SO_CHU_SO); i < le.size(); ++i)
        if (le[i] != '0')
            throw std::invalid_argument(std::string("so tien nho hon don vi nho nhat cua ") + TT::MA);
    le = le.substr(0, static_cast<std::size_t>(TT::SO_CHU_SO));
    return Tien<TT>(std::stoll(nguyen + le));
}

// =====================================================================
// Phat hien "co cong duoc khong" NGAY LUC BIEN DICH — dung o phan 7
// =====================================================================
template <class A, class B, class = void>
struct CongDuoc : std::false_type {};
template <class A, class B>
struct CongDuoc<A, B, std::void_t<decltype(std::declval<A>() + std::declval<B>())>> : std::true_type {};

static_assert(CongDuoc<Tien<USD>, Tien<USD>>::value, "cung te thi phai cong duoc");
static_assert(!CongDuoc<Tien<USD>, Tien<VND>>::value, "khac te thi KHONG duoc phep cong");
static_assert(!CongDuoc<Tien<USD>, long long>::value, "tien khong cong duoc voi so tran");

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CON BUG: `double` khong bieu dien duoc 0.1 ----
    check(0.1 + 0.2 != 0.3, "0.1 + 0.2 KHAC 0.3 trong so thuc dau phay dong");
    // Nguyen nhan: 0.1 trong he nhi phan la so vo han tuan hoan, y nhu 1/3 trong he thap
    // phan. Khong co gi "sua" duoc dieu do — no la ban chat cua kieu du lieu.

    double viDouble = 0.0;
    for (int i = 0; i < 10000; ++i) viDouble += 0.01;     // 10.000 lan cong 1 xu
    check(viDouble != 100.0, "sau 10.000 giao dich nho, so da lech");

    long long viNguyen = 0;
    for (int i = 0; i < 10000; ++i) viNguyen += 1;        // cong bang DON VI NHO NHAT
    check(viNguyen == 10000, "cong bang so nguyen: chinh xac tuyet doi, mai mai");

    // ---- 2. CON BUG: cong hai loai tien te ----
    double sai = 100.0 + 50.0;                            // 100 USD + 50 VND = ?
    check(sai == 150.0, "phep cong chay ngon lanh, va ket qua hoan toan vo nghia");
    auto usd = tuChuoi<USD>("100.00");
    auto vnd = tuChuoi<VND>("50");
    // Dong duoi day KHONG BIEN DICH DUOC — va do la toan bo diem manh cua ban C++ nay:
    //     auto x = usd + vnd;
    //     error: no match for 'operator+' (operands are 'Tien<USD>' and 'Tien<VND>')
    // Java va Python chan bang `if` luc chay: bug van nam trong ma nguon, chi la no no
    // muon. O day no khong ton tai duoc.
    check((usd + tuChuoi<USD>("0.50")).chuoi() == "100.50 USD", "cung te thi duoc");
    check(vnd.chuoi() == "50 VND", "VND khong co phan thap phan");

    // ---- 3. CON BUG: gia dinh "tien te nao cung 2 chu so thap phan" ----
    long long xuCuaVnd = 100000LL * 100;                  // "doi sang xu" theo thoi quen
    check(xuCuaVnd == 10000000LL, "100.000d thanh 10 trieu — sai 100 lan");
    check(VND::SO_CHU_SO == 0 && USD::SO_CHU_SO == 2 && JOD::SO_CHU_SO == 3,
          "ba loai tien te, ba so chu so khac nhau");
    check(tuChuoi<VND>("100000").donViNho() == 100000LL, "VND: 1 dong la don vi nho nhat");
    check(tuChuoi<USD>("100.00").donViNho() == 10000LL, "USD: 100 do = 10.000 cent");
    check(tuChuoi<JOD>("1.500").donViNho() == 1500LL, "JOD: 1,5 dinar = 1500 fils");

    bool quaNho = false;
    try { tuChuoi<VND>("100.50"); } catch (const std::invalid_argument&) { quaNho = true; }
    check(quaNho, "0,5 dong KHONG ton tai -> chan ngay tai bien, khong lam tron len");

    // ---- 4. CON BUG: chia 100 cho 3 lam boc hoi tien ----
    double moiNguoiDouble = static_cast<double>(static_cast<long long>(100.0 / 3 * 100 + 0.5)) / 100.0;
    check(moiNguoiDouble == 33.33, "moi nguoi 33,33");
    check(static_cast<long long>(moiNguoiDouble * 100 + 0.5) * 3 == 9999,
          "ba nguoi cong lai duoc 99,99 — thieu 1 xu");
    // 1 xu do di dau? Khong di dau ca — no bi lam tron mat. Nhan voi mot trieu giao dich
    // chia hoa don moi thang, va ke toan co mot khoan chenh khong giai thich duoc.

    auto tram = tuChuoi<USD>("100.00");
    auto ba = tram.chiaDeu(3);
    long long tong = 0;
    for (const auto& t : ba) tong += t.donViNho();
    check(tong == tram.donViNho(), "chia deu: tong cac phan BANG DUNG so ban dau");
    check(ba[0].chuoi() == "33.34 USD", "nguoi dau nhan them 1 cent du");
    check(ba[1].chuoi() == "33.33 USD" && ba[2].chuoi() == "33.33 USD", "hai nguoi sau nhan 33,33");
    // Thuat toan: chia lay nguyen, roi PHAT phan du cho cac phan dau, moi phan 1 don vi.
    // Khong xu nao bien mat, khong xu nao sinh ra. Ai nhan phan du la mot quyet dinh
    // NGHIEP VU — nhung no phai la mot quyet dinh, khong phai he qua cua viec lam tron.

    auto theoTiLe = tuChuoi<USD>("100.00").chiaTheo({3, 7});
    check(theoTiLe[0].chuoi() == "30.00 USD" && theoTiLe[1].chuoi() == "70.00 USD", "chia 30/70");
    auto le = tuChuoi<USD>("0.05").chiaTheo({3, 7});
    check(le[0].donViNho() + le[1].donViNho() == 5, "5 cent chia 30/70 van du 5 cent");
    check(le[0].donViNho() == 2 && le[1].donViNho() == 3, "2 + 3, phan du ve nguoi dau");

    // ---- 5. THU NGUYEN: tien x tien la vo nghia ----
    // `Tien` khong co `operator*` nhan `Tien`. Ba dong luat:
    //     tien x so   = tien     (100 USD x 0.1 = 10 USD thue)
    //     tien / tien = TI LE    (30 USD / 100 USD = 0.3)
    //     tien x tien = KHONG CO NGHIA — "do-la binh phuong" khong ton tai
    // `std::chrono` dung dung nguyen tac nay: `seconds * seconds` cung khong bien dich.
    check(!CongDuoc<Tien<USD>, long long>::value, "va tien cung khong cong duoc voi so tran");

    // ---- 6. CAI BAY RIENG CUA C++: TRAN SO NGUYEN ----
    // `long long` chua duoc ~9,2 ti ti. Voi VND (he so 1) la ~9 ti ti dong — thua cho
    // moi doanh nghiep. Nhung phep NHAN trong `chiaTheo` co the tran truoc do rat lau:
    //     donViNho_ * tiLe   voi donViNho_ = 10^18 va tiLe = 7  -> tran
    // Va tran so nguyen CO DAU trong C++ la HANH VI KHONG XAC DINH: khong ngoai le,
    // khong co gia tri nao duoc bao dam, va trinh bien dich duoc phep gia dinh no khong
    // xay ra. Nghia la bug nay co the "chay dung" tren may ban va sai tren may chu.
    //
    // Cach chan: kiem TRUOC khi nhan, bang phep chia (khong bao gio tran).
    bool tranDuocChan = false;
    try {
        Tien<VND>(4000000000000000000LL).chiaTheo({3, 7});
    } catch (const std::overflow_error&) { tranDuocChan = true; }
    check(tranDuocChan, "4 ti ti x 7 se tran -> chan truoc, khong de xay ra");
    check(tuChuoi<VND>("1000000000").chiaTheo({3, 7})[1].donViNho() == 700000000LL,
          "so tien thuc te thi khong sao");
    // Day la ly do Java (long, tran am tham nhung XAC DINH) va C++ (tran la UB) can
    // cung mot chan, nhung ly do khac nhau: Java cho ra so sai, C++ cho ra bat ky thu gi.

    // ---- 7. DIEU CHI C++ LAM DUOC: TRINH BIEN DICH TU XAC NHAN ----
    // Ba `static_assert` o dau file da chay xong truoc khi chuong trinh ton tai:
    //     CongDuoc<Tien<USD>, Tien<USD>>::value == true
    //     CongDuoc<Tien<USD>, Tien<VND>>::value == false
    //     CongDuoc<Tien<USD>, long long>::value == false
    // Dong thu hai la mot dieu Java va Python khong viet ra duoc: mot bang chung, kiem
    // tra tu dong, rang mot doan ma SAI la khong the ton tai. Neu ngay mai ai do them
    //     template <class A, class B> Tien<A> operator+(Tien<A>, Tien<B>)
    // "cho tien", `static_assert` do do ngay.
    check(CongDuoc<Tien<JOD>, Tien<JOD>>::value, "va no ap dung cho moi tien te, khong phai chep tay");

    // ---- 8. Ranh gioi: khi nao so nguyen don vi nho KHONG du ----
    // Ba truong hop phai dung kieu khac:
    //   - gia don vi nho hon don vi tien nho nhat (gia dien 1.234,56 d/kWh) -> do KHONG
    //     phai tien, do la DON GIA, mot kieu rieng;
    //   - tinh lai kep nhieu ky, can giu do chinh xac trung gian;
    //   - tien ma hoa (18 chu so thap phan) -> can so nguyen 128 bit, `long long` tran that.
    // Quy tac: so nguyen don vi nho cho SO TIEN, kieu thap phan cho DON GIA va TI LE, va
    // hai thu do la hai kieu du lieu khac nhau — dung nhu phan 5 noi.
    check(LLONG_MAX / 100 > 92000000000000000LL, "long long thua suc cho tien that");

    std::cout << "OK\n";
    return 0;
}
