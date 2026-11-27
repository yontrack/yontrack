package net.nemerosa.ontrack.extension.stash.scm

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.nemerosa.ontrack.extension.stash.client.BitbucketServerPRMergeRejectedException

/**
 * Waits for a PR to be mergeable and merges it.
 *
 * Between the moment the PR is found mergeable and the merge call, another PR may be merged into
 * the same target branch: Bitbucket Server then rejects the merge
 * ([BitbucketServerPRMergeRejectedException]). The PR is then checked again and, once mergeable
 * again, merged again — all within the same [timeoutMillis]. A PR with a real conflict does not
 * become mergeable again and the timeout ends the wait.
 *
 * Any other error of [merge] is not retried.
 *
 * @param timeoutMillis Overall time allowed for the PR to be merged
 * @param intervalMillis Time between two checks of the PR
 * @param isMergeable Checks if the PR can be merged
 * @param merge Merges the PR
 * @param onRejected Called each time the merge is rejected, before checking the PR again
 * @return `true` if the PR was merged, `false` if the timeout expired first
 */
internal fun mergeWhenMergeable(
    timeoutMillis: Long,
    intervalMillis: Long,
    isMergeable: () -> Boolean,
    merge: () -> Unit,
    onRejected: (BitbucketServerPRMergeRejectedException) -> Unit = {},
): Boolean =
    runBlocking {
        withTimeoutOrNull(timeMillis = timeoutMillis) {
            var merged = false
            while (!merged) {
                if (isMergeable()) {
                    merged = try {
                        merge()
                        true
                    } catch (ex: BitbucketServerPRMergeRejectedException) {
                        onRejected(ex)
                        false
                    }
                }
                if (!merged) {
                    delay(intervalMillis)
                }
            }
            true
        }
    } ?: false
