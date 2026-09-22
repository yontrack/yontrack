/**
 * Phone detection, from the user agent alone.
 *
 * The proxy is the only place the mobile/desktop decision can be taken
 * early enough to be a redirect rather than a flash of the wrong UI, and the
 * only thing a proxy knows about the device is the `User-Agent` header.
 * Viewport width is a client-side fact and arrives far too late.
 *
 * User agent sniffing is a blunt instrument, so the rules below are written to
 * fail towards the desktop UI: it is the complete one, every route exists there,
 * and a phone user who lands on it can still switch. A tablet or a laptop
 * wrongly sent to the mobile UI would lose functionality with no obvious way
 * back.
 *
 * Tablets are deliberately *not* phones - they have the width for the desktop
 * UI. That is easier than it sounds in 2026: since iPadOS 13 an iPad's Safari
 * claims to be a Mac, so it never matches anything here; an Android tablet is
 * an `Android` UA *without* the `Mobile` token, which is exactly what
 * distinguishes it from an Android phone.
 */

/** Explicitly tablet-shaped, whatever else the string says. */
const TABLET = /\b(iPad|Tablet|PlayBook|Silk)\b/i

/** Phone platforms that name themselves. */
const PHONE_PLATFORM = /\b(iPhone|iPod|Windows Phone|IEMobile|BlackBerry|BB10|webOS|Opera Mini)\b/i

/**
 * The generic marker. `Mobi` covers Firefox and Chrome on Android and most of
 * the long tail; `Mobile` is what iOS and Chrome for Android add.
 */
const MOBILE_TOKEN = /\bMobi(le)?\b/i

/**
 * Is this user agent a phone?
 *
 * @param {string|null|undefined} userAgent The raw `User-Agent` header.
 * @returns {boolean} `true` only for phones - never for tablets, desktops, or a
 *   request with no user agent at all.
 */
export function isPhoneUserAgent(userAgent) {
    if (typeof userAgent !== 'string' || userAgent === '') return false
    // An iPhone-shaped string wins over the tablet check: nothing legitimately
    // claims both, and if something does, the smaller layout is the safer call.
    if (PHONE_PLATFORM.test(userAgent)) return true
    if (TABLET.test(userAgent)) return false
    return MOBILE_TOKEN.test(userAgent)
}
