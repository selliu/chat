package server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Room implements AutoCloseable {
	private static SocketServer server;
	private String name;
	private final static Logger log = Logger.getLogger(Room.class.getName());

	private final static String COMMAND_TRIGGER = "/";
	private final static String CREATE_ROOM = "createroom";
	private final static String JOIN_ROOM = "joinroom";
	private final static String ROLL = "roll";
	private final static String FLIP = "flip";
	private final static String PM = "@";
	private final static String MUTE = "mute";
	private final static String UNMUTE = "unmute";
	private final static String TOGGLE = "toggle";

	public Room(String name) {
		this.name = name;
	}

	public static void setServer(SocketServer server) {
		Room.server = server;
	}

	public String getName() {
		return name;
	}

	private List<ServerThread> clients = new ArrayList<ServerThread>();

	protected synchronized void addClient(ServerThread client) {
		client.setCurrentRoom(this);
		if (clients.indexOf(client) > -1) {
			log.log(Level.INFO, "Attempting to add a client that already exists");
		} else {
			clients.add(client);
			if (client.getClientName() != null) {
				client.sendClearList();
				sendConnectionStatus(client, true, "joined the room " + getName());
				updateClientList(client);
			}
		}
	}

	private void updateClientList(ServerThread client) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread c = iter.next();
			if (c != client) {
				boolean messageSent = client.sendConnectionStatus(c.getClientName(), true, null);
			}
		}
	}

	protected synchronized void removeClient(ServerThread client) {
		clients.remove(client);
		if (clients.size() > 0) {
			sendConnectionStatus(client, false, "left the room " + getName());
		} else {
			cleanupEmptyRoom();
		}
	}

	private void cleanupEmptyRoom() {
		if (name == null || name.equalsIgnoreCase(SocketServer.LOBBY)) {
			return;
		}
		try {
			log.log(Level.INFO, "Closing empty room: " + name);
			close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void joinRoom(String room, ServerThread client) {
		server.joinRoom(room, client);
	}

	protected void joinLobby(ServerThread client) {
		server.joinLobby(client);
	}

	/***
	 * Helper function to process messages to trigger different functionality.
	 * 
	 * @param message The original message being sent
	 * @param client  The sender of the message (since they'll be the ones
	 *                triggering the actions)
	 */
	private boolean processCommands(String message, ServerThread client) {
		boolean wasCommand = false;
		try {
			if (message.indexOf(COMMAND_TRIGGER) > -1) {
				String[] comm = message.split(COMMAND_TRIGGER);
				log.log(Level.INFO, message);
				String part1 = comm[1];
				String[] comm2 = part1.split(" ");
				String command = comm2[0];
				if (command != null) {
					command = command.toLowerCase();
				}
				String roomName;
				switch (command) {
				case CREATE_ROOM:
					roomName = comm2[1];
					if (server.createNewRoom(roomName)) {
						joinRoom(roomName, client);
					}
					wasCommand = true;
					break;
				case JOIN_ROOM:
					roomName = comm2[1];
					joinRoom(roomName, client);
					wasCommand = true;
					break;
				case ROLL:
					// roll a number from 1 to 1000 randomly
					// changed the output text into underlined
					int randomNum = (int) ((Math.random() * (1000)));
					String rMsg = "<u>The number is<u> " + Integer.toString(randomNum);
					sendMessage(client, rMsg);
					wasCommand = true;
					break;
				case FLIP:
					// flip a coin either heads or tails
					// change the color of the result to purple if heads and green for tails
					// I used HTML for this
					int flipCoin = ((int) Math.random() * 2);
					String fMsg = "Ezpz u got <style=color:purple> Heads ¯\\_(^_^)_/¯";
					if (flipCoin == 2) {
						fMsg = "Oh u got <style=color:green>Tails";
					}
					sendMessage(client, fMsg);
					wasCommand = true;
					break;
				case MUTE:
					String[] msgMute = message.split(" ");
					String mutedUser = msgMute[1];
					client.mutedList.add(mutedUser);
					sendMessage(client, "<b> muted <b> " + mutedUser);
					wasCommand = true;
					break;
				case UNMUTE:
					String[] msgUnmute = message.split(" ");
					String unmutedUser = msgUnmute[1];
					for (String name : client.mutedList) {
						if (name.equals(unmutedUser)) {
							client.mutedList.remove(unmutedUser);
							sendMessage(client, "<b>unmuted <b>" + unmutedUser);
							wasCommand = true;
							break;
						}
					}
					break;
				// TO THIS PART TMRW - ADD Additional stuffs and fix the UI, work on the
				// front-end and help team members
				// regulate the backend of the project. Finish this within 5 hours.
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return wasCommand;
	}

	// TODO changed from string to ServerThread
	protected void sendConnectionStatus(ServerThread client, boolean isConnect, String message) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread c = iter.next();
			boolean messageSent = c.sendConnectionStatus(client.getClientName(), isConnect, message);
			if (!messageSent) {
				iter.remove();
				log.log(Level.INFO, "Removed client " + c.getId());
			}
		}
	}

	/***
	 * Takes a sender and a message and broadcasts the message to all clients in
	 * this room. Client is mostly passed for command purposes but we can also use
	 * it to extract other client info.
	 * 
	 * @param sender  The client sending the message
	 * @param message The message to broadcast inside the room
	 */
	protected void sendMessage(ServerThread sender, String message) {
		log.log(Level.INFO, getName() + ": Sending message to " + clients.size() + " clients");
		if (processCommands(message, sender)) {
			return;
		}
		if (sendPM(sender, message)) {
			return;
		}
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (!client.isMuted(sender.getClientName())) {
				boolean messageSent = client.send(sender.getClientName(), message);
				if (!messageSent) {
					iter.remove();
					log.log(Level.INFO, "Removed client " + client.getId());
				}
			}
		}
	}

	protected boolean sendPM(ServerThread sender, String message) {
		boolean pm = false;
		String receiver = null;

		if (message.indexOf("@") > -1) {
			String[] words = message.split(" ");
			for (String word : words) {
				if (word.charAt(0) == '@') {
					receiver = word.substring(1);
					pm = true;

					Iterator<ServerThread> iter = clients.iterator();
					while (iter.hasNext()) {
						ServerThread userPM = iter.next();
						if (userPM.getClientName().equals(receiver)) {
							userPM.send(sender.getClientName(), message);
						}
					}
				}
			}
			sender.send(sender.getClientName(), message);
		}
		return pm;
	}

	/***
	 * Will attempt to migrate any remaining clients to the Lobby room. Will then
	 * set references to null and should be eligible for garbage collection
	 */
	public List<String> getRooms() {
		return server.getRooms();
	}

	@Override
	public void close() throws Exception {
		int clientCount = clients.size();
		if (clientCount > 0) {
			log.log(Level.INFO, "Migrating " + clients.size() + " to Lobby");
			Iterator<ServerThread> iter = clients.iterator();
			Room lobby = server.getLobby();
			while (iter.hasNext()) {
				ServerThread client = iter.next();
				lobby.addClient(client);
				iter.remove();
			}
			log.log(Level.INFO, "Done Migrating " + clients.size() + " to Lobby");
		}
		server.cleanupRoom(this);
		name = null;
	}
}
