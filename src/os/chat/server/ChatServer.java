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
	private Vector<CommandsFromServer> registeredClients;
	private Registry registry;

  /**
   * Constructs and initializes the chat room before registering it to the RMI
   * registry.
   * @param roomName the name of the chat room
   */
	public ChatServer(String roomName){
		this.roomName = roomName;
		registeredClients = new Vector<CommandsFromServer>();
		try {
			ChatServerInterface stub = (ChatServerInterface) UnicastRemoteObject.exportObject(this,0);
			registry = LocateRegistry.getRegistry();
			registry.rebind("room_"+roomName,stub);
		} catch (RemoteException e){
			System.err.println("can not export the object");
			e.printStackTrace();
		}
	}

	/**
	 * Publishes to all subscribed clients (i.e. all clients registered to a
	 * chat room) a message send from a client.
	 * @param message the message to propagate
	 * @param publisher the client from which the message originates
	 */
	public void publish(String message, String publisher) {
		System.out.println("Broadcasting : "+publisher+": "+message);
		for (int i = 0; i < registeredClients.size(); i++) {
			CommandsFromServer client = registeredClients.get(i);
            try {
                client.receiveMsg(roomName,publisher+": "+message);
            } catch (RemoteException e) {
                System.err.println("Unable to publish to "+i);
				e.printStackTrace();
            }
        }
	}

	/**
	 * Registers a new client to the chat room.
	 * @param client the name of the client as registered with the RMI
	 * registry
	 */
	public void register(CommandsFromServer client) {

		registeredClients.add(client);
		System.out.println(client+" registered to "+roomName);
	}

	/**
	 * Unregisters a client from the chat room.
	 * @param client the name of the client as registered with the RMI
	 * registry
	 */
	public void unregister(CommandsFromServer client) {

		registeredClients.remove(client);
		System.out.println(client+"left "+roomName);
	}

}
