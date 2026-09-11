import {Avatar, Typography} from "antd";
import UserMenu, {useUserMenu} from "@components/layouts/UserMenu";
import {useContext} from "react";
import {UserContext} from "@components/providers/UserProvider";
import {FaRegUser} from "react-icons/fa";
import HomeLink from "@components/common/HomeLink";
import NavBarSearch from "@components/search/NavBarSearch";
import Image from "next/image";

const {Text} = Typography;

function NavBarText({text}) {
    return (
        <Text className="ot-navbar-user" style={{color: 'var(--ot-header-fg)'}}>{text}</Text>
    )
}

/**
 * The desktop UI's shared header row.
 *
 * The desktop UI is not responsive and is not becoming responsive. This row is
 * the one exception, and only because the user menu it opens carries the
 * "Mobile version" entry - the only way back to the mobile UI from here, so a
 * phone that once chose the desktop UI is stranded without it (#1719, #1729).
 *
 * The narrow-width rules live in `styles/globals.css` next to `.ot-navbar`,
 * because they need a media query: at phone width the wordmark and the user's
 * name step aside so that the logo, the search box and the avatar fit on one
 * 64px line. Everything else here is width-independent - the brand and the
 * avatar never shrink, and the search box is the only thing that gives.
 */
export default function NavBar() {

    const user = useContext(UserContext);
    const userMenu = useUserMenu();

    const openUserMenu = () => {
        userMenu.setOpen(true)
    }

    return (
        <>
            <div className="ot-navbar" data-testid="nav-bar">
                <div className="ot-navbar-brand">
                    <HomeLink
                        text={
                            <Image
                                src={`/yontrack-logo.svg`}
                                alt="Yontrack Logo"
                                width={24}
                                height={24}
                            />
                        }
                    />
                    <span className="ot-navbar-wordmark">
                        <HomeLink
                            text={
                                <Image
                                    src={`/yontrack-text.svg`}
                                    alt="Yontrack"
                                    width={120}
                                    height={24}
                                />
                            }
                        />
                    </span>
                </div>
                <div className="ot-navbar-actions">
                    <NavBarSearch
                        style={{display: 'flex', alignItems: 'center'}}
                    />
                    <NavBarText text={user?.fullName ?? user?.email}/>
                    <Avatar icon={<FaRegUser id="user-menu"/>}
                            onClick={openUserMenu}
                            data-testid="user-menu-trigger"
                            className="ot-navbar-avatar"
                            style={{
                                backgroundColor: 'var(--ot-header-avatar-bg)',
                                color: 'var(--ot-header-avatar-fg)',
                                cursor: 'pointer',
                            }}
                    />
                </div>
            </div>

            <UserMenu userMenu={userMenu}/>
        </>
    )
}
