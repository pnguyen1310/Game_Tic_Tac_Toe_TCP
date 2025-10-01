package ui;

import client.Net;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class HistoryView extends JFrame {

    private final Net net;
    private final String username;

    private final HistTableModel model = new HistTableModel();
    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel(" ");

    public HistoryView(Net net, String username) {
        super("Lịch sử trận • " + username);
        this.net = net;
        this.username = username;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(760, 500);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(new Color(0xF3F5F7));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Các trận của bạn");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(title);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        JButton btnClose = outlineButton("Đóng");
        JButton btnRefresh = outlineButton("Làm mới");
        status.setForeground(new Color(0x667085));
        right.add(status);
        right.add(btnClose);
        right.add(btnRefresh);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        // Table
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);

        table.getColumnModel().getColumn(0).setPreferredWidth(60);   // STT
        table.getColumnModel().getColumn(1).setPreferredWidth(200);  // Mã trận
        table.getColumnModel().getColumn(2).setPreferredWidth(180);  // Đối thủ
        table.getColumnModel().getColumn(3).setPreferredWidth(100);  // Kết quả
        table.getColumnModel().getColumn(4).setPreferredWidth(200);  // Thời gian

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(new Color(242,244,247),1,true));

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        btnClose.addActionListener(e -> dispose());
        btnRefresh.addActionListener(e -> fetchHistory());

        fetchHistory();
    }

    private void fetchHistory() {
        status.setText("Đang tải…");
        String resp = net.send("cmd=HISTORY;token=" + net.token);

        List<Row> rows = parseHistoryOrItems(resp);

        // Thử biến thể có user nếu chưa có gì
        if (rows.isEmpty()) {
            String resp2 = net.send("cmd=HISTORY;token=" + net.token + ";user=" + username);
            rows = parseHistoryOrItems(resp2);
            if (rows.isEmpty()) {
                model.setRows(rows);
                status.setText("Không có trận nào • raw: " + shorten(resp2.isBlank()?resp:resp2));
                return;
            } else {
                status.setText("Đã tải (" + rows.size() + ")");
                model.setRows(rows);
                return;
            }
        }

        status.setText("Đã tải (" + rows.size() + ")");
        model.setRows(rows);
    }

    /** Ưu tiên key 'history=', dự phòng 'items='; record có thể tách bằng | ; hoặc newline. */
    private List<Row> parseHistoryOrItems(String resp) {
        List<Row> out = new ArrayList<>();
        if (resp == null) return out;

        String payload = kv(resp, "history");
        if (payload == null || payload.isBlank()) {
            payload = kv(resp, "items");
        }
        if (payload == null || payload.isBlank()) return out;

        String[] recs = payload.split("\\||;|\\r?\\n");
        int seq = 1;
        for (String rec : recs) {
            if (rec == null) continue;
            rec = rec.trim();
            if (rec.isEmpty()) continue;

            // Ưu tiên tách theo ':', nếu không có dùng ','
            String[] p = rec.contains(":") ? rec.split(":") : rec.split(",");
            String mid = get(p, 0);
            String opponent = "";
            String result = "";
            String time = "";

            // Nếu có >=4 phần: mid, opp, result, time
            if (p.length >= 4) {
                opponent = get(p,1);
                result   = get(p,2);
                time     = get(p,3);
            } else if (p.length == 3) {
                // Có thể là mid, result, time
                result = get(p,1);
                time   = get(p,2);
            } else if (p.length == 2) {
                // mid, maybe user or result/time
                String second = get(p,1);
                if (looksLikeTime(second)) time = second;
                else if (!second.equalsIgnoreCase(username)) opponent = second;
                else result = "";
            }
            if (!mid.isBlank()) {
                out.add(new Row(String.format("%04d", seq++), mid, opponent, result, time));
            }
        }
        return out;
    }

    private static boolean looksLikeTime(String s) {
        String t = s == null ? "" : s.trim();
        return t.matches(".*\\d{4}.*") || t.contains(":") || t.contains("/") || t.contains("-");
    }

    private static String kv(String line, String key) {
        for (String part : line.split("[ ;]")) {
            if (part.startsWith(key + "=")) {
                return part.substring((key + "=").length()).replaceAll(";$", "");
            }
        }
        return null;
    }

    private static String get(String[] a, int i) {
        return (i >= 0 && i < a.length) ? a[i].trim() : "";
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }

    // ----- Table model -----
    private static class Row {
        final String no, matchId, opponent, result, time;
        Row(String no, String matchId, String opponent, String result, String time) {
            this.no = no; this.matchId = matchId; this.opponent = opponent; this.result = result; this.time = time;
        }
    }

    private static class HistTableModel extends AbstractTableModel {
        private final String[] cols = {"STT", "Mã trận", "Đối thủ", "Kết quả", "Thời gian"};
        private final List<Row> data = new ArrayList<>();
        void setRows(List<Row> rows) { data.clear(); data.addAll(rows); fireTableDataChanged(); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            Row x = data.get(r);
            return switch (c) {
                case 0 -> x.no;
                case 1 -> x.matchId;
                case 2 -> x.opponent;
                case 3 -> x.result;
                case 4 -> x.time;
                default -> "";
            };
        }
        @Override public boolean isCellEditable(int r, int c) { return false; }
    }

    // ----- UI helpers -----
    private JButton outlineButton(String text) {
        JButton b = new JButton(text);
        b.setForeground(new Color(0x2D8CFF));
        b.setBackground(Color.WHITE);
        b.setBorder(new LineBorder(new Color(0x2D8CFF), 1, true));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 12f));
        b.setPreferredSize(new Dimension(110, 32));
        return b;
    }
}
