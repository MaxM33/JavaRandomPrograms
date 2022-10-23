import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Player implements Runnable {
    private Socket socket;
    Scanner in;
    PrintWriter out;
    int playerHP, potionNumber, monsterHP, playerCapHP, monsterCapHP;

    Player(Socket socket) {
        this.socket = socket;
        try {
            in = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    public void generatePlayer() {
        this.playerHP = (int) (Math.random() * 15) + 10; // playerHP generated between 10 and 25
        this.potionNumber = (int) (Math.random() * 4) + 1; // potionNumber generated between 1 and 5
        this.playerCapHP = this.playerHP;
    }

    public void generateMonster() {
        this.monsterHP = (int) (Math.random() * 10) + 20; // monsterHP generated between 20 and 30
        this.monsterCapHP = this.monsterHP;
    }

    public void playerAttack() {
        double failChance = Math.random();
        if (failChance < 0.80) { // player missing chance is 20%
            int damageDealt = (int) (Math.random() * 4) + 2;
            this.monsterHP -= damageDealt;
            out.printf("\nYou dealt %s damage to Monster\n", damageDealt);
        } else
            out.printf("\nYou missed...\n");
    }

    public void monsterAttack() {
        double failChance = Math.random();
        if (failChance < 0.70) { // monster missing chance is 30%
            int damageReceived = (int) (Math.random() * 3) + 3;
            this.playerHP -= damageReceived;
            out.printf("You received %s damage from Monster\n", damageReceived);
        } else
            out.printf("Monster missed!\n");
    }

    public void displayStats() {
        out.printf(
                "\n********************\nYour HP : %s/%s\nYour potions : %s\nMonster's HP : %s/%s\n********************\n\nYou can:\n- attack\n- use potion (if available)\n- exit\n\n",
                this.playerHP, this.playerCapHP,
                this.potionNumber, this.monsterHP, this.monsterCapHP);
    }

    public void run() {
        try {
            boolean end = false, exit = false, won = false, lost = false, tied = false;

            while (!end) {
                generatePlayer();
                generateMonster();
                displayStats();

                out.printf("endReading\n");
                String cmd = in.nextLine();

                while (!won && !lost && !tied && !exit) {
                    switch (cmd) {
                        case "attack":
                            playerAttack();
                            monsterAttack();
                            if (this.monsterHP <= 0) {
                                won = true;
                                this.monsterHP = 0; // this is just to not show negative HP
                            }
                            if (this.playerHP <= 0) {
                                lost = true;
                                this.playerHP = 0;
                            }
                            if (won && lost) {
                                won = false;
                                lost = false;
                                tied = true;
                            }
                            displayStats();
                            break;

                        case "use potion":
                            if (this.potionNumber > 0) {
                                this.playerHP += 6; // potions heal you for 6 HP
                                this.potionNumber--;
                                if (this.playerHP > this.playerCapHP) {
                                    this.playerHP = this.playerCapHP;
                                }
                                displayStats();
                            } else {
                                out.printf("\nNo potions left!\n");
                            }
                            break;

                        case "exit":
                            exit = true;
                            end = true;
                            out.printf("\nYour session is closing.\n");
                            out.printf("endSession\n");
                            break;

                        default:
                            out.printf("\nInvalid command!\n");
                            break;
                    }

                    if (!won && !lost && !tied) {
                        out.printf("endReading\n");
                        try {
                            cmd = in.nextLine();
                        } catch (NoSuchElementException s) {
                        }
                    }
                }

                if (!exit) {
                    if (won || tied) {
                        if (tied) {
                            out.printf("\nYou have tied... Do you want to play again? y/n\n");
                        } else {
                            out.printf("\nYou have won! Monster has been defeated. Do you want to play again? y/n\n");
                        }

                        out.printf("endReading\n");
                        cmd = in.nextLine();

                        if (cmd.contentEquals("n")) {
                            end = true;
                            out.printf("\nYour session is closing.\n");
                            out.printf("endSession\n");
                        } else {
                            won = false;
                            lost = false;
                            tied = false;
                            out.printf("\nGenerating new game...\n");
                        }
                    } else if (lost) {
                        end = true;
                        out.printf("\nYou have lost... Your session is closing.\n");
                        out.printf("endSession\n");
                    }
                }
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

public class Server {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(1000)) {
            System.out.println("Server is running...");
            ExecutorService threadpool = Executors.newFixedThreadPool(10);
            while (true) {
                threadpool.execute(new Player(server.accept()));
            }
        }
    }
}