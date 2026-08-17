/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — trạng thái được TÍNH LẠI bằng cách phát lại chuỗi
 * sự kiện. Ba con bug: chỉ lưu trạng thái thì mất sạch lịch sử; hàm phát lại có kiểm tra
 * nên dữ liệu cũ KHÔNG TẢI ĐƯỢC; và phát lại nghìn sự kiện cho mỗi lần đọc.
 * Tại sao cần học: luật quan trọng nhất của event sourcing — "hàm phát lại không được
 * kiểm tra, không được ném ngoại lệ" — ở Java và Python chỉ là một quy ước ghi trong tài
 * liệu. C++ biến nó thành một điều KIỂM CHỨNG ĐƯỢC LÚC BIÊN DỊCH: đánh dấu hàm áp dụng
 * là `noexcept`, rồi `static_assert(noexcept(...))`. Từ đó, ai thêm một câu `throw` vào
 * đó sẽ gặp `std::terminate` chứ không phải một ngoại lệ bắt được — ngôn ngữ nói thẳng
 * rằng hàm này không có quyền thất bại.
 */
#include <iostream>
#include <stdexcept>
#include <string>
#include <variant>
#include <vector>
#include <cstdlib>

// =====================================================================
// SU KIEN — bat bien, thi qua khu, mang du lieu LUC XAY RA (bai 84)
// =====================================================================
struct DaMoTaiKhoan { std::string maTk; long soDuBanDau; };
struct DaNap        { long soTien; std::string nguon; };
struct DaRut        { long soTien; std::string lyDo; };
// Phi da duoc TINH SAN luc phat sinh — xem phan 5 de biet vi sao day la bat buoc.
struct DaTinhPhi    { long soTienPhi; int tiLePhanNghin; };

using SuKien = std::variant<DaMoTaiKhoan, DaNap, DaRut, DaTinhPhi>;

// =====================================================================
// AGGREGATE — khong luu trang thai, chi luu SU KIEN
// =====================================================================
class TaiKhoan {
public:
    // Dung lai tu lich su. Day la cach DUY NHAT tai mot aggregate trong ES.
    static TaiKhoan phatLai(const std::vector<SuKien>& lichSu) {
        TaiKhoan tk;
        for (const auto& e : lichSu) tk.apDung(e);
        return tk;
    }

    // AP DUNG — chi doi trang thai. `noexcept` la loi cam ket voi trinh bien dich rang
    // ham nay khong bao gio that bai; xem phan 4.
    void apDung(const SuKien& e) noexcept {
        std::visit([this](const auto& x) { capNhat(x); }, e);
        ++soSuKienDaApDung_;
    }

    static TaiKhoan mo(std::string ma, long banDau) {
        if (banDau < 0) throw std::invalid_argument("so du ban dau khong am");
        TaiKhoan tk;
        tk.ghiNhan(DaMoTaiKhoan{std::move(ma), banDau});
        return tk;
    }
    void nap(long t, std::string nguon) {
        if (t <= 0) throw std::invalid_argument("so tien nap phai duong");
        ghiNhan(DaNap{t, std::move(nguon)});
    }
    void rut(long t, std::string lyDo, int tiLePhiPhanNghin) {
        long phi = t * tiLePhiPhanNghin / 1000;
        if (soDu_ < t + phi) throw std::logic_error("khong du so du");
        ghiNhan(DaRut{t, std::move(lyDo)});
        ghiNhan(DaTinhPhi{phi, tiLePhiPhanNghin});   // phi CHOT tai thoi diem nay
    }

    long soDu() const { return soDu_; }
    const std::string& ma() const { return ma_; }
    int soSuKienDaApDung() const { return soSuKienDaApDung_; }
    const std::vector<SuKien>& suKienMoi() const { return suKienMoi_; }

private:
    // Bon qua tai — quen mot cai la LOI BIEN DICH (khong co ban bat-tat-ca).
    void capNhat(const DaMoTaiKhoan& x) noexcept { ma_ = x.maTk; soDu_ = x.soDuBanDau; }
    void capNhat(const DaNap& x) noexcept { soDu_ += x.soTien; }
    void capNhat(const DaRut& x) noexcept { soDu_ -= x.soTien; }
    void capNhat(const DaTinhPhi& x) noexcept { soDu_ -= x.soTienPhi; }

    // QUYET DINH — day la noi DUY NHAT duoc kiem tra luat nghiep vu.
    void ghiNhan(SuKien e) { apDung(e); suKienMoi_.push_back(std::move(e)); }

    std::string ma_;
    long soDu_ = 0;
    int soSuKienDaApDung_ = 0;
    std::vector<SuKien> suKienMoi_;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CON BUG: chi luu TRANG THAI thi mat sach lich su ----
    long soDuChiLuuTrangThai = 1000000L;
    soDuChiLuuTrangThai += 200000L;      // nap
    soDuChiLuuTrangThai -= 700000L;      // rut
    soDuChiLuuTrangThai -= 7000L;        // phi
    check(soDuChiLuuTrangThai == 493000L, "so du dung: 493.000");
    // Khach goi len hoi: "vi sao tai khoan toi con 493.000?" Cau tra loi duy nhat ma he
    // thong dua ra duoc la "vi no bang 493.000". Cot so du da bi ghi de bon lan va ba gia
    // tri cu bien mat.
    check(0 == 0, "0 cau hoi lich su tra loi duoc tu mot con so");

    // ---- 2. EVENT SOURCING: trang thai la du lieu THUA ----
    TaiKhoan tk = TaiKhoan::mo("TK-01", 1000000L);
    tk.nap(200000L, "chuyen khoan");
    tk.rut(700000L, "mua hang", 10);      // phi 1% = 7.000
    check(tk.soDu() == 493000L, "cung con so 493.000");

    std::vector<SuKien> lichSu = tk.suKienMoi();
    check(lichSu.size() == 4, "va 4 su kien giai thich tron ven con so do");
    check(std::holds_alternative<DaMoTaiKhoan>(lichSu[0]), "mo tai khoan 1.000.000");
    check(std::get<DaTinhPhi>(lichSu[3]).soTienPhi == 7000L, "phi 7.000, ti le 10 phan nghin");

    TaiKhoan dungLai = TaiKhoan::phatLai(lichSu);
    check(dungLai.soDu() == tk.soDu(), "phat lai lich su cho ra DUNG trang thai cu");
    check(dungLai.ma() == "TK-01", "toan bo trang thai, khong chi so du");
    check(dungLai.soSuKienDaApDung() == 4, "bang cach ap dung dung 4 su kien");
    // Loi hua cot loi: KHONG co cot `so_du` nao trong CSDL. Chi co bang su kien.

    // ---- 3. TRUY VAN THEO THOI GIAN — mien phi, va chi ES moi co ----
    std::vector<SuKien> haiDau(lichSu.begin(), lichSu.begin() + 2);
    check(TaiKhoan::phatLai(haiDau).soDu() == 1200000L, "so du TRUOC khi rut");
    // "So du cua khach nay luc 14h ngay 3 thang truoc la bao nhieu?" — voi mo hinh chi luu
    // trang thai, cau nay can mot bang lich su rieng ma ai do phai nho ghi.

    // ---- 4. DIEU CHI C++ LAM DUOC: CHUNG MINH HAM PHAT LAI KHONG THE NEM ----
    SuKien mau = DaNap{1, "x"};
    TaiKhoan t2;
    static_assert(noexcept(t2.apDung(mau)), "ham ap dung PHAI khong bao gio nem ngoai le");
    // Dong tren la mot bang chung kiem tra tu dong, chay luc BIEN DICH. Neu ngay mai ai do
    // them mot cau kiem tra co `throw` vao `capNhat(...)`, ho phai bo `noexcept` — va luc
    // do `static_assert` nay do ngay. Trong khi neu ho GIU `noexcept` roi van nem, chuong
    // trinh goi `std::terminate`: ngon ngu noi thang rang ham nay khong co quyen that bai.
    //
    // Vi sao dieu do quan trong den the: neu ham phat lai nem ngoai le voi mot su kien cu,
    // thi CA AGGREGATE do khong tai duoc nua. Khong doc duoc so du, khong mo duoc man
    // hinh, khong xu ly duoc giao dich moi. Va trong ES khong co ban sao nao khac de khoi
    // phuc — chuoi su kien LA du lieu.
    //
    // Vi du cu the: hom nay ngan hang ban hanh han muc rut 1.000.000/lan. Neu cau kiem tra
    // do duoc them vao ham AP DUNG, thi moi tai khoan tung rut 3.000.000 nam ngoai deu
    // ngung ton tai. Luat moi chi duoc ap cho QUYET DINH moi:
    std::vector<SuKien> lichSuCu{DaMoTaiKhoan{"TK-02", 5000000L},
                                 DaRut{3000000L, "mua xe"},        // hop le NAM NGOAI
                                 DaNap{1000000L, "luong"}};
    TaiKhoan dung = TaiKhoan::phatLai(lichSuCu);
    check(dung.soDu() == 3000000L, "ham ap dung khong kiem gi -> tai duoc binh thuong");
    bool luatMoiVanApDung = false;
    try { dung.rut(3000000L, "mua xe nua", 10); }
    catch (const std::logic_error&) { luatMoiVanApDung = true; }
    check(luatMoiVanApDung, "va luat moi van chan duoc QUYET DINH moi");

    // ---- 5. HE QUA THU HAI: SU KIEN PHAI TU DU ----
    // `DaTinhPhi` mang san `soTienPhi`, khong mang "hay tinh 1% cua so rut". Neu ham ap
    // dung phai TU TINH phi theo bieu phi hien tai, thi phat lai nam sau se cho ra so du
    // khac — vi bieu phi da doi.
    long phiTinhLaiTheoBieuPhiMoi = 700000L * 20 / 1000;   // bieu phi nam sau: 2%
    check(phiTinhLaiTheoBieuPhiMoi == 14000L, "phi tinh lai hom nay: 14.000");
    check(std::get<DaTinhPhi>(lichSu[3]).soTienPhi == 7000L, "phi THAT luc do: 7.000");
    check(phiTinhLaiTheoBieuPhiMoi != std::get<DaTinhPhi>(lichSu[3]).soTienPhi,
          "lech 7.000 — va moi tai khoan trong he thong deu lech cung luc");
    // Day la bai 84 phan 5 voi hau qua nang hon han: o do su kien thieu du lieu lam mot
    // bao cao sai; o day no lam SO DU sai, tren toan bo he thong, moi lan phat lai.
    // Quy tac: su kien mang KET QUA, khong mang CONG THUC.

    // ---- 6. ANH CHUP: phat lai 100.000 su kien la khong dung duoc ----
    std::vector<SuKien> lichSuDai;
    lichSuDai.push_back(DaMoTaiKhoan{"TK-03", 0});
    for (int i = 0; i < 1000; ++i) lichSuDai.push_back(DaNap{1000L, "lai"});
    TaiKhoan khongAnhChup = TaiKhoan::phatLai(lichSuDai);
    check(khongAnhChup.soSuKienDaApDung() == 1001, "phat lai 1.001 su kien cho MOI lan doc");

    struct AnhChup { long soDu; std::string ma; std::size_t denSuKienThu; };
    AnhChup ac{khongAnhChup.soDu(), "TK-03", lichSuDai.size()};
    lichSuDai.push_back(DaNap{500L, "lai"});
    lichSuDai.push_back(DaNap{500L, "lai"});

    long soDuTuAnhChup = ac.soDu;
    int soSuKienPhaiPhatLai = 0;
    for (std::size_t i = ac.denSuKienThu; i < lichSuDai.size(); ++i) {
        if (const auto* n = std::get_if<DaNap>(&lichSuDai[i])) soDuTuAnhChup += n->soTien;
        ++soSuKienPhaiPhatLai;
    }
    check(soSuKienPhaiPhatLai == 2, "co anh chup: chi phat lai 2 su kien duoi");
    check(soDuTuAnhChup == TaiKhoan::phatLai(lichSuDai).soDu(), "va cho ra cung ket qua");
    check(1001 / 2 > 100, "gap hon 500 lan cong phat lai");
    // Dieu quan trong nhat ve anh chup: no la BO NHO DEM, khong phai nguon su that. Xoa
    // het anh chup di thi he thong chi cham, khong sai. Neu xoa anh chup ma mat du lieu,
    // thi do khong con la event sourcing nua.

    // ---- 7. RIENG CUA C++: CHUOI SU KIEN LA MOT MANG LIEN TUC ----
    // `std::vector<SuKien>` giu su kien THEO GIA TRI, nam lien nhau. Phat lai la mot lan
    // quet tuyen tinh — dung kieu truy cap ma bo nho dem CPU toi uu nhat.
    const char* dau = reinterpret_cast<const char*>(lichSuDai.data());
    const char* cuoi = reinterpret_cast<const char*>(lichSuDai.data() + lichSuDai.size());
    check(static_cast<std::size_t>(cuoi - dau) == lichSuDai.size() * sizeof(SuKien),
          "1.003 su kien nam trong MOT khoi bo nho lien tuc");
    // Doi lai: `sizeof(SuKien)` bang kich thuoc cua bien the LON NHAT cong the loai. Mot
    // su kien nho di chung variant voi mot su kien to thi tra gia bang bo nho. Voi log
    // hang trieu ban ghi, do la danh doi phai do — va la ly do he ES that thuong luu su
    // kien duoi dang chuoi byte tuan tu hoa, khong phai `variant` trong RAM.
    check(sizeof(SuKien) >= sizeof(DaTinhPhi), "variant to bang bien the lon nhat");

    // ---- 8. GIA PHAI TRA, NOI THANG ----
    //   - Su kien la HOP DONG VINH VIEN. Doi nghia mot loai su kien cu la viet lai lich
    //     su; them loai moi thi duoc, sua loai cu thi phai phien ban hoa (bai 79).
    //   - Truy van ("tim moi tai khoan so du < 0") KHONG lam tren chuoi su kien duoc. Bat
    //     buoc phai co mo hinh doc rieng (bai 95) — nen ES gan nhu luon di kem CQRS.
    //   - Xoa du lieu ca nhan theo yeu cau phap ly la bai toan KHO, vi ban chat cua ES la
    //     khong xoa. Phai ma hoa du lieu ca nhan va vut khoa di.
    // Vi vay: ES dung cho nhung phan ma LICH SU LA NGHIEP VU — so ke toan, kho, ho so y
    // te, audit. Khong dung cho bang cau hinh va danh muc.

    std::cout << "OK\n";
    return 0;
}
