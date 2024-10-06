/*
 * This class is the broker class that will handle the messages and the topics
 */

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.UUID;

public class broker {
    private static final int MAX_MESSAGE_LENGTH = 100;
    private static final int MAX_PUBLISHERS = 5;
    private static final int MAX_SUBSCRIBERS = 10;

    private int port;
    private Map<String, Topic> topics;
    private Map<String, Socket> publisherSockets;
    private Map<String, Socket> subscriberSockets;
    private ExecutorService executorService;

    public broker(int port) {
        this.port = port;
        this.topics = new ConcurrentHashMap<>();
        this.publisherSockets = new ConcurrentHashMap<>();
        this.subscriberSockets = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(MAX_PUBLISHERS + MAX_SUBSCRIBERS);
    }

    // Start the broker
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Broker started on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleNewConnection(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handle new client connection
    private void handleNewConnection(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String clientType = reader.readLine();
            String clientName = reader.readLine();

            if ("PUBLISHER".equals(clientType) && publisherSockets.size() < MAX_PUBLISHERS) {
                publisherSockets.put(clientName, clientSocket);
                handlePublisher(clientName, reader);
            } else if ("SUBSCRIBER".equals(clientType) && subscriberSockets.size() < MAX_SUBSCRIBERS) {
                subscriberSockets.put(clientName, clientSocket);
                handleSubscriber(clientName, reader);
            } else {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Create a new topic
    public String createTopic(String topicId, String topicName, String publisherName) {
        if (topics.containsKey(topicId)) {
            return "ERROR: Topic ID already exists";
        }
        topics.put(topicId, new Topic(topicId, topicName, publisherName));
        return "SUCCESS: Topic created with ID: " + topicId;
    }

    // Publish a message to a topic
    public void publishMessage(String topicId, String message, String publisherName) {
        if (message.length() > MAX_MESSAGE_LENGTH) {
            try {
                messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Message exceeds maximum length");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        Topic topic = topics.get(topicId);
        if (topic != null) {
            String formattedMessage = messageHandler.formatMessage(topicId, topic.name, publisherName, message);
            boolean allSent = true;
            for (String subscriber : topic.subscribers) {
                try {
                    Socket subscriberSocket = subscriberSockets.get(subscriber);
                    if (subscriberSocket != null && subscriberSocket.isConnected()) {
                        messageHandler.sendMessage(subscriberSocket, formattedMessage);
                    } else {
                        System.out.println("Subscriber socket is null or disconnected: " + subscriber);
                        allSent = false;
                    }
                } catch (IOException e) {
                    System.out.println("Error sending message to subscriber: " + subscriber);
                    e.printStackTrace();
                    allSent = false;
                }
            }
            System.out.println("Message published to topic " + topicId + ": " + formattedMessage);
            try {
                if (allSent) {
                    messageHandler.sendMessage(publisherSockets.get(publisherName), "SUCCESS");
                } else {
                    messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Some subscribers could not receive the message");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Topic not found: " + topicId);
            try {
                messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Topic not found");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Subscribe to a topic
    public void subscribeTopic(String topicId, String subscriberName) {
        Topic topic = topics.get(topicId);
        if (topic != null) {
            topic.subscribers.add(subscriberName);
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriberName);
                messageHandler.sendMessage(subscriberSocket, "SUCCESS|" + topic.name + "|" + topic.publisherName + "|" + topicId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriberName);
                messageHandler.sendMessage(subscriberSocket, "FAILED|Topic not found");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Unsubscribe from a topic
    public void unsubscribeTopic(String topicId, String subscriberName) {
        System.out.println("Attempting to unsubscribe " + subscriberName + " from topic " + topicId);
        Topic topic = topics.get(topicId);
        if (topic != null) {
            boolean removed = topic.subscribers.remove(subscriberName);
            System.out.println("Subscriber removed from topic: " + removed);
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriberName);
                messageHandler.sendMessage(subscriberSocket, removed ? "SUCCESS" : "FAILED|Not subscribed to this topic");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Topic not found: " + topicId);
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriberName);
                messageHandler.sendMessage(subscriberSocket, "FAILED: Topic not found");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Delete a topic
    public void deleteTopic(String topicId) {
        Topic topic = topics.remove(topicId);
        if (topic != null) {
            for (String subscriber : topic.subscribers) {
                try {
                    Socket subscriberSocket = subscriberSockets.get(subscriber);
                    messageHandler.sendMessage(subscriberSocket, "Topic deleted: " + topic.name);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Get all available topics
    public List<Topic> getAllTopics() {
        return new ArrayList<>(topics.values());
    }

    // Inner class to represent a Topic
    private static class Topic {
        String id;
        String name;
        String publisherName;
        Set<String> subscribers;

        Topic(String id, String name, String publisherName) {
            this.id = id;
            this.name = name;
            this.publisherName = publisherName;
            this.subscribers = new HashSet<>();
        }
    }

    // Handle publisher requests
    private void handlePublisher(String publisherName, BufferedReader reader) {
        try {
            String request;
            while ((request = reader.readLine()) != null) {
                switch (request) {
                    case "CREATE_TOPIC":
                        String topicId = reader.readLine();
                        String topicName = reader.readLine();
                        String response = createTopic(topicId, topicName, publisherName);
                        messageHandler.sendMessage(publisherSockets.get(publisherName), response);
                        break;
                    case "PUBLISH_MESSAGE":
                        String msgTopicId = reader.readLine();
                        String message = reader.readLine();
                        publishMessage(msgTopicId, message, publisherName);
                        // 移除这行，因为响应已经在 publishMessage 方法中发送
                        // messageHandler.sendMessage(publisherSockets.get(publisherName), "Message published");
                        break;
                    case "SHOW_SUBSCRIBER_COUNT":
                        String showTopicId = reader.readLine();
                        Topic topic = topics.get(showTopicId);
                        if (topic != null && topic.publisherName.equals(publisherName)) {
                            String count = showTopicId + "|" + topic.name + "|" + topic.subscribers.size() + "\n";
                            messageHandler.sendMessage(publisherSockets.get(publisherName), count + "END");
                        } else {
                            messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Topic not found or not owned by this publisher");
                        }
                        break;
                    case "DELETE_TOPIC":
                        String delTopicId = reader.readLine();
                        deleteTopic(delTopicId);
                        messageHandler.sendMessage(publisherSockets.get(publisherName), "Topic deleted");
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error handling publisher: " + publisherName);
            e.printStackTrace();
        }
    }

    // Handle subscriber requests
    private void handleSubscriber(String subscriberName, BufferedReader reader) {
        try {
            String request;
            while ((request = reader.readLine()) != null) {
                switch (request) {
                    case "LIST_TOPICS":
                        StringBuilder topicList = new StringBuilder();
                        for (Topic topic : topics.values()) {
                            topicList.append(topic.id).append("|")
                                     .append(topic.name).append("|")
                                     .append(topic.publisherName).append("\n");
                        }
                        messageHandler.sendMessage(subscriberSockets.get(subscriberName), topicList.toString() + "END");
                        break;
                    case "SUBSCRIBE_TOPIC":
                        String subTopicId = reader.readLine();
                        subscribeTopic(subTopicId, subscriberName);
                        break;
                    case "UNSUBSCRIBE_TOPIC":
                        String unsubTopicId = reader.readLine();
                        unsubscribeTopic(unsubTopicId, subscriberName);
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Main method to run the broker
    public static void main(String[] args) {
        int port = 8080; // 默认端口
        broker brokerInstance = new broker(port);
        
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        // 连接到其他broker的逻辑保持不变，但在开发阶段可以先注释掉
        /*
        if (args.length > 2 && args[1].equals("-b")) {
            for (int i = 2; i < args.length; i++) {
                String[] brokerInfo = args[i].split(":");
                String ip = brokerInfo[0];
                int brokerPort = Integer.parseInt(brokerInfo[1]);
                brokerInstance.connectToBroker(ip, brokerPort);
            }
        }
        */
        
        System.out.println("Broker starting on port " + port);
        brokerInstance.start();
    }

    private void connectToBroker(String ip, int port) {
        // TODO: Implement connection to other brokers
        System.out.println("Connecting to broker at " + ip + ":" + port);
    }
}