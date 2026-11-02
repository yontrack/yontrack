import {promotionMedals} from "@components/branches/promotionMedals";

const auto = {autoRevoke: false}

const bronze = {id: 1, name: 'BRONZE', autoPromotionConditions: null}
const silver = {id: 2, name: 'SILVER', autoPromotionConditions: auto}
const gold = {id: 3, name: 'GOLD', autoPromotionConditions: null}
const platinum = {id: 4, name: 'PLATINUM', autoPromotionConditions: auto}

const runOf = (id, promotionLevel) => ({id, promotionLevel: {id: promotionLevel.id, name: promotionLevel.name}})

// What is drawn, in order: a run by its ID, a level not granted by its name
const drawn = medals => medals.map(({run, promotionLevel}) => run ? run.id : promotionLevel.name)

describe('promotionMedals', () => {

    it('adds a medal for a level with an auto promotion which is not granted', () => {
        expect(drawn(promotionMedals({
            promotionLevels: [bronze, silver],
            promotionRuns: [runOf(10, bronze)],
        }))).toEqual([10, 'SILVER'])
    })

    it('adds nothing for a level without auto promotion which is not granted', () => {
        expect(drawn(promotionMedals({
            promotionLevels: [bronze, gold],
            promotionRuns: [runOf(10, bronze)],
        }))).toEqual([10])
    })

    it('draws a granted level by its run, with or without auto promotion', () => {
        expect(drawn(promotionMedals({
            promotionLevels: [silver, gold],
            promotionRuns: [runOf(20, silver), runOf(30, gold)],
        }))).toEqual([20, 30])
    })

    it("follows the branch's order, whatever the order of the runs", () => {
        expect(drawn(promotionMedals({
            promotionLevels: [bronze, silver, gold, platinum],
            promotionRuns: [runOf(30, gold), runOf(10, bronze)],
        }))).toEqual([10, 'SILVER', 30, 'PLATINUM'])
    })

    it('keeps the runs in their order while the levels are not known yet', () => {
        expect(drawn(promotionMedals({
            promotionLevels: [],
            promotionRuns: [runOf(30, gold), runOf(10, bronze)],
        }))).toEqual([30, 10])
    })

    it('still draws, last, a run whose level is not in the known levels', () => {
        expect(drawn(promotionMedals({
            promotionLevels: [silver],
            promotionRuns: [runOf(10, bronze)],
        }))).toEqual(['SILVER', 10])
    })

    it('gives every medal its own key', () => {
        const keys = promotionMedals({
            // The ID of the run is the same as the ID of the level not granted
            promotionLevels: [bronze, silver],
            promotionRuns: [runOf(2, bronze)],
        }).map(medal => medal.key)
        expect(new Set(keys).size).toBe(2)
    })

})
