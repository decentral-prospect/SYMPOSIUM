#!/usr/bin/env python3
"""Build the bundled Linux relay from the exact source pinned by relay.lock.json.

Fetching sources/dependencies is separate from the offline compilation step so
F-Droid can scan and archive the sources before building any executable.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
REPOSITORY = 'decentral-prospect/symposium-relay'
ASSET = 'symposium-server-linux-amd64'
VERSION = r'[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def run(*args, cwd=None, env=None):
    result = subprocess.run([str(a) for a in args], cwd=cwd, env=env,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    require(result.returncode == 0, f'{args[0]} failed: {result.stderr.strip()}')
    return result.stdout.strip()


def digest(path):
    with path.open('rb') as stream:
        value = hashlib.sha256()
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
        return value.hexdigest()


def read_lock():
    lock = json.loads((ROOT / 'relay.lock.json').read_text())
    require(isinstance(lock, dict), 'relay.lock.json must be an object')
    for key in ('repository', 'source_repository', 'version', 'source_revision', 'asset', 'url', 'sha256'):
        require(isinstance(lock.get(key), str), f'Missing lock field: {key}')
    require(lock['repository'] == lock['source_repository'] == REPOSITORY, 'Unexpected relay repository')
    require(lock['asset'] == ASSET, 'Unexpected relay asset')
    require(re.fullmatch(VERSION, lock['version']), 'Invalid relay version')
    require(re.fullmatch(r'[0-9a-f]{40}', lock['source_revision']), 'Relay requires a full commit SHA')
    require(re.fullmatch(r'[0-9a-f]{64}', lock['sha256']), 'Invalid release SHA-256')
    require(lock['url'] == f'https://github.com/{REPOSITORY}/releases/download/{lock["version"]}/{ASSET}',
            'Unexpected release URL')
    return lock


def verify_source(source, lock):
    require(source.is_dir(), f'Missing relay source: {source}')
    require(Path(run('git', '-C', source, 'rev-parse', '--show-toplevel')).resolve() == source,
            'Relay source must be a separate Git checkout')
    require(run('git', '-C', source, 'config', '--get', 'remote.origin.url') ==
            f'https://github.com/{REPOSITORY}.git', 'Unexpected relay source origin')
    revision = run('git', '-C', source, 'rev-parse', 'HEAD')
    require(revision == lock['source_revision'], 'Relay HEAD does not match source_revision')
    tagged = run('git', '-C', source, 'rev-parse', '--verify', f'refs/tags/{lock["version"]}^{{commit}}')
    require(tagged == revision, 'Relay tag does not match source_revision')
    # Ignored Go files, workspaces, or overlays must not sneak into compilation.
    require(not run('git', '-C', source, 'status', '--porcelain', '--untracked-files=all', '--ignored'),
            'Relay source checkout is dirty (including untracked/ignored files)')
    entries = run('git', '-C', source, 'ls-files', '--stage').splitlines()
    require(not any(line.startswith('160000 ') for line in entries),
            'Relay source submodules are unsupported')
    require((source / 'go.mod').is_file() and (source / 'go.sum').is_file(), 'Missing Go module lock files')


def fetch(source, lock):
    if not source.exists():
        source.parent.mkdir(parents=True, exist_ok=True)
        run('git', 'init', '--quiet', source)
        run('git', '-C', source, 'remote', 'add', 'origin', f'https://github.com/{REPOSITORY}.git')
        ref = f'refs/tags/{lock["version"]}'
        run('git', '-C', source, 'fetch', '--depth=1', '--no-tags', 'origin', f'{ref}:{ref}')
        # Compare the tag before checking out/building anything from it.
        require(run('git', '-C', source, 'rev-parse', f'{ref}^{{commit}}') == lock['source_revision'],
                'Relay tag does not match source_revision')
        run('git', '-C', source, 'checkout', '--quiet', '--detach', lock['source_revision'])
    verify_source(source, lock)


def go_environment(offline=False):
    env = dict(os.environ)
    env.update(GOTOOLCHAIN='local', GOENV='off', GOWORK='off', GOFLAGS='', GO111MODULE='on',
               GOOS='linux', GOARCH='amd64', GOAMD64='v1', CGO_ENABLED='1',
               GOPRIVATE='', GONOSUMDB='', GONOPROXY='', GOSUMDB='sum.golang.org',
               GOMODCACHE=str(ROOT / 'build/relay/gomod'), GOCACHE=str(ROOT / 'build/relay/go-cache'))
    env['GOPROXY'] = 'off' if offline else 'https://proxy.golang.org'
    if offline:
        # download-deps authenticates modules before vendoring. Compilation
        # uses only that verified source tree, never the download cache.
        env['GOSUMDB'] = 'off'
    return env


def dependency_paths():
    return ROOT / '.relay-work/deps', ROOT / '.relay-work/deps.json'


def workspace_text(source, directory):
    directive = re.search(r'^go\s+(\d+\.\d+(?:\.\d+)?)\s*$',
                          (source / 'go.mod').read_text(), re.MULTILINE)
    require(directive is not None, 'Missing Go version in locked go.mod')
    version = tuple(int(part) for part in directive[1].split('.'))
    version = max(version + (0,) * (3 - len(version)), (1, 22, 0))
    relative_source = Path(os.path.relpath(source, directory)).as_posix()
    # A controlled workspace lets Go use vendor outside the locked checkout.
    # Relative paths also survive copying the project to the F-Droid builder.
    return f'go {".".join(map(str, version))}\n\nuse {json.dumps(relative_source)}\n'


def dependency_files(directory):
    require(directory.is_dir() and not directory.is_symlink(), 'Missing dependency source directory')
    files = {}
    for path in sorted(directory.rglob('*')):
        require(not path.is_symlink(), f'Symlink in prepared dependencies: {path}')
        if path.is_dir():
            continue
        require(path.is_file(), f'Non-regular dependency file: {path}')
        files[path.relative_to(directory).as_posix()] = digest(path)
    return files


def download_deps(source, lock):
    verify_source(source, lock)
    directory, manifest = dependency_paths()
    directory.parent.mkdir(parents=True, exist_ok=True)
    require(not directory.is_symlink(), 'Dependency directory must not be a symlink')
    # ZIPs, unused platform binaries and compiler caches never enter the source
    # tree scanned/archived by F-Droid. Only go work vendor's output is retained.
    with tempfile.TemporaryDirectory(prefix='relay-modules-') as cache, \
            tempfile.TemporaryDirectory(prefix='deps-', dir=directory.parent) as staging:
        staged = Path(staging) / 'prepared'
        staged.mkdir()
        env = go_environment()
        env.update(GOMODCACHE=str(Path(cache) / 'gomod'), GOCACHE=str(Path(cache) / 'gocache'))
        module = json.loads(run('go', 'mod', 'edit', '-json', cwd=source, env=env))
        require(all(item['New'].get('Version') for item in module.get('Replace') or []),
                'Local Go module replacements are not pinned by go.sum')
        run('go', 'mod', 'download', cwd=source, env=env)
        run('go', 'mod', 'verify', cwd=source, env=env)
        # The temporary workspace is one directory deeper than the final one.
        (staged / 'go.work').write_text(workspace_text(source, staged))
        vendor_env = dict(env, GOWORK=str(staged / 'go.work'), GOPROXY='off', GOSUMDB='off')
        run('go', 'work', 'vendor', cwd=source, env=vendor_env)
        run('go', 'mod', 'verify', cwd=source, env=env)
        verify_source(source, lock)
        (staged / 'go.work').write_text(workspace_text(source, directory))
        data = dict(schema=1, lock_sha256=digest(ROOT / 'relay.lock.json'),
                    go_mod_sha256=digest(source / 'go.mod'), go_sum_sha256=digest(source / 'go.sum'),
                    files=dependency_files(staged))
        staged_manifest = Path(staging) / 'deps.json'
        staged_manifest.write_text(json.dumps(data, indent=2, sort_keys=True) + '\n')
        if directory.exists():
            shutil.rmtree(directory)
        staged.replace(directory)
        staged_manifest.replace(manifest)
    verify_deps(source, lock)


def verify_deps(source, lock):
    directory, manifest = dependency_paths()
    require(manifest.is_file(), 'Missing prepared dependencies. Run relay-source.py download-deps SOURCE_DIR first.')
    data = json.loads(manifest.read_text())
    require(isinstance(data, dict) and data.get('schema') == 1, 'Invalid dependency manifest')
    require(data.get('lock_sha256') == digest(ROOT / 'relay.lock.json'), 'Relay lock changed; prepare dependencies again')
    require(data.get('go_mod_sha256') == digest(source / 'go.mod') and
            data.get('go_sum_sha256') == digest(source / 'go.sum'), 'Dependency lock files changed')
    require(data.get('files') == dependency_files(directory), 'Prepared dependency checksum mismatch')
    require((directory / 'go.work').read_text() == workspace_text(source, directory), 'Unexpected dependency workspace')
    return directory


def bundle_paths():
    return (ROOT / 'app/src/main/assets/symposium' / ASSET,
            ROOT / 'build/relay/bundled-relay.json')


def build(source, lock):
    verify_source(source, lock)
    dependencies = verify_deps(source, lock)
    env = go_environment(offline=True)
    env['GOWORK'] = str(dependencies / 'go.work')
    binary, receipt = bundle_paths()
    receipt.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='build-', dir=receipt.parent) as temporary:
        output = Path(temporary) / ASSET
        run('go', 'build', '-mod=vendor', '-trimpath', '-buildvcs=true',
            f'-ldflags=-s -w -X main.serverVersion={lock["version"]}', '-o', output, '.', cwd=source, env=env)
        verify_source(source, lock)
        verify_deps(source, lock)
        info = run('go', 'version', '-m', output, env=env)
        for setting in (f'vcs.revision={lock["source_revision"]}', 'vcs.modified=false',
                        'GOOS=linux', 'GOARCH=amd64', 'CGO_ENABLED=1'):
            require(f'\tbuild\t{setting}\n' in info + '\n', f'Relay build metadata missing {setting}')
        data = dict(schema=1, source_repository=REPOSITORY, source_revision=lock['source_revision'],
                    version=lock['version'], asset=ASSET, sha256=digest(output),
                    lock_sha256=digest(ROOT / 'relay.lock.json'),
                    go_version=run('go', 'env', 'GOVERSION', env=env))
        staged_receipt = Path(temporary) / 'receipt.json'
        staged_receipt.write_text(json.dumps(data, indent=2) + '\n')
        binary.parent.mkdir(parents=True, exist_ok=True)
        # Install on the destination filesystem; metadata is committed last.
        with tempfile.NamedTemporaryFile(dir=binary.parent, delete=False) as staged:
            staged_path = Path(staged.name)
            try:
                with output.open('rb') as stream:
                    shutil.copyfileobj(stream, staged)
                staged.flush()
                staged_path.chmod(0o755)
                staged_path.replace(binary)
            finally:
                staged_path.unlink(missing_ok=True)
        staged_receipt.replace(receipt)
    verify_bundle(lock)


def verify_bundle(lock):
    binary, receipt = bundle_paths()
    require(binary.is_file() and receipt.is_file(),
            'Missing source-built relay or build receipt. Run relay-source.py build SOURCE_DIR first.')
    data = json.loads(receipt.read_text())
    require(isinstance(data, dict) and data.get('schema') == 1, 'Invalid relay build receipt')
    for key in ('source_repository', 'source_revision', 'version', 'asset'):
        require(data.get(key) == lock[key], f'Stale relay build receipt: {key}')
    require(data.get('lock_sha256') == digest(ROOT / 'relay.lock.json'), 'Relay lock changed; rebuild the relay')
    require(data.get('sha256') == digest(binary), 'Bundled relay checksum mismatch')
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('fetch', 'verify-source', 'download-deps', 'verify-deps', 'build', 'verify-bundle'))
    parser.add_argument('source', nargs='?', type=Path)
    args = parser.parse_args()
    lock = read_lock()
    if args.action == 'verify-bundle':
        require(args.source is None, 'verify-bundle takes no source directory')
        verify_bundle(lock)
    else:
        require(args.source is not None, 'A relay source directory is required')
        source = args.source.resolve()
        require(not ROOT.is_relative_to(source), 'Relay checkout cannot contain the Android project')
        if args.action == 'fetch':
            fetch(source, lock)
        elif args.action == 'build':
            build(source, lock)
        else:
            verify_source(source, lock)
            if args.action == 'download-deps':
                download_deps(source, lock)
            elif args.action == 'verify-deps':
                verify_deps(source, lock)
    print(f'Relay {lock["version"]} ({lock["source_revision"]}): {args.action} OK')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError) as error:
        print(f'Relay source error: {error}', file=sys.stderr)
        sys.exit(1)
