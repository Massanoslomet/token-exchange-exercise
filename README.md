# Token Exchange Exercise

This project is a learning exercise for identity delegation and token exchange using Java and Spring Boot.

The system contains three separate services:

```text
identity-provider  -> port 8081
sts-service        -> port 8082
server             -> port 8080
```

## Architecture

The system follows this flow:

```text
1. A client gets an ID token from the Identity Provider.
2. The client sends that ID token to the Security Token Service.
3. The STS validates the ID token using the Identity Provider JWKS.
4. The STS checks scope policy and issues a delegated access token.
5. The backend server validates the delegated access token using the STS JWKS.
6. The backend enforces scopes per HTTP method.
```

Simplified flow:

```text
Client
  |
  | POST /auth/token
  v
Identity Provider :8081
  |
  | id_token
  v
Client
  |
  | POST /exchange
  | subject_token = id_token
  v
STS :8082
  |
  | delegated access_token
  v
Client
  |
  | Authorization: Bearer <access_token>
  v
Backend Server :8080
```

## Services

### 1. identity-provider

The Identity Provider issues RS256-signed ID tokens.

Main endpoints:

```text
POST /auth/token
GET  /.well-known/jwks.json
```

Example clients:

```text
agent_alpha -> scope: read
agent_beta  -> scope: read write
agent_admin -> scope: read write admin
```

Example token request:

```powershell
$body = @{
  client_id = "agent_alpha"
  client_secret = "alpha-secret"
} | ConvertTo-Json -Compress

Invoke-RestMethod `
  -Uri "http://localhost:8081/auth/token" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

### 2. sts-service

The Security Token Service performs token exchange.

Main endpoints:

```text
POST /exchange
GET  /.well-known/jwks.json
```

The STS validates the `subject_token` from the Identity Provider and issues a delegated access token.

The delegated access token contains claims such as:

```json
{
  "iss": "sts-service",
  "sub": "agent_alpha",
  "aud": "backend-service",
  "scope": "backend:read",
  "act": {
    "sub": "frontend-service"
  }
}
```

Scope mapping:

```text
IdP scope -> backend scope

read  -> backend:read
write -> backend:write
admin -> backend:admin
```

### 3. server

The backend server exposes protected REST resources.

Main endpoints:

```text
GET    /api/resources
GET    /api/resources/{id}
POST   /api/resources
PUT    /api/resources/{id}
DELETE /api/resources/{id}
```

The backend validates delegated access tokens from STS.

It does not accept raw ID tokens from the Identity Provider.

Scope rules:

```text
GET                 requires backend:read
POST / PUT / DELETE requires backend:write
```

## How to run

Open three PowerShell windows.

### Start identity-provider

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\identity-provider

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd spring-boot:run
```

Expected port:

```text
8081
```

### Start sts-service

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\sts-service

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd spring-boot:run
```

Expected port:

```text
8082
```

### Start server

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\server

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd spring-boot:run
```

Expected port:

```text
8080
```

## Manual full-flow test

### 1. Get an ID token from the Identity Provider

```powershell
$body = @{
  client_id = "agent_alpha"
  client_secret = "alpha-secret"
} | ConvertTo-Json -Compress

$idpResponse = Invoke-RestMethod `
  -Uri "http://localhost:8081/auth/token" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body

$idToken = $idpResponse.id_token
```

### 2. Exchange the ID token for a delegated access token

```powershell
$exchangeBody = @{
  grant_type = "urn:ietf:params:oauth:grant-type:token-exchange"
  subject_token = $idToken
  subject_token_type = "urn:ietf:params:oauth:token-type:id_token"
  requested_token_type = "urn:ietf:params:oauth:token-type:access_token"
  scope = "backend:read"
  audience = "backend-service"
} | ConvertTo-Json -Compress

$stsResponse = Invoke-RestMethod `
  -Uri "http://localhost:8082/exchange" `
  -Method Post `
  -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer frontend-service-token" } `
  -Body $exchangeBody

$accessToken = $stsResponse.access_token
```

### 3. Use the delegated access token against the backend

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/resources" `
  -Method Get `
  -Headers @{
    Authorization = "Bearer $accessToken"
    Accept = "application/json"
  }
```

Expected result:

```text
id name            owner
-- ----            -----
 1 First resource  agent_alpha
 2 Second resource agent_beta
```

## Security checks

### Raw ID token should not work against backend

```powershell
curl.exe -i `
  -H "Authorization: Bearer $idToken" `
  -H "Accept: application/json" `
  http://localhost:8080/api/resources
```

Expected:

```text
HTTP/1.1 401
```

### backend:read token should not allow POST

```powershell
$postBody = @{
  name = "Should fail"
  owner = "agent_alpha"
} | ConvertTo-Json -Compress

try {
  Invoke-WebRequest `
    -Uri "http://localhost:8080/api/resources" `
    -Method Post `
    -ContentType "application/json" `
    -Headers @{
      Authorization = "Bearer $accessToken"
      Accept = "application/json"
    } `
    -Body $postBody
} catch {
  $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
  $reader.ReadToEnd()
}
```

Expected:

```json
{
  "error": "insufficient_scope",
  "error_description": "Token does not have required scope: backend:write",
  "timestamp": "..."
}
```

### backend:write token should allow POST

```powershell
$body = @{
  client_id = "agent_beta"
  client_secret = "beta-secret"
} | ConvertTo-Json -Compress

$idpResponse = Invoke-RestMethod `
  -Uri "http://localhost:8081/auth/token" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body

$idToken = $idpResponse.id_token

$exchangeBody = @{
  grant_type = "urn:ietf:params:oauth:grant-type:token-exchange"
  subject_token = $idToken
  subject_token_type = "urn:ietf:params:oauth:token-type:id_token"
  requested_token_type = "urn:ietf:params:oauth:token-type:access_token"
  scope = "backend:write"
  audience = "backend-service"
} | ConvertTo-Json -Compress

$stsResponse = Invoke-RestMethod `
  -Uri "http://localhost:8082/exchange" `
  -Method Post `
  -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer frontend-service-token" } `
  -Body $exchangeBody

$writeToken = $stsResponse.access_token

$postBody = @{
  name = "Created with delegated write token"
  owner = "agent_beta"
} | ConvertTo-Json -Compress

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/resources" `
  -Method Post `
  -ContentType "application/json" `
  -Headers @{
    Authorization = "Bearer $writeToken"
    Accept = "application/json"
  } `
  -Body $postBody
```

Expected:

```text
201 Created
```

## Running tests

Run tests for each service separately.

### identity-provider

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\identity-provider
.\mvnw.cmd test
```

### sts-service

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\sts-service
.\mvnw.cmd test
```

### server

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\server
.\mvnw.cmd test
```

## Implemented security behavior

The project currently implements:

```text
- RS256-signed ID tokens from Identity Provider
- JWKS endpoint from Identity Provider
- STS validates subject_token using IdP JWKS
- STS rejects fake, expired, wrong issuer and wrong audience tokens
- STS maps IdP scopes to backend scopes
- STS issues RS256-signed delegated access tokens
- STS delegated token includes act claim
- STS exposes its own JWKS endpoint
- Backend validates STS access tokens using STS JWKS
- Backend rejects raw IdP tokens
- Backend enforces scope per HTTP method
- Structured JSON error responses
- Request/response logging without token logging
```

## Current limitations

This is a learning exercise and currently uses some simplified choices:

```text
- Client registry is in memory
- Service registry is in memory
- RSA keys are generated at startup
- No database
- No Docker Compose yet
- No frontend service yet
- Static frontend-service credential is used for STS caller authentication
```

## Useful endpoints

```text
Identity Provider:
http://localhost:8081/auth/token
http://localhost:8081/.well-known/jwks.json

STS:
http://localhost:8082/exchange
http://localhost:8082/.well-known/jwks.json

Backend:
http://localhost:8080/api/resources
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```