package projek.lanmessanger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

public class ChatServer {
    // HashMap untuk menyimpan username dan stream output
    private static HashMap<String, DataOutputStream> clients = new HashMap<>();
    private static HashMap<String, ClientHandler> clientHandlers = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            System.out.println("Server siap menerima koneksi...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                
                DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream());
                DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());
                
                // Baca username dari client
                String username = dataIn.readUTF();
                
                // Cek apakah username sudah digunakan
                if (clients.containsKey(username)) {
                    dataOut.writeInt(99); // Error code
                    dataOut.writeUTF("Username sudah digunakan!");
                    dataOut.flush();
                    clientSocket.close();
                    System.out.println("Koneksi ditolak: Username " + username + " sudah digunakan.");
                    continue;
                }
                
                // Simpan client
                clients.put(username, dataOut);
                
                ClientHandler handler = new ClientHandler(clientSocket, dataOut, dataIn, username);
                clientHandlers.put(username, handler);
                
                Thread t = new Thread(handler);
                t.start();
                
                System.out.println("User " + username + " terhubung. Total users: " + clients.size());
                
                // PENTING: Broadcast join message dulu
                broadcastText("--- " + username + " telah bergabung ---");
                
                // Kemudian broadcast user list ke semua client
                broadcastUserList();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcast daftar user online
    public static synchronized void broadcastUserList() {
        String[] usernames = clients.keySet().toArray(new String[0]);
        
        for (DataOutputStream out : clients.values()) {
            try {
                out.writeInt(4); // Type 4: User list
                out.writeInt(usernames.length);
                for (String user : usernames) {
                    out.writeUTF(user);
                }
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("User list broadcasted: " + String.join(", ", usernames));
    }

    // Broadcast pesan teks ke semua
    public static synchronized void broadcastText(String message) {
        for (DataOutputStream out : clients.values()) {
            try {
                out.writeInt(1); // Type 1: Broadcast text
                out.writeUTF(message);
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Broadcast file ke semua (kecuali pengirim)
    public static synchronized void broadcastFile(ClientHandler sender, String fileName, long fileSize, DataInputStream fileDataIn) {
        // Kirim header file
        for (Map.Entry<String, DataOutputStream> entry : clients.entrySet()) {
            if (entry.getKey().equals(sender.getUsername())) {
                continue; // Skip sender
            }

            try {
                DataOutputStream out = entry.getValue();
                out.writeInt(2); // Type 2: Broadcast file
                out.writeUTF(fileName);
                out.writeLong(fileSize);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Relay file data
        try {
            byte[] buffer = new byte[4096];
            int read;
            long remaining = fileSize;

            while (remaining > 0 && (read = fileDataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                remaining -= read;
                
                for (Map.Entry<String, DataOutputStream> entry : clients.entrySet()) {
                    if (entry.getKey().equals(sender.getUsername())) {
                        continue;
                    }
                    entry.getValue().write(buffer, 0, read);
                }
            }

            // Flush all streams
            for (Map.Entry<String, DataOutputStream> entry : clients.entrySet()) {
                if (entry.getKey().equals(sender.getUsername())) {
                    continue;
                }
                entry.getValue().flush();
            }
            
            System.out.println("Broadcast file " + fileName + " selesai.");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Kirim pesan private
    public static synchronized void sendPrivateMessage(String from, String to, String message) {
        DataOutputStream targetStream = clients.get(to);
        
        if (targetStream != null) {
            try {
                targetStream.writeInt(3); // Type 3: Private message
                targetStream.writeUTF(from);
                targetStream.writeUTF(message);
                targetStream.flush();
                System.out.println("Private message dari " + from + " ke " + to + ": " + message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Target offline, kirim error ke sender
            DataOutputStream senderStream = clients.get(from);
            if (senderStream != null) {
                try {
                    senderStream.writeInt(1);
                    senderStream.writeUTF("[SYSTEM] User " + to + " sedang offline.");
                    senderStream.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    // Kirim file private
    public static synchronized void sendPrivateFile(String from, String to, String fileName, long fileSize, DataInputStream fileDataIn) {
        DataOutputStream targetStream = clients.get(to);
        
        if (targetStream != null) {
            try {
                // Kirim header
                targetStream.writeInt(6); // Type 6: Private file
                targetStream.writeUTF(from);
                targetStream.writeUTF(fileName);
                targetStream.writeLong(fileSize);
                
                // Relay file data
                byte[] buffer = new byte[4096];
                int read;
                long remaining = fileSize;
                
                while (remaining > 0 && (read = fileDataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                    targetStream.write(buffer, 0, read);
                    remaining -= read;
                }
                
                targetStream.flush();
                System.out.println("Private file dari " + from + " ke " + to + ": " + fileName);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Target offline, buang data file
            try {
                byte[] buffer = new byte[4096];
                long remaining = fileSize;
                while (remaining > 0 && fileDataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining)) > 0) {
                    remaining -= fileDataIn.read(buffer);
                }
                
                // Kirim error ke sender
                DataOutputStream senderStream = clients.get(from);
                if (senderStream != null) {
                    senderStream.writeInt(1);
                    senderStream.writeUTF("[SYSTEM] User " + to + " sedang offline. File tidak terkirim.");
                    senderStream.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    // Remove client
    public static synchronized void removeClient(String username) {
        clients.remove(username);
        clientHandlers.remove(username);
        System.out.println("User " + username + " terputus. Total users: " + clients.size());
        
        // Broadcast leave message
        broadcastText("--- " + username + " telah keluar ---");
        
        // Update user list
        broadcastUserList();
    }

    // ClientHandler class
    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private DataInputStream dataIn;
        private DataOutputStream dataOut;
        private String username;

        public ClientHandler(Socket socket, DataOutputStream dataOut, DataInputStream dataIn, String username) {
            this.clientSocket = socket;
            this.dataOut = dataOut;
            this.dataIn = dataIn;
            this.username = username;
        }

        public String getUsername() {
            return username;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int messageType = dataIn.readInt();

                    if (messageType == 1) { // Broadcast text
                        String message = dataIn.readUTF();
                        System.out.println("Broadcast text: " + message);
                        broadcastText(message);

                    } else if (messageType == 2) { // Broadcast file
                        String fileName = dataIn.readUTF();
                        long fileSize = dataIn.readLong();
                        System.out.println("Broadcast file: " + fileName + " (" + fileSize + " bytes)");
                        broadcastFile(this, fileName, fileSize, dataIn);
                        
                    } else if (messageType == 3) { // Private message
                        String target = dataIn.readUTF();
                        String message = dataIn.readUTF();
                        sendPrivateMessage(username, target, message);
                        
                    } else if (messageType == 6) { // Private file
                        String target = dataIn.readUTF();
                        String fileName = dataIn.readUTF();
                        long fileSize = dataIn.readLong();
                        System.out.println("Private file dari " + username + " ke " + target + ": " + fileName);
                        sendPrivateFile(username, target, fileName, fileSize, dataIn);
                    }
                }
            } catch (EOFException | SocketException e) {
                System.out.println("Client " + username + " terputus.");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                removeClient(username);
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}