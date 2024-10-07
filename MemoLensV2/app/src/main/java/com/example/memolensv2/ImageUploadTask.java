package com.example.memolensv2;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageUploadTask {
    private static final String TAG = "ImageUploadTask";
    private byte[] imageBytes;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ImageUploadTask(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    public void uploadImage(String serverUrl) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(serverUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/octet-stream");

                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                outputStream.write(imageBytes);
                outputStream.flush();
                outputStream.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    byte[] responseBuffer = new byte[1024];
                    int length = inputStream.read(responseBuffer);
                    inputStream.close();
                    String response = new String(responseBuffer, 0, length);
                    Log.d(TAG, "Server Response: " + response);
                } else {
                    Log.d(TAG, "Server returned non-OK status: " + responseCode);
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Error: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}