package sender;

import java.io.*;
import java.net.*;
import java.awt.image.BufferedImage;
import common.*;

public class ClientInfo {
        public InetAddress address;
        public int port;
        public String name;
        public Socket socket;
        public DataOutputStream outputStream;
        public long lastActivity;
        public float jpegQuality = ProtocolConfig.JPEG_QUALITY; // Qualité par défaut
        private ImageEncoder encoder; // Encodeur dédié pour ce client
        
        private int pendingSends = 0;
        private static final int MAX_PENDING = 3;  // Max 3 frames en attente
        
        public ClientInfo(Socket socket, String name) throws IOException {
            this.socket = socket;
            this.address = socket.getInetAddress();
            this.port = socket.getPort();
            this.name = name;
            this.outputStream = new DataOutputStream(socket.getOutputStream());
            this.lastActivity = System.currentTimeMillis();
            this.encoder = new ImageEncoder(jpegQuality); // Encodeur propre au client
        
            // Démarrer un thread pour écouter les commandes du client
            startCommandListener();
        }
        
        /*Écoute les commandes du client*/
        private void startCommandListener() {
            new Thread(() -> {
                try {
                    byte[] buffer = new byte[256];
                    while (!socket.isClosed()) {
                        int len = socket.getInputStream().read(buffer);
                        if (len > 0) {
                            String command = new String(buffer, 0, len).trim();
                            handleCommand(command);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("connexion fermé");
                }
            }, "Client-Command-Listener-" + name).start();
        }
        
        /*Traite les commandes reçues du client*/
        private void handleCommand(String command) {
            if (command.startsWith("QUALITY:")) {
                try {
                    float newQuality = Float.parseFloat(command.substring(8));
                    if (newQuality >= 0.0f && newQuality <= 1.0f) {
                        jpegQuality = newQuality;
                        encoder.setQuality(newQuality);
                        System.out.println("Client " + name + " - Qualité changée : " + (newQuality * 100) + "%");
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Commande qualité invalide : " + command);
                }
            }
        }
        
        /*Encode une frame avec la qualité spécifique du client*/
        public byte[] encodeFrame(BufferedImage image) throws Exception {
            return encoder.encode(image);
        }

        //Vérifie si on peut envoyer
        public boolean canSend() {
            return pendingSends < MAX_PENDING;
        }
        
        public void incrementPending() {
            pendingSends++;
        }
        
        public void decrementPending() {
            if (pendingSends > 0) pendingSends--;
        }
        
        @Override
        public String toString() {
            return name + " (" + address.getHostAddress() + ":" + port + ")";
        }
        
        public void close() throws IOException {
            if (encoder != null) encoder.dispose();
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        }
    }