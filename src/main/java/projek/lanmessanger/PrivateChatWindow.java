package projek.lanmessanger;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class PrivateChatWindow extends JFrame {
    private JTextPane chatPane;
    private StyledDocument chatDocument;
    
    private JTextField messageField;
    private String targetUsername;
    private ChatClient parentClient;
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private int unreadCount = 0;
    private String originalTitle;

    // Styles
    private SimpleAttributeSet styleSelf;
    private SimpleAttributeSet styleTarget;
    private SimpleAttributeSet styleSystem;
    private SimpleAttributeSet styleMessage;

    public PrivateChatWindow(String targetUsername, ChatClient parentClient) {
        this.targetUsername = targetUsername;
        this.parentClient = parentClient;
        this.originalTitle = "Private Chat - " + targetUsername;
        
        initUI();
    }

    private void initUI() {
        setTitle(originalTitle);
        setSize(450, 550); 
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatDocument = chatPane.getStyledDocument();
        
        styleSelf = new SimpleAttributeSet();
        StyleConstants.setBold(styleSelf, true);
        StyleConstants.setForeground(styleSelf, new Color(0, 102, 204));

        styleTarget = new SimpleAttributeSet();
        StyleConstants.setBold(styleTarget, true);
        StyleConstants.setForeground(styleTarget, new Color(204, 0, 102)); 

        styleMessage = new SimpleAttributeSet();
        StyleConstants.setForeground(styleMessage, new Color(50, 50, 50));

        styleSystem = new SimpleAttributeSet();
        StyleConstants.setItalic(styleSystem, true);
        StyleConstants.setForeground(styleSystem, Color.GRAY);

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        messageField = new JTextField();
        messageField.addActionListener(new SendMessageListener());

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        
        JButton sendButton = new JButton("Kirim \u27A4"); 
        sendButton.addActionListener(new SendMessageListener());
        
        JButton sendFileButton = new JButton("File \uD83D\uDCCE");
        sendFileButton.addActionListener(new SendFileListener());

        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);

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

    public void appendMessage(String rawMessage) {
        SwingUtilities.invokeLater(() -> {
            try {
                String time = LocalTime.now().format(timeFormatter);
                chatDocument.insertString(chatDocument.getLength(), "[" + time + "] ", styleSystem);

                String senderName = null;
                String content = rawMessage;
                SimpleAttributeSet nameStyle = styleSystem;
                
                if (rawMessage.startsWith("You:")) {
                    senderName = "You:";
                    content = rawMessage.substring(4); 
                    nameStyle = styleSelf;
                } else if (rawMessage.startsWith(targetUsername + ":")) {
                    senderName = targetUsername + ":";
                    content = rawMessage.substring(targetUsername.length() + 1);
                    nameStyle = styleTarget;
                } else {
                    nameStyle = styleSystem;
                }

                if (senderName != null && nameStyle != styleSystem) {
                    chatDocument.insertString(chatDocument.getLength(), senderName, nameStyle);
                    chatDocument.insertString(chatDocument.getLength(), content + "\n", styleMessage);
                } else {
                    chatDocument.insertString(chatDocument.getLength(), rawMessage + "\n", styleSystem);
                }

                chatPane.setCaretPosition(chatDocument.getLength());

                if (!isFocused()) {
                    unreadCount++;
                    updateTitle();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateTitle() {
        if (unreadCount > 0) {
            setTitle(originalTitle + " (" + unreadCount + " pesan baru)");
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

    private class SendMessageListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String message = messageField.getText().trim();
            if (message.isEmpty()) return;

            parentClient.sendPrivateMessage(targetUsername, message);
            appendMessage("You: " + message);
            
            messageField.setText("");
            messageField.requestFocus();
        }
    }

    private class SendFileListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(PrivateChatWindow.this);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                appendMessage("Mengirim file: " + selectedFile.getName() + "...");
                new Thread(() -> {
                    parentClient.sendPrivateFile(targetUsername, selectedFile);
                }).start();
            }
        }
    }
}