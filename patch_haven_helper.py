import os
import json
import google.generativeai as genai
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import Optional

app = FastAPI(title="Haven Accessibility Core")

genai.configure(api_key=os.environ.get("GEMINI_API_KEY"))

class VoicePayload(BaseModel):
    user_id: str
    transcript: str

# 1. Define the Strict Pydantic Schema for Gemini's output
class DeviceActionSchema(BaseModel):
    type: str = Field(description="The action type, strictly either 'OPEN_OR_DOWNLOAD_APP' or 'SEARCH_EMAILS'")
    package: Optional[str] = Field(None, description="The package name like 'com.microsoft.solitairecollection' if opening an app")
    query: Optional[str] = Field(None, description="The search query string if searching emails")

class HavenResponseSchema(BaseModel):
    voice_response: str = Field(description="Deeply empathetic, warm, comforting spoken line to say to the user.")
    device_action: Optional[DeviceActionSchema] = Field(None, description="The automated hardware task block. Set to null if the user is just chatting.")

HAVEN_SYSTEM_PROMPT = """
You are Haven, the user's dedicated AI companion and guardian angel.
Your user may be technologically non-fluent, elderly, or easily overwhelmed.
Never give instructions on HOW to use the phone. Do it for them by generating the correct device_action object.
Speak with extreme warmth, patience, and clear, simple comfort.

If the user mentions wanting to play solitaire or finding games, you MUST populate the device_action object with:
type: "OPEN_OR_DOWNLOAD_APP" and package: "com.microsoft.solitairecollection"
"""

@app.post("/v1/haven/chat")
async def process_haven_voice(payload: VoicePayload):
    try:
        model = genai.GenerativeModel(
            model_name="gemini-1.5-flash",
            system_instruction=HAVEN_SYSTEM_PROMPT
        )
        
        # 2. Force the Gemini API to mathematically adhere to your structural format
        response = model.generate_content(
            payload.transcript,
            generation_config={
                "response_mime_type": "application/json",
                "response_schema": HavenResponseSchema # Hard constraint
            }
        )
        
        response_data = json.loads(response.text)
        return response_data

    except json.JSONDecodeError:
        return {
            "voice_response": "I'm right here with you. Let me try that again.",
            "device_action": None
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    print("Starting Locked Haven Engine on port 8000...")
    uvicorn.run(app, host="0.0.0.0", port=8000)
