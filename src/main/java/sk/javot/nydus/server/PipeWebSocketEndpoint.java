package sk.javot.nydus.server;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerEndpoint("/pipe")
public class PipeWebSocketEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(PipeWebSocketEndpoint.class);

    Map<Session, IoSession> sessions = new HashMap<>();

//	@OnMessage
//	public void handleMessage(Session session, String message) throws IOException {
//		session.getBasicRemote()
//				.sendText("Reversed: " + new StringBuilder(message).reverse());
//	}

    /**
     *     Decorates the function associated with handling a connection open event
     */
    @OnOpen
    public void open(final Session session) {
        final NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(10000);
        connector.getFilterChain().addLast("logger", new LoggingFilter());
        connector.setHandler(new IoHandlerAdapter() {


            @Override
            public void sessionOpened(IoSession ios) throws Exception {
                //ios.setAttribute("wss", session);
                //ios.setAttribute("connector", connector);
                sessions.put(session, ios);
            }


            @Override
            public void sessionClosed(IoSession ios) throws Exception {
                connector.dispose();
                session.close();
            }

            
            @Override
            public void messageReceived(IoSession ios, Object message) throws Exception {
                IoBuffer recv = (IoBuffer) message;
                RemoteEndpoint.Async remote = session.getAsyncRemote();
                remote.sendBinary(recv.buf());
            }
        });
        
        connector.connect(new InetSocketAddress("localhost", 2222));
    }


    //@OnMessage: Decorates the function associated with handling an event on message received
    //@OnError: Decorates the function associated with handling a connection error event
    //@OnClose: Decorates the function associated with handling an event on connection close

	@OnMessage
	public void onMessage(byte[] msg, Session session) {
	    IoSession ios = sessions.get(session);
	    if (ios == null) {
	        LOG.error("destination is not connected");
	        return;
	    }
	    LOG.error("pajp bajts in: " + new String(msg));
	    ios.write(IoBuffer.wrap(msg));
	}


//    @OnMessage
//    public void onMessage(String msg, Session session) {
//        IoSession ios = sessions.get(session);
//        if (ios == null) {
//            LOG.error("destination is not connected");
//            return;
//        }
//        LOG.error("pajp string in: " + msg);
//        ios.write(IoBuffer.wrap(msg.getBytes()));
//    }
}
