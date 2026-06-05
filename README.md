# GitHub Adapter for Ezone

This project acts as an adapter layer to expose a GitHub-compatible API for an Ezone repository.
It allows tools like `deepwiki-open` to interact with Ezone repositories as if they were GitHub repositories.

## Prerequisites

- Java 17+
- Maven 3+
- Git

## Configuration

The application is configured via environment variables. **Do not hard-code credentials.**

| Variable | Description | Default |
|---|---|---|
| `EZONE_REPO_URL` | Ezone repository HTTPS URL | `https://demo1.ezone.work/code/ah/futian.git` |
| `EZONE_USERNAME` | Ezone username | *(required)* |
| `EZONE_PASSWORD` | Ezone password or access token | *(required)* |
| `EZONE_LOCAL_PATH` | Local clone path | `/tmp/ezone-repo` |
| `ADAPTER_API_KEY` | API key for authenticating incoming requests | *(required)* |
| `SERVER_PORT` | HTTP listen port | `8080` |

Example:

```bash
export EZONE_USERNAME=myuser
export EZONE_PASSWORD=mytoken
export ADAPTER_API_KEY=$(openssl rand -hex 32)
```

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

- **Authentication Failed**: If you see `InvalidCredentialsException` or "Please check username or password", ensure that the `EZONE_USERNAME` and `EZONE_PASSWORD` environment variables are set correctly and the account has access to the repository via Git over HTTPS. If 2FA is enabled on Ezone, use an Access Token instead of a password.
- **401 Unauthorized on API calls**: Ensure you pass a valid `Authorization: Bearer <token>` header matching the `ADAPTER_API_KEY` environment variable.
