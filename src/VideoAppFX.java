import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.net.URI;

/**
 * Native desktop app using JavaFX WebView.
 * Embeds the web UI in a native window (no browser chrome).
 */
public class VideoAppFX extends Application {
    private static int PORT = 8090;

    @Override
    public void start(Stage stage) {
        // Start the HTTP server in a background thread
        Thread serverThread = new Thread(() -> {
            try {
                MediaPlayer.startServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "server-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        // Wait for server to start
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        engine.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            System.out.println("WebView load state: " + newState);
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                System.out.println("WebView loaded successfully");
            } else if (newState == javafx.concurrent.Worker.State.FAILED) {
                System.out.println("WebView load FAILED: " + engine.getLoadWorker().getException());
            }
        });
        engine.load("http://localhost:" + PORT + "/");

        Scene scene = new Scene(webView, 1280, 800);
        stage.setTitle("Video App");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { MediaPlayer.SERVER.stop(0); } catch (Exception ignored) {}
        }));
        launch(args);
    }
}