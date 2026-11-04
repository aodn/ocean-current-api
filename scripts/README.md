# Scripts

Python scripts that support the Ocean Current API. They are not part of the Spring Boot application.

| Folder | Purpose |
| --- | --- |
| [`cron/`](cron/README.md) | Production jobs that run on the legacy EC2 server and generate the JSON indexes this API reads |
| [`monitoring/`](monitoring/README.md) | Manual helpers for the fatal-log monitoring endpoint |

## Tests

CI runs the tests on Python 3.8 with the same `requests` version as the legacy server. See `test-scripts` in [`ci.yaml`](../.github/workflows/ci.yaml) for the exact versions.

```bash
python -m pip install pytest requests
python -m pytest scripts
```
