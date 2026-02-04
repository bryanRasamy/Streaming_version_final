package receiver;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public class ImageDecoder {
    private long totalDecoded = 0;
    private long totalDecodingTime = 0;
    private long totalDecodedSize = 0;
    
    public ImageDecoder() {
    
    }
    
    /*Décode une image JPEG*/
    public BufferedImage decode(byte[] jpegData) {
        if (jpegData == null || jpegData.length == 0) {
            throw new IllegalArgumentException("Données JPEG vides !");
        }
        
        long startTime = System.nanoTime();
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(jpegData);
            BufferedImage image = ImageIO.read(bais);
            
            long endTime = System.nanoTime();
            totalDecoded++;
            totalDecodingTime += (endTime - startTime);
            totalDecodedSize += jpegData.length;
            
            return image;
            
        } catch (IOException e) {
            System.err.println("Erreur décodage : " + e.getMessage());
            return null;
        }
    }
    
    /*Décode rapidement*/
    public BufferedImage decodeFast(byte[] jpegData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(jpegData);
        return ImageIO.read(bais);
    }
    
    /*Retourne le temps moyen de décodage en millisecondes*/
    public double getAverageDecodingTimeMs() {
        if (totalDecoded == 0) {
            return 0.0;
        }
        return (totalDecodingTime / (double) totalDecoded) / 1_000_000.0;
    }
    
    /*Retourne la taille moyenne des données décodées en KB*/
    public double getAverageDataSizeKB() {
        if (totalDecoded == 0) {
            return 0.0;
        }
        return (totalDecodedSize / (double) totalDecoded) / 1024.0;
    }
    
    /*Affiche les statistiques de décodage*/
    public void printStats() {
        System.out.println("=== Statistiques ImageDecoder ===");
        System.out.println("Images décodées : " + totalDecoded);
        System.out.println("Temps moyen : " + String.format("%.2f", getAverageDecodingTimeMs()) + " ms");
        System.out.println("Taille moyenne : " + String.format("%.2f", getAverageDataSizeKB()) + " KB");
        System.out.println("=================================");
    }
    
    /*Réinitialise les statistiques*/
    public void resetStats() {
        totalDecoded = 0;
        totalDecodingTime = 0;
        totalDecodedSize = 0;
    }
}