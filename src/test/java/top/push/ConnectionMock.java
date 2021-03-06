package top.push;

import top.push.ClientConnection;
import top.push.SendStatus;

public class ConnectionMock extends ClientConnection {
	public int sendCount;
	private boolean isValid;
	private boolean canSend;

	public ConnectionMock() {
		this(true, true);
	}

	public ConnectionMock(boolean isValid, boolean canSend) {
		super(null, null);
		this.isValid = isValid;
		this.canSend = canSend;
	}

	@Override
	public boolean isValid() {
		return this.isValid;
	}

	@Override
	public SendStatus sendMessage(Object msg) throws Exception {
		if (!this.canSend)
			throw new Exception("send message exception mock!");
		this.sendCount++;
		return SendStatus.SENT;
	}
}