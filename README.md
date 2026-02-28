# Tap to Scroll

An Android accessibility service that converts tap gestures into scroll actions within selected apps.

## Features

- **Tap-to-Scroll**: Tap in designated screen zones to scroll up or down without swiping
- **Customizable Zones**: Choose from edge zones, side zones, or corner zones
- **Smart Detection**: Automatically avoids scrolling when tapping on interactive elements (links, buttons)
- **Per-App Configuration**: Enable tap-to-scroll only in apps you choose
- **Adjustable Settings**: Configure scroll distance, speed, and direction

## Zone Layouts

### Edge Zones (Default)
- Tap the top 15% of the screen to scroll up
- Tap the bottom 15% of the screen to scroll down

### Side Zones
- Tap the left 20% of the screen to scroll up
- Tap the right 20% of the screen to scroll down

### Corner Zones
- Tap top-left or top-right corners to scroll up
- Tap bottom-left or bottom-right corners to scroll down

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Build Steps

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build the project: `./gradlew assembleDebug`
5. Install on device: `./gradlew installDebug`

### Command Line Build

```bash
cd TapToScroll
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Installation

1. Build and install the APK on your device
2. Open the app and tap "Enable" on the service status banner
3. In Android Accessibility Settings, find "Tap to Scroll" and enable it
4. Return to the app and configure your preferred settings

## Usage

1. Add apps where you want tap-to-scroll enabled (Brave Browser is added by default)
2. Choose your preferred zone layout
3. Adjust scroll distance and speed to your liking
4. Open one of your configured apps and tap in the scroll zones!

## Permissions

- **Accessibility Service**: Required to detect taps and inject scroll gestures
- **Vibrate**: For haptic feedback when scrolling
- **Query All Packages**: To show list of installed apps in the app selector

## Project Structure

```
app/src/main/java/com/tapscroll/
├── MainActivity.kt              # Main entry point
├── TapScrollApplication.kt      # Application class
├── data/
│   ├── Models.kt               # Data classes
│   └── PreferenceStore.kt      # DataStore preferences
├── service/
│   ├── TapScrollService.kt     # Accessibility service
│   └── GestureProcessor.kt     # Tap processing logic
├── ui/
│   ├── SettingsScreen.kt       # Main settings UI
│   ├── SettingsViewModel.kt    # ViewModel
│   ├── components/             # Reusable UI components
│   └── theme/                  # Material 3 theme
└── util/
    ├── ZoneCalculator.kt       # Zone boundary calculations
    └── NodeTreeHelper.kt       # Accessibility node utilities
```

## Technical Details

### How It Works

1. The accessibility service creates a transparent overlay over the screen
2. When a tap is detected, it checks if the tap location is in a scroll zone
3. If in a zone, it queries the accessibility node tree to check for interactive elements
4. If no interactive element is found, it injects a scroll gesture using `dispatchGesture()`
5. If an interactive element is found, the tap passes through normally

### Interactive Element Detection

The service checks for:
- `isClickable`, `isCheckable`, `isEditable` properties
- `ACTION_CLICK` in the node's action list
- Known interactive class names (Button, EditText, etc.)
- Web content role descriptions (link, button, etc.)

## Troubleshooting

### Service Not Working
- Ensure the accessibility service is enabled in Android Settings
- Check that the target app is in your active apps list and enabled
- Try restarting the service by toggling it off and on

### Taps Not Registering
- Make sure you're tapping within the configured zones
- Check that the zone layout matches your expectations
- Try increasing the zone size in settings

### Scrolling When Tapping Links
- Ensure "Avoid interactive elements" is enabled
- Some web content may not properly expose accessibility info

## License

MIT License - See LICENSE file for details

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.
