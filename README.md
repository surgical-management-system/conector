---
# DACS Connector

This project is the main connector for the backend/BFF. Its function is to act as an intermediary between the different services and modules of the system, managing the communication, integration, and data processing necessary for the operation of the application.

# ms-dacs-conector

Microservice for connecting to external APIs

## Objective
![Alternative text](assets/infraestructura.png)

## Configuration
[See the infrastructure configuration (PDF)](assets/DACS-configuracion-de-infraestructura.pdf)

# Run locally
```
mvn clean spring-boot:run
```

You can optionally add the parameter:

-P local

```

# Conector

This is the **Conector** project for DACS2025.

## Description
A Spring Boot microservice responsible for connecting and integrating external systems with the DACS2025 platform.

## Features
- Integration with external APIs
- Data transformation
- Logging

## Requirements
- Java 17+
- Maven 3.6+

## Getting Started
1. Clone the repository.
2. Configure your settings in `src/main/resources/application.yml` or `application.properties`.
3. Build the project:
	```bash
	./mvnw clean install
	```
4. Run the application:
	```bash
	./mvnw spring-boot:run
	```

## Project Structure
- `src/main/java` - Source code
- `src/main/resources` - Configuration files
- `assets` - Static assets

## License
MIT License
## Architecture & Technology Stack

**Patterns:**
- Microservice architecture
- Integration pattern (external APIs)
- Layered architecture (Controller, Service, Repository)
- Dependency Injection (Spring Framework)

**Technology Stack:**
- Java 17+
- Spring Boot
- Maven


