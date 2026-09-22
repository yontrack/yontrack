"use client"

/**
 * A mobile screen's frame: its title, then its content.
 *
 * The title lives in the screen rather than the header because the header is
 * fixed and every row it takes is a row the content does not get - and because a
 * screen knows its own name, while the shell would have to be told.
 */

import {Space, Typography} from "antd"

/**
 * @param {React.ReactNode} title What the screen is called.
 * @param {React.ReactNode} [subtitle] What places the title - the project a
 *   branch belongs to, say. Under the title rather than beside it, because at
 *   375px a title and its context do not share a line.
 * @param {React.ReactNode} [extra] One control beside the title, for something
 *   that acts on the screen's own subject rather than on a row: the favourite
 *   star of the project or branch being looked at.
 */
export default function MobileScreen({title, subtitle, extra, children}) {
    return (
        <Space orientation="vertical" size="middle" style={{width: '100%'}}>
            <div className="ot-mobile-screen-head">
                <div className="ot-mobile-screen-heading">
                    <Typography.Title
                        level={4}
                        style={{margin: 0}}
                        data-testid="mobile-screen-title"
                        // The name can be longer than the screen - `release/1.0`
                        // on a 375px phone - and must ellipsis rather than push
                        // the control beside it off the edge.
                        ellipsis
                    >
                        {title}
                    </Typography.Title>
                    {
                        subtitle &&
                        <div className="ot-mobile-screen-subtitle" data-testid="mobile-screen-subtitle">
                            {subtitle}
                        </div>
                    }
                </div>
                {
                    extra &&
                    <div className="ot-mobile-screen-extra">{extra}</div>
                }
            </div>
            {children}
        </Space>
    )
}
