# GoalZone — Giáo án cấp tốc trước buổi defense

**Dành cho:** Nguyen Phuc An (AnNP)

**Môn:** SWP391

**Phạm vi chính:** UC-34 đến UC-44 — Checkout, Payment, Invoice và Refund
**Nguồn chuẩn:** code trên nhánh `develop`, Flyway schema, backlog cuối và RDS/SDS code-first

---

## 0. Cách dùng giáo án này

Nếu có khoảng 4 giờ, học theo đúng thứ tự:

| Thời gian | Nội dung | Kết quả cần đạt |
|---|---|---|
| 00:00–00:20 | Pitch dự án và kiến trúc | Nói được GoalZone giải quyết gì và hệ thống chạy thế nào trong 60–90 giây |
| 00:20–00:50 | Nghiệp vụ và vai trò | Phân biệt đúng Customer, Staff, Admin và các trạng thái |
| 00:50–01:20 | Thiết kế dữ liệu và các module | Giải thích được vì sao chia entity/service như hiện tại |
| 01:20–02:30 | Cụm UC của AnNP | Nắm toàn bộ luồng checkout → payment → invoice → refund |
| 02:30–03:05 | Các module của thành viên khác | Có thể trả lời thay ở mức tổng quan và nối luồng |
| 03:05–03:40 | Câu hỏi “tại sao thiết kế như vậy?” | Trả lời được các câu phản biện quan trọng |
| 03:40–04:00 | Tập demo và xử lý sự cố | Demo có kịch bản, không bấm ngẫu hứng |

Nếu chỉ còn **30 phút**, học:

1. Phần 1 — bài nói 90 giây.
2. Phần 4 — công thức tiền.
3. Phần 5 — toàn bộ UC của AnNP.
4. Phần 8 — 15 câu hỏi trọng yếu.
5. Phần 10 — kịch bản demo.

---

# 1. Bài nói mở đầu 60–90 giây

> GoalZone là hệ thống đặt sân bóng và quản lý vận hành sân. Customer có thể tìm sân, chọn slot và dịch vụ, nhận ưu đãi, đặt sân, thanh toán PayPal Sandbox, theo dõi hóa đơn và yêu cầu hoàn tiền. Staff xử lý khách walk-in, check-in, dịch vụ, thanh toán tiền mặt, issue và refund. Admin quản lý cấu hình, tài khoản, sân, giá, slot, promotion, membership và báo cáo.
>
> Hệ thống dùng React cho giao diện, Spring Boot cung cấp REST API, Spring Security và JWT để xác thực, JPA kết nối PostgreSQL trên Supabase. Flyway quản lý phiên bản database. PayPal Orders v2 và Payments v2 xử lý thanh toán và hoàn tiền sandbox; SMTP dùng cho email. Tìm sân là logic deterministic trên dữ liệu GoalZone, không có assistant/LLM trong scope retake.
>
> Thiết kế theo code-first: code, migration và business rule thực thi được là nguồn sự thật; RDS, SDS, backlog và diagram được đồng bộ theo chúng. Với nghiệp vụ tiền, hệ thống tách Booking, Payment, Invoice và Refund để mỗi đối tượng có vòng đời, quyền truy cập và lịch sử kiểm toán riêng.

Ba câu phải nhớ:

- **Booking** mô tả giao dịch đặt sân và nghĩa vụ phải trả.
- **Payment** là từng dòng tiền đã thu hoặc thử thu.
- **Refund** là từng dòng tiền trả lại; **Invoice** tổng hợp ảnh tài chính hiện tại của booking.

---

# 2. Hiểu đúng bài toán nghiệp vụ

## 2.1 Hệ thống giải quyết vấn đề gì?

Nếu quản lý sân bằng tin nhắn hoặc bảng tính sẽ dễ gặp:

- Hai người cùng đặt một khung giờ.
- Giá thay đổi theo sân, ngày và giờ nhưng tính thủ công.
- Khó theo dõi tiền cọc, tiền còn lại, hoàn tiền và phí.
- Promotion và membership áp dụng không nhất quán.
- Staff và Admin không biết booking đang ở bước nào.
- Không có lịch sử rõ ràng khi khách khiếu nại.

GoalZone gom các nghiệp vụ đó thành một luồng có kiểm soát:

```text
Field + Price + Slot
        ↓
Search/Choose Slot
        ↓
Services + Promotion + Membership
        ↓
Booking + Checkout Summary
        ↓
Cash hoặc PayPal Sandbox
        ↓
Invoice + Payment History
        ↓
Check-in/Complete hoặc Cancel/Refund
```

## 2.2 Bốn vai trò

| Vai trò | Có thể làm gì | Không được làm gì |
|---|---|---|
| Guest | Xem sân, giá, slot; đăng ký/đăng nhập | Không tạo booking hoặc xem dữ liệu riêng |
| Customer | Đặt online, trả PayPal, xem booking/invoice/payment, hủy và request refund của mình | Không xem booking người khác; không tự approve refund |
| Staff | Tạo walk-in, nhận tiền mặt, check-in, complete/no-show, xử lý issue/refund | Không khởi tạo PayPal thay Customer; không quản trị hệ thống |
| Admin | Quản lý dữ liệu, policy, account, report; giám sát và xử lý nghiệp vụ | Không đóng vai người trả PayPal cho Customer |

**Câu trả lời defense:** Staff và Admin không có nút online payment vì thanh toán PayPal cần sự chấp thuận và tài khoản của chính người trả. Staff chỉ ghi nhận dòng tiền mặt của walk-in; Admin quản trị và giám sát chứ không giả danh payer.

## 2.3 Các trạng thái quan trọng

### Booking

```text
pending ──→ confirmed ──→ checked_in ──→ completed
   │             │             └──────→ (không quay lại)
   │             ├────────────→ no_show
   │             └────────────→ cancelled
   ├────────────→ cancelled
   └────────────→ expired
```

- Online booking bắt đầu ở `pending`.
- Khi số tiền đã trả đạt mức cọc yêu cầu, booking có thể thành `confirmed`.
- Walk-in do Staff tạo bắt đầu ở `pending`; nếu Staff thu đủ mức cọc hoặc thu đủ tiền ngay trong cùng giao dịch thì booking chuyển sang `confirmed`.
- Chỉ booking `pending` hoặc `confirmed` mới reschedule/edit services.
- Chỉ `confirmed` mới check-in.
- `completed`, `cancelled`, `expired`, `no_show` là trạng thái kết thúc. `rejected` chỉ được giữ để tương thích dữ liệu lịch sử; UI/API hiện tại không có hàng chờ duyệt thủ công.

### Payment

`pending → paid / failed / expired → partially_refunded / refunded`

Payment status không phải booking status. Một booking có thể confirmed dù mới trả cọc, và một booking có thể có nhiều payment do trả cọc, trả phần còn lại hoặc thử thanh toán lại.

### Refund

```text
requested ──→ approved ──→ processing ──→ completed
    │             │              └──────→ failed ──→ processing
    └────────────→ rejected
```

- Customer chỉ **request**.
- Venue Staff **approve hoặc reject**; Admin chỉ xem để audit.
- Với PayPal, hệ thống gọi provider rồi chỉ đánh `completed` khi PayPal trả trạng thái `COMPLETED`.
- Với cash, Venue Staff xác nhận đã hoàn tiền thực tế rồi đánh `completed`.

---

# 3. Kiến trúc và dữ liệu

## 3.1 Kiến trúc tổng quát

```text
React SPA
   ↓ Axios / JSON / JWT
Spring Security Filter
   ↓
REST Controller
   ↓
Business Service (@Transactional)
   ↓
Spring Data Repository / JPA
   ↓
Supabase PostgreSQL

External services:
- PayPal Sandbox
- SMTP mail
```

Ý nghĩa từng tầng:

- **Controller:** nhận HTTP request, không chứa nghiệp vụ lớn.
- **Service:** kiểm tra role/ownership, trạng thái, công thức và transaction.
- **Repository:** đọc/ghi entity.
- **Database:** khóa ngoại, unique, check constraint và trigger là lớp bảo vệ cuối.
- **Frontend:** hướng dẫn người dùng và ẩn thao tác không hợp lệ, nhưng không được xem là lớp bảo mật.

## 3.2 Vì sao dùng code-first?

“Code-first” ở dự án này không có nghĩa Hibernate tự ý tạo bảng. Quy trình đúng là:

1. Business rule được triển khai trong code.
2. Thay đổi schema đi qua Flyway migration có version.
3. Hibernate chạy chế độ `validate`, kiểm tra entity khớp schema chứ không tự sửa production.
4. Backlog, ERD, RDS và SDS mô tả lại hệ thống thực thi.

Ưu điểm:

- Có thể chạy test, không chỉ mô tả trên giấy.
- Schema thay đổi có lịch sử và tái tạo được.
- Tránh tình trạng diagram nói một kiểu, database chạy một kiểu.

## 3.3 Vì sao Supabase PostgreSQL thay vì Firebase?

Booking và payment là dữ liệu quan hệ, cần:

- Transaction.
- Foreign key.
- Unique constraint chống double booking.
- Check constraint cho tiền.
- Join và aggregate cho invoice/report.
- Row lock khi refund.

PostgreSQL phù hợp tự nhiên hơn document database. Supabase được dùng như PostgreSQL managed; backend kết nối JDBC và mọi quyền nghiệp vụ đi qua Spring Security.

## 3.4 Vì sao không dùng Supabase RLS?

Kiến trúc hiện tại không cho browser truy cập database trực tiếp. Mọi request đi qua backend, JWT và service ownership check. Vì vậy RLS sẽ lặp lại một authorization model thứ hai.

Câu trả lời cân bằng:

> Trong scope môn học, nhóm tập trung authorization ở Spring Security và service layer để có một nguồn policy. Production có thể bổ sung RLS như defense-in-depth nếu client cần truy cập Supabase trực tiếp, nhưng phải tránh để hai hệ quyền bị lệch.

## 3.5 Các entity không nên gộp

### `field_type` không gộp vào `football_field`

Một loại sân có thể dùng cho nhiều sân và mang thuộc tính chung như số người chơi. Tách ra giúp chuẩn hóa, quản lý loại sân một lần và lọc/report dễ hơn.

### `field_price` không gộp vào `football_field`

Một sân có nhiều mức giá theo:

- Ngày thường/cuối tuần.
- Giờ bắt đầu/kết thúc.
- Khoảng hiệu lực.

Nếu để một cột `price` trong field thì không biểu diễn được lịch sử và price band.

### `system_setting` không phải bảng dư

Nó lưu policy toàn hệ thống như:

- Tỷ lệ cọc.
- Timeout payment.
- Tỷ lệ refund trước 24 giờ/cùng ngày.
- Giờ mở/đóng cửa, độ dài slot, số ngày generate trước.
- Thời gian gửi reminder.

Những giá trị này có thể đổi trong vận hành mà không cần deploy code.

### `slot` được materialize thay vì tính ảo

Slot cần ID ổn định để booking tham chiếu, cần block/unblock cho sự kiện đặc biệt và cần constraint chống trùng. Hệ thống tự sinh slot theo rule; Admin không cần seed hoặc thêm tay từng slot.

### Booking, Payment, Invoice và Refund tách riêng

| Entity | Quan hệ | Lý do |
|---|---|---|
| Booking | Một slot, một customer; nhiều payment/refund/service | Giao dịch nghiệp vụ và tổng số phải trả |
| Payment | Nhiều dòng trên một booking | Cọc, trả đủ, trả còn lại, retry hoặc failed |
| Invoice | Một bản tổng hợp hiện tại cho một booking | Hiển thị subtotal, discount, paid, remaining, refund |
| Refund | Nhiều dòng trên booking/payment | Request, approval, provider status, partial refund |

Nếu gộp tất cả vào Booking sẽ mất lịch sử lần thanh toán, không biết PayPal capture nào cần refund và khó audit.

---

# 4. Mô hình tài chính phải thuộc lòng

## 4.1 Công thức checkout

```text
baseAmount = fieldPrice + serviceTotal
promotionDiscount = promotion hợp lệ, có giới hạn
membershipDiscount =
    phần trăm membership × (baseAmount - promotionDiscount)
    chỉ khi promotion cho phép stack và booking online

totalAmount =
    max(baseAmount - promotionDiscount - membershipDiscount, 0)

depositAmount = totalAmount × depositPercent
paidAmount = tổng gross payment đã thu
remainingAmount = max(totalAmount - paidAmount, 0)
```

Tất cả tiền dùng `BigDecimal` ở Java và `numeric(12,2)` trong PostgreSQL, làm tròn `HALF_UP`. Không dùng `double` vì sai số nhị phân.

## 4.2 Ví dụ tính tiền

Giả sử:

- Giá sân: **$20.00**
- Dịch vụ: **$6.00**
- Promotion: **$2.00**
- Membership: **5%**, được stack
- Cọc: **30%**

```text
Base                = 20.00 + 6.00 = 26.00
Sau promotion       = 26.00 - 2.00 = 24.00
Membership discount = 24.00 × 5%   = 1.20
Total               = 24.00 - 1.20 = 22.80
Deposit             = 22.80 × 30%  = 6.84
Remaining sau cọc   = 22.80 - 6.84 = 15.96
```

Promotion được trừ trước membership để không giảm giá chồng trên số tiền chưa thực trả. Nếu promotion `stackable = false`, membership discount bằng 0.

## 4.3 Gross, processor fee, net và refund

Giả sử Customer trả PayPal **$22.80**, provider trả:

```text
Gross collected = $22.80
Processor fee   = $1.02  (giá trị thật do PayPal trả về)
Provider net    = $21.78
```

Không cộng PayPal fee như “thuế” vào tiền khách phải trả:

- Processor fee là chi phí của merchant.
- Chỉ biết chính xác sau capture.
- Hệ thống lưu `providerFeeAmount` và `providerNetAmount` để report/audit.
- Dự án không triển khai VAT/sales tax vì chưa có yêu cầu pháp lý cụ thể.

## 4.4 Cancellation fee khác processor fee

- **Cancellation fee:** phần tiền giữ lại theo chính sách hủy.
- **Processor fee:** phí PayPal khấu trừ khi capture.

Hai khái niệm độc lập, không được gọi chung là “tax” hoặc “PayPal percentage”.

Ví dụ Customer đã trả $22.80:

- Hủy trước 24 giờ, policy 100%: refundable $22.80, cancellation fee $0.
- Hủy cùng ngày, policy 80%: refundable $18.24, cancellation fee $4.56.
- Sau thời điểm check-in: refundable $0 theo policy hiện tại.

## 4.5 Vì sao `paidAmount` không giảm khi refund?

`paidAmount` là **gross đã thu trong lịch sử**, không phải số tiền ròng đang giữ.

```text
Gross collected = sum(completed payments)
Refunded        = sum(completed refunds)
Net revenue     = gross collected - refunded - processor fees
```

Nếu giảm `paidAmount` sau refund sẽ xóa dấu vết “đã từng thu bao nhiêu” và report khó kiểm toán.

## 4.6 Reschedule hoặc edit service khi giá thay đổi

Hệ thống tính lại field price, service, promotion và membership:

```text
newRemaining  = max(newTotal - paidAmount, 0)
newRefundable = max(paidAmount - newTotal - completedRefunds, 0)
```

Ví dụ đã trả $22.80:

- Chuyển sang tổng mới $30.00 → còn phải trả $7.20.
- Chuyển sang tổng mới $18.00 → phát sinh quyền được request refund tối đa $4.80.

Hệ thống **không tự gọi PayPal refund ngay khi reschedule**. Lý do: refund là external side effect và cần quy trình request → approve → process để audit rõ ràng.

---

# 5. Cụm UC của AnNP — UC-34 đến UC-44

> Lưu ý: backlog cuối đã đánh lại số sau khi bỏ một UC cũ. Một số tên diagram hoặc comment trong code còn số cũ lớn hơn 1. Khi trình bày, dùng workbook backlog cuối làm chuẩn.

## UC-34 — Customer/Staff View checkout summary

**Mục tiêu:** Trước khi tạo booking/thanh toán, người dùng thấy rõ:

- Sân, ngày, giờ.
- Giá sân và dịch vụ.
- Promotion discount.
- Membership discount.
- Total, deposit, paid và remaining bằng USD.

**Điểm defense:**

- Preview chỉ là ước tính read-only.
- Khi tạo booking và payment, backend tính lại toàn bộ; không tin số tiền gửi từ frontend.
- Nhờ vậy sửa request trong DevTools cũng không thể tự giảm giá.

## UC-35 — Customer/Staff Choose payment option

| Actor | Booking source | Lựa chọn |
|---|---|---|
| Customer | online | Pay deposit hoặc pay full bằng PayPal Sandbox |
| Staff | walk_in | Ghi nhận cash deposit, full hoặc remaining |
| Admin | quản trị | Không có checkout cho Customer |

**Tại sao phân như vậy?** Customer tự phê duyệt giao dịch online. Staff thao tác nghiệp vụ tại quầy và ghi nhận cash. Admin không phải payer.

## UC-36 — Customer Pay online via PayPal Sandbox

Luồng:

1. Customer chọn deposit hoặc full.
2. Frontend yêu cầu backend tạo PayPal order.
3. Backend kiểm tra role, ownership, booking status và tự tính amount USD.
4. Backend gọi PayPal Orders v2 bằng secret ở server.
5. Frontend hiển thị PayPal SDK bằng public client ID.
6. Customer approve trong Sandbox.
7. Frontend gửi order ID về backend để capture.

**Bảo mật:**

- PayPal client ID có thể public.
- Client secret chỉ nằm ở environment backend.
- Customer không gửi “amount tùy ý” để backend tin.
- Staff/Admin không dùng endpoint này thay Customer.

## Bước nội bộ của UC-36 — Capture/confirm online payment

Sau khi approve:

1. Backend capture order.
2. Kiểm tra provider status là `COMPLETED`.
3. Kiểm tra currency là USD.
4. Kiểm tra captured amount bằng amount backend đang mong đợi.
5. Lưu order ID, capture ID, transaction code, gross, fee, net và status.
6. Cập nhật paid/remaining của booking.
7. Confirm booking khi đạt mức cọc.
8. Generate/update invoice và notification.

**Nếu request timeout sau khi PayPal đã capture:** backend dùng idempotency key ổn định và đọc lại trạng thái order, không tạo một charge mới mù quáng.

**Nếu double-click capture:** payment đã paid được trả lại idempotently; capture ID có unique constraint.

## UC-37 — Staff Confirm remaining payment

Staff mở booking walk-in hoặc booking cần thu phần còn lại:

- Chọn cash remaining.
- Backend tính đúng `remainingAmount`.
- Amount nhập phải bằng amount được phép.
- Lưu Payment và cập nhật invoice.

Không cho ghi một số cash tùy tiện lớn hơn/nhỏ hơn payable vì sẽ làm lệch invoice. Nếu cần partial cash linh hoạt trong tương lai thì phải bổ sung UC và accounting rule rõ ràng.

## UC-38 — Handle failed/expired payment

- Payment failed/expired vẫn tồn tại trong history để audit.
- Booking online giữ chỗ trong thời gian policy cho phép.
- Scheduler hết timeout sẽ chuyển unpaid hold thành `expired`.
- Booking hết active sẽ giải phóng slot cho người khác.

**Tại sao không xóa payment failed?** Xóa sẽ mất khả năng điều tra retry, lỗi provider và hành vi người dùng.

## UC-39 — View payment history

- Customer chỉ xem payment thuộc booking của mình.
- Staff/Admin xem được payment phục vụ vận hành.
- Hiển thị cash/PayPal, deposit/full/remaining, paid/failed/expired/refunded.
- Với PayPal có gross, provider fee và net nếu provider trả dữ liệu.

Security được kiểm tra ở service, không chỉ ẩn menu.

## Bước nội bộ của UC-40 — Generate/update booking invoice

Invoice tổng hợp:

- Field amount.
- Service amount.
- Promotion và membership discounts.
- Total, paid, remaining.
- Refund amount.

Invoice được tạo/cập nhật sau booking, payment, reschedule, service edit và refund. Đây là consolidated billing view, không phải hóa đơn thuế bất biến theo chuẩn kế toán quốc gia.

## UC-40 — View invoice/payment status

Customer xem billing của mình; Staff/Admin xem để hỗ trợ vận hành. Invoice liên kết với payment/refund history để giải thích:

- Tổng booking là bao nhiêu?
- Đã trả bao nhiêu?
- Còn bao nhiêu?
- Đã refund bao nhiêu?
- Dòng tiền nào qua cash hay PayPal?

## UC-41 — Process online refund

Với PayPal:

1. Refund đã được approve.
2. Hệ thống chọn Payment có capture ID và còn refund capacity.
3. Gọi PayPal Payments v2 refund theo capture ID.
4. Gửi idempotency key để retry an toàn.
5. Chỉ provider `COMPLETED` mới thành refund `completed`.
6. `PENDING` giữ ở `processing`; lỗi thành `failed` và có thể retry/check.
7. Cập nhật invoice và notification sau khi hoàn tất.

**Không phải refund “cho vui”:** trạng thái local không được đánh completed trước phản hồi authoritative của provider.

## UC-42 — Manage refund requests

- Customer owner tạo request với amount không vượt refundable capacity.
- Không thể có nhiều request đang giữ cùng một capacity rồi cộng lại vượt trần.
- Venue Staff approve/reject; Admin chỉ xem audit.
- PayPal refund được submit/retry qua provider.
- Cash refund phải được operator xác nhận đã trả thực tế.

**Separation of duties:** người yêu cầu không tự duyệt; external money movement có lịch sử actor và status.

## UC-43 — Admin Configure deposit rules

Admin cấu hình `deposit.default_percent`. Booking mới lấy policy hiện hành và lưu monetary snapshot.

**Tại sao không sửa ngược booking cũ?** Booking là hợp đồng tại thời điểm tạo; policy tương lai không được làm thay đổi nghĩa vụ lịch sử.

## UC-44 — Admin Configure cancellation/refund policy

Hiện có:

- Refund percent khi hủy trước 24 giờ.
- Refund percent khi hủy cùng ngày trước check-in.

Backend dùng policy để tạo cancellation preview, rồi mới thực hiện cancel.

**Preview không phải nút thừa:** nó là bước informed consent. UX đúng là “Review cancellation terms” → hiển thị số hoàn/phí → “Confirm cancellation”, không phải hai hành động ngang hàng không giải thích.

---

# 6. Luồng end-to-end AnNP phải kể được

## 6.1 Online deposit thành công

```text
Customer chọn slot
→ preview pricing
→ tạo online booking (pending)
→ chọn deposit
→ backend tạo PayPal order đúng USD
→ Customer approve
→ backend capture + verify amount/currency/status
→ Payment paid
→ Booking paidAmount tăng
→ đạt deposit nên Booking confirmed
→ Invoice update
→ Notification tạo
```

## 6.2 PayPal lỗi hoặc người dùng đóng popup

```text
Order/payment pending
→ cancel hoặc timeout
→ payment expired/failed
→ nếu booking chưa có tiền hợp lệ: booking expired
→ slot được phép dùng lại
```

## 6.3 Staff walk-in

```text
Staff chọn slot
→ tìm Customer bằng SĐT/email hoặc chọn khách lần đầu
→ nếu khách lần đầu: nhập tên + SĐT, email tùy chọn; không tạo account
→ tạo booking source=walk_in
→ nếu pay later: giữ pending và chưa tạo Payment
→ nếu nhận cash deposit/full: backend ghi Payment method=cash
→ đạt mức cọc thì booking confirmed
→ invoice update
→ staff check-in → complete
```

## 6.4 Customer hủy và refund PayPal

```text
Customer review cancellation terms
→ backend tính refundable + cancellation fee
→ Customer confirm cancel
→ Customer request refund
→ Venue Staff approve
→ Venue Staff submit PayPal refund
→ provider COMPLETED
→ Refund completed
→ Invoice refund amount update
→ Payment partial/full refunded
→ Notification
```

**Điểm rất dễ trả lời sai:** Cancel booking không đồng nghĩa tiền đã được hoàn. Cancel xác định booking status và entitlement; refund là quy trình dòng tiền riêng.

## 6.5 Edit services/reschedule

```text
Staff sửa trước check-in
→ validate service/slot mới
→ tính lại field + services + discounts
→ so newTotal với gross đã trả
→ cập nhật remaining hoặc refundable
→ update invoice
→ nếu cần hoàn: dùng refund workflow, không sửa payment history
```

---

# 7. Phần của các thành viên khác — đủ để trả lời thay

## 7.1 BonVT — Account và customer status, UC-01–10

Nắm các ý:

- Register, email verification, login/logout.
- Forgot/reset password.
- Customer tự đổi password; Admin không nên biết hoặc đặt lại password tùy tiện.
- Admin tạo Staff với initial credential/invitation; Staff tự quản password sau đó.
- JWT stateless, password BCrypt.
- `authVersion` giúp vô hiệu token cũ khi account/password/status thay đổi.
- `locked` là trạng thái chặn đăng nhập; “restricted” nên được hiểu là hành động/quyền hạn trong UI, không tạo một trạng thái mơ hồ song song nếu không có rule riêng.
- Khi account bị thay đổi trạng thái, hệ thống nên có notification/email theo flow được cấu hình.

Defense:

> Authentication trả lời “bạn là ai”; authorization trả lời “bạn được làm gì”. Route role check là lớp thô, service ownership là lớp chi tiết.

## 7.2 BaoNG — Field, Slot, Service và Issue, UC-11–23

- Admin CRUD field, type, price band, image URL/upload flow.
- Slot tự sinh theo opening time, closing time, duration và horizon.
- Block/unblock cho bảo trì, sự kiện đặc biệt hoặc VIP.
- Không thêm slot thủ công hằng ngày vì sẽ sai nghiệp vụ.
- Extra service có unit price, stock, maximum quantity per booking.
- Customer/Staff report issue; Staff xử lý; Admin xem lịch sử giám sát.

Chống double booking:

1. Service kiểm tra field active, slot không block/không quá giờ và không có active booking.
2. PostgreSQL có partial unique index: một slot chỉ có một booking active.
3. Cancelled/expired booking vẫn được giữ lịch sử nhưng không khóa slot.

## 7.3 NgocPA — Booking lifecycle, UC-24–33

- Online Customer và walk-in Staff là hai source.
- Create, detail, list/history.
- Reschedule.
- Cancel preview và cancel.
- Edit service trước check-in.
- Staff check-in, complete, no-show.
- Pending timeout/expire.
- Resolve slot conflict dùng chung kiểm tra và state machine, không tạo logic booking thứ hai.

Điểm quan trọng:

- Booking state machine không cho nhảy trạng thái tùy ý.
- Customer chỉ thao tác online booking của mình.
- Staff/Admin thao tác vận hành nhưng không thay Customer trả PayPal.

## 7.4 AnPTT — Promotion, Membership, Notification và Report, UC-45–56

- Promotion theo percent/fixed, min amount, max discount, ngày, khung giờ, field type, service hoặc membership.
- Một promotion được snapshot vào booking.
- `stackable=false` chặn membership discount.
- Membership progress dựa trên completed bookings.
- Mỗi customer có một current membership row; unique constraint ngăn tạo trùng. Khi level/progress đổi phải update/upsert row hiện có, không insert row thứ hai.
- Notification có persistent read/unread; email là kênh ngoài.
- Report tách gross, refund, fee và net.
- Availability search dùng slot/price thật trong database và revalidate ở checkout.

## 7.5 Phạm vi đã loại — Assistant và ranked suggestion

Hai mục cũ đã bị loại khỏi scope retake vì trùng với UC-13 Search available fields và làm tăng dependency/defense surface. Route, UI, controller, service, config và test liên quan đều đã xóa; không trình bày assistant như tính năng hiện hành.

---

# 8. Bộ câu hỏi phản biện và câu trả lời

## 8.1 Vì sao phải tách Booking và Payment?

Một booking có thể trả cọc, trả phần còn lại, có lần thất bại hoặc retry. Nếu để một payment field trong Booking sẽ mất lịch sử và không biểu diễn one-to-many. Booking là nghĩa vụ; Payment là sự kiện dòng tiền.

## 8.2 Vì sao Invoice one-to-one nhưng Payment one-to-many?

UI cần một bản billing summary hiện tại cho mỗi booking, còn payment là ledger nhiều giao dịch. Invoice tổng hợp, payment giữ chi tiết.

## 8.3 Vì sao Refund liên kết cả Booking và Payment?

Booking cho biết bối cảnh và refundable tổng. Payment cho biết dòng tiền/capture cụ thể mà provider phải hoàn. Hai liên kết cũng giúp kiểm tra refund không trỏ nhầm booking.

## 8.4 Nếu hai Customer book cùng slot trong cùng một mili-giây?

Service check giúp trả lỗi thân thiện. Dù cả hai cùng vượt qua check, partial unique index trong PostgreSQL chỉ cho một active booking; transaction còn lại thất bại và được chuyển thành conflict response. Database là lớp chống race condition cuối.

## 8.5 Vì sao partial unique index thay vì `slot_id UNIQUE`?

Cancelled/expired/rejected booking phải được giữ để audit và slot đó có thể được đặt lại. Constraint chỉ unique với trạng thái active, nên vừa giữ lịch sử vừa chống double booking hiện hành.

## 8.6 Vì sao kiểm tra business rule ở cả service và database?

Service cho thông báo có nghĩa và xử lý theo actor. Database chống race condition, bug mới hoặc thao tác ngoài ứng dụng. Hai lớp bổ sung nhau, không phải lặp vô ích.

## 8.7 Vì sao cần transaction?

Ví dụ capture thành công phải cập nhật Payment, Booking và Invoice như một đơn vị. Nếu cập nhật invoice lỗi thì local transaction rollback, tránh trạng thái nửa vời. Với provider bên ngoài không thể rollback PayPal, nên cần idempotency và reconciliation để phục hồi.

## 8.8 Vì sao không tin amount từ frontend?

Frontend nằm trên máy người dùng và có thể bị sửa. Backend phải tính từ field price, services, promotion, membership và policy đang lưu.

## 8.9 Idempotency là gì và dùng để làm gì?

Là cùng một request logic được retry nhưng không tạo giao dịch thứ hai. PayPal request có stable idempotency key; capture/refund ID có unique constraint và service nhận diện trạng thái đã hoàn thành.

## 8.10 Nếu PayPal đã capture nhưng mạng timeout trước khi backend nhận response?

Không charge lại ngay. Backend dùng order/request ID hiện có để đọc lại trạng thái provider. Nếu provider đã `COMPLETED`, hệ thống reconcile vào Payment.

## 8.11 Nếu refund bị retry nhiều lần?

Refund gắn idempotency key và provider refund ID. Hệ thống kiểm tra trạng thái cũ trước khi gửi lại; service và database cùng giới hạn refund capacity.

## 8.12 Làm sao tránh refund vượt số đã trả?

- Service tính available capacity trên booking và từng payment.
- Request đang `requested/approved/processing` cũng reserve capacity.
- Database trigger khóa booking row và kiểm tra tổng refund.
- Amount phải dương và không vượt refundable.

## 8.13 Tại sao Customer không tự approve refund?

Tách người yêu cầu và người phê duyệt để giảm gian lận/sai thao tác, đồng thời tạo audit trail. Đây là separation of duties.

## 8.14 Tại sao cancel không auto-refund?

Cancel là thay đổi vòng đời booking và tính entitlement. Refund là chuyển tiền thật qua provider hoặc cash, có thể cần review, retry và reconciliation. Tách hai bước an toàn hơn.

## 8.15 PayPal fee đã tính chưa?

Có ghi nhận actual fee và net do PayPal trả sau capture. Không tự đoán phần trăm và không cộng nó như thuế cho Customer. Revenue report có thể tách gross, refund, processor fee và net.

## 8.16 Vì sao không có VAT?

Backlog không quy định jurisdiction, tax rate hoặc hóa đơn thuế. Tự thêm vài phần trăm là nghiệp vụ sai. Scope hiện là booking invoice; production phải có tax module theo luật cụ thể.

## 8.17 Tại sao dùng `BigDecimal`?

`double` có sai số nhị phân như 0.1 + 0.2 không chính xác tuyệt đối. Tiền cần decimal, scale và rounding rule rõ ràng.

## 8.18 Vì sao lưu snapshot giá/discount trên Booking?

Giá sân, promotion và membership có thể đổi sau này. Booking cũ phải giữ hợp đồng tại thời điểm đặt, không được tự thay đổi theo master data mới.

## 8.19 Vì sao promotion được trừ trước membership?

Để có thứ tự discount xác định, tránh mỗi màn hình tính một kiểu. Membership áp dụng trên phần còn lại sau promotion khi stacking được cho phép.

## 8.20 Vì sao membership chỉ áp dụng online?

Membership thuộc account Customer xác thực. Staff có thể tìm đúng account bằng SĐT/email, nhưng walk-in khách vãng lai chỉ lưu tên + SĐT trên booking nên không nhận membership. Hệ thống không tự tạo account giả hoặc để Staff tự gán hạng.

## 8.21 Vì sao chỉ có một `customer_membership` row?

Hệ thống đang lưu **current membership state**, nên customer có tối đa một row và update/upsert khi level thay đổi. Nếu cần lịch sử tier, nên thêm bảng membership_history, không insert trùng current row.

## 8.22 Vì sao slot tự sinh nhưng vẫn lưu trong database?

Rule tạo lịch tự động, còn row slot phục vụ booking FK, block/unblock, search và concurrency constraint. “Tự sinh” không đồng nghĩa “tính ảo mỗi lần đọc”.

## 8.23 Vì sao không để Admin thêm từng slot?

Lịch bình thường lặp theo rule; thêm tay tốn công và dễ thiếu. Block/unblock giữ lại cho ngoại lệ. Admin chỉnh rule và hệ thống generate horizon.

## 8.24 Vì sao có `field_type` và `field_price` riêng?

Field type là classification dùng lại. Field price là one-to-many price bands theo ngày/giờ/thời gian hiệu lực. Gộp sẽ gây lặp dữ liệu hoặc không biểu diễn đủ.

## 8.25 Vì sao có `system_setting` thay vì hard-code?

Deposit, refund và slot generation là operational policy có thể thay đổi. Setting cho Admin đổi có kiểm soát mà không deploy code.

## 8.26 Vì sao không dùng RLS?

Client không truy cập Supabase trực tiếp; authorization tập trung ở Spring backend. RLS là cải tiến defense-in-depth cho production, không phải điều kiện để flow hiện tại đúng.

## 8.27 H2 test có đảm bảo PostgreSQL không?

H2 giúp unit/integration test chạy nhanh nhưng không mô phỏng hoàn toàn partial index, exclusion constraint hoặc trigger PostgreSQL. Vì vậy nhóm còn migrate và smoke-test trên Supabase thật.

## 8.28 Vì sao bỏ assistant khỏi scope retake?

Nó không giải quyết thêm mục tiêu cốt lõi so với tìm sân theo ngày/giờ, nhưng tăng dependency, UI và câu hỏi defense. UC-13 deterministic đã truy vấn live availability/price và checkout vẫn revalidate slot, nên bỏ assistant làm sản phẩm nhỏ hơn nhưng chuẩn hơn.

## 8.29 Nếu PayPal không cấu hình hoặc mất kết nối?

Backend config trả online checkout unavailable và không hiển thị flow giả. Staff vẫn có cash walk-in; Customer thử lại sau. Production nên bổ sung webhook và background reconciliation.

## 8.30 Có mock/demo payment không?

Release dùng PayPal Sandbox thật với `PAYPAL_MOCK_MODE=false`. Code có nhánh hỗ trợ test cô lập nhưng public checkout bị disable khi bật mock; không trình bày mock như tính năng người dùng.

## 8.31 Vì sao có vài enum payment method chưa dùng?

Schema còn một số giá trị legacy/extension như bank transfer, nhưng service allowlist hiện chỉ chấp nhận `cash` cho operator và `paypal_sandbox` cho Customer. Code thực thi là giới hạn thật; production có thể migration dọn enum sau khi chốt compatibility.

## 8.32 Tại sao report Overview không chỉ cộng invoice total?

Invoice total là nghĩa vụ booking, không chắc đã thu. Report tiền phải dựa trên completed payments, completed refunds và actual processor fee để phân biệt gross, refund, fee và net.

---

# 9. Những giới hạn nên thừa nhận thẳng

Defense tốt không phải nói hệ thống hoàn hảo. Có thể trả lời:

- PayPal đang ở **Sandbox**, chưa tuyên bố production live-money certified.
- Chưa có VAT/sales tax vì thiếu rule pháp lý cụ thể.
- Invoice là billing summary có thể cập nhật, chưa phải immutable fiscal invoice/versioned credit note.
- Authorization tập trung ở backend; chưa dùng Supabase RLS.
- H2 không kiểm tra hết đặc tính PostgreSQL, nên có bước verify trên Supabase.
- Provider flow hiện ưu tiên synchronous API và recovery; production nên thêm signed webhook và scheduled reconciliation.
- Một số enum/comment/tên diagram còn dấu vết numbering hoặc option cũ; backlog cuối và service allowlist là nguồn chuẩn.
- Inventory service dùng stock/maximum quantity ở scope đơn giản, chưa phải kho với ledger nhập/xuất.

Cách nói:

> Đây là giới hạn được kiểm soát theo scope, không phải logic bị bỏ quên. Nếu đưa lên production, bước tiếp theo của nhóm là webhook reconciliation, tax/fiscal invoice theo jurisdiction, RLS defense-in-depth và observability.

---

# 10. Kịch bản demo 12–15 phút

## Chuẩn bị trước demo

- Mở sẵn frontend, backend và Supabase connection.
- Kiểm tra PayPal config enabled và currency USD.
- Có sẵn một Customer, Staff, Admin.
- Có slot tương lai available; không chọn ngày đã qua.
- Có PayPal Sandbox buyer account.
- Không sửa deposit/refund policy ngay trước luồng payment trừ khi chủ động demo policy.

## Demo đề xuất

### 1. Customer — 4 phút

1. Đăng nhập Customer.
2. Find a slot, chọn field/date.
3. Chọn services và promotion.
4. Giải thích checkout breakdown và membership.
5. Tạo online booking.
6. Pay deposit/full bằng PayPal Sandbox.
7. Mở booking detail, invoice và payment history.

Nói:

> Số tiền không đến từ frontend. Backend tính lại và PayPal capture phải khớp amount/currency trước khi xác nhận.

### 2. Staff — 3 phút

1. Xem calendar/booking list.
2. Tạo walk-in hoặc mở booking phù hợp.
3. Ghi nhận cash payment/remaining.
4. Check-in rồi complete.

Nói:

> Staff chỉ xử lý cash walk-in; không có quyền khởi tạo PayPal thay Customer.

### 3. Cancel/refund — 3 phút

1. Customer review cancellation terms.
2. Confirm cancel.
3. Request refund.
4. Venue Staff approve; Admin chỉ xem audit.
5. Với PayPal: submit/retry/check provider; với cash: xác nhận hoàn thực tế.
6. Xem invoice/payment/refund status.

Nói:

> Cancel xác định entitlement; completed refund chỉ xuất hiện sau khi provider xác nhận hoặc operator ghi nhận cash đã trả.

### 4. Admin — 3 phút

1. Field/pricing và auto-slot rules.
2. Promotion/membership.
3. Deposit/refund policies.
4. Report gross/refund/fee/net.

### 5. Kết — 1 phút

> Điểm chính của hệ thống là consistency: role đúng, state transition đúng, tiền do server tính, external payment idempotent và database có constraint chống race condition.

## Nếu PayPal lỗi trong lúc demo

Không bấm retry loạn. Nói:

> Đây là external sandbox. Hệ thống giữ order/payment để audit và dùng order ID/idempotency key để reconcile, tránh double charge.

Sau đó chuyển sang payment history hoặc cash flow đã chuẩn bị. Đây là xử lý sự cố đúng nghiệp vụ, không phải né lỗi.

---

# 11. Cách đọc diagram và tài liệu khi bị hỏi

## Class diagram

Đừng đọc tất cả box. Kể theo chuỗi:

```text
Controller nhận use case
→ Service giữ business rule
→ Repository lưu entity
→ Entity thể hiện dữ liệu và quan hệ
```

Một use case có nhiều bảng Class Specifications là bình thường nếu diagram chứa nhiều class tham gia. Specification phải mô tả các method quan trọng thật sự xuất hiện trong diagram/code, không bắt buộc mỗi entity getter/setter có một bảng.

## Sequence diagram

Đọc từ trái sang phải:

1. Actor/UI gửi request.
2. Controller nhận.
3. Service validate và điều phối.
4. Repository/database.
5. External provider nếu có.
6. Response quay lại.

Activation bar thể hiện thời gian một participant đang thực thi, không phải trang trí.

## ERD

Khi được hỏi một quan hệ, trả lời cả multiplicity và lý do:

- Booking 1:N Payment vì cọc/còn lại/retry.
- Booking 1:N Refund vì partial refund.
- Booking 1:1 Invoice vì một consolidated current bill.
- Customer 0..1 CustomerMembership vì lưu current membership state.
- Field 1:N FieldPrice vì nhiều price bands.
- Field 1:N Slot; Slot có lịch sử N Booking nhưng tối đa một active booking.

## Package diagram

Package hiện tại nên đọc theo kiến trúc thực:

- `config/security`
- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `enums`
- `exception`

Không gọi repository là “DAO tự viết JDBC” nếu code đang dùng Spring Data JPA.

---

# 12. Phao một trang trước khi vào phòng

## Pitch

GoalZone = React + Spring Boot + JWT + Supabase PostgreSQL + Flyway + PayPal Sandbox + SMTP. Code-first; code/migration là nguồn chuẩn.

## AnNP

UC-34–44:

```text
checkout summary
→ payment option
→ PayPal order
→ capture/confirm (included in Pay online)
→ remaining cash
→ failed/expired
→ payment history
→ invoice maintain (included) / view status
→ refund process/manage
→ deposit/refund policy
```

## Công thức

```text
base = field + services
total = base - promotion - membership
deposit = total × policy%
remaining = max(total - grossPaid, 0)
refundable = policy entitlement - completed refunds
net revenue = gross - completed refunds - processor fee
```

## Bốn nguyên tắc tiền

1. Server tự tính amount.
2. `BigDecimal`, USD, round 2 decimals.
3. Gross paid không bị xóa khi refund.
4. PayPal `COMPLETED` mới là external success.

## Bốn nguyên tắc an toàn

1. Role + ownership ở backend.
2. Transaction cho local atomicity.
3. Idempotency cho external retry.
4. DB constraint/trigger cho race và integrity.

## Bốn câu cứu nguy

- Cancel khác refund.
- Booking status khác payment status.
- Processor fee khác cancellation fee và khác tax.
- Preview ở UI không thay thế server validation.

---

# 13. Tự kiểm tra trước khi ngủ

Nếu trả lời trôi chảy 15 câu này là đủ chắc:

1. GoalZone giải quyết vấn đề gì?
2. Customer, Staff và Admin khác nhau thế nào?
3. Vì sao Staff không có PayPal checkout?
4. Vì sao Booking, Payment, Invoice, Refund tách riêng?
5. Công thức total/deposit/remaining?
6. Promotion và membership stack thế nào?
7. PayPal fee được xử lý thế nào?
8. Vì sao paidAmount không giảm khi refund?
9. Cancel và refund khác nhau ra sao?
10. Reschedule sang giá cao/thấp xử lý thế nào?
11. Double click PayPal capture có bị charge hai lần không?
12. Hai người book cùng slot thì sao?
13. Vì sao dùng Flyway và Hibernate validate?
14. Vì sao field price/system setting/slot là bảng riêng?
15. Điểm yếu production tiếp theo là gì?

---

# 14. Câu kết khi defense

> Nhóm không chỉ làm đủ màn hình theo backlog mà cố bảo vệ tính nhất quán xuyên suốt từ UI, service đến database. Với luồng tài chính, server là nơi tính tiền; payment và refund giữ lịch sử; PayPal được xử lý idempotent; invoice được reconcile; và PostgreSQL constraint bảo vệ các race condition mà UI không thể xử lý. Những phần chưa production-grade như live payment certification, webhook, tax và RLS được xác định rõ là hướng mở rộng chứ không bị trộn thành logic giả trong bản hiện tại.

---

# 15. Cách đọc các mũi tên trong Class Diagram

## Ba ký hiệu đang dùng trong diagram GoalZone

| Hình nhìn thấy | PlantUML | Tên UML | Cách đọc |
|---|---|---|---|
| Nét đứt, đầu mũi tên mở | `A ..> B` | Dependency | A tạm thời **dùng/phụ thuộc vào** B |
| Nét liền, đầu mũi tên thường | `A --> B` | Directed association | A **biết hoặc giữ liên kết đến** B |
| Nét liền, đầu tam giác trắng | `A -|> B` | Generalization/Inheritance | A **kế thừa** B; đọc là “A is a B” |

Chiều mũi tên luôn đi từ lớp phụ thuộc/lớp con sang lớp được dùng/lớp cha.

### 1. Nét đứt `..>` — Dependency

Ví dụ:

```text
PaymentController ..> PaymentWorkflowService
```

Đọc là:

> `PaymentController` sử dụng `PaymentWorkflowService` để thực hiện nghiệp vụ.

Dependency thường xuất hiện khi A:

- Gọi method của B.
- Nhận B qua dependency injection.
- Dùng B làm parameter, return type hoặc biến tạm.

Nó là quan hệ “A cần B để làm việc”, nhưng không nói rằng hai object có vòng đời chung hoặc database có foreign key.

### 2. Nét liền `-->` — Directed association

Ví dụ:

```text
PaymentRepository --> Payment
Booking --> Slot
```

Đọc là:

- `PaymentRepository` quản lý/trả về entity `Payment`.
- `Booking` giữ một liên kết có hướng đến `Slot`.

Association mạnh và ổn định hơn dependency: A biết B như một phần cấu trúc hoặc domain relationship. Với entity JPA, nó thường tương ứng một field như:

```java
private Slot slot;
```

Tuy nhiên không nên kết luận mọi `-->` đều chắc chắn là foreign key; phải xem field/JPA mapping hoặc ERD.

### 3. Tam giác trắng `-|>` — Inheritance

Ví dụ:

```text
Payment -|> AuditEntity
Booking -|> AuditEntity
```

Đọc là:

> `Payment` và `Booking` kế thừa `AuditEntity`.

Tam giác trắng luôn chỉ về **lớp cha**. Nhờ vậy các entity dùng lại `createdAt` và `updatedAt`.

Mẹo nhớ:

> Tam giác trắng giống đầu của cây gia phả và luôn chỉ về “cha”.

## Các ký hiệu UML phổ biến khác

Những ký hiệu dưới đây không phải loại chính trong các class diagram GoalZone hiện tại, nhưng có thể xuất hiện trong template hoặc câu hỏi của giảng viên.

| Ký hiệu | Tên | Ý nghĩa |
|---|---|---|
| `A ..|> B` | Realization | A implements interface B; tam giác trắng + nét đứt |
| `A -- B` | Association hai chiều/không chỉ hướng | Hai lớp có liên hệ, diagram không nhấn mạnh chiều truy cập |
| `A o-- B` | Aggregation | A chứa/nhóm B, nhưng B vẫn sống độc lập |
| `A *-- B` | Composition | A sở hữu chặt B; B thường không tồn tại hợp lý nếu thiếu A |

### Hình thoi trắng và tam giác trắng không giống nhau

- **Tam giác trắng ở cuối đường:** inheritance hoặc realization.
- **Hình thoi trắng ở phía owner:** aggregation.
- **Hình thoi đen:** composition.

Ví dụ:

```text
Team o-- Player
House *-- Room
```

- Player có thể vẫn tồn tại khi đổi Team → aggregation.
- Room trong mô hình này thuộc chặt House → composition.

Trong JPA, không nên suy composition chỉ vì có `@OneToMany`. Phải xem ownership, cascade và quy tắc xóa.

## Multiplicity — số lượng ở hai đầu

| Ký hiệu | Nghĩa |
|---|---|
| `1` | Chính xác một |
| `0..1` | Không có hoặc một |
| `*` hoặc `0..*` | Không hoặc nhiều |
| `1..*` | Ít nhất một |

Ví dụ:

```text
Booking "1" --> "0..*" Payment
```

Đọc hai chiều:

- Một Booking có thể có 0 đến nhiều Payment.
- Mỗi Payment trong quan hệ này thuộc đúng một Booking.

## Ký hiệu trong ô class

| Ký hiệu | Visibility |
|---|---|
| `+` | public |
| `-` | private |
| `#` | protected |
| `~` | package/default |

Ví dụ:

```text
-paymentRepository : PaymentRepository
+capturePayment(...) : Object
```

Nghĩa là repository là field private, còn `capturePayment` là public method.

Chữ nghiêng thường biểu diễn abstract; tên/gạch chân biểu diễn static tùy renderer. `<<controller>>`, `<<service>>`, `<<repository>>`, `<<entity>>` là stereotype để nói vai trò kiến trúc, không phải quan hệ kế thừa.

## Cách đọc nhanh một diagram của GoalZone

Ví dụ chuỗi:

```text
PaymentController ..> PaymentWorkflowService
PaymentWorkflowService ..> PaymentRepository
PaymentRepository --> Payment
Payment -|> AuditEntity
Payment --> Booking
```

Có thể thuyết trình:

> Controller phụ thuộc vào workflow service để điều phối use case. Service dùng repository để truy cập dữ liệu. Repository quản lý entity Payment. Payment kế thừa thông tin audit và giữ quan hệ domain đến Booking.

Đừng nói mọi mũi tên là “kế thừa” và cũng đừng gọi dependency nét đứt là database relationship.
