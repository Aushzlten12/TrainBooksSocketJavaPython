package org.example;

import com.google.gson.Gson;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class BookClient extends JFrame {
    private JTextField ipField, portField;
    private JButton selectFileButton, sendButton;
    private JLabel fileLabel, statusLabel;
    private File selectedFile;
    private Gson gson;

    public BookClient() {
        setTitle("Client - Send book");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(6, 1));

        JPanel ipPanel = new JPanel(new FlowLayout());
        ipPanel.add(new JLabel("IP server:"));
        ipField = new JTextField("localhost", 15);
        ipPanel.add(ipField);
        add(ipPanel);

        JPanel portPanel = new JPanel(new FlowLayout());
        portPanel.add(new JLabel("Port:"));
        portField = new JTextField("1234", 5);
        portPanel.add(portField);
        add(portPanel);

        selectFileButton = new JButton("Select file .txt");
        selectFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectFile();
            }
        });
        add(selectFileButton);

        fileLabel = new JLabel("No file selected", SwingConstants.CENTER);
        add(fileLabel);

        sendButton = new JButton("Send file");
        sendButton.setEnabled(false);
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendFile();
            }
        });
        add(sendButton);

        statusLabel = new JLabel("Status: Waiting ", SwingConstants.CENTER);
        add(statusLabel);

        gson = new Gson(); // Initialize Gson
    }

    private void selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();

            if (selectedFile.getName().endsWith(".txt")) {
                fileLabel.setText("File selected: " + selectedFile.getName());
                sendButton.setEnabled(true);
                statusLabel.setText("Status: file ready to send");
            } else {
                statusLabel.setText("Please select a txt file");
                fileLabel.setText("No file selected");
                sendButton.setEnabled(false);
            }
        }
    }

    private void sendFile() {
        String ip = ipField.getText();
        int port = Integer.parseInt(portField.getText());

        try {
            Socket socket = new Socket(ip, port);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Create a JSON object to send
            String clientType = "book";
            String bookName = selectedFile.getName();
            String json = gson.toJson(new BookData(clientType, bookName)); // Create JSON
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            out.writeShort(jsonBytes.length);  // Escribir la longitud
            out.write(jsonBytes);
            sendFileToServer(socket, selectedFile);
            statusLabel.setText("File sent successfully!");
        } catch (IOException ex) {
            statusLabel.setText("Error sending the file.");
            ex.printStackTrace();
        }
    }

    private void sendFileToServer(Socket socket, File file) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        BufferedInputStream fileInput = new BufferedInputStream(new FileInputStream(file));

        // Enviar la longitud del archivo al servidor
        long fileLength = file.length();
        out.writeInt((int) fileLength);  // Enviar la longitud como un int
        out.flush();

        // Enviar el contenido del archivo
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fileInput.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        fileInput.close();
        out.flush();

        // Aquí puedes esperar una confirmación del servidor, si es necesario
        socket.close();  // Cerrar el socket solo cuando todo el archivo haya sido enviado
    }


    // Inner class to represent the data to be sent as JSON
    private static class BookData {
        private String clientType;
        private String name_of_book;

        public BookData(String clientType, String name_of_book) {
            this.clientType = clientType;
            this.name_of_book = name_of_book;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                BookClient client = new BookClient();
                client.setVisible(true);
            }
        });
    }
}
