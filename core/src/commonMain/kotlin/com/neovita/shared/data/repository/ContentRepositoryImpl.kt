package com.neovita.shared.data.repository

import com.neovita.shared.domain.repository.ContentRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.ContentItemDto
import com.neovita.shared.network.dto.ContentRequest

class ContentRepositoryImpl(private val apiService: ApiService) : ContentRepository {
    override suspend fun getContent(): Result<List<ContentItemDto>> = apiService.getContent()
    override suspend fun getAllContent(): Result<List<ContentItemDto>> = apiService.getAllContent()
    override suspend fun create(req: ContentRequest): Result<ContentItemDto> = apiService.createContent(req)
    override suspend fun update(id: String, req: ContentRequest): Result<ContentItemDto> = apiService.updateContent(id, req)
    override suspend fun delete(id: String): Result<Unit> = apiService.deleteContent(id)
}
