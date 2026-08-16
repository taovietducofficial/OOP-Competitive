/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — hai phép thử để quyết định entity hay value
 * object, và hai con bug thật: định danh do CSDL cấp làm hai khách hàng gộp làm một,
 * và value object khả biến bị chia sẻ làm sai địa chỉ giao hàng.
 * Tại sao cần học: C++ đảo ngược bài toán so với Java và Python. Ở đó, mặc định là
 * chia sẻ tham chiếu nên VALUE OBJECT là thứ phải cố gắng làm đúng. Ở C++, mặc định
 * là ngữ nghĩa giá trị — copy một struct là copy thật — nên value object gần như cho
 * không, còn ENTITY mới là thứ nguy hiểm: một dòng `auto ban_sao = *kho;` sinh ra hai
 * object CÙNG ĐỊNH DANH nhưng khác trạng thái, một mâu thuẫn nghiệp vụ không thể tồn
 * tại ngoài đời. Bù lại, C++ cho công cụ dập tắt nó ở mức trình biên dịch: `= delete`.
 */
#include <iostream>
#include <memory>
#include <set>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// VALUE OBJECT — trong C++, day la mac dinh cua ngon ngu
// =====================================================================
class Tien {
public:
    Tien(long soTien, std::string tienTe) : soTien_(soTien), tienTe_(std::move(tienTe)) {
        if (soTien_ < 0) throw std::invalid_argument("so tien khong duoc am");
        if (tienTe_.size() != 3) throw std::invalid_argument("ma tien te phai dung 3 ky tu");
    }

    long soTien() const { return soTien_; }
    const std::string& tienTe() const { return tienTe_; }

    // Value object mang LUAT cua chinh no, khong chi mang du lieu.
    Tien cong(const Tien& khac) const {
        if (tienTe_ != khac.tienTe_)
            throw std::invalid_argument("khong cong duoc hai loai tien te");
        return Tien(soTien_ + khac.soTien_, tienTe_);   // TRA VE CAI MOI
    }

    // So sanh theo TOAN BO gia tri — dinh nghia cua value object.
    bool operator==(const Tien& k) const { return soTien_ == k.soTien_ && tienTe_ == k.tienTe_; }
    bool operator<(const Tien& k) const {
        return tienTe_ != k.tienTe_ ? tienTe_ < k.tienTe_ : soTien_ < k.soTien_;
    }

private:
    // Khong `const` — xem phan 7 de biet vi sao. Bat bien duoc bao dam bang viec
    // KHONG CO SETTER, khong phai bang `const` tren field.
    long soTien_;
    std::string tienTe_;
};

class DiaChi {
public:
    DiaChi(std::string duong, std::string phuong, std::string tinh)
        : duong_(std::move(duong)), phuong_(std::move(phuong)), tinh_(std::move(tinh)) {
        if (duong_.empty()) throw std::invalid_argument("duong khong duoc rong");
    }
    const std::string& duong() const { return duong_; }

    // "Doi" mot value object = tao cai moi. Khong co setter, khong the co.
    DiaChi voiDuong(const std::string& moi) const { return DiaChi(moi, phuong_, tinh_); }

    bool operator==(const DiaChi& d) const {
        return duong_ == d.duong_ && phuong_ == d.phuong_ && tinh_ == d.tinh_;
    }

private:
    std::string duong_, phuong_, tinh_;
};

// =====================================================================
// ENTITY — va o C++, day moi la thu phai canh giu
// =====================================================================
class DiemGiao {
public:
    DiemGiao(std::string ma, DiaChi diaChi, std::string nguoiPhuTrach)
        : ma_(std::move(ma)), diaChi_(std::move(diaChi)), nguoiPhuTrach_(std::move(nguoiPhuTrach)) {
        if (ma_.empty()) throw std::invalid_argument("diem giao phai co ma ngay luc tao");
    }

    // DAY LA DIEU CHI C++ LAM DUOC: cam sao chep entity o muc trinh bien dich.
    // Voi hai dong nay, `auto sao = *kho;` la LOI BIEN DICH:
    //     error: use of deleted function 'DiemGiao::DiemGiao(const DiemGiao&)'
    DiemGiao(const DiemGiao&) = delete;
    DiemGiao& operator=(const DiemGiao&) = delete;
    // Di chuyen thi VAN cho phep: chuyen quyen so huu khong sinh ra ban sao dinh danh.
    DiemGiao(DiemGiao&&) = default;
    DiemGiao& operator=(DiemGiao&&) = default;

    const std::string& ma() const { return ma_; }
    const DiaChi& diaChi() const { return diaChi_; }   // an toan: DiaChi bat bien
    void doiDiaChi(DiaChi moi) { diaChi_ = std::move(moi); }
    void doiNguoiPhuTrach(std::string moi) { nguoiPhuTrach_ = std::move(moi); }

    // Entity so sanh CHI theo dinh danh. Khong field nao khac duoc xuat hien o day.
    bool operator==(const DiemGiao& d) const { return ma_ == d.ma_; }
    bool operator<(const DiemGiao& d) const { return ma_ < d.ma_; }

private:
    const std::string ma_;   // dinh danh: bat bien tron doi
    DiaChi diaChi_;          // thuoc tinh: doi thoai mai
    std::string nguoiPhuTrach_;
};

// =====================================================================
// BAN SAI 1 — entity CHO PHEP sao chep, va dinh danh bi nhan ban
// =====================================================================
struct DiemGiaoSai {
    std::string ma;
    std::string nguoiPhuTrach;
    bool operator==(const DiemGiaoSai& d) const { return ma == d.ma; }
};

// =====================================================================
// BAN SAI 2 — entity lay dinh danh tu CSDL
// =====================================================================
struct KhachHangSai {
    long id = 0;              // 0 = "chua luu". Quy uoc pho bien nhat, va sai nhat.
    std::string ten;
    bool operator<(const KhachHangSai& k) const { return id < k.id; }
};

// BAN DUNG — dinh danh sinh trong mien, co ngay tu luc tao
class KhachHang {
public:
    explicit KhachHang(std::string ten) : ma_("KH-" + std::to_string(++dem_)), ten_(std::move(ten)) {}
    const std::string& ma() const { return ma_; }
    bool operator<(const KhachHang& k) const { return ma_ < k.ma_; }
private:
    static long dem_;
    std::string ma_, ten_;
};
long KhachHang::dem_ = 0;

// =====================================================================
// BAN SAI 3 — "value object" nhung co setter
// =====================================================================
struct DiaChiSai {
    std::string duong;
    void setDuong(std::string d) { duong = std::move(d); }
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. HAI PHEP THU de quyet dinh entity hay value object ----
    //
    // Phep thu A — "doi HET thuoc tinh, con la cung mot thu khong?"
    DiemGiao kho("DG-01", DiaChi("12 Le Loi", "Ben Nghe", "TP.HCM"), "anh Nam");
    const std::string maGoc = kho.ma();
    kho.doiDiaChi(DiaChi("45 Nguyen Hue", "Ben Nghe", "TP.HCM"));
    kho.doiNguoiPhuTrach("chi Lan");
    check(kho.ma() == maGoc, "doi het thuoc tinh, van la cung diem giao -> ENTITY");

    // Phep thu B — "hai cai giong het nhau, thay cho nhau duoc khong?"
    Tien a(50000, "VND"), b(50000, "VND");
    check(a == b, "hai to 50.000d thay cho nhau duoc -> VALUE OBJECT");
    check(&a != &b, "van la hai object khac nhau trong bo nho — va dieu do KHONG quan trong");

    // ---- 2. CUNG MOT KHAI NIEM, HAI VAI TRO — tuy NGU CANH ----
    DiaChi dcDon("12 Le Loi", "Ben Nghe", "TP.HCM");
    DiaChi dcKhac("12 Le Loi", "Ben Nghe", "TP.HCM");
    check(dcDon == dcKhac, "trong don hang: hai dia chi giong nhau LA MOT");

    DiemGiao khoA("DG-01", dcDon, "anh Nam");
    DiemGiao khoB("DG-02", dcKhac, "chi Lan");
    check(!(khoA == khoB), "trong van chuyen: cung dia chi van la HAI diem giao");
    check(khoA.diaChi() == khoB.diaChi(), "du thuoc tinh dia chi cua chung bang nhau");
    // Cau hoi "cai nay la entity hay value object" KHONG co cau tra loi chung. No phu
    // thuoc vao viec nghiep vu co can theo doi CAI CU THE NAY qua thoi gian hay khong.

    // ---- 3. DIEU CHI C++ MOI CO: SAO CHEP ENTITY LA MAU THUAN NGHIEP VU ----
    // Voi ban SAI (struct thuong), sao chep bien dich ngon lanh:
    DiemGiaoSai saiGoc{"DG-99", "anh Nam"};
    DiemGiaoSai saiSao = saiGoc;               // mot dau `=` sinh ra mot the gioi song song
    saiSao.nguoiPhuTrach = "chi Lan";
    check(saiGoc == saiSao, "hai object nay LA CUNG MOT diem giao (cung ma)...");
    check(saiGoc.nguoiPhuTrach != saiSao.nguoiPhuTrach, "...nhung trang thai KHAC NHAU");
    // Doc lai hai dong tren: cung mot cai kho, dong thoi do anh Nam va chi Lan phu
    // trach. Ngoai doi khong ton tai. Trong bo nho thi ton tai — va tu day tro di, moi
    // thay doi tren mot ban se khong den ban kia. Du lieu bat dau troi ra khoi nhau.

    // Voi ban DUNG, chinh dong do KHONG BIEN DICH DUOC:
    //     DiemGiao ban_sao = kho;
    //     error: use of deleted function 'DiemGiao::DiemGiao(const DiemGiao&)'
    //
    // Nghia la o C++, "khong duoc nhan ban entity" khong phai mot quy uoc trong tai
    // lieu — no la mot luat trinh bien dich thuc thi. Java va Python khong lam duoc dieu
    // nay; o do ban chi co the... nho dung goi `clone()`.
    //
    // He qua: entity phai luu qua con tro / tham chieu, khong luu theo gia tri.
    std::vector<std::unique_ptr<DiemGiao>> cacKho;
    cacKho.push_back(std::make_unique<DiemGiao>("DG-10", dcDon, "anh Nam"));
    cacKho.push_back(std::make_unique<DiemGiao>("DG-11", dcKhac, "chi Lan"));
    check(cacKho.size() == 2, "entity luu bang unique_ptr — mot object, mot chu so huu");
    // Nguoc lai, value object luu THANG theo gia tri, va do la cach dung:
    std::vector<Tien> vi{Tien(50000, "VND"), Tien(20000, "VND")};
    check(vi.size() == 2, "value object luu theo gia tri, sao chep thoai mai");

    // ---- 4. CON BUG: dinh danh do CSDL cap -> hai khach gop lam mot ----
    std::set<KhachHangSai> gioSai{KhachHangSai{0, "Nguyen Van A"}, KhachHangSai{0, "Tran Thi B"}};
    check(gioSai.size() == 1, "HAI khach hang khac nhau, set chi giu MOT");
    // Mat mot khach hang. Khong ngoai le, khong log. Bug nay chi xuat hien khi xu ly
    // theo lo (nhap file CSV, tao hang loat) — nghia la no qua duoc moi test thu cong.

    std::set<KhachHang> gioDung{KhachHang("Nguyen Van A"), KhachHang("Tran Thi B")};
    check(gioDung.size() == 2, "dinh danh sinh trong mien -> hai khach, giu dung hai");
    // He qua thuc te: test khong can CSDL, va co the gui entity qua hang doi truoc khi
    // luu — thu ma kieu id-tu-tang khong cho phep.

    // ---- 5. CON BUG: value object kha bien bi chia se ----
    // O C++ phai CO Y moi tao duoc bug nay (can shared_ptr hoac tham chieu), vi mac
    // dinh cua ngon ngu la sao chep. Do la loi the that su cua C++ o bai nay.
    auto chung = std::make_shared<DiaChiSai>(DiaChiSai{"12 Le Loi"});
    auto don1 = chung, don2 = chung;             // vo tinh dung chung mot object
    don1->setDuong("45 Nguyen Hue");             // chi dinh sua don 1
    check(don2->duong == "45 Nguyen Hue", "don 2 bi doi dia chi theo — du khong ai dung vao no");
    // Hang cua don 2 vua duoc giao sai dia chi. Bug ALIASING, va no chi ton tai duoc vi
    // value object co setter.

    DiaChi cuaDon1("12 Le Loi", "Ben Nghe", "TP.HCM");
    DiaChi cuaDon2 = cuaDon1;                    // sao chep gia tri — an toan tuyet doi
    cuaDon1 = cuaDon1.voiDuong("45 Nguyen Hue");
    check(cuaDon1.duong() == "45 Nguyen Hue", "don 1 doi");
    check(cuaDon2.duong() == "12 Le Loi", "don 2 khong he han gi");

    // ---- 6. Value object mang LUAT, khong chi mang du lieu ----
    check(a.cong(b) == Tien(100000, "VND"), "cong cung te thi duoc");
    bool chan = false;
    try { a.cong(Tien(10, "USD")); } catch (const std::invalid_argument&) { chan = true; }
    check(chan, "cong khac te bi chan — luat nam TRONG kieu du lieu");
    // Bai 90 di sau vao rieng Money.

    // ---- 7. Cam bay rieng cua C++: `const` tren field ----
    // De ep bat bien, phan xa dau tien la khai bao `const long soTien_;`. Nhung lam vay
    // se XOA luon toan tu gan mac dinh, va `std::vector<Tien>` mat kha nang sort/erase:
    //     error: use of deleted function 'Tien& Tien::operator=(const Tien&)'
    // Do la ly do lop Tien o tren KHONG dung `const` tren field. Bat bien duoc bao dam
    // bang viec khong co setter va moi phuong thuc deu `const` — du an toan, va khong
    // hy sinh kha nang dung trong container.
    std::vector<Tien> dsTien{Tien(20000, "VND"), Tien(50000, "VND")};
    dsTien[0] = Tien(30000, "VND");   // gan duoc, vi khong co field `const`
    check(dsTien[0].soTien() == 30000, "value object nen gan lai duoc — dung `const` o cho khac");
    // Nguoc lai, `DiemGiao::ma_` CO `const`, va dung: dinh danh entity khong bao gio
    // duoc phep doi, ke ca qua toan tu gan.

    // ---- 8. Bang quyet dinh ----
    //
    //   Cau hoi                                     | Tra loi CO -> loai nao
    //   --------------------------------------------|-----------------------
    //   Doi het thuoc tinh, con la cung mot thu?     | ENTITY
    //   Hai cai giong het thi thay cho nhau duoc?    | VALUE OBJECT
    //   Nghiep vu can lich su cua CAI NAY?           | ENTITY
    //   Co the chia se tu do giua nhieu chu so huu?  | VALUE OBJECT
    //
    // Quy tac thuc dung: MAC DINH la value object. Chi nang len entity khi co mot cau
    // hoi nghiep vu that su can theo doi cai cu the do qua thoi gian.
    //
    // Va o C++, quy tac do co mot phien ban co the go ra thanh code:
    //   value object -> sao chep duoc, luu theo gia tri
    //   entity       -> `= delete` copy, luu qua unique_ptr
    check(true, "mac dinh la value object; entity phai co ly do");

    std::cout << "OK\n";
    return 0;
}
