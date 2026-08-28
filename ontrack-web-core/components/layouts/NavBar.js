import {Avatar, Space, Typography} from "antd";
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
        <Text style={{color: 'var(--ot-header-fg)'}}>{text}</Text>
    )
}

export default function NavBar() {

    const user = useContext(UserContext);
    const userMenu = useUserMenu();

    const openUserMenu = () => {
        userMenu.setOpen(true)
    }

    return (
        <>
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
            }}>
                <Space direction="horizontal" size={16}>
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
                </Space>
                <Space direction="horizontal" size={8}>
                    <NavBarSearch
                        style={{display: 'flex', alignItems: 'center'}}
                    />
                    <NavBarText text={user?.fullName ?? user?.email}/>
                    <Avatar icon={<FaRegUser id="user-menu"/>}
                            onClick={openUserMenu}
                            style={{
                                backgroundColor: 'var(--ot-header-avatar-bg)',
                                color: 'var(--ot-header-avatar-fg)',
                                cursor: 'pointer',
                            }}
                    />
                </Space>
            </div>

            <UserMenu userMenu={userMenu}/>
        </>
    )
}