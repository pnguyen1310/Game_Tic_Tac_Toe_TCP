package server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private final Path users, events;
    private final Map<String,User> usersByName = new ConcurrentHashMap<>();

    public Store(String usersFile, String eventsFile) {
        this.users = Paths.get(usersFile);
        this.events = Paths.get(eventsFile);
        loadUsers();
    }

    public void initIfMissing() {
        try {
            Files.createDirectories(users.getParent());
            if (!Files.exists(users)) Files.createFile(users);
            if (!Files.exists(events)) Files.createFile(events);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    // ===== Users =====
    private void loadUsers() {
        if (!Files.exists(users)) return;
        try (BufferedReader br = Files.newBufferedReader(users, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split("\\|");
                if (p.length<5) continue;
                usersByName.put(p[0], new User(p[0], p[1], i(p[2]), i(p[3]), i(p[4])));
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private int i(String s){ try { return Integer.parseInt(s); } catch(Exception e){ return 0; } }

    private void persistUsers() {
        try (BufferedWriter bw = Files.newBufferedWriter(users, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (User u : usersByName.values()) {
                bw.write(u.name+"|"+u.pwHash+"|"+u.wins+"|"+u.losses+"|"+u.draws);
                bw.newLine();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public boolean userExists(String name){ return usersByName.containsKey(name); }
    public void addUser(String name, String pwHash){
        usersByName.put(name, new User(name, pwHash, 0,0,0));
        persistUsers();
        appendEvent("user", Map.of("u",name,"created",Instant.now().toString()));
    }
    public boolean checkLogin(String name, String pwHash){
        User u = usersByName.get(name); return u!=null && u.pwHash.equals(pwHash);
    }
    public int[] getWL(String name){
        User u = usersByName.get(name); if (u==null) return new int[]{0,0,0};
        return new int[]{u.wins,u.losses,u.draws};
    }
    public void updateWL(String name, String res){
        User u = usersByName.get(name); if (u==null) return;
        switch (res){ case "W" -> u.wins++; case "L" -> u.losses++; case "D" -> u.draws++; }
        persistUsers();
    }

    // ===== Events =====
    public void appendRoomEvent(String roomId, String host, String guest, String status) {
        appendEvent("room", Map.of("id",roomId,"host",host,"guest",guest,"status",status,"ts",Instant.now().toString()));
    }

    public void appendMatch(String roomId, String x, String o, String winner, List<Integer> moves) {
        Map<String,String> m = new LinkedHashMap<>();
        m.put("id","M"+System.currentTimeMillis());
        m.put("room",roomId); m.put("x",x); m.put("o",o); m.put("winner",winner);
        m.put("moves", joinInts(moves)); m.put("ts",Instant.now().toString());
        appendEvent("match", m);
    }

    public void appendChat(String room, String from, String text) {
        appendEvent("chat", Map.of("room",room,"from",from,"text",text,"ts",Instant.now().toString()));
    }

    /** Trả dạng: matchId:opponent:result:timestamp|... */
    public String getHistoryCompact(String user) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = Files.newBufferedReader(events, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("kind=match")) continue;
                Map<String,String> m = parseEvent(line);

                String x  = m.get("x");
                String o  = m.get("o");
                String id = m.get("id");
                String ts = m.getOrDefault("ts", "");
                String w  = m.getOrDefault("winner", "");

                if (!(user.equals(x) || user.equals(o))) continue;

                String opp = user.equals(x) ? o : x;
                if (opp == null) opp = "";

                String result;
                if ("draw".equalsIgnoreCase(w)) {
                    result = "Draw";
                } else if (w == null || w.isBlank()) {
                    result = "Aborted";
                } else if (user.equals(w)) {
                    result = "Win";
                } else {
                    result = "Loss";
                }

                if (id == null || id.isBlank()) id = "M"+System.currentTimeMillis();

                sb.append(id).append(":")
                  .append(opp).append(":")
                  .append(result).append(":")
                  .append(ts).append("|");
            }
        } catch (IOException e) { e.printStackTrace(); }
        return sb.toString();
    }

    /** Phiên bản cũ: chỉ có username:wins|... */
    public String getLeaderboardCompact() {
        List<User> list = new ArrayList<>(usersByName.values());
        list.sort((a,b)->Integer.compare(b.wins, a.wins));
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<Math.min(10,list.size());i++) {
            User u = list.get(i);
            sb.append(u.name).append(":").append(u.wins).append("|");
        }
        return sb.toString();
    }

    public String getLeaderboardCompactV2() {
        List<User> list = new ArrayList<>(usersByName.values());
        list.sort((a,b)->Integer.compare(b.wins, a.wins));
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<Math.min(10,list.size());i++) {
            User u = list.get(i);
            sb.append(u.name).append(":")
              .append(u.wins).append(":")
              .append(u.losses).append(":")
              .append(u.draws).append("|");
        }
        return sb.toString();
    }

    public String getChatCompact(String room, int limit) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(events, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("kind=chat")) continue;
                Map<String,String> m = parseEvent(line);
                if (room.equals(m.get("room"))) {
                    lines.add(m.get("from")+": "+m.getOrDefault("text",""));
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        int from = Math.max(0, lines.size()-limit);
        StringBuilder sb = new StringBuilder();
        for (int i=from;i<lines.size();i++) sb.append(lines.get(i)).append("|");
        return sb.toString();
    }

    public String getLastWinnerForRoom(String room) {
        String winner = "";
        try (BufferedReader br = Files.newBufferedReader(events, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("kind=match")) continue;
                Map<String,String> m = parseEvent(line);
                if (room.equals(m.get("room"))) winner = m.getOrDefault("winner","");
            }
        } catch (IOException e) { e.printStackTrace(); }
        return winner==null?"":winner;
    }

    private synchronized void appendEvent(String kind, Map<String,String> kv) {
        try (BufferedWriter bw = Files.newBufferedWriter(events, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            StringBuilder sb = new StringBuilder();
            sb.append("kind=").append(kind).append(';');
            for (Map.Entry<String,String> e : kv.entrySet()) {
                sb.append(e.getKey()).append('=').append(esc(e.getValue())).append(';');
            }
            bw.write(sb.toString());
            bw.newLine();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static Map<String,String> parseEvent(String line) {
        Map<String,String> m = new LinkedHashMap<>();
        for (String kv : line.split(";")) {
            if (kv.isBlank()) continue;
            int i = kv.indexOf('=');
            if (i<0) continue;
            m.put(kv.substring(0,i), unesc(kv.substring(i+1)));
        }
        return m;
    }

    private static String joinInts(List<Integer> moves) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<moves.size();i++){ if(i>0) sb.append(','); sb.append(moves.get(i)); }
        return sb.toString();
    }

    private static String esc(String v){ return v.replace("\\","\\\\").replace(";","\\;").replace("\n","\\n"); }
    private static String unesc(String v){ return v.replace("\\n","\n").replace("\\;",";").replace("\\\\","\\"); }

    static class User {
        final String name, pwHash; int wins, losses, draws;
        User(String n,String p,int w,int l,int d){ name=n; pwHash=p; wins=w; losses=l; draws=d; }
    }
}
