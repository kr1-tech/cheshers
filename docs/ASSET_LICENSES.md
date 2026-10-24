# Original asset licenses

Unless a third-party notice says otherwise, original Cheshers visual and audio assets are dedicated to the public domain under **CC0 1.0 Universal**.

You may copy, modify, distribute, and use them for any purpose without asking permission. No attribution is required, though credit to Cheshers is appreciated. CC0 legal text: <https://creativecommons.org/publicdomain/zero/1.0/legalcode>.

## Included original assets

- `app/src/main/res/drawable/ic_launcher.xml`: original vector knight/launcher artwork.
- `app/src/main/res/mipmap-*`: launcher resources derived from that original artwork.
- `feature-game/src/main/res/raw/move.wav`: original procedurally synthesized move effect.
- `feature-game/src/main/res/raw/capture.wav`: original procedurally synthesized capture effect.
- `feature-game/src/main/res/raw/castle.wav`: original procedurally synthesized castling effect.
- `feature-game/src/main/res/raw/check.wav`: original procedurally synthesized check effect.
- `feature-game/src/main/res/raw/game_end.wav`: original procedurally synthesized game-end effect.
- The Cheshers name, original interface layout, color palettes, and written onboarding/puzzle text.

The five WAV files were generated specifically for this project; no sampled recording or third-party sound library is included. The 12 bundled chess puzzles are simple positions created/curated for Cheshers and are not copied from a commercial puzzle service.

## System rendering

Chess pieces are rendered with Unicode chess glyphs and platform font families. No third-party piece-set image pack or font file is bundled. Glyph appearance depends on the device's system fonts. Material icons come from the AndroidX core icon library and are covered by its Apache-2.0 license.

Project source remains AGPL-3.0-or-later under the root `LICENSE`; CC0 applies to the original assets listed here, not to third-party libraries.
