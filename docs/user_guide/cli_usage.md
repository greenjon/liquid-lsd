# Command Line Usage & Startup Options

Liquid LSD supports command-line startup options for headless automated capture, window resolution overrides, audio engine bypassing, and launching the isolated UI Lab sandbox.

## Quick Syntax

```bash
./gradlew run --args="[OPTIONS]"
```

Or when launching packaged executables:
```bash
./liquid-lsd [OPTIONS]
```

## Available Startup Options

### 1. Automated UI Screenshot Capture
Capture a crisp PNG screenshot of the application window and exit automatically:
```bash
./gradlew run --args="--screenshot-ui=docs/assets/app-1080p.png --window=1920x1080 --screenshot-after-frames=5 --no-audio"
```

- `--screenshot-ui=<file.png>`: Sets the target PNG image path.
- `--screenshot-after-frames=<N>`: Sets the frame delay before capture (default: 5 frames) to allow UI layouts and font metrics to settle.

### 2. Window Resolution Override
Override startup window dimensions:
```bash
./gradlew run --args="--window=1280x720"
```
Or start in maximized mode:
```bash
./gradlew run --args="--window=maximized"
```

### 3. Audio Engine Bypass
Bypass JACK and JavaSound audio engine initialization for environments without physical or virtual sound cards:
```bash
./gradlew run --args="--no-audio"
```

### 4. UI Lab Component Sandbox
Launch an isolated UI component gallery sandbox to preview theme colors, Lucide icons, custom sliders, and status meters:
```bash
./gradlew run --args="--ui-lab --window=1280x720 --no-audio"
```

### 5. Automated Gradle Tasks
Liquid LSD includes pre-configured Gradle tasks for automated asset generation:
- `./gradlew captureResponsiveApp`: Captures 1080p main workspace screenshot.
- `./gradlew captureUiLab`: Captures UI Lab gallery screenshot.
