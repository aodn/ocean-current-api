# SQLite Wave Tags Integration

This document describes the SQLite integration for querying wave tag data from a remote SQLite database.

## Overview

The application automatically downloads a SQLite database file from a remote server and provides REST API endpoints to query wave tag data. The system follows this architecture:

```
Remote Server (SQLite file updates hourly)
           ↓ (HTTP download every hour)
Your Spring Boot App (Local SQLite file)
           ↓ (Fast local queries)
REST API responses
```

## Configuration

Add the following configuration to your `application.yaml`:

```yaml
sqlite:
  remote-url: https://oceancurrent.aodn.org.au/waves/index.db
  local-path: data/sqlite/index.db
  download:
    cron:
      expression: 0 0 * * * ? # every hour
    connect-timeout: 30000
    read-timeout: 60000
```

## Database Schema

The SQLite database contains a `tags` table with the following structure:

| Column  | Type     | Description                                 |
| ------- | -------- | ------------------------------------------- |
| tagfile | TEXT     | Identifier for grouped records (part of PK) |
| order   | SMALLINT | Order/sequence within tagfile (part of PK)  |
| x       | SMALLINT | X coordinate                                |
| y       | SMALLINT | Y coordinate                                |
| sz      | SMALLINT | Size parameter                              |
| title   | TEXT     | Title/description (nullable)                |
| url     | TEXT     | Associated URL (nullable)                   |

**Primary Key:** Composite key consisting of `tagfile` + `order`
**Foreign Key:** `tagfile` references `files(file_name)`
**Index:** `ix_tags_tagfile` on `tagfile` column

## REST API Endpoints

### 1. Get Wave Tags by Date (Recommended)

**GET** `/api/v1/wave-tags/by-date/{dateTime}`

Retrieve all wave tag data for a specific date.

**Parameters:**

- `dateTime`: Date in format YYYYMMDDHH (e.g., `2021010100`)

**Example:** `/api/v1/wave-tags/by-date/2021010100`

### 2. Get Wave Tags by Full Tagfile Path

**GET** `/api/v1/wave-tags/by-tagfile?tagfile={tagfile}`

Retrieve all wave tag data using the complete tagfile path.

**Parameters:**

- `tagfile`: Complete tagfile path (e.g., `y2021/m01/2021010100_Buoy.txt`)

**Response:**

```json
{
  "tagfile": "example_tagfile",
  "tags": [
    {
      "x": 150,
      "y": 200,
      "sz": 12,
      "title": "Example Title",
      "url": "http://example.com"
    }
  ]
}
```

### 3. Get All Available Tagfiles

**GET** `/api/v1/wave-tags/tagfiles`

Retrieve a list of all available tagfiles.

**Response:**

```json
{
  "tagfiles": ["tagfile1", "tagfile2", "tagfile3"],
  "count": 3
}
```

### 4. Trigger Manual Download

**POST** `/api/v1/wave-tags/download`

Manually trigger the download of the SQLite database from the remote server.

**Response:**

```json
{
  "message": "SQLite database downloaded successfully",
  "status": "success"
}
```

### 5. Check Database Status

**GET** `/api/v1/wave-tags/status`

Check if the local SQLite database is available.

**Response:**

```json
{
  "databaseAvailable": true,
  "message": "Database is available"
}
```

## Components

### 1. SqliteProperties

Configuration properties class that maps the YAML configuration.

### 2. WaveTag

Simple POJO representing a wave tag record.

### 3. WaveTagJdbcRepository

JDBC-based repository for direct SQLite database operations.

### 4. WaveTagService

Service layer handling:

- SQLite database downloading from remote server
- Querying tag data by tagfile
- Database availability checks

### 5. WaveTagController

REST controller providing the API endpoints.

### 6. SqliteDownloadScheduler

Scheduled task that automatically downloads the SQLite database based on the cron expression.

## Usage Examples

### Query tags by date (recommended)

```bash
curl http://localhost:8080/api/v1/wave-tags/by-date/2021010100
```

### Query tags by full tagfile path

```bash
curl "http://localhost:8080/api/v1/wave-tags/by-tagfile?tagfile=y2021/m01/2021010100_Buoy.txt"
```

### Get all available tagfiles

```bash
curl http://localhost:8080/api/v1/wave-tags/tagfiles
```

### Manually trigger database download

```bash
curl -X POST http://localhost:8080/api/v1/wave-tags/download
```

### Check database status

```bash
curl http://localhost:8080/api/v1/wave-tags/status
```

## Error Handling

The API provides appropriate HTTP status codes:

- **200**: Success
- **404**: Tagfile not found
- **500**: Internal server error
- **503**: Service unavailable (database not available)

Error responses follow this format:

```json
{
  "message": "Error description"
}
```

## Scheduled Downloads

The application automatically downloads the SQLite database according to the configured cron expression. The default is every hour (`0 0 * * * ?`).

You can customize the schedule by modifying the `sqlite.download.cron.expression` property.

## Dependencies

The following dependencies are required:

```gradle
implementation 'org.xerial:sqlite-jdbc:3.47.1.0'
```

## File Structure

The SQLite database file is stored locally at the path specified in the configuration (default: `data/sqlite/index.db`).

Make sure the application has write permissions to this directory.
