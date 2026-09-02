import React from "react";
import {render, screen} from "@testing-library/react";

// Ant Design uses window.matchMedia for responsive features; jsdom doesn't provide it
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
})
import '@testing-library/jest-dom';

import NotificationBadgeCluster from "@components/primitives/NotificationBadgeCluster";

describe('NotificationBadgeCluster', () => {

    it('renders one pill per non-zero bucket', () => {
        render(<NotificationBadgeCluster counts={{success: 3, running: 1, error: 2}} href="/x"/>)
        expect(screen.getByTestId('notification-badge-success')).toHaveTextContent('3')
        expect(screen.getByTestId('notification-badge-running')).toHaveTextContent('1')
        expect(screen.getByTestId('notification-badge-error')).toHaveTextContent('2')
    })

    it('hides a bucket with a zero count', () => {
        render(<NotificationBadgeCluster counts={{success: 3, running: 0, error: 0}} href="/x"/>)
        expect(screen.getByTestId('notification-badge-success')).toBeInTheDocument()
        expect(screen.queryByTestId('notification-badge-running')).not.toBeInTheDocument()
        expect(screen.queryByTestId('notification-badge-error')).not.toBeInTheDocument()
    })

    it('renders nothing when every count is zero', () => {
        const {container} = render(
            <NotificationBadgeCluster counts={{success: 0, running: 0, error: 0}} href="/x"/>
        )
        expect(container).toBeEmptyDOMElement()
    })

    it('renders nothing when there are no counts at all', () => {
        const {container} = render(<NotificationBadgeCluster href="/x"/>)
        expect(container).toBeEmptyDOMElement()
    })

    it('pulses only the in-flight bucket', () => {
        render(<NotificationBadgeCluster counts={{success: 3, running: 1, error: 2}} href="/x"/>)
        expect(screen.getByTestId('notification-badge-running')).toHaveClass('ot-pulse')
        expect(screen.getByTestId('notification-badge-success')).not.toHaveClass('ot-pulse')
        expect(screen.getByTestId('notification-badge-error')).not.toHaveClass('ot-pulse')
    })

    it('names each bucket, so a bare number is never the only clue', () => {
        render(<NotificationBadgeCluster counts={{success: 3, running: 1, error: 2}} href="/x"/>)
        expect(screen.getByLabelText('3 notification(s) have succeeded.')).toBeInTheDocument()
        expect(screen.getByLabelText('1 notification(s) are still running.')).toBeInTheDocument()
        expect(screen.getByLabelText('2 notification(s) have failed.')).toBeInTheDocument()
    })

    it('links every pill to the entity', () => {
        render(<NotificationBadgeCluster counts={{success: 3, running: 1, error: 2}} href="/promotionRun/9"/>)
        const links = screen.getAllByRole('link')
        expect(links).toHaveLength(3)
        links.forEach(link => expect(link).toHaveAttribute('href', '/promotionRun/9'))
    })

    it('spells the buckets out when asked to', () => {
        render(<NotificationBadgeCluster counts={{success: 3, running: 0, error: 0}} href="/x" showText={true}/>)
        expect(screen.getByText('3 notification(s) have succeeded.')).toBeInTheDocument()
    })

    // Otherwise a screen reader reads the sentence twice - once off the pill's
    // own label, once off the text beside it.
    it('does not repeat the sentence as a label when it is already visible', () => {
        render(<NotificationBadgeCluster counts={{success: 3, running: 0, error: 0}} href="/x" showText={true}/>)
        expect(screen.getByTestId('notification-badge-success')).not.toHaveAttribute('aria-label')
    })

    it('stays a bare cluster when not asked to', () => {
        render(<NotificationBadgeCluster counts={{success: 3, running: 0, error: 0}} href="/x"/>)
        expect(screen.queryByText('3 notification(s) have succeeded.')).not.toBeInTheDocument()
    })

    // The counts are notification records across every channel - mail, Slack,
    // webhook, workflow. See docs/adr/0002.
    it('is named for notifications, not for workflows', () => {
        render(<NotificationBadgeCluster counts={{success: 1, running: 0, error: 0}} href="/x" showText={true}/>)
        expect(screen.queryByText(/workflow/i)).not.toBeInTheDocument()
    })
})
