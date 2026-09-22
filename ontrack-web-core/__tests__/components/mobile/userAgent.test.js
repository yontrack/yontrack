import {isPhoneUserAgent} from "@components/mobile/userAgent"

/*
 * Real user agent strings, kept verbatim. A hand-simplified UA would let a
 * regression through: the whole point of these is the shape of the strings the
 * proxy actually sees.
 */
const PHONES = {
    'iPhone Safari': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1',
    'iPhone Chrome': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/123.0.6312.52 Mobile/15E148 Safari/604.1',
    'Android Chrome': 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36',
    'Android Firefox': 'Mozilla/5.0 (Android 14; Mobile; rv:124.0) Gecko/124.0 Firefox/124.0',
    'Android Samsung Internet': 'Mozilla/5.0 (Linux; Android 13; SAMSUNG SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/23.0 Chrome/115.0.0.0 Mobile Safari/537.36',
    'iPod touch': 'Mozilla/5.0 (iPod touch; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1',
}

const NOT_PHONES = {
    'macOS Safari': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15',
    'Windows Chrome': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
    'Linux Firefox': 'Mozilla/5.0 (X11; Linux x86_64; rv:124.0) Gecko/20100101 Firefox/124.0',
    // Tablets keep the desktop UI: they have the width for it.
    'iPad (old UA)': 'Mozilla/5.0 (iPad; CPU OS 15_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.6 Mobile/15E148 Safari/604.1',
    // Since iPadOS 13 an iPad claims to be a Mac, which lands on the desktop UI
    // without any help from us. Pinned so the intent is on the record.
    'iPad (desktop-class UA)': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15',
    // An Android tablet is an Android UA *without* the `Mobile` token.
    'Android tablet': 'Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
    'Kindle Fire': 'Mozilla/5.0 (Linux; U; Android 9; en-us; KFONWI Build/PS7317) AppleWebKit/537.36 (KHTML, like Gecko) Silk/119.3 like Chrome/119.0.0.0 Safari/537.36',
}

describe('isPhoneUserAgent', () => {

    Object.entries(PHONES).forEach(([name, ua]) => {
        it(`recognises ${name} as a phone`, () => {
            expect(isPhoneUserAgent(ua)).toBe(true)
        })
    })

    Object.entries(NOT_PHONES).forEach(([name, ua]) => {
        it(`does not treat ${name} as a phone`, () => {
            expect(isPhoneUserAgent(ua)).toBe(false)
        })
    })

    it('treats a missing user agent as not a phone', () => {
        // Erring towards the desktop UI: it is the complete one, and a
        // header-less client (a probe, a crawler, curl) is not a phone.
        expect(isPhoneUserAgent(undefined)).toBe(false)
        expect(isPhoneUserAgent(null)).toBe(false)
        expect(isPhoneUserAgent('')).toBe(false)
    })
})
