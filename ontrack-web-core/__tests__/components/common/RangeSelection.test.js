import {act, renderHook} from "@testing-library/react";
import useRangeSelection from "@components/common/RangeSelection";

describe('useRangeSelection', () => {

    it('keeps its selection whatever happens around it when nothing says what is available', () => {
        const {result, rerender} = renderHook(() => useRangeSelection())
        act(() => result.current.select("1"))
        act(() => result.current.select("2"))
        rerender()
        expect(result.current.selection).toEqual(["1", "2"])
    })

    it('keeps a selection whose elements are all still available', () => {
        let available = ["3", "2", "1"]
        const {result, rerender} = renderHook(() => useRangeSelection({available}))
        act(() => result.current.select("1"))
        act(() => result.current.select("2"))
        // A refresh: a new build on top, the same ones below
        available = ["4", "3", "2", "1"]
        rerender()
        expect(result.current.selection).toEqual(["1", "2"])
        expect(result.current.isComplete()).toBe(true)
    })

    it('drops only the element which is no longer available', () => {
        let available = ["3", "2", "1"]
        const {result, rerender} = renderHook(() => useRangeSelection({available}))
        act(() => result.current.select("3"))
        act(() => result.current.select("1"))
        // A refresh: a new build on top, the oldest one dropped off the bottom
        available = ["4", "3", "2"]
        rerender()
        expect(result.current.selection).toEqual(["3"])
        expect(result.current.isComplete()).toBe(false)
    })

})
