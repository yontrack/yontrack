import 'antd/dist/reset.css'
import '@/styles/globals.css'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'
import 'reactflow/dist/style.css'
import UserContextProvider from "@components/providers/UserProvider";
import EventsContextProvider from "@components/common/EventsContext";
import PreferencesContextProvider from "@components/providers/PreferencesProvider";
import RefDataContextProvider from "@components/providers/RefDataProvider";
import Head from "next/head";
import {useRouter} from "next/router";
import SearchContextProvider from "@components/search/SearchContext";
import {SessionProvider} from "next-auth/react"
// Ace editors modes & themes
import 'ace-builds/src-noconflict/ace';
import 'ace-builds/src-noconflict/mode-yaml';
import 'ace-builds/src-noconflict/mode-json';
import 'ace-builds/src-noconflict/theme-github';
import 'ace-builds/src-noconflict/ext-searchbox';
import MessageContextProvider from "@components/providers/MessageProvider";
import AuthProvider from "@components/providers/AuthProvider";
import LoadingAggregator from "@components/providers/LoadingAggregator";

export default function App({Component, pageProps}) {

    const router = useRouter()

    return (
        <>
            <Head>
                <link rel="shortcut icon" href={`${router.basePath}/favicon.ico`}/>
            </Head>
            <SessionProvider>
                <MessageContextProvider>
                    <AuthProvider>
                        <UserContextProvider>
                            <PreferencesContextProvider>
                                <RefDataContextProvider>
                                    <SearchContextProvider>
                                        <EventsContextProvider>
                                            <LoadingAggregator>
                                                <Component {...pageProps} />
                                            </LoadingAggregator>
                                        </EventsContextProvider>
                                    </SearchContextProvider>
                                </RefDataContextProvider>
                            </PreferencesContextProvider>
                        </UserContextProvider>
                    </AuthProvider>
                </MessageContextProvider>
            </SessionProvider>
        </>
    )
}
