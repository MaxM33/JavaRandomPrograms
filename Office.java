import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Scanner;

/*
	This program is very similar to the multithreaded assignment
	written in C language.
*/

class Client implements Runnable {
	private int clientNumber;

	public Client(int clientNumber) {
		this.clientNumber = clientNumber;
	}

	public int getNumber() {
		return this.clientNumber;
	}

	@Override
	public void run() {
		try {
			Long duration = (long) (Math.random() * 10);
			System.out.printf("Client %s will finish in %d seconds.\n", clientNumber, duration);
			Thread.sleep(duration * 1000);
			System.out.printf("Client %s left the office.\n", clientNumber);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

public class Office {
	public static void main(String[] args) {
		BlockingQueue<Client> firstqueue = new LinkedBlockingQueue<Client>();

		System.out.printf("How many clients are about to enter? ");
		Scanner scan = new Scanner(System.in);
		int clients = scan.nextInt();

		for (int i = 1; i <= clients; i++) {
			firstqueue.add(new Client(i));
		}
		System.out.printf("\nAll clients entered the first room.\n\n");

		ThreadPoolExecutor sportelli = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
				new ArrayBlockingQueue<Runnable>(4));
		
		// max 8 clients are allowed to stay in the second queue
		while (firstqueue.peek() != null) {
			if (sportelli.getQueue().size() < 4) {
				Client currentClient = firstqueue.poll();
				System.out.printf("Client %s entered the second room.\n", currentClient.getNumber());
				sportelli.submit(currentClient);
			}
		}
		sportelli.shutdown();
	}
}
