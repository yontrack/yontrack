import {Popover, Space} from "antd";
import Decorations from "@components/framework/decorations/Decorations";
import Timestamp from "@components/common/Timestamp";
import BuildLink from "@components/builds/BuildLink";

/**
 *
 * @param build
 * @param text Alternative text for the build (build name by default)
 * @param extra Components to add at the right of the box
 * @param displayDecorations If the build decorations must be displayed after the build link
 * @param creationDisplayMode How the build timestamp information is displayed:
 *   * inline (default) - just below the build links & decorations
 *   * none - nothing to display
 *   * tooltip - as a tooltip
 * @param className Specific CSS class to set to the box
 * @returns {JSX.Element}
 * @constructor
 */
export default function BuildBox({
                                     build, text, extra,
                                     displayDecorations = true,
                                     creationDisplayMode = "inline",
                                     className,
                                     children
                                 }) {

    const core =
        <Space orientation="vertical" size={0}>
            <Space>
                <BuildLink build={build} text={text}/>
                {
                    displayDecorations && <Decorations entity={build}/>
                }
                {children}
            </Space>
            {
                creationDisplayMode === 'inline' &&
                <Timestamp prefix="Created on" value={build.creation.time}/>
            }
        </Space>

    return (
        <>
            <Space className={className}>
                {
                    creationDisplayMode === 'tooltip' &&
                    <Popover
                        content={<Timestamp prefix="Created on" value={build.creation.time}/>}
                    >
                        {core}
                    </Popover>
                }
                {
                    creationDisplayMode !== 'tooltip' && core
                }
                {extra}
            </Space>
        </>
    )
}