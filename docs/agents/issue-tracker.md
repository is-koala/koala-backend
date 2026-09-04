# Issue tracker: GitHub

Issues and specifications for this repository live in GitHub Issues.

Repository: `is-koala/koala-backend`

Use the `gh` CLI for tracker operations. Run commands from this repository or pass `--repo is-koala/koala-backend` explicitly.

## Conventions

- Create: `gh issue create --title "..." --body "..."`
- Read: `gh issue view <number> --comments`
- List: `gh issue list --state open --json number,title,body,labels,comments`
- Comment: `gh issue comment <number> --body "..."`
- Apply or remove labels: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- Close: `gh issue close <number> --comment "..."`

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub Issues, rather than pull requests, are the request surface consumed by the engineering skills.

## Cross-repository work

When work also changes the mobile repository, create one issue in each repository and link them in both issue bodies. Use GitHub's native issue dependencies for blocking relationships where available. Otherwise, add `Blocked by: <issue URL>` at the top of the blocked issue.

## Skill operations

- When a skill says "publish to the issue tracker", create a GitHub issue in this repository.
- When a skill says "fetch the relevant ticket", run `gh issue view <number> --comments`.
- `to-spec`, `to-tickets`, `triage`, and `wayfinder` use this file to locate their request surface.

## Wayfinding

The map is one issue labelled `wayfinder:map`; its child tickets use `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling`, or `wayfinder:task`. Prefer native GitHub sub-issues and issue dependencies. If they are unavailable, link children from a task list in the map and record blockers with `Blocked by:` lines.
