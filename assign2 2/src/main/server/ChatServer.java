package server;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import common.MessageType;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.*;
import java.security.*;


public class ChatServer {

    private int port;

    private SSLServerSocket serverSocket;
    private InetAddress bindAddr;

    private List<ClientHandler> clients; // access needs to be done via locks

    private final Lock clientListLock = new ReentrantLock();

    private Map<String, Room> rooms = new HashMap<>();
    private final Lock roomLock = new ReentrantLock();

    private final Map<String, String> activeTokens = new ConcurrentHashMap<>();

    // SSL configuration properties
    private static final String KEYSTORE_PATH = "server/server.keystore";  // Path to your keystore file
    private static final String KEYSTORE_PASSWORD = "changeit";     // Your keystore password
    private static final String KEY_MANAGER_ALGORITHM = KeyManagerFactory.getDefaultAlgorithm();
    private static final String SSL_PROTOCOL = "TLS";

    public ChatServer(int port, String bindAddress) throws UnknownHostException {
        this.port = port;
        this.clients = new ArrayList<>();
        this.bindAddr = InetAddress.getByName(bindAddress);

        initializeDefaultRooms();

    }

    public void start() {
        try {
            // Set up the SSL context
            SSLContext sslContext = createSSLContext();

            // Create an SSL server socket factory
            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();

            // Create the SSL server socket
            serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(port, 0, bindAddr);

            // Configure SSL parameters if needed
            String[] enabledProtocols = {"TLSv1.2", "TLSv1.3"};
            serverSocket.setEnabledProtocols(enabledProtocols);

            System.out.println("Chat server started on port " + port + " and address " + bindAddr);

            while (true) {
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                System.out.println("New client connected from " + socket.getInetAddress());

                ClientHandler handler = new ClientHandler(socket, this);
                handler.start();
            }

        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }


    private SSLContext createSSLContext() throws GeneralSecurityException, IOException {
        // Initialize KeyStore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }

        // Set up key manager factory
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KEY_MANAGER_ALGORITHM);
        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

        // Create and initialize the SSL context
        SSLContext sslContext = SSLContext.getInstance(SSL_PROTOCOL);
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        return sslContext;
    }

    public void removeClient(ClientHandler handler) {
        clientListLock.lock();
        try {
            clients.remove(handler);
        } finally {
            clientListLock.unlock();
        }
    }

    public void addClient(ClientHandler handler) {
        clientListLock.lock();
        try {
            clients.add(handler);
        } finally {
            clientListLock.unlock();
        }
    }

    private void initializeDefaultRooms() {
        roomLock.lock();
        try {
            rooms.put("parallel computation", new Room("parallel computation"));
            rooms.put("distributed computation", new Room("distributed computation"));
            rooms.put("AI", new AIRoom("AI", 
                "You are an AI assistant helping users discuss artificial intelligence topics. " +
                "Be knowledgeable but approachable in your responses."));
        } finally {
            roomLock.unlock();
        }
    }

    // No ChatServer.java, substitui o método getOrCreateRoom existente por estes dois:
    
    public Room getOrCreateRoom(String name) {
        return getOrCreateRoom(name, null);
    }
    
    public Room getOrCreateRoom(String name, String aiPrompt) {
        roomLock.lock();
        try {
            Room existingRoom = rooms.get(name);
            if (existingRoom != null) {
                return existingRoom;
            }
            
            // Se aiPrompt não for null, criar sala AI
            if (aiPrompt != null && !aiPrompt.trim().isEmpty()) {
                Room newRoom = new AIRoom(name, aiPrompt);
                rooms.put(name, newRoom);
                return newRoom;
            } else {
                // Sala normal
                Room newRoom = new Room(name);
                rooms.put(name, newRoom);
                return newRoom;
            }
        } finally {
            roomLock.unlock();
        }
    }
    
    // Método para forçar criação de sala AI (substitui sala normal existente se estiver vazia)
    public Room forceCreateAiRoom(String name, String aiPrompt) {
        roomLock.lock();
        try {
            Room existingRoom = rooms.get(name);
            if (existingRoom != null && existingRoom.getClientCount() == 0 && !existingRoom.isAiRoom()) {
                // Sala normal vazia, substituir por AI
                rooms.remove(name);
            }
            
            if (!rooms.containsKey(name)) {
                Room newRoom = new AIRoom(name, aiPrompt);
                rooms.put(name, newRoom);
                return newRoom;
            } else {
                return rooms.get(name);
            }
        } finally {
            roomLock.unlock();
        }
    }

    public List<ClientHandler> getClients(){
        return clients;
    }

    public List<String> getRoomNames() {
        roomLock.lock();
        try {
            return new ArrayList<>(rooms.keySet());
        } finally {
            roomLock.unlock();
        }
    }

    public boolean isUserLoggedIn(String username) {
        return activeTokens.containsKey(username);
    }

    public String createToken(String username) {
        String token = UUID.randomUUID().toString();
        activeTokens.put(username, token);
        return token;
    }

    public void removeToken(String username) {
        activeTokens.remove(username);
    }

    public boolean validateToken(String username, String token) {
        return token.equals(activeTokens.get(username));
    }

    public static void main(String[] args) {
        int port = 8443;
        String bindAddress = "localhost";

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 8443.");
            }
        }

        if (args.length > 1) {
            bindAddress = args[1];
        }

        try {
            ChatServer server = new ChatServer(port, bindAddress);
            server.start();
        } catch (UnknownHostException e) {
            System.err.println("Invalid bind address: " + bindAddress);
            e.printStackTrace();
        }
    }
}