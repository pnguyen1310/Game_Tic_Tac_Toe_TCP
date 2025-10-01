package ui;

import client.Net;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

public class LobbyView extends JFrame {
    private final Net net;
    private final String username;

    private final RoomsTableModel model = new RoomsTableModel();
    private final JTable table = new JTable(model);
    private final JTextField roomIdField = new JTextField();

    private Timer poller;

    public LobbyView(Net net, String username) {
        super("Sảnh chờ • " + username);
        this.net = net; this.username = username;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 600);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(new Color(0xF3F5F7));
        setContentPane(root);

        // ===== TOP =====
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel title = new JLabel("Danh sách phòng");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JPanel titleWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleWrap.setOpaque(false);
        titleWrap.add(title);
        top.add(titleWrap, BorderLayout.NORTH);

        JPanel leftRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        leftRow.setOpaque(false);

        JButton btnCreate = greenButton("TẠO PHÒNG");

        // ---- Join bar: [ Room ID .... ][ ➜ ]
        JPanel joinPanel = new JPanel(new BorderLayout());
        joinPanel.setBorder(new LineBorder(new Color(0xD0D5DD), 1, true));
        joinPanel.setBackground(Color.WHITE);
        joinPanel.setPreferredSize(new Dimension(320, 36));

        styleInner(roomIdField);
        installPlaceholder(roomIdField, "Nhập mã phòng");

        JButton btnJoin = new JButton("\u279C"); // ➜ icon
        btnJoin.setBackground(new Color(0x2ECC71));
        btnJoin.setForeground(Color.WHITE);
        btnJoin.setBorderPainted(false);
        btnJoin.setFocusPainted(false);
        btnJoin.setFont(btnJoin.getFont().deriveFont(Font.BOLD, 16f));
        btnJoin.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        joinPanel.add(roomIdField, BorderLayout.CENTER);
        joinPanel.add(btnJoin, BorderLayout.EAST);

        leftRow.add(btnCreate);
        leftRow.add(new JLabel("Tham gia bằng ID:"));
        leftRow.add(joinPanel);

        top.add(leftRow, BorderLayout.CENTER);

        // ===== TABLE (CENTER) =====
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);

        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(false);

        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new ButtonEditor(new JCheckBox(), this::joinRoomByRow));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(new Color(242,244,247),1,true));

        // ===== BOTTOM =====
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);

        JButton btnLogout = redButton("ĐĂNG XUẤT");
        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        leftBtns.setOpaque(false);
        leftBtns.add(btnLogout);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        rightBtns.setOpaque(false);
        JButton btnHistory = outlineButton("Lịch sử");
        JButton btnRank    = outlineButton("Xếp hạng");
        Dimension same = new Dimension(140, 36);
        btnHistory.setPreferredSize(same);
        btnRank.setPreferredSize(same);
        rightBtns.add(btnHistory);
        rightBtns.add(btnRank);

        bottom.add(leftBtns, BorderLayout.WEST);
        bottom.add(rightBtns, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        // Actions
        btnCreate.addActionListener(e -> createRoom());
        btnJoin.addActionListener(e -> joinById());
        roomIdField.addActionListener(e -> joinById());
        btnHistory.addActionListener(e -> new HistoryView(net, username).setVisible(true));
        btnRank.addActionListener(e -> new LeaderboardView(net).setVisible(true));
        btnLogout.addActionListener(e -> {
            int opt = JOptionPane.showConfirmDialog(
                    this,
                    "Bạn có chắc muốn đăng xuất?",
                    "Đăng xuất",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (opt == JOptionPane.YES_OPTION) {
                if (poller != null) poller.stop();
                new LoginView(net).setVisible(true);
                dispose();
            }
        });

        poller = new Timer(1200, e -> refreshRooms());
        poller.start();
        refreshRooms();
    }

    // ===== Styles =====
    private JButton greenButton(String text) {
        JButton b = new JButton(text);
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(0x2ECC71));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        b.setPreferredSize(new Dimension(150, 36));
        return b;
    }
    private JButton redButton(String text) {
        JButton b = new JButton(text);
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(0xE74C3C));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        b.setPreferredSize(new Dimension(150, 36));
        return b;
    }
    private JButton outlineButton(String text) {
        JButton b = new JButton(text);
        b.setForeground(new Color(0x2D8CFF));
        b.setBackground(Color.WHITE);
        b.setBorder(new LineBorder(new Color(0x2D8CFF), 1, true));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 12f));
        return b;
    }
    private void styleInner(JTextField f) {
        f.setBorder(new EmptyBorder(8,12,8,12));
        f.setBackground(Color.WHITE);
        f.setFont(f.getFont().deriveFont(Font.PLAIN, 13f));
    }
    private void installPlaceholder(JTextField field, String ghost) {
        Color ghostColor = new Color(0x98A2B3);
        field.setForeground(ghostColor);
        field.setText(ghost);
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (field.getForeground().equals(ghostColor)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (field.getText().isBlank()) {
                    field.setForeground(ghostColor);
                    field.setText(ghost);
                }
            }
        });
    }

    // ===== Networking =====
    private void refreshRooms() {
        String resp = net.send("cmd=LIST");
        model.setData(parseRooms(resp));
    }
    private void createRoom() {
        String resp = net.send("cmd=CREATE;token=" + net.token);
        String roomId = kv(resp, "room");
        if (roomId.isEmpty()) {
            JOptionPane.showMessageDialog(this, resp, "Tạo phòng", JOptionPane.ERROR_MESSAGE);
            return;
        }
        poller.stop();
        new GameView(net, roomId, username).setVisible(true);
        dispose();
    }
    private void joinById() {
        String id = roomIdField.getText().trim();
        if (id.isBlank() || id.equalsIgnoreCase("Nhập mã phòng")) {
            JOptionPane.showMessageDialog(this, "Hãy nhập mã phòng.", "Tham gia", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String resp = net.send("cmd=JOIN;token=" + net.token + ";room=" + id);
        if (!resp.startsWith("OK")) {
            JOptionPane.showMessageDialog(this, resp, "Tham gia", JOptionPane.ERROR_MESSAGE);
            return;
        }
        poller.stop();
        new GameView(net, id, username).setVisible(true);
        dispose();
    }
    private void joinRoomByRow(int row) {
        RoomRow r = model.getRow(row);
        if (r == null) return;
        if (!"waiting".equalsIgnoreCase(r.status)) {
            JOptionPane.showMessageDialog(this, "Chỉ có thể vào phòng đang chờ.", "Tham gia", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String resp = net.send("cmd=JOIN;token=" + net.token + ";room=" + r.roomId);
        if (!resp.startsWith("OK")) {
            JOptionPane.showMessageDialog(this, resp, "Tham gia", JOptionPane.ERROR_MESSAGE);
            return;
        }
        poller.stop();
        new GameView(net, r.roomId, username).setVisible(true);
        dispose();
    }

    // ===== Parse helpers =====
    private static List<RoomRow> parseRooms(String resp) {
        String rooms = kv(resp, "rooms");
        List<RoomRow> list = new ArrayList<>();
        if (rooms == null) return list;
        int seq = 1;
        for (String r : rooms.split("\\|")) {
            if (r.isBlank()) continue;
            String[] p = r.split(",");
            if (p.length < 4) continue;
            String id = p[0].trim();
            String guest = p[2].trim();
            String status = p[3].trim();
            int players = guest.isEmpty() ? 1 : 2;
            list.add(new RoomRow(String.format("%04d", seq++), id, players + "/2", status));
        }
        return list;
    }
    private static String kv(String line, String key) {
        for (String part : line.split("[ ;]")) {
            if (part.startsWith(key + "=")) return part.substring((key + "=").length()).replaceAll(";$", "");
        }
        return "";
    }

    // ===== Table model & button column =====
    private static class RoomRow {
        String no, roomId, players, status;
        RoomRow(String no, String roomId, String players, String status){ this.no=no; this.roomId=roomId; this.players=players; this.status=status; }
    }
    private static class RoomsTableModel extends AbstractTableModel {
        private final String[] cols = {"STT", "Mã phòng", "Người chơi", "Trạng thái", "Vào"};
        private final List<RoomRow> data = new ArrayList<>();
        public void setData(List<RoomRow> rows){ data.clear(); data.addAll(rows); fireTableDataChanged(); }
        public RoomRow getRow(int r){ return (r>=0 && r<data.size())?data.get(r):null; }
        @Override public int getRowCount(){ return data.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }
        @Override public Object getValueAt(int r,int c){
            RoomRow row=data.get(r);
            return switch(c){ 
                case 0->row.no; 
                case 1->row.roomId; 
                case 2->row.players; 
                case 3->row.status; 
                case 4->"Vào"; 
                default->""; 
            };
        }
        @Override public boolean isCellEditable(int r,int c){ return c==4; }
    }
    private static class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer(){
            setOpaque(true);
            setText("Vào");
            setBackground(new Color(0x2ECC71));
            setForeground(Color.WHITE);
            setBorderPainted(false);
        }
        @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean s,boolean f,int r,int c){ return this; }
    }
    private static class ButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton("Vào");
        private int row=-1;
        private final java.util.function.IntConsumer onClick;
        public ButtonEditor(JCheckBox check, java.util.function.IntConsumer onClick){
            this.onClick=onClick;
            button.setBackground(new Color(0x2ECC71));
            button.setForeground(Color.WHITE);
            button.setBorderPainted(false);
            button.setFocusPainted(false);
            button.addActionListener((ActionEvent e)->{ if(row>=0) onClick.accept(row); fireEditingStopped(); });
        }
        @Override public Component getTableCellEditorComponent(JTable t,Object v,boolean s,int r,int c){ row=r; return button; }
        @Override public Object getCellEditorValue(){ return "Vào"; }
        @Override public boolean isCellEditable(EventObject e){ return true; }
    }
}
