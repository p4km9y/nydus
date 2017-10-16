package sk.javot.nydus;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import javax.servlet.Servlet;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.ClientContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.client.standard.AnnotatedEndpointConnectionManager;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import sk.javot.nydus.client.Forwarder;
import sk.javot.nydus.server.PipeWebSocketEndpoint;
import sk.javot.nydus.server.SpringConfigurator;
import sk.javot.nydus.server.StatusServlet;

/**
 * keytool -genkey -v -alias jetty -keyalg RSA -keysize 2048 -keystore keystore.jks -validity 3650 -providername SUN http://www.websocket.org/echo.html
 * https://spring.io/blog/2013/05/23/spring-framework-4-0-m1-websocket-support org.eclipse.jetty.websocket.jsr356.ssl-trust-all=true
 * 
 * --type=client --pipeUrl='wss://10.230.18.8:8443/pipe' --proxyHostPort=localhost:6666 --proxyUserPwd=user:pwd --forwarderPort=8888
 * 
 * --type=server --targetHostPort=localhost:2222 --pipeListenerPort=8443
 * 
 * 
 * @author patrik
 */
@Configuration
@EnableAutoConfiguration
@ServletComponentScan
public class Application extends SpringBootServletInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Value("${keystore}")
    private String keystore;

    @Value("${keystorePassword}")
    private String keystorePassword;

    @Value("${pipeUrl}")
    private String pipeUrl; // wss://localhost:8443/pipe

    @Value("${pipeListenerPort}")
    private String pipeListenerPort; // 8443

    @Value("${proxyHostPort}")
    private String proxyHostPort; // proxy:6666

    @Value("${proxyUserPwd}")
    private String proxyUserPwd; // username:password

    @Value("${forwarderPort}")
    private String forwarderPort; // 8888

    @Value("${targetHostPort}")
    private String targetHostPort; // localhost:22


    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }


    @Bean
    public Servlet status() {
        return new StatusServlet();
    }


    @Bean
    @Conditional(ServerMode.class)
    public PipeWebSocketEndpoint pipeWebSocketEndpoint() {
        return new PipeWebSocketEndpoint(targetHostPort);
    }


    @Bean
    public SpringConfigurator customSpringConfigurator() {
        return new SpringConfigurator(); // This is just to get context
    }


    @Bean
    @Conditional(ServerMode.class)
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }


    @Bean
    @Conditional(ServerMode.class)
    public EmbeddedServletContainerCustomizer embeddedServletContainerCustomizer() throws Exception {
        final String absoluteKeystoreFile = ResourceUtils.getFile(keystore).getAbsolutePath();
        LOG.debug("keystore file: {}", absoluteKeystoreFile);
        return new EmbeddedServletContainerCustomizer() {

            @Override
            public void customize(ConfigurableEmbeddedServletContainer factory) {
                Assert.state(factory instanceof JettyEmbeddedServletContainerFactory, "use jetty for this server");
                JettyEmbeddedServletContainerFactory jettyFactory = (JettyEmbeddedServletContainerFactory) factory;
                jettyFactory.addServerCustomizers(new JettyServerCustomizer() {

                    @Override
                    public void customize(Server server) {
                        SslContextFactory sslContextFactory = new SslContextFactory();
                        sslContextFactory.setKeyStorePath(absoluteKeystoreFile);
                        sslContextFactory.setKeyStorePassword(keystorePassword);

                        ServerConnector sslConnector = new ServerConnector(server, sslContextFactory);
                        sslConnector.setPort(Integer.parseInt(pipeListenerPort));
                        server.setConnectors(new Connector[] { sslConnector });
                    }
                });
            }
        };
    }


    @Bean
    @Conditional(ClientMode.class)
    public AnnotatedEndpointConnectionManager connectionManager(Forwarder forwarder) throws URISyntaxException {
        AnnotatedEndpointConnectionManager em = new AnnotatedEndpointConnectionManager(forwarder, pipeUrl);
        em.setAutoStartup(true);
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
            return em;
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
        return em;
    }


    @Bean
    @Conditional(ClientMode.class)
    public Forwarder forwarderEndpoint() {
        return new Forwarder(Integer.parseInt(forwarderPort));
    }


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
