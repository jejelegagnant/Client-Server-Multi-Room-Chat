package os.chat.server;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;

/**
 * This class manages the available {@link ChatServer}s and available rooms.
 * <p>
 * It communicates with client through its remote interface {@link ChatServerManagerInterface} implementing RMI
 * It mainly handles the creation of new {@link ChatServer} that acts semi-independent chatroom.
 * All chat room are inside the same JVM.
 */
public class ChatServerManager implements ChatServerManagerInterface {

    /**
     * NOTE: technically this vector is redundant, since the room name can also
     * be retrieved from the chat server vector.
     */
	private Vector<String> chatRoomsList;
	
	private Vector<ChatServer> chatRooms;

    private static ChatServerManager instance = null;

	private Registry registry;

	private final String myIp = "localhost";

	/**
	 * Constructor of the <code>ChatServerManager</code>.
	 * <p>
	 * Must register its functionalities as stubs to be called from RMI by
	 * the {@link os.chat.client.ChatClient}.
	 */
	public ChatServerManager () {
		chatRoomsList = new Vector<String>();
		chatRooms = new Vector<ChatServer>();
		// Set system property to match the computer Ip instead of localhost
		System.setProperty("java.rmi.server.hostname",myIp);
		try {
			// create and export its stub
			ChatServerManagerInterface stub = (ChatServerManagerInterface) UnicastRemoteObject.exportObject(this,0);
			registry = LocateRegistry.getRegistry();
			// bind its stub to its name in the registry
			registry.rebind("ChatServerManager",stub);
		} catch (RemoteException e){
			System.err.println("can not export the object");
			e.printStackTrace();
		}
		System.out.println("ChatServerManager was created");
		// initial: we create a single chat room and the corresponding ChatServer
		chatRooms.add(new ChatServer("sports"));
		chatRoomsList.add("sports");
	}

    /**
     * Retrieves the chat server manager instance. This method creates a
     * singleton chat server manager instance if none was previously created.
     * @return a reference to the singleton chat server manager instance
     */
    public static ChatServerManager getInstance() {
	if (instance == null)
	    instance = new ChatServerManager();

	return instance;
    }

        /**
	 * Getter method for list of chat rooms.
	 * @return  a list of chat rooms
	 * @see Vector
	 */
	public Vector<String> getRoomsList() {
		return chatRoomsList;
	}

        /**
	 * Creates a chat room with a specified room name <code>roomName</code>.
	 * @param roomName the name of the chat room
	 * @return <code>true</code> if the chat room was successfully created,
	 * <code>false</code> otherwise.
	 */
	public boolean createRoom(String roomName) {
		// check if homonym exists
		if (!chatRoomsList.contains(roomName)){
			// instantiate a new ChatServerObject in the same process
			chatRooms.add(new ChatServer(roomName));
			chatRoomsList.add(roomName);
			System.out.println("New room created: "+roomName);
			return true;
		}else {
			System.out.println(roomName+" already exists");
			return false;
		}
	}

	public static void main(String[] args) {
		try {
			// create a registry on the default Java RMI port
			LocateRegistry.createRegistry(1099);
		} catch (RemoteException e) {
			System.err.println("error: can not create registry");
			e.printStackTrace();
		}
		System.out.println("registry was created");
		getInstance();
	}
	
}
