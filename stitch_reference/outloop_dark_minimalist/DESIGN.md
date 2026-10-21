---
name: OutLoop Dark Minimalist
colors:
  surface: '#111318'
  surface-dim: '#111318'
  surface-bright: '#37393f'
  surface-container-lowest: '#0c0e13'
  surface-container-low: '#1a1b21'
  surface-container: '#1e2025'
  surface-container-high: '#282a2f'
  surface-container-highest: '#33353a'
  on-surface: '#e2e2e9'
  on-surface-variant: '#c3c9b2'
  inverse-surface: '#e2e2e9'
  inverse-on-surface: '#2e3036'
  outline: '#8d937e'
  outline-variant: '#434937'
  surface-tint: '#9fd841'
  primary: '#fffeff'
  on-primary: '#223600'
  primary-container: '#b9f45a'
  on-primary-container: '#4a6e00'
  inverse-primary: '#466800'
  secondary: '#c3c6d3'
  on-secondary: '#2c303b'
  secondary-container: '#434752'
  on-secondary-container: '#b1b5c2'
  tertiary: '#fefff9'
  on-tertiary: '#003919'
  tertiary-container: '#6cfd9b'
  on-tertiary-container: '#007439'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#baf55b'
  primary-fixed-dim: '#9fd841'
  on-primary-fixed: '#121f00'
  on-primary-fixed-variant: '#334f00'
  secondary-fixed: '#dfe2f0'
  secondary-fixed-dim: '#c3c6d3'
  on-secondary-fixed: '#171b25'
  on-secondary-fixed-variant: '#434752'
  tertiary-fixed: '#6dfe9c'
  tertiary-fixed-dim: '#4de082'
  on-tertiary-fixed: '#00210c'
  on-tertiary-fixed-variant: '#005227'
  background: '#111318'
  on-background: '#e2e2e9'
  surface-variant: '#33353a'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  title-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-mobile: 0.75rem
  margin: 1.5rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system establishes a high-performance, distraction-free environment tailored for native Android experiences via modern Jetpack Compose paradigms. It targets discerning professionals and mobile-first power users who require absolute clarity, deliberate hierarchy, and rapid visual parsing in low-light contexts.

The design language balances functional minimalism with refined, technical precision:
- **Aesthetic Movement:** Modern Dark-Mode Minimalist infused with tactile Material 3 / Jetpack Compose architectural precision.
- **Atmospheric Intent:** Deep, pitch-adjacent depth grounded by `#111318`, punctuated by an intentional energetic lime accent (`#B9F45A`) that directs attention without producing visual fatigue.
- **Emotional Stance:** Calm, authoritative, hyper-focused, and premium. Interactions feel snappy, weighted, and structurally cohesive.

## Colors

The palette relies on nuanced luminance stepping rather than heavy drop shadows to communicate hierarchy, paired with strict functional semantic roles.

### Tonal Foundations
- **Background Root:** `#111318` — The deepest canvas layer.
- **Surface Dark (Elevated Container):** `#1A1D24` — Base layer for sheets, navigation rails, app bars, and structural frames.
- **Surface Higher (Card / Interactive Surface):** `#222630` — Elevated interactive blocks, cards, and list elements.
- **Surface Border:** `#2D323E` — Subtle structural delimitation across surfaces to maintain edge definition in deep dark modes.

### Brand & Functional Accents
- **Primary Brand Accent:** `#B9F45A` (Electric Lime) — High-contrast functional driver. Reserved for prime interactive targets, active states, key data spikes, and focused selection indicators. Text placed on this token must use the deep background `#111318`.
- **Text Primary:** `#F7F8F4` — High-emphasis typographic content (headlines, titles, critical data).
- **Text Secondary:** `#A0A5AA` — Medium-emphasis body text, labels, and secondary descriptors.
- **Text Muted:** `#686D76` — Low-emphasis placeholders, timestamps, disabled indicators, and structural metadata.

### Status Tones
- **Success:** `#4ADE80` (with `#3A9B68` reserved for low-prominence container fills).
- **Warning:** `#E7A23B` — High-visibility ambient alert tone.
- **Error:** `#F87171` — Controlled critical error and destructive trigger tone.

## Typography

The typographic hierarchy relies on **Plus Jakarta Sans** for primary human interface balance and **JetBrains Mono** for technical micro-data, telemetry, and status indicators.

- **Headlines & Display:** Set in tight, geometric weights with subtly compressed tracking to convey structural solidity and clean mobile scannability.
- **Body:** Standardized on 14px and 16px tiers to optimize legibility across varied OLED and AMOLED display sizes, enforcing strict line-height spacing for rapid comprehension.
- **Labels & Metas:** JetBrains Mono injects a deliberate instrument-panel quality to timestamps, statuses, numerical badges, and technical tags, balancing the soft curves of the primary font.

## Layout & Spacing

The layout is grounded in a flexible 8pt spatial grid built around Android edge-to-edge guidelines.

### Grid & Canvas Structure
- **Mobile (<600dp):** Single-column fluid view with `1rem` (16dp) standard margin and `0.75rem` (12dp) gutters between adjacent inline elements.
- **Tablet / Expanded Foldables (600dp - 840dp):** 8-column layout with `1.5rem` (24dp) side margins and `1rem` (16dp) gutters.
- **Desktop / Large Landscape (>840dp):** 12-column layout conforming to a centered max content container (1200dp) with `1.5rem` gutters.

### Spacing Principles
- **Internal Grouping:** Use `space-xs` (4dp) and `space-sm` (8dp) for strictly bound subcomponents (e.g., icon-to-label, badge-to-title).
- **Component Packing:** Apply `space-md` (16dp) for standard card padding and inter-item vertical stacks.
- **Section Separation:** Leverage `space-lg` (24dp) and `space-xl` (32dp) to establish macro-rhythms between unrelated functional cards and list categories.

## Elevation & Depth

In alignment with high-fidelity dark mode mechanics, depth is established through tonal layering and boundary containment rather than ambient drop shadows.

- **Level 0 (Canvas Base):** Background `#111318`. Absolute foundation.
- **Level 1 (Structural Containers):** `#1A1D24`. Used for toolbars, bottom sheets, navigation bars, and grouped modules. No shadow; separation achieved through juxtaposition against Level 0.
- **Level 2 (Interactive Cards / Dialogs):** `#222630` bounded by a 1px solid hairline border in `#2D323E`. This defines physical boundary edges without muddying the dark scene with dark blurred shadows.
- **Level 3 (Focused / Floating Components):** Floating Action Buttons (FABs) and dropdown popovers maintain Level 2 fill with an elevated outline glow: 1px border colored with 20% opacity `#B9F45A` and a faint 12dp ambient spread (`rgba(0, 0, 0, 0.45)`).

## Shapes

The design system enforces a distinctive, soft-architectural profile leveraging Compose's `CornerSize` paradigms.

- **Base Factor:** Level `2` rounded geometry, translating into a signature `rounded-2xl` (16dp / `1rem`) corner radius for primary cards, dialogs, and parent wrappers.
- **Interactive Units:** Buttons, text fields, chips, and list tiles inherit a consistent `12dp` to `16dp` curve to feel comfortable under direct touch interaction.
- **Pills / Micro Tags:** Fully rounded pill shapes (`9999px`) are exclusively reserved for micro chips, filter tags, and state badges.

## Components

### Buttons
- **Primary:** Filled with `#B9F45A`, text in `#111318` (`fontWeight: 600`), corner radius `16dp`, vertical padding `12dp`, horizontal padding `24dp`. Ripple effect tinted to `rgba(17, 19, 24, 0.2)`.
- **Secondary / Tonal:** Filled with `#222630`, 1px border in `#2D323E`, text in `#F7F8F4`.
- **Ghost:** Transparent background, text in `#A0A5AA`, active state highlights in `#1A1D24`.

### Input Fields
- **Container:** Background `#1A1D24`, 1px border `#2D323E`, height 56dp, corner radius `16dp`, padding `16dp`.
- **States:**
  - *Rest:* Text `#F7F8F4`, placeholder `#686D76`.
  - *Focused:* Border transitions to `#B9F45A` with a 1px hairline stroke. Label transitions to `#B9F45A`.
  - *Error:* Border switches to `#F87171` with accompanying helper text in `#F87171`.

### Cards
- **Base Surface:** Background `#222630`, 1px solid border in `#2D323E`, corner radius `16dp` (`rounded-2xl`), padding `16dp`.
- **Interactive Variant:** Incorporates a subtle touch-down state stepping the background to `#2A2F3C` with zero elevation shift.

### Chips & Filter Tags
- **Filter Chip:** Height 32dp, pill radius (`9999px`), background `#1A1D24`, border 1px `#2D323E`, label in `JetBrains Mono` 12dp (`#A0A5AA`).
- **Selected State:** Background `#B9F45A`, border `#B9F45A`, label text `#111318` (`fontWeight: 600`).

### Checkboxes & Radios
- **Checkbox:** 20x20dp, corner radius 6dp, unselected border 1.5px `#2D323E`. Selected state fill `#B9F45A` with `#111318` checkmark glyph.
- **Radio Button:** 20x20dp circular ring. Selected state outer border `#B9F45A` housing an inner dot `#B9F45A` (8dp diameter).

### Lists
- **Item Container:** Minimum height 56dp, padding 12dp vertical, 16dp horizontal. Separated by `space-sm` gaps or hairline dividers in `#1A1D24`.
- **Content Distribution:** Left icon/avatar contained in `#1A1D24` shape, middle stack using `title-md` (`#F7F8F4`) over `body-sm` (`#A0A5AA`), right trailing edge occupied by `JetBrains Mono` label (`#686D76`).