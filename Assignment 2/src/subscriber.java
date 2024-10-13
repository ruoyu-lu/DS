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
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 5000;

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

    // 新方法：从 Directory Service 获取 broker 列表
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
        if (args.length != 2) {
            System.out.println("用法: java -jar subscriber.jar username directoryServiceIp:directoryServicePort");
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
                // 随机选择broker连接
                String[] selectedBroker = brokers.get(new Random().nextInt(brokers.size())); 
                String brokerIp = selectedBroker[0];
                int brokerPort = Integer.parseInt(selectedBroker[1]);

                subscriber sub = new subscriber(username, brokerIp, brokerPort);

                //显示连接broker的端口
                System.out.println("Connected to broker at " + brokerIp + ":" + brokerPort);
                sub.startConsole();
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

        }
        scanner.close();
    }

}
