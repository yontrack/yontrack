import {Empty, Space} from "antd";
import SearchResult from "@components/search/SearchResult";

export default function SearchResultList({results}) {
    return (
        <>
            {
                results.length > 0 &&
                <Space orientation="vertical">
                    {
                        results.map((result, index) => <SearchResult
                            key={index}
                            result={result}
                        />)
                    }
                </Space>
            }
            {
                results.length === 0 &&
                <Empty description={"No results found."}/>
            }
        </>
    )
}