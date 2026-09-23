# S-UI Node visual system (2.2)

The shared interface has been rebuilt around Material 3 controls and a graphite / porcelain
palette with lime and soft teal accents. Wallpaper-derived accent colors remain available
on Android 12 and newer.

- One translucent rounded layer per card, with a fine edge. The previous nested specular
  rectangles and shadow stack are removed.
- A stationary refracted-light backdrop and faint dot grid. Navigation, presses and control
  changes animate; the background does not keep rendering while the app is idle.
- A compact panel switcher, persistent four-item navigation with labels, and larger page
  headings. Back buttons appear only where they can navigate back.
- The overview combines connection status and counts in one hero, followed by current
  traffic, resource meters, shortcuts and optional diagnostics.
- Clients have persistent search and a New client action. Client identity has a full row;
  secondary actions no longer compete with the name. Enable/disable requires confirmation.
- Tools are organized into a network grid, configuration group and management group.
- Editors share outlined text inputs, Material switches, segmented tabs, sheets and dialogs.
  Protocol configuration logic and APIv2 request formats remain independent of the styling.
- Destructive quick actions for client state, cloning and dashboard core restart now require
  explicit in-app confirmation.

Android 11 uses translucent gradients rather than Android 12-only background blur APIs.
This keeps the design usable on the connected Mi 9T Pro without a continuously rendered effect.
