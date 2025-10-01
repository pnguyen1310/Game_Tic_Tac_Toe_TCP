package server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Handler implements Runnable {
    private final Socket socket;       
    private final Core core;           
    private String lastToken = null;   

    // constructor nhận socket (client) và core (logic)
    public Handler(Socket socket, Core core) {
        this.socket = socket;
        this.core = core;
    }

    @Override
    public void run() {
        String remote = socket.getRemoteSocketAddress().toString();
        System.out.println("[Server] Connected: " + remote);

        // tạo luồng đọc và ghi text từ/đến client 
        try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            // vòng lặp: liên tục đọc dữ liệu text từ client
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue; // bỏ qua dòng rỗng

                // parse dữ liệu request text thành map key-value
                Map<String,String> req = Core.parseLine(line);

                // lấy token từ request (nếu có) để nhớ user này
                String tok = req.get("token");
                if (tok != null && !tok.isBlank()) lastToken = tok;

                // gọi core.handle(line) để xử lý logic yêu cầu
                Map<String,String> res = core.handle(line);

                // mã hóa kết quả thành chuỗi text để trả về
                String response = Core.encodeResponse(res);

                // ghi chuỗi phản hồi về cho client
                out.write(response);
                out.write("\n");
                out.flush();
            }
        } catch (IOException e) {
            // nếu client đóng kết nối hoặc bị mất kết nối
        } finally {
            // nếu client có token và thoát giữa chừng -> xử lý disconnect trong core
            if (lastToken != null) {
                core.onDisconnectToken(lastToken);
            }
            core.onDisconnect(socket); // giữ API cũ
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[Server] Disconnected: " + remote);
        }
    }
}
