## Repository-specific review direction

Describe only risks, architecture boundaries or kinds of changed code that
need special attention in this repository. The shared workflow already adds
the general review priorities, evidence requirements, output format and the
duplicate-implementation check; the reuse locations for that check are below
in the repository-specific guidance.

Pay particular attention to:

- Changes to indexing, reindex validation or the alias swap: a mistake here
  can leave the live index empty or stale for the frontend.
- Changes to `products.yaml` or `json-paths-config.yaml`: product IDs are a
  data contract with `ocean-current-frontend` and the upstream scanner.
- New services, controllers, utilities and constants: these are the places
  most likely to re-implement something that already exists in this
  codebase.
