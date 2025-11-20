package projek.lanmessanger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

public class ChatClient implements ClientListener {
    
    // ==============================================
    // [SECTION: UI COMPONENTS]
    // ==============================================
    private JFrame frame;
    private JTextPane incomingMessages;
    private JTextField outgoingMessage;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private TrayIcon trayIcon;
    
    // --- Reply Feature Components ---
    private JPanel replyPanel;
    private JLabel replyLabel;
    private String replyingToMessage = null;
    
    // --- Theme Toggle Component ---
    private JToggleButton themeToggle;
    private boolean isDarkMode = false; // Start with Light Mode

    // ==============================================
    // [SECTION: LOGIC COMPONENTS]
    // ==============================================
    private NetworkClient networkClient;
    private String username;
    private String serverIP = "";
    private int serverPort = 5000;
    
    private HashMap<String, PrivateChatWindow> privateChatWindows = new HashMap<>();
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // --- Chat Styling ---
    private StyledDocument chatDocument;
    private SimpleAttributeSet styleSender, styleMessage, styleSystem, styleError;

    // ==============================================
    // [MAIN METHOD]
    // ==============================================
    public static void main(String[] args) {
        FlatLightLaf.setup(); // Default: Light Mode
        SwingUtilities.invokeLater(() -> new ChatClient().go());
    }

    // ==============================================
    // [INITIALIZATION FLOW]
    // ==============================================
    public void go() {
        promptForUsername();      // Step 1: Get username
        promptForServerConfig();  // Step 2: Get server config
        initUI();                 // Step 3: Build UI
        initSystemTray();         // Step 4: Setup system tray
        initNetwork();            // Step 5: Connect to server
        frame.setVisible(true);   // Step 6: Show window
    }

    // --- Network Initialization ---
    private void initNetwork() {
        try {
            System.out.println("Menghubungkan ke " + serverIP + ":" + serverPort);
            networkClient = new NetworkClient(serverIP, serverPort, username, this);
            appendSystemMessage("Terhubung ke server.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Gagal terhubung: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // ==============================================
    // [SECTION: CLIENT LISTENER IMPLEMENTATION]
    // Handles all incoming events from server
    // ==============================================
    
    @Override
    public void onMessageReceived(String sender, String message, boolean isPrivate) {
        if (isPrivate) {
            // Route to private chat window
            handlePrivateIncoming(sender, message);
        } else {
            // Display in main chat
            SwingUtilities.invokeLater(() -> {
                // Create NEW style objects for each message to preserve colors
                SimpleAttributeSet sStyle = new SimpleAttributeSet();
                StyleConstants.setBold(sStyle, true);
                
                SimpleAttributeSet mStyle = new SimpleAttributeSet();
                // Set message color based on current theme
                StyleConstants.setForeground(mStyle, isDarkMode ? new Color(220, 220, 220) : new Color(50, 50, 50));

                // Color sender name based on who sent it
                if (sender != null && sender.startsWith(username)) {
                     StyleConstants.setForeground(sStyle, new Color(0, 130, 0)); // Green for self
                } else if (sender != null) {
                     StyleConstants.setForeground(sStyle, new Color(0, 102, 204)); // Blue for others
                }
                
                // System messages have no sender
                if (sender == null) {
                    sStyle = styleSystem;
                    mStyle = styleSystem;
                }

                appendMessage(sender, message, sStyle, mStyle);
                
                // Show notification if window not focused
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
        final File[] selectedFile = new File[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                String msg = (sender == null ? "Broadcast" : sender) + " mengirim file: " + fileName + " (" + (fileSize/1024) + " KB)";
                
                // Show in private chat if available
                if (sender != null && privateChatWindows.containsKey(sender)) {
                    privateChatWindows.get(sender).appendMessage("Receiving file: " + fileName);
                } else {
                    appendSystemMessage(msg);
                    showNotification("File Masuk", msg);
                }

                // Ask user where to save
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

    // ==============================================
    // [SECTION: PRIVATE CHAT MANAGEMENT]
    // ==============================================

    private void handlePrivateIncoming(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            // Create window if doesn't exist
            if (!privateChatWindows.containsKey(sender)) {
                openPrivateChat(sender);
            }
            
            // Append message to private window
            PrivateChatWindow window = privateChatWindows.get(sender);
            if (window != null) {
                window.appendMessage(sender + ": " + message);
                if (!window.isFocused()) showNotification("Private Msg: " + sender, message);
            }
        });
    }

    // [METHOD: Send Private Message]
    public void sendPrivateMessage(String target, String message) {
        networkClient.sendPrivateMessage(target, message);
    }

    // [METHOD: Send Private File]
    public void sendPrivateFile(String target, File file) {
        networkClient.sendFile(file, target);
        PrivateChatWindow window = privateChatWindows.get(target);
        if (window != null) {
            window.appendMessage("File " + file.getName() + " dikirim.");
        }
    }

    // [METHOD: Remove Private Chat Window]
    public void removePrivateChatWindow(String username) {
        privateChatWindows.remove(username);
    }

    // [METHOD: Open Private Chat Window]
    private void openPrivateChat(String targetUser) {
        if (privateChatWindows.containsKey(targetUser)) {
            // Bring existing window to front
            PrivateChatWindow window = privateChatWindows.get(targetUser);
            window.setVisible(true);
            window.toFront();
        } else {
            // Create new private chat window
            PrivateChatWindow window = new PrivateChatWindow(targetUser, this);
            privateChatWindows.put(targetUser, window);
            window.setVisible(true);
        }
    }

    // ==============================================
    // [SECTION: THEME TOGGLE FEATURE]
    // Dark Mode <-> Light Mode Switch
    // ==============================================
    
    // [METHOD: Toggle Between Dark and Light Mode]
    private void toggleTheme() {
        try {
            if (isDarkMode) {
                // Switch to Light Mode
                FlatLightLaf.setup();
                isDarkMode = false;
                themeToggle.setText("🌙");
                themeToggle.setToolTipText("Switch to Dark Mode");
            } else {
                // Switch to Dark Mode
                FlatDarkLaf.setup();
                isDarkMode = true;
                themeToggle.setText("☀️");
                themeToggle.setToolTipText("Switch to Light Mode");
            }
            
            // Update all UI components to new theme
            SwingUtilities.updateComponentTreeUI(frame);
            
            // Update chat text styles
            updateChatStyles();
            
            // CRITICAL: Recolor all existing chat text
            recolorExistingText();
            
            // Update all open private chat windows
            for (PrivateChatWindow window : privateChatWindows.values()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // [METHOD: Update Chat Style Colors Based on Theme]
    private void updateChatStyles() {
        if (isDarkMode) {
            // Dark Mode: Light colors for text
            StyleConstants.setForeground(styleMessage, new Color(220, 220, 220));
            StyleConstants.setForeground(styleSystem, new Color(150, 150, 150));
        } else {
            // Light Mode: Dark colors for text
            StyleConstants.setForeground(styleMessage, new Color(50, 50, 50));
            StyleConstants.setForeground(styleSystem, new Color(100, 100, 100));
        }
    }
    
    // [METHOD: Recolor All Existing Text in Chat]
    // This is critical to make old messages readable after theme switch
    private void recolorExistingText() {
        try {
            Element root = chatDocument.getDefaultRootElement();
            int lineCount = root.getElementCount();
            
            // Define colors based on current theme
            Color timestampColor = isDarkMode ? new Color(150, 150, 150) : new Color(100, 100, 100);
            Color senderColor = new Color(0, 102, 204);
            Color senderSelfColor = new Color(0, 130, 0);
            Color messageColor = isDarkMode ? new Color(220, 220, 220) : new Color(50, 50, 50);
            Color systemColor = isDarkMode ? new Color(150, 150, 150) : new Color(100, 100, 100);
            Color errorColor = new Color(220, 50, 50);
            
            // Process each line in chat
            for (int i = 0; i < lineCount; i++) {
                Element line = root.getElement(i);
                int start = line.getStartOffset();
                int end = line.getEndOffset();
                
                if (end <= start) continue;
                
                String lineText = chatDocument.getText(start, end - start);
                
                // Parse line format: [HH:mm:ss] Sender message
                if (lineText.startsWith("[") && lineText.contains("] ")) {
                    int timestampEnd = lineText.indexOf("] ");
                    
                    // Recolor timestamp [HH:mm:ss]
                    SimpleAttributeSet tsStyle = new SimpleAttributeSet();
                    StyleConstants.setItalic(tsStyle, true);
                    StyleConstants.setForeground(tsStyle, timestampColor);
                    chatDocument.setCharacterAttributes(start, timestampEnd + 2, tsStyle, false);
                    
                    int contentStart = start + timestampEnd + 2;
                    int contentLength = end - contentStart;
                    String content = lineText.substring(timestampEnd + 2);
                    
                    // Identify message type and apply appropriate color
                    if (content.contains("[ERROR]")) {
                        // Error messages
                        SimpleAttributeSet errStyle = new SimpleAttributeSet();
                        StyleConstants.setBold(errStyle, true);
                        StyleConstants.setForeground(errStyle, errorColor);
                        chatDocument.setCharacterAttributes(contentStart, contentLength, errStyle, false);
                    } 
                    else if (content.contains("Terhubung") || content.contains("bergabung") || 
                             content.contains("keluar") || content.contains("File") || 
                             content.contains("Receiving") || content.contains("Menyimpan")) {
                        // System messages
                        SimpleAttributeSet sysStyle = new SimpleAttributeSet();
                        StyleConstants.setItalic(sysStyle, true);
                        StyleConstants.setForeground(sysStyle, systemColor);
                        chatDocument.setCharacterAttributes(contentStart, contentLength, sysStyle, false);
                    }
                    else if (content.contains(": ")) {
                        // Regular message with sender
                        int colonIndex = content.indexOf(": ");
                        String senderPart = content.substring(0, colonIndex);
                        
                        // Recolor sender name
                        SimpleAttributeSet senderStyle = new SimpleAttributeSet();
                        StyleConstants.setBold(senderStyle, true);
                        if (senderPart.startsWith(username)) {
                            StyleConstants.setForeground(senderStyle, senderSelfColor);
                        } else {
                            StyleConstants.setForeground(senderStyle, senderColor);
                        }
                        chatDocument.setCharacterAttributes(contentStart, colonIndex + 2, senderStyle, false);
                        
                        // Recolor message text
                        SimpleAttributeSet msgStyle = new SimpleAttributeSet();
                        StyleConstants.setForeground(msgStyle, messageColor);
                        int msgStart = contentStart + colonIndex + 2;
                        int msgLength = end - msgStart;
                        if (msgLength > 0) {
                            chatDocument.setCharacterAttributes(msgStart, msgLength, msgStyle, false);
                        }
                    } else {
                        // Content without sender (system-like)
                        SimpleAttributeSet msgStyle = new SimpleAttributeSet();
                        StyleConstants.setForeground(msgStyle, systemColor);
                        StyleConstants.setItalic(msgStyle, true);
                        chatDocument.setCharacterAttributes(contentStart, contentLength, msgStyle, false);
                    }
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==============================================
    // [SECTION: GUI SETUP]
    // ==============================================

    private void initUI() {
        frame = new JFrame("LAN Messenger - (" + username + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initStyles();

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // [COMPONENT: Theme Toggle Button in Top Panel]
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        
        themeToggle = new JToggleButton("🌙");
        themeToggle.setToolTipText("Switch to Dark Mode");
        themeToggle.setFocusable(false);
        themeToggle.setPreferredSize(new Dimension(50, 30));
        themeToggle.addActionListener(e -> toggleTheme());
        
        JPanel themePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        themePanel.add(new JLabel("Theme:"));
        themePanel.add(themeToggle);
        topPanel.add(themePanel, BorderLayout.EAST);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // [COMPONENT: Left and Right Panels]
        JPanel leftPanel = createLeftPanel();
        JPanel rightPanel = createRightPanel();

        // [COMPONENT: Split Pane]
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

    // [METHOD: Initialize Text Styles]
    private void initStyles() {
        styleSender = new SimpleAttributeSet();
        StyleConstants.setBold(styleSender, true);
        StyleConstants.setForeground(styleSender, new Color(0, 102, 204));

        styleMessage = new SimpleAttributeSet();
        StyleConstants.setForeground(styleMessage, isDarkMode ? new Color(220, 220, 220) : new Color(50, 50, 50));

        styleSystem = new SimpleAttributeSet();
        StyleConstants.setItalic(styleSystem, true);
        StyleConstants.setForeground(styleSystem, isDarkMode ? new Color(150, 150, 150) : Color.GRAY);
        
        styleError = new SimpleAttributeSet();
        StyleConstants.setBold(styleError, true);
        StyleConstants.setForeground(styleError, Color.RED);
    }

    // [METHOD: Create Left Panel - User List]
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setPreferredSize(new Dimension(180, 0));
        
        // [COMPONENT: User List]
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setCellRenderer(new UserListRenderer());
        
        // [EVENT: Double-click to open private chat]
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = userList.getSelectedValue();
                    if (selected != null && !selected.equals(username)) openPrivateChat(selected);
                }
            }
        });

        // [COMPONENT: Private Chat Button]
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

    // [METHOD: Create Right Panel - Chat Area]
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        
        // [COMPONENT: Chat Display Area]
        incomingMessages = new JTextPane();
        incomingMessages.setEditable(false);
        chatDocument = incomingMessages.getStyledDocument();
        
        // [EVENT: Right-click for Reply Menu]
        incomingMessages.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) { if(e.isPopupTrigger()) showPopup(e); }
            public void mousePressed(MouseEvent e) { if(e.isPopupTrigger()) showPopup(e); }
        });

        // [COMPONENT: Message Input Field]
        outgoingMessage = new JTextField();
        outgoingMessage.addActionListener(e -> sendMessage());
        
        // [COMPONENT: Send Button]
        JButton btnSend = new JButton("Kirim \u27A4");
        btnSend.addActionListener(e -> sendMessage());
        
        // [COMPONENT: File Send Button]
        JButton btnFile = new JButton("File \uD83D\uDCCE");
        btnFile.addActionListener(e -> sendFileAction());

        JPanel bottomBox = new JPanel(new BorderLayout(5,0));
        JPanel btnBox = new JPanel(new GridLayout(1,2,5,0));
        btnBox.add(btnSend);
        btnBox.add(btnFile);
        bottomBox.add(outgoingMessage, BorderLayout.CENTER);
        bottomBox.add(btnBox, BorderLayout.EAST);

        // [COMPONENT: Reply Panel]
        replyPanel = new JPanel(new BorderLayout());
        replyPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        replyPanel.setVisible(false);
        replyLabel = new JLabel();
        
        // [COMPONENT: Close Reply Button]
        JButton closeReply = new JButton("x");
        closeReply.setFocusable(false);
        closeReply.addActionListener(e -> cancelReply());
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

    // ==============================================
    // [SECTION: MESSAGE SENDING]
    // ==============================================

    // [METHOD: Send Message to Server]
    private void sendMessage() {
        String text = outgoingMessage.getText().trim();
        if (text.isEmpty()) return;

        // Format message with reply if replying
        String fullMsg = (replyingToMessage != null) 
                ? username + " [↩ " + replyingToMessage + "]: " + text 
                : username + ": " + text;

        networkClient.sendBroadcastMessage(fullMsg);
        
        outgoingMessage.setText("");
        cancelReply(); // Clear reply state after sending
    }

    // [METHOD: Send File via File Chooser]
    private void sendFileAction() {
        JFileChooser ch = new JFileChooser();
        if (ch.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File f = ch.getSelectedFile();
            appendSystemMessage("Mengirim file: " + f.getName() + "...");
            
            // Send file in separate thread to avoid UI blocking
            new Thread(() -> {
                networkClient.sendFile(f, null);
                appendSystemMessage("File terkirim.");
            }).start();
        }
    }
    
    // ==============================================
    // [SECTION: REPLY FEATURE]
    // ==============================================
    
    // [METHOD: Show Reply Context Menu]
    private void showPopup(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem replyItem = new JMenuItem("Reply");
        
        replyItem.addActionListener(ev -> {
            try {
                // Step 1: Check if user selected text manually
                String selectedText = incomingMessages.getSelectedText();
                
                // Step 2: If no selection, get clicked line
                if (selectedText == null || selectedText.trim().isEmpty()) {
                    int offset = incomingMessages.viewToModel(e.getPoint());
                    if (offset >= 0) {
                        Element root = chatDocument.getDefaultRootElement();
                        int rowIndex = root.getElementIndex(offset);
                        Element line = root.getElement(rowIndex);
                        
                        int start = line.getStartOffset();
                        int end = line.getEndOffset();
                        
                        if (end > start) {
                            // Get full line text
                            selectedText = chatDocument.getText(start, end - start - 1);
                        }
                    }
                }

                // Step 3: Clean timestamp and set reply
                if (selectedText != null && !selectedText.trim().isEmpty()) {
                    // Remove timestamp [HH:mm:ss] from start
                    String cleanText = selectedText.replaceAll("^\\[\\d{2}:\\d{2}:\\d{2}\\]\\s*", "").trim();
                    setReplyTo(cleanText);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        menu.add(replyItem);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }
    
    // [METHOD: Set Reply Target Message]
    private void setReplyTo(String message) {
        // Clean nested replies to prevent "replyception"
        String cleanedMessage = cleanReplyChain(message);
        replyingToMessage = cleanedMessage;
        
        // Truncate display text if too long
        String displayMessage = cleanedMessage;
        if (displayMessage.length() > 50) {
            displayMessage = displayMessage.substring(0, 50) + "...";
        }
        
        replyLabel.setText("<html>Replying to: <i>" + displayMessage + "</i></html>");
        replyPanel.setVisible(true);
        outgoingMessage.requestFocus();
    }
    
    // [METHOD: Clean Reply Chain to Prevent Nested Replies]
    // FIXED: Hanya ambil pesan terakhir, tidak berantai
    private String cleanReplyChain(String message) {
        // Cari pola reply: [↩ ... ]:
        // Ambil hanya pesan SETELAH reply marker terakhir
        
        int lastReplyStart = message.lastIndexOf("[↩");
        
        // Jika tidak ada reply marker, return message asli
        if (lastReplyStart == -1) {
            return message;
        }
        
        // Cari penutup ]: setelah [↩
        int replyEnd = message.indexOf("]:", lastReplyStart);
        
        if (replyEnd != -1) {
            // Ambil text SETELAH ]: (ini adalah pesan actual yang direply)
            String actualMessage = message.substring(replyEnd + 2).trim();
            
            // Hapus nama sender di depan jika ada (format: "nama: pesan")
            int colonIndex = actualMessage.indexOf(": ");
            if (colonIndex > 0 && colonIndex < 20) { // Nama sender biasanya pendek
                // Pastikan ini bukan bagian dari pesan yang panjang
                String possibleSender = actualMessage.substring(0, colonIndex);
                // Jika tidak ada spasi, kemungkinan ini memang sender name
                if (!possibleSender.contains(" ")) {
                    actualMessage = actualMessage.substring(colonIndex + 2).trim();
                }
            }
            
            return actualMessage;
        }
        
        return message;
    }
    
    // [METHOD: Cancel Reply]
    private void cancelReply() {
        replyingToMessage = null;
        replyPanel.setVisible(false);
    }

    // ==============================================
    // [SECTION: CHAT MESSAGE DISPLAY]
    // ==============================================

    // [METHOD: Append Message to Chat]
    private void appendMessage(String sender, String message, SimpleAttributeSet styleS, SimpleAttributeSet styleM) {
        try {
            String time = LocalTime.now().format(timeFormatter);
            chatDocument.insertString(chatDocument.getLength(), "[" + time + "] ", styleSystem);
            if (sender != null) chatDocument.insertString(chatDocument.getLength(), sender + " ", styleS);
            chatDocument.insertString(chatDocument.getLength(), message + "\n", styleM);
            incomingMessages.setCaretPosition(chatDocument.getLength());
        } catch (BadLocationException e) { e.printStackTrace(); }
    }
    
    // [METHOD: Append System Message]
    private void appendSystemMessage(String msg) {
        SwingUtilities.invokeLater(() -> appendMessage(null, msg, styleSystem, styleSystem));
    }

    // ==============================================
    // [SECTION: INITIAL PROMPTS]
    // ==============================================

    // [METHOD: Prompt User for Username]
    private void promptForUsername() {
        while (username == null || username.trim().isEmpty()) {
            username = JOptionPane.showInputDialog(null, "Masukkan nama Anda:", "Login", JOptionPane.PLAIN_MESSAGE);
            if (username == null) System.exit(0);
        }
    }

    // [METHOD: Prompt User for Server Configuration]
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
    
    // ==============================================
    // [SECTION: SYSTEM TRAY & NOTIFICATIONS]
    // ==============================================
    
    // [METHOD: Show System Notification]
    private void showNotification(String title, String msg) {
        if (trayIcon != null) trayIcon.displayMessage(title, msg, TrayIcon.MessageType.INFO);
    }

    // [METHOD: Initialize System Tray Icon]
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

    // ==============================================
    // [SECTION: CUSTOM RENDERER]
    // ==============================================

    // [CLASS: Custom User List Cell Renderer]
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