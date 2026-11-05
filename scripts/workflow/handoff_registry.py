"""Public opaque handoffs resolve only through the enrolling repository's private registry."""
import hashlib
import json
import os
from pathlib import Path
import re
import uuid


def retain_review_evidence(storage, value):
    """Immutable private evidence within the existing coordinator storage, never an enrollment."""
    directory = Path(storage) / 'review-evidence'
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if directory.is_symlink() or directory.stat().st_uid != os.getuid() or directory.stat().st_mode & 0o077:
        raise ValueError('Review evidence directory must be private and owned')
    identifier = uuid.uuid4().hex
    raw = json.dumps(value, sort_keys=True).encode()
    reference = identifier + ':' + hashlib.sha256(raw).hexdigest()
    fd = os.open(directory / (identifier + '.json'), os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(raw)
    return reference


def read_review_evidence(storage, reference):
    if not isinstance(reference, str) or not re.fullmatch(r'[0-9a-f]{32}:[0-9a-f]{64}', reference):
        raise ValueError('Invalid review evidence reference')
    identifier, expected = reference.split(':')
    directory = Path(storage) / 'review-evidence'
    if directory.is_symlink() or directory.stat().st_uid != os.getuid() or directory.stat().st_mode & 0o077:
        raise ValueError('Review evidence directory must be private and owned')
    path = directory / (identifier + '.json')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as stream:
        stat = os.fstat(stream.fileno())
        if stat.st_uid != os.getuid() or stat.st_mode & 0o077:
            raise ValueError('Review evidence must be private and owned')
        raw = stream.read()
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError('Review evidence changed')
    return json.loads(raw)


def digest(public):
    return hashlib.sha256(json.dumps(public, sort_keys=True).encode()).hexdigest()


def register(storage, public, source, host):
    public = dict(public, registry_id=uuid.uuid4().hex, version=2)
    directory = Path(storage) / 'registry'
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(directory, 0o700)
    path = directory / (public['registry_id'] + '.json')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump({'digest': digest(public), 'source': str(source), 'host': host}, stream)
    return public


def resolve(storage, public, host):
    if public.get('version') != 2 or not re.fullmatch(r'[0-9a-f]{32}', public.get('registry_id', '')):
        raise ValueError('Invalid opaque enrollment')
    path = Path(storage) / 'registry' / (public['registry_id'] + '.json')
    try:
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        with os.fdopen(fd) as stream:
            stat = os.fstat(stream.fileno())
            if stat.st_uid != os.getuid() or stat.st_mode & 0o077:
                raise ValueError('Registry must be private and locally owned')
            local = json.load(stream)
    except (OSError, ValueError) as error:
        raise ValueError('Local enrollment registry unavailable or invalid; explicit owner recovery required') from error
    if (not isinstance(local, dict) or local.get('digest') != digest(public) or local.get('host') != host or
            not isinstance(local.get('source'), str) or not Path(local['source']).is_absolute()):
        raise ValueError('Enrollment binding/local ownership mismatch; preserve existing owner')
    return dict(public, source=local['source'], host=local['host'])
