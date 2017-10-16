package sk.javot.nydus.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class ForwarderListener implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(Forwarder.class);

    Integer forwarderPort;
    Forwarder forwarder;
    WebSocketContainer container;

    volatile IoAcceptor acceptor;
    

    public ForwarderListener(Integer listeningPort, Forwarder forwarder, WebSocketContainer container) {
        this.forwarderPort = listeningPort;
        this.container = container;
        this.forwarder = forwarder;
    }


    @Override
    public void start() {
        if (! isRunning()) {
            synchronized (this) {
                if (! isRunning()) {
                    NioSocketAcceptor acceptor = new NioSocketAcceptor();
                    acceptor.getFilterChain().addLast("logger", new LoggingFilter());
                    acceptor.setHandler(new IoHandlerAdapter() {


                        @Override
                        public void sessionOpened(IoSession ios) throws Exception {
                            Session session = container.connectToServer(forwarder, forwarder.destination()); // TODO spring way somehow?
                            session.getUserProperties().put(IoSession.class.getName(), ios);
                            ios.setAttribute(Session.class, session);
                        }


                        @Override
                        public void sessionClosed(IoSession ios) throws Exception {
                            Optional<Session> os = Optional.of((Session) ios.getAttribute(Session.class));
                            os.ifPresent(session -> {
                                try {
                                    session.close();
                                    LOG.debug("ws session closed: {} ", session);
                                } catch (IOException e) {
                                    LOG.warn("unable to close ws session: {} ", e);
                                }
                            });
                        }


                        @Override
                        public void messageReceived(IoSession ios, Object message) throws Exception {
                            Optional<Session> os = Optional.of((Session) ios.getAttribute(Session.class));
                            os.ifPresent(session -> {
                                LOG.debug("received: {} ", message);
                                IoBuffer recv = (IoBuffer) message;
                                RemoteEndpoint.Async remote = session.getAsyncRemote();
                                remote.sendBinary(recv.buf());
                            });
                        }
                    });
                    acceptor.getSessionConfig().setReadBufferSize(512);
                    acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, 100);
                    try {
                        acceptor.bind(new InetSocketAddress(forwarderPort));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    this.acceptor = acceptor;
                }
            }
        }
    }


    @Override
    public boolean isRunning() {
        return acceptor != null;
    }


    @Override
    public int getPhase() {
        return 0;
    }


    @Override
    public boolean isAutoStartup() {
        return true;
    }



    @Override
    public final void stop() {
        if (isRunning()) {
            synchronized (this) {
                if (isRunning()) {
                    LOG.info("stopping {}", this);
                    acceptor.dispose(true);
                    acceptor.unbind();
                    acceptor = null;
                }                
            }
        }
    }


    @Override
    public final synchronized void stop(Runnable callback) {
        stop();
        callback.run();
    }
}
