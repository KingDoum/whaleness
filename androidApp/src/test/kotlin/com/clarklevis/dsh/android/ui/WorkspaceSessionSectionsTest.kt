package com.clarklevis.dsh.android.ui

import com.clarklevis.dsh.shared.domain.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSessionSectionsTest {
    @Test
    fun recentSessionsExpandPastTwelveAndCollapseWithoutLosingRowsOrOrder() {
        val rows = sessions("recent", 113)
        val collapsed = recentWorkspaceSessionEntries(rows, "w1")
        assertEquals(rows.take(12), collapsed.filterIsInstance<WorkspaceSessionListEntry.Session>().map { it.session })
        assertEquals(WorkspaceSessionListEntry.Toggle("recent-w1", 101, false), collapsed.last())
        val expanded = recentWorkspaceSessionEntries(rows, "w1", setOf("recent-w1"))
        assertEquals(rows, expanded.filterIsInstance<WorkspaceSessionListEntry.Session>().map { it.session })
        assertEquals(WorkspaceSessionListEntry.Toggle("recent-w1", 101, true), expanded.last())
        assertEquals(expanded.size, expanded.map { it.key }.toSet().size)
        assertEquals(collapsed, recentWorkspaceSessionEntries(rows, "w1"))
        assertEquals(12, recentWorkspaceSessionEntries(rows, "w2", setOf("recent-w1"))
            .filterIsInstance<WorkspaceSessionListEntry.Session>().size)
        for (count in listOf(0, 1, 12)) {
            assertEquals(count, recentWorkspaceSessionEntries(rows.take(count), "w1").size)
        }
    }

    @Test
    fun knownScenesStayVisibleWhenEmptyAndMissingPresetFallsBackToUngrouped() {
        val entries = allSceneSessionEntries(
            sessions = listOf(session("alpha-1", 10.0, "alpha"), session("loose", 20.0, null)),
            scenes = listOf(scene("alpha"), scene("beta"))
        )

        assertEquals(
            listOf("alpha", "beta", UNGROUPED_SCENE_SECTION_ID),
            entries.filterIsInstance<WorkspaceSessionListEntry.Header>().map { it.sectionId }
        )
        assertEquals(listOf(1, 0, 1), entries.filterIsInstance<WorkspaceSessionListEntry.Header>().map { it.count })
        assertEquals("alpha", entries.filterIsInstance<WorkspaceSessionListEntry.Header>().first().agentPresetId)
        assertEquals(null, entries.filterIsInstance<WorkspaceSessionListEntry.Header>().last().agentPresetId)
    }

    @Test
    fun exactlyFiveStayVisibleWhileLargerAndUngroupedSectionsCollapse() {
        val small = sessions("small", 5, "small")
        val large = sessions("large", 6, "large")
        val loose = sessions("loose", 7, null)
        val entries = allSceneSessionEntries(
            small + large + loose,
            listOf(scene("small"), scene("large"))
        )

        assertEquals(15, entries.filterIsInstance<WorkspaceSessionListEntry.Session>().size)
        assertEquals(18, entries.filterIsInstance<WorkspaceSessionListEntry.Header>().sumOf { it.count })
        assertFalse(entries.filterIsInstance<WorkspaceSessionListEntry.Toggle>().any { it.sectionId == "small" })
        assertTrue(entries.contains(WorkspaceSessionListEntry.Toggle("large", 1, false)))
        assertTrue(entries.contains(WorkspaceSessionListEntry.Toggle(UNGROUPED_SCENE_SECTION_ID, 2, false)))
    }

    @Test
    fun expansionIsIndependentAndCollapsingRestoresTheSameRowsAndKeys() {
        val first = sessions("first", 6, "alpha")
        val second = sessions("second", 7, "beta")
        val scenes = listOf(scene("alpha"), scene("beta"))
        val collapsed = allSceneSessionEntries(first + second, scenes)
        val expanded = allSceneSessionEntries(first + second, scenes, setOf("alpha"))

        assertEquals(11, expanded.filterIsInstance<WorkspaceSessionListEntry.Session>().size)
        assertTrue(expanded.contains(WorkspaceSessionListEntry.Toggle("alpha", 1, true)))
        assertTrue(expanded.contains(WorkspaceSessionListEntry.Toggle("beta", 2, false)))
        assertEquals(expanded.size, expanded.map { it.key }.toSet().size)
        assertEquals(collapsed, allSceneSessionEntries(first + second, scenes, emptySet()))
        assertEquals(
            collapsed.map { it.key }.toSet(),
            expanded.map { it.key }.toSet() - "session-first-0"
        )
    }

    @Test
    fun catalogOrderControlsSectionsAndRowsUseLatestActivity() {
        val a = session("a", 10.0, "alpha")
        val b = session("b", 30.0, "beta")
        val c = session("c", 20.0, "alpha")
        val unknown = session("unknown", 5.0, "gamma")
        val entries = allSceneSessionEntries(
            listOf(a, c, b, unknown),
            listOf(scene("beta"), scene("alpha"))
        )

        assertEquals(
            listOf("beta", "alpha", "gamma"),
            entries.filterIsInstance<WorkspaceSessionListEntry.Header>().map { it.sectionId }
        )
        assertEquals(
            listOf("b", "c", "a", "unknown"),
            entries.filterIsInstance<WorkspaceSessionListEntry.Session>().map { it.session.id }
        )
    }

    @Test
    fun tenThousandSessionsCollapseWithoutLosingTotalsAndAllRemainAvailableWhenExpanded() {
        val scenes = (0..<100).map { scene("s$it") }
        val rows = scenes.flatMap { scene -> sessions(scene.agentPresetId, 100, scene.agentPresetId) }
        val collapsed = allSceneSessionEntries(rows, scenes)
        assertEquals(500, collapsed.filterIsInstance<WorkspaceSessionListEntry.Session>().size)
        assertEquals(10_000, collapsed.filterIsInstance<WorkspaceSessionListEntry.Header>().sumOf { it.count })
        val expanded = allSceneSessionEntries(rows, scenes, scenes.mapTo(mutableSetOf()) { it.agentPresetId })
        assertEquals(10_000, expanded.filterIsInstance<WorkspaceSessionListEntry.Session>().size)
        assertEquals(10_200, expanded.size)
        assertEquals(expanded.size, expanded.map { it.key }.toSet().size)
    }

    @Test
    fun searchCanHideEmptyScenesAndFilteredResultsKeepExactCounts() {
        val rows = sessions("alpha", 10, "alpha")
        val scenes = listOf(scene("alpha"), scene("beta"))
        val fullDirectory = allSceneSessionEntries(rows, scenes)
        assertEquals(2, fullDirectory.filterIsInstance<WorkspaceSessionListEntry.Header>().size)

        val filtered = allSceneSessionEntries(
            sessions = rows.take(2),
            scenes = scenes,
            includeEmptyScenes = false
        )
        assertEquals(2, filtered.filterIsInstance<WorkspaceSessionListEntry.Session>().size)
        assertEquals(2, filtered.filterIsInstance<WorkspaceSessionListEntry.Header>().single().count)
        assertTrue(filtered.filterIsInstance<WorkspaceSessionListEntry.Toggle>().isEmpty())
        assertTrue(allSceneSessionEntries(emptyList(), emptyList()).isEmpty())
    }

    private fun sessions(prefix: String, count: Int, agentPreset: String? = prefix): List<SessionSummary> =
        (0..<count).map { index ->
            session(
                id = "$prefix-$index",
                updatedAt = index.toDouble(),
                agentPreset = agentPreset
            )
        }

    private fun session(id: String, updatedAt: Double, agentPreset: String?): SessionSummary =
        SessionSummary(
            id = id,
            title = id,
            lastActivityEpochSeconds = updatedAt,
            isRunning = false,
            hasUnread = false,
            agentPreset = agentPreset
        )

    private fun scene(id: String) = SceneSessionSection(id, "场景 $id")
}
