# Scripts

Python scripts that support the Ocean Current API. They are not part of the Spring Boot application.

| Folder | Purpose |
| --- | --- |
| [`cron/`](cron/README.md) | Production jobs that run on the legacy EC2 server and generate the JSON indexes this API reads |
| [`monitoring/`](monitoring/README.md) | Manual helpers for the fatal-log monitoring endpoint |

## Tests

The cron scripts use Python 3.8 and the same `requests` version as the legacy server, set in [`cron/pyproject.toml`](cron/pyproject.toml).

Install [`uv`](https://docs.astral.sh/uv/getting-started/installation/), then run the tests from the repo root. CI runs the same command. `uv` sets up Python and the dependencies on the first run.

```bash
uv run --project scripts/cron pytest scripts/cron
```
