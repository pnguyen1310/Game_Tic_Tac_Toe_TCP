package ui;

import client.Net;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardView extends JFrame {
    private final Net net;

    private final RankModel model = new RankModel();
    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel(" ");

    // Icon top 3 (scale 24px) – ảnh đặt tại src/resources/
    private final ImageIcon goldIcon   = loadIcon("/resources/gold.png",   24);
    private final ImageIcon silverIcon = loadIcon("/resources/silver.png", 24);
    private final ImageIcon bronzeIcon = loadIcon("/resources/bronze.png", 24);

    public LeaderboardView(Net net) {
        super("Bảng xếp hạng");
        this.net = net;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(760, 500);
        setLocationRelativeTo(null);
        setResizable(false);

        // ===== Root =====
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(new Color(0xF3F5F7));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        // ===== Header (giống HistoryView) =====
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Bảng xếp hạng");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(title);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        JButton btnClose   = outlineButton("Đóng");
        JButton btnRefresh = outlineButton("Làm mới");
        status.setForeground(new Color(0x667085));
        right.add(status);
        right.add(btnClose);
        right.add(btnRefresh);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        // ===== Card + Table =====
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(0xE5E7EB), 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // Bảng
        table.setFillsViewportHeight(true);
        table.setRowHeight(32);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(false);
        JTableHeader th = table.getTableHeader();
        th.setFont(th.getFont().deriveFont(Font.BOLD, 13f));
        th.setBackground(new Color(0xF0F2F5));

        // độ rộng cột
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);

        // căn giữa số liệu
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(center);
        table.getColumnModel().getColumn(2).setCellRenderer(center);
        table.getColumnModel().getColumn(3).setCellRenderer(center);
        table.getColumnModel().getColumn(4).setCellRenderer(center);

        // renderer cột Hạng -> icon top 3
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setText(null);
                lbl.setIcon(null);
                if (row == 0 && goldIcon != null) {
                    lbl.setIcon(goldIcon);   lbl.setToolTipText("Hạng 1");
                } else if (row == 1 && silverIcon != null) {
                    lbl.setIcon(silverIcon); lbl.setToolTipText("Hạng 2");
                } else if (row == 2 && bronzeIcon != null) {
                    lbl.setIcon(bronzeIcon); lbl.setToolTipText("Hạng 3");
                } else {
                    lbl.setText(value == null ? "" : value.toString());
                    lbl.setToolTipText(null);
                }
                return lbl;
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(new Color(242,244,247),1,true));
        card.add(scroll, BorderLayout.CENTER);

        // Mount
        root.add(header, BorderLayout.NORTH);
        root.add(card, BorderLayout.CENTER);

        // Actions
        btnClose.addActionListener(e -> dispose());
        btnRefresh.addActionListener(e -> reload());

        // Load lần đầu
        reload();
    }

    private void reload() {
        status.setText("Đang tải…");
        String resp = net.send("cmd=RANK");            // server trả key rank=
        String rank = kv(resp, "rank");
        List<Row> rows = parseRank(rank);

        if (rows.isEmpty()) {
            status.setText("Không có dữ liệu • raw: " + shorten(resp));
        } else {
            model.setData(rows);
            status.setText("Đã tải (" + rows.size() + ")");
        }
    }

    // ===== Parse =====
    /** server: "user:wins[:losses][:draws]|..." */
    private static List<Row> parseRank(String s) {
        List<Row> list = new ArrayList<>();
        if (s == null || s.isBlank()) return list;
        int rank = 1;
        for (String rec : s.split("\\|")) {
            if (rec.isBlank()) continue;
            String[] p = rec.split(":");
            String user   = p.length>0 ? p[0] : "";
            int wins      = p.length>1 ? toInt(p[1]) : 0;
            int losses    = p.length>2 ? toInt(p[2]) : 0;
            int draws     = p.length>3 ? toInt(p[3]) : 0;
            int total = wins + losses + draws;
            String rate = total>0 ? String.format("%.1f%%", wins*100.0/total) : "0%";
            list.add(new Row(String.valueOf(rank++), user, String.valueOf(wins), String.valueOf(losses), rate));
        }
        return list;
    }
    private static int toInt(String s){ try{ return Integer.parseInt(s.trim()); }catch(Exception e){ return 0; } }

    private static String kv(String line, String key) {
        if (line == null) return "";
        for (String part : line.split("[ ;]")) {
            if (part.startsWith(key+"="))
                return part.substring((key+"=").length()).replaceAll(";$","");
        }
        return "";
    }
    private static String shorten(String s){
        if (s==null) return "";
        s = s.replaceAll("\\s+"," ").trim();
        return s.length()>120 ? s.substring(0,120)+"…" : s;
    }

    private static ImageIcon loadIcon(String pathOnClasspath, int size) {
        try {
            var url = LeaderboardView.class.getResource(pathOnClasspath);
            if (url == null) return null;
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) { return null; }
    }

    // ===== Model =====
    private static class Row { 
        final String rank,user,wins,losses,rate;
        Row(String r,String u,String w,String l,String rt){ rank=r; user=u; wins=w; losses=l; rate=rt; }
    }
    private static class RankModel extends AbstractTableModel {
        private final String[] cols = {"Hạng", "Người chơi", "Thắng", "Thua", "Tỉ lệ thắng"};
        private List<Row> data = new ArrayList<>();
        public void setData(List<Row> rows){ data = rows; fireTableDataChanged(); }
        @Override public int getRowCount(){ return data.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }
        @Override public Object getValueAt(int r,int c){
            Row x = data.get(r);
            return switch(c){
                case 0 -> x.rank;
                case 1 -> x.user;
                case 2 -> x.wins;
                case 3 -> x.losses;
                case 4 -> x.rate;
                default -> "";
            };
        }
        @Override public boolean isCellEditable(int r,int c){ return false; }
    }

    // ===== UI helpers (giống HistoryView) =====
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
