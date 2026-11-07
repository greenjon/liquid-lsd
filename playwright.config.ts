import { defineConfig, devices } from '@playwright/test';

// The web TV suite: tests/web. npx playwright test (local only, nothing leaves the machine).
// Long measurements are tagged @long: npx playwright test --grep @long. Generate the music first: python3 tests/web/make_tracks.py
const nvidia = { __NV_PRIME_RENDER_OFFLOAD: '1', __GLX_VENDOR_LIBRARY_NAME: 'nvidia' };
const chromeArgs = ['--use-angle=gl', '--ignore-gpu-blocklist', '--enable-gpu', '--autoplay-policy=no-user-gesture-required', '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--disable-backgrounding-occluded-windows', '--enable-precise-memory-info'];
const launchOptions = { args: chromeArgs, env: { ...process.env, ...nvidia } as Record<string, string> };

export default defineConfig({
  testDir: './tests/web',
  outputDir: 'test-results/pw',
  fullyParallel: false,
  workers: 3,
  timeout: 120_000,
  reporter: [['list']],
  grepInvert: process.env.LONG ? undefined : /@long/,
  use: { baseURL: 'http://127.0.0.1:8765', trace: 'off' },
  webServer: { command: 'node tests/web/server.mjs', url: 'http://127.0.0.1:8765/index.html', reuseExistingServer: true, stdout: 'ignore' },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 720 }, launchOptions } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'], viewport: { width: 1280, height: 720 }, launchOptions: { firefoxUserPrefs: { 'media.autoplay.default': 0, 'media.autoplay.blocking_policy': 0 } } } },
    { name: 'webkit', use: { ...devices['Desktop Safari'], viewport: { width: 1280, height: 720 } } },
    { name: 'phone', use: { ...devices['Pixel 7'], launchOptions } },
  ],
});
