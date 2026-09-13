# Publishing koala to production

Verify and the GHCR image run on Yusuf's Mac. GitHub Actions does not run
`./mvnw verify` and does not build the image. The only GitHub job is the
Dokploy webhook on a `v*` tag, on `ubuntu-latest`.

## Order

1. `./mvnw verify -Dkoala.test.latch-timeout=30` on this Mac (Docker open, Temurin 25).
2. Merge the release PR to `main`.
3. `docker login ghcr.io` (a `write:packages` token for `yustyy`).
4. `./scripts/publish-ghcr-image.sh --latest` — pushes `ghcr.io/yustyy/koala:<pom-version>` and `:latest` as a single `linux/amd64` manifest (`provenance` and `sbom` off, same GHCR-index trap as before).
5. Annotated tag `v<pom-version>` on the merge commit, then `git push origin v<pom-version>`.
6. GitHub curls `DOKPLOY_DEPLOY_WEBHOOK`. Dokploy pulls `:latest`.

Do not push the tag before the image is on GHCR. The webhook does not wait for a digest; it only tells Dokploy to pull.

`main` push no longer publishes `main-<sha7>`. Production is tag-only.

To retry the webhook without rebuilding: `gh workflow run "koala ci" --ref v<version>` (the job's `if` still requires a `v*` ref).

Other is-koala repos may still use the org self-hosted runner. This repository does not.
