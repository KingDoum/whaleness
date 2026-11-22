package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.android.localization.appText
import com.clarklevis.dsh.shared.domain.SessionSummary

private const val COLLAPSED_SECTION_LIMIT = 5
private const val COLLAPSED_RECENT_LIMIT = 12
internal const val UNGROUPED_SCENE_SECTION_ID = "__ungrouped_scene__"

internal data class SceneSessionSection(
    val agentPresetId: String,
    val title: String
)

/** 主页会话的懒加载条目；全部场景按 Agent 预设分区，展开前后 key 保持稳定。 */
internal sealed interface WorkspaceSessionListEntry {
    val key: String

    data class Header(
        val sectionId: String,
        val title: String,
        val number: Int,
        val count: Int,
        val agentPresetId: String? = null
    ) : WorkspaceSessionListEntry {
        override val key: String get() = "section-$sectionId"
    }

    data class Session(val session: SessionSummary) : WorkspaceSessionListEntry {
        override val key: String get() = "session-${session.id}"
    }

    data class Toggle(val sectionId: String, val remainingCount: Int, val expanded: Boolean) : WorkspaceSessionListEntry {
        override val key: String get() = "toggle-$sectionId"
    }
}

/** 单工作区和未分组保留十二条预览，展开后使用同一外层懒加载列表。 */
internal fun recentWorkspaceSessionEntries(
    sessions: List<SessionSummary>,
    scopeId: String,
    expandedSectionIds: Set<String> = emptySet()
): List<WorkspaceSessionListEntry> {
    val toggleId = "recent-$scopeId"
    val expanded = toggleId in expandedSectionIds
    return buildList {
        val visible = if (expanded) sessions else sessions.take(COLLAPSED_RECENT_LIMIT)
        visible.forEach { add(WorkspaceSessionListEntry.Session(it)) }
        if (sessions.size > COLLAPSED_RECENT_LIMIT) {
            add(WorkspaceSessionListEntry.Toggle(toggleId, sessions.size - COLLAPSED_RECENT_LIMIT, expanded))
        }
    }
}

/**
 * 全部场景目录。已知预设即使暂时没有会话也保留为可进入的空场景；
 * 搜索时隐藏空场景，避免结果页被空目录占满。
 */
internal fun allSceneSessionEntries(
    sessions: List<SessionSummary>,
    scenes: List<SceneSessionSection>,
    expandedSectionIds: Set<String> = emptySet(),
    includeEmptyScenes: Boolean = true
): List<WorkspaceSessionListEntry> {
    val scenesById = linkedMapOf<String, SceneSessionSection>()
    scenes.forEach { scene ->
        val id = scene.agentPresetId.trim()
        if (id.isNotEmpty()) scenesById.putIfAbsent(id, scene.copy(agentPresetId = id))
    }
    val sorted = sessions.sortedWith(
        compareByDescending<SessionSummary> { it.lastActivityEpochSeconds }.thenBy { it.id }
    )
    val byPreset = sorted.groupBy { it.agentPreset?.trim()?.takeIf(String::isNotEmpty) }
    byPreset.keys.filterNotNull().forEach { presetId ->
        scenesById.putIfAbsent(presetId, SceneSessionSection(presetId, presetId))
    }

    val ordered = scenesById.values.map { scene -> scene to byPreset[scene.agentPresetId].orEmpty() } +
        (listOf<Pair<SceneSessionSection?, List<SessionSummary>>>(null to byPreset[null].orEmpty()))

    return buildList {
        ordered.forEachIndexed { index, (scene, rows) ->
            if (scene == null) {
                if (rows.isEmpty()) return@forEachIndexed
                val expanded = UNGROUPED_SCENE_SECTION_ID in expandedSectionIds
                add(WorkspaceSessionListEntry.Header(UNGROUPED_SCENE_SECTION_ID, appText("未分组"), index + 1, rows.size))
                val visible = if (expanded) rows else rows.take(COLLAPSED_SECTION_LIMIT)
                visible.forEach { add(WorkspaceSessionListEntry.Session(it)) }
                if (rows.size > COLLAPSED_SECTION_LIMIT) {
                    add(WorkspaceSessionListEntry.Toggle(
                        UNGROUPED_SCENE_SECTION_ID,
                        rows.size - COLLAPSED_SECTION_LIMIT,
                        expanded
                    ))
                }
                return@forEachIndexed
            }
            if (!includeEmptyScenes && rows.isEmpty()) return@forEachIndexed
            val expanded = scene.agentPresetId in expandedSectionIds
            add(WorkspaceSessionListEntry.Header(
                sectionId = scene.agentPresetId,
                title = scene.title.ifBlank { scene.agentPresetId },
                number = index + 1,
                count = rows.size,
                agentPresetId = scene.agentPresetId
            ))
            val visible = if (expanded) rows else rows.take(COLLAPSED_SECTION_LIMIT)
            visible.forEach { add(WorkspaceSessionListEntry.Session(it)) }
            if (rows.size > COLLAPSED_SECTION_LIMIT) {
                add(WorkspaceSessionListEntry.Toggle(
                    scene.agentPresetId,
                    rows.size - COLLAPSED_SECTION_LIMIT,
                    expanded
                ))
            }
        }
    }
}

@Composable
internal fun WorkspaceSessionSectionHeader(
    entry: WorkspaceSessionListEntry.Header,
    onCreateSession: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = if (entry.number == 1) 8.dp else 24.dp, bottom = 8.dp)
            .semantics {
                heading()
                contentDescription = if (entry.count == 1) appText("场景 %1\$s，1 个会话", entry.title)
                    else appText("场景 %1\$s，%2\$s 个会话", entry.title, entry.count)
            }
            .testTag("scene-section-${entry.sectionId}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.title, modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.62f),
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val presetId = entry.agentPresetId
        if (presetId != null && onCreateSession != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(role = Role.Button) { onCreateSession(presetId) }
                    .semantics {
                        contentDescription = appText("在“%1\$s”场景新建会话", entry.title)
                    }
                    .testTag("scene-section-create-${entry.sectionId}"),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White.copy(alpha = 0.78f), fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
        HorizontalDivider(Modifier.width(28.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        Text(appText("%1\$s 个会话", entry.count), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
    }
}

@Composable
internal fun WorkspaceSessionSectionToggle(entry: WorkspaceSessionListEntry.Toggle, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button) { onToggle(entry.sectionId) }
            .padding(start = 18.dp)
            .testTag("scene-section-toggle-${entry.sectionId}"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (entry.expanded) appText("收起") else appText("展开其余 %1\$s 个会话", entry.remainingCount),
            color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
        Icon(
            painter = painterResource(if (entry.expanded) R.drawable.ic_chevron_up else R.drawable.ic_question_chevron_down),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(14.dp)
        )
    }
}
