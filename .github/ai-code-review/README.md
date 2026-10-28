# AI code review

Advisory AI code review for pull requests. It uses the shared workflow in
[`aodn/common-workflow`](https://github.com/aodn/common-workflow/tree/main/ai-code-review).
The scripts, tests, security notes and the Kiro engine are all documented there.
This repository only holds its configuration.

## Files

| File                                     | Role                                                                                |
| ---------------------------------------- | ----------------------------------------------------------------------------------- |
| `.github/workflows/ai-code-review.yaml`  | Caller: triggers, skip rules (forks, `skip-ai-review` label), engine and credential |
| `.github/ai-code-review/prompt.md`       | Repository-specific review direction added to the shared baseline                   |
| `.github/ai-code-review/instructions.md` | Repository context, focus areas, reuse locations and conventions                    |

Both files are read from the PR head, so a PR that edits them is reviewed
with its own version. The shared workflow adds the sandbox description, the
baseline review rules (review priorities, how to judge, the
duplicate-implementation check) and the output format around them.

## Behaviour

- Runs on PRs to `main`, including drafts, when opened, pushed or reopened.
  PRs labelled `skip-ai-review` and **PRs from forks** are skipped. A new push
  cancels the in-flight review.
- **No credential** (Dependabot, or secret not set): a "skipped" comment.
  Maintainers can run it manually from _Actions → AI code review (Kiro) → Run
  workflow_ with the PR number. The shared workflow still skips fork PRs on
  manual runs.
- Errors or timeouts: a comment links to the run. The check stays green and
  never blocks merging.

## Configuration

- `KIRO_API_KEY` **secret**: a Kiro API key (needs Kiro Pro or higher).
- `KIRO_MODEL` **variable** (optional): model id. Unset uses the account default.
- **Version**: the caller uses the floating `ai-code-review-v1` tag, so each
  compatible release of the shared workflow is picked up automatically.
