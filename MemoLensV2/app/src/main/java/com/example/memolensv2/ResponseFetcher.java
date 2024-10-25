package com.example.memolensv2;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.util.Log;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResponseFetcher {
    private String bucketName = "Bucket1";
    private String folderPath = "Outputmp3/";
    private AmazonS3Client s3Client;
    private Context context;
    private String audioPath, textPath;
    private SharedPreferences sharedPreferences;
    private MediaPlayer mediaPlayer;

    // Replace these with your own API keys
    private static final String ACCESS_KEY = "Key1";
    private static final String SECRET_KEY = "Key2";

    public ResponseFetcher(Context context, String audioFile, String textFile) {
        this.context = context;
        audioPath = audioFile;
        textPath = textFile;
        s3Client = new AmazonS3Client(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
        sharedPreferences = this.context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
    }

    public void beginPolling() {
        new Thread(() -> {
            boolean updated = false;
            while (!updated) {
                try {
                    Thread.sleep(3000);

                    String currentResponse = getTextResponse();
                    String lastResponse = sharedPreferences.getString("lastResponse", "");

                    if (!currentResponse.equals(lastResponse)) {
                        updated = true;
                        sharedPreferences.edit().putString("lastResponse", currentResponse).apply();
                        Log.d("Polling", "Updated response: " + currentResponse);
                        fetchAudio();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private String getTextResponse() {
        try {
            S3Object s3Object = s3Client.getObject(bucketName, folderPath + textPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            return response.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public void fetchAudio() {
        try {
            S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucketName, folderPath + audioPath));
            InputStream inputStream = s3Object.getObjectContent();

            File tempFile = File.createTempFile("temp_audio", ".mp3");
            tempFile.deleteOnExit();

            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(tempFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
