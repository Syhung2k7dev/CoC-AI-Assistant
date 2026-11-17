# Kiến trúc AI Core và quản lý kết nối AI Provider

## 1. Mục tiêu

Tài liệu này chốt cách tổ chức kết nối các dịch vụ AI trong CoC AI Assistant. Mục tiêu là xử lý lỗi kết nối một cách đúng tầng, hỗ trợ Gemini hiện tại và cho phép bổ sung Provider khác mà không tạo một hệ thống quản lý độc lập, cồng kềnh.

**Quyết định kiến trúc:** Không tạo AICRM thành module cấp cao riêng. Các chức năng kết nối được tổ chức thành **AI Provider Layer bên trong AI Core**.

## 2. Ranh giới trách nhiệm

### AI Core

AI Core cung cấp năng lực suy luận và giao tiếp với các mô hình AI. Bên trong AI Core có AI Provider Layer, gồm:

- **Provider Registry:** danh sách Provider, cấu hình, model, khả năng hỗ trợ và trạng thái cấu hình.
- **Provider Adapter:** chuyển đổi yêu cầu/kết quả giữa giao diện thống nhất của ứng dụng và định dạng riêng của từng Provider.
- **Connection Runtime:** thực hiện HTTP request, timeout, cancellation, streaming lifecycle và giải phóng tài nguyên.
- **Error Handler:** chuẩn hóa lỗi, phân biệt lỗi cấu hình, xác thực, quota, mạng, timeout, server và lỗi giao thức.
- **Retry Policy:** quyết định có thử lại hay không, số lần tối đa và khoảng chờ.
- **Usage/Diagnostics:** ghi nhận thời gian, trạng thái, model, loại lỗi và số lần thử; không ghi API key hoặc nội dung nhạy cảm vào log.

Đây là các thành phần nội bộ của AI Core, không phải các Agent hoặc hệ thống độc lập.

### Các module khác

- **AELM (AI Evolution & Lifecycle Management):** tạo, sửa, kiểm thử và phát hành phiên bản Workflow/Skill. AELM sử dụng AI Core để gọi mô hình nhưng không quản lý socket, HTTP hay SSE.
- **Workflow Engine:** điều phối công việc và gọi AI Core khi cần suy luận.
- **Skill Runtime:** thực thi script và gọi các capability/API được cấp quyền.
- **API Registry:** công bố giao diện capability cho Workflow/Skill. Registry không chứa logic kết nối riêng của từng Provider.

## 3. Luồng xử lý yêu cầu

1. Workflow hoặc Skill gửi yêu cầu suy luận đến giao diện AI Core thống nhất.
2. AI Core chọn Provider/model theo cấu hình và khả năng hỗ trợ.
3. Provider Adapter chuyển yêu cầu sang định dạng của Provider.
4. Connection Runtime thực hiện request hoặc stream.
5. Error Handler phân loại kết quả; Retry Policy chỉ thử lại khi lỗi và loại request cho phép.
6. Adapter chuẩn hóa phản hồi.
7. AI Core trả kết quả hoặc lỗi có cấu trúc cho bên gọi.
8. Usage/Diagnostics ghi metadata vận hành cần thiết, không ghi bí mật.

Việc chuyển Provider chỉ diễn ra khi Provider dự phòng đã được cấu hình, có thông tin xác thực hợp lệ, hỗ trợ đúng tính năng và tương thích yêu cầu. Không âm thầm chuyển Provider nếu điều đó làm thay đổi quyền riêng tư, chi phí hoặc ngữ nghĩa của yêu cầu.

## 4. Chính sách lỗi và phục hồi

| Nhóm lỗi | Xử lý |
|---|---|
| 400 / yêu cầu không hợp lệ | Không retry; trả lỗi đầu vào có thể chẩn đoán |
| 401 / 403 | Không retry tự động; yêu cầu kiểm tra khóa và quyền |
| 429 / quota | Tôn trọng Retry-After nếu có; retry giới hạn hoặc báo quota |
| 500 / 502 / 503 / 504 | Retry có giới hạn với exponential backoff và jitter nếu request an toàn để lặp |
| Lỗi DNS / kết nối / TLS | Phân loại lỗi mạng; retry có giới hạn nếu phù hợp |
| Timeout | Hủy request đang treo, giải phóng tài nguyên và trả lỗi timeout |
| Người dùng hủy / client disconnect | Hủy tác vụ upstream nếu có thể; không tiếp tục tiêu thụ tài nguyên |
| Phản hồi sai định dạng | Không coi là lỗi quota; ghi nhận lỗi giao thức và trả lỗi chuẩn hóa |
| Stream không có tiến triển | Áp dụng idle timeout; kết thúc stream đúng cách và ghi nhận nguyên nhân |

Không retry vô hạn. Không retry các lỗi xác thực hoặc lỗi đầu vào. Với request có thể gây tác dụng phụ, chỉ retry khi có cơ chế idempotency hoặc xác nhận an toàn.

## 5. Quy tắc vòng đời HTTP và SSE

Quy tắc này áp dụng cho thành phần server/proxy nếu dự án bổ sung backend streaming trong tương lai:

- Phân biệt vòng đời request đến và vòng đời response đi; không dùng sự kiện đóng request body như bằng chứng duy nhất rằng client đã ngắt kết nối.
- Theo dõi trạng thái response/socket phù hợp với framework; chỉ đánh dấu client disconnect khi response thực sự bị đóng trước khi kết thúc.
- Có timeout tổng thể và idle timeout cho stream.
- Khi client ngắt kết nối, hủy tác vụ upstream để tránh tiếp tục gọi mô hình không cần thiết.
- Mọi nhánh thành công, lỗi, timeout và cancellation đều phải kết thúc response đúng một lần; dùng cơ chế cleanup/finalization bảo đảm tài nguyên được giải phóng.
- Không gửi lại HTTP status/header sau khi response đã bắt đầu streaming. Lỗi phát sinh sau khi stream bắt đầu phải được biểu diễn theo giao thức stream rồi đóng stream.
- Ghi nhận thời điểm mở kết nối, byte/chunk đầu tiên, thời điểm kết thúc và lý do kết thúc để phân biệt lỗi Provider với lỗi proxy/server.

**Lưu ý về repository hiện tại:** nhánh mã nguồn đang có không chứa Express server, `server.ts` hoặc endpoint SSE. `GeminiClient.kt` hiện gọi Gemini trực tiếp bằng `HttpURLConnection` theo kiểu request/response thông thường, không phải SSE. Vì vậy, lỗi `req.on("close")` được mô tả trong trao đổi là một lỗi phía server của môi trường khác; không thể áp dụng nguyên văn bản sửa Express vào tệp Android này. Khi tích hợp backend đó vào repository, cần áp dụng các quy tắc vòng đời ở mục này tại đúng server layer.

## 6. Ranh giới bảo mật

- API key do người dùng nhập chỉ được giữ trong bộ nhớ phiên ứng dụng; không ghi vào SharedPreferences, CADSS, backup, Workflow, Skill script, prompt log hoặc crash report. Trường nhập khóa phải che nội dung và chỉ cung cấp thao tác kiểm tra kết nối.
- Script chỉ được gọi AI thông qua capability được cấp quyền, không được tự mở kết nối tùy ý.
- Provider Adapter chỉ nhận thông tin xác thực từ credential boundary; không trả key về Workflow/Skill.
- Log mặc định chỉ lưu metadata vận hành. Nội dung prompt/response chỉ được lưu khi có mục đích rõ ràng và chính sách đồng ý phù hợp.
- Provider fallback phải tuân theo chính sách dữ liệu và quyền riêng tư tương đương với yêu cầu gốc.

## 7. Liên kết với AELM và Workflow/Skill

AELM có thể dùng kết quả diagnostics đã khử bí mật để phát hiện lỗi lặp lại và đề xuất thay đổi Workflow/Skill. Tuy nhiên:

- Lỗi HTTP, TLS, SSE, timeout hoặc Provider thuộc AI Provider Layer/Connection Runtime.
- Lỗi logic công việc thuộc Workflow.
- Lỗi logic chức năng thuộc Skill.
- Lỗi thực thi script thuộc Script Runtime.
- Lỗi capability/native thuộc module cung cấp capability.

AI không được tự sửa Skill để che lấp lỗi hạ tầng. Mọi thay đổi do AELM tạo ra vẫn phải qua validation, testing, versioning và deployment policy riêng.

## 8. Trạng thái triển khai

Tài liệu này mô tả kiến trúc mục tiêu và phân biệt rõ phần đã có với phần còn thiếu.

Trong nhánh triển khai AI Provider Runtime:

- Đã có `AIProviderRegistry` công bố Provider ID, capability và tạo adapter; hiện đăng ký Gemini, chưa có Provider thứ hai.
- Đã có `AIProviderAdapter` làm ranh giới chuẩn hóa yêu cầu.
- Đã có `AIProviderRuntime` quản lý timeout cấu hình, phân loại lỗi, retry giới hạn, exponential backoff, jitter và Retry-After dạng giây.
- Đã có `GeminiProviderAdapter` đóng gói HTTP request/response và chuẩn hóa phản hồi Gemini.
- `GeminiClient` hiện là facade tương thích ngược để giao diện hiện tại gọi qua AI Core Runtime.
- Đã bổ sung unit test cho retry lỗi tạm thời, không retry lỗi xác thực và kiểm tra prompt rỗng.

Các phần vẫn chưa được triển khai đầy đủ:

- Đăng ký Provider hiện mới có Gemini; chưa có cấu hình Provider thứ hai hoặc fallback có chính sách. Credential vault không được bật: API Key chỉ tồn tại trong bộ nhớ phiên theo quyết định sản phẩm.
- Cancellation: Gemini adapter chạy request trên Dispatchers.IO; khi coroutine bị hủy, cancellation handler đóng HttpURLConnection đang hoạt động. Cần CI và kiểm thử thiết bị để xác nhận hành vi trên các tình huống mạng thực tế.
- Streaming/SSE và idle-timeout cho stream.
- Usage/Diagnostics bền vững và chính sách redaction nâng cao.
- Backend Express, `server.ts` hoặc SSE endpoint không thuộc ứng dụng Android hiện tại.

Không bật fallback đa Provider khi chưa có Provider dự phòng, credentials và chính sách quyền riêng tư/chi phí được cấu hình rõ ràng. CI và kiểm thử thiết bị phải xác nhận riêng trước khi coi phần triển khai là ổn định.
