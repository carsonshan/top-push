package com.taobao.top.push;

import java.util.List;

import com.taobao.top.push.ClientConnection.SendStatus;

public class ClientMessageSender implements MessageSender {
	private List<ClientConnection> connections;
	private int index;

	public ClientMessageSender(List<ClientConnection> connections) {
		this.connections = connections;
	}

	@Override
	public boolean send(Object message) {
		int size = this.connections.size();
		int begin = this.index++ % size;
		int i = begin;

		do {
			ClientConnection connection = this.connections.get(i);

			if (connection == null)
				break;

			if (!connection.isOpen())
				continue;

			SendStatus status;
			try {
				status = connection.sendMessage(message);
			} catch (Exception e) {
				// FIXME log error
				continue;
			}

			switch (status) {
			case SENT:
				return true;
			case DROP:
				return true;
			case RETRY:
				continue;
			}
		} while ((i = i + 1 >= size ? 0 : i + 1) != begin);

		return false;
	}
}
