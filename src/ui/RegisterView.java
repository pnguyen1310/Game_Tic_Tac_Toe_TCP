package ui;

import client.Net;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class RegisterView extends JFrame {
    private final Net net;
    private PlaceholderField user;
    private PlaceholderPassword pass1;
    private PlaceholderPassword pass2;
    private JLabel log; // dùng JLabel để hiển thị cảnh báo

    public RegisterView(Net net) {
        super("Đăng ký tài khoản");
        this.net = net;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(420, 520);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(new Color(0xF3F5F7));

        // Card
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setOpaque(true);
        card.setPreferredSize(new Dimension(340, 380));
        card.setMaximumSize(new Dimension(340, 380));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(230,233,236), 1, true),
                new EmptyBorder(24,24,20,24)
        ));

        JLabel title = new JLabel("Tạo tài khoản");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        user  = new PlaceholderField("Tên đăng nhập");
        pass1 = new PlaceholderPassword("Mật khẩu");
        pass2 = new PlaceholderPassword("Xác nhận mật khẩu");
        styleField(user); styleField(pass1); styleField(pass2);

        JButton btnCreate = new JButton("ĐĂNG KÝ");
        btnCreate.setFocusPainted(false);
        btnCreate.setForeground(Color.WHITE);
        btnCreate.setBackground(new Color(0x2ECC71));
        btnCreate.setBorderPainted(false);
        btnCreate.setFont(btnCreate.getFont().deriveFont(Font.BOLD, 14f));
        btnCreate.setPreferredSize(new Dimension(260, 42));
        btnCreate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnCreate.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel backLink = new JLabel(htmlLink(false, "Quay lại đăng nhập"));
        backLink.setForeground(new Color(0x2D8CFF));
        backLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backLink.setFont(backLink.getFont().deriveFont(Font.PLAIN, 12f));
        Dimension backSize = backLink.getPreferredSize();
        backLink.setPreferredSize(backSize);
        backLink.setMinimumSize(backSize);
        backLink.setMaximumSize(backSize);

        // 👉 Cảnh báo: JLabel (không cuộn, không lệch)
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
        card.add(pass1);
        card.add(Box.createVerticalStrut(v));
        card.add(pass2);
        card.add(Box.createVerticalStrut(18));
        card.add(btnCreate);
        card.add(Box.createVerticalStrut(12));

        JPanel backRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        backRow.setOpaque(false);
        backRow.add(backLink);
        card.add(backRow);

        card.add(Box.createVerticalStrut(10));
        card.add(log);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(new Color(0xF3F5F7));
        root.add(card, new GridBagConstraints());
        setContentPane(root);

        // events
        btnCreate.addActionListener(e -> doRegister());
        pass2.addActionListener(e -> doRegister());
        backLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                dispose();
                new LoginView(net).setVisible(true);
            }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                backLink.setText(htmlLink(true, "Quay lại đăng nhập"));
            }
            @Override public void mouseExited (java.awt.event.MouseEvent e) {
                backLink.setText(htmlLink(false, "Quay lại đăng nhập"));
            }
        });
    }

    private void styleField(JTextField f){
        f.setPreferredSize(new Dimension(260, 40));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        f.setBorder(new EmptyBorder(10,12,10,12));
        f.setBackground(new Color(0xF2F4F7));
        f.setForeground(new Color(0x111111));
        f.setCaretColor(new Color(0x111111));
        f.setFont(f.getFont().deriveFont(Font.PLAIN, 13f));
    }

    private void doRegister() {
        String u  = user.getText().trim();
        String p1 = new String(pass1.getPassword()).trim();
        String p2 = new String(pass2.getPassword()).trim();

        if (user.isHintVisible() || pass1.isHintVisible() || pass2.isHintVisible()
                || u.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
            log("⚠ Vui lòng điền đầy đủ thông tin.");
            return;
        }
        if (!u.matches("[a-zA-Z0-9_]{3,16}")) { log("⚠ Tên đăng nhập không hợp lệ (3–16, chữ/số/_)."); return; }
        if (p1.length() < 3)                    { log("⚠ Mật khẩu tối thiểu 3 ký tự."); return; }
        if (!p1.equals(p2))                     { log("⚠ Xác nhận mật khẩu không khớp."); return; }

        String resp = net.send("cmd=REGISTER;user="+u+";pass="+p1);
        if (resp.startsWith("OK")) {
            logSuccess("✅ Đăng ký thành công! Bạn có thể đăng nhập ngay bây giờ.");

            // Reset lại các input
            user.setText("");
            pass1.setText("");
            pass2.setText("");
        } else {
            log("⚠ " + resp);
        }
    }

    private void log(String s){
        log.setText(s);
        log.setForeground(new Color(0xA94442));
        log.setBackground(new Color(0xFFF5F5));
    }

    private void logSuccess(String s){
        log.setText(s);
        log.setForeground(new Color(0x166534));
        log.setBackground(new Color(0xDCFCE7));
    }

    private static String htmlLink(boolean underline, String text) {
        String deco = underline ? "underline" : "none";
        return "<html><span style='text-decoration:" + deco + ";'>" + text + "</span></html>";
    }

    // ===== Placeholders (giống LoginView) =====
    static class PlaceholderField extends JTextField {
        private final String hint; private boolean showingHint = true;
        public PlaceholderField(String hint){ this.hint = hint; setUI(new javax.swing.plaf.basic.BasicTextFieldUI()); }
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
            this.hint = hint; setEchoChar((char)0);
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
