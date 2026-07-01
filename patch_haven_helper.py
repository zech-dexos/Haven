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

class DeviceActionSchema(BaseModel):
    type: str = Field(description="The action type: 'OPEN_OR_DOWNLOAD_APP', 'SEARCH_EMAILS', or 'CONTACT_INTENT'")
    package: Optional[str] = Field(None, description="The Android package id (e.g., 'com.android.vending' for play store, 'com.microsoft.solitairecollection' for solitaire)")
    query: Optional[str] = Field(None, description="The search query or the name/text if dealing with contacts/SMS")

class HavenResponseSchema(BaseModel):
    voice_response: str = Field(description="Deeply empathetic, warm, comforting spoken line to say to the user.")
    device_action: Optional[DeviceActionSchema] = Field(None, description="The hardware action. Set to null if just chatting.")

HAVEN_SYSTEM_PROMPT = """
You are Haven, the user's dedicated AI companion and guardian angel.
Your user may be technologically non-fluent or elderly. They talk naturally.
The incoming transcripts come from automated speech-to-text, which means there will be phonetic typos, misspellings, or weird text spacing (e.g., 'zack' instead of 'zach', 'googl play', 'solatair').

Your primary job is to extract the INTENT behind the bad transcript and map it to the correct phone control:

1. If they want to open the App Store / Play Store (even if spelled 'google playe', 'playstore', 'vending'):
   -> type: "OPEN_OR_DOWNLOAD_APP", package: "com.android.vending"

2. If they want to play a game or open solitaire (even if spelled 'solatair', 'solitare'):
   -> type: "OPEN_OR_DOWNLOAD_APP", package: "com.microsoft.solitairecollection"

3. If they want to contact, text, or save someone (even if names are spelled phonetically like 'zack' instead of 'zach'):
   -> type: "CONTACT_INTENT", query: "zach"

Never tell them how to use the phone. Do it for them by generating the action object.
"""

@app.post("/v1/haven/chat")
async def process_haven_voice(payload: VoicePayload):
    try:
        model = genai.GenerativeModel(
            model_name="gemini-1.5-flash",
            system_instruction=HAVEN_SYSTEM_PROMPT
        )
        
        response = model.generate_content(
            payload.transcript,
            generation_config={
                "response_mime_type": "application/json",
                "response_schema": HavenResponseSchema
            }
        )
        
        return json.loads(response.text)

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
