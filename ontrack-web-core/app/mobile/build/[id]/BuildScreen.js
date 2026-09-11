"use client"

/**
 * One build, on a phone: the decision surface.
 *
 * Everything a person needs to answer "should I promote or deploy this build?"
 * and then act - what the build is, how far it has been promoted, where it is
 * deployed, whether it is green, and the two actions.
 *
 * **Validations are here on purpose**, even though validations are otherwise out
 * of scope for the mobile UI. Someone about to promote or deploy from a phone
 * needs to know whether the build passed; leaving it out would mean switching to
 * the desktop UI to check and switching back, which defeats the flow this whole
 * initiative exists to enable. They are a read-only list and nothing more - no
 * matrix, no filter, no drill-down into a run.
 *
 * The actions sit directly under the identity rather than at the foot of the
 * screen. The issue lists them last, but a validation list can be long, and a
 * user who already knows they want to promote should not have to scroll past
 * every stamp to reach the button.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import Link from "next/link"
import {useRouter} from "next/navigation"
import {Typography} from "antd"
import {useQuery} from "@components/services/GraphQL"
import MobileScreen from "@components/mobile/layout/MobileScreen"
import MobileSectionList from "@components/mobile/layout/MobileSectionList"
import MobileAsyncContent from "@components/mobile/layout/MobileAsyncContent"
import {MobileEntityRow} from "@components/mobile/entities/MobileEntityList"
import MobileBuildActions from "@components/mobile/builds/MobileBuildActions"
import {deploymentName, useMobileBuildDeployments} from "@components/mobile/builds/useMobileDeployments"
import TimestampText from "@components/common/TimestampText"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import ValidationChip from "@components/primitives/ValidationChip"
import {gqlValidationChipStamp} from "@components/primitives/ValidationChipFragments"
import {FaServer} from "react-icons/fa"
import {mobileBranchUri, mobileDeploymentUri, mobileProjectUri} from "@components/mobile/mobileRoutes"

/**
 * How many validation stamps the screen shows.
 *
 * The list is read, not paged: it is the evidence behind one decision. A build
 * with more stamps than this has a bigger problem than a phone screen - but the
 * screen must not silently show 50 of 80 and let a reader conclude the build is
 * green, so it asks for one more than it shows and says when there were more.
 */
const VALIDATION_LIMIT = 50

/** The promotion medal, at a size that reads on a phone rather than as a dot. */
const MEDAL_SIZE = 20

/** The stamp icon on a validation chip. */
const CHIP_SIZE = 20

export default function MobileBuildScreen({id}) {

    /*
     * What a promotion made from this screen does to it.
     *
     * The screen refetches rather than patching its own copy of the promotions:
     * the run's id, its signature and its place among the level's last runs are
     * the server's answer, not something the phone can invent - and
     * `promotionRuns(lastPerLevel: true)` means a second promotion to a level
     * already shown *replaces* a row rather than adding one.
     *
     * A counter in the query's `deps`, as the favourite toggles use: the mobile
     * provider stack has no `EventsContextProvider`, and a phone shows one
     * screen at a time.
     */
    const [refresh, setRefresh] = useState(0)

    const router = useRouter()

    const query = useQuery(
        gql`
            query MobileBuild($id: Int!, $validations: Int!) {
                build(id: $id) {
                    id
                    name
                    # Already the release property when there is one, and the
                    # build's own name otherwise.
                    displayName
                    description
                    creation {
                        time
                        user
                    }
                    branch {
                        id
                        name
                        displayName
                        project {
                            id
                            name
                        }
                    }
                    # What gates the two action entry points. Read exactly as the
                    # desktop UI reads it - see \`MobileBuildActions\`.
                    authorizations {
                        name
                        action
                        authorized
                    }
                    # The last run per level: a build promoted twice to the same
                    # level is still at that level once.
                    promotionRuns(lastPerLevel: true) {
                        id
                        creation {
                            time
                            user
                        }
                        promotionLevel {
                            id
                            name
                            image
                        }
                    }
                    # One entry per stamp, with its latest run - which is what
                    # makes this a list rather than the desktop's matrix.
                    validations(size: $validations) {
                        validationStamp {
                            ...ValidationChipStamp
                        }
                        validationRuns(count: 1) {
                            id
                            lastStatus {
                                creation {
                                    time
                                    user
                                }
                                statusID {
                                    id
                                    name
                                }
                            }
                        }
                    }
                }
            }
            ${gqlValidationChipStamp}
        `,
        {
            variables: {id: Number(id), validations: VALIDATION_LIMIT + 1},
            deps: [id, refresh],
        }
    )

    /*
     * No "no such build" state of its own: `build(id:)` is a non-null field, so
     * an id nobody can see comes back as a GraphQL error carrying the reason
     * rather than as a null. The error alert already says what happened.
     */
    /*
     * Deployments are a query of their own, and deliberately - see
     * `useMobileDeployments`. `currentDeployments` is absent from the schema on
     * an instance with no environments licence, which would fail the document
     * above and take the whole screen with it.
     */
    const {
        deployments,
        candidates,
        unavailable: deploymentsUnavailable,
    } = useMobileBuildDeployments(id, refresh)

    const build = query.data?.build
    const project = build?.branch?.project

    const promotions = build?.promotionRuns ?? []
    const foundValidations = build?.validations ?? []
    const validations = foundValidations.slice(0, VALIDATION_LIMIT)
    const moreValidations = foundValidations.length > VALIDATION_LIMIT

    return (
        <MobileScreen
            title={build ? (build.displayName || build.name) : "Build"}
            subtitle={
                // Both guarded: `Build.branch` is nullable in the schema, and a
                // subtitle is not worth crashing a screen over.
                build?.branch &&
                <>
                    {
                        project &&
                        <>
                            <Link href={mobileProjectUri(project.id)}>{project.name}</Link>
                            {' / '}
                        </>
                    }
                    <Link href={mobileBranchUri(build.branch.id)}>
                        {build.branch.displayName || build.branch.name}
                    </Link>
                </>
            }
        >
            <MobileAsyncContent
                state={query}
                errorMessage="Could not load the build."
                // A build always has an identity; the sections below say for
                // themselves when they are empty, and none of them being empty
                // is a state worth a screen-wide message.
                isEmpty={false}
                rows={8}
            >
                {
                    build &&
                    <div className="ot-mobile-stack">
                        <Typography.Text type="secondary" data-testid="mobile-build-created">
                            <MobileSignature signature={build.creation}/>
                        </Typography.Text>
                        {
                            build.description &&
                            <Typography.Text data-testid="mobile-build-description">
                                {build.description}
                            </Typography.Text>
                        }

                        <MobileBuildActions
                            build={build}
                            onPromotion={() => setRefresh(count => count + 1)}
                            /*
                             * A deployment starts as a CANDIDATE: nothing has
                             * happened to the environment yet, and everything
                             * that still has to happen - the input a rule wants,
                             * an override, the run itself - is on the deployment
                             * screen. Taking the user straight there is the flow
                             * they asked for by tapping Deploy; landing them
                             * back on this screen with a new row to notice is
                             * not.
                             *
                             * A `push`, so the back gesture returns to the build
                             * rather than leaving the phone on a screen it has
                             * no way out of.
                             */
                            onDeployment={pipelineId => {
                                setRefresh(count => count + 1)
                                if (pipelineId) router.push(mobileDeploymentUri(pipelineId))
                            }}
                        />

                        <MobileSectionList
                            title="Promotions"
                            testId="mobile-build-promotions"
                            isEmpty={promotions.length === 0}
                            empty="This build has not been promoted."
                        >
                            {
                                promotions.map(run =>
                                    <MobileEntityRow
                                        key={run.id}
                                        testId={`mobile-build-promotion-${run.id}`}
                                        name={
                                            <span className="ot-mobile-inline">
                                                <PromotionLevelImage
                                                    promotionLevel={run.promotionLevel}
                                                    size={MEDAL_SIZE}
                                                />
                                                {run.promotionLevel?.name}
                                            </span>
                                        }
                                        context={<MobileSignature signature={run.creation}/>}
                                    />
                                )
                            }
                        </MobileSectionList>

                        {
                            /*
                             * Deployments of this build which are still waiting
                             * on somebody - a manual approval, an override, or
                             * simply the run.
                             *
                             * Absent rather than empty, because unlike the three
                             * sections below it is not a facet of the build: a
                             * build with nothing pending has nothing to say
                             * here, and "no deployment is waiting" on every
                             * build screen would be a line nobody reads. It is
                             * also the only way into the deployment screen for a
                             * deployment somebody else started - CI usually -
                             * which is half of what #1725 is about.
                             */
                            candidates.length > 0 &&
                            <MobileSectionList
                                title="Waiting to deploy"
                                testId="mobile-build-candidates"
                                isEmpty={false}
                            >
                                {
                                    candidates.map(pipeline =>
                                        <MobileEntityRow
                                            key={pipeline.id}
                                            testId={`mobile-build-candidate-${pipeline.id}`}
                                            href={mobileDeploymentUri(pipeline.id)}
                                            name={
                                                <span className="ot-mobile-inline">
                                                    <FaServer aria-hidden="true"/>
                                                    {deploymentName(pipeline)}
                                                </span>
                                            }
                                            context={
                                                <TimestampText
                                                    value={pipeline.start}
                                                    prefix="started"
                                                    relative
                                                />
                                            }
                                        />
                                    )
                                }
                            </MobileSectionList>
                        }

                        <MobileSectionList
                            title="Deployments"
                            testId="mobile-build-deployments"
                            isEmpty={deployments.length === 0}
                            empty={
                                // Three states, not two. "Nowhere" is a fact
                                // about the build; "unavailable" is a fact about
                                // the instance, and saying the first for the
                                // second would be a lie.
                                deploymentsUnavailable
                                    ? "Deployments are not available on this instance."
                                    : "This build is not deployed anywhere."
                            }
                        >
                            {
                                deployments.map(pipeline =>
                                    <MobileEntityRow
                                        key={pipeline.id}
                                        testId={`mobile-build-deployment-${pipeline.id}`}
                                        name={
                                            <span className="ot-mobile-inline">
                                                <FaServer aria-hidden="true"/>
                                                {/*
                                                  Environment *and* qualifier: a
                                                  project can have two slots in
                                                  one environment and only the
                                                  qualifier tells them apart -
                                                  see `deploymentName`.
                                                */}
                                                {deploymentName(pipeline)}
                                            </span>
                                        }
                                        context={
                                            pipeline.end &&
                                            <TimestampText value={pipeline.end} prefix="deployed" relative/>
                                        }
                                    />
                                )
                            }
                        </MobileSectionList>

                        <MobileSectionList
                            title="Validations"
                            testId="mobile-build-validations"
                            isEmpty={validations.length === 0}
                            empty="This build has no validation."
                        >
                            {
                                validations.map(validation =>
                                    <li
                                        key={validation.validationStamp?.id}
                                        className="ot-mobile-row ot-mobile-row-chip"
                                    >
                                        {/*
                                          Not a `MobileEntityRow`: the chip is a
                                          bordered box that needs the row's whole
                                          width, rather than a name on one line
                                          with a context under it.

                                          It carries the stamp, the status in
                                          words and the status's own glyph, so
                                          the state survives greyscale. No href
                                          and no onClick: run detail is out of
                                          scope.
                                        */}
                                        <ValidationChip
                                            id={`mobile-build-validation-${validation.validationStamp?.id}`}
                                            validationStamp={validation.validationStamp}
                                            statusID={validation.validationRuns?.[0]?.lastStatus?.statusID}
                                            size={CHIP_SIZE}
                                        />
                                        <span className="ot-mobile-row-context">
                                            <TimestampText
                                                value={validation.validationRuns?.[0]?.lastStatus?.creation?.time}
                                                relative
                                            />
                                        </span>
                                    </li>
                                )
                            }
                        </MobileSectionList>
                        {
                            moreValidations &&
                            <Typography.Text type="secondary" data-testid="mobile-build-validations-truncated">
                                {`Only the first ${VALIDATION_LIMIT} validations are shown.`}
                            </Typography.Text>
                        }
                    </div>
                }
            </MobileAsyncContent>
        </MobileScreen>
    )
}

/**
 * "6 days ago by alice" - when something happened and who did it.
 *
 * Both halves of a `Signature`, and both optional: the user is dropped rather
 * than rendered as "by undefined" when the server did not record one.
 */
function MobileSignature({signature}) {
    return (
        <>
            <TimestampText value={signature?.time} relative/>
            {signature?.user ? ` by ${signature.user}` : ''}
        </>
    )
}
