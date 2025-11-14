package projek.lanmessanger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ChatClient {
    private JTextArea incomingMessages;
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

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            System.out.println("Gagal memuat Look and Feel Nimbus, menggunakan default.");
        }
        
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
                JOptionPane.showMessageDialog(
                        null, 
                        "Nama tidak boleh kosong.", 
                        "Error", 
                        JOptionPane.WARNING_MESSAGE
                );
            }
        }
    }

    private void initUI() {
        frame = new JFrame("Simple LAN Messenger - (" + username + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // LEFT SIDE: User list
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(150, 0));
        
        JLabel userListLabel = new JLabel("Online Users");
        userListLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        // Double click to open private chat
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
        
        incomingMessages = new JTextArea(15, 30);
        incomingMessages.setLineWrap(true);
        incomingMessages.setWrapStyleWord(true);
        incomingMessages.setEditable(false);
        
        // Reply feature: mouse listener
        incomingMessages.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }
            
            private void showPopupMenu(MouseEvent e) {
                try {
                    int offset = incomingMessages.viewToModel2D(e.getPoint());
                    int line = incomingMessages.getLineOfOffset(offset);
                    int start = incomingMessages.getLineStartOffset(line);
                    int end = incomingMessages.getLineEndOffset(line);
                    
                    String clickedLine = incomingMessages.getText(start, end - start).trim();
                    
                    if (!clickedLine.isEmpty() && 
                        !clickedLine.contains("telah bergabung") && 
                        !clickedLine.contains("Mengirim file") &&
                        !clickedLine.contains("berhasil terkirim") &&
                        !clickedLine.contains("Menerima file") &&
                        !clickedLine.contains("[ERROR]")) {
                        
                        JPopupMenu popup = new JPopupMenu();
                        JMenuItem replyItem = new JMenuItem("Reply");
                        
                        final String messageToReply = clickedLine;
                        replyItem.addActionListener(ev -> setReplyTo(messageToReply));
                        
                        popup.add(replyItem);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                } catch (Exception ex) {
                    // Ignore
                }
            }
        });
        
        JScrollPane qScroller = new JScrollPane(incomingMessages);
        qScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        qScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Reply panel
        replyPanel = new JPanel(new BorderLayout());
        replyPanel.setBackground(new Color(230, 230, 250));
        replyPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
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
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(new SendButtonListener());
        
        JButton sendFileButton = new JButton("Send File");
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
        
        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(150);
        splitPane.setEnabled(false);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        frame.add(mainPanel);

        frame.pack(); 
        frame.setMinimumSize(frame.getSize());
        
        // Position
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();
        Rectangle bounds = defaultScreen.getDefaultConfiguration().getBounds();
        
        int screenWidth = bounds.width;
        int screenHeight = bounds.height;
        int windowWidth = frame.getWidth();
        int windowHeight = frame.getHeight();
        
        int x = screenWidth - windowWidth - 10;
        int y = screenHeight - windowHeight - 40; 
        
        frame.setLocation(x, y);
    }
    
    private void setReplyTo(String message) {
        String cleanedMessage = cleanReplyChain(message);
        replyingToMessage = cleanedMessage;
        
        String displayMessage = cleanedMessage;
        if (displayMessage.length() > 50) {
            displayMessage = displayMessage.substring(0, 50) + "...";
        }
        
        replyLabel.setText("Replying to: " + displayMessage);
        replyPanel.setVisible(true);
        outgoingMessage.requestFocus();
        
        frame.pack();
    }
    
    private String cleanReplyChain(String message) {
        int lastReplyIndex = message.lastIndexOf("[↩");
        
        if (lastReplyIndex == -1) {
            return message;
        }
        
        int endReplyIndex = message.indexOf("]:", lastReplyIndex);
        
        if (endReplyIndex != -1 && endReplyIndex + 2 < message.length()) {
            return message.substring(endReplyIndex + 2).trim();
        }
        
        return message;
    }
    
    private void cancelReply() {
        replyingToMessage = null;
        replyPanel.setVisible(false);
        frame.pack();
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
            dataOut.writeInt(3); // Type 3: Private message
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
            dataOut.writeInt(6); // Type 6: Private file
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
            Socket sock = new Socket("192.168.18.114", 5000);
            dataIn = new DataInputStream(sock.getInputStream());
            dataOut = new DataOutputStream(sock.getOutputStream());
            System.out.println("Koneksi berhasil dibuat.");
            
            // Send username to server
            dataOut.writeUTF(username);
            dataOut.flush();
            
            // Send join notification
            dataOut.writeInt(1); 
            dataOut.writeUTF("--- " + username + " telah bergabung ---");
            dataOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(null, "Gagal terhubung ke server. Pastikan IP dan Port sudah benar.", "Koneksi Gagal", JOptionPane.ERROR_MESSAGE)
            );
            System.exit(1);
        }
    }

    public class SendButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            String message = outgoingMessage.getText().trim();
            if (message.isEmpty()) {
                return;
            }
            
            try {
                String fullMessage;
                
                if (replyingToMessage != null) {
                    String replyPreview = replyingToMessage;
                    if (replyPreview.length() > 40) {
                        replyPreview = replyPreview.substring(0, 40) + "...";
                    }
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
                appendMessage("Mengirim file: " + selectedFile.getName() + "...\n");
                
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

                appendMessage("File " + selectedFile.getName() + " berhasil terkirim.\n");

            } catch (IOException e) {
                e.printStackTrace();
                appendMessage("[ERROR] Gagal mengirim file: " + e.getMessage() + "\n");
            }
        }
    }

    public class IncomingReader implements Runnable {
        public void run() {
            try {
                while (true) {
                    int messageType = dataIn.readInt();

                    if (messageType == 1) { // Broadcast text
                        String message = dataIn.readUTF();
                        String time = LocalTime.now().format(timeFormatter);
                        appendMessage("[" + time + "] " + message + "\n");
                        
                        if (!frame.isFocused()) {
                            showNotification("Pesan Baru", message);
                        }
                    
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
                    }
                }
            } catch (EOFException | SocketException e) {
                 appendMessage("[ERROR] Koneksi ke server terputus.\n");
            } catch (Exception ex) {
                ex.printStackTrace();
                appendMessage("[ERROR] Gagal membaca data dari server.\n");
            }
        }
        
        private void handlePrivateMessage(String sender, String message) {
            // Open or get existing window
            if (!privateChatWindows.containsKey(sender)) {
                SwingUtilities.invokeLater(() -> openPrivateChat(sender));
            }
            
            PrivateChatWindow window = privateChatWindows.get(sender);
            if (window != null) {
                window.appendMessage(sender + ": " + message);
                
                // Show notification if window not focused
                if (!window.isFocused()) {
                    showNotification("Private Message from " + sender, message);
                }
            }
        }
        
        private void handlePrivateFile(String sender) throws IOException {
            String fileName = dataIn.readUTF();
            long fileSize = dataIn.readLong();
            
            // Open or get existing window
            if (!privateChatWindows.containsKey(sender)) {
                SwingUtilities.invokeLater(() -> openPrivateChat(sender));
            }
            
            PrivateChatWindow window = privateChatWindows.get(sender);
            if (window != null) {
                String fileInfo = "Receiving file: " + fileName + " (" + (fileSize / 1024) + " KB)";
                window.appendMessage(fileInfo);
                
                if (!window.isFocused()) {
                    showNotification("Private File from " + sender, fileInfo);
                }
            }
            
            // Save file dialog
            JFileChooser saveChooser = new JFileChooser();
            File saveDir = new File("Files");
            if (!saveDir.exists()) {
                saveDir.mkdir(); 
            }
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
                if (window != null) {
                    window.appendMessage("Saving to: " + saveFile[0].getAbsolutePath() + "...");
                }
            } else {
                if (window != null) {
                    window.appendMessage("File save cancelled. Discarding data...");
                }
            }
            
            byte[] buffer = new byte[4096];
            int read;
            long remaining = fileSize;
            while (remaining > 0 && (read = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                if (fos != null) { 
                    fos.write(buffer, 0, read);
                }
                remaining -= read;
            }

            if (fos != null) {
                fos.close();
                if (window != null) {
                    window.appendMessage("File " + saveFile[0].getName() + " saved successfully.");
                }
            } else {
                if (window != null) {
                    window.appendMessage("File " + fileName + " discarded.");
                }
            }
        }
        
        private void handleIncomingFile() throws IOException {
            String fileName = dataIn.readUTF();
            long fileSize = dataIn.readLong();
            
            String time = LocalTime.now().format(timeFormatter);
            String fileInfo = "Menerima file: " + fileName + " (" + (fileSize / 1024) + " KB).\n";
            appendMessage("[" + time + "] " + fileInfo);
            
            if (!frame.isFocused()) {
                showNotification("File Baru", fileInfo);
            }
            
            JFileChooser saveChooser = new JFileChooser();
            File saveDir = new File("Files");
            if (!saveDir.exists()) {
                saveDir.mkdir(); 
            }
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
                appendMessage("Menyimpan ke: " + saveFile[0].getAbsolutePath() + "...\n");
            } else {
                appendMessage("Penyimpanan file dibatalkan. Mengabaikan data...\n");
            }
            
            byte[] buffer = new byte[4096];
            int read;
            long remaining = fileSize;
            while (remaining > 0 && (read = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                if (fos != null) { 
                    fos.write(buffer, 0, read);
                }
                remaining -= read;
            }

            if (fos != null) {
                fos.close();
                appendMessage("File " + saveFile[0].getName() + " berhasil disimpan.\n");
            } else {
                appendMessage("File " + fileName + " selesai diabaikan.\n");
            }
        }
    }
    
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            incomingMessages.append(message);
            incomingMessages.setCaretPosition(incomingMessages.getDocument().getLength());
        });
    }
}