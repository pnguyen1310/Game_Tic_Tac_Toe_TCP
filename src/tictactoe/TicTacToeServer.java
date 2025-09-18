package tictactoe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server hỗ trợ multi-room. Protocol text-based (line per message).
 *
 * Client -> Server commands:
 *   SET_NAME <name>
 *   CREATE_ROOM
 *   JOIN_ROOM <roomId>
 *   MOVE <row> <col>
 *   CHAT:<message>
 *   PLAY_AGAIN_REQUEST
 *   PLAY_AGAIN_ACCEPT
 *   PLAY_AGAIN_DECLINE
 *   LEAVE_ROOM
 *   REFRESH
 *
 * Server -> Client messages:
 *   INFO <text>
 *   ROOM_LIST <id>(players/2);...
 *   ROOM_CREATED <id>
 *   JOIN_SUCCESS <roomId> <symbol>
 *   JOIN_FAIL <roomId> <reason>
 *   YOUR_MOVE
 *   WAITING
 *   MOVE <symbol> <row> <col>
 *   WIN <symbol>
 *   DRAW
 *   RESET
 *   CHAT: PlayerName: message
 *   PLAY_AGAIN_REQUEST
 *   PLAY_AGAIN_ACCEPT
 *   PLAY_AGAIN_DECLINE
 */
public class TicTacToeServer {
    private static final int PORT = 5000;
    private static final Map<Integer, Room> rooms = Collections.synchronizedMap(new HashMap<>());
    private static final List<ClientHandler> lobbyClients = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger nextRoomId = new AtomicInteger(1);

    public static void main(String[] args) {
        System.out.println("TicTacToeServer starting on port " + PORT + " ...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket s = serverSocket.accept();
                ClientHandler ch = new ClientHandler(s);
                new Thread(ch).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void broadcastRoomListToLobby() {
        synchronized (lobbyClients) {
            String listMsg = buildRoomListMessage();
            for (ClientHandler ch : lobbyClients) {
                ch.send(listMsg);
            }
        }
    }

    private static String buildRoomListMessage() {
        StringBuilder sb = new StringBuilder("ROOM_LIST ");
        synchronized (rooms) {
            for (Room r : rooms.values()) {
                sb.append(r.id).append("(").append(r.players.size()).append("/2);");
            }
        }
        return sb.toString();
    }

    private static class Room {
        final int id;
        final char[][] board = new char[3][3];
        final List<ClientHandler> players = new ArrayList<>(2);
        char currentPlayer = 'X';
        boolean gameActive = false;

        Room(int id) {
            this.id = id;
        }

        synchronized void resetGame() {
            for (int i = 0; i < 3; i++)
                Arrays.fill(board[i], '\0');
            currentPlayer = 'X';
            gameActive = true;
            for (ClientHandler p : players) p.send("RESET");
            sendTurnInfo();
        }

        synchronized void sendTurnInfo() {
            if (players.size() >= 1) players.get(0).send(currentPlayer == 'X' ? "YOUR_MOVE" : "WAITING");
            if (players.size() >= 2) players.get(1).send(currentPlayer == 'O' ? "YOUR_MOVE" : "WAITING");
        }

        synchronized void broadcast(String msg) {
            for (ClientHandler ch : new ArrayList<>(players)) {
                ch.send(msg);
            }
        }

        synchronized boolean isFull() {
            return players.size() >= 2;
        }

        synchronized void reassignSymbols() {
            if (players.size() >= 1) players.get(0).symbol = 'X';
            if (players.size() >= 2) players.get(1).symbol = 'O';
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String name = "Unknown";
        private Room currentRoom = null;
        private char symbol = '?';

        ClientHandler(Socket s) {
            this.socket = s;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                synchronized (lobbyClients) {
                    lobbyClients.add(this);
                }
                send(buildRoomListMessage());
                send("INFO Connected to TicTacToeServer");

                String line;
                while ((line = in.readLine()) != null) {
                    handle(line.trim());
                }
            } catch (IOException e) {
                // client disconnected
            } finally {
                cleanup();
            }
        }

        private void handle(String msg) {
            try {
                if (msg.startsWith("SET_NAME ")) {
                    name = msg.substring(9).trim();
                    send("INFO Hello " + name);
                    send(buildRoomListMessage());
                } else if (msg.equals("CREATE_ROOM")) {
                    int id = nextRoomId.getAndIncrement();
                    Room r = new Room(id);
                    rooms.put(id, r);
                    send("ROOM_CREATED " + id);
                    broadcastRoomListToLobby();
                } else if (msg.startsWith("JOIN_ROOM ")) {
                    int roomId;
                    try {
                        roomId = Integer.parseInt(msg.substring(10).trim());
                    } catch (NumberFormatException e) {
                        send("JOIN_FAIL 0 invalid_id");
                        return;
                    }
                    joinRoom(roomId);
                } else if (msg.startsWith("MOVE ")) {
                    String[] parts = msg.split(" ");
                    if (parts.length >= 3) {
                        int r = Integer.parseInt(parts[1]);
                        int c = Integer.parseInt(parts[2]);
                        handleMove(r, c);
                    }
                } else if (msg.startsWith("CHAT:")) {
                    String cm = msg.substring(5);
                    if (currentRoom != null) {
                        currentRoom.broadcast("CHAT: " + name + ": " + cm);
                    } else {
                        send("INFO You're not in a room");
                    }
                } else if (msg.equals("PLAY_AGAIN_REQUEST")) {
                    if (currentRoom != null) {
                        synchronized (currentRoom) {
                            for (ClientHandler p : currentRoom.players) {
                                if (p != this) { // chỉ gửi cho đối thủ
                                    p.send("PLAY_AGAIN_REQUEST");
                                }
                            }
                        }
                    }
                } else if (msg.equals("PLAY_AGAIN_ACCEPT")) {
                    if (currentRoom != null) {
                        currentRoom.resetGame();
                        currentRoom.broadcast("PLAY_AGAIN_ACCEPT");
                    }
                } else if (msg.equals("PLAY_AGAIN_DECLINE")) {
                    if (currentRoom != null) currentRoom.broadcast("PLAY_AGAIN_DECLINE");
                } else if (msg.equals("LEAVE_ROOM")) {
                    leaveCurrentRoom();
                } else if (msg.equals("REFRESH")) {
                    send(buildRoomListMessage());
                } else {
                    send("INFO Unknown command: " + msg);
                }
            } catch (Exception ex) {
                send("INFO Server error: " + ex.getMessage());
            }
        }

        private void joinRoom(int roomId) {
            Room r;
            synchronized (rooms) {
                r = rooms.get(roomId);
            }
            if (r == null) {
                send("JOIN_FAIL " + roomId + " not_found");
                return;
            }
            synchronized (r) {
                if (r.isFull()) {
                    send("JOIN_FAIL " + roomId + " full");
                    return;
                }
                synchronized (lobbyClients) {
                    lobbyClients.remove(this);
                }
                r.players.add(this);
                currentRoom = r;
                r.reassignSymbols();
                symbol = this.symbol;
                send("JOIN_SUCCESS " + roomId + " " + symbol);
                r.broadcast("INFO " + name + " joined room " + roomId);

                if (r.players.size() == 2) {
                    r.resetGame();
                    r.broadcast("INFO Match started in room " + r.id);
                } else {
                    send("INFO Waiting for opponent to join...");
                }
                broadcastRoomListToLobby();
            }
        }

        private void handleMove(int row, int col) {
            if (currentRoom == null) {
                send("INFO You are not in a room");
                return;
            }
            Room r = currentRoom;
            synchronized (r) {
                if (!r.gameActive) {
                    send("INFO Game not active");
                    return;
                }
                char myChar = this.symbol;
                if (myChar != r.currentPlayer) {
                    send("INFO Not your turn");
                    return;
                }
                if (row < 0 || row > 2 || col < 0 || col > 2) {
                    send("INFO Invalid cell");
                    return;
                }
                if (r.board[row][col] != '\0') {
                    send("INFO Cell already taken");
                    return;
                }
                r.board[row][col] = myChar;
                r.broadcast("MOVE " + myChar + " " + row + " " + col);

                if (checkWin(r.board, myChar)) {
                    r.gameActive = false;
                    r.broadcast("WIN " + myChar);
                    return;
                } else if (isBoardFull(r.board)) {
                    r.gameActive = false;
                    r.broadcast("DRAW");
                    return;
                } else {
                    r.currentPlayer = (r.currentPlayer == 'X') ? 'O' : 'X';
                    r.sendTurnInfo();
                }
            }
        }

        private boolean checkWin(char[][] b, char p) {
            for (int i = 0; i < 3; i++) {
                if (b[i][0] == p && b[i][1] == p && b[i][2] == p) return true;
            }
            for (int j = 0; j < 3; j++) {
                if (b[0][j] == p && b[1][j] == p && b[2][j] == p) return true;
            }
            if (b[0][0] == p && b[1][1] == p && b[2][2] == p) return true;
            if (b[0][2] == p && b[1][1] == p && b[2][0] == p) return true;
            return false;
        }

        private boolean isBoardFull(char[][] b) {
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++)
                    if (b[i][j] == '\0') return false;
            return true;
        }

        void send(String s) {
            if (out != null) out.println(s);
        }

        private void leaveCurrentRoom() {
            if (currentRoom != null) {
                Room r = currentRoom;
                synchronized (r) {
                    r.players.remove(this);
                    r.broadcast("INFO " + name + " left the room");

                    if (r.players.isEmpty()) {
                        synchronized (rooms) {
                            rooms.remove(r.id);
                        }
                    } else {
                        r.broadcast("INFO Waiting for opponent...");
                        r.gameActive = false;
                        for (int i = 0; i < 3; i++) Arrays.fill(r.board[i], '\0');
                        r.currentPlayer = 'X';
                    }
                }
                currentRoom = null;
                symbol = '?';
                synchronized (lobbyClients) {
                    lobbyClients.add(this);
                }
                broadcastRoomListToLobby();
            } else {
                send("INFO Not in a room");
            }
        }

        private void cleanup() {
            synchronized (lobbyClients) {
                lobbyClients.remove(this);
            }
            if (currentRoom != null) {
                leaveCurrentRoom();
            }
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
