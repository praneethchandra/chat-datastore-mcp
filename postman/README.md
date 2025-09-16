# Chat Datastore MCP API - Postman Collection

This Postman collection provides comprehensive testing capabilities for the Chat Datastore MCP Server APIs.

## Quick Start

### 1. Import the Collection
1. Open Postman
2. Click "Import" 
3. Select `Chat-Datastore-MCP-API.postman_collection.json`
4. The collection will be imported with all requests organized in folders

### 2. Set Up Environment Variables
The collection uses these variables:
- `baseUrl`: Set to `http://localhost:8080` (default)
- `sessionId`: Automatically populated when you run "Get SSE Session"

### 3. Basic Testing Workflow

#### Step 1: Start the Application
Make sure the Chat Datastore MCP Server is running:
```bash
# Start infrastructure
cd deploy && docker-compose up -d redis mongodb

# Start application
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

#### Step 2: Health Check
Run the **Health Check** request to verify the application is running.

#### Step 3: Get Session ID
Run **Get SSE Session** - this will automatically extract and store the session ID for subsequent requests.

#### Step 4: Initialize MCP Protocol
Run **MCP Initialize** to establish the MCP protocol connection.

#### Step 5: Explore Available Tools
Run **List Tools** to see all available MCP tools and their schemas.

## Collection Structure

### 📁 Health & Status
- **Health Check**: Basic application health endpoint
- **Actuator Health**: Spring Boot actuator health with detailed status

### 📁 MCP Protocol
- **Get SSE Session**: Establishes SSE connection and extracts session ID
- **MCP Initialize**: Initializes MCP protocol connection
- **List Tools**: Lists all available MCP tools with schemas
- **Get Capabilities**: Gets server capabilities and tool summary

### 📁 KV Operations (Redis Cache)
- **KV Set**: Store key-value pairs with optional TTL
- **KV Get**: Retrieve value by key
- **KV Multiple Get**: Retrieve multiple keys at once
- **KV Scan**: Scan keys by prefix pattern
- **KV TTL**: Check time-to-live for a key
- **KV Delete**: Remove a key from cache

### 📁 Store Operations (MongoDB)
- **Store Find Sessions**: Query sessions collection
- **Store Find Interactions**: Query interactions collection  
- **Store Find Events**: Query events collection (outbox pattern)
- **Store Aggregate - Session Count by User**: Aggregation example
- **Store Aggregate - Event Types Count**: Event type statistics
- **Session Append Event**: Add events to session streams

### 📁 Capabilities
- **List Capabilities**: Server introspection and capabilities

## Key Features

### Automatic Session Management
The "Get SSE Session" request includes a test script that automatically extracts the session ID from the SSE response and stores it as a collection variable. All subsequent requests use this session ID.

### Dynamic Data Generation
Many requests use Postman's dynamic variables:
- `{{$randomUUID}}`: Generates random UUIDs
- `{{$randomFirstName}}`: Generates random names
- `{{$isoTimestamp}}`: Generates ISO timestamps

### Organized Testing
Requests are logically grouped by functionality, making it easy to test specific areas of the API.

## Example Usage Scenarios

### Testing KV Operations
1. Run "Get SSE Session"
2. Run "MCP Initialize" 
3. Run "KV Set" to store a value
4. Run "KV Get" to retrieve it
5. Run "KV Scan" to find keys by prefix
6. Run "KV TTL" to check expiration
7. Run "KV Delete" to clean up

### Testing Store Operations
1. Run "Get SSE Session"
2. Run "MCP Initialize"
3. Run "Store Find Sessions" to query sessions
4. Run "Store Aggregate - Session Count by User" for analytics
5. Run "Session Append Event" to add events

### Testing Full Workflow
1. Health Check → Verify app is running
2. Get SSE Session → Get session ID
3. MCP Initialize → Establish protocol
4. List Tools → See available operations
5. Test KV and Store operations as needed

## Troubleshooting

### Common Issues

**"Session not found" errors**
- Make sure to run "Get SSE Session" first
- Check that the sessionId variable is populated
- The session may have expired - get a new one

**Connection refused**
- Verify the application is running on port 8080
- Check that infrastructure services (Redis, MongoDB) are running
- Update the baseUrl variable if using a different port

**Tool execution errors**
- Check the application logs for detailed error messages
- Verify that Redis and MongoDB are accessible
- Ensure the collection names in store operations are allowed

### Debugging Tips

1. **Check Console**: Postman console shows variable assignments and script execution
2. **Verify Variables**: Check collection variables are set correctly
3. **Test Individual Components**: Start with health checks, then protocol, then tools
4. **Check Application Logs**: The Spring Boot application logs show detailed MCP protocol interactions

## Advanced Usage

### Custom Environment
Create a custom Postman environment for different deployment targets:
- `baseUrl`: Your server URL
- `sessionId`: Leave empty (auto-populated)

### Batch Testing
Use Postman's Collection Runner to execute multiple requests in sequence for automated testing.

### Integration Testing
Chain requests together using test scripts to create complex testing scenarios.

## API Reference

All requests follow the MCP (Model Context Protocol) specification:
- **Method**: The MCP method name (initialize, tools/list, tools/call)
- **Params**: Method-specific parameters
- **Session ID**: Required for most operations (automatically managed)

For detailed API documentation, see the main project README.md file.
