"""Shared logging configuration for the Gliders publication jobs."""
import logging
import os
from pathlib import Path
import sys


LOG_FORMAT = "%(asctime)s %(levelname)s %(name)s %(message)s"
DEFAULT_LOG_PATH = Path("/var/log/imos/gliders_publishers.log")


class _BelowError(logging.Filter):
    def filter(self, record):
        return record.levelno < logging.ERROR


def configure_logging():
    """Persist all logs, while reserving stderr (and cron email) for errors."""
    stdout_handler = logging.StreamHandler(sys.stdout)
    stdout_handler.addFilter(_BelowError())

    stderr_handler = logging.StreamHandler(sys.stderr)
    stderr_handler.setLevel(logging.ERROR)

    log_path = Path(os.environ.get("GLIDERS_LOG_PATH", str(DEFAULT_LOG_PATH)))
    try:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        file_handler = logging.FileHandler(str(log_path), mode="a")
    except OSError as error:
        fallback = Path.cwd() / DEFAULT_LOG_PATH.name
        file_handler = logging.FileHandler(str(fallback), mode="a")
        print("Warning: Cannot write to {}; using fallback {}: {}".format(log_path, fallback, error))

    logging.basicConfig(
        level=logging.INFO,
        format=LOG_FORMAT,
        handlers=[stdout_handler, stderr_handler, file_handler],
    )
