import logging
import base64
import boto3
import json
import time
import random
import re
import uuid

# Initialize logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger()

# Initialize the Transcribe and Bedrock clients
transcoder_client = boto3.client('elastictranscoder')
transcribe_client = boto3.client('transcribe')
s3_client = boto3.client('s3') # Initialize S3 client
bedrock_client = boto3.client('bedrock-runtime', region_name='us-west-2') # Ensure you have the right permissions for Bedrock
bedrock_agent_runtime = boto3.client(
    service_name="bedrock-agent-runtime", region_name='us-west-2'
)
polly = boto3.client('polly')

def lambda_handler(event, context):
    try:
        # Parse the incoming event (assuming the body contains the base64-encoded audio data)
        #if 'body' in event:
        #body = json.loads(event['body']) # Ensure to parse the JSON body
        #body = body['body']
        #body = event['body']
        
        # audio_data_base64 = body['audioData'] # Base64-encoded audio data
        # # Decode the audio file
        # logger.info("Decoding audio data...")
        # audio_data = base64.b64decode(audio_data_base64)
        # logger.info("Audio data decoded successfully.")



        # Upload audio to S3 (needed for Transcribe service)
        s3_bucket = 'vuzix-audio-bucket'
        s3_key = 'uploads/audio_recording.3gp'

        transcoder_client.create_job(
            PipelineId='1728805850855-grjruo',  # Replace with your pipeline ID
            Input={
                'Key': s3_key,
                'FrameRate': 'auto',
                'Resolution': 'auto',
                'AspectRatio': 'auto',
                'Interlaced': 'auto',
                'Container': '3gp',
            },
            Outputs=[{
                'Key': s3_key.replace('.3gp', '.wav'),  # Change extension to .wav
                'PresetId': '1351620000001-300200',  # Replace with your WAV preset ID
                'ThumbnailPattern': '',
                'Rotate': 'auto',
            }],
            UserMetadata={
                'example_key': 'example_value',  # Optional metadata
            }
        )

        # logger.info(f"Uploading audio to S3 bucket '{s3_bucket}' with key '{s3_key}'...")
        # s3_client.put_object(Bucket=s3_bucket, Key=s3_key, Body=audio_data)
        # logger.info("Audio uploaded successfully.")

        # Start the transcription job with Amazon Transcribe
        current_time = time.strftime('%Y%m%d_%H%M%S', time.localtime())
        transcription_job_name = f'VuzixVoiceTranscription_{current_time}'
        logger.info(f"Starting transcription job '{transcription_job_name}'...")

        transcribe_client.start_transcription_job(
            TranscriptionJobName=transcription_job_name,
            Media={'MediaFileUri': f's3://{s3_bucket}/{s3_key}'},
            MediaFormat='wav',
            LanguageCode='en-US',
            OutputBucketName=s3_bucket # Direct the output to your S3 bucket
        )
        logger.info("Transcription job started successfully.")

        # Poll the job until it is complete
        while True:
            job = transcribe_client.get_transcription_job(TranscriptionJobName=transcription_job_name)
            if job['TranscriptionJob']['TranscriptionJobStatus'] in ['COMPLETED', 'FAILED']:
                break
            time.sleep(5) # Add a delay to avoid throttling

        if True or job['TranscriptionJob']['TranscriptionJobStatus'] == 'COMPLETED':
            # The transcription result is stored in your S3 bucket with the job name as the key
            transcription_bucket = s3_bucket
            TranscriptionKey = f'{transcription_job_name}.json'

            # Log the bucket and key information
            logger.info(f"Transcription Bucket: {transcription_bucket}")
            logger.info(f"Transcription Key: {TranscriptionKey}")

            #Try to get the transcription result from S3
            try:
                transcription_object = s3_client.get_object(Bucket=transcription_bucket, Key=TranscriptionKey)
                transcription_result = json.loads(transcription_object['Body'].read().decode('utf-8'))
                logger.info("Transcription result fetched successfully.")
            except s3_client.exceptions.NoSuchKey:
                logger.error(f"Object with key '{TranscriptionKey}' does NOT exist in bucket '{transcription_bucket}'.")
                return {
                    'statusCode': 500,
                    'body': json.dumps({'error': f"Object '{TranscriptionKey}' does not exist."})
                }
            except Exception as e:
                logger.error(f"Error accessing the object: {str(e)}")
                return {
                    'statusCode': 500,
                    'body': json.dumps({'error': str(e)})
                }

            transcribed_text = transcription_result['results']['transcripts'][0]['transcript']
            #transcribed_text = "Introduce yourself"
            print(transcribed_text)

            # Process with Bedrock AI agent
            bedrock_response = process_with_bedrock(transcribed_text)
            print(bedrock_response)
            audioResponse = polly.synthesize_speech(
                Text = bedrock_response,
                OutputFormat = 'mp3', 
                VoiceId = 'Matthew'
            )
            output_audio = audioResponse['AudioStream'].read()
            s3_client.put_object(
                Bucket = s3_bucket,
                Key = "Outputmp3/ttsAudio.mp3",
                Body=output_audio,
                ContentType='audio/mpeg'
            )
            bedrock_response_bytes = bedrock_response.encode('utf-8')
            
            s3_client.put_object(
                Bucket=s3_bucket,
                Key="Outputmp3/agent_text_response.txt",
                Body=bedrock_response_bytes,
                ContentType='text/plain'
            )

            # Return the response to the Vuzix companion app
            return {
                'statusCode': 200,
                'body': json.dumps({
                    'message': 'Voice processed successfully',
                    'response': bedrock_response
                })
            }

        return {
            'statusCode': 400,
            'body': json.dumps({'message': 'Invalid request, no audio data found'})
        }

    except Exception as e:
        logger.error(f"Error occurred: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }

def process_with_bedrock(transcribed_text):
    # Send the transcribed text to your Bedrock agent for further processing
    logger.info(f"Processing transcribed text with Bedrock: {transcribed_text}")
    try:
        new_session_id = str(uuid.uuid4())
        transcribed_text = re.sub(r'[.!?]+$', '', transcribed_text).strip()
        response = bedrock_agent_runtime.invoke_agent(
            agentAliasId="TSTALIASID",
            agentId="XEDW8JMJYW",
            sessionId=new_session_id,
            inputText=transcribed_text
        )
        logger.info("Bedrock processing completed successfully.")
        # Get the event stream from the response
        event_stream = response["completion"]

        # Initialize a variable to hold the full response text
        full_response = {"text": "", "traces": []}
        # Process each event in the stream
        for event in event_stream:
            if 'chunk' in event:
                if 'bytes' in event['chunk']:
                    message_bytes = event['chunk']['bytes']  # Get the bytes
                    message = message_bytes.decode('utf-8')
                    full_response["text"] = message
        # Return the full response including both the agent's text and any traces
        return full_response["text"]
    except Exception as e:
        logger.error(f"Error processing with Bedrock: {str(e)}")
        raise e
