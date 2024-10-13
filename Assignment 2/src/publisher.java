/*
 * File: publisher.java
 * Author: Ruoyu Lu
 * Student ID: 1466195
 * 
 * Description: 
 * This class represents a publisher client that communicates with the Broker via sockets.
 * It provides methods for creating topics, publishing messages, deleting topics, 
 * and retrieving subscriber counts.
 * 
 * Main functionalities include:
 * 1. Connecting to a specified Broker
 * 2. Creating new topics
 * 3. Publishing messages to specific topics
 * 4. Viewing subscriber counts for specific topics
 * 5. Deleting topics
 * 6. Interacting with users through a console interface
 * 
 * This class also implements interaction with the Directory Service to obtain 
 * available Broker information and retries connections in case of failure.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class publisher {
    private String name;
    private Socket brokerSocket;
    private PrintWriter out;
    private BufferedReader in;
    private static final int MAX_MESSAGE_LENGTH = 100;
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 5000;

    // Constructor to initialize the publisher with a name and broker connection details
    public publisher(String name, String brokerAddress, int brokerPort) throws IOException {
        this.name = name;
        this.brokerSocket = new Socket(brokerAddress, brokerPort);
        this.out = new PrintWriter(brokerSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()));
        
        out.println("PUBLISHER");
        out.println(name);
    }

    // Method to create a new topic
    public void createTopic(String topicId, String topicName) throws IOException {
        out.println("CREATE_TOPIC");
        out.println(topicId);
        out.println(topicName);
        String response = in.readLine();
        System.out.println(response);
    }

    // Method to publish a message to a specific topic
    public void publishMessage(String topicId, String message) throws IOException {
        if (message.length() > MAX_MESSAGE_LENGTH) {
            System.out.println("Message is too long. The max length is " + MAX_MESSAGE_LENGTH + " characters.");
            return;
        }
        out.println("PUBLISH_MESSAGE");
        out.println(topicId);
        out.println(message);
        String response = in.readLine();
        if (response.startsWith("SUCCESS")) {
            System.out.println("Message published successfully");
        } else {
            System.out.println("Failed to publish message: " + response);
        }
    }

    // Method to show the subscriber count for a specific topic
    public void showSubscriberCount(String topicId) throws IOException {
        System.out.println("Showing subscriber count for topic " + topicId);
        out.println("SHOW_SUBSCRIBER_COUNT");
        out.println(topicId);
        
        String response = in.readLine();
        System.out.println("Received: " + response);
        while ((response = in.readLine()) != null) {
            
            if (response.equals("END")) {
                break;
            }
            if (response.startsWith("ERROR:")) {
                System.out.println(response);
                break;
            }
            System.out.println(response);
        }
    }

    // Method to delete a topic
    public void deleteTopic(String topicId) throws IOException {
        out.println("DELETE_TOPIC");
        out.println(topicId);
        String response = in.readLine();
        System.out.println(response);
    }

    // Method to close the connection with the broker
    public void close() throws IOException {
        brokerSocket.close();
    }

    // Method to get broker information from the Directory Service
    private static List<String[]> getBrokerInfoFromDirectoryService(String directoryServiceIp, int directoryServicePort) throws IOException {
        List<String[]> brokers = new ArrayList<>();
        try (Socket socket = new Socket(directoryServiceIp, directoryServicePort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("GET_BROKERS");
            String response;
            while ((response = in.readLine()) != null && !response.equals("END")) {
                String[] brokerInfo = response.split(":");
                if (brokerInfo.length == 2) {
                    brokers.add(new String[]{brokerInfo[0], brokerInfo[1]});
                }
            }
            if (brokers.isEmpty()) {
                throw new IOException("未能从 Directory Service 获取 broker 信息");
            }
            return brokers;
        }
    }

    // Main method to run the publisher
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("用法: java -jar publisher.jar username directoryServiceIp:directoryServicePort");
            return;
        }

        String username = args[0];
        String[] directoryServiceInfo = args[1].split(":");
        if (directoryServiceInfo.length != 2) {
            System.out.println("无效的 Directory Service 信息。请使用格式：IP:Port");
            return;
        }

        String directoryServiceIp = directoryServiceInfo[0];
        int directoryServicePort = Integer.parseInt(directoryServiceInfo[1]);

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                List<String[]> brokers = getBrokerInfoFromDirectoryService(directoryServiceIp, directoryServicePort);
                if (brokers.isEmpty()) {
                    throw new IOException("没有可用的 broker");
                }
                // Randomly select a broker to connect
                String[] selectedBroker = brokers.get(new Random().nextInt(brokers.size())); 
                String brokerIp = selectedBroker[0];
                int brokerPort = Integer.parseInt(selectedBroker[1]);

                publisher pub = new publisher(username, brokerIp, brokerPort);

                // Display the connected broker's port
                System.out.println("Connected to broker at " + brokerIp + ":" + brokerPort);
                pub.startConsole();
                break;
            } catch (IOException e) {
                System.out.println("连接到 broker 时出错: " + e.getMessage());
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    System.out.println(RETRY_DELAY_MS / 1000 + " 秒后重试...");
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.out.println("达到最大重试次数。退出。");
                }
            }
        }
    }

    // Method to start the console interface for user interaction
    private void startConsole() {
        Scanner scanner = new Scanner(System.in);
        try {
            while (true) {
                System.out.println("\nPlease select command: create, publish, show, delete.");
                System.out.println("1. create {topic_id} {topic_name} #create a new topic");
                System.out.println("2. publish {topic_id} {message} #publish a message to an existing topic");
                System.out.println("3. show {topic_id} #show subsriber count for current publisher");
                System.out.println("4. delete {topic_id} #delete a topic");
                
                String input = scanner.nextLine().trim();
                String[] parts = input.split("\\s+", 3);
                
                if (parts.length == 0) {
                    System.out.println("Invalid input. Please try again.");
                    continue;
                }

                String command = parts[0].toLowerCase();

                try {
                    switch (command) {
                        case "create":
                            if (parts.length != 3) {
                                System.out.println("Invalid format. Use: create {topic_id} {topic_name}");
                                break;
                            }
                            createTopic(parts[1], parts[2]);
                            break;
                        case "publish":
                            if (parts.length != 3) {
                                System.out.println("Invalid format. Use: publish {topic_id} {message}");
                                break;
                            }
                            publishMessage(parts[1], parts[2]);
                            break;
                        case "show":
                            if (parts.length != 2) {
                                System.out.println("Invalid format. Use: show {topic_id}");
                                break;
                            }
                            showSubscriberCount(parts[1]);
                            break;
                        case "delete":
                            if (parts.length != 2) {
                                System.out.println("Invalid format. Use: delete {topic_id}");
                                break;
                            }
                            deleteTopic(parts[1]);
                            break;
                        default:
                            System.out.println("Invalid command. Please try again.");
                    }
                } catch (IOException e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("An unexpected error occurred: " + e.getMessage());
        } finally {
            scanner.close();
            try {
                close();
            } catch (IOException e) {
                System.out.println("Error closing connection: " + e.getMessage());
            }
        }
    }
}