# Community Translation Support

Thank you for helping translate **DoubleDoors** for your language community.

## How to contribute a translation

1. Fork the repository.
2. Create or update your language file in `src/main/resources/lang/`.
   - Name must be the ISO 639-1 language code.json like en_US.json
3. Keep all message keys *exactly* the same as the source language if possible.
4. Translate only values (text shown to players).
5. Open a Pull Request with:
   - Language name
   - Locale code
   - What was translated (new language or updated existing one)

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
