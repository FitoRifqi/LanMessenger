package projek.lanmessanger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatClient {
    private JTextArea incomingMessages;
    private JTextField outgoingMessage;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username; 
    
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public void go() {
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

        JFrame frame = new JFrame("Simple LAN Messenger - (" + username + ")");
        JPanel mainPanel = new JPanel();
        incomingMessages = new JTextArea(15, 30);
        incomingMessages.setLineWrap(true);
        incomingMessages.setWrapStyleWord(true);
        incomingMessages.setEditable(false);
        JScrollPane qScroller = new JScrollPane(incomingMessages);
        qScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        qScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        outgoingMessage = new JTextField(20);
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(new SendButtonListener());
        mainPanel.add(qScroller);
        mainPanel.add(outgoingMessage);
        mainPanel.add(sendButton);

        setupNetworking();

        Thread readerThread = new Thread(new IncomingReader());
        readerThread.start();

        frame.getContentPane().add(BorderLayout.CENTER, mainPanel);
        frame.setSize(400, 350);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void setupNetworking() {
        try {
            Socket sock = new Socket("192.168.1.189", 5000);
            InputStreamReader streamReader = new InputStreamReader(sock.getInputStream());
            reader = new BufferedReader(streamReader);
            writer = new PrintWriter(sock.getOutputStream());
            System.out.println("Koneksi berhasil dibuat.");
            
            writer.println("--- " + username + " telah bergabung ---");
            writer.flush();

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Gagal terhubung ke server. Pastikan IP dan Port sudah benar.", "Koneksi Gagal", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // --- Tombol Kirim ---
    public class SendButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            if (outgoingMessage.getText().trim().isEmpty()) {
                return;
            }
            
            try {
                String messageToSend = username + ": " + outgoingMessage.getText();
                
                writer.println(messageToSend);
                writer.flush();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
            outgoingMessage.setText("");
            outgoingMessage.requestFocus();
        }
    }

    public class IncomingReader implements Runnable {
        public void run() {
            String message;
            try {
                while ((message = reader.readLine()) != null) {
                    System.out.println("Membaca: " + message);
                    
                    String time = LocalTime.now().format(timeFormatter);
                    incomingMessages.append("[" + time + "] " + message + "\n");
                    
                    incomingMessages.setCaretPosition(incomingMessages.getDocument().getLength());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                incomingMessages.append("[ERROR] Koneksi ke server terputus.\n");
            }
        }
    }

    public static void main(String[] args) {
        new ChatClient().go();
    }
}