/* (C)2023 */
package com.example.demo.service.api;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Jersey Example microservice implemented using a Grizzly HTTP server.
 *
 * @author Luiz Decaro
 */
public class Main {

    public Main() {}

    public static void main(String[] args) {

        try {
            System.out.println("Running Java Version: " + System.getProperty("java.version"));
            System.out.println("\"Example\" Service");

            HttpServer server = (new Main()).startServer();

            System.out.println("Application started.\n"
                    + "Try accessing " + Main.getBaseURI() + " in the browser.\n"
                    + "Hit ^C to stop the application...");
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    server.shutdownNow();
                }
            }));

            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception msg:" + e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Throwable msg:" + t.getMessage());
        }
    }

    /**
     * Creates a JSON HTTP Web Service using Grizzly and Jersey.
     *
     * @return new instance of the Grizzly HTTP server
     * @throws IOException
     */
    HttpServer startServer() throws IOException {

        final ResourceConfig rc =
                new ResourceConfig().packages(this.getClass().getPackage().getName());
        return GrizzlyHttpServerFactory.createHttpServer(Main.getBaseURI(), rc);
    }

    static URI getBaseURI() throws UnknownHostException {
        String addr = "http://"
                + InetAddress.getLocalHost()
                        .toString()
                        .substring(0, InetAddress.getLocalHost().toString().indexOf("/")) + ":" + getPort(8080) + "/";
        System.out.println(addr);
        return URI.create(addr);
    }

    private static int getPort(int defaultPort) {
        final String port = System.getProperty("jersey.config.test.container.port");
        if (null != port) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException e) {
                System.out.println("Value of jersey.config.test.container.port property"
                        + " is not a valid positive integer [" + port + "]."
                        + " Reverting to default [" + defaultPort + "].");
            }
        }
        return defaultPort;
    }
}
