/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — luật đổi theo ngữ cảnh mà code gọi không đổi.
 * Ba con bug: chuỗi if-else chép ba nơi rồi một nơi quên nước Đức (lệch 19 triệu mỗi
 * đơn); thiếu chính sách thì âm thầm về 0; và bùng nổ tổ hợp 4×3 khi trộn hai trục luật.
 * Tại sao cần học: C++ là ngôn ngữ duy nhất trong ba ngôn ngữ này có HAI cách làm policy
 * hoàn toàn khác nhau — chọn LÚC CHẠY qua con trỏ hàm ảo (giống Java/Python), và chọn
 * LÚC BIÊN DỊCH qua tham số template. Cách thứ hai không có lời gọi ảo nào, không cấp
 * phát gì, và trình biên dịch nội tuyến thẳng luật vào chỗ gọi — nhưng đổi lại nó không
 * đọc được cấu hình lúc chạy. Biết khi nào dùng cái nào là một trong những quyết định
 * kiến trúc đặc trưng nhất của C++.
 */
#include <array>
#include <iostream>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <cstdlib>

enum class QuocGia { VN, JP, US, DE, SO_LUONG };
enum class HangKhach { THUONG, BAC, VANG, SO_LUONG };

static const char* ten(QuocGia q) {
    switch (q) {
        case QuocGia::VN: return "VN";
        case QuocGia::JP: return "JP";
        case QuocGia::US: return "US";
        case QuocGia::DE: return "DE";
        default: return "?";
    }
}

// =====================================================================
// POLICY LUC CHAY — mot interface, nhieu cai dat, mot bang tra
// =====================================================================
class ChinhSachThue {
public:
    virtual ~ChinhSachThue() = default;
    virtual long tinhThue(long tienHang) const = 0;
    virtual std::string moTa() const = 0;      // co ten, doc len thanh cau (bai 81)
};

class ThueTheoTiLe : public ChinhSachThue {
public:
    ThueTheoTiLe(int pt, std::string t) : phanTram_(pt), ten_(std::move(t)) {}
    long tinhThue(long tienHang) const override { return tienHang * phanTram_ / 100; }
    std::string moTa() const override { return ten_ + " " + std::to_string(phanTram_) + "%"; }
private:
    int phanTram_;
    std::string ten_;
};

class MienThue : public ChinhSachThue {
public:
    explicit MienThue(std::string t) : ten_(std::move(t)) {}
    long tinhThue(long) const override { return 0; }
    std::string moTa() const override { return ten_; }
private:
    std::string ten_;
};

// Bang tra: mang co kich thuoc dung bang so phan tu cua enum. Thieu mot o = nullptr,
// va vong lap o phan 3 bat duoc ngay.
using BangThue = std::array<std::shared_ptr<ChinhSachThue>, static_cast<std::size_t>(QuocGia::SO_LUONG)>;

static BangThue dungBangThue() {
    BangThue b;
    b[static_cast<std::size_t>(QuocGia::VN)] = std::make_shared<ThueTheoTiLe>(10, "VAT Viet Nam");
    b[static_cast<std::size_t>(QuocGia::JP)] = std::make_shared<ThueTheoTiLe>(8, "thue tieu dung Nhat");
    b[static_cast<std::size_t>(QuocGia::US)] = std::make_shared<MienThue>("khong thue lien bang");
    b[static_cast<std::size_t>(QuocGia::DE)] = std::make_shared<ThueTheoTiLe>(19, "USt Duc");
    return b;
}

// Tra chinh sach: THIEU thi NO, khong am tham ve 0. Xem phan 4.
static const ChinhSachThue& chinhSachCho(const BangThue& b, QuocGia q) {
    auto i = static_cast<std::size_t>(q);
    if (i >= b.size() || !b[i]) throw std::logic_error(std::string("chua co chinh sach cho ") + ten(q));
    return *b[i];
}

// =====================================================================
// POLICY LUC BIEN DICH — thu chi C++ co
// =====================================================================
// Chinh sach la mot THAM SO TEMPLATE. Khong lop cha, khong `virtual`, khong con tro.
struct ThueVn { static long tinhThue(long t) { return t * 10 / 100; } };
struct ThueUs { static long tinhThue(long) { return 0; } };

template <class ChinhSach>
struct MayTinhTien {
    // Loi goi duoi day duoc noi tuyen thang: khong co bang ao nao duoc tra.
    static long tongPhaiTra(long tienHang) { return tienHang + ChinhSach::tinhThue(tienHang); }
};

// =====================================================================
// TRUC THU HAI — giam gia theo hang khach, DOC LAP voi thue
// =====================================================================
using BangGiam = std::array<long (*)(long), static_cast<std::size_t>(HangKhach::SO_LUONG)>;
static long giamThuong(long) { return 0; }
static long giamBac(long t) { return t * 5 / 100; }
static long giamVang(long t) { return t * 10 / 100; }

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    const long tien = 100000000L;

    // ---- 1. CON BUG: chuoi if-else chep o ba noi ----
    // Ba noi cung can thue: man hinh thanh toan, sinh hoa don, bao cao doanh thu. Nuoc
    // Duc duoc them thang truoc, va chi hai trong ba noi duoc cap nhat.
    auto thanhToan = [](QuocGia q, long t) -> long {
        if (q == QuocGia::VN) return t * 10 / 100;
        if (q == QuocGia::JP) return t * 8 / 100;
        if (q == QuocGia::DE) return t * 19 / 100;
        return 0;
    };
    auto hoaDon = thanhToan;
    auto baoCao = [](QuocGia q, long t) -> long {
        if (q == QuocGia::VN) return t * 10 / 100;
        if (q == QuocGia::JP) return t * 8 / 100;
        return 0;                                   // <- QUEN nuoc Duc
    };

    check(thanhToan(QuocGia::DE, tien) == 19000000L, "thanh toan thu dung 19%");
    check(hoaDon(QuocGia::DE, tien) == 19000000L, "hoa don ghi dung 19%");
    check(baoCao(QuocGia::DE, tien) == 0L, "bao cao ghi 0% — lech 19 trieu moi don");
    check(thanhToan(QuocGia::DE, tien) - baoCao(QuocGia::DE, tien) == 19000000L,
          "so sach va tien that khong khop nhau");
    // Nhanh `return 0` nuot tron loi — va voi thue thi 0 la mot con so hoan toan hop le
    // (nuoc My dung la 0%), nen khong ai nghi ngo.

    // ---- 2. POLICY: mot nguon su that, ba noi cung dung ----
    BangThue bang = dungBangThue();
    check(chinhSachCho(bang, QuocGia::DE).tinhThue(tien) == 19000000L, "mot bang, mot cau tra loi");
    check(chinhSachCho(bang, QuocGia::US).tinhThue(tien) == 0L, "My 0% — nhung la 0% CO TEN");
    check(chinhSachCho(bang, QuocGia::US).moTa() == "khong thue lien bang",
          "va ten do phan biet duoc voi 'chua cau hinh'");
    // Diem tinh te nhat cua bai: `MienThue` va "thieu cau hinh" deu cho ra 0, nhung mot
    // cai la QUYET DINH NGHIEP VU con cai kia la LOI. Chuoi if-else khong phan biet duoc.

    // ---- 3. KIEM TRA DU CHINH SACH BANG MAY ----
    for (std::size_t i = 0; i < static_cast<std::size_t>(QuocGia::SO_LUONG); ++i) {
        check(bang[i] != nullptr,
              std::string("thieu chinh sach thue cho ") + ten(static_cast<QuocGia>(i)));
    }
    check(bang.size() == 4, "du 4/4 quoc gia");
    // C++ khong co `QuocGia.values()` nhu Java. Meo `SO_LUONG` o cuoi enum bu duoc dieu
    // do: kich thuoc mang GAN VOI enum, nen them `FR` vao enum la mang tu dai ra mot o,
    // o do bang nullptr, va vong lap tren do NGAY — truoc khi co don hang nao tu Phap.
    //
    // Meo nay chi hoat dong khi enum khong gan gia tri tay (`VN = 100`). Neu can gan gia
    // tri, dung `std::map` va mot mang liet ke tay — va nho rang danh sach do la thu phai
    // bao tri, nen dat no NGAY CANH enum.

    // ---- 4. THIEU CHINH SACH PHAI NO, KHONG DUOC AM THAM VE 0 ----
    BangThue bangThieu = dungBangThue();
    bangThieu[static_cast<std::size_t>(QuocGia::DE)] = nullptr;
    bool noLen = false;
    try { chinhSachCho(bangThieu, QuocGia::DE); } catch (const std::logic_error&) { noLen = true; }
    check(noLen, "tra chinh sach phai NO khi thieu — 19 trieu khong duoc im lang bien mat");
    // Mot ham `chinhSachCho` tra ve `MienThue("mac dinh")` khi khong tim thay la mot
    // trong nhung dong nguy hiem nhat trong ma nghiep vu. Null Object (bai 64) chi dung
    // khi "khong co gi" la hanh vi HOP LE. Voi thue thi khong.

    // ---- 5. DIEU CHI C++ CO: POLICY LUC BIEN DICH ----
    check(MayTinhTien<ThueVn>::tongPhaiTra(100000L) == 110000L, "VN: 100.000 + 10%");
    check(MayTinhTien<ThueUs>::tongPhaiTra(100000L) == 100000L, "US: khong thue");
    // Hai dong tren KHONG co loi goi ao nao, khong cap phat gi, va trinh bien dich noi
    // tuyen thang phep nhan vao cho goi. Voi vong lap tinh tien cho 10 trieu don thi
    // khac biet do do duoc.
    //
    //   Policy LUC BIEN DICH                  | Policy LUC CHAY
    //   --------------------------------------|-----------------------------------
    //   `MayTinhTien<ThueVn>`                 | `chinhSachCho(bang, q)`
    //   khong goi ao, noi tuyen duoc          | mot lan goi ao moi don
    //   moi to hop sinh mot ban ma rieng      | mot ban ma duy nhat
    //   KHONG doc duoc cau hinh luc chay      | doc duoc tu CSDL/file
    //   loi dung sai = LOI BIEN DICH          | loi dung sai = ngoai le luc chay
    //
    // Quy tac chon: neu tap ngu canh CO DINH va biet luc bien dich (kieu du lieu, che do
    // xu ly noi bo) -> template. Neu ngu canh den TU DU LIEU (quoc gia cua khach hang,
    // hang the, cau hinh cua tung khach) -> bang tra luc chay. Thue thuoc loai thu hai:
    // khong ai muon bien dich lai he thong de doi thue suat.

    // ---- 6. HAI TRUC DOC LAP: 4 + 3, KHONG PHAI 4 x 3 ----
    int soLopNeuTronTruc = 4 * 3;
    int soLopKhiTachTruc = 4 + 3;
    check(soLopNeuTronTruc == 12 && soLopKhiTachTruc == 7, "12 lop so voi 7");
    // Va con so do no theo cap so nhan: them truc thu ba (kenh ban) thi 12 -> 36, con
    // 7 -> 10. Quy tac: moi TRUC BIEN THIEN la mot bang chinh sach rieng.

    BangGiam bangGiam{giamThuong, giamBac, giamVang};
    auto tinhTongPhaiTra = [&](long tienHang, QuocGia q, HangKhach h) {
        long giam = bangGiam[static_cast<std::size_t>(h)](tienHang);
        long sauGiam = tienHang - giam;
        return sauGiam + chinhSachCho(bang, q).tinhThue(sauGiam);
    };
    check(tinhTongPhaiTra(100000L, QuocGia::VN, HangKhach::THUONG) == 110000L,
          "VN thuong: 100.000 + 10% = 110.000");
    check(tinhTongPhaiTra(100000L, QuocGia::VN, HangKhach::VANG) == 99000L,
          "VN vang: giam 10% con 90.000, +10% thue = 99.000");
    check(tinhTongPhaiTra(100000L, QuocGia::US, HangKhach::VANG) == 90000L,
          "My vang: giam 10%, khong thue");
    // Chu y THU TU: giam gia TRUOC, thue SAU — thue tinh tren so tien thuc tra. Day la
    // mot luat nghiep vu, va no nam o tang ung dung vi no noi ve QUAN HE giua hai chinh
    // sach chu khong thuoc chinh sach nao. Dao thu tu la sai luat thue o hau het cac
    // nuoc — loai bug khong ai phat hien cho toi luc bi kiem toan.

    // ---- 7. POLICY vs STRATEGY vs SPECIFICATION ----
    //
    //   Mau           | Tra loi cau hoi          | Chon luc nao  | Vi du o day
    //   --------------|--------------------------|---------------|------------------
    //   Specification | "co thoa man khong?"     | ghep luc viet | duocVayTinChap (87)
    //   Policy        | "luat o ngu canh nay?"   | tra LUC CHAY  | bang thue
    //   Strategy      | "lam bang cach nao?"     | tra luc chay  | thuat toan nen/sap xep
    //
    // Policy va Strategy co HINH DANG giong het nhau. Khac nhau o Y DINH: strategy doi
    // CACH LAM cho cung mot ket qua; policy doi CHINH KET QUA vi nghiep vu o ngu canh do
    // khac. Nham lan khong gay bug, nhung goi dung ten giup nguoi sau biet duoc phep doi
    // cai gi ma khong pha gi.
    check(chinhSachCho(bang, QuocGia::VN).tinhThue(1000) != chinhSachCho(bang, QuocGia::JP).tinhThue(1000),
          "policy: hai ngu canh, hai KET QUA khac nhau — va ca hai deu dung");

    // ---- 8. RANH GIOI: khoa tra chinh sach phai la KIEU CUA MIEN ----
    // `std::map<std::string, ...>` voi khoa "VN", "vn", "VNM" la cach chac chan nhat de
    // co mot bug khong ai tim ra. Dung `enum class` thi go sai la loi bien dich, va meo
    // `SO_LUONG` o phan 3 moi lam viec duoc. Chuoi chi nen xuat hien o BIEN (doc cau
    // hinh, nhan request) va duoc doi sang enum ngay tai do (bai 76, bai 78).

    std::cout << "OK\n";
    return 0;
}
