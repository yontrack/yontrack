import React from "react"

// recharts finds the series of a chart among its children with `react-is`. The `react-is` it gets
// must know the elements of the React the UI runs on: `react-is` 18 does not recognise the
// elements of React 19, and recharts then silently drops whatever is wrapped in a Fragment (#2175).
// `package.json` pins it for recharts through `overrides`.
describe("the react-is of recharts", () => {

    const resolveFromRecharts = (id) => require.resolve(id, {paths: [require.resolve("recharts")]})

    it("is the version of React", () => {
        expect(require(resolveFromRecharts("react-is/package.json")).version).toBe(React.version)
    })

    it("recognises a Fragment", () => {
        expect(require(resolveFromRecharts("react-is")).isFragment(<></>)).toBe(true)
    })

})
