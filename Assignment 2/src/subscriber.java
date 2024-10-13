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
    private Map<String, String> subscriptionDetails; 

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
        System.out.println("当前订阅：");
        if (subscriptionDetails.isEmpty()) {
            System.out.println("没有活跃的订阅。");
        } else {
            for (Map.Entry<String, String> entry : subscriptionDetails.entrySet()) {
                System.out.println(entry.getValue());
            }
        }
    }

    public void unsubscribeTopic(String topicId) throws IOException {
        System.out.println("正在尝试取消订阅主题：" + topicId);
        out.println("UNSUBSCRIBE_TOPIC");
        out.println(topicId);
        String response = waitForResponse();
        System.out.println("收到响应：" + response);
        if (response.equals("SUCCESS")) {
            subscriptions.remove(topicId);
            subscriptionDetails.remove(topicId);
            System.out.println("成功取消订阅主题：" + topicId);
        } else {
            System.out.println("取消订阅主题失败：" + topicId + " - " + response);
        }
    }

    private void startListening() {
        new Thread(() -> {
            try {
                String message;
                while (isRunning && (message = in.readLine()) != null) {
                    // check if the message is a time-stamped message
                    if (message.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}.*")) {
                        System.out.println(message);
                    }
                    if (message.startsWith("TOPIC_DELETED|")) {
                        handleTopicDeleted(message);
                    }else{
                        messageQueue.put(message);
                    }
                }
                // while (isRunning && (message = in.readLine()) != null) {
                //     if (message.startsWith("TOPIC_DELETED|")) {
                //         handleTopicDeleted(message);
                //     } else {
                //         messageQueue.put(message);
                //     }
                // }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

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

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("用法: java -jar subscriber.jar username broker_ip broker_port");
            return;
        }

        String username = args[0];
        String brokerIp = args[1];
        int brokerPort = Integer.parseInt(args[2]);

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
