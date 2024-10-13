/*
 * File: subscriber.java
 * Author: Ruoyu Lu
 * Student ID: 1466195
 * 
 * Description: 
 * This class represents a subscriber client in the distributed publish-subscribe system.
 * It communicates with a broker to subscribe to topics and receive messages.
 * 
 * Main functionalities include:
 * 1. Connecting to a broker (obtained from the Directory Service)
 * 2. Listing available topics
 * 3. Subscribing to topics
 * 4. Unsubscribing from topics
 * 5. Receiving and displaying messages from subscribed topics
 * 6. Maintaining a list of current subscriptions
 * 
 * The subscriber provides a console-based interface for users to interact with the system,
 * allowing them to manage their subscriptions and view incoming messages in real-time.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class subscriber {
    private String name;
    private Socket brokerSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Set<String> subscriptions;
    private boolean isRunning;
    private BlockingQueue<String> messageQueue;
    private Map<String, String> subscriptionDetails; 
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 5000;

    // Constructor: Initializes the subscriber and establishes connection with the broker
    public subscriber(String name, String brokerAddress, int brokerPort) throws IOException {
        this.name = name;
        this.brokerSocket = new Socket(brokerAddress, brokerPort);
        this.out = new PrintWriter(brokerSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()));
        this.subscriptions = new HashSet<>();
        this.isRunning = true;
        this.messageQueue = new LinkedBlockingQueue<>();
        this.subscriptionDetails = new HashMap<>();
        startListening();
        out.println("SUBSCRIBER");
        out.println(name);
    }

    // Retrieves broker information from the Directory Service
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
                throw new IOException("Failed to get broker information from Directory Service");
            }
            return brokers;
        }
    }

    // Lists all available topics
    public void listAllTopics() throws IOException {
        out.println("LIST_TOPICS");
        System.out.println("Available Topics:");
        
        String response;
        while (!(response = waitForResponse()).equals("END")) {
            String[] parts = response.split("\\|");
            if (parts.length == 3) {
                System.out.printf("%s %s %s%n", parts[0], parts[1], parts[2]);
            }
        }
    }

    // Subscribes to a specific topic
    public void subscribeTopic(String topicId) throws IOException {
        out.println("SUBSCRIBE_TOPIC");
        out.println(topicId);
        String response = waitForResponse();
        if (response.startsWith("SUCCESS")) {
            String[] parts = response.split("\\|");
            if (parts.length == 4) {
                subscriptions.add(topicId);
                subscriptionDetails.put(topicId, parts[1] + "|" + parts[2] + "|" + parts[3]);
                System.out.println("Successfully subscribed to topic: " + parts[1]);
            } else {
                System.out.println("Invalid response format from broker");
            }
        } else {
            System.out.println("Failed to subscribe to topic: " + topicId);
        }
    }

    // Displays current subscriptions
    public void showCurrentSubscriptions() {
        System.out.println("Current subscriptions:");
        if (subscriptionDetails.isEmpty()) {
            System.out.println("No active subscriptions.");
        } else {
            for (Map.Entry<String, String> entry : subscriptionDetails.entrySet()) {
                System.out.println(entry.getValue());
            }
        }
    }

    // Unsubscribes from a specific topic
    public void unsubscribeTopic(String topicId) throws IOException {
        System.out.println("Attempting to unsubscribe from topic: " + topicId);
        out.println("UNSUBSCRIBE_TOPIC");
        out.println(topicId);
        String response = waitForResponse();
        System.out.println("Received response: " + response);
        if (response.equals("SUCCESS")) {
            subscriptions.remove(topicId);
            subscriptionDetails.remove(topicId);
            System.out.println("Successfully unsubscribed from topic: " + topicId);
        } else {
            System.out.println("Failed to unsubscribe from topic: " + topicId + " - " + response);
        }
    }

    // Starts a thread to listen for incoming messages from the broker
    private void startListening() {
        new Thread(() -> {
            try {
                String message;
                while (isRunning && (message = in.readLine()) != null) {
                    if (message.startsWith("SUCCESS") || message.startsWith("FAILED|") || message.startsWith("ERROR:") || message.equals("END")) {
                        // Command responses and "END" are added to the messageQueue
                        messageQueue.put(message);
                    } else if (message.startsWith("TOPIC_DELETED|")) {
                        // Handle topic deletion messages
                        handleTopicDeleted(message);
                    } else {
                        // All other messages are considered published messages
                        System.out.println(message);
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Handles the deletion of a topic
    private void handleTopicDeleted(String message) {
        String[] parts = message.split("\\|");
        if (parts.length == 3) {
            String topicId = parts[1];
            String topicName = parts[2];
            subscriptions.remove(topicId);
            subscriptionDetails.remove(topicId);
            System.out.println("Topic deleted: " + topicName + " (ID: " + topicId + ")");
        }
    }

    // Waits for a response from the broker with a timeout
    private String waitForResponse() throws IOException {
        try {
            String message = messageQueue.poll(5, TimeUnit.SECONDS);
            if (message == null) {
                throw new IOException("Timeout waiting for response");
            }
            return message;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for response", e);
        }
    }

    // Main method to run the subscriber client
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -jar subscriber.jar username directoryServiceIp:directoryServicePort");
            return;
        }

        String username = args[0];
        String[] directoryServiceInfo = args[1].split(":");
        if (directoryServiceInfo.length != 2) {
            System.out.println("Invalid Directory Service information. Please use format: IP:Port");
            return;
        }

        String directoryServiceIp = directoryServiceInfo[0];
        int directoryServicePort = Integer.parseInt(directoryServiceInfo[1]);

        // Attempt to connect to a broker with retries
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                List<String[]> brokers = getBrokerInfoFromDirectoryService(directoryServiceIp, directoryServicePort);
                if (brokers.isEmpty()) {
                    throw new IOException("No available brokers");
                }
                // Randomly select a broker to connect
                String[] selectedBroker = brokers.get(new Random().nextInt(brokers.size())); 
                String brokerIp = selectedBroker[0];
                int brokerPort = Integer.parseInt(selectedBroker[1]);

                subscriber sub = new subscriber(username, brokerIp, brokerPort);

                // Display connected broker information
                System.out.println("Connected to broker at " + brokerIp + ":" + brokerPort);
                sub.startConsole();
                break;
            } catch (IOException e) {
                System.out.println("Error connecting to broker: " + e.getMessage());
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    System.out.println("Retrying in " + RETRY_DELAY_MS / 1000 + " seconds...");
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.out.println("Maximum retry attempts reached. Exiting.");
                }
            }
        }
    }

    // Starts the console interface for user interaction
    private void startConsole() {
        Scanner scanner = new Scanner(System.in);
        while (isRunning) {
            System.out.println("\nPlease select command: list, sub, current, unsub.");
            System.out.println("1. list {all} #list all topics");
            System.out.println("2. sub {topic_id} #subsribe to a topic");
            System.out.println("3. current #show the current subscriptions of the subsriber");
            System.out.println("4. unsub {topic_id} #unsubsribe from a topic");
            
            String input = scanner.nextLine().trim();
            String[] parts = input.split("\\s+", 2);
            
            if (parts.length == 0) {
                System.out.println("Invalid input. Please try again.");
                continue;
            }

            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "list":
                        if (parts.length != 2 || !parts[1].equalsIgnoreCase("all")) {
                            System.out.println("Invalid format. Use: list all");
                            break;
                        }
                        listAllTopics();
                        break;
                    case "sub":
                        if (parts.length != 2) {
                            System.out.println("Invalid format. Use: sub {topic_id}");
                            break;
                        }
                        subscribeTopic(parts[1]);
                        break;
                    case "current":
                        if (parts.length != 1) {
                            System.out.println("Invalid format. Use: current");
                            break;
                        }
                        showCurrentSubscriptions();
                        break;
                    case "unsub":
                        if (parts.length != 2) {
                            System.out.println("Invalid format. Use: unsub {topic_id}");
                            break;
                        }
                        unsubscribeTopic(parts[1]);
                        break;
                    default:
                        System.out.println("Invalid command. Please try again.");
                }
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Handle received messages

        }
        scanner.close();
    }

}
