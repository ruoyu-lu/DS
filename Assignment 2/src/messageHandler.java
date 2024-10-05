/*
 * This class is the message handler class that will handle the messages
 */

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class messageHandler {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    public static String formatMessage(String topicId, String topicName, String message) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(formatter);
        return String.format("%s %s:%s: %s", timestamp, topicId, topicName, message);
    }

    public static void sendMessage(Socket socket, String message) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(message);
    }

    public static String receiveMessage(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        return in.readLine();
    }
}
