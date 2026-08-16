/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — luật nghiệp vụ thành object có tên, ghép được,
 * giải thích được, dịch được sang SQL. Hai con bug: cùng một luật chép ba nơi rồi lệch
 * nhau, và luật trong bộ nhớ lệch với luật trong SQL.
 * Tại sao cần học: C++ cho phép nạp chồng `&&`, `||`, `!`, nên luật ghép đọc y hệt biểu
 * thức logic bình thường: `duTuoi(18) && duDiem(100) && !biKhoa()`. Không ngôn ngữ nào
 * trong ba ngôn ngữ này viết được gọn như vậy. Nhưng đúng chỗ đó có một cái bẫy nổi
 * tiếng và bài này đo nó: `&&` nạp chồng KHÔNG còn ngắn mạch — nếu toán tử của bạn nhận
 * vào `bool` thì cả hai vế đã bị tính xong trước khi hàm chạy, và một luật "chậm" ở vế
 * phải sẽ chạy cả khi vế trái đã sai. Cách thoát: toán tử phải DỰNG CÂY, không TÍNH.
 */
#include <iostream>
#include <memory>
#include <string>
#include <vector>
#include <cstdlib>

struct KhachHang {
    std::string ma;
    int tuoi, diem;
    bool biKhoa;
};

// =====================================================================
// SPECIFICATION — mot luat nghiep vu, ba kha nang
// =====================================================================
class DacTaBase {
public:
    virtual ~DacTaBase() = default;
    virtual bool thoaMan(const KhachHang&) const = 0;
    virtual std::string moTa() const = 0;              // 1. co TEN (bai 81)
    virtual std::string dieuKienSql() const = 0;       // 2. DICH duoc sang truy van
    // 3. GIAI THICH duoc: truot thi truot o menh de nao
    virtual void lyDoTruot(const KhachHang& k, std::vector<std::string>& ra) const {
        if (!thoaMan(k)) ra.push_back(moTa());
    }
};

// Vo boc gia tri: sao chep thoai mai, va la cho gan toan tu.
class Dac {
public:
    explicit Dac(std::shared_ptr<const DacTaBase> p) : p_(std::move(p)) {}
    bool thoaMan(const KhachHang& k) const { return p_->thoaMan(k); }
    std::string moTa() const { return p_->moTa(); }
    std::string dieuKienSql() const { return p_->dieuKienSql(); }
    std::vector<std::string> lyDoTruot(const KhachHang& k) const {
        std::vector<std::string> r;
        p_->lyDoTruot(k, r);
        return r;
    }
    const std::shared_ptr<const DacTaBase>& goc() const { return p_; }
private:
    std::shared_ptr<const DacTaBase> p_;
};

class Va : public DacTaBase {
public:
    Va(Dac t, Dac p) : trai_(std::move(t)), phai_(std::move(p)) {}
    bool thoaMan(const KhachHang& k) const override {
        // `&&` o DAY la toan tu dung san cua ngon ngu -> VAN ngan mach. Xem phan 5.
        return trai_.thoaMan(k) && phai_.thoaMan(k);
    }
    std::string moTa() const override { return "(" + trai_.moTa() + " VA " + phai_.moTa() + ")"; }
    std::string dieuKienSql() const override {
        return "(" + trai_.dieuKienSql() + " AND " + phai_.dieuKienSql() + ")";
    }
    void lyDoTruot(const KhachHang& k, std::vector<std::string>& ra) const override {
        for (auto& s : trai_.lyDoTruot(k)) ra.push_back(s);   // gom ly do CA HAI nhanh
        for (auto& s : phai_.lyDoTruot(k)) ra.push_back(s);
    }
private:
    Dac trai_, phai_;
};

class Hoac : public DacTaBase {
public:
    Hoac(Dac t, Dac p) : trai_(std::move(t)), phai_(std::move(p)) {}
    bool thoaMan(const KhachHang& k) const override {
        return trai_.thoaMan(k) || phai_.thoaMan(k);
    }
    std::string moTa() const override { return "(" + trai_.moTa() + " HOAC " + phai_.moTa() + ")"; }
    std::string dieuKienSql() const override {
        return "(" + trai_.dieuKienSql() + " OR " + phai_.dieuKienSql() + ")";
    }
private:
    Dac trai_, phai_;
};

class Khong : public DacTaBase {
public:
    explicit Khong(Dac t) : trong_(std::move(t)) {}
    bool thoaMan(const KhachHang& k) const override { return !trong_.thoaMan(k); }
    std::string moTa() const override { return "KHONG " + trong_.moTa(); }
    std::string dieuKienSql() const override { return "NOT (" + trong_.dieuKienSql() + ")"; }
private:
    Dac trong_;
};

// TOAN TU: chung DUNG CAY, khong TINH. Do la ly do chung an toan — xem phan 5.
Dac operator&&(const Dac& a, const Dac& b) { return Dac(std::make_shared<Va>(a, b)); }
Dac operator||(const Dac& a, const Dac& b) { return Dac(std::make_shared<Hoac>(a, b)); }
Dac operator!(const Dac& a) { return Dac(std::make_shared<Khong>(a)); }

// Ba luat co so — moi cai la mot cau nguoi lam nghiep vu noi ra mieng.
class DuTuoiImpl : public DacTaBase {
public:
    explicit DuTuoiImpl(int t) : toiThieu_(t) {}
    bool thoaMan(const KhachHang& k) const override { return k.tuoi >= toiThieu_; }
    std::string moTa() const override { return "du " + std::to_string(toiThieu_) + " tuoi"; }
    std::string dieuKienSql() const override { return "tuoi >= " + std::to_string(toiThieu_); }
private:
    int toiThieu_;
};
class DuDiemImpl : public DacTaBase {
public:
    explicit DuDiemImpl(int t) : toiThieu_(t) {}
    bool thoaMan(const KhachHang& k) const override { return k.diem >= toiThieu_; }
    std::string moTa() const override { return "du " + std::to_string(toiThieu_) + " diem tich luy"; }
    std::string dieuKienSql() const override { return "diem >= " + std::to_string(toiThieu_); }
private:
    int toiThieu_;
};
class BiKhoaImpl : public DacTaBase {
public:
    bool thoaMan(const KhachHang& k) const override { return k.biKhoa; }
    std::string moTa() const override { return "dang bi khoa"; }
    std::string dieuKienSql() const override { return "bi_khoa = 1"; }
};

Dac duTuoi(int t) { return Dac(std::make_shared<DuTuoiImpl>(t)); }
Dac duDiem(int t) { return Dac(std::make_shared<DuDiemImpl>(t)); }
Dac biKhoa()      { return Dac(std::make_shared<BiKhoaImpl>()); }

// Luat co DEM so lan duoc goi — dung o phan 5.
class LuatCoDem : public DacTaBase {
public:
    explicit LuatCoDem(int* dem) : dem_(dem) {}
    bool thoaMan(const KhachHang&) const override { ++(*dem_); return true; }
    std::string moTa() const override { return "luat dat"; }
    std::string dieuKienSql() const override { return "1 = 1"; }
private:
    int* dem_;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    std::vector<KhachHang> danhSach{
        {"KH-1", 25, 150, false},   // du dieu kien
        {"KH-2", 17, 500, false},   // thieu tuoi
        {"KH-3", 30, 50, false},    // thieu diem
        {"KH-4", 40, 900, true},    // bi khoa
        {"KH-5", 22, 100, false}};  // du dieu kien (dung nguong)

    // ---- 1. DIEU CHI C++ VIET GON DUOC: luat doc y het bieu thuc logic ----
    Dac duocVayTinChap = duTuoi(18) && duDiem(100) && !biKhoa();
    // So voi Java:  new DuTuoi(18).va(new DuDiem(100)).va(new BiKhoa().khong())
    // So voi Python: du_tuoi(18) & du_diem(100) & ~bi_khoa()
    // Ban C++ la ban duy nhat doc y het cau `if` ma no thay the.

    int soHopLe = 0;
    for (const auto& k : danhSach) if (duocVayTinChap.thoaMan(k)) ++soHopLe;
    check(soHopLe == 2, "hai khach du dieu kien: KH-1 va KH-5");
    check(duocVayTinChap.moTa() == "((du 18 tuoi VA du 100 diem tich luy) VA KHONG dang bi khoa)",
          "luat tu doc len thanh cau — dan thang vao tai lieu duoc");

    // ---- 2. CON BUG: cung mot luat chep o ba noi ----
    //   man hinh : k.tuoi >= 18 && k.diem >= 100 && !k.biKhoa
    //   job email: k.tuoi >= 18 && k.diem >= 100                 <- QUEN biKhoa
    //   bao cao  : k.tuoi >  18 && k.diem >= 100 && !k.biKhoa    <- `>` thay vi `>=`
    int nManHinh = 0, nJob = 0, nBaoCao = 0;
    for (const auto& k : danhSach) {
        if (k.tuoi >= 18 && k.diem >= 100 && !k.biKhoa) ++nManHinh;
        if (k.tuoi >= 18 && k.diem >= 100) ++nJob;
        if (k.tuoi > 18 && k.diem >= 100 && !k.biKhoa) ++nBaoCao;
    }
    check(nManHinh == 2 && nJob == 3 && nBaoCao == 2, "ba con so khac nhau cho CUNG mot luat");
    check(nJob - nManHinh == 1, "job gui loi moi vay cho mot khach DANG BI KHOA");
    // Ba dong tren deu "chay dung" theo y nguoi viet chung. Khong test nao hong, vi moi
    // cho co test rieng va test do khop voi code o cho do.

    // ---- 3. GIAI THICH: truot o menh de nao ----
    KhachHang truot{"KH-9", 16, 20, true};
    check(!duocVayTinChap.thoaMan(truot), "mot ham bool noi: false");
    // ...va het. `false` khong noi duoc vi sao. Muon bao cho khach "ban chua du tuoi va
    // chua du diem" thi phai viet LAI toan bo luat lan thu tu, duoi dang chuoi if.

    auto lyDo = duocVayTinChap.lyDoTruot(truot);
    check(lyDo.size() == 3, "specification noi: truot o BA menh de");
    check(lyDo[0] == "du 18 tuoi" && lyDo[2] == "KHONG dang bi khoa",
          "va noi ro tung menh de nao — dan thang vao thong bao loi");
    check(duocVayTinChap.lyDoTruot(danhSach[0]).empty(), "khach hop le: khong ly do nao");

    // ---- 4. DICH SANG TRUY VAN ----
    // Danh sach 2 trieu khach thi khong loc trong bo nho duoc — phai loc bang SQL. Nen
    // luat duoc viet LAN NUA, va lan nay lech:
    std::string sqlGoTay = "SELECT * FROM khach_hang WHERE tuoi >= 18 AND diem >= 100";
    check(sqlGoTay.find("bi_khoa") == std::string::npos, "SQL go tay lech khoi luat trong code");

    std::string sqlTuDacTa = "SELECT * FROM khach_hang WHERE " + duocVayTinChap.dieuKienSql();
    check(sqlTuDacTa ==
          "SELECT * FROM khach_hang WHERE ((tuoi >= 18 AND diem >= 100) AND NOT (bi_khoa = 1))",
          "SQL sinh TU CHINH luat — khong the lech");
    // Ranh gioi: chi sinh SQL tu cau truc CUA CHINH specification, va nguong phai la
    // so/hang do mien quyet dinh. Neu can nhet chuoi tu nguoi dung vao, hay tra ve cau
    // co THAM SO (`tuoi >= ?`) cong danh sach gia tri — dung noi chuoi.

    // ---- 5. CAI BAY RIENG CUA C++: `&&` NAP CHONG KHONG NGAN MACH ----
    // Luat vang cua C++: khi ban nap chong `operator&&` hoac `operator||`, chung mat
    // tinh ngan mach — vi chung tro thanh loi goi HAM, va moi doi so cua mot ham deu
    // phai duoc tinh xong truoc khi ham chay.
    //
    // (a) Cach SAI — toan tu nhan `bool`, tuc la nhan KET QUA da tinh:
    int demSai = 0;
    Dac luatDatSai(std::make_shared<LuatCoDem>(&demSai));
    bool veTraiSai = false;                                   // ve trai da SAI roi
    bool ketQuaSai = veTraiSai && luatDatSai.thoaMan(danhSach[0]);
    // Dong tren dung `&&` dung san nen VAN ngan mach. Nhung neu ai do viet mot ham:
    //     bool va(bool a, bool b) { return a && b; }
    //     va(veTrai, luatDat.thoaMan(k))
    // thi `luatDat.thoaMan(k)` DA CHAY truoc khi vao ham. Mo phong dung dieu do:
    auto vaKieuHam = [](bool a, bool b) { return a && b; };
    bool ketQua2 = vaKieuHam(veTraiSai, luatDatSai.thoaMan(danhSach[0]));
    check(!ketQua2 && demSai == 1, "luat dat VAN chay du ve trai da sai — mat ngan mach");
    (void)ketQuaSai;

    // (b) Cach DUNG — toan tu nhan `Dac` va DUNG CAY, khong tinh gi ca:
    int demDung = 0;
    Dac luatDatDung(std::make_shared<LuatCoDem>(&demDung));
    Dac ghep = duTuoi(200) && luatDatDung;      // dung cay: chua ai goi thoaMan()
    check(demDung == 0, "dung cay xong ma luat dat VAN chua chay lan nao");
    check(!ghep.thoaMan(danhSach[0]), "khong ai du 200 tuoi -> ve trai sai");
    check(demDung == 0, "va luat dat VAN khong chay — ngan mach duoc giu nguyen");
    // Vi sao giu duoc: `Va::thoaMan` dung `&&` DUNG SAN o ben trong (xem lop `Va`), va
    // toan tu nap chong chi lam mot viec la ghep hai nhanh lai. Day la quy tac chung:
    // toan tu nap chong nen DUNG CAU TRUC, khong nen TINH KET QUA.

    // ---- 6. GHEP LAI THANH LUAT MOI MA KHONG SUA GI (bai 61) ----
    Dac uuDaiDacBiet = duocVayTinChap || (duDiem(500) && !biKhoa());
    check(uuDaiDacBiet.thoaMan(danhSach[1]), "khach 17 tuoi nhung 500 diem: dat luat moi");
    check(!uuDaiDacBiet.thoaMan(danhSach[3]), "con khach bi khoa thi truot ca hai nhanh");
    // Luat moi ra doi ma KHONG sua mot dong nao cua ba luat co so — mo-dong (bai 61)
    // ap cho luat nghiep vu.

    // ---- 7. KHI NAO KHONG CAN SPECIFICATION ----
    // Mau thiet ke de bi lam dung. Ba cau hoi, can CO it nhat hai:
    //   (a) Luat nay co dung o NHIEU HON MOT cho khong?
    //   (b) No co can GHEP voi luat khac khong?
    //   (c) Co ai can biet VI SAO truot, hoac can dich no sang truy van khong?
    // Neu chi mot cho dung, khong ghep, khong giai thich — thi `if` la dung, va ba lop
    // Va/Hoac/Khong chi la chi phi.
    //
    // Va neu luat thuoc ve dung mot entity, no nen la mot PHUONG THUC cua entity do
    // (bai 86 cau hoi b): `don.quaHan(homNay)` tot hon `DonQuaHan(homNay).thoaMan(don)`.
    check(duTuoi(18).thoaMan(danhSach[0]), "luat co so van dung le duoc");

    // ---- 8. Specification la nen cua bai 88 ----
    // O day luat duoc ghep LUC VIET CODE. Buoc tiep theo la chon luat LUC CHAY — moi
    // quoc gia, moi hang khach mot luat khac nhau, va code goi khong doi mot chu.
    // Do la policy object (bai 88), va no chi la specification + mot bang tra.

    std::cout << "OK\n";
    return 0;
}
