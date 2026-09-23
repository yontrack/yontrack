import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import Result from "@components/framework/search/finding/Result"
import {findingUri} from "@components/common/Links"

const data = (branches) => ({
    finding: {
        id: 42,
        externalId: 'CVE-2021-44228',
        scanner: 'trivy',
        location: 'pkg:maven/org.apache.logging.log4j/log4j-core',
        kind: 'IMAGE',
        title: 'log4j-core: Remote code execution in Log4j 2.x',
        maxSeverity: 'CRITICAL',
        state: 'OPEN',
    },
    project: {id: 7, name: 'yontrack'},
    branches,
})

describe('Search result of a finding', () => {

    it('links the external ID to the finding page', () => {
        render(<Result data={data([])}/>)
        expect(screen.getByRole('link', {name: 'CVE-2021-44228'}))
            .toHaveAttribute('href', '/extension/findings/finding/42')
    })

    it('shows the finding page route', () => {
        expect(findingUri({id: 42})).toBe('/extension/findings/finding/42')
    })

    it('shows the project, the severity, the title and the location', () => {
        render(<Result data={data([])}/>)
        expect(screen.getByRole('link', {name: 'yontrack'})).toHaveAttribute('href', '/project/7')
        expect(screen.getByText('CRITICAL')).toBeInTheDocument()
        expect(screen.getByText('log4j-core: Remote code execution in Log4j 2.x')).toBeInTheDocument()
        expect(screen.getByText('pkg:maven/org.apache.logging.log4j/log4j-core')).toBeInTheDocument()
    })

    it('lists the branches the finding is exposed on, the accepted ones marked', () => {
        render(<Result data={data([
            {id: 1, name: 'main', state: 'EXPOSED'},
            {id: 2, name: 'release-1.0', state: 'ACCEPTED'},
        ])}/>)
        expect(screen.getByRole('link', {name: 'main'})).toHaveAttribute('href', '/branch/1')
        expect(screen.getByRole('link', {name: 'release-1.0'})).toHaveAttribute('href', '/branch/2')
        expect(screen.getByText('accepted')).toBeInTheDocument()
    })

    it('says when the finding is exposed on no branch', () => {
        render(<Result data={data([])}/>)
        expect(screen.getByText('Not exposed on any branch')).toBeInTheDocument()
    })
})
