package projek.lanmessanger;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class NetworkClient {
    private Socket socket;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private String username;
    private ClientListener listener;
    private boolean isRunning = true;

    public NetworkClient(String serverIP, int serverPort, String username, ClientListener listener) throws IOException {
        this.username = username;
        this.listener = listener;
        
        // Setup Koneksi
        this.socket = new Socket(serverIP, serverPort);
        this.dataIn = new DataInputStream(socket.getInputStream());
        this.dataOut = new DataOutputStream(socket.getOutputStream());
        
        // Handshake awal (kirim username)
        dataOut.writeUTF(username);
        dataOut.flush();

        // Jalankan thread pembaca pesan
        new Thread(new IncomingReader()).start();
    }

    public String getUsername() {
        return username;
    }

    public void sendBroadcastMessage(String message) {
        try {
            // Enkripsi pesan
            String encrypted = CryptoUtil.encrypt(message);
            dataOut.writeInt(1);
            dataOut.writeUTF(encrypted);
            dataOut.flush();
        } catch (IOException e) {
            listener.onError("Gagal mengirim pesan: " + e.getMessage());
        }
    }

    public void sendPrivateMessage(String target, String message) {
        try {
            String encrypted = CryptoUtil.encrypt(message);
            dataOut.writeInt(3);
            dataOut.writeUTF(target);
            dataOut.writeUTF(encrypted);
            dataOut.flush();
        } catch (IOException e) {
            listener.onError("Gagal mengirim pesan private: " + e.getMessage());
        }
    }

    public void sendFile(File file, String targetUser) {
        // targetUser null = Broadcast, ada isinya = Private
        try {
            if (targetUser == null) {
                dataOut.writeInt(2); // Broadcast File
            } else {
                dataOut.writeInt(6); // Private File
                dataOut.writeUTF(targetUser);
            }

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
        } catch (IOException e) {
            listener.onError("Gagal mengirim file: " + e.getMessage());
        }
    }

    // Inner class untuk membaca data dari server terus menerus
    private class IncomingReader implements Runnable {
        @Override
        public void run() {
            try {
                while (isRunning) {
                    int messageType = dataIn.readInt();

                    if (messageType == 1) { // Text Message
                        String encryptedMsg = dataIn.readUTF();
                        String msg = CryptoUtil.decrypt(encryptedMsg);
                        if (msg == null) msg = encryptedMsg; // Fallback
                        
                        // Parse sender manual seperti logika lama
                        String sender = null;
                        String content = msg;
                        if (msg.contains(":")) {
                            int idx = msg.indexOf(":");
                            sender = msg.substring(0, idx);
                            content = msg.substring(idx + 1);
                        }
                        listener.onMessageReceived(sender, content, false);

                    } else if (messageType == 2) { // Broadcast File
                        handleIncomingFile(null);

                    } else if (messageType == 3) { // Private Message
                        String sender = dataIn.readUTF();
                        String encryptedMsg = dataIn.readUTF();
                        String msg = CryptoUtil.decrypt(encryptedMsg);
                        if (msg == null) msg = encryptedMsg;

                        listener.onMessageReceived(sender, msg, true);

                    } else if (messageType == 4) { // User List
                        int count = dataIn.readInt();
                        String[] users = new String[count];
                        for (int i = 0; i < count; i++) {
                            users[i] = dataIn.readUTF();
                        }
                        listener.onUserListUpdate(users);

                    } else if (messageType == 6) { // Private File
                        String sender = dataIn.readUTF();
                        handleIncomingFile(sender);

                    } else if (messageType == 99) { // Error dari server
                        String errorMsg = dataIn.readUTF();
                        listener.onError("Server Error: " + errorMsg);
                    }
                }
            } catch (EOFException | SocketException e) {
                listener.onError("Koneksi terputus.");
            } catch (Exception e) {
                e.printStackTrace();
                listener.onError("Error data: " + e.getMessage());
            }
        }

        private void handleIncomingFile(String sender) throws IOException {
            String fileName = dataIn.readUTF();
            long fileSize = dataIn.readLong();

            // Minta UI menentukan lokasi simpan
            File saveFile = listener.onFileReceiveRequest(fileName, fileSize, sender);

            OutputStream fos = null;
            if (saveFile != null) {
                fos = new FileOutputStream(saveFile);
            }

            // Baca stream file dari socket
            byte[] buffer = new byte[4096];
            int read;
            long remaining = fileSize;
            while (remaining > 0 && (read = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                if (fos != null) fos.write(buffer, 0, read);
                remaining -= read;
            }

            if (fos != null) {
                fos.close();
                listener.onFileSaved(saveFile);
            }
        }
    }
}