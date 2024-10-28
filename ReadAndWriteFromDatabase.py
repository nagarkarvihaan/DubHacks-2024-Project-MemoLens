import json
import urllib.request
import re

def lambda_handler(event, context):
    agent = event['agent']
    actionGroup = event['actionGroup']
    function = event['function']
    parameters_list = event.get('parameters', {})
    print(parameters_list)
    parameters = {param['name']: param['value'] for param in parameters_list}

    def createMedInfo():
        # Extract input variables from the parameters object
        dosage = parameters["Dosage"]
        interval = parameters["Interval"]  # No integer conversion
        name = parameters["MedicationName"]
        instructions = parameters["Instructions"]
        firebaseProjectId = "YOUR_PROJECT_ID"
        firebaseDatabase = "YOUR_DATABASE"  # Default database name
        firebaseCollection = "YOUR_COLLECTION"
        firebaseApiKey = "YOUR_API_KEY"

        # Build the payload for creating a new document in Firebase
        payload = {
            "fields": {
                "dosage": {
                    "stringValue": dosage
                },
                "interval": {
                    "integerValue": interval
                },
                "name": {
                    "stringValue": name
                },
                "instructions": {
                    "stringValue": instructions
                }
            }
        }

        # Define the URL for Firebase REST operations
        baseUrl = "https://firestore.googleapis.com/v1"
        pathUrl = f"projects/{firebaseProjectId}/databases/{firebaseDatabase}/documents/{firebaseCollection}/{name}"
        endpointUrl = f"{baseUrl}/{pathUrl}?key={firebaseApiKey}"

        # Configure the request
        data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(endpointUrl, data=data, headers={"Content-Type": "application/json"}, method='PATCH')

        try:
            # Make the API call
            with urllib.request.urlopen(req) as response:
                response_body = response.read()
                responseBody = json.loads(response_body.decode('utf-8'))

                # Log success information
                print("Firebase API call successful")
                print("Document created:", json.dumps(responseBody, indent=2))

                # Return a success response, but simplified
                return "ok done"
                
        except urllib.error.HTTPError as e:
            error_message = e.read().decode('utf-8')
            print(f"Firebase API call error: {error_message}")
            # Returning a response even on error, as requested
            return "ok done"
            
        except Exception as e:
            print(f"Error: {str(e)}")
            # Returning a response even on exception
            return "ok done"

    # Call the createMedInfo function to add the new document
    responseBody = createMedInfo()

    # Construct the action response without original response structure
    action_response = {
        'actionGroup': actionGroup,
        'function': function,
        'functionResponse': {
            'responseBody': responseBody
        }
    }

    dummy_function_response = {'response': action_response, 'messageVersion': event['messageVersion']}
    print("Response: {}".format(dummy_function_response))

    return dummy_function_response
