# Publishing koala to production

Verify and release run on GitHub-hosted runners (`ubuntu-latest`). The
self-hosted Pi runner is gone; nothing in this repository targets
`self-hosted`.

## Workflows

- `.github/workflows/verify.yml` (`koala verify`) — pull requests to `main`,
  pushes to `main`, manual dispatch. Runs
  `./mvnw -B -ntp verify -Dkoala.test.latch-timeout=30` (Temurin 25, unit +
  every Failsafe execution, Testcontainers on the runner's Docker). Surefire
  and Failsafe reports are uploaded only on failure (3-day retention).
- `.github/workflows/release.yml` (`koala release`) — manual dispatch from
  `main`, plus a guard on `v*` tag pushes.

## Order

1. Merge the release PR (pom version bump) to `main`.
2. Wait for `koala verify` on the merge commit to go green. The squash merge is
   a new SHA; the PR run does not count.
3. `gh workflow run "koala release" --ref main` (or Actions → koala release →
   Run workflow).
4. The run refuses unless: it is on `main`, `koala verify` succeeded on the
   same SHA, `v<pom-version>` does not exist yet and
   `ghcr.io/is-koala/koala:<pom-version>` does not exist yet.
5. It runs `scripts/publish-ghcr-image.sh --latest` — `:<pom-version>` and
   `:latest`, a single `linux/amd64` manifest (`provenance` and `sbom` off),
   then checks the pushed tag is not an index and both tags share a digest.
6. Only then it creates the annotated tag `v<pom-version>` on that SHA and
   pushes it, and curls `DOKPLOY_DEPLOY_WEBHOOK`. Dokploy pulls `:latest`.

A tag pushed with `GITHUB_TOKEN` does not start another workflow run, so the
webhook fires once.

## Retry and manual tags

- Webhook only, image and tag already there:
  `gh workflow run "koala release" --ref main -f redeploy=true`.
- A human-pushed `v*` tag never builds an image. If the image for that version
  is on GHCR it deploys; otherwise the run fails and the tag must be deleted
  (`git push --delete origin v<version>`) and the release dispatched.
- Image pushed but tag missing (a run died between the two): the dispatch
  refuses. Check the image digest against the intended commit, then either
  push the tag by hand (the tag guard deploys it) or delete the GHCR version
  and dispatch again.

## Permissions

`GITHUB_TOKEN` with `packages: write` pushes `ghcr.io/is-koala/koala`: the
package is linked to `is-koala/koala-backend`, and the same token pushed the
`main-<sha7>` images in the self-hosted era. `contents: write` is for the tag,
`actions: read` for the verify lookup. The only repository secret used is
`DOKPLOY_DEPLOY_WEBHOOK`.
