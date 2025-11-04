package projek.lanmessanger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
// 1. Import yang diperlukan untuk 3 fitur baru
import java.awt.TrayIcon;
import java.awt.SystemTray;
import java.awt.Rectangle;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

public class ChatClient {
    private JTextArea incomingMessages;
    private JTextField outgoingMessage;
    
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    
    private String username;
    private JFrame frame; 
    
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // 2. Tambahkan variabel untuk TrayIcon
    private TrayIcon trayIcon;

    // --- Method main ---
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            System.out.println("Gagal memuat Look and Feel Nimbus, menggunakan default.");
        }
        
        SwingUtilities.invokeLater(() -> new ChatClient().go());
    }

    // --- Method Utama ---
    public void go() {
        promptForUsername();
        initUI();
        initSystemTray(); // 3. Panggil method untuk inisialisasi tray icon
        setupNetworking();

        Thread readerThread = new Thread(new IncomingReader());
        readerThread.start();

        frame.setVisible(true);
    }

    // --- Logika untuk meminta Username ---
    private void promptForUsername() {
        // (Tidak ada perubahan di sini... sama seperti sebelumnya)
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

    // --- Logika untuk membangun Tampilan/GUI ---
    private void initUI() {
        frame = new JFrame("Simple LAN Messenger - (" + username + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // 4. PERMINTAAN #2: Nonaktifkan Maximize dan Resize
        frame.setResizable(false);
        
        frame.getContentPane().setLayout(new BorderLayout(5, 5)); 
        ((JPanel) frame.getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Area Chat (Tengah) ---
        incomingMessages = new JTextArea(15, 30);
        incomingMessages.setLineWrap(true);
        incomingMessages.setWrapStyleWord(true);
        incomingMessages.setEditable(false);
        JScrollPane qScroller = new JScrollPane(incomingMessages);
        qScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        qScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        
        // --- Panel Input (Bawah) ---
        JPanel southPanel = new JPanel(new BorderLayout(5, 0)); 
        outgoingMessage = new JTextField(20);
        outgoingMessage.addActionListener(new SendButtonListener());

        // --- Panel Tombol (di dalam Panel Input) ---
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0)); 
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(new SendButtonListener());
        
        JButton sendFileButton = new JButton("Send File");
        sendFileButton.addActionListener(new SendFileButtonListener());
        
        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);

        southPanel.add(outgoingMessage, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.EAST);

        frame.getContentPane().add(qScroller, BorderLayout.CENTER);
        frame.getContentPane().add(southPanel, BorderLayout.SOUTH);

        frame.pack(); 
        frame.setMinimumSize(frame.getSize());
        
        // 5. PERMINTAAN #3: Posisi di Kanan Bawah
        // Ganti 'setLocationRelativeTo(null)' dengan logika ini:
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();
        Rectangle bounds = defaultScreen.getDefaultConfiguration().getBounds();
        
        int screenWidth = bounds.width;
        int screenHeight = bounds.height;
        int windowWidth = frame.getWidth();
        int windowHeight = frame.getHeight();
        
        // Kalkulasi posisi X dan Y. (40 adalah padding dari taskbar)
        int x = screenWidth - windowWidth - 10;
        int y = screenHeight - windowHeight - 40; 
        
        frame.setLocation(x, y);
    }

    // 6. METHOD BARU: Inisialisasi System Tray (Pop-up)
    private void initSystemTray() {
        // Cek apakah System Tray didukung oleh OS
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray tidak didukung");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            
            // PENTING: Pastikan Anda punya file "icon.png" di root proyek
            Image image = new ImageIcon("icon.png").getImage(); 
            
            trayIcon = new TrayIcon(image, "LAN Messenger");
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip("LAN Messenger (" + username + ")");
            
            tray.add(trayIcon);
            
            // Tambahkan aksi jika icon di-klik (misal: membawa window ke depan)
            trayIcon.addActionListener(e -> {
                frame.setVisible(true);
                frame.setState(Frame.NORMAL);
            });

        } catch (Exception e) {
            System.out.println("Gagal membuat TrayIcon: " + e.getMessage());
            trayIcon = null; // Set ke null jika gagal
        }
    }

    // 7. METHOD BARU: Helper untuk menampilkan notifikasi
    private void showNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }


    // --- Logika Koneksi Jaringan ---
    private void setupNetworking() {
        // (Tidak ada perubahan di sini... sama seperti sebelumnya)
        try {
            Socket sock = new Socket("192.168.18.62", 5000); // Ganti IP ini jika perlu
            dataIn = new DataInputStream(sock.getInputStream());
            dataOut = new DataOutputStream(sock.getOutputStream());
            System.out.println("Koneksi berhasil dibuat.");
            
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

    // --- Tombol Kirim Teks ---
    public class SendButtonListener implements ActionListener {
        // (Tidak ada perubahan di sini... sama seperti sebelumnya)
        public void actionPerformed(ActionEvent ev) {
            String message = outgoingMessage.getText().trim();
            if (message.isEmpty()) {
                return;
            }
            
            try {
                dataOut.writeInt(1); // Tipe 1: Teks
                dataOut.writeUTF(username + ": " + message);
                dataOut.flush();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            outgoingMessage.setText("");
            outgoingMessage.requestFocus();
        }
    }

    // --- Tombol Kirim File ---
    public class SendFileButtonListener implements ActionListener {
        // (Tidak ada perubahan di sini... sama seperti sebelumnya)
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

    // --- Penerima Pesan ---
    public class IncomingReader implements Runnable {
        public void run() {
            try {
                while (true) {
                    int messageType = dataIn.readInt();

                    if (messageType == 1) { // TIPE 1: Menerima Pesan Teks
                        String message = dataIn.readUTF();
                        String time = LocalTime.now().format(timeFormatter);
                        appendMessage("[" + time + "] " + message + "\n");
                        
                        // 8. PERMINTAAN #1: Tampilkan notifikasi
                        // Tampilkan hanya jika window tidak sedang aktif/fokus
                        if (!frame.isFocused()) {
                            showNotification("Pesan Baru", message);
                        }
                    
                    } else if (messageType == 2) { // TIPE 2: Menerima File
                        handleIncomingFile();
                    }
                }
            } catch (EOFException | SocketException e) {
                 appendMessage("[ERROR] Koneksi ke server terputus.\n");
            } catch (Exception ex) {
                ex.printStackTrace();
                appendMessage("[ERROR] Gagal membaca data dari server.\n");
            }
        }
        
        private void handleIncomingFile() throws IOException {
            String fileName = dataIn.readUTF();
            long fileSize = dataIn.readLong();
            
            String time = LocalTime.now().format(timeFormatter);
            String fileInfo = "Menerima file: " + fileName + " (" + (fileSize / 1024) + " KB).\n";
            appendMessage("[" + time + "] " + fileInfo);
            
            // 9. PERMINTAAN #1: Tampilkan notifikasi
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
    
    // Helper method (aman untuk thread)
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            incomingMessages.append(message);
            incomingMessages.setCaretPosition(incomingMessages.getDocument().getLength());
        });
    }
}