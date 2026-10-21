import {
    getLocalDeliveryMapVisibility,
    getLocalHighestPromotionsFormat,
    setLocalDeliveryMapVisibility,
    setLocalHighestPromotionsFormat,
} from "@components/storage/local";

describe('delivery map visibility preference', () => {

    beforeEach(() => {
        localStorage.clear()
    })

    it('has nothing turned off until the reader turns something off', () => {
        // Everything on the map is drawn by default: a map opening on a chain of promotions with no
        // visible cause hides the very thing which explains them
        expect(getLocalDeliveryMapVisibility()).toEqual({})
    })

    it('remembers what was turned off', () => {
        setLocalDeliveryMapVisibility({'validation-stamps': false})
        expect(getLocalDeliveryMapVisibility()).toEqual({'validation-stamps': false})
    })

    it('remembers what was turned back on', () => {
        setLocalDeliveryMapVisibility({'validation-stamps': false})
        setLocalDeliveryMapVisibility({'validation-stamps': true})
        expect(getLocalDeliveryMapVisibility()).toEqual({'validation-stamps': true})
    })

    it('says nothing about a kind it has never heard of', () => {
        // Which is how a kind added to the map later is drawn for everyone, rather than hidden from
        // every reader who happens to have a preference stored
        setLocalDeliveryMapVisibility({'validation-stamps': false})
        expect(getLocalDeliveryMapVisibility()['slots']).toBeUndefined()
    })

    it('falls back to showing everything for an entry it cannot read', () => {
        localStorage.setItem('delivery-map-visibility', 'not json at all')
        expect(getLocalDeliveryMapVisibility()).toEqual({})
    })

    it('falls back to showing everything for an entry which is not an object', () => {
        localStorage.setItem('delivery-map-visibility', '"yes"')
        expect(getLocalDeliveryMapVisibility()).toEqual({})
    })

})

describe('format of a highest promotions per branch widget', () => {

    beforeEach(() => {
        localStorage.clear()
    })

    it('has no format until the viewer picks one, so that the configured one applies', () => {
        expect(getLocalHighestPromotionsFormat('widget-1')).toBeUndefined()
    })

    it('remembers the format picked for each widget on its own', () => {
        setLocalHighestPromotionsFormat('widget-1', 'LADDER')
        expect(getLocalHighestPromotionsFormat('widget-1')).toBe('LADDER')
        expect(getLocalHighestPromotionsFormat('widget-2')).toBeUndefined()
    })

    it('ignores a stored value which is not a format', () => {
        localStorage.setItem('highest-promotions-format-widget-1', 'PIE')
        expect(getLocalHighestPromotionsFormat('widget-1')).toBeUndefined()
    })

})
