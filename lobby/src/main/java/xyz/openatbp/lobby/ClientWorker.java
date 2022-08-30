package xyz.openatbp.lobby;

import java.io.*;
import java.net.Socket;

class ClientWorker implements Runnable {
    private final Socket socket;

    ClientWorker(Socket socket) {
        this.socket = socket;
    }

    public void run(){
        System.out.println("New client: " + socket.getInetAddress());
        try {
            DataInputStream clientIn = new DataInputStream(socket.getInputStream());
            DataOutputStream clientOut = new DataOutputStream(socket.getOutputStream());

            while(!socket.isClosed()){
                Packet request = new Packet();
                if (request.receive(clientIn)) {
                    System.out.println("Received " + request.getType());
                    System.out.println("Payload " + request.getPayload().toPrettyString());
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
        try {
            socket.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
