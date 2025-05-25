package server;

import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class OllamaClient {
    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "phi3";
    
    public static String generateResponse(String prompt, String model) {
        try {
            // Se não especificar modelo, usa o padrão
            if (model == null || model.isEmpty()) {
                model = DEFAULT_MODEL;
            }
            
            // Criar o request JSON
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", model);
            requestJson.put("prompt", prompt);
            requestJson.put("stream", true); // Usar streaming
            
            // Configurar a conexão HTTP
            URL url = new URL(OLLAMA_URL + "/api/generate");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
            // Enviar o request
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(requestJson.toString());
                writer.flush();
            }
            
            // Ler a resposta streaming
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder fullResponse = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        try {
                            JSONObject responseChunk = new JSONObject(line);
                            if (responseChunk.has("response")) {
                                fullResponse.append(responseChunk.getString("response"));
                            }
                            
                            // Se chegámos ao fim da resposta
                            if (responseChunk.optBoolean("done", false)) {
                                break;
                            }
                        } catch (Exception jsonError) {
                            // Ignora linhas que não são JSON válido
                            continue;
                        }
                    }
                    
                    return fullResponse.toString().trim();
                }
            } else {
                System.err.println("Erro na chamada ao Ollama: " + responseCode);
                return "Desculpa, não consegui processar o teu pedido.";
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao comunicar com o Ollama: " + e.getMessage());
            e.printStackTrace();
            return "Desculpa, ocorreu um erro interno.";
        }
    }
    
    // Método para verificar se o Ollama está disponível
    public static boolean isAvailable() {
        try {
            URL url = new URL(OLLAMA_URL + "/api/tags");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }
}