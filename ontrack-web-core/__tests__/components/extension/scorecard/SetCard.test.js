import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"
import SetCard from "@components/extension/scorecard/SetCard"

const reading = (props) => ({
    key: 'delivery.leadTime',
    value: 7200,
    basis: 'MEASURED',
    unknownReason: null,
    direction: 'LOWER_IS_BETTER',
    target: null,
    targetMet: null,
    details: {},
    ...props,
})

const estateSet = (readings, marker = {kind: 'PROMOTION', levelName: 'GOLD'}) => ({
    name: 'Demo products',
    estate: {name: 'Demo products', description: 'The products we ship', labels: [], marker},
    readings,
})

const projectSet = {name: 'Project', estate: null, readings: [reading()]}

describe('The card of a set', () => {

    it('is a button which selects its set', () => {
        const onSelect = jest.fn()
        render(<SetCard set={projectSet} selected={false} onSelect={onSelect}/>)
        const button = screen.getByRole('button', {pressed: false})
        expect(button).toHaveAttribute('aria-pressed', 'false')
        fireEvent.click(button)
        expect(onSelect).toHaveBeenCalledWith(projectSet)
    })

    it('says when it is selected', () => {
        render(<SetCard set={projectSet} selected={true} onSelect={jest.fn()}/>)
        expect(screen.getByRole('button', {pressed: true, name: /^Project Readings only, never judged/})).toBeInTheDocument()
    })

    it('draws the ring of an estate, named by its headline', () => {
        render(<SetCard set={estateSet([
            reading({target: 86400, targetMet: true}),
            reading({key: 'delivery.frequency', target: 5, targetMet: false}),
            reading({key: 'delivery.successRate', target: 80, targetMet: true}),
            reading({key: 'quality.testFlakiness'}),
        ])} selected={false} onSelect={jest.fn()} testId="card"/>)
        const ring = screen.getByRole('img', {name: '2 of 3 targets met in Demo products'})
        expect(ring).toHaveTextContent('2/3')
        // One segment per judged reading, the met ones first
        const segments = [...ring.querySelectorAll('circle')].map(it => it.getAttribute('data-met'))
        expect(segments).toEqual(['true', 'true', 'false'])
        expect(screen.getByTestId('card')).toHaveTextContent('Demo products')
        expect(screen.getByTestId('card-headline')).toHaveTextContent('2 of 3 targets met')
        expect(screen.getByTestId('card')).toHaveTextContent('Up to promotion GOLD')
    })

    it('mentions the readings with a target and no judgement', () => {
        render(<SetCard set={estateSet([
            reading({target: 86400, targetMet: true}),
            reading({key: 'delivery.mttr', target: 86400, value: null, basis: 'UNKNOWN', unknownReason: 'NO_FAILURE'}),
        ])} selected={false} onSelect={jest.fn()} testId="card"/>)
        expect(screen.getByRole('img', {name: '1 of 1 target met in Demo products'})).toBeInTheDocument()
        expect(screen.getByTestId('card-headline')).toHaveTextContent('1 of 1 target met · 1 not judged')
    })

    it.each([
        ['met', [true, true], 'var(--ot-scorecard-met-text)'],
        ['missed', [true, false, false], 'var(--ot-scorecard-missed-text)'],
    ])('colours a headline where the targets are %s', (_, verdicts, color) => {
        render(<SetCard
            set={estateSet(verdicts.map((targetMet, index) => reading({key: `r${index}`, target: 1, targetMet})))}
            selected={false}
            onSelect={jest.fn()}
            testId="card"
        />)
        expect(screen.getByTestId('card-headline-text')).toHaveStyle({color})
    })

    it('shows the Project set as never judged, with no ring', () => {
        render(<SetCard set={projectSet} selected={false} onSelect={jest.fn()} testId="card"/>)
        expect(screen.queryByRole('img')).not.toBeInTheDocument()
        const card = screen.getByTestId('card')
        expect(card).toHaveTextContent('no targets')
        expect(card).toHaveTextContent('Readings only, never judged')
        expect(card).toHaveTextContent("Read up to each branch's last promotion")
    })

    it('names its button after its title, headline and marker', () => {
        render(<SetCard set={estateSet([reading({target: 86400, targetMet: true})])} selected={false} onSelect={jest.fn()}/>)
        expect(screen.getByRole('button', {pressed: false})).toHaveAccessibleName('Demo products 1 of 1 target met Up to promotion GOLD')
    })

    it('says that the targets of an estate are not judged when none could be, rather than that it has none', () => {
        render(<SetCard set={estateSet([
            reading({key: 'delivery.mttr', target: 86400, value: null, basis: 'UNKNOWN', unknownReason: 'NO_FAILURE'}),
        ])} selected={false} onSelect={jest.fn()} testId="card"/>)
        expect(screen.queryByRole('img')).not.toBeInTheDocument()
        expect(screen.getByTestId('card')).toHaveTextContent('not judged')
        expect(screen.getByTestId('card')).not.toHaveTextContent('no targets')
        expect(screen.getByTestId('card-headline')).toHaveTextContent('No target judged · 1 not judged')
    })

    it('has no ring for an estate with no judged reading', () => {
        render(<SetCard set={estateSet([reading()], {kind: 'ENVIRONMENT', environment: 'production', qualifier: ''})} selected={false} onSelect={jest.fn()} testId="card"/>)
        expect(screen.queryByRole('img')).not.toBeInTheDocument()
        expect(screen.getByTestId('card')).toHaveTextContent('no targets')
        expect(screen.getByTestId('card')).toHaveTextContent('Up to environment production')
    })

    it('explains its set, outside of the button', async () => {
        render(<SetCard set={estateSet([])} selected={false} onSelect={jest.fn()}/>)
        const info = screen.getByRole('button', {name: 'About the Estate: Demo products set'})
        expect(screen.getByRole('button', {pressed: false})).not.toContainElement(info)
        fireEvent.mouseEnter(info)
        expect(await screen.findByTestId('scorecard-set-info-Demo products')).toHaveTextContent('The products we ship')
    })
})
