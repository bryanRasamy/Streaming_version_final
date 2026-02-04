package view;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VideoPanel extends JPanel {
    private BufferedImage currentFrame;
    private JLabel imageLabel;
    private JLabel statusLabel;
    private int frameCount = 0;

    public VideoPanel() {
        setBackground(Color.BLACK);
        setLayout(new BorderLayout());

        // Label pour afficher l'image
        imageLabel = new JLabel("En attente du stream...", JLabel.CENTER);
        imageLabel.setForeground(Color.WHITE);
        imageLabel.setFont(new Font("Arial", Font.BOLD, 18));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);

        // Label pour les stats (en bas)
        statusLabel = new JLabel("Frame: 0 | Résolution: 0x0");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        add(imageLabel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    /*Met à jour l'affichage avec une nouvelle frame*/
    public void updateFrame(BufferedImage frame) {
        if (frame == null) {
            return;
        }

        currentFrame = frame;
        frameCount++;

        // Redimensionnement l'image pour l'adapter au panel
        ImageIcon icon = scaleImageToFit(frame);
        imageLabel.setIcon(icon);
        imageLabel.setText(null);  // Enleve le texte "En attente..."

        // Mettre à jour les stats
        statusLabel.setText("Frame: " + frameCount + " | Résolution: " 
            + frame.getWidth() + "x" + frame.getHeight());

        repaint();
    }

    /*Redimensionne l'image pour l'adapter au panel sans déformer*/
    private ImageIcon scaleImageToFit(BufferedImage img) {
        int panelWidth = getWidth();
        int panelHeight = getHeight() - statusLabel.getHeight();

        // Si le panel n'a pas encore de taille, on utilise l'image originale
        if (panelWidth <= 0 || panelHeight <= 0) {
            return new ImageIcon(img);
        }

        // Calculer le ratio pour garder les proportions
        double imgRatio = (double) img.getWidth() / img.getHeight();
        double panelRatio = (double) panelWidth / panelHeight;

        int scaledWidth, scaledHeight;

        if (imgRatio > panelRatio) {
            // Image plus large que le panel
            scaledWidth = panelWidth;
            scaledHeight = (int) (panelWidth / imgRatio);
        } else {
            // Image plus haute que le panel
            scaledHeight = panelHeight;
            scaledWidth = (int) (panelHeight * imgRatio);
        }

        // Redimensionner l'image
        Image scaledImage = img.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_FAST);
        return new ImageIcon(scaledImage);
    }

    /*Réinitialise l'affichage*/
    public void reset() {
        currentFrame = null;
        frameCount = 0;
        imageLabel.setIcon(null);
        imageLabel.setText("En attente du stream...");
        statusLabel.setText("Frame: 0 | Résolution: 0x0");
        repaint();
    }
}