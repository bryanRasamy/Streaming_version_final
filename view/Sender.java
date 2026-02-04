package view;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import controller.*;

public class Sender extends JFrame {
    private VideoPanel videoPanel;
    private UserListPanel userListPanel;

    public Sender(MainSender mainSender) {
        setTitle("Logiciel de Streaming - Réseau");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Créer les panels
        videoPanel = new VideoPanel();
        userListPanel = new UserListPanel(mainSender);

        // Ajouter à l'interface
        add(videoPanel, BorderLayout.CENTER);
        add(userListPanel, BorderLayout.EAST);
    }

    /*Met à jour l'affichage*/
    public void updatePreview(BufferedImage frame) {
        if (videoPanel != null) {
            videoPanel.updateFrame(frame);
        }
    }

    /*Réinitialise l'affichage*/
    public void resetPreview() {
        if (videoPanel != null) {
            videoPanel.reset();
        }
    }
}