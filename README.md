 <h2 align="center">
    <a href="https://dainam.edu.vn/vi/khoa-cong-nghe-thong-tin">
    🎓 Faculty of Information Technology (DaiNam University)
    </a>
</h2>
<h2 align="center">
   GAME TIC TAC TOE (CARO 3x3) SỬ DỤNG GIAO THỨC TCP
</h2>
<div align="center">
    <p align="center">
        <img src="docs/aiotlab_logo.png" alt="AIoTLab Logo" width="170"/>
        <img src="docs/fitdnu_logo.png" alt="AIoTLab Logo" width="180"/>
        <img src="docs/dnu_logo.png" alt="DaiNam University Logo" width="200"/>
    </p>

[![AIoTLab](https://img.shields.io/badge/AIoTLab-green?style=for-the-badge)](https://www.facebook.com/DNUAIoTLab)
[![Faculty of Information Technology](https://img.shields.io/badge/Faculty%20of%20Information%20Technology-blue?style=for-the-badge)](https://dainam.edu.vn/vi/khoa-cong-nghe-thong-tin)
[![DaiNam University](https://img.shields.io/badge/DaiNam%20University-orange?style=for-the-badge)](https://dainam.edu.vn)


</div>

## 💡1. Tổng quan về hệ thống
Ứng dụng "Game Caro 3x3" là phiên bản trò chơi cờ Caro cổ điển với bàn cờ 3x3. Người chơi có thể tham gia, tạo phòng và thi đấu trực tuyến với nhau.

Hệ thống được xây dựng theo mô hình client–server và giao tiếp thông qua giao thức TCP, giúp quá trình chơi diễn ra ổn định, mượt mà và đáng tin cậy.

### 💻 Thành phần chính
Ứng dụng gồm các thành phần chính sau:

1. **TicTacToeServer**  
   - **Quản lý kết nối** từ nhiều client.  
   - **Xử lý logic trò chơi**: kiểm tra thắng/thua/hòa.  
   - **Quản lý phòng chơi**: tạo phòng, tham gia phòng, rời phòng.  
   - Giao tiếp bằng **giao thức TCP**.

2. **TicTacToeClient**  
   - **Kết nối** tới server.  
   - **Gửi lệnh** (SET_NAME, CREATE_ROOM, JOIN_ROOM, MOVE, CHAT, PLAY_AGAIN, …).  
   - **Nhận và xử lý** thông điệp phản hồi từ server.  

3. **LoginUI**  
   - **Giao diện đăng nhập**: cho phép người chơi nhập tên trước khi vào sảnh chờ.  

4. **LobbyUI**  
   - **Hiển thị danh sách phòng chơi**.  
   - Cho phép **tạo phòng, tham gia phòng hoặc làm mới danh sách**.  

5. **TicTacToeGUI (Game UI)**  
   - **Bàn cờ 3x3** để chơi game.  
   - **Hiển thị trạng thái lượt chơi**.  
   - **Khung chat** để trao đổi giữa hai người chơi.  
   - Các nút chức năng: **Chơi lại** và **Rời phòng**.  
   - Hiệu ứng: **highlight 3 ô thắng**, *hover effect*, **popup kết quả**.  

---

### 🌐 Giao thức & Kết nối

Ứng dụng **Game Caro 3x3** sử dụng mô hình **Client-Server** kết nối qua **TCP Socket** (mặc định cổng `5000`).  

### 📥 Lệnh từ Client → Server
- `SET_NAME <name>` : Đặt tên người chơi.  
- `CREATE_ROOM` : Tạo phòng chơi mới.  
- `JOIN_ROOM <roomId>` : Tham gia vào một phòng có sẵn.  
- `MOVE <row> <col>` : Đánh cờ tại vị trí `(row, col)`.  
- `CHAT:<message>` : Gửi tin nhắn chat.  
- `PLAY_AGAIN_REQUEST` : Yêu cầu chơi lại.  
- `PLAY_AGAIN_ACCEPT` : Đồng ý chơi lại.  
- `PLAY_AGAIN_DECLINE` : Từ chối chơi lại.  
- `LEAVE_ROOM` : Rời khỏi phòng chơi.  
- `REFRESH` : Làm mới danh sách phòng.  

### 📤 Thông điệp từ Server → Client
- `INFO <text>` : Thông báo chung từ server.  
- `ROOM_LIST <id>(players/2);...` : Danh sách phòng hiện tại.  
- `ROOM_CREATED <id>` : Xác nhận phòng mới được tạo.  
- `JOIN_SUCCESS <roomId> <symbol>` : Tham gia phòng thành công, được cấp ký hiệu `X` hoặc `O`.  
- `JOIN_FAIL <roomId> <reason>` : Tham gia phòng thất bại.  
- `YOUR_MOVE` : Đến lượt người chơi.  
- `WAITING` : Chờ đối thủ đi.  
- `MOVE <symbol> <row> <col>` : Thông báo một nước đi.  
- `WIN <symbol>` : Người thắng cuộc.  
- `DRAW` : Ván đấu hòa.  
- `RESET` : Reset lại bàn cờ cho ván mới.  
- `CHAT: PlayerName: message` : Tin nhắn chat từ người chơi.  
- `PLAY_AGAIN_REQUEST` : Đối thủ gửi yêu cầu chơi lại.  
- `PLAY_AGAIN_ACCEPT` : Đối thủ đồng ý chơi lại.  
- `PLAY_AGAIN_DECLINE` : Đối thủ từ chối chơi lại.  


---

### 💾 Lưu trữ dữ liệu

- Server lưu trữ **tạm thời trong bộ nhớ** (in-memory).  
- Thành phần chính:  
  - **Rooms**: chứa bàn cờ 3x3, danh sách người chơi, lượt hiện tại, trạng thái trận.  
  - **LobbyClients**: danh sách client chưa tham gia phòng.  
- Dữ liệu **không lưu vĩnh viễn**, sẽ mất khi server tắt.  

---

### ♟️ Luật chơi (Tóm tắt)

- Bàn cờ **3x3**, hai người chơi lần lượt đánh dấu `X` và `O`.  
- Người thắng là người có **3 ký hiệu liên tiếp** (hàng ngang, hàng dọc hoặc chéo).  
- Nếu bàn cờ đầy mà **không ai thắng** → ván đấu hòa.  
- Sau khi kết thúc, người chơi có thể chọn **Chơi lại** hoặc **Rời phòng**.  


### 📌 Ví dụ bàn cờ thắng:
<p align="center">
  <img width="480"  alt="image" src="images/Capture3.PNG" />
  <br>
 <em> Hình 1: Ví dụ bàn cờ khi chiến thắng </em>
</p>

## 🔧 2. Công nghệ sử dụng
[![Java](https://img.shields.io/badge/Java-24-orange)](https://www.oracle.com/java/)
[![JDK](https://img.shields.io/badge/JDK-24-blueviolet)](https://adoptium.net/)
[![Language](https://img.shields.io/badge/Language-Java-green)](https://www.java.com/)
[![TCP](https://img.shields.io/badge/Protocol-TCP-9cf)](https://en.wikipedia.org/wiki/Transmission_Control_Protocol)
[![Socket](https://img.shields.io/badge/Socket-Server/Socket-blue)](https://docs.oracle.com/en/java/)
[![Swing](https://img.shields.io/badge/UI-Swing-orange)](https://docs.oracle.com/en/java/)

- **Ngôn ngữ lập trình:** Java  
- **Giao diện người dùng:** Java Swing (JFrame, JPanel, JButton, ...).  
- **Mạng & Giao tiếp:** TCP Socket (Client - Server).  
- **Xử lý đa luồng:** Thread để quản lý nhiều client kết nối cùng lúc.  
- **Cấu trúc dự án:** Tách riêng các thành phần `Client`, `Server`, và `UI` để dễ bảo trì và mở rộng.  


## 📸 3. Hình ảnh các chức năng

###  Giao diện Đăng nhập
<p align="center">
  <img src="images/Capture.PNG" alt="Giao diện Đăng nhập" width="450" />
  <br>
 <em> Hình 2: Giao diện Đăng nhập </em>
</p>

###  Giao diện Lobby
<p align="center">
  <img src="images/Capture1.PNG" alt="Giao diện Lobby" width="450" />
  <br>
<em> Hình 3: Giao diện Lobby </em>
</p>

###  Giao diện bàn cờ và khung chat
<p align="center">
 <img src="images/Capture2.PNG" alt="Giao diện bàn cờ và khung chat" width="700" />
  <br>
<em> Hình 4: Giao diện bàn cờ và khung chat </em>
</p>

###  Giao diện chiến thắng
<p align="center">
 <img src="images/Capture3.PNG" alt="Giao diện chiến thắng" width="450" />
  <br>
<em> Hình 5: Giao diện chiến thắng </em>
</p>

###  Giao diện Thua
<p align="center">
 <img src="images/Capture4.PNG" alt="Giao diện Thống kê" width="450" />
  <br>
<em> Hình 6: Giao diện thua </em>
</p>

## ⚙️ 4. Các bước cài đặt & Chạy ứng dụng

### 🛠️ 4.1. Yêu cầu hệ thống

* **Java Development Kit (JDK):** Phiên bản **Java 9 trở lên** (khuyến nghị **Java 17 LTS**).
    * *Lưu ý:* Dự án sử dụng `module-info.java`, do đó cần JDK 9+ để biên dịch và chạy.
* **Môi trường phát triển:** Eclipse IDE.
* **Hệ điều hành:** Windows, macOS, hoặc Linux.

---

### 📥 4.2. Thiết lập dự án trong Eclipse

1.  **Mở Eclipse và Import dự án:**
    * Mở Eclipse IDE.
    * Trên thanh menu, chọn **File > Import...**
    * Trong cửa sổ mới, chọn **General > Existing Projects into Workspace** rồi nhấn **Next**.
    * Chọn **Browse...** để tìm đến thư mục gốc của dự án và nhấn **Finish**.

2.  **Kiểm tra cấu hình JDK:**
    * Đảm bảo dự án đã được cấu hình với **JDK 9 trở lên**.
    * Nhấp chuột phải vào dự án trong **Package Explorer**, chọn **Properties**.
    * Kiểm tra trong mục **Java Build Path** hoặc **Java Compiler** để đảm bảo đúng phiên bản JDK được sử dụng.

---

### ▶️ 4.3. Chạy ứng dụng

1. **Khởi động Server**  
   - Mở IDE hoặc terminal tại thư mục chứa mã nguồn.  
   - Chạy file `TicTacToeServer.java`.  
   - Server sẽ lắng nghe trên cổng `5000` (mặc định).  

2. **Khởi động Client**  
   - Chạy file `LoginUI.java`.
   - Nhập **tên người chơi** và kết nối tới server.  

3. **Tham gia trò chơi**  
   - Người chơi có thể **tạo phòng** hoặc **tham gia phòng có sẵn**.  
   - Khi đủ 2 người, ván đấu sẽ bắt đầu.  

⚠️ Lưu ý: Nếu muốn chơi trên nhiều máy, đảm bảo rằng client nhập đúng **địa chỉ IP** của server thay vì `localhost`.  

---

### 📞 5. Liên hệ
- 👨‍🎓 **Sinh viên thực hiện**: Nguyễn Đào Phúc Nguyên
- 🎓 **Khoa**: Công nghệ thông tin – Đại học Đại Nam
- 📧 **Email**: nguyendaophucnguyen13@gmail.com




















