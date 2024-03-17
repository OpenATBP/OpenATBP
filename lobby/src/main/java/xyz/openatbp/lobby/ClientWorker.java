package xyz.openatbp.lobby;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

class ClientWorker implements Runnable {
    private final Socket socket;
    private final ArrayList<Player> players;
    private final ArrayList<Queue> queues;

    ClientWorker(Socket socket, ArrayList<Player> players, ArrayList<Queue> queues) {
        this.socket = socket;
        this.players = players;
        this.queues = queues;
        System.out.println("New Client Worker created! for " + socket.getRemoteSocketAddress().toString());
    }

    public void run(){
        System.out.println("New client: " + socket.getInetAddress());
        try {
            DataInputStream clientIn = new DataInputStream(socket.getInputStream());
            DataOutputStream clientOut = new DataOutputStream(socket.getOutputStream());

            while(!socket.isClosed()){
                for(Player p : players){
                    System.out.println("Player: " + p.getUsername() + " in players list");
                }
                for(Queue q : queues){
                    System.out.println(q.getPartyLeader() + " queue is active");
                    for(Player p : q.getPlayers()){
                        System.out.println("Queue " + q.getPartyLeader() + " has player " + p.getUsername());
                    }
                }
                System.out.println("Client address " + socket.getRemoteSocketAddress());
                Packet request = new Packet();
                if (request.receive(clientIn)) {
                    System.out.println("Received " + request.getType());
                    System.out.println("Payload " + request.getPayload().toPrettyString());
                    if(request.getType().equalsIgnoreCase("handshake")){ //Sends when client loads up the game
                        request.send(clientOut,"handshake",RequestHandler.handleHandshake(true));
                    }else if(request.getType().equalsIgnoreCase("login")){ // Sends when client logs in
                        if(this.findPlayer(socket.getRemoteSocketAddress().toString()) == null){
                            int guestNum = this.getGuestNum();
                            if(request.getPayload().get("name").asText().contains("Guest")){
                                //players.add(new Player(socket,guestNum)); //Adds logged player in to server's player list
                                //System.out.println("Guest joined! " + guestNum);
                                request.send(clientOut,"handshake",RequestHandler.handleHandshake(false));
                            }else{
                                players.add(new Player(socket,request.getPayload())); //Adds logged player in to server's player list
                                request.send(clientOut,"login", RequestHandler.handleLogin(request.getPayload(),guestNum));
                            }

                        }
                    }else if(request.getType().equalsIgnoreCase("auto_join")){ //Sends when client presses on quick match
                        JsonNode payload = request.getPayload();
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        if(requestingPlayer != null){
                            if(queues.size() == 0){ //If there are no current queues
                                queues.add(new Queue(requestingPlayer,payload.get("act").asText(),payload.get("vs").asBoolean())); //Creates new queue with player in it
                                System.out.println("New queue size: " + queues.size());
                            }else{ //If there are active queues
                                int tries = 0;
                                for (Queue q : queues) {
                                    //If the queue is not currently a premade, has enough open spots to fit the player, and is the correct game type
                                    if (q.getState() == Queue.State.MATCHMAKING && q.getSize() + 1 <= 6 && q.getType().equals(payload.get("act").asText()) && q.isPvP() == payload.get("vs").asBoolean()) {
                                        q.addPlayer(requestingPlayer); //Adds player to the queue
                                        requestingPlayer.setQueue(q);
                                        break;
                                    } else {
                                        tries++;
                                    }
                                }
                                if(tries == queues.size()){ //If there are no queues the player can join, it'll make a new one
                                    //TODO: Needs to account for multiple queues that could join together
                                    queues.add(new Queue(requestingPlayer,payload.get("act").asText(),payload.get("vs").asBoolean()));
                                    System.out.println("No existing type - New queue created");
                                }
                            }
                        }
                    }else if(request.getType().equalsIgnoreCase("set_avatar")){ //Calls when player selects a character
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        if(requestingPlayer != null){
                            Queue affectedQueue = this.findQueue(requestingPlayer);
                            requestingPlayer.setAvatar(request.getPayload()); //Updates player character
                            if (affectedQueue != null) affectedQueue.updatePlayer(requestingPlayer); //Updates queue with new player info
                        }
                    }else if(request.getType().equalsIgnoreCase(("set_ready"))){ // Calls when player readys up
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        if(requestingPlayer != null){
                            Queue affectedQueue = this.findQueue(requestingPlayer);
                            requestingPlayer.setReady(); //Updates player ready status
                            if(affectedQueue != null) affectedQueue.updatePlayer(requestingPlayer);
                        }
                    }else if(request.getType().equalsIgnoreCase("leave_team")){ //Calls when player leaves a queue or premade team
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        Queue affectedQueue = this.findQueue(requestingPlayer);
                        if(requestingPlayer != null && affectedQueue != null){
                            requestingPlayer.leaveTeam();
                            affectedQueue.removePlayer(requestingPlayer);
                            if(affectedQueue.isPremade()){ //If the queue is a premade team
                                Packet out = new Packet();
                                out.send(affectedQueue.getPartyLeader().getOutputStream(), "invite_declined", RequestHandler.handleInviteDecline(requestingPlayer.getUsername())); //Sends invite decline so host can reinvite
                            }
                            if(affectedQueue.getSize() == 0){ //If this was the last player in the team/queue, disbands the queue
                                queues.remove(affectedQueue);
                                System.out.println("Removed queue!");
                            }
                        }

                    }else if(request.getType().equalsIgnoreCase("send_invite")){ //Calls when host of team sends an invite
                        Player reqestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        Player receivingPlayer = null;
                        for (Player player : players) {
                            if (player.getUsername().equalsIgnoreCase(request.getPayload().get("player").asText())) { //Finds player getting the invite
                                receivingPlayer = player;
                            }
                        }
                        if(receivingPlayer != null){
                            Queue affectedQueue = this.findQueue(reqestingPlayer);
                            if(affectedQueue != null) request.send(receivingPlayer.getOutputStream(),"receive_invite",RequestHandler.handleInvite(affectedQueue)); //Sends the player the invite to team
                        }
                    }
                    else if(request.getType().equalsIgnoreCase("create_team")){ //Calls when choosing to make a party
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        JsonNode payload = request.getPayload();
                        queues.add(new Queue(requestingPlayer,payload.get("act").asText(),payload.get("vs").asBoolean(),true)); //Adds a new queue that is a premade
                    }else if(request.getType().equalsIgnoreCase("join_team")){ //Called when accepting an invite
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        assert requestingPlayer != null;
                        requestingPlayer.setTeam(request.getPayload().get("name").asText());
                        for (Queue queue : queues) {
                            if (queue.getPartyLeader().getUsername().equalsIgnoreCase(request.getPayload().get("name").asText())) { //Finds queue/team correlated with the invite request
                                request.send(requestingPlayer.getOutputStream(), "invite_verified", RequestHandler.handleInviteAccept());
                                queue.addPlayer(requestingPlayer);
                                break;
                            }
                        }
                    }else if(request.getType().equalsIgnoreCase("decline_invite")){ //Called when declining an invite
                        Player requestingPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        for (Queue queue : queues) {
                            if (queue.getPartyLeader().getPid() == (float) request.getPayload().get("party_leader").asDouble()) {
                                //Sends host a message that the player declined
                                request.send(queue.getPartyLeader().getOutputStream(), "invite_declined", RequestHandler.handleInviteDecline(requestingPlayer.getUsername()));
                                break;
                            }
                        }
                    }else if(request.getType().equalsIgnoreCase("unlock_team")){ //Called when sending a party into queue
                        Queue currentQueue = this.findQueue(socket.getRemoteSocketAddress().toString());
                        if(currentQueue != null){
                            int tries = 0;
                            for(int i = 0; i < queues.size(); i++){
                                if(!queues.get(i).isPremade()){ //If the queue is not a premade and matches gamemode it will add all players in the team
                                    if(queues.get(i).getType().equals(currentQueue.getType()) && queues.get(i).isPvP() == currentQueue.isPvP() && queues.get(i).getSize()+currentQueue.getSize() <= 6){ //If the queue can fit all players in the party, adds everyone
                                        queues.get(i).addPlayer(currentQueue.getPlayers());
                                        queues.remove(currentQueue); //Removes the premade queue as it combined into another queue
                                        break;
                                    }else{
                                        tries++;
                                    }
                                }else{
                                    tries++;
                                }
                            }
                            if(tries == queues.size()){ //If no queue fits the search criteria, it will create a new one
                                queues.add(new Queue(currentQueue.getPlayers(),currentQueue.getType(), currentQueue.isPvP()));
                                queues.remove(currentQueue); //Removes the premade queue as it is now public
                            }
                        }

                    }else if(request.getType().equalsIgnoreCase("chat_message")){
                        Player chatter = this.findPlayer(socket.getRemoteSocketAddress().toString());
                        if(chatter != null){
                            Queue q = this.findQueue(chatter);
                            if(q != null && q.getState() != Queue.State.MATCHMAKING){
                                for(Player p : q.getPlayers()){
                                    if(p.getTeam().equalsIgnoreCase(chatter.getTeam())) request.send(p.getOutputStream(),"chat_message",RequestHandler.handleChatMessage(chatter,request.getPayload().get("message_id").asText()));
                                }
                            }
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

    private void close() { //TODO: Communicate with database to track queue dodges
        System.out.println("Dropping client: " + socket.getInetAddress());
        Player droppedPlayer = this.findPlayer(socket.getRemoteSocketAddress().toString());
        Queue affectedQueue = this.findQueue(droppedPlayer);

        //Removes players from queues when they disconnect from the lobby server
        if(affectedQueue != null && droppedPlayer != null){
            affectedQueue.removePlayer(droppedPlayer);
            if(affectedQueue.getState() == Queue.State.TEAM_BUILDING){
                Packet out = new Packet();
                out.send(affectedQueue.getPartyLeader().getOutputStream(), "invite_declined", RequestHandler.handleInviteDecline(droppedPlayer.getUsername())); //Not sure if this is needed but did this to decline any outstanding invites to allow host to reinivte
            }
            if(affectedQueue.getSize() == 0){
                queues.remove(affectedQueue);
                System.out.println("Removed queue!");
            }
        }else{
            System.out.println("Error handling player disconnect");
        }
        players.remove(droppedPlayer); //Removes from server's players list
        System.out.println("Player removed!");
        try {
            socket.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Player findPlayer(String address){ //Finds player object with matching socket connection
        for (Player player : players) {
            if (player.isAddress(address)) return player;
            else System.out.println(player.getAddress() + " vs " + address);
        }
        return null;
    }

    private Queue findQueue(Player p){ //Finds queue that has a certain player in it
        for (Queue queue : queues) {
            if (queue.findPlayer(p)) return queue;
        }
        return null;
    }

    private Queue findQueue(String conn){ // Finds queue that has a player with the socket address in it
        for (Queue queue : queues) {
            if (queue.findPlayer(conn)) return queue;
        }
        return null;
    }

    private int getGuestNum(){
        int guestNum = 0;
        for(Player p : players){
            if(p.getUsername().contains("Guest")) guestNum++;
        }
        return guestNum;
    }
}
