package client;

import java.awt.BorderLayout;

import javax.swing.JEditorPane;
import javax.swing.JPanel;

public class User extends JPanel {
	private String name;
	private JEditorPane nameField;

	public User(String name) {
		this.name = name;
		nameField = new JEditorPane();
		nameField.setContentType("text/html");
		nameField.setText(name);
		nameField.setEditable(false);
		this.setLayout(new BorderLayout());
		this.add(nameField);
	}

	public void setName(String name, String wrapper) {
		nameField.setText(String.format(wrapper, name));
	}

	public String getName() {
		return name;
	}
}