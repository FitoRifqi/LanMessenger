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
    
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    
    private String username;
    private JFrame frame; 
    
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // --- Method main ---
    public static void main(String[] args) {
        // 1. Atur Look and Feel "Nimbus" agar tampilan lebih modern
        // Ini harus dijalankan sebelum komponen Swing dibuat
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            System.out.println("Gagal memuat Look and Feel Nimbus, menggunakan default.");
        }
        
        // Menjalankan GUI di Event Dispatch Thread (EDT) untuk keamanan thread
        SwingUtilities.invokeLater(() -> new ChatClient().go());
    }

    // --- Method Utama ---
    public void go() {
        // Pisahkan logika untuk merapikan
        promptForUsername();
        initUI();
        setupNetworking();

        // Mulai thread untuk mendengarkan pesan masuk
        Thread readerThread = new Thread(new IncomingReader());
        readerThread.start();

        // Tampilkan frame di akhir
        frame.setVisible(true);
    }

    // --- Logika untuk meminta Username ---
    private void promptForUsername() {
        while (username == null || username.trim().isEmpty()) {
            username = JOptionPane.showInputDialog(
                    null, 
                    "Masukkan nama Anda:", 
                    "Login LAN Messenger", 
                    JOptionPane.PLAIN_MESSAGE
            );

            if (username == null) { // User menekan 'Cancel'
                System.exit(0); 
            }
            if (username.trim().isEmpty()) { // User tidak mengisi nama
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
        
        // 2. Gunakan BorderLayout untuk layout yang lebih rapi
        frame.getContentPane().setLayout(new BorderLayout(5, 5)); // (HGap, VGap)
        
        // Beri sedikit padding/border di sekitar frame
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
        // Panel ini akan menampung kotak teks dan panel tombol
        JPanel southPanel = new JPanel(new BorderLayout(5, 0)); // (HGap, VGap)

        outgoingMessage = new JTextField(20);
        
        // 3. Tambahkan ActionListener ke text field agar bisa kirim pakai "Enter"
        outgoingMessage.addActionListener(new SendButtonListener());

        // --- Panel Tombol (di dalam Panel Input) ---
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0)); // (rows, cols, HGap, VGap)
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(new SendButtonListener());
        
        JButton sendFileButton = new JButton("Send File");
        sendFileButton.addActionListener(new SendFileButtonListener());
        
        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);

        // Susun panel input
        southPanel.add(outgoingMessage, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.EAST);

        // Tambahkan semua komponen ke frame utama
        frame.getContentPane().add(qScroller, BorderLayout.CENTER);
        frame.getContentPane().add(southPanel, BorderLayout.SOUTH);

        // 4. Gunakan pack() agar ukuran window pas, dan set lokasi di tengah
        frame.pack(); // Mengganti setSize()
        frame.setMinimumSize(frame.getSize()); // Mencegah window dikecilkan terlalu kecil
        frame.setLocationRelativeTo(null); // Membuka window di tengah layar
    }

    // --- Logika Koneksi Jaringan ---
    private void setupNetworking() {
        try {
            // Pastikan IP server sudah benar
            Socket sock = new Socket("192.168.18.62", 5000); // Ganti IP ini jika perlu
            
            dataIn = new DataInputStream(sock.getInputStream());
            dataOut = new DataOutputStream(sock.getOutputStream());

            System.out.println("Koneksi berhasil dibuat.");
            
            // Kirim pesan "bergabung" menggunakan protokol baru
            dataOut.writeInt(1); // Tipe 1: Teks
            dataOut.writeUTF("--- " + username + " telah bergabung ---");
            dataOut.flush();

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Gagal terhubung ke server. Pastikan IP dan Port sudah benar.", "Koneksi Gagal", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // --- Tombol Kirim Teks ---
    public class SendButtonListener implements ActionListener {
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
        public void actionPerformed(ActionEvent ev) {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(frame);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                
                // Kirim file dalam thread terpisah agar GUI tidak "macet"
                new Thread(() -> sendFile(selectedFile)).start();
            }
        }
        
        private void sendFile(File selectedFile) {
            try {
                // Tampilkan notifikasi di chat lokal
                appendMessage("Mengirim file: " + selectedFile.getName() + "...\n");
                
                // Kirim Tipe 2: File
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
        
        // Memisahkan logika penerimaan file agar lebih rapi
        private void handleIncomingFile() throws IOException {
            String fileName = dataIn.readUTF();
            long fileSize = dataIn.readLong();
            
            String time = LocalTime.now().format(timeFormatter);
            appendMessage("[" + time + "] Menerima file: " + fileName 
                            + " (" + (fileSize / 1024) + " KB).\n");
            
            // 5. Tanya user mau simpan di mana
            // Ini harus dijalankan di thread Swing (EDT)
            JFileChooser saveChooser = new JFileChooser();
            saveChooser.setSelectedFile(new File(fileName)); // Menyarankan nama file asli
            
            // Menggunakan SwingUtilities.invokeAndWait agar kita bisa dapat hasilnya
            // sebelum melanjutkan membaca stream.
            final File[] saveFile = new File[1]; // Array 1-elemen untuk menampung hasil
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
                saveFile[0] = null; // Anggap dibatalkan jika ada error
            }

            OutputStream fos = null;
            if (saveFile[0] != null) {
                fos = new FileOutputStream(saveFile[0]);
                appendMessage("Menyimpan ke: " + saveFile[0].getAbsolutePath() + "...\n");
            } else {
                appendMessage("Penyimpanan file dibatalkan. Mengabaikan data...\n");
            }
            
            // Logika membaca file dari stream
            byte[] buffer = new byte[4096];
            int read;
            long remaining = fileSize;
            while (remaining > 0 && (read = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                if (fos != null) { // Hanya tulis ke file jika user menekan "Save"
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
    
    /**
     * Helper method untuk menambahkan teks ke JTextArea secara thread-safe.
     * Semua pembaruan GUI Swing harus dipanggil dari Event Dispatch Thread (EDT).
     * @param message Pesan yang akan ditambahkan ke area chat.
     */
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            incomingMessages.append(message);
            incomingMessages.setCaretPosition(incomingMessages.getDocument().getLength());
        });
    }
}