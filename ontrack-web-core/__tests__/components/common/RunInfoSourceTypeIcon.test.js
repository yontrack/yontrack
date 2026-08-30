import RunInfoSourceTypeIcon from "@components/common/RunInfoSourceTypeIcon";
import RunInfoSource from "@components/common/RunInfoSource";
import {render, screen} from "@testing-library/react";
import React from "react";
import '@testing-library/jest-dom';

describe('RunInfoSourceTypeIcon', () => {

    it('renders the Jenkins icon for the jenkins source type', () => {
        render(<RunInfoSourceTypeIcon type="jenkins"/>)
        expect(screen.getByRole('img', {name: 'Jenkins'})).toBeInTheDocument()
    })

    // Both the CI workflows reporting through the CLI and the GitHub ingestion send
    // `github-workflow`, so one icon covers both.
    it('renders the GitHub icon for the github-workflow source type', () => {
        render(<RunInfoSourceTypeIcon type="github-workflow"/>)
        expect(screen.getByRole('img', {name: 'GitHub'})).toBeInTheDocument()
    })

    it('renders nothing for an unknown source type', () => {
        const {container} = render(<RunInfoSourceTypeIcon type="something-else"/>)
        expect(container).toBeEmptyDOMElement()
    })

    // The icon names itself for screen readers without an SVG <title>, which would otherwise
    // become the hover tooltip and hide the link affordance in minimal mode.
    it('leaves the link tooltip visible in minimal mode', () => {
        render(
            <RunInfoSource
                mode="minimal"
                info={{sourceType: 'github-workflow', sourceUri: 'https://example.com/run/1'}}
            />
        )
        expect(screen.getByTitle('Link to github-workflow')).toBeInTheDocument()
        expect(document.querySelector('svg title')).toBeNull()
    })

})
