import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import SubscriptionKeywordsHelp from "@components/extension/notifications/SubscriptionKeywordsHelp"

/**
 * The help of the keywords of a subscription lists the keywords on the actor of the event.
 */
describe('SubscriptionKeywordsHelp', () => {

    it('lists the actor and agent keywords', () => {
        render(<SubscriptionKeywordsHelp/>)
        expect(screen.getByText("actor:agent")).toBeInTheDocument()
        expect(screen.getByText("actor:human")).toBeInTheDocument()
        expect(screen.getByText("agent:<slug>[agent]")).toBeInTheDocument()
        expect(screen.getByText("agent:<slug>")).toBeInTheDocument()
    })

})
