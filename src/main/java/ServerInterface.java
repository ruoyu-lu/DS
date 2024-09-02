import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * ServerInterface.java
 * GUI interface for the dictionary server.
 * Displays server logs, connected clients, and processed requests.
 */
public class ServerInterface extends JFrame {
    private JTextArea logArea;
    private JLabel clientCountLabel;
    private JLabel requestCountLabel;
    private JScrollPane scrollPane;
    private Dictionary dictionary = new Dictionary();

    public ServerInterface() {
        initComponents();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    dictionary.saveDictionary(Constant.DICTIONARY_FILE);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private void initComponents() {
        setTitle("Dictionary Server Log");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clientCountLabel = new JLabel("Connected Clients: 0");
        requestCountLabel = new JLabel("Processed Requests: 0");
        statsPanel.add(clientCountLabel);
        statsPanel.add(requestCountLabel);
        add(statsPanel, BorderLayout.SOUTH);
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateClientCount(int count) {
        SwingUtilities.invokeLater(() -> {
            clientCountLabel.setText("Connected Clients: " + count);
        });
    }

    public void updateRequestCount(int count) {
        SwingUtilities.invokeLater(() -> {
            requestCountLabel.setText("Processed Requests: " + count);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerInterface gui = new ServerInterface();
            gui.setVisible(true);
        });
    }
}