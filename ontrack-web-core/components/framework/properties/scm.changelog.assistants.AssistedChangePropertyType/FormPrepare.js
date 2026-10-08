/**
 * A value set from the form is set like the CI sets it: it is never recomputed.
 */
export default function FormPrepare(value) {
    return {
        basis: 'SET_BY_CI',
        assistants: value?.assistants ?? [],
        assistedCommits: value?.assistedCommits ?? 0,
        totalCommits: value?.totalCommits ?? 0,
        sessionLinks: value?.sessionLinks ?? [],
    }
}
