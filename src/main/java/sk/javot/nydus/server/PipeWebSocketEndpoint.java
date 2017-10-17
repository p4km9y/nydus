package sk.javot.nydus.server;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Optional;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.OnClose;
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
import org.springframework.util.StringUtils;

@ServerEndpoint(
    value="/pipe",
    configurator = SpringConfigurator.class
)
public class PipeWebSocketEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(PipeWebSocketEndpoint.class);

    String targetHostPort; // localhost:22


    public PipeWebSocketEndpoint(String targetHostPort) { // not able to use such constructors without spring configurator
        if (StringUtils.isEmpty(targetHostPort)) {
            throw new IllegalArgumentException("pipe target (--targetHostPort=$host:$port) cannot be empty");
        }
        this.targetHostPort = targetHostPort;
    }


    /**
     * Decorates the function associated with handling a connection open event
     */
    @OnOpen
    public void open(final Session session) {
        final NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(10000);
        connector.getFilterChain().addLast("logger", new LoggingFilter());
        connector.setHandler(new IoHandlerAdapter() {


            @Override
            public void sessionOpened(IoSession ios) throws Exception {
                session.getUserProperties().put(IoSession.class.getName(), ios);
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


            @Override
            public void exceptionCaught(IoSession ios, Throwable cause) throws Exception {
                session.close(new CloseReason(CloseCodes.TRY_AGAIN_LATER, "destination exception: " + cause.getMessage()));
                LOG.warn("target connection exception occured: ws session closed", cause);
            }
        });
        String[] hostPort;
        if (StringUtils.isEmpty(targetHostPort) || (hostPort = targetHostPort.split(":")).length < 2) {
            LOG.error("wrong target format: should be targetHost:targetPort ");
            return;
        }
        LOG.debug("connecting to: {}", Arrays.asList(hostPort));
        connector.connect(new InetSocketAddress(hostPort[0], Integer.parseInt(hostPort[1])));
    }


    // @OnMessage: Decorates the function associated with handling an event on message received
    // @OnError: Decorates the function associated with handling a connection error event
    // @OnClose: Decorates the function associated with handling an event on connection close

    @OnMessage
    public void onMessage(byte[] msg, Session session) {
        Optional<IoSession> os = Optional.ofNullable((IoSession) session.getUserProperties().get(IoSession.class.getName()));
        os.ifPresent(ios -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("receive: {}", Arrays.asList(msg));
            }
            ios.write(IoBuffer.wrap(msg));
        });
    }


    @OnClose
    public void onClose(Session session) {
        Optional<IoSession> os = Optional.ofNullable((IoSession) session.getUserProperties().get(IoSession.class.getName()));
        os.ifPresent(ios -> {
            LOG.info("close called: {}", session);
            ios.closeNow();
        });
    }
}
