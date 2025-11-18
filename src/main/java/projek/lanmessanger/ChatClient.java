package projek.lanmessanger;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;

public class ChatClient implements ClientListener {
    
    // --- UI Components ---
    private JFrame frame;
    private JTextPane incomingMessages;
    private JTextField outgoingMessage;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private TrayIcon trayIcon;
    
    // Reply feature
    private JPanel replyPanel;
    private JLabel replyLabel;
    private String replyingToMessage = null;

    // --- Logic Components ---
    private NetworkClient networkClient;
    private String username;
    private String serverIP = "";
    private int serverPort = 5000;
    
    private HashMap<String, PrivateChatWindow> privateChatWindows = new HashMap<>();
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Styling
    private StyledDocument chatDocument;
    private SimpleAttributeSet styleSender, styleMessage, styleSystem, styleError;

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new ChatClient().go());
    }

    public void go() {
        promptForUsername();
        promptForServerConfig();
        initUI();
        initSystemTray();
        initNetwork();
        frame.setVisible(true);
    }

    private void initNetwork() {
        try {
            System.out.println("Menghubungkan ke " + serverIP + ":" + serverPort);
            // Inisialisasi NetworkClient dengan 'this' sebagai Listener
            networkClient = new NetworkClient(serverIP, serverPort, username, this);
            appendSystemMessage("Terhubung ke server.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Gagal terhubung: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // --- IMPLEMENTASI ClientListener (Callback dari NetworkClient) ---
    
    @Override
    public void onMessageReceived(String sender, String message, boolean isPrivate) {
        if (isPrivate) {
            handlePrivateIncoming(sender, message);
        } else {
            // Broadcast message logic
            SwingUtilities.invokeLater(() -> {
                SimpleAttributeSet sStyle = styleSender;
                SimpleAttributeSet mStyle = styleMessage;

                // Cek apakah pesan dari diri sendiri (untuk pewarnaan hijau)
                if (sender != null && sender.startsWith(username)) {
                     StyleConstants.setForeground(sStyle, new Color(0, 130, 0));
                } else {
                     StyleConstants.setForeground(styleSender, new Color(0, 102, 204)); // Reset warna default
                }
                
                // Jika formatnya sistem (tanpa sender jelas)
                if (sender == null) {
                    sStyle = styleSystem;
                    mStyle = styleSystem;
                }

                appendMessage(sender, message, sStyle, mStyle);
                if (!frame.isFocused()) showNotification("Pesan Baru", message);
            });
        }
    }

    @Override
    public void onUserListUpdate(String[] users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String user : users) {
                userListModel.addElement(user);
            }
        });
    }

    @Override
    public void onSystemMessage(String message) {
        appendSystemMessage(message);
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> {
            appendMessage(null, "[ERROR] " + message, styleError, styleError);
        });
    }
    
    @Override
    public File onFileReceiveRequest(String fileName, long fileSize, String sender) {
        // Method ini dipanggil oleh Network Thread, kita pakai invokeAndWait untuk GUI blocking
        final File[] selectedFile = new File[1];
        
        try {
            SwingUtilities.invokeAndWait(() -> {
                String msg = (sender == null ? "Broadcast" : sender) + " mengirim file: " + fileName + " (" + (fileSize/1024) + " KB)";
                
                // Jika private, tampilkan info di window private
                if (sender != null && privateChatWindows.containsKey(sender)) {
                    privateChatWindows.get(sender).appendMessage("Receiving file: " + fileName);
                } else {
                    appendSystemMessage(msg);
                    showNotification("File Masuk", msg);
                }

                JFileChooser saveChooser = new JFileChooser();
                File saveDir = new File("Files");
                if (!saveDir.exists()) saveDir.mkdir();
                saveChooser.setCurrentDirectory(saveDir.getAbsoluteFile());
                saveChooser.setSelectedFile(new File(fileName));

                if (saveChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    selectedFile[0] = saveChooser.getSelectedFile();
                    appendSystemMessage("Menyimpan ke: " + selectedFile[0].getName() + "...");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return selectedFile[0];
    }

    @Override
    public void onFileSaved(File file) {
        appendSystemMessage("File " + file.getName() + " berhasil disimpan.");
    }

    // --- Helper Method untuk Private Chat (Agar kompatibel dengan PrivateChatWindow.java) ---

    private void handlePrivateIncoming(String sender, String message) {
        if (!privateChatWindows.containsKey(sender)) {
            SwingUtilities.invokeLater(() -> openPrivateChat(sender));
        }
        PrivateChatWindow window = privateChatWindows.get(sender);
        if (window != null) {
            window.appendMessage(sender + ": " + message);
            if (!window.isFocused()) showNotification("Private Msg: " + sender, message);
        }
    }

    public void sendPrivateMessage(String target, String message) {
        networkClient.sendPrivateMessage(target, message);
    }

    public void sendPrivateFile(String target, File file) {
        networkClient.sendFile(file, target);
        // Update UI Private Window
        PrivateChatWindow window = privateChatWindows.get(target);
        if (window != null) {
            window.appendMessage("File " + file.getName() + " dikirim.");
        }
    }

    public void removePrivateChatWindow(String username) {
        privateChatWindows.remove(username);
    }

    private void openPrivateChat(String targetUser) {
        if (privateChatWindows.containsKey(targetUser)) {
            PrivateChatWindow window = privateChatWindows.get(targetUser);
            window.setVisible(true);
            window.toFront();
        } else {
            PrivateChatWindow window = new PrivateChatWindow(targetUser, this);
            privateChatWindows.put(targetUser, window);
            window.setVisible(true);
        }
    }

    // --- GUI & Setup Methods (Sama seperti sebelumnya, tapi dirapikan) ---

    private void initUI() {
        frame = new JFrame("LAN Messenger - (" + username + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Styles Setup
        initStyles();

        // Layout Utama
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left Panel (User List)
        JPanel leftPanel = createLeftPanel();
        
        // Right Panel (Chat Area)
        JPanel rightPanel = createRightPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.1);
        splitPane.setBorder(null);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        frame.add(mainPanel);
        frame.pack();
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
    }

    private void initStyles() {
        styleSender = new SimpleAttributeSet();
        StyleConstants.setBold(styleSender, true);
        StyleConstants.setForeground(styleSender, new Color(0, 102, 204));

        styleMessage = new SimpleAttributeSet();
        StyleConstants.setForeground(styleMessage, new Color(50, 50, 50));

        styleSystem = new SimpleAttributeSet();
        StyleConstants.setItalic(styleSystem, true);
        StyleConstants.setForeground(styleSystem, Color.GRAY);
        
        styleError = new SimpleAttributeSet();
        StyleConstants.setBold(styleError, true);
        StyleConstants.setForeground(styleError, Color.RED);
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setPreferredSize(new Dimension(180, 0));
        
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setCellRenderer(new UserListRenderer());
        
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = userList.getSelectedValue();
                    if (selected != null && !selected.equals(username)) openPrivateChat(selected);
                }
            }
        });

        JButton btnPrivate = new JButton("Private Chat");
        btnPrivate.addActionListener(e -> {
            String selected = userList.getSelectedValue();
            if (selected != null && !selected.equals(username)) openPrivateChat(selected);
        });

        panel.add(new JLabel("Pengguna Online"), BorderLayout.NORTH);
        panel.add(new JScrollPane(userList), BorderLayout.CENTER);
        panel.add(btnPrivate, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        
        incomingMessages = new JTextPane();
        incomingMessages.setEditable(false);
        chatDocument = incomingMessages.getStyledDocument();
        incomingMessages.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) { if(e.isPopupTrigger()) showPopup(e); }
            public void mousePressed(MouseEvent e) { if(e.isPopupTrigger()) showPopup(e); }
        });

        // Input Area
        outgoingMessage = new JTextField();
        outgoingMessage.addActionListener(e -> sendMessage());
        
        JButton btnSend = new JButton("Kirim \u27A4");
        btnSend.addActionListener(e -> sendMessage());
        
        JButton btnFile = new JButton("File \uD83D\uDCCE");
        btnFile.addActionListener(e -> sendFileAction());

        JPanel bottomBox = new JPanel(new BorderLayout(5,0));
        JPanel btnBox = new JPanel(new GridLayout(1,2,5,0));
        btnBox.add(btnSend);
        btnBox.add(btnFile);
        bottomBox.add(outgoingMessage, BorderLayout.CENTER);
        bottomBox.add(btnBox, BorderLayout.EAST);

        // Reply Panel
        replyPanel = new JPanel(new BorderLayout());
        replyPanel.setVisible(false);
        replyLabel = new JLabel();
        JButton closeReply = new JButton("x");
        closeReply.addActionListener(e -> { replyingToMessage = null; replyPanel.setVisible(false); });
        replyPanel.add(replyLabel, BorderLayout.CENTER);
        replyPanel.add(closeReply, BorderLayout.EAST);
        
        JPanel southContainer = new JPanel(new BorderLayout());
        southContainer.add(replyPanel, BorderLayout.NORTH);
        southContainer.add(bottomBox, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(incomingMessages);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(southContainer, BorderLayout.SOUTH);
        return panel;
    }

    private void sendMessage() {
        String text = outgoingMessage.getText().trim();
        if (text.isEmpty()) return;

        String fullMsg = (replyingToMessage != null) 
                ? username + " [↩ " + replyingToMessage + "]: " + text 
                : username + ": " + text;

        networkClient.sendBroadcastMessage(fullMsg);
        
        outgoingMessage.setText("");
        replyingToMessage = null;
        replyPanel.setVisible(false);
    }

    private void sendFileAction() {
        JFileChooser ch = new JFileChooser();
        if (ch.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File f = ch.getSelectedFile();
            appendSystemMessage("Mengirim file: " + f.getName() + "...");
            new Thread(() -> {
                networkClient.sendFile(f, null); // null = broadcast
                appendSystemMessage("File terkirim.");
            }).start();
        }
    }
    
    private void showPopup(MouseEvent e) {
        // (Logika popup menu reply disederhanakan disini, bisa dicopy dari kode lama jika butuh detail)
        JPopupMenu menu = new JPopupMenu();
        JMenuItem item = new JMenuItem("Reply");
        item.addActionListener(ev -> {
            replyingToMessage = "Selected Text"; // Simplifikasi untuk demo
            replyLabel.setText("Replying...");
            replyPanel.setVisible(true);
        });
        menu.add(item);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void appendMessage(String sender, String message, SimpleAttributeSet styleS, SimpleAttributeSet styleM) {
        try {
            String time = LocalTime.now().format(timeFormatter);
            chatDocument.insertString(chatDocument.getLength(), "[" + time + "] ", styleSystem);
            if (sender != null) chatDocument.insertString(chatDocument.getLength(), sender + " ", styleS);
            chatDocument.insertString(chatDocument.getLength(), message + "\n", styleM);
            incomingMessages.setCaretPosition(chatDocument.getLength());
        } catch (BadLocationException e) { e.printStackTrace(); }
    }
    
    private void appendSystemMessage(String msg) {
        SwingUtilities.invokeLater(() -> appendMessage(null, msg, styleSystem, styleSystem));
    }

    private void promptForUsername() {
        while (username == null || username.trim().isEmpty()) {
            username = JOptionPane.showInputDialog(null, "Masukkan nama Anda:", "Login", JOptionPane.PLAIN_MESSAGE);
            if (username == null) System.exit(0);
        }
    }

    private void promptForServerConfig() {
        JTextField ipField = new JTextField(serverIP);
        JTextField portField = new JTextField(String.valueOf(serverPort));
        Object[] message = {"IP Address:", ipField, "Port:", portField};
        int option = JOptionPane.showConfirmDialog(null, message, "Config Server", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            serverIP = ipField.getText();
            serverPort = Integer.parseInt(portField.getText());
        } else {
            System.exit(0);
        }
    }
    
    private void showNotification(String title, String msg) {
        if (trayIcon != null) trayIcon.displayMessage(title, msg, TrayIcon.MessageType.INFO);
    }

    private void initSystemTray() {
        if (SystemTray.isSupported()) {
            try {
                Image img = new ImageIcon("image/yuta.png").getImage();
                trayIcon = new TrayIcon(img, "LAN Messenger");
                trayIcon.setImageAutoSize(true);
                SystemTray.getSystemTray().add(trayIcon);
            } catch (Exception e) {}
        }
    }

    // Custom Renderer for User List
    class UserListRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String s = (String) value;
            setText("\u25CF " + s + (s.equals(username) ? " (Anda)" : ""));
            setForeground(s.equals(username) ? new Color(0, 102, 204) : new Color(0, 140, 0));
            setBorder(new EmptyBorder(5,5,5,5));
            return c;
        }
    }
}