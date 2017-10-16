package sk.javot.nydus.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.ClientContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import sk.javot.nydus.ClientMode;

@Configuration
@EnableAutoConfiguration
public class ClientConfigurator {

    private static final Logger LOG = LoggerFactory.getLogger(ClientConfigurator.class);

    @Value("${pipeUrl}")
    private String pipeUrl; // wss://localhost:8443/pipe

    @Value("${forwarderPort}")
    private String forwarderPort; // 8888

    @Value("${proxyHostPort}")
    private String proxyHostPort; // proxy:6666

    @Value("${proxyUserPwd}")
    private String proxyUserPwd; // username:password


    @Bean
    @Conditional(ClientMode.class)
    public Forwarder forwarderEndpoint() {
        return new Forwarder(pipeUrl);
    }


    @Bean
    @Conditional(ClientMode.class)
    public ForwarderListener forwarderListener(Forwarder fw, WebSocketContainer wsc) {
        return new ForwarderListener(Integer.parseInt(forwarderPort), fw, wsc);
    }


    @Bean
    @Conditional(ClientMode.class)
    public WebSocketContainer webSocketContainer() throws URISyntaxException {
        WebSocketContainer wsc = ContainerProvider.getWebSocketContainer();
        Assert.state(wsc instanceof ClientContainer, "use jetty as the client container");
        // SSL
        ClientContainer cc = (ClientContainer) wsc;
        SslContextFactory f = cc.getSslContextFactory();
        if (f != null) {
            f.setTrustAll(true);
            LOG.debug("websockets ssl client set to trust all server certs");
        }
        // AGENT
        HttpClient client = cc.getClient().getHttpClient();
        client.setUserAgentField(new HttpField(HttpHeader.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3_1 like Mac OS X) AppleWebKit/603.1.30 (KHTML, like Gecko) Version/10.0 Mobile/14E304 Safari/602.1"));
        // PROXY
        String[] hostPort;
        if (StringUtils.isEmpty(proxyHostPort) || (hostPort = proxyHostPort.split(":")).length < 2) {
            LOG.error("wrong proxy format: should be proxyHost:proxyPort ");
            return wsc;
        }
        HttpProxy proxy = new HttpProxy(hostPort[0], Integer.parseInt(hostPort[1]));
        LOG.debug("websockets http proxy: {}", proxy);
        client.getProxyConfiguration().getProxies().add(0, proxy);
        // PROXY authentication // bug: workround: https://github.com/eclipse/jetty.project/issues/138
        final URI proxyUri = new URI(String.format("http://%s:%s", hostPort[0], hostPort[1]));
        final String value = "Basic " + B64Code.encode(proxyUserPwd, StandardCharsets.UTF_8);
        client.getAuthenticationStore().addAuthenticationResult(new Authentication.Result() {

            @Override
            public URI getURI() {
                return proxyUri;
            }

            @Override
            public void apply(Request request) {
                request.header(HttpHeader.PROXY_AUTHORIZATION, value);
            }
        });
        return wsc;
    }
}
