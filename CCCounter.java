import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

/*
Viene dato un file JSON compresso (in formato GZIP) contenente i conti correnti di una banca.

Ogni conto corrente contiene il nome del correntista ed una lista di movimenti.
I movimenti registrati per un conto corrente possono essere molto numerosi.
Per ogni movimento vengono registrati la data e la causale del movimento.
L'insieme delle causali possibili è fissato: Bonifico, Accredito, Bollettino, F24, PagoBancomat.

Progettare un'applicazione che attiva un insieme di thread.
Uno di essi legge dal file gli oggetti "conto corrente" e li passa, uno per volta, ai thread presenti in un thread pool.
Si vuole trovare, per ogni possibile causale, quanti movimenti hanno quella causale. 
La lettura dal file deve essere fatta utilizzando l'API GSON per lo streaming.

Results:
# of F24: 3998118
# of PAGOBANCOMAT: 3996664
# of ACCREDITO: 3999846
# of BONIFICO: 4001414
# of BOLLETTINO: 4003958
*/

class Transazione {
    String date;
    String reason;

    Transazione(String date, String reason) {
        this.date = date;
        this.reason = reason;
    }
}

class ContoCorrente {
    String owner;
    List<Transazione> records;

    ContoCorrente(String owner, List<Transazione> records) {
        this.owner = owner;
        this.records = records;
    }
}

class Task implements Runnable {
    ContoCorrente conto;
    Map<String, Integer> map;

    public Task(ContoCorrente conto, Map<String, Integer> map) {
        this.conto = conto;
        this.map = map;
    }

    @Override
    public void run() {
        this.conto.records.forEach((transazione) -> {
            synchronized (map) {
                map.computeIfPresent(transazione.reason, (key, value) -> value + 1);
                map.putIfAbsent(transazione.reason, 1);
            }
        });
    }
}

public class CCCounter {
    public static void main(String[] args) throws Exception {
        Map<String, Integer> map = new ConcurrentHashMap<String, Integer>(5);
        ExecutorService threadPool = Executors.newFixedThreadPool(Integer.parseInt(args[1])); // number of threads
        File file = new File(args[0]); // pathname of accounts.json

        JsonReader reader = new JsonReader(new FileReader(file));
        reader.beginArray();
        while (reader.hasNext()) {
            ContoCorrente conto = new Gson().fromJson(reader, ContoCorrente.class);
            threadPool.execute(new Task(conto, map));
        }
        reader.endArray();
        reader.close();

        threadPool.shutdown();
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        map.forEach((key, value) -> System.out.println("# of " + key + ": " + Integer.toString(value)));
    }
}
