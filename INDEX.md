# Tầng 04 — Competitive · Kiến trúc nâng cao (bài 81–100)

**SOLID đầy đủ + mô hình miền (domain model). Đủ 20/20 bài.**

Mỗi bài là một thư mục gồm `README.md` (đề bài + ý tưởng + độ phức tạp + lời giải + thực tế đi làm)
và ba lời giải Java / C++ / Python, mỗi file có **self-check bằng `assert`** ở cuối — chạy là biết
đúng/sai.

Điểm khác so với tầng 03: mỗi bài ở đây **cho nổ một con bug đo được bằng tiền hoặc bằng dữ liệu
mất đi**. Không dừng ở "thiết kế này tốt hơn" — mà là *300.000đ doanh thu không tồn tại*, *hai khách
hàng gộp làm một*, *email gửi cho đơn hàng không tồn tại*, *1.000 lượt truy vấn cho một màn hình*.

Và điểm khác quan trọng hơn: **20 bài này không phải 20 mẫu thiết kế rời rạc.** Chúng khoá vào nhau
thành một chuỗi lý do — xem [bảng ở cuối trang](#chuỗi-lý-do-vì-sao-20-bài-này-là-một-thiết-kế).

## Ngôn ngữ & mô hình (81–85)

| # | Bài | Học được gì | Java | C++ | Python |
|---|---|---|---|---|---|
| 81 | [Ubiquitous Language](81-ubiquitous-language/) | `status >= 3` cộng nhầm đơn **đã huỷ** → dôi 300.000đ | [java](81-ubiquitous-language/LanguageDemo.java) | [cpp](81-ubiquitous-language/LanguageDemo.cpp) | [py](81-ubiquitous-language/language_demo.py) |
| 82 | [Entity vs Value Object](82-entity-value-object/) | Câu hỏi **không có câu trả lời chung** — tuỳ ngữ cảnh | [java](82-entity-value-object/EntityValueDemo.java) | [cpp](82-entity-value-object/EntityValueDemo.cpp) | [py](82-entity-value-object/entity_value_demo.py) |
| 83 | [Aggregate Boundary](83-aggregate-boundary/) | Cụm đó **to đến đâu** — quá nhỏ mất bất biến, quá to đụng độ giả | [java](83-aggregate-boundary/BoundaryDemo.java) | [cpp](83-aggregate-boundary/BoundaryDemo.cpp) | [py](83-aggregate-boundary/boundary_demo.py) |
| 84 | [Domain Event](84-domain-event/) | Khách nhận email về **một đơn hàng không tồn tại** | [java](84-domain-event/EventDemo.java) | [cpp](84-domain-event/EventDemo.cpp) | [py](84-domain-event/event_demo.py) |
| 85 | [Repository & Unit of Work](85-repository-unit-of-work/) | 50.000đ bay hơi vì **một dòng code không tồn tại** | [java](85-repository-unit-of-work/UnitOfWorkDemo.java) | [cpp](85-repository-unit-of-work/UnitOfWorkDemo.cpp) | [py](85-repository-unit-of-work/unit_of_work_demo.py) |

## Luật nghiệp vụ ở đúng chỗ (86–90)

| # | Bài | Học được gì | Java | C++ | Python |
|---|---|---|---|---|---|
| 86 | [Domain Service](86-domain-service/) | Ba câu hỏi lọc + **phép đo mô hình thiếu máu** | [java](86-domain-service/DomainServiceDemo.java) | [cpp](86-domain-service/DomainServiceDemo.cpp) | [py](86-domain-service/domain_service_demo.py) |
| 87 | [Specification](87-specification/) | Cùng một luật, ba nơi, **ba kết quả khác nhau** | [java](87-specification/SpecificationDemo.java) | [cpp](87-specification/SpecificationDemo.cpp) | [py](87-specification/specification_demo.py) |
| 88 | [Policy Object](88-policy-object/) | Báo cáo thu **0% thuế Đức**, lệch 19 triệu mỗi đơn | [java](88-policy-object/PolicyDemo.java) | [cpp](88-policy-object/PolicyDemo.cpp) | [py](88-policy-object/policy_demo.py) |
| 89 | [State Pattern](89-state-pattern/) | Đơn **đã huỷ vẫn được giao** — vì một dòng KHÔNG có ở đó | [java](89-state-pattern/StateDemo.java) | [cpp](89-state-pattern/StateDemo.cpp) | [py](89-state-pattern/state_demo.py) |
| 90 | [Money & Currency](90-money-currency/) | 100đ chia cho 3 người, **mất 1 xu** | [java](90-money-currency/MoneyDemo.java) | [cpp](90-money-currency/MoneyDemo.cpp) | [py](90-money-currency/money_demo.py) |

## Nhất quán trong thế giới thật (91–95)

| # | Bài | Học được gì | Java | C++ | Python |
|---|---|---|---|---|---|
| 91 | [Idempotency](91-idempotency/) | Mạng đứt **một lần**, khách bị trừ tiền **hai lần** | [java](91-idempotency/IdempotencyDemo.java) | [cpp](91-idempotency/IdempotencyDemo.cpp) | [py](91-idempotency/idempotency_demo.py) |
| 92 | [Optimistic Concurrency](92-optimistic-concurrency/) | An sửa tên, Bình sửa hạn mức, và **tên biến mất** | [java](92-optimistic-concurrency/ConcurrencyDemo.java) | [cpp](92-optimistic-concurrency/ConcurrencyDemo.cpp) | [py](92-optimistic-concurrency/concurrency_demo.py) |
| 93 | [Bounded Context](93-bounded-context/) | Cùng chữ "hoàn tất", hai đội hiểu hai kiểu — **cả hai đều đúng** | [java](93-bounded-context/ContextDemo.java) | [cpp](93-bounded-context/ContextDemo.cpp) | [py](93-bounded-context/context_demo.py) |
| 94 | [Anti-Corruption Layer](94-anti-corruption-layer/) | Mô hình xấu của đối tác **dừng lại ở biên** | [java](94-anti-corruption-layer/AclDemo.java) | [cpp](94-anti-corruption-layer/AclDemo.cpp) | [py](94-anti-corruption-layer/acl_demo.py) |
| 95 | [CQRS-lite](95-cqrs-lite/) | **1.000 lượt truy vấn** để hiện 500 dòng | [java](95-cqrs-lite/CqrsDemo.java) | [cpp](95-cqrs-lite/CqrsDemo.cpp) | [py](95-cqrs-lite/cqrs_demo.py) |

## Kiến trúc & thu hoạch (96–100)

| # | Bài | Học được gì | Java | C++ | Python |
|---|---|---|---|---|---|
| 96 | [Event Sourcing](96-event-sourcing/) | *"Vì sao số dư là 493.000?"* — *"vì nó bằng 493.000"* | [java](96-event-sourcing/EventSourcingDemo.java) | [cpp](96-event-sourcing/EventSourcingDemo.cpp) | [py](96-event-sourcing/event_sourcing_demo.py) |
| 97 | [Saga](97-saga/) | Bước 3 hỏng, và 500.000đ của khách **kẹt lại vĩnh viễn** | [java](97-saga/SagaDemo.java) | [cpp](97-saga/SagaDemo.cpp) | [py](97-saga/saga_demo.py) |
| 98 | [Hexagonal](98-hexagonal/) | Lục giác **không phải một cấu trúc thư mục** | [java](98-hexagonal/HexagonalDemo.java) | [cpp](98-hexagonal/HexagonalDemo.cpp) | [py](98-hexagonal/hexagonal_demo.py) |
| 99 | [Testing the Domain](99-testing-the-domain/) | Test miền cần framework → **thứ bạn test không phải miền** | [java](99-testing-the-domain/DomainTestDemo.java) | [cpp](99-testing-the-domain/DomainTestDemo.cpp) | [py](99-testing-the-domain/domain_test_demo.py) |
| 100 | [Capstone — Order System](100-capstone-order-system/) | Ghép cả 20 bài thành **một** hệ chạy được | [java](100-capstone-order-system/OrderSystemDemo.java) | [cpp](100-capstone-order-system/OrderSystemDemo.cpp) | [py](100-capstone-order-system/order_system_demo.py) |

## Chuỗi lý do: vì sao 20 bài này là MỘT thiết kế

Không có bài nào là một lựa chọn độc lập. Chúng khoá vào nhau:

| Vì | Nên |
|---|---|
| Có bất biến *"tổng ≤ hạn mức"* | phải có **ranh giới aggregate** ([83](83-aggregate-boundary/)) |
| Ranh giới → tham chiếu **bằng id** | hai aggregate không nói trực tiếp với nhau |
| Không nói trực tiếp | phải có **sự kiện miền** ([84](84-domain-event/)) |
| Sự kiện giao *ít nhất một lần* | người nghe phải **idempotent** ([91](91-idempotency/)) |
| Một transaction một aggregate | quy trình nhiều bước cần **saga** ([97](97-saga/)) |
| Nhiều người cùng sửa | cần **khoá lạc quan** ([92](92-optimistic-concurrency/)) |
| Aggregate phải tải trọn vẹn | màn hình danh sách cần **CQRS** ([95](95-cqrs-lite/)) |
| Luật đổi theo ngữ cảnh | **policy**, không phải `if-else` rải rác ([88](88-policy-object/)) |
| Luật cần giải thích + dịch sang SQL | **specification** ([87](87-specification/)) |
| Miền phải test được không CSDL | **cổng & bộ nối** ([98](98-hexagonal/)) |
| Test không CSDL | test miền chỉ là **hàm + assert** ([99](99-testing-the-domain/)) |

**Rút một mắt xích ra thì mắt kế bên mất lý do tồn tại.** Đó là lý do "áp dụng DDD một nửa" thường
tệ hơn không áp dụng.

## Điều mỗi ngôn ngữ làm được mà hai ngôn ngữ kia không

Tầng này cố tình không dịch máy móc giữa ba bản. Mỗi bản khai thác thứ ngôn ngữ đó có riêng:

| Ngôn ngữ | Công cụ | Xuất hiện ở bài |
|---|---|---|
| **Java** | `sealed` + switch vét cạn — thêm loại mới mà quên xử lý là **lỗi biên dịch** | [84](84-domain-event/), [96](96-event-sourcing/), [100](100-capstone-order-system/) |
| | Reflection để dựng **bài test kiến trúc** chạy trong CI | [81](81-ubiquitous-language/), [86](86-domain-service/), [94](94-anti-corruption-layer/), [98](98-hexagonal/) |
| **C++** | Kiểu ép luật **lúc biên dịch**: `enum class`, `Tien<VND>`, `= delete` copy, `const&` | [81](81-ubiquitous-language/), [82](82-entity-value-object/), [83](83-aggregate-boundary/), [90](90-money-currency/) |
| | `static_assert` + `constexpr` — máy trạng thái hỏng **không dịch được** | [89](89-state-pattern/), [96](96-event-sourcing/), [99](99-testing-the-domain/) |
| | RAII — rollback và bù trừ là **hành vi mặc định** | [85](85-repository-unit-of-work/), [97](97-saga/) |
| | Bài toán **ABA**, cache locality, quyền sở hữu bộ nhớ ở biên | [92](92-optimistic-concurrency/), [94](94-anti-corruption-layer/), [95](95-cqrs-lite/) |
| **Python** | `Protocol` — bộ nối thoả cổng mà **không cần biết cổng tồn tại** | [98](98-hexagonal/), [100](100-capstone-order-system/) |
| | Đọc đồ thị object / lớp lúc chạy để **kiểm kiến trúc** | [83](83-aggregate-boundary/), [86](86-domain-service/), [89](89-state-pattern/) |
| | Và những cái bẫy chỉ Python mới có: `and` không nạp chồng được, `defaultdict` tạo khoá khi đọc, `__exit__` trả `True` nuốt ngoại lệ, `match` im lặng, `assert` biến mất dưới `-O` | [87](87-specification/), [91](91-idempotency/), [85](85-repository-unit-of-work/), [84](84-domain-event/), [99](99-testing-the-domain/) |

## Ba công cụ đo dùng lại được

| Công cụ | Ở bài | Trả lời câu hỏi |
|---|---|---|
| Bài test **ngôn ngữ chung** — mọi từ nghiệp vụ có trong API, và API không chứa `flag`/`data`/`tmp` | [81](81-ubiquitous-language/) | *"Mô hình còn nói tiếng nghiệp vụ không?"* |
| Bài test **chiều phụ thuộc** — duyệt đồ thị object / reflection / `grep` trên `import` | [83](83-aggregate-boundary/), [94](94-anti-corruption-layer/), [98](98-hexagonal/) | *"Kiến trúc còn nguyên không?"* |
| Phép đo **mô hình thiếu máu** — tỉ lệ getter/setter, tỉ lệ field tuỳ chọn | [86](86-domain-service/), [93](93-bounded-context/) | *"Entity còn là entity không?"* |

## Chạy self-check

```bash
cd 81-ubiquitous-language
javac LanguageDemo.java && java LanguageDemo         # in "OK"
g++ -std=c++17 -o sol LanguageDemo.cpp && ./sol      # in "OK"
python language_demo.py                              # in "OK"  (KHÔNG dùng -O — xem bài 99)
```

Không in `AssertionError` / `FAIL:` và in ra `OK` = đạt.
