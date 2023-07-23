package xyz.openatbp.lobby;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;

public class ATBPLobbyServer {
    private static final ArrayList<Player> players = new ArrayList<>();
    private static final ArrayList<Queue> queues = new ArrayList<>();
    public static void main(String[] args) {
        if (!Config.loadConfig()) {
            System.out.println("Could not load or create config.properties");
            System.exit(-1);
        }
        if (args.length == 1) {
            try {
                createDungeonServer(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                printUsage();
            }
        } else {
            createDungeonServer(Config.getInt("lobby.port"));
        }
    }

    private static void printUsage() {
        System.out.println("Usage: ATBPLobbyServer.jar [port]");
    }

    private static void createDungeonServer(int port) {
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            System.out.println("DungeonServer running on port " + port);
        } catch (IOException ex) {
            System.out.println("DungeonServer could not listen on port " + port);
            ex.printStackTrace();
            System.exit(-1);
        }
        while (true) {
            ClientWorker worker;
            try {
                worker = new ClientWorker(server.accept(),players,queues);
                Thread thread = new Thread(worker);
                thread.start();
            } catch (IOException ex) {
                System.out.println("DungeonServer accept failed");
                ex.printStackTrace();
                System.exit(-1);
            }
        }
    }
}
