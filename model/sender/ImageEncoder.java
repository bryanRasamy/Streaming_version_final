package sender;

import common.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class ImageEncoder {
    private float quality;
    private ImageWriter jpegWriter;
    private ImageWriteParam jpegParams;
    
    // Statistiques
    private long totalEncoded = 0;
    private long totalEncodingTime = 0;
    private long totalCompressedSize = 0;
    private long totalOriginalSize = 0;
    
    public ImageEncoder() {
        this(ProtocolConfig.JPEG_QUALITY);
    }
    
    public ImageEncoder(float quality) {
        setQuality(quality);
        initializeWriter();
    }
    
    /*Initialisation le writer JPEG avec les paramètres*/
    private void initializeWriter() {
        // Obtenir le writer JPEG
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("Aucun writer JPEG disponible !");
        }
        jpegWriter = writers.next();
        
        // Configurer les paramètres de compression
        jpegParams = jpegWriter.getDefaultWriteParam();
        jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpegParams.setCompressionQuality(quality);
    }
    
    /*Encode une image en JPEG avec la qualité configurée*/
    public byte[] encode(BufferedImage image) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("Image null !");
        }
        
        long startTime = System.nanoTime();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        
        try {
            jpegWriter.setOutput(ios);
            
            // Création de l'image
            IIOImage iioImage = new IIOImage(image, null, null);
            
            // Écriture avec compression
            jpegWriter.write(null, iioImage, jpegParams);
            
            // Récupérer les données
            byte[] jpegData = baos.toByteArray();
            
            // Statistiques
            long endTime = System.nanoTime();
            totalEncoded++;
            totalEncodingTime += (endTime - startTime);
            totalCompressedSize += jpegData.length;
            totalOriginalSize += (image.getWidth() * image.getHeight() * 4); // RGBA
            
            return jpegData;
            
        } finally {
            ios.close();
        }
    }
    
    /*Encode rapidement avec ImageIO  si la qualité par défaut suffit*/
    public byte[] encodeFast(BufferedImage image) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("Image null !");
        }
        
        long startTime = System.nanoTime();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        
        byte[] jpegData = baos.toByteArray();
        
        // Statistiques
        long endTime = System.nanoTime();
        totalEncoded++;
        totalEncodingTime += (endTime - startTime);
        totalCompressedSize += jpegData.length;
        totalOriginalSize += (image.getWidth() * image.getHeight() * 4);
        
        return jpegData;
    }
    
    /*Définit la qualité JPEG (0.0 = pire qualité, 1.0 = meilleure qualité)*/
    public void setQuality(float quality) {
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("Qualité doit être entre 0.0 et 1.0");
        }
        
        this.quality = quality;
        
        // Mettre à jour les paramètres si le writer existe
        if (jpegParams != null) {
            jpegParams.setCompressionQuality(quality);
        }
        
        System.out.println("Qualité JPEG ajustée à : " + (quality * 100) + "%");
    }
    
    /*Retourne la qualité actuelle*/
    public float getQuality() {
        return quality;
    }
    
    /*Ajuste automatiquement la qualité selon la taille cible*/
    public void autoAdjustQuality(int targetSizeKB, int currentSizeKB) {
        if (currentSizeKB > targetSizeKB * 1.2) {
            // Trop gros, réduire qualité
            float newQuality = Math.max(0.3f, quality - 0.05f);
            setQuality(newQuality);
            System.out.println("Réduction qualité : taille actuelle " + currentSizeKB + " KB > cible " + targetSizeKB + " KB");
            
        } else if (currentSizeKB < targetSizeKB * 0.8 && quality < 0.95f) {
            // Trop petit, augmenter qualité
            float newQuality = Math.min(0.95f, quality + 0.05f);
            setQuality(newQuality);
            System.out.println("Augmentation qualité : taille actuelle " + currentSizeKB + " KB < cible " + targetSizeKB + " KB");
        }
    }
    
    /*Retourne le temps moyen d'encodage en millisecondes*/
    public double getAverageEncodingTimeMs() {
        if (totalEncoded == 0) {
            return 0.0;
        }
        return (totalEncodingTime / (double) totalEncoded) / 1_000_000.0;
    }
    
    /*Retourne le taux de compression moyen*/
    public double getAverageCompressionRatio() {
        if (totalOriginalSize == 0) {
            return 0.0;
        }
        return (double) totalCompressedSize / totalOriginalSize;
    }
    
    /*Retourne la taille moyenne compressée en KB*/
    public double getAverageCompressedSizeKB() {
        if (totalEncoded == 0) {
            return 0.0;
        }
        return (totalCompressedSize / (double) totalEncoded) / 1024.0;
    }
    
    /*Affiche les statistiques d'encodage*/
    public void printStats() {
        System.out.println("=== Statistiques ImageEncoder ===");
        System.out.println("Images encodées : " + totalEncoded);
        System.out.println("Qualité JPEG : " + (quality * 100) + "%");
        System.out.println("Temps moyen : " + String.format("%.2f", getAverageEncodingTimeMs()) + " ms");
        System.out.println("Taille moyenne : " + String.format("%.2f", getAverageCompressedSizeKB()) + " KB");
        System.out.println("Taux compression : " + String.format("%.2f", getAverageCompressionRatio() * 100) + "%");
        System.out.println("=================================");
    }
    
    /*Réinitialise les statistiques*/
    public void resetStats() {
        totalEncoded = 0;
        totalEncodingTime = 0;
        totalCompressedSize = 0;
        totalOriginalSize = 0;
    }
    
    /*Libère les ressources*/
    public void dispose() {
        if (jpegWriter != null) {
            jpegWriter.dispose();
        }
    }
}
