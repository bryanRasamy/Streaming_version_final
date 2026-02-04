package controller;

import javax.swing.*;
import java.awt.image.BufferedImage;
import receiver.*;
import view.*;
import common.*;

public class MainReceiver {
    private StreamClient client;
    private Receiver receiverWindow;
    
    public MainReceiver() {
        // Lancement de l'affichage
        SwingUtilities.invokeLater(() -> {
            receiverWindow = new Receiver(this);
            receiverWindow.setVisible(true);
        });
        
        // Lancement du client
        new Thread(() -> {
            try {
                Thread.sleep(500);
                
                System.out.println("=== RECEIVER - Client de Streaming ===");
                System.out.println("Configuration : " + ProtocolConfig.SCREEN_WIDTH + "x" + ProtocolConfig.SCREEN_HEIGHT + " @ " + ProtocolConfig.TARGET_FPS + " FPS");
                System.out.println("Port : " + (ProtocolConfig.SERVER_PORT + 1));
                System.out.println();
                
                // Création du client
                client = new StreamClient();
                
                // Définir le callback pour recevoir les frames
                client.setFrameCallback(new StreamClient.FrameCallback() {
                    @Override
                    public void onFrameReceived(BufferedImage frame, int frameNumber) {
                        // Affichage dans la fenêtre
                        if (frame != null && receiverWindow != null) {
                            SwingUtilities.invokeLater(() -> {
                                receiverWindow.updateFrame(frame, frameNumber);
                            });
                        }
                        
                        // Stats dans la console (tous les 30 frames)
                        if (frameNumber % 30 == 0) {
                            System.out.println("Frame #" + frameNumber + " reçue");
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        System.err.println("Erreur : " + error);
                        SwingUtilities.invokeLater(() -> {
                            if (receiverWindow != null) {
                                receiverWindow.showError(error);
                            }
                        });
                    }
                });
                
                // Se connecter au serveur
                String serverHost = ProtocolConfig.SERVER_HOST;
                System.out.println("Connexion au serveur : " + serverHost);
                client.connect(serverHost);
                
                // Démarrer la réception
                client.start();
                
                System.out.println("Réception démarrée !");
                
            } catch (Exception e) {
                System.err.println("Erreur : " + e.getMessage());
                e.printStackTrace();
            }
        }, "MainReceiver-Init").start();
    }
    
    /*Arrête la réception*/
    public void stopReceiving() {
        if (client != null && client.isRunning()) {
            client.stop();
            System.out.println("Réception arrêtée !");
        }
    }
    
    /*Retourne le client (pour accéder aux stats)*/
    public StreamClient getClient() {
        return client;
    }
}