package sender;

import common.*;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

public class ScreenCapturer {
    private Robot robot;
    private Rectangle screenRect;
    private Thread captureThread;
    private volatile boolean isRunning = false;
    private FrameCallback callback;
    private boolean enablePreview = true;
    private ImageEncoder encoder;

    
    // Statistiques
    private int framesCaptured = 0;
    private long lastFrameTime = 0;
    
    /*Interface pour recevoir les frames capturées*/
    public interface FrameCallback {
        void onFrameCaptured(byte[] jpegData, BufferedImage previewImage, int frameNumber);
    }
    
    public ScreenCapturer() throws AWTException {
        robot = new Robot();

        encoder = new ImageEncoder();
    }
    
    /*Définit une zone spécifique à capturer*/
    public void setCaptureArea(int x, int y, int width, int height) {
        screenRect = new Rectangle(x, y, width, height);
    }
    
    /*Définit une zone selon la config*/
    public void setCaptureAreaFromConfig() {
        // Centre l'écran capturé
        Dimension screendim=Toolkit.getDefaultToolkit().getScreenSize();
        
        int screenWidth = screendim.width;
        int screenHeight = screendim.height;
        
        int x = (screenWidth - ProtocolConfig.SCREEN_WIDTH) / 2;
        int y = (screenHeight - ProtocolConfig.SCREEN_HEIGHT) / 2;
        
        // Si l'écran est plus petit que la config, capture tout l'ecran
        if (x < 0 || y < 0) {
            screenRect = new Rectangle(screendim);
        } else {
            screenRect = new Rectangle(x, y, ProtocolConfig.SCREEN_WIDTH, ProtocolConfig.SCREEN_HEIGHT);
        }
    }
    
    /*Définit le callback qui recevra les frames*/
    public void setFrameCallback(FrameCallback callback) {
        this.callback = callback;
    }
    
    /*Active/désactive la génération d'image de preview*/
    public void setPreviewEnabled(boolean enabled) {
        this.enablePreview = enabled;
    }

    /*Définit la qualité JPEG*/
    public void setJpegQuality(float quality) {
        if (encoder != null) {
            encoder.setQuality(quality);
        }
    }
    
    /*Retourne l'encodeur (pour accéder aux stats)*/
    public ImageEncoder getEncoder() {
        return encoder;
    }

    /*Démarre la capture en continu*/
    public void startCapture() {
        if (isRunning) {
            System.out.println("Capture déjà en cours !");
            return;
        }
        
        if (callback == null) {
            throw new IllegalStateException("Callback non défini ! Utilisez setFrameCallback()");
        }
        
        isRunning = true;
        framesCaptured = 0;
        lastFrameTime = System.nanoTime();
        
        //Lancer la boucle de capture
        captureThread = new Thread(this::captureLoop, "ScreenCapturer-Thread");
        captureThread.start();
        
        System.out.println("Capture démarrée : " + screenRect.width + "x" + screenRect.height + " @ " + ProtocolConfig.TARGET_FPS + " FPS");
    }
    
    /*Arrête la capture*/
    public void stopCapture() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (captureThread != null) {
            try {
                captureThread.join(2000); // Attendre max 2 secondes
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("Capture arrêtée. Total frames capturées : " + framesCaptured);

        // Afficher les stats de l'encodeur
        if (encoder != null) {
            encoder.printStats();
        }
    }
    
    /*Boucle principale de capture*/
    private void captureLoop() {
        long frameDelayNanos = ProtocolConfig.getFrameDelayNanos();
        
        while (isRunning) {
            long frameStart = System.nanoTime();
            
            try {
                // Capture de l'écran
                BufferedImage screenshot = robot.createScreenCapture(screenRect);
                
                // Encode en JPEG
                byte[] jpegData = encodeToJPEG(screenshot);
                
                // Decode pour affichage du sender
                BufferedImage previewImage = null;
                if (enablePreview) {
                    previewImage = decodeFromJPEG(jpegData);
                }
                
                // Callback
                callback.onFrameCaptured(jpegData, previewImage, framesCaptured);
                
                framesCaptured++;
                
                // Attendre pour respecter le FPS cible
                long frameEnd = System.nanoTime();
                long elapsed = frameEnd - frameStart;
                long sleepTime = frameDelayNanos - elapsed;
                
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime / 1_000_000, (int)(sleepTime % 1_000_000));
                } else if (ProtocolConfig.ENABLE_FRAME_SKIP) {
                    System.out.println("Frame " + framesCaptured + " trop lente : " + (elapsed / 1_000_000) + "ms");
                }
                
                lastFrameTime = System.nanoTime();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Erreur capture frame " + framesCaptured + " : " + e.getMessage());
            }
        }
    }
    
    /*Encode une image en JPEG*/
    private byte[] encodeToJPEG(BufferedImage image) throws Exception {
        return encoder.encode(image);
    }
    
    /*Décode un JPEG en BufferedImage*/
    private BufferedImage decodeFromJPEG(byte[] jpegData) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(jpegData);
        return ImageIO.read(bais);
    }

    /*Retourne le nombre de frames capturées*/
    public int getFramesCaptured() {
        return framesCaptured;
    }
    
    /*Vérifie si la capture est en cours*/
    public boolean isRunning() {
        return isRunning;
    }
    
    /*Retourne le FPS réel mesuré*/
    public double getMeasuredFPS() {
        if (framesCaptured < 2) {
            return 0.0;
        }
        
        long now = System.nanoTime();
        long elapsed = now - lastFrameTime;
        
        if (elapsed == 0) {
            return 0.0;
        }
        
        return 1_000_000_000.0 / elapsed;
    }
}