package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModelClient extends JFrame {
    private JTextField serverIpField;
    private JTextField serverPortField;
    private JTextField inputField;
    private JComboBox<String> modelComboBox;
    private JButton predictButton;
    private JButton showModelsButton;
    private JTextArea resultArea;

    private String serverIp;
    private String serverPort;

    public ModelClient() {
        setTitle("Model Client");
        setSize(400, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Campos de entrada
        serverIpField = new JTextField("127.0.0.1");
        serverPortField = new JTextField("12345");
        inputField = new JTextField();
        modelComboBox = new JComboBox<>();
        predictButton = new JButton("Predict");
        showModelsButton = new JButton("Show Trained Books");
        resultArea = new JTextArea();
        resultArea.setEditable(false);

        // Panel de entrada
        JPanel inputPanel = new JPanel(new GridLayout(6, 1));
        inputPanel.add(new JLabel("Server IP:"));
        inputPanel.add(serverIpField);
        inputPanel.add(new JLabel("Server Port:"));
        inputPanel.add(serverPortField);
        inputPanel.add(new JLabel("Input:"));
        inputPanel.add(inputField);
        inputPanel.add(modelComboBox);
        inputPanel.add(showModelsButton);
        inputPanel.add(predictButton);

        add(inputPanel, BorderLayout.NORTH);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        predictButton.addActionListener(this::sendInputToServer);
        showModelsButton.addActionListener(this::loadTrainedBooks);

        setVisible(true);
    }

    private void loadTrainedBooks(ActionEvent e) {
        serverIp = serverIpField.getText();
        serverPort = serverPortField.getText();
        modelComboBox.removeAllItems(); // Limpia el JComboBox

        // Crear el JSON para enviar al servidor
        JSONObject requestJson = new JSONObject();
        requestJson.put("clientType", "get_trained_books");

        try (Socket socket = new Socket(serverIp, Integer.parseInt(serverPort));
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // Enviar el JSON
            byte[] jsonDataBytes = requestJson.toString().getBytes(StandardCharsets.UTF_8);
            out.writeShort(jsonDataBytes.length); // Enviar longitud del JSON
            out.write(jsonDataBytes); // Enviar el JSON

            // Leer la longitud de la respuesta
            int jsonLength = in.readUnsignedShort();
            byte[] jsonBytes = new byte[jsonLength];
            in.readFully(jsonBytes);  // Leer bytes del JSON
            String jsonResponse = new String(jsonBytes, StandardCharsets.UTF_8);  // Convertir a String

            // Parsear el JSON de respuesta
            JSONArray trainedBooks = new JSONArray(jsonResponse);
            for (int i = 0; i < trainedBooks.length(); i++) {
                String bookName = trainedBooks.getString(i);
                modelComboBox.addItem(bookName); // Agregar al JComboBox
            }
            out.close();
            in.close();
            socket.close();
        } catch (NumberFormatException ex) {
            resultArea.append("Invalid port number: " + ex.getMessage() + "\n");
        } catch (IOException ex) {
            resultArea.append("Error loading trained books: " + ex.getMessage() + "\n");
        }
    }

    private void sendInputToServer(ActionEvent e) {
        String ip = serverIpField.getText();
        int port = Integer.parseInt(serverPortField.getText());
        String selectedModel = (String) modelComboBox.getSelectedItem(); // Obtener modelo seleccionado
        String input = inputField.getText();
        System.out.println(selectedModel);
        System.out.println(input);

        // Crear el JSON para enviar al servidor
        JSONObject requestJson = new JSONObject();
        requestJson.put("clientType", "predict");
        requestJson.put("input", input);
        requestJson.put("bookName", selectedModel); // Nombre del libro seleccionado

        try (Socket socket = new Socket(ip, port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // Convertir el JSON a bytes y enviar
            byte[] jsonDataBytes = requestJson.toString().getBytes(StandardCharsets.UTF_8);
            out.writeShort(jsonDataBytes.length); // Enviar longitud del JSON
            out.write(jsonDataBytes); // Enviar el JSON

            // Leer la longitud de la respuesta
            int jsonLength = in.readUnsignedShort();
            byte[] jsonBytes = new byte[jsonLength];
            in.readFully(jsonBytes); // Leer bytes de la respuesta

            String jsonResponse = new String(jsonBytes, StandardCharsets.UTF_8); // Convertir a String
            JSONArray jsonArray = new JSONArray(jsonResponse);
            String prediction_string = "";
            for (int i = 0; i < jsonArray.length(); i++) {
                // Obtener cada objeto JSON en forma de cadena
                String jsonString = jsonArray.getString(i);

                // Convertir la cadena JSON en un JSONObject
                JSONObject jsonObject = new JSONObject(jsonString);

                // Extraer el valor de "prediction"
                String prediction = jsonObject.getString("prediction");
                prediction_string += prediction + "\n";
                // Imprimir la predicción
                System.out.println("Prediction: " + prediction);
            }
            // Mostrar el resultado en el área de texto
            resultArea.setText(prediction_string);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error to send: " + ex.getMessage() + "\n");
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(ModelClient::new);
    }
}
