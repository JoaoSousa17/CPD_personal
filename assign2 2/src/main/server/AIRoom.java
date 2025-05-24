package server;

import common.MessageType;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.time.Instant;

public class AIRoom extends Room {
    private String aiPrompt;
    private String aiModel;
    private static final String BOT_NAME = "Bot";
    
    // Lista para manter o histórico de mensagens para contexto da AI
    private List<String> messageHistory;
    private final Lock historyLock = new ReentrantLock();
    
    public AIRoom(String name, String aiPrompt, String aiModel) {
        super(name);
        this.aiPrompt = aiPrompt != null ? aiPrompt : "You are a helpful assistant in a chat room.";
        this.aiModel = aiModel != null ? aiModel : "phi3";
        this.messageHistory = new ArrayList<>();
        
        // Adicionar o prompt inicial ao histórico
        historyLock.lock();
        try {
            messageHistory.add("System: " + this.aiPrompt);
        } finally {
            historyLock.unlock();
        }
    }
    
    // Construtor simplificado - só com prompt (usa modelo padrão)
    public AIRoom(String name, String aiPrompt) {
        this(name, aiPrompt, "phi3");
    }
    
    @Override
    public void broadcastMessage(String content, ClientHandler sender) {
        // Primeiro, enviar a mensagem normal para todos os utilizadores
        super.broadcastMessage(content, sender);
        
        // Adicionar a mensagem ao histórico
        String userMessage = sender.getClient().getUsername() + ": " + content;
        historyLock.lock();
        try {
            messageHistory.add(userMessage);
            
            // Manter apenas as últimas 20 mensagens para evitar contexto muito grande
            if (messageHistory.size() > 20) {
                messageHistory.remove(0);
            }
        } finally {
            historyLock.unlock();
        }
        
        // Gerar resposta do bot de forma assíncrona
        Thread.startVirtualThread(() -> generateBotResponse());
    }
    
    private void generateBotResponse() {
        try {
            // Verificar se o Ollama está disponível
            if (!OllamaClient.isAvailable()) {
                System.err.println("Ollama não está disponível");
                return;
            }
            
            // Construir o contexto completo
            String fullContext = buildContext();
            
            // Gerar resposta usando o Ollama
            String aiResponse = OllamaClient.generateResponse(fullContext, aiModel);
            
            if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                // Adicionar resposta do bot ao histórico
                historyLock.lock();
                try {
                    messageHistory.add(BOT_NAME + ": " + aiResponse);
                    
                    // Manter limite do histórico
                    if (messageHistory.size() > 20) {
                        messageHistory.remove(0);
                    }
                } finally {
                    historyLock.unlock();
                }
                
                // Enviar resposta do bot para todos os clientes
                broadcastBotMessage(aiResponse);
            }
        } catch (Exception e) {
            System.err.println("Erro ao gerar resposta do Bot: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String buildContext() {
        StringBuilder context = new StringBuilder();
        
        historyLock.lock();
        try {
            for (String message : messageHistory) {
                context.append(message).append("\n");
            }
        } finally {
            historyLock.unlock();
        }
        
        // Adicionar instrução para o bot
        context.append("\nResponde como um assistente útil na sala de chat '")
                .append(getName())
                .append("'. Mantém a resposta concisa e relevante ao contexto da conversa:");
        
        return context.toString();
    }
    
    private void broadcastBotMessage(String content) {
        JSONObject message = new JSONObject();
        
        message.put("type", MessageType.MESSAGE_RECEIVED.toString());
        message.put("roomName", this.getName());
        message.put("sender", BOT_NAME);
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
    
    @Override
    public void addClient(ClientHandler client) {
        super.addClient(client);
        
        // Quando um novo utilizador entra na sala AI, enviar uma mensagem de boas-vindas do bot
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(1000); // Pequena pausa para dar tempo ao cliente conectar-se
                
                if (!OllamaClient.isAvailable()) {
                    broadcastBotMessage("Olá! Eu sou o Bot desta sala, mas estou com problemas de ligação ao AI. 🤖");
                    return;
                }
                
                String welcomePrompt = String.format(
                    "Um novo utilizador '%s' entrou na sala de chat AI '%s'. " +
                    "Dá-lhe as boas-vindas de forma amigável e explica brevemente o propósito desta sala.",
                    client.getClient().getUsername(),
                    getName()
                );
                
                String welcomeResponse = OllamaClient.generateResponse(welcomePrompt, aiModel);
                if (welcomeResponse != null && !welcomeResponse.trim().isEmpty()) {
                    broadcastBotMessage(welcomeResponse);
                }
            } catch (Exception e) {
                System.err.println("Erro ao gerar mensagem de boas-vindas: " + e.getMessage());
            }
        });
    }
    
    // Getters
    public String getAiPrompt() {
        return aiPrompt;
    }
    
    public String getAiModel() {
        return aiModel;
    }
    
    @Override
    public boolean isAiRoom() {
        return true;
    }
}