# AGENTS.md

Treat each `demos/<demo-name>/` as an independent project root:

- Keep all project files and repository-local dependencies inside the demo root. Run setup, build, test, format, and validation there without repository-root scripts or configuration.
- Declare and lock third-party packages in demo-owned manifests; use no undeclared global or repository-external local packages.
- Keep local links inside the demo. Documentation may escape, directly or through symlinks, only to a versioned OCDD specification under the repository-root `spec/`, as its single read-only specification source; never reference sibling demos.
- Before finishing, verify these boundaries for every changed path, project dependency, and escaping documentation link.
