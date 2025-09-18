package tictactoe;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Game UI: bàn 3x3 + chat + trạng thái + Play again + Leave Room
 * - Hover effect trên ô
 * - Highlight 3 ô thắng (vàng)
 * - Status bar có màu nền theo lượt
 * - Chat đơn giản, auto-scroll
 */
public class TicTacToeGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    private final TicTacToeClient client;
    private final JButton[][] buttons = new JButton[3][3];
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatField = new JTextField();
    private final JButton sendBtn = new JButton("📨 Gửi");
    private final JButton playAgainBtn = new JButton("🔄 Chơi lại");
    private final JButton leaveBtn = new JButton("❌ Rời phòng");
    private final JLabel statusLabel = new JLabel("Đang kết nối...");
    private boolean myTurn = false;
    private char mySymbol = '?'; // ký hiệu của chính mình

    public TicTacToeGUI(TicTacToeClient client) {
        super("TicTacToe - Game");
        this.client = client;
        client.setGameUI(this);
        buildUI();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(780, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(245, 245, 250));

        // Bàn cờ
        JPanel boardPanel = new JPanel(new GridLayout(3, 3, 8, 8));
        boardPanel.setBorder(BorderFactory.createTitledBorder("Bàn cờ"));
        boardPanel.setBackground(new Color(245, 245, 250));
        Font f = new Font("SansSerif", Font.BOLD, 48);

        // giữ kích thước cố định
        boardPanel.setPreferredSize(new Dimension(360, 360));
        boardPanel.setMaximumSize(new Dimension(360, 360));
        boardPanel.setMinimumSize(new Dimension(360, 360));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                JButton b = new JButton("");
                b.setFont(f);
                b.setFocusPainted(false);
                b.setBackground(Color.WHITE);
                b.setPreferredSize(new Dimension(110, 110));
                b.setOpaque(true);
                final int r = i, c = j;

                // Click
                b.addActionListener(e -> {
                    if (myTurn && b.getText().equals("")) {
                        client.send("MOVE " + r + " " + c);
                    } else if (!myTurn) {
                        JOptionPane.showMessageDialog(this, "⏳ Chưa tới lượt bạn!",
                                "Thông báo", JOptionPane.WARNING_MESSAGE);
                    }
                });

                // Hover effect
                b.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        if (b.getText().isEmpty()) b.setBackground(new Color(230, 230, 230));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        if (b.getText().isEmpty()) b.setBackground(Color.WHITE);
                    }
                });

                // smooth border
                b.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
                buttons[i][j] = b;
                boardPanel.add(b);
            }
        }

        // bọc để bàn cờ luôn căn giữa và không bị dãn
        JPanel centerWrap = new JPanel(new GridBagLayout());
        centerWrap.setBackground(new Color(245, 245, 250));
        centerWrap.add(boardPanel);
        add(centerWrap, BorderLayout.CENTER);

        // Chat panel
        JPanel chatPanel = new JPanel(new BorderLayout(6, 6));
        chatPanel.setPreferredSize(new Dimension(300, 0));
        chatPanel.setBorder(BorderFactory.createTitledBorder("💬 Trò chuyện"));
        chatPanel.setBackground(new Color(245, 245, 250));

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(null);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        JPanel input = new JPanel(new BorderLayout(6, 6));
        input.setBackground(new Color(245, 245, 250));
        chatField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        input.add(chatField, BorderLayout.CENTER);
        styleButton(sendBtn, new Color(52, 152, 219));
        input.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(input, BorderLayout.SOUTH);

        add(chatPanel, BorderLayout.EAST);

        // Bottom controls
        JPanel bottom = new JPanel(new BorderLayout(10, 10));
        bottom.setBackground(new Color(245, 245, 250));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        // Status (left)
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        setMyTurn(myTurn); // initialize color/text
        bottom.add(statusLabel, BorderLayout.WEST);

        // Buttons (right)
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 4));
        btnPanel.setBackground(new Color(245, 245, 250));
        styleButton(playAgainBtn, new Color(46, 204, 113));
        playAgainBtn.setEnabled(false);
        styleButton(leaveBtn, new Color(231, 76, 60));
        btnPanel.add(playAgainBtn);
        btnPanel.add(leaveBtn);
        bottom.add(btnPanel, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);

        // Listeners
        sendBtn.addActionListener(e -> sendChat());
        chatField.addActionListener(e -> sendChat());

        playAgainBtn.addActionListener(e -> {
            client.send("PLAY_AGAIN_REQUEST");
            playAgainBtn.setEnabled(false);
        });

        leaveBtn.addActionListener(e -> {
            client.send("LEAVE_ROOM");
            setVisible(false);
            LobbyUI lobby = new LobbyUI(client);
            client.setLobbyUI(lobby);
            lobby.setVisible(true);
        });

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                client.send("LEAVE_ROOM");
                client.close();
            }
        });
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        btn.setOpaque(true);
    }

    private void sendChat() {
        String m = chatField.getText().trim();
        if (!m.isEmpty()) {
            client.send("CHAT:" + m);
            chatField.setText("");
        }
    }

    // Thay đổi trạng thái lượt
    public void setMyTurn(boolean t) {
        this.myTurn = t;
        SwingUtilities.invokeLater(() -> {
            if (t) {
                statusLabel.setText("🟢 Lượt của bạn");
                statusLabel.setBackground(new Color(220, 255, 220));
                statusLabel.setForeground(new Color(16, 84, 16));
            } else {
                statusLabel.setText("🔴 Chờ đối thủ");
                statusLabel.setBackground(new Color(255, 230, 230));
                statusLabel.setForeground(new Color(120, 16, 16));
            }
        });
    }

    public void setMySymbol(char sym) {
        this.mySymbol = sym;
    }

    // Xử lý move từ server: MOVE <symbol> <row> <col>
    public void handleServerMove(String msg) {
        String[] p = msg.split(" ");
        if (p.length >= 4) {
            char sym = p[1].charAt(0);
            int r = Integer.parseInt(p[2]);
            int c = Integer.parseInt(p[3]);
            SwingUtilities.invokeLater(() -> {
                buttons[r][c].setText(String.valueOf(sym));
                buttons[r][c].setEnabled(false);
                buttons[r][c].setBackground(new Color(236, 240, 241));
            });
        }
    }

    // WIN <symbol>
    public void handleWin(String msg) {
        char winner = msg.length() > 4 ? msg.charAt(4) : '?';
        SwingUtilities.invokeLater(() -> {
            // highlight 3 ô thắng
            highlightWinningLine(winner);

            // popup kết quả
            if (winner == mySymbol) {
                JOptionPane.showMessageDialog(this,
                        "Bạn đã THẮNG!",
                        "Kết quả", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Bạn đã THUA!",
                        "Kết quả", JOptionPane.WARNING_MESSAGE);
            }
            setMyTurn(false);
            playAgainBtn.setEnabled(true);
        });
    }

    /** Tô sáng 3 ô thắng */
    private void highlightWinningLine(char sym) {
        Color winColor = new Color(241, 196, 15); // vàng
        String s = String.valueOf(sym);

        // Hàng
        for (int i = 0; i < 3; i++) {
            if (s.equals(buttons[i][0].getText()) &&
                s.equals(buttons[i][1].getText()) &&
                s.equals(buttons[i][2].getText())) {
                markWinningButtons(winColor, buttons[i][0], buttons[i][1], buttons[i][2]);
                return;
            }
        }
        // Cột
        for (int j = 0; j < 3; j++) {
            if (s.equals(buttons[0][j].getText()) &&
                s.equals(buttons[1][j].getText()) &&
                s.equals(buttons[2][j].getText())) {
                markWinningButtons(winColor, buttons[0][j], buttons[1][j], buttons[2][j]);
                return;
            }
        }
        // Chéo chính
        if (s.equals(buttons[0][0].getText()) &&
            s.equals(buttons[1][1].getText()) &&
            s.equals(buttons[2][2].getText())) {
            markWinningButtons(winColor, buttons[0][0], buttons[1][1], buttons[2][2]);
            return;
        }
        // Chéo phụ
        if (s.equals(buttons[0][2].getText()) &&
            s.equals(buttons[1][1].getText()) &&
            s.equals(buttons[2][0].getText())) {
            markWinningButtons(winColor, buttons[0][2], buttons[1][1], buttons[2][0]);
        }
    }

    /** Đổi màu nền 3 ô thắng và viền */
    private void markWinningButtons(Color color, JButton... btns) {
        for (JButton b : btns) {
            b.setBackground(color);
            b.setBorder(BorderFactory.createLineBorder(new Color(200, 120, 0), 3));
            b.setOpaque(true);
        }
    }

    public void handleDraw() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    "Ván đấu hòa!",
                    "Kết quả", JOptionPane.INFORMATION_MESSAGE);
            setMyTurn(false);
            playAgainBtn.setEnabled(true);
        });
    }

    // Reset board with a tiny visual clear (no external libs)
    public void resetBoard() {
        SwingUtilities.invokeLater(() -> {
            Timer t = new Timer(30, null);
            final int[] step = {0};
            t.addActionListener(e -> {
                int alpha = Math.max(0, 255 - step[0]);
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        buttons[i][j].setText("");
                        buttons[i][j].setEnabled(true);
                        buttons[i][j].setBackground(Color.WHITE);
                        buttons[i][j].setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));
                    }
                }
                step[0] += 40;
                if (step[0] > 200) {
                    ((Timer) e.getSource()).stop();
                }
            });
            t.setRepeats(true);
            t.start();

            playAgainBtn.setEnabled(false);
            showMessage("New Game Started");
        });
    }

    // Chat: nhận body như "Name: message"
    public void addChat(String body) {
        SwingUtilities.invokeLater(() -> {
            String line = body;
            int idx = body.indexOf(":");
            if (idx > 0) {
                String sender = body.substring(0, idx).trim();
                String msg = body.substring(idx + 1).trim();
                line = "👤 " + sender + ": " + msg;
            } else {
                line = "💬 " + body;
            }
            chatArea.append(line + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    public void showMessage(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    // Prompt opponent wants play again
    public void promptPlayAgainRequest() {
        int ch = JOptionPane.showConfirmDialog(this,
                "Đối thủ muốn chơi lại. Bạn có đồng ý?",
                "Play Again?",
                JOptionPane.YES_NO_OPTION);
        if (ch == JOptionPane.YES_OPTION) client.send("PLAY_AGAIN_ACCEPT");
        else client.send("PLAY_AGAIN_DECLINE");
    }
}
