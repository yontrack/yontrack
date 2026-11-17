import {formatHttpBody, InvalidHttpBody, parseHttpBody} from "@components/extension/notifications/http/httpBody"

/**
 * The JSON body of an HTTP call is edited as text and stored as a JSON value, so that the
 * subscription dialog and the workflow node form send the same thing.
 */
describe('httpBody', () => {

    it('parses a JSON object', () => {
        expect(parseHttpBody('{"version": "${build}", "replicas": 3}')).toEqual({version: "${build}", replicas: 3})
    })

    it('parses any JSON value', () => {
        expect(parseHttpBody('"${build}"')).toEqual("${build}")
        expect(parseHttpBody('[1, 2]')).toEqual([1, 2])
    })

    it('reads blank text as no body', () => {
        expect(parseHttpBody('')).toBeNull()
        expect(parseHttpBody('   \n')).toBeNull()
        expect(parseHttpBody(undefined)).toBeNull()
    })

    it('keeps invalid JSON as an invalid body', () => {
        const body = parseHttpBody('{"version": ')
        expect(body).toBeInstanceOf(InvalidHttpBody)
        expect(body.text).toEqual('{"version": ')
    })

    it('formats a JSON value for edition', () => {
        expect(formatHttpBody({version: "1.0"})).toEqual('{\n  "version": "1.0"\n}')
        expect(formatHttpBody(null)).toEqual('')
        expect(formatHttpBody(undefined)).toEqual('')
        expect(formatHttpBody(new InvalidHttpBody('{'))).toEqual('{')
    })

})
