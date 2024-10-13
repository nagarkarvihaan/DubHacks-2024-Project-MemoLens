import os
from flask import Flask, request, jsonify
from deepface import DeepFace
import io
from PIL import Image, ExifTags
import tempfile

app = Flask(__name__)

# Path to your stored image of yourself
stored_image_path = "/Users/adilhusain/Downloads/AdilPIcture.jpg"

@app.route('/upload', methods=['POST'])
def upload_image():
    temp_image_file_path = None
    try:
        # Read the raw image data from the request
        image_data = request.data
        print(f"Received image data of length: {len(image_data)}")

        if not image_data:
            return jsonify({"status": "fail", "message": "No image data received"}), 400

        # Convert the byte array into an image using PIL
        image = (Image.open(io.BytesIO(image_data))).rotate(180)
        print(f"Image decoded successfully: {image.format}, size: {image.size}")

        # Correct the image orientation based on EXIF data
        #image = correct_image_orientation(image)

        # Save the image to a temporary file
        with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as temp_image_file:
            image.save(temp_image_file, format='JPEG')
            temp_image_file_path = temp_image_file.name
            print(f"Image saved temporarily at: {temp_image_file_path}")

        # Perform verification between the input image and your stored image
        try:
            verification_result = DeepFace.verify(img1_path=temp_image_file_path, img2_path=stored_image_path, model_name='Facenet', enforce_detection=False)

            # Check the verification result
            if verification_result['verified']:
                print("Match found: Adil")
                return jsonify({
                    "status": "success",
                    "message": "Person in picture: Adil"
                }), 200
            else:
                print("No match found.")
                return jsonify({
                    "status": "success",
                    "message": "Not able to recognize anyone in picture"
                }), 200

        except Exception as e:
            print(f"Verification error: {e}")
            return jsonify({"status": "error", "message": str(e)}), 500

    except Exception as e:
        print(f"An error occurred: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        #Ensure the temporary file is deleted after use or in case of an error
        if temp_image_file_path and os.path.exists(temp_image_file_path):
           os.remove(temp_image_file_path)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)