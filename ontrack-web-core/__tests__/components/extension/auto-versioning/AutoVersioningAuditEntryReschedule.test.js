import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import AutoVersioningAuditEntryReschedule
    from "@components/extension/auto-versioning/AutoVersioningAuditEntryReschedule"

jest.mock("../../../../components/services/GraphQL", () => ({
    useMutation: () => ({mutate: jest.fn(), loading: false}),
}))

jest.mock("next/router", () => ({
    useRouter: () => ({push: jest.fn()}),
}))

const entry = (authorizations) => ({
    order: {uuid: 'a7a7a7a7-0000-0000-0000-000000000001'},
    lineage: {configuredRetryMaxCount: 3},
    authorizations,
})

describe('AutoVersioningAuditEntryReschedule', () => {

    it('shows the Reschedule button when rescheduling is authorized', () => {
        render(<AutoVersioningAuditEntryReschedule entry={entry([
            {name: 'auto_versioning_audit_entry', action: 'reschedule', authorized: true},
        ])}/>)
        expect(screen.getByRole('button', {name: /Reschedule/})).toBeInTheDocument()
    })

    it('hides the Reschedule button when rescheduling is not authorized', () => {
        render(<AutoVersioningAuditEntryReschedule entry={entry([
            {name: 'auto_versioning_audit_entry', action: 'reschedule', authorized: false},
        ])}/>)
        expect(screen.queryByRole('button', {name: /Reschedule/})).not.toBeInTheDocument()
    })

    it('hides the Reschedule button when the authorizations are not known', () => {
        render(<AutoVersioningAuditEntryReschedule entry={entry(undefined)}/>)
        expect(screen.queryByRole('button', {name: /Reschedule/})).not.toBeInTheDocument()
    })

})
