package com.neovita.shared.domain.repository

import com.neovita.shared.network.dto.ContentItemDto
import com.neovita.shared.network.dto.ContentRequest

interface ContentRepository {
    /** Active content items for the dashboard feed, ordered by sortOrder. */
    suspend fun getContent(): Result<List<ContentItemDto>>

    // --- Administration (EMPLOYER role) ---
    /** All items incl. inactive, for the admin screen. */
    suspend fun getAllContent(): Result<List<ContentItemDto>>
    suspend fun create(req: ContentRequest): Result<ContentItemDto>
    suspend fun update(id: String, req: ContentRequest): Result<ContentItemDto>
    suspend fun delete(id: String): Result<Unit>
}
