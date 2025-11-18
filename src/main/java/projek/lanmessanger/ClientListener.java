package projek.lanmessanger;

import java.io.File;

public interface ClientListener {
    // Dipanggil saat ada pesan text (broadcast)
    void onMessageReceived(String sender, String message, boolean isPrivate);

    // Dipanggil saat ada notifikasi system
    void onSystemMessage(String message);

    // Dipanggil saat terjadi error
    void onError(String message);

    // Dipanggil saat list user online diperbarui
    void onUserListUpdate(String[] users);

    // Dipanggil saat server menawarkan file (mengembalikan File tujuan simpan, atau null jika batal)
    File onFileReceiveRequest(String fileName, long fileSize, String sender);

    // Dipanggil saat file berhasil disimpan
    void onFileSaved(File file);
}