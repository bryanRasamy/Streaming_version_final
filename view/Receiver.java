package view;

import javax.swing.*;
import controller.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import common.*;

public class Receiver extends JFrame {
    private VideoPanel videoPanel;
    private JPanel receiverPanel;
    private MainReceiver mainReceiver;
    private JLabel statusLabel;
    
    public Receiver(MainReceiver mainReceiver) {
        this.mainReceiver = mainReceiver;
        
        setTitle("Logiciel de Streaming - Réception");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Panels
        videoPanel = new VideoPanel();
        receiverPanel = createReceiverPanel();

        add(videoPanel, BorderLayout.CENTER);
        add(receiverPanel, BorderLayout.EAST);
        
        // Ajouter un listener pour arrêter proprement à la fermeture
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (mainReceiver != null) {
                    mainReceiver.stopReceiving();
                }
            }
        });
    }
    
    /*Met à jour l'affichage avec une nouvelle frame reçue*/
    public void updateFrame(BufferedImage frame, int frameNumber) {
        if (videoPanel != null) {
            videoPanel.updateFrame(frame);
        }
        
        // Mettre à jour le statut
        if (statusLabel != null) {
            statusLabel.setText("Connecté - Frame #" + frameNumber);
        }
    }
    
    /*Affiche une erreur*/
    public void showError(String error) {
        if (statusLabel != null) {
            statusLabel.setText("Erreur : " + error);
        }
    }
    
    /*Crée le panel de contrôle du receiver*/
    private JPanel createReceiverPanel() {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(250, 0));
        panel.setLayout(new BorderLayout());
        panel.setBackground(new Color(45, 45, 45));

        // Header avec statut
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(35, 35, 35));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel title = new JLabel("Réception", JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        
        statusLabel = new JLabel("En attente...", JLabel.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        statusLabel.setForeground(Color.LIGHT_GRAY);
        
        headerPanel.add(title, BorderLayout.NORTH);
        headerPanel.add(statusLabel, BorderLayout.SOUTH);

        // Zone d'informations
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(new Color(55, 55, 55));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel serverInfo = new JLabel("Serveur : " + ProtocolConfig.SERVER_HOST);
        serverInfo.setForeground(Color.WHITE);
        serverInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel portInfo = new JLabel("Port : " + ProtocolConfig.SERVER_PORT);
        portInfo.setForeground(Color.WHITE);
        portInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        infoPanel.add(serverInfo);
        infoPanel.add(Box.createVerticalStrut(5));
        infoPanel.add(portInfo);

        // Panel des boutons
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayout(4, 1, 5, 5));
        controlPanel.setBackground(new Color(45, 45, 45));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JButton btnHauteresolution = new JButton("Haute résolution");
        JButton btnBasseresolution = new JButton("Basse résolution");
        JButton btnDisconnect = new JButton("Déconnecter");
        JButton btnBack = new JButton("Retour au menu");
        
        btnDisconnect.setFocusPainted(false);
        btnBack.setFocusPainted(false);
        
        // Actions
        btnHauteresolution.addActionListener(e -> highresolution());
        btnBasseresolution.addActionListener(e -> lowresolution());
        btnDisconnect.addActionListener(e -> disconnect());
        btnBack.addActionListener(e -> backToMenu());
        
        controlPanel.add(btnHauteresolution);
        controlPanel.add(btnBasseresolution);
        controlPanel.add(btnDisconnect);
        controlPanel.add(btnBack);

        // Assemblage
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(infoPanel, BorderLayout.CENTER);
        panel.add(controlPanel, BorderLayout.SOUTH);

        return panel;
    }
    
    /*Augmente la qualiter des frames*/
    private void highresolution(){
        if (mainReceiver != null && mainReceiver.getClient() != null) {
            try {
                mainReceiver.getClient().requestQualityChange(0.85f); 
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Erreur lors du changement de qualité : " + e.getMessage(), 
                    "Erreur", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /*Diminuer la qualiter des frames*/
    private void lowresolution(){
        if (mainReceiver != null && mainReceiver.getClient() != null) {
            try {
                mainReceiver.getClient().requestQualityChange(0.1f); 
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Erreur lors du changement de qualité : " + e.getMessage(), 
                    "Erreur", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /*Déconnecte du serveur*/
    private void disconnect() {
        int confirm = JOptionPane.showConfirmDialog(
            this, 
            "Se déconnecter du serveur ?",
            "Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            if (mainReceiver != null) {
                mainReceiver.stopReceiving();
            }
            
            // Réinitialiser l'affichage
            if (videoPanel != null) {
                videoPanel.reset();
            }
            
            if (statusLabel != null) {
                statusLabel.setText("Déconnecté");
            }
            
            JOptionPane.showMessageDialog(this, "Déconnecté du serveur !");
        }
    }
    
    /*Retour au menu principal*/
    private void backToMenu() {
        int confirm = JOptionPane.showConfirmDialog(
            this, 
            "Retourner au menu principal ?\nLa connexion sera fermée.", 
            "Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            // Arrêter la réception
            if (mainReceiver != null) {
                mainReceiver.stopReceiving();
            }
            
            // Fermer cette fenêtre
            dispose();
            
            // Retourner au menu
            SwingUtilities.invokeLater(() -> {
                new MainFrame().setVisible(true);
            });
        }
    }
}