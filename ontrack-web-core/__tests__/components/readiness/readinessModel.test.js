import {groupReadinessItems} from "@components/readiness/readinessModel"

const item = (kind, name, message = `${name} is missing`) => ({kind, name, message})

describe('groupReadinessItems', () => {

    it('returns no group when nothing is missing', () => {
        expect(groupReadinessItems([])).toEqual([])
        expect(groupReadinessItems(undefined)).toEqual([])
        expect(groupReadinessItems(null)).toEqual([])
    })

    it('groups the items by kind, in the order of the kinds, whatever the order of the items', () => {
        const groups = groupReadinessItems([
            item('MANUAL', 'GOLD'),
            item('CHECK', 'Previous promotion'),
            item('VALIDATION', 'UNIT.TESTS'),
            item('PROMOTION', 'BRONZE'),
            item('VALIDATION', 'INTEGRATION.TESTS'),
        ])
        expect(groups.map(group => group.kind)).toEqual(['VALIDATION', 'PROMOTION', 'CHECK', 'MANUAL'])
        expect(groups[0].items.map(it => it.name)).toEqual(['UNIT.TESTS', 'INTEGRATION.TESTS'])
    })

    it('titles each group', () => {
        const groups = groupReadinessItems([
            item('VALIDATION', 'UNIT.TESTS'),
            item('PROMOTION', 'BRONZE'),
            item('CHECK', 'Previous promotion'),
            item('ADMISSION_RULE', 'Promotion'),
            item('MANUAL', 'GOLD'),
            item('AGENT_POLICY', 'Agent'),
        ])
        expect(groups.map(group => group.title)).toEqual([
            'Validations',
            'Promotions',
            'Promotion checks',
            'Admission rules',
            'A person must act',
            'Agent policy',
        ])
    })

    it('keeps a kind it does not know, last, titled by the kind itself', () => {
        const groups = groupReadinessItems([
            item('SOMETHING_NEW', 'x'),
            item('VALIDATION', 'UNIT.TESTS'),
        ])
        expect(groups.map(group => group.kind)).toEqual(['VALIDATION', 'SOMETHING_NEW'])
        expect(groups[1].title).toBe('SOMETHING_NEW')
    })

})
