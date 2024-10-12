package com.example.memolensv2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class MainActivity extends Activity {
    private static final String TAG = "CameraCaptureApp";
    private TextureView mTextureView;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSessions;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Button mTakePictureButton;

    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Button mRecordButton;
    private boolean isRecording = false;
    private Drawable originalButtonBackground;
    private AudioRecord audioRecord;
    private ExecutorService executorService;
    private int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private ByteArrayOutputStream byteOutputStream;  // In-memory storage for audio data
    private static final int REQUEST_AUDIO_PERMISSION_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(textureListener);

        mTakePictureButton = findViewById(R.id.btn_capture);
        mTakePictureButton.setOnClickListener(v -> takePicture());

        // Find the buttons
        mRecordButton = findViewById(R.id.btn_record);
        originalButtonBackground = mRecordButton.getBackground();

        // Initialize the executor service for asynchronous tasks
        executorService = Executors.newSingleThreadExecutor();

        // Set up the recording button
        mRecordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
                mRecordButton.setText("Buzz");
                mRecordButton.setBackground(originalButtonBackground);
            } else {
                // Check for permissions before starting recording
                if (checkPermissions()) {
                    startRecording();
                    mRecordButton.setText("Stop Recording");
                    mRecordButton.setBackgroundColor(Color.RED);
                } else {
                    requestPermissions();
                }
            }
            isRecording = !isRecording;
        });

        startBackgroundThread();
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            closeCamera();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            mCameraId = cameraManager.getCameraIdList()[0];
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            cameraManager.openCamera(mCameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    };

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(640, 480);
            Surface surface = new Surface(texture);

            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCameraCaptureSessions = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            mCameraCaptureSessions.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void takePicture() {
        if (mCameraDevice == null) {
            return;
        }

        try {
            ImageReader reader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = Arrays.asList(reader.getSurface(), new Surface(mTextureView.getSurfaceTexture()));

            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(reader.getSurface());

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

            // Create a capture session for the capture request and the output surfaces
            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(mCaptureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                           @NonNull CaptureRequest request,
                                                           @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                createCameraPreview();  // Restart camera preview after capturing the image
                            }
                        }, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireNextImage();
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] imageBytes = new byte[buffer.capacity()];
                    buffer.get(imageBytes);

                    // Compress the image before sending it
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);  // 80% quality to reduce size
                    byte[] compressedBytes = stream.toByteArray();

                    // Execute the image upload task asynchronously using ExecutorService
                    new ImageUploadTask(compressedBytes, "http://10.136.9.145:5000/upload", MainActivity.this).uploadImage();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();  // Important: Close the image to avoid caching issues
                }
            }
        }
    };

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
                finish();
            }
        }

        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Audio recording permission granted");
                startRecording();
            } else {
                Log.w(TAG, "Audio recording permission denied");
                Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startRecording() {
        // Check if the RECORD_AUDIO permission is granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Requesting audio recording permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
            return;
        }

        // Proceed with recording since permission is granted
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        audioRecord.startRecording();

        byteOutputStream = new ByteArrayOutputStream();  // Initialize in-memory storage

        // Run the recording in a background thread using ExecutorService
        executorService.submit(this::writeAudioDataToMemory);

        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
    }

    private boolean checkPermissions() {
        // Check if permission to record audio is granted
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        // Request the audio recording permission
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
    }


    private void writeAudioDataToMemory() {
        byte[] audioData = new byte[bufferSize];
        int totalBytesRead = 0;

        while (isRecording) {
            int bytesRead = audioRecord.read(audioData, 0, audioData.length);
            if (bytesRead > 0) {
                byteOutputStream.write(audioData, 0, bytesRead);
                totalBytesRead += bytesRead;
                Log.d(TAG, "Bytes read: " + bytesRead + ", Total bytes: " + totalBytesRead);
            }
        }

        Log.d(TAG, "Finished writing audio data to memory. Total bytes: " + totalBytesRead);
    }

    private void stopRecording() {
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;

            // Process the in-memory PCM data and convert it to WAV format asynchronously
            executorService.submit(() -> convertPcmToWavInMemory());

            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void convertPcmToWavInMemory() {
        byte[] pcmData = byteOutputStream.toByteArray();  // Get the in-memory PCM data
        ByteArrayOutputStream wavOutputStream = new ByteArrayOutputStream();

        // Write WAV header and PCM data into wavOutputStream
        PcmToWavConverter.convertInMemory(pcmData, wavOutputStream, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        // Upload the WAV data to AWS S3
        AudioUploadTask uploadTask = new AudioUploadTask(wavOutputStream.toByteArray(), this);
        uploadTask.uploadAudioFile("voice_input.wav");
    }

    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCamera();
    }
}