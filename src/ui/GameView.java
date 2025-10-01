package ui;

import client.Net;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

public class GameView extends JFrame {
    private final Net net;
    private final String roomId;
    private final String username;

    // Board
    private final JButton[] cells = new JButton[9];

    // Top bar
    private final JLabel lblTurn   = new JLabel("Lượt đi: -");
    private final JLabel lblStatus = new JLabel("Trạng thái: -");
    private final JLabel lblResult = new JLabel("", SwingConstants.CENTER); // banner kết quả

    // Chat
    private final JTextArea chatLog = new JTextArea(12, 28);
    private final JTextField chatInp = new JTextField();
    private final JButton btnSend = new JButton("➤ Gửi");

    // Actions
    private final JButton btnReplay = new JButton("↻ Chơi lại");
    private final JButton btnExit   = new JButton("✖ Rời phòng");

    // Toast
    private final JLabel toast = new JLabel("", SwingConstants.LEFT);
    private Timer toastTimer;

    // Poll
    private Timer poller;

    // Room / state
    private char myMark = '?';
    private String host = "", guest = "";
    private boolean playingNow = false;
    private boolean ended = false;

    // Nhận biết kết thúc hợp lệ & trạng thái trước đó
    private boolean sawLegitEnd = false;   // true nếu đã từng nhận end=win|draw
    private String  lastStatus  = "";      // trạng thái tick trước ("playing"/"waiting"/"closed"...)

    // Replay popup
    private boolean replayDialogOpen = false;

    // Opponent & chat logic
    private boolean opponentWasHere = false;
    private boolean opponentQuitPopupShown = false;
    private String lastOpponent = null;

    // Chat filtering
    private boolean chatSuppressed = false; // không fetch chat khi chưa có đối thủ
    private boolean chatBaselineSet = false;
    private int chatBaselineCount = 0;      // số dòng chat cần bỏ qua (log cũ của phòng)

    // Colors
    private static final Color BG_ROOT = new Color(0xF5F6FA);
    private static final Color BG_CELL = Color.WHITE;
    private static final Color BG_DIM  = new Color(0xECEFF1);
    private static final Color FG_DIM  = new Color(0x9AA0A6);
    private static final Color BG_WIN  = new Color(0xF2C94C);
    private static final Color FG_WIN  = new Color(0x6B4F00);

    public GameView(Net net, String roomId, String username) {
        super("Cờ ca-rô - Trận đấu");
        this.net = net; this.roomId = roomId; this.username = username;

        // ===== Frame =====
        getContentPane().setBackground(BG_ROOT);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);

        // ===== Board =====
        JPanel board = new JPanel(new GridLayout(3,3,10,10));
        board.setBorder(new EmptyBorder(10,10,10,10));
        for (int i=0;i<9;i++) {
            final int idx = i;
            JButton b = new JButton(" ");
            b.setFont(b.getFont().deriveFont(Font.BOLD, 28f));
            b.setFocusPainted(false);
            b.setBackground(BG_CELL);
            b.setToolTipText("Ô " + (i+1));
            b.addActionListener(e -> move(idx));
            b.setEnabled(false);
            cells[i] = b;
            board.add(b);
        }
        JPanel boardWrap = titledPanel("Bàn cờ", board);

        // ===== Chat =====
        chatLog.setEditable(false);
        chatLog.setLineWrap(true);
        chatLog.setWrapStyleWord(true);
        JPanel chatBottom = new JPanel(new BorderLayout(6,0));
        chatInp.setToolTipText("Nhập nội dung và nhấn Enter để gửi");
        btnSend.setFocusable(false);
        btnSend.setToolTipText("Gửi tin nhắn");
        chatBottom.add(chatInp, BorderLayout.CENTER);
        chatBottom.add(btnSend, BorderLayout.EAST);
        JPanel chat = new JPanel(new BorderLayout(10,10));
        chat.setBorder(new EmptyBorder(10,10,10,10));
        chat.add(new JScrollPane(chatLog), BorderLayout.CENTER);
        chat.add(chatBottom, BorderLayout.SOUTH);
        JPanel chatWrap = titledPanel("🗨 Trò chuyện", chat);

        // ===== Top bar =====
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        lblTurn.setFont(lblTurn.getFont().deriveFont(Font.BOLD));
        top.add(lblTurn, BorderLayout.WEST);
        top.add(lblStatus, BorderLayout.CENTER);
        lblResult.setFont(new Font("Arial", Font.BOLD, 14));
        lblResult.setOpaque(true);
        lblResult.setVisible(false);
        lblResult.setBorder(new EmptyBorder(6, 10, 6, 10));
        lblResult.setBackground(new Color(0xE5E7EB));
        lblResult.setForeground(Color.DARK_GRAY);
        top.add(lblResult, BorderLayout.EAST);

        // ===== Buttons =====
        styleButtonPrimary(btnReplay);
        styleButtonDanger(btnExit);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
        bottom.setOpaque(false);
        bottom.add(btnReplay);
        bottom.add(btnExit);

        // ===== Toast =====
        toast.setOpaque(true);
        toast.setBackground(new Color(0xFCE8E6));
        toast.setForeground(new Color(0x8A1C16));
        toast.setFont(toast.getFont().deriveFont(Font.BOLD, 16f));
        toast.setBorder(new EmptyBorder(10,14,10,14));
        toast.setVisible(false);
        JPanel toastWrap = new JPanel(new BorderLayout());
        toastWrap.setOpaque(false);
        toastWrap.add(toast, BorderLayout.WEST);

        // ===== Layout =====
        JPanel center = new JPanel(new GridLayout(1,2,10,10));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(10,10,10,10));
        center.add(boardWrap);
        center.add(chatWrap);
        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(toastWrap, BorderLayout.WEST);
        south.add(bottom, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        // ===== Events =====
        btnSend.addActionListener(e -> sendChat());
        chatInp.addActionListener(e -> sendChat());
        btnExit.addActionListener(e -> confirmExit());
        btnReplay.addActionListener(e -> onReplayClicked());
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { confirmExit(); }});

        // Init & start polling
        initRoomInfo();

        // ===== Opponent & Chat init =====
        String opp = username.equals(host) ? guest : (username.equals(guest) ? host : null);
        opponentWasHere = (opp != null && !opp.isBlank());
        lastOpponent = (opp == null || opp.isBlank()) ? null : opp;
        opponentQuitPopupShown = false;

        // 🚩 baseline chat
        String baseResp = net.send("cmd=CHATLOG;token=" + net.token + ";room=" + roomId);
        String baseCompact = decode(kv(baseResp, "log"));
        chatBaselineCount = countChatItems(baseCompact);
        chatBaselineSet = true;

        chatSuppressed = !opponentWasHere;
        chatLog.setText("");

        showToast(username + " đã vào " + roomId);

        poller = new Timer(600, e -> pollState());
        poller.start();
        pollState();
    }

    // ===== UI helpers =====
    private JPanel titledPanel(String title, JComponent inner) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        TitledBorder tb = BorderFactory.createTitledBorder(title);
        tb.setTitleFont(tb.getTitleFont().deriveFont(Font.BOLD));
        wrap.setBorder(tb);
        wrap.add(inner, BorderLayout.CENTER);
        return wrap;
    }
    private void styleButtonPrimary(JButton b) {
        b.setBackground(new Color(0x22C55E));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
    }
    private void styleButtonDanger(JButton b) {
        b.setBackground(new Color(0xEF4444));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
    }
    private void showToast(String text) {
        toast.setText(" " + text + " ");
        toast.setVisible(true);
        if (toastTimer != null) toastTimer.stop();
        toastTimer = new Timer(3000, e -> toast.setVisible(false));
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    // ===== Init =====
    private void initRoomInfo() {
        String resp = net.send("cmd=ROOMINFO;token="+net.token+";room="+roomId);
        host = decode(kv(resp,"host"));
        guest = decode(kv(resp,"guest"));
        if (username.equals(host)) myMark = 'X';
        else if (username.equals(guest)) myMark = 'O';
        else myMark = '?';
        setTitle("Cờ ca-rô - Trận đấu (" + roomId + ")");
    }

    // ===== Actions =====
    private void move(int idx) {
        if (!" ".equals(cells[idx].getText())) return;
        String resp = net.send("cmd=MOVE;token="+net.token+";room="+roomId+";idx="+idx);
        if (resp.startsWith("ERR")) return;
        applyServerResp(resp);
    }

    private void onReplayClicked() {
        if (!ended) {
            JOptionPane.showMessageDialog(this,
                    "Ván hiện tại chưa kết thúc.\nChỉ được mời \"Chơi lại\" sau khi ván đã kết thúc.",
                    "Chưa thể chơi lại",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        net.send("cmd=OFFER_REPLAY;token="+net.token+";room="+roomId);
        showToast("Đã gửi lời mời chơi lại...");
    }

    private void pollState() {
        // 1) Đồng bộ host/guest/myMark
        String info = net.send("cmd=ROOMINFO;token="+net.token+";room="+roomId);
        host  = decode(kv(info,"host"));
        guest = decode(kv(info,"guest"));
        char newMark = username.equals(host) ? 'X' : (username.equals(guest) ? 'O' : '?');
        if (newMark != myMark) myMark = newMark;

        // 2) Xác định đối thủ hiện tại
        String opponent = username.equals(host) ? guest : (username.equals(guest) ? host : null);
        boolean opponentNowPresent = opponent != null && !opponent.isBlank();
        String normOpp = (opponent == null || opponent.isBlank()) ? null : opponent;

        // Nếu đổi đối thủ
        if ((lastOpponent == null && normOpp != null) ||
            (lastOpponent != null && (normOpp == null || !lastOpponent.equals(normOpp)))) {

            lastOpponent = normOpp;

            if (normOpp != null) {
                String baseResp = net.send("cmd=CHATLOG;token="+net.token+";room="+roomId);
                String baseCompact = decode(kv(baseResp,"log"));
                chatBaselineCount = countChatItems(baseCompact);
                chatBaselineSet = true;
                chatSuppressed = false;
            } else {
                chatBaselineSet = false;
                chatBaselineCount = 0;
                chatSuppressed = true;
            }
            chatLog.setText("");
            opponentQuitPopupShown = false;
        }

        // 3) State & UI
        String resp = net.send("cmd=STATE;token="+net.token+";room="+roomId);

        // Lưu trạng thái tick trước, sau đó mới cập nhật từ resp
        String prevStatus = lastStatus;
        applyServerResp(resp); // cập nhật playingNow/ended/sawLegitEnd & lastStatus

        // 4) Popup khi đối thủ rời
        if (opponentWasHere && !opponentNowPresent) {
            if (!opponentQuitPopupShown) {
                boolean forfeitMidGame = "playing".equalsIgnoreCase(prevStatus) && !sawLegitEnd;
                String msg = forfeitMidGame
                        ? "Đối thủ đã thoát phòng.\nBạn thắng!"
                        : "Đối thủ đã thoát trận.";
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, msg, "Đối thủ thoát", JOptionPane.INFORMATION_MESSAGE)
                );
                opponentQuitPopupShown = true;
            }
            chatLog.setText("");
            chatSuppressed = true;
            chatBaselineSet = false;
            chatBaselineCount = 0;
        }
        opponentWasHere = opponentNowPresent;

        // 5) Replay handling
        if (resp.contains("replayStart=true")) {
            resetBoardStyle();
            for (int i=0;i<9;i++) cells[i].setText(" ");
            ended = false; playingNow = true;
            sawLegitEnd = false;
            replayDialogOpen = false;
            opponentQuitPopupShown = false;
            showToast("Ván mới đã bắt đầu!");
        } else if (resp.contains("replayDeclined=true")) {
            showToast("Đối thủ đã từ chối chơi lại.");
            replayDialogOpen = false;
        } else if (resp.contains("offerReplay=true")) {
            String from = decode(kv(resp,"from"));
            if (!username.equals(from) && !replayDialogOpen) {
                replayDialogOpen = true;
                SwingUtilities.invokeLater(() -> {
                    int opt = JOptionPane.showConfirmDialog(
                            this,
                            "Đối thủ muốn chơi lại.\nBạn có đồng ý không?",
                            "Lời mời chơi lại",
                            JOptionPane.YES_NO_OPTION
                    );
                    if (opt == JOptionPane.YES_OPTION) {
                        net.send("cmd=ACCEPT_REPLAY;token="+net.token+";room="+roomId);
                    } else {
                        net.send("cmd=DECLINE_REPLAY;token="+net.token+";room="+roomId);
                        net.send("cmd=LEAVE;token="+net.token+";room="+roomId);
                        safeClose(false);
                    }
                    replayDialogOpen = false;
                });
            }
        }

        // 6) Chat fetch
        if (!chatSuppressed) {
            String clog = net.send("cmd=CHATLOG;token="+net.token+";room="+roomId);
            String compact = decode(kv(clog, "log"));
            String[] items = compact.isEmpty() ? new String[0] : compact.split("\\|");
            int n = items.length;
            int start = chatBaselineSet ? Math.min(chatBaselineCount, n) : 0;
            String view = (start >= n) ? "" : String.join("\n", Arrays.copyOfRange(items, start, n));
            chatLog.setText(view);
            chatLog.setCaretPosition(chatLog.getDocument().getLength());
        }
    }

    private void applyServerResp(String resp) {
        String status = decode(kv(resp,"status"));
        if (!status.isEmpty()) lblStatus.setText("Trạng thái: " + status);

        String turn = decode(kv(resp,"turn"));
        if (!turn.isEmpty()) {
            boolean isMyTurn = (myMark!='?' && turn.charAt(0)==myMark);
            lblTurn.setText("Lượt đi: " + turn + (isMyTurn ? " (bạn)" : ""));
        }

        String state = decode(kv(resp,"state"));
        if (!state.isEmpty() && state.length() >= 9) {
            resetBoardStyle();
            for (int i=0;i<9;i++) {
                char c = state.charAt(i);
                cells[i].setText(c == ' ' ? " " : String.valueOf(c));
            }
        }

        // cập nhật playing/ended
        playingNow = "playing".equalsIgnoreCase(status);

        if (playingNow) {
            ended = false;
            if (lblResult.isVisible()) {
                lblResult.setVisible(false);
                lblResult.setText("");
            }
        }

        boolean myTurn = playingNow && !turn.isEmpty() && myMark!='?' && turn.charAt(0)==myMark;
        for (int i=0;i<9;i++) cells[i].setEnabled(myTurn && " ".equals(cells[i].getText()));

        // --- xử lý kết thúc ván (hòa/thắng/thua) ---
        String end    = decode(kv(resp,"end"));        // "win" | "draw" | ""
        String winner = decode(kv(resp,"winner"));     // có thể là "draw" tùy server

        boolean isDraw = "draw".equalsIgnoreCase(end) || "draw".equalsIgnoreCase(winner);

        if (isDraw) {
            lblStatus.setText("Kết quả: Hòa");
            setResultBanner("draw");
            dimAll();
            for (JButton b : cells) b.setEnabled(false);
            ended = true; playingNow = false;
            sawLegitEnd = true;
        } else if ("win".equalsIgnoreCase(end)) {
            if (state != null && state.length() >= 9) {
                int[] line = findWinningLine(state);
                if (line != null) highlightWin(line); else dimAll();
            }
            boolean iWin = username.equals(winner);
            lblStatus.setText("Kết quả: " + (iWin ? "Bạn thắng" : (winner + " thắng")));
            setResultBanner(iWin ? "win" : "lose");
            for (JButton b : cells) b.setEnabled(false);
            ended = true; playingNow = false;
            sawLegitEnd = true;
        } else {
            if (lblResult.isVisible()) {
                lblResult.setVisible(false);
                lblResult.setText("");
            }
        }

        // Lưu trạng thái hiện tại để tick sau dùng làm prevStatus
        if (!status.isEmpty()) lastStatus = status;
    }

    // Banner nhỏ
    private void setResultBanner(String type) {
        switch (type) {
            case "win" -> {
                lblResult.setText("  Bạn đã THẮNG  ");
                lblResult.setBackground(new Color(0xDCFCE7));
                lblResult.setForeground(new Color(0x166534));
                lblResult.setBorder(new EmptyBorder(6, 10, 6, 10));
            }
            case "lose" -> {
                lblResult.setText("  Bạn đã THUA  ");
                lblResult.setBackground(new Color(0xFEE2E2));
                lblResult.setForeground(new Color(0x991B1B));
                lblResult.setBorder(new EmptyBorder(6, 10, 6, 10));
            }
            default -> {
                lblResult.setText("  HÒA  ");
                lblResult.setBackground(new Color(0xFEF3C7));
                lblResult.setForeground(new Color(0x92400E));
                lblResult.setBorder(new EmptyBorder(6, 10, 6, 10));
            }
        }
        lblResult.setVisible(true);
        lblResult.revalidate();
        lblResult.repaint();
    }

    // ===== Exit =====
    private void confirmExit() {
        if (ended || !playingNow) {
            try { net.send("cmd=LEAVE;token="+net.token+";room="+roomId); } catch (Exception ignore) {}
            safeClose(false);
            return;
        }
        int opt = JOptionPane.showConfirmDialog(
                this,
                "Rời phòng bây giờ sẽ bị xử THUA.\nBạn có chắc muốn rời phòng?",
                "Xác nhận rời phòng",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (opt == JOptionPane.YES_OPTION) safeClose(true);
    }

    private void safeClose(boolean sendForfeit) {
        try { if (sendForfeit) net.send("cmd=LEAVE;token="+net.token+";room="+roomId); }
        catch (Exception ignore) {}

        if (poller != null) poller.stop();
        dispose();

        SwingUtilities.invokeLater(() -> {
            JFrame lobby = openLobby();
            String msg = sendForfeit ? "Bạn đã rời phòng và bị xử thua." : "Bạn đã rời phòng.";
            JOptionPane.showMessageDialog(lobby, msg, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private JFrame openLobby() {
        try {
            ui.LobbyView lv = new ui.LobbyView(net, username);
            lv.setVisible(true);
            return lv;
        } catch (Throwable t) {
            JFrame f = new JFrame("Sảnh chờ");
            f.setSize(400,200);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setVisible(true);
            return f;
        }
    }

    private void sendChat() {
        String txt = chatInp.getText().trim();
        if (txt.isEmpty()) return;
        net.send("cmd=CHAT;token="+net.token+";room="+roomId+";text="+esc(txt));
        chatInp.setText("");
    }

    // ===== Board visuals =====
    private void resetBoardStyle() {
        for (JButton b : cells) {
            b.setBackground(BG_CELL);
            b.setForeground(Color.DARK_GRAY);
            b.setFont(b.getFont().deriveFont(Font.BOLD, 28f));
        }
    }
    private void highlightWin(int[] line) {
        for (int i=0;i<9;i++) {
            boolean inLine = (i==line[0] || i==line[1] || i==line[2]);
            if (inLine) {
                cells[i].setBackground(BG_WIN);
                cells[i].setForeground(FG_WIN);
                cells[i].setFont(cells[i].getFont().deriveFont(Font.BOLD, 30f));
            } else {
                cells[i].setBackground(BG_DIM);
                cells[i].setForeground(FG_DIM);
            }
        }
    }
    private void dimAll() {
        for (JButton b : cells) {
            b.setBackground(BG_DIM);
            b.setForeground(FG_DIM);
        }
    }
    private int[] findWinningLine(String s) {
        int[][] w = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] a : w) {
            char c0 = s.charAt(a[0]);
            if (c0!=' ' && c0==s.charAt(a[1]) && c0==s.charAt(a[2])) return a;
        }
        return null;
    }

    // ===== helpers =====
    private static String kv(String line, String key) {
        for (String part : line.split("[ ;]")) {
            if (part.startsWith(key+"=")) return part.substring((key+"=").length()).replaceAll(";$","");
        }
        return "";
    }
    private static String decode(String v){
        if (v == null) return "";
        return v.replace("%20"," ")
                .replace("\\n","\n")
                .replace("\\;",";")
                .replace("\\\\","\\");
    }
    private static String esc(String s){ return s.replace(";", "\\;"); }

    private static int countChatItems(String compact) {
        if (compact == null || compact.isEmpty()) return 0;
        return compact.split("\\|", -1).length - (compact.endsWith("|") ? 1 : 0);
    }
}
