package xyz.openatbp.lobby;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;

public class ATBPLobbyServer {
    private static ArrayList<Player> players = new ArrayList<Player>();
    private static ArrayList<Queue> queues = new ArrayList<Queue>();
    public static void main(String[] args) {
        if (args.length == 1) {
            try {
                createDungeonServer(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                printUsage();
            }
        } else {
            createDungeonServer(6778);
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
        } catch (IOException e) {
            System.out.println("DungeonServer could not listen on port " + port);
            System.out.println(e);
            System.exit(-1);
        }

        while (true) {
            ClientWorker w;
            try {
                w = new ClientWorker(server.accept(),players,queues);
                Thread t = new Thread(w);
                t.start();
            } catch (IOException e) {
                System.out.println("DungeonServer accept failed");
                System.out.println(e);
                System.exit(-1);
            }
        }
    }
}
