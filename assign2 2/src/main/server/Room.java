package server;

import common.MessageType;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.time.Instant;

public class Room {
    private String name;
    protected List<ClientHandler> clients; // access needs to be done via locks
    protected final Lock clientListLock = new ReentrantLock();

    public Room(String name) {
        this.name = name;
        this.clients = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void addClient(ClientHandler client) {
        clientListLock.lock();
        try {
            clients.add(client);
            // Notify all clients in the room that a new user joined
            broadcastUserJoined(client.getClient().getUsername());
        } finally {
            clientListLock.unlock();
        }
    }

    public boolean isAiRoom() {
        return false;
    }

    public void removeClient(ClientHandler client) {
        clientListLock.lock();
        try {
            clients.remove(client);
            // Notify remaining clients that a user left
            if (client.getClient() != null) {
                broadcastUserLeft(client.getClient().getUsername());
            }
        } finally {
            clientListLock.unlock();
        }
    }

    public void broadcastMessage(String content, ClientHandler sender) {
        JSONObject message = new JSONObject();
        String username = sender.getClient().getUsername();

        message.put("type", MessageType.MESSAGE_RECEIVED.toString());
        message.put("roomName", this.name);
        message.put("sender", username);
        message.put("content", content);
        message.put("timestamp", Instant.now().toString());

        String jsonMessage = message.toString();

        clientListLock.lock();
        try {
            for (ClientHandler client : clients) {
                client.sendJsonMessage(jsonMessage);
            }
        } finally {
            clientListLock.unlock();
        }
    }

    private void broadcastUserJoined(String username) {
        JSONObject message = new JSONObject();
        message.put("type", MessageType.USER_JOINED.toString());
        message.put("roomName", this.name);
        message.put("username", username);
        message.put("timestamp", Instant.now().toString());

        String jsonMessage = message.toString();

        clientListLock.lock();
        try {
            for (ClientHandler client : clients) {
                    client.sendJsonMessage(jsonMessage);
            }
        } finally {
            clientListLock.unlock();
        }
    }

    private void broadcastUserLeft(String username) {
        JSONObject message = new JSONObject();
        message.put("type", MessageType.USER_LEFT.toString());
        message.put("roomName", this.name);
        message.put("username", username);
        message.put("timestamp", Instant.now().toString());

        String jsonMessage = message.toString();

        clientListLock.lock();
        try {
            for (ClientHandler client : clients) {
                client.sendJsonMessage(jsonMessage);
            }
        } finally {
            clientListLock.unlock();
        }
    }

    public void broadcastSystemMessage(String content) {
        JSONObject message = new JSONObject();
        message.put("type", MessageType.ERROR.toString());
        message.put("roomName", this.name);
        message.put("content", content);
        message.put("timestamp", Instant.now().toString());

        String jsonMessage = message.toString();

        clientListLock.lock();
        try {
            for (ClientHandler client : clients) {
                client.sendJsonMessage(jsonMessage);
            }
        } finally {
            clientListLock.unlock();
        }
    }

    // Get the number of clients in the room
    public int getClientCount() {
        clientListLock.lock();
        try {
            return clients.size();
        } finally {
            clientListLock.unlock();
        }
    }

    // Get list of users in this room
    public List<String> getUserList() {
        List<String> users = new ArrayList<>();
        clientListLock.lock();
        try {
            for (ClientHandler client : clients) {
                users.add(client.getClient().getUsername());
            }
            return users;
        } finally {
            clientListLock.unlock();
        }
    }
}