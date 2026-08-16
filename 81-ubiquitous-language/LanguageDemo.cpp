/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học. Cùng một nghiệp vụ viết hai lần, và một
 * con bug thật: `status >= 3` cộng nhầm đơn đã huỷ vào doanh thu.
 * Tại sao cần học: C++ có một công cụ mà hai ngôn ngữ kia không có, và nó biến
 * bài học về ĐẶT TÊN thành một ràng buộc được trình biên dịch kiểm tra. Với
 * `enum class`, phép so sánh `>= 3` KHÔNG BIÊN DỊCH ĐƯỢC — con số không tự chuyển
 * thành trạng thái, và trạng thái không tự chuyển thành số. Nghĩa là ở đây, "dùng
 * sai từ ngữ" không chỉ khó đọc: nó là lỗi build.
 */
#include <iostream>
#include <map>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// BAN 1 — tu ngu cua LAP TRINH VIEN
// =====================================================================
struct DataRecord {
    std::string id;
    long amt;
    int status;   // 1=?, 2=?, 3=?, 4=?  <- bang dich nam trong dau ai do
    bool flag1;
    bool flag2;
};

class DataProcessor {
public:
    void add(const DataRecord& r) { records_.push_back(r); }

    // Nguoi viet ham nay hieu: "status >= 3 nghia la da xong". Va anh ta DUNG —
    // theo cach anh ta hieu chu "xong".
    long calcTotal() const {
        long t = 0;
        for (const auto& r : records_) if (r.status >= 3) t += r.amt;
        return t;
    }

    // Nguoi viet ham nay (ba thang sau, doi khac) cung hieu "status >= 3 la da xong".
    int countDone() const {
        int n = 0;
        for (const auto& r : records_) if (r.status >= 3) ++n;
        return n;
    }

    long calcRefundable() const {
        long t = 0;
        for (const auto& r : records_) if (r.status == 4) t += r.amt;
        return t;
    }

private:
    std::vector<DataRecord> records_;
};

// =====================================================================
// BAN 2 — tu ngu cua NGUOI LAM NGHIEP VU
// =====================================================================
enum class TrangThaiDonHang { MOI_TAO, DA_THANH_TOAN, DA_GIAO, DA_HUY };

// Cau hoi nghiep vu co DUNG MOT cau tra loi, va no nam o day.
static bool laHoanTat(TrangThaiDonHang t) { return t == TrangThaiDonHang::DA_GIAO; }

// "Ket thuc" va "hoan tat" la HAI khai niem khac nhau — va do chinh la cho ban 1 nham.
static bool laKetThuc(TrangThaiDonHang t) {
    return t == TrangThaiDonHang::DA_GIAO || t == TrangThaiDonHang::DA_HUY;
}
static bool duocHoanTien(TrangThaiDonHang t) { return t == TrangThaiDonHang::DA_HUY; }

struct DonHang {
    std::string maDon;
    long soTien;
    TrangThaiDonHang trangThai;
    bool laKhachThanThiet;
    bool giaoNhanhTrongNgay;
};

class SoDonHang {
public:
    void ghiNhan(const DonHang& d) { cacDon_.push_back(d); }

    long doanhThuDaHoanTat() const {
        long t = 0;
        for (const auto& d : cacDon_) if (laHoanTat(d.trangThai)) t += d.soTien;
        return t;
    }

    int soDonDaKetThuc() const {
        int n = 0;
        for (const auto& d : cacDon_) if (laKetThuc(d.trangThai)) ++n;
        return n;
    }

    long soTienPhaiHoanLai() const {
        long t = 0;
        for (const auto& d : cacDon_) if (duocHoanTien(d.trangThai)) t += d.soTien;
        return t;
    }

private:
    std::vector<DonHang> cacDon_;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // Cung bon don hang, bieu dien hai cach.
    DataProcessor cu;
    cu.add({"DH-1", 100000, 3, true, false});
    cu.add({"DH-2", 200000, 2, false, false});
    cu.add({"DH-3", 300000, 4, true, true});   // DA HUY
    cu.add({"DH-4", 400000, 3, false, true});

    SoDonHang moi;
    moi.ghiNhan({"DH-1", 100000, TrangThaiDonHang::DA_GIAO, true, false});
    moi.ghiNhan({"DH-2", 200000, TrangThaiDonHang::DA_THANH_TOAN, false, false});
    moi.ghiNhan({"DH-3", 300000, TrangThaiDonHang::DA_HUY, true, true});
    moi.ghiNhan({"DH-4", 400000, TrangThaiDonHang::DA_GIAO, false, true});

    // ---- 1. CON BUG: `status >= 3` cong nham don da huy vao doanh thu ----
    check(cu.calcTotal() == 800000, "ban cu: 100.000 + 300.000 + 400.000 = 800.000");
    check(moi.doanhThuDaHoanTat() == 500000, "ban moi: 100.000 + 400.000 = 500.000");
    check(cu.calcTotal() - moi.doanhThuDaHoanTat() == 300000,
          "chenh dung bang so tien cua don DA HUY");
    // 300.000d doanh thu khong ton tai vua di vao bao cao tai chinh. Khong ngoai le,
    // khong canh bao, va ca hai ham deu "chay dung" theo y nguoi viet chung.

    // ---- 2. Dieu chi C++ moi lam duoc: PHEP SO SANH SAI KHONG BIEN DICH DUOC ----
    // Voi `enum class`, hai dong duoi deu la LOI BIEN DICH:
    //     if (d.trangThai >= 3) ...
    //     error: no match for 'operator>=' (operands are 'TrangThaiDonHang' and 'int')
    //     int x = d.trangThai;
    //     error: cannot convert 'TrangThaiDonHang' to 'int'
    //
    // Nghia la con bug o phan 1 KHONG VIET RA DUOC trong ban 2. Khong phai "kho viet
    // hon" hay "de nhin thay hon khi review" — la khong bien dich duoc.
    //
    // Chu y: `enum` khong co `class` thi KHONG duoc bao ve — no tu chuyen thanh int:
    enum TrangThaiCu { CU_MOI, CU_THANH_TOAN, CU_GIAO, CU_HUY };
    int soNguyen = CU_HUY;                 // bien dich duoc: enum thuong tu chuyen thanh int
    check(soNguyen == 3, "enum thuong tu chuyen thanh so — va cai bay quay lai");
    check(CU_HUY >= CU_GIAO, "va phep so sanh sai cung bien dich duoc");
    // Do la ly do trong C++ hien dai, `enum class` la mac dinh, con `enum` tran chi con
    // dung khi phai tuong thich voi code C cu.

    // ---- 3. Vi sao `>=` sai ve BAN CHAT ----
    // `status >= 3` chi dung neu 4 la "xong hon" 3. Trong nghiep vu thi khong: DA HUY
    // khong phai mot dang "da giao o muc cao hon". Con so co thu tu, nhung khong co Y
    // NGHIA — va thu tu do la do lap trinh vien tinh co danh so, khong phai do nghiep vu.
    check(!laHoanTat(TrangThaiDonHang::DA_HUY), "phai hoi laHoanTat(), va cau tra loi ro rang");

    // ---- 4. Hai khai niem khac nhau, hai cai ten khac nhau ----
    check(moi.soDonDaKetThuc() == 3, "KET THUC: da giao (2) + da huy (1) = 3 don");
    check(moi.doanhThuDaHoanTat() == 500000, "HOAN TAT: chi da giao");
    check(cu.countDone() == 3, "ban cu dung CUNG dieu kien cho ca hai cau hoi");
    // `countDone()` tinh co DUNG (neu y la "ket thuc"), con `calcTotal()` thi SAI. Cung
    // mot bieu thuc `status >= 3`, mot cho dung mot cho sai — va khong co gi trong code
    // noi cho ban biet cho nao la cho nao.

    // ---- 5. Phep thu: DOC TEN LEN THANH LOI ----
    //
    //   ban cu                          | ban moi
    //   --------------------------------|--------------------------------
    //   "data processor calc total"     | "so don hang, doanh thu da hoan tat"
    //   "record status greater than 3"  | "trang thai don hang la hoan tat"
    //   "flag1"                         | "la khach than thiet"
    //   "flag2"                         | "giao nhanh trong ngay"
    //
    // Cot trai khong doc duoc thanh cau. Cot phai thi doc duoc — va do la phep thu:
    // NOI TO cau do cho nguoi lam nghiep vu nghe.
    check(moi.soTienPhaiHoanLai() == 300000, "cau 'so tien phai hoan lai' cung doc duoc");

    // ---- 6. Bang dich — thu dang le khong nen ton tai ----
    std::map<std::string, std::string> bangDich{
        {"DataRecord", "don hang"}, {"amt", "so tien"},
        {"status=1", "moi tao"}, {"status=2", "da thanh toan"},
        {"status=3", "da giao"}, {"status=4", "da huy"},
        {"flag1", "khach than thiet"}, {"flag2", "giao nhanh trong ngay"},
        {"calcTotal", "doanh thu (?)"}};
    check(bangDich.size() == 9, "chin muc phai nho, chi cho MOT lop");

    std::map<std::string, std::string> bangDichMoi;
    check(bangDichMoi.empty(), "bang dich rong — do la toan bo muc tieu cua bai nay");
    // Ngon ngu chung khong phai la "dat ten tieng Viet". No la: KHONG CO bang dich nao
    // giua loi nguoi lam nghiep vu noi va chu trong ma nguon.

    // ---- 7. Ranh gioi: tu ngu nao KHONG thuoc ngon ngu chung ----
    // Khong phai moi ten deu phai la tieng nghiep vu. Ba loai nam ngoai:
    //   - thuat toan va cau truc du lieu: `binary_search`, `std::vector`;
    //   - bien cuc bo ngan trong mot vong lap ba dong: `i`, `n`, `t`;
    //   - ha tang: `connection_pool`, `retry_policy`.
    // Ngon ngu chung ap cho MO HINH MIEN — noi nguoi lam nghiep vu va lap trinh vien
    // phai noi chuyen duoc voi nhau.
    check(bangDich.count("flag1") == 1, "`flag1` la ten mien -> phai sua");
    check(bangDich.count("std::vector") == 0, "`std::vector` la ten ky thuat -> giu nguyen");

    // ---- 8. Luat quan trong nhat: NGON NGU DI HAI CHIEU ----
    // Neu nguoi lam nghiep vu noi "don treo" ma trong code khong co khai niem do, thi
    // hoac ban thieu mot trang thai, hoac ho dang dung mot tu ma chinh ho cung chua
    // dinh nghia ro. Ca hai deu la mot cuoc trao doi can xay ra.
    //
    // Nguoc lai, neu trong code co `TRANG_THAI_TAM` ma khong ai ben nghiep vu biet no
    // la gi, thi do la mot khai niem do lap trinh vien bia ra — va no se troi dan khoi
    // thuc te cho toi luc gay ra mot con so sai nhu o phan 1.
    check(static_cast<int>(TrangThaiDonHang::DA_HUY) == 3,
          "bon trang thai, va ca bon deu co ten nghiep vu");

    std::cout << "OK\n";
    return 0;
}
