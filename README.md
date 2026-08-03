# Token Exchange Exercise

This project is a Java Spring Boot implementation of an identity delegation and token exchange flow.

The system demonstrates how a client can authenticate with an Identity Provider, send the user's ID token to a Frontend Service, let the Frontend Service exchange that token at an STS Service, and then call a protected Backend Service using a delegated access token.

## Architecture

This project implements a delegated access flow using four services:

```text
Client
  |
  | 1. Gets ID token from Identity Provider
  v
Identity Provider (:8081)
  |
  | 2. Client sends ID token to Frontend Service
  v
Frontend Service (:8080)
  |
  | 3. Frontend exchanges ID token for delegated access token
  v
STS Service (:8082)
  |
  | 4. Frontend calls backend using delegated access token
  v
Backend Service (:8083)
  |
  | 5. Backend validates STS token and writes audit log
  v
Audit Log
```

## Services

| Service | Port | Responsibility |
|---|---:|---|
| `frontend-service` | `8080` | Receives user ID token, performs token exchange, calls backend |
| `identity-provider` | `8081` | Issues signed ID tokens |
| `sts-service` | `8082` | Exchanges ID tokens for delegated access tokens |
| `server` / backend | `8083` | Protects resources, validates STS tokens, writes audit log |

## Project structure

```text
token-exchange-exercise/
  frontend-service/
    Dockerfile
  identity-provider/
    Dockerfile
  sts-service/
    Dockerfile
  server/
    Dockerfile
  docker-compose.yml
  README.md
```

## Identity Provider

The Identity Provider issues signed ID tokens.

### Endpoint

```text
POST http://localhost:8081/auth/token
```

Example request:

```json
{
  "client_id": "agent_alpha",
  "client_secret": "alpha-secret"
}
```

Example response:

```json
{
  "id_token": "<signed JWT>",
  "token_type": "Bearer",
  "expires_in": 300
}
```

### JWKS endpoint

```text
GET http://localhost:8081/.well-known/jwks.json
```

The Identity Provider signs ID tokens using RS256. The private key stays inside the Identity Provider. Other services can verify tokens using the public key from JWKS.

### Example clients

```text
agent_alpha  -> read
agent_beta   -> read write
agent_admin  -> read write admin
```

## STS Service

The STS Service exchanges an ID token for a delegated access token.

### Endpoint

```text
POST http://localhost:8082/exchange
```

The frontend-service authenticates to STS using:

```text
Authorization: Bearer frontend-service-token
```

Example request:

```json
{
  "grant_type": "urn:ietf:params:oauth:grant-type:token-exchange",
  "subject_token": "<id_token_from_identity_provider>",
  "subject_token_type": "urn:ietf:params:oauth:token-type:id_token",
  "requested_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "scope": "backend:read",
  "audience": "backend-service"
}
```

Example response:

```json
{
  "access_token": "<delegated access token>",
  "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "token_type": "Bearer",
  "scope": "backend:read",
  "expires_in": 300
}
```

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

### JWKS endpoint

```text
GET http://localhost:8082/.well-known/jwks.json
```

The STS Service signs delegated access tokens using RS256.

## Backend Service

The backend service is located in the `server` folder and runs on port `8083`.

It protects resource endpoints and only accepts delegated access tokens issued by the STS Service.

### Protected resource endpoint

```text
GET http://localhost:8083/api/resources
```

Requires:

```text
Authorization: Bearer <delegated_access_token>
```

Required scope:

```text
backend:read
```

### Write operations

The following operations require:

```text
backend:write
```

Endpoints:

```text
POST   http://localhost:8083/api/resources
PUT    http://localhost:8083/api/resources/{id}
DELETE http://localhost:8083/api/resources/{id}
```

### Audit endpoint

```text
GET http://localhost:8083/audit
```

Example audit entry:

```json
{
  "timestamp": "2026-08-03T12:04:23.269632069Z",
  "event": "ACCESS",
  "user": "agent_alpha",
  "actor": "frontend-service",
  "action": "GET /api/resources",
  "scope": "backend:read",
  "result": "ALLOWED"
}
```

Audit results include:

```text
ALLOWED
DENIED_MISSING_TOKEN
DENIED_INVALID_TOKEN
DENIED_INSUFFICIENT_SCOPE
```

The audit log is currently in-memory. It is reset when the backend service restarts.

## Frontend Service

The frontend-service runs on port `8080`.

It receives the user's ID token, exchanges it at STS for a delegated access token, and calls the backend service with the delegated access token.

### Endpoint

```text
GET http://localhost:8080/frontend/resources
```

Requires:

```text
Authorization: Bearer <id_token_from_identity_provider>
```

The frontend-service does not send the original ID token to the backend. It sends only the delegated access token from STS.

## Running the services locally without Docker

Open four separate PowerShell terminals.

### 1. Identity Provider

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\identity-provider

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd spring-boot:run
```

Runs on:

```text
http://localhost:8081
```

### 2. STS Service

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\sts-service

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd spring-boot:run
```

Runs on:

```text
http://localhost:8082
```

### 3. Backend Service

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\server

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd spring-boot:run
```

Runs on:

```text
http://localhost:8083
```

### 4. Frontend Service

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\frontend-service

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd spring-boot:run
```

Runs on:

```text
http://localhost:8080
```

## Health check / quick manual check

When all services are running:

```powershell
curl.exe -i http://localhost:8081/.well-known/jwks.json
curl.exe -i http://localhost:8082/.well-known/jwks.json
curl.exe -i http://localhost:8083/audit
curl.exe -i http://localhost:8080/frontend/resources
```

Expected:

```text
8081 -> HTTP/1.1 200
8082 -> HTTP/1.1 200
8083 -> HTTP/1.1 200
8080 -> HTTP/1.1 401 without token
```

The `401` from frontend-service is expected when no Bearer token is provided.

## Full frontend delegation flow

### 1. Get ID token from Identity Provider

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

### 2. Call Frontend Service with the ID token

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/frontend/resources" `
  -Method Get `
  -Headers @{
    Authorization = "Bearer $idToken"
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

### 3. Check backend audit log

```powershell
curl.exe -i http://localhost:8083/audit
```

Expected audit entry:

```json
{
  "event": "ACCESS",
  "user": "agent_alpha",
  "actor": "frontend-service",
  "action": "GET /api/resources",
  "scope": "backend:read",
  "result": "ALLOWED"
}
```

This proves that:

```text
Client sent ID token to frontend-service
Frontend-service exchanged ID token at STS
STS issued delegated access token
Frontend-service called backend with delegated access token
Backend validated STS token
Backend logged user, actor, action, scope and result
```

## Running with Docker Compose

The project can also be started with Docker Compose.

This starts all four services:

```text
frontend-service   -> http://localhost:8080
identity-provider  -> http://localhost:8081
sts-service        -> http://localhost:8082
backend-service    -> http://localhost:8083
```

### Start all services

From the project root:

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise

docker compose up --build
```

This builds and starts:

```text
identity-provider
sts-service
backend-service
frontend-service
```

The services communicate internally through Docker service names:

```text
frontend-service -> sts-service
frontend-service -> backend-service
sts-service      -> identity-provider
backend-service  -> sts-service
```

Important: inside Docker, services do not use `localhost` to call each other. They use the service names from `docker-compose.yml`.

For example:

```text
http://sts-service:8082/exchange
http://backend-service:8083/api/resources
http://identity-provider:8081/.well-known/jwks.json
```

### Stop all services

```powershell
docker compose down
```

## Docker health check

When Docker Compose is running, open a new PowerShell terminal and run:

```powershell
curl.exe -i http://localhost:8081/.well-known/jwks.json
curl.exe -i http://localhost:8082/.well-known/jwks.json
curl.exe -i http://localhost:8083/audit
curl.exe -i http://localhost:8080/frontend/resources
```

Expected result:

```text
8081 -> HTTP/1.1 200
8082 -> HTTP/1.1 200
8083 -> HTTP/1.1 200
8080 -> HTTP/1.1 401 without token
```

The `401` from `frontend-service` is expected when no Bearer token is provided.

## Full Docker Compose delegation flow

### 1. Get ID token from Identity Provider

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

### 2. Call Frontend Service with the ID token

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/frontend/resources" `
  -Method Get `
  -Headers @{
    Authorization = "Bearer $idToken"
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

### 3. Check backend audit log

```powershell
curl.exe -i http://localhost:8083/audit
```

Expected audit entry:

```json
{
  "event": "ACCESS",
  "user": "agent_alpha",
  "actor": "frontend-service",
  "action": "GET /api/resources",
  "scope": "backend:read",
  "result": "ALLOWED"
}
```

This proves that the full Docker Compose flow works:

```text
Client
  -> frontend-service container
  -> sts-service container
  -> backend-service container
  -> audit log
```

## Docker files

The project contains one `Dockerfile` for each service:

```text
identity-provider/Dockerfile
sts-service/Dockerfile
server/Dockerfile
frontend-service/Dockerfile
```

The root folder contains:

```text
docker-compose.yml
```

Each Dockerfile uses a two-stage build:

```text
1. Build stage: uses Java 17 JDK and Maven wrapper to build the jar
2. Runtime stage: uses Java 17 JRE to run the jar
```

This keeps the runtime container simpler than the build container.

## Docker commands used during development

Build individual images manually:

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\identity-provider
docker build -t identity-provider .

cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\sts-service
docker build -t sts-service .

cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\server
docker build -t backend-service .

cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\frontend-service
docker build -t frontend-service .
```

Run everything together with Docker Compose:

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise

docker compose up --build
```

Stop everything:

```powershell
docker compose down
```

Check running containers:

```powershell
docker ps
```

Check all containers, including stopped ones:

```powershell
docker ps -a
```

Remove old test containers if needed:

```powershell
docker rm -f frontend-service-test
docker rm -f backend-service-test
docker rm -f sts-service-test
docker rm -f identity-provider-test
```

## Manual backend security checks

### Missing token

```powershell
curl.exe -i http://localhost:8083/api/resources
```

Expected:

```text
HTTP/1.1 401
```

Audit result:

```text
DENIED_MISSING_TOKEN
```

### Read token used for GET

Expected:

```text
HTTP/1.1 200
```

Audit result:

```text
ALLOWED
```

### Read token used for POST

Expected:

```text
HTTP/1.1 403
```

Audit result:

```text
DENIED_INSUFFICIENT_SCOPE
```

## Running tests

### Backend service

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\server

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd test
```

Current backend tests include:

```text
ResourceSecurityTest
AuditFlowTest
ServerApplicationTests
```

### Frontend service

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\frontend-service

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd test
```

Current frontend tests include:

```text
FrontendControllerTest
FrontendOrchestrationServiceTest
```

### STS service

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\sts-service

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd test
```

Current STS tests include:

```text
ExchangeControllerTest
DelegatedTokenServiceTest
SubjectTokenValidatorTest
StsServiceApplicationTests
```

### Identity Provider

```powershell
cd C:\Users\atimo\OneDrive\documents\token-exchange-exercise\identity-provider

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd test
```

## Security properties demonstrated

This project demonstrates:

```text
ID token is issued by Identity Provider
Delegated access token is issued by STS
Backend does not accept raw ID tokens
Backend validates STS signature using JWKS
Backend checks issuer
Backend checks audience
Backend checks expiration
Backend checks scope
Backend records delegation chain in audit log
Frontend retries once if backend returns 401
Docker Compose runs all services together
Services communicate through Docker service names
```

## Important ports

```text
frontend-service   8080
identity-provider  8081
sts-service        8082
backend/server     8083
```

## Useful endpoints

```text
POST http://localhost:8081/auth/token
GET  http://localhost:8081/.well-known/jwks.json

POST http://localhost:8082/exchange
GET  http://localhost:8082/.well-known/jwks.json

GET  http://localhost:8080/frontend/resources

GET  http://localhost:8083/api/resources
POST http://localhost:8083/api/resources
PUT  http://localhost:8083/api/resources/{id}
DELETE http://localhost:8083/api/resources/{id}
GET  http://localhost:8083/audit
```

## Current implementation status

Completed:

```text
Module 1: REST API foundation
Module 2: Identity Provider as separate service
Module 3: STS token exchange service
Module 4: Backend audit logging
Module 4: Frontend service orchestration
Module 4: Tests for backend audit flow
Module 4: Tests for frontend controller
Module 4: Tests for frontend orchestration service
Module 5: Dockerfiles for all services
Module 5: Docker Compose setup
Module 5: Full Docker Compose flow tested manually
```

Not completed yet:

```text
Module 6: Optional extensions
```

## Notes

This is a learning project. Secrets and clients are hardcoded for simplicity.

The audit log is in-memory and will be cleared when the backend service restarts.

The services use local ports and static configuration for local development.

Docker Compose is available for running all services together with one command.