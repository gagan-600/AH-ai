import requests
import os
import time
from PIL import Image, ImageDraw

def create_sample_image():
    img = Image.new('RGB', (200, 100), color = (255, 255, 255))
    d = ImageDraw.Draw(img)
    d.text((10,10), "Hello World", fill=(0,0,0))
    img.save('sample_text.png')
    return 'sample_text.png'

def upload_file():
    url = "http://localhost:5000/api/upload"
    file_path = create_sample_image()
    
    if not os.path.exists(file_path):
        print(f"File {file_path} not found.")
        return

    print(f"Uploading {file_path} to {url}...")
    try:
        with open(file_path, 'rb') as f:
            files = {'file': f}
            response = requests.post(url, files=files)
            
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.text}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    # Wait for service to be ready
    print("Waiting for service to be ready...")
    time.sleep(5) 
    upload_file()
