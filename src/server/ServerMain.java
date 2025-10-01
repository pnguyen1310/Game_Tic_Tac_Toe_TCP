package server;

import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.Socket;

public class ServerMain {
    // Khai báo cổng server chạy 
    public static final int PORT = 5555;

    public static void main(String[] args) {
        System.out.println("[Server] Starting on port " + PORT);

        // Khởi tạo Store để quản lý user & event (từ file txt)
        Store store = new Store("data/users.txt", "data/events.txt");
        store.initIfMissing();

        // Khởi tạo Core (logic game) dựa trên dữ liệu từ Store
        Core core = new Core(store);

        // Tạo thread pool để xử lý nhiều client song song
        ExecutorService pool = Executors.newCachedThreadPool();

        // Khởi tạo ServerSocket để lắng nghe trên cổng PORT = 5555
        try (ServerSocket ss = new ServerSocket(PORT)) {
            while (true) {
                // Chờ client kết nối (blocking)
                Socket s = ss.accept();

                // Mỗi client kết nối sẽ được gán cho 1 Handler
                pool.submit(new Handler(s, core));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pool.shutdownNow();
        }
    }
}
