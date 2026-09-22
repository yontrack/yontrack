"use client"

/**
 * What the home screen shows to a user with no favourites yet.
 *
 * Which is every user who has not already curated favourites on the desktop UI -
 * so without this the mobile home is blank on first use and reads as broken. It
 * says what favourites are, and offers the one thing that fills the screen:
 * the project list.
 */

import Link from "next/link"
import {Button, Empty, Space, Typography} from "antd"
import {MOBILE_PROJECTS} from "@components/mobile/mobileRoutes"

export default function MobileFavouritesEmpty() {
    return (
        <div data-testid="mobile-favourites-empty">
            <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                    <Space orientation="vertical" size="small">
                        <Typography.Text strong>No favourites yet</Typography.Text>
                        <Typography.Text type="secondary">
                            Star the projects and branches you follow and they show up here,
                            so your phone opens on what you actually check.
                        </Typography.Text>
                    </Space>
                }
            >
                <Link href={MOBILE_PROJECTS} data-testid="mobile-favourites-empty-projects">
                    <Button type="primary">Browse the projects</Button>
                </Link>
            </Empty>
        </div>
    )
}
