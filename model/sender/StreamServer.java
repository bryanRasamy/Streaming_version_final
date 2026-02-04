package sender;

import common.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.awt.image.*;

public class StreamServer {
    private ServerSocket serverSocket;
    private List<ClientInfo> clients;
    private boolean isRunning = false;
    private Thread listenerThread;  // Thread pour accepter les connexions
    private ExecutorService sendExecutor;  // Pour envoyer aux clients en parallèle
    private Thread discoveryThread; //Thread pour la decouverte multicast
    
    // Statistiques
    private long totalBytesSent = 0;
    private long totalFramesSent = 0;
    private long startTime = 0;
    
    public StreamServer() throws IOException {
        this(ProtocolConfig.SERVER_PORT);
    }
    
    public StreamServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        clients = new CopyOnWriteArrayList<>();  // Thread-safe pour ajout/suppression
        sendExecutor = Executors.newCachedThreadPool();  // Pour envoi parallèle
        
        System.out.println("StreamServer TCP créé sur le port " + port);
    }
    
    /*Démarre le serveur*/
    public void start() {
        if (isRunning) {
            System.out.println("Serveur déjà démarré !");
            return;
        }
        
        isRunning = true;
        startTime = System.currentTimeMillis();
        
        // Démarrer l'écoute des clients
        startClientListener();
        startDiscoveryResponder();
        
        System.out.println("StreamServer TCP démarré !");
        System.out.println("En attente de clients...");
    }
    
    /*Démarre un thread pour accepter les connexions TCP des clients*/
    private void startClientListener() {
        listenerThread = new Thread(() -> {
            while (isRunning) {
                try {
                    // Accepter une nouvelle connexion
                    Socket clientSocket = serverSocket.accept();
                    
                    // Désactivation du Nagle pour réduire latence
                    clientSocket.setTcpNoDelay(true);
                    
                    // Augmenter les buffers
                    clientSocket.setSendBufferSize(ProtocolConfig.TCP_SEND_BUFFER_SIZE);  // 64KB
                    
                    // Lire le message HELLO
                    byte[] buffer = new byte[1024];
                    int len = clientSocket.getInputStream().read(buffer);
                    String message = new String(buffer, 0, len);
                    
                    String clientName = "Client-Anonyme";
                    if (message.startsWith("HELLO:")) {
                        clientName = message.substring(6).trim();
                    }
                    
                    // Ajouter le client
                    ClientInfo client = new ClientInfo(clientSocket, clientName);
                    addClient(client);
                    
                    System.out.println("Client connecté : " + client);
                    
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Erreur accept connexion : " + e.getMessage());
                    }
                }
            }
        }, "TCP-Listener-Thread");
        listenerThread.start();
    }

    /*Ajouter le serveur au groupe multiccast*/
    private void startDiscoveryResponder() {
        discoveryThread = new Thread(() -> {
            MulticastSocket socket = null;
            try {
                socket = new MulticastSocket(ProtocolConfig.DISCOVERY_PORT);
                InetAddress group = InetAddress.getByName(ProtocolConfig.MULTICAST_GROUP);
                
                // Trouver une interface PHYSIQUE IPv4
                NetworkInterface networkInterface = findPhysicalIPv4Interface();
                
                if (networkInterface == null) {
                    System.err.println("Impossible de démarrer la découverte : aucune interface physique IPv4");
                    return;
                }
                
                System.out.println("Interface multicast : " + networkInterface.getName() + " (" + networkInterface.getDisplayName() + ")");
                
                // Rejoindre le groupe
                InetSocketAddress groupAddress = new InetSocketAddress(group, ProtocolConfig.DISCOVERY_PORT);
                socket.joinGroup(groupAddress, networkInterface);
                
                System.out.println("Serveur visible sur multicast " + ProtocolConfig.MULTICAST_GROUP);
                
                byte[] buffer = new byte[256];
                
                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    String request = new String(packet.getData(), 0, packet.getLength());
                    
                    if (request.equals("DISCOVER_STREAMER")) {
                        // Utiliser l'IP de l'interface physique
                        String myIP = getIPFromInterface(networkInterface);
                        System.out.println("📡 Requête découverte reçue, envoi IP : " + myIP);
                        
                        byte[] responseData = myIP.getBytes();
                        
                        DatagramPacket responsePacket = new DatagramPacket(
                            responseData, responseData.length,
                            packet.getAddress(), packet.getPort()
                        );
                        
                        DatagramSocket sendSocket = new DatagramSocket();
                        sendSocket.send(responsePacket);
                        sendSocket.close();
                    }
                }
                
                socket.leaveGroup(groupAddress, networkInterface);
                socket.close();
                
            } catch (Exception e) {
                if (isRunning) {
                    e.printStackTrace();
                }
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        }, "Discovery-Responder");
        discoveryThread.start();
    }

    /*Trouve une interface réseau physique IPv4 (WiFi/Ethernet) pour le multicast*/
    private NetworkInterface findPhysicalIPv4Interface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            
            String name = ni.getName().toLowerCase();
            String displayName = ni.getDisplayName().toLowerCase();
            
            // IGNORER docker, veth, virtual, loopback
            if (name.contains("docker") || name.contains("veth") || name.contains("br-") || name.contains("vboxnet") || displayName.contains("virtual") || displayName.contains("loopback")) {
                continue;
            }
            
            if (!ni.isUp() || !ni.supportsMulticast()) {
                continue;
            }
            
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                    return ni;
                }
            }
        }
        
        return null;
    }

    private String getIPFromInterface(NetworkInterface ni) {
        Enumeration<InetAddress> addresses = ni.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                return addr.getHostAddress();
            }
        }
        return "unknown";
    }
    
    /* Ajoute un client à la liste*/
    private void addClient(ClientInfo client) {
        // Vérifier si déjà connecté (par IP:port)
        for (ClientInfo c : clients) {
            if (c.address.equals(client.address) && c.port == client.port) {
                System.out.println("Client déjà connecté : " + client);
                try {
                    client.close();
                } catch (IOException e) {}
                return;
            }
        }
        
        clients.add(client);
        
        // Thread pour gérer la déconnexion (heartbeats ou errors)
        new Thread(() -> {
            try {
                while (isRunning && !client.socket.isClosed()) {
                    Thread.sleep(5000);  // Check toutes les 5s
                    if (System.currentTimeMillis() - client.lastActivity > 30000) {  // 30s inactif
                        System.out.println("Client inactif : " + client);
                        removeClient(client);
                        break;
                    }
                }
            } catch (InterruptedException e) {}
        }).start();
    }
    
    /*Retire un client*/
    private void removeClient(ClientInfo client) {
        try {
            client.close();
        } catch (IOException e) {
            System.err.println("Erreur fermeture client : " + e.getMessage());
        }
        clients.remove(client);
        System.out.println("Client déconnecté : " + client);
    }
    
    /*Arrête le serveur et ferme toutes les connexions*/
    public void stop() {
        isRunning = false;
        
        // Fermer tous les clients
        for (ClientInfo client : new ArrayList<>(clients)) {
            removeClient(client);
        }
        
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {}
        
        if (listenerThread != null) {
            listenerThread.interrupt();
        }

        if (discoveryThread != null) {
            discoveryThread.interrupt();
        }
        
        sendExecutor.shutdown();
        
        printStats();
        System.out.println("StreamServer arrêté !");
    }
    
    /*Envoie une frame JPEG à tous les clients connectés*/
    public void sendFrame(BufferedImage rawImage, int frameNumber) throws IOException {
        if (clients.isEmpty()) {
            return;
        }
        
        List<ClientInfo> disconnected = new ArrayList<>();
        int skippedClients = 0;
        
        // Envoyer à chaque client avec SA qualité
        for (ClientInfo client : clients) {
            if (!client.canSend()) {
                skippedClients++;
                System.out.println("Client " + client.name + " surchargé, frame #" + frameNumber + " ignorée");
                continue;
            }
            
            client.incrementPending();
            
            sendExecutor.submit(() -> {
                try {
                    // Encoder avec la qualité du client
                    byte[] frameData = client.encodeFrame(rawImage);
                    
                    // Préparer le paquet
                    ByteBuffer packet = ByteBuffer.allocate(8 + frameData.length);
                    packet.putInt(frameNumber);
                    packet.putInt(frameData.length);
                    packet.put(frameData);
                    byte[] fullPacket = packet.array();
                    
                    synchronized (client.outputStream) {
                        client.outputStream.write(fullPacket);
                        client.outputStream.flush();
                    }
                    
                    client.decrementPending();
                    client.lastActivity = System.currentTimeMillis();
                    
                } catch (Exception e) {
                    System.err.println("Erreur envoi à " + client + " : " + e.getMessage());
                    synchronized (disconnected) {
                        disconnected.add(client);
                    }
                }
            });
        }
        
        // Nettoyer les clients déconnectés
        for (ClientInfo client : disconnected) {
            removeClient(client);
        }
        
        totalFramesSent++;
    }
    
    /*Diffuse un message texte à tous les clients*/
    public void broadcast(String message) throws IOException {
        byte[] data = message.getBytes();
        
        for (ClientInfo client : clients) {
            try {
                client.outputStream.writeInt(data.length);
                client.outputStream.write(data);
                client.outputStream.flush();
            } catch (IOException e) {
                removeClient(client);
            }
        }
    }
    
    /*Retourne la liste des clients connectés*/
    public List<ClientInfo> getClients() {
        return new ArrayList<>(clients);
    }
    
    /*Retourne le nombre de clients connectés*/
    public int getClientCount() {
        return clients.size();
    }
    
    /*Calcule le débit actuel en KB/s*/
    public double getCurrentBitrateKBps() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed == 0) {
            return 0.0;
        }
        return (totalBytesSent / 1024.0) / (elapsed / 1000.0);
    }
    
    /*Calcule le FPS moyen d'envoi*/
    public double getAverageFPS() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed == 0) {
            return 0.0;
        }
        return (totalFramesSent * 1000.0) / elapsed;
    }
    
    /*Affiche les statistiques du serveur*/
    public void printStats() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println("=== Statistiques StreamServer TCP ===");
        System.out.println("Durée : " + elapsed + " secondes");
        System.out.println("Frames envoyées : " + totalFramesSent);
        System.out.println("Données envoyées : " + (totalBytesSent / 1024 / 1024) + " MB");
        System.out.println("Débit moyen : " + String.format("%.2f", getCurrentBitrateKBps()) + " KB/s");
        System.out.println("FPS moyen : " + String.format("%.2f", getAverageFPS()));
        System.out.println("Clients actifs : " + clients.size());
        System.out.println("================================");
    }
    
    /*Réinitialise les statistiques*/
    public void resetStats() {
        totalBytesSent = 0;
        totalFramesSent = 0;
        startTime = System.currentTimeMillis();
    }
    
    /*Vérifie si le serveur tourne*/
    public boolean isRunning() {
        return isRunning;
    }
}