package sk.javot.nydus.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ClientEndpoint
public class Forwarder {

    private static final Logger LOG = LoggerFactory.getLogger(Forwarder.class);

    String destination;
    Map<Session, IoSession> sessions = new HashMap<>();


    public Forwarder(String destination) {
        this.destination = destination;
    }


    /**
     *     Decorates the function associated with handling a connection open event
     */
    @OnOpen
    public void open(final Session session) throws Exception {

        
        session.close();

    }


    @OnMessage
    public void onMessage(byte[] msg, Session session) {
        IoSession ios = sessions.get(session);
        if (ios == null) {
            LOG.error("destination is not connected");
            return;
        }
        LOG.error("send: {}", Arrays.asList(msg));
        ios.write(IoBuffer.wrap(msg));
    }


    @OnClose
    public void onClose(Session session) {
        LOG.debug("on close called");
        IoSession ios = sessions.get(session);
        if (ios == null) {
            return;
        }
        ios.closeNow();
    }


    @Override
    public void onApplicationEvent(MinaSessionOpened event) {
        sessions.put(session, ios);

    }


    public URI destination() {
        try {
            return new URI(destination);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
