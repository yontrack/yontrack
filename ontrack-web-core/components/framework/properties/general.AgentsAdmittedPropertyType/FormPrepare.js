/**
 * The property is set to admit agents unless the switch was explicitly turned off.
 */
export default function FormPrepare(value) {
    return {
        admitted: value?.admitted ?? true,
    }
}
