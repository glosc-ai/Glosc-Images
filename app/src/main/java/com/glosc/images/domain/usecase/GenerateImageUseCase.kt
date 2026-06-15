package com.glosc.images.domain.usecase

import com.glosc.images.data.repository.AppRepository
import com.glosc.images.domain.model.GenerateImageRequest

class GenerateImageUseCase(private val repository: AppRepository) {
    suspend operator fun invoke(request: GenerateImageRequest) = repository.generateImage(request)
}
