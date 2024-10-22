package org.example;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.json.JSONObject;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Node {
    private JFrame frame;
    private JTextField portField;
    private JLabel statusLabel;
    private int port;
    private MultiLayerNetwork model;

    public Node() {
        // Inicialización de la interfaz gráfica (GUI)
        frame = new JFrame("Node Configuration");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 150);
        frame.setLayout(new FlowLayout());

        JLabel portLabel = new JLabel("Enter Node Port: ");
        frame.add(portLabel);

        portField = new JTextField(10);
        frame.add(portField);

        JButton startButton = new JButton("Start Node");
        frame.add(startButton);
        startButton.addActionListener(e -> startNode());

        statusLabel = new JLabel("");
        frame.add(statusLabel);

        frame.setVisible(true);

        // Configuración inicial del modelo
        initializeModel();
    }

    private void startNode() {
        try {
            port = Integer.parseInt(portField.getText());
            statusLabel.setText("Starting node on port " + port + "...");

            // Ejecutar el nodo en un nuevo hilo
            new Thread(this::runNode).start();
        } catch (NumberFormatException e) {
            statusLabel.setText("Invalid port number.");
        }
    }

    private void runNode() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            statusLabel.setText("Node listening on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            int length = in.readInt(); // Leer la longitud del JSON
            byte[] dataBytes = new byte[length];
            in.readFully(dataBytes); // Leer los datos JSON

            String receivedData = new String(dataBytes, StandardCharsets.UTF_8);
            System.out.println("Received JSON: " + receivedData);

            JSONObject jsonData = new JSONObject(receivedData);

            String action = jsonData.getString("action");
            if (action.equals("predict")) {
                String modelJson = jsonData.getString("model");
                String inputText = jsonData.getString("input");

                String prediction = predictWords(inputText);

                // Enviar el resultado al servidor
                JSONObject resultData = new JSONObject();
                resultData.put("action", "result");
                resultData.put("prediction", prediction);

                byte[] jsonDataBytes = resultData.toString().getBytes(StandardCharsets.UTF_8);
                out.writeInt(jsonDataBytes.length); // Enviar longitud del JSON
                out.write(jsonDataBytes); // Enviar JSON
                out.flush();
                System.out.println("Sent prediction to the master server.");
            } else if (action.equals("train")) {
                String bookName = jsonData.getString("bookName");
                String partOfBook = jsonData.getString("partOfBook");

                trainModel(bookName, partOfBook);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeModel() {
        // Inicialización del modelo LSTM
        int inputSize = 100;  // Ajustar el tamaño de las características de entrada
        int lstmLayerSize = 200;
        int outputSize = 100;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .weightInit(WeightInit.XAVIER)
                .list()
                .layer(0, new LSTM.Builder()
                        .nIn(inputSize)
                        .nOut(lstmLayerSize)
                        .activation(Activation.TANH)
                        .build())
                .layer(1, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .nIn(lstmLayerSize)
                        .nOut(outputSize)
                        .activation(Activation.SOFTMAX)
                        .build())
                .build();

        model = new MultiLayerNetwork(conf);
        model.init();
    }

    private void trainModel(String bookName, String partOfBook) {
        // Preprocesar el texto (convertir a minúsculas y eliminar caracteres especiales)
        String processedText = preprocesarTexto(partOfBook);

        // Suponiendo que tenemos las características de entrada y etiquetas listas
        INDArray features = preprocessText(processedText);
        INDArray labels = createLabels(processedText);

        // Entrenar el modelo
        for (int i = 0; i < 10; i++) {
            model.fit(features, labels);
        }

        System.out.println("Model trained for book: " + bookName);
        sendModelToMaster(bookName, "trained_model_json_here");
    }

    private void sendModelToMaster(String bookName, String modelJson) {
        try (Socket socket = new Socket("127.0.0.1", 12345); // IP y puerto del servidor maestro
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            JSONObject dataToSend = new JSONObject();
            dataToSend.put("clientType", "model");
            dataToSend.put("name_of_book", bookName);
            dataToSend.put("model_of_book", modelJson);

            byte[] jsonDataBytes = dataToSend.toString().getBytes(StandardCharsets.UTF_8);
            out.writeInt(jsonDataBytes.length); // Enviar longitud del JSON
            out.write(jsonDataBytes); // Enviar JSON
            out.flush();

            System.out.println("Model and book name sent to the master server.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String predictWords(String inputText) {
        // Ejemplo de predicción utilizando el modelo LSTM
        INDArray inputFeatures = preprocessText(inputText);
        INDArray predictions = model.output(inputFeatures);
        return predictions.toString();
    }

    private String preprocesarTexto(String texto) {
        // Convertir a minúsculas y eliminar caracteres especiales
        return texto.toLowerCase().replaceAll("[^a-zA-Z ]", "");
    }

    private INDArray preprocessText(String inputText) {
        // Aquí deberías convertir el texto en vectores de características
        return Nd4j.zeros(1, 100);  // Placeholder de ejemplo
    }

    private INDArray createLabels(String outputText) {
        // Aquí deberías convertir el texto de salida en etiquetas
        return Nd4j.zeros(1, 100);  // Placeholder de ejemplo
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Node::new);
    }
}
