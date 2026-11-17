/**
 * Text of a body which is not valid JSON, kept as it was typed so that the form can refuse it.
 */
export class InvalidHttpBody {
    constructor(text) {
        this.text = text
    }
}

/**
 * Reads the text of a body as a JSON value: `null` when blank, an `InvalidHttpBody` when it
 * cannot be parsed.
 */
export const parseHttpBody = (text) => {
    if (!text || !text.trim()) {
        return null
    }
    try {
        return JSON.parse(text)
    } catch {
        return new InvalidHttpBody(text)
    }
}

/**
 * Text to edit for a body value.
 */
export const formatHttpBody = (value) => {
    if (value === null || value === undefined) {
        return ''
    } else if (value instanceof InvalidHttpBody) {
        return value.text
    } else {
        return JSON.stringify(value, null, 2)
    }
}

/**
 * Form rule refusing a body which is not valid JSON.
 */
export const httpBodyRule = {
    validator: async (_, value) => {
        if (value instanceof InvalidHttpBody) {
            throw new Error('The body must be valid JSON.')
        }
    }
}
