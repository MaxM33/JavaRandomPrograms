This one is a simple game with an ugly textual interface. It is based on a socket connection between each client and a server.
It is a round game where each client plays in his own match and, as a warrior, his goal is to defeat the monster he is facing.
The server randomly generates the parameters of each game session (HP of monster and warrior, number of potions the warrior holds),
whereas the damage of the two and their chance to fail are calculated each round. The client can exit the game at every time he wants.
If the player wins or ties, the server asks if he wants to play another game, whereas if he loses, he must quit the game.
