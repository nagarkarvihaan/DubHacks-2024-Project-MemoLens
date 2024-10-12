package com.example.memolensv2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageUploadTask {
    private static final String TAG = "ImageUploadTask";
    private byte[] imageBytes;
    private String serverUrl;
    private Context context;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ImageUploadTask(byte[] imageBytes, String serverUrl, Context context) {
        this.imageBytes = imageBytes;
        this.serverUrl = serverUrl;
        this.context = context;
    }

    public void uploadImage() {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            String response = "";
            try {
                URL url = new URL(serverUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/octet-stream");

                // Set timeouts (e.g., 10 minutes)
                connection.setConnectTimeout(600000);  // 10 minutes
                connection.setReadTimeout(600000);     // 10 minutes

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
                    response = new String(responseBuffer, 0, length);
                    Log.d(TAG, "Server Response: " + response);
                } else {
                    Log.d(TAG, "Server returned non-OK status: " + responseCode);
                    response = "Server error: " + responseCode;
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Error: " + e.getMessage());
                response = "Error: " + e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            // Update the UI on the main thread
            String finalResponse = response;
            new Handler(Looper.getMainLooper()).post(() -> {
                // Customize toast messages based on the server response
                if (finalResponse.contains("Person in picture: Adil")) {
                    Toast.makeText(context, "This is Adil, your brother.", Toast.LENGTH_LONG).show();
                } else if (finalResponse.contains("Not able to recognize anyone in picture")) {
                    Toast.makeText(context, "Not able to recognize anyone in picture.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "Upload result: " + finalResponse, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}