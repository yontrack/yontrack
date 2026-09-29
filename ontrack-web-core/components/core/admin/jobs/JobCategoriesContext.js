import {createContext, useState} from "react";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const JobCategoriesContext = createContext({
    categories: [],
    selectedCategory: '',
    setSelectedCategory: () => {
    },
})

export default function JobCategoriesContextProvider({children}) {

    const [selectedCategory, setSelectedCategory] = useState('')

    const {data: categories} = useQuery(
        gql`
            query JobCategories {
                jobCategories {
                    key
                    name
                    types {
                        key
                        name
                    }
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.jobCategories,
        }
    )

    const context = {
        categories: categories ?? [],
        selectedCategory,
        setSelectedCategory,
    }

    return (
        <JobCategoriesContext.Provider value={context}>
            {children}
        </JobCategoriesContext.Provider>
    )
}