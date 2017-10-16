package sk.javot.nydus.server;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import sk.javot.nydus.ServerMode;

/**
 * @author patrik
 */
@Configuration
@EnableAutoConfiguration
public class ServerConfigurator {

    private static final Logger LOG = LoggerFactory.getLogger(ServerConfigurator.class);

    @Value("${keystore}")
    private String keystore;

    @Value("${keystorePassword}")
    private String keystorePassword;


    @Value("${pipeListenerPort}")
    private String pipeListenerPort; // 8443


    @Value("${targetHostPort}")
    private String targetHostPort; // localhost:22


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
        return new EmbeddedServletContainerCustomizer() {

            @Override
            public void customize(ConfigurableEmbeddedServletContainer factory) {
                Assert.state(factory instanceof JettyEmbeddedServletContainerFactory, "use jetty for this server");
                JettyEmbeddedServletContainerFactory jettyFactory = (JettyEmbeddedServletContainerFactory) factory;
                jettyFactory.addServerCustomizers(new JettyServerCustomizer() {

                    @Override
                    public void customize(Server server) {
                        SslContextFactory sslContextFactory = new SslContextFactory();
                        try {
                            sslContextFactory.setKeyStoreResource(Resource.newClassPathResource(keystore));
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        LOG.debug("keystore file: {}", sslContextFactory.getKeyStoreResource());
                        sslContextFactory.setKeyStorePassword(keystorePassword);

                        ServerConnector sslConnector = new ServerConnector(server, sslContextFactory);
                        sslConnector.setPort(Integer.parseInt(pipeListenerPort));
                        server.setConnectors(new Connector[] { sslConnector });
                    }
                });
            }
        };
    }
}
