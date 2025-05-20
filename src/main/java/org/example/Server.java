package org.example;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final int port;
    private final List<String> validPaths;
    private final Path publicDirectory;
    private final ExecutorService executorService;

    public Server(int port, List<String> validPaths, String publicDir) {
        this.port = port;
        this.validPaths = validPaths;
        this.publicDirectory = Path.of(publicDir);
        this.executorService = Executors.newFixedThreadPool(64);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                executorService.submit(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdown();
        }
    }

    private void handleConnection(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            String requestLine = in.readLine();
            String[] parts = requestLine.split(" ");

            if (parts.length != 3) {
                return;
            }

            String path = parts[1];
            if (!validPaths.contains(path)) {
                sendResponse(out, 404, "Not Found", null, 0);
                return;
            }

            Path filePath = publicDirectory.resolve(path.substring(1));
            String mimeType = Files.probeContentType(filePath);

            if (path.equals("/classic.html")) {
                String template = Files.readString(filePath);
                String content = template.replace("{time}", LocalDateTime.now().toString());
                sendResponse(out, 200, "OK", mimeType, content.getBytes().length);
                out.write(content.getBytes());
                out.flush();
                return;
            }

            long length = Files.size(filePath);
            sendResponse(out, 200, "OK", mimeType, length);
            Files.copy(filePath, out);
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendResponse(OutputStream out, int status, String statusText, String mimeType, long contentLength) throws IOException {
        String response = String.format(
                "HTTP/1.1 %d %s\r\n" +
                        "Content-Type: %s\r\n" +
                        "Content-Length: %d\r\n" +
                        "Connection: close\r\n\r\n",
                status, statusText, mimeType != null ? mimeType : "application/octet-stream", contentLength);

        out.write(response.getBytes());
    }
}
