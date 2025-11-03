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
    
    // 1. Mengganti stream lama dengan Data Stream
    // private BufferedReader reader;
    // private PrintWriter writer;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    
    private String username;
    private JFrame frame; // 2. Jadikan frame global agar bisa diakses JFileChooser
    
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

        // Inisialisasi frame di sini
        frame = new JFrame("Simple LAN Messenger - (" + username + ")");
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
        
        // 3. Tambahkan tombol "Send File"
        JButton sendFileButton = new JButton("Send File");
        sendFileButton.addActionListener(new SendFileButtonListener());

        mainPanel.add(qScroller);
        mainPanel.add(outgoingMessage);
        mainPanel.add(sendButton);
        mainPanel.add(sendFileButton); // 4. Tambahkan tombol ke panel

        setupNetworking();

        Thread readerThread = new Thread(new IncomingReader());
        readerThread.start();

        frame.getContentPane().add(BorderLayout.CENTER, mainPanel);
        frame.setSize(500, 350); // Perlebar frame sedikit untuk tombol baru
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void setupNetworking() {
        try {
            // 5. Pastikan IP server sudah benar
            Socket sock = new Socket("192.168.1.15", 5000); 
            
            // 6. Ganti stream lama dengan Data Stream
            // InputStreamReader streamReader = new InputStreamReader(sock.getInputStream());
            // reader = new BufferedReader(streamReader);
            // writer = new PrintWriter(sock.getOutputStream());
            dataIn = new DataInputStream(sock.getInputStream());
            dataOut = new DataOutputStream(sock.getOutputStream());

            System.out.println("Koneksi berhasil dibuat.");
            
            // 7. Kirim pesan "bergabung" menggunakan protokol baru
            // writer.println("--- " + username + " telah bergabung ---");
            // writer.flush();
            dataOut.writeInt(1); // Tipe 1: Teks
            dataOut.writeUTF("--- " + username + " telah bergabung ---");
            dataOut.flush();

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Gagal terhubung ke server. Pastikan IP dan Port sudah benar.", "Koneksi Gagal", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // 8. --- Tombol Kirim Teks --- (Dimodifikasi)
    public class SendButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            if (outgoingMessage.getText().trim().isEmpty()) {
                return;
            }
            
            try {
                // 9. Kirim teks menggunakan protokol baru
                // String messageToSend = username + ": " + outgoingMessage.getText();
                // writer.println(messageToSend);
                // writer.flush();
                dataOut.writeInt(1); // Tipe 1: Teks
                dataOut.writeUTF(username + ": " + outgoingMessage.getText());
                dataOut.flush();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
            outgoingMessage.setText("");
            outgoingMessage.requestFocus();
        }
    }

    // 10. --- Tombol Kirim File --- (Baru)
    public class SendFileButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(frame); // Gunakan 'frame' global

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    // Kirim Tipe 2: File
                    dataOut.writeInt(2);
                    dataOut.writeUTF(selectedFile.getName());
                    dataOut.writeLong(selectedFile.length());

                    // Baca file dan kirim datanya
                    FileInputStream fis = new FileInputStream(selectedFile);
                    byte[] buffer = new byte[4096]; // Buffer 4KB
                    int read;

                    while ((read = fis.read(buffer)) > 0) {
                        dataOut.write(buffer, 0, read);
                    }
                    
                    fis.close();
                    dataOut.flush();

                    // Tampilkan notifikasi di chat lokal
                    incomingMessages.append("Anda mengirim file: " + selectedFile.getName() + "\n");
                    incomingMessages.setCaretPosition(incomingMessages.getDocument().getLength());

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    // 11. --- Penerima Pesan --- (Dimodifikasi total)
    public class IncomingReader implements Runnable {
        public void run() {
            try {
                // Ganti loop lama dengan loop baru yang membaca protokol
                while (true) {
                    // Pertama, baca tipe pesan
                    int messageType = dataIn.readInt();

                    if (messageType == 1) { // TIPE 1: Menerima Pesan Teks
                        String message = dataIn.readUTF();
                        System.out.println("Membaca Teks: " + message);
                        
                        String time = LocalTime.now().format(timeFormatter);
                        incomingMessages.append("[" + time + "] " + message + "\n");
                    
                    } else if (messageType == 2) { // TIPE 2: Menerima File
                        String fileName = dataIn.readUTF();
                        long fileSize = dataIn.readLong();
                        
                        String time = LocalTime.now().format(timeFormatter);
                        incomingMessages.append("[" + time + "] Menerima file: " + fileName 
                                                + " (" + (fileSize / 1024) + " KB). Menyimpan...\n");
                        
                        // TODO: Idealnya, tanya pengguna mau simpan di mana pakai JFileChooser.showSaveDialog()
                        // Untuk kesederhanaan, kita simpan di folder proyek
                        FileOutputStream fos = new FileOutputStream(fileName);
                        
                        byte[] buffer = new byte[4096];
                        int read;
                        long remaining = fileSize;

                        // Baca dari socket sampai ukuran file terpenuhi
                        while (remaining > 0 && (read = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                            fos.write(buffer, 0, read);
                            remaining -= read;
                        }

                        fos.close();
                        incomingMessages.append("File " + fileName + " berhasil disimpan.\n");
                    }
                    
                    // Auto-scroll ke bawah
                    incomingMessages.setCaretPosition(incomingMessages.getDocument().getLength());
                }
            } catch (EOFException | SocketException e) {
                 incomingMessages.append("[ERROR] Koneksi ke server terputus.\n");
            } catch (Exception ex) {
                ex.printStackTrace();
                incomingMessages.append("[ERROR] Gagal membaca data dari server.\n");
            }
        }
    }

    public static void main(String[] args) {
        new ChatClient().go();
    }
}