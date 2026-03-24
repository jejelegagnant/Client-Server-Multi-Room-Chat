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
public class ChatClient implements CommandsFromWindow,CommandsFromServer {
	ChatServerManagerInterface csm;
	HashMap<String, ChatServerInterface> joinedRooms;
	Registry registry;
	CommandsFromServer stub;
	private final String hostIp = "localhost"; //update to another computer ip
	private final String myIp = "localhost";
	// Add this to your class fields
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
	private final CommandsToWindow window ;
	
  /**
   * Constructor for the <code>ChatClient</code>. Must perform the connection to the
   * server. If the connection is not successful, it must exit with an error.
   * 
   * @param window reference to the GUI operating the chat client
   * @param userName the name of the user for this client
   * @since Q1
   */
	public ChatClient(CommandsToWindow window, String userName) {
		this.window = window;
		this.userName = userName;
		joinedRooms = new HashMap<>();
		System.setProperty("java.rmi.server.hostname",myIp);
		connectToHost();
	}

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
	 * @param roomName the chat room name
	 * @param message the message to send to the chat room on the server
	 */
	public void sendText(String roomName, String message) {
		ChatServerInterface room = joinedRooms.get(roomName);
		if (room == null) {
			System.err.println("Not joined to room: " + roomName);
			return;
		}
        try {
            room.publish(message,userName);
        } catch (RemoteException e) {
			pendingMessages.computeIfAbsent(roomName, k -> new java.util.LinkedList<>()).add(message);
            if(connectToHost() && joinChatRoom(roomName)){
				flushPendingMessages(roomName);
			}
        }
    }
	private void flushPendingMessages(String roomName) {
		java.util.Queue<String> queue = pendingMessages.get(roomName);
		ChatServerInterface room = joinedRooms.get(roomName);

		while (queue != null && !queue.isEmpty()) {
			String msg = queue.peek(); // Look at the oldest message
			try {
				room.publish(msg, userName);
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
	 * @return a list of available chat rooms or an empty Vector if there is
	 * none, or if the server is unavailable
	 * @see Vector
	 */
	public Vector<String> getChatRoomsList() {
		try {
			return csm.getRoomsList();
		} catch (RemoteException e) {
			System.err.println("can not call ChatServerManager.getRoomsList()");
			connectToHost();
			return null;
		}
	}

	/**
	 * Join the chat room. Does not leave previously joined chat rooms. To
	 * join a chat room we need to know only the chat room's name.
	 * @param roomName the name (unique identifier) of the chat room
	 * @return <code>true</code> if joining the chat room was successful,
	 * <code>false</code> otherwise
	 */
	public boolean joinChatRoom(String roomName) {
		try {
			ChatServerInterface cs = (ChatServerInterface) registry.lookup("room_" + roomName);
			cs.register(stub);
			joinedRooms.put(roomName,cs);
			return true;
		} catch (RemoteException e) {
			System.err.println("cannot locate registry or call ChatServer.register");
			connectToHost();
			return false;
		} catch (NotBoundException e) {
			System.err.println("Could not find room: " + roomName);
			return false;
		}
	}

	/**
	 * Leaves the chat room with the specified name
	 * <code>roomName</code>. The operation has no effect if has not
	 * previously joined the chat room.
	 * @param roomName the name (unique identifier) of the chat room
	 * @return <code>true</code> if leaving the chat room was successful,
	 * <code>false</code> otherwise
	 */	
	public boolean leaveChatRoom(String roomName) {
        try {
			ChatServerInterface cs = joinedRooms.remove(roomName);
            cs.unregister(stub);
			return true;
        } catch (RemoteException e) {
            System.err.println("Unable to leave "+roomName);
			return false;
        }
    }

    /**
     * Creates a new room named <code>roomName</code> on the server.
     * @param roomName the chat room name
     * @return <code>true</code> if chat room was successfully created,
     * <code>false</code> otherwise.
     */
	public boolean createNewRoom(String roomName) {
        try {
            return csm.createRoom(roomName);
        } catch (RemoteException e) {
            System.err.println("Unable to create room, possible connection error");
			connectToHost();
			return false;
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
	 * @param roomName the name of the chat room
	 * @param message the message to display
	 */
	public void receiveMsg(String roomName, String message) {
		window.publish(roomName,message);
	}
		
	// This class does not contain a main method. You should launch the whole program by launching ChatClientWindow's main method.
}
