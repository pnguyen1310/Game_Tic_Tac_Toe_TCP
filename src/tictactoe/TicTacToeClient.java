package tictactoe;

import java.io.*;
import java.net.*;
import javax.swing.*;

/**
 * Client: quản lý socket, gửi/nhận message và chuyển tiếp tới UI.
 */
public class TicTacToeClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private LobbyUI lobbyUI;
    private TicTacToeGUI gameUI;

    private char mySymbol = '?'; // lưu ký hiệu X hoặc O

    public TicTacToeClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // start listener thread
        new Thread(this::listen).start();
    }

    public void setLobbyUI(LobbyUI ui) {
        this.lobbyUI = ui;
    }

    public void setGameUI(TicTacToeGUI ui) {
        this.gameUI = ui;
        if (ui != null) {
            ui.setMySymbol(mySymbol); // gán ký hiệu cho GUI
        }
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> handle(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                if (lobbyUI != null) lobbyUI.showInfo("Connection lost");
                if (gameUI != null) gameUI.showMessage("Connection lost");
            });
        }
    }

    private void handle(String msg) {
        if (msg.startsWith("ROOM_LIST ")) {
            if (lobbyUI != null) lobbyUI.updateRoomList(msg);

        } else if (msg.startsWith("ROOM_CREATED ")) {
            if (lobbyUI != null) lobbyUI.showInfo("Room created: " + msg.substring(13));

        } else if (msg.startsWith("JOIN_SUCCESS ")) {
            // JOIN_SUCCESS <roomId> <symbol>
            String[] parts = msg.split(" ");
            if (parts.length >= 3) {
                mySymbol = parts[2].charAt(0);
                if (gameUI != null) {
                    gameUI.setMySymbol(mySymbol);
                }
            }
            if (lobbyUI != null) lobbyUI.enterRoom(msg);

        } else if (msg.startsWith("JOIN_FAIL ")) {
            if (lobbyUI != null) lobbyUI.showInfo("Join failed: " + msg.substring(10));

        } else if (msg.startsWith("YOUR_MOVE")) {
            if (gameUI != null) gameUI.setMyTurn(true);

        } else if (msg.startsWith("WAITING")) {
            if (gameUI != null) gameUI.setMyTurn(false);

        } else if (msg.startsWith("MOVE ")) {
            if (gameUI != null) gameUI.handleServerMove(msg);

        } else if (msg.startsWith("WIN ")) {
            if (gameUI != null) gameUI.handleWin(msg); // ✅ chỉ truyền 1 tham số

        } else if (msg.startsWith("DRAW")) {
            if (gameUI != null) gameUI.handleDraw();

        } else if (msg.startsWith("RESET")) {
            if (gameUI != null) gameUI.resetBoard();

        } else if (msg.startsWith("CHAT:")) {
            String body = msg.substring(5).trim();
            if (gameUI != null) gameUI.addChat(body);
            else if (lobbyUI != null) lobbyUI.showInfo(body);

        } else if (msg.startsWith("INFO ")) {
            String body = msg.substring(5).trim();
            if (lobbyUI != null) lobbyUI.showInfo(body);
            if (gameUI != null) gameUI.showMessage(body);

        } else if (msg.startsWith("PLAY_AGAIN_REQUEST")) {
            if (gameUI != null) gameUI.promptPlayAgainRequest();

        } else if (msg.startsWith("PLAY_AGAIN_ACCEPT")) {
            if (gameUI != null) gameUI.showMessage("Opponent accepted rematch");

        } else if (msg.startsWith("PLAY_AGAIN_DECLINE")) {
            if (gameUI != null) gameUI.showMessage("Opponent declined rematch");

        } else {
            if (lobbyUI != null) lobbyUI.showInfo(msg);
        }
    }

    public void send(String message) {
        if (out != null) out.println(message);
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}
