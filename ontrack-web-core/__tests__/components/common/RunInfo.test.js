import RunInfo from "@components/common/RunInfo";
import {render, screen} from "@testing-library/react";
import React from "react";
import '@testing-library/jest-dom';

describe('RunInfo', () => {

    const source = {
        sourceType: 'github-workflow',
        sourceUri: 'https://github.com/yontrack/yontrack/actions/runs/1234',
    }

    it('separates the source from the run time when both are there', () => {
        const {container} = render(<RunInfo info={{...source, runTime: 42}}/>)
        expect(screen.getByText('github-workflow')).toBeInTheDocument()
        expect(container.querySelector('.ant-divider')).toBeInTheDocument()
    })

    // A build's run info is recorded while the run is still going, so it carries a source and
    // no run time. A divider with nothing on its right is a stray mark, not a separator.
    it('does not draw a divider when there is no run time', () => {
        const {container} = render(<RunInfo info={{...source, runTime: null}}/>)
        expect(screen.getByText('github-workflow')).toBeInTheDocument()
        expect(container.querySelector('.ant-divider')).toBeNull()
    })

    // Rows written before the server normalised it away still hold 0 for "not measured", and
    // "Ran in 0 second" is noise rather than a duration.
    it('treats a zero run time as no run time', () => {
        const {container} = render(<RunInfo info={{...source, runTime: 0}}/>)
        expect(screen.getByText('github-workflow')).toBeInTheDocument()
        expect(screen.queryByText(/Ran in/)).toBeNull()
        expect(container.querySelector('.ant-divider')).toBeNull()
    })

    // The same, the other way round: an unrecognised source type renders no source at all.
    it('does not draw a divider when there is no source', () => {
        const {container} = render(<RunInfo info={{runTime: 42}}/>)
        expect(container.querySelector('.ant-divider')).toBeNull()
    })

})
