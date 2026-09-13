# Publishing koala to production

Verify and the GHCR image run on Yusuf's Mac. GitHub Actions does not run
`./mvnw verify` and does not build the image. The only GitHub job is the
Dokploy webhook on a `v*` tag (or `workflow_dispatch`), on the org
self-hosted runners.

## Order

1. `./mvnw verify -Dkoala.test.latch-timeout=30` on this Mac (Docker open, Temurin 25).
2. Merge the release PR to `main`.
3. `docker login ghcr.io` (a `write:packages` token that can push `is-koala/koala`).
4. `./scripts/publish-ghcr-image.sh --latest` — pushes `ghcr.io/is-koala/koala:<pom-version>` and `:latest` as a single `linux/amd64` manifest (`provenance` and `sbom` off, same GHCR-index trap as before).
5. Annotated tag `v<pom-version>` on the merge commit, then `git push origin v<pom-version>`.
6. The self-hosted job curls `DOKPLOY_DEPLOY_WEBHOOK`. Dokploy pulls `:latest`.

Do not push the tag before the image is on GHCR. The webhook does not wait for a digest; it only tells Dokploy to pull.

`main` push no longer publishes `main-<sha7>`. Production is tag-only.

To retry the webhook without rebuilding: `gh workflow run "koala ci"` (dispatch also curls; it does not require a `v*` ref).

Do not register this Mac as an org runner. Other is-koala repos may still run verify on the same self-hosted machines; this repository only uses them for the webhook.
