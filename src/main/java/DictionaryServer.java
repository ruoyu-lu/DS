/**
 * DictionaryServer:
 * The main server class, responsible for listening for connection requests from clients over the network.
 * Upon receiving a connection request, it may create a new ClientHandler to manage interactions with
 * that client. It also likely manages loading and maintaining the dictionary data, as well as managing
 * a thread pool to handle concurrent requests efficiently.
 */
import javax.swing.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class DictionaryServer {
    private static final int PORT = Constant.SERVER_PORT;
    private final ThreadPool threadPool;
    private ServerSocket serverSocket;
    private final Dictionary dictionary;
    private ServerInterface gui;
    private AtomicInteger connectedClients;
    private AtomicInteger processedRequests;

    public DictionaryServer(int numberOfThreads) {
        this.threadPool = new ThreadPool(numberOfThreads);
        this.dictionary = new Dictionary();
        this.connectedClients = new AtomicInteger(0);
        this.processedRequests = new AtomicInteger(0);
    }

    public void setGUI(ServerInterface gui) {
        this.gui = gui;
    }

    public void startServer() throws IOException {
        dictionary.loadDictionary(Constant.DICTIONARY_FILE);
        try {
            serverSocket = new ServerSocket(PORT);
            log("Server started on port " + PORT);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log("Client connected: " + clientSocket.getInetAddress().getHostAddress());

                    threadPool.execute(new ClientHandler(clientSocket, dictionary, this));
                    updateConnectedClients(1);
                } catch (IOException e) {
                    log("Exception accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log("Could not listen on port " + PORT + ": " + e.getMessage());
        } finally {
            stopServer();
        }
    }

    public void stopServer() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log("Error closing server socket: " + e.getMessage());
            }
        }
        threadPool.shutdown();
        log("Server stopped.");
    }

    public void updateConnectedClients(int delta) {
        int newCount = connectedClients.addAndGet(delta);
        if (gui != null) {
            gui.updateClientCount(newCount);
        }
    }

    public void incrementRequestCount() {
        int newCount = processedRequests.incrementAndGet();
        if (gui != null) {
            gui.updateRequestCount(newCount);
        }
    }

    public void log(String message) {
        System.out.println(message);
        if (gui != null) {
            gui.appendLog(message);
        }
    }

    public static void main(String[] args) {
        DictionaryServer server = new DictionaryServer(Constant.NUMBER_OF_THREADS);
        ServerInterface gui = new ServerInterface();
        server.setGUI(gui);
        SwingUtilities.invokeLater(() -> gui.setVisible(true));
        try {
            server.startServer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}