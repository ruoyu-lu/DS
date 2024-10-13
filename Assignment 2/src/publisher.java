/*
 * represent a publisher client that communicates with the Broker through sockets.
 * provide methods to create topics, publish messages, delete topics, and get subscriber counts.
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

    public publisher(String name, String brokerAddress, int brokerPort) throws IOException {
        this.name = name;
        this.brokerSocket = new Socket(brokerAddress, brokerPort);
        this.out = new PrintWriter(brokerSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()));
        
        out.println("PUBLISHER");
        out.println(name);
    }

    public void createTopic(String topicId, String topicName) throws IOException {
        out.println("CREATE_TOPIC");
        out.println(topicId);
        out.println(topicName);
        String response = in.readLine();
        System.out.println(response);
    }

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

    public void showSubscriberCount(String topicId) throws IOException {
        System.out.println("Showing subscriber count for topic " + topicId);
        out.println("SHOW_SUBSCRIBER_COUNT");
        out.println(topicId);
        // System.out.println("Waiting for response...");
        
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
            // String[] parts = response.split("\\|");
            // if (parts.length == 3) {
            //     System.out.printf("%s %s %s%n", parts[0], parts[1], parts[2]);
            // } else {
            //     System.out.println("Unexpected response format: " + response);
            // }
            System.out.println(response);
        }
        // System.out.println("Finished processing SHOW_SUBSCRIBER_COUNT response");
    }

    public void deleteTopic(String topicId) throws IOException {
        out.println("DELETE_TOPIC");
        out.println(topicId);
        String response = in.readLine();
        System.out.println(response);
    }

    public void close() throws IOException {
        brokerSocket.close();
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java -jar publisher.jar username broker_ip broker_port");
            return;
        }

        String username = args[0];
        String brokerIp = args[1];
        int brokerPort = Integer.parseInt(args[2]);

        try {
            publisher pub = new publisher(username, brokerIp, brokerPort);
            pub.startConsole();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

//    private void handleShowSubscriberCount(String topicId) throws IOException {
//        out.println("SHOW_SUBSCRIBER_COUNT");
//        out.println(topicId);
//
//        StringBuilder response = new StringBuilder();
//        String line;
//        while (!(line = in.readLine()).equals("END")) {
//            response.append(line).append("\n");
//        }
//
//        String result = response.toString().trim();
//        if (result.startsWith("ERROR")) {
//            System.out.println(result);
//        } else {
//            String[] parts = result.split("\\|");
//            if (parts.length == 3) {
//                System.out.println("Topic ID: " + parts[0]);
//                System.out.println("Topic Name: " + parts[1]);
//                System.out.println("Subscriber Count: " + parts[2]);
//            } else {
//                System.out.println("Unexpected response format: " + result);
//            }
//        }
//    }
}