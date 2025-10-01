package ui;

import client.Net;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class LoginView extends JFrame {
    private final Net net;
    private PlaceholderField user;
    private PlaceholderPassword pass;
    private JLabel log; // dùng JLabel thay cho JTextArea

    public LoginView(Net net) {
        super("Đăng nhập");
        this.net = net;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 500);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(new Color(0xF3F5F7));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(28, 28, 24, 28));
        card.setBackground(Color.WHITE);
        card.setOpaque(true);
        card.setPreferredSize(new Dimension(340, 360));
        card.setMaximumSize(new Dimension(340, 360));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(230, 233, 236), 1, true),
                new EmptyBorder(24, 24, 20, 24)
        ));

        JLabel title = new JLabel("Đăng nhập");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        user = new PlaceholderField("Tên đăng nhập");
        pass = new PlaceholderPassword("Mật khẩu");
        styleField(user);
        styleField(pass);

        JButton btnLogin = new JButton("ĐĂNG NHẬP");
        btnLogin.setFocusPainted(false);
        btnLogin.setFocusable(false);
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setBackground(new Color(0x2ECC71));
        btnLogin.setBorderPainted(false);
        btnLogin.setFont(btnLogin.getFont().deriveFont(Font.BOLD, 14f));
        btnLogin.setPreferredSize(new Dimension(260, 42));
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnLogin.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel helper = new JLabel("Chưa có tài khoản? ");
        JLabel link = new JLabel(htmlLink(false));
        link.setForeground(new Color(0x2D8CFF));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setFont(link.getFont().deriveFont(Font.PLAIN, 12f));

        JPanel bottomLine = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        bottomLine.setOpaque(false);
        bottomLine.add(helper);
        bottomLine.add(link);

        // 👉 Log cảnh báo bằng JLabel
        log = new JLabel(" ");
        log.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        log.setForeground(new Color(0xA94442));
        log.setBackground(new Color(0xFFF5F5));
        log.setOpaque(true);
        log.setBorder(new EmptyBorder(8, 10, 8, 10));
        log.setAlignmentX(Component.CENTER_ALIGNMENT);

        int v = 12;
        card.add(Box.createVerticalStrut(4));
        card.add(title);
        card.add(Box.createVerticalStrut(18));
        card.add(user);
        card.add(Box.createVerticalStrut(v));
        card.add(pass);
        card.add(Box.createVerticalStrut(18));
        card.add(btnLogin);
        card.add(Box.createVerticalStrut(12));
        card.add(bottomLine);
        card.add(Box.createVerticalStrut(10));
        card.add(log);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(new Color(0xF3F5F7));
        root.add(card, new GridBagConstraints());
        setContentPane(root);

        // events
        btnLogin.addActionListener(e -> doLogin());
        user.addActionListener(e -> doLogin());
        pass.addActionListener(e -> doLogin());
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                dispose();
                new RegisterView(net).setVisible(true);
            }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { link.setText(htmlLink(true)); }
            @Override public void mouseExited (java.awt.event.MouseEvent e) { link.setText(htmlLink(false)); }
        });
    }

    private void styleField(JTextField f) {
        f.setPreferredSize(new Dimension(260, 40));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        f.setBorder(new EmptyBorder(10, 12, 10, 12));
        f.setBackground(new Color(0xF2F4F7));
        f.setForeground(new Color(0x111111));
        f.setCaretColor(new Color(0x111111));
        f.setFont(f.getFont().deriveFont(Font.PLAIN, 13f));
    }

    private void doLogin() {
        String u = user.getText().trim();
        String p = new String(pass.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty() || user.isHintVisible() || pass.isHintVisible()) {
            showErr("⚠ Vui lòng nhập tên đăng nhập và mật khẩu.");
            return;
        }
        String resp = net.send("cmd=LOGIN;user=" + u + ";pass=" + p);
        if (resp.startsWith("OK")) {
            net.token = kv(resp, "token");
            new LobbyView(net, u).setVisible(true);
            dispose();
        } else {
            showErr("⚠ " + resp);
        }
    }

    private void showErr(String s) { log.setText(s); }

    private static String kv(String line, String key) {
        for (String part : line.split("[ ;]")) {
            if (part.startsWith(key + "="))
                return part.substring((key + "=").length()).replaceAll(";$", "");
        }
        return "";
    }

    private static String htmlLink(boolean underline) {
        String deco = underline ? "underline" : "none";
        return "<html><span style='text-decoration:" + deco + ";'>Tạo tài khoản</span></html>";
    }

    // === Placeholders giữ nguyên như cũ ===
    static class PlaceholderField extends JTextField {
        private final String hint; private boolean showingHint = true;
        public PlaceholderField(String hint){ this.hint=hint; setUI(new javax.swing.plaf.basic.BasicTextFieldUI()); }
        public boolean isHintVisible(){ return showingHint && getText().isEmpty(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setColor(new Color(0x9AA4B2));
                g2.setFont(getFont());
                Insets ins=getInsets();
                g2.drawString(hint, ins.left, getHeight()/2 + getFontMetrics(getFont()).getAscent()/2 - 4);
                g2.dispose();
                showingHint = true;
            } else showingHint = false;
        }
    }
    static class PlaceholderPassword extends JPasswordField {
        private final String hint; private boolean showingHint = true;
        public PlaceholderPassword(String hint){
            this.hint=hint; setEchoChar((char)0);
            getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void upd(){ setEchoChar(getPassword().length==0 ? (char)0 : '•'); }
                public void insertUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e){ upd(); }
            });
        }
        public boolean isHintVisible(){ return showingHint && getPassword().length==0; }
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            if (getPassword().length==0){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setColor(new Color(0x9AA4B2));
                g2.setFont(getFont());
                Insets ins=getInsets();
                g2.drawString(hint, ins.left, getHeight()/2 + getFontMetrics(getFont()).getAscent()/2 - 4);
                g2.dispose();
                showingHint = true;
            } else showingHint = false;
        }
    }
}
