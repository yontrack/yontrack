/**
 * The restriction is on unless the switch was explicitly turned off.
 */
export default function FormPrepare(value) {
    return {
        enabled: value?.enabled ?? true,
    }
}
