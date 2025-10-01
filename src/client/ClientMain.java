package client;

import ui.LoginView; 

public class ClientMain {
    public static void main(String[] args) {
        // khai báo địa chỉ server (localhost)
        String host = "127.0.0.1";
        // khai báo cổng server (5555)
        int port = 5555;

        // tạo đối tượng Net -> kết nối TCP tới server qua host:port
        Net net = new Net(host, port);

        // chạy giao diện đăng nhập trên luồng giao diện Swing
        javax.swing.SwingUtilities.invokeLater(() -> 
            new LoginView(net).setVisible(true)
        );
    }
}
