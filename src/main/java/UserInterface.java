/**
 * UserInterface:
 * Responsible for all the visual and interaction elements of the user interface. This class likely includes
 * creating and managing GUI components such as windows, buttons, text fields, etc. It receives user actions,
 * triggers corresponding events, and provides feedback.
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.Arrays;

public class UserInterface extends JFrame {
    private JTextField inputField;
    private JTextArea displayArea;
    private JButton addButton, queryButton, deleteButton, appendButton, updateButton;
    private DictionaryClient client;

    public UserInterface(DictionaryClient client) {
        this.client = client;

        setTitle("Dictionary Application");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Input field
        inputField = new JTextField(30);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(inputField, gbc);

        // Buttons
        addButton = createButton("Add", e -> handleAdd());
        queryButton = createButton("Query", e -> handleQuery());
        deleteButton = createButton("Delete", e -> handleDelete());
        appendButton = createButton("Append", e -> handleAppend());
        updateButton = createButton("Update", e -> handleUpdate());

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        add(addButton, gbc);

        gbc.gridx = 1;
        add(queryButton, gbc);

        gbc.gridx = 2;
        add(deleteButton, gbc);

        gbc.gridx = 3;
        add(appendButton, gbc);

        gbc.gridx = 4;
        add(updateButton, gbc);

        // Display area
        displayArea = new JTextArea(10, 30);
        displayArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(displayArea);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        add(scrollPane, gbc);

        setVisible(true);
    }

    private JButton createButton(String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        return button;
    }

    private void handleAdd() {
        String word = inputField.getText();
        String definition = JOptionPane.showInputDialog("Enter definition:");
        if (word != null && !word.isEmpty() && definition != null && !definition.isEmpty()) {
            try {
                String response = client.add(word, definition);
                displayArea.append("Server response: " + response + "\n");
            } catch (IOException e) {
                displayArea.append("Error: " + e.getMessage() + "\n");
            }
        }
    }

    private void handleQuery() {
        String word = inputField.getText();
        if (word != null && !word.isEmpty()) {
            try {
                String response = client.query(word);
                showQueryResult(word, response);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Query Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showQueryResult(String word, String response) {
        if (response.equals("Word not found")) {
            JOptionPane.showMessageDialog(this, "The word '" + word + "' was not found in the dictionary.", "Word Not Found", JOptionPane.INFORMATION_MESSAGE);
        } else {
            String[] definitions = response.split("\n");
            StringBuilder formattedDefinitions = new StringBuilder();
            for (int i = 0; i < definitions.length; i++) {
                formattedDefinitions.append(i + 1).append(". ").append(definitions[i]).append("\n");
            }

            JTextArea textArea = new JTextArea(formattedDefinitions.toString());
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(300, 150));

            JOptionPane.showMessageDialog(this, scrollPane, "Definition of '" + word + "'", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void handleAppend() {
        String word = inputField.getText();
        String definition = JOptionPane.showInputDialog("Enter definition to append:");
        if (word != null && !word.isEmpty() && definition != null && !definition.isEmpty()) {
            try {
                String response = client.append(word, definition);
                displayArea.append("Server response: " + response + "\n");
            } catch (IOException e) {
                displayArea.append("Error: " + e.getMessage() + "\n");
            }
        }
    }

    private void handleDelete() {
        String word = inputField.getText();
        if (word != null && !word.isEmpty()) {
            try {
                String response = client.delete(word);
                displayArea.append("Server response: " + response + "\n");
            } catch (IOException e) {
                displayArea.append("Error: " + e.getMessage() + "\n");
            }
        }
    }

    private void handleUpdate() {
        String word = inputField.getText();
        if (word != null && !word.isEmpty()) {
            JTextField oldDefinitionField = new JTextField(20);
            JTextField newDefinitionField = new JTextField(20);

            JPanel panel = new JPanel(new GridLayout(0, 1));
            panel.add(new JLabel("Old definition:"));
            panel.add(oldDefinitionField);
            panel.add(new JLabel("New definition:"));
            panel.add(newDefinitionField);

            int result = JOptionPane.showConfirmDialog(null, panel,
                    "Update Definition", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String oldDefinition = oldDefinitionField.getText();
                String newDefinition = newDefinitionField.getText();
                if (!newDefinition.isEmpty()) {
                    try {
                        String response = client.update(word, oldDefinition, newDefinition);
                        displayArea.append("Server response: " + response + "\n");
                    } catch (IOException e) {
                        displayArea.append("Error: " + e.getMessage() + "\n");
                    }
                }
            }
        }
    }
}