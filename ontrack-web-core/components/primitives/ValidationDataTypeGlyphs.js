// @ts-check

import {
    TbArrowBigUpLine,
    TbChartLine,
    TbDivide,
    TbFileText,
    TbFlask,
    TbHash,
    TbPercentage,
    TbRubberStamp,
    TbShieldExclamation,
} from "react-icons/tb"

/**
 * The glyph a validation stamp falls back to when nobody uploaded an image for
 * it, derived from what the stamp MEASURES - its data type.
 *
 * Read the default first: a validation stamp with no data type at all is the
 * common case, not the exceptional one, so `DEFAULT_GLYPH` is what most users
 * will actually see. It is a rubber stamp - the thing the entity is named after
 * - and it has to look deliberate, because for most stamps it is the whole icon.
 *
 * NO GLYPH HERE MAY LOOK LIKE A RUN STATUS. `ValidationRunStatusConfig` owns the
 * marks for PASSED, FAILED, WARNING and the rest; a tick or a cross borrowed
 * into this table would let a stamp's identity be misread as its outcome. That
 * is why the default is a stamp and not a checkmark.
 *
 * Keys are data type ids with the `net.nemerosa.ontrack.extension.` prefix
 * stripped, which is the same shortening `ValidationDataType.js` and the
 * `framework/validation-data-type/` component folder already use. A data type
 * this table does not know falls back to the default, so a new backend type can
 * never break a page.
 */

/** Shared by every consumer that shortens a data type descriptor id. */
export const VALIDATION_DATA_TYPE_PREFIX = "net.nemerosa.ontrack.extension."

/** @typedef {import('react-icons').IconType} GlyphComponent */

/**
 * Glyph per known data type, keyed by shortened descriptor id.
 *
 * @type {Record<string, GlyphComponent>}
 */
export const VALIDATION_DATA_TYPE_GLYPHS = {
    // A test run: laboratory flask, not a tick.
    'general.validation.TestSummaryValidationDataType': TbFlask,
    'general.validation.ThresholdPercentageValidationDataType': TbPercentage,
    // A fraction is a division - passed over total.
    'general.validation.FractionValidationDataType': TbDivide,
    'general.validation.MetricsValidationDataType': TbChartLine,
    // CHML counts issues by severity, so a shield rather than a chart.
    'general.validation.CHMLValidationDataType': TbShieldExclamation,
    'general.validation.ThresholdNumberValidationDataType': TbHash,
    'general.validation.TextValidationDataType': TbFileText,
    // A version being pushed up.
    'av.validation.AutoVersioningValidationDataType': TbArrowBigUpLine,
}

/**
 * What a stamp with no data type gets - and, as the comment above says, what
 * most stamps get.
 *
 * @type {GlyphComponent}
 */
export const DEFAULT_VALIDATION_DATA_TYPE_GLYPH = TbRubberStamp

/**
 * Resolves a data type descriptor id to its glyph. Never returns undefined.
 *
 * @param {string} [descriptorId] Full or already-shortened descriptor id.
 * @returns {GlyphComponent}
 */
export function getValidationDataTypeGlyph(descriptorId) {
    if (!descriptorId) return DEFAULT_VALIDATION_DATA_TYPE_GLYPH
    const shortId = descriptorId.startsWith(VALIDATION_DATA_TYPE_PREFIX)
        ? descriptorId.slice(VALIDATION_DATA_TYPE_PREFIX.length)
        : descriptorId
    return VALIDATION_DATA_TYPE_GLYPHS[shortId] ?? DEFAULT_VALIDATION_DATA_TYPE_GLYPH
}
