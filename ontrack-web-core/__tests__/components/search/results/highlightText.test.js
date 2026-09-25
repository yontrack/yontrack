import {highlightText} from "@components/search/results/highlightText"

describe('highlightText', () => {

    it('flags the words of the query found in the text, whatever the case', () => {
        expect(highlightText('Billing/Release-4.1', 'billing release')).toEqual([
            {text: 'Billing', match: true},
            {text: '/', match: false},
            {text: 'Release', match: true},
            {text: '-4.1', match: false},
        ])
    })

    it('is one plain part when nothing matches', () => {
        expect(highlightText('Billing', 'payment')).toEqual([{text: 'Billing', match: false}])
    })

    it('flags every occurrence', () => {
        expect(highlightText('abcab', 'ab')).toEqual([
            {text: 'ab', match: true},
            {text: 'c', match: false},
            {text: 'ab', match: true},
        ])
    })

    it('merges overlapping matches', () => {
        expect(highlightText('release', 'rele lease')).toEqual([
            {text: 'release', match: true},
        ])
    })

    it('treats the characters of the query literally', () => {
        expect(highlightText('a.b axb', 'a.b')).toEqual([
            {text: 'a.b', match: true},
            {text: ' axb', match: false},
        ])
    })

    it('has nothing to flag for an empty query', () => {
        expect(highlightText('Billing', '  ')).toEqual([{text: 'Billing', match: false}])
    })

    it('has no part for an empty text', () => {
        expect(highlightText('', 'x')).toEqual([])
        expect(highlightText(null, 'x')).toEqual([])
    })

})
