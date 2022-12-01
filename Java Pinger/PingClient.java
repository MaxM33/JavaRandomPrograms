import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class PingClient {
    public static void main(String[] args) throws SocketException, IOException {
        if (args.length != 2) {
            System.out.println("Usage: java PingClient <hostname> <port>");
            return;
        }
        int port = 0;
        if (!args[0].contentEquals("localhost") && !args[0].contentEquals("127.0.0.1")) {
            System.out.println("ERR -arg 1 'Wrong server name'.");
            return;
        }
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException a) {
            System.out.println("ERR -arg 2 'Not a number'.");
            return;
        }
        if (port != 4567) {
            System.out.println("ERR -arg 2 'Server is not listening to that port'.");
            return;
        }
        long timeStart = 0, timeEnd = 0;
        int countreceived = 0, RTT = 0, RTTcumulative = 0, minRTT = 0, maxRTT = 0;
        
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(args[0]);
        socket.setSoTimeout(2000);

        for (int i = 0; i < 10; i++) {
            String message = "PING " + Integer.toString(i) + " " + Long.toString(System.currentTimeMillis());
            DatagramPacket packetsent = new DatagramPacket(message.getBytes(), message.length(), address, port);
            socket.send(packetsent);
            timeStart = System.currentTimeMillis();

            try {
                byte[] buf = new byte[256];
                DatagramPacket packetreceived = new DatagramPacket(buf, buf.length);
                socket.receive(packetreceived);
                countreceived++;
                timeEnd = System.currentTimeMillis();
                RTT = (int) timeEnd - timeStart;
                RTTcumulative += RTT;
                if (RTT > maxRTT)
                    maxRTT = RTT;
                if (RTT < minRTT)
                    minRTT = RTT;
                System.out.println(message + " RTT: " + Integer.toString(RTT) + " ms");
            } catch (SocketTimeoutException so) {
                System.out.println(message + " RTT: *");
            }
        }
        float averageRTT = RTTcumulative / countreceived;
        System.out.printf(
                "---- PING Statistics ----\n10 packets transmitted, %s packets received, %spercent packet loss\nround-trip (ms) min/avg/max = %s/%.2f/%s\n\n",
                countreceived, (10 - countreceived) * 10, minRTT, averageRTT, maxRTT);
        socket.close();
    }
}
