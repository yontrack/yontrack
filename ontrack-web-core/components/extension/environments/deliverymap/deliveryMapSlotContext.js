import {createContext} from "react"

/**
 * What a slot checkpoint does when its name is activated, read from context rather than carried in
 * the checkpoint.
 *
 * The registry dispatches to a checkpoint's component with the checkpoint and nothing else - that
 * signature is what lets an extension contribute a kind the core has never heard of - so a handler
 * cannot be passed down to it. Context is the same answer `SlotGraphNodeContext` gives on the
 * project slot graph, and for the second reason it gives too: React Flow builds its nodes once, in
 * the effect which lays them out, and a handler closed over at that moment would keep writing a
 * stale router query back over the current one. A context is read at render time and cannot go
 * stale.
 *
 * The default makes the name inert, which is what a checkpoint rendered outside the map wants.
 */
export const DeliveryMapSlotContext = createContext({onSlotClick: undefined})
