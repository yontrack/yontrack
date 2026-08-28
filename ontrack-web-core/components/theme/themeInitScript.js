/**
 * The script that decides the theme before anything is painted.
 *
 * It runs inline in `<head>`, ahead of React, of the stylesheets' first use and
 * of any GraphQL round trip, and stamps `data-theme` on `<html>`. Every colour
 * in `globals.css` keys off that attribute, so the first paint is already in the
 * right theme - no flash.
 *
 * It has to be a string: it is injected inline, so it cannot import anything.
 * That means the cookie name and the mode vocabulary are duplicated from
 * `themeMode.js` - the tests in `themeInitScript.test.js` pin them together.
 *
 * Written defensively and wrapped in try/catch: this runs before everything
 * else, and a throw here would leave the page unstyled rather than merely
 * mis-themed.
 */
import {THEME_COOKIE_NAME} from "@components/theme/themeMode"

export const themeInitScript = `
(function () {
    try {
        var name = '${THEME_COOKIE_NAME}';
        var match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        var mode = match ? decodeURIComponent(match[1]).toLowerCase() : 'system';
        if (mode !== 'light' && mode !== 'dark') mode = 'system';
        var dark = mode === 'dark' || (
            mode === 'system' &&
            typeof window.matchMedia === 'function' &&
            window.matchMedia('(prefers-color-scheme: dark)').matches
        );
        var theme = dark ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', theme);
        document.documentElement.style.colorScheme = theme;
    } catch (e) {
        document.documentElement.setAttribute('data-theme', 'light');
    }
})();
`
