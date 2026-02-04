package receiver;

import common.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class StreamClient {
    private Socket socket;
    private DataInputStream inputStream;
    private Thread receiverThread;
    private volatile boolean isRunning = false;
    private FrameCallback callback;
    
    private long totalBytesReceived = 0;
    private long totalFramesReceived = 0;
    private long startTime = 0;
    private int lastFrameNumber = -1;
    private int droppedFrames = 0;
    
    /*Interface callback pour recevoir les frames décodées*/
    public interface FrameCallback {
        void onFrameReceived(BufferedImage frame, int frameNumber);
        void onError(String error);
    }
    
    public StreamClient() throws IOException {
        this(ProtocolConfig.SERVER_PORT + 1); // Port différent
    }
    
    public StreamClient(int port) throws IOException {
        System.out.println("StreamClient TCP créé (se connectera au port serveur " + ProtocolConfig.SERVER_PORT + ")");
    }
    
    /*Définit le callback pour recevoir les frames*/
    public void setFrameCallback(FrameCallback callback) {
        this.callback = callback;
    }
    
    /*Se connecte au serveur TCP et envoie HELLO*/
    public void connect(String host) throws IOException {
        if (isRunning) {
            throw new IllegalStateException("Déjà connecté !");
        }
        
        socket = new Socket(host, ProtocolConfig.SERVER_PORT);
        
        // Désactivation du Nagle pour réduire la latence
        socket.setTcpNoDelay(true);
        
        // Augmenter le buffer de réception
        socket.setReceiveBufferSize(ProtocolConfig.TCP_RECEIVE_BUFFER_SIZE);  // 64KB
        
        inputStream = new DataInputStream(socket.getInputStream());
        
        // Envoyer HELLO (pour compatibilité)
        String hello = "HELLO:" + InetAddress.getLocalHost().getHostName();
        socket.getOutputStream().write(hello.getBytes());
        socket.getOutputStream().flush();
        
        System.out.println("Connecté au serveur TCP : " + host + ":" + ProtocolConfig.SERVER_PORT);
    }

    /*Recuperer la liste des ip de serveurs disponible*/
    public static List<String> discoverStreamers(int timeoutMs) {
        List<String> streamerIPs = new ArrayList<>();
        
        try {
            System.out.println("Démarrage de la découverte multicast...");
            
            // UN SEUL socket pour envoyer ET recevoir
            DatagramSocket socket = new DatagramSocket();
            int localPort = socket.getLocalPort();
            
            socket.setSoTimeout(timeoutMs);
            
            InetAddress group = InetAddress.getByName(ProtocolConfig.MULTICAST_GROUP);
            
            // Envoyer requête multicast
            String message = "DISCOVER_STREAMER";
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                buffer, buffer.length, group, ProtocolConfig.DISCOVERY_PORT
            );
            
            System.out.println("Envoi requête multicast...");
            socket.send(packet);
            
            System.out.println("Attente des réponses...");
            
            // Recevoir les réponses sur le MÊME socket
            byte[] receiveBuffer = new byte[256];
            
            try {
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(
                        receiveBuffer, receiveBuffer.length
                    );
                    socket.receive(receivePacket);
                    
                    String ip = new String(
                        receivePacket.getData(), 
                        0, 
                        receivePacket.getLength()
                    ).trim();
                    
                    System.out.println("Serveur trouvé : " + ip);
                    streamerIPs.add(ip);
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout atteint");
            }
            
            socket.close();
            
            System.out.println("Découverte terminée. Total : " + streamerIPs.size());
            
        } catch (Exception e) {
            System.err.println("Erreur découverte : " + e.getMessage());
            e.printStackTrace();
        }
        
        return streamerIPs;
    }

    /*Envoie une requête de changement de qualité au serveur*/
    public void requestQualityChange(float quality) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IllegalStateException("Non connecté !");
        }
        
        // Valider la qualité
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("Qualité doit être entre 0.0 et 1.0");
        }
        
        // Envoyer commande 
        String command = "QUALITY:" + quality + "\n";
        socket.getOutputStream().write(command.getBytes());
        socket.getOutputStream().flush();
        
        System.out.println("Requête changement qualité envoyée : " + (quality * 100) + "%");
    }

    /*Démarre la réception des frames*/
    public void start() {
        if (isRunning) {
            System.out.println("Réception déjà démarrée !");
            return;
        }
        
        if (socket == null || socket.isClosed()) {
            throw new IllegalStateException("Non connecté ! Appelez connect() d'abord.");
        }
        
        isRunning = true;
        startTime = System.currentTimeMillis();
        
        receiverThread = new Thread(() -> {
            while (isRunning) {
                try {
                    // Lire header : [FRAME_NUM:4] [DATA_SIZE:4]
                    int frameNumber = inputStream.readInt();
                    int dataSize = inputStream.readInt();
                    
                    // VALIDATION CRITIQUE : vérifier que la taille est raisonnable
                    if (dataSize < 0 || dataSize > 10 * 1024 * 1024) {  // Max 10MB
                        System.err.println("CORRUPTION : Taille invalide " + dataSize + " pour frame #" + frameNumber);
                        System.err.println("Stream TCP corrompu, arrêt...");
                        if (callback != null) {
                            callback.onError("Corruption du stream TCP détectée");
                        }
                        stop();
                        break;
                    }
                    
                    // Lire les données JPEG
                    byte[] frameData = new byte[dataSize];
                    inputStream.readFully(frameData);
                    
                    totalBytesReceived += 8 + dataSize;
                    
                    // Vérifier la continuité et ignorer frames anciennes
                    if (lastFrameNumber != -1 && frameNumber > lastFrameNumber + 1) {
                        droppedFrames += (frameNumber - lastFrameNumber - 1);
                        System.out.println("Frames sautées : " + (frameNumber - lastFrameNumber - 1));
                    } else if (lastFrameNumber != -1 && frameNumber < lastFrameNumber) {
                        System.out.println("Frame en retard ignorée : #" + frameNumber + " (actuel: #" + lastFrameNumber + ")");
                        continue;  // Ignorer cette frame
                    }

                    lastFrameNumber = frameNumber;
                    
                    // Décoder et callback
                    processCompleteFrame(frameData, frameNumber);
                    
                } catch (java.io.EOFException e) {
                    System.err.println("Connexion fermée par le serveur");
                    if (callback != null) {
                        callback.onError("Connexion fermée");
                    }
                    stop();
                    break;
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Erreur réception TCP : " + e.getMessage());
                        if (callback != null) {
                            callback.onError("Erreur réseau : " + e.getMessage());
                        }
                        stop();
                    }
                    break;
                }
            }
        }, "TCP-Receiver-Thread");
        receiverThread.start();
        
        System.out.println("Réception TCP démarrée !");
    }

    
    /*Arrête la réception et ferme la connexion*/
    public void stop() {
        isRunning = false;
        
        try {
            if (inputStream != null) inputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {}
        
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
        
        printStats();
        System.out.println("StreamClient TCP arrêté !");
    }
    
    /*Traite une frame complète (décodage et callback)*/
    private void processCompleteFrame(byte[] frameData, int frameNumber) {
        // Décoder l'image JPEG
        BufferedImage image = decodeImage(frameData);
        
        if (image != null && callback != null) {
            callback.onFrameReceived(image, frameNumber);
            totalFramesReceived++;
        }
    }
    
    /*Décode une image JPEG*/
    private BufferedImage decodeImage(byte[] jpegData) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(jpegData);
            return ImageIO.read(bais);
        } catch (IOException e) {
            System.err.println("Erreur décodage image : " + e.getMessage());
            return null;
        }
    }
    
    /*Calcule le FPS moyen de réception*/
    public double getAverageFPS() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed == 0) {
            return 0.0;
        }
        return (totalFramesReceived * 1000.0) / elapsed;
    }
    
    /*Calcule le débit moyen en KB/s*/
    public double getAverageBitrateKBps() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed == 0) {
            return 0.0;
        }
        return (totalBytesReceived / 1024.0) / (elapsed / 1000.0);
    }
    
    /*Affiche les statistiques*/
    public void printStats() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println("=== Statistiques StreamClient TCP ===");
        System.out.println("Durée : " + elapsed + " secondes");
        System.out.println("Frames reçues : " + totalFramesReceived);
        System.out.println("Frames perdues : " + droppedFrames);
        System.out.println("Données reçues : " + (totalBytesReceived / 1024 / 1024) + " MB");
        System.out.println("Débit moyen : " + String.format("%.2f", getAverageBitrateKBps()) + " KB/s");
        System.out.println("FPS moyen : " + String.format("%.2f", getAverageFPS()));
        System.out.println("=================================");
    }
    
    /*Vérifie si le client tourne*/
    public boolean isRunning() {
        return isRunning;
    }
}