package projek.lanmessanger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ChatServer {

    private static final HashMap<String, DataOutputStream> clients = new HashMap<>();
    private static final HashMap<String, ClientHandler> clientHandlers = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5000)) {

            System.out.println("Server siap menerima koneksi...");

            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                String username = in.readUTF();

                // Cek duplikat user
                if (clients.containsKey(username)) {
                    out.writeInt(99);
                    out.writeUTF("Username sudah digunakan!");
                    out.flush();
                    socket.close();
                    continue;
                }

                clients.put(username, out);

                ClientHandler handler = new ClientHandler(socket, out, in, username);
                clientHandlers.put(username, handler);
                new Thread(handler).start();

                System.out.println("User " + username + " terhubung.");

                broadcastText("--- " + username + " telah bergabung ---");
                broadcastUserList();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Kirim list user online
    public static synchronized void broadcastUserList() {
        String[] users = clients.keySet().toArray(new String[0]);

        for (DataOutputStream out : clients.values()) {
            try {
                out.writeInt(4);
                out.writeInt(users.length);
                for (String u : users) out.writeUTF(u);
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Broadcast text
    public static synchronized void broadcastText(String msg) {
        for (DataOutputStream out : clients.values()) {
            try {
                out.writeInt(1);
                out.writeUTF(msg);
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Relay file ke semua user (kecuali pengirim)
    public static synchronized void broadcastFile(ClientHandler sender, String name, long size, DataInputStream in) {

        // Kirim header file
        for (Map.Entry<String, DataOutputStream> e : clients.entrySet()) {
            if (e.getKey().equals(sender.username)) continue;

            try {
                e.getValue().writeInt(2);
                e.getValue().writeUTF(name);
                e.getValue().writeLong(size);
                e.getValue().flush();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Kirim body file
        byte[] buffer = new byte[4096];
        long remaining = size;

        try {
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (read == -1) break;

                remaining -= read;

                // Kirim ke semua target
                for (Map.Entry<String, DataOutputStream> e : clients.entrySet()) {
                    if (e.getKey().equals(sender.username)) continue;

                    try {
                        e.getValue().write(buffer, 0, read);
                    } catch (IOException io) {
                        // Client bermasalah → hapus
                        removeClient(e.getKey());
                    }
                }
            }

            // flush
            for (Map.Entry<String, DataOutputStream> e : clients.entrySet()) {
                if (!e.getKey().equals(sender.username)) {
                    try { e.getValue().flush(); } 
                    catch (IOException io) {}
                }
            }

            System.out.println("Broadcast file selesai: " + name);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Kirim private message
    public static synchronized void sendPrivateMessage(String from, String to, String msg) {
        DataOutputStream out = clients.get(to);

        try {
            if (out != null) {
                out.writeInt(3);
                out.writeUTF(from);
                out.writeUTF(msg);
                out.flush();
            } else {
                // Target offline
                DataOutputStream fromOut = clients.get(from);
                if (fromOut != null) {
                    fromOut.writeInt(1);
                    fromOut.writeUTF("[SYSTEM] User " + to + " offline");
                    fromOut.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Private file
    public static synchronized void sendPrivateFile(String from, String to, String name, long size, DataInputStream in) {

        DataOutputStream out = clients.get(to);

        if (out != null) {
            try {
                // header
                out.writeInt(6);
                out.writeUTF(from);
                out.writeUTF(name);
                out.writeLong(size);

                // body
                byte[] buffer = new byte[4096];
                long remain = size;

                while (remain > 0) {
                    int read = in.read(buffer, 0, (int)Math.min(buffer.length, remain));
                    if (read == -1) break;

                    out.write(buffer, 0, read);
                    remain -= read;
                }

                out.flush();

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            // Target offline → buang file
            try {
                byte[] buffer = new byte[4096];
                long remain = size;

                while (remain > 0) {
                    int read = in.read(buffer, 0, (int)Math.min(buffer.length, remain));
                    if (read == -1) break;
                    remain -= read;
                }

                // notify sender
                DataOutputStream fromOut = clients.get(from);
                if (fromOut != null) {
                    fromOut.writeInt(1);
                    fromOut.writeUTF("[SYSTEM] User " + to + " offline, file dibatalkan.");
                    fromOut.flush();
                }

            } catch (Exception e) {}
        }
    }

    // Remove client
    public static synchronized void removeClient(String user) {
        clients.remove(user);
        clientHandlers.remove(user);
        System.out.println("User keluar: " + user);

        broadcastText("--- " + user + " telah keluar ---");
        broadcastUserList();
    }

    // Handler
    static class ClientHandler implements Runnable {
        Socket socket;
        DataInputStream in;
        DataOutputStream out;
        String username;

        public ClientHandler(Socket s, DataOutputStream out, DataInputStream in, String name) {
            this.socket = s;
            this.out = out;
            this.in = in;
            this.username = name;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int type = in.readInt();

                    if (type == 1) {
                        broadcastText(in.readUTF());

                    } else if (type == 2) {
                        String fileName = in.readUTF();
                        long fileSize = in.readLong();
                        broadcastFile(this, fileName, fileSize, in);

                    } else if (type == 3) {
                        sendPrivateMessage(username, in.readUTF(), in.readUTF());

                    } else if (type == 6) {
                        String target = in.readUTF();
                        String fileName = in.readUTF();
                        long fileSize = in.readLong();
                        sendPrivateFile(username, target, fileName, fileSize, in);
                    }
                }

            } catch (EOFException | SocketException e) {
                System.out.println("Client " + username + " terputus.");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                removeClient(username);
                try { socket.close(); } catch (Exception e) {}
            }
        }
    }
}
