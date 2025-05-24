package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.border.EmptyBorder;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;



public class ClientGUI extends JFrame implements ChatClient.ChatListener {
    private ChatClient client;

    private JTextPane chatArea;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField messageField;
    private JButton sendButton;
    private JButton loginButton;

    private JPanel loginPanel;
    private JPanel chatPanel;
    private JPanel registerPanel;

    private JTextField regUsernameField;
    private JPasswordField regPasswordField;
    private JButton regSubmitButton;
    private JButton regLoginButton;

    private String serverAddress;
    private int serverPort;


    public ClientGUI(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        client = new ChatClient();
        client.setListener(this);
        buildUI();
    }

    private void buildUI() {
        setTitle("Chat Client");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new CardLayout());

        // Login panel - redesigned for cleaner look
        buildLoginPanel();

        // Chat panel
        chatPanel = new JPanel(new BorderLayout());
        chatArea = new JTextPane();
        chatArea.setContentType("text/html");
        chatArea.setEditable(false);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        add(chatPanel, "chat");

        // Register panel - redesigned for cleaner look
        buildRegisterPanel();

        // Listeners
        loginButton.addActionListener(e -> doLogin());
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                client.disconnect();
            }
        });

        showLoginPanel();
    }

    private void buildLoginPanel() {
        loginPanel = new JPanel(new BorderLayout());

        // Create a container panel with some padding
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(30, 40, 30, 40));

        // App title/logo area
        JLabel appTitle = new JLabel("Chat Application", JLabel.CENTER);
        appTitle.setFont(new Font("Arial", Font.BOLD, 24));
        appTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Login form panel
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        formPanel.setMaximumSize(new Dimension(300, 200));
        formPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 5, 8, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Username field with label
        JLabel usernameLabel = new JLabel("Username");
        usernameLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        usernameField = new JTextField(15);
        usernameField.setPreferredSize(new Dimension(200, 28));

        // Password field with label
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        passwordField = new JPasswordField(15);
        passwordField.setPreferredSize(new Dimension(200, 28));

        // Buttons panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

        loginButton = new JButton("Login");
        JButton registerButton = new JButton("Create Account");

        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        registerButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Make buttons same size
        Dimension buttonSize = new Dimension(200, 32);
        loginButton.setPreferredSize(buttonSize);
        loginButton.setMaximumSize(buttonSize);
        registerButton.setPreferredSize(buttonSize);
        registerButton.setMaximumSize(buttonSize);

        registerButton.addActionListener(e -> showRegisterPanel());

        // Add components to form with proper layout
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        formPanel.add(usernameLabel, gbc);
        gbc.gridy = 1;
        formPanel.add(usernameField, gbc);

        gbc.gridy = 2;
        formPanel.add(passwordLabel, gbc);
        gbc.gridy = 3;
        formPanel.add(passwordField, gbc);

        // Add buttons to button panel with spacing
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        buttonPanel.add(loginButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        buttonPanel.add(registerButton);

        // Add all components to content panel with spacing
        contentPanel.add(appTitle);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 25)));
        contentPanel.add(formPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        contentPanel.add(buttonPanel);

        // Center the content in the login panel
        loginPanel.add(contentPanel, BorderLayout.CENTER);

        add(loginPanel, "login");
    }

    private void buildRegisterPanel() {
        registerPanel = new JPanel(new BorderLayout());

        // Create a container panel with some padding
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(30, 40, 30, 40));

        // Registration title
        JLabel registerTitle = new JLabel("Create Account", JLabel.CENTER);
        registerTitle.setFont(new Font("Arial", Font.BOLD, 24));
        registerTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Register form panel
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        formPanel.setMaximumSize(new Dimension(300, 200));
        formPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 5, 8, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Username field with label
        JLabel usernameLabel = new JLabel("Username");
        usernameLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        regUsernameField = new JTextField(15);
        regUsernameField.setPreferredSize(new Dimension(200, 28));

        // Password field with label
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setFont(new Font("Arial", Font.PLAIN, 14)); // Change the font size here
        regPasswordField = new JPasswordField(15);
        regPasswordField.setPreferredSize(new Dimension(200, 28));

        // Buttons panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

        regSubmitButton = new JButton("Register");
        regLoginButton = new JButton("Back to Login");

        regSubmitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        regLoginButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Make buttons same size
        Dimension buttonSize = new Dimension(200, 32);
        regSubmitButton.setPreferredSize(buttonSize);
        regSubmitButton.setMaximumSize(buttonSize);
        regLoginButton.setPreferredSize(buttonSize);
        regLoginButton.setMaximumSize(buttonSize);

        regSubmitButton.addActionListener(e -> doRegister());
        regLoginButton.addActionListener(e -> showLoginPanel());

        // Add components to form with proper layout
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        formPanel.add(usernameLabel, gbc);
        gbc.gridy = 1;
        formPanel.add(regUsernameField, gbc);

        gbc.gridy = 2;
        formPanel.add(passwordLabel, gbc);
        gbc.gridy = 3;
        formPanel.add(regPasswordField, gbc);

        // Add buttons to button panel with spacing
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        buttonPanel.add(regSubmitButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        buttonPanel.add(regLoginButton);

        // Add all components to content panel with spacing
        contentPanel.add(registerTitle);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 25)));
        contentPanel.add(formPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        contentPanel.add(buttonPanel);

        // Center the content in the register panel
        registerPanel.add(contentPanel, BorderLayout.CENTER);

        add(registerPanel, "register");
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        if (client.connect(serverAddress, serverPort)) {
            if (client.login(username, password)) {
                showChatPanel();
            } else {
                showError("Login failed. " + client.getLastLoginError());
            }
        } else {
            showError("Failed to connect to server.");
        }
    }

    private void doRegister() {
        String username = regUsernameField.getText().trim();
        String password = new String(regPasswordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        if (client.connect(serverAddress, serverPort)) {
            if (client.register(username, password)) {
                JOptionPane.showMessageDialog(this, "Registration successful. Welcome to the chat!");
                showChatPanel();
            } else {
                showError("Registration failed. " + client.getLastLoginError());
            }
        } else {
            showError("Failed to connect to server.");
        }
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty()) {
            client.sendChatMessage(msg);
            messageField.setText("");
        }
    }

    private void showLoginPanel() {
        CardLayout cl = (CardLayout) getContentPane().getLayout();
        cl.show(getContentPane(), "login");
    }

    private void showChatPanel() {
        CardLayout cl = (CardLayout) getContentPane().getLayout();
        cl.show(getContentPane(), "chat");
    }

    private void showRegisterPanel() {
        CardLayout cl = (CardLayout) getContentPane().getLayout();
        cl.show(getContentPane(), "register");
    }

    public void onMessageReceived(String receivedMessage) {
        SwingUtilities.invokeLater(() -> {
            String formattedMessage = receivedMessage.replace("[BOLD]", "<b>")
                    .replace("[/BOLD]", "</b>");

            chatArea.setContentType("text/html");

            try {
                javax.swing.text.html.HTMLDocument doc = (javax.swing.text.html.HTMLDocument) chatArea.getDocument();
                doc.insertAfterEnd(doc.getCharacterElement(doc.getLength()),
                        formattedMessage + "<br>");
            } catch (Exception e) {
                chatArea.setText(chatArea.getText() + formattedMessage + "<br>");
                e.printStackTrace();
            }
        });
    }

    public void onDisconnect() {
        SwingUtilities.invokeLater(() -> {
            chatArea.setText("");
            messageField.setText("");
            usernameField.setText("");
            passwordField.setText("");

            showLoginPanel();

            JOptionPane.showMessageDialog(this, "Disconnected from server.", "Conection lost", JOptionPane.INFORMATION_MESSAGE);

            client = new ChatClient();
            client.setListener(this);
        });
    }
    public void onChatMessage(String sender, String content, String roomName, String timestamp) {
        String formatted = String.format("[%s] <b>%s</b>: %s", timestamp, sender, content);
        onMessageReceived(formatted);
    }

    public void onSystemMessage(String message) {
        String formatted = "<i>[System]: " + message + "</i>";
        onMessageReceived(formatted);
    }

    public void onErrorMessage(String errorMessage) {
        String formatted = "<span style='color:red;'><b>Error:</b> " + errorMessage + "</span>";
        onMessageReceived(formatted);
    }

    public void onUserJoined(String username, String roomName) {
        onSystemMessage(username + " has joined room: " + roomName);
    }

    public void onUserLeft(String username, String roomName) {
        onSystemMessage(username + " has left room: " + roomName);
    }

    public void onRoomJoined(String roomName) {
        onSystemMessage("Joined room: " + roomName);
    }

    public void onRoomLeft(String roomName) {
        onSystemMessage("Left room: " + roomName);
    }


    public void onRoomList(List<String> rooms) {
        onSystemMessage("Available rooms:");
        for (String room : rooms) {
            onSystemMessage("- " + room);
        }
    }


    public void onRoom(String room) {
        if(room == null){
            onSystemMessage("You are not currently connected to a room.");
        }
        else onSystemMessage("Current Room: " + room);

    }

    public void onCmds(Map<String, String> cmds) {
        onSystemMessage("Available commands:");
        for (Map.Entry<String, String> entry : cmds.entrySet()) {
            String line = entry.getKey() + ": " + entry.getValue();
            onSystemMessage(line);
        }
    }




    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        String serverAddr = "localhost";
        int port = 8443;

        if (args.length > 0) serverAddr = args[0];
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default 8443");
            }
        }

        final String finalServerAddr = serverAddr;
        final int finalPort = port;

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            ClientGUI gui = new ClientGUI(finalServerAddr, finalPort);
            gui.setVisible(true);
        });
    }

}