package com.cydoniancitizen.bingee.testutil

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode

/**
 * Statistics and Your Bingee lay their sections out in a LazyColumn, so a section below the fold is
 * not composed at all until the list scrolls to it, and a node just above the fold is composed but
 * not displayed. Scrolling the list itself covers both cases; scrolling to a sibling node does not.
 *
 * The screens contain exactly one vertically scrollable node, so the axis range identifies it
 * without a test tag and without touching the accessibility semantics of the content.
 */
internal fun SemanticsNodeInteractionsProvider.scrollListTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
    // Scrolling the lazy list only aligns the containing item, which is not enough for a node deep
    // inside a tall item, so bring the node itself into view afterwards.
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .performScrollToNode(matcher)
    return onNode(matcher).performScrollTo()
}

/**
 * Index of the ratings section inside StatisticsContent's LazyColumn, which lays out taste, viewing,
 * genres and ratings in that order.
 */
internal const val STATISTICS_RATINGS_ITEM = 3

/**
 * Re-aligns the lazy list on one item. Selecting a rating expands a shelf underneath the histogram,
 * and a lazy list keeps a stale scroll extent for content added since its last scroll, which leaves
 * the new shelf clipped with empty bounds that [scrollListTo]'s viewport maths cannot act on.
 * Scrolling by item index refreshes the extent and puts the grown section back in view.
 */
internal fun SemanticsNodeInteractionsProvider.scrollListToItem(index: Int) {
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .performScrollToIndex(index)
}

/**
 * The rating shelf lays its posters out in a LazyRow, so a poster past the fold is not composed and
 * cannot be reached by scrolling a sibling node. It is the only horizontal lazy list on these
 * screens: the chart rows use `horizontalScroll` and expose no scroll-to-index action, and the only
 * other list carrying that action scrolls vertically.
 */
internal fun SemanticsNodeInteractionsProvider.scrollRowTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
    onNode(
        SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)
    ).performScrollToNode(matcher)
    return onNode(matcher)
}
