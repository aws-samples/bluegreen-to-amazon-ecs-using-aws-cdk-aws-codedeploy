/* (C)2023 */
package com.example.demo.service.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Example resource returns example.html directly at the root context.
 */
@Path("/")
public class ExampleResource {

    private static String HTML = null;

    public ExampleResource() {
        if (ExampleResource.HTML == null) ExampleResource.HTML = getFile(getPathHTML() + "example.html");
    }

    public String getPathHTML() {
        return this.getClass()
                        .getName()
                        .substring(0, this.getClass().getName().lastIndexOf("."))
                        .replace(".", "/") + "/";
    }

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String example() {
        return ExampleResource.HTML;
    }

    private String getFile(String filename) {

        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            // Use resource
            final String fileAsText = reader.lines().collect(Collectors.joining());
            return fileAsText;
        } catch (IOException ioe) {
            System.out.println("ExampleResource::Cannot HTML file " + filename + ". IOException:" + ioe.getMessage());
            ioe.printStackTrace();
            return "";
        }
    }

    public static void main(String args[]) {

        ExampleResource e = new ExampleResource();
        System.out.println(e.getPathHTML());
    }
}
