/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — một mô hình dùng chung cho cả công ty chặn đội
 * bán hàng tạo khách tiềm năng, làm chữ "hoàn tất" mang hai nghĩa, và biến mọi thay đổi
 * nhỏ thành việc của ba đội.
 * Tại sao cần học: ở Java và Python, cái giá của "mô hình dùng chung" là chi phí TỔ CHỨC
 * — ba đội phải xếp lịch với nhau. Ở C++ có thêm một cái giá đo được bằng đồng hồ: mô
 * hình chung sống trong một HEADER, và mọi đơn vị biên dịch `#include` nó sẽ được dịch
 * lại mỗi khi nó đổi một dòng. Ranh giới ngữ cảnh ở C++ vì thế không chỉ là chuyện thiết
 * kế, nó là chuyện thời gian build — và đó là thứ cả đội cảm thấy mỗi ngày.
 */
#include <iostream>
#include <map>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// SAI — MOT mo hinh dung chung cho ca cong ty
// =====================================================================
struct KhachHangChung {
    // Ban hang can:
    std::string maKhach, ten, nguonKhach, giaiDoanBan;
    // Ke toan can:
    std::string tenPhapNhan, maSoThue, diaChiXuatHoaDon, dieuKhoanThanhToan;
    // Ho tro can:
    std::string email, hangUuTien, ngonNguGiaoTiep;
    // Va mot chu ma ca ba doi cung dung, moi doi hieu mot kieu:
    bool hoanTat = false;

    static KhachHangChung tao(std::string maKhach, std::string ten, std::string maSoThue) {
        // Ke toan yeu cau ma so thue bat buoc — hoan toan hop ly VOI KE TOAN.
        if (maSoThue.empty()) throw std::invalid_argument("ma so thue la bat buoc");
        KhachHangChung k;
        k.maKhach = std::move(maKhach);
        k.ten = std::move(ten);
        k.maSoThue = std::move(maSoThue);
        return k;
    }
};

// =====================================================================
// DUNG — moi bounded context mot mo hinh, noi nhau BANG MA
// =====================================================================

// Ngu canh BAN HANG. "Khach hang" o day la mot CO HOI dang duoc theo duoi.
namespace banHang {
enum class GiaiDoan { TIEM_NANG, DANG_TU_VAN, DA_CHOT, DA_MAT };

struct KhachHang {
    std::string maKhach, ten, nguonKhach;
    GiaiDoan giaiDoan;

    KhachHang(std::string ma, std::string t, std::string nguon, GiaiDoan gd)
        : maKhach(std::move(ma)), ten(std::move(t)), nguonKhach(std::move(nguon)), giaiDoan(gd) {
        if (ten.empty()) throw std::invalid_argument("khach tiem nang phai co ten");
    }
    // Voi BAN HANG, "hoan tat" nghia la DA CHOT DON.
    bool daHoanTat() const { return giaiDoan == GiaiDoan::DA_CHOT; }
};
}  // namespace banHang

// Ngu canh KE TOAN. "Khach hang" o day la mot PHAP NHAN xuat hoa don duoc.
namespace keToan {
struct BenNhanHoaDon {
    std::string maKhach, tenPhapNhan, maSoThue, diaChiXuatHoaDon;
    bool daThuDuTien;

    BenNhanHoaDon(std::string ma, std::string tpn, std::string mst, std::string dc, bool thu)
        : maKhach(std::move(ma)), tenPhapNhan(std::move(tpn)), maSoThue(std::move(mst)),
          diaChiXuatHoaDon(std::move(dc)), daThuDuTien(thu) {
        if (maSoThue.empty()) throw std::invalid_argument("ben nhan hoa don phai co ma so thue");
    }
    // Voi KE TOAN, "hoan tat" nghia la DA THU DU TIEN.
    bool daHoanTat() const { return daThuDuTien; }
};
}  // namespace keToan

// Ngu canh HO TRO. "Khach hang" o day la mot NGUOI co the mo phieu ho tro.
namespace hoTro {
struct NguoiDung {
    std::string maKhach, email;
    int mucUuTien;
};
}  // namespace hoTro

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CON BUG: doi ban hang khong tao noi mot khach tiem nang ----
    // 9h sang o hoi cho. Nhan vien ban hang gap mot nguoi, co ten va so dien thoai, muon
    // ghi lai ngay. Nguoi do chua phai khach, chua co cong ty, chua co ma so thue.
    bool biChan = false;
    try { KhachHangChung::tao("KH-01", "Chi Hoa o hoi cho", ""); }
    catch (const std::invalid_argument&) { biChan = true; }
    check(biChan, "khong tao duoc: mo hinh chung doi ma so thue");
    // Luat "ma so thue la bat buoc" HOAN TOAN DUNG — voi ke toan. No chi sai khi bi ap
    // len mot ngu canh ma khai niem "khach hang" con chua co nghia do.
    //
    // Cach va ma moi du an deu lam, va vi sao no te hon: dat `maSoThue` thanh tuy chon.
    // The la ke toan mat luon bao dam "moi ben nhan hoa don deu co ma so thue", va phai
    // tu kiem o moi cho dung. Mot rang buoc that vua bien thanh loi khuyen.

    banHang::KhachHang ch("KH-01", "Chi Hoa o hoi cho", "hoi cho", banHang::GiaiDoan::TIEM_NANG);
    check(ch.ten == "Chi Hoa o hoi cho", "ngu canh ban hang: tao duoc voi 4 field");
    check(sizeof(banHang::KhachHang) < sizeof(KhachHangChung),
          "va mo hinh nho hon han — 4 field so voi 12");

    // ---- 2. CON BUG: cung mot chu, hai nghia ----
    // Don cua chi Hoa: da chot ban (ban hang goi la "hoan tat"), nhung cong no 30 ngay
    // nen chua thu tien (ke toan KHONG goi la hoan tat).
    banHang::KhachHang daChot("KH-01", "Chi Hoa", "hoi cho", banHang::GiaiDoan::DA_CHOT);
    keToan::BenNhanHoaDon chuaThu("KH-01", "Cong ty Hoa Mai", "0301234567", "12 Le Loi", false);

    check(daChot.daHoanTat(), "BAN HANG: hoan tat = da chot -> DUNG");
    check(!chuaThu.daHoanTat(), "KE TOAN: hoan tat = da thu tien -> CHUA");
    check(daChot.daHoanTat() != chuaThu.daHoanTat(),
          "cung mot khach, cung mot chu, hai cau tra loi — va ca hai deu dung");
    // Voi mo hinh chung, `hoanTat` la MOT bool. Ai gan no? Doi nao gan thi doi kia doc
    // sai. Khong co cach va nao ngoai viec tach ra thanh hai khai niem — va tach ra thi
    // da la hai bounded context roi.
    //
    // Day la con bug dung nhu bai 81 phan 1, nhung o quy mo to chuc: o do la hai lap
    // trinh vien hieu khac nhau, o day la hai PHONG BAN hieu khac nhau. Va ho deu dung.

    // ---- 3. NOI HAI NGU CANH BANG MA, KHONG BANG OBJECT ----
    check(daChot.maKhach == chuaThu.maKhach, "cung mot con nguoi ngoai doi");
    // Nhung KHONG co duong nao di tu `banHang::KhachHang` sang `keToan::BenNhanHoaDon`.
    // Day chinh la bai 83 (tham chieu bang id) nang len cap do to chuc: hai ngu canh chia
    // se mot DINH DANH, khong chia se mot MO HINH.
    //
    // Va trinh bien dich canh giup — dong duoi KHONG bien dich duoc:
    //     keToan::BenNhanHoaDon x = daChot;
    //     error: no matching function for call to 'BenNhanHoaDon::BenNhanHoaDon(banHang::KhachHang&)'
    // Chu y: dieu nay chi dung vi constructor cua `BenNhanHoaDon` khong nhan mot tham so
    // duy nhat co the chuyen doi ngam. Neu no co mot constructor mot tham so khong
    // `explicit`, C++ se tu tim duong chuyen doi — va ranh gioi vua thung mot lo.

    // ---- 4. DO CHI PHI THAY DOI ----
    // Ke toan can them `dieuKhoanThanhToan`.
    //   Mo hinh chung: sua header -> MOI file `#include` no duoc dich lai. Ba doi cung
    //                  build lai, cung test lai, cung trien khai.
    //   Tach ngu canh: sua header cua rieng ke toan -> chi cac file cua ke toan dich lai.
    int doiBiAnhHuongMoHinhChung = 3;
    int doiBiAnhHuongKhiTach = 1;
    check(doiBiAnhHuongMoHinhChung == 3 && doiBiAnhHuongKhiTach == 1, "3 doi so voi 1");
    // O C++ con so nay khong chi la chuyen lich hop: no la THOI GIAN BUILD. Mot header
    // dung chung duoc include o 400 file la 400 lan dich lai moi khi no doi mot dong —
    // va do la thu ca doi cam thay moi ngay, khac han voi "no ky thuat" truu tuong.

    // ---- 5. BAN DO NGU CANH: quan he giua cac ngu canh co TEN ----
    //
    //   Quan he            | Nghia                                   | Khi nao dung
    //   -------------------|-----------------------------------------|-------------------
    //   Doi tac            | hai doi cung doi, cung chiu trach nhiem | hai doi cung cong ty
    //   Khach/Nha cung cap | thuong nguon nghe ha nguon              | co quyen thuong luong
    //   Tuan thu           | ha nguon dung y nguyen mo hinh tren     | ben tren khong doi duoc
    //   Chong hu hong      | ha nguon DICH mo hinh tren sang cua minh| mo hinh tren xau (bai 94)
    //   Nhan chung         | hai doi cung so huu mot phan ma dung chung | rat it, rat nguy hiem
    //
    // "Nhan chung" la thu moi nguoi bat dau va hoi han: mot thu vien `common-model` ma
    // ba doi cung sua. No co moi nhuoc diem cua mo hinh chung, cong them viec khong ai
    // so huu no.
    std::map<std::string, std::string> banDo{
        {"BanHang -> KeToan", "Khach/Nha cung cap: ban hang chot don, ke toan xuat hoa don"},
        {"KeToan -> CongThue", "Tuan thu: co quan thue khong doi dinh dang vi ta"},
        {"HoTro -> BanHang", "Chong hu hong: ho tro tu dich, khong phu thuoc giai doan ban"}};
    check(banDo.size() == 3, "ban do ngu canh la tai lieu THAT, ve duoc tren mot trang giay");
    // Neu khong ve duoc ban do nay cho he thong cua ban, thi ranh gioi ngu canh chua ton
    // tai — chi co cac thu muc code cung dung chung mot mo hinh.

    // ---- 6. DICH O BIEN, MOI CHIEU MOT LAN ----
    std::vector<keToan::BenNhanHoaDon> soHoaDon;
    if (daChot.daHoanTat()) {
        soHoaDon.emplace_back(daChot.maKhach, "Cong ty Hoa Mai", "0301234567", "12 Le Loi", false);
    }
    check(soHoaDon.size() == 1, "dich o bien: mot chieu, mot cho, co ten");
    check(soHoaDon[0].maKhach == daChot.maKhach, "chi MA di qua bien");
    // Cho dich nay la noi DUY NHAT hai ngon ngu gap nhau, nen no la noi duy nhat phai sua
    // khi mot ben doi. Bai 94 noi ky ve viec bao ve minh khi ben kia co mo hinh xau.

    // ---- 7. KHI NAO KHONG TACH NGU CANH ----
    // Bounded context co chi phi that: mo hinh lap lai, ma dich o bien, du lieu dong bo
    // tre. Ba dau hieu cho thay CHUA nen tach:
    //   - Ca he thong do MOT doi lam, va moi nguoi dung cung mot bo tu ngu.
    //   - Chua tim ra duoc mot tu nao mang hai nghia (phep thu o phan 2).
    //   - So field ma moi ben phai bo trong con nho.
    // Nguoc lai, ba dau hieu DA den luc tach:
    //   - Co field ma nua so noi dung luon de rong;
    //   - Co tu ma ban phai hoi lai "y anh la hoan tat theo nghia nao";
    //   - Mot thay doi nho phai xep lich voi doi khong lien quan.
    check(true, "ranh gioi ngu canh la quyet dinh TO CHUC, duoc bieu dien bang code");

    std::cout << "OK\n";
    return 0;
}
