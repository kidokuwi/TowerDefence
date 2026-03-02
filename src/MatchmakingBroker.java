import java.io.*;
import java.net.*;
import java.util.*;

/**
 * A central broker that pairs players for random matchmaking.
 * Run this as a separate process: java MatchmakingBroker
 */
public class MatchmakingBroker {
    private static final int PORT = 12346;
    private static final Queue<Pair> queue = new LinkedList<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Matchmaking Broker started on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New player connected: " + socket.getInetAddress());

                synchronized (queue) {
                    if (queue.isEmpty()) {
                        queue.add(new Pair(socket));
                        System.out.println("Player added to queue. Waiting for opponent...");
                    } else {
                        Pair opponent = queue.poll();
                        System.out.println("Match found! Pairing " + opponent.socket.getInetAddress() + " with "
                                + socket.getInetAddress());

                        new Thread(() -> handlePairing(opponent.socket, socket)).start();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handlePairing(Socket s1, Socket s2) {
        try {
            PrintWriter out1 = new PrintWriter(s1.getOutputStream(), true);
            PrintWriter out2 = new PrintWriter(s2.getOutputStream(), true);

            String ip1 = s1.getInetAddress().getHostAddress();

            // To be robust, if it's on the same machine (localhost),
            // the reported address might be 127.0.0.1 or similar.
            // But we need the address that s2 can actually use to reach s1.

            out1.println("ROLE:HOST");
            out1.flush(); // Ensure host gets its role immediately

            // Small delay to let the host start its ServerSocket before the joiner tries to
            // connect
            Thread.sleep(500);

            out2.println("ROLE:JOIN:" + ip1);
            out2.flush();

            // Keep connections open briefly to ensure messages are sent
            Thread.sleep(1000);

            s1.close();
            s2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class Pair {
        Socket socket;

        Pair(Socket s) {
            this.socket = s;
        }
    }
}
