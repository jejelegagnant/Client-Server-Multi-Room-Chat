package os.chat.client;


import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This is the interface between {@link ChatClient} and {@link ChatServer}.
 */
public interface CommandsFromServer extends Remote {
	
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
	public void receiveMsg(String roomName, String message) throws RemoteException;
}


