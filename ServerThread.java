package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerThread extends Thread {
	private Socket client;
	private ObjectInputStream in;// from client
	private ObjectOutputStream out;// to client
	private boolean isRunning = false;
	private Room currentRoom;// what room we are in, should be lobby by default
	private String clientName;
	private final static Logger log = Logger.getLogger(ServerThread.class.getName());
	public List<String> mutedList = new ArrayList<String>();

	public boolean isMuted(String clientName) {
		for (String name : mutedList) {
			if (name.equals(clientName)) {
				return true;
			}
		}
		return false;
	}

	public String getClientName() {
		return clientName;
	}

	protected synchronized Room getCurrentRoom() {
		return currentRoom;
	}

	protected synchronized void setCurrentRoom(Room room) {
		if (room != null) {
			currentRoom = room;
		} else {
			log.log(Level.INFO, "Passed in room was null, this shouldn't happen");
		}
	}

	public ServerThread(Socket myClient, Room room) throws IOException {
		this.client = myClient;
		this.currentRoom = room;
		out = new ObjectOutputStream(client.getOutputStream());
		in = new ObjectInputStream(client.getInputStream());
	}

	/***
	 * Sends the message to the client represented by this ServerThread
	 * 
	 * @param message
	 * @return
	 */
	@Deprecated
	protected boolean send(String message) {
		try {
			out.writeObject(message);
			return true;
		} catch (IOException e) {
			log.log(Level.INFO, "Error sending message to client (most likely disconnected)");
			e.printStackTrace();
			cleanup();
			return false;
		}
	}

	/***
	 * Replacement for send(message) that takes the client name and message and
	 * converts it into a payload
	 * 
	 * @param clientName
	 * @param message
	 * @return
	 */
	protected boolean send(String clientName, String message) {
		// checking if there are multiple text style triggers in the message
		int boldCount = 0;
		int italicsCount = 0;
		int underLineCount = 0;

		for (int i = 0; i < message.length(); i++) {
			if (message.charAt(i) == '@') {
				boldCount++;
			} else if (message.charAt(i) == '#') {
				italicsCount++;
			} else if (message.charAt(i) == '_') {
				underLineCount++;
			}
		}

		if (boldCount >= 2) {
			message = message + " ";
			message = message.replace("@", "<b>");
			message = message.replace("<b> ", "</b> ");
		}
		if (italicsCount >= 2) {
			message = message + " ";
			message = message.replace("#", "<i>");
			message = message.replace("<i> ", "</i> ");
		}
		if (underLineCount >= 2) {
			message = message + " ";
			message = message.replace("_", "<u>");
			message = message.replace("<u> ", "</u> ");
		}

		int colorCount = 0;
		for (int i = 0; i < message.length(); i++) {
			if (message.charAt(i) == '%') {
				colorCount++;
			}
		}

		if (colorCount % 2 == 0) {
			message = message + " ";
			message = message.replace("% ", "</font> ");

			String[] words = message.split(" ");
			message = "";
			for (String word : words) {

				if (word.contains("%")) {
					int trigger = word.indexOf('%');
					String color = word.substring(0, trigger);
					String colorStyle = "<font color=" + color + ">";
					String replace = word.substring(0, trigger + 1);
					word = word.replace(replace, colorStyle);
				}

				message = message + word + " ";
			}
		}

		Payload payload = new Payload();
		payload.setPayloadType(PayloadType.MESSAGE);
		payload.setClientName(clientName);
		payload.setMessage(message);

		return sendPayload(payload);
	}

	protected boolean sendConnectionStatus(String clientName, boolean isConnect, String message) {
		Payload payload = new Payload();
		if (isConnect) {
			payload.setPayloadType(PayloadType.CONNECT);
			payload.setMessage(message);
		} else {
			payload.setPayloadType(PayloadType.DISCONNECT);
			payload.setMessage(message);
		}
		payload.setClientName(clientName);
		return sendPayload(payload);
	}

	protected boolean sendClearList() {
		Payload payload = new Payload();
		payload.setPayloadType(PayloadType.CLEAR_PLAYERS);
		return sendPayload(payload);
	}

	protected boolean sendRoom(String room) {
		Payload payload = new Payload();
		payload.setPayloadType(PayloadType.GET_ROOMS);
		payload.setMessage(room);
		return sendPayload(payload);
	}

	private boolean sendPayload(Payload p) {
		try {
			out.writeObject(p);
			return true;
		} catch (IOException e) {
			log.log(Level.INFO, "Error sending message to client (most likely disconnected)");
			e.printStackTrace();
			cleanup();
			return false;
		}
	}

	/***
	 * Process payloads we receive from our client
	 * 
	 * @param p
	 */
	private void processPayload(Payload p) {
		switch (p.getPayloadType()) {
		case CONNECT:
			String n = p.getClientName();
			if (n != null) {
				clientName = n;
				log.log(Level.INFO, "Set our name to " + clientName);
				if (currentRoom != null) {
					currentRoom.joinLobby(this);
				}
			}
			break;
		case DISCONNECT:
			isRunning = false;
			break;
		case MESSAGE:
			currentRoom.sendMessage(this, p.getMessage());
			break;
		case GET_ROOMS:
			List<String> roomNames = currentRoom.getRooms();
			Iterator<String> iter = roomNames.iterator();
			while (iter.hasNext()) {
				String room = iter.next();
				if (room != null && !room.equalsIgnoreCase(currentRoom.getName())) {
					if (!sendRoom(room)) {
						break;
					}
				}
			}
			break;
		case JOIN_ROOM:
			currentRoom.joinRoom(p.getMessage(), this);
			break;
		default:
			log.log(Level.INFO, "Unhandled payload on server: " + p);
			break;
		}
	}

	@Override
	public void run() {
		try {
			isRunning = true;
			Payload fromClient;
			while (isRunning && // flag to let us easily control the loop
					!client.isClosed() // breaks the loop if our connection closes
					&& (fromClient = (Payload) in.readObject()) != null // reads an object from inputStream (null would
			// likely mean a disconnect)
			) {
				System.out.println("Received from client: " + fromClient);
				processPayload(fromClient);
			} // close while loop
		} catch (Exception e) {
			// happens when client disconnects
			e.printStackTrace();
			log.log(Level.INFO, "Client Disconnected");
		} finally {
			isRunning = false;
			log.log(Level.INFO, "Cleaning up connection for ServerThread");
			cleanup();
		}
	}

	private void cleanup() {
		if (currentRoom != null) {
			log.log(Level.INFO, getName() + " removing self from room " + currentRoom.getName());
			currentRoom.removeClient(this);
		}
		if (in != null) {
			try {
				in.close();
			} catch (IOException e) {
				log.log(Level.INFO, "Input already closed");
			}
		}
		if (out != null) {
			try {
				out.close();
			} catch (IOException e) {
				log.log(Level.INFO, "Client already closed");
			}
		}
		if (client != null && !client.isClosed()) {
			try {
				client.shutdownInput();
			} catch (IOException e) {
				log.log(Level.INFO, "Socket/Input already closed");
			}
			try {
				client.shutdownOutput();
			} catch (IOException e) {
				log.log(Level.INFO, "Socket/Output already closed");
			}
			try {
				client.close();
			} catch (IOException e) {
				log.log(Level.INFO, "Client already closed");
			}
		}
	}
}