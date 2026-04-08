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


