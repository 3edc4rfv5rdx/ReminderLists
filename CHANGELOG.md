# Changelog

## Unreleased
- Dictionary entries can include a unit suffix ("Молоко /kg"): to-dictionary saves it, autocomplete fills Name and Unit, amounts display as "5/kg".
- Dictionary screen: search field at the top.
- Context menus open at the touch point; single menu component with themed colored background (also used by the autocomplete drop-down); row menus use horizontal dots.
- Swipe fixes: actions fire reliably, no stuck colored background after navigation.
- Folder and list comments moved to the bottom, under a divider; spacing before the drag handle.
- Photo module groundwork: EXIF dependency, import pipeline (resize, orientation, JPEG).
- Dictionary (TZ 3.4): management screen, name autocomplete in the item editor, "to dictionary" button on item rows; shared swipe-actions row component.
- List items (TZ 3.3): checkbox with done divider, drag-handle reorder, item editor (Name/Quantity/Unit), swipe right/left for edit/delete, long-press menu, delete-checked and uncheck-all menu actions.
- Lists tab counters: "(done/total)" after list names, "(N)" after folders; non-empty bold, fully done struck through.
- FAB pinned at one level on all screens (bottom bar height measured at startup).
- Fix top bar pushed down by a doubled status-bar inset.
- Confirmation dialogs show the object name without quotes and question mark.
- About dialog description mentions all three modules.
- Lists module: folders and lists CRUD (create/rename/comment/move/delete, alphabetical order, folder navigation in-tab), list detail placeholder screen.
- Shared UI: colored dialog buttons (primary/secondary/error), theme-colored FABs, autofocus on the first field of every input dialog.
- Project scaffold: Gradle Kotlin DSL build, Compose + Room + Coil stack (minSdk 33), three-tab shell (Lists · Reminders · Notes), full Room schema, shared UI components and reminder-engine stubs per TZ.
