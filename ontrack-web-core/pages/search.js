import MainLayout from "@components/layouts/MainLayout";
import SearchResultsView from "@components/search/results/SearchResultsView";

export default function SearchPage() {
    return (
        <>
            <main>
                <MainLayout>
                    <SearchResultsView/>
                </MainLayout>
            </main>
        </>
    )
}
