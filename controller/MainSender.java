package controller;

import javax.swing.*;

import java.net.InetAddress;
import sender.*;
import view.Sender;
import common.*;

public class MainSender {
    private ScreenCapturer capturer;
    private Sender senderWindow;
    private StreamServer server;  // ← StreamServer ajouté

    public MainSender() {
        // Lancement de l'affichage
        SwingUtilities.invokeLater(() -> {
            senderWindow = new Sender(this);
            senderWindow.setVisible(true);
        });

        // Lancement de la capture en parallele avec l'affichage
        new Thread(() -> {
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                ProtocolConfig.SERVER_HOST = localHost.getHostAddress();
                
                Thread.sleep(500);
                
                System.out.println("=== SENDER - Serveur de Streaming ===");
                System.out.println("Configuration : " + ProtocolConfig.SCREEN_WIDTH + "x" + ProtocolConfig.SCREEN_HEIGHT + " @ " + ProtocolConfig.TARGET_FPS + " FPS");
                System.out.println("Port : " + ProtocolConfig.SERVER_PORT);
                System.out.println();
                
                // Demarrage du serveur
                server = new StreamServer();
                server.start();
                
                // Création du capture
                capturer = new ScreenCapturer();
                capturer.setCaptureAreaFromConfig();
                capturer.setPreviewEnabled(true);
                
                capturer.setFrameCallback((jpegData, previewImage, frameNumber) -> {
                    try {
                        // Envoyer Frame au client
                        if (server != null && server.isRunning() && previewImage != null) {
                            server.sendFrame(previewImage, frameNumber); // Passer BufferedImage
                        }
                        
                        // update l'affichage
                        if (previewImage != null && senderWindow != null) {
                            SwingUtilities.invokeLater(() -> {
                                senderWindow.updatePreview(previewImage);
                            });
                        }
                        
                        // Stats dans la console
                        if (frameNumber % 30 == 0) {
                            System.out.println("Frame #" + frameNumber + " - Taille : " + (jpegData.length / 1024) + " KB - Clients : " + server.getClientCount());
                        }
                        
                    } catch (Exception e) {
                        System.err.println("❌ Erreur envoi frame : " + e.getMessage());
                    }
                });
                
                // Démarrage de la capture
                capturer.startCapture();
                
                System.out.println("La capture commence !");
                
            } catch (Exception e) {
                System.err.println("❌ Erreur : " + e.getMessage());
                e.printStackTrace();
            }
        }, "MainSender-Init").start();
    }
    
    /*Méthode pour arrêter la capture*/
    public void stopStreaming() {
        if (capturer != null && capturer.isRunning()) {
            capturer.stopCapture();
            System.out.println("Capture arrêtée !");
            
            // Arrêt du serveur
            if (server != null) {
                server.stop();
            }
            
            // Réinitialisation de l'affichage
            if (senderWindow != null) {
                SwingUtilities.invokeLater(() -> {
                    senderWindow.resetPreview();
                });
            }
        }
    }
    
    public StreamServer getServer() {
        return server;
    }
}