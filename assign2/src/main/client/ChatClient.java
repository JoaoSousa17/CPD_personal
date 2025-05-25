package client;

import java.net.*;
import java.io.*;
import javax.swing.*;
import java.util.concurrent.locks.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import common.MessageType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


public class ChatClient {
    private SSLSocket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ChatListener listener;
    private boolean isAuthenticated = false;
    private String currentRoom = null;

    private final Lock authLock = new ReentrantLock();
    private final Condition authCondition = authLock.newCondition();
    private JSONObject authResponse = null;
    private boolean authResponseReceived = false;
    private String lastLoginError = "";
    private String Token= "";
    private String Username = "";

    private static final String TRUSTSTORE_PATH = "client/client.truststore";
    private static final String TRUSTSTORE_PASSWORD = "changeit";


    private String serverAddress = "";
    private int serverPort = 0;

    private final int HEARTBEAT_INTERVAL = 10_000;
    private final int HEARTBEAT_TIMEOUT = 20_000;
    private long lastHeartbeatAckTime = System.currentTimeMillis();

    private Thread heartbeatThread;
    private boolean heartbeatRunning = false;


    public boolean connect(String serverAddress, int port) {
        this.serverAddress = serverAddress;
        this.serverPort = port;
        try {
            // Set up the SSL context with trust manager
            SSLContext sslContext = createSSLContext();

            // Create SSL socket factory
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // Create SSL socket
            socket = (SSLSocket) sslSocketFactory.createSocket(serverAddress, port);
            socket.setSoTimeout(HEARTBEAT_TIMEOUT);

            // Configure SSL parameters
            String[] enabledProtocols = {"TLSv1.2", "TLSv1.3"};
            socket.setEnabledProtocols(enabledProtocols);

            // Start handshake explicitly
            socket.startHandshake();

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            return true;
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
            return false;
        }
    }

    private SSLContext createSSLContext() throws GeneralSecurityException, IOException {

        if (new File(TRUSTSTORE_PATH).exists()) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } else {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null); 

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        }
    }
    public static void main(String[] args) {
        String serverAddress = "localhost";
        int port = 8443;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 8443.");
            }
        }
        if (args.length > 1) {
            serverAddress = args[1];
        }


        String finalServerAddress = serverAddress;
        int finalPort = port;

        javax.swing.SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI(finalServerAddress, finalPort);
            gui.setVisible(true);
        });
    }


    public void setListener(ChatListener listener) {
        this.listener = listener;
    }

    public boolean register(String username, String password) {
        new Thread(new IncomingMessageHandler()).start();

        JSONObject registerRequest = new JSONObject();
        registerRequest.put("type", MessageType.REGISTER_REQUEST.toString());
        registerRequest.put("username", username);
        registerRequest.put("password", password);

        out.println(registerRequest.toString());

        authLock.lock();
        try {
            long timeout = System.currentTimeMillis() + 5000;
            while (!authResponseReceived && System.currentTimeMillis() < timeout) {
                try {
                    authCondition.await(timeout - System.currentTimeMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    return false;
                }
            }

            if (!authResponseReceived) {
                lastLoginError = "No response from server.";
                return false;
            }

            if (authResponse.getString("type").equals(MessageType.REGISTER_RESPONSE.toString())) {
                boolean success = authResponse.getBoolean("success");
                if (success) {
                    JSONObject readyMsg = new JSONObject();
                    readyMsg.put("type", "READY");
                    out.println(readyMsg.toString());
                    isAuthenticated = true;
                    Token = authResponse.getString("message");
                    Username = username;
                    startHeartbeat();
                    return true;
                } else {
                    lastLoginError = authResponse.getString("message");
                    return false;
                }
            } else {
                lastLoginError = "Unexpected response type.";
                return false;
            }
        } catch (JSONException e) {
            lastLoginError = "Invalid response format.";
            return false;
        } finally {
            authLock.unlock();
        }
    }

    public boolean login(String username, String password) {
        new Thread(new IncomingMessageHandler()).start();

        JSONObject loginRequest = new JSONObject();
        loginRequest.put("type", MessageType.LOGIN_REQUEST.toString());
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        out.println(loginRequest.toString());

        authLock.lock();
        try {
            long timeout = System.currentTimeMillis() + 5000;
            while (!authResponseReceived && System.currentTimeMillis() < timeout) {
                try {
                    authCondition.await(timeout - System.currentTimeMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    return false;
                }
            }

            if (!authResponseReceived) {
                lastLoginError = "No response from server.";
                return false;
            }

            if (authResponse.getString("type").equals(MessageType.LOGIN_RESPONSE.toString())) {
                boolean success = authResponse.getBoolean("success");
                if (success) {
                    JSONObject readyMsg = new JSONObject();
                    readyMsg.put("type", "READY");
                    out.println(readyMsg.toString());
                    isAuthenticated = true;
                    Token = authResponse.getString("message");
                    Username = username;
                    startHeartbeat();
                    return true;
                } else {
                    lastLoginError = authResponse.getString("message");
                    return false;
                }
            } else {
                lastLoginError = "Unexpected response type.";
                return false;
            }
        } catch (JSONException e) {
            lastLoginError = "Invalid response format.";
            return false;
        } finally {
            authLock.unlock();
        }
    }

    public void sendChatMessage(String content) {
        if (out != null) {
            JSONObject message = new JSONObject();

            if (content.startsWith("/")) {
                if (content.equalsIgnoreCase("/quit")) {
                    message.put("type", MessageType.QUIT.toString());
                    message.put("content", content);
                } else if (content.equalsIgnoreCase("/rooms")) {
                    message.put("type", MessageType.LIST_ROOMS.toString());
                    message.put("content", content);
                } else if (content.equalsIgnoreCase("/cmds")) {
                    message.put("type", MessageType.LIST_CMDS.toString());
                    message.put("content", content);
                } else if (content.equalsIgnoreCase("/room")) {
                    message.put("type", MessageType.LIST_CUR_ROOM.toString());
                    message.put("content", content);
                } else if (content.startsWith("/join ")) {
                    String roomName = content.split("\\s+", 2)[1];
                    message.put("type", MessageType.JOIN_ROOM.toString());
                    message.put("roomName", roomName);
                    message.put("content", content);
                } else if (content.equalsIgnoreCase("/leave")) {
                    message.put("type", MessageType.LEAVE_ROOM.toString());
                    message.put("content", content);
                }
            } else {
                message.put("type", MessageType.SEND_MESSAGE.toString());
                message.put("content", content);
                message.put("roomName", currentRoom);
            }

            out.println(message.toString());
        }
    }

    private boolean reconnect() {

        int attempts = 0;
        while (attempts < 5) {
            try {
                Thread.sleep(10000);

                if (!connect(serverAddress, serverPort)) {
                    attempts++;
                    continue;
                }

                new Thread(new IncomingMessageHandler()).start();

                JSONObject reconnectMsg = new JSONObject();
                reconnectMsg.put("type", "RECONNECT");
                reconnectMsg.put("token", Token);
                reconnectMsg.put("username", Username);
                reconnectMsg.put("roomName", currentRoom);

                authLock.lock();
                try {
                    authResponseReceived = false;
                    authResponse = null;
                } finally {
                    authLock.unlock();
                }

                out.println(reconnectMsg.toString());

                authLock.lock();
                try {
                    long timeout = System.currentTimeMillis() + 5000;
                    while (!authResponseReceived && System.currentTimeMillis() < timeout) {
                        long waitTime = timeout - System.currentTimeMillis();
                        if (waitTime <= 0) break;
                        authCondition.await(waitTime, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }

                    if (!authResponseReceived) {
                        attempts++;
                        continue;
                    }
                    if (authResponse.getString("type").equals(MessageType.RECONNECT_RESPONSE.toString())) {
                        boolean success = authResponse.getBoolean("success");
                        if (success) {
                            isAuthenticated = true;
                            startHeartbeat();
                            return true;
                        } else {
                            attempts++;
                            continue;
                        }
                    } else {
                        attempts++;
                        continue;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (JSONException e) {
                    attempts++;
                    continue;
                } finally {
                    authLock.unlock();
                }

            } catch (Exception e) {
                e.printStackTrace();
                attempts++;
            }
        }

        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onDisconnect());
        }
        return false;
    }




    public void disconnect() {
        stopHeartbeat();
        try {
            if (out != null) {
                JSONObject quitMsg = new JSONObject();
                quitMsg.put("type", MessageType.QUIT.toString());
                out.println(quitMsg.toString());
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void startHeartbeat() {
        heartbeatRunning = true;
        heartbeatThread = new Thread(() -> {
            while (heartbeatRunning && socket != null && !socket.isClosed() && isAuthenticated) {
                try {
                    JSONObject heartbeat = new JSONObject();
                    heartbeat.put("type", "HEARTBEAT");
                    out.println(heartbeat.toString());

                    Thread.sleep(HEARTBEAT_INTERVAL);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void stopHeartbeat() {
        heartbeatRunning = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
    }


    public String getLastLoginError() {
        return lastLoginError;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    private class IncomingMessageHandler implements Runnable {
        public void run() {
            try {
                String jsonStr;
                while ((jsonStr = in.readLine()) != null) {
                    try {
                        final JSONObject jsonMessage = new JSONObject(jsonStr);
                        String type = jsonMessage.getString("type");

                        // Authentication responses
                        if (!isAuthenticated &&
                                (type.equals(MessageType.LOGIN_RESPONSE.toString()) ||
                                        type.equals(MessageType.REGISTER_RESPONSE.toString()) || type.equals(MessageType.RECONNECT_RESPONSE.toString()))) {
                            authLock.lock();
                            try {
                                authResponse = jsonMessage;
                                authResponseReceived = true;
                                authCondition.signal();
                            } finally {
                                authLock.unlock();
                            }
                            continue;
                        }

                        // Process other message types
                        SwingUtilities.invokeLater(() -> {
                            processMessage(jsonMessage);
                        });

                    } catch (JSONException e) {
                        System.err.println("Invalid JSON received: " + jsonStr);
                    }
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    e.printStackTrace();
                }
            } finally {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                    isAuthenticated = false;
                    startHeartbeat();
                    reconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


        }

        private void processMessage(JSONObject message) {
            try {
                String type = message.getString("type");

                switch (type) {
                    case "WELCOME":
                        if (listener != null) {
                            String welcomeMsg = message.getString("message");
                            listener.onSystemMessage(welcomeMsg);
                        }
                        break;
                    case "ROOM_JOINED":
                        if (listener != null) {
                            String roomName = message.getString("roomName");
                            currentRoom = roomName;
                            listener.onRoomJoined(roomName);
                        }
                        break;

                    case "USER_JOINED":
                        if (listener != null) {
                            String username = message.getString("username");
                            String roomName = message.getString("roomName");
                            listener.onUserJoined(username, roomName);
                        }
                        break;

                    case "USER_LEFT":
                        if (listener != null) {
                            String username = message.getString("username");
                            String roomName = message.getString("roomName");
                            listener.onUserLeft(username, roomName);
                        }
                        break;

                    case "MESSAGE_RECEIVED":
                        if (listener != null) {
                            String sender = message.getString("sender");
                            String content = message.getString("content");
                            String roomName = message.getString("roomName");
                            listener.onChatMessage(sender, content, roomName);
                        }
                        break;

                    case "ROOM_LEFT":
                        if (listener != null) {
                            String roomName = message.getString("roomName");
                            currentRoom = null;
                            listener.onRoomLeft(roomName);
                        }
                        break;
                    case "HEARTBEAT_ACK":
                        lastHeartbeatAckTime = System.currentTimeMillis();
                        break;

                    case "ROOM_LIST":
                        if (listener != null) {
                            List<String> rooms = new ArrayList<>();
                            JSONArray roomArray = message.getJSONArray("rooms");
                            for (int i = 0; i < roomArray.length(); i++) {
                                rooms.add(roomArray.getString(i));
                            }
                            listener.onRoomList(rooms);
                        }
                        break;
                    case "ROOM":
                        if (listener != null) {
                            listener.onRoom(currentRoom);
                        }
                        break;
                    case "CMDS":
                        if (listener != null) {
                            Map<String, String> cmds = new LinkedHashMap<>();
                            for (String key : message.keySet()) {
                                if (!key.equals("type")) {
                                    cmds.put(key, message.getString(key));
                                }
                            }
                            listener.onCmds(cmds);
                        }
                        break;

                    case "ERROR":
                        if (listener != null) {
                            String errorMsg = message.getString("message");
                            listener.onErrorMessage(errorMsg);
                        }
                        break;
                    case "RECONNECT_RESPONSE":
                        if (listener != null) {
                            boolean success = message.getBoolean("success");
                            if (success) {
                                isAuthenticated = true;
                                startHeartbeat();
                                listener.onSystemMessage("Reconnected successfully!");
                            } else {
                                listener.onErrorMessage("Reconnection failed. Please login again.");
                                Token = "";
                                Username = "";
                            }
                        }
                        break;
                }
            } catch (JSONException e) {
                System.err.println("Error processing message: " + e.getMessage());
            }
        }
    }

    public interface ChatListener {
        void onChatMessage(String sender, String content, String roomName);
        void onSystemMessage(String message);
        void onErrorMessage(String errorMessage);
        void onUserJoined(String username, String roomName);
        void onUserLeft(String username, String roomName);
        void onRoomJoined(String roomName);
        void onRoomLeft(String roomName);
        void onRoomList(List<String> rooms);
        void onRoom(String roomName);
        void onCmds(Map<String, String> cmds);
        void onDisconnect();
    }
}