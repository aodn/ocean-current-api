"""Temporary demo test to see Scripts CI run. Remove before merging."""
import sys

import requests


def test_runs_on_python_38():
    assert sys.version_info[:2] == (3, 8)


def test_requests_is_installed():
    assert requests.__version__
