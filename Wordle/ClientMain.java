import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ClientMain {

    // funzione per richiedere username e password durante la register e il login.
    public static String[] askCredentials(BufferedReader input) throws IOException {
        String[] credentials = new String[2];
        System.out.println("Enter your username:");
        credentials[0] = input.readLine();
        System.out.println("Enter your password:");
        credentials[1] = input.readLine();
        return credentials;
    }

    // funzione che verifica se la parola passata è presente nel file delle parole.
    public static boolean checkVocabulary(String word) throws IOException {
        BufferedReader reader = new BufferedReader(
                new FileReader("files/words.txt"));
        String line = reader.readLine();
        while (line != null) {
            if (line.contentEquals(word)) {
                reader.close();
                return true;
            }
            line = reader.readLine();
        }
        reader.close();
        return false;
    }

    // funzione che ascolta sulla connessione delle parole. Se ci sono più parole
    // nel buffer (magari il client è stato inattivo per diverso tempo),vengono
    // scartate tutte tranne l'ultima, cioè la parola corrente.
    public static String updateWord(BufferedReader inWord) throws IOException {
        String result = new String();
        while (inWord.ready()) {
            result = inWord.readLine();
        }
        if (result.length() != 10) {
            return null;
        }
        return result;
    }

    public static void main(String[] args) throws IOException, UnknownHostException {

        // setup dei parametri definiti in client_settings.
        String correctLine = Files.readAllLines(Paths.get("files/client_settings.txt")).get(1);
        int port = Integer.parseInt(correctLine.substring(36, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/client_settings.txt")).get(2);
        int portWord = Integer.parseInt(correctLine.substring(25, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/client_settings.txt")).get(3);
        int portMulticast = Integer.parseInt(correctLine.substring(18, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/client_settings.txt")).get(4);
        int TTL = Integer.parseInt(correctLine.substring(34, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/client_settings.txt")).get(5);
        int timeoutReceive = Integer.parseInt(correctLine.substring(46, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/client_settings.txt")).get(6);
        String addr = correctLine.substring(25, correctLine.length());
        correctLine = Files.readAllLines(Paths.get("files/client_settings.txt")).get(7);
        String multaddr = correctLine.substring(21, correctLine.length());

        int guesses = 0;
        boolean isLogged = false, isGuessed = false;
        String[] credentials = new String[2];
        String username = new String(), line = new String(), response = new String(), wordToGuess = new String();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in)); // stdin.

        // indirizzo del localhost.
        InetAddress ipAddr = InetAddress.getByName(addr);

        // la connessione request/response.
        Socket socket = new Socket(ipAddr, port);
        InputStream instream = socket.getInputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(instream));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        // la connessione per le parole.
        Socket socketWord = new Socket(ipAddr, portWord);
        InputStream stream = socketWord.getInputStream();
        BufferedReader inWord = new BufferedReader(new InputStreamReader(stream));

        // un loop in cui il client aspetta un messaggio
        // da parte dell'utente sullo standard input. Alla ricezione,
        // manda la relativa richiesta al server, che agisce di conseguenza.
        // Termina quando il client farà login con successo.
        System.out.println("\nType the action you want to do:\n- register\n- login\n");
        while (!isLogged) {
            response = input.readLine();
            switch (response) {

                case "register":
                    credentials = askCredentials(input);
                    out.println("register," + credentials[0] + "," + credentials[1]);
                    response = in.readLine();
                    System.out.println(response);
                    break;

                case "login":
                    credentials = askCredentials(input);
                    out.println("login," + credentials[0] + "," + credentials[1]);
                    response = in.readLine();
                    System.out.println(response);
                    response = in.readLine();
                    if (!response.contentEquals("false")) {
                        isLogged = true;
                        username = credentials[0];
                        guesses = Integer.parseInt(in.readLine());
                        isGuessed = Boolean.parseBoolean(in.readLine());
                    }
                    break;

                default:
                    System.out.println("ERROR - Invalid action. Please retry.\n");
                    break;
            }
        }

        // il client si unisce al gruppo multicast perchè
        // ha fatto login con successo.
        InetAddress multiAddr = InetAddress.getByName(multaddr);
        MulticastSocket multiSocket = new MulticastSocket(portMulticast);
        multiSocket.setSoTimeout(timeoutReceive);
        multiSocket.setTimeToLive(TTL);
        multiSocket.joinGroup(multiAddr);

        // struttura dati per lo storage delle notifiche ricevute.
        ArrayList<String> notificationList = new ArrayList<String>();

        // il loop dove il client effettua tutte le richieste durante il gioco,
        // il client invia messaggi con un certo format al server. Alcune richieste
        // sono fatte esplicitamente dal giocatore, mentre altre sono fatte dal client
        // per ricevere certi dati.
        while (isLogged) {

            // questa parte tra i due while serve a capire se un giocatore, che ha appena
            // fatto il login, ha già fatto dei progressi sulla parola corrente.
            // In tal caso (può darsi che il client abbia fatto logout dopo aver fatto
            // qualche tentativo), quei progressi temporanei vengono ripristinati.
            // Se la parola nel frattempo è cambiata, i progressi vengono resettati.
            wordToGuess = updateWord(inWord);
            if (wordToGuess == null) {
                // se il giocatore ha terminato i tentativi, o ha già indovinato la parola
                // non può più giocare e deve aspettare la generazione di una nuova parola.
                // Può refreshare con qualsiasi tasto o fare logout.
                System.out.println(
                        "\nYou can't guess this word anymore.\nYou have to wait for a new word or you can logout (no username needed).\nType anything to refresh...");
                response = input.readLine();
                if (response.contentEquals("logout")) {
                    out.println("logout," + username);
                    response = in.readLine();
                    System.out.println(response);
                    response = in.readLine();
                    if (response.contentEquals("true")) {
                        isLogged = false;
                    }
                }
                continue;
            } else {
                // se non è null, la parola è cambiata, quindi la invia al server.
                // La prima volta che un client esegue il programma, entrerà sicuramente
                // in questo blocco. Per capire se il client in questione ha già fatto
                // progressi sulla parola corrente, quest'ultima viene inviata al server
                // che farà adeguati controlli e ritornerà "reset" o "keep".
                out.println("word," + wordToGuess + "," + username);
                response = in.readLine();
                if (response.contentEquals("reset")) {
                    guesses = 12;
                    isGuessed = false;
                    System.out.println("\nWord has changed, try guessing it!");
                }
            }

            System.out.printf(
                    "\nHi %s!\nThis is what you can do:\n1) sendWord <word> (to try guessing the current word)\n2) skip (to skip and refresh on current word, if the skipped word was the actual current word, you will have to wait for a new word)\n3) sendStats (to get your account stats)\n4) showSharings (to show notifications about other's results)\n5) logout <username> (to exit the session)\n\nGuesses remaining: %s\n\n",
                    username, guesses);

            // loop del gioco, viene eseguito finchè il giocatore non ha indovinato,
            // ha ancora tentativi o non fa il logout.
            while (guesses > 0 && isLogged && !isGuessed) {

                // per x secondi riceve tutte le notifiche disponibili,
                // le conserva e rimane in ascolto.
                try {
                    byte[] buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    multiSocket.receive(packet);
                    String message = new String(packet.getData(), StandardCharsets.UTF_8);
                    notificationList.add(message);
                } catch (SocketTimeoutException io) {
                }

                // controllo del stdin.
                while (input.ready()) {
                    line = input.readLine();
                    String[] parts = line.split(" ");
                    if (parts.length == 2) {
                        switch (parts[0]) {

                            // i controlli eseguiti quando viene fatto
                            // un tentativo di indovinare la parola.
                            case "sendWord":
                                if (parts[1].length() != 10) {
                                    System.out.println("\nWord is not 10 characters.\n");
                                    continue;
                                }
                                if (!checkVocabulary(parts[1])) {
                                    System.out.println("\nWord is not in vocabulary.\n");
                                    continue;
                                }
                                guesses--;
                                // decrementa i guesses lato server.
                                out.println("guesses," + Integer.toString(guesses));
                                System.out.printf("\n");
                                for (int i = 0; i < 10; i++) {
                                    System.out.printf("%s  ", parts[1].charAt(i));
                                }
                                System.out.printf("\n");
                                for (int i = 0; i < 10; i++) {
                                    if (wordToGuess.charAt(i) == parts[1].charAt(i)) {
                                        System.out.printf("+  ");
                                    } else if (wordToGuess.indexOf(parts[1].charAt(i)) != -1) {
                                        System.out.printf("?  ");
                                    } else {
                                        System.out.printf("X  ");
                                    }
                                }
                                System.out.printf("\n\n");
                                if (wordToGuess.contentEquals(parts[1])) {
                                    isGuessed = true;
                                    out.println("won," + username + "," + Integer.toString(12 - guesses));
                                    System.out.println("YOU HAVE WON!! Do you want to share it with everybody? y/n");
                                    response = input.readLine();
                                    if (response.contentEquals("y")) {
                                        out.println(
                                                "share," + username + "," + wordToGuess + ","
                                                        + Integer.toString(12 - guesses)); // richiesta di share.
                                    } else {
                                        System.out.println("\nAlright then...keep your secrets...");
                                    }
                                }
                                // se i tentativi rimanenti sono 0 e la parola
                                // non è stata indovinata è una sconfitta e viene
                                // eseguita la stessa procedura dello skip nel lato server.
                                else if (guesses > 0) {
                                    System.out.printf("Guesses remaining: %s\n\n", guesses);
                                } else if (guesses == 0 && !isGuessed) {
                                    out.println("lost," + username);
                                }
                                break;

                            // richiesta di logout, invia al server e
                            // viene eseguita la relativa funzione di logout.
                            case "logout":
                                out.println("logout," + parts[1]);
                                response = in.readLine();
                                System.out.println(response);
                                response = in.readLine();
                                if (response.contentEquals("true")) {
                                    isLogged = false;
                                }
                                break;

                            default:
                                System.out.println("ERROR - Invalid action. Please retry.\n");
                                continue;
                        }
                    } else if (parts.length == 1) {
                        switch (line) {

                            // richiesta delle statistiche relative al giocatore,
                            // invia al server viene eseguita la relativa funzione.
                            case "sendStats":
                                out.println("sendStats," + username);
                                response = in.readLine();
                                String[] stats = response.split(",");
                                float percentage = 0;
                                if (Float.parseFloat(stats[1]) != 0) {
                                    percentage = (Float.parseFloat(stats[2]) / Float.parseFloat(stats[1])) * 100;
                                }
                                System.out.printf(
                                        "\nusername: %s\n# of matches: %s\n# of wins: %s\nwin percentage: %.1f\ncurrent win streak: %s\nmax win streak: %s\naverage tries per win: %.1f\n",
                                        stats[0], stats[1], stats[2], percentage, stats[3], stats[4],
                                        Float.parseFloat(stats[5]));
                                break;

                            // printa tutte le notifiche presenti nella lista
                            // e le rimuove perchè sono già state visualizzate.
                            case "showSharings":
                                System.out.println("\nRetrieving notifications, please wait...\n");
                                for (int i = 0; i < notificationList.size(); i++) {
                                    System.out.println(notificationList.get(i));
                                    notificationList.remove(i);
                                }
                                System.out.println("Done.");
                                break;

                            // se un giocatore non vuole più provare ad indovinare la parola,
                            // può skipparla. Se ce ne sono altre nel buffer, verranno scartate
                            // tranne l'ultima, quindi il gioco viene aggiornato alla parola
                            // corrente. Se il giocatore skippa la parola corrente, deve aspettare
                            // la generazione della prossima parola. Uno skip azzera i tentativi
                            // rimanenti sulla parola, quindi conta come una sconfitta.
                            case "skip":
                                System.out.println("\nWord skipped.");
                                out.println("lost," + username);
                                isGuessed = true;
                                continue;

                            case "show"://////////////////////////////// per testing.
                                System.out.println(wordToGuess);
                                continue;

                            default:
                                System.out.println("ERROR - Invalid action. Please retry.\n");
                                continue;
                        }
                    } else {
                        System.out.println("ERROR - Invalid command.\n");
                        continue;
                    }
                }
            }
        }
        input.close();
        socketWord.close();
        socket.close();
    }
}