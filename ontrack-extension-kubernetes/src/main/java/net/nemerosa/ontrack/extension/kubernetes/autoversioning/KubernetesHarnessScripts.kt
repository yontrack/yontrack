package net.nemerosa.ontrack.extension.kubernetes.autoversioning

/**
 * The harness of a Job: the paths, volumes and environment variables shared by its steps, and
 * the scripts that the `clone` and `push` steps run through `sh -c` on the stock harness image.
 *
 * The scripts are constants. Every dynamic value (clone URL, branch, commit message, identity)
 * reaches them as an environment variable of the harness container, never by interpolation, so
 * that no value can inject shell code.
 *
 * The credentials are files of a Secret volume, mounted by the harness steps only. They are fed
 * to Git at run time by a credential helper given on the command line (`git -c
 * credential.helper=...`), which reads these files: they are never in the remote URL, in a Git
 * config file, in an environment variable nor in the script text. Without the files (anonymous
 * access), the helper answers nothing and a public repository still clones.
 */
object KubernetesHarnessScripts {

    /** Volume of the work tree, the only one the `run` step mounts. */
    const val VOLUME_WORK_TREE = "work-tree"

    /** Volume holding the Git directory, out of reach of the `run` step. */
    const val VOLUME_GIT_DIR = "git-dir"

    /** Secret volume of the SCM credentials, harness steps only. */
    const val VOLUME_CREDENTIALS = "credentials"

    /** `HOME` of the harness steps, where the credential helper is written. Harness steps only. */
    const val VOLUME_HARNESS_HOME = "harness-home"

    /** Mount path of the work tree, also the working directory of the `run` step. */
    const val WORK_TREE_PATH = "/workspace"

    /** Mount path of the Git directory volume. */
    const val GIT_DIR_VOLUME_PATH = "/yontrack/git"

    /**
     * The Git directory itself: a sub-directory of its volume, because `git clone
     * --separate-git-dir` refuses an existing directory.
     */
    const val GIT_DIR = "$GIT_DIR_VOLUME_PATH/repo.git"

    /** Mount path of the credentials. */
    const val CREDENTIALS_PATH = "/yontrack/credentials"

    /** Mount path of the harness home. */
    const val HARNESS_HOME_PATH = "/yontrack/home"

    /** Key of the username in the credentials Secret, and name of its file. */
    const val CREDENTIALS_USERNAME_KEY = "username"

    /** Key of the password in the credentials Secret, and name of its file. */
    const val CREDENTIALS_PASSWORD_KEY = "password"

    /** Clone URL, without credentials. */
    const val ENV_CLONE_URL = "YONTRACK_CLONE_URL"

    /** Upgrade branch, cloned and pushed to. */
    const val ENV_UPGRADE_BRANCH = "YONTRACK_UPGRADE_BRANCH"

    /** Path of the Git directory. */
    const val ENV_GIT_DIR = "YONTRACK_GIT_DIR"

    /** Path of the work tree. */
    const val ENV_WORK_TREE = "YONTRACK_WORK_TREE"

    /** Directory holding the credential files, which may be absent. */
    const val ENV_CREDENTIALS_DIR = "YONTRACK_CREDENTIALS_DIR"

    /** Message of the commit made by the `push` step. */
    const val ENV_COMMIT_MESSAGE = "YONTRACK_COMMIT_MESSAGE"

    /**
     * Common start: strict mode, the credential helper written in the harness home, and a `git`
     * function with the helper, no hook, and no ownership check (volume roots belong to root).
     */
    private const val PREAMBLE = $$"""set -eu
helper="$HOME/git-credential-yontrack"
cat > "$helper" <<'EOF'
#!/bin/sh
cat > /dev/null
[ "${1:-}" = get ] || exit 0
d="$YONTRACK_CREDENTIALS_DIR"
if [ -f "$d/username" ] && [ -f "$d/password" ]; then
printf 'username=%s\n' "$(cat "$d/username")"
printf 'password=%s\n' "$(cat "$d/password")"
fi
EOF
chmod 700 "$helper"
yt_git() {
git -c credential.helper= -c "credential.helper=$helper" -c safe.directory='*' -c core.hooksPath=/dev/null "$@"
}
"""

    /**
     * `clone` step: shallow clone of the upgrade branch, Git directory apart, then removal of the
     * `.git` pointer file so that the work tree holds no reference to it.
     */
    const val CLONE_SCRIPT = PREAMBLE + $$"""yt_git clone --depth 1 --single-branch --branch "$YONTRACK_UPGRADE_BRANCH" --separate-git-dir "$YONTRACK_GIT_DIR" -- "$YONTRACK_CLONE_URL" "$YONTRACK_WORK_TREE"
rm -f "$YONTRACK_WORK_TREE/.git"
"""

    /**
     * `push` step: stages everything; nothing staged is a success without a commit; else one
     * commit (identity from the `GIT_AUTHOR_*` / `GIT_COMMITTER_*` variables) and a non-forced
     * push to the upgrade branch.
     */
    const val PUSH_SCRIPT = PREAMBLE + $$"""yt_git_wt() {
yt_git --git-dir="$YONTRACK_GIT_DIR" --work-tree="$YONTRACK_WORK_TREE" "$@"
}
yt_git_wt add -A
if yt_git_wt diff --cached --quiet; then
echo "No change to commit"
exit 0
fi
yt_git_wt -c commit.gpgSign=false commit --no-verify --quiet -m "$YONTRACK_COMMIT_MESSAGE"
yt_git_wt push origin "HEAD:refs/heads/$YONTRACK_UPGRADE_BRANCH"
"""
}
