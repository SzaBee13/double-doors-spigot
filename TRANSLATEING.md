# Community Translation Support

Thank you for helping translate **DoubleDoors** for your language community.

## Crowdin workflow

Translations are managed through Crowdin.

- Source language file: `bukkit/src/main/resources/lang/english/en_US.json`
- Crowdin config: `.crowdin.yml`
- GitHub workflow: `.github/workflows/crowdin.yml`

### Maintainer setup

Set these repository secrets before using the workflow:

- `CROWDIN_PROJECT_ID`
- `CROWDIN_PERSONAL_TOKEN`

What the workflow does:

- On push to `main`, uploads the latest source strings to Crowdin.
- On schedule (weekly) or manual run, downloads translated files and opens a pull request.
- Downloaded locales are normalized into this repository's language folder structure.

## How to contribute a translation

1. Join the Crowdin project and submit translations there.
2. Maintainers trigger or wait for the sync workflow to create a pull request.
3. Review the generated pull request and merge it.

If Crowdin is unavailable, direct pull requests with JSON translation changes are still accepted.

## Translation rules

- Do not rename, remove, or reorder keys.
- Keep placeholders unchanged (examples: `%player%`, `%world%`, `{0}`).
- Keep color-code or formatting tokens unchanged.
- Use clear, natural wording for Minecraft players.
- Stay consistent with existing terms for doors, trapdoors, and fence gates.

## Quality checklist

Before submitting, verify:

- No missing keys
- No broken placeholders
- No untranslated leftovers (unless intentional)
- File encoding is UTF-8
- Server starts without YAML errors

If you have a friend that can co-auth/verify the language it would be good.

## Testing locally

1. Build with:

   ```bash
   mvn package
   ```

2. Place the JAR in your Paper/Spigot 1.21 server.
3. Switch to your translated language.
4. Test common flows:
   - Door interaction messages
   - Redstone-related behavior messages
   - Permission/command feedback

## Need help?

If you are unsure about wording or placeholders, open a draft Pull Request and request review.  
Community translators are welcome and appreciated.
