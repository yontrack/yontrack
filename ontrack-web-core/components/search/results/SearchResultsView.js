import Head from "next/head";
import Link from "next/link";
import {useRouter} from "next/router";
import {gql} from "graphql-request";
import {Alert, Button, Input, Pagination, Skeleton, Space, Spin, Typography} from "antd";
import {pageTitle} from "@components/common/Titles";
import {homeUri} from "@components/common/Links";
import {CloseCommand} from "@components/common/Commands";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import MainPage from "@components/layouts/MainPage";
import ItemList from "@components/common/ItemList";
import {Dynamic} from "@components/common/Dynamic";
import {useQuery} from "@components/services/GraphQL";
import {useRefData} from "@components/providers/RefDataProvider";
import {MIN_SEARCH_LENGTH} from "@components/search/palette/paletteSections";
import {searchResultHref} from "@components/search/palette/searchResultHref";
import {resultContext} from "@components/search/searchResultContext";
import {PAGE_SIZE, searchResultsParams, searchResultsRoute} from "@components/search/results/searchResultsParams";
import {highlightText} from "@components/search/results/highlightText";
import HighlightedText from "@components/search/results/HighlightedText";

/**
 * One request for the page: the results of the page, and - when they are filtered on a type - the
 * results of all the types, for the counts of the filters. The facets only count the types the
 * search is asked for.
 *
 * The `highlight` of the free text is computed by the server for the results of the page only.
 */
const SEARCH_QUERY = gql`
    query SearchResultsPage($query: String!, $types: [String!], $offset: Int!, $size: Int!, $filtered: Boolean!) {
        all: search(query: $query, size: 0) @include(if: $filtered) {
            total
            facets {
                type {
                    id
                    name
                    description
                }
                count
            }
        }
        page: search(query: $query, types: $types, offset: $offset, size: $size) {
            total
            message
            facets {
                type {
                    id
                    name
                    description
                }
                count
            }
            items {
                type {
                    id
                    name
                    description
                }
                title
                description
                data
                highlight {
                    text
                    match
                }
            }
        }
    }
`

const plural = (count) => count === 1 ? '1 result' : `${count} results`

/**
 * One result: the icon of its type, its title - highlighted, linking to its page - its type and
 * where it is, and an excerpt of its free text.
 */
function SearchResultItem({result, q}) {
    const href = searchResultHref(result)
    const title = <HighlightedText parts={highlightText(result.title, q)}/>
    const context = resultContext(result)
    return (
        <ItemList.Item
            data-testid="search-result"
            avatar={
                <span aria-hidden="true">
                    <Dynamic path={`framework/search/${result.type.id}/Icon`}/>
                </span>
            }
            title={
                <Space size="small" wrap>
                    {href ? <Link href={href}>{title}</Link> : <Typography.Text strong>{title}</Typography.Text>}
                    <Typography.Text type="secondary">{result.type.name}</Typography.Text>
                    {context && <Typography.Text type="secondary">{context}</Typography.Text>}
                </Space>
            }
            description={
                result.highlight ?
                    <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                        <HighlightedText parts={result.highlight}/>
                    </Typography.Paragraph> :
                    result.description ?
                        <Typography.Paragraph type="secondary" style={{marginBottom: 0}} ellipsis={{rows: 2}}>
                            {result.description}
                        </Typography.Paragraph> :
                        null
            }
        />
    )
}

/**
 * The filters on the type of results: all of them, then each type having results - and the type
 * of the URL even without any.
 */
function SearchTypeFilters({total, facets, type, onSelect}) {
    const {searchResultTypes = []} = useRefData()
    const filters = [...facets]
    if (type && !filters.some(facet => facet.type.id === type)) {
        const selected = searchResultTypes.find(it => it.id === type) ?? {id: type, name: type}
        filters.push({type: selected, count: 0})
    }
    return (
        <Space role="group" aria-label="Filter by type" size="small" wrap>
            <Button
                aria-pressed={!type}
                type={!type ? 'primary' : 'default'}
                onClick={() => onSelect(null)}
                data-testid="search-filter-all"
            >
                All ({total})
            </Button>
            {
                filters.map(facet => (
                    <Button
                        key={facet.type.id}
                        aria-pressed={type === facet.type.id}
                        type={type === facet.type.id ? 'primary' : 'default'}
                        onClick={() => onSelect(facet.type.id)}
                        data-testid={`search-filter-${facet.type.id}`}
                    >
                        {facet.type.name} ({facet.count})
                    </Button>
                ))
            }
        </Space>
    )
}

/**
 * The results page, `/search` (#1885).
 *
 * The URL is the one source of truth for what is shown - `q`, `type` and `page`, see
 * {@link searchResultsParams} - so that a page of results can be shared, and the back and forward
 * buttons move between searches. Every change goes through the URL.
 */
export default function SearchResultsView() {

    const router = useRouter()
    const {q, type, page} = searchResultsParams(router.query)
    const query = q.trim()
    const searchable = router.isReady && query.length >= MIN_SEARCH_LENGTH

    const {data, loading, finished} = useQuery(SEARCH_QUERY, {
        variables: {
            query,
            types: type ? [type] : null,
            offset: (page - 1) * PAGE_SIZE,
            size: PAGE_SIZE,
            filtered: !!type,
        },
        deps: [query, type, page],
        condition: searchable,
    })

    const go = (params) => router.push(searchResultsRoute({q, type, page, ...params}))

    const onSearch = (value) => {
        const text = value.trim()
        if (text) go({q: text, page: 1})
    }

    const onPage = (newPage) => {
        go({page: newPage})
        window.scrollTo({top: 0})
    }

    const results = searchable ? data?.page : null
    const facets = (type ? data?.all : results) ?? results
    const pending = searchable && (loading || !finished)

    return (
        <>
            <Head>
                {pageTitle(query ? `Search: ${query}` : "Search")}
            </Head>
            <MainPage
                pageId="search"
                title="Search"
                breadcrumbs={homeBreadcrumbs()}
                commands={[
                    <CloseCommand key="close" href={homeUri()}/>,
                ]}
            >
                <Space orientation="vertical" className="ot-line" size="middle">
                    <div role="search" style={{maxWidth: 640}}>
                        <Input.Search
                            key={q}
                            type="search"
                            aria-label="Search"
                            size="large"
                            defaultValue={q}
                            enterButton
                            allowClear
                            loading={pending}
                            onSearch={onSearch}
                            data-testid="search-results-input"
                        />
                    </div>
                    {
                        router.isReady && !searchable &&
                        <Typography.Text type="secondary">
                            Type at least {MIN_SEARCH_LENGTH} characters to search.
                        </Typography.Text>
                    }
                    {
                        results?.message &&
                        <Alert type="info" showIcon title={results.message}/>
                    }
                    {
                        searchable && !results &&
                        <Skeleton active/>
                    }
                    {
                        results &&
                        <Spin spinning={loading}>
                            <Space orientation="vertical" className="ot-line" size="middle">
                                <SearchTypeFilters
                                    total={facets?.total ?? 0}
                                    facets={facets?.facets ?? []}
                                    type={type}
                                    onSelect={(selected) => go({type: selected, page: 1})}
                                />
                                <Typography.Text type="secondary" role="status" data-testid="search-results-count">
                                    {results.total === 0 ? 'No results' : plural(results.total)}
                                </Typography.Text>
                                <ItemList
                                    aria-label="Search results"
                                    aria-busy={loading}
                                    emptyText={
                                        <Typography.Text type="secondary">
                                            No results for &quot;{query}&quot;.
                                        </Typography.Text>
                                    }
                                    data-testid="search-results"
                                >
                                    {
                                        results.items.map((result, index) => (
                                            <SearchResultItem
                                                key={`${result.type.id}-${index}`}
                                                result={result}
                                                q={query}
                                            />
                                        ))
                                    }
                                </ItemList>
                                {
                                    results.total > PAGE_SIZE &&
                                    <nav aria-label="Pages of results">
                                        <Pagination
                                            current={page}
                                            pageSize={PAGE_SIZE}
                                            total={results.total}
                                            showSizeChanger={false}
                                            onChange={onPage}
                                            itemRender={(itemPage, itemType, element) =>
                                                itemType === 'page' ?
                                                    <a aria-current={itemPage === page ? 'page' : undefined}>{itemPage}</a> :
                                                    element
                                            }
                                        />
                                    </nav>
                                }
                            </Space>
                        </Spin>
                    }
                </Space>
            </MainPage>
        </>
    )
}
