import MainLayout from "@components/layouts/MainLayout";
import SetupView from "@components/extension/environments/setup/SetupView";

export default function EnvironmentsSetupPage() {
    return (
        <>
            <main>
                <MainLayout>
                    <SetupView/>
                </MainLayout>
            </main>
        </>
    )
}
