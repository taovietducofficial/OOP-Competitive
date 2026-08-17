/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — hai người sửa một bản ghi, số hiệu phiên bản
 * phát hiện đụng độ, thử lại "mù" vẫn mất dữ liệu, và vòng lặp đọc-lại/áp-lại/ghi.
 * Tại sao cần học: C++ cho thấy số hiệu phiên bản không phải một mẹo của CSDL — nó là
 * lời giải cho một bài toán có tên riêng và nổi tiếng trong lập trình không khoá: BÀI
 * TOÁN ABA. `compare_exchange` chỉ so GIÁ TRỊ, nên nếu giá trị đi từ A sang B rồi quay
 * về A, phép so sánh vẫn khớp và bạn ghi đè lên hai thay đổi mà không hề biết. Đúng con
 * bug ở phần 1 của bài này, chỉ khác là ở mức CPU. Và cách chữa ở cả hai nơi giống hệt
 * nhau: gắn thêm một BỘ ĐẾM chỉ tăng, không bao giờ quay lại.
 */
#include <atomic>
#include <iostream>
#include <map>
#include <stdexcept>
#include <string>
#include <cstdlib>

struct BanGhi {
    std::string ten;
    long hanMuc;
    long phienBan;
};

// CSDL gia. `capNhat` mo phong dung `UPDATE ... WHERE ma=? AND phien_ban=?`.
class Csdl {
public:
    void tao(const std::string& ma, BanGhi bg) { bang_[ma] = std::move(bg); }
    const BanGhi& doc(const std::string& ma) const { return bang_.at(ma); }

    // Ghi KHONG kiem phien ban — "ai ghi sau thi thang".
    void ghiDe(const std::string& ma, BanGhi moi) {
        bang_[ma] = std::move(moi);
        ++soLanGhiThanhCong;
    }

    // Ghi CO kiem phien ban. Tra ve so dong bi anh huong, y nhu driver CSDL that.
    int capNhat(const std::string& ma, const BanGhi& moi, long phienBanKyVong) {
        auto it = bang_.find(ma);
        if (it == bang_.end() || it->second.phienBan != phienBanKyVong) {
            ++soLanDungDo;
            return 0;                          // 0 dong -> co nguoi da sua truoc
        }
        it->second = BanGhi{moi.ten, moi.hanMuc, phienBanKyVong + 1};
        ++soLanGhiThanhCong;
        return 1;
    }

    int soLanGhiThanhCong = 0, soLanDungDo = 0;

private:
    std::map<std::string, BanGhi> bang_;
};

// Gia tri co gan BO DEM — chia khoa cho bai toan ABA o phan 7.
struct CoPhienBan {
    long giaTri;
    long phienBan;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CON BUG: ai ghi sau thi thang, va nguoi truoc mat trang ----
    Csdl db;
    db.tao("KH-01", BanGhi{"Nguyen Van A", 10000000L, 1});

    // Hai nhan vien mo cung mot ho so khach hang luc 9h00.
    BanGhi cuaAn = db.doc("KH-01");          // An doc: han muc 10 trieu
    BanGhi cuaBinh = db.doc("KH-01");        // Binh doc: han muc 10 trieu

    // An sua TEN (khach doi ten dem), Binh sua HAN MUC (duyet nang han).
    db.ghiDe("KH-01", BanGhi{"Nguyen Van An", cuaAn.hanMuc, 1});
    db.ghiDe("KH-01", BanGhi{cuaBinh.ten, 50000000L, 1});

    check(db.doc("KH-01").hanMuc == 50000000L, "han muc moi cua Binh: co");
    check(db.doc("KH-01").ten == "Nguyen Van A", "ten moi cua An: MAT");
    // An thay man hinh bao "luu thanh cong", dong may, ve nha. Khong ngoai le, khong
    // canh bao, va khong ai biet cho toi khi khach hang goi dien hoi.
    //
    // Day KHONG phai bai 85. O do hai lan tai nam trong mot use case, va ban do dinh
    // danh cuu duoc. O day la hai nguoi, hai may, hai transaction.

    // ---- 2. SO HIEU PHIEN BAN: phat hien duoc, va phat hien DUNG LUC ----
    Csdl db2;
    db2.tao("KH-01", BanGhi{"Nguyen Van A", 10000000L, 1});
    BanGhi anDoc = db2.doc("KH-01");         // phien ban 1
    BanGhi binhDoc = db2.doc("KH-01");       // phien ban 1

    check(db2.capNhat("KH-01", BanGhi{"Nguyen Van An", anDoc.hanMuc, 0}, anDoc.phienBan) == 1,
          "An ghi truoc: thanh cong, phien ban -> 2");
    check(db2.doc("KH-01").phienBan == 2, "phien ban tu tang cung lan ghi");
    check(db2.capNhat("KH-01", BanGhi{binhDoc.ten, 50000000L, 0}, binhDoc.phienBan) == 0,
          "Binh ghi sau voi phien ban 1 -> 0 DONG bi anh huong");
    check(db2.soLanDungDo == 1, "dung do duoc DEM, khong im lang");
    check(db2.doc("KH-01").ten == "Nguyen Van An", "va thay doi cua An con nguyen");
    // `UPDATE ... WHERE ma=? AND phien_ban=?` khong can khoa gi ca. CSDL tra ve so dong
    // bi anh huong, va `0` la cau tra loi "co nguoi da sua truoc ban".

    // ---- 3. CON BUG: THU LAI "MU" ----
    // Phan xa dau tien khi gap `0 dong`: doc lai phien ban roi ghi lai. SAI.
    long phienBanMoi = db2.doc("KH-01").phienBan;
    db2.capNhat("KH-01", BanGhi{binhDoc.ten, 50000000L, 0}, phienBanMoi);
    check(db2.doc("KH-01").ten == "Nguyen Van A", "ten cua An lai MAT lan nua");
    // Binh chi lay phien ban MOI nhung van ghi bang du lieu CU (`binhDoc.ten`). Ket qua
    // y het phan 1 — chi cham hon vai mili-giay. So hieu phien ban khong tu sua gi; no
    // chi NOI cho ban biet phai doc lai.

    // ---- 4. BAN DUNG: doc lai, AP DUNG LAI thay doi, roi ghi ----
    Csdl db3;
    db3.tao("KH-01", BanGhi{"Nguyen Van A", 10000000L, 1});
    db3.capNhat("KH-01", BanGhi{"Nguyen Van An", 10000000L, 0}, 1);   // An xong

    const long hanMucBinhMuonDat = 50000000L;
    int soLanThu = 0;
    bool xong = false;
    while (!xong && soLanThu < 5) {
        ++soLanThu;
        BanGhi tuoi = db3.doc("KH-01");                            // DOC LAI du lieu moi nhat
        BanGhi sua{tuoi.ten, hanMucBinhMuonDat, 0};                // AP LAI y dinh cua Binh
        xong = db3.capNhat("KH-01", sua, tuoi.phienBan) == 1;
    }
    check(xong && soLanThu == 1, "doc lai roi ghi: thanh cong ngay lan dau");
    check(db3.doc("KH-01").ten == "Nguyen Van An", "ten cua An: GIU");
    check(db3.doc("KH-01").hanMuc == 50000000L, "han muc cua Binh: GIU");
    check(db3.doc("KH-01").phienBan == 3, "hai lan ghi, phien ban 1 -> 3");
    // Ba buoc, luon luon: DOC LAI -> AP LAI Y DINH -> GHI CO KIEM PHIEN BAN.
    // "Y dinh" o day la `hanMucBinhMuonDat`, khong phai ca ban ghi cu.

    // ---- 5. KHONG PHAI Y DINH NAO CUNG AP LAI DUOC ----
    // Thu lai tu dong chi dung khi y dinh KHONG phu thuoc vao du lieu da doc:
    //   "dat han muc = 50 trieu"    -> ap lai duoc (tuyet doi, bai 91 phan 7)
    //   "tang han muc them 10%"     -> ap lai duoc, vi tinh tren ban MOI doc
    //   "duyet vi han muc < 20tr"   -> KHONG: dieu kien duyet da dua tren so cu
    // Truong hop thu ba phai hoi lai nguoi dung. Tu dong thu lai o day la ra mot quyet
    // dinh nghiep vu ho con nguoi.
    Csdl db4;
    db4.tao("KH-01", BanGhi{"A", 10000000L, 1});
    bool duocDuyet = db4.doc("KH-01").hanMuc < 20000000L;          // dieu kien tren ban CU
    db4.capNhat("KH-01", BanGhi{"A", 90000000L, 0}, 1);            // nguoi khac nang len 90tr
    check(duocDuyet && db4.doc("KH-01").hanMuc == 90000000L,
          "quyet dinh 'duoc duyet' da loi thoi — thu lai tu dong se duyet sai");

    // ---- 6. PHIEN BAN DAT O DAU: DUNG MOT CAI, TREN AGGREGATE ROOT ----
    //   - MOI FIELD mot phien ban  -> An sua ten, Binh sua han muc: khong dung do. Nghe
    //     hay, nhung no pha BAT BIEN: hai nguoi sua hai field co the cung nhau tao ra
    //     trang thai vi pham ma khong ai vi pham rieng le. Day dung la ly do bai 83 ton tai.
    //   - PHIEN BAN TOAN CUC       -> moi nguoi dung do voi moi nguoi.
    //   - MOT phien ban tren ROOT  -> dung: don vi nhat quan = don vi dung do.
    // Noi tiep bai 83 phan 5: aggregate cang TO thi dung do gia cang nhieu. Khoa lac quan
    // khong cuu duoc ranh gioi ve sai — no lam hau qua lo ra som hon.
    check(db3.doc("KH-01").phienBan == 3, "mot so hieu cho ca cum, khong phai cho tung field");

    // ---- 7. DIEU CHI C++ NOI RO: BAI TOAN ABA ----
    // `compare_exchange` chi so GIA TRI. Neu gia tri di A -> B -> A, phep so van khop.
    std::atomic<long> o{10};
    long docDuoc = 10;                    // luong 1 doc gia tri 10
    o.store(20);                          // luong 2 doi thanh 20
    o.store(10);                          // luong 3 doi nguoc ve 10
    bool casKhop = o.compare_exchange_strong(docDuoc, 99);
    check(casKhop, "CAS THANH CONG du gia tri da doi HAI lan o giua");
    check(o.load() == 99, "va luong 1 vua ghi de len hai thay doi ma khong he biet");
    // Day chinh xac la con bug o phan 1, chi khac la o muc CPU. Voi con tro thi no con
    // nguy hiem hon: o nho duoc giai phong roi cap phat lai dung dia chi cu, CAS khop, va
    // ban vua ghi vao mot object khac hoan toan.

    // Cach chua: gan mot BO DEM chi tang. Gia tri co the quay lai, phien ban thi khong.
    std::atomic<CoPhienBan> oCoPb{CoPhienBan{10, 1}};
    check(oCoPb.is_lock_free() || true, "16 byte co the can khoa — do la cai gia phai tra");
    CoPhienBan docPb = oCoPb.load();                       // {10, 1}
    oCoPb.store(CoPhienBan{20, 2});                        // luong 2
    oCoPb.store(CoPhienBan{10, 3});                        // luong 3: gia tri ve 10, PHIEN BAN 3
    bool casPb = oCoPb.compare_exchange_strong(docPb, CoPhienBan{99, 2});
    check(!casPb, "CAS THAT BAI: phien ban 1 khong con nua");
    check(oCoPb.load().giaTri == 10 && oCoPb.load().phienBan == 3, "khong ghi de gi ca");
    // Doc lai hai khoi tren canh nhau: cot "gia tri" khong du de biet co ai da sua, cot
    // "phien ban" thi du. Do la TOAN BO ly do so hieu phien ban ton tai — o CSDL cung y
    // het nhu o CPU.

    // Va vong lap thu lai o muc CPU co hinh dang giong het phan 4:
    std::atomic<long> dem{10};
    int lan = 0;
    while (true) {
        ++lan;
        long cu = dem.load();                              // DOC LAI
        if (dem.compare_exchange_weak(cu, cu * 2)) break;   // AP LAI Y DINH roi GHI
    }
    check(dem.load() == 20 && lan == 1, "cung ba buoc, cung mot thuat toan");
    // Nhin ra hai thu nay la MOT giai thich duoc dieu hay bi hoi: "vi sao phai co vong
    // lap?" — vi that bai KHONG phai loi, no la thong tin. Va thong tin do chi dung duoc
    // neu ban quay lai doc.

    // ---- 8. LAC QUAN hay BI QUAN ----
    //
    //             | Khoa LAC QUAN (phien ban)          | Khoa BI QUAN (SELECT FOR UPDATE)
    //   ----------|-------------------------------------|----------------------------------
    //   gia dinh  | dung do HIEM                        | dung do THUONG
    //   chi phi   | 0 khi khong dung; thu lai khi dung  | giu khoa suot transaction
    //   rui ro    | thu lai nhieu lan / doi tai nguyen  | deadlock, cho, nghen co chai
    //   hop voi   | web, API, nguoi dung sua ho so      | tru kho, cap so phieu, hang doi
    //
    // Quy tac thuc dung: mac dinh LAC QUAN. Chi chuyen sang bi quan khi DO duoc rang ti
    // le dung do cao toi muc thu lai ton hon cho — va truoc do hay xem lai ranh gioi
    // aggregate (bai 83), vi dung do cao thuong la trieu chung cua ranh gioi qua to.
    check(db2.soLanDungDo + db3.soLanDungDo >= 1, "dung do la so lieu — hay do no");

    std::cout << "OK\n";
    return 0;
}
