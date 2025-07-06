package com.rbxkt.typegen.models

import kotlinx.serialization.Serializable

@Serializable
internal data class GithubFileTreeResponse(
    val payload: GithubFileTreePayload
) {
    @Serializable
    internal data class GithubFileTreePayload(
        val tree: GithubTree
    )
}

@Serializable
internal data class GithubTree(
    val totalCount: Int,
    val items: List<GithubTreeItem>,
) {
    @Serializable
    internal data class GithubTreeItem(
        val name: String,
        val path: String,
        val contentType: String,
    )
}