package sk.javot.nydus;

import javax.servlet.Servlet;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.io.ssl.SslClientConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.ClientContainer;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.web.socket.client.standard.AnnotatedEndpointConnectionManager;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import sk.javot.nydus.client.Forwarder;
import sk.javot.nydus.server.PipeWebSocketEndpoint;
import sk.javot.nydus.server.StatusServlet;

/**
 * openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365
 * 
 * keytool -genkey -alias jetty -storetype -keyalg RSA -keysize 2048 -keystore keystore.p12 -validity 3650
 * 
 * keytool -genkey -v -alias jetty -keyalg RSA -keysize 2048 -keystore keystore.jks -validity 3650 -providername SUN
 * 
 * http://www.websocket.org/echo.html
 * 
 * https://spring.io/blog/2013/05/23/spring-framework-4-0-m1-websocket-support
 * 
 * org.eclipse.jetty.websocket.jsr356.ssl-trust-all=true
 * 
 * @author patrik
 */
@Configuration
@EnableAutoConfiguration
@ServletComponentScan
public class NydusApplication extends SpringBootServletInitializer {

    @Value("${keystore.file}")
    private String keystoreFile;

    @Value("${keystore.pass}")
    private String keystorePass;


    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(NydusApplication.class);
    }


    @Bean
    public Servlet status() {
        return new StatusServlet();
    }


    @Bean
    public PipeWebSocketEndpoint pipeWebSocketEndpoint() {
        return new PipeWebSocketEndpoint();
    }


    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }


    @Bean
    public EmbeddedServletContainerCustomizer embeddedServletContainerCustomizer() throws Exception {
        final String absoluteKeystoreFile = ResourceUtils.getFile(keystoreFile).getAbsolutePath();

        return new EmbeddedServletContainerCustomizer() {
            @Override
            public void customize(ConfigurableEmbeddedServletContainer factory) {
                Assert.state(factory instanceof JettyEmbeddedServletContainerFactory, "use Jetty for this server");
                JettyEmbeddedServletContainerFactory jettyFactory = (JettyEmbeddedServletContainerFactory) factory;
                jettyFactory.addServerCustomizers(new JettyServerCustomizer() {

                    @Override
                    public void customize(Server server) {
                        SslContextFactory sslContextFactory = new SslContextFactory();
                        sslContextFactory.setKeyStorePath(absoluteKeystoreFile);
                        sslContextFactory.setKeyStorePassword(keystorePass);
                        //sslContextFactory.setValidateCerts(false);

                        ServerConnector sslConnector = new ServerConnector(server, sslContextFactory);
                        sslConnector.setPort(8443);
                        server.setConnectors(new Connector[] { sslConnector });
                    }
                });
            }
        };
    }

    @Bean
    public AnnotatedEndpointConnectionManager connectionManager() {
        AnnotatedEndpointConnectionManager em =
            new AnnotatedEndpointConnectionManager(forwarderEndpoint(), "wss://localhost:8443/pipe");
        em.setAutoStartup(true);
        System.out.println("XXXXXXXXX " + ContainerProvider.getWebSocketContainer());
        WebSocketContainer wsc = ContainerProvider.getWebSocketContainer();
        if (wsc instanceof ClientContainer) {
            ClientContainer cc = (ClientContainer) wsc;
//            cc.getClient().getHttpClient().getProxyConfiguration().getProxies().add(0, new HttpProxy("localhost", 6666));
            SslContextFactory f = cc.getSslContextFactory();
            if (f != null) {
                f.setTrustAll(true);
            }
        }
        return em;
    }

    @Bean
    public Object forwarderEndpoint() {
        return new Forwarder();
    }


    public static void main(String[] args) {
        SpringApplication.run(NydusApplication.class, args);
    }
}
