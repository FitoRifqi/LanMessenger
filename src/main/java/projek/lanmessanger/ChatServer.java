package projek.lanmessanger;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class ChatServer {
    // 1. Mengganti list untuk menyimpan DataOutputStream (bukan PrintWriter)
    private static ArrayList<DataOutputStream> clientOutputStreams = new ArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            System.out.println("Server siap menerima koneksi (dengan fitur file)...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                
                // 2. Membuat DataOutputStream untuk klien
                DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream());
                clientOutputStreams.add(dataOut);

                // 3. Membuat Thread ClientHandler baru, kini juga melewatkan 'dataOut'
                Thread t = new Thread(new ClientHandler(clientSocket, dataOut));
                t.start();
                System.out.println("Mendapat koneksi baru.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Class untuk menangani setiap klien
    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private DataInputStream dataIn;
        private DataOutputStream dataOut; // Menyimpan stream output klien ini

        public ClientHandler(Socket socket, DataOutputStream dataOut) {
            try {
                this.clientSocket = socket;
                this.dataOut = dataOut;
                // 4. Mengganti BufferedReader menjadi DataInputStream
                this.dataIn = new DataInputStream(clientSocket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                // 5. Mengubah total logika 'run' untuk menangani protokol baru
                while (true) {
                    // Pertama, baca tipe pesan (1 untuk Teks, 2 untuk File)
                    int messageType = dataIn.readInt();

                    if (messageType == 1) { // Jika Tipe 1: Teks
                        String message = dataIn.readUTF();
                        System.out.println("Menerima Teks: " + message);
                        broadcastText(message); // Panggil broadcast teks

                    } else if (messageType == 2) { // Jika Tipe 2: File
                        String fileName = dataIn.readUTF();
                        long fileSize = dataIn.readLong();
                        System.out.println("Menerima File: " + fileName + " (" + fileSize + " bytes)");
                        
                        // Panggil broadcast file, kirim 'this' agar server tahu siapa pengirimnya
                        // dan kirim 'dataIn' agar server bisa membaca data file-nya
                        broadcastFile(this, fileName, fileSize, dataIn);
                    }
                }
            } catch (EOFException | SocketException e) {
                System.out.println("Klien terputus.");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // 6. Saat klien putus, hapus dari list dan tutup socket
                clientOutputStreams.remove(dataOut);
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 7. Method broadcast baru untuk Teks (harus 'synchronized' demi keamanan thread)
    public static synchronized void broadcastText(String message) {
        for (DataOutputStream out : clientOutputStreams) {
            try {
                out.writeInt(1); // Kirim Tipe 1 (Teks)
                out.writeUTF(message);
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 8. Method broadcast baru untuk File (sangat penting 'synchronized')
    public static synchronized void broadcastFile(ClientHandler sender, String fileName, long fileSize, DataInputStream fileDataIn) {
        
        // Loop ini untuk mengirim "header" file ke semua klien (kecuali pengirim)
        for (DataOutputStream out : clientOutputStreams) {
            // Jangan kirim file kembali ke pengirimnya
            if (out == sender.dataOut) {
                continue;
            }

            try {
                out.writeInt(2); // Kirim Tipe 2 (File)
                out.writeUTF(fileName);
                out.writeLong(fileSize);
                // (flush belum perlu, akan di-flush saat data dikirim)
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Loop ini untuk me-relay (meneruskan) data file
        try {
            byte[] buffer = new byte[4096]; // Buffer 4KB
            int read;
            long remaining = fileSize;

            // Baca dari PENGIRIM (fileDataIn)
            while (remaining > 0 && (read = fileDataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                remaining -= read;
                // Tulis ke SEMUA PENERIMA
                for (DataOutputStream out : clientOutputStreams) {
                    if (out == sender.dataOut) {
                        continue; // Skip pengirim
                    }
                    out.write(buffer, 0, read); // Tulis data biner
                }
            }

            // Setelah selesai, flush semua stream penerima
            for (DataOutputStream out : clientOutputStreams) {
                 if (out == sender.dataOut) {
                    continue; 
                 }
                out.flush();
            }
            System.out.println("Broadcast file " + fileName + " selesai.");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}