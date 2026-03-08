# Apple UI Master Guidelines for Simple Mobile Interfaces

## Goal
Use Apple UX patterns as a practical design framework for building interfaces that feel simple, predictable, and low-friction, without copying visual style blindly.

## 1) Core Principles (what to preserve)
- Clarity: every screen should answer immediately: where am I, what can I do, what is primary.
- Deference: interface chrome should not compete with content.
- Depth: layering and motion should explain hierarchy and transitions, not decorate.
- Consistency: same interaction = same outcome across the app.

Design rule:
- If an element does not help task completion, remove it.

## 2) Navigation Patterns (when to use each)
- Hierarchical flow: drill-down journeys (list -> detail -> action).
- Flat/tab flow: 3-5 primary destinations users switch between frequently.
- Content-driven flow: browsing/exploration interfaces (feeds, galleries, logs).

Guardrails:
- Keep tab bar to 3-5 items.
- View title describes the current context, not the app name.
- Preserve back behavior and standard system gestures.

## 3) Visual System for Simplicity
- Typography first: strong hierarchy via size/weight, not many colors.
- Semantic colors: success/warning/error/action with role-based meaning.
- High contrast by default; never rely only on color to communicate state.
- Icons as support, not as the only label.

Practical checks:
- Primary action is obvious in under 2 seconds.
- Only one dominant visual focus per screen.

## 4) Interaction and Feedback
- Touch targets: minimum 44x44pt (larger for critical actions).
- Motion: short, purposeful transitions to maintain orientation.
- Haptics/sound: only for high-value confirmation or error, not every tap.
- Loading states: always show progress/placeholder for operations >300ms.

## 5) Error UX (important for perceived simplicity)
- Error messages must be localized and user-facing text must be actionable.
- Use consistent error taxonomy (same wording for same failure class).
- Offer immediate recovery path: retry, edit input, or fallback action.

Good pattern:
- "Authentication error" + next step.
Bad pattern:
- raw backend/network exception shown directly to users.

## 6) Accessibility as baseline, not optional
- Dynamic text should not break layout.
- VoiceOver labels/traits for all interactive controls.
- Respect reduce-motion preferences.
- Provide alternatives to gesture-only paths.

## 7) Applying Apple Patterns Without Copying
Adopt:
- interaction predictability
- hierarchy discipline
- semantic feedback
- accessibility defaults

Avoid copying blindly:
- decorative effects that add no task value
- over-animated transitions
- visual style mismatched with product identity

## 8) "Simple UI" Heuristics for Product Teams
Use this before shipping each screen:
- Can a new user complete the main task without tutorial?
- Is there exactly one clear primary action?
- Are destructive actions explicit and safely separated?
- Are empty/error/loading states clear and localized?
- Does the screen still work with larger text/accessibility settings?

If 2+ answers are "no", redesign before release.

## 9) Suggested Adoption Order (for real impact)
1. Normalize navigation and screen hierarchy.
2. Standardize error/localization patterns.
3. Improve loading/feedback and action affordances.
4. Strengthen accessibility and dynamic type resilience.
5. Refine visual polish and motion last.

This order maximizes user value and avoids vanity design work.
