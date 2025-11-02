package projek.lanmessanger;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class ChatServer {
    private static ArrayList<PrintWriter> clientOutputStreams = new ArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            System.out.println("Server siap menerima koneksi...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream());
                clientOutputStreams.add(writer);

                Thread t = new Thread(new ClientHandler(clientSocket));
                t.start();
                System.out.println("Mendapat koneksi baru.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private BufferedReader reader;

        public ClientHandler(Socket socket) {
            try {
                clientSocket = socket;
                InputStreamReader isReader = new InputStreamReader(clientSocket.getInputStream());
                reader = new BufferedReader(isReader);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            String message;
            try {
                while ((message = reader.readLine()) != null) {
                    System.out.println("Menerima: " + message);
                    broadcastMessage(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void broadcastMessage(String message) {
        for (PrintWriter writer : clientOutputStreams) {
            try {
                writer.println(message);
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}