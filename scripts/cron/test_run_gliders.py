"""Exercise the downloaded entry point without accessing production or the network."""
import io
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest


SCRIPT = Path(__file__).parent / 'gliders' / 'run_gliders.sh'
MODULES = ('gliders_catalogue', 'gliders_logging', 'publish_gliders_catalogue',
           'gliders_yearmaps', 'publish_gliders_yearmaps')


class RunGlidersTests(unittest.TestCase):
    def run_entry(self, catalogue=0, yearmaps=0, download=0, corrupt=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            temporary = root / 'tmp'
            temporary.mkdir()
            archive = root / 'snapshot.tar.gz'
            with tarfile.open(archive, 'w:gz') as tar:
                for module in MODULES:
                    content = b'# fixture module\n'
                    member = tarfile.TarInfo('ocean-current-api-main/scripts/cron/gliders/' + module + '.py')
                    member.size = len(content)
                    tar.addfile(member, io.BytesIO(content))
            if corrupt:
                archive.write_bytes(b'invalid archive')
            # curl checks its limits and copies a real tar fixture; timeout records each
            # invocation but never executes Python against the production paths.
            (bin_dir / 'curl').write_text('''#!/bin/bash
printf '%s\\n' "$*" >> "$CALLS"
[ "$1 $2 $3 $4 $5" = '-fsSL --connect-timeout 15 --max-time 120' ] || exit 99
[ "$DOWNLOAD" = 0 ] || exit "$DOWNLOAD"
cp "$ARCHIVE" "${@: -1}"
''')
            (bin_dir / 'timeout').write_text('''#!/bin/bash
printf '%s\\n' "$*" >> "$CALLS"
[ "$1 $2 $3 $4" = '-k 30s 15m /usr/bin/python3' ] || exit 99
[ -f "$5" ] || exit 98
case "$5" in
  *publish_gliders_catalogue.py) exit "$CATALOGUE" ;;
  *publish_gliders_yearmaps.py) exit "$YEARMAPS" ;;
  *) exit 97 ;;
esac
''')
            for executable in bin_dir.iterdir():
                executable.chmod(0o755)
            calls = root / 'calls'
            env = dict(os.environ, PATH=str(bin_dir) + ':' + os.environ['PATH'],
                       TMPDIR=str(temporary), CALLS=str(calls), ARCHIVE=str(archive),
                       CATALOGUE=str(catalogue), YEARMAPS=str(yearmaps), DOWNLOAD=str(download))
            result = subprocess.run(['bash', str(SCRIPT)], env=env, capture_output=True, text=True)
            self.assertEqual(list(temporary.iterdir()), [])
            return result, calls.read_text().splitlines()

    def test_stage_results_and_continuation(self):
        for catalogue, yearmaps in [(0, 0), (1, 0), (0, 1), (124, 137), (1, 1)]:
            with self.subTest(catalogue=catalogue, yearmaps=yearmaps):
                result, calls = self.run_entry(catalogue, yearmaps)
                self.assertEqual(result.returncode, int(bool(catalogue or yearmaps)))
                stream = result.stdout if catalogue == yearmaps == 0 else result.stderr
                self.assertIn('Gliders catalogue={} yearmaps={}'.format(catalogue, yearmaps), stream)
                if catalogue == yearmaps == 0:
                    self.assertEqual(result.stderr, '')
                self.assertEqual(len(calls), 3)
                self.assertIn('publish_gliders_catalogue.py --input /mnt/oceancurrent/website/gliders/index.html', calls[1])
                self.assertIn('--output /mnt/oceancurrent/website/api/gliders-catalogue.json', calls[1])
                self.assertIn('publish_gliders_yearmaps.py --input /mnt/oceancurrent/website/gliders/yearmaps', calls[2])
                self.assertIn('--catalogue /mnt/oceancurrent/website/api/gliders-catalogue.json', calls[2])
                self.assertIn('--output /mnt/oceancurrent/website/api/gliders-yearmaps.json', calls[2])
                for call in calls[1:]:
                    self.assertTrue(call.endswith('--stability-seconds 30'))

    def test_failed_download_and_corrupt_archive_cleanup(self):
        for options in [dict(download=22), dict(corrupt=True)]:
            with self.subTest(options=options):
                result, calls = self.run_entry(**options)
                self.assertEqual(result.returncode, 1)
                self.assertEqual(len(calls), 1)
                self.assertIn('Gliders catalogue=1 yearmaps=1', result.stderr)
