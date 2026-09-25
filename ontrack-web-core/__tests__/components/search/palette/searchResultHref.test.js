import {searchResultHref} from "@components/search/palette/searchResultHref";

const href = (type, data) => searchResultHref({type: {id: type}, data})

describe('where a search result opens', () => {

    it('opens a project at its page', () => {
        expect(href('project', {project: {id: 1, name: 'ontrack'}})).toBe('/project/1')
    })

    it('opens a branch, and a Git branch, at the page of the branch', () => {
        expect(href('branch', {branch: {id: 10}})).toBe('/branch/10')
        expect(href('git-branch', {gitBranch: 'release/1.0', branch: {id: 10}})).toBe('/branch/10')
    })

    it('opens a build, and a build with a release, at the page of the build', () => {
        expect(href('build', {build: {id: 100}})).toBe('/build/100')
        expect(href('build-release', {build: {id: 100}, release: '1.0.0'})).toBe('/build/100')
    })

    it('opens a build link at the build declaring it', () => {
        expect(href('build-link', {sourceBuild: {id: 100}, targetBuild: {id: 200}})).toBe('/build/100')
    })

    it('opens a finding at its page', () => {
        expect(href('finding', {finding: {id: 5}})).toBe('/extension/findings/finding/5')
    })

    it('opens a commit and an issue at their page in the project', () => {
        expect(href('scm-commit', {item: {id: 'abcdef1234', projectName: 'ontrack'}}))
            .toBe('/extension/scm/ontrack/commit-info/abcdef1234')
        expect(href('scm-issue', {item: {key: 'ISS-1', projectName: 'ontrack'}}))
            .toBe('/extension/scm/ontrack/issue-info/ISS-1')
    })

    it('opens a catalog entry at its project, or else at its repository', () => {
        expect(href('scm-catalog', {project: {id: 1}, scmCatalogEntry: {repositoryPage: 'https://github.com/x/y'}}))
            .toBe('/project/1')
        expect(href('scm-catalog', {scmCatalogEntry: {repositoryPage: 'https://github.com/x/y'}}))
            .toBe('https://github.com/x/y')
    })

    it('has nowhere to open a type it does not know', () => {
        expect(href('something-else', {whatever: true})).toBeNull()
    })

    it('has nowhere to open a result missing its data', () => {
        expect(href('project', null)).toBeNull()
        expect(href('project', {})).toBeNull()
    })

})
