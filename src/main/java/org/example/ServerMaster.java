package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMaster extends JFrame {
    private JTextField portField;
    private JButton startServerButton;
    private JButton addNodeButton;
    private JLabel statusLabel;
    private JTextArea logArea;

    private DefaultListModel<String> bookListModel = new DefaultListModel<>();
    private JList<String> bookList;

    private List<JTextField> nodeIPFields = new ArrayList<>();
    private List<JTextField> nodePortFields = new ArrayList<>();

    private ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private Map<String, List<String>> modelsMap = new HashMap<>();



    private JPanel nodePanel;
    private ServerSocket serverSocket;
    private boolean isServerRunning = false;
    private List<BookEntry> receivedBooks = new ArrayList<>();

    private int expectedNodes = 0;

    public ServerMaster() {
        setTitle("Server - Received books or Send Response");
        setSize(800, 600); // Aumentado el tamaño para mejor visualización
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(new JLabel("Server Port:"));
        portField = new JTextField("12345", 5);
        topPanel.add(portField);
        startServerButton = new JButton("Start server");
        startServerButton.addActionListener(e -> startServer());
        topPanel.add(startServerButton);
        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2));
        JPanel nodeContainerPanel = new JPanel(new BorderLayout());
        nodePanel = new JPanel();
        nodePanel.setLayout(new BoxLayout(nodePanel, BoxLayout.Y_AXIS));

        JScrollPane nodeScrollPane = new JScrollPane(nodePanel);
        nodeContainerPanel.add(nodeScrollPane, BorderLayout.CENTER);

        addNodeButton = new JButton("Add Node");
        addNodeButton.addActionListener(e -> addNodeFields());
        nodeContainerPanel.add(addNodeButton, BorderLayout.SOUTH);

        centerPanel.add(nodeContainerPanel);
        bookListModel = new DefaultListModel<>();
        bookList = new JList<>(bookListModel);
        JScrollPane bookListScrollPane = new JScrollPane(bookList);
        centerPanel.add(bookListScrollPane);

        add(centerPanel, BorderLayout.CENTER);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        add(logScrollPane, BorderLayout.SOUTH);

        statusLabel = new JLabel("Status: Stopped server", SwingConstants.CENTER);
        add(statusLabel, BorderLayout.PAGE_END);

        setVisible(true);
    }

    private void addNodeFields() {
        JPanel nodeRowPanel = new JPanel(new FlowLayout());
        JTextField nodeIPField = new JTextField("127.0.0.1", 10);
        JTextField nodePortField = new JTextField("5000", 5);

        nodeRowPanel.add(new JLabel("Node IP:"));
        nodeRowPanel.add(nodeIPField);
        nodeRowPanel.add(new JLabel("Node Port:"));
        nodeRowPanel.add(nodePortField);

        nodeIPFields.add(nodeIPField);
        nodePortFields.add(nodePortField);

        nodePanel.add(nodeRowPanel);
        nodePanel.revalidate();
        nodePanel.repaint();

        expectedNodes++; // Incrementar el número esperado de nodos
    }

    private void startServer() {
        if (isServerRunning) {
            logArea.append("Server is already running\n");
            return;
        }

        int port = Integer.parseInt(portField.getText());

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isServerRunning = true;

                String serverIP = java.net.InetAddress.getLocalHost().getHostAddress();
                statusLabel.setText("Status: Server is running on IP " + serverIP + " and port " + port);
                logArea.append("Server started on IP " + serverIP + " and port " + port + "\n");

                while (isServerRunning) {
                    Socket clientSocket = serverSocket.accept();
                    logArea.append("Connected client: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "\n");

                    executorService.submit(() -> handleClient(clientSocket));
                }
            } catch (IOException e) {
                logArea.append("Error starting server: " + e.getMessage() + "\n");
            } finally {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                }
            }
        }).start();
    }

    private void handleClient(Socket clientSocket) {
        try(DataInputStream in = new DataInputStream(clientSocket.getInputStream()); DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
            // Read the length of the JSON data
            int jsonLength = in.readUnsignedShort();
            byte[] jsonBytes = new byte[jsonLength];
            in.readFully(jsonBytes);  // Read the JSON data bytes
            String jsonData = new String(jsonBytes, StandardCharsets.UTF_8);  // Convert to String

            // Parse JSON
            JSONObject jsonObject = new JSONObject(jsonData);
            String clientType = jsonObject.getString("clientType"); // Get client type
            if ("book".equals(clientType)) {
                String nameBook = jsonObject.getString("name_of_book");
                System.out.println(nameBook);
                manageBookReceived(in,nameBook);
            } else if ("model".equals(clientType)) {
                System.out.println("DATA");
                String nameBook = jsonObject.getString("name_of_book");
                System.out.println(nameBook);
                String modelJson = jsonObject.getString("model_of_book");
                System.out.println(modelJson);
                addModel(modelsMap, nameBook, modelJson);
                // Actualizamos el estado del libro entrenado

                updateTrainedBookStatus(nameBook);

                // Mostramos el modelo recibido en el área de logs
                logArea.append("Model received for book '" + nameBook + "': " + modelJson + "\n");
                System.out.println(modelsMap);
            } else if ("get_trained_books".equals(clientType)) {
                System.out.println(modelsMap.keySet());
                JSONArray jsonResponse = new JSONArray(modelsMap.keySet()); // Crear un JSONArray con las claves
                byte[] responseBytes = jsonResponse.toString().getBytes(StandardCharsets.UTF_8);
                System.out.println(jsonResponse);
                out.writeShort(responseBytes.length); // Enviar la longitud del JSON
                out.write(responseBytes); // Enviar el JSON
            } else if ("predict".equals(clientType)) {
                handlePredictionRequest(jsonObject,out);
            } else {
                logArea.append("Unknown client type: " + clientType + "\n");
            }
        } catch (IOException e) {
            logArea.append("Error handling client: " + e.getMessage() + "\n");
        }
    }

    private void handlePredictionRequest(JSONObject jsonObject, DataOutputStream out) {
        String input = jsonObject.getString("input");
        String bookName = jsonObject.getString("bookName");

        // Aquí obtenemos las respuestas de los nodos
        JSONArray predictions = getPredictsForInput(input, bookName);

        // Envía la respuesta de vuelta al cliente modelo
        try {
            byte[] responseBytes = predictions.toString().getBytes(StandardCharsets.UTF_8);
            out.writeShort(responseBytes.length);
            out.write(responseBytes);
        } catch (IOException e) {
            logArea.append("Error sending response to model client: " + e.getMessage() + "\n");
        }
    }


    private JSONArray getPredictsForInput(String input, String bookName) {
        List<String> models = modelsMap.get(bookName);
        JSONArray predictions = new JSONArray();

        if (models != null) {
            for (int i = 0; i < models.size(); i++) {
                JSONObject requestJson = new JSONObject();
                requestJson.put("action", "predict");
                requestJson.put("model", models.get(i)); // Nombre del modelo
                requestJson.put("input", input); // Input para la predicción
                String nodeIP = nodeIPFields.get(i).getText();
                int nodePort = Integer.parseInt(nodePortFields.get(i).getText());
                String prediction = sendToNode(nodeIP,nodePort,requestJson); // Enviar parte del libro
                predictions.put(prediction);
            }
        }

        return predictions;
    }

    private String sendToNode(String nodeIP, int nodePort, JSONObject requestJson) {
        try (Socket socket = new Socket(nodeIP, nodePort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // Enviar JSON al nodo
            byte[] jsonDataBytes = requestJson.toString().getBytes(StandardCharsets.UTF_8);
            out.writeShort(jsonDataBytes.length);
            out.write(jsonDataBytes);

            // Leer la respuesta del nodo
            int jsonLength = in.readUnsignedShort();
            byte[] jsonBytes = new byte[jsonLength];
            in.readFully(jsonBytes);
            String jsonData = new String(jsonBytes, StandardCharsets.UTF_8);
            return jsonData; // Retorna la predicción o la respuesta del nodo
        } catch (IOException ex) {
            System.err.println("Error sending to node: " + ex.getMessage());
            return null;
        }
    }




    private static void addModel(Map<String, List<String>> modelsMap, String nameBook, String modelJson) {
        // Verificar si la clave ya existe
        if (!modelsMap.containsKey(nameBook)) {
            // Si no existe, crear una nueva lista
            modelsMap.put(nameBook, new ArrayList<>());
        }
        // Agregar el modelo a la lista correspondiente
        modelsMap.get(nameBook).add(modelJson);
    }


    private void manageBookReceived(DataInputStream in, String bookName) {
        // Ahora, recibir el contenido del archivo
        FileData fileData = receiveFileFromClient(in,bookName);

        if (fileData != null) {
            String fileContent = new String(fileData.getFileContent(), StandardCharsets.UTF_8);
            System.out.println("file content: " + fileContent);
            addBookToList(bookName); // Usar bookName que se pasa como parámetro

            // Dividir el contenido del libro como antes
            String[] parts = splitBookIntoParts(fileContent, expectedNodes);

            for (int i = 0; i < expectedNodes; i++) {
                String nodeIP = nodeIPFields.get(i).getText();
                int nodePort = Integer.parseInt(nodePortFields.get(i).getText());
                sendPartToNode(parts[i], nodeIP, nodePort, bookName); // Enviar parte del libro
            }
        }
    }

    private FileData receiveFileFromClient(DataInputStream in, String fileName) {
        try {
            // Leer la longitud del contenido del archivo
            int fileContentLength = in.readInt();  // Leer la longitud como un int
            System.out.println("Tamaño del archivo: " + fileContentLength);

            byte[] fileContentBytes = new byte[fileContentLength];
            in.readFully(fileContentBytes);  // Leer los bytes del contenido del archivo

            // Retornar los datos del archivo
            return new FileData(fileName, fileContentBytes);
        } catch (IOException e) {
            logArea.append("Error receiving file: " + e.getMessage() + "\n");
            return null;  // Indicar fallo
        }
    }



    private void addBookToList(String bookName) {
        BookEntry newBook = new BookEntry(bookName, false);
        receivedBooks.add(newBook);
        bookListModel.addElement(bookName + " - No trained");
    }

    private String[] splitBookIntoParts(String bookContent, int expectedNodes) {
        String[] paragraphs = bookContent.split("\n");
        String[] parts = new String[expectedNodes];

        for(int i=0; i< expectedNodes;i++){
            parts[i] = "";
        }

        for (int i = 0; i < paragraphs.length; i++) {
            parts[i % expectedNodes] += paragraphs[i] + "\n";
        }

        return parts;
    }


    private void sendPartToNode(String part, String nodeIP, int nodePort, String fileName) {
        try(Socket socket = new Socket(nodeIP, nodePort)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Crear el objeto JSON
            JSONObject json = new JSONObject();
            json.put("action","train");
            json.put("bookName", fileName);
            json.put("partOfBook", part);

            // Convertir JSON a una cadena y luego a bytes UTF-8
            String jsonString = json.toString();
            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);

            // Enviar la longitud de los datos (4 bytes para evitar problemas con tamaños grandes)
            out.writeInt(jsonBytes.length);

            // Enviar los datos del JSON como bytes
            out.write(jsonBytes);

            logArea.append("Sent part to node " + nodeIP + ":" + nodePort + "\n");
        } catch (IOException e) {
            logArea.append("Error sending part to node: " + e.getMessage() + "\n");
        }
    }



    private void updateTrainedBookStatus(String bookName) {
        for (int i = 0; i < receivedBooks.size(); i++) {
            if (receivedBooks.get(i).getFileName().equals(bookName)) {
                receivedBooks.get(i).setTrained(true);
                bookListModel.set(i, bookName + " - Trained");
            }
        }
    }

    private class FileData {
        private String fileName;
        private byte[] fileContent;

        public FileData(String fileName, byte[] fileContent) {
            this.fileName = fileName;
            this.fileContent = fileContent;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getFileContent() {
            return fileContent;
        }
    }

    private static class BookEntry {
        private final String fileName;
        private boolean trained;

        public BookEntry(String fileName, boolean trained) {
            this.fileName = fileName;
            this.trained = trained;
        }

        public String getFileName() {
            return fileName;
        }

        public boolean isTrained() {
            return trained;
        }

        public void setTrained(boolean trained) {
            this.trained = trained;
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServerMaster::new);
    }

}

