import {createContext, useContext, useEffect, useState} from "react";
import {gql} from "graphql-request";
import {useMutation, useQuery} from "@components/services/GraphQL";

export const PreferencesContext = createContext({
    branchViewVsGroups: null,
    branchViewVsNames: null,
    dashboardUuid: null,
    selectedBranchViewKey: null,
    themeMode: null,
    setPreferences: () => {
    },
    loaded: false,
})

export default function PreferencesContextProvider({children}) {

    const [loaded, setLoaded] = useState(false)

    const [preferencesRecord, setPreferencesRecord] = useState({
        branchViewVsGroups: null,
        branchViewVsNames: null,
        dashboardUuid: null,
        selectedBranchViewKey: null,
        themeMode: null,
    })

    const {data, loading, error, finished} = useQuery(
        gql`
            query GetPreferences {
                preferences {
                    branchViewVsGroups
                    branchViewVsNames
                    dashboardUuid
                    selectedBranchViewKey
                    themeMode
                }
            }
        `,
        {
            dataFn: data => data.preferences
        }
    )

    useEffect(() => {
        if (data && finished) {
            setPreferencesRecord(data)
            setLoaded(true)
        }
    }, [data, finished])

    const {mutate} = useMutation(
        gql`
            mutation SetPreferences($input: SetPreferencesInput!) {
                setPreferences(input: $input) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'setPreferences'
        }
    )

    const setPreferences = async (values) => {
        await mutate({input: values})
        setPreferencesRecord({
            ...preferencesRecord,
            ...values,
        })
    }

    const contextValue = {
        ...preferencesRecord,
        setPreferences,
        loaded,
    }

    return <>
        {
            !loading &&
            <PreferencesContext.Provider value={contextValue}>{children}</PreferencesContext.Provider>
        }
    </>
}

export function usePreferences() {
    return useContext(PreferencesContext)
}