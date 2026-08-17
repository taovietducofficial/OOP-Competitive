/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — tách mô hình ĐỌC khỏi mô hình GHI. Ba con bug:
 * dựng màn hình danh sách bằng aggregate làm 1.000 lượt truy vấn; thêm cột hiển thị làm
 * bẩn mô hình miền; và mô hình đọc bị dùng để ghi.
 * Tại sao cần học: ở Java và Python, cái giá của việc dùng aggregate cho màn hình danh
 * sách là SỐ LƯỢT TRUY VẤN. Ở C++ có thêm một cái giá thứ hai mà hai ngôn ngữ kia giấu
 * đi: CÁCH DỮ LIỆU NẰM TRONG BỘ NHỚ. Aggregate là một cây con trỏ — mỗi đơn hàng là một
 * lần nhảy, mỗi dòng hàng là một lần nhảy nữa, và bộ nhớ đệm của CPU trượt ở gần như mọi
 * bước. Mô hình đọc là một mảng struct phẳng nằm liền nhau, duyệt một lượt từ đầu đến
 * cuối. Bài đo số lần NHẢY CON TRỎ của cả hai đường — con số đó là thứ quyết định khi
 * màn hình có 50.000 dòng chứ không phải 500.
 */
#include <iostream>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// BEN GHI — aggregate, dung nhu bai 83: co bat bien, co hanh vi, co ranh gioi
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

    DonHang(std::string ma, std::string maKhach)
        : ma_(std::move(ma)), maKhach_(std::move(maKhach)) {}

    void themDong(std::string sp, long donGia, int sl) {
        if (tongTien() + donGia * sl > HAN_MUC) throw std::logic_error("don vuot han muc");
        cacDong_.push_back(DongHang{std::move(sp), donGia, sl});
    }
    void giao() { trangThai_ = "DA_GIAO"; }
    long tongTien() const {
        long t = 0;
        for (const auto& d : cacDong_) t += d.thanhTien();
        return t;
    }
    const std::string& ma() const { return ma_; }
    const std::string& maKhach() const { return maKhach_; }
    const std::string& trangThai() const { return trangThai_; }
    std::size_t soDong() const { return cacDong_.size(); }

private:
    std::string ma_, maKhach_;
    std::vector<DongHang> cacDong_;
    std::string trangThai_ = "MOI_TAO";
};

struct KhachHang {
    std::string ma, ten;
};

// =====================================================================
// BEN DOC — mo hinh PHANG, dung rieng cho MOT man hinh
// =====================================================================
// Khong hanh vi, khong bat bien, khong setter cong khai. No khong phai entity, khong
// phai value object cua mien — no la MOT DONG TREN MAN HINH.
// Chu y: no ghep du lieu cua HAI aggregate (don hang + khach hang) — dieu ma ben ghi bi
// cam lam (bai 83), va ben doc thi hoan toan duoc phep.
struct DongDanhSachDon {
    std::string maDon, tenKhach, trangThai;
    int soDong;
    long tongTien;

    bool operator==(const DongDanhSachDon& k) const {
        return maDon == k.maDon && tenKhach == k.tenKhach && trangThai == k.trangThai
            && soDong == k.soDong && tongTien == k.tongTien;
    }
};

// =====================================================================
// "CSDL" gia — dem so luot truy van, so object da tai, va SO LAN NHAY CON TRO
// =====================================================================
class Csdl {
public:
    // Ben ghi: aggregate song sau con tro (bai 82: entity khong sao chep duoc).
    std::map<std::string, std::unique_ptr<DonHang>> donHang;
    std::map<std::string, KhachHang> khachHang;

    int soLuotTruyVan = 0, soObjectDaTai = 0, soLanNhayConTro = 0;
    void datLai() { soLuotTruyVan = 0; soObjectDaTai = 0; soLanNhayConTro = 0; }

    // Duong GHI: tai aggregate TRON VEN (bat buoc, de kiem bat bien — bai 83).
    DonHang& taiDon(const std::string& ma) {
        ++soLuotTruyVan;
        DonHang& d = *donHang.at(ma);
        ++soLanNhayConTro;                          // theo unique_ptr toi vung heap cua root
        soObjectDaTai += 1 + static_cast<int>(d.soDong());
        soLanNhayConTro += static_cast<int>(d.soDong());   // vector cacDong_ nam o vung khac
        return d;
    }
    const KhachHang& taiKhach(const std::string& ma) {
        ++soLuotTruyVan;
        ++soObjectDaTai;
        ++soLanNhayConTro;
        return khachHang.at(ma);
    }

    // Duong DOC: MOT truy van, tra ve dung nhung cot man hinh can, NAM LIEN NHAU.
    std::vector<DongDanhSachDon> truyVanDanhSach() {
        ++soLuotTruyVan;                            // dung 1 — du co bao nhieu don
        std::vector<DongDanhSachDon> ra;
        ra.reserve(donHang.size());                 // mot lan cap phat, mot vung lien tuc
        for (const auto& [ma, d] : donHang) {
            ++soObjectDaTai;
            ra.push_back(DongDanhSachDon{d->ma(), khachHang.at(d->maKhach()).ten,
                                         d->trangThai(), static_cast<int>(d->soDong()),
                                         d->tongTien()});
        }
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
    Csdl db;
    for (int i = 0; i < 500; ++i) {
        std::string mk = "KH-" + std::to_string(i);
        db.khachHang[mk] = KhachHang{mk, "Khach " + std::to_string(i)};
        auto d = std::make_unique<DonHang>("DH-" + std::to_string(i), mk);
        d->themDong("laptop", 1000000L, 1);
        d->themDong("chuot", 200000L, 2);
        d->themDong("ban phim", 300000L, 1);
        db.donHang["DH-" + std::to_string(i)] = std::move(d);
    }

    // ---- 1. CON BUG: dung man hinh danh sach bang AGGREGATE ----
    db.datLai();
    std::vector<DongDanhSachDon> quaAggregate;
    for (const auto& [ma, _] : db.donHang) {
        DonHang& d = db.taiDon(ma);                          // 1 truy van / don
        const KhachHang& k = db.taiKhach(d.maKhach());       // + 1 truy van / don  <- N+1
        quaAggregate.push_back(DongDanhSachDon{d.ma(), k.ten, d.trangThai(),
                                               static_cast<int>(d.soDong()), d.tongTien()});
    }
    check(db.soLuotTruyVan == 1000, "1.000 luot truy van cho MOT man hinh");
    check(db.soObjectDaTai == 2500, "va 2.500 object: 500 don x (1 root + 3 dong) + 500 khach");
    int nhayQuaAggregate = db.soLanNhayConTro;
    check(nhayQuaAggregate == 2500, "va 2.500 lan NHAY CON TRO — moi lan la mot co hoi truot cache");

    // ---- 2. BAN DUNG: mot truy van, mot mo hinh phang ----
    db.datLai();
    std::vector<DongDanhSachDon> quaModelDoc = db.truyVanDanhSach();
    check(db.soLuotTruyVan == 1, "DUNG MOT luot truy van");
    check(db.soObjectDaTai == 500, "va dung 500 object — moi dong man hinh mot object");
    check(quaModelDoc.size() == quaAggregate.size() && quaModelDoc[0] == quaAggregate[0],
          "cung ket qua, cung noi dung");

    // ---- 3. DIEU CHI C++ NOI RO: CACH DU LIEU NAM TRONG BO NHO ----
    // `quaModelDoc` la mot `std::vector<DongDanhSachDon>` — mot vung nho LIEN TUC. Duyet
    // no la di thang tu dau den cuoi, va bo nho dem cua CPU nap san cac phan tu ke tiep.
    const char* dau = reinterpret_cast<const char*>(quaModelDoc.data());
    const char* cuoi = reinterpret_cast<const char*>(quaModelDoc.data() + quaModelDoc.size());
    check(static_cast<std::size_t>(cuoi - dau) == quaModelDoc.size() * sizeof(DongDanhSachDon),
          "500 dong doc nam lien nhau trong MOT khoi bo nho");
    //
    // Ben ghi thi nguoc lai: mot `map` cua `unique_ptr`, moi aggregate o mot vung heap
    // rieng, va `cacDong_` cua no lai o mot vung khac nua. Duyet 500 don la 2.500 lan
    // nhay toi nhung dia chi khong lien quan gi nhau.
    check(nhayQuaAggregate / static_cast<int>(quaModelDoc.size()) == 5,
          "trung binh 5 lan nhay cho MOI dong man hinh, so voi 0 cua ben doc");
    // Voi 500 dong thi khong ai thay khac biet. Voi 50.000 dong va mot bao cao chay moi
    // dem thi day la khac biet giua 2 giay va 2 phut — va no khong sua duoc bang cach
    // toi uu vong lap, vi nguyen nhan nam o CHO DAT DU LIEU, khong o code.

    // ---- 4. MO HINH DOC DUOC PHEP LAM DIEU BEN GHI BI CAM ----
    // `DongDanhSachDon` ghep du lieu cua HAI aggregate. O ben ghi dieu do bi cam (bai 83:
    // tham chieu bang id, mot transaction mot aggregate). O ben doc no hoan toan hop le —
    // vi mo hinh doc KHONG BAO GIO GHI, nen no khong co bat bien nao de giu.
    check(quaModelDoc[0].tenKhach == "Khach 0", "ten khach nam ngay trong dong doc");
    // Day la diem giai phong lon nhat cua CQRS: ben doc duoc ghep bang thoai mai, doc cheo
    // ngu canh, luu du lieu trung lap — va khong gi trong so do gay hai, vi no khong phai
    // nguon su that.

    // ---- 5. CON BUG: them cot hien thi lam BAN mo hinh mien ----
    // Man hinh can them cot "ten khach". Voi mo hinh dung chung, phan xa la them `tenKhach`
    // vao `DonHang` "cho tien".
    //   - `DonHang` gio giu du lieu cua aggregate khac -> pha bai 83;
    //   - ten khach doi thi phai cap nhat moi don hang cu -> hoac la hien thi sai;
    //   - va khong ai biet `DonHang::tenKhach` la ban chup luc dat hay gia tri hien tai.
    // Voi mo hinh doc: them mot field vao `DongDanhSachDon`, sua mot cau truy van. Mien
    // khong doi mot chu.
    //
    // Ghi chu: neu nghiep vu THAT SU can "ten khach tai thoi diem dat" (hoa don phai in
    // dung ten luc do), thi do la mot value object cua mien, khong phai nhu cau hien thi.
    // Cau hoi la "nghiep vu co can khong", khong phai "man hinh co hien khong".

    // ---- 6. CON BUG: dung mo hinh DOC de GHI ----
    // `DongDanhSachDon` khong co phuong thuc nao. Muon doi trang thai that thi phai di
    // qua aggregate — va bat bien van duoc giu:
    DonHang& don = db.taiDon("DH-0");
    bool chan = false;
    try { don.themDong("may chu", 60000000L, 1); } catch (const std::logic_error&) { chan = true; }
    check(chan, "ben GHI van giu bat bien — moi thay doi phai di qua aggregate");
    check(quaModelDoc[0].tongTien == 1700000L, "ben DOC chi nhin, khong dung vao");
    // Neu ai do sua `quaModelDoc[0].tongTien = 999`, ho chi sua mot BAN SAO trong bo nho
    // cua man hinh — CSDL khong he hay biet. O C++ dieu do la mac dinh cua ngon ngu (ngu
    // nghia gia tri, bai 82); o Python thi khong, va do la cai bay cua ban Python.

    // ---- 7. MO HINH DOC DUOC PHEP CU ----
    std::vector<DongDanhSachDon> anhChup = db.truyVanDanhSach();   // man hinh vua tai xong
    db.taiDon("DH-1").giao();                                      // ai do giao hang NGAY SAU do
    check(anhChup[1].trangThai == "MOI_TAO", "man hinh van hien trang thai CU");
    check(db.donHang.at("DH-1")->trangThai() == "DA_GIAO", "trong khi su that da doi");
    // Cau hoi phai hoi nghiep vu, KHONG duoc tu quyet: "man hinh nay cu 2 giay co sao
    // khong?" Voi danh sach don hang thi thuong la khong. Voi so du tai khoan truoc khi
    // bam nut chuyen tien thi CO — va cho do phai doc tu ben ghi.
    //
    // Quy tac: doc de HIEN THI thi dung mo hinh doc; doc de RA QUYET DINH GHI thi phai tai
    // aggregate (va co khoa lac quan — bai 92).

    // ---- 8. "LITE" NGHIA LA GI, VA RANH GIOI O DAU ----
    //
    //   Muc                | Kho ghi | Kho doc | Do tre | Chi phi
    //   -------------------|---------|---------|--------|------------------
    //   Khong tach         | chung   | chung   | 0      | N+1, mien bi ban
    //   CQRS-LITE (bai nay)| chung   | chung   | 0      | them mo hinh doc + truy van
    //   CQRS day du        | chung   | RIENG   | co     | dong bo, ha tang, van hanh
    //
    // Hang giua giai quyet duoc 90% van de voi gan nhu khong co chi phi van hanh. Dung
    // nhay sang hang cuoi khi chua do duoc rang hang giua khong du.

    // ---- 9. LUAT NGHIEP VU KHONG DUOC NAM O BEN DOC ----
    // Cam do: cau truy van danh sach tinh luon "don nao duoc giam gia". Dung — luc do luat
    // giam gia co hai ban: mot trong mien, mot trong SQL, va chung se lech (bai 87).
    //
    // Phep thu: neu xoa toan bo mo hinh doc di, he thong co con DUNG khong (chi cham va
    // xau)? Neu cau tra loi la "khong, mat luon luat X" thi luat X dang nam sai cho.
    check(db.donHang.at("DH-0")->tongTien() == quaModelDoc[0].tongTien,
          "ben doc TRINH BAY lai con so ben ghi tinh ra, khong tu tinh luat");

    std::cout << "OK\n";
    return 0;
}
