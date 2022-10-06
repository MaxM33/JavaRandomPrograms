import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
Scrivere un programma che conta le occorrenze dei caratteri alfabetici (lettere dalla "A" alla "Z") in un insieme di file di testo.
Il programma prende in input una serie di percorsi di file testuali e per ciascuno di essi conta le occorrenze dei caratteri,
ignorando eventuali caratteri non alfabetici (come per esempio le cifre da 0 a 9). 
Per ogni file, il conteggio viene effettuato da un apposito task e tutti i task attivati vengono gestiti tramite un pool di thread. 
I task registrano i loro risultati parziali all'interno di una ConcurrentHashMap. 
Prima di terminare, il programma stampa su un apposito file di output il numero di occorrenze di ogni carattere. 
Il file di output contiene una riga per ciascun carattere ed è formattato come segue:

<carattere 1>,<numero occorrenze>
<carattere 2>,<numero occorrenze>
...
<carattere N>,<numero occorrenze>
*/

class Task implements Runnable {
    String filename;
    Map<String, Integer> map;

    public Task(String filename, Map<String, Integer> map) {
        this.filename = filename;
        this.map = map;
    }

    @Override
    public void run() {
        int c = 0;
        File file = new File(filename);
        try (FileReader fr = new FileReader(file)) {
            BufferedReader br = new BufferedReader(fr);
            while ((c = br.read()) != -1) {
                if ((c >= 65 && c <= 90) || (c >= 97 && c <= 122)) {
                    String character = String.valueOf(Character.toLowerCase((char) c));
                    synchronized (map) {
                        map.computeIfPresent(character, (key, value) -> value + 1);
                        map.putIfAbsent(character, 1);
                    }
                }
            }
            br.close();
            fr.close();
        } catch (IOException e) {
        }
    }
}

public class Occorrenze {
    public static void main(String[] args) {
        Map<String, Integer> map = new ConcurrentHashMap<String, Integer>(26);
        ExecutorService threadPool = Executors.newFixedThreadPool(args.length);
        for (int i = 0; i < args.length; i++) {
            threadPool.execute(new Task(args[i], map));
        }
        threadPool.shutdown();

        try {
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (Exception e) {
        }

        try (FileWriter writer = new FileWriter("output.txt")) {
            map.forEach((key, value) -> {
                try {
                    writer.write(key + "," + Integer.toString(value) + "\n");
                } catch (IOException e) {
                }
            });
            writer.close();
        } catch (IOException e) {
        }
    }
}