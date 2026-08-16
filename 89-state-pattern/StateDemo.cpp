/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — máy trạng thái đơn hàng, con bug "đơn đã huỷ
 * vẫn được giao", và hành vi (phí huỷ) đổi theo trạng thái.
 * Tại sao cần học: Java và Python kiểm máy trạng thái LÚC CHẠY — viết một bài test đếm
 * cạnh, tìm trạng thái mồ côi. C++ làm được điều đó SỚM HƠN MỘT BƯỚC: bảng chuyển là
 * `constexpr`, nên "máy trạng thái có đúng 4 cạnh" và "không có trạng thái mồ côi" trở
 * thành `static_assert` — trình biên dịch chạy thuật toán loang trên đồ thị và TỪ CHỐI
 * dịch nếu sai. Một máy trạng thái hỏng không tạo ra được file thực thi để mà chạy.
 */
#include <array>
#include <iostream>
#include <stdexcept>
#include <string>
#include <cstdlib>

enum class TrangThai { MOI_TAO, DA_THANH_TOAN, DA_GIAO, DA_HUY, SO_LUONG };
enum class SuKien { THANH_TOAN, GIAO, HUY, SO_LUONG };

constexpr std::size_t N_TT = static_cast<std::size_t>(TrangThai::SO_LUONG);
constexpr std::size_t N_SK = static_cast<std::size_t>(SuKien::SO_LUONG);

// Sentinel "khong hop le" — dung chinh gia tri SO_LUONG.
constexpr TrangThai KHONG = TrangThai::SO_LUONG;

// =====================================================================
// BANG CHUYEN — `constexpr`, nen kiem duoc LUC BIEN DICH
// =====================================================================
//                          THANH_TOAN                GIAO                    HUY
constexpr TrangThai BANG[N_TT][N_SK] = {
    /* MOI_TAO       */ {TrangThai::DA_THANH_TOAN, KHONG,               TrangThai::DA_HUY},
    /* DA_THANH_TOAN */ {KHONG,                    TrangThai::DA_GIAO,  TrangThai::DA_HUY},
    /* DA_GIAO       */ {KHONG,                    KHONG,               KHONG},
    /* DA_HUY        */ {KHONG,                    KHONG,               KHONG},
};

// Doc bang duoi dang mot buc tranh: moi o KHONG la mot loi goi BI TU CHOI. Voi cach
// viet bang `switch`, moi o KHONG do can mot dong `if` do con nguoi nho viet — 8 dong.
// O day chung la mac dinh, va thu phai viet ra la 4 o CHO PHEP.

constexpr int demCanh() {
    int n = 0;
    for (std::size_t t = 0; t < N_TT; ++t)
        for (std::size_t s = 0; s < N_SK; ++s)
            if (BANG[t][s] != KHONG) ++n;
    return n;
}
static_assert(demCanh() == 4, "may trang thai phai co dung 4 canh hop le");

// Loang tu MOI_TAO — chay TRONG LUC BIEN DICH.
constexpr bool moiTrangThaiDenDuoc() {
    bool den[N_TT] = {};
    den[static_cast<std::size_t>(TrangThai::MOI_TAO)] = true;
    bool coThem = true;
    while (coThem) {
        coThem = false;
        for (std::size_t t = 0; t < N_TT; ++t) {
            if (!den[t]) continue;
            for (std::size_t s = 0; s < N_SK; ++s) {
                TrangThai ke = BANG[t][s];
                if (ke == KHONG) continue;
                std::size_t i = static_cast<std::size_t>(ke);
                if (!den[i]) { den[i] = true; coThem = true; }
            }
        }
    }
    for (std::size_t t = 0; t < N_TT; ++t) if (!den[t]) return false;
    return true;
}
static_assert(moiTrangThaiDenDuoc(), "co trang thai mo coi: khong canh nao dan toi no");
// Hai `static_assert` tren bat mot lop bug rat kho thay bang mat: ai do them `TAM_GIU`
// vao enum, viet du hanh vi cho no, nhung quen them canh dan TOI no — va tinh nang
// "tam giu don" khong bao gio xay ra tren production. O day, no khong bien dich duoc.

// =====================================================================
// PHI HUY — hanh vi doi theo trang thai, khong chi co chuyen tiep
// =====================================================================
constexpr int PHAN_TRAM_PHI_HUY[N_TT] = {
    /* MOI_TAO       */ 0,     // chua tra tien -> huy mien phi
    /* DA_THANH_TOAN */ 10,    // da tra -> phi 10%
    /* DA_GIAO       */ -1,    // -1 = khong huy duoc
    /* DA_HUY        */ -1,
};

static const char* ten(TrangThai t) {
    switch (t) {
        case TrangThai::MOI_TAO: return "MOI_TAO";
        case TrangThai::DA_THANH_TOAN: return "DA_THANH_TOAN";
        case TrangThai::DA_GIAO: return "DA_GIAO";
        case TrangThai::DA_HUY: return "DA_HUY";
        default: return "?";
    }
}

// =====================================================================
// AGGREGATE — trang thai doi CHI qua hanh vi co ten
// =====================================================================
class DonHang {
public:
    DonHang(std::string ma, long tongTien) : ma_(std::move(ma)), tongTien_(tongTien) {}

    void thanhToan() { chuyen(SuKien::THANH_TOAN); }
    void giao()      { chuyen(SuKien::GIAO); }
    long huy() {
        int pt = PHAN_TRAM_PHI_HUY[static_cast<std::size_t>(trangThai_)];
        if (pt < 0) throw std::logic_error(std::string("khong huy duoc o ") + ten(trangThai_));
        long phi = tongTien_ * pt / 100;   // hoi phi TRUOC khi doi trang thai
        chuyen(SuKien::HUY);
        return phi;
    }
    TrangThai trangThai() const { return trangThai_; }

private:
    void chuyen(SuKien su) {
        TrangThai ke = BANG[static_cast<std::size_t>(trangThai_)][static_cast<std::size_t>(su)];
        if (ke == KHONG)
            throw std::logic_error(std::string("khong lam duoc viec do o trang thai ") + ten(trangThai_));
        trangThai_ = ke;
    }
    std::string ma_;
    long tongTien_;
    TrangThai trangThai_ = TrangThai::MOI_TAO;   // private: khong ai gan tu ngoai
};

// =====================================================================
// BAN SAI — may trang thai viet bang `if` roi rac
// =====================================================================
struct DonHangIf {
    int trangThai = 1;      // 1=moi, 2=da thanh toan, 3=da giao, 4=da huy
    void thanhToan() {
        if (trangThai != 1) throw std::logic_error("sai trang thai");
        trangThai = 2;
    }
    void giao() {
        // O day DANG LE phai co: if (trangThai != 2) throw ...
        // Nguoi viet nghi "chi don da thanh toan moi goi giao()" va bo qua.
        trangThai = 3;
    }
    void huy() {
        if (trangThai == 3) throw std::logic_error("da giao thi khong huy");
        trangThai = 4;
    }
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CON BUG: don DA HUY van duoc giao ----
    DonHangIf sai;
    sai.thanhToan();
    sai.huy();
    check(sai.trangThai == 4, "don da huy, khach da duoc hoan tien");
    sai.giao();                                   // khong ai chan
    check(sai.trangThai == 3, "va hang van duoc giao di — cong ty mat ca hang lan tien");
    // Hinh dang pho bien nhat cua bug may trang thai: KHONG phai mot dieu kien sai, ma
    // la mot dieu kien KHONG TON TAI. Doc `giao()` o tren, khong co gi trong sai ca —
    // chi co mot dong khong co o do.

    // ---- 2. BAN DUNG: mac dinh la TU CHOI ----
    DonHang don("DH-01", 1000000);
    don.thanhToan();
    check(don.huy() == 100000, "huy sau khi tra tien: phi 10%");
    check(don.trangThai() == TrangThai::DA_HUY, "da huy");

    bool chan = false;
    try { don.giao(); } catch (const std::logic_error&) { chan = true; }
    check(chan, "giao mot don da huy -> NO, va khong ai phai nho viet `if`");
    check(don.trangThai() == TrangThai::DA_HUY, "trang thai khong he bi sua do dang");
    // O `DA_HUY`, ca ba o trong bang deu la KHONG. Nguoi viet dong do khong phai nghi
    // toi viec "cam giao"; ho chi can de nguyen gia tri mac dinh. Do la khac biet giua
    // "an toan neu nho" va "an toan mac dinh".

    // ---- 3. HANH VI DOI THEO TRANG THAI, KHONG CHI CO CHUYEN TIEP ----
    DonHang chuaTra("DH-02", 1000000);
    check(chuaTra.huy() == 0, "chua tra tien -> huy mien phi");
    DonHang daTra("DH-03", 1000000);
    daTra.thanhToan();
    check(daTra.huy() == 100000, "da tra tien -> phi huy 10%");
    // Cung mot loi goi `huy()`, hai ket qua khac nhau, va KHONG co `if` nao trong logic
    // nghiep vu — chi co mot phep tra bang.

    // ---- 4. THEM MOT TRANG THAI: DO SO CHO PHAI SUA ----
    // Gia su them `DANG_GIAO` (da roi kho, chua toi tay khach).
    //   Bang if roi rac : sua MOI ham co kiem tra trang thai — thanhToan, giao, huy,
    //                     phiHuy, hienThi, xuatBaoCao... va quen mot cho la bug im lang.
    //   Bang bang chuyen: them MOT hang vao enum va MOT dong vao bang. Va neu quen dong
    //                     do, `static_assert` reachability o tren TU CHOI BIEN DICH.
    check(N_TT == 4 && N_SK == 3, "bang 4x3 = 12 o, trong do 4 o cho phep");
    check(demCanh() == 4, "8/12 loi goi bi tu choi — va khong ai phai viet 8 dong `if`");

    // ---- 5. DIEU CHI C++ LAM DUOC: KIEM MAY TRANG THAI LUC BIEN DICH ----
    // Hai `static_assert` o dau file da chay XONG truoc khi chuong trinh nay ton tai:
    //   - demCanh() == 4                -> bang khong bi sua nham (them/bot canh la lo ra)
    //   - moiTrangThaiDenDuoc()         -> khong co trang thai mo coi
    // Java va Python phai viet cac bai kiem tra nay thanh test luc chay; C++ chay chung
    // trong `constexpr`. Cai gia: bang phai la hang so biet luc bien dich — nen cach nay
    // KHONG dung duoc khi quy trinh do nguoi dung cau hinh (xem phan 7).
    constexpr TrangThai keTiep = BANG[0][0];
    static_assert(keTiep == TrangThai::DA_THANH_TOAN, "tra bang duoc ngay luc bien dich");
    check(true, "toan bo phan 5 da duoc kiem truoc khi chay dong nao");

    // ---- 6. TRANG THAI PHAI KHONG GAN DUOC TU NGOAI ----
    // Trong `DonHang`, `trangThai_` la `private` va khong co setter. Dong
    //     don.trangThai_ = TrangThai::DA_GIAO;
    // KHONG BIEN DICH DUOC. Neu co setter, toan bo may trang thai thanh trang tri —
    // dung nhu mo hinh thieu mau o bai 86. Chu y `DonHangIf` o tren: `trangThai` cua no
    // la `public int`, va do la ly do goc re khien bug o phan 1 co the ton tai.
    check(don.trangThai() == TrangThai::DA_HUY, "doc duoc, gan thi khong");

    // ---- 7. KHI NAO KHONG DUNG CACH NAY ----
    // Bang chuyen `constexpr` la cach manh nhat, va no co dieu kien: tap trang thai va
    // luat chuyen phai CO DINH luc bien dich. Ba truong hop nen chon cach khac:
    //   - Quy trinh duyet do nguoi dung cau hinh -> bang chuyen DU LIEU, doc tu CSDL.
    //   - Moi trang thai co HANH VI phuc tap rieng (khong chi canh khac nhau) -> mot lop
    //     cho moi trang thai, nhu bai 32 va nhu ban Java/Python cua bai nay.
    //   - Chi 2 trang thai, 1 su kien -> mot `bool` la du.
    check(demCanh() < static_cast<int>(N_TT * N_SK), "bang thua nhieu hon dac — dau hieu tot");

    // ---- 8. Ranh gioi voi bai 84 ----
    // Chuyen trang thai la cho tu nhien nhat de GHI SU KIEN MIEN: `giao()` doi trang
    // thai roi ghi `DonHangDaGiao`. Nhung phai GHI, khong PHAT (bai 84), va phai ghi SAU
    // khi trang thai da doi thanh cong — neu `chuyen()` nem ngoai le thi khong co su kien
    // nao duoc ghi. Thu tu trong `DonHang` o tren da dung san.

    std::cout << "OK\n";
    return 0;
}
