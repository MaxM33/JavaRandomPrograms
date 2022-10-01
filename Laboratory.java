import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

// java Laboratory X Y Z (where X is number of Students, Y is number of Thesis worker, Z is number of Professors)

class Computer {
	int number;

	public Computer(int number) {
		this.number = number;
	}

	public int getNumber() {
		return number;
	}
}

class Studente extends Thread {
	PCQueue PCqueue;
	int number;

	public Studente(PCQueue queue, int number) {
		this.PCqueue = queue;
		this.number = number;
	}

	public int getNumber() {
		return number;
	}

	@Override
	public void run() {
		int k = (int) (Math.random() * 3) + 1;
		// System.out.printf("Studente %s will access %s times\n", number, k);
		for (int i = 0; i < k; i++) {
			Long duration = (long) (Math.random() * 5) + 1;
			Computer currentPC = this.PCqueue.takePC("Studente", 0);
			System.out.printf("Studente %s got PC %s for %s seconds\n", number, currentPC.getNumber(),
					duration);
			try {
				Thread.sleep(duration * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			this.PCqueue.leavePC(currentPC, "Studente");
			System.out.printf("Studente %s left PC %s\n", number, currentPC.getNumber());
			duration = (long) (Math.random() * 5) + 1;
			try {
				Thread.sleep(duration * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

class Tesista extends Thread {
	PCQueue PCqueue;
	int number;

	public Tesista(PCQueue queue, int number) {
		this.PCqueue = queue;
		this.number = number;
	}

	public int getNumber() {
		return number;
	}

	@Override
	public void run() {
		int k = (int) (Math.random() * 3) + 1;
		int index = (int) (Math.random() * 19) + 1;
		// System.out.printf("Tesista %s will access %s times\n", number, k);
		for (int i = 0; i < k; i++) {
			Long duration = (long) (Math.random() * 5) + 1;
			Computer currentPC = this.PCqueue.takePC("Tesista", index);
			System.out.printf("Tesista %s got PC %s for %s seconds\n", number, currentPC.getNumber(),
					duration);
			try {
				Thread.sleep(duration * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			this.PCqueue.leavePC(currentPC, "Tesista");
			System.out.printf("Tesista %s left PC %s\n", number, currentPC.getNumber());
			duration = (long) (Math.random() * 5) + 1;
			try {
				Thread.sleep(duration * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

class Professore extends Thread {
	PCQueue PCqueue;
	int number;

	public Professore(PCQueue queue, int number) {
		this.PCqueue = queue;
		this.number = number;
	}

	public int getNumber() {
		return number;
	}

	@Override
	public void run() {
		int k = (int) (Math.random() * 3) + 1;
		// System.out.printf("Professore %s will access %s times\n", number, k);
		for (int i = 0; i < k; i++) {
			Long duration = (long) (Math.random() * 5) + 1;
			Computer currentPC = this.PCqueue.takePC("Professore", 0);
			System.out.printf("Professore %s got PC %s for %s seconds\n", number, currentPC.getNumber(),
					duration);
			try {
				Thread.sleep(duration * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			this.PCqueue.leavePC(currentPC, "Professore");
			System.out.printf("Professore %s left PC %s\n", number, currentPC.getNumber());
			duration = (long) (Math.random() * 5) + 1;
			try {
				Thread.sleep(duration * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

// contains available computers
class PCQueue {
	ArrayBlockingQueue<Computer> items;
	int assignedTesista, assignedProfessore, waitingProfessore, waitingTesista;

	public PCQueue(int size) {
		items = new ArrayBlockingQueue<Computer>(size);
		assignedTesista = 0;
		assignedProfessore = 0;
		waitingProfessore = 0;
		waitingTesista = 0;
	}

	public synchronized Computer takePC(String user, int index) {
		if (user.compareTo("Studente") == 0) {
			while (items.size() == 0 || assignedProfessore > 0 || waitingProfessore > 0 || waitingTesista > 0) {
				try {
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			Computer ret = items.poll();
			notifyAll();
			return ret;
		} else if (user.compareTo("Tesista") == 0) { // Tesista should stay on the same pc, here it doesn't because 
							     // contains() returns false even though should be true
			Computer toGet = new Computer(index);
			waitingTesista++;
			while (items.size() == 0 /* || items.contains(toGet) == false */ || assignedProfessore > 0
					|| waitingProfessore > 0) {
				try {
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			waitingTesista--;
			// items.remove(toGet);
			Computer ret = items.poll(); ///////
			notifyAll();
			return ret; // toGet
		} else if (user.compareTo("Professore") == 0) {
			waitingProfessore++;
			while (items.size() != 20) {
				try {
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			waitingProfessore--;
			assignedProfessore++;
			Computer ret = items.poll();
			notifyAll();
			return ret;
		} else {
			System.out.printf("Something wrong in taking the computer\n");
			return null;
		}
	}

	public synchronized void leavePC(Computer computer, String user) {
		if (user.compareTo("Professore") == 0) {
			assignedProfessore--;
		}
		items.add(computer);
		notifyAll();
	}
}

public class Laboratory {
	public static void main(String[] args) {
		int numberStudente = Integer.parseInt(args[0]);
		int numberTesista = Integer.parseInt(args[1]);
		int numberProfessore = Integer.parseInt(args[2]);

		PCQueue queue = new PCQueue(20);

		// size = 20
		for (int i = 1; i <= 20; i++) {
			queue.items.add(new Computer(i));
		}

		// Assuming that numberStudente >= 4 and numberTesista >= 2, this is just to mix
		// things up without making fancy randomizer

		for (int i = 1; i <= 2; i++) {
			Thread studThread = new Thread(new Studente(queue, i));
			studThread.start();
			Thread tesiThread = new Thread(new Tesista(queue, i));
			tesiThread.start();
		}
		for (int i = 3; i <= 4; i++) {
			Thread studThread = new Thread(new Studente(queue, i));
			studThread.start();
		}

		// now the straight initialization
		for (int i = 1; i <= numberProfessore; i++) {
			Thread profThread = new Thread(new Professore(queue, i));
			profThread.start();
		}
		for (int i = 3; i <= numberTesista; i++) {
			Thread tesiThread = new Thread(new Tesista(queue, i));
			tesiThread.start();
		}
		for (int i = 5; i <= numberStudente; i++) {
			Thread studThread = new Thread(new Studente(queue, i));
			studThread.start();
		}
	}
}
