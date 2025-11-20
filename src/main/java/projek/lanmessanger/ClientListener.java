package projek.lanmessanger;

import java.io.File;

public interface ClientListener {
    void onMessageReceived(String sender, String message, boolean isPrivate);

    void onSystemMessage(String message);

    void onError(String message);

    void onUserListUpdate(String[] users);

    File onFileReceiveRequest(String fileName, long fileSize, String sender);


    void onFileSaved(File file);
}