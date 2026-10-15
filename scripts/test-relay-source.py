#!/usr/bin/env python3
"""Offline integration tests: real Git tags and real Go builds; no GitHub access."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('relay-source.py')
REPO = 'https://github.com/decentral-prospect/symposium-relay.git'
ASSET = 'symposium-server-linux-amd64'


class RelaySourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='relay-source-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'upstream'
        self.app = self.root / 'app'
        (self.app / 'scripts').mkdir(parents=True)
        shutil.copy2(SCRIPT, self.app / 'scripts/relay-source.py')
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL=str(self.root / 'gitconfig'), GIT_CONFIG_NOSYSTEM='1',
                        GIT_AUTHOR_NAME='Test', GIT_AUTHOR_EMAIL='test@example.invalid',
                        GIT_COMMITTER_NAME='Test', GIT_COMMITTER_EMAIL='test@example.invalid')
        self.git('init', '-q', self.source)
        (self.source / 'go.mod').write_text('module fixture\n\ngo 1.18\n')
        (self.source / 'go.sum').write_text('')
        (self.source / 'main.go').write_text('package main\nimport "fmt"\nvar serverVersion string\nfunc main() { fmt.Print(serverVersion) }\n')
        (self.source / '.gitignore').write_text('ignored.go\n')
        self.git('-C', self.source, 'add', '.')
        self.git('-C', self.source, 'commit', '-qm', 'source')
        self.revision = self.git('-C', self.source, 'rev-parse', 'HEAD').stdout.strip()
        self.git('-C', self.source, 'tag', '-a', '0.3.4', '-m', 'release')
        self.git('config', '--global', f'url.file://{self.source}.insteadOf', REPO)
        self.lock = dict(repository='decentral-prospect/symposium-relay', source_repository='decentral-prospect/symposium-relay',
                         version='0.3.4', source_revision=self.revision, asset=ASSET,
                         url=f'https://github.com/decentral-prospect/symposium-relay/releases/download/0.3.4/{ASSET}', sha256='0' * 64)
        self.write_lock()
        self.checkout = self.app / '.relay-work/source'
        self.command('fetch', self.checkout)
        self.command('download-deps', self.checkout)

    def run_command(self, *args, ok=True):
        result = subprocess.run([str(a) for a in args], env=self.env, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, ok, result.stdout + result.stderr)
        return result

    def git(self, *args):
        return self.run_command('git', *args)

    def command(self, *args, ok=True):
        return self.run_command('python3', self.app / 'scripts/relay-source.py', *args, ok=ok)

    def write_lock(self):
        (self.app / 'relay.lock.json').write_text(json.dumps(self.lock))

    def test_offline_build_and_bundle_verification(self):
        # F-Droid prepares/scans sources before compilation. Preparation must
        # not produce either the executable or its receipt.
        self.command('download-deps', self.checkout)
        binary = self.app / 'app/src/main/assets/symposium' / ASSET
        receipt_path = self.app / 'build/relay/bundled-relay.json'
        self.assertFalse(binary.exists())
        self.assertFalse(receipt_path.exists())
        # Share only the compiler cache across fixtures, never Go module sources.
        cache = self.app / 'build/relay/go-cache'
        cache.parent.mkdir(parents=True)
        shared_cache = Path(tempfile.gettempdir()) / 'symposium-relay-test-go-cache'
        shared_cache.mkdir(exist_ok=True)
        cache.symlink_to(shared_cache)
        self.command('build', self.checkout)
        self.command('verify-bundle')
        self.assertEqual(self.run_command(binary).stdout, '0.3.4')
        receipt = json.loads(receipt_path.read_text())
        self.assertNotEqual(receipt['sha256'], self.lock['sha256'])
        original = binary.read_bytes()
        binary.write_bytes(original + b'tampered')
        self.assertIn('checksum mismatch', self.command('verify-bundle', ok=False).stderr)
        binary.write_bytes(original)
        self.lock['source_revision'] = '1' * 40
        self.write_lock()
        self.assertIn('Stale', self.command('verify-bundle', ok=False).stderr)
        self.lock['source_revision'] = self.revision
        self.write_lock()
        receipt['source_repository'] = 'untrusted/relay'
        receipt_path.write_text(json.dumps(receipt))
        self.command('verify-bundle', ok=False)

    def test_lock_change_requires_rebuild_even_at_same_commit(self):
        # An upstream release digest can change independently of source fields.
        # The bundle receipt must bind the entire lock, not just the revision.
        self.command('build', self.checkout)
        self.lock['sha256'] = '1' * 64
        self.write_lock()
        self.assertIn('lock changed', self.command('verify-bundle', ok=False).stderr)

    def test_prepared_dependencies_reject_changes_and_extra_files(self):
        directory = self.app / '.relay-work/deps'
        for name in ('vendor/modules.txt', 'go.work', 'vendor/extra.go'):
            with self.subTest(name=name):
                path = directory / name
                original = path.read_bytes() if path.exists() else None
                path.write_text('changed')
                self.assertIn('checksum mismatch', self.command('build', self.checkout, ok=False).stderr)
                if original is None:
                    path.unlink()
                else:
                    path.write_bytes(original)
        self.command('verify-deps', self.checkout)

    def test_prepared_dependencies_reject_symlinks(self):
        link = self.app / '.relay-work/deps/vendor/injected.go'
        link.symlink_to(self.checkout / 'main.go')
        self.assertIn('Symlink', self.command('verify-deps', self.checkout, ok=False).stderr)

    def test_dependencies_survive_clean_and_project_relocation(self):
        # The F-Droid builder copies the project to another absolute path and
        # runs Gradle clean between preparation and compilation.
        relocated = self.root / 'relocated'
        shutil.copytree(self.app, relocated)
        shutil.rmtree(relocated / 'build', ignore_errors=True)
        command = relocated / 'scripts/relay-source.py'
        source = relocated / '.relay-work/source'
        self.run_command('python3', command, 'verify-deps', source)
        self.run_command('python3', command, 'build', source)
        self.run_command('python3', command, 'verify-bundle')

    def test_changed_lock_rejects_prepared_dependencies(self):
        self.lock['sha256'] = '1' * 64
        self.write_lock()
        self.assertIn('lock changed', self.command('build', self.checkout, ok=False).stderr)

    def test_dirty_and_ignored_sources_are_rejected(self):
        for name in ('main.go', 'extra.go', 'ignored.go'):
            with self.subTest(name=name):
                path = self.checkout / name
                original = path.read_bytes() if path.exists() else None
                path.write_text('package main\n')
                self.assertIn('dirty', self.command('build', self.checkout, ok=False).stderr)
                if original is None:
                    path.unlink()
                else:
                    path.write_bytes(original)

    def test_wrong_tag_even_with_same_named_branch_is_rejected(self):
        self.git('-C', self.checkout, 'branch', '0.3.4', self.revision)
        self.git('-C', self.checkout, 'tag', '-d', '0.3.4')
        self.command('verify-source', self.checkout, ok=False)

    def test_moved_tag_is_rejected_before_checkout(self):
        (self.source / 'main.go').write_text('package main\nfunc main() {}\n')
        self.git('-C', self.source, 'commit', '-am', 'changed')
        self.git('-C', self.source, 'tag', '-f', '0.3.4')
        result = self.command('fetch', self.root / 'new-checkout', ok=False)
        self.assertIn('tag does not match', result.stderr)

    def test_wrong_head_and_origin_are_rejected(self):
        self.lock['source_revision'] = '1' * 40
        self.write_lock()
        self.assertIn('HEAD', self.command('build', self.checkout, ok=False).stderr)
        self.lock['source_revision'] = self.revision
        self.write_lock()
        self.git('-C', self.checkout, 'remote', 'set-url', 'origin', 'https://example.invalid/relay.git')
        self.assertIn('origin', self.command('verify-source', self.checkout, ok=False).stderr)

    def test_invalid_lock_and_missing_receipt_fail_closed(self):
        self.command('verify-bundle', ok=False)
        for key, value in (('source_revision', 'main'), ('version', '../main'), ('source_repository', 'other/repo'),
                           ('url', 'https://example.invalid/binary'), ('sha256', 'bad')):
            with self.subTest(key=key):
                original = self.lock[key]
                self.lock[key] = value
                self.write_lock()
                self.command('fetch', self.checkout, ok=False)
                self.lock[key] = original


class InstallerDigestTest(unittest.TestCase):
    def test_download_and_uploaded_bundle_use_their_own_checksums(self):
        # Execute the actual embedded installer functions, without SSH or apt.
        source = SCRIPT.parent.parent / 'app/src/main/java/com/decentralprospect/symposium/install/RemoteInstaller.kt'
        text = source.read_text().replace("${'$'}", '$')
        functions = text[text.index('            verify_server_binary() {'):text.index('            log "[1/8] Detect OS"')]
        final_check = next(line.strip() for line in text.splitlines()
                           if 'verify_server_binary "$BINARY_DIR/$BINARY_NAME"' in line)
        with tempfile.TemporaryDirectory(prefix='installer-digest-test-') as temporary:
            root = Path(temporary)
            uploaded, download = root / 'uploaded', root / 'release'
            uploaded.write_bytes(b'compiled from locked sources')
            download.write_bytes(b'upstream release from same sources')
            env = dict(os.environ, LOCAL_BINARY=str(uploaded), DOWNLOADED_BINARY=str(root / 'download'),
                       FIXTURE_DOWNLOAD=str(download), RELAY_RELEASE_VERSION='0.3.4', RELAY_DOWNLOAD_URL='unused',
                       RELAY_EXPECTED_SHA256=hashlib.sha256(download.read_bytes()).hexdigest(),
                       RELAY_BUNDLED_SHA256=hashlib.sha256(uploaded.read_bytes()).hexdigest(),
                       BINARY_DIR=str(root), BINARY_NAME='installed')
            harness = '''set -Eeuo pipefail
log() { :; }
die() { echo "$*" >&2; exit 1; }
curl() { while [[ "$1" != -o ]]; do shift; done; cp "$FIXTURE_DOWNLOAD" "$2"; }
''' + functions + '\nselect_server_binary\ncp "$SERVER_BINARY_SOURCE" "$BINARY_DIR/$BINARY_NAME"\n' + final_check
            for mode, content in [('0', download.read_bytes()), ('1', uploaded.read_bytes())]:
                env['RELAY_FORCE_LOCAL_BINARY'] = mode
                result = subprocess.run(['bash', '-c', harness], env=env, capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual((root / 'installed').read_bytes(), content)
            # A source binary cannot pass as the release, nor vice versa.
            env['RELAY_EXPECTED_SHA256'], env['RELAY_BUNDLED_SHA256'] = (
                env['RELAY_BUNDLED_SHA256'], env['RELAY_EXPECTED_SHA256'])
            for mode in ('0', '1'):
                env['RELAY_FORCE_LOCAL_BINARY'] = mode
                result = subprocess.run(['bash', '-c', harness], env=env, capture_output=True, text=True)
                self.assertNotEqual(result.returncode, 0)


if __name__ == '__main__':
    unittest.main(verbosity=2)
