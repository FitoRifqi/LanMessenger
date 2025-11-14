package projek.lanmessanger;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

public class PrivateChatWindow extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private String targetUsername;
    private ChatClient parentClient;
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private int unreadCount = 0;
    private String originalTitle;

    public PrivateChatWindow(String targetUsername, ChatClient parentClient) {
        this.targetUsername = targetUsername;
        this.parentClient = parentClient;
        this.originalTitle = "Private Chat with " + targetUsername;
        
        initUI();
    }

    private void initUI() {
        setTitle(originalTitle);
        setSize(400, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // Layout
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Chat Area
        chatArea = new JTextArea(20, 30);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        // Input Panel
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        messageField = new JTextField();
        messageField.addActionListener(new SendMessageListener());

        // Button Panel
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(new SendMessageListener());
        
        JButton sendFileButton = new JButton("Send File");
        sendFileButton.addActionListener(new SendFileListener());

        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Window listener untuk reset unread count
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                resetUnreadCount();
            }
            
            @Override
            public void windowClosing(WindowEvent e) {
                parentClient.removePrivateChatWindow(targetUsername);
            }
        });
    }

    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().format(timeFormatter);
            chatArea.append("[" + time + "] " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            
            // Tambah unread count jika window tidak fokus
            if (!isFocused()) {
                unreadCount++;
                updateTitle();
            }
        });
    }
    
    private void updateTitle() {
        if (unreadCount > 0) {
            setTitle(originalTitle + " (" + unreadCount + " new)");
        } else {
            setTitle(originalTitle);
        }
    }
    
    private void resetUnreadCount() {
        unreadCount = 0;
        updateTitle();
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    // Send Message Listener
    private class SendMessageListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String message = messageField.getText().trim();
            if (message.isEmpty()) {
                return;
            }

            // Kirim ke server melalui parent client
            parentClient.sendPrivateMessage(targetUsername, message);
            
            // Tampilkan di window sendiri
            appendMessage("You: " + message);
            
            messageField.setText("");
            messageField.requestFocus();
        }
    }

    // Send File Listener
    private class SendFileListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(PrivateChatWindow.this);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                appendMessage("Sending file: " + selectedFile.getName() + "...");
                
                // Kirim file private melalui parent client
                new Thread(() -> {
                    parentClient.sendPrivateFile(targetUsername, selectedFile);
                }).start();
            }
        }
    }
}