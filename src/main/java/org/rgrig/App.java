package org.rgrig;

import io.github.cdimascio.dotenv.Dotenv;
import org.java_websocket.client.WebSocketClient;

import java.net.URI;
import java.net.URISyntaxException;

public class App {
    public static void main(final String[] args) throws URISyntaxException {
        String uri;
        String id;
        String token;
        if (args.length != 3) {
            Dotenv dotenv;
             System.out.println(System.getProperty("user.dir").toString());;

            try {
                Dotenv.configure()
                        .directory("./")
                        .load();
                dotenv = Dotenv.load();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("usage: <cmd> <server-ip> <kent-id> <access-token>");
                System.err.println("or provide a id and token in your .env");
                return;
            }
            uri = String.format("ws://%s:1234", "129.12.44.229");
            id = dotenv.get("ID");
            token = dotenv.get("TOKEN");
            if (id == null || id.isEmpty() || token == null || token.isEmpty()) {
                System.err.println("usage: <cmd> <server-ip> <kent-id> <access-token>");
                System.err.println("or provide a id and token in your .env");
                return;
            }

        } else {
            uri = String.format("ws://%s:1234", args[0]);
            id = args[1];
            token = args[2];
        }
        System.out.println(String.format("uri: %s", uri));
        System.out.println(String.format("id: %s", id));
        System.out.println(String.format("token: %s", token));
        WebSocketClient client = new Client(new URI(uri), id, token);
        client.connect();
    }
}
