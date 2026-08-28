import AceEditor from "react-ace";
import {useAceTheme} from "@components/theme/useAceTheme";

export default function Yaml({yaml, height = '32em', showLineNumbers = false}) {
    return <AceEditor
        mode="yaml"
        theme={useAceTheme()}
        value={yaml}
        readOnly={true}
        showPrintMargin={false}
        width="100%"
        height={height}
        setOptions={{
            showLineNumbers: showLineNumbers
        }}
    />
}