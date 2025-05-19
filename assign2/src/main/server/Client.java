package server;

public class Client {
    private String username;
    private String ipAddress;
    private long connectedAt;

    public Client(String username, String ipAddress) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.connectedAt = System.currentTimeMillis();
    }
    String getUsername() {
        return username;
    }
    String getIpAddress() {
        return ipAddress;
    }
    long getConnectedAt() {
        return connectedAt;
    }

}
