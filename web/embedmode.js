// ?embed: the picture only, for hosting inside another app (Spaz Radio's Android WebView). No TV body,
// OSD or menu, no stream or microphone: the host app plays the radio and pushes its audio in with
// window.LSD.feed(). See embed.js for the API.
export const embed = typeof location !== 'undefined' && new URLSearchParams(location.search).has('embed');
