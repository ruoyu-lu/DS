import javax.swing.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DictionaryServer.java
 * Server class for the dictionary server.
 * The server listens for incoming client connections and creates a new ClientHandler thread for each client.
 * The ClientHandler thread reads the client's request, processes it, and sends a response back to the client.
 * The server uses a thread pool to manage the ClientHandler threads.
 * The server also keeps track of the number of connected clients and the number of processed requests.
 * The server can be started and stopped using the startServer() and stopServer() methods.
 * The server can be run as a standalone application with a GUI interface or from the command line.
 */
public class DictionaryServer {
    private final ThreadPool threadPool;
    private ServerSocket serverSocket;
    private final Dictionary dictionary;
    private ServerInterface gui;
    private AtomicInteger connectedClients;
    private AtomicInteger processedRequests;
    private final String port;
    private final String fileName;

    public DictionaryServer(int numberOfThreads, String port , String fileName) {
        this.threadPool = new ThreadPool(numberOfThreads);
        this.port = port;
        this.fileName = fileName;
        try {
            this.dictionary = new Dictionary(fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.connectedClients = new AtomicInteger(0);
        this.processedRequests = new AtomicInteger(0);
    }

    public void setGUI(ServerInterface gui) {
        this.gui = gui;
    }

    public void startServer() throws IOException {
        dictionary.loadDictionary(fileName);
        try {
            int portNum = Integer.parseInt(port);
            serverSocket = new ServerSocket(portNum);
            log("Server started on port " + port);

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
            log("Could not listen on port " + port + ": " + e.getMessage());
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

    public static void main(String[] args) throws IOException {
        //args[0] = port, args[1] = fileName
        DictionaryServer server = new DictionaryServer(Constant.NUMBER_OF_THREADS,args[0],args[1]);
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