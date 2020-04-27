package it.polimi.ingsw.server;

import it.polimi.ingsw.messages.GameMessage;
import it.polimi.ingsw.messages.InfoMessage;
import it.polimi.ingsw.messages.PlayerToGameMessages.DataMessages.DataMessage;
import it.polimi.ingsw.messages.PlayerInfo;
import it.polimi.ingsw.observe.Observable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.NoSuchElementException;

public class ServerSideConnection extends Observable<DataMessage> implements Runnable {

    private Server server;
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private boolean active;
    private boolean inUse;

    public ServerSideConnection(Socket socket, Server server){
        this.socket = socket;
        this.server = server;
        active=true;
        inUse=true;
    }

    private synchronized boolean isActive(){
        return active;
    }

    public void deactivate(){
        active=false;
    }

    private synchronized boolean isInUse(){return inUse;}

    public void notInUse(){inUse=false;}

    public void asyncSend(final Object message){
        new Thread(new Runnable() {
            @Override
            public void run() {
                send(message);
            }
        }).start();

    }

    private synchronized void send(Object message) {
        try {
            outputStream.reset();
            outputStream.writeObject(message);
            outputStream.flush();
        } catch(IOException e){
            System.err.println(e.getMessage());
        }

    }

    private void close() {
        closeConnection();
        System.out.println("Deregistering client...");
        server.deregisterConnection(this);
        System.out.println("client successfully deregistered!");
    }

    public synchronized void closeConnection() {
        send("Connection closed from server side");
        try {
            //closes streams and then the socket
            outputStream.close();
            inputStream.close();
            socket.close();
        } catch (IOException e) {
            System.err.println("Error while closing socket!");
        }
        active = false;
        inUse=false;
    }

    @Override
    public void run() {

        try{
            inputStream = new ObjectInputStream(socket.getInputStream());
            outputStream = new ObjectOutputStream(socket.getOutputStream());

            //sends first message
            send(new InfoMessage(GameMessage.welcome));

            //reads player info and sends them to the server
            PlayerInfo playerInfo = (PlayerInfo) inputStream.readObject();
            server.lobby(new PlayerConnection(playerInfo,this));

            //continues to read input commands until the connections stays active, and notifies them to the virtualView
            while(isActive() && isInUse()){
                notify((DataMessage)inputStream.readObject());
            }

        //serialization adds ClassNotFoundException
        } catch (IOException | NoSuchElementException | ClassNotFoundException e) {
            System.err.println("Error!" + e.getMessage());

        //when isActive becomes false or exception is thrown (such as someone disconnects)
        }finally{

            //this way the only one which can use close() is the winner, deregistering all the players and
            //closing their socket and streams
            if(isInUse()){
                close();
            }
       }
    }

}
