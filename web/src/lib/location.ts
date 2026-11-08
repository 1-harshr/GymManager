/** Where the app is served from: "/" locally, "/GymManager/" on GitHub Pages. Always ends in a slash. */
export const BASE = import.meta.env.BASE_URL

/** The current page's path within the app, e.g. "/delete-account" wherever the app is served from. */
export function appPath(): string {
  return '/' + window.location.pathname.slice(BASE.length)
}

/** The privacy policy and terms: a plain page beside the app (public/privacy.html), readable signed out. */
export const PRIVACY_URL = BASE + 'privacy.html'

/** The app's own address, for sign-in providers to send people back to. */
export function appUrl(): string {
  return window.location.origin + BASE
}
