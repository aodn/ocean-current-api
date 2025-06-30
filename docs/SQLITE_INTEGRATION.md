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
  remote-url: database-remote-url
  local-path: data/sqlite/index.db
  download:
    cron:
      expression: 0 0 */2 * * ? # every 2 hours
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

### Surface Wave Specific Endpoints

#### 1. Get Wave Tags by Date (Recommended)

**GET** `/tags/surface-waves/by-date/{dateTime}`

Retrieve all surface wave buoy tag data for a specific date.

**Parameters:**

- `dateTime`: Date in format YYYYMMDDHH (e.g., `2021010100`)

**Example:** `/tags/surface-waves/by-date/2021010100`

#### 2. Get Wave Tags by Full Tagfile Path

**GET** `/tags/surface-waves/by-tag-file?tagFile={tagFile}`

Retrieve all surface wave tag data using the complete tagfile path.

**Parameters:**

- `tagFile`: Complete tagfile path (e.g., `y2021/m01/2021010100_Buoy.txt`)

**Example:** `/tags/surface-waves/by-tag-file?tagFile=y2021/m01/2021010100_Buoy.txt`

#### 3. Get All Available Surface Wave Tagfiles

**GET** `/tags/surface-waves/tag-files`

Retrieve a list of all available surface wave tagfiles.

**Response:**

```json
{
  "tagfiles": [
    "y2021/m01/2021010100_Buoy.txt",
    "y2021/m01/2021010101_Buoy.txt"
  ],
  "count": 2
}
```

#### 4. Trigger Manual Surface Wave Download

**POST** `/tags/surface-waves/download`

Manually trigger the download of the surface wave SQLite database from the remote server.

**Response:**

```json
{
  "message": "Surface wave data downloaded successfully",
  "status": "success",
  "tagFileCount": 1250
}
```

#### 5. Check Surface Wave Database Status

**GET** `/tags/surface-waves/status`

Check if the local surface wave SQLite database is available and get statistics.

**Response:**

```json
{
  "databaseAvailable": true,
  "tagFileCount": 1250,
  "lastUpdate": "recently",
  "message": "Surface wave data is available (1250 tag files)",
  "autoDownload": "enabled"
}
```

### Generic Multi-Product Endpoints

The API now supports multiple product types through generic endpoints:

#### 6. Get All Supported Product Types

**GET** `/tags/products`

Retrieve a list of all supported product types.

**Response:**

```json
{
  "productTypes": ["surface-waves"],
  "count": 1
}
```

#### 7. Get Tags by Date for Any Product Type

**GET** `/tags/{productType}/by-date/{dateTime}`

Retrieve tag data for any supported product type and date.

**Parameters:**

- `productType`: Product type (e.g., `surface-waves`)
- `dateTime`: Date (format depends on product type)

**Example:** `/tags/surface-waves/by-date/2021010100`

#### 8. Get All Tag Files for a Product Type

**GET** `/tags/{productType}/tag-files`

Retrieve all available tag files for a specific product type.

**Parameters:**

- `productType`: Product type (e.g., `surface-waves`)

**Response:**

```json
{
  "productType": "surface-waves",
  "tagFiles": [
    "y2021/m01/2021010100_Buoy.txt",
    "y2021/m01/2021010101_Buoy.txt"
  ],
  "count": 2
}
```

#### 9. Trigger Data Download for Any Product Type

**POST** `/tags/{productType}/download`

Manually trigger the download of data for any supported product type.

**Parameters:**

- `productType`: Product type (e.g., `surface-waves`)

**Response:**

```json
{
  "message": "Data downloaded successfully for product surface-waves",
  "productType": "surface-waves",
  "status": "success",
  "tagFileCount": 1250
}
```

### Tag Data Response Format

All tag endpoints return data in this format:

```json
{
  "tagfile": "y2021/m01/2021010100_Buoy.txt",
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

## Components

### 1. SqliteProperties

Configuration properties class that maps the YAML configuration.

### 2. WaveTagResponse

Response DTO containing tagfile information and associated tag data.

### 3. TagService

Multi-product service interface that handles:

- SQLite database downloading from remote server for various product types
- Querying tag data by tagfile for different products
- Database availability checks across product types
- Product type validation and date format validation
- Tag file path construction for different product patterns

### 4. SurfaceWavesTagService

Concrete implementation of TagService for surface wave data, providing:

- JDBC-based repository operations for SQLite database
- Surface wave specific tag file path construction (y{YYYY}/m{MM}/{YYYYMMDDHH}\_Buoy.txt pattern)
- Auto-download functionality with data availability checks

### 5. TagController

REST controller providing both specific surface wave endpoints and generic multi-product endpoints.

### 6. SqliteDownloadScheduler

Scheduled task that automatically downloads the SQLite database based on the cron expression.

## Usage Examples

### Surface Wave Specific Examples

#### Query tags by date (recommended)

```bash
curl http://localhost:8080/tags/surface-waves/by-date/2021010100
```

#### Query tags by full tagfile path

```bash
curl "http://localhost:8080/tags/surface-waves/by-tag-file?tagFile=y2021/m01/2021010100_Buoy.txt"
```

#### Get all available surface wave tagfiles

```bash
curl http://localhost:8080/tags/surface-waves/tag-files
```

#### Manually trigger surface wave database download

```bash
curl -X POST http://localhost:8080/tags/surface-waves/download
```

#### Check surface wave database status

```bash
curl http://localhost:8080/tags/surface-waves/status
```

### Generic Multi-Product Examples

#### Get all supported product types

```bash
curl http://localhost:8080/tags/products
```

#### Query tags by date for any product type

```bash
curl http://localhost:8080/tags/surface-waves/by-date/2021010100
```

#### Get tag files for any product type

```bash
curl http://localhost:8080/tags/surface-waves/tag-files
```

#### Trigger download for any product type

```bash
curl -X POST http://localhost:8080/tags/surface-waves/download
```

## Error Handling

The API provides appropriate HTTP status codes:

- **200**: Success
- **400**: Bad request (invalid product type or date format)
- **404**: Tagfile or date not found
- **500**: Internal server error
- **503**: Service unavailable (database not available)

Error responses follow this format:

```json
{
  "message": "Error description"
}
```

## Automatic Data Availability

The system includes intelligent auto-download functionality:

- When data is requested but not available, the system automatically attempts to download it
- If the database file exists but appears empty, it triggers a refresh download
- The system includes cache refresh mechanisms and retry logic
- Database availability is checked before each operation

## Scheduled Downloads

The application automatically downloads the SQLite database according to the configured cron expression. The default is every 2 hours (`0 0 */2 * * ?`).

You can customize the schedule by modifying the `sqlite.download.cron.expression` property.

## Dependencies

The following dependencies are required:

```gradle
implementation 'org.xerial:sqlite-jdbc:3.47.1.0'
```

## File Structure

The SQLite database file is stored locally at the path specified in the configuration (default: `data/sqlite/index.db`).

Make sure the application has write permissions to this directory.

## Multi-Product Architecture

The system is designed to support multiple product types beyond just surface waves:

- **Extensible Design**: New product types can be added by implementing the TagService interface
- **Product-Specific Logic**: Each product type can have its own date format validation and tag file path construction
- **Unified API**: Generic endpoints provide consistent access patterns across all product types
- **Individual Configuration**: Each product type can have separate download schedules and data sources
