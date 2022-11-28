import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

// Questo oggetto rappresenta i progressi permanenti 
// che devono essere salvati e ripristinati al riavvio del server.
// Rappresenta anche un elenco di tutti gli utenti registrati finora.
class Account {
    String username, password;
    int currentWinStreak, maxWinStreak, numberOfMatches, numberOfWins;
    float averageTries;

    Account(String username, String password) {
        this.username = username;
        this.password = password;
        this.currentWinStreak = 0;
        this.maxWinStreak = 0;
        this.numberOfMatches = 0;
        this.numberOfWins = 0;
        this.averageTries = 0;
    }
}

// Questo oggetto rappresenta i dati temporanei che servono
// a gestire le variabili di una partita e vengono resettati
// ad ogni cambio di parola. Si resettano al riavvio del server.
class TemporaryPlayerData {
    String username, word;
    int guesses;
    boolean isLogged;
    boolean isGuessed;

    TemporaryPlayerData(String username) {
        this.username = username;
        this.guesses = 12;
        this.isLogged = true;
        this.isGuessed = false;
        this.word = "";
    }
}

class Player implements Runnable {
    Socket socket;
    ArrayList<TemporaryPlayerData> tempDataList; // lista dei dati temporanei condivisa tra i thread.
    ArrayList<Account> userList; // lista dei dati permanenti condivisa tra i thread.
    String username; // viene salvato lo username con cui viene fatto il login.
    boolean isLogged; // per impedire ad un altro client di fare login con lo stesso username.

    InputStream instream;
    BufferedReader in;
    PrintWriter out;

    Player(Socket socket, ArrayList<TemporaryPlayerData> tempDataList, ArrayList<Account> userList) throws IOException {
        this.socket = socket;
        this.tempDataList = tempDataList;
        this.userList = userList;
        this.isLogged = false;

        instream = socket.getInputStream();
        in = new BufferedReader(new InputStreamReader(instream));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    // funzione per cercare dati permanenti appartenenti allo username passato.
    public int searchUser(String username, String password) throws IOException {
        for (int i = 0; i < this.userList.size(); i++) {
            Account item = this.userList.get(i);
            if (item.username.contentEquals(username) && item.password.contentEquals(password)) {
                return 1;
            } else if (item.username.contentEquals(username)) {
                return 2;
            }
        }
        return 0;
    }

    // funzione che aggiunge un utente, se nuovo, nella lista dati permanenti.
    public synchronized int register(String username, String password) throws IOException {
        if (username.isBlank() || password.isBlank()) {
            out.println("ERROR - username/password is blank.");
            return -1;
        }
        int isRegistered = searchUser(username, password);
        if (isRegistered == 2) {
            out.println("ERROR - username is taken already.");
            return -1;
        } else if (isRegistered == 1) {
            out.println("ERROR - user is registered already. Please log in.");
            return -1;
        } else if (isRegistered == 0) {
            Account newAccount = new Account(username, password);
            this.userList.add(newAccount);
            out.println("OK - user successfully registered! Please log in.");
            return 0;
        } else {
            out.println("Something went wrong...please retry.");
            return -1;
        }
    }

    // funzione che verifica che lo username passato sia registrato
    // così da poter fare login. Restituisce lo stato della partita
    // di un player riguardo l'ultima parola che ha provato ad indovinare.
    public synchronized String[] login(String username, String password) throws IOException {
        if (username.isBlank() || password.isBlank()) {
            out.println("ERROR - username/password is blank.");
            return null;
        }
        String[] data = new String[2];
        boolean found = false;
        int isRegistered = searchUser(username, password);
        if (isRegistered == 1) {
            for (int i = 0; i < this.tempDataList.size(); i++) {
                TemporaryPlayerData item = this.tempDataList.get(i);
                if (item.username.contentEquals(username)) {
                    if (item.isLogged) {
                        out.println("ERROR - user is already logged.");
                        return null;
                    }
                    item.isLogged = true;
                    data[0] = Integer.toString(item.guesses);
                    data[1] = Boolean.toString(item.isGuessed);
                    this.username = username;
                    this.isLogged = true;
                    this.tempDataList.set(i, item);
                    found = true;
                    break;
                }
            }
            if (!found) {
                TemporaryPlayerData newPlayer = new TemporaryPlayerData(username);
                data[0] = Integer.toString(newPlayer.guesses);
                data[1] = Boolean.toString(newPlayer.isGuessed);
                this.username = newPlayer.username;
                this.isLogged = true;
                this.tempDataList.add(newPlayer);
            }
            out.println("OK - login successful!");
            return data;
        } else {
            out.println("ERROR - invalid username/password. Please retry.");
            return null;
        }
    }

    // funzione per il logout del giocatore, lo username deve essere
    // effettivamente quello con cui ha fatto login.
    public synchronized int logout(String username) throws IOException {
        if (username.isBlank()) {
            out.println("ERROR - username/password is blank.");
            return -1;
        }
        if (!username.contentEquals(this.username)) {
            out.println("ERROR - you are not logged with this username.");
            return -1;
        }
        boolean found = false;
        for (int i = 0; i < this.tempDataList.size(); i++) {
            TemporaryPlayerData item = this.tempDataList.get(i);
            if (item.username.contentEquals(username)) {
                item.isLogged = false;
                this.isLogged = false;
                this.tempDataList.set(i, item);
                found = true;
                break;
            }
        }
        if (!found) {
            out.println("ERROR - user is not logged in.");
            return -1;
        } else {
            out.println("OK - log out successful. Bye!");
            return 0;
        }
    }

    // funzione che riceve ad ogni tentativo il numero di tries
    // rimasti al giocatore.
    public synchronized void updateGuesses(int guesses) throws IOException {
        for (int i = 0; i < this.tempDataList.size(); i++) {
            TemporaryPlayerData item = this.tempDataList.get(i);
            if (item.username.contentEquals(this.username)) {
                item.guesses = guesses;
                this.tempDataList.set(i, item);
                break;
            }
        }
    }

    // funzione che controlla se la parola è stata cambiata e in questo caso
    // resetta i dati temporanei del giocatore.
    public synchronized void checkIfWordChanged(String word, String username, PrintWriter out) throws IOException {
        for (int i = 0; i < this.tempDataList.size(); i++) {
            TemporaryPlayerData item = this.tempDataList.get(i);
            if (username.contentEquals(item.username)) {
                if (!word.contentEquals(item.word)) {
                    out.println("reset");
                    item.guesses = 12;
                    item.word = word;
                    item.isGuessed = false;
                    this.tempDataList.set(i, item);
                } else {
                    out.println("keep");
                }
                break;
            }
        }
    }

    // funzione che aggiorna le statistiche (nella lista dei dati permanenti)
    // quando il giocatore vince. Aggiorna dei dati anche nella lista temporanea.
    public synchronized void updateAccountStatsOnWin(String username, int tries) throws IOException {
        for (int i = 0; i < this.userList.size(); i++) {
            Account item = this.userList.get(i);
            if (item.username.contentEquals(username)) {
                item.currentWinStreak++;
                item.numberOfMatches++;
                if (item.currentWinStreak >= item.maxWinStreak) {
                    item.maxWinStreak = item.currentWinStreak;
                }
                item.averageTries = ((item.averageTries * item.numberOfWins) + tries) / (item.numberOfWins + 1);
                item.numberOfWins++;
                this.userList.set(i, item);
                break;
            }
        }
        for (int i = 0; i < this.tempDataList.size(); i++) {
            TemporaryPlayerData item = this.tempDataList.get(i);
            if (item.username.contentEquals(username)) {
                item.isGuessed = true;
                this.tempDataList.set(i, item);
                break;
            }
        }
    }

    // stessa cosa di quella sopra, ma viene eseguita quando la parola viene
    // skippata o quando il giocatore perde. Aggiorna alcuni dati anche nella
    // lista temporanea.
    public synchronized void updateAccountStatsOnLoss(String username) throws IOException {
        for (int i = 0; i < this.userList.size(); i++) {
            Account item = this.userList.get(i);
            if (item.username.contentEquals(username)) {
                item.numberOfMatches++;
                item.currentWinStreak = 0;
                this.userList.set(i, item);
                break;
            }
        }
        for (int i = 0; i < this.tempDataList.size(); i++) {
            TemporaryPlayerData item = this.tempDataList.get(i);
            if (item.username.contentEquals(username)) {
                item.guesses = 0;
                this.tempDataList.set(i, item);
                break;
            }
        }
    }

    // funzione che passa al client le statistiche del giocatore.
    public void getAccountStats(String username, PrintWriter out) throws IOException {
        for (int i = 0; i < this.userList.size(); i++) {
            Account item = this.userList.get(i);
            if (item.username.contentEquals(username)) {
                out.println(item.username + "," + item.numberOfMatches + "," + item.numberOfWins + ","
                        + item.currentWinStreak + "," + item.maxWinStreak + "," + item.averageTries);
                break;
            }
        }
    }

    // printa le liste dei dati permanenti e temporanei, è per fare testing.
    public void printList() throws IOException {
        System.out.println("*****************************************");
        for (int i = 0; i < this.userList.size(); i++) {
            Account item = this.userList.get(i);
            System.out.printf("username: %s - matches: %s - wins: %s - curWS: %s - maxWS: %s - avgtries: %s\n",
                    item.username, item.numberOfMatches, item.numberOfWins, item.currentWinStreak, item.maxWinStreak,
                    item.averageTries);
        }
        System.out.println("-----------------------------------------");
        for (int i = 0; i < this.tempDataList.size(); i++) {
            TemporaryPlayerData item = this.tempDataList.get(i);
            System.out.printf("username: %s - isLogged: %s - guesses: %s - word: %s - isGuessed: %s\n",
                    item.username, item.isLogged, item.guesses, item.word, item.isGuessed);
        }
    }

    public void run() {
        String message = new String();

        // un loop in cui il server aspetta un messaggio
        // con un format predefinito dal client e agisce di conseguenza.
        // Termina quando il client farà login con successo.
        try {
            while (!this.isLogged) {
                message = in.readLine();
                String[] data = message.split(",");
                if (data.length != 3) {
                    out.println("ERROR - username/password is null.");
                    continue;
                }
                switch (data[0]) {

                    case "register":
                        register(data[1], data[2]);
                        printList();////////////////////////////////// per testing.
                        break;

                    case "login":
                        String[] res = login(data[1], data[2]);
                        if (res == null) {
                            out.println("false"); // login non andato a buon fine.
                        } else {
                            this.isLogged = true;
                            out.println("true"); // login successful.
                            out.println(res[0]); // manda il numero di tries rimanenti.
                            out.println(res[1]); // manda se la parola è stata indovinata.
                        }
                        printList();////////////////////////////////// per testing.
                        break;

                    default:
                        out.println("Invalid command.");
                        break;

                }
            }

            // setup delle settings per Multisocket.
            String correctLine = Files.readAllLines(Paths.get("files/server_settings.txt")).get(6);
            int portMulticast = Integer.parseInt(correctLine.substring(18, correctLine.length()));
            correctLine = Files.readAllLines(Paths.get("files/server_settings.txt")).get(7);
            int TTL = Integer.parseInt(correctLine.substring(34, correctLine.length()));
            correctLine = Files.readAllLines(Paths.get("files/server_settings.txt")).get(8);
            String addr = correctLine.substring(21, correctLine.length());

            // il server si unisce al gruppo multicast.
            InetAddress multiAddr = InetAddress.getByName(addr);
            MulticastSocket multiSocket = new MulticastSocket(portMulticast);
            multiSocket.setTimeToLive(TTL);
            multiSocket.joinGroup(multiAddr);

            // il loop che gestisce tutte le richieste fatte dal client durante il gioco,
            // anche qui il server sta in ascolto e riceve un messaggio con un certo format
            // e agisce in base alla richiesta. Alcune richieste sono fatte esplicitamente
            // dal giocatore, mentre altre sono fatte dal client per ricevere certi dati.
            while (this.isLogged) {
                message = in.readLine();
                String[] data = message.split(",");
                switch (data[0]) {

                    case "logout":
                        int res = logout(data[1]);
                        if (res == 0) {
                            this.isLogged = false; // il thread terminerà.
                            out.println("true"); // logout successful.
                        } else {
                            out.println("false"); // logout non a buon fine.
                        }
                        printList();//////////////////////////////// per testing.
                        break;

                    case "sendStats":
                        getAccountStats(data[1], out);
                        break;

                    case "share":
                        String notification = data[1] + " has guessed the word '" + data[2] + "' with " + data[3]
                                + " attempts."; // viene formata la stringa della notifica.
                        DatagramPacket dp = new DatagramPacket(notification.getBytes(), notification.length(),
                                multiAddr, portMulticast); // crea il datagram.
                        multiSocket.send(dp); // datagram inviato.
                        break;

                    case "word":
                        checkIfWordChanged(data[1], data[2], out);
                        break;

                    case "guesses":
                        updateGuesses(Integer.parseInt(data[1]));
                        printList();////////////////////////////////// per testing.
                        break;

                    case "won":
                        updateAccountStatsOnWin(data[1], Integer.parseInt(data[2]));
                        printList();//////////////////////////////// per testing.
                        break;

                    case "lost":
                        updateAccountStatsOnLoss(data[1]);
                        printList();//////////////////////////////// per testing.
                        break;
                }
            }
        } catch (IOException e) {
            try {
                // se un client è crashato per qualche motivo, il server fa
                // logout al posto suo.
                System.out.println("Client forced to disconnect.");
                logout(this.username);
            } catch (IOException m) {
            }
        }
    }
}

public class ServerMain {

    // funzione che genera un numero casuale, quello sarà il numero
    // della riga del file delle parole che sarà estratta.
    public static String generateNewWord() throws IOException {
        int numline = (int) (Math.random() * 30825.0);
        Stream<String> lines = Files
                .lines(Paths.get("files/words.txt"));
        String result = lines.skip(numline).findFirst().get();
        lines.close();
        return result;
    }

    // funzione che serve a monitorare lo standard input del server,
    // necessario per avviare la procedura di salvataggio dei progressi
    // permanenti sul file json.
    public static String saveStateOnInputDemand(BufferedReader input) throws IOException {
        String result = new String();
        while (input.ready()) {
            result = input.readLine();
        }
        if (result.length() != 9) {
            return null;
        }
        return result;
    }

    public static void main(String[] args) throws Exception {

        // setup dei parametri definiti in server_settings.
        String correctLine = Files.readAllLines(Paths.get("files/server_settings.txt")).get(1);
        int port = Integer.parseInt(correctLine.substring(36, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/server_settings.txt")).get(2);
        int portWord = Integer.parseInt(correctLine.substring(25, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/server_settings.txt")).get(3);
        int timeoutAccept = Integer.parseInt(correctLine.substring(44, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/server_settings.txt")).get(4);
        int timeoutAcceptWord = Integer.parseInt(correctLine.substring(46, correctLine.length()));
        correctLine = Files.readAllLines(Paths.get("files/server_settings.txt")).get(5);
        int timeoutWord = Integer.parseInt(correctLine.substring(57, correctLine.length()));

        String resultInput = new String();
        long timeStart = 0, timeEnd = 0;
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

        ServerSocket server = new ServerSocket(port); // connessione request/response.
        ServerSocket serverWord = new ServerSocket(portWord); // connessione per le parole generate.
        server.setSoTimeout(timeoutAccept); // timeout della accept() request/response.
        serverWord.setSoTimeout(timeoutAcceptWord); // timeout della accept() parole.

        ExecutorService threadpool = Executors.newCachedThreadPool();
        ArrayList<TemporaryPlayerData> tempDataList = new ArrayList<TemporaryPlayerData>();

        // lista che conterrà i socket dei client, relativi alla connessione parole.
        ArrayList<Socket> socketList = new ArrayList<Socket>();

        File file = new File("files/users.json");
        ArrayList<Account> userList = new ArrayList<Account>();

        // se ci sono già dei progressi permanenti, li ripristina.
        // Altrimenti la lista rimane vuota.
        if (!file.createNewFile()) {
            JsonReader reader = new JsonReader(new FileReader(file));
            reader.beginArray();
            while (reader.hasNext()) {
                Account account = new Gson().fromJson(reader, Account.class);
                userList.add(account);
            }
            reader.endArray();
            reader.close();
        }

        String word = generateNewWord(); // viene estratta la prima parola.
        timeStart = System.currentTimeMillis();
        System.out.println("Server is running...");
        while (true) {
            try {
                Socket socket = server.accept(); // connessione request/response
                Socket socketWord = serverWord.accept(); // connessione per parole
                System.out.println("Client connected!");
                socketList.add(socketWord);

                PrintWriter out = new PrintWriter(socketWord.getOutputStream(), true);

                // ogni x secondi viene generata
                // una nuova parola e viene inoltrata.
                timeEnd = System.currentTimeMillis();
                if (timeEnd - timeStart > timeoutWord) {
                    word = generateNewWord();
                    for (int i = 0; i < socketList.size(); i++) {
                        Socket tempsocket = socketList.get(i);
                        if (!tempsocket.isClosed()) {
                            PrintWriter tempout = new PrintWriter(tempsocket.getOutputStream(), true);
                            tempout.println(word);
                        }
                    }
                    timeStart = System.currentTimeMillis();
                } else {
                    // Altrimenti viene inoltrata la parola corrente al nuovo
                    // client.
                    out.println(word);
                }

                threadpool.execute(new Player(socket, tempDataList, userList));

                // controllo dello standard input e salvataggio se richiesto.
                resultInput = saveStateOnInputDemand(input);
                if (resultInput != null && resultInput.contentEquals("savestate")) {
                    System.out.println("Saving the server state...");
                    String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                            .toJson(userList);
                    BufferedWriter printerJSON = new BufferedWriter(new FileWriter(file));
                    printerJSON.write(json);
                    printerJSON.close();
                    System.out.println("Done.");
                }

            } catch (SocketTimeoutException so) {
                // se la accept() lancia un eccezione perchè
                // nessuno si è connesso negli ultimi y secondi,
                // viene fatto un check del tempo per la generazione
                // della parola e un check dello standard input,
                // come nel blocco di codice sopra.
                // Questo perchè se viene lanciata un'eccezione, il codice
                // dopo la accept() non viene eseguito.
                timeEnd = System.currentTimeMillis();
                if (timeEnd - timeStart > timeoutWord) {
                    word = generateNewWord();
                    for (int i = 0; i < socketList.size(); i++) {
                        Socket tempsocket = socketList.get(i);
                        if (!tempsocket.isClosed()) {
                            PrintWriter tempout = new PrintWriter(tempsocket.getOutputStream(), true);
                            tempout.println(word);
                        }
                    }
                    timeStart = System.currentTimeMillis();
                }

                resultInput = saveStateOnInputDemand(input);
                if (resultInput != null && resultInput.contentEquals("savestate")) {
                    System.out.println("Saving the server state...");
                    String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                            .toJson(userList);
                    BufferedWriter printerJSON = new BufferedWriter(new FileWriter(file));
                    printerJSON.write(json);
                    printerJSON.close();
                    System.out.println("Done.");
                }
            }
        }
    }
}