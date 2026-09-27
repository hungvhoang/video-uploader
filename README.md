# Video Uploader

Spring Boot application for direct browser-to-S3 multipart video uploads. MongoDB stores metadata, while Floci provides the local S3-compatible endpoint.

## Requirements

- Java 21 and Maven
- Docker for MongoDB
- Floci running at `http://localhost:4566`
- Existing S3 bucket named `default-storage`

## Start

```bash
docker compose up -d
mvn spring-boot:run
```

Open `http://localhost:8080`.

The browser uploads at most three parts concurrently. The initiation response includes the first batch of up to 20 presigned URLs; larger uploads request later batches just in time. Failed parts retry with exponential backoff. Spring Boot never receives the video bytes.

## Upload Flow

```mermaid
flowchart LR
    Browser[Browser / JavaScript]
    API[Spring Boot API]
    Mongo[(MongoDB)]
    S3[(Floci / S3)]

    Browser -->|1. Initiate upload| API
    API -->|2. CreateMultipartUpload| S3
    S3 -->|3. Upload ID| API
    API -->|4. Store INITIATED metadata| Mongo
    API -->|5. Video ID, part plan, first URL batch| Browser

    Browser -.->|6. Request next URL batch when needed| API
    API -.->|7. Return presigned URL batch| Browser
    Browser ==>|8. PUT video part directly| S3
    S3 -->|9. Return ETag| Browser

    Browser -->|10. Complete with part ETags| API
    API -->|11. CompleteMultipartUpload| S3
    API -->|12. Mark video READY| Mongo

    Browser -->|13. Request playback URL| API
    API -->|14. Return presigned GET URL| Browser
    Browser ==>|15. Stream video with byte ranges| S3
```

The thick arrows carry video bytes directly between the browser and Floci. Spring Boot only coordinates upload state, signs URLs, completes the multipart upload, and stores metadata.

## Backend Logs

Run the application with `mvn spring-boot:run` to see INFO logs for multipart initiation, presigned URL batches, completion, cancellation, stale-upload cleanup, and playback URL generation.

There is intentionally no Spring Boot log while a video part is being uploaded. The browser sends each `PUT` directly to Floci, so those requests appear in Floci's logs instead of the application logs.

Open the browser developer tools and select the **Console** tab to see matching frontend messages prefixed with `[video-uploader]`. INFO messages show orchestration and URL batches, while DEBUG messages show individual direct-to-S3 part uploads without exposing presigned URLs or ETags.

## Floci CORS

Direct browser uploads require the bucket to expose the `ETag` response header:

```bash
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
aws --endpoint-url http://localhost:4566 s3api put-bucket-cors \
  --bucket default-storage \
  --cors-configuration '{"CORSRules":[{"AllowedHeaders":["*"],"AllowedMethods":["GET","HEAD","PUT"],"AllowedOrigins":["http://localhost:8080"],"ExposeHeaders":["ETag"],"MaxAgeSeconds":3600}]}'
```

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/uploads` | Start a multipart upload |
| `POST` | `/api/uploads/{id}/parts/presign` | Sign one or more upload parts |
| `POST` | `/api/uploads/{id}/complete` | Complete the S3 multipart upload |
| `DELETE` | `/api/uploads/{id}` | Abort an active upload |
| `GET` | `/api/videos` | List ready videos |
| `GET` | `/api/videos/{id}` | Read video metadata |
| `GET` | `/api/videos/{id}/playback-url` | Create a temporary playback URL |

## Configuration

Environment variables override local defaults:

| Variable | Default |
| --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27017/uploader` |
| `S3_ENDPOINT` | `http://localhost:4566` |
| `AWS_REGION` | `us-east-1` |
| `AWS_ACCESS_KEY_ID` | `test` |
| `AWS_SECRET_ACCESS_KEY` | `test` |
| `S3_BUCKET` | `default-storage` |
