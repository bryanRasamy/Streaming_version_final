package view;

import javax.swing.*;
import java.awt.*;
import controller.*;
import sender.*;

public class UserListPanel extends JPanel {
    private MainSender mainSender;
    private DefaultListModel<String> clientListModel;
    private JList<String> clientList;
    private JLabel clientCountLabel;
    private Timer updateTimer;

    // Constructeur avec MainSender
    public UserListPanel(MainSender mainSender) {
        this.mainSender = mainSender;
        initComponents();
        startAutoUpdate();
    }

    // Constructeur sans paramètre (pour compatibilité)
    public UserListPanel() {
        this(null);
    }

    private void initComponents() {
        setPreferredSize(new Dimension(250, 0));
        setLayout(new BorderLayout());
        setBackground(new Color(45, 45, 45));

        // Titre avec compteur de clients
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(35, 35, 35));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel title = new JLabel("Clients connectés", JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        
        clientCountLabel = new JLabel("0 client(s)", JLabel.CENTER);
        clientCountLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        clientCountLabel.setForeground(Color.LIGHT_GRAY);
        
        headerPanel.add(title, BorderLayout.NORTH);
        headerPanel.add(clientCountLabel, BorderLayout.SOUTH);

        // Liste des clients
        clientListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        clientList.setBackground(new Color(55, 55, 55));
        clientList.setForeground(Color.WHITE);
        clientList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        clientList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JScrollPane scrollPane = new JScrollPane(clientList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)));

        // Panel des boutons
        JPanel controllerPanel = new JPanel();
        controllerPanel.setLayout(new GridLayout(2, 1, 5, 5));
        controllerPanel.setBackground(new Color(45, 45, 45));
        controllerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JButton btnRefresh = new JButton("Actualiser");
        JButton btnStopShare = new JButton("Arrêter partage");
        
        btnRefresh.setFocusPainted(false);
        btnStopShare.setFocusPainted(false);
        
        controllerPanel.add(btnRefresh);
        controllerPanel.add(btnStopShare);
        
        // Actions
        btnRefresh.addActionListener(e -> updateClientList());
        btnStopShare.addActionListener(e -> stopStreaming());

        // Assemblage
        add(headerPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(controllerPanel, BorderLayout.SOUTH);
        
        // Mise à jour initiale
        updateClientList();
    }
    
    /* Met à jour la liste des clients depuis le StreamServer*/
    public void updateClientList() {
        if (mainSender == null || mainSender.getServer() == null) {
            clientListModel.clear();
            clientListModel.addElement("Serveur non démarré");
            clientCountLabel.setText("0 client(s)");
            return;
        }
        
        StreamServer server = mainSender.getServer();
        
        // Sauvegarder la sélection actuelle
        int selectedIndex = clientList.getSelectedIndex();
        
        // Vider et remplir la liste
        clientListModel.clear();
        
        if (server.getClientCount() == 0) {
            clientListModel.addElement("En attente de clients...");
            clientCountLabel.setText("0 client(s)");
        } else {
            for (ClientInfo client : server.getClients()) {
                // Format : "Nom (IP:Port)"
                String status = isClientActive(client) ? "Actif:" : "Non actif:";
                String clientInfo = String.format("%s %s", status, client.toString());
                clientListModel.addElement(clientInfo);
            }
            
            int count = server.getClientCount();
            clientCountLabel.setText(count + " client" + (count > 1 ? "s" : ""));
        }
        
        // Restaurer la sélection si possible
        if (selectedIndex >= 0 && selectedIndex < clientListModel.size()) {
            clientList.setSelectedIndex(selectedIndex);
        }
    }
    
    /*Vérifie si un client est actif*/
    private boolean isClientActive(ClientInfo client) {
        long inactiveTime = System.currentTimeMillis() - client.lastActivity;
        return inactiveTime < 5000; // 5 secondes
    }
    
    /*Démarre la mise à jour automatique toutes les 2 secondes*/
    private void startAutoUpdate() {
        updateTimer = new Timer(2000, e -> updateClientList());
        updateTimer.start();
    }
    
    /*Arrête la mise à jour automatique*/
    public void stopAutoUpdate() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
    }
    
    /*Action pour arrêter le streaming et retourner au menu*/
    private void stopStreaming() {
        if (mainSender != null) {
            int confirm = JOptionPane.showConfirmDialog(
                this, 
                "Arrêter le partage d'écran ?\nTous les clients seront déconnectés.\nVous serez redirigé vers le menu principal.", 
                "Confirmation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (confirm == JOptionPane.YES_OPTION) {
                // Arrêter le streaming
                mainSender.stopStreaming();
                stopAutoUpdate();
                
                // Fermer la fenêtre Sender
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window != null) {
                    window.dispose();
                }
                
                openMain();
            }
        } else {
            openMain();
        }
    }
    
    private void openMain() {
        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}