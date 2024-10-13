package com.example.memolensv2;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Toast;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.regions.Regions;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Recorder {

    private static final String TAG = "Recorder";
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String audioFilePath;
    private Context context;
    private ResponseFetcher fetcher;

    // AWS Credentials (hardcoded)
    private static final String ACCESS_KEY = "AKIA4SYAMJUQI73QQ4XF";  // Replace with your AWS Access Key
    private static final String SECRET_KEY = "rU21X/KDArxo0mLfLVrMspylmrOujS6KaB9Pzm2/";  // Replace with your AWS Secret Key
    private static final String BUCKET_NAME = "vuzix-audio-bucket";  // Replace with your S3 bucket name

    // Constructor
    public Recorder(String dir, Context context) {
        this.audioFilePath = dir + "/new_audio.3gp";  // Save audio as .3gp file
        this.context = context;
        fetcher = new ResponseFetcher(context, "ttsAudio.mp3", "agent_text_response.txt");
    }

    // Start recording
    public void startRecording() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(audioFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Stop recording
    public void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Toast.makeText(context, "Recording stopped", Toast.LENGTH_SHORT).show();
        }
    }

    // Play the recorded audio
    private void playAudio() {
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
                Log.d(TAG, "Playback completed.");
            });
        } catch (IOException e) {
            Log.e(TAG, "Error playing audio: " + e.getMessage());
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    // Upload audio to S3
    private void uploadAudio() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            File audioFile = new File(audioFilePath);

            if (audioFile.exists()) {
                // AWS S3 Setup
                AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));

                // Set the correct region (us-west-2 as per your error log)
                s3Client.setRegion(com.amazonaws.regions.Region.getRegion(Regions.US_WEST_2));  // Correct region

                TransferUtility transferUtility = TransferUtility.builder()
                        .context(context)
                        .s3Client(s3Client)
                        .build();

                // Initialize TransferNetworkLossHandler
                TransferNetworkLossHandler.getInstance(context);

                // Start the upload process
                transferUtility.upload(
                        BUCKET_NAME,  // S3 bucket name
                        "uploads/audio_recording.3gp",  // S3 object key (file path in S3)
                        audioFile  // File to upload
                ).setTransferListener(new TransferListener() {
                    @Override
                    public void onStateChanged(int id, TransferState state) {
                        if (state == TransferState.COMPLETED) {
                            Log.d(TAG, "Upload completed successfully");
                            // Optionally, delete the file after successful upload
                            if (audioFile.delete()) {
                                Log.d(TAG, "Temporary file deleted after successful upload.");
                            }
                        } else if (state == TransferState.FAILED) {
                            Log.e(TAG, "Upload failed.");
                        }
                    }

                    @Override
                    public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                        Log.d(TAG, "Upload progress: " + bytesCurrent + " / " + bytesTotal);
                    }

                    @Override
                    public void onError(int id, Exception ex) {
                        Log.e(TAG, "Error during upload: " + ex.getMessage());
                    }
                });
            } else {
                Log.e(TAG, "Audio file does not exist.");
            }
        });
    }

    // Handle stopping, playing, and uploading the recording
    public void onStopButtonClicked() {
        stopRecording();  // Stop recording first
        //playAudio();  // Play the audio after stopping
        uploadAudio();  // Upload the recorded file to AWS S3
        fetcher.beginPolling();

    }
}