package os.chat.client;


import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Vector;

import os.chat.server.ChatServer;
import os.chat.server.ChatServerInterface;
import os.chat.server.ChatServerManagerInterface;

/**
 * This class implements a chat client that can be run locally or remotely to
 * communicate with a {@link ChatServer} using RMI.
 */
public class ChatClient implements CommandsFromWindow, CommandsFromServer {
    /**
     * Holds a reference to the active {@link os.chat.server.ChatServerManager} through its interface
     */
    ChatServerManagerInterface csm;
    /**
     * Maps joined Rooms name to their remote interfaces
     */
    HashMap<String, ChatServerInterface> joinedRooms;
    /**
     * Registry in which the server manager and its room are registered, may be remote
     */
    Registry registry;
    /**
     * Lightweight interface sent to room when the client joins a room
     */
    CommandsFromServer stub;
    /**
     * String that stores the {@link os.chat.server.ChatServerManager} ip address
     */
    private final String hostIp = "localhost"; //update to another computer ip, likely to be 134.21.x.x @unifr
    private final String myIp = "localhost"; //update with your computer ip

    /**
     * Map the unsent messages to their respective rooms, messages are stored in a queue to remain ordered.
     */
    private final HashMap<String, java.util.Queue<String>> pendingMessages = new HashMap<>();
    /**
     * The name of the user of this client
     */
    private final String userName;

    /**
     * The graphical user interface, accessed through its interface. In return,
     * the GUI will use the CommandsFromWindow interface to call methods to the
     * ChatClient implementation.
     */
    private final CommandsToWindow window;

    /**
     * Constructor for the <code>ChatClient</code>. Must perform the connection to the
     * server. If the connection is not successful, it must exit with an error.
     *
     * @param window   reference to the GUI operating the chat client
     * @param userName the name of the user for this client
     * @since Q1
     */
    public ChatClient(CommandsToWindow window, String userName) {
        // initialize object variables
        this.window = window;
        this.userName = userName;
        joinedRooms = new HashMap<>();
        // for remote host: Setup host ip address to public ip instead of localhost
        System.setProperty("java.rmi.server.hostname", myIp);
        // call to help method that handles the connection
        connectToHost();
    }

    /**
     * Private helper method that handles the connection to the {@link os.chat.server.ChatServerManager} by locating the
     * registry and looking up the name of the service. If not done yet, it creates and exports the stub
     *
     * @return weather the connection <strong>and</strong> the look-up were successful
     */
    private boolean connectToHost() {
        try {
            registry = LocateRegistry.getRegistry(hostIp);
            csm = (ChatServerManagerInterface) registry.lookup("ChatServerManager");
            // ONLY export if it hasn't been exported yet
            if (stub == null) {
                stub = (CommandsFromServer) UnicastRemoteObject.exportObject(this, 0);
            }
            return true;
        } catch (RemoteException e) {
            System.err.println("can not locate registry");
            e.printStackTrace();
            return false;
        } catch (NotBoundException e) {
            System.err.println("can not lookup for ChatServerManager");
            e.printStackTrace();
            return false;
        }
    }

    /*
     * Implementation of the functions from the CommandsFromWindow interface.
     * See methods description in the interface definition.
     */

    /**
     * Sends a new <code>message</code> to the server to propagate to all clients
     * registered to the chat room <code>roomName</code>.
     * In case of a {@link RemoteException}, it saves the messages, and tries to reconnect before retrying to send them
     *
     * @param roomName the chat room name
     * @param message  the message to send to the chat room on the server
     */
    public void sendText(String roomName, String message) {
        // retrieves the room interface
        ChatServerInterface room = joinedRooms.get(roomName);
        // Returns ff the room is not in the list, to avoid null pointer exceptions
        if (room == null) {
            System.err.println("Not joined to room: " + roomName);
            return;
        }
        try {
            // remote invocation of the room publish method
            room.publish(message, userName);
        } catch (RemoteException e) {
            // create a new queue if absent or add failed message to an existing queue
            pendingMessages.computeIfAbsent(roomName, k -> new java.util.LinkedList<>()).add(message);
            // tries to reconnect and re join the room
            if (connectToHost() && joinChatRoom(roomName)) {
                // call to helper method in connection reestablished to send pending messages
                flushPendingMessages(roomName);
            }
        }
    }

    /**
     * Helper method that tries to publish all pending messages of a chosen room. If the publication is not successful
     * the messages are not removed from the queue.
     *
     * @param roomName Name of the room for which we need to publish pending messages.
     */
    private void flushPendingMessages(String roomName) {
        // queue references the original data, all modification are recuperated
        java.util.Queue<String> queue = pendingMessages.get(roomName);
        // get the reference of the remote interface
        ChatServerInterface room = joinedRooms.get(roomName);
        // iterate until the queue is empty, if not null
        while (queue != null && !queue.isEmpty()) {
            String msg = queue.peek(); // Look at the oldest message
            try {
                room.publish(msg, userName); // if this throws, the next line is ignored
                queue.remove(); // Only remove if the server actually received it
            } catch (RemoteException e) {
                // Still no connection? Stop trying and keep the rest in the queue
                System.err.println("Chatroom still unavailable");
                break;
            }
        }
    }

    /**
     * Retrieves the list of chat rooms from the server (as a {@link Vector}
     * of {@link String}s)
     *
     * @return a list of available chat rooms or an empty Vector if there is
     * none, or if the server is unavailable
     * @see Vector
     */
    public Vector<String> getChatRoomsList() {
        try {
            return csm.getRoomsList(); // remote invocation
        } catch (RemoteException e) {
            System.err.println("can not call ChatServerManager.getRoomsList()");
            connectToHost();
            return null;
        }
    }

    /**
     * Join the chat room. Does not leave previously joined chat rooms. To
     * join a chat room we need to know only the chat room's name.
     *
     * @param roomName the name (unique identifier) of the chat room
     * @return <code>true</code> if joining the chat room was successful,
     * <code>false</code> otherwise
     */
    public boolean joinChatRoom(String roomName) {
        try {
            // registry locate for the ChatServer remote interface
            ChatServerInterface cs = (ChatServerInterface) registry.lookup("room_" + roomName);
            cs.register(stub); // remote invocation
            joinedRooms.put(roomName, cs); // only executed if previously line did not throw
            return true;
        } catch (RemoteException e) {
            System.err.println("cannot locate registry or call ChatServer.register");
            connectToHost(); // call to helper to update registry, so next call might work
            return false; // return false so gui does not create a new tab
        } catch (NotBoundException e) {
            System.err.println("Could not find room: " + roomName);
            return false; // return false so gui does not create a new tab
        }
    }

    /**
     * Leaves the chat room with the specified name
     * <code>roomName</code>. The operation has no effect if the client has not
     * previously joined the chat room.
     *
     * @param roomName the name (unique identifier) of the chat room
     * @return <code>true</code> if leaving the chat room was successful,
     * <code>false</code> otherwise
     */
    public boolean leaveChatRoom(String roomName) {
        // Look up the room without removing it yet
        ChatServerInterface cs = joinedRooms.get(roomName);

        // If it's already null, the UI is out of sync.
        // Return true so the UI can finally close the tab.
        if (cs == null) {
            return true;
        }

        try {
            cs.unregister(stub); // remote invocation
            joinedRooms.remove(roomName); // Only remove it cleanly if successful
            return true;
        } catch (RemoteException e) {
            System.err.println("Unable to unregister from " + roomName + " on the server.");

            // The server is likely down or lost connection.
            // Force-remove it locally so the UI doesn't get stuck in a locked state.
            joinedRooms.remove(roomName);
            return true;
        }
    }

    /**
     * Creates a new room named <code>roomName</code> on the server.
     *
     * @param roomName the chat room name
     * @return <code>true</code> if chat room was successfully created,
     * <code>false</code> otherwise.
     */
    public boolean createNewRoom(String roomName) {
        try {
            return csm.createRoom(roomName); // remote invocation, passes the returned value to the gui
        } catch (RemoteException e) {
            System.err.println("Unable to create room, possible connection error");
            connectToHost(); // try to update the registry so that if the user retries a better outcome is possible
            // no recursive call to avoid infinite loops
            return false; // passes failure to the gui
        }
    }

    /*
     * Implementation of the functions from the CommandsFromServer interface.
     * See methods description in the interface definition.
     */


    /**
     * Publish a <code>message</code> in the chat room <code>roomName</code>
     * of the GUI interface. This method acts as a proxy for the
     * {@link CommandsToWindow#publish(String chatName, String message)}
     * interface i.e., when the server calls this method, the {@link
     * ChatClient} calls the
     * {@link CommandsToWindow#publish(String chatName, String message)} method
     * of it's window to display the message.
     *
     * @param roomName the name of the chat room
     * @param message  the message to display
     */
    public void receiveMsg(String roomName, String message) {
        window.publish(roomName, message);
    }

    // This class does not contain a main method. You should launch the whole program by launching ChatClientWindow's main method.
}
