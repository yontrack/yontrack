import {createContext, useEffect, useState} from "react";
import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";

export const UserContext = createContext({
    name: '',
    fullName: '',
    email: '',
    authorizations: {},
    userMenuGroups: [],
    profile: {
        auth: {
            account: {
                url: ''
            }
        }
    }
})

export default function UserContextProvider({children}) {

    const [user, setUser] = useState({
        name: '',
        fullName: '',
        email: '',
        authorizations: {},
        userMenuGroups: [],
        profile: {
            auth: {
                account: {
                    url: ''
                }
            }
        }
    })

    const {data, loading, error, finished} = useQuery(
        gql`
            query UserContext {
                user {
                    account {
                        fullName
                        email
                    }
                }
                userMenuItems {
                    id
                    name
                    items {
                        extension
                        id
                        name
                    }
                }
                authorizations {
                    name
                    action
                    authorized
                }
            }
        `
    )

    const [profile, setProfile] = useState()

    useEffect(() => {
        fetch('/api/protected/profile')
            .then(data => data.json())
            .then(profile => setProfile(profile))
    }, [])

    useEffect(() => {
        if (data && profile && finished) {
            const tmpUser = {
                name: data?.user?.account?.email,
                fullName: data?.user?.account?.fullName,
                email: data?.user?.account?.email,
            }
            // Groups
            tmpUser.userMenuGroups = data.userMenuItems
            // Indexing of authorizations
            const authorizations = data.authorizations
            tmpUser.authorizations = {}
            authorizations.forEach(authorization => {
                let domain = tmpUser.authorizations[authorization.name]
                if (!domain) {
                    domain = {}
                    tmpUser.authorizations[authorization.name] = domain
                }
                domain[authorization.action] = authorization.authorized
            })
            // Profile
            tmpUser.profile = profile
            // We're done
            setUser(tmpUser)
        }
    }, [data, profile, finished])

    return (
        <>
            {
                !loading && user?.name &&
                <UserContext.Provider value={user}>{children}</UserContext.Provider>
            }
        </>
    )

}
