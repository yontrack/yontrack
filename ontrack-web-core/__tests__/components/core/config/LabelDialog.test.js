import {colorToHex, labelColorRegex} from "@components/core/config/LabelDialog";
import {brand} from "@components/common/brand/Colors";

describe('LabelDialog colours', () => {

    /**
     * The colour a label gets when the picker is never opened. It must come from the brand
     * palette rather than antd's own blue - see issue #1814.
     */
    it('defaults to the brand gray', () => {
        expect(colorToHex(null)).toEqual(brand.colors.gray)
        expect(colorToHex(undefined)).toEqual('#E6E1E9')
    })

    it('accepts the default against the server-side format', () => {
        expect(labelColorRegex.test(colorToHex(null))).toBe(true)
    })

    /**
     * antd's ColorPicker hands over a Color object once the user picks something, and the
     * initial value stays a plain string.
     */
    it('takes a hex string through unchanged and unwraps a Color object', () => {
        expect(colorToHex('#FF0000')).toEqual('#FF0000')
        expect(colorToHex({toHexString: () => '#123456'})).toEqual('#123456')
    })
})
