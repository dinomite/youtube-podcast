# Authentication

This application uses HTTP Basic Authentication to protect access to RSS feeds and audio files.

## Configuration

Authentication is configured via `application.conf` with environment variable overrides:

```hocon
ytpodcast {
    auth {
        username = "dev"
        username = ${?AUTH_USERNAME}
        password = "dev"
        password = ${?AUTH_PASSWORD}
    }
}
```

## Development

The default credentials are:
- Username: `dev`
- Password: `dev`

## Production

Set environment variables to override default credentials:

```bash
export AUTH_USERNAME="your_username"
export AUTH_PASSWORD="your_secure_password"
./gradlew run
```

The application will fail to start if credentials are not configured.

## Protected Endpoints

- `GET /show/{playlistId}` - RSS feed (requires auth)
- `GET /episode/{videoId}.mp3` - Audio file (requires auth)

## Public Endpoints

- `GET /` - Root endpoint (no auth required)
- `GET /health` - Health check (no auth required)

## Podcast Client Usage

Configure your podcast client with URLs that include credentials:

```
https://username:password@yourserver.com/show/playlistId
```

Mainstream podcast clients (Apple Podcasts, Overcast, Pocket Casts) support HTTP Basic Auth natively.
