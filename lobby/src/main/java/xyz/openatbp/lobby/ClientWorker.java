package xyz.openatbp.lobby;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

class ClientWorker implements Runnable {
    private final Socket socket;
    private ArrayList<Player> players;
    private ArrayList<Queue> queues;

    ClientWorker(Socket socket, ArrayList<Player> players, ArrayList<Queue> queues) {
        this.socket = socket;
        this.players = players;
        this.queues = queues;
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
                        JsonNode payload = request.getPayload();
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        if(queues.size() == 0){
                            for(int i = 0; i < players.size(); i++){
                                if(players.get(i).isAddress(socket.getRemoteSocketAddress().toString())){
                                    queues.add(new Queue(players.get(i),payload.get("act").asText(),payload.get("vs").asBoolean()));
                                    System.out.println("New queue size: " + queues.size());
                                    break;
                                }
                            }
                        }else{
                            int tries = 0;
                            for(int i = 0; i < queues.size(); i++){
                                Queue q = queues.get(i);
                                if(q.getSize()+1 <= 6 && q.getType().equals(payload.get("act").asText()) && q.isPvP() == payload.get("vs").asBoolean()){
                                    q.addPlayer(requestingPlayer);
                                    break;
                                }else{
                                    tries++;
                                }
                            }
                            if(tries == queues.size()){
                                queues.add(new Queue(requestingPlayer,payload.get("act").asText(),payload.get("vs").asBoolean()));
                                System.out.println("No existing type - New queue created");
                            }
                        }
                    }else if(request.getType().equalsIgnoreCase("set_avatar")){
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        Queue affectedQueue = this.findQueue(requestingPlayer);
                        requestingPlayer.setAvatar(request.getPayload());
                        affectedQueue.updatePlayer(requestingPlayer);
                    }else if(request.getType().equalsIgnoreCase(("set_ready"))){
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        Queue affectedQueue = this.findQueue(requestingPlayer);
                        requestingPlayer.setReady();
                        affectedQueue.updatePlayer(requestingPlayer);
                    }else if(request.getType().equalsIgnoreCase("leave_team")){
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        Queue affectedQueue = this.findQueue(requestingPlayer);
                        affectedQueue.removePlayer(requestingPlayer);
                        if(affectedQueue.getSize() == 0){
                            queues.remove(affectedQueue);
                            System.out.println("Removed queue!");
                        }
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
        Player droppedPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
        Queue affectedQueue = this.findQueue(droppedPlayer);
        affectedQueue.removePlayer(droppedPlayer);
        if(affectedQueue.getSize() == 0){
            queues.remove(affectedQueue);
            System.out.println("Removed queue!");
        }
        players.remove(droppedPlayer);
        System.out.println("Player removed!");
        try {
            socket.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Player findPlayer(String address){
        for(int i = 0; i < players.size(); i++){
            if(players.get(i).isAddress(address)) return players.get(i);
        }
        return null;
    }

    private Queue findQueue(Player p){
        for(int i = 0; i < queues.size(); i++){
            if(queues.get(i).findPlayer(p)) return queues.get(i);
        }
        return null;
    }

    private Queue findQueue(String conn){
        for(int i = 0; i < queues.size(); i++){
            if(queues.get(i).findPlayer(conn)) return queues.get(i);
        }
        return null;
    }
}
