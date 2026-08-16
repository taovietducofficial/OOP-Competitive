/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — ba con bug của hệ thống không có Unit of Work:
 * ghi nửa vời khi lệnh thứ hai hỏng, MẤT thay đổi khi cùng một đơn được tải hai lần, và
 * thay đổi bay hơi vì quên gọi `luu()`.
 * Tại sao cần học: Java cần `try-with-resources`, Python cần `with` — C++ không cần gì
 * cả. RAII làm cho Unit of Work trở thành thứ tự nhiên nhất của ngôn ngữ: destructor
 * chạy trên MỌI đường thoát khỏi khối lệnh, kể cả đường ngoại lệ, nên "ra khỏi phạm vi
 * mà chưa commit = rollback" là hành vi mặc định chứ không phải kỷ luật. Đổi lại, C++
 * có một cái bẫy mà hai ngôn ngữ kia không có: nếu kho trả aggregate THEO GIÁ TRỊ
 * (`std::optional<DonHang>`), bản đồ định danh trở thành vô nghĩa mà code vẫn biên dịch
 * và chạy — và thay đổi rơi vào hư không.
 */
#include <iostream>
#include <map>
#include <memory>
#include <optional>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// MIEN — aggregate root, va interface kho nam CUNG cho voi no
// =====================================================================
class DonHang {
public:
    DonHang(std::string ma, long tongTien) : ma_(std::move(ma)), tongTien_(tongTien) {}
    void themPhi(long p) { tongTien_ += p; }
    void giamGia(long g) { tongTien_ -= g; }
    const std::string& ma() const { return ma_; }
    long tongTien() const { return tongTien_; }
private:
    std::string ma_;
    long tongTien_;
};

// Interface nay thuoc ve MIEN, khong thuoc ve ha tang (bai 66). No noi bang ngon ngu
// nghiep vu — "tim don theo ma" — khong noi "SELECT", khong noi "collection".
class KhoDonHang {
public:
    virtual ~KhoDonHang() = default;
    // Tra CON TRO, khong tra gia tri. Ly do o phan 1 — va do la quyet dinh quan trong
    // nhat trong ca file nay.
    virtual DonHang* timTheoMa(const std::string& ma) = 0;
    virtual void luu(const DonHang& don) = 0;
};

// =====================================================================
// HA TANG — CSDL gia co DEM, de moi thu thanh con so
// =====================================================================
struct CsdlGia {
    std::map<std::string, long> bang;
    int soLanDoc = 0, soLanGhi = 0;

    // Moi lan doc dung mot object MOI — dung nhu moi ORM/driver that lam.
    std::unique_ptr<DonHang> doc(const std::string& ma) {
        ++soLanDoc;
        auto it = bang.find(ma);
        return it == bang.end() ? nullptr : std::make_unique<DonHang>(ma, it->second);
    }
    void ghi(const DonHang& d) { ++soLanGhi; bang[d.ma()] = d.tongTien(); }
};

// Kho THUAN — khong co Unit of Work. Moi `luu()` la mot lan ghi that, ngay lap tuc.
class KhoThuan : public KhoDonHang {
public:
    explicit KhoThuan(CsdlGia& csdl) : csdl_(csdl) {}
    DonHang* timTheoMa(const std::string& ma) override {
        auto d = csdl_.doc(ma);
        if (!d) return nullptr;
        giuHo_.push_back(std::move(d));       // kho thuan phai tu giu de con tro con song
        return giuHo_.back().get();
    }
    void luu(const DonHang& don) override { csdl_.ghi(don); }
private:
    CsdlGia& csdl_;
    std::vector<std::unique_ptr<DonHang>> giuHo_;
};

// =====================================================================
// UNIT OF WORK — o C++ no la mot RAII guard, khong hon
// =====================================================================
class DonViCongViec {
public:
    explicit DonViCongViec(CsdlGia& csdl) : csdl_(csdl) {}

    // Viec 1 — BAN DO DINH DANH: tai hai lan van ra MOT object.
    DonHang* tim(const std::string& ma) {
        auto it = theoDoi_.find(ma);
        if (it != theoDoi_.end()) return it->second.get();
        auto d = csdl_.doc(ma);
        if (!d) return nullptr;
        DonHang* raw = d.get();
        theoDoi_.emplace(ma, std::move(d));
        return raw;
    }

    // Viec 2 — THEO DOI THAY DOI: object da lay tu day thi khong can goi `luu()`.
    void dangKyMoi(std::unique_ptr<DonHang> d) {
        std::string ma = d->ma();
        theoDoi_[ma] = std::move(d);
    }

    // Viec 3 — MOT DIEM QUYET DINH: ghi het, hoac khong ghi gi.
    void commit() {
        for (auto& [ma, d] : theoDoi_) csdl_.ghi(*d);
        daCommit_ = true;
    }

    // Ra khoi pham vi ma chua commit = rollback. Khong ai phai nho goi no, va no chay
    // ke ca khi thoat bang ngoai le. Day la toan bo ly do RAII ton tai.
    // KHONG bao gio nem tu destructor (bai 74) — no chi don dep, khong lam nghiep vu.
    ~DonViCongViec() { if (!daCommit_) theoDoi_.clear(); }

    // Unit of Work la mot pham vi, khong phai mot gia tri: cam sao chep.
    DonViCongViec(const DonViCongViec&) = delete;
    DonViCongViec& operator=(const DonViCongViec&) = delete;

    std::size_t soObjectDangTheoDoi() const { return theoDoi_.size(); }

private:
    CsdlGia& csdl_;
    std::map<std::string, std::unique_ptr<DonHang>> theoDoi_;
    bool daCommit_ = false;
};

// =====================================================================
// KHO SAI — tra aggregate THEO GIA TRI. Bien dich duoc, chay duoc, va vo dung.
// =====================================================================
class KhoTraGiaTri {
public:
    explicit KhoTraGiaTri(CsdlGia& csdl) : csdl_(csdl) {}
    std::optional<DonHang> timTheoMa(const std::string& ma) {
        auto d = csdl_.doc(ma);
        if (!d) return std::nullopt;
        return *d;                      // <- COPY. Tu day tro di moi thu la ban sao.
    }
private:
    CsdlGia& csdl_;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CAI BAY RIENG CUA C++: kho tra AGGREGATE THEO GIA TRI ----
    CsdlGia csdl;
    csdl.bang["DH-00"] = 100000L;
    KhoTraGiaTri khoSai(csdl);

    auto ban1 = khoSai.timTheoMa("DH-00");
    auto ban2 = khoSai.timTheoMa("DH-00");
    ban1->themPhi(10000);
    check(ban1->tongTien() == 110000L, "ban sao 1 doi");
    check(ban2->tongTien() == 100000L, "ban sao 2 KHONG doi — chung khong lien quan gi nhau");
    check(csdl.bang["DH-00"] == 100000L, "va CSDL cung khong doi");
    // Doc lai: `khoSai` co dat mot ban do dinh danh hoan hao thi cung vo nghia, vi thu
    // no tra ra khong phai aggregate — la BAN SAO cua aggregate. Moi sua doi roi vao hu
    // khong, va khong co dong code nao trong sang trong hon dong `return *d;` o tren.
    //
    // Day la ly do `KhoDonHang::timTheoMa` tra `DonHang*`. Trong C++, mot aggregate root
    // la ENTITY (bai 82) — no khong duoc phep sao chep, va kho cua no khong duoc phep
    // tra ban sao. `std::optional<T>` la kieu tuyet voi cho VALUE OBJECT va la kieu sai
    // cho entity.

    // ---- 2. CON BUG: CUNG MOT DON TAI HAI LAN -> MAT THAY DOI ----
    csdl.bang["DH-01"] = 100000L;
    KhoThuan khoThuan(csdl);

    DonHang* a = khoThuan.timTheoMa("DH-01");
    DonHang* b = khoThuan.timTheoMa("DH-01");
    check(a != b, "HAI object khac nhau cho CUNG mot don hang");

    a->themPhi(10000);      // +10.000 -> 110.000
    b->giamGia(5000);       //  -5.000 ->  95.000  (tu ban CU, khong thay phi cua a)
    khoThuan.luu(*a);
    khoThuan.luu(*b);
    check(csdl.bang["DH-01"] == 95000L, "phi 10.000 cua a BIEN MAT — b ghi de");
    // Khong ngoai le, khong canh bao. Lenh ghi cuoi cung thang. Day la "lost update" o
    // ngay TRONG mot tien trinh — chua can hai nguoi dung, chua can hai may chu (bai 92).

    csdl.bang["DH-02"] = 100000L;
    {
        DonViCongViec uow(csdl);
        DonHang* a2 = uow.tim("DH-02");
        DonHang* b2 = uow.tim("DH-02");
        check(a2 == b2, "CUNG mot object — ban do dinh danh lam viec cua no");
        a2->themPhi(10000);
        b2->giamGia(5000);
        uow.commit();
    }
    check(csdl.bang["DH-02"] == 105000L, "ca hai thay doi deu con: 100 + 10 - 5");

    // ---- 3. CON BUG: GHI NUA VOI ----
    int ghiTruoc = csdl.soLanGhi;
    bool hong = false;
    try {
        khoThuan.luu(DonHang("DH-10", 1000L));           // ghi THAT ngay tai day
        throw std::runtime_error("kiem tra ton kho that bai");
    } catch (const std::runtime_error&) { hong = true; }
    check(hong && csdl.bang.count("DH-10") == 1,
          "DH-10 da nam trong CSDL du nghiep vu chua hoan tat");
    check(csdl.soLanGhi == ghiTruoc + 1, "mot lan ghi le loi, khong ai don");

    ghiTruoc = csdl.soLanGhi;
    hong = false;
    try {
        DonViCongViec uow(csdl);
        uow.dangKyMoi(std::make_unique<DonHang>("DH-11", 1000L));
        uow.dangKyMoi(std::make_unique<DonHang>("DH-12", 2000L));
        check(uow.soObjectDangTheoDoi() == 2, "hai object dang cho, chua cai nao cham CSDL");
        throw std::runtime_error("kiem tra ton kho that bai");
        // Destructor cua `uow` chay TRONG luc ngoai le dang bay ra -> rollback.
        // Khong can `finally`, khong can `try-with-resources`, khong can `with`.
    } catch (const std::runtime_error&) { hong = true; }
    check(hong, "van hong nhu tren");
    check(csdl.bang.count("DH-11") == 0 && csdl.bang.count("DH-12") == 0,
          "nhung CSDL sach: khong ghi cai nao");
    check(csdl.soLanGhi == ghiTruoc, "dung 0 lan ghi — khong phai 'ghi roi xoa'");

    // ---- 4. CON BUG: QUEN GOI luu() ----
    DonHang* c = khoThuan.timTheoMa("DH-01");
    c->themPhi(50000);
    // ...va o day thieu mot dong `khoThuan.luu(*c);`
    check(csdl.bang["DH-01"] == 95000L, "50.000 vua bay hoi, khong dau vet");
    // Loi nay khong co cach nao phat hien bang doc code, vi thu thieu la mot dong KHONG
    // ton tai. Trinh bien dich khong biet, linter khong biet.

    {
        DonViCongViec uow(csdl);
        DonHang* c2 = uow.tim("DH-02");
        c2->themPhi(50000);
        uow.commit();                    // KHONG co dong `luu(c2)` nao ca
    }
    check(csdl.bang["DH-02"] == 155000L, "105.000 + 50.000 — khong quen duoc nua");

    // ---- 5. Repository tra AGGREGATE ROOT, khong tra gi khac ----
    // Bai 83: aggregate la don vi nhat quan. Nen kho cung phai la kho cua ROOT.
    //   DUNG : KhoDonHang::timTheoMa("DH-01")   -> DonHang (co luon cac dong ben trong)
    //   SAI  : KhoDongHang::timTheoDon("DH-01") -> vector<DongHang>
    // Cai sai cho phep sua dong hang ma khong di qua don hang, nghia la bat bien
    // "tong <= han muc" mat tac dung — dung con bug o bai 83 phan 2.
    //
    // Quy tac dem duoc: SO REPOSITORY = SO AGGREGATE ROOT.
    check(csdl.bang.size() >= 3, "mot kho cho DonHang, khong co kho rieng cho DongHang");

    // ---- 6. Interface kho thuoc ve MIEN, cai dat thuoc ve HA TANG ----
    // `KhoDonHang` khai bao canh `DonHang`; `KhoThuan` va `DonViCongViec` thi khong.
    // Nghia la mien bien dich duoc ma khong can driver CSDL nao — va test mien chay
    // trong vai mili giay (bai 66, bai 98, bai 99).
    //
    // Phep thu: `#include` nao xuat hien trong file mien cua ban? Neu co <pqxx>,
    // <sqlite3.h>, <mysql.h> — thi mien dang phu thuoc ha tang, va moi loi hua con lai
    // cua kien truc nay deu rong.

    // ---- 7. Ranh gioi: Unit of Work KHONG phai transaction cua CSDL ----
    // Hai thu hay bi nham la mot. Unit of Work la khai niem o TANG UNG DUNG: gom thay
    // doi, mot diem quyet dinh. Transaction la co che cua CSDL. Chung thuong trung ranh
    // gioi, nhung khong phai luc nao cung:
    //   - UoW tren kho trong bo nho thi khong co transaction nao ca;
    //   - mot saga (bai 97) co nhieu UoW, moi cai mot transaction rieng.
    // Nham hai thu dan toi thoi quen tai hai: mo transaction o tang controller va giu no
    // suot request, ke ca trong luc goi API ben ngoai — khoa CSDL bi giu vai giay cho mang.

    // ---- 8. Vi sao RAII lam viec nay tot hon `finally` ----
    // Java phai co `try-with-resources`, Python phai co `with`, va ca hai deu la thu
    // NGUOI GOI phai nho viet. Quen mot chu `try` la mat rollback.
    // Trong C++, `DonViCongViec uow(csdl);` la du: destructor chay tren moi duong thoat.
    // Va vi lop nay `= delete` copy, no khong the bi mang ra khoi pham vi cua no.
    //
    // Cai gia: destructor KHONG duoc nem ngoai le (bai 74). Nen `commit()` phai la lenh
    // TUONG MINH — no lam viec co the that bai, nen no khong the nam trong destructor.
    // Destructor chi lam viec khong bao gio hong: vut bo nhung gi chua ghi.
    check(csdl.soLanDoc > 0 && csdl.soLanGhi > 0, "dem duoc ca doc lan ghi — vi co ranh gioi ro");

    std::cout << "OK\n";
    return 0;
}
