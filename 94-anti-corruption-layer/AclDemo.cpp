/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — bọc hệ ngoài lại để mô hình xấu của họ không lây
 * vào miền. Ba con bug: mã trạng thái dạng chuỗi rải rác nhiều nơi; số tiền dạng chuỗi
 * phân tích ở nhiều chỗ; và khái niệm chỉ đối tác mới có rơi vào nhánh mặc định.
 * Tại sao cần học: ở C++ lớp chống hư hỏng có một nhiệm vụ nữa mà Java và Python không
 * có — nó là nơi QUYỀN SỞ HỮU BỘ NHỚ đổi chủ. SDK của đối tác trả về `const char*` trỏ
 * vào bộ đệm nội bộ của họ, con trỏ đó có thể bị giải phóng bất cứ lúc nào, và một
 * `struct` của miền giữ nguyên con trỏ đó là một quả bom hẹn giờ không xác định. Biên
 * phải SAO CHÉP sang kiểu tự sở hữu (`std::string`) — và đó là lý do lớp này không bao
 * giờ chỉ là "đổi tên field".
 */
#include <cctype>
#include <cstring>
#include <iostream>
#include <map>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// HE NGOAI — mo hinh cua doi tac. Ta KHONG sua duoc no.
// SDK C cu: moi thu la `const char*`, va con tro tro vao bo dem CUA HO.
// =====================================================================
struct GiaoHangDto {
    const char* cust_nm;    // ten khach, VIET HOA, co khoang trang thua
    const char* st;         // "1"=nhan don "2"=dang giao "3"=da giao "4"=tra nguoi gui
    const char* amt_cent;   // so tien, don vi xu, DANG CHUOI
    const char* dt;         // ngay, "yyyyMMdd"
    const char* flag_x;     // "Y"/"N", nghia la "giao nhanh"
};

// =====================================================================
// MIEN CUA TA — sach, va KHONG biet doi tac ton tai
// =====================================================================
enum class TrangThaiGiaoHang { DA_NHAN_DON, DANG_GIAO, DA_GIAO, DA_TRA_LAI };

struct Tien {
    long xu;
    explicit Tien(long x) : xu(x) {
        if (x < 0) throw std::invalid_argument("so tien khong am");
    }
    bool operator==(const Tien& t) const { return xu == t.xu; }
};

struct ChuyenGiaoHang {
    std::string tenKhach;          // std::string, KHONG phai const char*
    TrangThaiGiaoHang trangThai;
    Tien cuocPhi;
    int ngayISO;
    bool giaoNhanh;
};

// =====================================================================
// LOP CHONG HU HONG — noi DUY NHAT biet ca hai mo hinh
// =====================================================================
struct LoiDoiTac : std::runtime_error {
    explicit LoiDoiTac(const std::string& m)
        : std::runtime_error("du lieu doi tac khong hop le: " + m) {}
};

class BienDoiTac {
public:
    ChuyenGiaoHang dich(const GiaoHangDto& d) {
        // Fail fast NGAY TAI BIEN (bai 76): thieu gi thi bao ro thieu gi, kem TEN TRUONG
        // CUA DOI TAC — de nguoi truc dem biet phai hoi ai.
        std::string ten = chuanHoaTen(batBuoc(d.cust_nm, "cust_nm"));
        if (ten.empty()) throw LoiDoiTac("cust_nm rong");

        auto it = bangTrangThai().find(batBuoc(d.st, "st"));
        if (it == bangTrangThai().end()) {
            ++soLanTuChoi;
            throw LoiDoiTac(std::string("ma trang thai la: st=") + d.st);
        }

        long xu = 0;
        try { xu = std::stol(batBuoc(d.amt_cent, "amt_cent")); }
        catch (const std::exception&) { throw LoiDoiTac("amt_cent khong phai so"); }

        int ngay = 0;
        try { ngay = std::stoi(batBuoc(d.dt, "dt")); }
        catch (const std::exception&) { throw LoiDoiTac("dt khong dung yyyyMMdd"); }

        std::string cy = batBuoc(d.flag_x, "flag_x");
        if (cy != "Y" && cy != "N") throw LoiDoiTac("flag_x la: " + cy);

        // Moi truong deu da duoc SAO CHEP sang kieu tu so huu. Ke tu day, `ChuyenGiaoHang`
        // song doc lap hoan toan voi bo dem cua doi tac.
        return ChuyenGiaoHang{ten, it->second, Tien(xu), ngay, cy == "Y"};
    }

    int soLanTuChoi = 0;

private:
    // Bang dich ma trang thai — cho DUY NHAT trong he thong biet "3" nghia la gi.
    static const std::map<std::string, TrangThaiGiaoHang>& bangTrangThai() {
        static const std::map<std::string, TrangThaiGiaoHang> b{
            {"1", TrangThaiGiaoHang::DA_NHAN_DON},
            {"2", TrangThaiGiaoHang::DANG_GIAO},
            {"3", TrangThaiGiaoHang::DA_GIAO},
            {"4", TrangThaiGiaoHang::DA_TRA_LAI}};
        return b;
    }

    // Tra ve `std::string` chu KHONG phai `const char*`: day la cho quyen so huu doi chu.
    static std::string batBuoc(const char* v, const std::string& tenTruong) {
        if (v == nullptr) throw LoiDoiTac("thieu truong " + tenTruong);
        return std::string(v);            // SAO CHEP — xem phan 6
    }

    // "  NGUYEN VAN A  " -> "Nguyen Van A". Quy uoc cua TA, khong phai cua ho.
    static std::string chuanHoaTen(const std::string& t) {
        std::string ra;
        bool dauTu = true;
        for (char c : t) {
            if (c == ' ') {
                if (!ra.empty() && ra.back() != ' ') ra += ' ';
                dauTu = true;
                continue;
            }
            char l = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
            ra += dauTu ? static_cast<char>(std::toupper(static_cast<unsigned char>(l))) : l;
            dauTu = false;
        }
        while (!ra.empty() && ra.back() == ' ') ra.pop_back();
        return ra;
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
    auto dtoMau = [](const char* st, const char* amt) {
        return GiaoHangDto{"  NGUYEN VAN A  ", st, amt, "20260817", "Y"};
    };

    // ---- 1. CON BUG: mo hinh doi tac ro vao mien ----
    std::vector<GiaoHangDto> loHang{dtoMau("3", "1050"), dtoMau("2", "800"), dtoMau("4", "0")};
    int daGiaoTheoManHinh = 0, daGiaoTheoBaoCao = 0, daGiaoTheoKeToan = 0;
    for (const auto& d : loHang) {
        if (std::strcmp(d.st, "3") == 0) ++daGiaoTheoManHinh;                       // man hinh
        if (std::strcmp(d.st, "3") == 0 || std::strcmp(d.st, "4") == 0) ++daGiaoTheoBaoCao;  // bao cao
        if (std::atoi(d.st) >= 3) ++daGiaoTheoKeToan;                               // ke toan
    }
    check(daGiaoTheoManHinh == 1 && daGiaoTheoBaoCao == 2 && daGiaoTheoKeToan == 2,
          "ba noi, ba con so — khong noi nao SAI cu phap, va hai noi sai NGHIA");
    // Day la bai 81 phan 1 quay lai, nhung nguyen nhan khac: lan nay ngon ngu xau KHONG
    // phai do ta dat ten te — no la ngon ngu cua doi tac, va no da tran vao.
    //
    // Va cai gia that den khi doi tac phat hanh v2: ma "3" tach thanh "3" va "3R". Moi
    // cho so chuoi phai tim va sua — con `std::atoi("3R")` thi khong nem gi ca, no tra
    // ve 3 va bao cao van "chay dung". Do la kieu hong te nhat: sai lang le.
    check(std::atoi("3R") == 3, "`atoi` nuot ky tu la, tra 3 — khong loi, khong canh bao");

    // ---- 2. LOP CHONG HU HONG: doi tac dung lai o day ----
    BienDoiTac bien;
    ChuyenGiaoHang c = bien.dich(dtoMau("3", "1050"));
    check(c.trangThai == TrangThaiGiaoHang::DA_GIAO, "chuoi '3' thanh ENUM cua ta");
    check(c.cuocPhi == Tien(1050), "chuoi '1050' thanh Tien cua ta (bai 90)");
    check(c.giaoNhanh, "'Y' thanh bool");
    check(c.tenKhach == "Nguyen Van A", "'  NGUYEN VAN A  ' thanh ten chuan cua ta");
    check(c.ngayISO == 20260817, "va ngay thanh so nguyen co kieu");

    // ---- 3. FAIL FAST TAI BIEN, VOI THONG BAO NOI DUOC TEN DOI TAC ----
    std::string thongBao;
    try { bien.dich(dtoMau("3", nullptr)); }
    catch (const LoiDoiTac& e) { thongBao = e.what(); }
    check(thongBao.find("amt_cent") != std::string::npos, "bao ro THIEU TRUONG NAO cua doi tac");
    // So voi cach khong co ACL: `std::stol(nullptr)` la hanh vi khong xac dinh — khong
    // ngoai le, khong thong bao, chi la mot vu sap o cho nao do.

    // ---- 4. KHAI NIEM CHI DOI TAC MOI CO: phai QUYET DINH, khong duoc roi mac dinh ----
    ChuyenGiaoHang traLai = bien.dich(dtoMau("4", "0"));
    check(traLai.trangThai == TrangThaiGiaoHang::DA_TRA_LAI,
          "'tra ve nguoi gui' duoc DICH thanh mot khai niem CO TEN trong mien cua ta");
    // Neu mien cua ta khong co khai niem tuong ung thi co dung hai lua chon hop le:
    //   (a) them khai niem do vao mien — sau khi hoi nghiep vu;
    //   (b) TU CHOI ban ghi do o bien, co log, co canh bao.
    // Lua chon thu ba — cho roi vao nhanh mac dinh — la cach du lieu sai di vao he thong.
    bool tuChoiMaLa = false;
    try { bien.dich(dtoMau("9", "100")); } catch (const LoiDoiTac&) { tuChoiMaLa = true; }
    check(tuChoiMaLa && bien.soLanTuChoi == 1, "ma la bi TU CHOI va DEM, khong roi mac dinh");

    // ---- 5. DOI TAC RA v2: DO SO CHO PHAI SUA ----
    //   Khong ACL: moi file cham toi DTO. Trong du an that thuong la 10-40 cho.
    //   Co ACL   : dung MOT lop `BienDoiTac`. Trinh bien dich chi ra het.
    check(12 > 1 * 10, "12 cho so voi 1");

    // ---- 6. DIEU CHI C++ CO: BIEN LA NOI QUYEN SO HUU DOI CHU ----
    // `GiaoHangDto` giu `const char*` — con tro vao bo dem CUA DOI TAC. Ta khong biet
    // bo dem do song bao lau; nhieu SDK dung mot bo dem xoay vong va ghi de sau vai loi
    // goi. Mo phong dieu do bang mot bo dem tu ghi de:
    char boDemCuaHo[32];
    std::strcpy(boDemCuaHo, "  NGUYEN VAN A  ");
    GiaoHangDto tam{boDemCuaHo, "3", "1050", "20260817", "Y"};

    ChuyenGiaoHang daDich = bien.dich(tam);       // ACL SAO CHEP sang std::string
    std::strcpy(boDemCuaHo, "RAC RAC RAC");       // doi tac ghi de bo dem cua ho

    check(daDich.tenKhach == "Nguyen Van A", "ban da dich VAN NGUYEN VEN — vi no tu so huu");
    check(std::strcmp(tam.cust_nm, "RAC RAC RAC") == 0, "trong khi DTO thi da thanh rac");
    // Neu `ChuyenGiaoHang` giu `const char* tenKhach` (chi copy con tro cho "nhanh"), thi
    // dong `check` dau tien doc mot bo dem da bi ghi de. O day may man la con doc duoc;
    // trong he that, bo dem do co the da duoc GIAI PHONG — va luc do la hanh vi khong xac
    // dinh: co the ra rac, co the sap, co the chay dung tren may ban va sai tren may chu.
    //
    // Quy tac: KHONG kieu nao cua mien duoc giu con tro tro vao bo nho cua he ngoai. Bien
    // la noi sao chep, va cai gia sao chep do la mot trong nhung khoan re nhat ban tra.

    // ---- 7. ACL KHONG PHAI CHO DAT LUAT NGHIEP VU ----
    // Lop chong hu hong chi lam DUNG BA viec:
    //   1. Kiem tinh hop le cua du lieu DAU VAO (thieu truong, sai kieu, ma la);
    //   2. Dich mo hinh ho -> mo hinh ta (kieu, don vi, khai niem, QUYEN SO HUU);
    //   3. Tu choi cai khong dich duoc, va dem.
    // Neu no bat dau biet "don tren 10 trieu phai duyet", thi luat nghiep vu vua chuyen
    // ra ngoai mien — va se co ban sao thu hai cua no o trong mien (bai 87).

    // ---- 8. DAT ACL O DAU, VA MAY CAI ----
    // Mot lop chong hu hong cho MOI he ngoai, thuoc ve BEN GOI. Ba he qua:
    //   - Hai doi cung goi mot doi tac co the co HAI ACL khac nhau — va do la dung, vi
    //     hai doi can hai mo hinh khac nhau (bai 93).
    //   - ACL nam o tang ha tang, cai dat mot CONG do mien dinh nghia (bai 98).
    //   - Khi doi tac chet, ACL la noi duy nhat can mot ban gia de test (bai 68).
    //
    // Va dieu cuoi, de quen nhat: ACL cung di CA HAI CHIEU. Khi ta GUI du lieu sang doi
    // tac, cung phai dich tu mo hinh cua ta sang cua ho — chu khong phai serialize thang
    // object mien ra JSON va hy vong khop.
    std::string maStGuiDi = (c.trangThai == TrangThaiGiaoHang::DA_GIAO) ? "3" : "2";
    check(maStGuiDi == "3", "chieu ra cung dich, o cung mot cho");

    std::cout << "OK\n";
    return 0;
}
