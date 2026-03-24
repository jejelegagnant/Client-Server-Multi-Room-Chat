package os.chat.server;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;

import os.chat.client.CommandsFromServer;

/**
 * Each instance of this class is a server for one room.
 * <p>
 * At first there is only one room server, and the names of the room available
 * is fixed.
 * <p>
 * Later you will have multiple room server, each managed by its own
 * <code>ChatServer</code>. A {@link ChatServerManager} will then be responsible
 * for creating and adding new rooms.
 */
public class ChatServer implements ChatServerInterface {

    private String roomName;
    /**
     * List of {@link os.chat.client.ChatClient} that want to receive messages, through their interface
     */
    private Vector<CommandsFromServer> registeredClients;
    private Registry registry;

    /**
     * Constructs and initializes the chat room before registering it to the RMI
     * registry.
     *
     * @param roomName the name of the chat room
     */
    public ChatServer(String roomName) {
        this.roomName = roomName;
        registeredClients = new Vector<CommandsFromServer>();
        try {
            // create and export the stub
            ChatServerInterface stub = (ChatServerInterface) UnicastRemoteObject.exportObject(this, 0);
            //Get the local registry
            registry = LocateRegistry.getRegistry();
            // Bind itself into the registry
            registry.rebind("room_" + roomName, stub);
        } catch (RemoteException e) {
            System.err.println("can not export the object");
            e.printStackTrace();
        }
    }

    /**
     * Publishes to all subscribed clients (i.e. all clients registered to a
     * chat room) a message send from a client. If a client is inaccessible removes it from the list
     *
     * @param message   the message to propagate
     * @param publisher the client from which the message originates
     */
    public void publish(String message, String publisher) {
        System.out.println("Broadcasting : " + publisher + ": " + message);

        //temporary list of disconnected clients
        Vector<CommandsFromServer> disconnectedClients = new Vector<>();

        // Phase 1: publish to all client, add to a list the one that failed
        for (CommandsFromServer client : registeredClients) {
            try {
                client.receiveMsg(roomName, publisher + ": " + message);
            } catch (RemoteException e) {
                System.err.println("Assuming the connection is lost, client disconnected");
                disconnectedClients.add(client);
            }
        }

        // Phase 2 remove the list of failed clients from the list of client
        if (!disconnectedClients.isEmpty()) {
            registeredClients.removeAll(disconnectedClients);
            // announces to the remaining client that some client lost connection
            publish("One or more client lost connection", "server");
        }
    }

    /**
     * Registers a new client to the chat room.
     *
     * @param client the name of the client as registered with the RMI
     *               registry
     */
    public void register(CommandsFromServer client) {
        // Announces that a client will join, must be done before due to race conditions in the GUI.
        publish("A new client joined the room " + roomName, "server");
        // add the new client to the data structure holding the active clients
        registeredClients.add(client);
        System.out.println(client + " registered to " + roomName);
    }

    /**
     * Unregisters a client from the chat room.
     *
     * @param client the name of the client as registered with the RMI
     *               registry
     */
    public void unregister(CommandsFromServer client) {
        // Removes the client from the list
        registeredClients.remove(client);
        System.out.println(client + "left " + roomName);
        // Announcement sent after the removal, not to the removed client to avoid race conditions with the gui
        publish("A client left the room " + roomName, "server");
    }

}
