package projek.lanmessanger;

import com.formdev.flatlaf.FlatLightLaf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
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
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class ChatClient {
    private JTextPane incomingMessages;
    private JTextField outgoingMessage;
    
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    
    private String username;
    private JFrame frame; 
    
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private TrayIcon trayIcon;
    
    // Reply feature
    private JPanel replyPanel;
    private JLabel replyLabel;
    private String replyingToMessage = null;
    
    // Private chat feature
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private HashMap<String, PrivateChatWindow> privateChatWindows;

    // Styling
    private StyledDocument chatDocument;
    private SimpleAttributeSet styleSender;
    private SimpleAttributeSet styleMessage;
    private SimpleAttributeSet styleSystem;
    private SimpleAttributeSet styleError;

    public static void main(String[] args) {
        FlatLightLaf.setup(); 
        SwingUtilities.invokeLater(() -> new ChatClient().go());
    }

    public void go() {
        privateChatWindows = new HashMap<>();
        promptForUsername();
        initUI();
        initSystemTray();
        setupNetworking();

        Thread readerThread = new Thread(new IncomingReader());
        readerThread.start();

        frame.setVisible(true);
    }

    private void promptForUsername() {
        while (username == null || username.trim().isEmpty()) {
            username = JOptionPane.showInputDialog(
                    null, 
                    "Masukkan nama Anda:", 
                    "Login LAN Messenger", 
                    JOptionPane.PLAIN_MESSAGE
            );

            if (username == null) { 
                System.exit(0); 
            }
            if (username.trim().isEmpty()) { 
                JOptionPane.showMessageDialog(null, "Nama tidak boleh kosong.", "Error", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void initUI() {
        frame = new JFrame("Simple LAN Messenger - (" + username + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // LEFT SIDE: User list
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(180, 0));
        
        JLabel userListLabel = new JLabel("Pengguna Online");
        userListLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 0));
        
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setCellRenderer(new UserListRenderer());
        
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = userList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selectedUser = userListModel.getElementAt(index);
                        if (!selectedUser.equals(username)) {
                            openPrivateChat(selectedUser);
                        }
                    }
                }
            }
        });
        
        JScrollPane userListScroll = new JScrollPane(userList);
        userListScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        
        JButton privateChatBtn = new JButton("Private Chat");
        privateChatBtn.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser != null && !selectedUser.equals(username)) {
                openPrivateChat(selectedUser);
            } else if (selectedUser == null) {
                JOptionPane.showMessageDialog(frame, "Pilih user terlebih dahulu!", "Info", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        leftPanel.add(userListLabel, BorderLayout.NORTH);
        leftPanel.add(userListScroll, BorderLayout.CENTER);
        leftPanel.add(privateChatBtn, BorderLayout.SOUTH);

        // RIGHT SIDE: Chat area
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        
        incomingMessages = new JTextPane();
        incomingMessages.setEditable(false);
        chatDocument = incomingMessages.getStyledDocument();
        
        // Styles
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
        
        incomingMessages.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopupMenu(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopupMenu(e);
            }
            
            private void showPopupMenu(MouseEvent e) {
                try {
                    int offset = incomingMessages.viewToModel2D(e.getPoint());
                    int start = incomingMessages.getStyledDocument().getParagraphElement(offset).getStartOffset();
                    int end = incomingMessages.getStyledDocument().getParagraphElement(offset).getEndOffset();
                    
                    String clickedLine = incomingMessages.getText(start, end - start).trim();
                    
                    if (!clickedLine.isEmpty() &&
                        !clickedLine.contains("telah bergabung") && 
                        !clickedLine.contains("telah keluar") && 
                        !clickedLine.contains("Mengirim file") &&
                        !clickedLine.contains("berhasil terkirim") &&
                        !clickedLine.contains("Menerima file") &&
                        !clickedLine.contains("berhasil disimpan") &&
                        !clickedLine.contains("[ERROR]")) {
                        
                        JPopupMenu popup = new JPopupMenu();
                        JMenuItem replyItem = new JMenuItem("Reply");
                        String messageToReply = clickedLine.substring(clickedLine.indexOf("]") + 2);
                        
                        replyItem.addActionListener(ev -> setReplyTo(messageToReply));
                        popup.add(replyItem);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                } catch (Exception ex) {}
            }
        });
        
        JScrollPane qScroller = new JScrollPane(incomingMessages);
        qScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        qScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        qScroller.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        
        // Reply panel
        replyPanel = new JPanel(new BorderLayout(5, 0));
        replyPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        replyPanel.setVisible(false);
        
        replyLabel = new JLabel();
        JButton cancelReplyButton = new JButton("×");
        cancelReplyButton.setFocusable(false);
        cancelReplyButton.addActionListener(e -> cancelReply());
        
        replyPanel.add(replyLabel, BorderLayout.CENTER);
        replyPanel.add(cancelReplyButton, BorderLayout.EAST);
        
        // Input Panel
        JPanel southPanel = new JPanel(new BorderLayout(5, 0)); 
        outgoingMessage = new JTextField(20);
        outgoingMessage.addActionListener(new SendButtonListener());

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0)); 
        JButton sendButton = new JButton("Kirim \u27A4");
        sendButton.addActionListener(new SendButtonListener());
        
        JButton sendFileButton = new JButton("File \uD83D\uDCCE");
        sendFileButton.addActionListener(new SendFileButtonListener());
        
        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);

        southPanel.add(outgoingMessage, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.EAST);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(replyPanel, BorderLayout.NORTH);
        bottomPanel.add(southPanel, BorderLayout.CENTER);

        rightPanel.add(qScroller, BorderLayout.CENTER);
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.1);
        splitPane.setBorder(null);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        frame.add(mainPanel);

        frame.pack(); 
        frame.setMinimumSize(new Dimension(600, 400));
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
    }
    
    private void setReplyTo(String message) {
        String cleanedMessage = cleanReplyChain(message);
        replyingToMessage = cleanedMessage;
        
        String displayMessage = cleanedMessage;
        if (displayMessage.length() > 50) {
            displayMessage = displayMessage.substring(0, 50) + "...";
        }
        
        replyLabel.setText("<html>Replying to: <i>" + displayMessage + "</i></html>");
        replyPanel.setVisible(true);
        outgoingMessage.requestFocus();
        
        frame.validate();
        frame.repaint();
    }
    
    private String cleanReplyChain(String message) {
        int lastReplyIndex = message.lastIndexOf("[↩");
        if (lastReplyIndex == -1) return message;
        
        int endReplyIndex = message.indexOf("]:", lastReplyIndex);
        if (endReplyIndex != -1 && endReplyIndex + 2 < message.length()) {
            return message.substring(endReplyIndex + 2).trim();
        }
        return message;
    }
    
    private void cancelReply() {
        replyingToMessage = null;
        replyPanel.setVisible(false);
        frame.validate();
        frame.repaint();
    }
    
    // PRIVATE CHAT METHODS
    private void openPrivateChat(String targetUser) {
        if (privateChatWindows.containsKey(targetUser)) {
            PrivateChatWindow window = privateChatWindows.get(targetUser);
            window.setVisible(true);
            window.toFront();
            window.requestFocus();
        } else {
            PrivateChatWindow window = new PrivateChatWindow(targetUser, this);
            privateChatWindows.put(targetUser, window);
            window.setVisible(true);
        }
    }
    
    public void sendPrivateMessage(String target, String message) {
        try {
            dataOut.writeInt(3); 
            dataOut.writeUTF(target);
            dataOut.writeUTF(message);
            dataOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Gagal mengirim pesan private!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public void sendPrivateFile(String target, File file) {
        try {
            dataOut.writeInt(6);
            dataOut.writeUTF(target);
            dataOut.writeUTF(file.getName());
            dataOut.writeLong(file.length());
            
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                dataOut.write(buffer, 0, read);
            }
            fis.close();
            dataOut.flush();
            
            PrivateChatWindow window = privateChatWindows.get(target);
            if (window != null) {
                window.appendMessage("File " + file.getName() + " berhasil terkirim.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Gagal mengirim file!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public void removePrivateChatWindow(String username) {
        privateChatWindows.remove(username);
    }
    
    private void updateUserList(String[] users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String user : users) {
                userListModel.addElement(user);
            }
        });
    }

    private void initSystemTray() {
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray tidak didukung");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = new ImageIcon("image/yuta.png").getImage(); 
            trayIcon = new TrayIcon(image, "LAN Messenger");
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip("LAN Messenger (" + username + ")");
            tray.add(trayIcon);
            
            trayIcon.addActionListener(e -> {
                frame.setVisible(true);
                frame.setState(Frame.NORMAL);
            });
        } catch (Exception e) {
            System.out.println("Gagal membuat TrayIcon: " + e.getMessage());
            trayIcon = null;
        }
    }

    private void showNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    private void setupNetworking() {
        try {
            // Gunakan IP server yang sesuai
            Socket sock = new Socket("192.168.18.62", 5000);
            dataIn = new DataInputStream(sock.getInputStream());
            dataOut = new DataOutputStream(sock.getOutputStream());
            System.out.println("Koneksi berhasil dibuat.");
            
            dataOut.writeUTF(username);
            dataOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(null, "Gagal terhubung ke server: " + e.getMessage(), "Koneksi Gagal", JOptionPane.ERROR_MESSAGE)
            );
            System.exit(1);
        }
    }

    public class SendButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            String message = outgoingMessage.getText().trim();
            if (message.isEmpty()) return;
            
            try {
                String fullMessage;
                if (replyingToMessage != null) {
                    String replyPreview = replyingToMessage;
                    if (replyPreview.length() > 40) replyPreview = replyPreview.substring(0, 40) + "...";
                    if(replyPreview.contains(":")) replyPreview = replyPreview.substring(replyPreview.indexOf(":") + 1).trim();
                    
                    fullMessage = username + " [↩ " + replyPreview + "]: " + message;
                    cancelReply();
                } else {
                    fullMessage = username + ": " + message;
                }
                
                dataOut.writeInt(1);
                dataOut.writeUTF(fullMessage);
                dataOut.flush();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            outgoingMessage.setText("");
            outgoingMessage.requestFocus();
        }
    }

    public class SendFileButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(frame);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                new Thread(() -> sendFile(selectedFile)).start();
            }
        }
        
        private void sendFile(File selectedFile) {
            try {
                appendSystemMessage("Mengirim file: " + selectedFile.getName() + "...");
                
                dataOut.writeInt(2);
                dataOut.writeUTF(selectedFile.getName());
                dataOut.writeLong(selectedFile.length());

                FileInputStream fis = new FileInputStream(selectedFile);
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) > 0) {
                    dataOut.write(buffer, 0, read);
                }
                fis.close();
                dataOut.flush();
                appendSystemMessage("File " + selectedFile.getName() + " berhasil terkirim.");
            } catch (IOException e) {
                e.printStackTrace();
                appendErrorMessage("Gagal mengirim file: " + e.getMessage());
            }
        }
    }

    // INNER CLASS: IncomingReader
    public class IncomingReader implements Runnable {
        public void run() {
            try {
                while (true) {
                    int messageType = dataIn.readInt();
                    String time = LocalTime.now().format(timeFormatter);

                    if (messageType == 1) { // Broadcast text
                        String message = dataIn.readUTF();
                        String sender = null;
                        String content = message;
                        SimpleAttributeSet senderStyle = styleSender;
                        SimpleAttributeSet contentStyle = styleMessage;

                        if (message.contains(":")) {
                            try {
                                int separatorIndex = message.indexOf(":");
                                sender = message.substring(0, separatorIndex + 1); 
                                content = message.substring(separatorIndex + 1);    
                            } catch (Exception e) {
                                sender = null;
                                content = message;
                                senderStyle = styleSystem;
                                contentStyle = styleSystem;
                            }
                        } else {
                            senderStyle = styleSystem;
                            contentStyle = styleSystem;
                        }
                        
                        if (sender != null && sender.startsWith(username + ":")) {
                             StyleConstants.setForeground(senderStyle, new Color(0, 130, 0));
                        }

                        appendMessage(time, sender, content, senderStyle, contentStyle);
                        if (!frame.isFocused()) showNotification("Pesan Baru", message);
                    
                    } else if (messageType == 2) { // Broadcast file
                        handleIncomingFile();
                        
                    } else if (messageType == 3) { // Private message
                        String sender = dataIn.readUTF();
                        String message = dataIn.readUTF();
                        handlePrivateMessage(sender, message);
                        
                    } else if (messageType == 4) { // User list update
                        int userCount = dataIn.readInt();
                        String[] users = new String[userCount];
                        for (int i = 0; i < userCount; i++) {
                            users[i] = dataIn.readUTF();
                        }
                        updateUserList(users);
                        
                    } else if (messageType == 6) { // Private file
                        String sender = dataIn.readUTF();
                        handlePrivateFile(sender);
                        
                    } else if (messageType == 99) { // Error
                        String errorMsg = dataIn.readUTF();
                        JOptionPane.showMessageDialog(frame, "Error dari server: " + errorMsg, "Koneksi Gagal", JOptionPane.ERROR_MESSAGE);
                        System.exit(0);
                    }
                }
            } catch (EOFException | SocketException e) {
                 appendErrorMessage("Koneksi ke server terputus.");
            } catch (Exception ex) {
                ex.printStackTrace();
                appendErrorMessage("Gagal membaca data dari server: " + ex.getMessage());
            }
        }
        
        private void handlePrivateMessage(String sender, String message) {
            if (!privateChatWindows.containsKey(sender)) {
                SwingUtilities.invokeLater(() -> openPrivateChat(sender));
            }
            
            PrivateChatWindow window = privateChatWindows.get(sender);
            if (window != null) {
                window.appendMessage(sender + ": " + message);
                if (!window.isFocused()) showNotification("Private Message from " + sender, message);
            }
        }
        
        private void handlePrivateFile(String sender) throws IOException {
            String fileName = dataIn.readUTF();
            long fileSize = dataIn.readLong();
            
            if (!privateChatWindows.containsKey(sender)) {
                SwingUtilities.invokeLater(() -> openPrivateChat(sender));
            }
            
            PrivateChatWindow window = privateChatWindows.get(sender);
            if (window != null) {
                String fileInfo = "Receiving file: " + fileName + " (" + (fileSize / 1024) + " KB)";
                window.appendMessage(fileInfo);
                if (!window.isFocused()) showNotification("Private File from " + sender, fileInfo);
            }
            
            JFileChooser saveChooser = new JFileChooser();
            File saveDir = new File("Files");
            if (!saveDir.exists()) saveDir.mkdir(); 
            saveChooser.setCurrentDirectory(saveDir.getAbsoluteFile());
            saveChooser.setSelectedFile(new File(fileName)); 
            
            final File[] saveFile = new File[1]; 
            try {
                SwingUtilities.invokeAndWait(() -> {
                    Component parent = (window != null) ? window : frame;
                    int saveResult = saveChooser.showSaveDialog(parent);
                    if (saveResult == JFileChooser.APPROVE_OPTION) {
                        saveFile[0] = saveChooser.getSelectedFile();
                    } else {
                        saveFile[0] = null;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                saveFile[0] = null; 
            }

            OutputStream fos = null;
            if (saveFile[0] != null) {
                fos = new FileOutputStream(saveFile[0]);
                if (window != null) window.appendMessage("Saving to: " + saveFile[0].getAbsolutePath() + "...");
            } else {
                if (window != null) window.appendMessage("File save cancelled. Discarding data...");
            }
            
            byte[] buffer = new byte[4096];
            int read;
            long remaining = fileSize;
            while (remaining > 0 && (read = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                if (fos != null) fos.write(buffer, 0, read);
                remaining -= read;
            }

            if (fos != null) {
                fos.close();
                if (window != null) window.appendMessage("File " + saveFile[0].getName() + " saved successfully.");
            } else {
                if (window != null) window.appendMessage("File " + fileName + " discarded.");
            }
        }
        
        private void handleIncomingFile() throws IOException {
            String fileName = dataIn.readUTF();
            long fileSize = dataIn.readLong();
            
            String fileInfo = "Menerima file: " + fileName + " (" + (fileSize / 1024) + " KB).";
            appendSystemMessage(fileInfo);
            
            if (!frame.isFocused()) showNotification("File Baru", fileInfo);
            
            JFileChooser saveChooser = new JFileChooser();
            File saveDir = new File("Files");
            if (!saveDir.exists()) saveDir.mkdir(); 
            saveChooser.setCurrentDirectory(saveDir.getAbsoluteFile());
            saveChooser.setSelectedFile(new File(fileName)); 
            
            final File[] saveFile = new File[1]; 
            try {
                SwingUtilities.invokeAndWait(() -> {
                    int saveResult = saveChooser.showSaveDialog(frame);
                    if (saveResult == JFileChooser.APPROVE_OPTION) {
                        saveFile[0] = saveChooser.getSelectedFile();
                    } else {
                        saveFile[0] = null;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                saveFile[0] = null; 
            }

            OutputStream fos = null;
            if (saveFile[0] != null) {
                fos = new FileOutputStream(saveFile[0]);
                appendSystemMessage("Menyimpan ke: " + saveFile[0].getAbsolutePath() + "...");
            } else {
                appendSystemMessage("Penyimpanan file dibatalkan. Mengabaikan data...");
            }
            
            byte[] buffer = new byte[4096];
            int read;
            long remaining = fileSize;
            while (remaining > 0 && (read = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                if (fos != null) fos.write(buffer, 0, read);
                remaining -= read;
            }

            if (fos != null) {
                fos.close();
                appendSystemMessage("File " + saveFile[0].getName() + " berhasil disimpan.");
            } else {
                appendSystemMessage("File " + fileName + " selesai diabaikan.");
            }
        }
    }
    
    private void appendMessage(String time, String sender, String message, SimpleAttributeSet senderStyle, SimpleAttributeSet messageStyle) {
        SwingUtilities.invokeLater(() -> {
            try {
                chatDocument.insertString(chatDocument.getLength(), "[" + time + "] ", styleSystem);
                if (sender != null && !sender.isEmpty()) {
                    chatDocument.insertString(chatDocument.getLength(), sender, senderStyle);
                }
                chatDocument.insertString(chatDocument.getLength(), message + "\n", messageStyle);
                incomingMessages.setCaretPosition(chatDocument.getLength());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void appendSystemMessage(String message) {
        String time = LocalTime.now().format(timeFormatter);
        appendMessage(time, null, message, styleSystem, styleSystem);
    }
    
    private void appendErrorMessage(String message) {
        String time = LocalTime.now().format(timeFormatter);
        appendMessage(time, "[ERROR] ", message, styleError, styleError);
    }
    
    class UserListRenderer extends DefaultListCellRenderer {
        private Color onlineColor = new Color(0, 140, 0); 
        private Color selfColor = new Color(0, 102, 204); 
        private EmptyBorder padding = new EmptyBorder(5, 7, 5, 7); 

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String usernameValue = (String) value;
            setBorder(padding);
            setText("\u25CF " + usernameValue); 
            
            if (usernameValue.equals(ChatClient.this.username)) {
                setText("\u25CF " + usernameValue + " (Anda)");
                setForeground(selfColor);
                if (!isSelected) setBackground(new Color(240, 245, 255));
            } else {
                 setForeground(onlineColor);
                 if (!isSelected) setBackground(Color.WHITE);
            }
            return c;
        }
    }
}