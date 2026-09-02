// @ts-check

import {theme} from "antd"
import {getValidationDataTypeGlyph} from "@components/primitives/ValidationDataTypeGlyphs"
import {iconBoxStyle} from "@components/common/icons/iconBox"

/**
 * The icon a validation stamp with no uploaded image gets on a `ValidationChip`:
 * a glyph for what the stamp measures, in a neutral box.
 *
 * NEUTRAL ON PURPOSE. The chip around it carries the run state in colour; if
 * this box were tinted too, a stamp would change identity between a passing run
 * and a failing one, and the eye would lose the one thing on the chip that stays
 * put. Only the chip moves.
 *
 * @param {Object} props
 * @param {{id: any, name: string, dataType?: {descriptor?: {id?: string}}}} props.validationStamp
 * @param {number} [props.size] Box size in px.
 */
export default function ValidationStampGlyph({validationStamp, size = 22}) {

    const {token} = theme.useToken()

    // react-icons declares IconType as returning ReactNode, which TypeScript only
    // accepts as a JSX component from TS 5.1 onward - same cast as
    // `ValidationRunStatusIcon.js` makes, for the same toolchain reason.
    const Glyph = /** @type {(props: import('react-icons').IconBaseProps) => import('react').ReactElement} */ (
        getValidationDataTypeGlyph(validationStamp?.dataType?.descriptor?.id)
    )

    return (
        <span
            data-testid={`validation-stamp-glyph-${validationStamp?.id}`}
            role="img"
            aria-label={validationStamp?.name}
            style={{
                ...iconBoxStyle(size),
                borderRadius: token.borderRadiusSM,
                backgroundColor: token.colorFillQuaternary,
                color: token.colorTextSecondary,
                lineHeight: 0,
            }}
        >
            <Glyph
                size={Math.round(size * 0.62)}
                // Tabler is drawn at strokeWidth 2 on a 24px grid; at these
                // sizes the stroke effectively halves, so it is bumped - see the
                // legibility note in `ValidationRunStatusConfig`.
                strokeWidth={2.25}
                // Decorative: the accessible name is on the wrapper above.
                aria-hidden="true"
                focusable="false"
            />
        </span>
    )
}
