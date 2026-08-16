/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — domain service là chỗ đặt hành vi không thuộc
 * về entity nào; ba loại "service" phải phân biệt; và hai lỗi đối xứng: mô hình THIẾU
 * MÁU và nhét hành vi liên-aggregate vào một entity.
 * Tại sao cần học: Java bắt mọi thứ phải là một lớp, nên "domain service" ở đó luôn có
 * hình dạng một object — và chính điều đó dụ người ta tiêm repository vào nó. C++ không
 * bắt: domain service ở đây là một HÀM TỰ DO trong namespace của miền, và nó phơi bày
 * bản chất thật của khái niệm này — một phép tính thuần, không trạng thái, không I/O.
 * Đổi lại, C++ dụ người ta vào cái bẫy ngược: `struct` với dữ liệu public cộng vài hàm
 * tự do là cách viết mô hình thiếu máu tự nhiên nhất trong mọi ngôn ngữ.
 */
#include <iostream>
#include <map>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// SAI 1 — MO HINH THIEU MAU: struct du lieu public + ham tu do
// =====================================================================
struct TaiKhoanThieuMau {
    std::string ma;
    long soDu;              // <- cua mo toang, va o C++ khong can ca setter
};

namespace dichVuThieuMau {
// Luat "khong duoc am" nam o DAY, nen no chi co hieu luc voi ai di qua day.
void rut(TaiKhoanThieuMau& tk, long tien) {
    if (tk.soDu < tien) throw std::logic_error("khong du so du");
    tk.soDu -= tien;
}
}  // namespace dichVuThieuMau

// =====================================================================
// DUNG — entity giu luat cua chinh no
// =====================================================================
class TaiKhoan {
public:
    TaiKhoan(std::string ma, long soDu) : ma_(std::move(ma)), soDu_(soDu) {
        if (soDu_ < 0) throw std::invalid_argument("so du ban dau khong am");
    }
    // Luat nam TRONG entity -> khong co duong vong nao.
    void rut(long tien) {
        if (tien <= 0) throw std::invalid_argument("so tien rut phai duong");
        if (soDu_ < tien) throw std::logic_error("khong du so du");
        soDu_ -= tien;
    }
    void nap(long tien) {
        if (tien <= 0) throw std::invalid_argument("so tien nap phai duong");
        soDu_ += tien;
    }
    const std::string& ma() const { return ma_; }
    long soDu() const { return soDu_; }
private:
    std::string ma_;
    long soDu_;             // private: cach DUY NHAT doi so du la rut/nap
};

// Bang phi la VALUE OBJECT truyen vao, khong phai repository duoc tiem.
struct BieuPhi {
    long nguong, phiThap, phiCao;
    long tinhPhi(long soTien) const { return soTien <= nguong ? phiThap : phiCao; }
};

struct BienLaiChuyenTien {
    std::string tuTaiKhoan, denTaiKhoan;
    long soTien, phi;
};

// =====================================================================
// DOMAIN SERVICE — o C++ no la mot HAM TU DO, khong phai mot lop
// =====================================================================
namespace mien {
// Chuyen tien dinh toi HAI tai khoan ngang nhau. Dat no vao `TaiKhoan::chuyenToi(khac)`
// la bat mot aggregate sua mot aggregate khac — dung thu bai 83 cam. Dat no vao tang ung
// dung thi luat tinh phi (mot luat NGHIEP VU) roi khoi mien.
//
// Ham nay khong co bien tinh, khong bat con tro toan cuc, khong doc dong ho, khong cham
// CSDL. Trang thai bang 0 khong phai vi ky luat — vi no la mot HAM, no khong co cho nao
// de ma cat trang thai.
BienLaiChuyenTien chuyenTien(TaiKhoan& tu, TaiKhoan& den, long soTien, const BieuPhi& bieuPhi) {
    if (tu.ma() == den.ma()) throw std::invalid_argument("khong chuyen cho chinh minh");
    long phi = bieuPhi.tinhPhi(soTien);
    tu.rut(soTien + phi);       // moi entity van tu giu luat cua no
    den.nap(soTien);
    return BienLaiChuyenTien{tu.ma(), den.ma(), soTien, phi};
}
}  // namespace mien

// =====================================================================
// Ba loai "service" — thu hay bi gop lam mot
// =====================================================================
class KhoTaiKhoan {                                   // cong (port) cua MIEN
public:
    virtual ~KhoTaiKhoan() = default;
    virtual TaiKhoan* tim(const std::string& ma) = 0;
    virtual void luu(TaiKhoan& tk) = 0;
};

class GuiThongBao {                                   // HA TANG
public:
    virtual ~GuiThongBao() = default;
    virtual void gui(const std::string& ma, const std::string& noiDung) = 0;
};

// UNG DUNG: dieu phoi. Tai, goi mien, luu, phat thong bao. Khong tinh luat nao.
class UngDungChuyenTien {
public:
    UngDungChuyenTien(KhoTaiKhoan& kho, GuiThongBao& tb) : kho_(kho), tb_(tb) {}
    BienLaiChuyenTien thucHien(const std::string& maTu, const std::string& maDen, long soTien) {
        TaiKhoan* tu = kho_.tim(maTu);
        TaiKhoan* den = kho_.tim(maDen);
        if (!tu || !den) throw std::invalid_argument("khong tim thay tai khoan");
        ++soLanGoiMien;
        BienLaiChuyenTien bl = mien::chuyenTien(*tu, *den, soTien, BieuPhi{1000000, 1000, 5000});
        kho_.luu(*tu);
        kho_.luu(*den);
        tb_.gui(maTu, "da chuyen " + std::to_string(soTien));
        return bl;
    }
    int soLanGoiMien = 0;
private:
    KhoTaiKhoan& kho_;
    GuiThongBao& tb_;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. MO HINH THIEU MAU: luat o ngoai thi luat bi lach ----
    TaiKhoanThieuMau tm{"TK-01", 100000};
    bool chan = false;
    try { dichVuThieuMau::rut(tm, 200000); } catch (const std::logic_error&) { chan = true; }
    check(chan, "di qua dich vu thi luat co hieu luc...");

    tm.soDu = -500000;      // ...va day la duong vong, mo san cho tat ca moi nguoi
    check(tm.soDu == -500000, "so du AM, khong ai chan, khong ngoai le");
    // O C++ cai bay nay tinh vi hon Java: khong ai phai VIET mot setter. Chi can khai
    // bao `struct` thay vi `class`, va moi field thanh public. Mot tu khoa, va toan bo
    // bat bien cua ban tro thanh loi khuyen.

    TaiKhoan tk("TK-01", 100000);
    chan = false;
    try { tk.rut(200000); } catch (const std::logic_error&) { chan = true; }
    check(chan && tk.soDu() == 100000, "luat nam TRONG entity -> khong co duong vong");
    // Va khong ton tai `setSoDu` de ma lach: cach DUY NHAT doi so du la rut/nap. Dong
    //     tk.soDu_ = -500000;
    // la LOI BIEN DICH: 'long TaiKhoan::soDu_' is private within this context.

    // ---- 2. PHEP DEM: ban da roi vao mo hinh thieu mau chua? ----
    // C++ khong co reflection nen khong dem duoc luc chay. Nhung co mot phep thu khac,
    // manh khong kem va lam duoc bang mat: DEM SO FIELD PUBLIC trong cac lop mien.
    //   TaiKhoanThieuMau: 2/2 field public  -> 100% du lieu phoi ra ngoai
    //   TaiKhoan:         0/2 field public  -> moi thay doi di qua mot HANH VI co ten
    // Trong CI, `grep -n "struct" mien/*.h` la mot bai kiem tra kien truc du dung.
    check(sizeof(TaiKhoanThieuMau) > 0 && sizeof(TaiKhoan) > 0, "hai cach mo hinh, hai the gioi");

    // ---- 3. KHI NAO THI THAT SU CAN DOMAIN SERVICE ----
    // Ba cau hoi, phai tra loi CO ca ba:
    //   (a) Hanh vi nay co phai LUAT NGHIEP VU khong? (khong phai dieu phoi, khong phai I/O)
    //   (b) No co thuoc ve dung MOT entity khong? — neu CO thi dat vao entity do, xong.
    //   (c) Ep no vao mot entity co lam entity do phai sua entity khac khong?
    // "Chuyen tien" tra loi: (a) co, (b) KHONG — hai tai khoan ngang nhau, (c) co.
    // => domain service.
    TaiKhoan a("TK-A", 5000000), b("TK-B", 0);
    BienLaiChuyenTien bl = mien::chuyenTien(a, b, 2000000, BieuPhi{1000000, 1000, 5000});
    check(bl.phi == 5000, "tren 1 trieu -> phi cao");
    check(a.soDu() == 2995000, "tru ca tien lan phi");
    check(b.soDu() == 2000000, "ben nhan khong chiu phi");
    // Neu nhet vao entity: `a.chuyenToi(b, 2000000)` thi `TaiKhoan` phai goi `b.nap(...)`
    // — mot aggregate sua mot aggregate khac trong cung loi goi. Va cau hoi "phi do ben
    // nao chiu" bong thanh trach nhiem cua lop `TaiKhoan`, du no la luat cua DICH VU
    // CHUYEN TIEN chu khong phai cua tai khoan.

    // ---- 4. DIEU CHI C++ NOI RO: DOMAIN SERVICE KHONG CO CHO DE CAT TRANG THAI ----
    // `mien::chuyenTien` la mot ham tu do. No khong co `this`, khong co field, khong co
    // constructor de ai do tiem mot repository vao. Muon no biet bieu phi thi phai
    // TRUYEN bieu phi — va do chinh la ky luat ma Java phai nhac bang tai lieu.
    //
    // He qua truc tiep: test no khong can gi ca. Khong mock, khong fake, khong CSDL.
    TaiKhoan x("TK-X", 10000), y("TK-Y", 0);
    check(mien::chuyenTien(x, y, 5000, BieuPhi{1000000, 1000, 5000}).phi == 1000,
          "test domain service: hai dong, khong ha tang nao");
    //
    // Neu mot ngay ai do can them repository vao domain service, do la tin hieu ro rang:
    // hoac no la application service doi lot, hoac du lieu no can phai duoc TRUYEN VAO.

    // ---- 5. BA LOAI SERVICE — bang phan biet ----
    //
    //                | Domain service      | Application service   | Infrastructure
    //   -------------|---------------------|-----------------------|----------------
    //   tra loi      | "luat la gi?"       | "quy trinh la gi?"    | "lam the nao?"
    //   vi du        | mien::chuyenTien    | UngDungChuyenTien     | GuiThongBao
    //   co trang thai| KHONG               | khong                 | thuong co
    //   cham I/O     | KHONG               | co (qua interface)    | CO
    //   mo transaction| KHONG              | CO                    | khong
    //   nam o tang   | mien                | ung dung              | ha tang
    //   test can gi  | khong can gi        | fake (bai 68)         | moi truong that
    //
    // Sai lam pho bien nhat: gop cot 1 va cot 2 thanh mot lop `OrderService` dai 800
    // dong, vua mo transaction vua tinh luat vua gui email.
    struct KhoBoNho : KhoTaiKhoan {
        std::map<std::string, TaiKhoan> m;
        TaiKhoan* tim(const std::string& ma) override {
            auto it = m.find(ma);
            return it == m.end() ? nullptr : &it->second;
        }
        void luu(TaiKhoan&) override { /* da sua tai cho */ }
    };
    struct ThongBaoGia : GuiThongBao {
        std::vector<std::string> daGui;
        void gui(const std::string& ma, const std::string& nd) override { daGui.push_back(ma + ":" + nd); }
    };

    KhoBoNho kho;
    kho.m.emplace("TK-A", TaiKhoan("TK-A", 5000000));
    kho.m.emplace("TK-B", TaiKhoan("TK-B", 0));
    ThongBaoGia tb;
    UngDungChuyenTien ud(kho, tb);
    ud.thucHien("TK-A", "TK-B", 500000);
    check(kho.tim("TK-A")->soDu() == 4499000, "500.000 + phi thap 1.000");
    check(tb.daGui.size() == 1, "tang ung dung lo thong bao — mien khong biet email ton tai");
    check(ud.soLanGoiMien == 1, "va no goi mien dung mot lan, khong tu tinh luat");

    // ---- 6. CAM BAY: `XxxService` thanh thung rac ----
    // Dau hieu nhan biet, theo thu tu nang dan:
    //   1. Ten la danh tu chung: `OrderService`, `UserManager`, `DataHandler` (bai 81).
    //   2. No co field la repository VA dong thoi chua luat nghiep vu.
    //   3. No co phuong thuc thu 15.
    //   4. Entity tuong ung chi con getter/setter (hoac field public).
    // Domain service tot thuong la DUNG MOT ham va ten la mot DONG TU nghiep vu:
    // `chuyenTien`, `tinhLaiSuat`, `kiemTraTrungLap`.

    // ---- 7. RANH GIOI: khi nao KHONG can domain service ----
    // Cam do nguoc lai cung co that: tao `DichVuRutTien` cho viec `tk.rut(tien)`. Cau
    // hoi (b) o phan 3 tra loi CO — hanh vi thuoc ve dung mot entity — nen no phai nam
    // trong entity, va mot service o day chi them mot lop vo nghia.
    //
    // Quy tac: domain service la NGOAI LE, khong phai mac dinh. Neu mien cua ban co
    // nhieu service hon entity, thi ban dang viet mo hinh thieu mau va goi no la DDD.
    check(true, "mac dinh: hanh vi nam trong entity/value object; service phai co ly do");

    std::cout << "OK\n";
    return 0;
}
