/*
 * File: messageHandler.java
 * Author: Ruoyu Lu
 * Student ID: 1466195
 * 
 * Description: 
 * This class is a message handler responsible for formatting and transmitting messages.
 * Main functionalities include:
 * 1. Formatting messages by adding timestamps, topic details, and content
 * 2. Sending messages through sockets
 * 3. Receiving messages through sockets
 * 
 * This class provides static methods that can be directly called by other components 
 * of the system (such as publisher and subscriber) to implement unified message 
 * handling logic.
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class messageHandler {
    // Define a date-time formatter for consistent timestamp formatting
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    // Method to format a message with timestamp, topic details, and content
    public static String formatMessage(String topicId, String topicName, String publisherName, String message) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(formatter);
        return String.format("%s %s:%s:%s %s", timestamp, topicId, topicName, publisherName, message);
    }

    // Method to send a message through a socket
    public static void sendMessage(Socket socket, String message) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(message);
    }
}
