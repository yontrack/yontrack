/**
 * The medals of a build in the branch builds view, in the branch's order of promotion levels, so that
 * a medal stays in place when its level gets granted.
 *
 * A granted level is drawn by its run, and a level not granted only when it has an auto promotion,
 * since this is what its medal has to show. A run whose level is not known (yet) still gets its medal,
 * after the others: the levels are loaded apart from the builds.
 *
 * @param promotionLevels Promotion levels of the branch, in the branch's order
 * @param promotionRuns Promotion runs of the build
 * @return Medals, each one having a `key`, its `position` and either a `run` or a `promotionLevel` not granted
 */
export const promotionMedals = ({promotionLevels, promotionRuns}) => {
    const positions = new Map(promotionLevels.map((promotionLevel, index) => [promotionLevel.id, index]))
    const positionOf = id => positions.get(id) ?? promotionLevels.length
    const granted = new Set(promotionRuns.map(run => run.promotionLevel.id))
    return [
        ...promotionRuns.map(run => ({
            key: `run-${run.id}`,
            position: positionOf(run.promotionLevel.id),
            run,
        })),
        ...promotionLevels
            .filter(promotionLevel => promotionLevel.autoPromotionConditions && !granted.has(promotionLevel.id))
            .map(promotionLevel => ({
                key: `level-${promotionLevel.id}`,
                position: positionOf(promotionLevel.id),
                promotionLevel,
            })),
    ].sort((a, b) => a.position - b.position)
}
