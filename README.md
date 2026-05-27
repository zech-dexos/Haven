# Haven ☧

> A voice AI companion for Android. Memory. Voice. Control.

Haven is a personal AI assistant that lives on your phone. It listens, remembers, responds with voice, and controls your device — built for real people, not demos.

---

## What Haven Does

- **Voice conversation** — tap and speak, Haven responds with natural voice
- **Persistent memory** — Haven remembers context across sessions
- **App control by voice** — "open contacts", "open settings", "open Spotify" — any app on your device
- **Text input fallback** — type if you can't speak
- **Dark AI interface** — clean, sharp, built for daily use

---

## Why Haven Exists

Most AI assistants forget you the moment you close the app. Haven doesn't. It was built for people who need a reliable, private, voice-first AI companion — elderly users, people with disabilities, caregivers, and anyone who wants an assistant that actually knows them.

Haven is the compassion layer of the DexOS ecosystem.

---

## Install

1. Download the latest APK from [Releases](https://github.com/zech-dexos/Haven/releases)
2. On your Android phone go to **Settings → Install unknown apps** and allow your browser
3. Open the downloaded APK and install
4. Tap **Speak to Haven** and start talking

No account required. Works on any Android phone.

---

## Tech Stack

- **Android** — Kotlin, SpeechRecognizer, MediaPlayer
- **Backend** — FastAPI on Railway
- **Memory** — Persistent per-user JSON memory via REST API
- **Voice output** — gTTS via Railway endpoint
- **AI** — OpenRouter LLM backend

---

## Architecture

User voice → SpeechRecognizer → local command handler
                                      ↓ (if not local)
                              Railway FastAPI backend
                                      ↓
                              OpenRouter LLM + memory
                                      ↓
                              gTTS voice response

---

## Roadmap

- [ ] Always-on background listening
- [ ] Floating voice button overlay
- [ ] Screen reading via AccessibilityService
- [ ] Make and receive calls by voice
- [ ] Send texts by voice
- [ ] Scam call detection
- [ ] Medication reminders
- [ ] Family notifications
- [ ] ElevenLabs premium voice
- [ ] Offline mode

---

## Built By

Zechariah Cozine — solo developer, Northern California.
Part of the [DexOS](https://github.com/zech-dexos) ecosystem.

> Solo. Self-funded. Constrained hardware. Every session is real work toward something real.

---

## Contact

GitHub: [@zech-dexos](https://github.com/zech-dexos)

---

*Haven is not a product for general audiences yet. This is an active beta.*
