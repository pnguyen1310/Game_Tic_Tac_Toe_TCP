package client;

import java.io.*;
import java.net.Socket;                       
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Net implements Closeable {
    private final String host; private final int port;  
    private Socket socket;                    // dt Socket đại diện cho kết nối client ↔ server
    private BufferedReader in;                // đọc dữ liệu từ server
    private BufferedWriter out;               // ghi dữ liệu gửi tới server
    public String token = null;

    public Net(String host, int port) {
        this.host = host; 
        this.port = port; 
        connect();                            
    }

    private void connect() {
        try {
            socket = new Socket(host, port);  // khởi tạo client socket 
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); // luồng đọc
            out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)); // luồng ghi
        } catch (IOException e) { 
            throw new RuntimeException("Cannot connect server", e); 
        }
    }

    public synchronized String send(String cmdLine) {
        String id = UUID.randomUUID().toString().substring(0,8);  // sinh id ngẫu nhiên cho request
        String line = "REQ id="+id+";" + cmdLine;
        try {
            out.write(line); 
            out.write("\n"); 
            out.flush();                      // gửi dữ liệu tới server

            String resp = in.readLine();      // đọc phản hồi từ server
            return resp==null? "ERR req="+id+";msg=disconnected" : resp;
        } catch (IOException e) {
            return "ERR req="+id+";msg=io_error";
        }
    }

    @Override 
    public void close() throws IOException { 
        if (socket!=null) socket.close();     
    }
}
