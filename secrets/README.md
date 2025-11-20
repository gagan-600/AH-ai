Local secret handling (examples)

1) Simple local dev (recommended for convenience)
- Copy `.env.example` to `.env` and set your keys there.
- Ensure `.env` is in `.gitignore` (already in project).
- `docker-compose` will read `.env` and/or `env_file` entries.

2) Using Docker secrets (better for production / swarm)
- Create a secret from a file:
  ```bash
  echo "sk-..." > ./secrets/openai_api_key.txt
  docker secret create openai_api_key ./secrets/openai_api_key.txt
  ```
- Use `docker stack deploy -c docker-compose.secrets.yml mystack` (Swarm mode).
- Inside the container the secret is available at `/run/secrets/openai_api_key`.

3) Using docker-compose (non-swarm) with override (less secure)
- You can use `env_file` in `docker-compose.yml` and keep `.env` out of version control.
- Example `docker-compose.yml` already loads `.env` via `env_file: - .env`.

Notes:
- Never commit real API keys into git. Use CI secrets, environment variables in hosting platforms, or Docker secrets in production.
