# 0008. Design from tokens, no prototype

**Status:** Accepted (2026-09-26)

## Context
The original project brief referred to a `prototype/index.html` as the visual reference, but no prototype exists.

## Decision
- The design spec is CLAUDE.md section 8: the design tokens (colors for light and dark, spacing, radii, shadow, type scale), the contrast rule, and the written page-structure list.
- The `prototype/` directory is removed from the repository layout.
- Components are built from shadcn/ui primitives restyled with the tokens as CSS variables. Empty states use flat token colors, not decorative art.

## Consequences
- The UI is defined by text and tokens that live in the repo. Changes to look and feel happen in one token file.
- If a prototype or Figma file is produced later, a new ADR adopts it as a reference and this one is superseded.
