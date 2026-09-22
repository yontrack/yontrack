// antd 6 observes the size of more of its components (Select, Tabs, Space.Compact...) than antd 5
// did, through `ResizeObserver`, which jsdom does not implement (#1852). Nothing under test depends
// on a resize ever being reported, so an observer that never fires is enough.
if (typeof window !== 'undefined' && !window.ResizeObserver) {
    window.ResizeObserver = class ResizeObserver {
        observe() {
        }

        unobserve() {
        }

        disconnect() {
        }
    }
}

// `@rc-component/form` (antd 6's `Form`) defers its watchers to a macro task through a
// `MessageChannel`, which jest-environment-jsdom does not expose. A timeout is the same macro task.
if (typeof window !== 'undefined' && !window.MessageChannel) {
    window.MessageChannel = class MessageChannel {
        constructor() {
            this.port1 = {onmessage: null}
            this.port2 = {
                postMessage: (data) => setTimeout(() => this.port1.onmessage?.({data}), 0),
            }
        }
    }
}
