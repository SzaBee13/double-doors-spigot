# Contributing

Thanks for your interest in contributing to DoubleDoors.

## Before You Start

- Check existing issues to avoid duplicates.
- For larger changes, open an issue first to discuss the proposal.
- Be respectful and follow `CODE_OF_CONDUCT.md`.

## Development Setup

Requirements:

- Java 21
- Maven

Build locally:

```bash
mvn -DskipTests package
```

## Branch and PR Workflow

- Create a focused branch for each change.
- Keep pull requests small and scoped.
- Fill out the pull request template completely.
- Reference related issues in the PR description.

## Coding Guidelines

- Keep behavior changes intentional and documented.
- Prefer clear naming and minimal complexity.
- Update docs/config comments when behavior changes.

## Reporting Bugs and Requesting Features

Use the issue templates in `.github/ISSUE_TEMPLATE/`.

Current labels used by this repository:

- `feature request`
- `bug`
- `documentation`
- `duplicate`
- `good first issue`
- `question`
- `translation`

## Testing Expectations

- Verify plugin behavior on a supported server build.
- Include clear reproduction steps for bug fixes.
- Mention manual test steps in your PR when relevant.

## License

By contributing, you agree that your contributions are licensed under the same
license as this project.
