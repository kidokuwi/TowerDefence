import java.io.*;
import java.net.*;
import java.util.function.Consumer;

/**
 * Handles TCP socket connection for VS mode.
 */
public class NetworkManager {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenThread;
    private Consumer<String> onMessageReceived;

    private ServerSocket serverSocket;

    public void host(int port, Consumer<String> onMsg) throws IOException {
        this.onMessageReceived = onMsg;
        serverSocket = new ServerSocket(port);
        System.out.println("Waitng for client on port " + port + "...");
        // This blocks, so in a real app we'd do this in a thread or with a callback
        new Thread(() -> {
            try {
                socket = serverSocket.accept();
                setupStreams();
                startListening();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void connect(String ip, int port, Consumer<String> onMsg) throws IOException {
        this.onMessageReceived = onMsg;
        int maxAttempts = 3;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                socket = new Socket(ip, port);
                setupStreams();
                startListening();
                return; // Success
            } catch (ConnectException e) {
                attempt++;
                if (attempt >= maxAttempts)
                    throw e;
                System.out.println("Connection failed, retrying (" + attempt + "/" + maxAttempts + ")...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Connection interrupted", ie);
                }
            }
        }
    }

    private void setupStreams() throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    private void startListening() {
        listenThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (onMessageReceived != null) {
                        onMessageReceived.accept(line);
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection closed.");
            }
        });
        listenThread.start();
    }

    public void send(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    public void close() {
        try {
            if (serverSocket != null)
                serverSocket.close();
            if (socket != null)
                socket.close();
            if (listenThread != null)
                listenThread.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getLocalIpCode() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            return encodeIp(ip);
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private static final String CHARSET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
    private static final long SALT = 0x5A5A5A5A; // To make the IP code look random

    public static String encodeIp(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4)
                return "INVALID";

            long ipNum = 0;
            for (int i = 0; i < 4; i++) {
                ipNum = (ipNum << 8) + Integer.parseInt(parts[i]);
            }

            // XOR with salt to make it look random
            ipNum ^= SALT;

            StringBuilder sb = new StringBuilder();
            // 32 bits into 5-bit chunks -> 7 chars
            for (int i = 0; i < 7; i++) {
                sb.append(CHARSET.charAt((int) (ipNum % 32)));
                ipNum /= 32;
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    public static String decodeIp(String code) {
        try {
            code = code.toUpperCase().replace("-", "").trim();
            if (code.length() != 7)
                return code; // Assume it's already an IP if length != 7

            long ipNum = 0;
            long power = 1;
            for (int i = 0; i < 7; i++) {
                int val = CHARSET.indexOf(code.charAt(i));
                if (val == -1)
                    return code; // Not a valid code
                ipNum += val * power;
                power *= 32;
            }

            // Reverse the salt
            ipNum ^= SALT;

            return String.format("%d.%d.%d.%d",
                    (ipNum >> 24) & 0xFF,
                    (ipNum >> 16) & 0xFF,
                    (ipNum >> 8) & 0xFF,
                    ipNum & 0xFF);
        } catch (Exception e) {
            return code;
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
