import {useSyncExternalStore} from "react";

/**
 * Whether the browser runs on an Apple platform, where the palette shortcut is ⌘K rather than
 * Ctrl+K. Both work everywhere: this only says which one to show.
 */
export function isMacPlatform() {
    if (typeof navigator === 'undefined') return false
    const platform = navigator.userAgentData?.platform ?? navigator.platform ?? ''
    return /mac|iphone|ipad|ipod/i.test(platform)
}

const subscribe = () => () => {
}

/**
 * {@link isMacPlatform} as a hook: `false` on the server, so that the server rendering and the first
 * client one agree, and the real answer on the client.
 */
export function useIsMacPlatform() {
    return useSyncExternalStore(subscribe, isMacPlatform, () => false)
}
