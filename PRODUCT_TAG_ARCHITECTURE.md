# Product Tag Service Architecture

## Overview

The Tag Service has been refactored into a flexible, extensible architecture that supports multiple product types, each with their own data sources and implementation strategies. This allows for easy integration of new products without changing existing code.

## Architecture Components

### 1. Core Interface: `ProductTagService`

All product-specific services implement this interface:

```java
public interface ProductTagService {
    String getProductType();
    boolean downloadData();
    boolean isDataAvailable();
    boolean hasData();
    List<String> getAllTagFiles();
    boolean tagFileExists(String tagFile);
    Object getTagsByTagFile(String tagFile);
    String constructTagFilePath(String dateTime);
    boolean isValidDateFormat(String dateTime);
}
```

### 2. Coordinator Service: `TagService`

The main `TagService` acts as a coordinator that:

- Auto-discovers all `ProductTagService` implementations
- Routes requests to the appropriate product service
- Provides backward compatibility for existing surface waves endpoints
- Offers new generic endpoints for all product types

### 3. Product-Specific Implementations

Each product type has its own service implementation with complete freedom to:

- Use any data source (database, API, files, etc.)
- Define custom date formats
- Implement product-specific logic
- Return appropriate response types

## Current Product Implementations

### 1. Surface Waves (`SurfaceWavesTagService`)

- **Data Source**: SQLite database
- **Date Format**: `YYYYMMDDHH` (10 digits)
- **Tag File Format**: `y{YYYY}/m{MM}/{YYYYMMDDHH}_Buoy.txt`
- **Response Type**: `WaveTagResponse`
- **Status**: ✅ Fully implemented

### 2. Sea Level (`SeaLevelTagService`)

- **Data Source**: REST API
- **Date Format**: `YYYYMMDD` (8 digits)
- **Tag File Format**: `sealevel/{YYYY}/{MM}/{YYYYMMDD}_sealevel.json`
- **Response Type**: `GenericTagResponse`
- **Status**: 📝 Example implementation (configurable via properties)

### 3. Temperature (`TemperatureTagService`)

- **Data Source**: NetCDF files from file system
- **Date Format**: `YYYYMMDDHH` (10 digits)
- **Tag File Format**: `temperature/{YYYY}/{MM}/{DD}/{YYYYMMDDHH}_temperature.nc`
- **Response Type**: `GenericTagResponse`
- **Status**: 📝 Example implementation

### 4. Current Meters (`CurrentMeterTagService`)

- **Data Source**: PostgreSQL database
- **Date Format**: `YYYYMMDDHH` (10 digits)
- **Tag File Format**: `currentmeters/{YYYY}/{MM}/{YYYYMMDDHH}_deployment_{ID}_{SITE}.json`
- **Response Type**: `GenericTagResponse`
- **Status**: 🔧 Stub implementation (requires database setup)

### 5. Ocean Color (`OceanColorTagService`)

- **Data Source**: To be determined
- **Date Format**: `YYYYMMDD` (8 digits)
- **Tag File Format**: `oceancolor/{YYYY}/{MM}/{YYYYMMDD}_oceancolor.nc`
- **Response Type**: `GenericTagResponse`
- **Status**: 🔧 Stub implementation

## API Endpoints

### Legacy Endpoints (Surface Waves)

These endpoints remain unchanged for backward compatibility:

- `GET /tags/surface-waves/by-date/{dateTime}`
- `GET /tags/surface-waves/by-tag-file?tagFile={tagFile}`
- `GET /tags/surface-waves/tag-files`
- `POST /tags/surface-waves/download`
- `GET /tags/surface-waves/status`

### New Generic Endpoints

These work with any product type:

- `GET /tags/products` - List all supported product types
- `GET /tags/{productType}/by-date/{dateTime}` - Get tags by date for any product
- `GET /tags/{productType}/tag-files` - Get all tag files for a product
- `POST /tags/{productType}/download` - Download data for a product

## Data Source Examples

### SQLite Database (Surface Waves)

```java
@Service
public class SurfaceWavesTagService implements ProductTagService {
    private final SqliteProperties sqliteProperties;

    // Direct SQL queries using JDBC
    private List<String> getAllTagFilesDirectSql() throws SQLException {
        String url = "jdbc:sqlite:" + sqliteProperties.getLocalPath();
        // Query SQLite database...
    }
}
```

### REST API (Sea Level)

```java
@Service
public class SeaLevelTagService implements ProductTagService {
    private final RestTemplate restTemplate;

    @Override
    public boolean isDataAvailable() {
        String healthUrl = baseApiUrl + "/health";
        restTemplate.getForObject(healthUrl, String.class);
        return true;
    }
}
```

### File System (Temperature)

```java
@Service
public class TemperatureTagService implements ProductTagService {

    @Override
    public List<String> getAllTagFiles() {
        Path dataDir = Paths.get(dataPath);
        try (Stream<Path> files = Files.find(dataDir, 10,
                (path, attrs) -> path.toString().endsWith(".nc"))) {
            // Process NetCDF files...
        }
    }
}
```

### PostgreSQL Database (Current Meters)

```java
@Service
@ConditionalOnProperty("ocean-current.current-meters.enabled")
public class CurrentMeterTagService implements ProductTagService {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<String> getAllTagFiles() {
        String sql = "SELECT DISTINCT deployment_date, deployment_id FROM meter_deployments";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        // Process database results...
    }
}
```

## Adding New Products

To add a new product type:

1. **Create Service Class**:

   ```java
   @Service
   public class MyProductTagService implements ProductTagService {
       private static final String PRODUCT_TYPE = "my-product";

       @Override
       public String getProductType() {
           return PRODUCT_TYPE;
       }

       // Implement other methods...
   }
   ```

2. **Configure Properties** (if needed):

   ```yaml
   ocean-current:
     my-product:
       enabled: true
       data-source: "https://api.example.com/my-product"
       api-key: "${MY_PRODUCT_API_KEY:}"
   ```

3. **The service is automatically discovered** by Spring's dependency injection and registered with the `TagService` coordinator.

4. **Use the generic endpoints**:
   - `GET /tags/my-product/by-date/20231215`
   - `GET /tags/my-product/tag-files`

## Configuration Examples

### Sea Level API Configuration

```yaml
ocean-current:
  sea-level:
    api:
      base-url: "https://sealevel-api.example.com"
      key: "${SEA_LEVEL_API_KEY:}"
```

### Temperature File System Configuration

```yaml
ocean-current:
  temperature:
    data:
      path: "/data/temperature"
    file:
      pattern: "**/*.nc"
    cache:
      ttl: 3600
```

### Current Meters Database Configuration

```yaml
ocean-current:
  current-meters:
    enabled: false # Disabled by default (stub implementation)
    schema: "current_meters"
    table: "meter_deployments"
```

## Benefits of This Architecture

1. **Separation of Concerns**: Each product has its own service with specific logic
2. **Extensibility**: Adding new products requires no changes to existing code
3. **Flexibility**: Each product can use any data source and format
4. **Backward Compatibility**: Existing endpoints continue to work
5. **Type Safety**: Product-specific implementations can return appropriate types
6. **Spring Integration**: Auto-discovery via dependency injection
7. **Configuration Driven**: Products can be enabled/disabled via properties

## Response Types

### Product-Specific Responses

Products can return their own response types (like `WaveTagResponse` for surface waves).

### Generic Response

For new products, use the `GenericTagResponse`:

```java
public class GenericTagResponse {
    private String productType;
    private String tagFile;
    private List<Map<String, Object>> tags;
    private int count;
}
```

This allows maximum flexibility while maintaining a consistent API structure.

## Testing

Each product service can be tested independently:

```java
@Test
public void testMyProductService() {
    MyProductTagService service = new MyProductTagService();

    // Test product-specific logic
    assertTrue(service.isValidDateFormat("20231215"));
    assertEquals("my-product", service.getProductType());

    // Mock external dependencies as needed
}
```

The coordinator can be tested with mocked product services to verify routing logic.
