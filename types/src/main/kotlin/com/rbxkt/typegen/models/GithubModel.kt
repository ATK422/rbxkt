package com.rbxkt.typegen.models

import kotlinx.serialization.Serializable

@Serializable
internal data class GithubTree(
    val tree: List<GithubTreeItem>,
    val truncated: Boolean,
) {
    @Serializable
    internal data class GithubTreeItem(
        val path: String,
        val type: String,
    )
}
