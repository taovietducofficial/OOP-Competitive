/*
 * Ngôn ngữ: C++
 * Công dụng: Bản C++ của cùng bài học — quy trình nhiều bước, mỗi bước có hành động bù
 * trừ. Ba con bug: bước 3 hỏng thì tiền khách kẹt lại; bước không-bù-trừ-được đặt sai
 * chỗ; và hành động bù trừ không idempotent nên thử lại là hoàn tiền hai lần.
 * Tại sao cần học: Java phải dựng một lớp `Saga` để giữ danh sách bước và chạy ngược,
 * Python có `ExitStack`. C++ thì saga TRONG MỘT TIẾN TRÌNH chính là RAII — một lớp bảo
 * vệ phạm vi gom các hành động bù trừ, và destructor chạy chúng theo thứ tự ĐẢO trên mọi
 * đường thoát, kể cả đường ngoại lệ. Không cần `try`, không cần `finally`, không ai quên
 * được. Nhưng chính điều đó dẫn tới giới hạn quan trọng nhất của bài: destructor KHÔNG
 * chạy khi tiến trình bị giết — nên RAII đủ cho saga trong bộ nhớ, và tuyệt đối không đủ
 * cho saga phân tán.
 */
#include <functional>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>

// =====================================================================
// Ba dich vu, ba aggregate, ba transaction ROI NHAU
// =====================================================================
struct Kho {
    int tonKho = 10, soLanTru = 0, soLanTra = 0;
    void tru(int sl) {
        if (tonKho < sl) throw std::logic_error("khong du ton kho");
        tonKho -= sl; ++soLanTru;
    }
    void tra(int sl) { tonKho += sl; ++soLanTra; }        // BU TRU cho `tru`
};

struct Vi {
    long soDu = 1000000L;
    int soLanTru = 0, soLanHoan = 0;
    void tru(long t) {
        if (soDu < t) throw std::logic_error("khong du so du");
        soDu -= t; ++soLanTru;
    }
    void hoan(long t) { soDu += t; ++soLanHoan; }         // BU TRU cho `tru`
};

struct VanChuyen {
    bool seHong = false;
    int soVanDon = 0;
    std::string taoVanDon() {
        if (seHong) throw std::logic_error("doi tac van chuyen het cho");
        return "VD-" + std::to_string(++soVanDon);
    }
};

struct HopThu {
    int soEmailDaGui = 0;
    void gui(const std::string&) { ++soEmailDaGui; }
    // KHONG co `thuHoiEmail()`. Do la toan bo van de cua phan 4.
};

// =====================================================================
// SAGA TRONG MOT TIEN TRINH = MOT LOP BAO VE PHAM VI (RAII)
// =====================================================================
class BuTruGuard {
public:
    void them(std::function<void()> f) { buTru_.push_back(std::move(f)); }
    void danhDauThanhCong() { thanhCong_ = true; }
    int soLanBuTru = 0;

    // Chay tren MOI duong thoat khoi pham vi — ke ca duong ngoai le.
    // KHONG bao gio duoc nem tu destructor (bai 74): neu mot hanh dong bu tru hong thi
    // ghi lai va di tiep, khong duoc lam sap chuong trinh giua luc don dep.
    ~BuTruGuard() {
        if (thanhCong_) return;
        for (auto it = buTru_.rbegin(); it != buTru_.rend(); ++it) {   // thu tu DAO
            try { (*it)(); ++soLanBuTru; }
            catch (...) { ++soLanBuTruHong; }
        }
    }
    int soLanBuTruHong = 0;

private:
    std::vector<std::function<void()>> buTru_;
    bool thanhCong_ = false;
};

// ---- Self-check ----
static void check(bool ok, const std::string& msg) {
    if (!ok) {
        std::cerr << "FAIL: " << msg << "\n";
        std::exit(1);
    }
}

int main() {
    // ---- 1. CON BUG: khong co bu tru -> tien cua khach ket lai ----
    Kho kho; Vi vi; VanChuyen vc;
    vc.seHong = true;                       // doi tac van chuyen het cho

    bool hong = false;
    try {
        kho.tru(2);                         // transaction 1: COMMIT
        vi.tru(500000L);                    // transaction 2: COMMIT
        vc.taoVanDon();                     // transaction 3: HONG
    } catch (const std::logic_error&) { hong = true; }

    check(hong, "dat hang that bai");
    check(kho.tonKho == 8, "nhung 2 san pham van bi giu trong kho");
    check(vi.soDu == 500000L, "va 500.000 cua khach da bi tru");
    // Khong co gi de "rollback": hai transaction dau da COMMIT xong tu lau. Day chinh la
    // he qua truc tiep cua luat o bai 83 — mot transaction sua mot aggregate. Luat do
    // dung, va cai gia cua no la bai toan nay.

    // ---- 2. SAGA BANG RAII: bu tru NGUOC CHIEU, tu dong, tren moi duong thoat ----
    Kho kho2; Vi vi2; VanChuyen vc2;
    vc2.seHong = true;
    int soBuTru = 0, soBuTruHong = 0;
    bool sagaHong = false;
    {
        BuTruGuard g;
        try {
            kho2.tru(2);
            g.them([&] { kho2.tra(2); });          // dang ky bu tru NGAY SAU khi lam

            vi2.tru(500000L);
            g.them([&] { vi2.hoan(500000L); });

            vc2.taoVanDon();                       // <- nem ngoai le tai day
            g.danhDauThanhCong();                  // khong bao gio toi dong nay
        } catch (const std::logic_error&) { sagaHong = true; }
        // Ra khoi khoi lenh -> destructor cua `g` chay -> bu tru theo thu tu DAO.
        soBuTru = g.soLanBuTru;                    // doc truoc khi g bi huy? Xem ghi chu duoi.
        soBuTruHong = g.soLanBuTruHong;
    }
    // Ghi chu: hai dong doc `g.soLanBuTru` o tren chay TRUOC destructor, nen chung con la
    // 0. Do la dieu can biet ve RAII: ban khong quan sat duoc ket qua don dep tu ben
    // trong pham vi. Muon dem thi phai de bien dem O NGOAI:
    check(soBuTru == 0 && soBuTruHong == 0, "khong doc duoc ket qua don dep tu ben trong pham vi");
    check(sagaHong, "saga that bai o buoc 3");
    check(kho2.tonKho == 10, "kho duoc TRA LAI: 10 nhu ban dau");
    check(vi2.soDu == 1000000L, "tien duoc HOAN: 1.000.000 nhu ban dau");
    check(kho2.soLanTra == 1 && vi2.soLanHoan == 1, "moi buoc da xong duoc bu dung mot lan");
    // Thu tu dao khong phai chuyen tham my. Neu buoc 2 phu thuoc buoc 1 (rat thuong), thi
    // bu tru buoc 1 truoc khi bu buoc 2 se de lai trang thai vo nghia o giua.

    // ---- 3. BU TRU KHONG PHAI ROLLBACK ----
    check(kho2.soLanTru == 1 && kho2.soLanTra == 1, "kho co HAI but toan, khong phai khong co gi");
    check(vi2.soLanTru == 1 && vi2.soLanHoan == 1, "vi cung vay: tru roi hoan");
    // Diem quan trong nhat va hay bi hieu sai nhat. Rollback XOA dau vet nhu chua tung
    // xay ra. Bu tru thi KHONG: no ghi them mot su that nghiep vu MOI.
    //
    // Voi so ke toan, do la but toan dao — va no PHAI hien tren sao ke cua khach:
    //   -500.000  thanh toan don DH-01
    //   +500.000  hoan tien don DH-01 (khong tao duoc van don)
    // Chu khong phai mot dong trong. Khach da nhin thay so du bi tru; giau but toan hoan
    // di la lam sao ke noi doi.

    // ---- 4. CON BUG: buoc KHONG BU TRU DUOC dat sai cho ----
    Kho kho3; Vi vi3; VanChuyen vc3; HopThu ht3;
    vc3.seHong = true;
    bool hong3 = false;
    {
        BuTruGuard g;
        try {
            kho3.tru(2);
            g.them([&] { kho3.tra(2); });

            ht3.gui("don cua ban da duoc xac nhan");   // <- KHONG dang ky bu tru duoc
            // g.them(...) — khong ton tai `thuHoiEmail()`

            vi3.tru(500000L);
            g.them([&] { vi3.hoan(500000L); });

            vc3.taoVanDon();
            g.danhDauThanhCong();
        } catch (const std::logic_error&) { hong3 = true; }
    }
    check(hong3 && kho3.tonKho == 10 && vi3.soDu == 1000000L, "kho va tien deu duoc bu tru");
    check(ht3.soEmailDaGui == 1, "NHUNG email da bay di va khong thu ve duoc");
    // Day la bai 84 phan 3 quay lai o quy mo quy trinh. Luat rut ra rat don gian va rat
    // dat neu quen:
    //
    //   XEP MOI BUOC KHONG BU TRU DUOC XUONG CUOI SAGA.
    //
    // Gui email, gui SMS, goi API ben thu ba khong co ham huy, in phieu — tat ca di sau
    // cung, sau khi moi buoc co the hong da xong.
    Kho kho4; Vi vi4; VanChuyen vc4; HopThu ht4;
    {
        BuTruGuard g;
        kho4.tru(2);       g.them([&] { kho4.tra(2); });
        vi4.tru(500000L);  g.them([&] { vi4.hoan(500000L); });
        vc4.taoVanDon();
        ht4.gui("don cua ban da duoc xac nhan");      // <- CUOI CUNG
        g.danhDauThanhCong();
    }
    check(kho4.tonKho == 8 && vi4.soDu == 500000L && ht4.soEmailDaGui == 1,
          "duong thuan loi: khong bu tru gi, va email gui dung mot lan");
    check(kho4.soLanTra == 0, "danh dau thanh cong -> destructor khong lam gi ca");

    // ---- 5. HANH DONG BU TRU PHAI IDEMPOTENT ----
    Vi viBu;
    viBu.tru(500000L);
    viBu.hoan(500000L);
    viBu.hoan(500000L);                    // goi lai do thu lai
    check(viBu.soDu == 1500000L, "hoan hai lan -> khach duoc them 500.000 tu tren troi");
    check(viBu.soLanHoan == 2, "vi `hoan` la phep TUONG DOI");
    // Cach chua la bai 91: moi hanh dong bu tru mang mot khoa idempotency (thuong la ma
    // saga + so thu tu buoc), va dich vu dich bo qua lan goi trung.

    // ---- 6. GIOI HAN CUA RAII: DESTRUCTOR KHONG CHAY KHI TIEN TRINH BI GIET ----
    // `BuTruGuard` xu ly hoan hao: ngoai le, `return` som, `break` — moi duong thoat khoi
    // pham vi. Nhung co ba thu no KHONG cuu duoc:
    //   - `std::terminate` / `std::abort` / mot ngoai le thoat khoi destructor khac;
    //   - tien trinh bi `kill -9`, may chu mat dien, container bi thu hoi;
    //   - buoc thu hai nam o MOT MAY KHAC, va may do khong biet may nay vua chet.
    // Ca ba deu de lai trang thai nua voi ma khong ai don.
    //
    // Vi vay: RAII du cho saga TRONG MOT TIEN TRINH (mo file, giu khoa, don bo nho). Voi
    // saga phan tan, trang thai saga phai duoc LUU sau MOI buoc, va co mot tien trinh
    // rieng quet nhung saga dang do de tiep tuc hoac bu tru:
    //   maSaga | buocHienTai | trangThai
    //   SG-01  | 2           | DANG_CHAY
    //   SG-02  | 3           | DANG_BU_TRU
    // Va vi no la entity co trang thai, no cung can khoa lac quan (bai 92) — hai tien
    // trinh cung tiep tuc mot saga la chuyen co that.
    check(true, "RAII: dung cho trong tien trinh, khong du cho phan tan");

    // ---- 7. DIEU PHOI hay HOP XUONG ----
    //
    //   Cach      | Ai biet quy trinh              | Thay duoc quy trinh?  | Ghep chat?
    //   ----------|--------------------------------|-----------------------|------------
    //   DIEU PHOI | MOT object saga (bai nay)      | CO — doc mot file     | trung tam biet moi buoc
    //   HOP XUONG | rai trong nguoi nghe su kien   | KHONG — lan theo 5 DV | long hon
    //
    // Quy tac thuc dung: quy trinh co BU TRU thi dung dieu phoi — vi "chay toi dau, bu toi
    // do" can mot cho biet thu tu. Con he qua phu doc lap (cong diem, gui thong bao, ghi
    // thong ke) thi dung hop xuong (bai 84).
    //
    // Dau hieu chon sai: phai mo 5 dich vu moi tra loi duoc cau "don hang nay dang o buoc
    // nao" — do la hop xuong dung cho viec cua dieu phoi.

    // ---- 8. SAGA KHONG PHAI TRANSACTION ----
    // Ba tinh chat bi mat, phai noi ra voi nghiep vu TRUOC khi lam:
    //   - Khong co lap: giua buoc 1 va buoc 3, nguoi khac NHIN THAY trang thai nua voi.
    //     Neu dieu do khong chap nhan duoc thi cum nay phai la MOT aggregate (bai 83).
    //   - Khong nguyen tu tuc thoi: co mot khoang thoi gian he thong o trang thai trung
    //     gian. Do la nhat quan CUOI, va do tre cua no la con so phai do.
    //   - Bu tru co the HONG. Luc do can hang doi thu chet va con nguoi xu ly tay — mot
    //     saga khong co duong thoat cho truong hop nay la mot saga chua xong.
    check(kho2.tonKho == 10 && vi2.soDu == 1000000L, "cuoi cung thi nhat quan — CUOI cung");

    std::cout << "OK\n";
    return 0;
}
