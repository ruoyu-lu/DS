/*
 * represent a subscriber client that communicates with the Broker through sockets.
 * provide methods to list topics, subscribe/unsubscribe to topics, and receive messages.
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
    private Map<String, String> subscriptionDetails; // 新增字段来存储订阅详情

    public subscriber(String name, String brokerAddress, int brokerPort) throws IOException {
        this.name = name;
        this.brokerSocket = new Socket(brokerAddress, brokerPort);
        this.out = new PrintWriter(brokerSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()));
        this.subscriptions = new HashSet<>();
        this.isRunning = true;
        this.messageQueue = new LinkedBlockingQueue<>();
        this.subscriptionDetails = new HashMap<>(); // 初始化新字段
        startListening();
        out.println("SUBSCRIBER");
        out.println(name);
    }

    public void listAllTopics() throws IOException {
        out.println("LIST_TOPICS");
        System.out.println("Available Topics:");
        System.out.println("+--------------------------------------+------------+------------------+");
        System.out.println("|               Topic ID               | Topic Name |     Publisher    |");
        System.out.println("+--------------------------------------+------------+------------------+");
        
        String response;
        while (!(response = waitForResponse()).equals("END")) {
            String[] parts = response.split("\\|");
            if (parts.length == 3) {
                System.out.printf("| %-32s | %-10s | %-16s |%n", parts[0], parts[1], parts[2]);
            } else {
                System.out.println("| " + response);
            }
        }
        
        System.out.println("+--------------------------------------+------------+------------------+");
    }

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

    public void showCurrentSubscriptions() {
        System.out.println("Current subscriptions:");
        System.out.println("+--------------------------------------+------------+------------------+");
        System.out.println("|               Topic ID               | Topic Name |     Publisher    |");
        System.out.println("+--------------------------------------+------------+------------------+");
        for (String topicId : subscriptions) {
            String details = subscriptionDetails.get(topicId);
            if (details != null) {
                String[] parts = details.split("\\|");
                System.out.printf("| %-36s | %-10s | %-16s |%n", topicId, parts[0], parts[1]);
            }
        }
        System.out.println("+--------------------------------------+------------+------------------+");
    }

    public void unsubscribeTopic(String topicId) throws IOException {
        out.println("UNSUBSCRIBE_TOPIC");
        out.println(topicId);
        String response = waitForResponse();
        if (response.equals("SUCCESS")) {
            subscriptions.remove(topicId);
            subscriptionDetails.remove(topicId);
            System.out.println("Successfully unsubscribed from topic: " + topicId);
        } else {
            System.out.println("Failed to unsubscribe from topic: " + topicId);
        }
    }

    private void startListening() {
        new Thread(() -> {
            try {
                String message;
                while (isRunning && (message = in.readLine()) != null) {
                    messageQueue.put(message);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

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

    public void close() throws IOException {
        isRunning = false;
        brokerSocket.close();
    }

    public static void main(String[] args) {
        String username = "DefaultSubscriber";
        String brokerIp = "localhost";
        int brokerPort = 8080;

        if (args.length == 3) {
            username = args[0];
            brokerIp = args[1];
            brokerPort = Integer.parseInt(args[2]);
        }

        try {
            subscriber sub = new subscriber(username, brokerIp, brokerPort);
            sub.startConsole();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startConsole() {
        Scanner scanner = new Scanner(System.in);
        while (isRunning) {
            System.out.println("\n1. List All Topics");
            System.out.println("2. Subscribe to Topic");
            System.out.println("3. Show Current Subscriptions");
            System.out.println("4. Unsubscribe from Topic");
            System.out.println("5. Exit");
            System.out.print("Choose an option: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            try {
                switch (choice) {
                    case 1:
                        listAllTopics();
                        break;
                    case 2:
                        System.out.print("Enter topic ID to subscribe: ");
                        subscribeTopic(scanner.nextLine());
                        break;
                    case 3:
                        showCurrentSubscriptions();
                        break;
                    case 4:
                        System.out.print("Enter topic ID to unsubscribe: ");
                        unsubscribeTopic(scanner.nextLine());
                        break;
                    case 5:
                        close();
                        isRunning = false;
                        return;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 处理接收到的消息
            processReceivedMessages();
        }
    }

    private void processReceivedMessages() {
        List<String> messages = new ArrayList<>();
        messageQueue.drainTo(messages);
        for (String message : messages) {
            if (!message.equals("SUCCESS")) {
                System.out.println("Received message: " + message);
            }
        }
    }
}