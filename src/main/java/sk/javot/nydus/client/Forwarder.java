package sk.javot.nydus.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.Session;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

@ClientEndpoint
public class Forwarder {

    private static final Logger LOG = LoggerFactory.getLogger(Forwarder.class);

    String destination;


    public Forwarder(String destination) {
        if (StringUtils.isEmpty(destination)) {
            throw new IllegalArgumentException("forwarder destination (--pipeUrl=wss://$host:$port/pipe) cannot be empty");
        }
        this.destination = destination;
    }


    @OnMessage
    public void onMessage(byte[] msg, Session session) {
        Optional<IoSession> os = Optional.ofNullable((IoSession) session.getUserProperties().get(IoSession.class.getName()));
        os.ifPresent(ios -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("send: {}", Arrays.asList(msg));
            }
            ios.write(IoBuffer.wrap(msg));
        });
    }


    @OnClose
    public void onClose(Session session) {
        Optional<IoSession> os = Optional.ofNullable((IoSession) session.getUserProperties().get(IoSession.class.getName()));
        os.ifPresent(ios -> {
            LOG.info("closing mina session: {}", ios);
            ios.closeNow();
        });
    }


    public URI destination() {
        try {
            return new URI(destination);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
