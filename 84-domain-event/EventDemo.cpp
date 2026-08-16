/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — aggregate GHI sự kiện thay vì tự phát, và hai
 * con bug thật: email xác nhận gửi cho một đơn hàng không tồn tại (phát sự kiện trong
 * transaction), và báo cáo doanh thu lệch (sự kiện mang tham chiếu thay vì mang dữ liệu
 * tại thời điểm xảy ra).
 * Tại sao cần học: C++ đạt được đúng thứ Java đạt bằng `sealed interface`, nhưng bằng
 * một con đường khác hẳn: `std::variant` + `std::visit`. Quên xử lý một loại sự kiện là
 * LỖI BIÊN DỊCH, và toàn bộ chuyện đó xảy ra KHÔNG cần lớp cha, không cần `virtual`,
 * không cần cấp phát động. Quan trọng hơn, C++ làm lộ ra một điều hai ngôn ngữ kia giấu
 * đi: sự kiện phải MANG DỮ LIỆU chứ không mang tham chiếu — vì ở đây, tham chiếu tới một
 * aggregate đã bị huỷ không phải "số sai", nó là hành vi không xác định.
 */
#include <functional>
#include <iostream>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <variant>
#include <vector>
#include <cstdlib>

// =====================================================================
// SU KIEN — bat bien, ten o THI QUA KHU, mang du lieu LUC XAY RA
// =====================================================================
struct DonHangDaTao  { std::string maDon, maKhach; long tongTien; long luc; };
struct DonHangDaGiao { std::string maDon; long tongTienLucGiao; long luc; };
struct DonHangDaHuy  { std::string maDon, lyDo; long soTienHoan; long luc; };

// `variant` la phien ban C++ cua `sealed interface`: tap hop DONG, liet ke duoc het.
using SuKienMien = std::variant<DonHangDaTao, DonHangDaGiao, DonHangDaHuy>;

// Doi chieu — MENH LENH. Khac su kien o ba diem, xem phan 1.
struct GuiEmailXacNhan { std::string maDon, diaChiEmail; };

// Bo cong cu de viet `std::visit` cho gon.
template <class... Ts> struct TongHop : Ts... { using Ts::operator()...; };
template <class... Ts> TongHop(Ts...) -> TongHop<Ts...>;

// =====================================================================
// AGGREGATE DUNG — GHI su kien, KHONG tu phat di
// =====================================================================
enum class TrangThai { MOI_TAO, DA_THANH_TOAN, DA_GIAO, DA_HUY };

class DonHang {
public:
    DonHang(std::string ma, std::string maKhach, long tongTien, long luc)
        : ma_(std::move(ma)), maKhach_(std::move(maKhach)), tongTien_(tongTien) {
        suKienChuaPhat_.push_back(DonHangDaTao{ma_, maKhach_, tongTien_, luc});
    }

    void thanhToan() {
        if (trangThai_ != TrangThai::MOI_TAO)
            throw std::logic_error("chi thanh toan duoc don moi tao");
        trangThai_ = TrangThai::DA_THANH_TOAN;
    }

    void giao(long luc) {
        if (trangThai_ != TrangThai::DA_THANH_TOAN)
            throw std::logic_error("chua thanh toan thi chua giao duoc");
        trangThai_ = TrangThai::DA_GIAO;
        // Su kien chup lai tong tien TAI THOI DIEM GIAO — xem phan 5.
        suKienChuaPhat_.push_back(DonHangDaGiao{ma_, tongTien_, luc});
    }

    void huy(std::string lyDo, long luc) {
        if (trangThai_ == TrangThai::DA_GIAO)
            throw std::logic_error("don da giao thi khong huy duoc");
        long hoan = trangThai_ == TrangThai::DA_THANH_TOAN ? tongTien_ : 0;
        trangThai_ = TrangThai::DA_HUY;
        suKienChuaPhat_.push_back(DonHangDaHuy{ma_, std::move(lyDo), hoan, luc});
    }

    void doiTongTien(long moi) { tongTien_ = moi; }   // dung o phan 5
    TrangThai trangThai() const { return trangThai_; }
    long tongTien() const { return tongTien_; }

    // Tang ung dung lay su kien ra SAU KHI luu thanh cong.
    std::vector<SuKienMien> layVaXoaSuKien() {
        std::vector<SuKienMien> ds = std::move(suKienChuaPhat_);
        suKienChuaPhat_.clear();
        return ds;
    }
    std::size_t soSuKienChoPhat() const { return suKienChuaPhat_.size(); }

private:
    std::string ma_, maKhach_;
    long tongTien_;
    TrangThai trangThai_ = TrangThai::MOI_TAO;
    // Su kien nam TRONG aggregate cho toi khi transaction xong. Aggregate khong biet
    // bus ton tai — khong co field nao tro toi no, khong #include dong nao.
    std::vector<SuKienMien> suKienChuaPhat_;
};

// =====================================================================
// Ha tang: bus + hai nguoi nghe
// =====================================================================
class Bus {
public:
    // Khoa theo `index()` cua variant — tuong duong "dang ky theo loai su kien".
    void dangKy(std::size_t loai, std::function<void(const SuKienMien&)> xuLy) {
        nguoiNghe_[loai].push_back(std::move(xuLy));
    }

    void phat(const SuKienMien& sk) {
        ++soSuKienDaPhat;
        auto it = nguoiNghe_.find(sk.index());
        if (it == nguoiNghe_.end()) return;
        for (auto& h : it->second) {
            // Mot nguoi nghe hong KHONG duoc lam chuyen da xay ra thanh chua xay ra,
            // va cung khong duoc chan nhung nguoi nghe khac. Xem phan 6.
            try { h(sk); } catch (const std::exception&) { ++soLoiNguoiNghe; }
        }
    }

    int soSuKienDaPhat = 0;
    int soLoiNguoiNghe = 0;

private:
    std::map<std::size_t, std::vector<std::function<void(const SuKienMien&)>>> nguoiNghe_;
};

// =====================================================================
// AGGREGATE SAI — tu goi bus ngay ben trong
// =====================================================================
class DonHangSai {
public:
    DonHangSai(std::string ma, Bus& bus) : ma_(std::move(ma)), bus_(bus) {}
    void giao(long luc) {
        trangThai_ = TrangThai::DA_GIAO;
        bus_.phat(DonHangDaGiao{ma_, 100000, luc});   // phat NGAY, trong transaction
    }
private:
    std::string ma_;
    Bus& bus_;                                        // <- aggregate phu thuoc ha tang
    TrangThai trangThai_ = TrangThai::DA_THANH_TOAN;
};

struct HopThu { int soEmailDaGui = 0; };
struct SoDoanhThu { long tong = 0; };

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. SU KIEN != MENH LENH ----
    //
    //                    | MENH LENH (GuiEmailXacNhan) | SU KIEN (DonHangDaGiao)
    //   -----------------|-----------------------------|-------------------------
    //   thi cua ten      | menh lenh: "hay gui"        | qua khu: "da giao"
    //   nguoi nhan       | DUNG MOT, biet truoc        | KHONG BIET, ai nghe cung duoc
    //   tu choi duoc?    | co — "email sai dinh dang"  | KHONG — chuyen xay ra roi
    //   ai quyet dinh?   | nguoi gui                   | khong ai; no la SU THAT
    //
    // Neu `DonHang` phat ra `GuiEmailXacNhan`, thi mien nghiep vu vua quyet dinh ho rang
    // he qua cua viec giao hang LA gui email. Ngay mai them SMS, them tich diem, them ghi
    // so ke toan — moi lan lai sua `DonHang`.
    GuiEmailXacNhan menhLenh{"DH-01", "a@b.c"};
    check(menhLenh.maDon == "DH-01", "menh lenh noi LAM GI va noi voi AI");
    check(std::variant_size_v<SuKienMien> == 3, "ba loai su kien, dem duoc luc BIEN DICH");
    // C++ khong co reflection nen khong tu kiem duoc luat "ten o thi qua khu" nhu Java
    // va Python lam. Bu lai, so loai su kien la hang so biet luc bien dich.

    // ---- 2. AGGREGATE GHI SU KIEN, KHONG PHAT ----
    long dongHo = 1000;
    DonHang don("DH-01", "KH-01", 100000, dongHo++);
    don.thanhToan();
    don.giao(dongHo++);
    check(don.soSuKienChoPhat() == 2, "hai su kien da duoc GHI: da tao, da giao");
    check(don.trangThai() == TrangThai::DA_GIAO, "va trang thai da doi");
    // `DonHang` khong co field `Bus`. Nghia la no test duoc ma khong can bus, khong can
    // hang doi, khong can mang.

    // ---- 3. CON BUG: phat su kien BEN TRONG transaction ----
    Bus busSai;
    HopThu hopThuSai;
    busSai.dangKy(1, [&](const SuKienMien&) { ++hopThuSai.soEmailDaGui; });   // 1 = DonHangDaGiao

    DonHangSai donSai("DH-99", busSai);
    bool luuHong = false;
    try {
        donSai.giao(dongHo++);                     // phat ngay tai day
        throw std::runtime_error("CSDL het cho");  // transaction hong SAU do
    } catch (const std::runtime_error&) { luuHong = true; }

    check(luuHong, "transaction da rollback — don DH-99 khong ton tai trong CSDL");
    check(hopThuSai.soEmailDaGui == 1, "nhung khach da nhan email 'don cua ban da giao'");
    // Khong co cach nao thu email ve. Day la bug kinh dien nhat cua su kien mien, va no
    // chi xay ra khi he thong co loi — nghia la dung luc ban it muon no nhat.

    // ---- 4. BAN DUNG: luu truoc, phat sau ----
    Bus bus;
    HopThu hopThu;
    SoDoanhThu so;
    bus.dangKy(1, [&](const SuKienMien&) { ++hopThu.soEmailDaGui; });
    bus.dangKy(1, [&](const SuKienMien& sk) { so.tong += std::get<DonHangDaGiao>(sk).tongTienLucGiao; });

    DonHang don2("DH-02", "KH-01", 100000, dongHo++);
    don2.thanhToan();
    don2.giao(dongHo++);

    bool luuThatBai = true;                        // gia lap CSDL hong
    std::vector<SuKienMien> choPhat = don2.layVaXoaSuKien();
    if (!luuThatBai) for (const auto& sk : choPhat) bus.phat(sk);
    check(hopThu.soEmailDaGui == 0, "luu hong -> khong email nao duoc gui");
    check(bus.soSuKienDaPhat == 0, "khong su kien nao roi khoi tien trinh");
    // Thu tu dung chi co mot: BAT DAU transaction -> doi aggregate -> LUU -> COMMIT ->
    // roi moi phat su kien. Trong he that, "phat sau commit" hay duoc lam bang outbox:
    // ghi su kien vao mot bang trong CUNG transaction, roi mot tien trinh rieng doc bang
    // do va phat di (bai 91 lo phan gui trung).

    DonHang don3("DH-03", "KH-01", 100000, dongHo++);
    don3.thanhToan();
    don3.giao(dongHo++);
    for (const auto& sk : don3.layVaXoaSuKien()) bus.phat(sk);
    check(hopThu.soEmailDaGui == 1, "luu xong moi phat -> dung mot email");
    check(so.tong == 100000, "va so doanh thu ghi dung 100.000");
    check(don3.soSuKienChoPhat() == 0, "su kien da lay ra thi khong phat lai lan hai");

    // ---- 5. CON BUG: su kien mang THAM CHIEU thay vi mang DU LIEU ----
    auto don4 = std::make_shared<DonHang>("DH-04", "KH-01", 100000, dongHo++);
    don4->thanhToan();
    don4->giao(dongHo++);
    std::vector<SuKienMien> sk4 = don4->layVaXoaSuKien();

    don4->doiTongTien(120000);   // ke toan chinh don sau khi giao (chuyen rat thuong)

    SoDoanhThu soSai, soDung;
    for (const auto& sk : sk4) {
        if (const auto* g = std::get_if<DonHangDaGiao>(&sk)) {
            soSai.tong += don4->tongTien();      // nguoi nghe di TRA LAI object -> gia HIEN TAI
            soDung.tong += g->tongTienLucGiao;   // su kien mang san gia LUC GIAO
        }
    }
    check(soSai.tong == 120000 && soDung.tong == 100000, "lech 20.000 tren mot don");
    // Su kien la ANH CHUP mot khoanh khac. No phai mang du du lieu de nguoi nghe lam
    // viec ma KHONG can di hoi lai ai. Quy tac: neu nguoi nghe phai tra CSDL de hieu su
    // kien, thi su kien do thieu thong tin.
    //
    // O C++ hau qua con nang hon Java/Python. Neu su kien giu `const DonHang&` hoac
    // `DonHang*` thay vi giu gia tri, thi khi aggregate bi huy truoc luc nguoi nghe chay
    // — dieu rat de xay ra vi nguoi nghe chay SAU commit — con tro do lung lang, va doc
    // no la HANH VI KHONG XAC DINH: co the ra so rac, co the sap, co the "chay dung"
    // tren may ban va sai tren may chu. Do la ly do moi truong su kien o tren deu la
    // `std::string` va `long` theo gia tri, khong co con tro nao.
    check(sk4.size() == 2 && std::get<DonHangDaGiao>(sk4[1]).maDon == "DH-04",
          "su kien tu song duoc, khong phu thuoc vong doi cua aggregate");

    // ---- 6. NGUOI NGHE HONG KHONG LAM CHUYEN DA XAY RA THANH CHUA XAY RA ----
    Bus bus3;
    HopThu ht3;
    bus3.dangKy(1, [](const SuKienMien&) { throw std::runtime_error("SMTP chet"); });
    bus3.dangKy(1, [&](const SuKienMien&) { ++ht3.soEmailDaGui; });

    DonHang don5("DH-05", "KH-01", 100000, dongHo++);
    don5.thanhToan();
    don5.giao(dongHo++);
    for (const auto& sk : don5.layVaXoaSuKien()) bus3.phat(sk);

    check(bus3.soLoiNguoiNghe == 1, "mot nguoi nghe hong");
    check(ht3.soEmailDaGui == 1, "nguoi nghe thu hai VAN chay");
    check(don5.trangThai() == TrangThai::DA_GIAO, "va don VAN da giao — su that khong rut lai duoc");
    // Khac biet cot loi voi menh lenh: menh lenh hong thi huy duoc ca viec. Su kien hong
    // thi chi co HE QUA hong, con chuyen da xay ra thi van xay ra roi. Cach chua la thu
    // lai nguoi nghe do (va nguoi nghe phai chiu duoc goi trung — bai 91).

    // ---- 7. DIEU CHI C++ CO O DAY: `std::visit` VET CAN LUC BIEN DICH ----
    std::vector<std::string> moTa;
    std::vector<SuKienMien> mau{
        DonHangDaTao{"DH-06", "KH-01", 1, 1},
        DonHangDaGiao{"DH-06", 1, 2},
        DonHangDaHuy{"DH-06", "khach doi y", 1, 3}};
    for (const auto& sk : mau) {
        moTa.push_back(std::visit(TongHop{
            [](const DonHangDaTao& e)  { return "tao:" + e.maKhach; },
            [](const DonHangDaGiao& e) { return "giao:" + std::to_string(e.tongTienLucGiao); },
            [](const DonHangDaHuy& e)  { return "huy:" + e.lyDo; },
        }, sk));
    }
    check(moTa.size() == 3 && moTa[0] == "tao:KH-01" && moTa[2] == "huy:khach doi y",
          "xu ly du ba loai");
    // Them `DonHangDaTraLai` vao `variant` ma quen them lambda o day:
    //     error: no matching function for call to object of type 'TongHop<...>'
    // Do la ly do KHONG viet lambda bat tat ca (`[](auto&&){...}`) trong `visit` tren
    // variant su kien — no bien loi bien dich thanh bug luc chay, dung nhu vai tro cua
    // `default` trong switch cua Java.

    // ---- 8. Su kien mien giai bai toan cua bai 83 ----
    // Bai 83: "mot transaction sua dung MOT aggregate". Vay khi giao hang xong can cong
    // diem thuong cho khach (mot aggregate khac) thi lam sao?
    //   SAI : don.giao(); khachHang.congDiem();  <- hai aggregate, mot transaction
    //   DUNG: don.giao() ghi DonHangDaGiao -> commit -> nguoi nghe tai KhachHang va cong
    //         diem trong transaction THU HAI.
    // Cai gia: co mot khoanh khac don da giao ma diem chua cong — NHAT QUAN CUOI.
    // Cai duoc: hai aggregate khong khoa lan nhau, them he qua moi khong sua `DonHang`.
    // Neu buoc sau hong va phai quay lai buoc truoc, do la saga (bai 97).

    std::cout << "OK\n";
    return 0;
}
