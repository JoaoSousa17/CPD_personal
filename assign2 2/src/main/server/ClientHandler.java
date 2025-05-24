package server;

import java.net.*;
import java.io.*;
import java.util.*;
import common.MessageType;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONObject;
import org.json.JSONException;
import javax.net.ssl.SSLSocket;

public class ClientHandler {
    private SSLSocket socket;
    private ChatServer server;
    private BufferedReader in;
    private PrintWriter out;
    private Client client;
    private Room currentRoom;
    private final Lock messageLock = new ReentrantLock();

    public ClientHandler(SSLSocket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public void start() {
        Thread.startVirtualThread(this::run);
    }

    public void run() {
        try {
            socket.startHandshake();

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String jsonStr = in.readLine();
            try {
                JSONObject json = new JSONObject(jsonStr);
                String type = json.getString("type");

                if (type.equals(MessageType.LOGIN_REQUEST.toString())) {
                    handleLoginRequest(json);
                } else if (type.equals(MessageType.REGISTER_REQUEST.toString())) {
                    handleRegisterRequest(json);
                } else if (type.equals(MessageType.RECONNECT.toString())) {
                    handleReconnectRequest(json);
                } else {
                    sendErrorMessage("Invalid initial message type");
                    closeConnection();
                    return;
                }
            } catch (JSONException e) {
                sendErrorMessage("Invalid JSON format");
                closeConnection();
                return;
            }

            String jsonMessageStr;
            while ((jsonMessageStr = in.readLine()) != null) {
                try {
                    JSONObject jsonMessage = new JSONObject(jsonMessageStr);
                    String type = jsonMessage.getString("type");

                    if (type.equals(MessageType.QUIT.toString())) {
                        break;
                    }
                    else if (type.equals(MessageType.LIST_ROOMS.toString())) {
                        sendRoomList();
                    }
                    else if (type.equals(MessageType.JOIN_ROOM.toString())) {
                        String roomName = jsonMessage.getString("roomName");
                        joinRoom(roomName);
                    }
                    else if (type.equals(MessageType.LEAVE_ROOM.toString())) {
                        leaveRoom();
                    }
                    else if (type.equals(MessageType.LIST_CMDS.toString())) {
                        sendListCmds();
                    }
                    else if (type.equals(MessageType.LIST_CUR_ROOM.toString())) {
                        sendListCurrRoom();
                    }
                    else if (type.equals(MessageType.SEND_MESSAGE.toString())) {
                        if (currentRoom == null) {
                            sendErrorMessage("You must join a room first");
                            continue;
                        }
                        String content = jsonMessage.getString("content");
                        currentRoom.broadcastMessage(content, this);
                    }
                    else if (type.equals("HEARTBEAT")) {
                        if(client != null) {
                            sendHeartbeatAck();
                        }
                    }

                    else {
                        sendErrorMessage("Unknown message type: " + type);
                    }
                } catch (JSONException e) {
                    sendErrorMessage("Invalid JSON format: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Connection error with client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleLoginRequest(JSONObject json) throws IOException {
        try {
            String username = json.getString("username");
            String password = json.getString("password");

            if (username == null || username.isEmpty()) {
                sendLoginResponse(false, "Invalid username");
                closeConnection();
                return;
            }

            if (password == null || password.isEmpty()) {
                sendLoginResponse(false, "Invalid password");
                closeConnection();
                return;
            }

            if (server.isUserLoggedIn(username)) {
                sendLoginResponse(false, "User already logged in");
                closeConnection();
                return;
            }

            if (!authenticateUser(username, password)) {
                sendLoginResponse(false, "Invalid credentials");
                closeConnection();
                return;
            }

            String token = server.createToken(username);
            sendLoginResponse(true, token);

            // Wait for client confirmation
            String confirmationStr = in.readLine();
            JSONObject confirmation = new JSONObject(confirmationStr);
            if (confirmation.getString("type").equals("READY")) {
                this.client = new Client(username, socket.getInetAddress().getHostAddress());
                server.addClient(this);
                sendWelcomeMessage(username);
            } else {
                closeConnection();
            }
        } catch (JSONException e) {
            sendLoginResponse(false, "Invalid JSON format");
            closeConnection();
        }
    }

    private void handleRegisterRequest(JSONObject json) throws IOException {
        try {
            String username = json.getString("username");
            String password = json.getString("password");

            if (username == null || username.isEmpty()) {
                sendRegisterResponse(false, "Invalid username");
                closeConnection();
                return;
            }

            if (password == null || password.isEmpty()) {
                sendRegisterResponse(false, "Invalid password");
                closeConnection();
                return;
            }

            if (checkUserExists(username)) {
                sendRegisterResponse(false, "Username already exists");
                closeConnection();
                return;
            }

            String token = server.createToken(username);
            sendRegisterResponse(true, token);

            // Wait for client confirmation
            String confirmationStr = in.readLine();
            JSONObject confirmation = new JSONObject(confirmationStr);
            if (confirmation.getString("type").equals("READY")) {
                storeUser(username, password);
                this.client = new Client(username, socket.getInetAddress().getHostAddress());
                server.addClient(this);
                sendWelcomeMessage(username);
            } else {
                closeConnection();
            }
        } catch (JSONException e) {
            sendRegisterResponse(false, "Invalid JSON format");
            closeConnection();
        }
    }

    private void handleReconnectRequest(JSONObject json) {

        try {
            String username = json.getString("username");
            String token = json.getString("token");
            String roomName = null;
            if (json.has("roomName") && !json.isNull("roomName")) {
                roomName = json.getString("roomName");
            }



            boolean isValidToken = server.validateToken(username,token);

            if (isValidToken) {
                this.client = new Client(username, socket.getInetAddress().getHostAddress());
                server.addClient(this);

                if (roomName != null && !roomName.isEmpty()) {
                    joinRoom(roomName, true);
                }

                JSONObject response = new JSONObject();
                response.put("type", "RECONNECT_RESPONSE");
                response.put("success", true);
                sendJsonMessage(response.toString());

                sendWelcomeMessage(username);

            } else {
                JSONObject response = new JSONObject();
                response.put("type", "RECONNECT_RESPONSE");
                response.put("success", false);
                sendJsonMessage(response.toString());

                closeConnection();
            }

        } catch (JSONException e) {
            JSONObject response = new JSONObject();
            response.put("type", "RECONNECT_RESPONSE");
            response.put("success", false);
            sendJsonMessage(response.toString());

            closeConnection();
        }
    }

    private void sendLoginResponse(boolean success, String message) {
        JSONObject response = new JSONObject();
        response.put("type", MessageType.LOGIN_RESPONSE.toString());
        response.put("success", success);
        response.put("message", message);

        sendJsonMessage(response.toString());
    }

    private void sendRegisterResponse(boolean success, String message) {
        JSONObject response = new JSONObject();
        response.put("type", MessageType.REGISTER_RESPONSE.toString());
        response.put("success", success);
        response.put("message", message);

        sendJsonMessage(response.toString());
    }

    private void sendWelcomeMessage(String username) {
        JSONObject welcome = new JSONObject();
        welcome.put("type", "WELCOME");
        welcome.put("message", "Welcome to the chat, " + username + "!");

        sendJsonMessage(welcome.toString());
    }
    private void sendHeartbeatAck() {
        JSONObject ack = new JSONObject();
        ack.put("type", "HEARTBEAT_ACK");
        sendJsonMessage(ack.toString());
    }

    private void sendRoomList() {
        List<String> roomNames = server.getRoomNames();

        JSONObject response = new JSONObject();
        response.put("type", MessageType.ROOM_LIST.toString());
        response.put("rooms", roomNames);

        sendJsonMessage(response.toString());
    }

    private void sendListCmds() {
        JSONObject response = new JSONObject();
        response.put("type", MessageType.CMDS.toString());
        response.put("/room", "List current room");
        response.put("/rooms", "List all available rooms");
        response.put("/quit", "Logout");
        response.put("/leave", "Leave room");
        response.put("/cmds", "List all commands available");
        response.put("/join <room name>", "Joins room if exists if not creates a new one");
        response.put("/join AI <topic>", "Creates or joins an AI-powered chat room");
        sendJsonMessage(response.toString());
    }

    private void sendListCurrRoom() {
        JSONObject response = new JSONObject();
        response.put("type", MessageType.ROOM.toString());
        sendJsonMessage(response.toString());
    }

    private void joinRoom(String roomName, boolean silent) {
        if (currentRoom != null) {
            currentRoom.removeClient(this);
        }

        Room newRoom;
        boolean isAiRoom = roomName.toLowerCase().startsWith("ai ") ||
                roomName.toLowerCase().contains(" ai") ||
                roomName.toLowerCase().equals("ai");


        if (isAiRoom) {


            Room existingRoom = server.getOrCreateRoom(roomName);

            if (existingRoom instanceof AIRoom) {

                newRoom = existingRoom;
            } else {


                if (existingRoom.getClientCount() == 0) {

                    String defaultPrompt = generateDefaultPrompt(roomName);

                    newRoom = server.forceCreateAiRoom(roomName, defaultPrompt);
                } else {

                    newRoom = existingRoom;
                    if (!silent) {
                        Thread.startVirtualThread(() -> {
                            try {
                                Thread.sleep(500);
                                sendErrorMessage("Esta sala já existe como sala normal. Para uma sala AI, tenta um nome diferente como '" + roomName + " AI'");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                    }
                }
            }
        } else {
            newRoom = server.getOrCreateRoom(roomName);
        }

        currentRoom = newRoom;
        currentRoom.addClient(this);

        if (!silent) {
            JSONObject response = new JSONObject();
            response.put("type", MessageType.ROOM_JOINED.toString());
            response.put("roomName", roomName);
            response.put("isAiRoom", currentRoom.isAiRoom());

            sendJsonMessage(response.toString());
        }
    }

    private void joinRoom(String roomName) {
        joinRoom(roomName, false);
    }
    
    private String generateDefaultPrompt(String roomName) {
        String lowerName = roomName.toLowerCase();
        
        if (lowerName.contains("programming") || lowerName.contains("code")) {
            return "You are a programming assistant in the chat room '" + roomName + 
                   "'. Help users with coding questions, debug issues, and provide programming best practices. " +
                   "Be concise but thorough in your explanations.";
        } else if (lowerName.contains("math") || lowerName.contains("mathematics")) {
            return "You are a mathematics tutor in the chat room '" + roomName + 
                   "'. Help users solve mathematical problems and explain concepts clearly. " +
                   "Use step-by-step explanations when helpful.";
        } else if (lowerName.contains("science")) {
            return "You are a science assistant in the chat room '" + roomName + 
                   "'. Help users understand scientific concepts and answer their questions " +
                   "across various scientific fields.";
        } else if (lowerName.contains("language") || lowerName.contains("translation")) {
            return "You are a language assistant in the chat room '" + roomName + 
                   "'. Help users with language learning, translations, and linguistic questions.";
        } else {
            return "You are a helpful AI assistant in the chat room '" + roomName + 
                   "'. Engage in meaningful conversation and help users with their questions. " +
                   "Adapt your responses to the context of the conversation.";
        }
    }

    private void leaveRoom() {
        if (currentRoom != null) {
            String roomName = currentRoom.getName();
            currentRoom.removeClient(this);
            currentRoom = null;

            JSONObject response = new JSONObject();
            response.put("type", MessageType.ROOM_LEFT.toString());
            response.put("roomName", roomName);

            sendJsonMessage(response.toString());
        } else {
            sendErrorMessage("You are not in any room");
        }
    }

    private void sendErrorMessage(String errorMessage) {
        JSONObject error = new JSONObject();
        error.put("type", MessageType.ERROR.toString());
        error.put("message", errorMessage);

        sendJsonMessage(error.toString());
    }

    public void sendJsonMessage(String jsonMessage) {
        messageLock.lock();
        try {
            out.println(jsonMessage);
        } finally {
            messageLock.unlock();
        }
    }

    private void cleanup() {
        try {
            if (currentRoom != null) {
                currentRoom.removeClient(this);
                currentRoom = null;
            }

            server.removeClient(this);

            if (client != null) {
                server.removeToken(client.getUsername());
            }

            closeConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    private boolean authenticateUser(String username, String password) {
        try (BufferedReader fileReader = new BufferedReader(new FileReader("users/users.txt"))) {
            String hashedUsername = hashString(username);
            String hashedPassword = hashString(password);
            String line;

            while ((line = fileReader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length != 2) continue;

                if (parts[0].equals(hashedUsername) && parts[1].equals(hashedPassword)) {
                    return true;
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String hashString(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private boolean checkUserExists(String username) {
        try (BufferedReader fileReader = new BufferedReader(new FileReader("users/users.txt"))) {
            String hashedUsername = hashString(username);
            String line;

            while ((line = fileReader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length != 2) continue;

                if (parts[0].equals(hashedUsername)) {
                    return true;
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void storeUser(String username, String password) {
        try {
            String hashedUsername = hashString(username);
            String hashedPassword = hashString(password);
            String userEntry = hashedUsername + ":" + hashedPassword;

            try (BufferedWriter writer = new BufferedWriter(new FileWriter("users/users.txt", true))) {
                writer.write(userEntry);
                writer.newLine();
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public Client getClient() {
        return client;
    }
}