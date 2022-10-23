import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws IOException, UnknownHostException {
        String temp = new String(), command = new String();
        Scanner scanner = null;
        Scanner in = null;

        InetAddress ipAddr = InetAddress.getByName("127.0.0.1");
        Socket socket = new Socket(ipAddr, 1000);

        in = new Scanner(socket.getInputStream());
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        scanner = new Scanner(System.in);

        while (true) {
            while (in.hasNextLine()) {
                temp = in.nextLine();
                if (temp.contentEquals("endSession") || temp.contentEquals("endReading")) {
                    break;
                }
                System.out.println(temp);
            }

            if (temp.contentEquals("endSession")) {
                break;
            }

            command = scanner.nextLine();
            out.println(command);
        }
        scanner.close();
        socket.close();
    }
}