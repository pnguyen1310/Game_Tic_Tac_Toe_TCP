package tictactoe;

import javax.swing.*;
import java.awt.*;

/**
 * LoginUI: chỉ nhập tên để chơi.
 * Sau khi nhập thành công -> mở LobbyUI.
 */
public class LoginUI extends JFrame {
    private final TicTacToeClient client;
    private final JTextField nameField = new JTextField(20);
    private final JButton loginBtn = new JButton("🚀 Tham Gia Game");
    private final JLabel infoLabel = new JLabel("Hãy nhập tên để bắt đầu", SwingConstants.CENTER);

    public LoginUI(TicTacToeClient client) {
        super("Login - TicTacToe");
        this.client = client;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(450, 250);
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        // dùng BorderLayout
        setLayout(new BorderLayout(15, 15));
        getContentPane().setBackground(new Color(245, 245, 250));

        // Tiêu đề
        JLabel title = new JLabel("Tic Tac Toe Online", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(44, 62, 80));
        add(title, BorderLayout.NORTH);

        // Panel trung tâm chứa form
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(new Color(245, 245, 250));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel nameLabel = new JLabel("Tên của bạn:");
        nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        gbc.gridx = 0; gbc.gridy = 0;
        center.add(nameLabel, gbc);

        nameField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        gbc.gridx = 1; gbc.gridy = 0;
        center.add(nameField, gbc);

        loginBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        loginBtn.setBackground(new Color(52, 152, 219));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        center.add(loginBtn, gbc);

        add(center, BorderLayout.CENTER);

        // Label thông tin
        infoLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        infoLabel.setForeground(new Color(127, 140, 141));
        add(infoLabel, BorderLayout.SOUTH);

        // Actions
        loginBtn.addActionListener(e -> doLogin());
        nameField.addActionListener(e -> doLogin());
    }

    private void doLogin() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            infoLabel.setText("⚠️ Bạn chưa nhập tên!");
            infoLabel.setForeground(Color.RED);
            return;
        }
        client.send("SET_NAME " + name);

        // mở LobbyUI ngay
        SwingUtilities.invokeLater(() -> {
            this.setVisible(false);
            LobbyUI lobby = new LobbyUI(client);
            client.setLobbyUI(lobby);
            lobby.setVisible(true);
        });
    }

    // main entry
    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;
        try {
            TicTacToeClient client = new TicTacToeClient(host, port);
            LoginUI login = new LoginUI(client);
            SwingUtilities.invokeLater(() -> login.setVisible(true));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Không thể kết nối server: " + e.getMessage());
        }
    }
}
