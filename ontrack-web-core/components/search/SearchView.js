import {useRefData} from "@components/providers/RefDataProvider";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import {homeUri} from "@components/common/Links";
import {CloseCommand} from "@components/common/Commands";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import MainPage from "@components/layouts/MainPage";
import {Button, Popover, Skeleton, Space, Typography} from "antd";
import {useContext, useEffect, useState} from "react";
import {gql} from "graphql-request";
import SearchResultList from "@components/search/SearchResultList";
import SearchResultType from "@components/search/SearchResultType";
import {FaBan} from "react-icons/fa";
import {useRouter} from "next/router";
import {conditionalPlural} from "@components/common/TextUtils";
import LoadMoreButton from "@components/common/LoadMoreButton";
import {SearchContext} from "@components/search/SearchContext";
import SearchInput from "@components/search/SearchInput";

export default function SearchView() {

    const router = useRouter()
    const {q, type} = router.query

    const {setActive} = useContext(SearchContext)
    useEffect(() => {
        setActive(true)
        return () => {
            setActive(false)
        }
    }, [])

    const client = useGraphQLClient()
    const [searching, setSearching] = useState(true)
    const [selectedType, setSelectedType] = useState(type)
    const [results, setResults] = useState([])

    const {searchResultTypes} = useRefData()

    const [pagination, setPagination] = useState({
        offset: 0,
        size: 10,
    })

    const [pageInfo, setPageInfo] = useState({})

    const onLoadMore = () => {
        if (pageInfo.nextPage) {
            setPagination(pageInfo.nextPage)
        }
    }

    useEffect(() => {
        if (client && q) {
            setSearching(true)
            client.request(
                gql`
                    query SearchWithType($type: String, $q: String!, $offset: Int!, $size: Int!) {
                        search(offset: $offset, size: $size, type: $type, token: $q) {
                            pageInfo {
                                totalSize
                                nextPage {
                                    offset
                                    size
                                }
                            }
                            pageItems {
                                type {
                                    id
                                    name
                                    description
                                }
                                title
                                description
                                data
                                accuracy
                                page
                            }
                        }
                    }
                `,
                {
                    q: q,
                    type: selectedType,
                    ...pagination,
                }
            ).then(data => {
                setPageInfo(data.search.pageInfo)
                if (pagination.offset > 0) {
                    setResults((results) => [...results, ...data.search.pageItems])
                } else {
                    setResults(data.search.pageItems)
                }
            }).finally(() => {
                setSearching(false)
            })
        }
    }, [client, q, selectedType, pagination])

    const selectType = (type) => {
        setSelectedType(type)
        const query = {q}
        if (type) {
            query.type = type
        }
        router.replace({
            pathname: '/search',
            query: query
        }, undefined, {shallow: true})
    }

    return (
        <>
            <Head>
                {pageTitle("Search")}
            </Head>
            <MainPage
                pageId="search"
                title="Search"
                breadcrumbs={homeBreadcrumbs()}
                commands={[
                    <CloseCommand key="close" href={homeUri()}/>,
                ]}
            >
                <Skeleton active loading={searching}>
                    <Space orientation="vertical" className="ot-line">
                        <SearchInput
                            searching={searching}
                            q={q}
                        />
                        <Space orientation="horizontal" wrap className="ot-line">
                            <Button
                                style={{height: 48}}
                                onClick={() => selectType('')}
                                icon={<FaBan/>}
                                type={selectedType === '' ? "primary" : "default"}
                            >
                                All types
                            </Button>
                            {
                                searchResultTypes.map(type => (
                                    <>
                                        <Popover
                                            key={type.id}
                                            title={`Filters results on ${type.name}`}
                                        >
                                            <Button
                                                style={{height: 48}}
                                                onClick={() => selectType(type.id)}
                                                type={selectedType === type.id ? "primary" : "default"}
                                            >
                                                <SearchResultType type={type} displayName={true} popover={false}/>
                                            </Button>
                                        </Popover>
                                    </>
                                ))
                            }
                        </Space>
                        <Typography.Title level={4} type="secondary">
                            {results.length} {conditionalPlural(results.length, "result")}
                        </Typography.Title>
                        <SearchResultList results={results}/>
                        <LoadMoreButton
                            pageInfo={pageInfo}
                            moreText="There are more search results..."
                            noMoreText="There are no more search results."
                            onLoadMore={onLoadMore}
                        />
                    </Space>
                </Skeleton>
            </MainPage>
        </>
    )
}