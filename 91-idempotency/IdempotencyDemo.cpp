/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — thử lại sau timeout làm khách bị trừ tiền hai
 * lần; "kiểm tra rồi mới làm" vẫn hỏng vì khe hở giữa hai lời gọi; cùng khoá khác nội
 * dung phải bị từ chối.
 * Tại sao cần học: C++ có sẵn đúng nguyên thuỷ mà bài này cần — `std::map::insert` trả
 * về `pair<iterator, bool>`, và cái `bool` đó chính là câu trả lời "tôi có phải người
 * giành được chỗ không". Một lời gọi, không khe hở. Nhưng C++ cũng có một cái bẫy mà
 * hai ngôn ngữ kia không có, và nó phá hỏng đúng bài toán này: `so[khoa]` — chỉ ĐỌC
 * thôi — đã CHÈN một bản ghi rỗng vào map. Sổ idempotency của bạn tự đầy lên bằng những
 * bản ghi không ai tạo, và câu kiểm tra "khoá này đã dùng chưa" trả lời sai từ lần thứ hai.
 */
#include <iostream>
#include <map>
#include <mutex>
#include <stdexcept>
#include <string>
#include <cstdlib>

struct LenhChuyenTien {
    std::string khoaIdempotency, tuTaiKhoan;
    long soTien;
};

struct BienLai {
    std::string maGiaoDich;
    long soDuSauKhi;
    bool operator==(const BienLai& b) const {
        return maGiaoDich == b.maGiaoDich && soDuSauKhi == b.soDuSauKhi;
    }
};

class TaiKhoan {
public:
    explicit TaiKhoan(long soDu) : soDu_(soDu) {}
    void tru(long t) {
        if (soDu_ < t) throw std::logic_error("khong du so du");
        soDu_ -= t;
    }
    long soDu() const { return soDu_; }
private:
    long soDu_;
};

// =====================================================================
// SAI 1 — khong co khoa idempotency: thu lai = tru tien lan nua
// =====================================================================
class DichVuNgayTho {
public:
    BienLai chuyen(TaiKhoan& tk, long soTien) {
        tk.tru(soTien);
        return BienLai{"GD-" + std::to_string(++dem_), tk.soDu()};
    }
private:
    int dem_ = 0;
};

// =====================================================================
// SAI 2 — "kiem tra roi moi lam": co khe ho giua hai loi goi
// =====================================================================
class DichVuKiemTraRoiLam {
public:
    bool daCo(const std::string& khoa) const { return daXuLy_.count(khoa) == 1; }  // buoc 1
    void lam(TaiKhoan& tk, const LenhChuyenTien& l) {                              // buoc 2
        tk.tru(l.soTien);
        daXuLy_[l.khoaIdempotency] = BienLai{"GD-" + std::to_string(++dem_), tk.soDu()};
    }
private:
    std::map<std::string, BienLai> daXuLy_;
    int dem_ = 0;
};

// =====================================================================
// DUNG — GIANH CHO nguyen tu, roi moi lam
// =====================================================================
class DichVuIdempotent {
public:
    BienLai chuyen(TaiKhoan& tk, const LenhChuyenTien& l) {
        std::lock_guard<std::mutex> khoa(mtx_);      // map khong tu an toan da luong
        std::string vanTay = l.tuTaiKhoan + "|" + std::to_string(l.soTien);

        // MOT loi goi vua hoi vua gianh cho. `.second` = "toi co phai nguoi chen khong".
        auto [vt, daChen] = so_.insert({l.khoaIdempotency, BanGhi{vanTay, false, {}}});

        if (!daChen) {
            const BanGhi& cu = vt->second;
            // Cung khoa nhung NOI DUNG KHAC -> hai lenh khac nhau bi trung khoa, khong
            // phai mot lenh gui lai. Tra ket qua cu la sai nghiem trong.
            if (cu.vanTay != vanTay) throw std::logic_error("khoa da dung cho mot lenh khac");
            if (!cu.xong) throw std::logic_error("lenh dang duoc xu ly, hay thu lai sau");
            return cu.ketQua;                        // phat lai KET QUA CU, khong lam lai
        }

        // Toi day thi CHAC CHAN chi minh ta gianh duoc cho.
        ++soLanThucSuTru;
        tk.tru(l.soTien);
        BienLai bl{"GD-" + std::to_string(++dem_), tk.soDu()};
        vt->second = BanGhi{vanTay, true, bl};
        return bl;
    }

    std::size_t soKhoa() const { return so_.size(); }
    int soLanThucSuTru = 0;

private:
    struct BanGhi {
        std::string vanTay;
        bool xong;
        BienLai ketQua;
    };
    std::map<std::string, BanGhi> so_;
    std::mutex mtx_;
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
    // ---- 1. CON BUG: thu lai sau timeout = tru tien hai lan ----
    // Kich ban co that va rat thuong: may chu xu ly xong, roi mang dut truoc khi tra
    // loi. Dien thoai cua khach khong phan biet duoc "chua xu ly" voi "xu ly xong ma mat
    // phan hoi", nen no thu lai — dung nhu moi thu vien HTTP duoc cau hinh.
    TaiKhoan tk(1000000);
    DichVuNgayTho ngayTho;
    ngayTho.chuyen(tk, 100000);          // lan 1: thanh cong, phan hoi bi mat
    ngayTho.chuyen(tk, 100000);          // lan 2: dien thoai tu thu lai
    check(tk.soDu() == 800000, "khach bi tru 200.000 cho MOT giao dich");

    // ---- 2. CON BUG: "kiem tra roi moi lam" van hong ----
    TaiKhoan tk2(1000000);
    DichVuKiemTraRoiLam vaTam;
    LenhChuyenTien lenh{"KEY-1", "TK-A", 100000};

    bool aThay = vaTam.daCo(lenh.khoaIdempotency);   // phien A: chua co
    bool bThay = vaTam.daCo(lenh.khoaIdempotency);   // phien B: cung chua co
    if (!aThay) vaTam.lam(tk2, lenh);
    if (!bThay) vaTam.lam(tk2, lenh);
    check(tk2.soDu() == 800000, "van tru hai lan — khe ho giua hai loi goi la tien");
    // Bai hoc chung: MOI cap "hoi roi lam" tren trang thai chia se deu co khe ho nay.
    // `count` + `operator[]`, `SELECT` + `INSERT`, `exists()` + `create()` — cung mot bug.

    // ---- 3. CAI BAY RIENG CUA C++: `operator[]` CHEN khi doc ----
    std::map<std::string, BienLai> so;
    check(so.size() == 0, "so rong");
    if (so["KEY-9"].maGiaoDich.empty()) {            // chi DINH doc thoi...
        // ...nhung `operator[]` da CHEN mot ban ghi mac dinh vao map.
    }
    check(so.size() == 1, "chi doc ma so da co mot ban ghi — khong ai tao no ca");
    check(so.count("KEY-9") == 1, "va tu lan sau, 'khoa nay da dung chua' tra loi SAI");
    // Voi bai toan idempotency thi day la tham hoa: cau hoi "khoa nay da xu ly chua" tro
    // thanh "co" cho MOI khoa tung duoc hoi. Va so idempotency phinh len bang nhung ban
    // ghi rong, moi lan co ai do go sai mot khoa.
    //
    // Ba cach doc map an toan, khong bao gio chen:
    //     so.count(k) == 1        /  so.find(k) != so.end()   /  so.at(k)  (nem neu thieu)
    // `operator[]` chi dung khi BAN CO Y muon tao neu chua co.
    std::map<std::string, BienLai> soAnToan;
    check(soAnToan.find("KEY-9") == soAnToan.end() && soAnToan.empty(), "`find` khong chen gi");

    // ---- 4. BAN DUNG: GIANH CHO nguyen tu ----
    TaiKhoan tk3(1000000);
    DichVuIdempotent dv;
    LenhChuyenTien l1{"KEY-1", "TK-A", 100000};

    BienLai bl1 = dv.chuyen(tk3, l1);
    BienLai bl2 = dv.chuyen(tk3, l1);           // gui lai y het
    BienLai bl3 = dv.chuyen(tk3, l1);           // va lan nua
    check(tk3.soDu() == 900000, "tru DUNG MOT lan, du goi ba lan");
    check(dv.soLanThucSuTru == 1, "va chi mot lan di vao phan nghiep vu");
    check(bl1 == bl2 && bl2 == bl3, "ca ba lan tra ve CUNG MOT bien lai");
    // Chi tiet cuoi quan trong hon ve ngoai: idempotent khong phai la "lan sau thi bo
    // qua" ma la "lan sau tra lai DUNG KET QUA CU". Neu lan hai tra ve rong hay nem loi
    // "da xu ly", thi phia goi van khong biet ma giao dich — va no se thu lai.

    // ---- 5. CUNG KHOA, KHAC NOI DUNG: phai TU CHOI ----
    LenhChuyenTien lenhKhac{"KEY-1", "TK-A", 5000000};
    bool tuChoi = false;
    try { dv.chuyen(tk3, lenhKhac); } catch (const std::logic_error&) { tuChoi = true; }
    check(tuChoi, "cung khoa nhung so tien khac -> TU CHOI");
    check(tk3.soDu() == 900000, "va khong dung vao so du");
    // Neu cho nay tra ve bien lai cu (100.000) cho mot lenh 5 trieu, phia goi se tin rang
    // 5 trieu da chuyen xong. Hong nang hon tru tien hai lan: he thong vua NOI DOI. Vi
    // vay ban ghi idempotency phai luu VAN TAY cua noi dung, khong chi khoa.

    // ---- 6. AI SINH KHOA, VA SINH LUC NAO ----
    // Khoa phai do PHIA GOI sinh, TRUOC lan gui dau tien, va giu nguyen qua moi lan thu
    // lai. Ba cach sinh khoa SAI hay gap:
    //   - may chu sinh  -> moi request mot khoa moi, vo dung hoan toan;
    //   - bam noi dung  -> hai lan chuyen 100.000 CO Y cho cung nguoi bi gop lam mot;
    //   - thoi gian     -> thu lai o mili-giay khac la khoa khac.
    // Cach dung: UUID sinh o phia goi khi NGUOI DUNG bam nut, khong phai khi gui request.
    dv.chuyen(tk3, LenhChuyenTien{"KEY-2", "TK-A", 100000});
    dv.chuyen(tk3, LenhChuyenTien{"KEY-3", "TK-A", 100000});
    check(tk3.soDu() == 700000, "hai lenh CO Y giong nhau, hai khoa -> tru du hai lan");
    check(dv.soKhoa() == 3, "ba khoa da dung");
    // Day la ly do khong duoc bam noi dung lam khoa: he thong khong co cach nao tu phan
    // biet "gui lai" voi "co y lam hai lan" — chi phia goi biet.

    // ---- 7. PHEP TINH TUYET DOI THI TU NO DA IDEMPOTENT ----
    std::map<std::string, long> soDuBang{{"TK-A", 500000}};
    soDuBang["TK-A"] = 400000;
    soDuBang["TK-A"] = 400000;                  // lam lai y het
    check(soDuBang["TK-A"] == 400000, "GAN gia tri: chay bao nhieu lan cung the");

    long tuongDoi = 500000;
    tuongDoi -= 100000;
    tuongDoi -= 100000;                         // lam lai y het
    check(tuongDoi == 300000, "CONG TRU: moi lan chay lai la mot lan sai them");
    // Nguyen tac thiet ke: khi duoc chon, hay thiet ke lenh theo dang TUYET DOI ("dat
    // trang thai = DA GIAO") thay vi TUONG DOI ("tang so luong len 1"). Lenh tuyet doi
    // idempotent mien phi, khong can so khoa, khong can don dep.

    // ---- 8. SO KHOA PHAI CO HAN, VA PHAI CO PHAM VI ----
    //   - HAN: khoa giu mai thi so lon vo han. Thuong giu 24-72 gio — du dai hon moi
    //     lich thu lai, du ngan de so khong phinh. Sau khi het han, cung khoa do duoc
    //     coi la lenh moi; do la danh doi CO Y, phai noi ra trong tai lieu API.
    //   - PHAM VI: khoa phai kem dinh danh nguoi goi. Neu khong, khach A doan duoc khoa
    //     cua khach B la chan duoc giao dich cua nguoi khac.
    // Trong he that, "gianh cho nguyen tu" chinh la RANG BUOC DUY NHAT cua CSDL:
    //     INSERT INTO so_idempotency(khoa, van_tay) VALUES (?, ?)
    // Insert trung thi CSDL bao loi khoa trung — do la `map::insert().second` o muc ben vung.
    check(dv.soKhoa() == 3, "so khoa la du lieu THAT, phai duoc thiet ke nhu moi bang khac");

    // ---- 9. Vi sao bai nay di lien sau bai 84 ----
    // Outbox, hang doi, co che thu lai cua HTTP — ca ba deu giao IT NHAT MOT LAN. "Dung
    // mot lan" khong ton tai tren mang: ben gui khong bao gio phan biet duoc "chua nhan"
    // voi "nhan roi ma mat phan hoi". Nen "dung mot lan" luon duoc lam bang: GIAO it nhat
    // mot lan + XU LY idempotent. Bai 84 lo nua dau, bai nay lo nua sau.

    std::cout << "OK\n";
    return 0;
}
