package xyz.openatbp.lobby;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

class ClientWorker implements Runnable {
    private final Socket socket;
    private ArrayList<Player> players = new ArrayList<Player>();

    ClientWorker(Socket socket) {
        this.socket = socket;
    }

    public void run(){
        System.out.println("New client: " + socket.getInetAddress());
        try {
            DataInputStream clientIn = new DataInputStream(socket.getInputStream());
            DataOutputStream clientOut = new DataOutputStream(socket.getOutputStream());

            while(!socket.isClosed()){
                System.out.println("Client address " + socket.getRemoteSocketAddress());
                Packet request = new Packet();
                if (request.receive(clientIn)) {
                    System.out.println("Received " + request.getType());
                    System.out.println("Payload " + request.getPayload().toPrettyString());
                    if(request.getType().equalsIgnoreCase("handshake")){
                        request.send(clientOut,"handshake",RequestHandler.handleHandshake(request.getPayload()));
                    }else if(request.getType().equalsIgnoreCase("login")){
                        players.add(new Player(socket,request.getPayload()));
                        request.send(clientOut,"login", RequestHandler.handleLogin(request.getPayload()));
                    }else if(request.getType().equalsIgnoreCase("auto_join")){
                        request.send(clientOut,"match_found",RequestHandler.handleMatchFound(request.getPayload()));
                        request.send(clientOut, "team_update",RequestHandler.handleTeamUpdate(request.getPayload()));
                    }else if(request.getType().equalsIgnoreCase("set_avatar")){
                        request.send(players.get(0).getOutputStream(), "team_update", RequestHandler.handleAvatarChange(request.getPayload()));
                    }else if(request.getType().equalsIgnoreCase(("set_ready"))){
                        request.send(clientOut, "team_update", RequestHandler.handleReady());
                    }
                } else {
                    this.close();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void close() {
        System.out.println("Dropping client: " + socket.getInetAddress());
        for(int i = 0; i < players.size(); i++){
            if(players.get(i).isAddress(socket.getRemoteSocketAddress().toString())){
                players.remove(i);
                System.out.println("Player removed!");
                break;
            }
        }
        try {
            socket.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
