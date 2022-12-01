import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class PingServer {
    public static void main(String[] args) throws SocketException, InterruptedException, IOException {
        if (args.length < 1 || args.length >= 3) {
            System.out.println("Usage: java PingServer <port> [seed]");
            return;
        }
        int port = 0;
        long seed = 200;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException a) {
            System.out.println("ERR -arg 1 'Not A Number'.");
            return;
        }
        if (args.length == 2) {
            try {
                seed = Long.parseLong(args[1]);
            } catch (NumberFormatException b) {
                System.out.println("ERR -arg 2 'Not A Number'");
            }
        }
        int timewait = 0;
        double losschance = 0.25;
        
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("Server running...");
        
        while (true) {
            byte[] buf = new byte[256];
            DatagramPacket packetreceived = new DatagramPacket(buf, buf.length);
            socket.receive(packetreceived);
            String message = new String(packetreceived.getData(), StandardCharsets.UTF_8);

            // get ip address
            int i = 4;
            StringBuilder ipAddress = new StringBuilder();
            for (byte raw : packetreceived.getAddress().getAddress()) {
                ipAddress.append(raw & 0xFF);
                if (--i > 0) {
                    ipAddress.append(".");
                }
            }

            if (Math.random() <= losschance) {
                System.out.println(ipAddress.toString() + ":"
                        + Integer.toString(packetreceived.getPort()) + "> " + message + " ACTION: not sent");
            } else {
                timewait = (int) (Math.random() * seed);
                Thread.sleep(timewait);
                String myresponse = "PONG";
                DatagramPacket packetsent = new DatagramPacket(myresponse.getBytes(), myresponse.length(),
                        packetreceived.getAddress(), packetreceived.getPort());
                socket.send(packetsent);
                System.out.println(ipAddress.toString() + ":" + Integer.toString(packetreceived.getPort()) + "> "
                        + message + " ACTION: delayed " + Integer.toString(timewait) + " ms");
            }
        }
    }
}
