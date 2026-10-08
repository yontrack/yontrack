/**
 * Names of the stamps, an empty list when none is selected.
 */
export default function FormPrepare(value) {
    return {
        validationStamps: value?.validationStamps ?? [],
    }
}
