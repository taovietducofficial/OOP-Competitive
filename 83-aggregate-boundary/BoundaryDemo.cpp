/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — hai lỗi đối xứng nhau: ranh giới QUÁ TO (đổi
 * số điện thoại phải tải 501 object, hai người sửa hai đơn khác nhau lại đụng nhau) và
 * ranh giới QUÁ NHỎ (bất biến "tổng đơn ≤ hạn mức" bị hai phiên xen kẽ vượt qua).
 * Tại sao cần học: ở C++, ranh giới aggregate không chỉ là quy ước thiết kế — nó là
 * QUYỀN SỞ HỮU BỘ NHỚ. Aggregate root giữ các phần tử con theo GIÁ TRỊ, nên con không
 * thể sống lâu hơn cha; còn aggregate khác thì chỉ tham chiếu bằng id, nên không có
 * con trỏ nào đi xuyên ranh giới để mà lủng lẳng. Đổi lại, C++ có một cách rò rỉ ranh
 * giới mà Java và Python không có: trả về THAM CHIẾU KHÔNG const ra bên trong — một ký
 * tự `const` thiếu là cửa mở toang, và bài này cho thấy đúng chỗ đó.
 */
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// Dinh danh la KIEU RIENG, khong phai std::string tran. Xem phan 3.
struct MaKhachHang {
    explicit MaKhachHang(std::string v) : giaTri(std::move(v)) {}
    std::string giaTri;
    bool operator==(const MaKhachHang& k) const { return giaTri == k.giaTri; }
};
struct MaDonHang {
    explicit MaDonHang(std::string v) : giaTri(std::move(v)) {}
    std::string giaTri;
};

// =====================================================================
// AGGREGATE DUNG KICH THUOC: DonHang
// Bat bien cua no: TONG TIEN CAC DONG KHONG VUOT HAN MUC.
// =====================================================================
struct DongHang {
    std::string sanPham;
    long donGia;
    int soLuong;
    long thanhTien() const { return donGia * soLuong; }
};

class DonHang {
public:
    static constexpr long HAN_MUC = 50000000L;

    DonHang(MaDonHang ma, MaKhachHang maKh)
        : ma_(std::move(ma)), maKhachHang_(std::move(maKh)) {}

    // BAT BIEN duoc kiem NGAY TAI DAY, trong cung mot loi goi ghi du lieu.
    void themDong(std::string sanPham, long donGia, int soLuong) {
        long sauKhiThem = tongTien() + donGia * soLuong;
        if (sauKhiThem > HAN_MUC)
            throw std::logic_error("don vuot han muc 50.000.000");
        cacDong_.push_back(DongHang{std::move(sanPham), donGia, soLuong});
    }

    long tongTien() const {
        long t = 0;
        for (const auto& d : cacDong_) t += d.thanhTien();
        return t;
    }
    std::size_t soDong() const { return cacDong_.size(); }
    const MaKhachHang& maKhachHang() const { return maKhachHang_; }

    // CUA DONG: tra ve tham chieu CONST. Khong ai them dong ma khong qua themDong().
    // Bo chu `const` o dau kieu tra ve la ranh gioi mo toang — xem phan 8.
    const std::vector<DongHang>& cacDong() const { return cacDong_; }

private:
    MaDonHang ma_;
    MaKhachHang maKhachHang_;         // THAM CHIEU BANG ID, khong giu object
    std::vector<DongHang> cacDong_;   // SO HUU theo GIA TRI: con khong song lau hon cha
};

// =====================================================================
// AGGREGATE DUNG KICH THUOC: KhachHang — KHONG chua don hang
// =====================================================================
class KhachHang {
public:
    KhachHang(MaKhachHang ma, std::string ten, std::string dienThoai)
        : ma_(std::move(ma)), ten_(std::move(ten)), dienThoai_(std::move(dienThoai)) {}
    void doiDienThoai(std::string moi) { dienThoai_ = std::move(moi); ++phienBan_; }
    long phienBan() const { return phienBan_; }
private:
    MaKhachHang ma_;
    std::string ten_, dienThoai_;
    long phienBan_ = 0;
};

// =====================================================================
// SAI 1 — RANH GIOI QUA TO: khach hang om luon danh sach don
// =====================================================================
class KhachHangQuaTo {
public:
    KhachHangQuaTo(MaKhachHang ma, std::string dienThoai)
        : ma_(std::move(ma)), dienThoai_(std::move(dienThoai)) {}
    void doiDienThoai(std::string moi) { dienThoai_ = std::move(moi); ++phienBan_; }
    void themDon(DonHang d) { cacDon_.push_back(std::move(d)); ++phienBan_; }
    std::size_t soDon() const { return cacDon_.size(); }
    long phienBan() const { return phienBan_; }
private:
    MaKhachHang ma_;
    std::string dienThoai_;
    long phienBan_ = 0;
    std::vector<DonHang> cacDon_;      // <- mot dong, ba hau qua
};

// Kho gia co DEM so object phai tai — de "qua to" thanh con so, khong thanh cam giac.
struct KhoDem {
    int soObjectDaTai = 0;
    KhachHangQuaTo& taiQuaTo(KhachHangQuaTo& kh) {
        soObjectDaTai += 1 + static_cast<int>(kh.soDon());   // aggregate phai tai TRON VEN
        return kh;
    }
    KhachHang& tai(KhachHang& kh) { soObjectDaTai += 1; return kh; }
};

// =====================================================================
// SAI 2 — RANH GIOI QUA NHO: dong hang thanh aggregate rieng
// =====================================================================
class KhoDongRoi {
public:
    void them(DongHang d) { dong_.push_back(std::move(d)); }
    long tong() const {
        long t = 0;
        for (const auto& d : dong_) t += d.thanhTien();
        return t;
    }
private:
    std::vector<DongHang> dong_;   // khong co cho nao kiem han muc duoc
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    MaKhachHang maKh("KH-01");

    // ---- 1. PHEP THU RANH GIOI ----
    // Cau hoi duy nhat can hoi:
    //   "Neu hai thu nay duoc sua trong HAI transaction khac nhau,
    //    co luat nghiep vu nao bi pha khong?"
    //   CO    -> cung mot aggregate.
    //   KHONG -> tach ra, tham chieu bang id.
    DonHang don(MaDonHang("DH-01"), maKh);
    don.themDong("laptop", 20000000L, 2);
    check(don.tongTien() == 40000000L, "40 trieu, con trong han muc");

    bool chan = false;
    try { don.themDong("man hinh", 8000000L, 2); }
    catch (const std::logic_error&) { chan = true; }
    check(chan, "them 16 trieu nua thi vuot 50 trieu -> bi chan NGAY");
    check(don.tongTien() == 40000000L, "va du lieu khong he bi sua do dang");

    // ---- 2. SAI: RANH GIOI QUA NHO -> bat bien khong giu duoc ----
    // Neu `DongHang` la aggregate rieng, cau kiem han muc phai nam o tang ung dung:
    //     tong = kho.tong();  if (tong + moi <= HAN_MUC) kho.them(...)
    // Hai phien chay xen ke la du de pha:
    KhoDongRoi kho;
    kho.them(DongHang{"laptop", 20000000L, 2});   // dang co 40 trieu

    long docBoiA = kho.tong();     // phien A doc:  40.000.000
    long docBoiB = kho.tong();     // phien B doc:  40.000.000  <- cung luc
    if (docBoiA + 8000000L <= DonHang::HAN_MUC) kho.them(DongHang{"chuot", 8000000L, 1});
    if (docBoiB + 8000000L <= DonHang::HAN_MUC) kho.them(DongHang{"ban phim", 8000000L, 1});

    check(kho.tong() == 56000000L, "tong thanh 56 trieu");
    check(kho.tong() > DonHang::HAN_MUC, "VUOT han muc — va ca hai phien deu 'kiem tra roi'");
    // Moi phien deu doc dung, kiem dung, ghi dung. Cai sai nam o RANH GIOI: hai thu cung
    // chiu mot bat bien ma lai duoc sua trong hai transaction roi nhau.

    DonHang donDung(MaDonHang("DH-02"), maKh);
    donDung.themDong("laptop", 20000000L, 2);
    donDung.themDong("chuot", 8000000L, 1);
    chan = false;
    try { donDung.themDong("ban phim", 8000000L, 1); }
    catch (const std::logic_error&) { chan = true; }
    check(chan, "lenh thu hai bi chan vi bat bien nam TRONG ranh gioi");
    check(donDung.tongTien() == 48000000L, "va tong dung dung cho hop le");

    // ---- 3. THAM CHIEU AGGREGATE KHAC BANG ID, KHONG BANG OBJECT ----
    check(don.maKhachHang() == maKh, "don hang biet MA khach hang...");
    // ...va KHONG co cach nao di tu don hang toi object KhachHang de sua no. Day khong
    // phai ky luat, day la kieu du lieu: `DonHang` khong co field `KhachHang`, nen dong
    //     don.khachHang().doiDienThoai(...)
    // KHONG BIEN DICH DUOC.
    //
    // Va vi `MaKhachHang` co constructor `explicit` va la kieu rieng, dong nay cung vay:
    //     DonHang(MaDonHang("DH-03"), MaDonHang("DH-01"));
    //     error: no matching function ... cannot convert 'MaDonHang' to 'MaKhachHang'
    // Dung `std::string` tran thi loi do bien dich duoc va sinh du lieu rac. Chi phi cua
    // hai struct bao boc nay bang 0 luc chay — trinh bien dich xoa sach.
    check(sizeof(MaKhachHang) == sizeof(std::string), "kieu bao boc khong ton them byte nao");

    // ---- 4. SAI: RANH GIOI QUA TO -> tai 501 object de doi mot so dien thoai ----
    KhachHangQuaTo khTo(MaKhachHang("KH-01"), "0900000000");
    for (int i = 0; i < 500; ++i) khTo.themDon(DonHang(MaDonHang("DH-" + std::to_string(i)), maKh));

    KhoDem khoDem;
    khoDem.taiQuaTo(khTo).doiDienThoai("0911111111");
    check(khoDem.soObjectDaTai == 501, "doi MOT so dien thoai: tai 501 object");
    // Aggregate phai tai tron ven thi bat bien cua no moi kiem duoc — do la luat, khong
    // phai chuyen toi uu. Nen ranh gioi to = moi thao tac deu dat.

    KhachHang khDung(maKh, "Nguyen Van A", "0900000000");
    KhoDem khoDem2;
    khoDem2.tai(khDung).doiDienThoai("0911111111");
    check(khoDem2.soObjectDaTai == 1, "ranh gioi dung: tai dung 1 object");
    check(khoDem.soObjectDaTai - khoDem2.soObjectDaTai == 500, "chenh 500 lan tai vo ich");

    // ---- 5. Hau qua thu hai cua ranh gioi qua to: DUNG DO GIA ----
    long truoc = khTo.phienBan();
    khTo.themDon(DonHang(MaDonHang("DH-A"), maKh));   // nguoi dung 1 tao don A
    khTo.themDon(DonHang(MaDonHang("DH-B"), maKh));   // nguoi dung 2 tao don B
    check(khTo.phienBan() == truoc + 2, "hai don KHAC NHAU cung lam tang phien ban KHACH HANG");
    // Voi khoa lac quan (bai 92), hai nguoi tao hai don khong lien quan se bao loi "du
    // lieu da bi nguoi khac sua". Dung do nay la GIA — do ranh gioi sai sinh ra.

    long pbTruoc = khDung.phienBan();
    DonHang donC(MaDonHang("DH-C"), maKh);
    DonHang donD(MaDonHang("DH-D"), maKh);
    check(khDung.phienBan() == pbTruoc, "tao hai don: khach hang khong doi phien ban");
    (void)donC; (void)donD;

    // ---- 6. LUAT "MOT TRANSACTION = MOT AGGREGATE" ----
    // He qua truc tiep cua phan 2 va phan 5. Neu mot use case phai sua hai aggregate
    // cung luc, do la dau hieu MOT trong hai:
    //   (a) ranh gioi ve sai   -> ve lai;
    //   (b) hai thu do that su khong can dung dong thoi -> chap nhan NHAT QUAN CUOI:
    //       aggregate thu nhat phat ra su kien, aggregate thu hai xu ly sau (bai 84),
    //       va neu buoc sau hong thi co hanh dong bu tru (bai 97).
    int soRootChamToi = 1;   // "them dong vao don" -> chi DonHang
    check(soRootChamToi == 1, "use case lanh manh cham dung mot aggregate root");

    // ---- 7. Bat bien nao KHONG duoc keo vao ranh gioi ----
    // Cam do lon nhat: "tong no cua khach hang khong qua 200 trieu" — nghe nhu mot bat
    // bien, va no keo TOAN BO don hang vao trong KhachHang (phan 4).
    //
    // Cau hoi phai hoi tiep: neu luat do bi vuot trong 5 giay roi duoc sua, cong ty mat
    // gi? Voi han muc no, thuong la "khong mat gi, goi dien doi la xong". Voi tong tien
    // mot don, la "xuat hoa don sai, phai huy".
    //
    //   Vuot trong choc lat ma KHONG chap nhan duoc -> bat bien that -> chung aggregate
    //   Vuot trong choc lat ma chap nhan duoc       -> luat nghiep vu -> kiem sau, tach ra
    check(DonHang::HAN_MUC == 50000000L, "han muc MOT DON: khong duoc vuot du mot giay");

    // ---- 8. CACH RO RI RANH GIOI RIENG CUA C++: thieu mot chu `const` ----
    // `cacDong()` tra ve `const std::vector<DongHang>&`. Voi chu `const` do, dong duoi
    // la LOI BIEN DICH:
    //     don.cacDong().push_back(DongHang{"len", 1, 1});
    //     error: passing 'const std::vector<DongHang>' as 'this' argument discards qualifiers
    //
    // Bo `const` di — chi mot tu — va ranh gioi mo toang: ai cung them dong duoc ma
    // khong qua `themDong()`, nghia la bat bien han muc khong con nghia ly gi. Day la
    // cach pha aggregate de nhat trong C++, va no khong de lai dau vet nao trong code
    // goi: `don.cacDong().push_back(...)` trong nhu mot dong binh thuong.
    //
    // Ba cach tra du lieu con ra ngoai, xep theo do an toan:
    //   const std::vector<DongHang>&   -> an toan, khong copy   <- DUNG CAI NAY
    //   std::vector<DongHang>          -> an toan, copy O(n)
    //   std::vector<DongHang>&         -> CUA MO TOANG
    check(don.cacDong().size() == 1 && don.soDong() == 1, "cua van dong: ban chi doc");

    // Bon quy tac rut gon:
    //   1. Ranh gioi nam o noi mot bat bien phai dung NGAY LAP TUC.
    //   2. Tham chieu aggregate khac BANG ID, khong giu object.
    //   3. Mot transaction sua dung MOT aggregate.
    //   4. Nghi ngo thi lam NHO.

    std::cout << "OK\n";
    return 0;
}
