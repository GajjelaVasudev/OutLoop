---
name: OutLoop Design System
colors:
  surface: '#f9faf6'
  surface-dim: '#d9dad7'
  surface-bright: '#f9faf6'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f0'
  surface-container: '#edeeea'
  surface-container-high: '#e7e9e5'
  surface-container-highest: '#e2e3df'
  on-surface: '#1a1c1a'
  on-surface-variant: '#434937'
  inverse-surface: '#2e312f'
  inverse-on-surface: '#f0f1ed'
  outline: '#737a65'
  outline-variant: '#c3c9b2'
  surface-tint: '#466800'
  primary: '#466800'
  on-primary: '#ffffff'
  primary-container: '#b9f45a'
  on-primary-container: '#4a6e00'
  inverse-primary: '#9fd841'
  secondary: '#5d5e64'
  on-secondary: '#ffffff'
  secondary-container: '#dfdfe6'
  on-secondary-container: '#616269'
  tertiary: '#006d41'
  on-tertiary: '#ffffff'
  tertiary-container: '#96f6bb'
  on-tertiary-container: '#007345'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#baf55b'
  primary-fixed-dim: '#9fd841'
  on-primary-fixed: '#121f00'
  on-primary-fixed-variant: '#334f00'
  secondary-fixed: '#e2e2e9'
  secondary-fixed-dim: '#c6c6cd'
  on-secondary-fixed: '#1a1b21'
  on-secondary-fixed-variant: '#45474c'
  tertiary-fixed: '#96f6bc'
  tertiary-fixed-dim: '#7adaa1'
  on-tertiary-fixed: '#002110'
  on-tertiary-fixed-variant: '#005230'
  background: '#f9faf6'
  on-background: '#1a1c1a'
  surface-variant: '#e2e3df'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
    letterSpacing: -0.03em
  display-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  title-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.01em
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.03em
  label-caps:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.06em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system powers a modern, mindful productivity experience for Android. Rooted in the ethos of intentional engagement, it helps users disengage from compulsive digital patterns while preserving meaningful human and workflow connections.

### Personality & Tone
- **Composed & Restrained:** Never shout, guilt, or alarm. The system communicates clarity and calm control.
- **Intentional & Kinetic:** Interactions feel deliberate, crisp, and tactile, using high-impact accents only to highlight deliberate user intent.
- **Empathetic & Non-Judgmental:** Avoid scolding visual tropes (heavy reds, alert banners). Progress is framed through calm neutrality and constructive momentum.

### Visual Style
A hybrid of **Modern Material 3** and **Precise Minimalism**. The aesthetic relies on expansive, high-key light surfaces, stark near-black structural anchors, razor-thin structural borders (`#E4E7DF`), and a piercing, vibrant lime-green kinetic punch (`#B9F45A`). Elevation avoids heavy skeuomorphic drop shadows, opting instead for tonal card differentiation, surface layering, and crisp 1px borders.

## Colors

The palette balances serene, non-fatiguing backgrounds with sharp typography and an energetic focal accent.

### Palette Architecture
- **Primary Accent (`#B9F45A`):** Used selectively for focal call-to-action touchpoints, active states, timer rings, and progress highlights. Text placed over this lime background must use `#111318` for contrast compliance.
- **Dark Neutral & Surfaces (`#111318`, `#1A1D24`):** Represents high-priority contrast containers, primary action targets (when unaccented), deep navigation surfaces, and primary typography.
- **Light Surfaces (`#F7F8F4`, `#FFFFFF`):** Base canvas utilizes `#F7F8F4` to prevent harsh OLED screen glare. Individual content modules sit on elevated `#FFFFFF` cards.
- **Subtle Borders (`#E4E7DF`):** Defines boundaries between surfaces without creating cognitive clutter.
- **Functional Semantics:**
  - **Success (`#3A9B68`):** Goal completion, streaks, and healthy states.
  - **Warning (`#E7A23B`):** Threshold boundaries, impending limits, and gentle warnings.
  - **Danger (`#D9534F`):** Destructive actions and broken limits.
  - **Secondary Text (`#72766F`):** Accessible, subdued metadata and supporting body copy.

## Typography

The design system implements **Plus Jakarta Sans** across all hierarchies to infuse geometric structure with warm, humanistic terminals.

### Typographic Rhythm
- Numbers, metrics, and timers leverage bold weights (`700`, `800`) with tight letter-spacing for immediate glanceability.
- Body text prioritizes legibility with deliberate vertical line-height ratios (1.4x to 1.5x) to maintain effortless scanning during mobile sessions.
- Small contextual labels and badges use `label-caps` with uppercase transformation and tracking (`+0.06em`) to anchor floating metadata without commanding primary focus.

## Layout & Spacing

Engineered specifically for Android viewport architectures (typical 390dp to 412dp form factors), adhering to modern edge-to-edge system window guidelines.

### Layout Principles
- **Edge-to-Edge:** Scaffold stretches fully beneath system status bars and navigation gesture pills with custom inset padding handling.
- **Rhythm:** An 8dp baseline grid governs macro layout structures, supported by a 4dp sub-grid for icons, micro badges, and chip spacing.
- **Margins & Gutters:** Outer horizontal margin is fixed to `1.25rem` (20dp) on standard mobile portrait displays to balance content density with edge thumb safety. Internal grid gaps maintain `1rem` (16dp).

## Elevation & Depth

Visual hierarchy uses **tonal layer differentiation** and **hairline containment** rather than heavy blurred drop shadows.

### Elevation Hierarchy
- **Level 0 (Canvas Base):** Ground layer tinted at `#F7F8F4`.
- **Level 1 (Card Containers & Inset Modules):** Pure white (`#FFFFFF`) surfaces defined with a single crisp `1px solid #E4E7DF` boundary. No ambient drop shadow.
- **Level 2 (Interactive Floating Elements & Snackbars):** Near-black (`#111318`) or pure white cards elevated via an ambient blur shadow: `0px 8px 24px rgba(17, 19, 24, 0.06)`, retaining the `1px` structural boundary border.
- **Level 3 (Modal Bottom Sheets):** Elevated from base screen with `0px -4px 32px rgba(17, 19, 24, 0.12)`, anchored with rounded top edges.

## Shapes

The shape system adopts smooth, organic corners that mirror native Android Material 3 continuous corners.

### Shape Scaling
- **Cards & Primary Modules:** 16px to 24px (`rounded-lg` to `rounded-xl`) corner radius creates distinct, thumb-friendly touch targets.
- **Buttons & Interactive Controls:** Standard buttons adhere to 14px-16px corner radiuses or full circular pills depending on layout context.
- **Status Badges & Pill Chips:** Fully circular pill shapes (`9999px`) provide contrast against rectangular cards.
- **Modal Sheets:** 28px top corner radius for bottom sheet surfaces.

## Components

### Buttons
- **Primary Kinetic Button:**
  - Height: `50px`.
  - Background: Primary lime green (`#B9F45A`).
  - Text/Icon: `#111318`, `label-lg` (bold).
  - Shape: 16px radius or full pill. Border: None.
- **Secondary Neutral Button:**
  - Height: `50px`.
  - Background: `#111318`.
  - Text: `#FFFFFF`.
  - Shape: 16px radius.
- **Outlined / Tertiary Button:**
  - Height: `48px`.
  - Background: Transparent or `#FFFFFF`.
  - Border: `1px solid #E4E7DF`.
  - Text: `#171A18`.

### Chips & Badges
- **Pill Badge:**
  - Height: `28px`.
  - Internal Padding: `0 12px`.
  - Background: Primary accent tint (`rgba(185, 244, 90, 0.2)`) with `#171A18` text, or subtle light grey (`#E4E7DF`) with `#72766F` text.
  - Border: Hairline border matching foreground token.

### Cards & Modules
- **Standard Card:**
  - Surface: `#FFFFFF`.
  - Border: `1px solid #E4E7DF`.
  - Radius: `20px`.
  - Padding: `16px` to `20px`.
- **Active Focus Card:**
  - Surface: `#111318`.
  - Text: `#FFFFFF`.
  - Border: `1px solid #1A1D24`.
  - Accent: `#B9F45A` icons or action rings.

### Inputs & Fields
- **Text Input Field:**
  - Height: `54px`.
  - Surface: `#FFFFFF`.
  - Border: `1px solid #E4E7DF` resting; `2px solid #111318` focused.
  - Radius: `14px`.
  - Typography: `body-lg` in `#171A18`, placeholder in `#72766F`.

### Selection Controls
- **Checkboxes & Radios:**
  - Size: `22px x 22px`.
  - Active fill: `#111318` or `#B9F45A`.
  - Inactive stroke: `2px solid #E4E7DF`.
- **Toggle Switch:**
  - Track: Width `52px`, Height `30px`, Pill radius.
  - Inactive Track: `#E4E7DF`; Active Track: `#111318`.
  - Thumb: `24px` circular token, resting `#FFFFFF`, active `#B9F45A`.

### Lists & Navigation
- **List Items:** Separated with 8dp gap instead of hard inset divider lines, or contained inside single contiguous parent cards with `1px solid #E4E7DF` row delimiters.
- **Bottom Navigation Bar:** Surface `#FFFFFF` or translucent `#F7F8F4` blur; active indicator utilizes `#B9F45A` pill capsule behind dark icons.