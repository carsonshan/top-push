package com.tmall.top.push.websocket;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.time.StopWatch;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocket.Connection;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;
import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.tmall.top.push.PushManager;
import com.tmall.top.push.messages.MessageIO;
import com.tmall.top.push.messages.MessageType;
import com.tmall.top.push.messages.PublishConfirmMessage;
import com.tmall.top.push.messages.PublishMessage;

public class WebSocketPushServerTest {

	@Test
	public void init_test() throws Exception {
		Server server = this.initServer(8001, 8002);
		server.start();

		WebSocketClientFactory factory = new WebSocketClientFactory();
		factory.start();
		this.connect(factory, "ws://localhost:8001/front", "front-test",
				"mqtt", null);
		this.connect(factory, "ws://localhost:8002/back", "back-test", "mqtt",
				null);

		server.stop();
	}

	@Test
	public void publish_confirm_test() throws Exception {
		Server server = this.initServer(9001, 9002);
		server.start();

		WebSocketClientFactory factory = new WebSocketClientFactory();
		factory.start();

		// front-end client like a subscriber
		String frontId = "front";
		final PublishMessage publishMessage = new PublishMessage();
		final Object waitFront = new Object();
		Connection front = this.connect(factory, "ws://localhost:9001/front",
				frontId, "mqtt", new WebSocket.OnBinaryMessage() {

					@Override
					public void onOpen(Connection arg0) {
					}

					@Override
					public void onClose(int arg0, String arg1) {
					}

					@Override
					public void onMessage(byte[] data, int offset, int length) {
						int messageType = MessageIO
								.parseMessageType(data[offset]);
						assertEquals(MessageType.PUBLISH, messageType);

						// receiving publish-message from server
						ByteBuffer buffer = ByteBuffer.allocate(length);
						buffer.put(data, offset, length);
						MessageIO.parseClientReceiving(publishMessage, buffer);
						System.out
								.println("---- [frontend] receiving publish-message from server");
						synchronized (waitFront) {
							waitFront.notifyAll();
						}
					}
				});

		// back-end client like a publisher
		String backId = "back";
		final PublishConfirmMessage confirmMessage = new PublishConfirmMessage();
		final Object waitBack = new Object();
		Connection back = this.connect(factory, "ws://localhost:9002/back",
				backId, "mqtt", new WebSocket.OnBinaryMessage() {

					@Override
					public void onOpen(Connection arg0) {
					}

					@Override
					public void onClose(int arg0, String arg1) {
					}

					@Override
					public void onMessage(byte[] data, int offset, int length) {
						int messageType = MessageIO
								.parseMessageType(data[offset]);
						assertEquals(MessageType.PUBCONFIRM, messageType);

						// receiving confirm-message from server
						ByteBuffer buffer = ByteBuffer.allocate(length);
						buffer.put(data, offset, length);
						MessageIO.parseClientReceiving(confirmMessage, buffer);
						System.out
								.println("---- [backend] receiving confirm-message from server");
						synchronized (waitBack) {
							waitBack.notifyAll();
						}
					}
				});

		// send publish
		ByteBuffer publish = this.createPublishMessage(frontId);
		back.sendMessage(publish.array(), 0, publish.limit());

		// receive publish
		synchronized (waitFront) {
			waitFront.wait();
		}
		assertEquals(backId, publishMessage.from);
		// assert body is expected
		assertEquals('a', (char) ((ByteBuffer) publishMessage.body).get());
		assertEquals('b', (char) ((ByteBuffer) publishMessage.body).get());
		assertEquals('c', (char) ((ByteBuffer) publishMessage.body).get());
		assertEquals('d', (char) ((ByteBuffer) publishMessage.body).get());
		assertEquals('e', (char) ((ByteBuffer) publishMessage.body).get());
		assertEquals('f', (char) ((ByteBuffer) publishMessage.body).get());
		assertEquals('g', (char) ((ByteBuffer) publishMessage.body).get());
		// send confirm
		ByteBuffer confirm = this.createConfirmMessage(publishMessage);
		front.sendMessage(confirm.array(), 0, confirm.limit());

		// receive confirm
		synchronized (waitBack) {
			waitBack.wait();
		}
		assertEquals(frontId, confirmMessage.from);

		front.close();
		back.close();
		Thread.sleep(1000);
		server.stop();
	}

	@Test
	public void publish_confirm_long_running_test() throws Exception {
		Server server = this.initServer(9003, 9004);
		server.start();

		WebSocketClientFactory factory = new WebSocketClientFactory();
		factory.start();

		this.connect(factory, "ws://localhost:9003/front", "front", "mqtt",
				null);
		this.connect(factory, "ws://localhost:9003/front", "front", "mqtt",
				null);

		Connection back = this.connect(factory, "ws://localhost:9004/back",
				"back", "mqtt", null);
		StopWatch watch = new StopWatch();
		watch.start();
		for (int i = 0; i < 10000; i++) {
			ByteBuffer publish = this.createPublishMessage("front");
			back.sendMessage(publish.array(), 0, publish.limit());
		}
		watch.stop();
		// jetty websocket client slower than nodejs impl
		System.out.println(String.format("---- publish %s messages cost %sms",
				10000, watch.getTime()));

		Thread.sleep(2000);
		while (!PushManager.current().isIdleClient("front")) {
			Thread.sleep(1000);
		}

		server.stop();
	}

	@Test
	public void rcp_test() throws Exception {
		Server server = this.initServer(9005, 9006);
		server.start();

		WebSocketClientFactory factory = new WebSocketClientFactory();
		factory.start();

		final Response response = new Response();
		this.connect(factory, "ws://localhost:9005/front", "front", "mqtt",
				null);
		final String waitObject = new String("abc");
		Connection back = this.connect(factory, "ws://localhost:9006/back",
				"back", "mqtt", new WebSocket.OnTextMessage() {
					public void onOpen(Connection connection) {
					}

					public void onClose(int closeCode, String message) {
					}

					public void onMessage(String data) {
						Response temp = JSON.parseObject(data, Response.class);
						response.IsError = temp.IsError;
						response.ErrorPhrase = temp.ErrorPhrase;
						response.Result = temp.Result;
						synchronized (waitObject) {
							waitObject.notify();
						}
					}
				});
		Request request = new Request();
		request.Command = "isonline";
		request.Arguments = new HashMap<String, String>();
		request.Arguments.put("id", "front");
		back.sendMessage(JSON.toJSONString(request));
		synchronized (waitObject) {
			waitObject.wait();
		}
		assertFalse(response.IsError);
		assertEquals("true", response.Result);

		server.stop();
	}

	private ByteBuffer createPublishMessage(String to) {
		byte[] bytes = new byte[1024];
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		PublishMessage msg = new PublishMessage();
		msg.to = to;
		msg.remainingLength = 7;
		buffer.position(13);
		buffer.put((byte) 'a');
		buffer.put((byte) 'b');
		buffer.put((byte) 'c');
		buffer.put((byte) 'd');
		buffer.put((byte) 'e');
		buffer.put((byte) 'f');
		buffer.put((byte) 'g');
		MessageIO.parseClientSending(msg, buffer);
		return buffer;
	}

	private ByteBuffer createConfirmMessage(PublishMessage publishMessage) {
		byte[] bytes = new byte[1024];
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		PublishConfirmMessage msg = new PublishConfirmMessage();
		msg.to = publishMessage.from;
		msg.remainingLength = 100;
		MessageIO.parseClientSending(msg, buffer);
		return buffer;
		// TODO:message id formatter in MessageIO
	}

	private WebSocket.Connection connect(WebSocketClientFactory factory,
			String uri, String origin, String protocol, WebSocket handler)
			throws InterruptedException, ExecutionException, TimeoutException,
			IOException, URISyntaxException {
		WebSocketClient client = factory.newWebSocketClient();
		client.setProtocol(protocol);
		client.setOrigin(origin);
		return client.open(new URI(uri),
				handler == null ? new WebSocket.OnTextMessage() {
					public void onOpen(Connection connection) {
					}

					public void onClose(int closeCode, String message) {
					}

					public void onMessage(String data) {
					}
				} : handler).get(1, TimeUnit.SECONDS);
	}

	private Server initServer(int front, int back) throws IOException {
		Server server = new Server();
		// front
		SelectChannelConnector connector0 = new SelectChannelConnector();
		connector0.setPort(front);
		connector0.setMaxIdleTime(30000);
		connector0.setRequestHeaderSize(8192);
		connector0.accept(2);
		connector0.setThreadPool(new QueuedThreadPool(20));
		// back
		SelectChannelConnector connector1 = new SelectChannelConnector();
		connector1.setPort(back);
		connector1.setMaxIdleTime(30000);
		connector1.setRequestHeaderSize(8192);
		connector1.accept(1);
		connector1.setThreadPool(new QueuedThreadPool(20));

		// web-context
		ServletContextHandler context = new ServletContextHandler(
				ServletContextHandler.NO_SESSIONS);
		context.addServlet(new ServletHolder(new FrontendServlet()), "/front");
		context.addServlet(new ServletHolder(new BackendServlet()), "/back");
		// init push server
		ServletHolder initHolder = new ServletHolder(new InitServlet());
		initHolder.setInitParameter("maxConnectionCount", "10");
		initHolder.setInitParameter("publishMessageSize", "1024");
		initHolder.setInitParameter("confirmMessageSize", "1024");
		initHolder.setInitParameter("publishMessageBufferCount", "10000");
		initHolder.setInitParameter("confirmMessageBufferCount", "10000");
		initHolder.setInitParameter("senderCount", "4");
		initHolder.setInitParameter("senderIdle", "10");
		initHolder.setInitParameter("stateBuilderIdle", "1000");
		context.addServlet(initHolder, "/init");
		context.setContextPath("/");

		server.setConnectors(new Connector[] { connector0, connector1 });
		server.setHandler(context);
		return server;
	}
}
