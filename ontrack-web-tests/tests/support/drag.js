/**
 * Drags an element onto another one with the mouse, in small steps, the way react-easy-sort
 * expects a reorder gesture to happen.
 */
export const dragOnto = async (page, from, to) => {
    const fromBox = await from.boundingBox()
    const toBox = await to.boundingBox()

    const fromX = fromBox.x + fromBox.width / 2
    const fromY = fromBox.y + fromBox.height / 2
    const toX = toBox.x + toBox.width / 2
    const toY = toBox.y + toBox.height / 2

    await page.mouse.move(fromX, fromY)
    await page.mouse.down()
    await page.mouse.move(toX, toY, {steps: 10})
    await page.mouse.up()
}
