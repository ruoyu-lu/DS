/*
 * represent a publisher client that communicates with the Broker through sockets.
 * provide methods to create topics, publish messages, delete topics, and get subscriber counts.
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class publisher {
    private String name;
    private Socket brokerSocket;
    private PrintWriter out;
    private BufferedReader in;
    private static final int MAX_MESSAGE_LENGTH = 100;

    public publisher(String name, String brokerAddress, int brokerPort) throws IOException {
        this.name = name;
        this.brokerSocket = new Socket(brokerAddress, brokerPort);
        this.out = new PrintWriter(brokerSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()));
        
        out.println("PUBLISHER");
        out.println(name);
    }

    public void createTopic(String topicName) throws IOException {
        out.println("CREATE_TOPIC");
        out.println(topicName);
        String response = in.readLine();
        System.out.println("Topic created with ID: " + response);
    }

    public void publishMessage(String topicId, String message) throws IOException {
        if (message.length() > MAX_MESSAGE_LENGTH) {
            System.out.println("Message is too long. Maximum length is " + MAX_MESSAGE_LENGTH + " characters.");
            return;
        }
        out.println("PUBLISH_MESSAGE");
        out.println(topicId);
        out.println(message);
        String response = in.readLine();
        System.out.println("Message published: " + response);
    }

    public void showSubscriberCount() throws IOException {
        out.println("SHOW_SUBSCRIBER_COUNT");
        System.out.println("Topics and their subscriber counts:");
        System.out.println("+----------------------+---------------------------------------+------------------+");
        System.out.println("|     Topic Name       |                Topic ID               | Subscriber Count |");
        System.out.println("+----------------------+---------------------------------------+------------------+");
        
        String response;
        while (!(response = in.readLine()).equals("END")) {
            String[] parts = response.split("\\|");
            if (parts.length == 3) {
                System.out.printf("| %-20s | %-32s | %-17s |%n", parts[0], parts[1], parts[2]);
            }
        }
        System.out.println("+----------------------+---------------------------------------+------------------+");
    }

    public void deleteTopic(String topicId) throws IOException {
        out.println("DELETE_TOPIC");
        out.println(topicId);
        String response = in.readLine();
        System.out.println("Topic deleted: " + response);
    }

    public void close() throws IOException {
        brokerSocket.close();
    }

    public static void main(String[] args) {
        String username = "DefaultPublisher";
        String brokerIp = "localhost";
        int brokerPort = 8080;

        if (args.length == 3) {
            username = args[0];
            brokerIp = args[1];
            brokerPort = Integer.parseInt(args[2]);
        }

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
                System.out.println("\n1. Create Topic");
                System.out.println("2. Publish Message");
                System.out.println("3. Show Subscriber Count");
                System.out.println("4. Delete Topic");
                System.out.println("5. Exit");
                System.out.print("Choose an option: ");

                int choice = scanner.nextInt();
                scanner.nextLine(); // Consume newline

                try {
                    switch (choice) {
                        case 1:
                            System.out.print("Enter topic name: ");
                            createTopic(scanner.nextLine());
                            break;
                        case 2:
                            System.out.print("Enter topic ID: ");
                            String topicId = scanner.nextLine();
                            System.out.print("Enter message: ");
                            String message = scanner.nextLine();
                            publishMessage(topicId, message);
                            break;
                        case 3:
                            showSubscriberCount();
                            break;
                        case 4:
                            System.out.print("Enter topic ID to delete: ");
                            deleteTopic(scanner.nextLine());
                            break;
                        case 5:
                            close();
                            scanner.close();
                            return;
                        default:
                            System.out.println("Invalid option. Please try again.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            scanner.close();
        }
    }
}