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

import GitLabIssues from "@components/framework/issues/gitlab-issues";
import IssueGitLabSummary from "@components/framework/issues/gitlab/Summary";

/**
 * What the server sends as `rawIssue` is the JSON of `GitLabIssueWrapper`, pinned on the Kotlin side by
 * `GitLabIssueServiceExtensionTest`.
 */
const rawIssue = ({
                      state = 'opened',
                      labels = ['bug', 'urgent'],
                      milestoneTitle = 'v1',
                      milestoneUrl = 'https://gitlab.com/group/project/-/milestones/7',
                  } = {}) => ({state, labels, milestoneTitle, milestoneUrl})

const issue = (overrides = {}) => ({
    displayKey: '#16',
    summary: 'Some issue',
    url: 'https://gitlab.com/group/project/-/issues/16',
    updateTime: '2026-09-19T10:11:12Z',
    rawIssue: rawIssue(overrides),
})

describe('gitlab-issues', () => {

    it('renders one row per issue, with its key linked', () => {
        render(<GitLabIssues issues={[issue()]}/>)
        expect(screen.getByText('#16')).toBeInTheDocument()
        expect(screen.getByText('#16').closest('a')).toHaveAttribute(
            'href',
            'https://gitlab.com/group/project/-/issues/16',
        )
        expect(screen.getByText('Some issue')).toBeInTheDocument()
    })

    it('renders the state, the labels and the milestone', () => {
        render(<GitLabIssues issues={[issue()]}/>)
        expect(screen.getByText('opened')).toBeInTheDocument()
        expect(screen.getByText('bug')).toBeInTheDocument()
        expect(screen.getByText('urgent')).toBeInTheDocument()
        expect(screen.getByText('v1').closest('a')).toHaveAttribute(
            'href',
            'https://gitlab.com/group/project/-/milestones/7',
        )
    })

    it('renders an issue with no milestone and no label', () => {
        render(<GitLabIssues issues={[issue({labels: [], milestoneTitle: null, milestoneUrl: null})]}/>)
        expect(screen.getByText('#16')).toBeInTheDocument()
        expect(screen.queryByText('v1')).not.toBeInTheDocument()
    })

})

describe('gitlab issue summary', () => {

    it('renders the state, the milestone and the labels', () => {
        render(<IssueGitLabSummary rawIssue={rawIssue()}/>)
        expect(screen.getByText('opened')).toBeInTheDocument()
        expect(screen.getByText('v1')).toBeInTheDocument()
        expect(screen.getByText('bug')).toBeInTheDocument()
    })

    it('renders a closed issue without a milestone', () => {
        render(<IssueGitLabSummary rawIssue={rawIssue({
            state: 'closed',
            labels: [],
            milestoneTitle: null,
            milestoneUrl: null,
        })}/>)
        expect(screen.getByText('closed')).toBeInTheDocument()
        expect(screen.queryByText('Milestone:')).not.toBeInTheDocument()
        expect(screen.queryByText('Labels:')).not.toBeInTheDocument()
    })

    it('renders a milestone Yontrack has no URL for as plain text', () => {
        render(<IssueGitLabSummary rawIssue={rawIssue({milestoneUrl: null})}/>)
        expect(screen.getByText('v1')).toBeInTheDocument()
        expect(screen.getByText('v1').closest('a')).toBeNull()
    })

})
