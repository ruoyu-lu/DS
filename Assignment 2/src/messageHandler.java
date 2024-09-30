/*
 * This class is the message handler class that will handle the messages
 */

import java.io.*;
import java.net.*;

public class messageHandler {
    public static String formatMessage(String topicId, String topicName, String publisherName, String message) {
        return String.format("[Topic: %s (%s)] %s: %s", topicName, topicId, publisherName, message);
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
