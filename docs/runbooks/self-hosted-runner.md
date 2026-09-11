# Self-hosted Raspberry Pi runner

Linux CI for `koala-backend`, `koala-web` and `koala-admin` shares one Raspberry
Pi. Three runner processes can be registered; they still share one CPU, one
disk and one Docker daemon. A hung job in any repo blocks the others.

## What CI already does

- Backend `./mvnw verify` runs on pull requests and `workflow_dispatch`, not on
  `main` push (is-koala/koala-backend#91 / #174). Image build on `main` does not
  wait for that skipped job.
- Backend jobs have wall-clock timeouts (test 45m, image push 60m, deploy 5m)
  so a stuck QEMU build cannot occupy the Pi indefinitely.
- Test-report upload is `continue-on-error`. Artifact storage quota must not fail
  a green Surefire/Failsafe run. JaCoCo HTML is not uploaded.
- Docker `cache-to: type=gha` uses `ignore-error=true` so quota cannot fail an
  image that already pushed.

## When the Pi is the problem

A job that dies with "the next steps never ran / logs were not uploaded" is
usually the machine, not the suite (measured in is-koala/koala#97). Typical
signatures: Docker `no space left on device`, QEMU `amd64` builds past the job
timeout, or three heavy suites scheduled at once.

Safe checks on the Pi — none of these delete named volumes or running
containers:

```bash
df -h
docker system df
docker ps -a
```

Safe reclaim, only unused build cache and dangling images:

```bash
docker builder prune -f
docker image prune -f
```

Do not run `docker system prune -a --volumes`. That removes images the next
backend `main` push would otherwise reuse and can delete local volume data.

## What production actually waits on

`main` push publishes `ghcr.io/<owner>/koala:main-<sha7>` and does **not**
move `:latest`. Dokploy's production app tracks `:latest` and the deploy
webhook fires only on `v*` tags. A green `main` workflow is necessary for a
`main-*` image; it is not a production deploy by itself.

If the runner is offline, GitHub shows queued jobs with no `Set up job`. That
is a human step: start the runner service on the Pi. This repository cannot
reach that machine.
