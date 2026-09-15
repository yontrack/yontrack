package net.nemerosa.ontrack.extension.bitbucket.cloud.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Representation of a "repository" in Bitbucket Cloud
 *
 * @property uuid Technical ID for the repository
 * @property slug Repository slug
 * @property name Display name for the repository
 * @property updated_on Last updated date
 * @property created_on Creation date
 * @property project Associated Bitbucket Cloud project
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class BitbucketCloudRepository(
    val uuid: String,
    val slug: String,
    val name: String,
    val updated_on: String,
    val created_on: String,
    val project: BitbucketCloudProject,
    val mainbranch: BitbucketCloudBranchName? = null,
)
