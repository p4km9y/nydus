package sk.javot.nydus;

import javax.servlet.Servlet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import sk.javot.nydus.client.ClientConfigurator;
import sk.javot.nydus.server.ServerConfigurator;

/**
 * keytool -genkey -v -alias jetty -keyalg RSA -keysize 2048 -keystore keystore.jks -validity 3650 -providername SUN
 * 
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
@Import({ ClientConfigurator.class, ServerConfigurator.class })
public class Application extends SpringBootServletInitializer {


    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class, ClientConfigurator.class, ServerConfigurator.class);
    }


    @Bean
    public Servlet status() {
        return new StatusServlet();
    }


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
