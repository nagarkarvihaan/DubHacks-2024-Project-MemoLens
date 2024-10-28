# MemoLens - Smart Glasses for Alzheimer's Assistance

MemoLens is an Android application built on a pair of smart glasses designed to assist individuals with Alzheimer's by leveraging advanced AI for facial recognition and voice interaction through a cloud-based LLM agent. Built for the Vuzix M400 smart glasses, MemoLens integrates AI models and AWS services for seamless, real-time interaction.

## Features
- **Facial Recognition**: Identify individuals using the smart glasses camera with local facial recognition processing.
- **Audio Interaction with LLM**: Record audio, convert it to text, and receive responses via the glasses’ speakers using AWS services like Transcribe, Polly, and Llama.

## System Overview

### 1. **Facial Recognition**
   - Programmatically takes control of the Vuzix M400’s camera for camera management and to capture and process images.
   - **Local Processing**: Using Deepface, facial embeddings are extracted from the image taken and then compared against existing embeddings from a dataset that we trained.

### 2. **Audio Interaction Workflow**
   - The audio is recorded through the smart glasses’ microphone.
   - The recorded 3gp file is uploaded to an **AWS S3 bucket** using the Android app.
   - An **AWS Lambda** function is triggered to convert the 3gp file to WAV format.
   - The WAV file is transcribed into text using **Amazon Transcribe**.
   - The transcription is passed as a prompt to the **Llama LLM** (Large Language Model) to generate a contextually relevant response.
   - The generated response is converted to an MP3 audio file using **Amazon Polly**.
   - The MP3 file is pushed back to the same S3 bucket, retrieved by the Android app, and played through the glasses’ speakers.

## Technologies Used
- **Android (Java)**: Core application for the Vuzix M400 smart glasses.
- **AWS Services**: 
  - **S3**: Cloud storage for audio files.
  - **Lambda**: For triggering the file format conversion and processing workflow.
  - **Transcribe**: To convert the audio input to text.
  - **Polly**: To convert LLM-generated responses from text to speech (MP3).
  - **Bedrock & Llama LLM**: For LLM-based response generation.
- **Deepface**: For facial recognition processing via embedding comparison.
- **Flask**: Backend server for managing facial recognition workflow.
