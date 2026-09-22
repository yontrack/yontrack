/**
 * The Next.js Image Optimization API stays off (#1789, #1787).
 *
 * A deliberate choice, not a stop-gap. It was turned off against
 * GHSA-2xp9-vwfh-vxw4, an unauthenticated RCE in `/_next/image`, and it stays
 * off after the Next.js 16 upgrade that fixes it: the UI serves no image that
 * needs resizing, so the optimizer is attack surface for no benefit. With
 * `images.unoptimized`, Next answers that endpoint with a 404 instead of
 * running the optimizer. This test keeps it from being turned back on by
 * accident.
 */
import nextConfig from "../../../next.config"

describe('next.config.js images', () => {

    it('turns the image optimizer off (GHSA-2xp9-vwfh-vxw4)', () => {
        expect(nextConfig.images?.unoptimized).toBe(true)
    })

})
