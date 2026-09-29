import {useTemplateRenderers} from "@components/extension/issues/SelectTemplateRenderer";

/**
 * The renderers the server can render a change log with — `text`, `markdown`, `html`, `jira`,
 * `slack`.
 *
 * Same list as `useTemplateRenderers` in `@components/extension/issues/SelectTemplateRenderer`,
 * which this delegates to.
 */
export default function useTemplatingRenderers() {
    return useTemplateRenderers()
}
