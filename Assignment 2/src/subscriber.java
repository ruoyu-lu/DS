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
        
        String response;
        while (!(response = waitForResponse()).equals("END")) {
            String[] parts = response.split("\\|");
            if (parts.length == 3) {
                System.out.printf("%s %s %s%n", parts[0], parts[1], parts[2]);
            }
        }
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
        for (String topicId : subscriptions) {
            String details = subscriptionDetails.get(topicId);
            if (details != null) {
                String[] parts = details.split("\\|");
                System.out.printf("%s %s %s%n", topicId, parts[0], parts[1]);
            }
        }
    }

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

    private void startListening() {
        new Thread(() -> {
            try {
                String message;
                while (isRunning && (message = in.readLine()) != null) {
                    // 检查消息是否符合新的格式（以日期时间开头）
                    if (message.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}.*")) {
                        System.out.println(message);
                    } else {
                        messageQueue.put(message);
                    }
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

            // 处理接收到的消息
            processReceivedMessages();
        }
        scanner.close();
    }

    private void processReceivedMessages() {
        List<String> messages = new ArrayList<>();
        messageQueue.drainTo(messages);
        for (String message : messages) {
            if (!message.equals("SUCCESS") && !message.startsWith("[")) {
                System.out.println(message);
            }
        }
    }
}