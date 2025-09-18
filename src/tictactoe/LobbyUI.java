package tictactoe;

import javax.swing.*;
import java.awt.*;

/**
 * LobbyUI: hiển thị danh sách phòng, tạo phòng, tham gia phòng, refresh danh sách
 */
public class LobbyUI extends JFrame {
    private static final long serialVersionUID = 1L;

    private final JButton joinBtn = new JButton("➡️ Tham Gia");
    private final JButton createBtn = new JButton("➕ Tạo Phòng");
    private final JButton refreshBtn = new JButton("🔄 Làm Mới");
    private final JList<String> roomList = new JList<>();
    private final TicTacToeClient client;

    public LobbyUI(TicTacToeClient client) {
        super("Lobby - TicTacToe");
        this.client = client;
        client.setLobbyUI(this);

        setSize(500, 400);
        setLayout(new BorderLayout(15, 15));
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(245, 245, 250));

        // Tiêu đề
        JLabel title = new JLabel("🎮 Chọn Phòng Để Chơi", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(44, 62, 80));
        add(title, BorderLayout.NORTH);

        // Danh sách phòng
        roomList.setFont(new Font("Monospaced", Font.PLAIN, 16));
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(roomList);
        scroll.setBorder(BorderFactory.createTitledBorder("Danh sách phòng"));
        add(scroll, BorderLayout.CENTER);

        // Các nút điều khiển
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottom.setBackground(new Color(245, 245, 250));

        styleButton(createBtn, new Color(46, 204, 113));  // xanh lá
        styleButton(joinBtn, new Color(52, 152, 219));    // xanh dương
        styleButton(refreshBtn, new Color(241, 196, 15)); // vàng

        bottom.add(createBtn);
        bottom.add(joinBtn);
        bottom.add(refreshBtn);

        add(bottom, BorderLayout.SOUTH);

        // Sự kiện
        createBtn.addActionListener(e -> client.send("CREATE_ROOM"));
        joinBtn.addActionListener(e -> {
            String sel = roomList.getSelectedValue();
            if (sel != null) {
                String id = sel.split("\\(")[0].trim();
                client.send("JOIN_ROOM " + id);
            } else {
                showInfo("⚠️ Vui lòng chọn phòng để tham gia!");
            }
        });
        refreshBtn.addActionListener(e -> client.send("REFRESH"));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setFont(new Font("SansSerif", Font.BOLD, 15));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
    }

    // Cập nhật danh sách phòng từ server
    public void updateRoomList(String msg) {
        String[] parts = msg.substring("ROOM_LIST ".length()).split(";");
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String p : parts) {
            if (!p.trim().isEmpty()) model.addElement(p.trim());
        }
        roomList.setModel(model);
    }

    // Khi join phòng thành công thì mở GUI game
    public void enterRoom(String msg) {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            new TicTacToeGUI(client).setVisible(true);
        });
    }

    // Hiển thị popup thông báo
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }
}
