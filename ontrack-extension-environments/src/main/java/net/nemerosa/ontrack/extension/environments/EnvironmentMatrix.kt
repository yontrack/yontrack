package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.graphql.support.ListRef
import net.nemerosa.ontrack.model.structure.Project

/**
 * What the matrix home is asked for: projects down, environments across.
 *
 * The screen it serves reads "what is running where" in one glance, which is the one question the
 * environments list could not answer. The shape below is therefore the *screen's* shape and not the
 * data model's: a list of environments to use as columns, a page of projects to use as rows, and,
 * under each project, one row per qualifier.
 *
 * @property environments The visible columns, by [Environment.order]. Only the environments holding
 *   at least one slot among the rows of this page: a column no row can fill is noise, and the
 *   matrix is already wide.
 * @property projects The rows of this page, by project name.
 * @property totalProjects How many projects match the filter altogether, so a reader knows there is
 *   more and the pager knows how much.
 * @property offset Where this page starts.
 * @property size How many projects were asked for.
 * @property hasFavourites Whether the current user has *any* favourite project. The toolbar opens on
 *   Favourites and falls back to All for somebody who has never starred anything - which the screen
 *   cannot tell apart from "your favourites have no slot" without being told.
 */
data class EnvironmentMatrix(
    val environments: List<Environment>,
    val projects: List<EnvironmentMatrixProject>,
    val totalProjects: Int,
    val offset: Int,
    val size: Int,
    val hasFavourites: Boolean,
)

/**
 * One project of the matrix, with its qualifier rows.
 *
 * A project with only the default qualifier has exactly one row, whose qualifier is the empty
 * string - that row *is* the project row on screen. A project with several has one row per
 * qualifier, nested under the project's name.
 */
data class EnvironmentMatrixProject(
    val project: Project,
    val rows: List<EnvironmentMatrixRow>,
)

/**
 * One row of the matrix: a project and a qualifier, across the environments.
 *
 * @property qualifier The qualifier, the empty string being the default one.
 * @property slots The slots of this project and qualifier, by environment order. A row carries only
 *   the slots which exist: the screen leaves the other cells *empty* rather than drawing a dash,
 *   because "there is no such slot" and "there is a slot and nothing is in it" are different
 *   answers and only the second one is a slot cell.
 */
data class EnvironmentMatrixRow(
    val qualifier: String,
    val slots: List<Slot>,
)

/**
 * What narrows the matrix down.
 *
 * Every one of these is applied by the *server*, before paging, because paging over projects is
 * only correct if the filters have already been applied: a page of twenty projects of which the
 * client then hides eighteen is not a page.
 *
 * @property project A fragment of a project name, matched without case. The toolbar's search box.
 * @property projects Exact project names. What a *widget* pins itself to, as opposed to [project]
 *   which is what a person types: a dashboard says "these three projects" and means those three.
 * @property environments Exact environment names, restricting the columns. The one-environment
 *   widget is a matrix of one column, and this is what makes it one.
 * @property favourites Keep only the current user's favourite projects.
 * @property label Keep only the projects carrying this label (its id).
 * @property tags Keep only the environments carrying at least one of these tags - a column filter,
 *   which then also removes the projects left with no slot at all.
 * @property activity Keep only what has something going on. *In flight or blocked*, which is one
 *   condition and not two: a slot is never blocked unless something is in flight on it
 *   ([net.nemerosa.ontrack.extension.environments.service.SlotStatusService.isBlocked] answers false
 *   for an idle slot), so "in flight" is the whole of it and it is a plain SQL condition rather than
 *   a check that would have to be run per slot before paging.
 */
data class EnvironmentMatrixFilter(
    val project: String? = null,
    @ListRef
    val projects: List<String>? = emptyList(),
    @ListRef
    val environments: List<String>? = emptyList(),
    val favourites: Boolean? = false,
    val label: Int? = null,
    @ListRef
    val tags: List<String>? = emptyList(),
    val activity: Boolean? = false,
)

/**
 * Every criterion left out means "do not filter on it", so an absent flag is a false one.
 *
 * Extension properties rather than members: [EnvironmentMatrixFilter] is turned into a GraphQL input
 * type by reflection over its member properties, and a computed member would show up there as a
 * field callers could set.
 */
val EnvironmentMatrixFilter.onFavourites: Boolean get() = favourites == true

/** See [onFavourites]. */
val EnvironmentMatrixFilter.onActivity: Boolean get() = activity == true

/** See [onFavourites]. */
val EnvironmentMatrixFilter.onTags: List<String> get() = tags.orEmpty()

/** See [onFavourites]. */
val EnvironmentMatrixFilter.onProjects: List<String> get() = projects.orEmpty()

/** See [onFavourites]. */
val EnvironmentMatrixFilter.onEnvironments: List<String> get() = environments.orEmpty()
