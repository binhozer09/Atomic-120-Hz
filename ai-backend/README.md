# Atomic 120 Hz AI backend

This folder is deployed as a separate Vercel project, with this directory configured as the project root.

## Required environment variable

Set `OPENAI_API_KEY` in Vercel for Production, Preview, and Development. Never put this key in the Android app or commit it to Git.

The API uses OpenAI's Responses API and structured JSON output. The Android app sends device/display/game metadata to `POST /api/analyze`. The server returns a diagnostic and a short list of validated command suggestions. It does not execute commands.

## Endpoint

`POST /api/analyze`

The endpoint has a small in-memory per-IP request limit. For a public production release, add durable rate limiting and app/user authentication.
