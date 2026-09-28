package com.vtuber.ai

import android.content.Context

object AvatarModels {

    fun list(context: Context): List<String> {
        val root = context.assets.list("models") ?: return emptyList()
        return root.filter { name ->
            context.assets.list("models/$name")?.any { it.endsWith(".model3.json") } == true
        }.sorted()
    }

    fun exists(context: Context, name: String): Boolean =
        name.isNotBlank() && context.assets.list("models/$name")?.any { it.endsWith(".model3.json") } == true
}
