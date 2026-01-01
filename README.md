# GitHub Adapter for Ezone

This project acts as an adapter layer to expose a GitHub-compatible API for an Ezone repository.
It allows tools like `deepwiki-open` to interact with Ezone repositories as if they were GitHub repositories.

## Prerequisites

- Java 17+
- Maven 3+
- Git

## Configuration

The application is configured via `src/main/resources/application.properties`:

```properties
server.port=8080
ezone.repo.url=https://demo1.ezone.work/code/ah/futian.git
ezone.repo.username=test02
ezone.repo.password=test02123
ezone.repo.local-path=/tmp/ezone-repo
```

Update `ezone.repo.username` and `ezone.repo.password` with valid credentials if they change.

## Running the Application

1. Build the project:
   ```bash
   mvn clean package
   ```

2. Run the application:
   ```bash
   java -jar target/ezone-adapter-0.0.1-SNAPSHOT.jar
   ```

## API Usage

The adapter exposes the following GitHub-compatible endpoints:

- `GET /repos/{owner}/{repo}/contents/{path}`: Get file or directory content.
- `GET /repos/{owner}/{repo}/branches`: List branches (mocked).
- `GET /user`: Get current user (mocked).

## Troubleshooting

- **Authentication Failed**: If you see `InvalidCredentialsException` or "Please check username or password", ensure that the credentials in `application.properties` are correct and have access to the repository via Git over HTTPS. If 2FA is enabled on Ezone, you might need to use an Access Token instead of a password.
