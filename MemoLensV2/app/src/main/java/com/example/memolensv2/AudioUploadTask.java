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

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//  private static final String ACCESS_KEY = "AKIA4SYAMJUQI73QQ4XF";  // Replace with your AWS Access Key
//    private static final String SECRET_KEY = "rU21X/KDArxo0mLfLVrMspylmrOujS6KaB9Pzm2/";  // Replace with your AWS Secret Key
//    private static final String BUCKET_NAME = "vuzix-audio-bucket";

public class AudioUploadTask {
    private static final String TAG = "AudioUploadTask";
    private String filePath;  // Path to the audio file to be uploaded
    private String bucketName = "vuzix-audio-bucket";  // Replace with your S3 bucket name
    private Context context;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // AWS S3 details
    private TransferUtility transferUtility;

    // Replace these with your own AWS credentials
    private static final String ACCESS_KEY = "YOUR_ACCESS_KEY";
    private static final String SECRET_KEY = "YOUR_SECRET_KEY";

    // Constructor: Accepts the file path to the audio file and the context for Toast notifications
    public AudioUploadTask(String filePath, Context context) {
        this.filePath = filePath;
        this.context = context;

        // Set up the S3 client with hardcoded credentials (for testing purposes)
        AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));

        // Set up TransferUtility for file uploads
        transferUtility = TransferUtility.builder()
                .context(context)
                .s3Client(s3Client)
                .build();

        TransferNetworkLossHandler.getInstance(context);  // Handles network loss scenarios for the upload
    }

    // Method to upload the audio file asynchronously
    public void uploadAudioFile(String customFileName) {
        executorService.execute(() -> {
            File audioFile = new File(filePath);  // Use the file path to get the audio file

            // Ensure the file exists before starting the upload
            if (audioFile.exists()) {
                String folderPath = "uploads/";  // Folder path in S3 for uploading the file

                // Start the upload process
                transferUtility.upload(
                        bucketName,  // The S3 bucket to upload to
                        folderPath + customFileName,  // The key for the S3 object (folder path + file name)
                        audioFile  // The actual file to upload
                ).setTransferListener(new TransferListener() {
                    @Override
                    public void onStateChanged(int id, TransferState state) {
                        // Upload completed successfully
                        if (state == TransferState.COMPLETED) {
                            Log.d(TAG, "Upload completed successfully");

                            // Delete the temporary file after a successful upload
                            if (audioFile.exists()) {
                                if (audioFile.delete()) {
                                    Log.d(TAG, "Temporary file deleted after successful upload.");
                                } else {
                                    Log.e(TAG, "Failed to delete temporary file after upload.");
                                }
                            }

                            notifyUploadSuccess();
                        } else if (state == TransferState.FAILED) {
                            Log.e(TAG, "Upload failed. State: " + state);
                            notifyUploadError(new Exception("Upload failed with state: " + state));
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
                Log.e(TAG, "Audio file does not exist. Cannot upload.");
                notifyUploadError(new Exception("File not found at path: " + filePath));
            }
        });
    }

    // Helper method to notify success via Toast (runs on UI thread)
    private void notifyUploadSuccess() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, "Upload completed successfully", Toast.LENGTH_SHORT).show();
        });
    }

    // Helper method to notify an upload error via Toast (runs on UI thread)
    private void notifyUploadError(Exception ex) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, "Error uploading: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}