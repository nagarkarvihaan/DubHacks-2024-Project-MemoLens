package com.example.memolensv2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioUploadTask {
    private static final String TAG = "AudioUploadTask";
    private byte[] audioBytes;  // The in-memory audio data (e.g., WAV)
    private String bucketName = "vuzix-audio-bucket"; // Replace with your S3 bucket name
    private Context context;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // AWS S3 details
    private TransferUtility transferUtility;

    // Replace these with your own API keys
    private static final String ACCESS_KEY = "AKIA4SYAMJUQI73QQ4XF";
    private static final String SECRET_KEY = "rU21X/KDArxo0mLfLVrMspylmrOujS6KaB9Pzm2/";

    // Constructor
    public AudioUploadTask(byte[] audioBytes, Context context) {
        this.audioBytes = audioBytes;
        this.context = context;

        // Set up the S3 client with hardcoded credentials
        AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));

        // Set up TransferUtility for file upload
        transferUtility = TransferUtility.builder()
                .context(context)
                .s3Client(s3Client)
                .build();

        TransferNetworkLossHandler.getInstance(context);
    }

    // Method to upload audio asynchronously with a custom file name
    public void uploadAudioFile(String customFileName) {
        executorService.execute(() -> {
            try {
                // Create temporary file from the audio data
                File tempFile = createTempWavFile(audioBytes);

                if (tempFile != null) {
                    // Set the folder path to 'uploads/' and use the custom file name
                    String folderPath = "uploads/";  // This will store files in the 'uploads' folder

                    // Upload the file to S3 with the folder path and custom file name
                    transferUtility.upload(
                            bucketName,  // The bucket to upload to
                            folderPath + customFileName,  // The key for the object (including folder path and custom file name)
                            tempFile  // The file to upload
                    ).setTransferListener(new TransferListener() {
                        @Override
                        public void onStateChanged(int id, TransferState state) {
                            if (state == TransferState.COMPLETED) {
                                Log.d(TAG, "Upload completed successfully");
                                notifyUploadSuccess();
                            }
                        }

                        @Override
                        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                            Log.d(TAG, "Upload progress: " + bytesCurrent + " / " + bytesTotal);
                        }

                        @Override
                        public void onError(int id, Exception ex) {
                            Log.e(TAG, "Error occurred during upload: " + ex.getMessage(), ex);
                            notifyUploadError(ex);
                        }
                    });
                } else {
                    Log.e(TAG, "Failed to create temp WAV file for upload");
                    notifyUploadError(new Exception("Failed to create temp file"));
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception during upload: " + e.getMessage(), e);
                notifyUploadError(e);
            }
        });
    }

    // Helper method to create a temporary file from the in-memory byte array
    private File createTempWavFile(byte[] audioData) throws IOException {
        File tempFile = File.createTempFile("audio", ".wav", context.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(audioData);
            Log.d(TAG, "Successfully wrote audio data to temp WAV file");
        } catch (IOException e) {
            Log.e(TAG, "Error writing audio data to temp file: " + e.getMessage(), e);
            return null;
        }
        return tempFile;
    }

    // Helper methods to update the UI after upload
    private void notifyUploadSuccess() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, "Upload completed", Toast.LENGTH_SHORT).show();
        });
    }

    private void notifyUploadError(Exception ex) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, "Error uploading: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}