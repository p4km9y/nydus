package sk.javot.nydus.client;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ClientEndpoint
public class Forwarder {
    private static final Logger LOG = LoggerFactory.getLogger(Forwarder.class);

    Map<Session, IoSession> sessions = new HashMap<>();

    /**
     *     Decorates the function associated with handling a connection open event
     */
    @OnOpen
    public void open(final Session session) throws Exception {
        IoAcceptor acceptor = new NioSocketAcceptor();
        acceptor.getFilterChain().addLast( "logger", new LoggingFilter() );
        acceptor.setHandler(new IoHandlerAdapter() {


            @Override
            public void sessionOpened(IoSession ios) throws Exception {
                sessions.put(session, ios);
            }


            @Override
            public void sessionClosed(IoSession ios) throws Exception {
                session.close();
                acceptor.unbind();
            }

            
            @Override
            public void messageReceived(IoSession ios, Object message) throws Exception {
                System.out.println("8888 in ***** " + message);
                IoBuffer recv = (IoBuffer) message;
                RemoteEndpoint.Async remote = session.getAsyncRemote();
                remote.sendBinary(recv.buf());
            }
        });
        //acceptor.getSessionConfig().setReadBufferSize( 2048 );
        //acceptor.getSessionConfig().setIdleTime( IdleStatus.BOTH_IDLE, 10 );
        acceptor.bind( new InetSocketAddress(8888) );
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
        LOG.error("fw bytes in: " + new String(msg));
        ios.write(IoBuffer.wrap(msg));
    }


//    @OnMessage
//    public void onMessage(String msg, Session session) {
//        IoSession ios = sessions.get(session);
//        if (ios == null) {
//            LOG.error("destination is not connected");
//            return;
//        }
//        LOG.error("fw string in: " + msg);
//        ios.write(IoBuffer.wrap(msg.getBytes()));
//    }
}
