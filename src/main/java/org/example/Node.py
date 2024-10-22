import numpy as np
import re
import tkinter as tk
import socket
import json
import os
import threading  # Importar el módulo threading
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'
from keras.models import model_from_json
from tensorflow.keras.preprocessing.text import Tokenizer
from tensorflow.keras.preprocessing.sequence import pad_sequences
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Embedding, LSTM, Dense
from tensorflow.keras.utils import to_categorical
from tensorflow.keras.optimizers import Adam

class Node:
    def __init__(self, master):
        self.master = master
        self.master.title("Node Configuration")

        self.port_label = tk.Label(master, text="Enter Node Port: ")
        self.port_label.pack()

        self.port_entry = tk.Entry(master)
        self.port_entry.pack()

        self.start_button = tk.Button(master, text="Start Node", command=self.start_node)
        self.start_button.pack()

        self.status_label = tk.Label(master, text="", fg="green")
        self.status_label.pack()


        self.tokenizer = Tokenizer()

    def start_node(self):
        port = int(self.port_entry.get())
        self.start_button.config(state=tk.DISABLED, text="Connecting...")
        self.status_label.config(text=f"Starting node on port {port}...")
        self.master.update()

        # Iniciar un hilo para ejecutar el nodo
        threading.Thread(target=self.run_node, args=(port,), daemon=True).start()

    def run_node(self, port):
        print(f"Node started on port: {port}")
        # Crear el socket del servidor
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  # Permitir reutilizar la dirección
            s.bind(('0.0.0.0', port))
            s.listen(1)  # Escuchar hasta 1 conexión
            print(f"Node listening on port {port}...")

            while True:
                conn, addr = s.accept()
                print(f"Connected by {addr}")
                self.receive_file_part(conn)
                conn.close()  # Cerrar la conexión

    def receive_file_part(self, conn):
        try:
            length = int.from_bytes(conn.recv(4), byteorder='big')
            received_data = conn.recv(length)
            json_data = received_data.decode('utf-8')

            print("Received JSON:", json_data)

            data = json.loads(json_data)
            print(data)

            if data["action"] == "predict":
                model_json = data["model"]
                input_text = data["input"]
                model = model_from_json(model_json)
                model.compile(optimizer='adam', loss='categorical_crossentropy', metrics=['accuracy'])
                prediction = predecir_palabras(model, self.tokenizer, input_text, 1)
                print(prediction)

                data_to_send = {
                    "action": "result",
                    "prediction": prediction,
                }
                json_data = json.dumps(data_to_send)

                json_data_bytes = json_data.encode('utf-8')
                conn.send(self.short_to_bytes(len(json_data_bytes)))
                conn.sendall(json_data_bytes)
                print("Sent prediction to the master server.")


            elif data["action"] == "train":
                print(f"Book Name: {data['bookName']}")
                print(f"Part of Book: {data['partOfBook']}")
                self.train_model(data['bookName'], data['partOfBook'])

        except ConnectionAbortedError as ex:
            print(f"Connection aborted: {ex}")
            # Optionally, you could attempt to reconnect or handle this gracefully
        except Exception as ex:
            print(f"An error occurred: {ex}")


    def train_model(self, book_name, file_part):
        # Preprocesar el texto
        file_part = self.preprocesar_texto(file_part)
        self.tokenizer.fit_on_texts([file_part])
        # Tokenizar el texto
        self.tokenizer.fit_on_texts([file_part])
        total_words = len(self.tokenizer.word_index) + 1

        input_sequences = []
        for line in file_part.split('\n'):
            token_list = self.tokenizer.texts_to_sequences([line])[0]
            for i in range(1, len(token_list)):
                n_gram_sequence = token_list[:i+1]
                input_sequences.append(n_gram_sequence)

        max_sequence_len = max([len(x) for x in input_sequences])
        input_sequences = np.array(pad_sequences(input_sequences, maxlen=max_sequence_len, padding='pre'))

        X = input_sequences[:,:-1]
        y = input_sequences[:,-1]
        y = to_categorical(y, num_classes=total_words)

        # Definir el modelo
        model = Sequential()
        model.add(Embedding(total_words, 10, input_length=max_sequence_len-1))
        model.add(LSTM(100))
        model.add(Dense(total_words, activation='softmax'))

        model.compile(loss='categorical_crossentropy', optimizer=Adam())

        # Entrenamos el modelo
        model.fit(X, y, epochs=1, verbose=1)

        # Obtener la arquitectura del modelo
        model_json = model.to_json()  # Serializar la arquitectura del modelo a JSON

        # Enviar el modelo al servidor
        self.send_model_to_master(book_name, model_json)

    def send_model_to_master(self, book_name, model_json):
        # Connect to the master server to send the model
        master_ip = "127.0.0.1"  # Update this with the actual IP of the server
        master_port = 12345  # Update this with the correct port of the server

        # Create a JSON object to send
        data_to_send = {
            "clientType": "model",  # Specify the client type
            "name_of_book": book_name,
            "model_of_book": model_json
        }
        json_data = json.dumps(data_to_send)

        # Send the model as JSON
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((master_ip, master_port))

            # Send JSON length and then the JSON data
            json_data_bytes = json_data.encode('utf-8')
            s.send(self.short_to_bytes(len(json_data_bytes)))  # Send the length of the JSON data
            s.sendall(json_data_bytes)  # Send the actual JSON data

            print("Model and book name sent to the master server.")


    @staticmethod
    def short_to_bytes(value):
        """Converts an integer value to a 2-byte array."""
        return value.to_bytes(2, 'big')

    def preprocesar_texto(self, texto):
        texto = texto.lower()  # Convertir a minúsculas
        texto = re.sub(r'\W+', ' ', texto)  # Eliminar caracteres especiales
        return texto

# Función para predecir las siguientes palabras (no cambia)
def predecir_palabras(modelo, tokenizer, texto_inicial, num_palabras):
    predict = ''
    for _ in range(num_palabras):
        token_list = tokenizer.texts_to_sequences([texto_inicial])[0]
        token_list = pad_sequences([token_list], maxlen=50, padding='pre')
        predicted = np.argmax(modelo.predict(token_list), axis=-1)
        salida = ""

        for palabra, index in tokenizer.word_index.items():
            if index == predicted:
                salida = palabra
                break

        predict += ' ' + salida
    return predict

if __name__ == "__main__":
    root = tk.Tk()
    app = Node(root)
    root.mainloop()
