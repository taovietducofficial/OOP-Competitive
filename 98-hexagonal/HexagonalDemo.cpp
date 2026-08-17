/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — miền định nghĩa CỔNG, hạ tầng cung cấp BỘ NỐI,
 * mọi phụ thuộc chỉ đi vào TRONG. Ba con bug: miền gọi thẳng hạ tầng nên không test được;
 * cổng nói tiếng bộ nối; và "cấu trúc thư mục lục giác" mà chiều phụ thuộc vẫn ngược.
 * Tại sao cần học: ở Java và Python, "chiều phụ thuộc" là một luật về `import` mà ta phải
 * tự kiểm bằng test. Ở C++ nó có một phiên bản CỨNG HƠN và thấy được ngay: chiều của
 * `#include`. Header của miền không được `#include` bất cứ thứ gì thuộc hạ tầng — và nếu
 * ai đó vi phạm, cái giá không chỉ là kiến trúc mà là THỜI GIAN BUILD (bài 93). C++ còn
 * cho một lựa chọn thứ hai mà hai ngôn ngữ kia không có: cổng dưới dạng THAM SỐ TEMPLATE,
 * đảo ngược phụ thuộc mà không tốn một lời gọi ảo nào.
 */
#include <iostream>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// MIEN — khong biet CSDL, mang, dong ho hay khung phan mem nao ton tai
// (Trong du an that, toan bo phan nay nam trong `mien/` va header cua no
//  khong `#include` bat cu thu gi ngoai thu vien chuan.)
// =====================================================================
struct DonHang {
    std::string ma, maKhach;
    long tongTien, tao;
    DonHang(std::string m, std::string mk, long tt, long t)
        : ma(std::move(m)), maKhach(std::move(mk)), tongTien(tt), tao(t) {
        if (tongTien <= 0) throw std::invalid_argument("tong tien phai duong");
    }
};

// ---- CONG BI DIEU KHIEN (mien GOI RA ngoai) ----
// Chu y tu ngu: "tim theo ma", "luu", "bao cho khach". Khong co `sqlite3_stmt*`, khong
// co `CURL*`. Cong noi tieng NGHIEP VU (bai 81).
class KhoDonHang {
public:
    virtual ~KhoDonHang() = default;
    virtual const DonHang* timTheoMa(const std::string& ma) const = 0;
    virtual void luu(const DonHang& d) = 0;
};
class BaoChoKhach {
public:
    virtual ~BaoChoKhach() = default;
    virtual void bao(const std::string& maKhach, const std::string& noiDung) = 0;
};
class DongHo {
public:
    virtual ~DongHo() = default;
    virtual long bayGio() const = 0;      // bai 67
};

// ---- CONG DIEU KHIEN (the gioi GOI VAO mien) ----
class DatHang {
public:
    virtual ~DatHang() = default;
    virtual DonHang thucHien(const std::string& maKhach, long tongTien) = 0;
};

// Loi ung dung: cai cong dieu khien, dung cong bi dieu khien. 0 phu thuoc ha tang.
class DichVuDatHang : public DatHang {
public:
    DichVuDatHang(KhoDonHang& kho, BaoChoKhach& bao, const DongHo& dongHo)
        : kho_(kho), bao_(bao), dongHo_(dongHo) {}

    DonHang thucHien(const std::string& maKhach, long tongTien) override {
        DonHang d("DH-" + std::to_string(++dem_), maKhach, tongTien, dongHo_.bayGio());
        kho_.luu(d);
        bao_.bao(maKhach, "da tao don " + d.ma);
        return d;
    }
private:
    KhoDonHang& kho_;
    BaoChoKhach& bao_;
    const DongHo& dongHo_;
    int dem_ = 0;
};

// =====================================================================
// BO NOI — ha tang. Chung biet mien; mien KHONG biet chung.
// =====================================================================
class KhoTrongBoNho : public KhoDonHang {
public:
    const DonHang* timTheoMa(const std::string& ma) const override {
        auto it = bang.find(ma);
        return it == bang.end() ? nullptr : &it->second;
    }
    void luu(const DonHang& d) override { bang.insert({d.ma, d}); }
    std::map<std::string, DonHang> bang;
};

// Bo noi thu hai: gia lap SQL. Cung cong, cai dat hoan toan khac.
class KhoSql : public KhoDonHang {
public:
    const DonHang* timTheoMa(const std::string& ma) const override {
        cauLenh.push_back("SELECT * FROM don_hang WHERE ma = '" + ma + "'");
        auto it = bang.find(ma);
        return it == bang.end() ? nullptr : &it->second;
    }
    void luu(const DonHang& d) override {
        cauLenh.push_back("INSERT INTO don_hang VALUES ('" + d.ma + "', ...)");
        bang.insert({d.ma, d});
    }
    mutable std::vector<std::string> cauLenh;
    std::map<std::string, DonHang> bang;
};

class BaoGia : public BaoChoKhach {
public:
    void bao(const std::string& maKhach, const std::string& noiDung) override {
        daBao.push_back(maKhach + ":" + noiDung);
    }
    std::vector<std::string> daBao;
};
class DongHoCoDinh : public DongHo {
public:
    explicit DongHoCoDinh(long l) : luc_(l) {}
    long bayGio() const override { return luc_; }
private:
    long luc_;
};

// =====================================================================
// BAN SAI — mien goi thang ha tang
// =====================================================================
struct KetNoiCsdl {
    static int soLanMoKetNoi;
    static bool coSan;
    KetNoiCsdl() {
        ++soLanMoKetNoi;
        if (!coSan) throw std::logic_error("khong ket noi duoc CSDL");
    }
};
int KetNoiCsdl::soLanMoKetNoi = 0;
bool KetNoiCsdl::coSan = false;

struct DichVuDatHangSai {
    DonHang thucHien(const std::string& maKhach, long tongTien) {
        KetNoiCsdl kn;                                  // <- dung `new`/khoi tao thang ha tang
        return DonHang("DH-X", maKhach, tongTien, 0);
    }
};

// =====================================================================
// CONG DUOI DANG THAM SO TEMPLATE — dieu chi C++ co (phan 7)
// =====================================================================
template <class Kho, class Bao, class Ho>
class DichVuDatHangTinh {
public:
    DichVuDatHangTinh(Kho& k, Bao& b, const Ho& h) : kho_(k), bao_(b), ho_(h) {}
    DonHang thucHien(const std::string& maKhach, long tongTien) {
        DonHang d("DH-" + std::to_string(++dem_), maKhach, tongTien, ho_.bayGio());
        kho_.luu(d);
        bao_.bao(maKhach, "da tao don " + d.ma);
        return d;
    }
private:
    Kho& kho_;
    Bao& bao_;
    const Ho& ho_;
    int dem_ = 0;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CON BUG: mien goi thang ha tang -> KHONG TEST DUOC ----
    bool khongTestDuoc = false;
    try { DichVuDatHangSai().thucHien("KH-01", 100000L); }
    catch (const std::logic_error&) { khongTestDuoc = true; }
    check(khongTestDuoc, "muon test mot luat nghiep vu, phai dung ca CSDL truoc");
    check(KetNoiCsdl::soLanMoKetNoi == 1, "va moi lan chay test la mot lan mo ket noi");
    // He qua day chuyen: test cham -> test gion (hong vi CSDL, khong phai vi bug) ->
    // khong ai chay test nua -> khong ai viet test nua. Bat dau tu dung mot dong khoi tao
    // ha tang nam sai cho.

    // ---- 2. LOI UNG DUNG CHAY VOI 0 HA TANG ----
    KhoTrongBoNho kho;
    BaoGia bao;
    DongHoCoDinh ho(1700000000L);
    DichVuDatHang dv(kho, bao, ho);

    DonHang d = dv.thucHien("KH-01", 250000L);
    check(d.ma == "DH-1" && d.tao == 1700000000L, "ket qua TAT DINH");
    check(kho.bang.size() == 1, "don da duoc luu");
    check(bao.daBao.size() == 1 && bao.daBao[0] == "KH-01:da tao don DH-1", "khach da duoc bao");
    check(KetNoiCsdl::soLanMoKetNoi == 1, "0 ket noi CSDL nao duoc mo them");
    // Bon dong dung boi canh, khong mock, khong khung phan mem, khong tep cau hinh.

    // ---- 3. DOI BO NOI: sua DUNG MOT DONG, o goc lap rap ----
    KhoSql khoSql;
    DichVuDatHang dvSql(khoSql, bao, ho);
    DonHang d2 = dvSql.thucHien("KH-02", 300000L);
    check(d2.ma == "DH-1", "cung logic nghiep vu, khong sua mot chu nao trong mien");
    check(khoSql.cauLenh.size() == 1 && khoSql.cauLenh[0].rfind("INSERT", 0) == 0,
          "nhung lan nay no sinh SQL");
    // "Doi CSDL sau nay" hiem khi xay ra that, va do KHONG phai ly do chinh. Ly do chinh
    // la: bo noi thu hai — ban trong bo nho — cho phep TEST, va no duoc dung hang ngay.

    // ---- 4. CONG PHAI NOI TIENG NGHIEP VU, KHONG NOI TIENG BO NOI ----
    // Cong RO RI (rat hay gap):
    //     class KhoDonHang { virtual sqlite3_stmt* query(const char* sql) = 0; };
    // No "la interface" nen trong nhu da dao nguoc phu thuoc — nhung chua. Ba hau qua:
    //   1. Khong viet noi ban trong bo nho (lay dau ra `sqlite3_stmt*`?) -> mat cai loi o
    //      phan 3;
    //   2. Header cua mien phai `#include <sqlite3.h>` -> chieu phu thuoc van nguoc, VA
    //      moi file cua mien phai dich lai khi thu vien do doi (bai 93);
    //   3. Doi sang kho khoa-gia tri la phai sua cong, tuc la sua mien.
    // Phep thu: doc ten phuong thuc cua cong len. Neu nguoi lam nghiep vu hieu duoc thi
    // cong dung; neu chi lap trinh vien hieu thi do la bo noi doi lot cong (bai 81).

    // ---- 5. CHIEU PHU THUOC — o C++ no la chieu cua `#include` ----
    // Luc giac KHONG phai mot cau truc thu muc. Doi ten thu muc thanh `domain/`,
    // `infrastructure/` ma `#include` van di tu trong ra ngoai thi chang co gi thay doi.
    // Luat that chi co mot: LOI KHONG DUOC THAM CHIEU HA TANG.
    //
    // O C++, luat do kiem duoc bang mot lenh, khong can thu vien nao:
    //     grep -rE '#include\s*[<"](sqlite3|curl|pqxx|boost/asio)' mien/
    // Ket qua rong = dat. Va no manh hon phien ban Java/Python o mot diem: `#include` la
    // phu thuoc VAT LY, nen vi pham khong chi lam ban kien truc — no lam moi file cua mien
    // phai dich lai khi thu vien ha tang doi mot dong.
    KhoDonHang* conTro = &khoSql;
    check(conTro != nullptr, "ha tang CAI cong cua mien — mui ten chi vao trong");
    check(dynamic_cast<KhoDonHang*>(&kho) != nullptr, "ca hai bo noi deu la KhoDonHang");

    // ---- 6. HAI LOAI CONG, VA VI SAO PHAI PHAN BIET ----
    //
    //   Loai              | Ai goi ai              | Vi du o day     | Bo noi
    //   ------------------|------------------------|-----------------|------------------
    //   Cong DIEU KHIEN   | the gioi -> mien       | `DatHang`       | REST, CLI, hang doi
    //   Cong BI DIEU KHIEN| mien -> the gioi       | `KhoDonHang`    | CSDL, SMTP, dong ho
    //
    // Ca hai deu do MIEN dinh nghia — do la diem mau chot va cung la cho hay sai. Voi cong
    // bi dieu khien thi ai cung hieu; voi cong dieu khien thi nguoi ta hay de khung web
    // dinh nghia. Hau qua: chu ky use case bi dinh hinh boi HTTP, va mot job nen muon dung
    // lai thi phai gia lap request.
    DatHang* congDieuKhien = &dv;
    check(congDieuKhien->thucHien("KH-03", 1L).maKhach == "KH-03", "goi qua cong, khong goi thang lop");

    // ---- 7. DIEU CHI C++ CO: CONG DUOI DANG THAM SO TEMPLATE ----
    // Cung nguyen tac dao nguoc phu thuoc, nhung quyet dinh luc BIEN DICH: khong bang ao,
    // khong con tro, noi tuyen duoc — dung nhu bai 88 phan 5.
    KhoTrongBoNho kho2;
    BaoGia bao2;
    DongHoCoDinh ho2(99L);
    DichVuDatHangTinh<KhoTrongBoNho, BaoGia, DongHoCoDinh> dvTinh(kho2, bao2, ho2);
    DonHang d3 = dvTinh.thucHien("KH-04", 500L);
    check(d3.tao == 99L && kho2.bang.size() == 1, "cung ket qua, khong mot loi goi ao nao");
    //
    //   Cong ao (`virtual`)        | Cong template
    //   ---------------------------|---------------------------------
    //   chon bo noi LUC CHAY       | chon LUC BIEN DICH
    //   mot ban ma duy nhat        | moi to hop sinh mot ban ma rieng
    //   doc duoc tu cau hinh       | KHONG
    //   co chi phi goi ao          | 0
    //
    // Quy tac chon giong bai 88: bo noi den TU CAU HINH (doi CSDL theo moi truong) thi
    // dung cong ao; bo noi co dinh va nam trong vong lap nong thi dung template. Voi phan
    // lon ung dung nghiep vu, cong ao la lua chon dung — chi phi goi ao khong dang ke so
    // voi mot luot truy van CSDL.

    // ---- 8. GOC LAP RAP, VA KHI NAO KHONG CAN LUC GIAC ----
    // Co dung mot cho trong chuong trinh duoc phep khoi tao ca mien lan ha tang — `main`.
    // Moi cho khac chi nhan phu thuoc qua constructor (bai 51).
    //
    // Va luc giac co chi phi that: moi cong la mot lop truu tuong, moi bo noi mot lop. Voi
    // mot ung dung CRUD thuan thi do la ba lop cho mot viec ma `save()` lam xong. Ba dau
    // hieu DU de can:
    //   - co luat nghiep vu dang test rieng (khong chi doc/ghi bang);
    //   - co nhieu hon mot duong vao (REST + hang doi + job nen);
    //   - co he ngoai ma ban khong kiem soat (bai 94).
    // Thieu ca ba thi mot controller goi thang repository la thiet ke dung.
    check(kho.bang.size() == 2 && khoSql.bang.size() == 1 && kho2.bang.size() == 1,
          "ba bo noi da chay, mot mien duy nhat — va mien khong biet cai nao la cai nao");

    std::cout << "OK\n";
    return 0;
}
