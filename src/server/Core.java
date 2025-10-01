package server;

import java.net.Socket;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Core {
    // Lưu trữ phụ thuộc ngoài: đọc/ghi users, lịch sử, leaderboard, chat...
    private final Store store;

    // Bản đồ token -> user; concurrent để an toàn luồng giữa nhiều Handler
    private final Map<String, String> tokenToUser = new ConcurrentHashMap<>();

    // Danh sách phòng đang tồn tại (id -> Room); concurrent để multi-client
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    // Hàng đợi ghép nhanh (quick match queue) theo thứ tự vào hàng
    private final ArrayDeque<String> quickQ = new ArrayDeque<>();

    public Core(Store store) { this.store = store; }

    // Parser giao thức dạng text line: "REQ id=...;cmd=...;token=...;..."
    // Trả về Map<String,String> với khóa đặc biệt _verb là từ đầu dòng (REQ)
    public static Map<String,String> parseLine(String line) {
        Map<String,String> m = new LinkedHashMap<>();
        String[] parts = line.split("\\s+", 2);
        m.put("_verb", parts[0]); 
        if (parts.length == 2) {
            String[] kvs = parts[1].split(";");
            for (String kv : kvs) {
                if (kv.isBlank()) continue;
                int i = kv.indexOf('=');
                if (i < 0) continue;
                m.put(kv.substring(0, i), unesc(kv.substring(i + 1)));
            }
        }
        return m;
    }

    // Mã hóa Map phản hồi về chuỗi text: "OK key=val;key2=val2"
    // Bỏ qua các khóa bắt đầu bằng "_" (meta)
    public static String encodeResponse(Map<String,String> map) {
        StringBuilder sb = new StringBuilder();
        String status = map.getOrDefault("_status", "OK");
        sb.append(status);
        for (Map.Entry<String,String> e : map.entrySet()) {
            if (e.getKey().startsWith("_")) continue;
            sb.append(' ').append(e.getKey()).append('=').append(esc(e.getValue())).append(';');
        }
        if (sb.charAt(sb.length()-1)==';') sb.setLength(sb.length()-1);
        return sb.toString();
    }

    // Điểm vào xử lý mọi request text: parse -> switch theo cmd -> build resp
    public Map<String,String> handle(String line) {
        Map<String,String> req = parseLine(line);
        Map<String,String> resp = new LinkedHashMap<>();
        resp.put("_status","OK");
        resp.put("req", req.getOrDefault("id",""));

        if (!"REQ".equals(req.get("_verb"))) return err(resp,"bad_verb");

        String cmd = req.getOrDefault("cmd","");
        try {
            switch (cmd) {
                case "REGISTER" -> doRegister(req, resp);
                case "LOGIN"    -> doLogin(req, resp);

                case "LIST"     -> doListRooms(resp);
                case "CREATE"   -> doCreateRoom(req, resp);
                case "JOIN"     -> doJoin(req, resp);
                case "QUICK"    -> doQuick(req, resp);

                case "READY"    -> doReady(req, resp, true);
                case "UNREADY"  -> doReady(req, resp, false);
                case "LEAVE"    -> doLeave(req, resp);

                case "MOVE"     -> doMove(req, resp);
                case "STATE"    -> doState(req, resp);
                case "ROOMINFO" -> doRoomInfo(req, resp);

                case "CHAT"     -> doChat(req, resp);
                case "CHATLOG"  -> doChatLog(req, resp);

                case "HISTORY"  -> doHistory(req, resp);
                case "RANK"     -> doRank(resp);

                // Cơ chế rủ chơi lại (replay) sau khi trận đóng
                case "OFFER_REPLAY"   -> doOfferReplay(req, resp);
                case "ACCEPT_REPLAY"  -> doAcceptReplay(req, resp);
                case "DECLINE_REPLAY" -> doDeclineReplay(req, resp);

                default         -> err(resp,"unknown_cmd");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            err(resp,"exception");
        }
        return resp;
    }

    // Hook cũ (không dùng) khi socket ngắt
    public void onDisconnect(Socket s) { /* no-op */ }

    // Khi biết token cuối cùng của socket bị rớt: xử thua nếu đang chơi
    public void onDisconnectToken(String token) {
        if (token == null || token.isBlank()) return;
        String u = tokenToUser.get(token);
        forfeitIfPlaying(u);
    }

    // Đăng ký người dùng mới: validate username, hash password, ghi Store
    private void doRegister(Map<String,String> req, Map<String,String> resp) {
        String u = req.get("user"), p = req.get("pass");
        if (!validUser(u) || p==null || p.length()<3) { err(resp,"invalid_input"); return; }
        synchronized (store) {
            if (store.userExists(u)) { err(resp,"user_exists"); return; }
            store.addUser(u, hash(p));
        }
        resp.put("msg","registered");
    }

    // Đăng nhập: check hash, phát token mới, trả W/L/D
    private void doLogin(Map<String,String> req, Map<String,String> resp) {
        String u = req.get("user"), p = req.get("pass");
        if (!store.checkLogin(u, hash(p))) { err(resp,"bad_credentials"); return; }
        String t = UUID.randomUUID().toString();
        tokenToUser.put(t, u);
        resp.put("token", t);
        int[] wl = store.getWL(u);
        resp.put("wins", ""+wl[0]); resp.put("losses",""+wl[1]); resp.put("draws",""+wl[2]);
    }

    // Lấy user từ token trong request; nếu thiếu thì trả unauthorized
    private String userFromToken(Map<String,String> req, Map<String,String> resp) {
        String t = req.get("token");
        String u = tokenToUser.get(t);
        if (u==null) { err(resp,"unauthorized"); return null; }
        return u;
    }

    // Trả danh sách phòng ở trạng thái hiển thị được (waiting/ready/playing)
    private void doListRooms(Map<String,String> resp) {
        StringBuilder sb = new StringBuilder();
        for (Room r : rooms.values()) {
            if (!r.status.equals("waiting") && !r.status.equals("ready") && !r.status.equals("playing")) continue;
            sb.append(r.id).append(",").append(r.host==null?"":r.host).append(",")
              .append(r.guest==null?"":r.guest).append(",").append(r.status).append("|");
        }
        resp.put("rooms", sb.toString());
    }

    // Tạo phòng mới với host là user hiện tại; ghi log Store
    private void doCreateRoom(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        String id = "R-" + UUID.randomUUID().toString().substring(0,4).toUpperCase();
        Room r = new Room(id, u);
        rooms.put(id, r);
        store.appendRoomEvent(id, u, "", "waiting");
        resp.put("room", id);
    }

    // Tham gia phòng đang waiting; khi đủ 2 người -> bắt đầu playing, X đi trước
    private void doJoin(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r==null) { err(resp,"no_room"); return; }
        if (!"waiting".equals(r.status)) { err(resp,"room_unavailable"); return; }
        if (u.equals(r.host)) { err(resp,"cannot_join_own_room"); return; }

        r.guest = u;
        r.hostReady = r.guestReady = true;
        r.status = "playing";
        r.resetBoard();
        r.turn = 'X';

        store.appendRoomEvent(r.id, r.host, r.guest, "playing");
        resp.put("room", r.id);
        resp.put("start","true");
        resp.put("turn","X");
        resp.put("state", new String(r.board));
    }

    // Ghép nhanh: nếu hàng đợi có người -> tạo phòng và start ngay, nếu không thì xếp hàng
    private void doQuick(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        if (!quickQ.isEmpty()) {
            String other = quickQ.poll();
            if (u.equals(other)) { quickQ.offer(u); resp.put("msg","queued"); return; }

            String id = "R-" + UUID.randomUUID().toString().substring(0,4).toUpperCase();
            Room r = new Room(id, other);
            r.guest = u;
            r.hostReady = r.guestReady = true;
            r.status = "playing";
            r.resetBoard();
            r.turn = 'X';

            rooms.put(id, r);
            store.appendRoomEvent(id, r.host, r.guest, "playing");

            resp.put("room", id);
            resp.put("start","true");
            resp.put("turn","X");
            resp.put("state", new String(r.board));
        } else {
            quickQ.offer(u);
            resp.put("msg","queued");
        }
    }

    // Đặt trạng thái sẵn sàng/không sẵn sàng; khi cả hai sẵn sàng -> start
    private void doReady(Map<String,String> req, Map<String,String> resp, boolean set) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r==null) { err(resp,"no_room"); return; }
        if (u.equals(r.host)) r.hostReady = set;
        if (u.equals(r.guest)) r.guestReady = set;
        if (r.hostReady && r.guestReady) {
            r.status = "playing";
            r.resetBoard();
            r.turn = 'X';
            store.appendRoomEvent(r.id, r.host, r.guest, "playing");
            resp.put("start","true");
            resp.put("turn","X");
            resp.put("state", new String(r.board));
        } else resp.put("ready","ok");
    }

    // Thông tin phòng: host/guest/status/ready/turn/state hoặc winner khi closed
    private void doRoomInfo(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r==null) { err(resp,"no_room"); return; }
        resp.put("host", r.host==null?"":r.host);
        resp.put("guest", r.guest==null?"":r.guest);
        resp.put("status", r.status);
        resp.put("hostReady", String.valueOf(r.hostReady));
        resp.put("guestReady", String.valueOf(r.guestReady));
        if ("playing".equals(r.status)) {
            resp.put("turn", String.valueOf(r.turn));
            resp.put("state", new String(r.board));
        } else if ("closed".equals(r.status)) {
            String lastWin = store.getLastWinnerForRoom(r.id);
            if (!lastWin.isEmpty()) resp.put("winner", lastWin);
        }
    }

    // Nước đi: validate lượt/ô; cập nhật bàn; xét thắng/hòa; ghi Store; trả state/turn
    private void doMove(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r==null || !"playing".equals(r.status)) { err(resp,"not_playing"); return; }

        int idx;
        try { idx = Integer.parseInt(req.getOrDefault("idx","-1")); } catch (Exception e) { idx = -1; }
        if (idx<0 || idx>8 || r.board[idx]!=' ') { err(resp,"bad_move"); return; }

        char my = u.equals(r.host)?'X':(u.equals(r.guest)?'O':'?');
        if (my=='?' || my!=r.turn) { err(resp,"not_your_turn"); return; }

        r.board[idx] = my;
        r.moves.add(idx);
        r.turn = (r.turn=='X')?'O':'X';

        char winner = r.winner();
        if (winner!=' ') {
            // Kết thúc: có người thắng
            closeRoomAndClearReplay(r);

            String winUser = winner=='X'? r.host : r.guest;
            store.appendMatch(r.id, r.host, r.guest, winUser, r.moves);
            store.updateWL(winUser, "W");
            store.updateWL(winner=='X'? r.guest : r.host, "L");

            resp.put("state", new String(r.board));
            resp.put("status","closed");
            resp.put("end","win");
            resp.put("winner", winUser);

        } else if (r.full()) {
            // Kết thúc: hòa
            closeRoomAndClearReplay(r);

            store.appendMatch(r.id, r.host, r.guest, "draw", r.moves);
            store.updateWL(r.host,"D"); store.updateWL(r.guest,"D");

            resp.put("state", new String(r.board));
            resp.put("status","closed");
            resp.put("end","draw");

        } else {
            // Chưa kết thúc: trả state và lượt tiếp theo
            resp.put("state", new String(r.board));
            resp.put("turn", String.valueOf(r.turn));
        }
    }

    // Rời phòng: xử thua nếu đang chơi; sắp xếp lại vai trò; dọn phòng trống
    private void doLeave(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r == null) { err(resp,"no_room"); return; }

        if ("playing".equals(r.status)) {
            String loser  = u;
            String winner = loser.equals(r.host) ? r.guest : r.host;

            // Đóng trận và reset trạng thái replay
            closeRoomAndClearReplay(r);

            if (winner == null || winner.isBlank()) {
                store.appendRoomEvent(r.id, r.host==null?"":r.host, r.guest==null?"":r.guest, "closed");
            } else {
                store.appendMatch(r.id, r.host, r.guest, winner, r.moves);
                store.updateWL(winner, "W");
                store.updateWL(loser,  "L");
            }

            // Ghi nhận người rời để cập nhật host/guest/ready/status
            markLeft(r, u);

            // Nếu còn 1 người, chuyển về waiting (swap guest -> host khi cần)
            if (!"playing".equals(r.status) && ((r.host!=null && !r.host.isBlank()) ^ (r.guest!=null && !r.guest.isBlank()))) {
                if (r.host == null && r.guest != null) {
                    r.host = r.guest; r.guest = null;
                }
                r.hostReady = false; r.guestReady = false;
                r.status = "waiting";
                store.appendRoomEvent(r.id, r.host==null?"":r.host, "", "waiting");
            }

            // Dọn phòng nếu trống hoàn toàn
            cleanupRoomIfEmpty(r);

            resp.put("left","true");
            resp.put("status", r.status);
            return;
        }

        // Không ở trạng thái playing: cập nhật rời phòng theo vai trò
        if (u.equals(r.host)) {
            if (r.guest != null) {
                r.host = r.guest; r.guest = null;
                r.hostReady = false; r.guestReady = false;
                r.status = "waiting";
                store.appendRoomEvent(r.id, r.host, "", "waiting");
            } else {
                r.host = null;
                r.hostReady = false;
                r.status = "closed";
                store.appendRoomEvent(r.id, "", "", "closed");
            }
        } else if (u.equals(r.guest)) {
            r.guest = null;
            r.guestReady = false;
            if (!"playing".equals(r.status)) {
                r.status = "waiting";
                store.appendRoomEvent(r.id, r.host==null?"":r.host, "", "waiting");
            }
        }
        // Reset trạng thái replay và dọn phòng nếu cần
        r.replayOffer = null; r.replayAccepted = false; r.replayDeclined = false; r.replayPopupSent = false;
        cleanupRoomIfEmpty(r);

        resp.put("left","true");
        resp.put("status", r.status);
    }

    // Trả trạng thái phòng hiện tại; khi closed có thể trả thông tin replay popup
    private void doState(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r==null) { err(resp,"no_room"); return; }

        resp.put("status", r.status);
        resp.put("state", new String(r.board));
        resp.put("turn", String.valueOf(r.turn));

        if ("closed".equals(r.status)) {
            String win = store.getLastWinnerForRoom(r.id);
            if (!win.isEmpty()) { resp.put("end","win"); resp.put("winner", win); }
            else { resp.put("end","draw"); }

            // Điều khiển UI popup replay theo phía nhận/đề nghị
            if (r.replayOffer != null && !u.equals(r.replayOffer) && !r.replayPopupSent) {
                resp.put("offerReplay","true");
                resp.put("from", r.replayOffer);
                r.replayPopupSent = true;
            }

            if (r.replayDeclined && r.replayOffer != null && u.equals(r.replayOffer)) {
                resp.put("replayDeclined","true");
                r.replayDeclined = false;
                r.replayOffer = null;
                r.replayPopupSent = false;
            }

            if (r.replayAccepted) {
                resp.put("replayStart","true");
                r.replayAccepted = false; 
            }
        }
    }

    // Gửi chat và lưu vào Store theo room
    private void doChat(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        String room = req.get("room");
        String text = req.getOrDefault("text","");
        store.appendChat(room, u, text);
        resp.put("sent","true");
    }

    // Lấy log chat gọn cho 1 phòng (mặc định 20 dòng gần nhất)
    private void doChatLog(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        String room = req.get("room");
        String log = store.getChatCompact(room, 20);
        resp.put("log", log);
    }

    // Lịch sử cá nhân gọn (compact) theo user hiện tại
    private void doHistory(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        resp.put("history", store.getHistoryCompact(u));
    }

    // Bảng xếp hạng: ưu tiên API V2 nếu có, fallback từ V1
    private void doRank(Map<String,String> resp) {
        String s;
        try {
            var m = store.getClass().getMethod("getLeaderboardCompactV2");
            s = (String) m.invoke(store);
        } catch (Throwable ignore) {
            String v1 = store.getLeaderboardCompact();
            s = upgradeV1ToV2(v1);
        }
        resp.put("rank", s == null ? "" : s);
    }

    // Người chơi A mời chơi lại; đặt cờ replay trong Room
    private void doOfferReplay(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r==null) { err(resp,"no_room"); return; }

        r.replayOffer     = u;
        r.replayDeclined  = false;
        r.replayAccepted  = false;
        r.replayPopupSent = false; 

        resp.put("offerReplay","ok");
    }

    // Người còn lại chấp nhận: reset bàn, set playing, thông báo replayStart
    private void doAcceptReplay(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r==null) { err(resp,"no_room"); return; }
        if (r.replayOffer==null || u.equals(r.replayOffer)) { err(resp,"no_offer"); return; }

        r.resetBoard();
        r.turn = 'X';
        r.status = "playing";

        r.replayAccepted  = true;
        r.replayDeclined  = false;
        r.replayOffer     = null;
        r.replayPopupSent = false;

        store.appendRoomEvent(r.id, r.host, r.guest, "playing");

        resp.put("replayStart","true");
        resp.put("turn","X");
        resp.put("state", new String(r.board));
    }

    // Từ chối replay: set cờ để phía mời nhận thông báo
    private void doDeclineReplay(Map<String,String> req, Map<String,String> resp) {
        String u = userFromToken(req, resp); if (u==null) return;
        Room r = rooms.get(req.get("room"));
        if (r==null) { err(resp,"no_room"); return; }
        if (r.replayOffer==null || u.equals(r.replayOffer)) { err(resp,"no_offer"); return; }

        r.replayDeclined  = true;
        r.replayAccepted  = false;
        r.replayPopupSent = false;

        resp.put("replayDeclined","true");
    }

    // Validate username; hash SHA-256; util lỗi/escape/unescape cho giao thức text
    private static boolean validUser(String u) { return u!=null && u.matches("[a-zA-Z0-9_]{3,16}"); }
    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte x: b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { return s; }
    }
    private static Map<String,String> err(Map<String,String> resp, String msg) { resp.put("_status","ERR"); resp.put("msg",msg); return resp; }
    private static String esc(String v){ return v.replace("\\","\\\\").replace(";","\\;").replace("\n","\\n").replace(" ","%20"); }
    private static String unesc(String v){ return v.replace("%20"," ").replace("\\n","\n").replace("\\;",";").replace("\\\\","\\"); }

    // Cấu trúc Room: id/host/guest/status/ready/board/turn/moves + trạng thái replay
    static class Room {
        final String id;
        String host;
        String guest = null;
        String status = "waiting"; 
        boolean hostReady=false, guestReady=false;
        char[] board = new char[9];
        char turn='X';
        List<Integer> moves = new ArrayList<>();

        // Trạng thái quy trình đề nghị chơi lại
        String  replayOffer     = null;
        boolean replayDeclined  = false;
        boolean replayAccepted  = false;
        boolean replayPopupSent = false;

        Room(String id, String host){ this.id=id; this.host=host; resetBoard(); }
        void resetBoard(){ Arrays.fill(board,' '); moves.clear(); }
        boolean full(){ for(char c:board) if(c==' ') return false; return true; }
        char winner(){
            int[][] w={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
            for(int[] a:w){ if(board[a[0]]!=' ' && board[a[0]]==board[a[1]] && board[a[1]]==board[a[2]]) return board[a[0]]; }
            return ' ';
        }
    }

    // Đóng phòng và reset mọi cờ replay
    private static void closeRoomAndClearReplay(Room r) {
        r.status = "closed";
        r.replayOffer = null;
        r.replayAccepted = false;
        r.replayDeclined = false;
        r.replayPopupSent = false;
    }

    // Đánh dấu người rời phòng và cập nhật lại state/role/ready/status
    private static void markLeft(Room r, String user) {
        if (r == null || user == null) return;
        if (user.equals(r.host)) {
            r.host = null;
            r.hostReady = false;
        } else if (user.equals(r.guest)) {
            r.guest = null;
            r.guestReady = false;
        }
        r.replayOffer = null;
        r.replayAccepted = false;
        r.replayDeclined = false;
        r.replayPopupSent = false;

        if (!"playing".equals(r.status)) {
            boolean hasHost  = r.host  != null && !r.host.isBlank();
            boolean hasGuest = r.guest != null && !r.guest.isBlank();

            if (hasHost ^ hasGuest) {
                if (!hasHost && hasGuest) {
                    r.host = r.guest; r.guest = null;
                }
                r.hostReady = false; r.guestReady = false;
                r.status = "waiting";
            } else if (!hasHost && !hasGuest) {
                r.status = "closed";
            }
        }
    }

    // Xóa phòng nếu không còn ai; ghi event "removed" vào Store
    private void cleanupRoomIfEmpty(Room r) {
        if (r == null) return;
        if (r.host == null && (r.guest == null || r.guest.isBlank())) {
            rooms.remove(r.id);
            store.appendRoomEvent(r.id, "", "", "removed");
        }
    }

    // Xử thua khi user rớt kết nối trong lúc playing; sắp xếp lại room như LEAVE
    private void forfeitIfPlaying(String u) {
        if (u == null || u.isBlank()) return;
        for (Room r : rooms.values()) {
            if ("closed".equals(r.status)) continue;
            if (!"playing".equals(r.status)) continue;

            if (u.equals(r.host) || u.equals(r.guest)) {
                String winner = u.equals(r.host) ? r.guest : r.host;

                closeRoomAndClearReplay(r);

                if (winner == null || winner.isBlank()) {
                    store.appendRoomEvent(r.id,
                            r.host==null?"":r.host,
                            r.guest==null?"":r.guest,
                            "closed");
                } else {
                    store.appendMatch(r.id, r.host, r.guest, winner, r.moves);
                    store.updateWL(winner, "W");
                    store.updateWL(u, "L");
                }

                markLeft(r, u);

                if (!"playing".equals(r.status) &&
                    ((r.host!=null && !r.host.isBlank()) ^ (r.guest!=null && !r.guest.isBlank()))) {
                    if (r.host == null && r.guest != null) {
                        r.host = r.guest; r.guest = null;
                    }
                    r.hostReady = false;
                    r.guestReady = false;
                    r.status = "waiting";
                    store.appendRoomEvent(r.id, r.host==null?"":r.host, "", "waiting");
                }

                cleanupRoomIfEmpty(r);
            }
        }
    }

    // Nâng cấp định dạng leaderboard compact từ V1 sang V2 nếu Store chưa có V2
    private static String upgradeV1ToV2(String v1) {
        if (v1 == null || v1.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        String[] items = v1.split("\\|");
        for (String it : items) {
            if (it.isBlank()) continue;
            String[] p = it.split(":");
            String user = p.length>0 ? p[0] : "-";
            String wins = p.length>1 ? p[1] : "0";
            if (out.length()>0) out.append('|');
            out.append(user).append(':').append(wins).append(":0:0");
        }
        return out.toString();
    }
}
